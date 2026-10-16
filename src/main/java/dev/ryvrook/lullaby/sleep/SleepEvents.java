package dev.ryvrook.lullaby.sleep;

import dev.ryvrook.lullaby.model.SleepSnapshot;

/**
 * Where the coordinator reports skipped nights and progress. The plugin sends them to the engine event bus.
 * Both run on the world's thread and must return quickly.
 */
public interface SleepEvents {
    String NATIVE = "native";
    String MANUAL = "manual";
    String DYNAMIC = "dynamic";
    String VANILLA = "vanilla";
    String ADMIN = "admin";

    /** Reports nothing, for tests and for a coordinator without a bus. */
    SleepEvents NONE = new SleepEvents() {
        @Override
        public void progress(String world, String requirement, SleepSnapshot snap, double progress) {
        }

        @Override
        public void skipped(String world, int sleepers, int nightsSkipped, String cause) {
        }
    };

    /** The in bed count of a world changed. */
    void progress(String world, String requirement, SleepSnapshot snap, double progress);

    /** A night was counted as skipped. {@code cause} is one of the constants above. */
    void skipped(String world, int sleepers, int nightsSkipped, String cause);
}
