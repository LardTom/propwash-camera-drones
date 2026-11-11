package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.SportMode;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SportLoadTest {
    static final double DT = 0.01;
    static final double HOVER = 0.5;
    static final double ATTITUDE_LAG = 0.1;
    static final double MOTOR_LAG = 0.05;
    static final double DRAG = 0.15;
    static final double TERMINAL = 1.2;
    static final double MIN_SPIN = 0.05;
    static final double[] PITCH_SPEEDS = {6.0, 9.6};
    static final double[] THROTTLES = {0.5, 0.75, 1.0};
    static final double SECONDS = 16.0;
    static final double WARMUP = 2.0;
    static final double SWING = 0.5;
    static final int MAX_SWINGS = 2;
    static final double MAX_TILT_RATE = 12.0;
    static final int RATE_STEPS = 10;
    static final double HEIGHT_DROP = 1.5;
    static final double CLIMB_SHARE = 0.25;
    static final double EXHAUSTED = 0.98;
    static final double CLIMB_COST = 1.0;
    static final double WOBBLE = 0.06;
    static final double WOBBLE_HZ = 0.7;
    static final double APPROACH_SECONDS = 3.0;
    static final double APPROACH_OVER = 1.03;

    private record Flight(double[] tilt, double lowest, double climbed, double maxThrottle) {
        double end() {
            return this.tilt[this.tilt.length - 1];
        }
    }

    private static Flight fly(FlightClass c, double pitchSpeed, double throttleStick) {
        NavLogic logic = new NavLogic(c);
        logic.stickMode(NavLogic.StickMode.SPORT);
        double g = HorizontalControl.G;
        double terminal = TERMINAL * c.sportSpeed();
        double quadratic = (g * Math.tan(Math.toRadians(SportMode.MAX_ANGLE)) - DRAG * terminal) / (terminal * terminal);
        double y = 30.0;
        double vy = 0.0;
        double speed = 0.0;
        double tilt = 0.0;
        double thrust = 1.0;
        double lowest = y;
        double maxThrottle = 0.0;
        int steps = (int) Math.round(SECONDS / DT);
        double[] tilts = new double[steps];
        for (int i = 0; i < steps; i++) {
            NavInput in = new NavInput().time(i * DT).position(0, y, 0).velocity(0, vy, -speed).attitude(0, tilt, 0).heightAboveGround(y)
                    .pilot(true).sticks(0, 1.0, 0, throttleStick).hoverThrottle(HOVER);
            NavOutput out = new NavOutput();
            logic.update(in, out);
            double throttle = out.stickThrottle();
            maxThrottle = Math.max(maxThrottle, throttle);
            tilt += (out.stickPitch() * in.angleLimit() - tilt) * DT / ATTITUDE_LAG;
            double ratio = throttle / HOVER;
            thrust += (ratio * ratio - thrust) * DT / MOTOR_LAG;
            double radians = Math.toRadians(tilt);
            double inflow = speed * Math.sin(radians);
            double lift = g * thrust * Math.max(0.0, 1.0 - inflow / (pitchSpeed * Math.max(MIN_SPIN, Math.sqrt(Math.max(0.0, thrust)))));
            speed += (lift * Math.sin(radians) - DRAG * speed - quadratic * speed * speed) * DT;
            vy += (lift * Math.cos(radians) - g) * DT;
            y += vy * DT;
            lowest = Math.min(lowest, y);
            tilts[i] = tilt;
        }
        return new Flight(tilts, lowest - 30.0, y - 30.0, maxThrottle);
    }

    static int swings(double[] values, int from, double band) {
        int count = 0;
        int direction = 0;
        double low = values[from];
        double high = low;
        double extreme = low;
        for (int i = from + 1; i < values.length; i++) {
            double v = values[i];
            if (direction == 0) {
                low = Math.min(low, v);
                high = Math.max(high, v);
                if (v - low >= band) {
                    direction = 1;
                    extreme = v;
                } else if (high - v >= band) {
                    direction = -1;
                    extreme = v;
                }
            } else if (direction > 0) {
                if (v > extreme) {
                    extreme = v;
                } else if (extreme - v >= band) {
                    count++;
                    direction = -1;
                    extreme = v;
                }
            } else if (v < extreme) {
                extreme = v;
            } else if (v - extreme >= band) {
                count++;
                direction = 1;
                extreme = v;
            }
        }
        return count;
    }

    static double maxRate(double[] values, int from, int span) {
        double worst = 0.0;
        for (int i = Math.max(from, span); i < values.length; i++) {
            worst = Math.max(worst, Math.abs(values[i] - values[i - span]) / (span * DT));
        }
        return worst;
    }

    @Test
    void sportLeansSmoothlyWhereEvenFullThrottleRunsShort() {
        int from = (int) Math.round(WARMUP / DT);
        for (FlightClass c : FlightClass.values()) {
            for (double pitchSpeed : PITCH_SPEEDS) {
                for (double throttle : THROTTLES) {
                    Flight flight = fly(c, pitchSpeed, throttle);
                    String name = String.format(Locale.ROOT, "%s, Props %.1f m/s, Gas %.0f %%", c, pitchSpeed, throttle * 100.0);
                    int swings = swings(flight.tilt(), from, SWING);
                    double rate = maxRate(flight.tilt(), from, RATE_STEPS);
                    assertTrue(swings <= MAX_SWINGS, name + ": " + swings + " Wechsel der Neigung (Band " + SWING + "°)");
                    assertTrue(rate <= MAX_TILT_RATE, name + ": Neigung ändert sich um " + rate + "°/s");
                    if (throttle <= NavInput.THROTTLE_CENTER) {
                        assertTrue(flight.lowest() > -HEIGHT_DROP, name + ": sinkt " + -flight.lowest() + " m");
                    } else {
                        double wanted = (throttle - NavInput.THROTTLE_CENTER) * 2.0 * c.climbSpeed() * SECONDS;
                        assertTrue(flight.climbed() >= CLIMB_SHARE * wanted, name + ": steigt nur " + flight.climbed() + " von " + wanted + " m");
                    }
                }
            }
        }
    }

    @Test
    void climbingCostsForwardLeanOnlyWhereTheThrustRunsShort() {
        double pitchSpeed = PITCH_SPEEDS[0];
        Flight hold = fly(FlightClass.CINE, pitchSpeed, THROTTLES[0]);
        Flight climb = fly(FlightClass.CINE, pitchSpeed, THROTTLES[2]);
        assertTrue(hold.maxThrottle() >= EXHAUSTED && climb.maxThrottle() >= EXHAUSTED,
                "Modell: Kino fliegt nicht am Gas-Anschlag (" + hold.maxThrottle() + ", " + climb.maxThrottle() + ")");
        assertTrue(climb.end() < hold.end() - CLIMB_COST, "Kino steigt mit Vollgas und neigt " + climb.end() + "° statt weniger als "
                + (hold.end() - CLIMB_COST) + "°");
        Flight free = fly(FlightClass.COMPACT, PITCH_SPEEDS[1], THROTTLES[2]);
        Flight freeHold = fly(FlightClass.COMPACT, PITCH_SPEEDS[1], THROTTLES[0]);
        assertTrue(Math.abs(free.end() - freeHold.end()) < CLIMB_COST, "Kompakt mit Gas-Reserve: Steigen kostet keine Neigung ("
                + free.end() + "° gegen " + freeHold.end() + "°)");
    }

    @Test
    void aThrustWobbleWhileApproachingTheSportSpeedDoesNotRockTheLean() {
        int from = (int) Math.round(WARMUP / DT);
        for (FlightClass c : FlightClass.values()) {
            SportMode mode = new SportMode(c);
            double[] lean = new double[(int) Math.round(SECONDS / DT)];
            for (int i = 0; i < lean.length; i++) {
                double t = i * DT;
                double speed = APPROACH_OVER * c.sportSpeed() * (1.0 - Math.exp(-t / APPROACH_SECONDS)) + WOBBLE * Math.sin(2.0 * Math.PI * WOBBLE_HZ * t);
                NavOutput out = new NavOutput();
                NavInput in = new NavInput().time(t).position(0, 30, 0).velocity(0, 0, -speed).attitude(0, 30, 0).heightAboveGround(30)
                        .pilot(true).sticks(0, 1.0, 0, NavInput.THROTTLE_CENTER);
                mode.update(in, out);
                lean[i] = out.stickPitch() * in.angleLimit();
            }
            int swings = swings(lean, from, SWING);
            assertTrue(swings == 0, c + ": Tempo-Schwankung ±" + WOBBLE + " m/s mit " + WOBBLE_HZ + " Hz kippt die Neigung " + swings
                    + "-mal um mindestens " + SWING + "°");
        }
    }
}
