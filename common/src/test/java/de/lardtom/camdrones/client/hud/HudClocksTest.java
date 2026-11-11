package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.nav.FlightClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudClocksTest {
    @Test
    void statusIsFreshOnlyWhileTheAssistKeepsReporting() {
        StatusWatch watch = new StatusWatch();
        Object first = new Object();
        watch.observe(first, 100L);
        assertTrue(watch.fresh(first, 100L));
        assertTrue(watch.fresh(first, 100L + StatusWatch.FRESH_TICKS));
        assertFalse(watch.fresh(first, 101L + StatusWatch.FRESH_TICKS));
        Object second = new Object();
        watch.observe(second, 200L);
        assertFalse(watch.fresh(first, 200L));
        assertTrue(watch.fresh(second, 205L));
        watch.observe(second, 300L);
        assertFalse(watch.fresh(second, 300L));
        watch.observe(second, 50L);
        assertTrue(watch.fresh(second, 50L));
        watch.observe(null, 51L);
        assertFalse(watch.fresh(second, 51L));
        assertFalse(watch.fresh(null, 51L));
    }

    @Test
    void recordingClockCountsFromTheStart() {
        RecordingClock clock = new RecordingClock();
        assertTrue(Double.isNaN(clock.update(false, 1000L)));
        assertEquals(0.0, clock.update(true, 5000L));
        assertEquals(42.5, clock.update(true, 47500L));
        assertTrue(clock.running());
        assertTrue(Double.isNaN(clock.update(false, 48000L)));
        assertFalse(clock.running());
        assertEquals(0.0, clock.update(true, 60000L));
    }

    @Test
    void flightLeftUsesTheModelUntilMeasured() {
        FlightLeft left = new FlightLeft();
        assertTrue(Double.isNaN(left.remaining(0.8, 0.0, 0.0)));
        left.sample(FlightClass.TOUR, 0.0, 0.8);
        assertEquals(FlightTimeEstimate.modelSeconds(FlightClass.TOUR, 0.8, 0.0, 0.0), left.remaining(0.8, 0.0, 0.0), 1.0E-9);
        assertTrue(left.remaining(0.8, 12.0, 0.0) < left.remaining(0.8, 0.0, 0.0));
        assertTrue(Double.isNaN(left.remaining(Double.NaN, 0.0, 0.0)));
        assertFalse(left.measured());
    }

    @Test
    void flightLeftShowsTheWorkbenchValueUntilEnoughIsMeasured() {
        FlightLeft left = new FlightLeft();
        left.bench(25.6 * 60.0);
        double charge = 1.0;
        for (int tick = 0; tick < 20 * 20; tick++) {
            left.sample(FlightClass.TOUR, tick / 20.0, charge, true);
            charge -= 0.00003;
            assertEquals(FlightTimeEstimate.benchSeconds(25.6 * 60.0, charge + 0.00003), left.remaining(charge + 0.00003, 6.0, 1.0), 1.0E-6,
                    "Tick " + tick);
        }
        assertFalse(left.measured());
        assertTrue(left.remaining(charge, 0.0, 0.0) < 99.0 * 60.0);
        left.reset();
        assertTrue(Double.isNaN(left.benchSeconds()));
        left.bench(-5.0);
        assertTrue(Double.isNaN(left.benchSeconds()));
    }

    @Test
    void airborneNeedsArmedAndHeightOrMotion() {
        assertFalse(FlightLeft.airborne(false, 10.0, 5.0, 1.0));
        assertFalse(FlightLeft.airborne(true, 0.1, 0.0, 0.0), "am Boden scharf geschaltet");
        assertTrue(FlightLeft.airborne(true, 4.0, 0.0, 0.0));
        assertTrue(FlightLeft.airborne(true, 0.0, 3.0, 0.0));
        assertTrue(FlightLeft.airborne(true, 0.0, 0.0, -1.0));
    }

    @Test
    void flightLeftLearnsTheRealDrain() {
        FlightLeft left = new FlightLeft();
        double charge = 0.9;
        for (int tick = 0; tick <= 20 * 60; tick++) {
            left.sample(FlightClass.TOUR, tick / 20.0, charge);
            charge -= 0.0001;
        }
        assertTrue(left.measured());
        double rate = 0.0001 * 20.0;
        assertEquals((charge + 0.0001 - FlightTimeEstimate.RESERVE) / rate, left.remaining(charge + 0.0001, 0.0, 0.0), 5.0);
        left.sample(FlightClass.CINE, 61.0, 0.95);
        assertFalse(left.measured());
        left.reset();
        assertTrue(Double.isNaN(left.remaining(0.5, 0.0, 0.0)));
    }
}
