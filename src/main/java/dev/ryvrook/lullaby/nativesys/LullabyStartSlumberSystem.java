package dev.ryvrook.lullaby.nativesys;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.sleep.SleepCoordinator;
import dev.ryvrook.lullaby.sleep.WakeTime;
import dev.ryvrook.lullaby.sleep.Waker;

import java.time.Duration;
import java.time.Instant;

/**
 * Stands in for the vanilla StartSlumberSystem while native mode is on. It runs on the same 0.3 second
 * beat and keeps every vanilla step, the sleep hours check, the wake up time, the slumber length and the
 * success sound. Two things differ. The slumber starts as soon as the Lullaby threshold is met, and only
 * players who are actually ready are put to sleep, where vanilla would also put players who slept earlier
 * and are now awake into the slumber state.
 */
public final class LullabyStartSlumberSystem extends DelayedSystem<EntityStore> {
    private static final float INTERVAL_S = 0.3f;
    private static final Duration NODDING_OFF = Duration.ofMillis(3200);

    private final ComponentType<EntityStore, PlayerSomnolence> somnolenceType;
    private final ResourceType<EntityStore, WorldSomnolence> worldSomnolenceType;
    private final ResourceType<EntityStore, WorldTimeResource> timeType;
    private final SleepCoordinator coordinator;

    public LullabyStartSlumberSystem(ComponentType<EntityStore, PlayerSomnolence> somnolenceType,
                                     ResourceType<EntityStore, WorldSomnolence> worldSomnolenceType,
                                     ResourceType<EntityStore, WorldTimeResource> timeType,
                                     SleepCoordinator coordinator) {
        super(INTERVAL_S);
        this.somnolenceType = somnolenceType;
        this.worldSomnolenceType = worldSomnolenceType;
        this.timeType = timeType;
        this.coordinator = coordinator;
    }

    @Override
    public void delayedTick(float dt, int systemIndex, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world.getPlayerRefs().isEmpty() || !WakeTime.canSleep(world)) {
            return;
        }
        WorldSomnolence somnolence = store.getResource(worldSomnolenceType);
        if (somnolence.getState() != WorldSleep.Awake.INSTANCE) {
            return;
        }
        SleepCoordinator.Start how = coordinator.nativeStart(world, store);
        if (how == SleepCoordinator.Start.NONE) {
            return;
        }
        WorldTimeResource time = store.getResource(timeType);
        Instant now = time.getGameTime();
        Instant target = WakeTime.wakeInstant(now, WakeTime.wakeHour(world));
        somnolence.setState(new WorldSlumber(now, target, WakeTime.irlSeconds(now, target)));
        int sound = Waker.successSound(WakeTime.sleepConfig(world));
        Instant realNow = Instant.now();
        store.forEachEntityParallel(somnolenceType, (index, chunk, buffer) -> {
            PlayerSomnolence current = chunk.getComponent(index, somnolenceType);
            if (current == null || !isReady(current.getSleepState(), realNow)) {
                return;
            }
            buffer.putComponent(chunk.getReferenceTo(index), somnolenceType, PlayerSleep.Slumber.createComponent(time));
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && sound > 0) {
                SoundUtil.playSoundEvent2dToPlayer(player, sound, SoundCategory.UI);
            }
        });
        somnolence.resetNotificationCooldown();
        coordinator.nativeStarted(world, how);
    }

    /** The vanilla readiness rule, in bed for 3.2 real seconds or already asleep. */
    private static boolean isReady(PlayerSleep state, Instant realNow) {
        if (state instanceof PlayerSleep.Slumber) {
            return true;
        }
        if (state instanceof PlayerSleep.NoddingOff nodding) {
            return realNow.isAfter(nodding.realTimeStart().plus(NODDING_OFF));
        }
        return false;
    }
}
