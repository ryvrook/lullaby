package dev.ryvrook.lullaby.config;

import com.hypixel.hytale.codec.ExtraInfo;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LullabyConfigTest {

    static LullabyConfig decode(String json) {
        return LullabyConfig.CODEC.decode(BsonDocument.parse(json), new ExtraInfo());
    }

    @Test
    void anEmptyFileKeepsEveryDefault() {
        LullabyConfig c = decode("{}");
        assertEquals("auto", c.mode());
        assertEquals("percent", c.requirement());
        assertEquals(50, c.percent());
        assertEquals(1, c.count());
        assertEquals(1, c.minimumSleepers());
        assertTrue(c.excludeCreative());
        assertEquals(300, c.afkSeconds());
        assertEquals(4, c.skipSeconds());
        assertEquals(60, c.cooldownSeconds());
        assertTrue(c.progressMessages());
        assertTrue(c.showToasts());
        assertFalse(c.showTitle());
        assertTrue(c.clearStorms());
        assertEquals(List.of("Storm", "Rain", "Snow_Heavy"), List.of(c.stormWeathers()));
        assertFalse(c.allowDynamic());
        assertEquals(20, c.dynamicMaxSpeed());
        assertTrue(c.worlds().isEmpty());
    }

    @Test
    void keysAndWorldOverridesAreRead() {
        LullabyConfig c = decode("""
                {"Mode": "manual", "Requirement": "count", "Count": 3, "SkipSeconds": 0,
                 "StormWeathers": ["Thunder"],
                 "Worlds": {"lobby": {"Enabled": false}, "survival": {"Requirement": "percent", "Percent": 75}}}
                """);
        assertEquals("manual", c.mode());
        assertEquals("count", c.requirement());
        assertEquals(3, c.count());
        assertEquals(0, c.skipSeconds());
        assertEquals(List.of("Thunder"), List.of(c.stormWeathers()));
        assertEquals(Boolean.FALSE, c.worlds().get("lobby").enabled());
        assertNull(c.worlds().get("lobby").percent());
        assertEquals(75, c.worlds().get("survival").percent());
        assertEquals("percent", c.worlds().get("survival").requirement());
    }

    @Test
    void everyKeyIsWrittenOnSave() {
        BsonDocument written = LullabyConfig.CODEC.encode(new LullabyConfig(), new ExtraInfo()).asDocument();
        for (String key : List.of("Mode", "Requirement", "Percent", "Count", "MinimumSleepers", "ExcludeCreative",
                "AfkSeconds", "SkipSeconds", "CooldownSeconds", "ProgressMessages", "ShowToasts", "ShowTitle",
                "ClearStorms", "StormWeathers", "AllowDynamic", "DynamicMaxSpeed", "Worlds")) {
            assertTrue(written.containsKey(key), key);
        }
    }

    @Test
    void adminChangesSwitchTheRequirement() {
        LullabyConfig c = decode("{}");
        c.setCount(4);
        assertEquals("count", c.requirement());
        assertEquals(4, c.count());
        c.setPercent(30);
        assertEquals("percent", c.requirement());
        assertEquals(30, c.percent());
        c.setDynamic();
        assertEquals("dynamic", c.requirement());
    }

    @Test
    void worldRuleMatchesIgnoringCaseAndAddsMissingWorlds() {
        LullabyConfig c = decode("{\"Worlds\": {\"Lobby\": {\"Enabled\": false}}}");
        assertSame(c.worlds().get("Lobby"), c.worldRule("lobby"));
        WorldRule added = c.worldRule("arena");
        added.setEnabled(false);
        assertEquals(2, c.worlds().size());
        assertSame(added, c.worlds().get("arena"));
    }
}
