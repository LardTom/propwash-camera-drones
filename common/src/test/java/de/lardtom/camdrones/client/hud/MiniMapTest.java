package de.lardtom.camdrones.client.hud;

import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMapTest {
    private static final double EPS = 1.0E-9;

    @Test
    void headingUpMap() {
        MiniMap.Point north = MiniMap.project(0.0, 0.0, 0.0, 0.0, -10.0, 20.0, 20.0);
        assertEquals(0.0, north.x(), EPS);
        assertEquals(-10.0, north.y(), EPS);
        MiniMap.Point east = MiniMap.project(0.0, 0.0, 0.0, 10.0, 0.0, 20.0, 20.0);
        assertEquals(10.0, east.x(), EPS);
        assertEquals(0.0, east.y(), EPS);
        MiniMap.Point aheadWhenFacingEast = MiniMap.project(0.0, 0.0, 90.0, 10.0, 0.0, 20.0, 20.0);
        assertEquals(0.0, aheadWhenFacingEast.x(), EPS);
        assertEquals(-10.0, aheadWhenFacingEast.y(), EPS);
        MiniMap.Point behindWhenFacingSouth = MiniMap.project(5.0, 5.0, 180.0, 5.0, -5.0, 20.0, 20.0);
        assertEquals(0.0, behindWhenFacingSouth.x(), EPS);
        assertEquals(10.0, behindWhenFacingSouth.y(), EPS);
        assertFalse(north.clamped());
    }

    @Test
    void agreesWithTheHomeBearingOfTheTelemetry() {
        for (double heading = 0.0; heading < 360.0; heading += 15.0) {
            FlightTelemetry t = new FlightTelemetry(true, "ANGLE", Optional.empty(), 3.0, 70.0, -4.0, 0.0, 0.0, heading, 0.0, 0.0, 0.5, 16.0,
                    4.0, 0.8, true, -27.0, 64.0, 36.0, true, 12, 1.0, 1.0, 10.0, 0.0, 0.0, 0.0, 0.5);
            MiniMap.Point home = MiniMap.project(t.x(), t.z(), heading, t.homeX(), t.homeZ(), 100.0, 50.0);
            double screenBearing = Math.toDegrees(Math.atan2(home.x(), -home.y()));
            double expected = t.homeBearingDeg() - heading;
            double diff = Math.abs(((screenBearing - expected) % 360.0 + 540.0) % 360.0 - 180.0);
            assertTrue(diff < 1.0E-6, "Kurs " + heading + ": " + screenBearing + " statt " + expected);
        }
    }

    @Test
    void farHomeIsClampedToTheEdge() {
        MiniMap.Point far = MiniMap.project(0.0, 0.0, 0.0, 300.0, -400.0, 100.0, 30.0);
        assertTrue(far.clamped());
        assertEquals(30.0, Math.max(Math.abs(far.x()), Math.abs(far.y())), EPS);
        assertEquals(0.75, -far.x() / far.y(), EPS);
    }

    @Test
    void radiusGrowsWithTheHomeDistance() {
        assertEquals(25.0, MiniMap.radius(0.0));
        assertEquals(25.0, MiniMap.radius(Double.NaN));
        assertEquals(25.0, MiniMap.radius(20.0));
        assertEquals(50.0, MiniMap.radius(21.0));
        assertEquals(200.0, MiniMap.radius(150.0));
        assertEquals(5000.0, MiniMap.radius(1.0E6));
    }
}
