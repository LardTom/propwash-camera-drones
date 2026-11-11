package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavLogicTest {
    @Test
    void releasedSticksBrakeAndHoldWithinOneMetreInFiveSeconds() {
        for (FlightClass c : FlightClass.values()) {
            NavLogic logic = new NavLogic(c);
            PointMass drone = new PointMass(c).at(0, 30, 0, 0).wind(4.0, 2.0, 2.0);
            drone.sticks(0, 1.0, 0, 0.5);
            drone.run(logic::update, 12.0);
            double top = Math.min(c.normalSpeed(), c.allAroundSensors() ? ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY) : c.normalSpeed());
            assertTrue(drone.speed() > top - 1.0, c + " Tempo " + drone.speed());
            drone.sticks(0, 0, 0, 0.5);
            drone.run(logic::update, 5.0);
            double x = drone.x;
            double z = drone.z;
            double[] drift = {0.0};
            drone.run(logic::update, 20.0, d -> drift[0] = Math.max(drift[0], Math.hypot(d.x - x, d.z - z)));
            assertTrue(drift[0] <= 1.0, c + " hält nicht: " + drift[0]);
            assertEquals(30.0, drone.y, 0.5);
        }
    }

    @Test
    void cineIsSlowerAndSmootherThanNormal() {
        NavLogic normal = new NavLogic(FlightClass.TOUR);
        NavLogic cine = new NavLogic(FlightClass.TOUR);
        cine.stickMode(NavLogic.StickMode.CINE);
        PointMass a = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        PointMass b = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        a.sticks(0, 1.0, 1.0, 0.5);
        b.sticks(0, 1.0, 1.0, 0.5);
        a.run(normal::update, 0.3);
        b.run(cine::update, 0.3);
        double normalCommand = Math.hypot(a.out.velocityX(), a.out.velocityZ());
        double cineCommand = Math.hypot(b.out.velocityX(), b.out.velocityZ());
        assertEquals(FlightClass.TOUR.normalSpeed(), normalCommand, 1.0E-6);
        assertTrue(cineCommand < 0.5 * FlightClass.TOUR.cineSpeed(), "Kino nicht geglättet: " + cineCommand);
        a.run(normal::update, 10.0);
        b.run(cine::update, 10.0);
        assertEquals(FlightClass.TOUR.cineSpeed(), b.speed(), 0.3);
        assertEquals(90.0, a.out.yawRateTarget(), 1.0E-6);
        assertEquals(30.0, b.out.yawRateTarget(), 0.1);
        assertEquals("C", b.out.label());
        assertEquals(NavLogic.StickMode.SPORT, cine.cycleStickMode());
        assertEquals(NavLogic.StickMode.NORMAL, cine.cycleStickMode());
    }

    @Test
    void throttleIsClimbRateAndCentreHoldsTheHeight() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 0, 0, 0);
        drone.sticks(0, 0, 0, 0.0);
        drone.run(logic::update, 2.0);
        assertTrue(drone.grounded());
        drone.sticks(0, 0, 0, 1.0);
        drone.run(logic::update, 4.0);
        assertEquals(FlightClass.TOUR.climbSpeed(), drone.vy, 0.3);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 4.0);
        double y = drone.y;
        drone.run(logic::update, 10.0);
        assertEquals(y, drone.y, 0.3);
        assertEquals(NavOutput.Vertical.ALTITUDE, drone.out.vertical());
    }

    @Test
    void sticksPauseAMissionAndResumeContinuesAtTheIndex() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            list.add(Waypoint.at(0, 30, -15.0 * i));
        }
        Mission mission = Mission.of(list);
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.pilot = true;
        drone.run(logic::update, 12.0);
        int index = logic.missionIndex();
        assertTrue(index >= 1, "Index " + index);
        drone.sticks(0.6, 0, 0, 0.5);
        drone.run(logic::update, 0.05);
        assertEquals(NavLogic.MissionState.PAUSED, logic.missionState());
        assertEquals(NavLogic.Function.NONE, logic.function());
        assertTrue(drone.out.has(NavOutput.Warning.PAUSED));
        assertEquals("N", drone.out.label());
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 3.0);
        assertTrue(logic.resumeMission());
        assertEquals(index, logic.missionIndex());
        drone.run(logic::update, 60.0);
        assertEquals(NavLogic.MissionState.DONE, logic.missionState());
        assertFalse(logic.resumeMission());
    }

    @Test
    void withoutPilotTheDroneHoldsAndIgnoresSticks() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(3.0, 0.0, 1.0);
        drone.stickPitch = 1.0;
        drone.pilot = false;
        drone.run(logic::update, 10.0);
        assertEquals(NavLogic.Function.HOLD, logic.function());
        assertTrue(Math.hypot(drone.x, drone.z) < 1.0);
        assertEquals("HOLD", drone.out.label());
    }

    @Test
    void stopAndAbortEndTheMission() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 30, -50)));
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.run(logic::update, 2.0);
        logic.pauseMission();
        assertEquals(NavLogic.MissionState.PAUSED, logic.missionState());
        assertEquals(NavLogic.Function.HOLD, logic.function());
        logic.abortMission();
        assertEquals(NavLogic.MissionState.ABORTED, logic.missionState());
        assertFalse(logic.resumeMission());
        logic.startMission(mission, 0);
        logic.stopMission();
        assertEquals(NavLogic.MissionState.IDLE, logic.missionState());
        assertEquals(NavLogic.Function.HOLD, logic.function());
        logic.startMission(Mission.empty(), 0);
        assertEquals(NavLogic.MissionState.IDLE, logic.missionState());
    }

    @Test
    void returnHomeIsRequestedOnce() {
        NavLogic logic = new NavLogic(FlightClass.COMPACT);
        PointMass drone = new PointMass(FlightClass.COMPACT).at(0, 30, 0, 0);
        int[] requests = {0};
        logic.requestReturnHome();
        drone.run(logic::update, 1.0, d -> requests[0] += d.out.returnHome() ? 1 : 0);
        assertEquals(1, requests[0]);
    }

    @Test
    void outputIsForwardedOneToOne() {
        NavOutput out = new NavOutput();
        out.sticks(0.2, -0.3, 0.1, 0.6);
        out.holdAltitude(70.0);
        out.velocity(1.0, -2.0);
        out.maxSpeed(25.0);
        out.heading(-10.0);
        out.label("WP 12/50 TOO LONG");
        out.requestReturnHome();
        out.land();
        List<String> calls = new ArrayList<>();
        out.writeTo(new NavOutput.Sink() {
            public void sticks(double roll, double pitch, double yaw, double throttle) {
                calls.add("sticks " + roll + " " + pitch + " " + yaw + " " + throttle);
            }

            public void angleMode() {
                calls.add("angle");
            }

            public void holdAltitude(double y) {
                calls.add("alt " + y);
            }

            public void climb(double metresPerSecond) {
                calls.add("climb " + metresPerSecond);
            }

            public void holdPosition(double x, double z) {
                calls.add("pos " + x + " " + z);
            }

            public void velocity(double vx, double vz) {
                calls.add("vel " + vx + " " + vz);
            }

            public void maxSpeed(double metresPerSecond) {
                calls.add("max " + metresPerSecond);
            }

            public void yawRate(double degreesPerSecond) {
                calls.add("rate " + degreesPerSecond);
            }

            public void heading(double compassDegrees) {
                calls.add("heading " + compassDegrees);
            }

            public void label(String text) {
                calls.add("label " + text);
            }

            public void requestReturnHome() {
                calls.add("home");
            }

            public void land() {
                calls.add("land");
            }
        });
        assertEquals(List.of("sticks 0.2 -0.3 0.1 0.6", "angle", "alt 70.0", "vel 1.0 -2.0", "max 20.0", "heading 350.0", "label WP 12/50",
                "home", "land"), calls);
        out.reset();
        out.climb(50.0);
        out.velocity(Double.NaN, 1.0);
        assertEquals(NavOutput.MAX_CLIMB, out.climbRate(), 0.0);
        assertEquals(NavOutput.Horizontal.NONE, out.horizontal());
    }

    @Test
    void inputSanitizesSticksAndHidesThemWithoutPilot() {
        NavInput in = new NavInput().sticks(3.0, Double.NaN, -2.0, 7.0).angleLimit(5.0);
        assertEquals(0.0, in.stickRoll(), 0.0);
        assertEquals(NavInput.THROTTLE_CENTER, in.stickThrottle(), 0.0);
        in.pilot(true);
        assertEquals(1.0, in.stickRoll(), 0.0);
        assertEquals(0.0, in.stickPitch(), 0.0);
        assertEquals(-1.0, in.stickYaw(), 0.0);
        assertEquals(1.0, in.stickThrottle(), 0.0);
        assertEquals(NavInput.DEFAULT_ANGLE_LIMIT, in.angleLimit(), 0.0);
        assertTrue(Double.isNaN(in.obstacle(NavInput.Sensor.UP)));
        assertEquals(15.0, new NavLogic(FlightClass.TOUR).sensorRange(), 0.0);
    }
}
