package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.integration.autonomy.FpvMissions;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneSupportTest {
    private static DroneSupport fpv(int satellites, boolean leds, boolean actions, boolean gripper, boolean lightControl) {
        return DroneSupport.of(false, Optional.of("freestyle"), satellites, leds, actions, gripper,
                List.of(Optional.of("example.action.drop"), Optional.empty()), lightControl);
    }

    private static List<String> keys(List<FieldText> texts) {
        return texts.stream().map(FieldText::key).toList();
    }

    @Test
    void cameraDronesFlyMissionsWithAndWithoutPilot() {
        DroneSupport camera = DroneSupport.of(true, Optional.of("long_range"), 18, false, false, false, List.of(), false);
        assertEquals(DroneSupport.Kind.CAMERA, camera.kind());
        assertTrue(camera.missionsWithPilot());
        assertTrue(camera.obstacleSensors());
        assertTrue(camera.gpsFix());
        assertTrue(camera.header().isEmpty());
        assertEquals(Optional.empty(), camera.notice());
    }

    @Test
    void fpvDronesFlyOnlyWithoutPilotAndSayWhy() {
        DroneSupport freestyle = fpv(12, false, false, false, false);
        assertEquals(DroneSupport.Kind.FPV, freestyle.kind());
        assertFalse(freestyle.missionsWithPilot());
        assertFalse(freestyle.obstacleSensors());
        assertEquals(List.of(FieldText.PREFIX + "support.header.fpv"), keys(freestyle.header()));
        assertEquals(FieldText.PREFIX + "support.notice.fpv", freestyle.notice().orElseThrow().key());
        assertEquals(List.of(FieldText.PREFIX + "support.role.freestyle", FieldText.PREFIX + "support.fpv_pilot",
                FieldText.PREFIX + "support.fpv_sensors", FieldText.PREFIX + "support.gps_ok"), keys(freestyle.details()));
        DroneSupport unknownRole = DroneSupport.of(false, Optional.of("mystery"), 12, false, false, false, List.of(), false);
        assertEquals(FieldText.PREFIX + "support.fpv", unknownRole.details().getFirst().key());
        for (String role : DroneSupport.ROLES) {
            assertTrue(DroneSupport.knownRole(role));
        }
    }

    @Test
    void withoutGpsThePlannerWarnsClearly() {
        DroneSupport covered = fpv(3, false, false, false, false);
        assertFalse(covered.gpsFix());
        assertEquals(List.of(FieldText.PREFIX + "support.header.fpv", FieldText.PREFIX + "support.header.no_gps"), keys(covered.header()));
        FieldText notice = covered.notice().orElseThrow();
        assertEquals(FieldText.PREFIX + "support.notice.no_gps", notice.key());
        assertEquals(List.of("3", "5"), notice.args());
        assertTrue(keys(covered.details()).contains(FieldText.PREFIX + "support.gps_missing"));
        assertTrue(fpv(DroneSupport.FIX_SATELLITES, false, false, false, false).gpsFix());
        assertTrue(DroneSupport.UNKNOWN.gpsFix(), "unbekannt heißt nicht kein GPS");
        assertTrue(fpv(-1, false, false, false, false).gpsFix());
    }

    @Test
    void missingEquipmentIsSkippedWithReason() {
        DroneSupport bare = fpv(18, false, false, false, false);
        assertEquals(DroneSupport.Skip.NO_ACCESSORY, bare.accessory(1));
        assertEquals(DroneSupport.Skip.NONE, bare.accessory(0));
        assertEquals(DroneSupport.Skip.NO_LIGHT_API, bare.light(Waypoint.Light.ON));
        assertEquals(DroneSupport.Skip.NONE, bare.light(Waypoint.Light.NONE));
        assertEquals(DroneSupport.Skip.NO_ACCESSORY, fpv(18, false, false, true, false).accessory(2));
        DroneSupport dispenser = fpv(18, true, true, false, true);
        assertEquals(DroneSupport.Skip.NONE, dispenser.accessory(1));
        assertEquals(DroneSupport.Skip.NONE, dispenser.light(Waypoint.Light.BLINK));
        assertEquals(DroneSupport.Skip.NO_LEDS, fpv(18, false, true, false, true).light(Waypoint.Light.OFF));
        assertEquals(Optional.of("example.action.drop"), dispenser.actionName(1));
        assertEquals(Optional.empty(), dispenser.actionName(2));
        assertEquals(Optional.empty(), dispenser.actionName(5));
        assertEquals(DroneSupport.Skip.UNKNOWN, DroneSupport.UNKNOWN.accessory(3));
        assertFalse(DroneSupport.Skip.UNKNOWN.skipped());
        assertTrue(DroneSupport.Skip.NO_LEDS.skipped());
        assertEquals(FieldText.PREFIX + "support.reason.no_gripper", DroneSupport.Skip.NO_GRIPPER.key());
    }

    @Test
    void skippedCountsRepeatedWaypointsToo() {
        Mission mission = Mission.of(List.of(Waypoint.at(0.0, 70.0, 0.0), Waypoint.at(5.0, 70.0, 0.0), Waypoint.at(10.0, 70.0, 0.0)));
        mission = mission.replace(0, mission.waypoint(0).withAccessory(Waypoint.Accessory.ACTION_1, true));
        mission = mission.replace(2, mission.waypoint(2).withLight(Waypoint.Light.ON));
        DroneSupport bare = fpv(18, false, false, false, false);
        assertEquals(3, bare.skipped(mission));
        assertEquals(DroneSupport.Skip.NO_ACCESSORY, bare.skip(mission, 1));
        assertEquals(1, fpv(18, false, true, false, false).skipped(mission));
        assertEquals(0, fpv(18, true, true, false, true).skipped(mission));
    }

    @Test
    void fpvMissionsNeedTheGogglesOffAndKeepPropwashsExit() {
        assertTrue(FpvMissions.refusedWithPilot(MissionCommand.Command.START, true));
        assertTrue(FpvMissions.refusedWithPilot(MissionCommand.Command.RESUME, true));
        assertFalse(FpvMissions.refusedWithPilot(MissionCommand.Command.STOP, true));
        assertFalse(FpvMissions.refusedWithPilot(MissionCommand.Command.PAUSE, true));
        assertFalse(FpvMissions.refusedWithPilot(MissionCommand.Command.START, false));
        assertTrue(FpvMissions.keepsPropwashExit(true, false, SmartState.Function.NONE));
        assertTrue(FpvMissions.keepsPropwashExit(true, false, null));
        assertFalse(FpvMissions.keepsPropwashExit(true, true, SmartState.Function.NONE), "laufende Mission fliegt weiter");
        assertFalse(FpvMissions.keepsPropwashExit(true, false, SmartState.Function.FOLLOW));
        assertFalse(FpvMissions.keepsPropwashExit(false, false, SmartState.Function.NONE), "Kamera-Drohnen behalten ihre Einstellung");
    }

    @Test
    void taskFieldsCycleAndRepeatNeedsAnAction() {
        Waypoint w = Waypoint.at(0.0, 70.0, 0.0);
        assertFalse(WaypointField.REPEAT.enabled(w));
        assertEquals(w, WaypointField.REPEAT.step(w, 1, false, 0, FlightClass.TOUR));
        Waypoint light = WaypointField.LIGHT.step(w, 1, false, 0, FlightClass.TOUR);
        assertEquals(Waypoint.Light.ON, light.light());
        assertEquals(Waypoint.Light.RAINBOW, WaypointField.LIGHT.step(w, -1, false, 0, FlightClass.TOUR).light());
        Waypoint action = WaypointField.ACCESSORY.step(w, 1, false, 0, FlightClass.TOUR);
        assertEquals(Waypoint.Accessory.ACTION_1, action.accessory());
        assertTrue(WaypointField.REPEAT.enabled(action));
        Waypoint repeating = WaypointField.REPEAT.step(action, 1, false, 0, FlightClass.TOUR);
        assertTrue(repeating.repeat());
        assertEquals(FieldText.PREFIX + "value.repeat.on", WaypointField.REPEAT.value(repeating, ',', 0.0).key());
        Waypoint none = WaypointField.ACCESSORY.step(repeating, -1, false, 0, FlightClass.TOUR);
        assertEquals(Waypoint.Accessory.NONE, none.accessory());
        assertFalse(none.repeat(), "ohne Aktion keine Wiederholung");
        assertEquals(FieldText.PREFIX + "value.accessory.action", WaypointField.ACCESSORY.value(action, ',', 0.0).key());
        assertEquals(List.of("1"), WaypointField.ACCESSORY.value(action, ',', 0.0).args());
        assertEquals(FieldText.PREFIX + "value.accessory.end_repeat",
                WaypointField.ACCESSORY.value(w.withAccessory(Waypoint.Accessory.END_REPEAT, false), ',', 0.0).key());
        assertEquals(FieldText.PREFIX + "value.light.low_battery", WaypointField.LIGHT.value(w.withLight(Waypoint.Light.LOW_BATTERY), ',', 0.0).key());
        assertTrue(WaypointField.LIGHT.task() && WaypointField.ACCESSORY.task() && WaypointField.REPEAT.task());
        assertFalse(WaypointField.ACTION.task());
        Waypoint stepped = WaypointField.SPEED.step(repeating.withLight(Waypoint.Light.BLINK), 1, false, 0, FlightClass.TOUR);
        assertEquals(Waypoint.Light.BLINK, stepped.light());
        assertTrue(stepped.repeat(), "andere Felder lassen die Aktionen stehen");
    }
}
