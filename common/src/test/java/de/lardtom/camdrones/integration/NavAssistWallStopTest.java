package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistWallStopTest {
    static final double DT = 0.01;
    static final double G = 9.81;
    static final double GROUND = 64.0;
    static final double WALL = 45.0;
    static final double WALL_TOP = 12.0;
    static final double ANGLE_LIMIT = 60.0;
    static final double ANGLE_GAIN = 10.7;
    static final double MAX_RATE = 500.0;
    static final double RATE_LAG = 0.06;
    static final double SENSOR_PERIOD = 0.05;
    static final double BODY = 0.3;
    static final double CINE_DRAG = 0.19;
    static final double TOUR_DRAG = 0.25;

    static final class Flight {
        final FakeFlight.State state = new FakeFlight.State();
        final NavAssist assist;
        final double drag;
        final double height;
        final double wall;
        double angleLimit = ANGLE_LIMIT;
        double travelled;
        double speed;
        double tilt;
        double rate;
        double sensed = Double.NEGATIVE_INFINITY;
        double closest = Double.POSITIVE_INFINITY;
        double peak;
        boolean crashed;

        Flight(FlightClass flightClass, NavLogic.StickMode mode, double height, double wall) {
            this.assist = new NavAssist(flightClass, EnumSet.allOf(SensorDirection.class), true, NavContext.NONE);
            this.assist.logic().stickMode(mode);
            this.state.flies(mode);
            this.drag = flightClass == FlightClass.CINE ? CINE_DRAG : TOUR_DRAG;
            this.height = height;
            this.wall = wall;
            this.state.heading = 0.0;
            this.state.stickPitch = 1.0;
            this.place();
        }

        private void place() {
            this.state.x = 0.0;
            this.state.y = GROUND + this.height;
            this.state.z = -this.travelled;
            this.state.vx = 0.0;
            this.state.vy = 0.0;
            this.state.vz = -this.speed;
            this.state.noseDown = this.tilt;
            this.state.heightAboveGround = this.height;
        }

        Flight angleLimit(double degrees) {
            this.angleLimit = degrees;
            this.state.angleLimit = degrees;
            return this;
        }

        double gap() {
            return this.wall - this.travelled;
        }

        private double forwardRay(double down) {
            double s = Math.sin(Math.toRadians(down));
            double c = Math.cos(Math.toRadians(down));
            double best = s > 1.0E-9 ? this.height / s : Double.POSITIVE_INFINITY;
            if (c > 1.0E-9) {
                double t = this.gap() / c;
                double y = this.height - t * s;
                if (t >= 0.0 && y >= 0.0 && y <= WALL_TOP) {
                    best = Math.min(best, t);
                }
            }
            return best > ObstacleGuard.SENSOR_RANGE ? Double.POSITIVE_INFINITY : best;
        }

        private double downRay() {
            double s = Math.sin(Math.toRadians(this.tilt));
            double c = Math.cos(Math.toRadians(this.tilt));
            double best = this.height / c;
            if (s < -1.0E-9) {
                double t = this.gap() / -s;
                if (this.height - t * c >= 0.0) {
                    best = Math.min(best, t);
                }
            }
            return best > ObstacleGuard.SENSOR_RANGE ? Double.POSITIVE_INFINITY : best;
        }

        private void sense() {
            if (this.state.time - this.sensed < SENSOR_PERIOD - 1.0E-9) {
                return;
            }
            this.sensed = this.state.time;
            this.state.obstacle(SensorDirection.FORWARD, this.forwardRay(this.tilt));
            double back = -this.tilt;
            double backDistance = back > 1.0E-9 ? this.height / Math.sin(Math.toRadians(back)) : Double.POSITIVE_INFINITY;
            this.state.obstacle(SensorDirection.BACK, backDistance > ObstacleGuard.SENSOR_RANGE ? Double.POSITIVE_INFINITY : backDistance);
            this.state.obstacle(SensorDirection.DOWN, this.downRay());
            this.state.obstacle(SensorDirection.UP, Double.POSITIVE_INFINITY);
            this.state.obstacle(SensorDirection.LEFT, Double.POSITIVE_INFINITY);
            this.state.obstacle(SensorDirection.RIGHT, Double.POSITIVE_INFINITY);
        }

        void step() {
            this.sense();
            FakeFlight.Output out = new FakeFlight.Output();
            this.assist.update(this.state, out);
            double target = out.sticks ? Math.clamp(out.pitch, -1.0, 1.0) * this.angleLimit : 0.0;
            double command = Math.clamp(ANGLE_GAIN * (target - this.tilt), -MAX_RATE, MAX_RATE);
            this.rate += (command - this.rate) * DT / RATE_LAG;
            this.tilt += this.rate * DT;
            double accel = G * Math.tan(Math.toRadians(this.tilt)) - this.drag * this.speed;
            this.speed += accel * DT;
            this.travelled += this.speed * DT;
            this.peak = Math.max(this.peak, this.speed);
            this.closest = Math.min(this.closest, this.gap());
            if (this.gap() <= BODY) {
                this.crashed = true;
            }
            this.state.step(DT);
            this.place();
        }

        Flight fly(double seconds) {
            int steps = (int) Math.round(seconds / DT);
            for (int i = 0; i < steps && !this.crashed; i++) {
                this.step();
            }
            return this;
        }
    }

    static Flight run(FlightClass flightClass, NavLogic.StickMode mode, double height) {
        return new Flight(flightClass, mode, height, WALL).fly(20.0);
    }

    private static String describe(FlightClass flightClass, NavLogic.StickMode mode, double height, Flight flight) {
        return String.format(Locale.ROOT, "%s %s in %.1f m: nächster Abstand %.2f m, Spitzentempo %.2f m/s, Tempo am Ende %.2f m/s%s",
                flightClass.key(), mode, height, flight.closest, flight.peak, flight.speed, flight.crashed ? ", Aufprall" : "");
    }

    @Test
    void theClassesStillReachTheirNormalSpeedWithoutAWall() {
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            Flight flight = new Flight(flightClass, NavLogic.StickMode.NORMAL, 6.0, 1000.0).fly(8.0);
            assertTrue(flight.peak >= 0.85 * flightClass.normalSpeed(), flightClass.key() + " in 6 m nur " + flight.peak + " m/s");
        }
    }

    @Test
    void fullStickTowardsAWallStopsAtLeastTheStopDistanceAwayAtAnyHeight() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (NavLogic.StickMode mode : List.of(NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE)) {
                for (double height : new double[] {1.5, 3.0, 4.5, 6.0, 10.0}) {
                    Flight flight = run(flightClass, mode, height);
                    String line = describe(flightClass, mode, height, flight);
                    all.add(line);
                    if (flight.crashed || flight.closest < ObstacleGuard.STOP_DISTANCE || Math.abs(flight.speed) > 0.3) {
                        problems.add(line);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "zu nah an der Wand: " + problems + " (alle: " + all + ")");
    }
}
