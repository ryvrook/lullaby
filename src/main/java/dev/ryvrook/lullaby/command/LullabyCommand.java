package dev.ryvrook.lullaby.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.LullabyPlugin;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.model.Rule;
import dev.ryvrook.lullaby.model.SleepSnapshot;
import dev.ryvrook.lullaby.sleep.Announcer;
import dev.ryvrook.lullaby.sleep.WakeTime;

/**
 * {@code /lullaby}, alias {@code /sleepers}, shows the sleep status of the caller's world. Its sub commands
 * are ignore and admin.
 */
public final class LullabyCommand extends AbstractPlayerCommand {
    private final LullabyPlugin plugin;

    public LullabyCommand(LullabyPlugin plugin) {
        super("lullaby", "Show who is sleeping in your world.");
        // Without this the engine generates ryvrook.lullaby.command.lullaby and only admins could run it.
        requireNoPermission();
        this.plugin = plugin;
        addAliases("sleepers");
        addSubCommand(new IgnoreCommand(plugin));
        // No requireNoPermission here, so admin and each of its sub commands get generated admin only nodes.
        addSubCommand(new AdminCommand(plugin));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        Settings settings = plugin.settings();
        Rule rule = settings.ruleFor(world.getName());
        context.sendMessage(Replies.info("status in world " + world.getName()));
        if (!rule.enabled()) {
            context.sendMessage(Replies.info("Lullaby is off in this world, so everyone has to sleep as usual."));
        } else {
            context.sendMessage(Replies.info("Mode " + plugin.modeName() + ", " + rule.describe() + "."));
            SleepSnapshot snap = plugin.coordinator().snapshot(world, store);
            if (!WakeTime.canSleep(world)) {
                context.sendMessage(Replies.info("It is not time to sleep yet. " + snap.eligible()
                        + (snap.eligible() == 1 ? " player counts" : " players count") + " right now."));
            } else if (snap.inBed() == 0) {
                context.sendMessage(Replies.info("Nobody is in bed yet, " + snap.needed() + " of " + snap.eligible()
                        + " needed."));
            } else {
                context.sendMessage(Replies.info(Announcer.progressText(snap, rule, settings) + "."));
            }
            long cooldown = plugin.coordinator().cooldownLeft(world);
            if (cooldown > 0) {
                context.sendMessage(Replies.info("The next skip can happen in " + cooldown + " seconds."));
            }
        }
        int nights = plugin.data().nightsSkipped(world.getName());
        context.sendMessage(Replies.info(nights + (nights == 1 ? " night" : " nights") + " skipped in this world."));
        if (plugin.data().isIgnored(playerRef.getUuid())) {
            context.sendMessage(Replies.info("You do not count for the sleep check. Run /lullaby ignore to count again."));
        }
    }
}
