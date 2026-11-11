package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.VerticalControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerticalControlTest {
    private static NavInput air(double time, double y, double vy) {
        return new NavInput().time(time).position(0.0, y, 0.0).velocity(0.0, vy, 0.0).heightAboveGround(10.0).grounded(false);
    }

    @Test
    void holdTargetMovesIntoTheEstimateAndCoversTheVelocityBias() {
        VerticalControl control = new VerticalControl();
        NavOutput out = new NavOutput();
        out.holdAltitude(50.0);
        control.apply(air(0.0, 50.0, 0.0), out, 50.3, 0.45);
        assertEquals(50.0 + 0.3 + 0.45 / VerticalControl.ALTITUDE_GAIN, out.altitudeTarget(), 1.0E-9);
    }

    @Test
    void sagLiftsTheTargetButOvershootIsLeftToPropwash() {
        VerticalControl control = new VerticalControl();
        NavOutput sag = new NavOutput();
        sag.holdAltitude(50.0);
        control.apply(air(0.0, 50.0, -0.4), sag, 50.0, -0.4);
        assertEquals(50.0 + VerticalControl.RATE_GAIN * 0.4 / VerticalControl.ALTITUDE_GAIN, sag.altitudeTarget(), 1.0E-9);
        VerticalControl rising = new VerticalControl();
        NavOutput up = new NavOutput();
        up.holdAltitude(50.0);
        rising.apply(air(0.0, 49.0, 1.5), up, 49.0, 1.5);
        assertEquals(50.0, up.altitudeTarget(), 1.0E-9);
    }

    @Test
    void whileBrakingForAnObstacleARiseIsPulledBack() {
        VerticalControl control = new VerticalControl();
        NavOutput hold = new NavOutput();
        hold.holdAltitude(50.0);
        control.apply(air(0.0, 50.3, 1.0), hold, 50.3, 1.0, true);
        double want = VerticalControl.ALTITUDE_GAIN * (50.0 - 50.3);
        assertEquals(50.0 + VerticalControl.settle(want, 1.0) / VerticalControl.ALTITUDE_GAIN, hold.altitudeTarget(), 1.0E-9);
        assertEquals(-VerticalControl.RATE_LIMIT, VerticalControl.settle(want, 1.0), 1.0E-9);
        NavOutput settling = new NavOutput();
        settling.climb(0.0);
        control.apply(air(0.01, 50.3, 0.5), settling, 50.3, 0.5, true);
        assertEquals(-VerticalControl.BRAKE_RATE_GAIN * 0.5, settling.climbRate(), 1.0E-9);
        NavOutput normal = new NavOutput();
        normal.holdAltitude(50.0);
        control.apply(air(0.02, 50.3, 1.0), normal, 50.3, 1.0, false);
        assertEquals(50.0, normal.altitudeTarget(), 1.0E-9, "ohne Bremsen bleibt das Überschwingen bei Propwash");
    }

    @Test
    void stickRatesCatchUpOnlyUpwards() {
        VerticalControl control = new VerticalControl();
        NavOutput sinkTooFast = new NavOutput();
        sinkTooFast.climb(-6.0);
        control.apply(air(0.0, 50.0, -7.0), sinkTooFast, 50.0, -7.0);
        assertEquals(-6.0 + VerticalControl.STICK_RATE_GAIN * 1.0, sinkTooFast.climbRate(), 1.0E-9);
        NavOutput startSinking = new NavOutput();
        startSinking.climb(-6.0);
        control.apply(air(0.01, 50.0, -1.0), startSinking, 50.0, -1.0);
        assertEquals(-6.0, startSinking.climbRate(), 1.0E-9, "kein Schub nach unten");
        NavOutput climbLagging = new NavOutput();
        climbLagging.climb(6.0);
        control.apply(air(0.02, 50.0, 3.0), climbLagging, 50.0, 3.0);
        assertEquals(6.0 + VerticalControl.RATE_LIMIT, climbLagging.climbRate(), 1.0E-9);
    }

    @Test
    void onTheGroundOnlyTheEstimateOffsetApplies() {
        VerticalControl control = new VerticalControl();
        NavInput ground = new NavInput().time(0.0).position(0.0, 64.0, 0.0).velocity(0.0, 0.0, 0.0).heightAboveGround(0.0).grounded(true);
        NavOutput hold = new NavOutput();
        hold.holdAltitude(64.0);
        control.apply(ground, hold, 63.7, 0.5);
        assertEquals(63.7, hold.altitudeTarget(), 1.0E-9, "am Boden kein Abheben durch den Ausgleich");
        NavOutput rest = new NavOutput();
        rest.climb(0.0);
        control.apply(ground.time(0.01), rest, 63.7, 0.5);
        assertEquals(0.0, rest.climbRate(), 1.0E-9);
    }

    @Test
    void withoutEstimateNothingChanges() {
        VerticalControl control = new VerticalControl();
        NavOutput out = new NavOutput();
        out.holdAltitude(50.0);
        control.apply(air(0.0, 49.0, 0.0), out, Double.NaN, Double.NaN);
        assertEquals(50.0, out.altitudeTarget(), 1.0E-12);
    }
}
