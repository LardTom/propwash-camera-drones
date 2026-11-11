package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoShotsTest {
    private static NavLogic shoot(PointMass drone, AutoShots.Shot shot) {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.autoShot(AutoShots.ahead(FlightClass.TOUR, shot, drone.input()));
        return logic;
    }

    @Test
    void everyShotTakesTenToTwentySecondsAndThenHolds() {
        for (AutoShots.Shot shot : AutoShots.Shot.values()) {
            double seconds = AutoShots.duration(shot);
            assertTrue(seconds >= 10.0 && seconds <= 20.0, shot + ": " + seconds);
            PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(2.0, 1.0, 1.0);
            NavLogic logic = shoot(drone, shot);
            drone.run(logic::update, seconds - 0.2);
            assertEquals("AUTO", drone.out.label(), shot.name());
            drone.run(logic::update, 8.0);
            assertEquals("HOLD", drone.out.label(), shot.name());
            assertTrue(((AutoShots) logic.activeMode()).done());
            double x = drone.x;
            double z = drone.z;
            drone.run(logic::update, 5.0);
            assertTrue(Math.hypot(drone.x - x, drone.z - z) < 0.3, shot + " hält nicht");
        }
    }

    @Test
    void retreatFliesBackwardsAndUpWhileTheNoseStays() {
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(3.0, 0.0, 1.5);
        NavLogic logic = shoot(drone, AutoShots.Shot.RETREAT);
        double[] nose = {0.0};
        drone.run(logic::update, AutoShots.RETREAT_SECONDS, d -> {
            nose[0] = Math.max(nose[0], Math.abs(Compass.wrap180(d.heading)));
            assertTrue(d.out.hasGimbalTarget());
        });
        assertTrue(nose[0] < 2.0, "Nase " + nose[0]);
        assertEquals(30.0, drone.z, 2.5);
        assertTrue(Math.abs(drone.x) < 0.5, "seitlich " + drone.x);
        assertEquals(35.0, drone.y, 1.5);
    }

    @Test
    void riseClimbsStraightAndTiltsTheCameraDown() {
        PointMass drone = new PointMass(FlightClass.TOUR).at(5, 20, 5, 90).wind(4.0, -2.0, 2.0);
        NavLogic logic = shoot(drone, AutoShots.Shot.RISE);
        double[] drift = {0.0};
        double[] firstTilt = {Double.NaN};
        drone.run(logic::update, AutoShots.RISE_SECONDS, d -> {
            drift[0] = Math.max(drift[0], Math.hypot(d.x - 5, d.z - 5));
            if (Double.isNaN(firstTilt[0])) {
                firstTilt[0] = d.out.gimbalTilt();
            }
        });
        assertTrue(drift[0] < 1.0, "Abdrift " + drift[0]);
        assertEquals(0.0, firstTilt[0], 1.0);
        assertTrue(drone.out.gimbalTilt() < -85.0, "Neigung " + drone.out.gimbalTilt());
        assertEquals(38.0, drone.y, 1.5);
    }

    @Test
    void circleGoesOnceAroundTheSubject() {
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).wind(2.0, 2.0, 1.0);
        AutoShots shot = AutoShots.ahead(FlightClass.TOUR, AutoShots.Shot.CIRCLE, drone.input());
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.autoShot(shot);
        double[] radius = {0.0};
        double[] turned = {0.0};
        double[] last = {Compass.headingTo(drone.x - shot.subjectX(), drone.z - shot.subjectZ())};
        drone.run(logic::update, AutoShots.CIRCLE_SECONDS, d -> {
            double r = Math.hypot(d.x - shot.subjectX(), d.z - shot.subjectZ());
            if (d.time > 3.0) {
                radius[0] = Math.max(radius[0], Math.abs(r - AutoShots.SUBJECT_DISTANCE));
            }
            double now = Compass.headingTo(d.x - shot.subjectX(), d.z - shot.subjectZ());
            turned[0] += Compass.wrap180(now - last[0]);
            last[0] = now;
        });
        assertTrue(radius[0] < 0.7, "Radius " + radius[0]);
        assertEquals(360.0, turned[0], 25.0);
    }

    @Test
    void spiralWidensAndClimbs() {
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0);
        AutoShots shot = AutoShots.ahead(FlightClass.TOUR, AutoShots.Shot.SPIRAL, drone.input());
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.autoShot(shot);
        drone.run(logic::update, AutoShots.SPIRAL_SECONDS);
        double r = Math.hypot(drone.x - shot.subjectX(), drone.z - shot.subjectZ());
        assertEquals(AutoShots.SUBJECT_DISTANCE + AutoShots.SPIRAL_WIDEN, r, 1.5);
        assertEquals(20.0 + AutoShots.SPIRAL_CLIMB * AutoShots.SPIRAL_SECONDS, drone.y, 1.5);
    }

    @Test
    void sticksCancelAShot() {
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0);
        NavLogic logic = shoot(drone, AutoShots.Shot.RETREAT);
        drone.pilot = true;
        drone.run(logic::update, 2.0);
        assertEquals(NavLogic.Function.AUTO_SHOT, logic.function());
        drone.sticks(0, 0.8, 0, 0.5);
        drone.run(logic::update, 0.1);
        assertEquals(NavLogic.Function.NONE, logic.function());
        assertEquals("N", drone.out.label());
    }
}
