package de.lardtom.camdrones.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindWatchTest {
    private static final double TICK = 0.05;

    private static boolean run(WindWatch watch, double from, double seconds, boolean holding, double pitch, double roll, double drift) {
        boolean high = false;
        for (double t = from; t < from + seconds; t += TICK) {
            high = watch.update(t, holding, pitch, roll, drift);
        }
        return high;
    }

    @Test
    void calmHoverStaysQuiet() {
        WindWatch watch = new WindWatch();
        assertFalse(run(watch, 0.0, 30.0, true, 3.0, -2.0, 0.2));
    }

    @Test
    void strongTiltWhileHoldingWarnsAfterSettling() {
        WindWatch watch = new WindWatch();
        double flagged = WindWatch.SETTLE_SECONDS + WindWatch.PERSIST_SECONDS;
        assertFalse(run(watch, 0.0, flagged - 0.2, true, 18.0, 6.0, 0.1));
        assertTrue(run(watch, flagged - 0.2, 1.0, true, 18.0, 6.0, 0.1));
        assertTrue(run(watch, 10.0, 3.0, true, 11.0, 0.0, 0.1));
        assertFalse(run(watch, 13.0, 6.0, true, 4.0, 0.0, 0.1));
    }

    @Test
    void driftWhileHoldingWarns() {
        WindWatch watch = new WindWatch();
        assertTrue(run(watch, 0.0, 8.0, true, 6.0, 0.0, 3.0));
    }

    @Test
    void brakingAfterReleaseIsNoWind() {
        WindWatch watch = new WindWatch();
        assertFalse(run(watch, 0.0, 2.5, true, 30.0, 0.0, 12.0));
        assertFalse(run(watch, 2.5, 6.0, true, 2.0, 0.0, 0.1));
    }

    @Test
    void notHoldingResetsAndNeverWarns() {
        WindWatch watch = new WindWatch();
        assertTrue(run(watch, 0.0, 8.0, true, 25.0, 0.0, 0.0));
        assertFalse(watch.update(8.0, false, 25.0, 0.0, 0.0));
        assertFalse(run(watch, 8.05, 3.0, true, 25.0, 0.0, 0.0));
        assertFalse(run(watch, 20.0, 10.0, false, 40.0, 10.0, 8.0));
        assertFalse(watch.update(1.0, true, 40.0, 0.0, 0.0));
        assertFalse(watch.update(Double.NaN, true, 40.0, 0.0, 0.0));
    }

    @Test
    void centeredSticks() {
        assertTrue(WindWatch.centered(0.05, -0.05, 0.0));
        assertFalse(WindWatch.centered(0.2, 0.0, 0.0));
        assertFalse(WindWatch.centered(0.0, 0.0, -0.3));
    }
}
