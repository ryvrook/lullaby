package dev.ryvrook.lullaby.sleep;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WakeTimeTest {
    /** WakeUpHour of the vanilla Default gameplay config, 04:47. */
    private static final float WAKE = 4.79f;

    private static Instant at(String time) {
        return Instant.parse("0001-01-05T" + time + "Z");
    }

    private static float progress(String time) {
        return (float) WakeTime.dayProgress(at(time));
    }

    @Test
    void wakeInstantIsTodayBeforeTheHourAndTomorrowAfterIt() {
        assertEquals(at("04:47:00"), WakeTime.wakeInstant(at("01:00:00"), WAKE));
        assertEquals(Instant.parse("0001-01-06T04:47:00Z"), WakeTime.wakeInstant(at("23:00:00"), WAKE));
        assertEquals(Instant.parse("0001-01-06T04:47:00Z"), WakeTime.wakeInstant(at("04:47:00"), WAKE));
    }

    @Test
    void aSlumberLastsAtLeastThreeRealSeconds() {
        assertEquals(3f, WakeTime.irlSeconds(at("19:30:00"), Instant.parse("0001-01-06T04:47:00Z")));
        assertEquals(3f, WakeTime.irlSeconds(at("04:00:00"), at("04:47:00")));
        assertEquals(4f, WakeTime.irlSeconds(at("00:00:00"), Instant.parse("0001-01-06T00:00:00Z")));
    }

    @Test
    void dayProgressRunsFromMidnight() {
        assertEquals(0.0, WakeTime.dayProgress(at("00:00:00")));
        assertEquals(0.5, WakeTime.dayProgress(at("12:00:00")));
        assertEquals(0.75, WakeTime.dayProgress(at("18:00:00")));
    }

    @Test
    void nightRunsFromDuskToTheWakeUpTime() {
        assertTrue(WakeTime.isNight(progress("22:00:00"), at("22:00:00"), WAKE));
        assertTrue(WakeTime.isNight(progress("02:00:00"), at("02:00:00"), WAKE));
        assertTrue(WakeTime.isNight(progress("04:46:00"), at("04:46:00"), WAKE));
        assertFalse(WakeTime.isNight(progress("12:00:00"), at("12:00:00"), WAKE));
        assertFalse(WakeTime.isNight(progress("06:00:00"), at("06:00:00"), WAKE));
    }

    @Test
    void theMinuteAfterWakingIsNotNight() {
        // Between the wake up time and 04:48 the next wake up is a day away, so an admin skip there would
        // jump over a whole day.
        assertFalse(WakeTime.isNight(progress("04:47:30"), at("04:47:30"), WAKE));
    }
}
