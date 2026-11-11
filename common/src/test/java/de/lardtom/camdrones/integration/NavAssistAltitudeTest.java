package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistAltitudeTest {
    private static final double DT = 0.01;

    static final class PropwashHeight {
        static final double POSITION_GAIN = 0.9;
        static final double VELOCITY_GAIN = 2.2;
        static final double INTEGRAL_GAIN = 0.9;
        static final double INTEGRAL_LIMIT = 4.0;
        static final double HOLD_RATE = 1.5;
        static final double G = 9.81;

        final FakeFlight.State state;
        final DoubleUnaryOperator offset;
        final DoubleUnaryOperator bias;
        double target;
        double integral;

        PropwashHeight(FakeFlight.State state, DoubleUnaryOperator offset, DoubleUnaryOperator bias) {
            this.state = state;
            this.offset = offset;
            this.bias = bias;
            this.target = state.y;
            this.estimate();
        }

        void estimate() {
            this.state.estimatedY = this.state.y + this.offset.applyAsDouble(this.state.time);
            this.state.estimatedVy = this.state.vy + this.bias.applyAsDouble(this.state.time);
        }

        void step(FakeFlight.Output out) {
            double z = this.state.estimatedY;
            double velocity;
            if (!Double.isNaN(out.climb)) {
                double climb = out.climb;
                this.target = Math.clamp(this.target + climb * DT, z - 1.0, z + 1.0);
                double max = Math.max(HOLD_RATE, Math.abs(climb));
                velocity = Math.clamp(climb + POSITION_GAIN * (this.target - z), -max, max);
            } else if (!Double.isNaN(out.altitude)) {
                this.target = out.altitude;
                velocity = Math.clamp(POSITION_GAIN * (this.target - z), -HOLD_RATE, HOLD_RATE);
            } else {
                velocity = 0.0;
            }
            double error = velocity - this.state.estimatedVy;
            this.integral = Math.clamp(this.integral + INTEGRAL_GAIN * error * DT, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
            double acceleration = Math.clamp(VELOCITY_GAIN * error + this.integral, -0.5 * G, 0.8 * G);
            this.state.vy += acceleration * DT;
            this.state.y += this.state.vy * DT;
            this.state.step(DT);
            this.estimate();
        }
    }

    private static void fly(NavAssist assist, PropwashHeight height, double seconds, Runnable probe) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            FakeFlight.Output output = new FakeFlight.Output();
            assist.update(height.state, output);
            height.step(output);
            probe.run();
        }
    }

    private static NavAssist assist(FlightClass flightClass) {
        return new NavAssist(flightClass, EnumSet.allOf(SensorDirection.class), true, NavContext.NONE);
    }

    @Test
    void holdsTheTrueHeightWhileBarometerAndVelocityEstimateDrift() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0);
        PropwashHeight height = new PropwashHeight(state, t -> 0.3 * Math.sin(2.0 * Math.PI * t / 20.0), t -> 0.25 + 0.1 * Math.sin(2.0 * Math.PI * t / 13.0));
        NavAssist assist = assist(FlightClass.TOUR);
        fly(assist, height, 5.0, () -> {
        });
        double reference = state.y;
        double[] worst = {0.0};
        fly(assist, height, 60.0, () -> worst[0] = Math.max(worst[0], Math.abs(state.y - reference)));
        assertTrue(worst[0] <= 0.3, "Luftstativ weicht senkrecht " + worst[0] + " m ab");
    }

    @Test
    void sticksClimbAndSinkWithTheClassRateDespiteTheEstimateBias() {
        for (FlightClass c : FlightClass.values()) {
            FakeFlight.State state = new FakeFlight.State().at(0.0, 200.0, 0.0);
            PropwashHeight height = new PropwashHeight(state, t -> 0.2, t -> 0.6);
            NavAssist assist = assist(c);
            fly(assist, height, 3.0, () -> {
            });
            state.stickThrottle = 0.0;
            double[] sum = {0.0, 0.0};
            fly(assist, height, 5.0, () -> {
                if (state.time > 5.5) {
                    sum[0] += state.vy;
                    sum[1]++;
                }
            });
            double sink = -sum[0] / sum[1];
            assertEquals(c.descendSpeed(), sink, 0.1 * c.descendSpeed(), c + ": sinkt mit " + sink + " m/s");
            state.stickThrottle = 0.5;
            fly(assist, height, 6.0, () -> {
            });
            double settled = state.y;
            double[] worst = {0.0};
            fly(assist, height, 5.0, () -> worst[0] = Math.max(worst[0], Math.abs(state.y - settled)));
            assertTrue(worst[0] <= 0.3, c + ": Gas Mitte hält die Höhe nur auf " + worst[0] + " m");
        }
    }
}
