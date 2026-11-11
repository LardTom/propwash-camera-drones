package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.OrbitController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitTest {
    record Result(double radius, double height, double nose, double turned) {
    }

    private static Result orbit(FlightClass c, double radius, double speed, boolean clockwise, double windX, double windZ, double gust) {
        double cx = 12.0;
        double cz = -7.0;
        double startHeading = 180.0;
        PointMass drone = new PointMass(c).at(cx, 40.0, cz - radius, startHeading).wind(windX, windZ, gust);
        NavLogic logic = new NavLogic(c);
        logic.orbit(OrbitController.around(c, drone.input(), cx, 30.0, cz, radius, speed, clockwise));
        drone.run(logic::update, 20.0);
        double[] worst = new double[3];
        double[] turned = {0.0};
        double[] last = {Compass.headingTo(drone.x - cx, drone.z - cz)};
        drone.run(logic::update, 60.0, d -> {
            worst[0] = Math.max(worst[0], Math.abs(Math.hypot(d.x - cx, d.z - cz) - radius));
            worst[1] = Math.max(worst[1], Math.abs(d.y - 40.0));
            double bearing = Compass.headingTo(cx - d.x, cz - d.z);
            worst[2] = Math.max(worst[2], Math.abs(Compass.wrap180(bearing - d.heading)));
            double now = Compass.headingTo(d.x - cx, d.z - cz);
            turned[0] += Compass.wrap180(now - last[0]);
            last[0] = now;
            assertTrue(d.out.hasGimbalTarget());
            assertEquals(30.0, d.out.gimbalY(), 1.0E-9);
            assertEquals("POI", d.out.label());
        });
        return new Result(worst[0], worst[1], worst[2], turned[0]);
    }

    @Test
    void tourOrbitInWindKeepsRadiusHeightAndNose() {
        Result r = orbit(FlightClass.TOUR, 20.0, 5.0, true, 4.0, -3.0, 2.0);
        assertTrue(r.radius() <= 0.5, "Radius " + r.radius());
        assertTrue(r.height() <= 0.3, "Höhe " + r.height());
        assertTrue(r.nose() <= 3.0, "Nase " + r.nose());
        assertTrue(r.turned() > 300.0, "kreist nicht im Uhrzeigersinn: " + r.turned());
    }

    @Test
    void tightCounterClockwiseOrbit() {
        Result r = orbit(FlightClass.COMPACT, 8.0, 3.0, false, -3.0, 2.0, 1.0);
        assertTrue(r.radius() <= 0.5, "Radius " + r.radius());
        assertTrue(r.height() <= 0.3, "Höhe " + r.height());
        assertTrue(r.nose() <= 3.0, "Nase " + r.nose());
        assertTrue(r.turned() < -300.0, "kreist nicht gegen den Uhrzeigersinn: " + r.turned());
    }

    @Test
    void wideFastCineOrbit() {
        Result r = orbit(FlightClass.CINE, 60.0, 10.0, true, 5.0, 0.0, 2.0);
        assertTrue(r.radius() <= 0.5, "Radius " + r.radius());
        assertTrue(r.height() <= 0.3, "Höhe " + r.height());
        assertTrue(r.nose() <= 3.0, "Nase " + r.nose());
    }

    @Test
    void windFromEveryDirectionAtTheSpeedLimit() {
        for (int direction = 0; direction < 8; direction++) {
            double a = Math.toRadians(direction * 45.0);
            Result r = orbit(FlightClass.TOUR, 15.0, 20.0, direction % 2 == 0, 5.0 * Math.sin(a), 5.0 * Math.cos(a), 2.5);
            String name = "Wind aus " + direction * 45 + "°";
            assertTrue(r.radius() <= 0.5, name + ": Radius " + r.radius());
            assertTrue(r.height() <= 0.3, name + ": Höhe " + r.height());
            assertTrue(r.nose() <= 3.0, name + ": Nase " + r.nose());
        }
    }

    @Test
    void sticksChangeSpeedRadiusAndHeight() {
        FlightClass c = FlightClass.TOUR;
        PointMass drone = new PointMass(c).at(0, 30, -15, 180);
        NavLogic logic = new NavLogic(c);
        OrbitController orbit = OrbitController.around(c, drone.input(), 0, 20, 0, 0.0, 3.0, true);
        assertEquals(15.0, orbit.radius(), 1.0E-9);
        logic.orbit(orbit);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 5.0);
        drone.sticks(-1.0, 0, 0, 0.5);
        drone.run(logic::update, 2.0);
        assertTrue(orbit.clockwise());
        assertEquals(orbit.speedLimit(), orbit.speed(), 1.0E-9, "Tempo");
        assertTrue(orbit.speed() > 4.5);
        drone.sticks(0, 1.0, 0, 0.5);
        drone.run(logic::update, 2.0);
        assertTrue(orbit.radius() < 10.0, "Radius " + orbit.radius());
        drone.sticks(0, 0, 0, 1.0);
        drone.run(logic::update, 2.0);
        assertTrue(orbit.altitude() > 40.0, "Höhe " + orbit.altitude());
        assertEquals(NavLogic.Function.ORBIT, logic.function());
        drone.sticks(1.0, 0, 0, 0.5);
        drone.run(logic::update, 10.0);
        assertTrue(!orbit.clockwise(), "Richtung nicht umgekehrt");
        assertTrue(orbit.speed() <= orbit.speedLimit() + 1.0E-9);
    }
}
