package dev.ryvrook.lullaby.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepSnapshotTest {

    private static SleepSnapshot snap(int eligible, int inBed, int ready, int needed) {
        return new SleepSnapshot(eligible, inBed, ready, needed, List.of(), eligible);
    }

    @Test
    void metCountsReadyPlayersNotPlayersInBed() {
        assertFalse(snap(4, 2, 1, 2).met());
        assertTrue(snap(4, 2, 2, 2).met());
        assertTrue(snap(4, 3, 3, 2).met());
    }

    @Test
    void nobodyReadyIsNeverMet() {
        assertFalse(snap(0, 0, 0, 0).met());
    }

    @Test
    void missingCountsPlayersInBedAndStopsAtZero() {
        assertEquals(1, snap(5, 2, 0, 3).missing());
        assertEquals(0, snap(5, 4, 1, 3).missing());
    }

    @Test
    void readyShareStaysBetweenZeroAndOne() {
        assertEquals(0.0, snap(0, 0, 0, 1).readyShare());
        assertEquals(0.25, snap(4, 2, 1, 2).readyShare());
        assertEquals(1.0, snap(2, 3, 3, 1).readyShare());
    }

    @Test
    void progressIsInBedOverNeededAndStopsAtOne() {
        assertEquals(0.5, snap(4, 1, 0, 2).progress(false));
        assertEquals(1.0, snap(4, 3, 3, 2).progress(false));
        assertEquals(0.75, snap(4, 3, 0, 1).progress(true));
        assertEquals(0.0, SleepSnapshot.empty().progress(true));
        assertEquals(0.0, SleepSnapshot.empty().progress(false));
    }
}
