package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MissionField;
import de.lardtom.camdrones.planner.model.MissionTemplates;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorStateTest {
    private static EditorState editor() {
        return new EditorState(TabletState.EMPTY, FlightClass.TOUR, 0.0, 64.0, 0.0, false);
    }

    private static EditorState withWaypoints(int count) {
        EditorState editor = editor();
        for (int i = 0; i < count; i++) {
            assertTrue(editor.addWaypointAt(i * 10.0, 74.0, 0.0));
        }
        return editor;
    }

    @Test
    void opensWithOneEmptyMissionAndIsClean() {
        EditorState editor = editor();
        assertEquals(1, editor.missionCount());
        assertEquals("Mission 1", editor.missionName());
        assertFalse(editor.dirty());
        assertEquals(EditorState.Kind.NONE, editor.selection().kind());
    }

    @Test
    void addInsertsAfterSelectionAndSelectsTheNewPoint() {
        EditorState editor = withWaypoints(3);
        editor.select(EditorState.Selection.waypoint(0));
        assertTrue(editor.addWaypointAt(5.0, 80.0, 5.0));
        assertEquals(EditorState.Selection.waypoint(1), editor.selection());
        assertEquals(5.0, editor.mission().waypoint(1).x());
        assertEquals(4, editor.mission().size());
        assertTrue(editor.dirty());
        editor.markSaved();
        assertFalse(editor.dirty());
    }

    @Test
    void addStopsAtFiftyWithStatus() {
        EditorState editor = withWaypoints(MissionLimits.MAX_WAYPOINTS);
        assertFalse(editor.addWaypointAt(1.0, 70.0, 1.0));
        assertTrue(editor.status().orElseThrow().error());
        assertEquals("camdrones.planner.status.waypoints_full", editor.status().orElseThrow().text().key());
    }

    @Test
    void nextGuessContinuesTheCourse() {
        EditorState editor = withWaypoints(2);
        Waypoint guess = editor.nextWaypointGuess(64.0);
        assertEquals(20.0, guess.x(), 1.0E-9);
        assertEquals(0.0, guess.z(), 1.0E-9);
        assertEquals(74.0, guess.y(), 1.0E-9);
        EditorState empty = editor();
        assertEquals(64.0 + TabletState.DEFAULT_GROUND_HEIGHT, empty.nextWaypointGuess(64.0).y(), 1.0E-9);
    }

    @Test
    void reorderMoveAndDelete() {
        EditorState editor = withWaypoints(3);
        editor.select(EditorState.Selection.waypoint(2));
        assertTrue(editor.moveSelected(-1));
        assertEquals(EditorState.Selection.waypoint(1), editor.selection());
        assertEquals(20.0, editor.mission().waypoint(1).x());
        editor.select(EditorState.Selection.waypoint(0));
        assertFalse(editor.moveSelected(-1));
        assertTrue(editor.moveWaypoint(0, 100.0, -50.0));
        assertEquals(100.0, editor.mission().waypoint(0).x());
        assertEquals(74.0, editor.mission().waypoint(0).y());
        editor.select(EditorState.Selection.waypoint(2));
        assertTrue(editor.deleteSelected());
        assertEquals(2, editor.mission().size());
        assertEquals(EditorState.Selection.waypoint(1), editor.selection());
    }

    @Test
    void waypointFieldsStepWithinLimits() {
        EditorState editor = withWaypoints(1);
        editor.select(EditorState.Selection.waypoint(0));
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, false));
        assertEquals(75.0, editor.mission().waypoint(0).y(), 1.0E-9);
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, -2, true));
        assertEquals(65.0, editor.mission().waypoint(0).y(), 1.0E-9);
        assertTrue(editor.stepWaypoint(WaypointField.SPEED, 1, false));
        assertEquals(5, editor.mission().waypoint(0).speedDmS());
        assertTrue(editor.stepWaypoint(WaypointField.SPEED, -1, false));
        assertEquals(0, editor.mission().waypoint(0).speedDmS());
        assertFalse(editor.stepWaypoint(WaypointField.SPEED, -1, false));
        for (int i = 0; i < 100; i++) {
            editor.stepWaypoint(WaypointField.SPEED, 1, true);
        }
        assertEquals(MissionLimits.MAX_SPEED_DM_S, editor.mission().waypoint(0).speedDmS());
        editor.stepWaypoint(WaypointField.WAIT, 3, false);
        assertEquals(30, editor.mission().waypoint(0).waitDs());
        for (int i = 0; i < 30; i++) {
            editor.stepWaypoint(WaypointField.TILT, 1, true);
        }
        assertEquals(30, editor.mission().waypoint(0).tiltDeg(), "Tour-Gimbal bis +30°");
        for (int i = 0; i < 30; i++) {
            editor.stepWaypoint(WaypointField.TILT, -1, true);
        }
        assertEquals(MissionLimits.MIN_TILT, editor.mission().waypoint(0).tiltDeg());
        assertFalse(editor.stepWaypoint(WaypointField.YAW, 1, false), "Richtung nur bei festem Blick");
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        assertEquals(Waypoint.Heading.FIXED, editor.mission().waypoint(0).heading());
        assertTrue(editor.stepWaypoint(WaypointField.YAW, -1, false));
        assertEquals(345, editor.mission().waypoint(0).yawDeg());
        editor.stepWaypoint(WaypointField.ACTION, -1, false);
        assertEquals(Waypoint.Action.RECORD_STOP, editor.mission().waypoint(0).action());
    }

    @Test
    void headingCyclesThroughPointsOfInterest() {
        EditorState editor = withWaypoints(1);
        assertTrue(editor.addPoi(50.0, 64.0, 50.0));
        assertTrue(editor.addPoi(-50.0, 64.0, 50.0));
        editor.select(EditorState.Selection.waypoint(0));
        Waypoint.Heading[] expected = {Waypoint.Heading.FIXED, Waypoint.Heading.POI, Waypoint.Heading.POI, Waypoint.Heading.FREE,
                Waypoint.Heading.COURSE};
        int[] pois = {0, 0, 1, 0, 0};
        for (int i = 0; i < expected.length; i++) {
            editor.stepWaypoint(WaypointField.HEADING, 1, false);
            Waypoint w = editor.mission().waypoint(0);
            assertEquals(expected[i], w.heading());
            if (w.heading() == Waypoint.Heading.POI) {
                assertEquals(pois[i], w.poi());
            }
        }
    }

    @Test
    void deletingPoiFixesWaypointsLookingAtIt() {
        EditorState editor = withWaypoints(3);
        editor.addPoi(0, 64, 0);
        editor.addPoi(10, 64, 10);
        Mission mission = editor.mission();
        editor.select(EditorState.Selection.waypoint(0));
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        assertEquals(Waypoint.Heading.POI, editor.mission().waypoint(0).heading());
        editor.select(EditorState.Selection.waypoint(1));
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        assertEquals(1, editor.mission().waypoint(1).poi());
        assertTrue(editor.deletePoi(0));
        assertEquals(1, editor.mission().pointsOfInterest().size());
        assertEquals(Waypoint.Heading.COURSE, editor.mission().waypoint(0).heading());
        assertEquals(Waypoint.Heading.POI, editor.mission().waypoint(1).heading());
        assertEquals(0, editor.mission().waypoint(1).poi());
        assertEquals(mission.waypoint(2), editor.mission().waypoint(2));
    }

    @Test
    void pointOfInterestLimitAndRadius() {
        EditorState editor = editor();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            assertTrue(editor.addPoi(i, 64, i));
        }
        assertFalse(editor.addPoi(9, 64, 9));
        editor.select(EditorState.Selection.poi(0));
        assertTrue(editor.stepPoiRadius(1, true));
        assertEquals(EditorState.DEFAULT_POI_RADIUS + 5.0, editor.selectedPoi().orElseThrow().radius(), 1.0E-9);
        assertTrue(editor.stepPoiHeight(-1, false));
        assertEquals(63.0, editor.selectedPoi().orElseThrow().y(), 1.0E-9);
        assertTrue(editor.movePoi(0, 30, 40));
        PointOfInterest moved = editor.mission().pointsOfInterest().getFirst();
        assertEquals(30.0, moved.x(), 1.0E-9);
        assertEquals(40.0, moved.z(), 1.0E-9);
    }

    @Test
    void missionFieldsAndRows() {
        EditorState editor = withWaypoints(2);
        editor.addPoi(5, 64, 5);
        assertEquals(3, editor.rowCount());
        assertEquals(EditorState.Selection.poi(0), editor.row(2));
        assertEquals(2, editor.rowOf(EditorState.Selection.poi(0)));
        editor.stepMissionField(MissionField.END, 1, false);
        assertEquals(Mission.End.START, editor.mission().end());
        editor.stepMissionField(MissionField.END, 1, false);
        editor.stepMissionField(MissionField.END, 1, false);
        assertEquals(MissionField.Ending.LOOP, MissionField.Ending.of(editor.mission()));
        assertTrue(editor.mission().loop());
        editor.stepMissionField(MissionField.END, 1, false);
        assertFalse(editor.mission().loop());
        assertEquals(Mission.End.HOVER, editor.mission().end());
        editor.stepMissionField(MissionField.CURVES, 1, false);
        assertTrue(editor.mission().smooth());
        editor.stepMissionField(MissionField.OBSTACLE, 1, false);
        assertEquals(ObstacleGuard.Mode.CLIMB_OVER, editor.mission().obstacle());
        editor.stepMissionField(MissionField.BASE_SPEED, -100, false);
        assertEquals(MissionField.SPEED_STEP_DM_S, editor.mission().baseSpeedDmS());
        assertTrue(editor.stepGroundHeight(5));
        assertEquals(TabletState.DEFAULT_GROUND_HEIGHT + 5, editor.tablet().groundHeight());
    }

    @Test
    void missionManagementAndRename() {
        EditorState editor = withWaypoints(2);
        assertTrue(editor.rename("  Burg  Nord "));
        assertEquals("Burg Nord", editor.missionName());
        assertFalse(editor.rename("Burg Nord"));
        assertTrue(editor.addMission());
        assertEquals(2, editor.missionCount());
        assertEquals(1, editor.missionIndex());
        assertTrue(editor.mission().isEmpty());
        assertTrue(editor.stepMission(1));
        assertEquals(0, editor.missionIndex());
        assertEquals(EditorState.Selection.waypoint(0), editor.selection());
        assertTrue(editor.duplicateMission());
        assertEquals(3, editor.missionCount());
        assertEquals("Burg Nord 2", editor.missionName());
        assertTrue(editor.deleteMission());
        assertEquals(2, editor.missionCount());
        for (int i = 0; i < 10; i++) {
            editor.addMission();
        }
        assertEquals(TabletState.MAX_MISSIONS, editor.missionCount());
        assertEquals("camdrones.planner.status.missions_full", editor.status().orElseThrow().text().key());
    }

    @Test
    void heightIsShownAboveTheStartPointLikeRealPlanners() {
        Waypoint w = Waypoint.at(0.0, 74.0, 0.0);
        assertEquals(FieldText.of("value.height", "10"), WaypointField.HEIGHT.value(w, ',', 64.0));
        assertEquals(FieldText.of("value.height", "-5"), WaypointField.HEIGHT.value(Waypoint.at(0.0, -65.0, 0.0), ',', -60.0));
        assertEquals(FieldText.of("value.height", "12,5"), WaypointField.HEIGHT.value(Waypoint.at(0.0, 76.5, 0.0), ',', 64.0));
        assertEquals(FieldText.of("value.height", "0"), WaypointField.HEIGHT.value(Waypoint.at(0.0, 63.95, 0.0), ',', 64.0));
        assertEquals(FieldText.of("value.height", "20"), EditorCatalog.poiHeight(84.0 - 64.0, ','));
        Waypoint stepped = WaypointField.HEIGHT.step(w, 2, false, 0, FlightClass.TOUR);
        assertEquals(FieldText.of("value.height", "12"), WaypointField.HEIGHT.value(stepped, ',', 64.0), "Schritte bleiben 1 m");
        assertEquals(76.0, stepped.y(), 1.0E-9, "gespeichert wird weiter die Welthöhe");
    }

    @Test
    void templatesAppendAndSelectTheFirstNewPoint() {
        EditorState editor = withWaypoints(1);
        MissionTemplates.Anchor anchor = editor.anchor(64.0, 0.0F);
        assertTrue(editor.applyTemplate(MissionTemplates.Template.CIRCLE, anchor));
        assertEquals(1 + MissionTemplates.CIRCLE_POINTS, editor.mission().size());
        assertEquals(EditorState.Selection.waypoint(1), editor.selection());
        assertEquals(1, editor.mission().pointsOfInterest().size());
        assertFalse(editor.templateMenu());
    }

    @Test
    void autoShotAndTemplateMenusCloseEachOther() {
        EditorState editor = withWaypoints(2);
        assertFalse(editor.autoMenu());
        editor.autoMenu(true);
        assertTrue(editor.autoMenu());
        editor.templateMenu(true);
        assertTrue(editor.templateMenu());
        assertFalse(editor.autoMenu());
        editor.autoMenu(true);
        assertFalse(editor.templateMenu());
        editor.select(EditorState.Selection.waypoint(1));
        assertFalse(editor.autoMenu());
        editor.autoMenu(true);
        editor.addMission();
        assertFalse(editor.autoMenu());
    }

    @Test
    void previewGrowsWithTheMission() {
        EditorState editor = editor();
        assertEquals(0.0, editor.preview().length());
        editor.addWaypointAt(0, 74, 0);
        editor.addWaypointAt(100, 74, 0);
        FlightTimeEstimate.Preview preview = editor.preview();
        assertTrue(preview.length() > 100.0, "Länge " + preview.length());
        assertTrue(preview.seconds() > 10.0);
        assertTrue(preview.charge() > 0.0 && preview.charge() < 0.2);
    }
}
