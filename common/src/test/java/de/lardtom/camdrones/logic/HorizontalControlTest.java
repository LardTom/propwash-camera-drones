package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalControlTest {
    static final double LIGHT_WIND = 1.2;
    static final double STRONG_WIND = 3.6;
    static final double SETTLE_SECONDS = 5.0;
    static final double SETTLED_SPEED = 0.3;
    static final double HOLD_ERROR = 1.5;

    private static BiConsumer<NavInput, NavOutput> piloted(NavLogic logic, HorizontalControl control) {
        return (in, out) -> {
            logic.update(in, out);
            control.apply(in, out);
        };
    }

    private record Hold(double speedAfterSettling, double error) {
    }

    private static Hold releaseAndHold(FlightClass c, double windX, double windZ, double gust) {
        NavLogic logic = new NavLogic(c);
        HorizontalControl control = new HorizontalControl();
        BiConsumer<NavInput, NavOutput> chain = piloted(logic, control);
        PointMass drone = new PointMass(c).at(0, 30, 0, 0).wind(windX, windZ, gust);
        drone.sticks(0, 0.6, 0, 0.5);
        drone.run(chain, 6.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(chain, SETTLE_SECONDS);
        double speed = drone.speed();
        double x = drone.x;
        double z = drone.z;
        double[] error = {0.0};
        drone.run(chain, 30.0, d -> error[0] = Math.max(error[0], Math.hypot(d.x - x, d.z - z)));
        return new Hold(speed, error[0]);
    }

    @Test
    void lightWindHoldsBelowThreeTenthsAfterFiveSecondsAndWithinTheGpsError() {
        for (FlightClass c : FlightClass.values()) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = Math.toRadians(direction * 45.0);
                Hold hold = releaseAndHold(c, LIGHT_WIND * Math.cos(angle), LIGHT_WIND * Math.sin(angle), 0.3);
                String name = c + " Wind aus " + direction * 45 + "°";
                assertTrue(hold.speedAfterSettling() < SETTLED_SPEED, name + ": " + hold.speedAfterSettling() + " m/s nach 5 s");
                assertTrue(hold.error() < HOLD_ERROR, name + ": weicht " + hold.error() + " m ab");
                assertTrue(hold.error() < 0.3, name + ": Windausgleich zu schwach, " + hold.error() + " m");
            }
        }
    }

    @Test
    void strongWindWithGustsStaysWithinOneAndAHalfMetres() {
        for (FlightClass c : FlightClass.values()) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = Math.toRadians(direction * 45.0);
                Hold hold = releaseAndHold(c, STRONG_WIND * Math.cos(angle), STRONG_WIND * Math.sin(angle), 1.0);
                String name = c + " starker Wind aus " + direction * 45 + "°";
                assertTrue(hold.speedAfterSettling() < 0.5, name + ": " + hold.speedAfterSettling() + " m/s nach 5 s");
                assertTrue(hold.error() < HOLD_ERROR, name + ": weicht " + hold.error() + " m ab");
            }
        }
    }

    @Test
    void windIsLearnedByTheIntegralAndBounded() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        HorizontalControl control = new HorizontalControl();
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(STRONG_WIND, 0.0, 0.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(piloted(logic, control), 30.0);
        assertEquals(-PointMass.DRAG * STRONG_WIND, control.windX(), 0.1, "Integral = Gegenkraft zum Wind");
        assertEquals(0.0, control.windZ(), 0.05);
        PointMass storm = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(60.0, 0.0, 0.0);
        HorizontalControl capped = new HorizontalControl();
        storm.sticks(0, 0, 0, 0.5);
        storm.run(piloted(new NavLogic(FlightClass.TOUR), capped), 20.0);
        assertTrue(Math.hypot(capped.windX(), capped.windZ()) <= HorizontalControl.WIND_LIMIT + 1.0E-9);
    }

    @Test
    void sticksFlyTheCommandedSpeedInNormalAndCine() {
        for (FlightClass c : FlightClass.values()) {
            NavLogic logic = new NavLogic(c);
            HorizontalControl control = new HorizontalControl();
            PointMass drone = new PointMass(c).at(0, 30, 0, 90).wind(LIGHT_WIND, 0.0, 0.3);
            drone.sticks(0, 0.6, 0, 0.5);
            drone.run(piloted(logic, control), 8.0);
            assertEquals(0.6 * c.normalSpeed(), drone.speed(), 0.1 * c.normalSpeed(), c + " N");
            NavLogic cine = new NavLogic(c);
            cine.stickMode(NavLogic.StickMode.CINE);
            PointMass slow = new PointMass(c).at(0, 30, 0, 90).wind(LIGHT_WIND, 0.0, 0.3);
            slow.sticks(0, 1.0, 0, 0.5);
            slow.run(piloted(cine, new HorizontalControl()), 10.0);
            assertEquals(c.cineSpeed(), slow.speed(), 0.25, c + " C");
        }
    }

    @Test
    void tiltStaysWithinTheAssistedLimit() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        HorizontalControl control = new HorizontalControl();
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        double limit = HorizontalControl.MAX_TILT / drone.angleLimit;
        double[] worst = {0.0};
        drone.sticks(0.7, 1.0, 0.3, 0.5);
        drone.run(piloted(logic, control), 4.0, d -> worst[0] = Math.max(worst[0], Math.max(Math.abs(d.out.stickPitch()), Math.abs(d.out.stickRoll()))));
        drone.sticks(0, 0, 0, 0.5);
        drone.run(piloted(logic, control), 4.0, d -> worst[0] = Math.max(worst[0], Math.max(Math.abs(d.out.stickPitch()), Math.abs(d.out.stickRoll()))));
        assertTrue(worst[0] <= limit + 1.0E-9, "Neigung " + worst[0] * drone.angleLimit + "°");
        assertTrue(worst[0] > 0.5 * limit, "nutzt die Neigung nicht");
    }

    private static double forwardTilt(double sightHeight, double forwardSensor) {
        NavInput in = new NavInput().time(1.0).position(0.0, 67.0, 0.0).velocity(0.0, 0.0, -11.0).attitude(0.0, 0.0, 0.0).pilot(true)
                .obstacle(NavInput.Sensor.FORWARD, forwardSensor).obstacle(NavInput.Sensor.BACK, Double.POSITIVE_INFINITY).sightHeight(sightHeight);
        NavOutput out = new NavOutput();
        out.velocity(0.0, -15.0);
        out.maxSpeed(15.0);
        out.holdAltitude(67.0);
        assertTrue(new HorizontalControl().apply(in, out));
        return out.stickPitch() * in.angleLimit();
    }

    @Test
    void nearTheGroundTheTiltKeepsWallsInSightWithTheBrakeOfASmallerAngleLimit() {
        double height = 2.7;
        NavInput in = new NavInput().time(1.0).position(0.0, 67.0, 0.0).velocity(0.0, 0.0, -11.0).attitude(0.0, 0.0, 0.0).pilot(true)
                .angleLimit(30.0).obstacle(NavInput.Sensor.FORWARD, Double.POSITIVE_INFINITY).obstacle(NavInput.Sensor.BACK, Double.POSITIVE_INFINITY)
                .sightHeight(height);
        NavOutput out = new NavOutput();
        out.velocity(0.0, -15.0);
        out.maxSpeed(15.0);
        out.holdAltitude(67.0);
        assertTrue(new HorizontalControl().apply(in, out));
        double tilt = out.stickPitch() * in.angleLimit();
        double need = ObstacleGuard.STOP_DISTANCE + 11.0 * ObstacleGuard.REACTION + 11.0 * 11.0 / (2.0 * HorizontalControl.G * Math.tan(Math.toRadians(30.0)));
        assertTrue(tilt > HorizontalControl.MIN_SIGHT_TILT, "neigt nicht: " + tilt + "°");
        assertTrue(height / Math.tan(Math.toRadians(tilt)) >= need - 1.0E-6, "sieht nur " + height / Math.tan(Math.toRadians(tilt)) + " m weit, braucht " + need + " m");
    }

    @Test
    void nearTheGroundTheTiltTowardsTheFlightKeepsWallsInSight() {
        double height = 2.7;
        double tilt = forwardTilt(height, Double.POSITIVE_INFINITY);
        double sight = height / Math.tan(Math.toRadians(tilt));
        assertTrue(tilt > HorizontalControl.MIN_SIGHT_TILT, "neigt nicht: " + tilt + "°");
        assertTrue(sight >= ObstacleGuard.sightNeeded(11.0) - 1.0E-6, "sieht nur " + sight + " m weit, braucht " + ObstacleGuard.sightNeeded(11.0) + " m");
        assertEquals(HorizontalControl.MAX_TILT, forwardTilt(Double.NaN, Double.POSITIVE_INFINITY), 1.0E-6, "ohne Höhe über Grund");
        assertEquals(HorizontalControl.MAX_TILT, forwardTilt(height, Double.NaN), 1.0E-6, "ohne Sensor vorn");
        assertEquals(HorizontalControl.MAX_TILT, forwardTilt(40.0, Double.POSITIVE_INFINITY), 1.0E-6, "hoch über Grund");
    }

    @Test
    void holdIsIndependentOfTheNoseDirection() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        HorizontalControl control = new HorizontalControl();
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(2.0, 1.0, 0.5);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(piloted(logic, control), 8.0);
        double x = drone.x;
        double z = drone.z;
        double[] error = {0.0};
        drone.sticks(0, 0, 0.6, 0.5);
        drone.run(piloted(logic, control), 12.0, d -> error[0] = Math.max(error[0], Math.hypot(d.x - x, d.z - z)));
        assertTrue(error[0] < 0.5, "Gieren verschiebt die Drohne um " + error[0] + " m");
    }

    @Test
    void stopsBeforeAWallLikeTheGuardWants() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        HorizontalControl control = new HorizontalControl();
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 10, 40, 0);
        drone.vz = -FlightClass.TOUR.normalSpeed();
        PointMass.Box wall = new PointMass.Box(-30, 0, -1.0, 30, 40, 0.0);
        drone.boxes.add(wall);
        drone.sticks(0, 1.0, 0, 0.5);
        double[] closest = {Double.POSITIVE_INFINITY};
        drone.run(piloted(logic, control), 15.0, d -> closest[0] = Math.min(closest[0], wall.distanceTo(d.x, d.y, d.z)));
        assertFalse(drone.crashed);
        assertTrue(closest[0] >= ObstacleGuard.STOP_DISTANCE - 0.3, "Abstand " + closest[0]);
        assertTrue(drone.speed() < SETTLED_SPEED, "steht nicht: " + drone.speed());
    }

    @Test
    void attiAndSportSticksPassUntouched() {
        HorizontalControl control = new HorizontalControl();
        NavInput in = new NavInput().time(1.0).position(0, 30, 0).velocity(1.0, 0, 0).attitude(0, 0, 0).heightAboveGround(30);
        NavOutput out = new NavOutput();
        out.sticks(0.2, -0.3, 0.1, 0.6);
        out.holdAltitude(30.0);
        assertFalse(control.apply(in, out));
        assertTrue(out.hasSticks());
        assertEquals(0.2, out.stickRoll(), 1.0E-12);
        assertEquals(-0.3, out.stickPitch(), 1.0E-12);
        assertFalse(control.active());

        NavOutput noHeight = new NavOutput();
        noHeight.velocity(2.0, 0.0);
        assertFalse(control.apply(in, noHeight), "ohne Höhenvorgabe bleibt es bei Propwash");
        assertEquals(NavOutput.Horizontal.VELOCITY, noHeight.horizontal());
    }

    @Test
    void groundedDroneStaysLevelAndForgetsTheWind() {
        HorizontalControl control = new HorizontalControl();
        NavInput air = new NavInput().time(0.0).position(0, 30, 0).velocity(-1.0, 0, 0).attitude(0, 0, 0).heightAboveGround(30);
        NavOutput out = new NavOutput();
        for (int i = 0; i < 100; i++) {
            air.time(i * 0.01);
            out.reset();
            out.holdPosition(0.0, 0.0);
            out.holdAltitude(30.0);
            assertTrue(control.apply(air, out));
        }
        assertTrue(control.windX() > 0.5);
        NavInput ground = new NavInput().time(1.1).position(0, 0, 0).velocity(0, 0, 0).attitude(0, 0, 0).heightAboveGround(0).grounded(true);
        out.reset();
        out.holdPosition(5.0, 0.0);
        out.climb(0.0);
        assertTrue(control.apply(ground, out));
        assertEquals(0.0, out.stickRoll(), 1.0E-12);
        assertEquals(0.0, out.stickPitch(), 1.0E-12);
        assertEquals(0.0, control.windX(), 1.0E-12);
        assertEquals(NavOutput.Horizontal.NONE, out.horizontal());
    }

    @Test
    void yawAndThrottleKeepTheirOwnOutputs() {
        HorizontalControl control = new HorizontalControl();
        NavInput in = new NavInput().time(0.0).position(0, 30, 0).velocity(0, 0, 0).attitude(90, 0, 0).heightAboveGround(30)
                .pilot(true).sticks(0.0, 0.0, 0.4, 0.7);
        NavOutput rate = new NavOutput();
        rate.velocity(3.0, 0.0);
        rate.climb(1.0);
        rate.yawRate(45.0);
        assertTrue(control.apply(in, rate));
        assertEquals(NavOutput.Yaw.RATE, rate.yaw());
        assertEquals(45.0, rate.yawRateTarget(), 1.0E-12);
        assertEquals(NavOutput.Vertical.CLIMB, rate.vertical());
        assertTrue(rate.stickPitch() > 0.0, "Nase nach Osten, Wunsch nach Osten: vorwärts");
        assertEquals(0.0, rate.stickRoll(), 1.0E-9);
        NavOutput free = new NavOutput();
        free.velocity(0.0, 3.0);
        free.holdAltitude(30.0);
        assertTrue(control.apply(in, free));
        assertEquals(0.4, free.stickYaw(), 1.0E-12, "ohne Gier-Vorgabe gilt der Knüppel");
        assertTrue(free.stickRoll() > 0.0, "Nase nach Osten, Wunsch nach Süden: rechts");
    }
}
