package dev.ryvrook.lullaby.api;

/**
 * One world's last sleep count. Plain values only.
 *
 * @param world           the world name as the engine spells it
 * @param enabled         false when the world is left to vanilla, where everyone has to sleep
 * @param requirement     percent, count or dynamic
 * @param online          every valid player in the world
 * @param eligible        players the rule is measured against, sleepers always included
 * @param inBed           eligible players lying in bed, nodding off or asleep
 * @param ready           players in bed long enough to count for a skip, 3.2 seconds as vanilla has it
 * @param needed          sleepers the rule asks for, at least 1
 * @param progress        from 0 to 1, in bed over needed, or in bed over eligible for a dynamic rule
 * @param cooldownSeconds seconds left before another skip may start in this world, 0 when none
 * @param nightsSkipped   nights skipped in this world so far
 */
public record SleepStatus(String world, boolean enabled, String requirement, int online, int eligible, int inBed,
                          int ready, int needed, double progress, long cooldownSeconds, int nightsSkipped) {

    /** How many more players have to get into bed, never negative. */
    public int missing() {
        return Math.max(0, needed - inBed);
    }
}
