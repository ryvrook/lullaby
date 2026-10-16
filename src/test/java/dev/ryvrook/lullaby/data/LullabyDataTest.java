package dev.ryvrook.lullaby.data;

import com.hypixel.hytale.logger.HytaleLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LullabyDataTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @TempDir
    Path dir;

    @Test
    void ignoredPlayersAndNightsSurviveARestart() {
        LullabyData data = new LullabyData(dir, LOGGER);
        data.load();
        UUID id = UUID.randomUUID();
        assertTrue(data.toggleIgnored(id));
        assertEquals(1, data.countSkip("default"));
        assertEquals(2, data.countSkip("default"));
        assertEquals(1, data.countSkip("Nether"));
        data.save();

        LullabyData again = new LullabyData(dir, LOGGER);
        again.load();
        assertTrue(again.isIgnored(id));
        assertEquals(2, again.nightsSkipped("default"));
        assertEquals(1, again.nightsSkipped("Nether"));
        assertEquals(0, again.nightsSkipped("nether"));
        assertEquals(0, again.nightsSkipped(null));
        assertFalse(again.toggleIgnored(id));
        assertFalse(again.isIgnored(id));
        assertFalse(Files.exists(dir.resolve("stats.json.tmp")));
    }

    @Test
    void aBrokenFileIsKeptAsideAndStartsEmpty() throws Exception {
        Files.writeString(dir.resolve("stats.json"), "{ not json");
        Files.writeString(dir.resolve("ignored.json"), "{\"Ignored\": [\"not-a-uuid\"]}");
        LullabyData data = new LullabyData(dir, LOGGER);
        data.load();
        assertEquals(0, data.nightsSkipped("default"));
        assertTrue(data.ignored().isEmpty());
        assertTrue(Files.exists(dir.resolve("stats.json.broken")));
        data.save();
        assertTrue(Files.readString(dir.resolve("stats.json")).contains("NightsSkipped"));
    }

    @Test
    void nightsAddUpAcrossWorlds() {
        LullabyData data = new LullabyData(dir, LOGGER);
        data.load();
        assertEquals(0, data.nightsSkippedTotal());
        data.countSkip("default");
        data.countSkip("default");
        data.countSkip("Arena");
        assertEquals(3, data.nightsSkippedTotal());
        assertEquals(java.util.List.of("Arena", "default"), java.util.List.copyOf(data.nightsByWorld().keySet()));
        data.nightsByWorld().clear();
        assertEquals(2, data.nightsSkipped("default"));
    }
}
