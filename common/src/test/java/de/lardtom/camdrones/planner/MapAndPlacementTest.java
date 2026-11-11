package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.planner.model.FlightRecorder;
import de.lardtom.camdrones.planner.model.MapView;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.WaypointPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapAndPlacementTest {
    private static final Rect MAP = new Rect(10, 20, 200, 160);

    @Test
    void mapTransformsRoundTrip() {
        MapView view = new MapView(100.0, -40.0, MapView.DEFAULT_ZOOM);
        assertEquals(110.0, view.screenX(100.0, MAP), 1.0E-9);
        assertEquals(100.0, view.screenY(-40.0, MAP), 1.0E-9);
        for (double wx : new double[]{-300.0, 0.0, 87.5}) {
            assertEquals(wx, view.worldX(view.screenX(wx, MAP), MAP), 1.0E-9);
            assertEquals(wx, view.worldZ(view.screenY(wx, MAP), MAP), 1.0E-9);
        }
        assertTrue(view.screenY(-50.0, MAP) < view.screenY(-40.0, MAP), "Norden oben");
        assertTrue(view.screenX(110.0, MAP) > view.screenX(100.0, MAP), "Osten rechts");
    }

    @Test
    void zoomKeepsThePointUnderTheCursor() {
        MapView view = new MapView(0.0, 0.0, MapView.DEFAULT_ZOOM);
        double px = 40.0;
        double py = 150.0;
        double wx = view.worldX(px, MAP);
        double wz = view.worldZ(py, MAP);
        assertTrue(view.zoomBy(1, px, py, MAP));
        assertEquals(0.5, view.blocksPerPixel());
        assertEquals(wx, view.worldX(px, MAP), 1.0E-9);
        assertEquals(wz, view.worldZ(py, MAP), 1.0E-9);
        assertTrue(view.zoomBy(-3, px, py, MAP));
        assertEquals(4.0, view.blocksPerPixel());
        assertFalse(new MapView(0, 0, MapView.ZOOMS.length - 1).zoomBy(-1, px, py, MAP));
        view.pan(10.0, -5.0);
        assertEquals(wx - 40.0, view.worldX(px, MAP), 1.0E-9);
    }

    @Test
    void fitShowsAllPoints() {
        MapView view = new MapView();
        List<Waypoint> points = List.of(Waypoint.at(0, 70, 0), Waypoint.at(300, 70, 200));
        view.fit(points, List.of(PointOfInterest.at(-20, 64, 0, 10)), 0, 0, MAP);
        for (Waypoint w : points) {
            assertTrue(MAP.contains(view.screenX(w.x(), MAP), view.screenY(w.z(), MAP)), "Punkt sichtbar: " + w);
        }
        view.fit(List.of(), List.of(), 55.0, 66.0, MAP);
        assertEquals(55.0, view.centerX());
        assertEquals(MapView.DEFAULT_ZOOM, view.zoom());
    }

    @Test
    void blockClickPutsWaypointAboveTheBlockCentre() {
        Waypoint w = WaypointPlacement.aboveBlock(12, 65.0, -4, 10);
        assertEquals(12.5, w.x());
        assertEquals(75.0, w.y());
        assertEquals(-3.5, w.z());
        assertEquals(WaypointPlacement.MAX_Y, WaypointPlacement.aboveBlock(0, 5000.0, 0, 10).y());
        assertEquals(65.5 + 1.0, WaypointPlacement.aboveBlock(0, 65.5, 0, 0).y(), "mindestens 1 m");
    }

    @Test
    void flightWaypointKeepsHeadingAndTilt() {
        Waypoint w = WaypointPlacement.inFlight(1.25, 90.0, 2.5, 271.6, -37.4);
        assertEquals(Waypoint.Heading.FIXED, w.heading());
        assertEquals(272, w.yawDeg());
        assertEquals(-37, w.tiltDeg());
        assertEquals(MissionLimits.MIN_TILT, WaypointPlacement.inFlight(0, 70, 0, 0, -400).tiltDeg());
        assertEquals(0, WaypointPlacement.inFlight(0, 70, 0, Double.NaN, Double.NaN).tiltDeg());
        assertEquals(30, WaypointPlacement.clampTilt(45, FlightClass.TOUR));
        assertEquals(45, WaypointPlacement.clampTilt(45, FlightClass.CINE));
    }

    @Test
    void flightWaypointTakesTheGimbalTiltOfThePilot() {
        assertEquals(-42.0, WaypointPlacement.flightTilt(-42.0, Double.NaN), "Handflug: Neigung des Gimbals");
        assertEquals(-42, WaypointPlacement.inFlight(0, 70, 0, 0, WaypointPlacement.flightTilt(-42.0, Double.NaN)).tiltDeg());
        assertEquals(-30.0, WaypointPlacement.flightTilt(-30.0, -10.0), "Gimbal vor der Vorgabe der Flughilfe");
        assertEquals(-15.0, WaypointPlacement.flightTilt(Double.NaN, -15.0), "ohne Gimbal-Zustand die Vorgabe");
        assertEquals(0.0, WaypointPlacement.flightTilt(Double.NaN, Double.NaN));
        assertEquals(0.0, WaypointPlacement.flightTilt(Double.POSITIVE_INFINITY, Double.NaN));
    }

    @Test
    void markerPickTakesTheNearestAlongTheRay() {
        List<Waypoint> list = List.of(Waypoint.at(0, 70, 10), Waypoint.at(0, 70, 5), Waypoint.at(0, 70, -5), Waypoint.at(3, 70, 20));
        OptionalInt hit = WaypointPlacement.pick(list, 0, 70, 0, 0, 0, 1, 64, WaypointPlacement.MARKER_RADIUS);
        assertEquals(1, hit.getAsInt());
        assertTrue(WaypointPlacement.pick(list, 0, 70, 0, 0, 1, 0, 64, WaypointPlacement.MARKER_RADIUS).isEmpty());
        assertTrue(WaypointPlacement.pick(list, 0, 70, 0, 0, 0, 1, 4, WaypointPlacement.MARKER_RADIUS).isEmpty(), "zu weit");
        assertEquals(2, WaypointPlacement.pick(list, 0, 70, 0, 0, 0, -1, 64, WaypointPlacement.MARKER_RADIUS).getAsInt());
        assertTrue(WaypointPlacement.pick(list, 0, 70, 0, 0, 0, 0, 64, 1).isEmpty());
    }

    @Test
    void recorderAddsEveryTenMetresAndAtTurns() {
        FlightRecorder recorder = new FlightRecorder();
        assertTrue(recorder.sample(0, 70, 0).isEmpty(), "aus");
        recorder.start();
        assertTrue(recorder.sample(0, 70, 0).isPresent(), "erster Punkt sofort");
        int points = 1;
        for (int z = 1; z <= 35; z++) {
            if (recorder.sample(0, 70, z).isPresent()) {
                points++;
            }
        }
        assertEquals(4, points, "0, 10, 20, 30 m");
        assertTrue(recorder.sample(0, 70, 32).isEmpty(), "geradeaus kein neuer Punkt");
        assertTrue(recorder.sample(5, 70, 32).isPresent(), "Abbiegen");
        assertEquals(5, recorder.recorded());
        recorder.stop();
        assertFalse(recorder.active());
        assertTrue(recorder.sample(50, 70, 50).isEmpty());
    }

    @Test
    void rateLimiterAllowsTwoPerSecond() {
        RateLimiter limiter = new RateLimiter(2.0);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(a, 1000));
        assertTrue(limiter.tryAcquire(a, 1000));
        assertFalse(limiter.tryAcquire(a, 1100));
        assertTrue(limiter.tryAcquire(b, 1100), "je Spieler getrennt");
        assertTrue(limiter.tryAcquire(a, 1600));
        assertFalse(limiter.tryAcquire(a, 1700));
        int allowed = 0;
        for (long t = 10_000; t < 20_000; t += 50) {
            if (limiter.tryAcquire(a, t)) {
                allowed++;
            }
        }
        assertTrue(allowed <= 22, "höchstens 2/s über 10 s: " + allowed);
        assertEquals(0, limiter.tracked(200_000));
    }
}
