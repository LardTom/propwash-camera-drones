package de.lardtom.camdrones.client.record;

import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.client.record.model.RecordingRoute;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingModeTest {
    @Test
    void defaultIsFlashbackOnlyWhenFlashbackIsInstalled() {
        assertEquals(RecordingMode.FLASHBACK, RecordingMode.defaultFor(true));
        assertEquals(RecordingMode.DIRECT, RecordingMode.defaultFor(false));
    }

    @Test
    void flashbackCannotBeChosenWithoutFlashback() {
        assertTrue(RecordingMode.FLASHBACK.selectable(true));
        assertFalse(RecordingMode.FLASHBACK.selectable(false));
        assertTrue(RecordingMode.DIRECT.selectable(false));
        assertTrue(RecordingMode.DIRECT.selectable(true));
    }

    @Test
    void flashbackFallsBackToDirectWhenFlashbackIsNotReady() {
        assertEquals(RecordingMode.FLASHBACK, RecordingMode.FLASHBACK.effective(true));
        assertEquals(RecordingMode.DIRECT, RecordingMode.FLASHBACK.effective(false));
        assertEquals(RecordingMode.DIRECT, RecordingMode.DIRECT.effective(true));
    }

    @Test
    void keysRoundTripAndUnknownKeysAreEmpty() {
        for (RecordingMode mode : RecordingMode.values()) {
            assertEquals(Optional.of(mode), RecordingMode.byKey(mode.key()));
            assertEquals(Optional.of(mode), RecordingMode.byKey(" " + mode.key().toUpperCase() + " "));
            assertTrue(mode.translationKey().startsWith("camdrones.record.mode."));
        }
        assertEquals(Optional.empty(), RecordingMode.byKey("video"));
        assertEquals(Optional.empty(), RecordingMode.byKey(null));
    }

    @Test
    void flashbackRecordsInTheBackgroundDirectOnlyInLiveView() {
        assertEquals(RecordingRoute.FLASHBACK, RecordingRoute.of(RecordingMode.FLASHBACK, true, false));
        assertEquals(RecordingRoute.FLASHBACK, RecordingRoute.of(RecordingMode.FLASHBACK, true, true));
        assertEquals(RecordingRoute.DIRECT, RecordingRoute.of(RecordingMode.DIRECT, true, true));
        assertEquals(RecordingRoute.NEEDS_LIVE_VIEW, RecordingRoute.of(RecordingMode.DIRECT, true, false));
        assertEquals(RecordingRoute.DIRECT, RecordingRoute.of(RecordingMode.FLASHBACK, false, true));
        assertEquals(RecordingRoute.NEEDS_LIVE_VIEW, RecordingRoute.of(RecordingMode.FLASHBACK, false, false));
        assertFalse(RecordingRoute.NEEDS_LIVE_VIEW.records());
        assertTrue(RecordingRoute.FLASHBACK.records());
        assertTrue(RecordingRoute.DIRECT.records());
    }

    @Test
    void onlyMissionsWithCameraActionsFilm() {
        Mission plain = Mission.of(List.of(Waypoint.at(0, 70, 0), Waypoint.at(10, 70, 0)));
        assertFalse(RecordingRoute.films(plain));
        assertFalse(RecordingRoute.films((Mission) null));
        for (Waypoint.Action action : List.of(Waypoint.Action.PHOTO, Waypoint.Action.RECORD_START, Waypoint.Action.RECORD_STOP)) {
            Mission filming = Mission.of(List.of(Waypoint.at(0, 70, 0), Waypoint.at(10, 70, 0).withAction(action)));
            assertTrue(RecordingRoute.films(filming), action.name());
            assertTrue(RecordingRoute.films(action));
        }
        assertFalse(RecordingRoute.films(Waypoint.Action.NONE));
    }
}
