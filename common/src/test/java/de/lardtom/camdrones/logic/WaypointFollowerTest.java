package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.CurvedTurn;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFollowerTest {
    private static final double[][] SQUARE = {{0, 25, -40}, {40, 30, -40}, {40, 22, 0}, {10, 35, 20}, {0, 20, 0}};

    private static Mission square(boolean smooth) {
        List<Waypoint> list = new ArrayList<>();
        for (double[] p : SQUARE) {
            list.add(Waypoint.at(p[0], p[1], p[2]));
        }
        list.set(1, list.get(1).withSpeed(4.0));
        list.set(2, list.get(2).withSpeed(15.0));
        return Mission.of(list).withSmooth(smooth).withBaseSpeed(8.0);
    }

    static final class Stats {
        final double[] closest;
        final double[] maxSpeed;
        double maxPathDeviation;
        double flown;
        boolean done;
        double doneAt = Double.NaN;

        Stats(int waypoints) {
            this.closest = new double[waypoints];
            this.maxSpeed = new double[waypoints];
            Arrays.fill(this.closest, Double.POSITIVE_INFINITY);
        }
    }

    private static Stats fly(PointMass drone, NavLogic logic, Mission mission, double seconds, double[] polylineStart) {
        Stats stats = new Stats(mission.size());
        List<double[]> polyline = planned(mission, polylineStart);
        drone.run(logic::update, seconds, d -> {
            for (int i = 0; i < mission.size(); i++) {
                stats.closest[i] = Math.min(stats.closest[i], mission.waypoint(i).distanceTo(d.x, d.y, d.z));
            }
            WaypointFollower follower = logic.follower();
            if (follower != null && follower.phase() == WaypointFollower.Phase.FLY) {
                int i = follower.index();
                stats.maxSpeed[i] = Math.max(stats.maxSpeed[i], d.speed());
                if (polylineStart != null) {
                    stats.maxPathDeviation = Math.max(stats.maxPathDeviation, distanceToPolyline(polyline, d.x, d.y, d.z));
                }
            }
            if (!stats.done && logic.missionState() == NavLogic.MissionState.DONE) {
                stats.done = true;
                stats.doneAt = d.time;
            }
        });
        return stats;
    }

    static List<double[]> planned(Mission mission, double[] start) {
        if (mission.smooth() && start != null) {
            return CurvedTurn.path(mission, start);
        }
        List<double[]> polyline = new ArrayList<>();
        if (start != null) {
            polyline.add(start);
        }
        for (Waypoint w : mission.waypoints()) {
            polyline.add(new double[]{w.x(), w.y(), w.z()});
        }
        return polyline;
    }

    static void assertReached(Mission mission, Stats stats, double[] start, String name) {
        List<double[]> path = planned(mission, start);
        for (int i = 0; i < mission.size(); i++) {
            Waypoint w = mission.waypoint(i);
            double allowed = distanceToPolyline(path, w.x(), w.y(), w.z()) + 1.0;
            assertTrue(stats.closest[i] <= allowed, name + ": Wegpunkt " + i + " verfehlt: " + stats.closest[i] + " > " + allowed);
        }
    }

    static double distanceToPolyline(List<double[]> points, double x, double y, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            double sx = b[0] - a[0];
            double sy = b[1] - a[1];
            double sz = b[2] - a[2];
            double l2 = sx * sx + sy * sy + sz * sz;
            double t = l2 < 1.0E-9 ? 0.0 : Math.clamp(((x - a[0]) * sx + (y - a[1]) * sy + (z - a[2]) * sz) / l2, 0.0, 1.0);
            double dx = x - (a[0] + sx * t);
            double dy = y - (a[1] + sy * t);
            double dz = z - (a[2] + sz * t);
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }

    private static NavLogic logic(Mission mission) {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        return logic;
    }

    @Test
    void sharpCornersInWindReachEveryWaypointWithinOneMetre() {
        Mission mission = square(false);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(4.0, 2.5, 2.0);
        Stats stats = fly(drone, logic(mission), mission, 160.0, new double[]{0, 20, 0});
        assertTrue(stats.done, "Mission nicht fertig");
        for (int i = 0; i < mission.size(); i++) {
            assertTrue(stats.closest[i] <= 1.0, "Wegpunkt " + i + " verfehlt: " + stats.closest[i]);
        }
        assertTrue(stats.maxPathDeviation <= 1.0, "Abweichung von der Linie " + stats.maxPathDeviation);
        assertFalse(drone.crashed);
    }

    @Test
    void speedLimitsHoldPerSegment() {
        Mission mission = square(false);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(3.0, -2.0, 1.5);
        Stats stats = fly(drone, logic(mission), mission, 160.0, null);
        assertTrue(stats.done);
        double[] limits = {8.0, 4.0, FlightClass.TOUR.normalSpeed(), 8.0, 8.0};
        for (int i = 0; i < limits.length; i++) {
            assertTrue(stats.maxSpeed[i] <= limits[i] * 1.05 + 0.1, "Segment " + i + ": " + stats.maxSpeed[i] + " > " + limits[i]);
        }
        assertTrue(stats.maxSpeed[0] > 6.0, "Grundtempo nie erreicht: " + stats.maxSpeed[0]);
        Mission straight = Mission.of(List.of(Waypoint.at(0, 20, -150).withSpeed(15.0), Waypoint.at(0, 20, -200).withSpeed(4.0)));
        PointMass fast = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(0.0, 4.0, 2.0);
        Stats run = fly(fast, logic(straight), straight, 80.0, null);
        assertTrue(run.done);
        assertTrue(run.maxSpeed[0] > 11.0 && run.maxSpeed[0] <= FlightClass.TOUR.normalSpeed() * 1.05,
                "Klassengrenze: " + run.maxSpeed[0]);
        assertTrue(run.maxSpeed[1] > 3.5 && run.maxSpeed[1] <= 4.0 * 1.05 + 0.1, "Wegpunkt-Tempo: " + run.maxSpeed[1]);
    }

    @Test
    void smoothCornersStayCloseToEveryPointWithoutOvershoot() {
        Mission mission = square(true);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(4.0, 2.5, 2.0);
        Stats smooth = fly(drone, logic(mission), mission, 160.0, new double[]{0, 20, 0});
        assertTrue(smooth.done);
        assertReached(mission, smooth, new double[]{0, 20, 0}, "weich");
        assertTrue(smooth.maxPathDeviation <= 1.0, "Überschwingen " + smooth.maxPathDeviation);
        PointMass sharpDrone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(4.0, 2.5, 2.0);
        Stats sharp = fly(sharpDrone, logic(square(false)), square(false), 160.0, null);
        assertTrue(smooth.doneAt < sharp.doneAt, "weiche Kurven nicht schneller: " + smooth.doneAt + " gegen " + sharp.doneAt);
    }

    @Test
    void windFromEveryDirection() {
        for (boolean smooth : new boolean[]{false, true}) {
            for (int direction = 0; direction < 8; direction++) {
                double a = Math.toRadians(direction * 45.0);
                Mission mission = square(smooth);
                PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(5.0 * Math.sin(a), 5.0 * Math.cos(a), 2.5);
                Stats stats = fly(drone, logic(mission), mission, 200.0, new double[]{0, 20, 0});
                String name = (smooth ? "weich" : "eckig") + ", Wind aus " + direction * 45 + "°";
                assertTrue(stats.done, name);
                assertReached(mission, stats, new double[]{0, 20, 0}, name);
                assertTrue(stats.maxPathDeviation <= 1.0, name + ": Abweichung " + stats.maxPathDeviation);
            }
        }
    }

    @Test
    void zigZagWithManySmoothCornersStaysOnTrack() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(Waypoint.at(15.0 * (i + 1), 30.0 + (i % 3) * 2.0, i % 2 == 0 ? -12.0 : 12.0));
        }
        Mission mission = Mission.of(list).withSmooth(true).withBaseSpeed(10.0);
        PointMass drone = new PointMass(FlightClass.CINE).at(0, 30, 0, 90).wind(-5.0, 3.0, 2.5);
        NavLogic logic = new NavLogic(FlightClass.CINE);
        logic.startMission(mission, 0);
        Stats stats = fly(drone, logic, mission, 200.0, new double[]{0, 30, 0});
        assertTrue(stats.done);
        assertReached(mission, stats, new double[]{0, 30, 0}, "Zickzack");
        assertTrue(stats.maxPathDeviation <= 1.0, "Überschwingen " + stats.maxPathDeviation);
    }

    static Mission curves() {
        double d = 40.0 / Math.sqrt(2.0);
        return Mission.of(List.of(Waypoint.at(40, 30, 0).withAction(Waypoint.Action.PHOTO),
                Waypoint.at(40 + d, 30, d).withAction(Waypoint.Action.RECORD_START), Waypoint.at(40, 30, 2 * d).withAction(Waypoint.Action.PHOTO),
                Waypoint.at(0, 30, 2 * d).withAction(Waypoint.Action.RECORD_STOP))).withSmooth(true).withBaseSpeed(8.0);
    }

    @Test
    void smoothTurnsKeepTheCruiseSpeedAndStillFireTheActions() {
        Mission mission = curves();
        for (FlightClass flightClass : FlightClass.values()) {
            for (double wind : new double[]{0.0, 3.0}) {
                NavLogic logic = new NavLogic(flightClass);
                logic.startMission(mission, 0);
                PointMass drone = new PointMass(flightClass).at(0, 30, 0, 90).wind(wind, -wind, wind * 0.5);
                List<Waypoint.Action> fired = new ArrayList<>();
                double[] lowest = {Double.POSITIVE_INFINITY, -1};
                double[] highest = {0.0};
                boolean[] cruising = {false};
                drone.run(logic::update, 90.0, d -> {
                    if (d.out.action() != Waypoint.Action.NONE) {
                        fired.add(d.out.action());
                    }
                    WaypointFollower f = logic.follower();
                    if (f == null || f.phase() != WaypointFollower.Phase.FLY) {
                        return;
                    }
                    cruising[0] |= d.speed() >= 0.95 * 8.0;
                    Waypoint last = mission.waypoint(3);
                    boolean braking = f.index() == 3 && Math.hypot(d.x - last.x(), d.z - last.z()) < 28.0;
                    if (cruising[0] && !braking && d.speed() < lowest[0]) {
                        lowest[0] = d.speed();
                        lowest[1] = f.index();
                    }
                    highest[0] = Math.max(highest[0], d.speed());
                });
                String name = flightClass.key() + ", Wind " + wind;
                assertEquals(NavLogic.MissionState.DONE, logic.missionState(), name);
                assertTrue(cruising[0], name + ": Reisetempo nie erreicht");
                assertTrue(lowest[0] >= 0.7 * 8.0, name + ": bremst vor Wegpunkt " + (int) (lowest[1] + 1) + " auf " + lowest[0] + " m/s");
                assertTrue(highest[0] <= 8.0 * 1.05 + 0.1, name + ": zu schnell " + highest[0]);
                assertEquals(List.of(Waypoint.Action.PHOTO, Waypoint.Action.RECORD_START, Waypoint.Action.PHOTO, Waypoint.Action.RECORD_STOP),
                        fired, name);
                assertFalse(drone.crashed, name);
            }
        }
    }

    @Test
    void tiltAndHeadingStayContinuousThroughSmoothTurns() {
        Mission base = curves();
        List<Waypoint> tilted = new ArrayList<>();
        int[] tilts = {0, -40, 10, -60};
        for (int i = 0; i < base.size(); i++) {
            tilted.add(base.waypoint(i).withTilt(tilts[i]));
        }
        Mission mission = base.withWaypoints(tilted);
        NavLogic logic = logic(mission);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 90);
        double[] previous = {Double.NaN, Double.NaN};
        double[] worst = {0.0, 0.0};
        drone.run(logic::update, 60.0, d -> {
            WaypointFollower f = logic.follower();
            if (f == null || f.phase() != WaypointFollower.Phase.FLY) {
                return;
            }
            double tilt = WaypointFollower.tiltAt(mission, f.index(), d.x, d.y, d.z);
            if (!Double.isNaN(previous[0])) {
                worst[0] = Math.max(worst[0], Math.abs(tilt - previous[0]));
            }
            if (!Double.isNaN(previous[1])) {
                worst[1] = Math.max(worst[1], Math.abs(Compass.wrap180(d.heading - previous[1])));
            }
            previous[0] = tilt;
            previous[1] = d.heading;
        });
        assertEquals(NavLogic.MissionState.DONE, logic.missionState());
        assertTrue(worst[0] < 0.5, "Neigung springt um " + worst[0] + "° in 10 ms");
        assertTrue(worst[1] < WaypointFollower.HEADING_RATE * PointMass.DT * 1.5, "Richtung springt um " + worst[1] + "° in 10 ms");
    }

    @Test
    void obstacleGuardStillStopsInsideASmoothTurn() {
        Mission mission = curves();
        double[] apex = CurvedTurn.at(mission, 1).point(0.5);
        NavLogic logic = logic(mission);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 90);
        drone.boxes.add(new PointMass.Box(apex[0] - 2.0, 0.0, apex[2] - 2.0, apex[0] + 2.0, 60.0, apex[2] + 2.0));
        boolean[] stopped = {false};
        double[] nearest = {Double.POSITIVE_INFINITY};
        drone.run(logic::update, 60.0, d -> {
            stopped[0] |= d.out.has(NavOutput.Warning.OBSTACLE_STOP);
            nearest[0] = Math.min(nearest[0], d.boxes.getFirst().distanceTo(d.x, d.y, d.z));
        });
        assertFalse(drone.crashed, "in den Pfeiler geflogen");
        assertTrue(stopped[0], "keine Hindernis-Warnung im Bogen");
        assertTrue(nearest[0] >= 1.0, "zu nah am Pfeiler: " + nearest[0]);
    }

    @Test
    void waitTimesAreHeldOnThePoint() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 20, -20).withWait(3.0), Waypoint.at(20, 20, -20).withWait(1.5),
                Waypoint.at(20, 20, 0))).withBaseSpeed(6.0);
        NavLogic logic = logic(mission);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(3.0, 1.0, 1.5);
        double[] waited = new double[mission.size()];
        double[] drift = new double[mission.size()];
        drone.run(logic::update, 60.0, d -> {
            WaypointFollower f = logic.follower();
            if (f != null && f.phase() == WaypointFollower.Phase.WAIT) {
                waited[f.index()] += PointMass.DT;
                drift[f.index()] = Math.max(drift[f.index()], mission.waypoint(f.index()).distanceTo(d.x, d.y, d.z));
            }
        });
        assertEquals(NavLogic.MissionState.DONE, logic.missionState());
        assertEquals(3.0, waited[0], 0.05);
        assertEquals(1.5, waited[1], 0.05);
        assertEquals(0.0, waited[2], 1.0E-9);
        assertTrue(drift[0] <= 0.6 && drift[1] <= 0.6, "Drift beim Warten " + Arrays.toString(drift));
    }

    @Test
    void takeoffClimbsBeforeLeavingTheStart() {
        Mission mission = Mission.of(List.of(Waypoint.at(30, 18, 0), Waypoint.at(30, 18, 30))).withEnd(Mission.End.LAND);
        NavLogic logic = logic(mission);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 0, 0, 90).wind(2.0, 0.0, 0.5);
        double[] lowDrift = {0.0};
        boolean[] tookOff = {false};
        drone.run(logic::update, 90.0, d -> {
            WaypointFollower f = logic.follower();
            if (f != null && f.phase() == WaypointFollower.Phase.TAKEOFF) {
                tookOff[0] = true;
                lowDrift[0] = Math.max(lowDrift[0], Math.hypot(d.x, d.z));
            }
        });
        assertTrue(tookOff[0]);
        assertTrue(lowDrift[0] < 0.5, "Abdrift beim Steigen " + lowDrift[0]);
        assertEquals(NavLogic.MissionState.DONE, logic.missionState());
        assertTrue(drone.grounded(), "nicht gelandet");
        assertTrue(Math.hypot(drone.x - 30, drone.z - 30) < 1.0, "Landung daneben");
    }

    @Test
    void endActionsAndLoop() {
        Mission base = Mission.of(List.of(Waypoint.at(20, 25, 0), Waypoint.at(20, 25, 20))).withBaseSpeed(6.0);
        NavLogic hover = logic(base);
        PointMass a = new PointMass(FlightClass.TOUR).at(0, 20, 0, 90).wind(3, 0, 1);
        a.run(hover::update, 60.0);
        assertEquals(NavLogic.MissionState.DONE, hover.missionState());
        assertTrue(Math.hypot(a.x - 20, a.z - 20) < 0.5 && Math.abs(a.y - 25) < 0.5, "schwebt nicht am Ende");
        NavLogic home = logic(base.withEnd(Mission.End.START));
        PointMass b = new PointMass(FlightClass.TOUR).at(0, 20, 0, 90).wind(3, 0, 1);
        b.run(home::update, 80.0);
        assertEquals(NavLogic.MissionState.DONE, home.missionState());
        assertTrue(Math.hypot(b.x, b.z) < 0.6, "nicht zurück am Start: " + b.x + "," + b.z);
        assertEquals(20.0, b.y, 0.6);
        NavLogic loop = logic(base.withLoop(true));
        PointMass c = new PointMass(FlightClass.TOUR).at(0, 20, 0, 90).wind(3, 0, 1);
        c.run(loop::update, 90.0);
        assertEquals(NavLogic.MissionState.RUNNING, loop.missionState());
        assertTrue(loop.follower().laps() >= 2, "Schleife: " + loop.follower().laps());
    }

    @Test
    void actionsFireOncePerWaypoint() {
        Mission mission = Mission.of(List.of(Waypoint.at(10, 20, 0).withAction(Waypoint.Action.RECORD_START),
                Waypoint.at(20, 20, 0).withAction(Waypoint.Action.PHOTO), Waypoint.at(30, 20, 0).withAction(Waypoint.Action.RECORD_STOP)));
        NavLogic logic = logic(mission);
        PointMass drone = new PointMass(FlightClass.COMPACT).at(0, 20, 0, 90);
        List<Waypoint.Action> fired = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        drone.run(logic::update, 40.0, d -> {
            if (d.out.action() != Waypoint.Action.NONE) {
                fired.add(d.out.action());
            }
            if (!labels.contains(d.out.label())) {
                labels.add(d.out.label());
            }
        });
        assertEquals(List.of(Waypoint.Action.RECORD_START, Waypoint.Action.PHOTO, Waypoint.Action.RECORD_STOP), fired);
        assertEquals(List.of("WP 1/3", "WP 2/3", "WP 3/3", "HOLD"), labels);
    }

    @Test
    void headingModes() {
        PointOfInterest poi = PointOfInterest.at(30, 0, -30, 0);
        Mission mission = Mission.of(List.of(Waypoint.at(0, 20, -20).withHeading(Waypoint.Heading.FIXED, 135.0),
                Waypoint.at(20, 20, -20).facing(0).withWait(4.0), Waypoint.at(40, 20, -20)), List.of(poi)).withBaseSpeed(5.0);
        NavLogic logic = logic(mission);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0);
        double[] fixedError = {Double.NaN};
        double[] poiError = {0.0};
        double[] courseError = {0.0};
        drone.run(logic::update, 60.0, d -> {
            WaypointFollower f = logic.follower();
            if (f == null) {
                return;
            }
            if (f.index() == 1 && f.phase() == WaypointFollower.Phase.FLY && Double.isNaN(fixedError[0])) {
                fixedError[0] = Math.abs(Compass.wrap180(d.heading - 135.0));
            }
            if (f.index() == 1 && f.phase() == WaypointFollower.Phase.WAIT) {
                double bearing = Compass.headingTo(poi.x() - d.x, poi.z() - d.z);
                poiError[0] = Math.max(poiError[0], Math.abs(Compass.wrap180(bearing - d.heading)));
            }
            if (f.index() == 2 && f.phase() == WaypointFollower.Phase.FLY && Math.hypot(d.x - 20, d.z + 20) > 8.0
                    && Math.hypot(d.x - 40, d.z + 20) > 2.0) {
                courseError[0] = Math.max(courseError[0], Math.abs(Compass.wrap180(d.heading - 90.0)));
            }
        });
        assertTrue(fixedError[0] < 5.0, "feste Richtung " + fixedError[0]);
        assertTrue(poiError[0] < 12.0, "Blick zum Point of Interest " + poiError[0]);
        assertTrue(courseError[0] < 3.0, "Flugrichtung " + courseError[0]);
    }

    @Test
    void gimbalTiltIsInterpolatedTheSameForEveryClient() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 20, 0).withTilt(0), Waypoint.at(0, 20, -40).withTilt(-60),
                Waypoint.at(0, 20, -60).facing(0)), List.of(PointOfInterest.at(0, 0, -80, 0)));
        assertEquals(0.0, WaypointFollower.tiltAt(mission, 0, 5, 20, 5), 1.0E-9);
        assertEquals(-30.0, WaypointFollower.tiltAt(mission, 1, 0, 20, -20), 1.0E-9);
        assertEquals(-60.0, WaypointFollower.tiltAt(mission, 1, 0, 20, -45), 1.0E-9);
        assertTrue(WaypointFollower.tiltAt(mission, 1, 0, 20, -10) > -30.0);
        assertEquals(-45.0, WaypointFollower.tiltAt(mission, 2, 0, 20, -60), 1.0E-9);
    }

    @Test
    void resumeFindsTheNearestSegmentFromTheReportedIndex() {
        Mission mission = square(false);
        assertEquals(2, WaypointFollower.resumeIndex(mission, 1, 40.5, 26, -20));
        assertEquals(3, WaypointFollower.resumeIndex(mission, 3, 40.5, 26, -20));
        assertEquals(0, WaypointFollower.resumeIndex(mission, 0, 0, 24, -35));
        assertEquals(4, WaypointFollower.resumeIndex(mission, 9, 0, 0, 0));
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, WaypointFollower.resumeIndex(mission, 1, 40.5, 26, -20));
        PointMass drone = new PointMass(FlightClass.TOUR).at(40.5, 26, -20, 180);
        Stats stats = fly(drone, logic, mission, 90.0, null);
        assertTrue(stats.done);
        assertTrue(stats.closest[2] <= 1.0 && stats.closest[3] <= 1.0 && stats.closest[4] <= 1.0);
        assertTrue(stats.closest[0] > 10.0, "fliegt zurück zum Anfang");
    }
}
