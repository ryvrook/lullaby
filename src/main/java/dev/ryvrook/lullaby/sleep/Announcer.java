package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.model.Requirement;
import dev.ryvrook.lullaby.model.Rule;
import dev.ryvrook.lullaby.model.SleepSnapshot;

import java.util.logging.Level;

/** Chat lines, toasts and the morning title. Every send here is safe from any thread. */
public final class Announcer {
    public static final String BRAND = "#E8C15A";
    public static final String SUCCESS = "#8EDD9A";
    public static final String ERROR = "#ff6b6b";
    public static final String MUTED = "#96a9be";
    private static final String TOAST_ICON = "Furniture_Crude_Bed";

    private final HytaleLogger logger;
    private volatile boolean iconBroken;

    public Announcer(HytaleLogger logger) {
        this.logger = logger;
    }

    /** "Lullaby " in the brand colour, put in front of every chat line. */
    public static Message prefix() {
        return Message.raw("Lullaby ").color(BRAND).bold(true);
    }

    public static Message line(String text, String color) {
        return Message.join(prefix(), Message.raw(text).color(color));
    }

    /**
     * Words such as "2 of 5 players sleeping, 1 more needed". With dynamic it names the speed instead,
     * and once enough players are in bed it says the night will pass.
     */
    public static String progressText(SleepSnapshot snap, Rule rule, Settings settings) {
        String head = snap.inBed() + " of " + snap.eligible() + (snap.eligible() == 1 ? " player" : " players")
                + " sleeping, ";
        if (rule.requirement() == Requirement.DYNAMIC) {
            // The line goes out when someone gets into bed, before they count as ready, so it names the
            // speed their sleep will give once it does.
            double share = snap.eligible() <= 0 ? 0.0 : Math.min(1.0, (double) snap.inBed() / snap.eligible());
            long speed = Math.round(SleepCoordinator.dynamicSpeed(settings.dynamicMaxSpeed(), share));
            return head + (speed <= 1 ? "time will speed up in a moment" : "time runs " + speed + "x faster");
        }
        int missing = snap.missing();
        return head + (missing == 0 ? "the night will pass" : missing + " more needed");
    }

    /** The progress line to the whole world, plus a toast to the eligible players still awake. */
    public void progress(World world, SleepSnapshot snap, Rule rule, Settings settings) {
        String text = progressText(snap, rule, settings);
        world.sendMessage(line(text, MUTED));
        if (!settings.showToasts()) {
            return;
        }
        for (PlayerRef player : snap.awake()) {
            try {
                ItemWithAllMetadata icon = icon();
                if (icon != null) {
                    NotificationUtil.sendNotification(player.getPacketHandler(), Message.raw("Lullaby").color(BRAND),
                            Message.raw(text), icon, NotificationStyle.Default);
                } else {
                    NotificationUtil.sendNotification(player.getPacketHandler(), Message.raw("Lullaby").color(BRAND),
                            Message.raw(text), NotificationStyle.Default);
                }
            } catch (RuntimeException e) {
                logger.at(Level.FINE).withCause(e).log("Lullaby could not send a toast to %s", player.getUsername());
            }
        }
    }

    /** "Lullaby the night was skipped, 2 players slept", and the morning title when ShowTitle is on. */
    public void skipped(World world, int sleepers, Settings settings) {
        String text = sleepers <= 0 ? "the night was skipped"
                : "the night was skipped, " + sleepers + (sleepers == 1 ? " player slept" : " players slept");
        world.sendMessage(line(text, SUCCESS));
        if (!settings.showTitle()) {
            return;
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            try {
                EventTitleUtil.showEventTitleToPlayer(player, Message.raw("Good morning").color(BRAND),
                        Message.raw("The night was skipped"), EventTitleStyle.Default);
            } catch (RuntimeException e) {
                logger.at(Level.FINE).withCause(e).log("Lullaby could not show the title to %s", player.getUsername());
            }
        }
    }

    private ItemWithAllMetadata icon() {
        if (iconBroken) {
            return null;
        }
        try {
            return new ItemStack(TOAST_ICON, 1).toPacket();
        } catch (RuntimeException e) {
            iconBroken = true;
            return null;
        }
    }
}
