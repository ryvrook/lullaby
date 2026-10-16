package dev.ryvrook.lullaby;

import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import dev.ryvrook.lullaby.api.NightSkippedEvent;
import dev.ryvrook.lullaby.api.SleepProgressEvent;
import dev.ryvrook.lullaby.model.SleepSnapshot;
import dev.ryvrook.lullaby.sleep.SleepEvents;

import java.util.logging.Level;

/**
 * Sends the coordinator's moments to the engine event bus. An event is only built when something listens, and a
 * failing listener is logged at most once a minute so it cannot flood the log from a world thread.
 */
final class BusEvents implements SleepEvents {
    private static final long ERROR_LOG_GAP_MS = 60_000L;

    private final HytaleLogger logger;
    private volatile long lastErrorLog;

    BusEvents(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public void progress(String world, String requirement, SleepSnapshot snap, double progress) {
        try {
            IEventDispatcher<SleepProgressEvent, SleepProgressEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(SleepProgressEvent.class, null);
            if (d.hasListener()) {
                d.dispatch(new SleepProgressEvent(world, requirement, snap.online(), snap.eligible(), snap.inBed(),
                        snap.ready(), snap.needed(), progress));
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e, world);
        }
    }

    @Override
    public void skipped(String world, int sleepers, int nightsSkipped, String cause) {
        try {
            IEventDispatcher<NightSkippedEvent, NightSkippedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(NightSkippedEvent.class, null);
            if (d.hasListener()) {
                d.dispatch(new NightSkippedEvent(world, sleepers, nightsSkipped, cause));
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e, world);
        }
    }

    private void failed(Throwable e, String world) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog > ERROR_LOG_GAP_MS) {
            lastErrorLog = now;
            logger.at(Level.WARNING).withCause(e).log("A Lullaby event listener failed in world %s", world);
        }
    }
}
