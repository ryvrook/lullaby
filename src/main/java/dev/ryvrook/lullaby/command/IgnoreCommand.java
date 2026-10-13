package dev.ryvrook.lullaby.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.LullabyPlugin;

/**
 * {@code /lullaby ignore} takes the caller out of the sleep count, or puts them back. An ignored player
 * never holds up a skip, and still sleeps and wakes like everyone else when they use a bed.
 */
final class IgnoreCommand extends AbstractPlayerCommand {
    private final LullabyPlugin plugin;

    IgnoreCommand(LullabyPlugin plugin) {
        super("ignore", "Stop or start counting yourself for the sleep check.");
        requireNoPermission();
        this.plugin = plugin;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        boolean ignored = plugin.data().toggleIgnored(playerRef.getUuid());
        plugin.saveDataSoon();
        context.sendMessage(ignored
                ? Replies.ok("You no longer count for the sleep check. Run /lullaby ignore again to count.")
                : Replies.ok("You count for the sleep check again."));
    }
}
