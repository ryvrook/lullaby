package dev.ryvrook.lullaby.command;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.LullabyPlugin;
import dev.ryvrook.lullaby.sleep.WakeTime;

import java.util.Locale;

/**
 * {@code /lullaby admin ...} for server staff. None of these call requireNoPermission, so the engine
 * guards each one with its own generated node under {@code ryvrook.lullaby.command.lullaby.admin}. Every
 * sub command also works from the console.
 */
final class AdminCommand extends AbstractCommandCollection {

    AdminCommand(LullabyPlugin plugin) {
        super("admin", "Lullaby tools for server staff.");
        addSubCommand(new Skip(plugin));
        addSubCommand(new Set(plugin));
        addSubCommand(new WorldToggle(plugin));
        addSubCommand(new Reload(plugin));
    }

    /** {@code skip [--world <name>]} ends the night now, in the sender's world or the one named. */
    static final class Skip extends AbstractWorldCommand {
        private final LullabyPlugin plugin;

        Skip(LullabyPlugin plugin) {
            super("skip", "Skip the night now.");
            this.plugin = plugin;
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            if (time != null && !WakeTime.isNight(time, world)) {
                context.sendMessage(Replies.error("It is daytime in " + world.getName() + ", there is no night to skip."));
                return;
            }
            plugin.coordinator().requestSkip(world);
            context.sendMessage(Replies.ok("Skipping the night in " + world.getName() + "."));
        }
    }

    /** {@code set percent <n>}, {@code set count <n>} and {@code set dynamic} change the default rule. */
    static final class Set extends AbstractCommandCollection {
        Set(LullabyPlugin plugin) {
            super("set", "Change how many players need to sleep.");
            addSubCommand(new Percent(plugin));
            addSubCommand(new Count(plugin));
            addSubCommand(new Dynamic(plugin));
        }

        static final class Percent extends CommandBase {
            private final LullabyPlugin plugin;
            private final RequiredArg<Integer> value;

            Percent(LullabyPlugin plugin) {
                super("percent", "Need a percentage of the players.");
                this.plugin = plugin;
                this.value = withRequiredArg("percent", "0 to 100", ArgTypes.INTEGER);
            }

            @Override
            protected void executeSync(CommandContext context) {
                int n = context.get(value);
                if (n < 0 || n > 100) {
                    context.sendMessage(Replies.error("Use a percentage from 0 to 100."));
                    return;
                }
                plugin.updateConfig(c -> c.setPercent(n), n + "% of players now need to sleep.")
                        .thenAccept(msg -> Replies.reply(context, msg));
            }
        }

        static final class Count extends CommandBase {
            private final LullabyPlugin plugin;
            private final RequiredArg<Integer> value;

            Count(LullabyPlugin plugin) {
                super("count", "Need a fixed number of players.");
                this.plugin = plugin;
                this.value = withRequiredArg("count", "1 or more", ArgTypes.INTEGER);
            }

            @Override
            protected void executeSync(CommandContext context) {
                int n = context.get(value);
                if (n < 1) {
                    context.sendMessage(Replies.error("Use a count of 1 or more."));
                    return;
                }
                plugin.updateConfig(c -> c.setCount(n), n + (n == 1 ? " player now needs" : " players now need")
                        + " to sleep.").thenAccept(msg -> Replies.reply(context, msg));
            }
        }

        static final class Dynamic extends CommandBase {
            private final LullabyPlugin plugin;

            Dynamic(LullabyPlugin plugin) {
                super("dynamic", "Speed the night up with every sleeper.");
                this.plugin = plugin;
            }

            @Override
            protected void executeSync(CommandContext context) {
                if (!plugin.settings().allowDynamic()) {
                    context.sendMessage(Replies.error("Dynamic nights are experimental and off. Set AllowDynamic to true"
                            + " in Lullaby.json, run /lullaby admin reload, then try again."));
                    return;
                }
                plugin.updateConfig(c -> c.setDynamic(), "The night now speeds up with every sleeper.")
                        .thenAccept(msg -> Replies.reply(context, msg));
            }
        }
    }

    /** {@code world <name> enabled <true|false>} turns Lullaby on or off in one world. */
    static final class WorldToggle extends CommandBase {
        private final LullabyPlugin plugin;
        private final RequiredArg<String> name;
        private final RequiredArg<String> setting;
        private final RequiredArg<String> value;

        WorldToggle(LullabyPlugin plugin) {
            super("world", "Turn Lullaby on or off in one world.");
            this.plugin = plugin;
            this.name = withRequiredArg("name", "World name", ArgTypes.STRING);
            this.setting = withRequiredArg("setting", "enabled", ArgTypes.STRING);
            this.value = withRequiredArg("value", "true or false", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            String world = context.get(name).trim();
            if (!"enabled".equalsIgnoreCase(context.get(setting).trim())) {
                context.sendMessage(Replies.error("Only enabled can be set here, for example /lullaby admin world "
                        + world + " enabled false."));
                return;
            }
            String raw = context.get(value).trim().toLowerCase(Locale.ROOT);
            boolean on;
            if (raw.equals("true") || raw.equals("on")) {
                on = true;
            } else if (raw.equals("false") || raw.equals("off")) {
                on = false;
            } else {
                context.sendMessage(Replies.error("Use true or false."));
                return;
            }
            String note = Universe.get().getWorld(world) == null
                    ? " No world by that name is loaded right now, the rule waits for it." : "";
            plugin.updateConfig(c -> c.worldRule(world).setEnabled(on),
                    "Lullaby is now " + (on ? "on" : "off") + " in " + world + "." + note)
                    .thenAccept(msg -> Replies.reply(context, msg));
        }
    }

    /** {@code reload} re-reads Lullaby.json and switches between native and manual when the mode changed. */
    static final class Reload extends CommandBase {
        private final LullabyPlugin plugin;

        Reload(LullabyPlugin plugin) {
            super("reload", "Reload Lullaby.json.");
            this.plugin = plugin;
        }

        @Override
        protected void executeSync(CommandContext context) {
            plugin.reload().thenAccept(msg -> Replies.reply(context, msg));
        }
    }
}
