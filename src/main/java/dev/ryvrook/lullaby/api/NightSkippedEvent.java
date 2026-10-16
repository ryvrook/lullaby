package dev.ryvrook.lullaby.api;

import com.hypixel.hytale.event.IEvent;

/**
 * Fired once for every night Lullaby counts as skipped, on the engine event bus. It is a synchronous event
 * without a key, so register it like any global event,
 * {@code getEventRegistry().registerGlobal(NightSkippedEvent.class, event -> ...)}.
 *
 * <p>It is delivered on the world's thread right after the clock moved to morning. Keep listeners short and hand
 * slow work, such as a web request, to another thread. Only plain values, nothing of the engine. Fields are only
 * ever added.
 */
public final class NightSkippedEvent implements IEvent<Void> {
    private final String world;
    private final int sleepers;
    private final int nightsSkipped;
    private final String cause;

    public NightSkippedEvent(String world, int sleepers, int nightsSkipped, String cause) {
        this.world = world;
        this.sleepers = sleepers;
        this.nightsSkipped = nightsSkipped;
        this.cause = cause;
    }

    /** The world name as the engine spells it. */
    public String world() {
        return world;
    }

    /** Players who were in bed when the night ended, 0 after an admin skip with nobody in bed. */
    public int sleepers() {
        return sleepers;
    }

    /** Nights skipped in this world including this one. */
    public int nightsSkipped() {
        return nightsSkipped;
    }

    /**
     * What ended the night. {@code native} for a Lullaby slumber in native mode, {@code manual} for the manual fast
     * forward, {@code dynamic} for a dynamic night that reached morning, {@code vanilla} when everyone slept and
     * the vanilla slumber finished, {@code admin} for {@code /lullaby admin skip}. More values may be added.
     */
    public String cause() {
        return cause;
    }

    @Override
    public String toString() {
        return "NightSkippedEvent{" + world + " " + sleepers + " slept, night " + nightsSkipped + ", " + cause + "}";
    }
}
