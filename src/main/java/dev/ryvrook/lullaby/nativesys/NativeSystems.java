package dev.ryvrook.lullaby.nativesys;

import com.hypixel.hytale.builtin.beds.BedsPlugin;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.StartSlumberSystem;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.UpdateWorldSlumberSystem;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.sleep.SleepCoordinator;

import java.util.logging.Level;

/**
 * Swaps the two vanilla slumber systems for the Lullaby copies and back. Both directions go straight
 * through {@link EntityStore#REGISTRY}, not through the plugin's own registry proxy, so the vanilla
 * systems Lullaby puts back stay registered after Lullaby is gone.
 *
 * <p>Every call here changes the registry, which updates every world store. Call it from the plugin
 * lifecycle or the Lullaby IO thread, never from a world thread.
 */
public final class NativeSystems {
    private final SleepCoordinator coordinator;
    private final HytaleLogger logger;
    private volatile boolean active;
    /** Set once on plugin shutdown. After that nothing may install the copies again. */
    private boolean closed;

    public NativeSystems(SleepCoordinator coordinator, HytaleLogger logger) {
        this.coordinator = coordinator;
        this.logger = logger;
    }

    /** True while the Lullaby copies run in place of vanilla. */
    public boolean isActive() {
        return active;
    }

    /**
     * Removes the vanilla systems and registers the copies. Returns false, after putting vanilla back and
     * logging one warning, when anything on the way throws.
     */
    public synchronized boolean install() {
        if (closed) {
            return false;
        }
        if (active) {
            return true;
        }
        try {
            BedsPlugin beds = BedsPlugin.get();
            if (beds == null) {
                throw new IllegalStateException("the Beds plugin is not loaded");
            }
            // Build the vanilla systems first, so a game version that changed them is noticed before
            // anything is removed and Lullaby knows it can always put them back.
            vanillaStart();
            vanillaUpdate();
            LullabyStartSlumberSystem start = new LullabyStartSlumberSystem(beds.getPlayerSomnolenceComponentType(),
                    beds.getWorldSomnolenceResourceType(), WorldTimeResource.getResourceType(), coordinator);
            LullabyUpdateSlumberSystem update = new LullabyUpdateSlumberSystem(beds.getWorldSomnolenceResourceType(),
                    WorldTimeResource.getResourceType(), coordinator);
            ComponentRegistry<EntityStore> registry = EntityStore.REGISTRY;
            if (registry.hasSystemClass(StartSlumberSystem.class)) {
                registry.unregisterSystem(StartSlumberSystem.class);
            }
            if (registry.hasSystemClass(UpdateWorldSlumberSystem.class)) {
                registry.unregisterSystem(UpdateWorldSlumberSystem.class);
            }
            registry.registerSystem(start);
            registry.registerSystem(update);
            active = true;
            logger.at(Level.INFO).log("Lullaby replaced the vanilla slumber systems with its own copies");
            return true;
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not take over the vanilla sleep systems on this "
                    + "server version, so it falls back to manual mode");
            restoreVanilla();
            return false;
        }
    }

    /** Removes the copies and registers fresh vanilla systems. Each step is tried on its own. */
    public synchronized void uninstall() {
        restoreVanilla();
    }

    /** Puts vanilla back for good, used on plugin shutdown. */
    public synchronized void close() {
        closed = true;
        restoreVanilla();
    }

    private void restoreVanilla() {
        ComponentRegistry<EntityStore> registry = EntityStore.REGISTRY;
        boolean wasActive = active;
        active = false;
        try {
            if (registry.hasSystemClass(LullabyStartSlumberSystem.class)) {
                registry.unregisterSystem(LullabyStartSlumberSystem.class);
            }
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not remove its slumber start system");
        }
        try {
            if (registry.hasSystemClass(LullabyUpdateSlumberSystem.class)) {
                registry.unregisterSystem(LullabyUpdateSlumberSystem.class);
            }
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not remove its slumber update system");
        }
        boolean restored = false;
        try {
            if (!registry.hasSystemClass(StartSlumberSystem.class)) {
                registry.registerSystem(vanillaStart());
                restored = true;
            }
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not put back the vanilla StartSlumberSystem");
        }
        try {
            if (!registry.hasSystemClass(UpdateWorldSlumberSystem.class)) {
                registry.registerSystem(vanillaUpdate());
                restored = true;
            }
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not put back the vanilla UpdateWorldSlumberSystem");
        }
        if (wasActive || restored) {
            logger.at(Level.INFO).log("Lullaby restored the vanilla slumber systems");
        }
    }

    private static StartSlumberSystem vanillaStart() {
        BedsPlugin beds = BedsPlugin.get();
        return new StartSlumberSystem(beds.getPlayerSomnolenceComponentType(), beds.getWorldSomnolenceResourceType(),
                WorldTimeResource.getResourceType());
    }

    private static UpdateWorldSlumberSystem vanillaUpdate() {
        BedsPlugin beds = BedsPlugin.get();
        return new UpdateWorldSlumberSystem(beds.getPlayerSomnolenceComponentType(), beds.getWorldSomnolenceResourceType(),
                WorldTimeResource.getResourceType(), MountedComponent.getComponentType());
    }
}
