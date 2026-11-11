package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindReleaseTest {
    static final double STORM = 15.0;
    static final double GUST = 1.5;
    static final double PULL_BACK = 0.2;
    static final double GUSTY_PULL_BACK = 0.3;
    static final double HOVER_SECONDS = 15.0;
    static final double PUSH_SECONDS = 5.0;
    static final double AFTER_SECONDS = 8.0;
    static final double LEARN_SECONDS = 10.0;
    static final double WIND_TOLERANCE = 0.3;

    private static BiConsumer<NavInput, NavOutput> piloted(NavLogic logic, HorizontalControl control) {
        return (in, out) -> {
            logic.update(in, out);
            control.apply(in, out);
        };
    }

    private record Flight(NavLogic logic, HorizontalControl control, PointMass drone, BiConsumer<NavInput, NavOutput> chain) {
        static Flight hovering(FlightClass c, double wind, double gust) {
            NavLogic logic = new NavLogic(c);
            HorizontalControl control = new HorizontalControl();
            PointMass drone = new PointMass(c).at(0, 30, 0, 90).wind(wind, 0.0, gust);
            Flight flight = new Flight(logic, control, drone, piloted(logic, control));
            drone.sticks(0, 0, 0, 0.5);
            drone.run(flight.chain(), HOVER_SECONDS);
            return flight;
        }

        void sportDash() {
            this.logic.stickMode(NavLogic.StickMode.SPORT);
            this.drone.sticks(0, 1.0, 0, 0.5);
            this.drone.run(this.chain, 3.0);
            this.drone.sticks(0, 0, 0, 0.5);
            this.logic.stickMode(NavLogic.StickMode.NORMAL);
        }

        void atti(double seconds) {
            this.drone.gps = false;
            this.drone.run(this.chain, seconds);
            this.drone.gps = true;
        }

        double release(double roll, double pitch) {
            this.drone.sticks(roll, pitch, 0, 0.5);
            this.drone.run(this.chain, PUSH_SECONDS);
            double startX = this.drone.x;
            double startZ = this.drone.z;
            double speed = this.drone.speed();
            double axisX = this.drone.vx / Math.max(1.0E-9, speed);
            double axisZ = this.drone.vz / Math.max(1.0E-9, speed);
            this.drone.sticks(0, 0, 0, 0.5);
            double[] furthest = {0.0};
            double[] pulled = {0.0};
            this.drone.run(this.chain, AFTER_SECONDS, d -> {
                double along = (d.x - startX) * axisX + (d.z - startZ) * axisZ;
                furthest[0] = Math.max(furthest[0], along);
                pulled[0] = Math.max(pulled[0], furthest[0] - along);
            });
            return pulled[0];
        }
    }

    @Test
    void afterSportTheStormIsLearnedAgain() {
        for (FlightClass c : FlightClass.values()) {
            Flight flight = Flight.hovering(c, STORM, 0.0);
            flight.sportDash();
            flight.drone().run(flight.chain(), LEARN_SECONDS);
            String name = c + " nach S→N im Sturm " + STORM + " m/s";
            assertFalse(flight.control().settling(), name + ": Windanteil ruht noch");
            assertEquals(-PointMass.DRAG * STORM, flight.control().windX(), WIND_TOLERANCE, name + ": Windanteil");
            assertTrue(flight.drone().speed() < 0.3, name + ": treibt mit " + flight.drone().speed() + " m/s");
        }
    }

    @Test
    void afterSportReleasingAgainstTheStormDoesNotPullBack() {
        for (FlightClass c : FlightClass.values()) {
            for (double gust : new double[] {0.0, GUST}) {
                Flight flight = Flight.hovering(c, STORM, gust);
                flight.sportDash();
                flight.drone().run(flight.chain(), LEARN_SECONDS);
                double against = flight.release(0, -1.0);
                double across = flight.release(1.0, 0);
                String name = c + " nach S→N, Sturm " + STORM + " m/s, Böen " + gust;
                double limit = gust > 0.0 ? GUSTY_PULL_BACK : PULL_BACK;
                assertTrue(against <= limit, name + ": gegen den Wind " + against + " m zurück");
                assertTrue(across <= limit, name + ": quer zum Wind " + across + " m zurück");
            }
        }
    }

    @Test
    void releasingRightAfterSportAgainstTheStormDoesNotPullBack() {
        for (FlightClass c : FlightClass.values()) {
            Flight flight = Flight.hovering(c, STORM, 0.0);
            flight.logic().stickMode(NavLogic.StickMode.SPORT);
            flight.drone().sticks(0, -1.0, 0, 0.5);
            flight.drone().run(flight.chain(), 3.0);
            double startX = flight.drone().x;
            double axis = Math.signum(flight.drone().vx);
            flight.drone().sticks(0, 0, 0, 0.5);
            flight.logic().stickMode(NavLogic.StickMode.NORMAL);
            double[] furthest = {0.0};
            double[] pulled = {0.0};
            flight.drone().run(flight.chain(), AFTER_SECONDS, d -> {
                double along = (d.x - startX) * axis;
                furthest[0] = Math.max(furthest[0], along);
                pulled[0] = Math.max(pulled[0], furthest[0] - along);
            });
            assertTrue(pulled[0] <= PULL_BACK, c + " von S nach N gegen den Sturm: " + pulled[0] + " m zurück");
        }
    }

    @Test
    void inAGustyStormReleasingInNormalDoesNotPullBack() {
        for (FlightClass c : FlightClass.values()) {
            Flight flight = Flight.hovering(c, STORM, GUST);
            double with = flight.release(0, 1.0);
            double against = flight.release(0, -1.0);
            double left = flight.release(-1.0, 0);
            double againstAgain = flight.release(0, -1.0);
            String name = c + " N im Sturm mit Böen";
            assertTrue(with <= GUSTY_PULL_BACK, name + ": mit dem Wind " + with + " m zurück");
            assertTrue(against <= GUSTY_PULL_BACK, name + ": gegen den Wind " + against + " m zurück");
            assertTrue(left <= GUSTY_PULL_BACK, name + ": quer " + left + " m zurück");
            assertTrue(againstAgain <= GUSTY_PULL_BACK, name + ": wieder gegen den Wind " + againstAgain + " m zurück");
        }
    }

    @Test
    void afterAttiTheStormStaysCompensated() {
        for (FlightClass c : FlightClass.values()) {
            Flight flight = Flight.hovering(c, STORM, 0.0);
            flight.atti(4.0);
            flight.drone().run(flight.chain(), LEARN_SECONDS);
            String name = c + " nach ATTI→N im Sturm";
            assertFalse(flight.control().settling(), name + ": Windanteil ruht noch");
            assertEquals(-PointMass.DRAG * STORM, flight.control().windX(), WIND_TOLERANCE, name + ": Windanteil");
            double against = flight.release(0, -1.0);
            assertTrue(against <= PULL_BACK, name + ": gegen den Wind " + against + " m zurück");
        }
    }
}
