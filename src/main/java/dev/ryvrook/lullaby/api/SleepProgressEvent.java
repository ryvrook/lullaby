package dev.ryvrook.lullaby.api;

import com.hypixel.hytale.event.IEvent;

/**
 * Fired on the engine event bus when the number of players in bed changes in a world that may sleep, both up and
 * down, including the drop to 0 when everyone got up or the night was skipped. Lullaby counts every 0.3 seconds,
 * so a player who gets into bed and out again within one count is not seen. Register it like any global event,
 * {@code getEventRegistry().registerGlobal(SleepProgressEvent.class, event -> ...)}.
 *
 * <p>It is delivered on the world's thread. Keep listeners short. It fires whatever ProgressMessages is set to.
 * Only plain values, nothing of the engine. Fields are only ever added.
 */
public final class SleepProgressEvent implements IEvent<Void> {
    private final String world;
    private final String requirement;
    private final int online;
    private final int eligible;
    private final int inBed;
    private final int ready;
    private final int needed;
    private final double progress;

    public SleepProgressEvent(String world, String requirement, int online, int eligible, int inBed, int ready,
                              int needed, double progress) {
        this.world = world;
        this.requirement = requirement;
        this.online = online;
        this.eligible = eligible;
        this.inBed = inBed;
        this.ready = ready;
        this.needed = needed;
        this.progress = progress;
    }

    /** The world name as the engine spells it. */
    public String world() {
        return world;
    }

    /** percent, count or dynamic. */
    public String requirement() {
        return requirement;
    }

    /** Every valid player in the world. */
    public int online() {
        return online;
    }

    /** Players the rule is measured against. */
    public int eligible() {
        return eligible;
    }

    /** Players in bed now. */
    public int inBed() {
        return inBed;
    }

    /** Players in bed long enough to count for a skip. */
    public int ready() {
        return ready;
    }

    /** Sleepers the rule asks for. */
    public int needed() {
        return needed;
    }

    /** From 0 to 1, in bed over needed, or in bed over eligible for a dynamic rule. */
    public double progress() {
        return progress;
    }

    /** How many more players have to get into bed, never negative. */
    public int missing() {
        return Math.max(0, needed - inBed);
    }

    @Override
    public String toString() {
        return "SleepProgressEvent{" + world + " " + inBed + " of " + needed + " needed, " + eligible + " eligible}";
    }
}
