package dev.ryvrook.lullaby;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DependentsTest {
    private static final String LULLABY = "ryvrook:lullaby";
    private static final String TRACKER = "ryvrook:trackerapi";
    private static final String RESTAPI = "ryvrook:restapi";
    private static final String SOCIAL = "ryvrook:socialbridge";

    @Test
    void aPluginComesBackAfterTheDependentItReads() {
        Map<String, Set<String>> deps = Map.of(SOCIAL, Set.of(TRACKER, LULLABY, RESTAPI), RESTAPI, Set.of(TRACKER, LULLABY));
        assertEquals(List.of(RESTAPI, SOCIAL), Dependents.order(List.of(SOCIAL, RESTAPI), deps));
    }

    @Test
    void aCycleStillReloadsEveryPluginOnce() {
        Map<String, Set<String>> cycle = Map.of("a:a", Set.of("b:b"), "b:b", Set.of("a:a"));
        assertEquals(List.of("b:b", "a:a"), Dependents.order(List.of("a:a", "b:b"), cycle));
    }

    @Test
    void lullabyTakesTheSuiteLock() {
        assertSame(Dependents.sharedLock(), System.getProperties().get("ryvrook.dependents.lock"));
    }
}
