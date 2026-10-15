package dev.ryvrook.lullaby.weather;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormClearerTest {
    private static final List<String> WORDS = List.of("storm", "rain", "snow_heavy");

    @Test
    void aWeatherIsStormyWhenItsIdContainsAWord() {
        assertTrue(StormClearer.isStorm("Zone1_Storm", WORDS));
        assertTrue(StormClearer.isStorm("Zone3_Snow_Heavy", WORDS));
        assertTrue(StormClearer.isStorm("RAIN_LIGHT", WORDS));
    }

    @Test
    void calmWeatherIsLeftAlone() {
        assertFalse(StormClearer.isStorm("Zone1_Sunny", WORDS));
        assertFalse(StormClearer.isStorm("Zone3_Snow", WORDS));
        assertFalse(StormClearer.isStorm(null, WORDS));
        assertFalse(StormClearer.isStorm("Zone1_Storm", List.of()));
    }
}
