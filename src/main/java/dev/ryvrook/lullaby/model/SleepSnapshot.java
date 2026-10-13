package dev.ryvrook.lullaby.model;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

/**
 * Who is in bed in one world right now, read on that world's thread.
 *
 * @param eligible players the threshold is measured against, sleepers always included
 * @param inBed    eligible players lying in bed, nodding off or asleep
 * @param ready    players in bed long enough to count for a skip, as vanilla defines it
 * @param needed   sleepers the rule asks for
 * @param awake    eligible players who are not in bed, the ones a progress toast goes to
 * @param online   every valid player in the world
 */
public record SleepSnapshot(int eligible, int inBed, int ready, int needed, List<PlayerRef> awake, int online) {

    /** True when enough players are ready for a skip. */
    public boolean met() {
        return ready > 0 && ready >= needed;
    }

    /** How many more players have to get into bed, never negative. */
    public int missing() {
        return Math.max(0, needed - inBed);
    }

    /** The share of eligible players who are ready, from 0 to 1. */
    public double readyShare() {
        return eligible <= 0 ? 0.0 : Math.min(1.0, (double) ready / eligible);
    }
}
