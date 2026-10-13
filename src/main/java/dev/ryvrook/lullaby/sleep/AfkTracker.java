package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notices idle players. The engine keeps no activity timestamp, so each world polls the position and head
 * rotation of its players once a second and any change counts as activity. Joining and chatting count too.
 */
public final class AfkTracker {
    private static final double MOVE_EPSILON = 0.01;
    private static final float TURN_EPSILON = 0.001f;

    private static final class Activity {
        double x;
        double y;
        double z;
        float pitch;
        float yaw;
        volatile long lastActive;
    }

    private final Map<UUID, Activity> players = new ConcurrentHashMap<>();

    /** Marks a player active right now, used on join and chat. */
    public void touch(UUID uuid, long now) {
        players.computeIfAbsent(uuid, id -> new Activity()).lastActive = now;
    }

    public void forget(UUID uuid) {
        players.remove(uuid);
    }

    /** Compares every player's position and head rotation with the last poll. Safe from any thread. */
    public void poll(Collection<PlayerRef> refs, long now) {
        for (PlayerRef ref : refs) {
            if (ref == null) {
                continue;
            }
            Transform transform = ref.getTransform();
            Vector3d pos = transform == null ? null : transform.getPosition();
            Rotation3f head = ref.getHeadRotation();
            if (pos == null) {
                continue;
            }
            float pitch = head == null ? 0f : head.pitch();
            float yaw = head == null ? 0f : head.yaw();
            Activity a = players.get(ref.getUuid());
            if (a == null) {
                a = new Activity();
                set(a, pos, pitch, yaw);
                a.lastActive = now;
                players.put(ref.getUuid(), a);
                continue;
            }
            boolean moved = Math.abs(a.x - pos.x) > MOVE_EPSILON || Math.abs(a.y - pos.y) > MOVE_EPSILON
                    || Math.abs(a.z - pos.z) > MOVE_EPSILON;
            boolean turned = Math.abs(a.pitch - pitch) > TURN_EPSILON || Math.abs(a.yaw - yaw) > TURN_EPSILON;
            if (moved || turned) {
                a.lastActive = now;
            }
            set(a, pos, pitch, yaw);
        }
    }

    /** True when the player has not moved, looked around or chatted for {@code afkSeconds}. 0 turns it off. */
    public boolean isAfk(UUID uuid, long now, int afkSeconds) {
        if (afkSeconds <= 0) {
            return false;
        }
        Activity a = players.get(uuid);
        return a != null && now - a.lastActive >= afkSeconds * 1000L;
    }

    private static void set(Activity a, Vector3d pos, float pitch, float yaw) {
        a.x = pos.x;
        a.y = pos.y;
        a.z = pos.z;
        a.pitch = pitch;
        a.yaw = yaw;
    }
}
