package dev.ryvrook.lullaby.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.sleep.SleepCoordinator;

/**
 * Runs Lullaby's own per world work every tick on that world's thread. The coordinator throttles the
 * counting to every 0.3 seconds and the idle check to once a second. Only dynamic nights and manual skips
 * need the full tick rate.
 */
public final class LullabyTickSystem extends TickingSystem<EntityStore> {
    private final SleepCoordinator coordinator;

    public LullabyTickSystem(SleepCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        coordinator.tick(store.getExternalData().getWorld(), store, dt);
    }
}
