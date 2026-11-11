package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.CurvedTurn;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvedTurnTest {
    @Test
    void arcStartsAndEndsTangentOnBothLegs() {
        for (double degrees : new double[]{0.5, 20.0, 45.0, 90.0, 135.0, 150.0}) {
            for (double side : new double[]{-1.0, 1.0}) {
                double a = Math.toRadians(degrees) * side;
                Waypoint w = Waypoint.at(30, 40, 0);
                Waypoint next = Waypoint.at(30 + 20 * Math.cos(a), 34, 20 * Math.sin(a));
                CurvedTurn turn = CurvedTurn.of(0, 30, 0, w, next);
                assertNotNull(turn, degrees + "°");
                String name = degrees * side + "°";
                assertEquals(CurvedTurn.LEG_SHARE * 20.0, turn.tangent(), 0.03, name);
                assertArrayEquals(new double[]{30 - 9, 40 - 10.0 / 30.0 * 9, 0}, turn.point(0.0), 0.03, name);
                assertArrayEquals(new double[]{30 + 9 * Math.cos(a), 40 - 6.0 / 20.0 * 9, 9 * Math.sin(a)}, turn.point(1.0), 0.03, name);
                assertArrayEquals(new double[]{1, 0}, turn.direction(0.0), 0.01, name);
                assertArrayEquals(new double[]{Math.cos(a), Math.sin(a)}, turn.direction(1.0), 0.01, name);
                assertEquals(10.0 / 30.0, turn.slope(0.0), 1.0E-9, name);
                assertEquals(-6.0 / 20.0, turn.slope(1.0), 0.01, name);
                for (double u = 0.0; u <= 1.0; u += 0.125) {
                    double[] p = turn.point(u);
                    assertEquals(u, turn.progress(p[0], p[2]), 1.0E-6, name + " bei " + u);
                    assertEquals(0.0, turn.offset(p[0], p[2]), 1.0E-6, name + " bei " + u);
                }
                assertTrue(turn.beforeEntry(5, 0) && !turn.beforeEntry(25, 0), name);
            }
        }
        assertNull(CurvedTurn.of(0, 30, 0, Waypoint.at(30, 30, 0), Waypoint.at(10, 30, 3)), "Haarnadel über 150°");
        assertNull(CurvedTurn.of(0, 30, 0, Waypoint.at(30, 30, 0), Waypoint.at(30.5, 40, 0)), "senkrechtes Bein");
    }

    @Test
    void turnSpeedFollowsTheLateralAccelerationOfTheClass() {
        CurvedTurn turn = CurvedTurn.of(0, 30, 0, Waypoint.at(40, 30, 0), Waypoint.at(40, 30, 40));
        assertEquals(18.0, turn.radius(), 1.0E-9);
        for (FlightClass c : FlightClass.values()) {
            assertEquals(Math.sqrt(c.curveAccel() * 18.0), turn.speed(c, 12.0, 12.0), 1.0E-9, c.key());
            assertEquals(4.0, turn.speed(c, 4.0, 9.0), 1.0E-9, c.key());
        }
        Mission stop = Mission.of(List.of(Waypoint.at(40, 30, 0), Waypoint.at(40, 30, 40), Waypoint.at(40, 30, 50))).withSmooth(true)
                .withBaseSpeed(12.0);
        CurvedTurn first = CurvedTurn.at(stop, 0, 0, 30, 0);
        double straight = 40.0 - first.tangent() - CurvedTurn.at(stop, 1).tangent();
        double toTheSecondTurn = Math.sqrt(Math.pow(CurvedTurn.at(stop, 1).speed(FlightClass.TOUR, 12.0, 12.0), 2) + 3.0 * straight);
        assertEquals(Math.min(first.speed(FlightClass.TOUR, 12.0, 12.0), toTheSecondTurn), CurvedTurn.plannedSpeed(FlightClass.TOUR, stop, 0, first),
                1.0E-6);
    }

    @Test
    void pathShowsTheFlownCurveAndOnlyCornersWhereTheDroneDoesNotStop() {
        Mission mission = WaypointFollowerTest.curves();
        List<double[]> sharp = CurvedTurn.path(mission.withSmooth(false), new double[]{0, 30, 0});
        assertEquals(5, sharp.size());
        List<double[]> path = CurvedTurn.path(mission, new double[]{0, 30, 0});
        assertTrue(path.size() > 20, "Bögen fehlen: " + path.size());
        assertArrayEquals(new double[]{0, 30, 0}, path.getFirst(), 1.0E-9);
        Waypoint last = mission.waypoint(3);
        assertArrayEquals(new double[]{last.x(), last.y(), last.z()}, path.getLast(), 1.0E-9);
        double cut = WaypointFollowerTest.distanceToPolyline(path, mission.waypoint(1).x(), 30, mission.waypoint(1).z());
        assertEquals(18.0 * (Math.sqrt(2.0) - 1.0), cut, 0.05, "90°-Bogen schneidet die Ecke");
        List<double[]> waiting = CurvedTurn.path(mission.replace(1, mission.waypoint(1).withWait(2.0)), new double[]{0, 30, 0});
        assertEquals(0.0, WaypointFollowerTest.distanceToPolyline(waiting, mission.waypoint(1).x(), 30, mission.waypoint(1).z()), 1.0E-9);
        assertTrue(CurvedTurn.loopPath(mission).isEmpty());
        assertTrue(CurvedTurn.loopPath(mission.withLoop(true)).size() > 3);
    }

    @Test
    void arcCornerAndPathShareTheHairpinDecision() {
        Waypoint w = Waypoint.at(40, 30, 0);
        for (Waypoint next : List.of(Waypoint.at(30, 30, 2), Waypoint.at(30, 38, 2), Waypoint.at(30, 50, 2), Waypoint.at(38.5, 45, 0))) {
            String name = "nach " + next.x() + "/" + next.y() + "/" + next.z();
            assertNull(CurvedTurn.of(0, 30, 0, w, next), name);
            assertNull(WaypointFollower.corner(0, 30, 0, w, next), name);
            List<double[]> path = CurvedTurn.path(Mission.of(List.of(w, next, Waypoint.at(next.x(), next.y(), 42))).withSmooth(true),
                    new double[]{0, 30, 0});
            assertEquals(0.0, WaypointFollowerTest.distanceToPolyline(path, w.x(), w.y(), w.z()), 1.0E-9, name);
        }
        for (Waypoint next : List.of(Waypoint.at(30, 30, 6), Waypoint.at(30, 50, 6))) {
            String name = "nach " + next.x() + "/" + next.y() + "/" + next.z();
            assertNotNull(CurvedTurn.of(0, 30, 0, w, next), name);
            assertNotNull(WaypointFollower.corner(0, 30, 0, w, next), name);
        }
        Waypoint steep = Waypoint.at(40.5, 45, 0);
        assertNull(CurvedTurn.of(0, 30, 0, w, steep), "steiles Bein");
        assertNotNull(WaypointFollower.corner(0, 30, 0, w, steep), "steiles Bein");
        assertNull(WaypointFollower.corner(39.5, 45, 0, w, steep), "steil hinab und wieder hinauf");
    }
}
