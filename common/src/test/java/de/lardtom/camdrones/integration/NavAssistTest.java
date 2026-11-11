package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.AttiMode;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.logic.nav.VerticalControl;
import de.lardtom.propwash.api.flight.DroneProfile;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistTest {
    private static final double DT = 0.01;
    private static final Set<SensorDirection> ALL = EnumSet.allOf(SensorDirection.class);

    static final class Context implements NavContext {
        final Set<CamControl> active = EnumSet.noneOf(CamControl.class);
        int maxAltitude = CamWorldRules.DEFAULT_MAX_ALTITUDE;
        int maxDistance = CamWorldRules.DEFAULT_MAX_DISTANCE;
        Mission mission;
        NavTarget follow;
        NavTarget poi;
        NavTarget home;
        double[] poiRay;
        final List<String> progress = new ArrayList<>();
        final List<double[]> waypoints = new ArrayList<>();
        final List<NavStatus> statuses = new ArrayList<>();
        int limitQueries;

        @Override
        public boolean active(CamControl control) {
            return this.active.contains(control);
        }

        @Override
        public int maxAltitude() {
            this.limitQueries++;
            return this.maxAltitude;
        }

        @Override
        public int maxDistance() {
            return this.maxDistance;
        }

        @Override
        public Optional<NavTarget> home() {
            return Optional.ofNullable(this.home);
        }

        @Override
        public Optional<Mission> mission() {
            return Optional.ofNullable(this.mission);
        }

        @Override
        public Optional<NavTarget> followTarget() {
            return Optional.ofNullable(this.follow);
        }

        @Override
        public Optional<NavTarget> pointOfInterest(double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
            this.poiRay = new double[]{x, y, z, dirX, dirY, dirZ, range};
            return Optional.ofNullable(this.poi);
        }

        @Override
        public void waypointAdded(double x, double y, double z, double heading) {
            this.waypoints.add(new double[]{x, y, z, heading});
        }

        @Override
        public void missionProgress(NavLogic.MissionState state, int index) {
            this.progress.add(state + " " + index);
        }

        @Override
        public void status(NavStatus status) {
            this.statuses.add(status);
        }

        NavStatus last() {
            return this.statuses.get(this.statuses.size() - 1);
        }
    }

    private static FakeFlight.Output run(NavAssist assist, FakeFlight.State state) {
        FakeFlight.Output output = new FakeFlight.Output();
        state.step(DT);
        assist.update(state, output);
        return output;
    }

    private static FakeFlight.Output run(NavAssist assist, FakeFlight.State state, int steps) {
        FakeFlight.Output output = null;
        for (int i = 0; i < steps; i++) {
            output = run(assist, state);
        }
        return output;
    }

    private static NavAssist tour(Context context) {
        return new NavAssist(FlightClass.TOUR, ALL, true, context);
    }

    @Test
    void forwardsEveryNavOutputCallOneToOne() {
        NavOutput out = new NavOutput();
        out.sticks(0.25, -0.5, 0.1, 0.6);
        out.holdAltitude(80.5);
        out.holdPosition(12.0, -7.5);
        out.maxSpeed(6.0);
        out.heading(275.0);
        out.label("POI");
        out.requestReturnHome();
        out.land();
        FakeFlight.Output first = new FakeFlight.Output();
        NavAssist.forward(out, first);
        assertEquals(List.of("sticks 0.2500 -0.5000 0.1000 0.6000", "mode ANGLE", "holdAltitude 80.5000", "holdPosition 12.0000 -7.5000",
                "maxSpeed 6.0000", "heading 275.0000", "label POI", "returnHome", "land"), first.calls);

        out.reset();
        out.climb(-3.5);
        out.velocity(4.0, -2.0);
        out.yawRate(-45.0);
        FakeFlight.Output second = new FakeFlight.Output();
        NavAssist.forward(out, second);
        assertEquals(List.of("climb -3.5000", "velocity 4.0000 -2.0000", "yawRate -45.0000"), second.calls);

        out.reset();
        FakeFlight.Output empty = new FakeFlight.Output();
        NavAssist.forward(out, empty);
        assertTrue(empty.calls.isEmpty(), empty.calls.toString());
    }

    @Test
    void assistOutputMatchesLogicFedWithTheSameInput() {
        FakeFlight.State state = new FakeFlight.State().at(5.0, 80.0, -3.0);
        state.estimatedX = 5.2;
        state.estimatedY = 80.1;
        state.estimatedZ = -3.1;
        state.estimatedVx = 0.0;
        state.estimatedVy = 0.0;
        state.estimatedVz = 0.0;
        state.heading = 30.0;
        Context context = new Context();
        context.maxAltitude = 0;
        NavAssist assist = tour(context);
        NavLogic reference = new NavLogic(FlightClass.TOUR);
        HorizontalControl control = new HorizontalControl();
        VerticalControl vertical = new VerticalControl();
        NavInput in = new NavInput();
        NavOutput expected = new NavOutput();
        for (int i = 0; i < 300; i++) {
            state.stickPitch = i < 150 ? 0.8 : 0.0;
            state.stickYaw = i < 50 ? 0.4 : 0.0;
            state.stickThrottle = i < 100 ? 0.8 : 0.5;
            FakeFlight.Output actual = run(assist, state);
            in.time(state.time).position(state.x, state.y, state.z).velocity(0.0, 0.0, 0.0)
                    .attitude(state.heading, state.pitch, state.roll).gps(true, state.satellites).heightAboveGround(state.heightAboveGround)
                    .grounded(false).battery(state.battery).pilot(true)
                    .sticks(state.stickRoll, state.stickPitch, state.stickYaw, state.stickThrottle);
            for (NavInput.Sensor sensor : NavInput.Sensor.values()) {
                in.obstacle(sensor, Double.POSITIVE_INFINITY);
            }
            reference.update(in, expected);
            control.apply(in, expected);
            vertical.apply(in, expected, state.estimatedY, state.estimatedVy);
            FakeFlight.Output wanted = new FakeFlight.Output();
            NavAssist.forward(expected, wanted);
            assertEquals(wanted.calls, actual.calls, "Schritt " + i);
        }
    }

    @Test
    void pilotFlightsUseTheTruePositionAlsoForTheHeightServerFlightsTheBarometricEstimate() {
        FakeFlight.State state = new FakeFlight.State().at(1.0, 70.0, 2.0);
        state.vx = 0.4;
        state.vy = 0.1;
        NavAssist assist = tour(new Context());
        run(assist, state);
        assertEquals(1.0, assist.input().x(), 1.0E-9);
        assertEquals(70.0, assist.input().y(), 1.0E-9, "ohne Schätzung die echte Höhe");
        state.estimatedX = 1.5;
        state.estimatedY = 70.4;
        state.estimatedZ = 2.5;
        state.estimatedVx = -0.3;
        state.estimatedVy = 0.2;
        state.estimatedVz = 0.3;
        run(assist, state);
        assertEquals(1.0, assist.input().x(), 1.0E-9, "waagrecht regelt das Add-on auf die echte Lage wie der Server-Flug");
        assertEquals(2.0, assist.input().z(), 1.0E-9);
        assertEquals(0.4, assist.input().vx(), 1.0E-9);
        assertEquals(70.0, assist.input().y(), 1.0E-9, "senkrecht regelt das Add-on im Pilotenflug ebenfalls auf die echte Höhe");
        assertEquals(0.1, assist.input().vy(), 1.0E-9);
        assertTrue(assist.input().pilot());
        NavAssist server = new NavAssist(FlightClass.TOUR, ALL, false, new Context());
        run(server, state);
        assertEquals(1.0, server.input().x(), 1.0E-9);
        assertEquals(70.4, server.input().y(), 1.0E-9, "ohne Pilot hält Propwash auf die Barometer-Schätzung, also gilt sie");
        assertEquals(0.2, server.input().vy(), 1.0E-9);
    }

    @Test
    void pilotHeightTargetsAreHandedToPropwashInItsEstimate() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0);
        state.estimatedY = 70.4;
        state.estimatedVy = 0.0;
        NavAssist assist = tour(new Context());
        FakeFlight.Output output = run(assist, state, 100);
        assertFalse(Double.isNaN(output.altitude), output.calls.toString());
        assertEquals(70.4, output.altitude, 0.05, "echte Haltehöhe 70 m, Propwash rechnet 0,4 m höher");
    }

    @Test
    void releasedSticksBrakeAndHoldInNormalMode() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 75.0, 0.0);
        NavAssist assist = tour(new Context());
        FakeFlight.Output output = run(assist, state, 200);
        assertTrue(output.sticks, output.calls.toString());
        assertEquals(StabilizationMode.ANGLE, output.mode);
        assertFalse(output.horizontal(), "waagrecht regelt das Add-on selbst: " + output.calls);
        assertTrue(output.vertical(), output.calls.toString());
        assertTrue(assist.control().active());
        assertEquals(0.0, output.roll, 1.0E-9);
        assertEquals(0.0, output.pitch, 1.0E-9);
        assertEquals("N", output.label);
        assertEquals(NavLogic.StickMode.NORMAL, assist.logic().stickMode());
    }

    @Test
    void sportSwitchFliesSticksInAngleMode() {
        FakeFlight.State state = new FakeFlight.State().on(CamControl.MODE_SPORT);
        state.stickPitch = 0.5;
        NavAssist assist = tour(new Context());
        FakeFlight.Output output = run(assist, state);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode());
        assertTrue(output.sticks);
        assertEquals(StabilizationMode.ANGLE, output.mode);
        assertEquals("S", output.label);
    }

    @Test
    void modeSwitchesApplyAtStartButButtonsDoNot() {
        NavAssist cine = tour(new Context());
        run(cine, new FakeFlight.State().on(CamControl.MODE_CINE));
        assertEquals(NavLogic.StickMode.CINE, cine.logic().stickMode());

        NavAssist held = tour(new Context());
        FakeFlight.State state = new FakeFlight.State().on(CamControl.MODE_CYCLE);
        run(held, state, 10);
        assertEquals(NavLogic.StickMode.NORMAL, held.logic().stickMode());
        state.off(CamControl.MODE_CYCLE);
        run(held, state);
        state.on(CamControl.MODE_CYCLE);
        run(held, state);
        assertEquals(NavLogic.StickMode.CINE, held.logic().stickMode());
    }

    @Test
    void modeCycleSwitchesOncePerPressAndTheLastChangeWins() {
        FakeFlight.State state = new FakeFlight.State();
        NavAssist assist = tour(new Context());
        run(assist, state);
        List<NavLogic.StickMode> seen = new ArrayList<>();
        for (int press = 0; press < 4; press++) {
            state.on(CamControl.MODE_CYCLE);
            run(assist, state, 25);
            seen.add(assist.logic().stickMode());
            state.off(CamControl.MODE_CYCLE);
            run(assist, state, 5);
        }
        assertEquals(List.of(NavLogic.StickMode.CINE, NavLogic.StickMode.SPORT, NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE), seen);

        state.on(CamControl.MODE_SPORT);
        run(assist, state);
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode());
        state.on(CamControl.MODE_CYCLE);
        run(assist, state, 10);
        assertEquals(NavLogic.StickMode.NORMAL, assist.logic().stickMode());
        state.off(CamControl.MODE_CYCLE);
        run(assist, state, 10);
        assertEquals(NavLogic.StickMode.NORMAL, assist.logic().stickMode(), "Schalter bleibt an, die Taste war die letzte Änderung");
        state.off(CamControl.MODE_SPORT).on(CamControl.MODE_CINE);
        run(assist, state);
        assertEquals(NavLogic.StickMode.CINE, assist.logic().stickMode());
    }

    @Test
    void switchesFromTheContextCountLikeFlightStateSwitches() {
        Context context = new Context();
        context.active.add(CamControl.MODE_SPORT);
        NavAssist assist = tour(context);
        run(assist, new FakeFlight.State());
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode());
        context.active.add(CamControl.PAUSE);
        run(assist, new FakeFlight.State());
        assertEquals(NavLogic.Function.HOLD, assist.logic().function());
    }

    @Test
    void pauseHoldsInPlaceWithoutMission() {
        FakeFlight.State state = new FakeFlight.State().at(3.0, 90.0, -4.0);
        NavAssist assist = tour(new Context());
        run(assist, state);
        state.on(CamControl.PAUSE);
        FakeFlight.Output output = run(assist, state, 30);
        assertEquals(NavLogic.Function.HOLD, assist.logic().function());
        assertTrue(output.sticks, output.calls.toString());
        assertTrue(assist.control().active());
        assertEquals("HOLD", output.label);
    }

    @Test
    void missionStartRunsTheContextMissionPausesAndResumes() {
        Context context = new Context();
        context.mission = Mission.of(List.of(Waypoint.at(30.0, 80.0, 0.0), Waypoint.at(30.0, 80.0, 30.0), Waypoint.at(0.0, 80.0, 30.0)));
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0);
        NavAssist assist = tour(context);
        run(assist, state);
        assertTrue(context.progress.isEmpty(), context.progress.toString());

        state.on(CamControl.MISSION_START);
        FakeFlight.Output output = run(assist, state, 20);
        assertEquals(NavLogic.Function.MISSION, assist.logic().function());
        assertEquals(NavLogic.MissionState.RUNNING, assist.logic().missionState());
        assertEquals(List.of("RUNNING 0"), context.progress);
        assertTrue(output.sticks, output.calls.toString());
        assertTrue(assist.control().commandX() > 0.1, "fliegt Richtung erster Wegpunkt: " + assist.control().commandX());
        assertEquals(0.0, assist.control().commandZ(), 0.05);

        state.off(CamControl.MISSION_START).on(CamControl.PAUSE);
        run(assist, state, 5);
        assertEquals(NavLogic.MissionState.PAUSED, assist.logic().missionState());
        assertEquals(NavLogic.Function.HOLD, assist.logic().function());
        assertTrue(assist.status().orElseThrow().warns(NavOutput.Warning.PAUSED));

        state.off(CamControl.PAUSE).on(CamControl.MISSION_START);
        run(assist, state, 5);
        assertEquals(NavLogic.MissionState.RUNNING, assist.logic().missionState());
        assertEquals(List.of("RUNNING 0", "PAUSED 0", "RUNNING 0"), context.progress);
    }

    @Test
    void missionStartWithoutMissionDoesNothing() {
        FakeFlight.State state = new FakeFlight.State();
        NavAssist assist = tour(new Context());
        run(assist, state);
        state.on(CamControl.MISSION_START);
        run(assist, state, 3);
        assertEquals(NavLogic.Function.NONE, assist.logic().function());
        assertEquals(NavLogic.MissionState.IDLE, assist.logic().missionState());
    }

    @Test
    void serverFlightsLeaveTheConversionToPropwash() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 75.0, 0.0);
        NavAssist server = new NavAssist(FlightClass.TOUR, ALL, false, new Context());
        FakeFlight.Output output = run(server, state, 50);
        assertTrue(output.horizontal(), output.calls.toString());
        assertFalse(output.sticks, output.calls.toString());
        assertFalse(server.control().active());
    }

    @Test
    void withoutUsablePositionTheDroneFliesAttiEvenWithAFix() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 75.0, 0.0);
        state.positionValid = false;
        NavAssist assist = tour(new Context());
        FakeFlight.Output output = run(assist, state, 5);
        assertEquals("ATTI", output.label);
        assertTrue(assist.status().orElseThrow().atti());
        assertFalse(assist.control().active());
        state.positionValid = true;
        run(assist, state, 150);
        assertEquals("ATTI", run(assist, state).label, "erst nach zwei Sekunden mit Position wieder N");
        FakeFlight.Output back = run(assist, state, 60);
        assertEquals("N", back.label);
        assertTrue(assist.control().active());
    }

    @Test
    void withoutGpsFixTheDroneFliesAtti() {
        FakeFlight.State state = new FakeFlight.State();
        state.gpsFix = false;
        state.satellites = 0;
        NavAssist assist = tour(new Context());
        FakeFlight.Output output = run(assist, state, 5);
        assertEquals("ATTI", output.label);
        assertEquals(StabilizationMode.ANGLE, output.mode);
        assertTrue(output.sticks);
        NavStatus status = assist.status().orElseThrow();
        assertTrue(status.atti());
        assertTrue(status.warns(NavOutput.Warning.NO_GPS));
    }

    @Test
    void disturbedCompassFromTheApiFliesAttiWithCompassWarning() {
        FakeFlight.State state = new FakeFlight.State();
        state.compassError = 25.0;
        NavAssist assist = tour(new Context());
        FakeFlight.Output output = run(assist, state, 5);
        assertEquals("ATTI", output.label);
        assertEquals(StabilizationMode.ANGLE, output.mode);
        NavStatus status = assist.status().orElseThrow();
        assertTrue(status.atti());
        assertTrue(status.warns(NavOutput.Warning.COMPASS));
        assertFalse(status.warns(NavOutput.Warning.NO_GPS));
        assertEquals(25.0, assist.input().compassError(), 1.0E-9);
        assertFalse(assist.input().compassHealthy());
    }

    @Test
    void testDisturbanceAddsToTheApiError() {
        FakeFlight.State state = new FakeFlight.State();
        state.compassError = 5.0;
        NavAssist assist = tour(new Context());
        try {
            CompassCheck.testDisturbance(40.0);
            run(assist, state, 5);
            assertTrue(assist.status().orElseThrow().warns(NavOutput.Warning.COMPASS));
            assertEquals(45.0, assist.input().compassError(), 1.0E-9);
            CompassCheck.testDisturbance(0.0);
            state.step(AttiMode.REGAIN_SECONDS + 0.5);
            run(assist, state, 5);
            run(assist, state.step(AttiMode.REGAIN_SECONDS + 0.5), 5);
            assertFalse(assist.status().orElseThrow().atti());
            assertFalse(assist.status().orElseThrow().warns(NavOutput.Warning.COMPASS));
        } finally {
            CompassCheck.testDisturbance(0.0);
        }
    }

    private static double forwardSpeedTowardWall(Set<SensorDirection> sensors) {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0).obstacle(SensorDirection.FORWARD, 2.0);
        state.vz = -2.0;
        state.stickPitch = 1.0;
        NavAssist assist = new NavAssist(FlightClass.TOUR, sensors, true, new Context());
        run(assist, state, 50);
        return -assist.control().commandZ();
    }

    @Test
    void onlyTheSensorsOfTheBoardReachTheLogic() {
        double guarded = forwardSpeedTowardWall(ALL);
        double blind = forwardSpeedTowardWall(EnumSet.of(SensorDirection.DOWN));
        assertTrue(guarded <= ObstacleGuard.commandLimit(2.0) + 1.0E-6, "mit Sensor vorn: " + guarded);
        assertTrue(blind > guarded + 1.0, "ohne Sensor vorn: " + blind + " gegen " + guarded);
        NavAssist compact = new NavAssist(FlightClass.TOUR, EnumSet.of(SensorDirection.DOWN), true, new Context());
        run(compact, new FakeFlight.State().obstacle(SensorDirection.FORWARD, 2.0).obstacle(SensorDirection.DOWN, 6.0));
        assertTrue(Double.isNaN(compact.input().obstacle(NavInput.Sensor.FORWARD)));
        assertEquals(6.0, compact.input().obstacle(NavInput.Sensor.DOWN), 1.0E-9);
        assertEquals(ObstacleGuard.SENSOR_RANGE, compact.sensorRange(), 1.0E-9);
        assertEquals(0.0, new NavAssist(FlightClass.TOUR, Set.of(), true, NavContext.NONE).sensorRange(), 1.0E-9);
    }

    @Test
    void altitudeLimitComesFromTheContextAboveTheTakeOffPoint() {
        Context context = new Context();
        context.maxAltitude = 10;
        FakeFlight.State state = new FakeFlight.State().at(0.0, 64.0, 0.0);
        state.grounded = true;
        NavAssist assist = tour(context);
        run(assist, state, 3);
        assertEquals(64.0, assist.home().orElseThrow().y(), 1.0E-9);
        state.grounded = false;
        state.at(0.0, 73.9, 0.0);
        state.stickThrottle = 1.0;
        state.step(1.0);
        FakeFlight.Output output = run(assist, state);
        assertEquals(10, assist.maxAltitude());
        assertFalse(Double.isNaN(output.climb), output.calls.toString());
        assertTrue(output.climb <= 0.1 + 1.0E-6, "Steigen an der Grenze: " + output.climb);
        assertEquals(64.0, assist.home().orElseThrow().y(), 1.0E-9);

        Context free = new Context();
        free.maxAltitude = 0;
        NavAssist open = tour(free);
        FakeFlight.State high = new FakeFlight.State().at(0.0, 73.9, 0.0);
        high.stickThrottle = 1.0;
        FakeFlight.Output unlimited = run(open, high, 5);
        assertTrue(unlimited.climb > 1.0, "ohne Grenze: " + unlimited.climb);
    }

    @Test
    void limitsAreReadTwicePerSecondAndHomeFromTheContextWins() {
        Context context = new Context();
        context.home = NavTarget.at(0.0, 50.0, 0.0);
        NavAssist assist = tour(context);
        FakeFlight.State state = new FakeFlight.State().at(0.0, 90.0, 0.0);
        run(assist, state, 100);
        assertEquals(2, context.limitQueries);
        assertEquals(50.0, assist.home().orElseThrow().y(), 1.0E-9);
        assertEquals(170.0, assist.logic().heightLimit().ceiling(), 1.0E-9);
    }

    @Test
    void followMeUsesTheContextTargetWhileTheSwitchIsOn() {
        Context context = new Context();
        context.follow = new NavTarget(0.0, 64.0, 30.0, 0.0, 0.0, 0.0);
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0).on(CamControl.FOLLOW_ME);
        NavAssist assist = tour(context);
        FakeFlight.Output output = run(assist, state, 50);
        assertEquals(NavLogic.Function.FOLLOW, assist.logic().function());
        assertTrue(assist.input().hasTarget());
        assertEquals(30.0, assist.input().targetZ(), 1.0E-9);
        assertEquals("FOLLOW", output.label);
        state.off(CamControl.FOLLOW_ME);
        run(assist, state);
        assertEquals(NavLogic.Function.NONE, assist.logic().function());
        assertFalse(assist.input().hasTarget());
    }

    @Test
    void courseLockFollowsItsSwitch() {
        FakeFlight.State state = new FakeFlight.State();
        state.heading = 120.0;
        NavAssist assist = tour(new Context());
        run(assist, state);
        state.on(CamControl.COURSE_LOCK);
        run(assist, state);
        assertEquals(NavLogic.Function.COURSE_LOCK, assist.logic().function());
        state.off(CamControl.COURSE_LOCK);
        run(assist, state);
        assertEquals(NavLogic.Function.NONE, assist.logic().function());
    }

    @Test
    void pointOfInterestCirclesTheContextPointAlongTheCameraRay() {
        Context context = new Context();
        context.poi = NavTarget.at(0.0, 64.0, -20.0);
        FakeFlight.State state = new FakeFlight.State().at(0.0, 75.0, 0.0);
        NavAssist assist = tour(context);
        run(assist, state);
        state.on(CamControl.POI_HERE);
        FakeFlight.Output output = run(assist, state, 20);
        assertEquals(NavLogic.Function.ORBIT, assist.logic().function());
        assertEquals("POI", output.label);
        assertNotNull(context.poiRay);
        assertEquals(-1.0, context.poiRay[5], 1.0E-9);
        assertEquals(NavAssist.POI_RANGE, context.poiRay[6], 1.0E-9);

        Context miss = new Context();
        NavAssist none = tour(miss);
        FakeFlight.State look = new FakeFlight.State();
        run(none, look);
        look.on(CamControl.POI_HERE);
        run(none, look);
        assertEquals(NavLogic.Function.NONE, none.logic().function());
    }

    @Test
    void waypointButtonReportsPositionAndHeading() {
        Context context = new Context();
        FakeFlight.State state = new FakeFlight.State().at(12.0, 81.0, -6.0);
        state.heading = 45.0;
        NavAssist assist = tour(context);
        run(assist, state);
        state.on(CamControl.WAYPOINT_ADD);
        run(assist, state, 10);
        assertEquals(1, context.waypoints.size());
        double[] waypoint = context.waypoints.get(0);
        assertEquals(12.0, waypoint[0], 1.0E-9);
        assertEquals(81.0, waypoint[1], 1.0E-9);
        assertEquals(-6.0, waypoint[2], 1.0E-9);
        assertEquals(45.0, waypoint[3], 1.0E-9);
    }

    @Test
    void statusIsPublishedEveryUpdate() {
        Context context = new Context();
        NavAssist assist = new NavAssist(FlightClass.CINE, ALL, true, context);
        run(assist, new FakeFlight.State().on(CamControl.MODE_CINE), 3);
        assertEquals(3, context.statuses.size());
        NavStatus status = context.last();
        assertEquals(FlightClass.CINE, status.flightClass());
        assertEquals("C", status.label());
        assertEquals(NavLogic.StickMode.CINE, status.stickMode());
        assertEquals(NavLogic.Function.NONE, status.function());
        assertFalse(status.atti());
        assertEquals(status, assist.status().orElseThrow());
    }

    @Test
    void resetKeepsTheFlightModeAndForgetsTheRest() {
        Context context = new Context();
        FakeFlight.State state = new FakeFlight.State().on(CamControl.MODE_SPORT);
        NavAssist assist = tour(context);
        run(assist, state, 3);
        state.off(CamControl.MODE_SPORT).on(CamControl.PAUSE);
        run(assist, state);
        assertEquals(NavLogic.Function.HOLD, assist.logic().function());
        assist.reset();
        assertEquals(NavLogic.StickMode.SPORT, assist.logic().stickMode());
        assertEquals(NavLogic.Function.NONE, assist.logic().function());
        assertTrue(assist.home().isEmpty());
        assertTrue(assist.status().isEmpty());
        run(assist, state, 3);
        assertEquals(NavLogic.Function.NONE, assist.logic().function(), "gehaltener Taster löst nach dem Zurücksetzen nicht aus");
    }

    private static DroneProfile profile(String frame, String stack, double massKg) {
        return new DroneProfile(frame, stack, "camdrones:m2008_500", "camdrones:fold_9", "camdrones:cam_link_pro",
                "camdrones:smart_4s_4240_hv", Map.of("front", "camdrones:gimbal_tour"), massKg);
    }

    @Test
    void factoryNeedsANavBoardAndPicksClassAndSensors() {
        NavAssist tour = NavAssist.pilotFor(profile("camdrones:tour_fold_frame", "camdrones:nav_fc_pro", 0.72));
        assertNotNull(tour);
        assertEquals(FlightClass.TOUR, tour.flightClass());
        assertEquals(ALL, tour.sensors());
        assertTrue(tour.piloted());

        NavAssist cine = NavAssist.pilotFor(profile("camdrones:cine_frame", "camdrones:nav_fc_pro", 0.5));
        assertEquals(FlightClass.CINE, cine.flightClass());

        NavAssist foreign = NavAssist.pilotFor(profile("justmoreparts:x8_frame", "camdrones:nav_fc_compact", 3.2));
        assertEquals(FlightClass.CINE, foreign.flightClass());
        assertEquals(EnumSet.of(SensorDirection.DOWN), foreign.sensors());
        assertEquals(FlightClass.COMPACT, NavAssist.flightClass(profile("propwash:frame_5in", "camdrones:nav_fc_pro", 0.3)));
        assertEquals(FlightClass.TOUR, NavAssist.flightClass(profile("propwash:frame_7in", "camdrones:nav_fc_pro", 1.1)));

        assertNull(NavAssist.pilotFor(profile("camdrones:tour_fold_frame", "propwash:stack_f7", 0.72)));
        assertNull(NavAssist.pilotFor(profile("camdrones:tour_fold_frame", "kein gültiger Name", 0.72)));
        assertFalse(NavAssist.equipped(null));
        assertEquals("camdrones:nav", NavAssist.ID);
    }

    @Test
    void pilotFactoryUsesTheInstalledPilotContext() {
        Context context = new Context();
        NavContexts.pilot(() -> context);
        try {
            NavAssist assist = NavAssist.pilotFor(profile("camdrones:tour_fold_frame", "camdrones:nav_fc_pro", 0.72));
            assertEquals(context, assist.context());
        } finally {
            NavContexts.pilot(() -> NavContext.NONE);
        }
        assertEquals(NavContext.NONE, NavContexts.pilot());
    }
}
