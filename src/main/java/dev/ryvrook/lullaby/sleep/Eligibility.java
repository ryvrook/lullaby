package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.StartSlumberSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.data.LullabyData;
import dev.ryvrook.lullaby.model.Rule;
import dev.ryvrook.lullaby.model.SleepSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts who the threshold is measured against. Runs on the world thread that owns the store.
 *
 * <p>A player in bed always counts, as a sleeper and as eligible. Everyone else is eligible unless they
 * are spectating, in Creative mode while ExcludeCreative is on, idle for AfkSeconds or on the ignore list.
 */
public final class Eligibility {
    private Eligibility() {
    }

    public static SleepSnapshot snapshot(World world, Store<EntityStore> store, Settings settings, Rule rule,
                                         LullabyData data, AfkTracker afk, long now) {
        int eligible = 0;
        int inBed = 0;
        int ready = 0;
        int online = 0;
        List<PlayerRef> awake = new ArrayList<>();
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            online++;
            if (isInBed(store, ref)) {
                eligible++;
                inBed++;
                if (StartSlumberSystem.isReadyToSleep(store, ref)) {
                    ready++;
                }
                continue;
            }
            if (Spectating.isSpectating(ref, store)) {
                continue;
            }
            if (settings.excludeCreative()) {
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null && p.getGameMode() == GameMode.Creative) {
                    continue;
                }
            }
            if (data.isIgnored(player.getUuid())) {
                continue;
            }
            if (afk.isAfk(player.getUuid(), now, settings.afkSeconds())) {
                continue;
            }
            eligible++;
            awake.add(player);
        }
        return new SleepSnapshot(eligible, inBed, ready, rule.needed(eligible, settings.minimumSleepers()),
                List.copyOf(awake), online);
    }

    /** Nodding off or asleep. A player who never slept has no somnolence component and is awake. */
    public static boolean isInBed(Store<EntityStore> store, Ref<EntityStore> ref) {
        PlayerSomnolence s = store.getComponent(ref, PlayerSomnolence.getComponentType());
        if (s == null) {
            return false;
        }
        PlayerSleep state = s.getSleepState();
        return state instanceof PlayerSleep.NoddingOff || state instanceof PlayerSleep.Slumber;
    }

    /** The vanilla rule, every valid player in the world ready to sleep. False for an empty world. */
    public static boolean everyoneReady(World world, Store<EntityStore> store) {
        boolean any = false;
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            any = true;
            if (!StartSlumberSystem.isReadyToSleep(store, ref)) {
                return false;
            }
        }
        return any;
    }

    /** The vanilla abort check, true when any valid player has never slept or is fully awake. */
    public static boolean someoneAwake(World world, Store<EntityStore> store) {
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            PlayerSomnolence s = store.getComponent(ref, PlayerSomnolence.getComponentType());
            if (s == null || s.getSleepState() instanceof PlayerSleep.FullyAwake) {
                return true;
            }
        }
        return false;
    }
}
