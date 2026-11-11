package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.SportMode;
import de.lardtom.camdrones.logic.nav.SportThrottle;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SportSpeedTest {
    static final double OVERSHOOT = 1.1;
    static final double HOLD_TOLERANCE = 0.05;
    static final double HOVER = 0.47;
    static final double ALTITUDE_HOLD_LIMIT = 0.8;
    static final double MOTOR_LAG = 0.05;
    static final double INFLOW_LOSS = 0.06;
    static final double TERMINAL = 1.2;
    static final double HEIGHT_DROP = 1.5;

    private static BiConsumer<NavInput, NavOutput> piloted(NavLogic logic, HorizontalControl control) {
        return (in, out) -> {
            logic.update(in, out);
            control.apply(in, out);
        };
    }

    @Test
    void sportStopsAtTheSportSpeedOfTheClass() {
        for (FlightClass c : FlightClass.values()) {
            NavLogic logic = new NavLogic(c);
            HorizontalControl control = new HorizontalControl();
            BiConsumer<NavInput, NavOutput> chain = piloted(logic, control);
            PointMass drone = new PointMass(c).at(0, 30, 0, 90);
            double terminal = TERMINAL * c.sportSpeed();
            drone.quadraticDrag = (HorizontalControl.G * Math.tan(Math.toRadians(SportMode.MAX_ANGLE)) - PointMass.DRAG * terminal) / (terminal * terminal);
            drone.sticks(0, 0, 0, 0.5);
            drone.run(chain, 2.0);
            logic.stickMode(NavLogic.StickMode.SPORT);
            drone.sticks(0, 1.0, 0, 0.5);
            double[] peak = {0.0};
            drone.run(chain, 20.0, d -> peak[0] = Math.max(peak[0], d.speed()));
            String name = c + " Sport mit vollem Knüppel";
            assertTrue(peak[0] <= OVERSHOOT * c.sportSpeed(), name + ": Spitze " + peak[0] + " m/s, Sport-Tempo " + c.sportSpeed());
            assertEquals(c.sportSpeed(), drone.speed(), HOLD_TOLERANCE * c.sportSpeed(), name + ": hält nicht das Sport-Tempo");
        }
    }

    private static NavOutput sport(SportMode mode, double time, double forwardSpeed, double rightSpeed, double roll, double pitch) {
        NavOutput out = new NavOutput();
        mode.update(new NavInput().time(time).position(0, 30, 0).velocity(rightSpeed, 0, -forwardSpeed).attitude(0, 0, 0).heightAboveGround(30)
                .pilot(true).sticks(roll, pitch, 0.0, 0.5), out);
        return out;
    }

    @Test
    void atTheSportSpeedOnlyTheLeanAlongTheFlightIsCapped() {
        double full = SportMode.MAX_ANGLE / NavInput.DEFAULT_ANGLE_LIMIT;
        for (FlightClass c : FlightClass.values()) {
            SportMode mode = new SportMode(c);
            double fast = 1.2 * c.sportSpeed();
            NavOutput out = null;
            for (int i = 0; i <= 100; i++) {
                out = sport(mode, i * 0.01, fast, 0.0, 0.0, 1.0);
            }
            assertTrue(out.stickPitch() < 0.8 * full, c + ": über dem Sport-Tempo neigt S weiter " + out.stickPitch() * NavInput.DEFAULT_ANGLE_LIMIT + "°");
            out = sport(mode, 1.01, fast, 0.0, 0.0, -1.0);
            assertEquals(-full, out.stickPitch(), 1.0E-9, c + ": Bremsen gegen die Flugrichtung bleibt voll");
            out = sport(mode, 1.02, fast, 0.0, 1.0, 0.0);
            assertEquals(full, out.stickRoll(), 1.0E-9, c + ": quer zur Flugrichtung bleibt voll");
            SportMode slow = new SportMode(c);
            out = sport(slow, 0.0, 0.5 * c.sportSpeed(), 0.0, 0.0, 1.0);
            assertEquals(full, out.stickPitch(), 1.0E-9, c + ": unter dem Sport-Tempo volle Neigung");
        }
    }

    @Test
    void withTheHoverThrottleSportFliesWithItsOwnThrottle() {
        for (FlightClass c : FlightClass.values()) {
            NavLogic logic = new NavLogic(c);
            logic.stickMode(NavLogic.StickMode.SPORT);
            NavOutput out = new NavOutput();
            NavInput in = new NavInput().time(0.0).position(0, 30, 0).attitude(0, 0, 0).heightAboveGround(30).pilot(true).sticks(0, 0, 0, 0.5);
            logic.update(in, out);
            assertTrue(out.vertical() != NavOutput.Vertical.NONE, c + ": ohne Schwebe-Gas bleibt Propwashs Höhenhaltung");
            for (int i = 1; i <= 100; i++) {
                out = new NavOutput();
                logic.update(new NavInput().time(i * 0.01).position(0, 30, 0).attitude(0, 0, 0).heightAboveGround(30).pilot(true)
                        .sticks(0, 0, 0, 0.5).hoverThrottle(HOVER), out);
            }
            assertTrue(out.hasSticks(), c + ": S gibt Knüppel aus");
            assertEquals(NavOutput.Vertical.NONE, out.vertical(), c + ": S mit eigenem Gas gibt keine Höhe an Propwash");
            assertEquals(HOVER, out.stickThrottle(), 0.02, c + ": im Schweben Gas = Schwebe-Gas");
            logic.stickMode(NavLogic.StickMode.NORMAL);
            out = new NavOutput();
            logic.update(new NavInput().time(1.01).position(0, 30, 0).attitude(0, 0, 0).heightAboveGround(30).pilot(true)
                    .sticks(0, 0, 0, 0.5).hoverThrottle(HOVER), out);
            assertTrue(out.vertical() != NavOutput.Vertical.NONE, c + ": N hält die Höhe wieder über Propwash");
        }
    }

    private record Climb(double lowest, double highest, double maxThrottle, double endVy) {
    }

    private static Climb fly(double seconds, double tilt, double throttleStick, double topSpeed) {
        return fly(seconds, tilt, throttleStick, topSpeed, 1.0);
    }

    private static Climb fly(double seconds, double tilt, double throttleStick, double topSpeed, double limit) {
        SportThrottle control = new SportThrottle();
        NavOutput out = new NavOutput();
        double g = HorizontalControl.G;
        double y = 30.0;
        double vy = 0.0;
        double thrust = 1.0;
        double lowest = y;
        double highest = y;
        double maxThrottle = 0.0;
        double dt = 0.01;
        double hold = y;
        for (int i = 0; i < Math.round(seconds / dt); i++) {
            double t = i * dt;
            double speed = topSpeed * Math.min(1.0, t / 5.0);
            out.reset();
            out.sticks(0.0, tilt / NavInput.DEFAULT_ANGLE_LIMIT, 0.0, throttleStick);
            if (throttleStick > 0.5) {
                out.climb(4.0);
            } else {
                out.holdAltitude(hold);
            }
            NavInput in = new NavInput().time(t).position(0, y, 0).velocity(0, vy, speed).attitude(0, tilt, 0).heightAboveGround(y).pilot(true)
                    .hoverThrottle(HOVER);
            control.apply(in, out);
            double throttle = Math.min(limit, out.stickThrottle());
            maxThrottle = Math.max(maxThrottle, throttle);
            double wanted = throttle / HOVER;
            thrust += (wanted * wanted - thrust) * dt / MOTOR_LAG;
            double inflow = speed * Math.sin(Math.toRadians(tilt));
            double lift = g * thrust * Math.max(0.0, 1.0 - INFLOW_LOSS * inflow) * Math.cos(Math.toRadians(tilt));
            vy += (lift - g) * dt;
            y += vy * dt;
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
        }
        return new Climb(lowest - 30.0, highest - 30.0, maxThrottle, vy);
    }

    @Test
    void theOwnThrottleHoldsTheHeightWhereTheThrustFadesWithSpeed() {
        double tilt = SportMode.MAX_ANGLE;
        double cap = HOVER * Math.sqrt((1.0 + ALTITUDE_HOLD_LIMIT) / Math.cos(Math.toRadians(tilt)));
        Climb capped = fly(15.0, tilt, 0.5, 20.0, cap);
        assertTrue(capped.lowest() < -2.0 * HEIGHT_DROP, "Modell: mit Propwashs Grenze von " + cap + " sinkt die Drohne nur " + capped.lowest() + " m");
        Climb fast = fly(15.0, tilt, 0.5, 20.0);
        assertTrue(fast.maxThrottle() > 1.05 * cap, "braucht mehr Gas als Propwashs Höhenhaltung gibt: " + fast.maxThrottle() + " gegen " + cap);
        assertTrue(fast.maxThrottle() <= 1.0, "Gas über 100 %");
        assertTrue(fast.lowest() > -HEIGHT_DROP, "sinkt " + fast.lowest() + " m");
        assertTrue(fast.highest() < 1.0, "steigt " + fast.highest() + " m");
        assertTrue(Math.abs(fast.endVy()) < 0.2, "kommt nicht zur Ruhe: " + fast.endVy() + " m/s");
        Climb level = fly(5.0, 0.0, 0.5, 0.0);
        assertTrue(Math.abs(level.lowest()) < 0.05 && level.highest() < 0.05, "schwebt nicht ruhig: " + level);
        Climb up = fly(3.0, 0.0, 1.0, 0.0);
        assertTrue(up.highest() > 8.0, "steigt mit Gas nur " + up.highest() + " m in 3 s");
    }

    @Test
    void whenEvenFullThrottleDoesNotSufficeSportStillLeansLess() {
        for (FlightClass c : FlightClass.values()) {
            SportThrottle throttle = new SportThrottle();
            SportMode sport = new SportMode(c, throttle);
            double full = SportMode.MAX_ANGLE / NavInput.DEFAULT_ANGLE_LIMIT;
            double pitch = full;
            double y = 30.0;
            for (int i = 0; i < 200; i++) {
                double t = i * 0.01;
                y -= 0.03;
                NavInput in = new NavInput().time(t).position(0, y, 0).velocity(0, -3.0, -10.0).attitude(0, 35, 0).heightAboveGround(y).pilot(true)
                        .sticks(0.0, 1.0, 0.0, 0.5).hoverThrottle(HOVER);
                NavOutput out = new NavOutput();
                sport.update(in, out);
                pitch = out.stickPitch();
                throttle.apply(in, out);
            }
            assertTrue(throttle.exhausted(), c + ": Gas nicht am Anschlag, " + throttle.throttle());
            assertTrue(pitch * NavInput.DEFAULT_ANGLE_LIMIT < SportMode.MAX_ANGLE - 5.0, c + ": neigt trotz vollem Gas weiter "
                    + pitch * NavInput.DEFAULT_ANGLE_LIMIT + "°");
        }
    }
}
