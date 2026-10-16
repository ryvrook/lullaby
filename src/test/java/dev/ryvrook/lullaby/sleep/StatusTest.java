package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.codec.ExtraInfo;
import dev.ryvrook.lullaby.api.SleepStatus;
import dev.ryvrook.lullaby.config.LullabyConfig;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.model.SleepSnapshot;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTest {
    private static Settings settings(String json) {
        return Settings.from(LullabyConfig.CODEC.decode(BsonDocument.parse(json), new ExtraInfo()));
    }

    @Test
    void aCountedWorldReportsItsSleepersAndProgress() {
        SleepSnapshot snap = new SleepSnapshot(4, 1, 1, 2, List.of(), 5);
        SleepStatus s = SleepCoordinator.status("default", settings("{}"), snap, 0, 7, 1_000_000);
        assertEquals("default", s.world());
        assertTrue(s.enabled());
        assertEquals("percent", s.requirement());
        assertEquals(5, s.online());
        assertEquals(4, s.eligible());
        assertEquals(1, s.inBed());
        assertEquals(2, s.needed());
        assertEquals(0.5, s.progress());
        assertEquals(0, s.cooldownSeconds());
        assertEquals(7, s.nightsSkipped());
    }

    @Test
    void aWorldNeverCountedGivesZeros() {
        SleepStatus s = SleepCoordinator.status("Lobby", settings("{\"Worlds\": {\"lobby\": {\"Enabled\": false}}}"),
                null, 0, 0, 1_000_000);
        assertFalse(s.enabled());
        assertEquals(0, s.inBed());
        assertEquals(1, s.needed());
        assertEquals(0.0, s.progress());
    }

    @Test
    void theCooldownCountsDownAfterASkip() {
        SleepStatus s = SleepCoordinator.status("default", settings("{\"CooldownSeconds\": 60}"), null, 100_000, 1, 110_500);
        assertEquals(50, s.cooldownSeconds());
    }

    @Test
    void aDynamicWorldMeasuresProgressAgainstEveryone() {
        SleepSnapshot snap = new SleepSnapshot(4, 1, 0, 1, List.of(), 4);
        SleepStatus s = SleepCoordinator.status("default",
                settings("{\"Requirement\": \"dynamic\", \"AllowDynamic\": true}"), snap, 0, 0, 1_000_000);
        assertEquals("dynamic", s.requirement());
        assertEquals(0.25, s.progress());
    }

    @Test
    void progressIsSentOnlyWhenTheInBedCountMoves() {
        assertFalse(SleepCoordinator.progressChanged(0, 0));
        assertTrue(SleepCoordinator.progressChanged(0, 1));
        assertTrue(SleepCoordinator.progressChanged(2, 0));
        assertFalse(SleepCoordinator.progressChanged(2, 2));
    }
}
