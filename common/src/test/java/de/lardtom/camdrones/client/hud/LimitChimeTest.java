package de.lardtom.camdrones.client.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitChimeTest {
    private static final double TICK = 0.05;

    private static List<Double> chimes(LimitChime chime, double from, double seconds, boolean limit) {
        List<Double> times = new ArrayList<>();
        for (int i = 0; i * TICK < seconds - 1.0E-9; i++) {
            double time = from + i * TICK;
            if (chime.update(time, limit)) {
                times.add(time);
            }
        }
        return times;
    }

    @Test
    void silentWithoutLimit() {
        assertTrue(chimes(new LimitChime(), 10.0, 5.0, false).isEmpty());
    }

    @Test
    void oneToneWhenTheLimitIsReachedAndNoneWhileHeld() {
        LimitChime chime = new LimitChime();
        assertEquals(List.of(10.0), chimes(chime, 10.0, 8.0, true));
        assertFalse(chime.armed());
    }

    @Test
    void shortFlickerDoesNotRepeatTheTone() {
        LimitChime chime = new LimitChime();
        assertEquals(1, chimes(chime, 10.0, 1.0, true).size());
        assertTrue(chimes(chime, 11.0, 0.5, false).isEmpty());
        assertTrue(chimes(chime, 11.5, 1.0, true).isEmpty());
    }

    @Test
    void reachingTheLimitAgainAfterOneSecondPlaysAgain() {
        LimitChime chime = new LimitChime();
        assertEquals(1, chimes(chime, 10.0, 1.0, true).size());
        assertTrue(chimes(chime, 11.0, LimitChime.REARM + TICK, false).isEmpty());
        assertTrue(chime.armed());
        assertEquals(List.of(12.1), chimes(chime, 12.1, 1.0, true).stream().map(t -> Math.round(t * 100.0) / 100.0).toList());
    }

    @Test
    void invalidTimeOrResetRearms() {
        LimitChime chime = new LimitChime();
        assertTrue(chime.update(10.0, true));
        assertFalse(chime.update(Double.NaN, true));
        assertTrue(chime.armed());
        assertTrue(chime.update(10.1, true));
        chime.reset();
        assertTrue(chime.update(10.2, true));
    }

    @Test
    void timeGoingBackwardsAfterAWorldChangeStillRearms() {
        LimitChime chime = new LimitChime();
        assertTrue(chime.update(500.0, true));
        assertFalse(chime.update(500.5, false));
        assertFalse(chime.update(2.0, false));
        assertFalse(chime.update(3.0 - 0.01, false));
        assertFalse(chime.armed());
        assertFalse(chime.update(3.0, false));
        assertTrue(chime.armed());
    }
}
