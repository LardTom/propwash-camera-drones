package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowTest {
    private static void walk(PointMass drone, double heading, double speed) {
        drone.targetVx = Compass.forwardX(heading) * speed;
        drone.targetVz = Compass.forwardZ(heading) * speed;
        drone.targetVy = 0.0;
    }

    private static PointMass start(FlightClass c, NavLogic logic, FollowController.Style style) {
        PointMass drone = new PointMass(c).at(0, 69, 8, 0).wind(3.0, 2.0, 1.5);
        drone.target(0, 64, 0, 0, 0, 0);
        drone.input();
        logic.follow(style);
        return drone;
    }

    @Test
    void behindKeepsEightMetresAndStopsWithoutPendulum() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = start(FlightClass.TOUR, logic, FollowController.Style.BEHIND);
        drone.run(logic::update, 5.0);
        walk(drone, 0.0, 4.3);
        drone.run(logic::update, 6.0);
        double[] distance = {Double.POSITIVE_INFINITY, 0.0};
        double[] height = {0.0};
        double[] nose = {0.0};
        double[] turn = {0.0};
        drone.run(logic::update, 20.0, d -> {
            double h = Math.hypot(d.x - d.targetX, d.z - d.targetZ);
            distance[0] = Math.min(distance[0], h);
            distance[1] = Math.max(distance[1], h);
            height[0] = Math.max(height[0], Math.abs(d.y - d.targetY - FollowController.HEIGHT));
            nose[0] = Math.max(nose[0], Math.abs(Compass.wrap180(Compass.headingTo(d.targetX - d.x, d.targetZ - d.z) - d.heading)));
        });
        assertTrue(distance[0] >= 7.0 && distance[1] <= 9.0, "Abstand " + distance[0] + " … " + distance[1]);
        assertTrue(height[0] <= 0.5, "Höhe " + height[0]);
        assertTrue(nose[0] <= 5.0, "Nase " + nose[0]);
        for (int i = 0; i < 30; i++) {
            walk(drone, 3.0 * (i + 1), 4.3);
            drone.run(logic::update, 0.1);
        }
        drone.run(logic::update, 6.0);
        drone.run(logic::update, 15.0, d -> turn[0] = Math.max(turn[0], Math.abs(Math.hypot(d.x - d.targetX, d.z - d.targetZ) - 8.0)));
        assertTrue(turn[0] <= 1.0, "Abstand nach der Kurve " + turn[0]);
        walk(drone, 90.0, 0.0);
        drone.gust = 0.0;
        drone.run(logic::update, 8.0);
        double[] box = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0};
        int[] reversals = {0};
        double[] lastVx = {drone.vx};
        drone.run(logic::update, 12.0, d -> {
            box[0] = Math.min(box[0], d.x);
            box[1] = Math.max(box[1], d.x);
            box[2] = Math.min(box[2], d.z);
            box[3] = Math.max(box[3], d.z);
            box[4] = Math.max(box[4], d.speed());
            if (Math.abs(d.vx) > 0.05 && Math.signum(d.vx) != Math.signum(lastVx[0]) && Math.abs(lastVx[0]) > 0.05) {
                reversals[0]++;
            }
            if (Math.abs(d.vx) > 0.05) {
                lastVx[0] = d.vx;
            }
        });
        assertTrue(box[1] - box[0] < 0.3 && box[3] - box[2] < 0.3, "Pendeln: " + (box[1] - box[0]) + " × " + (box[3] - box[2]));
        assertTrue(box[4] < 0.2, "steht nicht: " + box[4]);
        assertTrue(reversals[0] <= 1, "Richtungswechsel " + reversals[0]);
        assertEquals(8.0, Math.hypot(drone.x - drone.targetX, drone.z - drone.targetZ), 0.5);
    }

    @Test
    void parallelStaysBesideTheTarget() {
        NavLogic logic = new NavLogic(FlightClass.CINE);
        PointMass drone = new PointMass(FlightClass.CINE).at(8, 69, 0, 270).wind(-2.0, 1.0, 1.0);
        drone.target(0, 64, 0, 0, 0, -4.0);
        drone.input();
        logic.follow(FollowController.Style.PARALLEL);
        drone.run(logic::update, 10.0);
        double[] worst = {0.0, 0.0};
        drone.run(logic::update, 15.0, d -> {
            worst[0] = Math.max(worst[0], Math.abs(d.x - d.targetX - 8.0));
            worst[1] = Math.max(worst[1], Math.abs(d.z - d.targetZ));
        });
        assertTrue(worst[0] <= 1.0 && worst[1] <= 1.0, "seitlich " + worst[0] + " / " + worst[1]);
    }

    @Test
    void watchStaysInPlaceAndTurnsTowardsTheTarget() {
        NavLogic logic = new NavLogic(FlightClass.COMPACT);
        PointMass drone = new PointMass(FlightClass.COMPACT).at(0, 70, 10, 0).wind(2.0, 0.0, 1.0);
        drone.target(0, 64, 0, 3.0, 0, 0);
        drone.input();
        logic.follow(FollowController.Style.WATCH);
        double[] worst = {0.0, 0.0};
        drone.run(logic::update, 3.0);
        drone.run(logic::update, 12.0, d -> {
            worst[0] = Math.max(worst[0], Math.hypot(d.x, d.z - 10.0));
            worst[1] = Math.max(worst[1], Math.abs(Compass.wrap180(Compass.headingTo(d.targetX - d.x, d.targetZ - d.z) - d.heading)));
            assertTrue(d.out.hasGimbalTarget());
        });
        assertTrue(worst[0] <= 0.5, "bewegt sich " + worst[0]);
        assertTrue(worst[1] <= 5.0, "Blick " + worst[1]);
    }

    @Test
    void lostTargetHoldsAndSticksChangeDistance() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = start(FlightClass.TOUR, logic, FollowController.Style.BEHIND);
        drone.run(logic::update, 5.0);
        drone.target = false;
        drone.run(logic::update, 8.0);
        double x = drone.x;
        double z = drone.z;
        drone.run(logic::update, 5.0);
        assertTrue(Math.hypot(drone.x - x, drone.z - z) < 0.3, "hält nicht ohne Ziel");
        assertEquals("FOLLOW", drone.out.label());
        drone.target = true;
        FollowController follow = new FollowController(FlightClass.TOUR, FollowController.Style.BEHIND);
        logic.follow(follow);
        drone.sticks(0, 1.0, 0, 0.5);
        drone.run(logic::update, 1.0);
        assertTrue(follow.distance() < 6.0, "Abstand per Knüppel " + follow.distance());
        assertEquals(NavLogic.Function.FOLLOW, logic.function());
    }
}
