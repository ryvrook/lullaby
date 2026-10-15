package dev.ryvrook.lullaby.sleep;

import dev.ryvrook.lullaby.config.LullabyConfig;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.model.Requirement;
import dev.ryvrook.lullaby.model.Rule;
import dev.ryvrook.lullaby.model.SleepSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressTextTest {
    private static final Settings SETTINGS = Settings.from(new LullabyConfig());
    private static final Rule PERCENT = new Rule(true, Requirement.PERCENT, 50, 1);
    private static final Rule DYNAMIC = new Rule(true, Requirement.DYNAMIC, 50, 1);

    private static SleepSnapshot snap(int eligible, int inBed, int needed) {
        return new SleepSnapshot(eligible, inBed, 0, needed, List.of(), eligible);
    }

    @Test
    void namesHowManyMoreAreNeeded() {
        assertEquals("2 of 5 players sleeping, 1 more needed", Announcer.progressText(snap(5, 2, 3), PERCENT, SETTINGS));
        assertEquals("3 of 5 players sleeping, the night will pass",
                Announcer.progressText(snap(5, 3, 3), PERCENT, SETTINGS));
        assertEquals("1 of 1 player sleeping, the night will pass",
                Announcer.progressText(snap(1, 1, 1), PERCENT, SETTINGS));
    }

    @Test
    void dynamicNamesTheSpeedTheSleepersWillGive() {
        assertEquals("1 of 10 players sleeping, time runs 3x faster",
                Announcer.progressText(snap(10, 1, 1), DYNAMIC, SETTINGS));
        assertEquals("0 of 10 players sleeping, time will speed up in a moment",
                Announcer.progressText(snap(10, 0, 1), DYNAMIC, SETTINGS));
    }
}
