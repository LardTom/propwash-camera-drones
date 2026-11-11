package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalServoTone;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.client.gimbal.model.PilotGimbalInput;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalServoToneTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);
    private static final int DRONE = 7;
    private static final boolean[] NONE = new boolean[OperatorGimbalInput.ACTIONS];

    private static List<Long> plays(GimbalServoTone tone, int drone, boolean operator, long from, long to, LongPredicate recentered) {
        List<Long> out = new ArrayList<>();
        for (long tick = from; tick <= to; tick++) {
            if (tone.update(drone, operator, recentered.test(tick), tick)) {
                out.add(tick);
            }
        }
        return out;
    }

    private static boolean[] press(int number) {
        boolean[] actions = new boolean[OperatorGimbalInput.ACTIONS];
        actions[number - 1] = true;
        return actions;
    }

    @Test
    void movingTheGimbalNeverPlays() {
        PilotGimbalInput pilot = new PilotGimbalInput();
        pilot.takeOver(GimbalSetting.CENTER, 0.0, 0.0, false, false);
        GimbalServoTone tone = new GimbalServoTone();
        List<Long> plays = new ArrayList<>();
        double dial = 0.0;
        for (long tick = 0; tick < 120; tick++) {
            dial = Math.max(-1.0, dial - 0.05);
            pilot.update(CINE, dial, Math.min(1.0, tick * 0.02), tick * 0.02 - 1.0, false, false, false, tick * 3.0);
            if (tone.update(DRONE, false, pilot.recentered(), tick)) {
                plays.add(tick);
            }
        }
        assertEquals(-90.0, pilot.setting().tilt(), 1.0E-9);
        assertEquals(List.of(), plays, "Neigen, Schwenken, Zoomen und Gieren als Pilot");
        OperatorGimbalInput operator = new OperatorGimbalInput();
        operator.takeOver(GimbalSetting.CENTER, NONE);
        GimbalServoTone crew = new GimbalServoTone();
        List<Long> crewPlays = new ArrayList<>();
        for (long tick = 0; tick < 120; tick++) {
            boolean recentered = operator.update(CINE, new double[]{1.0, -1.0, 0.0, tick / 120.0}, NONE, 0.05, tick * 50L, tick * 3.0)
                    .contains(OperatorGimbalInput.Event.RECENTER);
            if (crew.update(DRONE, true, recentered, tick)) {
                crewPlays.add(tick);
            }
        }
        assertEquals(List.of(0L), crewPlays, "als Kameramann nur bei der Übernahme, nicht beim Bewegen");
    }

    @Test
    void recenterPlaysOnce() {
        GimbalServoTone tone = new GimbalServoTone();
        assertEquals(List.of(30L), plays(tone, DRONE, false, 0, 80, tick -> tick == 30), "Pilot zentriert");
        assertEquals(List.of(100L), plays(tone, DRONE, false, 81, 140, tick -> tick == 100));
    }

    @Test
    void operatorTakeOverPlaysOnceButAPilotFlightDoesNot() {
        GimbalServoTone tone = new GimbalServoTone();
        assertEquals(List.of(), plays(tone, DRONE, false, 0, 40, tick -> false), "Pilot fliegt los");
        assertEquals(List.of(41L), plays(tone, DRONE, true, 41, 100, tick -> false), "Kameramann übernimmt");
        assertEquals(List.of(), plays(tone, DRONE, false, 101, 140, tick -> false), "Pilot nimmt zurück");
        assertEquals(List.of(141L), plays(tone, DRONE + 1, true, 141, 180, tick -> false), "Kameramann einer anderen Drohne");
        tone.reset();
        assertEquals(List.of(181L), plays(tone, DRONE + 1, true, 181, 200, tick -> false), "nach dem Zurücksetzen wieder Übernahme");
    }

    @Test
    void tonesDoNotOverlapWithinOneClip() {
        GimbalServoTone tone = new GimbalServoTone();
        assertEquals(List.of(0L, 16L), plays(tone, DRONE, true, 0, 20, tick -> tick == 3 || tick == 16),
                "Übernahme und Zentrieren kurz danach: ein Ton je Clip");
        assertEquals(List.of(40L, 56L), plays(tone, DRONE, true, 21, 60, tick -> tick == 40 || tick == 45 || tick == 56),
                "zweimal zentrieren (senkrecht nach unten) in 0,25 s: ein Ton");
    }

    @Test
    void operatorActionTwoAndPilotRecenterKeyFeedTheTone() {
        OperatorGimbalInput operator = new OperatorGimbalInput();
        operator.takeOver(GimbalSetting.CENTER, NONE);
        assertFalse(operator.update(TOUR, new double[4], NONE, 0.05, 0L, 0.0).contains(OperatorGimbalInput.Event.RECENTER));
        assertTrue(operator.update(TOUR, new double[4], press(2), 0.05, 50L, 0.0).contains(OperatorGimbalInput.Event.RECENTER));
        assertFalse(operator.update(TOUR, new double[4], press(2), 0.05, 100L, 0.0).contains(OperatorGimbalInput.Event.RECENTER),
                "gehalten zählt nicht doppelt");
        PilotGimbalInput pilot = new PilotGimbalInput();
        pilot.takeOver(GimbalSetting.CENTER.withTilt(-40.0), 0.0, 0.0, false, false);
        pilot.update(TOUR, 0.0, 0.0, -1.0, false, false, false, 0.0);
        assertFalse(pilot.recentered());
        pilot.update(TOUR, 0.0, 0.0, -1.0, false, false, true, 0.0);
        assertTrue(pilot.recentered(), "Taste Gimbal zentrieren");
        pilot.update(TOUR, 0.0, 0.0, -1.0, false, false, true, 0.0);
        assertFalse(pilot.recentered(), "gehalten zählt nicht doppelt");
    }

    @Test
    void zeroVolumeIsOff() {
        assertFalse(GimbalServoTone.audible(0.0));
        assertFalse(GimbalServoTone.audible(Double.NaN));
        assertTrue(GimbalServoTone.audible(0.05));
    }
}
