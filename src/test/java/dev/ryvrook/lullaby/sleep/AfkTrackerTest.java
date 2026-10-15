package dev.ryvrook.lullaby.sleep;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkTrackerTest {

    @Test
    void aPlayerIsIdleAfterAfkSecondsWithoutActivity() {
        AfkTracker afk = new AfkTracker();
        UUID id = UUID.randomUUID();
        afk.touch(id, 1_000L);
        assertFalse(afk.isAfk(id, 300_999L, 300));
        assertTrue(afk.isAfk(id, 301_000L, 300));
        afk.touch(id, 301_000L);
        assertFalse(afk.isAfk(id, 301_000L, 300));
    }

    @Test
    void zeroSecondsTurnsTheCheckOff() {
        AfkTracker afk = new AfkTracker();
        UUID id = UUID.randomUUID();
        afk.touch(id, 0L);
        assertFalse(afk.isAfk(id, Long.MAX_VALUE / 2, 0));
    }

    @Test
    void anUnknownOrForgottenPlayerIsNotIdle() {
        AfkTracker afk = new AfkTracker();
        UUID id = UUID.randomUUID();
        assertFalse(afk.isAfk(id, 10_000_000L, 1));
        afk.touch(id, 0L);
        afk.forget(id);
        assertFalse(afk.isAfk(id, 10_000_000L, 1));
    }
}
