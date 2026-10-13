package dev.ryvrook.lullaby.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.lullaby.sleep.Announcer;

import java.util.UUID;

/** Reply helpers. Replies from the IO thread go back on the sender's world thread, as in Feats. */
final class Replies {
    private Replies() {
    }

    static Message info(String text) {
        return Announcer.line(text, Announcer.MUTED);
    }

    static Message ok(String text) {
        return Announcer.line(text, Announcer.SUCCESS);
    }

    static Message error(String text) {
        return Announcer.line(text, Announcer.ERROR);
    }

    /** Sends a reply on the sender's world thread when the sender is a player, directly otherwise. */
    static void reply(CommandContext context, Message message) {
        World world = senderWorld(context);
        if (world == null) {
            context.sendMessage(message);
            return;
        }
        try {
            world.execute(() -> context.sendMessage(message));
        } catch (RuntimeException e) {
            context.sendMessage(message);
        }
    }

    private static World senderWorld(CommandContext context) {
        if (!context.isPlayer()) {
            return null;
        }
        try {
            PlayerRef sender = context.senderAs(PlayerRef.class);
            UUID worldId = sender == null ? null : sender.getWorldUuid();
            return worldId == null ? null : Universe.get().getWorld(worldId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
