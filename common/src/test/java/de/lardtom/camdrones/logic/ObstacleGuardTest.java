package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstacleGuardTest {
    private static PointMass.Box wall(double z, double height) {
        return new PointMass.Box(-30, 0, z - 1.0, 30, height, z);
    }

    private static double gap(PointMass d, PointMass.Box wall) {
        return wall.distanceTo(d.x, d.y, d.z);
    }

    @Test
    void commandLimitNeverExceedsTheDesignFormula() {
        for (double d = 0.0; d < ObstacleGuard.SENSOR_RANGE; d += 0.05) {
            double design = Math.max(0.0, (d - ObstacleGuard.STOP_DISTANCE) * ObstacleGuard.SLOW_GAIN);
            assertTrue(ObstacleGuard.commandLimit(d) <= design + 1.0E-9, "d=" + d);
        }
        assertEquals(0.0, ObstacleGuard.commandLimit(1.4), 1.0E-12);
        assertEquals(Double.POSITIVE_INFINITY, ObstacleGuard.commandLimit(Double.NaN), 0.0);
        assertTrue(ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY) >= FlightClass.TOUR.normalSpeed());
    }

    @Test
    void plansTheBrakeWithTheTiltTheControllerFlies() {
        assertEquals(HorizontalControl.MAX_TILT, ObstacleGuard.BRAKE_ANGLE, 1.0E-12);
        assertEquals(HorizontalControl.maxAcceleration(), ObstacleGuard.BRAKE_ACCEL, 1.0E-12);
        NavInput in = new NavInput().time(1.0).position(0.0, 70.0, 0.0).velocity(0.0, 0.0, -14.0).attitude(0.0, 0.0, 0.0).pilot(true);
        NavOutput out = new NavOutput();
        out.velocity(0.0, 0.0);
        out.maxSpeed(15.0);
        out.holdAltitude(70.0);
        assertTrue(new HorizontalControl().apply(in, out));
        assertEquals(-ObstacleGuard.BRAKE_ANGLE, out.stickPitch() * in.angleLimit(), 1.0E-6);
    }

    private static NavInput towardsAWall(double angleLimit, double speed, double distance) {
        return new NavInput().time(1.0).position(0.0, 70.0, 0.0).velocity(0.0, 0.0, -speed).attitude(0.0, 0.0, 0.0).pilot(true)
                .angleLimit(angleLimit).obstacle(NavInput.Sensor.FORWARD, distance).obstacle(NavInput.Sensor.BACK, Double.POSITIVE_INFINITY)
                .obstacle(NavInput.Sensor.LEFT, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.RIGHT, Double.POSITIVE_INFINITY)
                .obstacle(NavInput.Sensor.UP, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.DOWN, 10.0);
    }

    @Test
    void plansTheBrakeWithTheSmallerTiltOfItsOwnLimitAndTheAngleLimit() {
        assertEquals(ObstacleGuard.BRAKE_ACCEL, ObstacleGuard.brakeAcceleration(new NavInput()), 1.0E-12);
        assertEquals(ObstacleGuard.BRAKE_ACCEL, ObstacleGuard.brakeAcceleration(new NavInput().angleLimit(85.0)), 1.0E-12);
        assertEquals(HorizontalControl.G * Math.tan(Math.toRadians(30.0)), ObstacleGuard.brakeAcceleration(new NavInput().angleLimit(30.0)), 1.0E-12);
        assertEquals(30.0, ObstacleGuard.brakeAngle(new NavInput().angleLimit(30.0)), 1.0E-12);
        assertTrue(ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY, ObstacleGuard.SENSOR_RANGE,
                ObstacleGuard.brakeAcceleration(new NavInput().angleLimit(30.0))) < ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY) - 1.0,
                "mit kleinerer Grenze langsamer zum Hindernis");
        assertTrue(ObstacleGuard.sightNeeded(11.0, ObstacleGuard.brakeAcceleration(new NavInput().angleLimit(30.0))) > ObstacleGuard.sightNeeded(11.0));
        for (double limit : new double[] {30.0, 60.0}) {
            ObstacleGuard guard = new ObstacleGuard();
            NavInput in = towardsAWall(limit, 8.0, 8.0);
            NavOutput out = new NavOutput();
            out.velocity(0.0, -8.0);
            out.holdAltitude(70.0);
            guard.apply(in, out, FlightClass.CINE, ObstacleGuard.Mode.STOP, false);
            if (limit < HorizontalControl.MAX_TILT) {
                assertTrue(guard.emergency(), "8 m/s auf 8 m mit " + limit + "° ist zu schnell");
                assertEquals(-limit, out.stickPitch() * in.angleLimit(), 1.0E-6, "Notbremse mit der ganzen Grenze");
            } else {
                assertFalse(guard.emergency(), "8 m/s auf 8 m mit " + limit + "° reicht zum Bremsen");
            }
        }
    }

    @Test
    void reportsBrakingWhileItLimitsTheSpeedTowardsAnObstacle() {
        ObstacleGuard guard = new ObstacleGuard();
        NavInput in = new NavInput().time(1.0).position(0.0, 70.0, 0.0).velocity(0.0, 0.0, -10.0).attitude(0.0, 0.0, 0.0).pilot(true)
                .obstacle(NavInput.Sensor.FORWARD, 4.0).obstacle(NavInput.Sensor.BACK, Double.POSITIVE_INFINITY)
                .obstacle(NavInput.Sensor.LEFT, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.RIGHT, Double.POSITIVE_INFINITY)
                .obstacle(NavInput.Sensor.UP, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.DOWN, 10.0);
        NavOutput out = new NavOutput();
        out.velocity(0.0, -10.0);
        out.holdAltitude(70.0);
        guard.apply(in, out, FlightClass.CINE, ObstacleGuard.Mode.STOP, false);
        assertTrue(guard.braking(), "vor der Wand");
        NavInput free = new NavInput().time(1.1).position(0.0, 70.0, 0.0).velocity(0.0, 0.0, -10.0).attitude(0.0, 0.0, 0.0).pilot(true)
                .obstacle(NavInput.Sensor.FORWARD, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.BACK, Double.POSITIVE_INFINITY)
                .obstacle(NavInput.Sensor.LEFT, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.RIGHT, Double.POSITIVE_INFINITY)
                .obstacle(NavInput.Sensor.UP, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.DOWN, 10.0);
        NavOutput open = new NavOutput();
        open.velocity(0.0, -10.0);
        open.holdAltitude(70.0);
        guard.apply(free, open, FlightClass.CINE, ObstacleGuard.Mode.STOP, false);
        assertFalse(guard.braking(), "frei");
    }

    @Test
    void stopsAtLeastOneAndAHalfMetresBeforeAWallFromTwelveMetresPerSecond() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 10, 40, 0);
        drone.vz = -12.0;
        PointMass.Box wall = wall(0.0, 40.0);
        drone.boxes.add(wall);
        drone.sticks(0, 1.0, 0, 0.5);
        double[] closest = {Double.POSITIVE_INFINITY};
        boolean[] stop = {false};
        boolean[] warned = {false};
        drone.run(logic::update, 15.0, d -> {
            closest[0] = Math.min(closest[0], gap(d, wall));
            stop[0] |= d.out.has(NavOutput.Warning.OBSTACLE_STOP);
            warned[0] |= d.out.has(NavOutput.Warning.OBSTACLE);
        });
        assertFalse(drone.crashed);
        assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE, "Abstand " + closest[0]);
        assertTrue(drone.speed() < 0.3, "steht nicht: " + drone.speed());
        assertTrue(gap(drone, wall) < 3.0, "bleibt zu weit weg: " + gap(drone, wall));
        assertTrue(stop[0] && warned[0]);
    }

    @Test
    void stopsInWindFromEveryDirection() {
        for (FlightClass c : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (int direction = 0; direction < 8; direction++) {
                double a = Math.toRadians(direction * 45.0);
                NavLogic logic = new NavLogic(c);
                PointMass drone = new PointMass(c).at(0, 10, 150, 0).wind(5.0 * Math.sin(a), 5.0 * Math.cos(a), 2.5);
                PointMass.Box wall = new PointMass.Box(-60, 0, -1, 60, 40, 0);
                drone.boxes.add(wall);
                drone.sticks(0, 1.0, 0, 0.5);
                double[] closest = {Double.POSITIVE_INFINITY};
                drone.run(logic::update, 40.0, d -> closest[0] = Math.min(closest[0], gap(d, wall)));
                assertFalse(drone.crashed);
                assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE, c + ", Wind aus " + direction * 45 + "°: " + closest[0]);
            }
        }
    }

    @Test
    void cineAtFullNormalSpeedStillStops() {
        NavLogic logic = new NavLogic(FlightClass.CINE);
        PointMass drone = new PointMass(FlightClass.CINE).at(0, 10, 200, 0).wind(0.0, -4.0, 2.0);
        PointMass.Box wall = wall(0.0, 40.0);
        drone.boxes.add(wall);
        drone.sticks(0, 1.0, 0, 0.5);
        double[] closest = {Double.POSITIVE_INFINITY};
        double[] top = {0.0};
        drone.run(logic::update, 40.0, d -> {
            closest[0] = Math.min(closest[0], gap(d, wall));
            top[0] = Math.max(top[0], d.speed());
        });
        assertFalse(drone.crashed);
        assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE, "Abstand " + closest[0]);
        assertTrue(top[0] <= ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY) + 0.5, "Tempo mit Sensor vorn " + top[0]);
    }

    @Test
    void sidewaysAndBackwardsAreGuardedToo() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 10, 20, 90);
        PointMass.Box wall = wall(0.0, 40.0);
        drone.boxes.add(wall);
        drone.sticks(-1.0, 0, 0, 0.5);
        double[] closest = {Double.POSITIVE_INFINITY};
        drone.run(logic::update, 15.0, d -> closest[0] = Math.min(closest[0], gap(d, wall)));
        assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE, "seitlich " + closest[0]);
        drone.at(0, 10, 20, 180);
        drone.vx = 0;
        drone.vz = 0;
        logic = new NavLogic(FlightClass.TOUR);
        drone.sticks(0, -1.0, 0, 0.5);
        closest[0] = Double.POSITIVE_INFINITY;
        NavLogic back = logic;
        drone.run(back::update, 15.0, d -> closest[0] = Math.min(closest[0], gap(d, wall)));
        assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE, "rückwärts " + closest[0]);
        assertFalse(drone.crashed);
    }

    @Test
    void missionStopsInFrontOfAWall() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 8, -20))).withBaseSpeed(8.0);
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 8, 30, 0);
        PointMass.Box wall = wall(0.0, 15.0);
        drone.boxes.add(wall);
        double[] closest = {Double.POSITIVE_INFINITY};
        drone.run(logic::update, 30.0, d -> closest[0] = Math.min(closest[0], gap(d, wall)));
        assertFalse(drone.crashed);
        assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE, "Abstand " + closest[0]);
        assertEquals(NavLogic.MissionState.RUNNING, logic.missionState());
        assertTrue(drone.y < 9.0, "steigt ohne Übersteigen");
    }

    @Test
    void climbOverPassesTheWallAndReachesTheWaypoint() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 8, -25))).withBaseSpeed(8.0).withObstacle(ObstacleGuard.Mode.CLIMB_OVER);
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 8, 30, 0).wind(2.0, 0.0, 1.0);
        PointMass.Box wall = wall(0.0, 14.0);
        drone.boxes.add(wall);
        double[] closest = {Double.POSITIVE_INFINITY};
        double[] top = {0.0};
        drone.run(logic::update, 60.0, d -> {
            closest[0] = Math.min(closest[0], gap(d, wall));
            top[0] = Math.max(top[0], d.y);
        });
        assertFalse(drone.crashed, "durch die Wand");
        assertTrue(closest[0] > 0.5, "zu knapp " + closest[0]);
        assertTrue(top[0] > 14.0 && top[0] <= 8.0 + ObstacleGuard.CLIMB_LIMIT + ObstacleGuard.FLOOR_MARGIN + 0.5, "Höhe " + top[0]);
        assertEquals(NavLogic.MissionState.DONE, logic.missionState());
        assertTrue(mission.waypoint(0).distanceTo(drone.x, drone.y, drone.z) < 1.0);
    }

    @Test
    void climbOverGivesUpAfterTenMetres() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 8, -25))).withObstacle(ObstacleGuard.Mode.CLIMB_OVER);
        NavLogic logic = new NavLogic(FlightClass.CINE);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.CINE).at(0, 8, 20, 0);
        PointMass.Box wall = wall(0.0, 60.0);
        drone.boxes.add(wall);
        double[] top = {0.0};
        drone.run(logic::update, 40.0, d -> top[0] = Math.max(top[0], d.y));
        assertFalse(drone.crashed);
        assertTrue(top[0] <= 8.0 + ObstacleGuard.CLIMB_LIMIT + 0.6, "zu hoch: " + top[0]);
        assertTrue(top[0] >= 8.0 + ObstacleGuard.CLIMB_LIMIT - 0.5, "nicht gestiegen: " + top[0]);
        assertTrue(drone.out.has(NavOutput.Warning.OBSTACLE_STOP));
    }

    @Test
    void ceilingAndGroundAreRespected() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 10, 0, 0);
        drone.boxes.add(new PointMass.Box(-10, 16, -10, 10, 20, 10));
        drone.sticks(0, 0, 0, 1.0);
        drone.run(logic::update, 10.0);
        assertFalse(drone.crashed);
        assertTrue(drone.y <= 16.0 - ObstacleGuard.STOP_DISTANCE + 0.2, "Decke " + drone.y);
        drone.sticks(0, 0, 0, 0.0);
        double[] slowest = {0.0};
        drone.run(logic::update, 30.0, d -> {
            if (d.y - d.ground < 1.0 && d.y > d.ground) {
                slowest[0] = Math.max(slowest[0], -d.vy);
            }
        });
        assertTrue(drone.grounded(), "landet nicht mit Knüppel");
        assertTrue(slowest[0] < 1.0, "zu schnell am Boden " + slowest[0]);
    }

    @Test
    void compactHasNoHorizontalSensorsAndSportIsUnguarded() {
        PointMass compact = new PointMass(FlightClass.COMPACT).at(0, 10, 15, 0);
        compact.boxes.add(wall(0.0, 40.0));
        NavInput in = compact.input();
        assertTrue(Double.isNaN(in.obstacle(NavInput.Sensor.FORWARD)));
        assertTrue(Double.isFinite(in.obstacle(NavInput.Sensor.DOWN)));
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.stickMode(NavLogic.StickMode.SPORT);
        PointMass sport = new PointMass(FlightClass.TOUR).at(0, 10, 5, 0);
        sport.boxes.add(wall(0.0, 40.0));
        sport.sticks(0, 0.5, 0, 0.5);
        sport.run(logic::update, 0.5);
        assertTrue(sport.out.hasSticks());
        assertEquals(0.5 * 35.0 / 60.0, sport.out.stickPitch(), 1.0E-9);
        assertFalse(sport.out.has(NavOutput.Warning.OBSTACLE));
    }
}
