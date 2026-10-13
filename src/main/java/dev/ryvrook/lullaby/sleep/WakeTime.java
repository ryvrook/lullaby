package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.builtin.beds.sleep.systems.world.CanSleepInWorld;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.sleep.SleepConfig;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/** Time maths shared by every way Lullaby ends a night. The wake up rules copy the vanilla slumber. */
public final class WakeTime {
    /** WakeUpHour of the vanilla Default gameplay config, used when a world has no sleep config. */
    private static final float FALLBACK_WAKE_HOUR = 4.79f;

    private WakeTime() {
    }

    public static SleepConfig sleepConfig(World world) {
        GameplayConfig gameplay = world.getGameplayConfig();
        if (gameplay == null || gameplay.getWorldConfig() == null) {
            return null;
        }
        return gameplay.getWorldConfig().getSleepConfig();
    }

    public static float wakeHour(World world) {
        SleepConfig config = sleepConfig(world);
        return config == null ? FALLBACK_WAKE_HOUR : config.getWakeUpHour();
    }

    /** Inside the sleep hours and with game time running, the same test vanilla runs before a slumber. */
    public static boolean canSleep(World world) {
        return !CanSleepInWorld.check(world).isNegative();
    }

    /**
     * True between dusk and the wake up time, used to stop an admin skip from jumping over a whole day. The
     * second check covers the game minute between the wake up hour and sunrise, when the next wake up time
     * is already a day away.
     */
    public static boolean isNight(WorldTimeResource time, World world) {
        float progress = time.getDayProgress();
        if (progress < 0.8f && progress >= 0.2f) {
            return false;
        }
        Instant now = time.getGameTime();
        return Duration.between(now, wakeInstant(now, wakeHour(world))).compareTo(Duration.ofHours(12)) <= 0;
    }

    /** Today at the wake up hour, or tomorrow when that is not after {@code now}. Copied from vanilla. */
    public static Instant wakeInstant(Instant now, float wakeHour) {
        LocalDateTime current = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int hour = (int) wakeHour;
        float minutes = (wakeHour - hour) * 60f;
        LocalDateTime target = current.toLocalDate().atTime(hour, (int) minutes);
        if (!current.isBefore(target)) {
            target = target.plusDays(1);
        }
        return target.toInstant(ZoneOffset.UTC);
    }

    /** Real seconds a native slumber lasts, at least 3, copied from vanilla. */
    public static float irlSeconds(Instant start, Instant target) {
        long hours = TimeUnit.MILLISECONDS.toHours(Duration.between(start, target).toMillis());
        return (float) Math.ceil(Math.max(3.0, hours / 6.0));
    }

    /** The day progress of an instant, from 0 at midnight to just under 1. */
    public static double dayProgress(Instant instant) {
        LocalDateTime t = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return t.toLocalTime().toNanoOfDay() / (double) TimeUnit.DAYS.toNanos(1);
    }

    /** Game seconds that pass per real second at night in this world, 30 with the default config. */
    public static double nightRate(World world) {
        int night = world.getNighttimeDurationSeconds();
        if (night <= 0) {
            return 30.0;
        }
        return WorldTimeResource.NIGHTTIME_SECONDS / (double) night;
    }
}
