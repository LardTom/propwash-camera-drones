package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorGimbalInputTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);
    private static final boolean[] NONE = new boolean[4];

    private static boolean[] press(int number) {
        boolean[] actions = new boolean[4];
        actions[number - 1] = true;
        return actions;
    }

    private static double[] sticks(double pan, double tilt, double throttle) {
        return new double[]{pan, tilt, 0.0, throttle};
    }

    @Test
    void rightStickTiltsWithRateAndDeadband() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        for (int i = 0; i < 20; i++) {
            input.update(TOUR, sticks(0.0, -1.0, 0.0), NONE, 0.05, i * 50L, 0.0);
        }
        assertEquals(-60.0, input.setting().tilt(), 1.0E-9, "60 °/s eine Sekunde lang");
        input.update(TOUR, sticks(0.0, 0.05, 0.0), NONE, 1.0, 2_000L, 0.0);
        assertEquals(-60.0, input.setting().tilt(), 1.0E-9, "Totzone");
        for (int i = 0; i < 40; i++) {
            input.update(TOUR, sticks(0.0, -1.0, 0.0), NONE, 0.05, 3_000L + i * 50L, 0.0);
        }
        assertEquals(-90.0, input.setting().tilt(), "Anschlag unten");
        input.update(TOUR, sticks(1.0, 0.0, 0.0), NONE, 0.2, 6_000L, 0.0);
        assertEquals(18.0, input.setting().heading(), 1.0E-9, "Tour-Gimbal schwenkt innerhalb der Grenze");
        input.update(TOUR, sticks(1.0, 0.0, 0.0), NONE, 0.2, 6_200L, 0.0);
        assertEquals(GimbalLimits.LIMITED_YAW, input.setting().heading(), 1.0E-9, "bis zum Anschlag");
        assertEquals(0.0, input.setting().pan(), "Schwenk steht in der Weltrichtung");
    }

    @Test
    void cinePansAndThrottleZooms() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        for (int i = 0; i < 10; i++) {
            input.update(CINE, sticks(1.0, 0.0, 0.75), NONE, 0.1, i * 100L, 0.0);
        }
        assertEquals(90.0, input.setting().heading(), 1.0E-9);
        assertTrue(input.setting().locked());
        assertEquals(0.75, input.setting().zoom());
        input.update(CINE, sticks(0.0, 0.0, -0.4), NONE, 0.1, 1_100L, 0.0);
        assertEquals(0.0, input.setting().zoom());
    }

    @Test
    void actionOneKeepsTheWorldHeading() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        input.takeOver(new GimbalSetting(GimbalSetting.Mode.FOLLOW, -10.0, 30.0, 0.0, 0.0), NONE);
        List<OperatorGimbalInput.Event> events = input.update(CINE, sticks(0, 0, 0), press(1), 0.0, 0L, 200.0);
        assertTrue(events.isEmpty(), "Aktion 1 hat für den Kameramann keine Funktion: " + events);
        assertTrue(input.setting().locked(), "Kameramann schwenkt immer weltbezogen");
        assertEquals(230.0, input.setting().heading(), 1.0E-9, "Übernahme behält den Blick");
        input.update(CINE, sticks(0, 0, 0), NONE, 0.0, 20L, 250.0);
        input.update(CINE, sticks(0, 0, 0), press(1), 0.0, 30L, 250.0);
        assertTrue(input.setting().locked(), "kein Folgen für den Kameramann");
        assertEquals(230.0, input.setting().heading(), 1.0E-9, "Gieren der Drohne dreht den Blick nicht");
    }

    @Test
    void actionTwoRecentersAndThenPointsDown() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        input.takeOver(new GimbalSetting(GimbalSetting.Mode.FOLLOW, -40.0, 50.0, 0.0, 0.0), NONE);
        input.update(CINE, sticks(0, 0, 0), NONE, 0.0, 0L, 10.0);
        assertEquals(60.0, input.setting().heading(), 1.0E-9);
        input.update(CINE, sticks(0, 0, 0), press(2), 0.0, 5L, 120.0);
        assertEquals(0.0, input.setting().tilt());
        assertEquals(120.0, input.setting().heading(), 1.0E-9, "zentrieren setzt die Weltrichtung auf Drohnen-Vorne");
        assertTrue(input.setting().locked());
        input.update(CINE, sticks(0, 0, 0), NONE, 0.0, 10L, 0.0);
        List<OperatorGimbalInput.Event> events = input.update(CINE, sticks(0, 0, 0), press(2), 0.0, 20L, 0.0);
        assertEquals(List.of(OperatorGimbalInput.Event.RECENTER), events);
        assertEquals(-90.0, input.setting().tilt());
    }

    @Test
    void actionThreeRecordsAndShortActionFourTakesAPhoto() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        assertEquals(List.of(OperatorGimbalInput.Event.RECORD), input.update(TOUR, sticks(0, 0, 0), press(3), 0.0, 0L, 0.0));
        input.update(TOUR, sticks(0, 0, 0), NONE, 0.0, 10L, 0.0);
        assertTrue(input.update(TOUR, sticks(0, 0, 0), press(4), 0.0, 100L, 0.0).isEmpty());
        assertTrue(input.update(TOUR, sticks(0, 0, 0), press(4), 0.0, 500L, 0.0).isEmpty());
        assertEquals(List.of(OperatorGimbalInput.Event.PHOTO), input.update(TOUR, sticks(0, 0, 0), NONE, 0.0, 600L, 0.0));
    }

    @Test
    void longActionFourHandsTheCameraBackOnce() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        List<OperatorGimbalInput.Event> all = new ArrayList<>();
        for (long t = 0; t <= 2_000L; t += 50L) {
            all.addAll(input.update(TOUR, sticks(0, 0, 0), press(4), 0.05, t, 0.0));
        }
        all.addAll(input.update(TOUR, sticks(0, 0, 0), NONE, 0.05, 2_050L, 0.0));
        assertEquals(List.of(OperatorGimbalInput.Event.LEAVE), all);
    }

    @Test
    void buttonHeldDuringTakeOverDoesNothing() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        input.takeOver(GimbalSetting.CENTER, press(4));
        List<OperatorGimbalInput.Event> all = new ArrayList<>();
        for (long t = 0; t <= 1_500L; t += 100L) {
            all.addAll(input.update(TOUR, sticks(0, 0, 0), press(4), 0.1, t, 0.0));
        }
        all.addAll(input.update(TOUR, sticks(0, 0, 0), NONE, 0.1, 1_600L, 0.0));
        assertTrue(all.isEmpty(), all.toString());
    }

    @Test
    void deadbandIsScaledToFullRange() {
        assertEquals(0.0, OperatorGimbalInput.deadband(0.06));
        assertEquals(1.0, OperatorGimbalInput.deadband(1.0));
        assertEquals(-1.0, OperatorGimbalInput.deadband(-3.0));
        assertEquals(0.0, OperatorGimbalInput.deadband(Double.NaN));
        assertTrue(OperatorGimbalInput.deadband(0.5) < 0.5);
    }

    @Test
    void operatorTurnsFpvIntoTheWorldHeading() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        input.takeOver(new GimbalSetting(GimbalSetting.Mode.FPV, -20.0, 0.0, 0.0, 0.0), NONE);
        input.update(CINE, sticks(0.0, -1.0, 0.0), NONE, 0.1, 0L, 90.0);
        assertTrue(input.setting().locked(), "kein FPV für den Kameramann");
        assertEquals(90.0, input.setting().heading(), 1.0E-9, "übernimmt die Blickrichtung des FPV-Bilds");
        assertEquals(-26.0, input.setting().tilt(), 1.0E-9, "Neigung bleibt steuerbar");
        for (int i = 1; i <= 4; i++) {
            input.update(CINE, sticks(0, 0, 0), NONE, 0.1, i * 100L, 90.0 + 30.0 * i);
        }
        assertEquals(90.0, input.setting().heading(), 1.0E-9, "Gieren der Drohne dreht den Blick nicht");
    }

    @Test
    void limitedGimbalPansBackFromTheStopRightAway() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        input.takeOver(GimbalSetting.CENTER, NONE);
        input.update(TOUR, sticks(0, 0, 0), NONE, 0.0, 0L, 0.0);
        input.update(TOUR, sticks(0, 0, 0), NONE, 0.0, 10L, 40.0);
        assertEquals(0.0, input.setting().heading(), 1.0E-9, "ohne Knüppel bleibt die Weltrichtung, der Gimbal hält am Anschlag");
        assertTrue(TOUR.atStop(input.setting().heading(), 40.0));
        input.update(TOUR, sticks(1.0, 0, 0), NONE, 0.1, 20L, 40.0);
        assertEquals(24.0, input.setting().heading(), 1.0E-9, "Schwenk startet am Anschlag 15°");
        assertFalse(TOUR.atStop(input.setting().heading(), 40.0));
        input.update(TOUR, sticks(-1.0, 0, 0), NONE, 0.25, 30L, 40.0);
        assertEquals(15.0, input.setting().heading(), 1.0E-9, "nicht über den Anschlag hinaus");
    }

    @Test
    void limitsClampTheHeadingAndReportTheStop() {
        assertEquals(25.0, TOUR.heading(80.0, 0.0), 1.0E-9);
        assertEquals(335.0, TOUR.heading(-80.0, 0.0), 1.0E-9);
        assertEquals(190.0, TOUR.heading(190.0, 200.0), 1.0E-9);
        assertEquals(80.0, CINE.heading(80.0, 0.0), 1.0E-9);
        assertEquals(10.0, TOUR.heading(Double.NaN, 10.0), 1.0E-9);
        assertTrue(TOUR.atStop(25.0, 0.0));
        assertTrue(TOUR.atStop(0.0, 40.0));
        assertFalse(TOUR.atStop(10.0, 0.0));
        assertFalse(CINE.atStop(179.0, 0.0), "Kino schwenkt 360°");
    }
}
