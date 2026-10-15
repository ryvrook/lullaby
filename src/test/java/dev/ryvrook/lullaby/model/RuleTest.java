package dev.ryvrook.lullaby.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleTest {

    private static Rule percent(int p) {
        return new Rule(true, Requirement.PERCENT, p, 1);
    }

    private static Rule count(int c) {
        return new Rule(true, Requirement.COUNT, 50, c);
    }

    @Test
    void percentRoundsUp() {
        assertEquals(3, percent(50).needed(5, 1));
        assertEquals(2, percent(50).needed(4, 1));
        assertEquals(1, percent(50).needed(1, 1));
        assertEquals(4, percent(34).needed(10, 1));
        assertEquals(10, percent(100).needed(10, 1));
    }

    @Test
    void zeroPercentStillNeedsOneSleeper() {
        assertEquals(1, percent(0).needed(8, 1));
    }

    @Test
    void countIsCappedAtTheEligiblePlayers() {
        assertEquals(2, count(2).needed(6, 1));
        assertEquals(1, count(2).needed(1, 1));
        assertEquals(3, count(5).needed(3, 1));
    }

    @Test
    void minimumSleepersIsAFloorUnlessFewerPlayersCount() {
        assertEquals(3, percent(10).needed(10, 3));
        assertEquals(2, percent(10).needed(2, 3));
        assertEquals(4, count(4).needed(10, 2));
    }

    @Test
    void dynamicNeedsOneSleeperOrTheMinimum() {
        Rule dynamic = new Rule(true, Requirement.DYNAMIC, 50, 1);
        assertEquals(1, dynamic.needed(10, 1));
        assertEquals(2, dynamic.needed(10, 2));
    }

    @Test
    void anEmptyWorldNeedsOne() {
        assertEquals(1, percent(50).needed(0, 4));
        assertEquals(1, count(3).needed(-1, 1));
    }

    @Test
    void describeNamesTheRule() {
        assertEquals("50% of players needed", percent(50).describe());
        assertEquals("1 player needed", count(1).describe());
        assertEquals("3 players needed", count(3).describe());
    }
}
