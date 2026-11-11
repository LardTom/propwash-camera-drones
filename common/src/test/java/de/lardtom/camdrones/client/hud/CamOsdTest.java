package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamOsdTest {
    @Test
    void nothingForOtherDrones() {
        CamOsdState other = CamOsdState.NONE;
        StateBuilder fpv = StateBuilder.camera().everything();
        fpv.cameraDrone = false;
        for (CamOsdState state : List.of(other, fpv.build())) {
            for (CamOsdElements.Element element : CamOsdElements.ELEMENTS) {
                assertNull(element.text().apply(state), element.id().toString());
            }
            assertTrue(CamOsd.warnings(state).isEmpty());
        }
    }

    @Test
    void modeShowsTheAssistLabel() {
        assertEquals(OsdText.of("N", OsdTone.NORMAL), CamOsd.mode(StateBuilder.camera().build()));
        StateBuilder atti = StateBuilder.camera();
        atti.label = "ATTI";
        atti.atti = true;
        assertEquals(OsdText.of("ATTI", OsdTone.CAUTION), CamOsd.mode(atti.build()));
        StateBuilder mission = StateBuilder.camera();
        mission.label = "WP 3/12";
        assertEquals("WP 3/12", CamOsd.mode(mission.build()).text());
        StateBuilder none = StateBuilder.camera();
        none.label = " ";
        assertNull(CamOsd.mode(none.build()));
    }

    @Test
    void gimbalOnlyWithKnownTilt() {
        assertNull(CamOsd.gimbal(StateBuilder.camera().build()));
        StateBuilder tilt = StateBuilder.camera();
        tilt.gimbalTilt = -45.0;
        assertEquals(OsdText.of("G -45", OsdTone.NORMAL), CamOsd.gimbal(tilt.build()));
        tilt.operator = true;
        assertEquals(OsdTone.ACCENT, CamOsd.gimbal(tilt.build()).tone());
    }

    @Test
    void missionOnlyWhileRunningOrPaused() {
        assertNull(CamOsd.mission(StateBuilder.camera().mission(NavLogic.MissionState.IDLE, 2, 12, 42.0).build()));
        assertNull(CamOsd.mission(StateBuilder.camera().mission(NavLogic.MissionState.DONE, 11, 12, 0.0).build()));
        assertNull(CamOsd.mission(StateBuilder.camera().mission(NavLogic.MissionState.RUNNING, 0, 0, 0.0).build()));
        assertEquals(OsdText.of("WP 3/12 0:42", OsdTone.NORMAL),
                CamOsd.mission(StateBuilder.camera().mission(NavLogic.MissionState.RUNNING, 2, 12, 42.0).build()));
        assertEquals(OsdText.of("WP 3/12", OsdTone.CAUTION),
                CamOsd.mission(StateBuilder.camera().mission(NavLogic.MissionState.PAUSED, 2, 12, Double.NaN).build()));
    }

    @Test
    void obstacleOnlyBelowEightMetres() {
        assertNull(CamOsd.obstacle(StateBuilder.camera().build()));
        assertNull(CamOsd.obstacle(StateBuilder.camera().obstacle(NavInput.Sensor.LEFT, 8.0).build()));
        assertNull(CamOsd.obstacle(StateBuilder.camera().obstacle(NavInput.Sensor.LEFT, Double.NaN).build()));
        assertEquals(OsdText.of("< 4.2M", OsdTone.CAUTION), CamOsd.obstacle(StateBuilder.camera().obstacle(NavInput.Sensor.LEFT, 4.2).build()));
        assertEquals(OsdText.of("^ 2.0M", OsdTone.WARNING), CamOsd.obstacle(StateBuilder.camera().obstacle(NavInput.Sensor.FORWARD, 2.0).build()));
        assertEquals(OsdTone.WARNING, CamOsd.obstacle(StateBuilder.camera().obstacle(NavInput.Sensor.BACK, 6.0)
                .warn(NavOutput.Warning.OBSTACLE_STOP).build()).tone());
    }

    @Test
    void flightLeftTurnsYellowThenRed() {
        StateBuilder state = StateBuilder.camera();
        assertNull(CamOsd.flightLeft(state.build()));
        state.flightSecondsLeft = 1450.0;
        assertEquals(OsdText.of("LEFT 24:10", OsdTone.NORMAL), CamOsd.flightLeft(state.build()));
        state.flightSecondsLeft = 179.0;
        assertEquals(OsdTone.CAUTION, CamOsd.flightLeft(state.build()).tone());
        state.flightSecondsLeft = 59.0;
        assertEquals(OsdTone.WARNING, CamOsd.flightLeft(state.build()).tone());
        state.flightSecondsLeft = -5.0;
        assertEquals("LEFT 0:00", CamOsd.flightLeft(state.build()).text());
    }

    @Test
    void warningsFollowTheFlightAssist() {
        CamOsdState quiet = StateBuilder.camera().build();
        for (CamOsd.Warning warning : CamOsd.Warning.values()) {
            assertNull(CamOsd.warning(warning, quiet), warning.name());
        }
        assertEquals(OsdText.of("OBSTACLE", OsdTone.CAUTION), CamOsd.warning(CamOsd.Warning.OBSTACLE,
                StateBuilder.camera().warn(NavOutput.Warning.OBSTACLE).build()));
        assertEquals(OsdText.of("OBSTACLE", OsdTone.WARNING), CamOsd.warning(CamOsd.Warning.OBSTACLE,
                StateBuilder.camera().warn(NavOutput.Warning.OBSTACLE_STOP).build()));
        assertEquals(OsdText.of("ALT LIMIT", OsdTone.CAUTION), CamOsd.warning(CamOsd.Warning.ALT_LIMIT,
                StateBuilder.camera().warn(NavOutput.Warning.ALTITUDE_LIMIT).build()));
        assertEquals(OsdText.of("DIST LIMIT", OsdTone.CAUTION), CamOsd.warning(CamOsd.Warning.DIST_LIMIT,
                StateBuilder.camera().warn(NavOutput.Warning.DISTANCE_LIMIT).build()));
        assertEquals(OsdText.of("NO GPS ATTI", OsdTone.CAUTION), CamOsd.warning(CamOsd.Warning.NO_GPS,
                StateBuilder.camera().warn(NavOutput.Warning.NO_GPS).build()));
        StateBuilder atti = StateBuilder.camera();
        atti.atti = true;
        assertNotNull(CamOsd.warning(CamOsd.Warning.NO_GPS, atti.build()));
        assertEquals(OsdText.of("PAUSED", OsdTone.ACCENT), CamOsd.warning(CamOsd.Warning.PAUSED,
                StateBuilder.camera().warn(NavOutput.Warning.PAUSED).build()));
        assertNotNull(CamOsd.warning(CamOsd.Warning.PAUSED, StateBuilder.camera().mission(NavLogic.MissionState.PAUSED, 1, 4, 10.0).build()));
        StateBuilder wind = StateBuilder.camera();
        wind.highWind = true;
        assertEquals(OsdText.of("HIGH WIND", OsdTone.CAUTION), CamOsd.warning(CamOsd.Warning.HIGH_WIND, wind.build()));
        StateBuilder operator = StateBuilder.camera();
        operator.operator = true;
        assertEquals(OsdText.of("OPERATOR", OsdTone.ACCENT), CamOsd.warning(CamOsd.Warning.OPERATOR, operator.build()));
        assertNull(CamOsd.warning(CamOsd.Warning.GIMBAL_LIMIT, operator.build()), "Gimbal frei: kein Anschlag");
        operator.gimbalStop = true;
        assertEquals(OsdText.of("GIMBAL LIMIT", OsdTone.CAUTION), CamOsd.warning(CamOsd.Warning.GIMBAL_LIMIT, operator.build()));
        StateBuilder compass = StateBuilder.camera().warn(NavOutput.Warning.COMPASS);
        compass.atti = true;
        assertEquals(OsdText.of("COMPASS ERROR", OsdTone.WARNING), CamOsd.warning(CamOsd.Warning.COMPASS, compass.build()));
        assertNull(CamOsd.warning(CamOsd.Warning.NO_GPS, compass.build()), "ATTI wegen des Kompasses meldet kein fehlendes GPS");
        assertNotNull(CamOsd.warning(CamOsd.Warning.NO_GPS, compass.warn(NavOutput.Warning.NO_GPS).build()));
        StateBuilder off = StateBuilder.camera().warn(NavOutput.Warning.NO_GPS);
        off.atti = true;
        assertNull(CamOsd.warning(CamOsd.Warning.OBSTACLE_OFF, off.build()), "ATTI ohne Hindernis-Sensoren meldet keine abgeschaltete Erkennung");
        assertEquals(OsdText.of("ATTI - OBSTACLE SENSING OFF", OsdTone.WARNING), CamOsd.warning(CamOsd.Warning.OBSTACLE_OFF,
                off.warn(NavOutput.Warning.OBSTACLE_OFF).build()));
    }

    @Test
    void designWarningTextsAndIds() {
        Map<CamOsd.Warning, String> design = Map.of(CamOsd.Warning.OBSTACLE, "OBSTACLE", CamOsd.Warning.ALT_LIMIT, "ALT LIMIT",
                CamOsd.Warning.NO_GPS, "NO GPS ATTI", CamOsd.Warning.PAUSED, "PAUSED", CamOsd.Warning.HIGH_WIND, "HIGH WIND",
                CamOsd.Warning.OPERATOR, "OPERATOR", CamOsd.Warning.GIMBAL_LIMIT, "GIMBAL LIMIT");
        design.forEach((warning, text) -> assertEquals(text, warning.text()));
        for (CamOsd.Warning warning : CamOsd.Warning.values()) {
            assertTrue(warning.path().matches("warning/[a-z_]+"), warning.path());
            assertTrue(HudText.printable(warning.text()));
            assertEquals(warning.text().toUpperCase(Locale.ROOT), warning.text());
        }
        assertEquals("camdrones.osd.warning.compass", CamOsd.Warning.COMPASS.key());
        assertEquals("COMPASS ERROR", CamOsd.Warning.COMPASS.shown());
        assertEquals("camdrones.osd.warning.obstacle_off", CamOsd.Warning.OBSTACLE_OFF.key());
        assertEquals("ATTI - OBSTACLE SENSING OFF", CamOsd.Warning.OBSTACLE_OFF.shown());
        assertEquals(10, CamOsd.warnings(StateBuilder.camera().everything().build()).size());
    }

    @Test
    void warningsTakeTurnsLikeTheOsd() {
        List<OsdText> list = List.of(OsdText.of("A"), OsdText.of("B"), OsdText.of("C"));
        assertNull(CamOsd.cycle(List.of(), 0L));
        assertEquals("A", CamOsd.cycle(list, 0L).text());
        assertEquals("A", CamOsd.cycle(list, CamOsd.WARNING_CYCLE_MILLIS - 1L).text());
        assertEquals("B", CamOsd.cycle(list, CamOsd.WARNING_CYCLE_MILLIS).text());
        assertEquals("C", CamOsd.cycle(list, 2L * CamOsd.WARNING_CYCLE_MILLIS).text());
        assertEquals("A", CamOsd.cycle(list, 3L * CamOsd.WARNING_CYCLE_MILLIS).text());
        assertEquals("C", CamOsd.cycle(list, -1L).text());
    }
}
