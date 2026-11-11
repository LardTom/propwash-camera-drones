package de.lardtom.camdrones.client.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstacleBeeperTest {
    private static final double TICK = 0.05;

    private static List<Double> beeps(double distance, boolean warn, double seconds) {
        ObstacleBeeper beeper = new ObstacleBeeper();
        List<Double> times = new ArrayList<>();
        for (int i = 0; i * TICK <= seconds; i++) {
            double time = 100.0 + i * TICK;
            if (beeper.update(time, distance, warn)) {
                times.add(time);
            }
        }
        return times;
    }

    @Test
    void silentWithoutWarningOrFarAway() {
        assertTrue(beeps(3.0, false, 5.0).isEmpty());
        assertTrue(beeps(8.0, true, 5.0).isEmpty());
        assertTrue(beeps(12.0, true, 5.0).isEmpty());
        assertTrue(beeps(Double.NaN, true, 5.0).isEmpty());
    }

    @Test
    void beepsFasterTheCloser() {
        int far = beeps(7.5, true, 10.0).size();
        int mid = beeps(4.5, true, 10.0).size();
        int near = beeps(2.0, true, 10.0).size();
        int stop = beeps(1.0, true, 10.0).size();
        assertTrue(far < mid && mid < near && near <= stop, far + " " + mid + " " + near + " " + stop);
        assertEquals(14, far, 1);
        assertEquals(101, stop, 1);
        assertTrue(ObstacleBeeper.interval(7.9) > ObstacleBeeper.interval(4.0));
        assertEquals(ObstacleBeeper.FASTEST, ObstacleBeeper.interval(0.5), 1.0E-9);
        assertEquals(ObstacleBeeper.SLOWEST, ObstacleBeeper.interval(8.0), 1.0E-9);
        assertTrue(ObstacleBeeper.pitch(1.5) > ObstacleBeeper.pitch(7.0));
    }

    @Test
    void firstBeepImmediatelyAndResetWhenClear() {
        ObstacleBeeper beeper = new ObstacleBeeper();
        assertTrue(beeper.update(0.0, 5.0, true));
        assertFalse(beeper.update(0.05, 5.0, true));
        assertTrue(beeper.active());
        assertFalse(beeper.update(0.1, 9.0, true));
        assertFalse(beeper.active());
        assertTrue(beeper.update(0.15, 5.0, true));
        assertTrue(beeper.update(0.1, 5.0, true));
    }

    @Test
    void approachingShortensTheGap() {
        ObstacleBeeper beeper = new ObstacleBeeper();
        assertTrue(beeper.update(0.0, 7.0, true));
        assertFalse(beeper.update(0.1, 7.0, true));
        assertTrue(beeper.update(0.15, 1.6, true));
    }
}
