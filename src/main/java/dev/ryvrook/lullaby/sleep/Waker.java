package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.gameplay.sleep.SleepConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gets players out of bed the way the vanilla slumber does, by writing the shared awake state and removing
 * the bed mount. Must run inside a system tick on the world thread, because it uses the store's command
 * buffers.
 */
public final class Waker {
    private Waker() {
    }

    /**
     * Wakes players who are asleep, and with {@code includeNoddingOff} also those still nodding off.
     *
     * @param successSound the vanilla sleep success sound index to play to each woken player, or 0 for none
     * @return how many players were woken
     */
    public static int wake(Store<EntityStore> store, boolean includeNoddingOff, int successSound) {
        AtomicInteger woken = new AtomicInteger();
        store.forEachEntityParallel(PlayerSomnolence.getComponentType(), (index, chunk, buffer) -> {
            PlayerSomnolence somnolence = chunk.getComponent(index, PlayerSomnolence.getComponentType());
            if (somnolence == null) {
                return;
            }
            PlayerSleep state = somnolence.getSleepState();
            boolean asleep = state instanceof PlayerSleep.Slumber;
            boolean nodding = state instanceof PlayerSleep.NoddingOff;
            if (!asleep && !(includeNoddingOff && nodding)) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            buffer.putComponent(ref, PlayerSomnolence.getComponentType(), PlayerSomnolence.AWAKE);
            MountedComponent mount = chunk.getComponent(index, MountedComponent.getComponentType());
            if (mount != null && mount.getControllerType() == MountController.BlockMount) {
                buffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
            }
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null) {
                woken.incrementAndGet();
                if (successSound > 0) {
                    SoundUtil.playSoundEvent2dToPlayer(player, successSound, SoundCategory.UI);
                }
            }
        });
        return woken.get();
    }

    /** The vanilla sleep success sound of a world, or 0 when there is none. */
    public static int successSound(SleepConfig config) {
        try {
            return config == null || config.getSounds() == null ? 0 : config.getSounds().getSuccessIndex();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
