package dev.ryvrook.lullaby.sleep;

import dev.ryvrook.lullaby.model.Requirement;
import dev.ryvrook.lullaby.model.Rule;
import dev.ryvrook.lullaby.model.SleepSnapshot;
import dev.ryvrook.lullaby.sleep.SleepCoordinator.Start;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SkipDecisionTest {
    private static final Rule PERCENT = new Rule(true, Requirement.PERCENT, 50, 1);
    private static final Rule COUNT = new Rule(true, Requirement.COUNT, 50, 2);
    private static final Rule DYNAMIC = new Rule(true, Requirement.DYNAMIC, 50, 1);
    private static final Rule OFF = new Rule(false, Requirement.PERCENT, 50, 1);
    private static final BooleanSupplier YES = () -> true;
    private static final BooleanSupplier NO = () -> false;
    private static final BooleanSupplier NOT_ASKED = () -> fail("the threshold should not be counted");

    @Test
    void theThresholdStartsALullabySlumber() {
        assertEquals(Start.LULLABY, SleepCoordinator.decideStart(PERCENT, false, YES, NO));
        assertEquals(Start.LULLABY, SleepCoordinator.decideStart(COUNT, false, YES, YES));
        assertEquals(Start.NONE, SleepCoordinator.decideStart(PERCENT, false, NO, NO));
    }

    @Test
    void everyoneInBedStillStartsTheVanillaSlumber() {
        assertEquals(Start.VANILLA, SleepCoordinator.decideStart(PERCENT, false, NO, YES));
    }

    @Test
    void disabledDynamicAndCoolingWorldsOnlyFollowTheVanillaRule() {
        assertEquals(Start.VANILLA, SleepCoordinator.decideStart(OFF, false, NOT_ASKED, YES));
        assertEquals(Start.NONE, SleepCoordinator.decideStart(OFF, false, NOT_ASKED, NO));
        assertEquals(Start.VANILLA, SleepCoordinator.decideStart(DYNAMIC, false, NOT_ASKED, YES));
        assertEquals(Start.NONE, SleepCoordinator.decideStart(DYNAMIC, false, NOT_ASKED, NO));
        assertEquals(Start.VANILLA, SleepCoordinator.decideStart(PERCENT, true, NOT_ASKED, YES));
        assertEquals(Start.NONE, SleepCoordinator.decideStart(PERCENT, true, NOT_ASKED, NO));
    }

    @Test
    void theCooldownRunsForItsSecondsAfterASkip() {
        long skip = 1_000_000L;
        assertFalse(SleepCoordinator.cooling(skip, 0L, 60));
        assertTrue(SleepCoordinator.cooling(skip + 59_999L, skip, 60));
        assertFalse(SleepCoordinator.cooling(skip + 60_000L, skip, 60));
        assertFalse(SleepCoordinator.cooling(skip, skip, 0));
    }

    @Test
    void cooldownSecondsRoundUp() {
        long skip = 1_000_000L;
        assertEquals(60, SleepCoordinator.secondsLeft(skip, skip, 60));
        assertEquals(1, SleepCoordinator.secondsLeft(skip + 59_001L, skip, 60));
        assertEquals(0, SleepCoordinator.secondsLeft(skip + 60_000L, skip, 60));
        assertEquals(0, SleepCoordinator.secondsLeft(skip, 0L, 60));
    }

    @Test
    void aSkipHoldsWhileEnoughPlayersStayInBed() {
        assertTrue(SleepCoordinator.held(new SleepSnapshot(4, 2, 0, 2, List.of(), 4)));
        assertFalse(SleepCoordinator.held(new SleepSnapshot(4, 1, 1, 2, List.of(), 4)));
    }

    @Test
    void onlyAVanillaSlumberThatMovedTheClockAnHourCounts() {
        Instant start = Instant.parse("0001-01-05T22:00:00Z");
        assertTrue(SleepCoordinator.completedSlumber(start, start.plus(Duration.ofHours(6))));
        assertTrue(SleepCoordinator.completedSlumber(start, start.plus(Duration.ofHours(1))));
        assertFalse(SleepCoordinator.completedSlumber(start, start.plus(Duration.ofMinutes(20))));
    }

    @Test
    void dynamicSpeedGrowsWithTheReadyShare() {
        assertEquals(1.0, SleepCoordinator.dynamicSpeed(20, 0.0));
        assertEquals(20.0, SleepCoordinator.dynamicSpeed(20, 1.0));
        assertEquals(2.9, SleepCoordinator.dynamicSpeed(20, 0.1), 1e-9);
        assertEquals(20.0, SleepCoordinator.dynamicSpeed(20, 3.0));
        assertEquals(1.0, SleepCoordinator.dynamicSpeed(1, 0.5));
    }
}
