package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.CourseLock;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseLockTest {
    @Test
    void forwardKeepsTheStoredCourseWhileTheNoseTurns() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        logic.courseLock(drone.heading);
        drone.sticks(0, 1.0, 0.6, 0.5);
        double[] lateral = {0.0};
        double[] turned = {0.0};
        double[] last = {drone.heading};
        drone.run(logic::update, 12.0, d -> {
            lateral[0] = Math.max(lateral[0], Math.abs(d.x));
            double step = d.heading - last[0];
            turned[0] += step < -180 ? step + 360 : step > 180 ? step - 360 : step;
            last[0] = d.heading;
            assertEquals("LOCK", d.out.label());
        });
        assertTrue(turned[0] > 360.0, "Nase dreht nicht frei: " + turned[0]);
        assertTrue(lateral[0] < 0.3, "Kurs verlassen: " + lateral[0]);
        assertTrue(drone.z < -100.0, "fliegt nicht nach Norden: " + drone.z);
        assertTrue(drone.speed() > FlightClass.TOUR.normalSpeed() - 0.5, "Tempo " + drone.speed());
    }

    @Test
    void rollMovesSidewaysRelativeToTheCourse() {
        NavLogic logic = new NavLogic(FlightClass.COMPACT);
        PointMass drone = new PointMass(FlightClass.COMPACT).at(0, 30, 0, 200);
        logic.courseLock(90.0);
        drone.sticks(1.0, 0, 0, 0.5);
        drone.run(logic::update, 6.0);
        assertTrue(drone.z > 30.0 && Math.abs(drone.x) < 0.5, "rechts vom Kurs Osten ist Süden: " + drone.x + "," + drone.z);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 6.0);
        assertTrue(drone.speed() < 0.3, "bremst nicht");
    }

    @Test
    void cineLockUsesCineSpeed() {
        NavLogic logic = new NavLogic(FlightClass.CINE);
        logic.stickMode(NavLogic.StickMode.CINE);
        PointMass drone = new PointMass(FlightClass.CINE).at(0, 30, 0, 45);
        logic.courseLock(45.0);
        assertEquals(45.0, ((CourseLock) logic.activeMode()).course(), 1.0E-9);
        drone.sticks(0, 1.0, 0, 0.5);
        double[] top = {0.0};
        drone.run(logic::update, 10.0, d -> top[0] = Math.max(top[0], d.speed()));
        assertTrue(top[0] <= FlightClass.CINE.cineSpeed() * 1.05 && top[0] > FlightClass.CINE.cineSpeed() - 0.3, "Tempo " + top[0]);
    }
}
