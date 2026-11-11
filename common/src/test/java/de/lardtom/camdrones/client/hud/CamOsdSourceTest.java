package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.integration.NavAssist;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.HoldController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamOsdSourceTest {
    private static FlightTelemetry moving(double speed, double vertical) {
        return new FlightTelemetry(true, "ANGLE", Optional.empty(), 0.0, 70.0, 0.0, speed, vertical, 0.0, 0.0, 0.0, 0.5, 16.0, 4.0, 0.8, false,
                0.0, 0.0, 0.0, true, 12, 1.0, 1.0, 10.0, 0.0, 0.0, 0.0, 0.5);
    }

    private static NavStatus status(String label, NavLogic.Function function, NavLogic.MissionState missionState, NavOutput.Warning... warnings) {
        Set<NavOutput.Warning> set = EnumSet.noneOf(NavOutput.Warning.class);
        set.addAll(List.of(warnings));
        return new NavStatus(FlightClass.TOUR, label, NavLogic.StickMode.NORMAL, function, missionState, 0, set, false, Double.NaN,
                Optional.empty(), Waypoint.Action.NONE, Optional.empty(), Double.NaN);
    }

    private static CamOsdState shown(Set<NavOutput.Warning> warnings, NavLogic.MissionState missionState) {
        StateBuilder state = StateBuilder.camera();
        state.label = HoldController.LABEL;
        state.warnings = EnumSet.noneOf(NavOutput.Warning.class);
        state.warnings.addAll(warnings);
        state.missionState = missionState;
        return state.build();
    }

    @Test
    void pauseWithoutMissionShowsPausedLikeAPausedMission() {
        NavStatus tripod = status(HoldController.LABEL, NavLogic.Function.HOLD, NavLogic.MissionState.IDLE);
        Set<NavOutput.Warning> warnings = CamOsdSource.warnings(Optional.of(tripod), NavLogic.MissionState.IDLE);
        assertTrue(warnings.contains(NavOutput.Warning.PAUSED), "Luftstativ ohne Mission: " + warnings);
        assertEquals(OsdText.of("PAUSED", OsdTone.ACCENT), CamOsd.warning(CamOsd.Warning.PAUSED, shown(warnings, NavLogic.MissionState.IDLE)));
        assertTrue(CamOsd.warnings(shown(warnings, NavLogic.MissionState.IDLE)).contains(OsdText.of("PAUSED", OsdTone.ACCENT)));

        NavStatus done = status(HoldController.LABEL, NavLogic.Function.HOLD, NavLogic.MissionState.DONE);
        assertTrue(CamOsdSource.warnings(Optional.of(done), NavLogic.MissionState.DONE).contains(NavOutput.Warning.PAUSED));

        NavStatus paused = status(HoldController.LABEL, NavLogic.Function.HOLD, NavLogic.MissionState.PAUSED, NavOutput.Warning.PAUSED);
        assertEquals(EnumSet.of(NavOutput.Warning.PAUSED), CamOsdSource.warnings(Optional.of(paused), NavLogic.MissionState.PAUSED));
        assertEquals(EnumSet.of(NavOutput.Warning.PAUSED), CamOsdSource.warnings(Optional.empty(), NavLogic.MissionState.PAUSED));
    }

    @Test
    void noPausedWhileFlyingOrInOtherFunctions() {
        for (NavLogic.Function function : NavLogic.Function.values()) {
            if (function == NavLogic.Function.HOLD) {
                continue;
            }
            NavStatus flying = status("N", function, NavLogic.MissionState.IDLE, NavOutput.Warning.OBSTACLE);
            Set<NavOutput.Warning> warnings = CamOsdSource.warnings(Optional.of(flying), NavLogic.MissionState.IDLE);
            assertFalse(warnings.contains(NavOutput.Warning.PAUSED), function + ": " + warnings);
            assertEquals(EnumSet.of(NavOutput.Warning.OBSTACLE), warnings, function.name());
        }
        NavStatus mission = status("WP 2/4", NavLogic.Function.MISSION, NavLogic.MissionState.RUNNING);
        assertTrue(CamOsdSource.warnings(Optional.of(mission), NavLogic.MissionState.RUNNING).isEmpty());
        assertTrue(CamOsdSource.warnings(Optional.empty(), NavLogic.MissionState.IDLE).isEmpty());
    }

    @Test
    void labelFromDroneDataForSpectators() {
        assertEquals("WP 3/12", CamOsdSource.label(NavLogic.MissionState.RUNNING, 2, 12, SmartState.NONE));
        assertEquals("WP 3/12", CamOsdSource.label(NavLogic.MissionState.PAUSED, 2, 12, SmartState.NONE));
        assertEquals("", CamOsdSource.label(NavLogic.MissionState.DONE, 2, 12, SmartState.NONE));
        assertEquals("HOLD", CamOsdSource.label(NavLogic.MissionState.IDLE, 0, 0, SmartState.hold(0.0, 70.0, 0.0)));
        assertEquals("FOLLOW", CamOsdSource.label(NavLogic.MissionState.IDLE, 0, 0, SmartState.follow(UUID.randomUUID(),
                FollowController.Style.BEHIND)));
        assertEquals("POI", CamOsdSource.label(NavLogic.MissionState.IDLE, 0, 0, SmartState.pointOfInterest(0.0, 64.0, 0.0, 10.0, 3.0, true)));
        assertEquals("LOCK", CamOsdSource.label(NavLogic.MissionState.IDLE, 0, 0, SmartState.courseLock(90.0)));
        assertEquals("", CamOsdSource.label(NavLogic.MissionState.RUNNING, 0, 0, SmartState.NONE));
    }

    @Test
    void labelShowsTheFunctionThatInterruptedTheMission() {
        assertEquals("AUTO", CamOsdSource.label(NavLogic.MissionState.PAUSED, 1, 4, SmartState.autoShot(AutoShots.Shot.CIRCLE, 0.0, 64.0, 12.0)));
        assertEquals("POI", CamOsdSource.label(NavLogic.MissionState.PAUSED, 1, 4, SmartState.pointOfInterest(0.0, 64.0, 0.0, 10.0, 3.0, true)));
        assertEquals("FOLLOW", CamOsdSource.label(NavLogic.MissionState.PAUSED, 1, 4, SmartState.follow(UUID.randomUUID(),
                FollowController.Style.BEHIND)));
        assertEquals("HOLD", CamOsdSource.label(NavLogic.MissionState.PAUSED, 1, 4, SmartState.hold(0.0, 70.0, 0.0)));
        assertEquals("WP 2/4", CamOsdSource.label(NavLogic.MissionState.PAUSED, 1, 4, SmartState.NONE));
        assertEquals("WP 2/4", CamOsdSource.label(NavLogic.MissionState.RUNNING, 1, 4, SmartState.NONE));
    }

    @Test
    void classFromMassLikeTheFlightAssist() {
        assertEquals(FlightClass.COMPACT, CamOsdSource.byMass(0.249));
        assertEquals(FlightClass.TOUR, CamOsdSource.byMass(NavAssist.COMPACT_MAX_KG));
        assertEquals(FlightClass.TOUR, CamOsdSource.byMass(0.72));
        assertEquals(FlightClass.CINE, CamOsdSource.byMass(3.95));
        assertEquals(FlightClass.COMPACT, CamOsdSource.byMass(Double.NaN));
    }

    @Test
    void horizontalSpeedWithoutTheVerticalPart() {
        assertEquals(4.0, CamOsdSource.horizontalSpeed(moving(5.0, 3.0)), 1.0E-9);
        assertEquals(5.0, CamOsdSource.horizontalSpeed(moving(5.0, Double.NaN)), 1.0E-9);
        assertEquals(0.0, CamOsdSource.horizontalSpeed(moving(2.0, 3.0)), 1.0E-9);
        assertTrue(Double.isNaN(CamOsdSource.horizontalSpeed(moving(Double.NaN, 1.0))));
    }
}
