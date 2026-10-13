package dev.ryvrook.lullaby.sleep;

import dev.ryvrook.lullaby.model.SleepSnapshot;

import java.time.Instant;

/**
 * What Lullaby remembers about one world. Only that world's thread touches the plain fields. The volatile
 * ones are also read or set by commands.
 */
final class WorldState {
    float evalTimer;
    float afkTimer;
    /** The in bed count the last progress line was sent for. */
    int lastAnnounced;
    /** A manual skip is moving the clock towards morning. */
    boolean manualSkipping;
    /** Dynamic mode is speeding the clock up. */
    boolean dynamicActive;
    Instant dynamicTarget;
    /** Manual mode only. When a vanilla slumber was first seen and how many were in bed then. */
    Instant vanillaSlumberStart;
    int vanillaSlumberSleepers;
    /** Native mode only. The running slumber was started by the Lullaby threshold, not by everyone sleeping. */
    boolean lullabySlumber;

    volatile boolean forceSkip;
    volatile long lastSkipAt;
    volatile SleepSnapshot lastSnapshot;
}
