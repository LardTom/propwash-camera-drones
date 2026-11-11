package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.ValueEntry;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiEditTest {
    private static final double START_Y = 64.0;

    private static EditorState editor(double... heights) {
        EditorState editor = new EditorState(TabletState.EMPTY, FlightClass.TOUR, 0.0, START_Y, 0.0, false);
        for (int i = 0; i < heights.length; i++) {
            assertTrue(editor.addWaypointAt(i * 10.0, START_Y + heights[i], 0.0));
        }
        return editor;
    }

    private static double height(EditorState editor, int index) {
        return editor.mission().waypoint(index).y() - START_Y;
    }

    private static List<Double> heights(EditorState editor) {
        return editor.mission().waypoints().stream().map(w -> w.y() - START_Y).toList();
    }

    private static int row(WaypointField field) {
        return field.ordinal();
    }

    private static EditorState firstThree() {
        EditorState editor = editor(6.0, 9.0, 13.0, 20.0);
        editor.select(EditorState.Selection.waypoint(0));
        assertTrue(editor.extendTo(EditorState.Selection.waypoint(2)));
        return editor;
    }

    @Test
    void ctrlClickTogglesSingleWaypoints() {
        EditorState editor = editor(6.0, 9.0, 13.0, 20.0, 25.0);
        editor.select(EditorState.Selection.waypoint(0));
        assertFalse(editor.multiple());
        assertTrue(editor.toggle(EditorState.Selection.waypoint(2)));
        assertEquals(List.of(0, 2), editor.selectedWaypoints());
        assertEquals(EditorState.Selection.waypoint(2), editor.selection(), "der zuletzt geklickte führt");
        assertTrue(editor.multiple());
        assertTrue(editor.selected(EditorState.Selection.waypoint(2)));
        assertFalse(editor.selected(EditorState.Selection.waypoint(1)));
        assertTrue(editor.toggle(EditorState.Selection.waypoint(2)));
        assertEquals(List.of(0), editor.selectedWaypoints());
        assertEquals(EditorState.Selection.waypoint(0), editor.selection());
        assertFalse(editor.toggle(EditorState.Selection.waypoint(0)), "einer bleibt immer gewählt");
        assertEquals(List.of(0), editor.selectedWaypoints());
        editor.select(EditorState.Selection.waypoint(4));
        assertEquals(List.of(4), editor.selectedWaypoints());
    }

    @Test
    void shiftClickSelectsTheRangeFromTheAnchor() {
        EditorState editor = editor(6.0, 9.0, 13.0, 20.0, 25.0);
        editor.select(EditorState.Selection.waypoint(1));
        assertTrue(editor.extendTo(EditorState.Selection.waypoint(3)));
        assertEquals(List.of(1, 2, 3), editor.selectedWaypoints());
        assertEquals(EditorState.Selection.waypoint(3), editor.selection());
        assertTrue(editor.extendTo(EditorState.Selection.waypoint(0)));
        assertEquals(List.of(0, 1), editor.selectedWaypoints(), "der Anker bleibt beim ersten Klick");
        editor.toggle(EditorState.Selection.waypoint(4));
        editor.extendTo(EditorState.Selection.waypoint(2));
        assertEquals(List.of(2, 3, 4), editor.selectedWaypoints(), "Strg/Cmd-Klick setzt den Anker neu");
    }

    @Test
    void selectAllTakesEveryWaypointAndPoiRowsStaySingle() {
        EditorState editor = editor(6.0, 9.0, 13.0);
        assertTrue(editor.addPoi(5.0, START_Y, 5.0));
        assertEquals(EditorState.Kind.POI, editor.selection().kind());
        assertTrue(editor.selectAllWaypoints());
        assertEquals(List.of(0, 1, 2), editor.selectedWaypoints());
        assertEquals(EditorState.Selection.waypoint(0), editor.selection());
        assertTrue(editor.toggle(EditorState.Selection.poi(0)));
        assertEquals(EditorState.Selection.poi(0), editor.selection());
        assertEquals(List.of(), editor.selectedWaypoints());
        assertTrue(editor.selected(EditorState.Selection.poi(0)));
        assertTrue(editor.extendTo(EditorState.Selection.waypoint(2)));
        assertEquals(List.of(2), editor.selectedWaypoints(), "Umschalt-Klick ohne Wegpunkt-Anker wählt nur den einen");
        EditorState empty = editor();
        assertFalse(empty.selectAllWaypoints());
    }

    @Test
    void mixedValuesShowAsDashAndEnabledMeansAnySelected() {
        EditorState editor = firstThree();
        assertTrue(editor.mixed(WaypointField.HEIGHT));
        assertFalse(editor.mixed(WaypointField.WAIT));
        assertFalse(editor.mixed(WaypointField.SPEED));
        assertFalse(editor.enabled(WaypointField.YAW));
        editor.select(EditorState.Selection.waypoint(1));
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        editor.select(EditorState.Selection.waypoint(0));
        editor.extendTo(EditorState.Selection.waypoint(2));
        assertTrue(editor.enabled(WaypointField.YAW), "Richtung, sobald einer der gewählten fest blickt");
        assertTrue(editor.mixed(WaypointField.HEADING));
        assertEquals("–", EditorTexts.MIXED);
    }

    @Test
    void stepSetsAllSelectedToOneValueByDefault() {
        EditorState editor = firstThree();
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, false, false));
        assertEquals(List.of(14.0, 14.0, 14.0, 20.0), heights(editor), "Wert von Wegpunkt 3 (zuletzt geklickt) plus 1 m für alle");
        assertFalse(editor.mixed(WaypointField.HEIGHT));
        assertTrue(editor.stepWaypoint(WaypointField.WAIT, 2, false, false));
        for (int i = 0; i < 3; i++) {
            assertEquals(20, editor.mission().waypoint(i).waitDs());
        }
        assertEquals(0, editor.mission().waypoint(3).waitDs());
        assertTrue(editor.stepWaypoint(WaypointField.ACTION, 1, false, false));
        for (int i = 0; i < 3; i++) {
            assertEquals(Waypoint.Action.values()[1], editor.mission().waypoint(i).action());
        }
        assertEquals(Waypoint.Action.NONE, editor.mission().waypoint(3).action());
    }

    @Test
    void altStepChangesEachWaypointByTheStep() {
        EditorState editor = firstThree();
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, true, true));
        assertEquals(List.of(11.0, 14.0, 18.0, 20.0), heights(editor), "Abstände bleiben");
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, -1, false, true));
        assertEquals(List.of(10.0, 13.0, 17.0, 20.0), heights(editor));
        EditorState high = editor(MissionLimits.MAX_Y_DM / 10.0 - START_Y - 2.0, 0.0);
        high.selectAllWaypoints();
        assertTrue(high.stepWaypoint(WaypointField.HEIGHT, 1, true, true));
        assertEquals(MissionLimits.MAX_Y_DM, high.mission().waypoint(0).yDm(), "jeder an seiner Grenze");
        assertEquals(5.0, height(high, 1), 1.0E-9);
    }

    @Test
    void singleSelectionStepsAsBefore() {
        EditorState editor = editor(6.0, 9.0);
        editor.select(EditorState.Selection.waypoint(1));
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, false));
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, false, true));
        assertEquals(List.of(6.0, 11.0), heights(editor));
    }

    @Test
    void typedValueAppliesToAllSelected() {
        EditorState editor = firstThree();
        assertEquals("", editor.entryText(row(WaypointField.HEIGHT), '.'), "gemischt: leeres Feld");
        assertEquals(ValueEntry.Status.OK, editor.commitEntry(row(WaypointField.HEIGHT), "12", '.'));
        assertEquals(List.of(12.0, 12.0, 12.0, 20.0), heights(editor));
        assertEquals("12", editor.entryText(row(WaypointField.HEIGHT), '.'));
        assertEquals(ValueEntry.Status.OK, editor.commitEntry(row(WaypointField.HEIGHT), "12,5", ','));
        assertEquals("12,5", editor.entryText(row(WaypointField.HEIGHT), ','));
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, true, true));
        assertEquals(List.of(17.5, 17.5, 17.5, 20.0), heights(editor));
    }

    @Test
    void entryKeepsLimitsLikeTheSteps() {
        EditorState editor = firstThree();
        int speed = row(WaypointField.SPEED);
        assertEquals(ValueEntry.Status.CLAMPED, editor.commitEntry(speed, "99", '.'));
        assertEquals(MissionLimits.MAX_SPEED_DM_S, editor.mission().waypoint(0).speedDmS());
        EditorState.Status status = editor.status().orElseThrow();
        assertTrue(status.error());
        assertEquals(FieldText.of("status.clamped", "20", "0", "20"), status.text());
        assertEquals(ValueEntry.Status.OK, editor.commitEntry(speed, "0", '.'));
        assertEquals(0, editor.mission().waypoint(1).speedDmS(), "0 heißt Grundtempo");
        assertEquals(ValueEntry.Status.INVALID, editor.commitEntry(speed, "4x", '.'));
        assertEquals(ValueEntry.Status.EMPTY, editor.commitEntry(speed, " ", '.'));
        assertEquals(0, editor.mission().waypoint(1).speedDmS());
        assertEquals(ValueEntry.Status.CLAMPED, editor.commitEntry(row(WaypointField.TILT), "80", '.'));
        assertEquals(30, editor.mission().waypoint(2).tiltDeg(), "Tour-Gimbal bis +30°");
        assertEquals(ValueEntry.Status.CLAMPED, editor.commitEntry(row(WaypointField.WAIT), "-3", '.'));
        assertEquals(0, editor.mission().waypoint(0).waitDs());
        assertEquals(ValueEntry.Status.OK, editor.commitEntry(row(WaypointField.WAIT), "2.5", '.'));
        assertEquals(25, editor.mission().waypoint(2).waitDs());
        assertEquals(ValueEntry.Status.CLAMPED, editor.commitEntry(row(WaypointField.HEIGHT), "99999", '.'));
        assertEquals(MissionLimits.MAX_Y_DM, editor.mission().waypoint(1).yDm());
    }

    @Test
    void onlyNumberFieldsOpenTheEntry() {
        EditorState editor = firstThree();
        assertTrue(editor.typeable(row(WaypointField.HEIGHT)));
        assertTrue(editor.typeable(row(WaypointField.WAIT)));
        assertFalse(editor.typeable(row(WaypointField.HEADING)));
        assertFalse(editor.typeable(row(WaypointField.ACTION)));
        assertFalse(editor.typeable(row(WaypointField.YAW)), "Richtung nur bei festem Blick");
        assertFalse(editor.typeable(row(WaypointField.EVERY)), "Takt nur im Takt");
        editor.stepWaypoint(WaypointField.HEADING, 1, false);
        assertTrue(editor.typeable(row(WaypointField.YAW)));
        assertEquals(ValueEntry.Status.OK, editor.commitEntry(row(WaypointField.YAW), "-90", '.'));
        for (int i = 0; i < 3; i++) {
            assertEquals(270, editor.mission().waypoint(i).yawDeg(), "Richtung läuft wie beim Schritt um");
        }
        editor.select(EditorState.Selection.waypoint(0));
        editor.stepWaypoint(WaypointField.ACCESSORY, 1, false);
        editor.stepWaypoint(WaypointField.REPEAT, -1, false);
        assertEquals(Waypoint.Repeat.EVERY, editor.mission().waypoint(0).repeatMode());
        editor.extendTo(EditorState.Selection.waypoint(2));
        assertTrue(editor.typeable(row(WaypointField.EVERY)));
        assertEquals(ValueEntry.Status.CLAMPED, editor.commitEntry(row(WaypointField.EVERY), "5000", '.'));
        assertEquals(MissionLimits.MAX_EVERY_TICKS, editor.mission().waypoint(0).everyTicks());
        assertEquals(0, editor.mission().waypoint(1).everyTicks(), "ohne Takt bleibt ohne Takt");
    }

    @Test
    void pointOfInterestRowsAreTypeable() {
        EditorState editor = editor(6.0);
        assertTrue(editor.addPoi(5.0, START_Y, 5.0));
        assertEquals("0", editor.entryText(0, '.'));
        assertEquals("15", editor.entryText(1, '.'));
        assertEquals(ValueEntry.Status.OK, editor.commitEntry(0, "30", '.'));
        assertEquals(START_Y + 30.0, editor.mission().pointsOfInterest().getFirst().y(), 1.0E-9);
        assertEquals(ValueEntry.Status.CLAMPED, editor.commitEntry(1, "500", '.'));
        assertEquals(MissionLimits.MAX_RADIUS_DM, editor.mission().pointsOfInterest().getFirst().radiusDm());
        assertFalse(editor.typeable(2));
    }

    @Test
    void entryHintNamesRangeAllAndKeys() {
        EditorState editor = firstThree();
        List<FieldText> hint = editor.entryHint(row(WaypointField.WAIT), "", '.');
        assertEquals(List.of(FieldText.of("entry.range", "0", "300"), FieldText.of("entry.all", "3"), FieldText.of("entry.keys")), hint);
        assertEquals(FieldText.of("entry.invalid"), editor.entryHint(row(WaypointField.WAIT), "1-", '.').getFirst());
        assertEquals(FieldText.of("entry.will_clamp", "300", "0", "300"), editor.entryHint(row(WaypointField.WAIT), "301", '.').getFirst());
        editor.select(EditorState.Selection.waypoint(0));
        assertEquals(2, editor.entryHint(row(WaypointField.WAIT), "", '.').size());
    }

    @Test
    void deleteRemovesAllSelectedAndReorderNeedsOne() {
        EditorState editor = editor(6.0, 9.0, 13.0, 20.0, 25.0);
        editor.select(EditorState.Selection.waypoint(1));
        editor.extendTo(EditorState.Selection.waypoint(3));
        assertFalse(editor.moveSelected(1));
        assertTrue(editor.deleteSelected());
        assertEquals(List.of(6.0, 25.0), heights(editor));
        assertEquals(EditorState.Selection.waypoint(1), editor.selection());
        assertEquals(List.of(1), editor.selectedWaypoints());
    }

    @Test
    void addAndMissionSwitchFallBackToOneWaypoint() {
        EditorState editor = firstThree();
        assertTrue(editor.addWaypointAt(50.0, START_Y + 1.0, 0.0));
        assertEquals(List.of(3), editor.selectedWaypoints());
        editor.selectAllWaypoints();
        assertTrue(editor.addMission());
        assertEquals(List.of(), editor.selectedWaypoints());
        assertTrue(editor.stepMission(-1));
        assertEquals(List.of(0), editor.selectedWaypoints());
    }

    @Test
    void revertUndoesTheFirstClickOfADoubleClick() {
        EditorState editor = firstThree();
        TabletState before = editor.tablet();
        assertTrue(editor.stepWaypoint(WaypointField.HEIGHT, 1, false, false));
        assertTrue(editor.revert(before));
        assertEquals(List.of(6.0, 9.0, 13.0, 20.0), heights(editor));
        assertEquals(List.of(0, 1, 2), editor.selectedWaypoints());
        assertFalse(editor.revert(before), "nichts mehr zurückzunehmen");
        assertFalse(editor.revert(null));
    }

    @Test
    void copyFollowsTheShownField() {
        Waypoint plain = Waypoint.at(0.0, 70.0, 0.0);
        Waypoint timed = plain.withAccessory(Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EVERY, 10);
        Waypoint once = plain.withAccessory(Waypoint.Accessory.ACTION_2, false);
        Waypoint copied = WaypointField.REPEAT.copy(timed, once);
        assertEquals(Waypoint.Repeat.EVERY, copied.repeatMode());
        assertEquals(10, copied.everyTicks());
        assertEquals(Waypoint.Accessory.ACTION_2, copied.accessory());
        assertEquals(plain, WaypointField.REPEAT.copy(timed, plain), "ohne Zubehör-Aktion kein Wiederholen");
        Waypoint accessory = WaypointField.ACCESSORY.copy(once, timed);
        assertEquals(Waypoint.Accessory.ACTION_2, accessory.accessory());
        assertEquals(10, accessory.everyTicks(), "Takt des Ziels bleibt");
    }

    @Test
    void valueEntryParsesLikeTheCoreNumberEditor() {
        ValueEntry.Range range = ValueEntry.Range.of(-10.0, 10.0, 1);
        assertEquals(ValueEntry.Status.OK, ValueEntry.evaluate("+5", range).status());
        assertEquals(1.5, ValueEntry.evaluate("1,5", range).value(), 1.0E-9);
        assertEquals(-3.0, ValueEntry.evaluate("−3", range).value(), 1.0E-9);
        assertEquals(1.3, ValueEntry.evaluate("1.25", range).value(), 1.0E-9);
        assertEquals(ValueEntry.Status.INVALID, ValueEntry.evaluate("1.2.3", range).status());
        assertEquals(ValueEntry.Status.INVALID, ValueEntry.evaluate("-", range).status());
        assertEquals(ValueEntry.Status.EMPTY, ValueEntry.evaluate("", range).status());
        assertEquals(new ValueEntry.Result(ValueEntry.Status.CLAMPED, 10.0), ValueEntry.evaluate("11", range));
        ValueEntry.Range yaw = ValueEntry.Range.wrapping(0, 360);
        assertEquals(new ValueEntry.Result(ValueEntry.Status.OK, 0.0), ValueEntry.evaluate("360", yaw));
        assertEquals(new ValueEntry.Result(ValueEntry.Status.OK, 350.0), ValueEntry.evaluate("-10", yaw));
        assertEquals("12", ValueEntry.text(12.0, range, ','));
        assertEquals("12,5", ValueEntry.text(12.5, range, ','));
        assertTrue(ValueEntry.allowed(',', range));
        assertFalse(ValueEntry.allowed(',', ValueEntry.Range.of(0.0, 5.0, 0)));
        assertFalse(ValueEntry.allowed('-', ValueEntry.Range.of(0.0, 5.0, 1)));
        assertTrue(ValueEntry.allowed('-', yaw));
        assertFalse(ValueEntry.allowed('x', range));
    }

    @Test
    void doubleClickOpensOnlyOnTheValueNotOnTheArrows() {
        Rect box = new Rect(100, 10, 80, 12);
        assertFalse(EditorLayout.onValueText(box, 100.0 + EditorLayout.VALUE_INSET - 1));
        assertTrue(EditorLayout.onValueText(box, 100.0 + EditorLayout.VALUE_INSET));
        assertTrue(EditorLayout.onValueText(box, box.centerX()));
        assertFalse(EditorLayout.onValueText(box, box.right() - EditorLayout.VALUE_INSET));
    }

    @Test
    void multiTitleCountsTheSelection() {
        assertEquals(FieldText.of("detail.waypoints", "3"), EditorCatalog.waypointsTitle(3));
    }
}
