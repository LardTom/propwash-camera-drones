package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.integration.autonomy.WaypointTaskServer;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GripperActionTest {
    private static final FlightClass TOUR = FlightClass.TOUR;

    private static DroneSupport fpv(boolean actions, boolean gripper) {
        return DroneSupport.of(false, Optional.of("freestyle"), 18, false, actions, gripper, List.of(), true);
    }

    private static Mission mission() {
        Mission m = Mission.of(List.of(Waypoint.at(0.0, 70.0, 0.0), Waypoint.at(10.0, 70.0, 0.0), Waypoint.at(20.0, 70.0, 0.0)));
        return m.replace(2, m.waypoint(2).withAccessory(Waypoint.Accessory.GRIPPER_RELEASE, false));
    }

    @Test
    void theGripperIsNoLongerBlocked() {
        assertTrue(Arrays.stream(DroneSupport.Skip.values()).noneMatch(skip -> skip.name().equals("GRIPPER_ONLY")), "Sperre GRIPPER_ONLY entfällt");
        DroneSupport gripperOnly = fpv(false, true);
        assertEquals(DroneSupport.Skip.NO_ACCESSORY, gripperOnly.accessory(2), "Aktion 1 bis 4 braucht ein Zubehör mit Aktionen");
        assertEquals(DroneSupport.Skip.NONE, gripperOnly.release());
        assertEquals(DroneSupport.Skip.NONE, gripperOnly.skip(mission(), 2));
        assertEquals(0, gripperOnly.skipped(mission()));
    }

    @Test
    void withoutAGripperTheWaypointSaysWhy() {
        DroneSupport dispenser = fpv(true, false);
        assertEquals(DroneSupport.Skip.NO_GRIPPER, dispenser.release());
        assertTrue(DroneSupport.Skip.NO_GRIPPER.skipped());
        assertEquals(FieldText.PREFIX + "support.reason.no_gripper", DroneSupport.Skip.NO_GRIPPER.key());
        Mission m = mission();
        assertEquals(DroneSupport.Skip.NO_GRIPPER, dispenser.skip(m, 2));
        assertEquals(DroneSupport.Skip.NO_GRIPPER, dispenser.accessory(m, 2), "Feld Zubehör im Wegpunkt-Detail");
        assertEquals(DroneSupport.Skip.NONE, dispenser.accessory(m, 1));
        assertEquals(1, dispenser.skipped(m));
        assertEquals(DroneSupport.Skip.UNKNOWN, DroneSupport.UNKNOWN.release());
        assertEquals(DroneSupport.Skip.UNKNOWN, DroneSupport.UNKNOWN.accessory(m, 2));
        assertEquals(DroneSupport.Skip.NONE, fpv(true, true).accessory(m, 2));
        Mission action = m.replace(1, m.waypoint(1).withAccessory(Waypoint.Accessory.ACTION_1, false));
        assertEquals(DroneSupport.Skip.NO_ACCESSORY, fpv(false, true).accessory(action, 1));
        assertEquals(DroneSupport.Skip.NO_GRIPPER, fpv(true, false).skip(WaypointTasks.due(m, 2)));
    }

    @Test
    void theAccessoryFieldOffersTheRelease() {
        Waypoint w = Waypoint.at(0.0, 70.0, 0.0);
        Waypoint end = w.withAccessory(Waypoint.Accessory.END_REPEAT, false);
        Waypoint release = WaypointField.ACCESSORY.step(end, 1, false, 0, TOUR);
        assertEquals(Waypoint.Accessory.GRIPPER_RELEASE, release.accessory());
        assertEquals(Waypoint.Accessory.GRIPPER_RELEASE, WaypointField.ACCESSORY.step(w, -1, false, 0, TOUR).accessory());
        assertEquals(Waypoint.Accessory.NONE, WaypointField.ACCESSORY.step(release, 1, false, 0, TOUR).accessory());
        assertFalse(WaypointField.REPEAT.enabled(release), "kein Wiederholen und kein Takt");
        assertFalse(WaypointField.EVERY.enabled(release));
        assertEquals(FieldText.PREFIX + "value.accessory.gripper_release", WaypointField.ACCESSORY.value(release, ',', 0.0).key());
        Waypoint timed = w.withAccessory(Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EVERY, 20);
        Waypoint switched = WaypointField.ACCESSORY.copy(release, timed);
        assertEquals(Waypoint.Accessory.GRIPPER_RELEASE, switched.accessory());
        assertEquals(0, switched.everyTicks());
        assertFalse(switched.repeat());
    }

    @Test
    void textsExistInBothLanguagesAndKeepTheLayout() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            PlannerLang lang = PlannerLang.of(language);
            for (String key : List.of(FieldText.PREFIX + "value.accessory.gripper_release", FieldText.PREFIX + "support.reason.no_gripper",
                    FieldText.PREFIX + "support.marker.release", WaypointTaskServer.PREFIX + "gripper.release")) {
                assertTrue(lang.has(key), language + " fehlt: " + key);
            }
            assertFalse(lang.has(FieldText.PREFIX + "support.reason.gripper_only"), language + ": alter Grund für die Sperre");
            int before = Math.max(font.width(lang.text(FieldText.PREFIX + "value.accessory.end_repeat", List.of())),
                    font.width(lang.text(FieldText.PREFIX + "value.accessory.repeated", List.of("4", "50"))));
            int width = font.width(lang.text(FieldText.PREFIX + "value.accessory.gripper_release", List.of()));
            assertTrue(width <= before, language + ": „Greifer auslösen“ " + width + " breiter als " + before);
        }
        assertEquals("Greifer auslösen", PlannerLang.of("de_de").text(FieldText.PREFIX + "value.accessory.gripper_release", List.of()));
        assertEquals("kein Greifer an der Drohne", PlannerLang.of("de_de").text(FieldText.PREFIX + "support.reason.no_gripper", List.of()));
    }
}
