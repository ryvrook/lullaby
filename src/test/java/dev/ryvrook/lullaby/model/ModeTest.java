package dev.ryvrook.lullaby.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeTest {

    @Test
    void modeParsesIgnoringCaseAndSpaces() {
        assertEquals(Mode.NATIVE, Mode.parse("Native"));
        assertEquals(Mode.MANUAL, Mode.parse(" manual "));
        assertEquals(Mode.AUTO, Mode.parse("AUTO"));
    }

    @Test
    void anUnknownOrEmptyModeIsAuto() {
        assertEquals(Mode.AUTO, Mode.parse(null));
        assertEquals(Mode.AUTO, Mode.parse(""));
        assertEquals(Mode.AUTO, Mode.parse("vanilla"));
    }

    @Test
    void onlyManualSkipsTheSystemSwap() {
        assertTrue(Mode.AUTO.wantsNative());
        assertTrue(Mode.NATIVE.wantsNative());
        assertFalse(Mode.MANUAL.wantsNative());
    }

    @Test
    void requirementFallsBackWhenUnknown() {
        assertEquals(Requirement.COUNT, Requirement.parse("Count", Requirement.PERCENT));
        assertEquals(Requirement.DYNAMIC, Requirement.parse("dynamic", Requirement.PERCENT));
        assertEquals(Requirement.PERCENT, Requirement.parse("half", Requirement.PERCENT));
        assertEquals(Requirement.COUNT, Requirement.parse(null, Requirement.COUNT));
        assertEquals("count", Requirement.COUNT.id());
    }
}
