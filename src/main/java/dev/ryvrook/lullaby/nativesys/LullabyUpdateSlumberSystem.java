package dev.ryvrook.lullaby.nativesys;

import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.sleep.SleepCoordinator;
import dev.ryvrook.lullaby.sleep.Waker;

import java.time.Duration;
import java.time.Instant;

/**
 * Stands in for the vanilla UpdateWorldSlumberSystem while native mode is on. Each tick it advances the
 * slumber exactly like vanilla and, at the end, sets the clock to the wake up time and gets the sleepers
 * out of bed. The difference is when a slumber is cut short. Vanilla stops as soon as any player is awake.
 * A slumber Lullaby started only stops when fewer players than the threshold are still in bed.
 */
public final class LullabyUpdateSlumberSystem extends TickingSystem<EntityStore> {
    private final ResourceType<EntityStore, WorldSomnolence> worldSomnolenceType;
    private final ResourceType<EntityStore, WorldTimeResource> timeType;
    private final SleepCoordinator coordinator;

    public LullabyUpdateSlumberSystem(ResourceType<EntityStore, WorldSomnolence> worldSomnolenceType,
                                      ResourceType<EntityStore, WorldTimeResource> timeType,
                                      SleepCoordinator coordinator) {
        this.worldSomnolenceType = worldSomnolenceType;
        this.timeType = timeType;
        this.coordinator = coordinator;
    }

    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        WorldSomnolence somnolence = store.getResource(worldSomnolenceType);
        if (!(somnolence.getState() instanceof WorldSlumber slumber)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        slumber.incrementProgressSeconds(dt);
        boolean done = slumber.getProgressSeconds() >= slumber.getIrlDurationSeconds();
        boolean abort = !done && coordinator.nativeShouldAbort(world, store);
        if (!done && !abort) {
            return;
        }
        somnolence.setState(WorldSleep.Awake.INSTANCE);
        WorldTimeResource time = store.getResource(timeType);
        time.setGameTime(wakeTime(slumber), world, store);
        // A finished night also gets players who were still nodding off out of bed. A cut short one only
        // wakes the sleepers, as vanilla does, so someone who just lay down stays in bed.
        int woken = Waker.wake(store, done, 0);
        coordinator.nativeEnded(world, store, done, woken);
    }

    /** The share of the night that has passed, copied from vanilla. */
    private static Instant wakeTime(WorldSlumber slumber) {
        float share = slumber.getProgressSeconds() / slumber.getIrlDurationSeconds();
        long nanos = (long) (share * Duration.between(slumber.getStartInstant(), slumber.getTargetInstant()).toNanos());
        return slumber.getStartInstant().plusNanos(nanos);
    }
}
