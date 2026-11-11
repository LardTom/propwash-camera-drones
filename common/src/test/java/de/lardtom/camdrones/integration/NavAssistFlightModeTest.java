package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistFlightModeTest {
    private static final double DT = 0.01;
    private static final Set<SensorDirection> ALL = EnumSet.allOf(SensorDirection.class);

    private static NavAssist pilot(FlightClass flightClass) {
        return new NavAssist(flightClass, ALL, true, NavContext.NONE);
    }

    private static FakeFlight.Output run(NavAssist assist, FakeFlight.State state, int steps) {
        FakeFlight.Output output = null;
        for (int i = 0; i < steps; i++) {
            output = new FakeFlight.Output();
            state.step(DT);
            assist.update(state, output);
        }
        return output;
    }

    private static StabilizationMode propwashKey(StabilizationMode mode) {
        return switch (mode) {
            case ACRO -> StabilizationMode.ANGLE;
            case ANGLE -> StabilizationMode.HORIZON;
            case HORIZON -> StabilizationMode.ACRO;
        };
    }

    @Test
    void angleIsNormalHorizonIsCineAndAcroIsSport() {
        assertEquals(NavLogic.StickMode.NORMAL, NavAssist.stickMode(StabilizationMode.ANGLE));
        assertEquals(NavLogic.StickMode.CINE, NavAssist.stickMode(StabilizationMode.HORIZON));
        assertEquals(NavLogic.StickMode.SPORT, NavAssist.stickMode(StabilizationMode.ACRO));
    }

    @Test
    void flightModeKeyOfPropwashSwitchesNormalCineSportOnEveryClass() {
        for (FlightClass flightClass : FlightClass.values()) {
            FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0).mode(StabilizationMode.ANGLE);
            NavAssist assist = pilot(flightClass);
            FakeFlight.Output first = run(assist, state, 5);
            assertEquals(NavLogic.StickMode.NORMAL, assist.logic().stickMode(), flightClass + ": Tastatur startet in Angle");
            assertEquals("N", first.label, flightClass.toString());
            List<NavLogic.StickMode> modes = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            StabilizationMode mode = StabilizationMode.ANGLE;
            for (int press = 0; press < 6; press++) {
                mode = propwashKey(mode);
                FakeFlight.Output output = run(assist, state.mode(mode), 5);
                modes.add(assist.logic().stickMode());
                labels.add(output.label);
            }
            assertEquals(List.of(NavLogic.StickMode.CINE, NavLogic.StickMode.SPORT, NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE,
                    NavLogic.StickMode.SPORT, NavLogic.StickMode.NORMAL), modes, flightClass.toString());
            assertEquals(List.of("C", "S", "N", "C", "S", "N"), labels, flightClass.toString());
        }
    }

    @Test
    void everySwitchPositionSelectsItsModeAlsoAtTheStart() {
        assertEquals(NavLogic.StickMode.SPORT, started(StabilizationMode.ACRO));
        assertEquals(NavLogic.StickMode.CINE, started(StabilizationMode.HORIZON));
        assertEquals(NavLogic.StickMode.NORMAL, started(StabilizationMode.ANGLE));

        FakeFlight.State state = new FakeFlight.State().mode(StabilizationMode.ACRO);
        NavAssist assist = pilot(FlightClass.TOUR);
        run(assist, state, 3);
        List<NavLogic.StickMode> modes = new ArrayList<>();
        for (StabilizationMode position : List.of(StabilizationMode.HORIZON, StabilizationMode.ANGLE, StabilizationMode.HORIZON,
                StabilizationMode.ACRO, StabilizationMode.ANGLE, StabilizationMode.ACRO)) {
            run(assist, state.mode(position), 3);
            modes.add(assist.logic().stickMode());
        }
        assertEquals(List.of(NavLogic.StickMode.CINE, NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE, NavLogic.StickMode.SPORT,
                NavLogic.StickMode.NORMAL, NavLogic.StickMode.SPORT), modes);
    }

    private static NavLogic.StickMode started(StabilizationMode mode) {
        NavAssist assist = pilot(FlightClass.CINE);
        run(assist, new FakeFlight.State().mode(mode), 3);
        return assist.logic().stickMode();
    }

    @Test
    void sportFromTheFlightModeFliesSticksInAngleMode() {
        FakeFlight.State state = new FakeFlight.State().mode(StabilizationMode.ACRO);
        state.stickPitch = 0.5;
        FakeFlight.Output output = run(pilot(FlightClass.TOUR), state, 5);
        assertEquals("S", output.label);
        assertEquals(StabilizationMode.ANGLE, output.mode, "Sport fliegt in Propwashs Angle mit eigener Grenze");
        assertTrue(output.sticks, output.calls.toString());
    }

    @Test
    void ownModeControlsStillWorkAndTheLastChangeCounts() {
        FakeFlight.State state = new FakeFlight.State().mode(StabilizationMode.ANGLE).on(CamControl.MODE_CINE);
        NavAssist assist = pilot(FlightClass.TOUR);
        run(assist, state, 3);
        assertEquals(NavLogic.StickMode.CINE, assist.logic().stickMode(), "eigener Schalter gilt beim Start vor der Flugmodus-Stellung");
        run(assist, state.mode(StabilizationMode.ACRO), 3);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode(), "Flugmodus von Propwash ist die letzte Änderung");
        state.on(CamControl.MODE_CYCLE);
        run(assist, state, 3);
        state.off(CamControl.MODE_CYCLE);
        run(assist, state, 3);
        assertEquals(NavLogic.StickMode.NORMAL, assist.logic().stickMode(), "eigene Taste schaltet weiter");
        run(assist, state.mode(StabilizationMode.HORIZON), 3);
        assertEquals(NavLogic.StickMode.CINE, assist.logic().stickMode());
        state.off(CamControl.MODE_CINE).on(CamControl.MODE_SPORT);
        run(assist, state, 3);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode());
        run(assist, state, 20);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode(), "ohne Änderung bleibt der Modus");
    }

    @Test
    void resetKeepsTheModeUntilTheFlightModeChanges() {
        FakeFlight.State state = new FakeFlight.State().mode(StabilizationMode.HORIZON);
        NavAssist assist = pilot(FlightClass.TOUR);
        run(assist, state, 3);
        state.on(CamControl.MODE_CYCLE);
        run(assist, state, 3);
        state.off(CamControl.MODE_CYCLE);
        run(assist, state, 3);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode());
        assist.reset();
        run(assist, state, 5);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode(), "Zurücksetzen (etwa nach GPS Rescue) behält den Modus");
        run(assist, state.mode(StabilizationMode.ANGLE), 3);
        assertEquals(NavLogic.StickMode.NORMAL, assist.logic().stickMode());
    }

    @Test
    void withoutPilotTheFlightModeOfPropwashIsIgnored() {
        NavAssist server = new NavAssist(FlightClass.TOUR, ALL, false, NavContext.NONE);
        FakeFlight.State state = new FakeFlight.State().mode(StabilizationMode.ACRO);
        run(server, state, 3);
        assertEquals(NavLogic.StickMode.NORMAL, server.logic().stickMode());
        run(server, state.mode(StabilizationMode.HORIZON), 3);
        assertEquals(NavLogic.StickMode.NORMAL, server.logic().stickMode());
    }
}
