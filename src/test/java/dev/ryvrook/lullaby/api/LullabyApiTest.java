package dev.ryvrook.lullaby.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LullabyApiTest {
    private final List<String> seen = new ArrayList<>();
    private final Consumer<LullabyApi> enable = api -> seen.add("enable " + api.version());
    private final Runnable disable = () -> seen.add("disable");
    private Fake running;

    @AfterEach
    void cleanUp() {
        LullabyApi.removeHooks(enable, disable);
        if (running != null) {
            LullabyApi.disable(running);
        }
    }

    @Test
    void aHookAddedWhileLullabyRunsFiresAtOnce() {
        running = new Fake("1.1.0");
        LullabyApi.enable(running);
        LullabyApi.onEnable(enable);
        assertEquals(List.of("enable 1.1.0"), seen);
        assertSame(running, LullabyApi.get());
        assertTrue(LullabyApi.isPresent());
    }

    @Test
    void hooksFireAgainAfterEveryStart() {
        LullabyApi.onEnable(enable);
        LullabyApi.onDisable(disable);
        assertTrue(seen.isEmpty());
        Fake first = new Fake("a");
        LullabyApi.enable(first);
        LullabyApi.disable(first);
        running = new Fake("b");
        LullabyApi.enable(running);
        assertEquals(List.of("enable a", "disable", "enable b"), seen);
    }

    @Test
    void anOldInstanceStoppingLeavesTheNewOneAlone() {
        LullabyApi.onDisable(disable);
        Fake old = new Fake("old");
        running = new Fake("new");
        LullabyApi.enable(old);
        LullabyApi.enable(running);
        LullabyApi.disable(old);
        assertSame(running, LullabyApi.get());
        assertTrue(seen.isEmpty());
    }

    @Test
    void aFailingHookDoesNotStopTheOthers() {
        Consumer<LullabyApi> broken = api -> {
            throw new IllegalStateException("broken on purpose");
        };
        LullabyApi.onEnable(broken);
        LullabyApi.onEnable(enable);
        running = new Fake("x");
        LullabyApi.enable(running);
        LullabyApi.removeHooks(broken, null);
        assertEquals(List.of("enable x"), seen);
    }

    @Test
    void removedHooksStayQuiet() {
        LullabyApi.onEnable(enable);
        LullabyApi.onDisable(disable);
        LullabyApi.removeHooks(enable, disable);
        running = new Fake("y");
        LullabyApi.enable(running);
        LullabyApi.disable(running);
        running = null;
        assertTrue(seen.isEmpty());
        assertNull(LullabyApi.get());
        assertFalse(LullabyApi.isPresent());
    }

    @Test
    void theFirstContractIsVersionOne() {
        assertEquals(1, LullabyApi.API_VERSION);
    }

    @Test
    void eventsCarryPlainValues() {
        NightSkippedEvent skipped = new NightSkippedEvent("default", 2, 14, "native");
        assertEquals("default", skipped.world());
        assertEquals(2, skipped.sleepers());
        assertEquals(14, skipped.nightsSkipped());
        assertEquals("native", skipped.cause());
        SleepProgressEvent progress = new SleepProgressEvent("default", "percent", 5, 4, 1, 0, 2, 0.5);
        assertEquals(1, progress.missing());
        assertEquals(0.5, progress.progress());
        assertEquals("SleepProgressEvent{default 1 of 2 needed, 4 eligible}", progress.toString());
        assertEquals(0, new SleepStatus("w", true, "count", 3, 3, 3, 3, 2, 1.0, 0, 0).missing());
    }

    private static final class Fake extends LullabyApi {
        private final String version;

        Fake(String version) {
            this.version = version;
        }

        @Override
        public int apiVersion() {
            return API_VERSION;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String mode() {
            return "manual";
        }

        @Override
        public List<String> worlds() {
            return List.of();
        }

        @Override
        public SleepStatus status(String world) {
            return null;
        }

        @Override
        public List<SleepStatus> statuses() {
            return List.of();
        }

        @Override
        public int sleepers(String world) {
            return 0;
        }

        @Override
        public int needed(String world) {
            return 1;
        }

        @Override
        public double progress(String world) {
            return 0;
        }

        @Override
        public int nightsSkipped(String world) {
            return 0;
        }

        @Override
        public int nightsSkipped() {
            return 0;
        }

        @Override
        public Map<String, Integer> nightsSkippedByWorld() {
            return Map.of();
        }
    }
}
