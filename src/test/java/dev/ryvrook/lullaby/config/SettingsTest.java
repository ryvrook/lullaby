package dev.ryvrook.lullaby.config;

import dev.ryvrook.lullaby.model.Mode;
import dev.ryvrook.lullaby.model.Requirement;
import dev.ryvrook.lullaby.model.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.ryvrook.lullaby.config.LullabyConfigTest.decode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void defaultsMatchTheReadme() {
        Settings s = Settings.from(decode("{}"));
        assertEquals(Mode.AUTO, s.mode());
        assertEquals(new Rule(true, Requirement.PERCENT, 50, 1), s.defaultRule());
        assertEquals("50% of players needed", s.defaultRule().describe());
        assertEquals(List.of("storm", "rain", "snow_heavy"), s.stormWeathers());
        assertFalse(s.dynamicIgnored());
    }

    @Test
    void valuesOutOfRangeArePulledBackIn() {
        Settings s = Settings.from(decode("""
                {"Percent": 140, "Count": 0, "MinimumSleepers": -2, "AfkSeconds": -5, "SkipSeconds": 600,
                 "CooldownSeconds": -1, "DynamicMaxSpeed": 900}
                """));
        assertEquals(100, s.defaultRule().percent());
        assertEquals(1, s.defaultRule().count());
        assertEquals(1, s.minimumSleepers());
        assertEquals(0, s.afkSeconds());
        assertEquals(Settings.MAX_SKIP_SECONDS, s.skipSeconds());
        assertEquals(0, s.cooldownSeconds());
        assertEquals(200, s.dynamicMaxSpeed());
        assertEquals(1, Settings.from(decode("{\"DynamicMaxSpeed\": 0}")).dynamicMaxSpeed());
        assertEquals(0, Settings.from(decode("{\"Percent\": -10}")).defaultRule().percent());
    }

    @Test
    void unknownWordsFallBackToAutoAndPercent() {
        Settings s = Settings.from(decode("{\"Mode\": \"turbo\", \"Requirement\": \"most\"}"));
        assertEquals(Mode.AUTO, s.mode());
        assertEquals(Requirement.PERCENT, s.defaultRule().requirement());
    }

    @Test
    void dynamicIsReadAsPercentUntilAllowed() {
        Settings off = Settings.from(decode("{\"Requirement\": \"dynamic\"}"));
        assertEquals(Requirement.PERCENT, off.defaultRule().requirement());
        assertTrue(off.dynamicIgnored());

        Settings worldOnly = Settings.from(decode("{\"Worlds\": {\"w\": {\"Requirement\": \"dynamic\"}}}"));
        assertEquals(Requirement.PERCENT, worldOnly.ruleFor("w").requirement());
        assertTrue(worldOnly.dynamicIgnored());

        Settings on = Settings.from(decode("{\"Requirement\": \"dynamic\", \"AllowDynamic\": true}"));
        assertEquals(Requirement.DYNAMIC, on.defaultRule().requirement());
        assertFalse(on.dynamicIgnored());
    }

    @Test
    void worldRulesInheritMissingKeysAndMatchIgnoringCase() {
        Settings s = Settings.from(decode("""
                {"Requirement": "count", "Count": 3, "Percent": 40,
                 "Worlds": {"Lobby": {"Enabled": false}, "Arena": {"Requirement": "percent", "Percent": 120}}}
                """));
        Rule lobby = s.ruleFor("lobby");
        assertFalse(lobby.enabled());
        assertEquals(Requirement.COUNT, lobby.requirement());
        assertEquals(3, lobby.count());
        Rule arena = s.ruleFor("ARENA");
        assertTrue(arena.enabled());
        assertEquals(Requirement.PERCENT, arena.requirement());
        assertEquals(100, arena.percent());
        assertSame(s.defaultRule(), s.ruleFor("default"));
        assertSame(s.defaultRule(), s.ruleFor(null));
    }

    @Test
    void blankStormWordsAreDropped() {
        Settings s = Settings.from(decode("{\"StormWeathers\": [\" Thunder \", \"\", \"  \"]}"));
        assertEquals(List.of("thunder"), s.stormWeathers());
    }
}
