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

class NavAssistWallTopTest {
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
    static final double ALT_POSITION_GAIN = 0.9;
    static final double ALT_VELOCITY_GAIN = 2.2;
    static final double ALT_INTEGRAL_GAIN = 0.9;
    static final double ALT_INTEGRAL_LIMIT = 4.0;
    static final double HOLD_CLIMB = 1.5;
    static final double MOTOR_LAG = 0.05;
    static final double ZERO_THRUST_SPEED = 15.0;
    static final double DESCENT_GAIN = 0.5;
    static final double DESCENT_CAP = 0.6;
    static final double VERTICAL_DRAG = 0.3;
    static final double MAX_RISE = 0.35;

    static final class Flight implements NavContext {
        final FakeFlight.State state = new FakeFlight.State();
        final NavAssist assist;
        final double drag;
        final double start;
        final boolean probes;
        double angleLimit = ANGLE_LIMIT;
        double height;
        double travelled;
        double speed;
        double climb;
        double tilt;
        double rate;
        double thrust = 1.0;
        double targetAlt;
        double altI;
        double sensed = Double.NEGATIVE_INFINITY;
        double closest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        double peak;
        boolean crashed;
        boolean over;

        Flight(FlightClass flightClass, NavLogic.StickMode mode, double height, boolean probes) {
            this.assist = new NavAssist(flightClass, EnumSet.allOf(SensorDirection.class), true, this);
            this.assist.logic().stickMode(mode);
            this.state.flies(mode);
            this.drag = flightClass == FlightClass.CINE ? CINE_DRAG : TOUR_DRAG;
            this.height = height;
            this.start = height;
            this.probes = probes;
            this.targetAlt = GROUND + height;
            this.state.heading = 0.0;
            this.place();
        }

        public double probe(double x, double y, double z, double dirX, double dirZ, double range) {
            if (!this.probes) {
                return Double.NaN;
            }
            double above = y - GROUND;
            if (above < 0.0) {
                return 0.0;
            }
            double length = Math.hypot(dirX, dirZ);
            if (!(length > 1.0E-9) || !(-dirZ / length > 1.0E-9)) {
                return Double.POSITIVE_INFINITY;
            }
            double gap = this.wall(z);
            double t = gap / (-dirZ / length);
            if (t < 0.0 || above > WALL_TOP || t > range) {
                return Double.POSITIVE_INFINITY;
            }
            return t;
        }

        Flight angleLimit(double degrees) {
            this.angleLimit = degrees;
            this.state.angleLimit = degrees;
            return this;
        }

        private double wall(double z) {
            return WALL + z;
        }

        private void place() {
            this.state.x = 0.0;
            this.state.y = GROUND + this.height;
            this.state.z = -this.travelled;
            this.state.vx = 0.0;
            this.state.vy = this.climb;
            this.state.vz = -this.speed;
            this.state.estimatedY = this.state.y;
            this.state.estimatedVy = this.climb;
            this.state.noseDown = this.tilt;
            this.state.heightAboveGround = this.height;
        }

        double gap() {
            return WALL - this.travelled;
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

        private void altitudeHold(FakeFlight.Output out) {
            double y = GROUND + this.height;
            double feedForward = 0.0;
            double maxClimb = HOLD_CLIMB;
            if (!Double.isNaN(out.climb)) {
                feedForward = out.climb;
                maxClimb = Math.max(HOLD_CLIMB, Math.abs(out.climb));
                this.targetAlt = Math.clamp(this.targetAlt + out.climb * DT, y - 1.0, y + 1.0);
            } else if (!Double.isNaN(out.altitude)) {
                this.targetAlt = out.altitude;
            }
            double velCmd = Math.clamp(feedForward + ALT_POSITION_GAIN * (this.targetAlt - y), -maxClimb, maxClimb);
            double ev = velCmd - this.climb;
            this.altI = Math.clamp(this.altI + ALT_INTEGRAL_GAIN * ev * DT, -ALT_INTEGRAL_LIMIT, ALT_INTEGRAL_LIMIT);
            double acc = Math.clamp(ALT_VELOCITY_GAIN * ev + this.altI, -0.5 * G, 0.8 * G);
            double cos = Math.max(0.5, Math.cos(Math.toRadians(this.tilt)));
            double factor = Math.max(0.05, (1.0 + acc / G) / cos);
            this.thrust += (factor - this.thrust) * DT / MOTOR_LAG;
        }

        private double inflow() {
            double t = Math.toRadians(this.tilt);
            double axial = this.climb * Math.cos(t) + this.speed * Math.sin(t);
            double lambda = axial / ZERO_THRUST_SPEED;
            return lambda >= 0.0 ? Math.max(0.0, 1.0 - lambda) : 1.0 + DESCENT_GAIN * Math.min(-lambda, DESCENT_CAP);
        }

        void step(double stickPitch) {
            this.state.stickPitch = stickPitch;
            this.sense();
            FakeFlight.Output out = new FakeFlight.Output();
            this.assist.update(this.state, out);
            double target = out.sticks ? Math.clamp(out.pitch, -1.0, 1.0) * this.angleLimit : 0.0;
            double command = Math.clamp(ANGLE_GAIN * (target - this.tilt), -MAX_RATE, MAX_RATE);
            this.rate += (command - this.rate) * DT / RATE_LAG;
            this.tilt += this.rate * DT;
            this.altitudeHold(out);
            double accel = G * Math.tan(Math.toRadians(this.tilt)) - this.drag * this.speed;
            double lift = G * this.thrust * this.inflow() * Math.cos(Math.toRadians(this.tilt)) - G - VERTICAL_DRAG * this.climb;
            this.speed += accel * DT;
            this.climb += lift * DT;
            this.travelled += this.speed * DT;
            this.height += this.climb * DT;
            this.peak = Math.max(this.peak, this.speed);
            this.highest = Math.max(this.highest, this.height);
            this.closest = Math.min(this.closest, this.gap());
            if (this.gap() <= BODY && this.height - BODY < WALL_TOP) {
                this.crashed = true;
            }
            if (this.gap() < -BODY) {
                this.over = true;
            }
            this.state.step(DT);
            this.place();
        }

        Flight fly(double seconds) {
            int steps = (int) Math.round(seconds / DT);
            for (int i = 0; i < steps && !this.crashed && !this.over; i++) {
                this.step(1.0);
            }
            return this;
        }

        double rise() {
            return this.highest - this.start;
        }
    }

    private static String describe(FlightClass flightClass, NavLogic.StickMode mode, double below, Flight flight) {
        return String.format(Locale.ROOT, "%s %s%s, Oberkante %.1f m über der Flughöhe: nächster Abstand %.2f m, Spitzentempo %.2f m/s, "
                        + "Tempo am Ende %.2f m/s, gestiegen %.2f m, Höhe am Ende %.2f m%s%s",
                flightClass.key(), mode, flight.probes ? "" : " ohne Strahlen der Welt", below, flight.closest, flight.peak, flight.speed,
                flight.rise(), flight.height, flight.crashed ? ", Aufprall" : "", flight.over ? ", über die Wand" : "");
    }

    @Test
    void fullStickTowardsAWallWhoseTopIsJustAboveTheFlightHeightStopsInFrontOfIt() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (NavLogic.StickMode mode : List.of(NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE)) {
                for (double below : new double[] {0.3, 0.6, 1.0, 1.5}) {
                    for (boolean probes : new boolean[] {true, false}) {
                        Flight flight = new Flight(flightClass, mode, WALL_TOP - below, probes).fly(20.0);
                        String line = describe(flightClass, mode, below, flight);
                        all.add(line);
                        if (flight.crashed || flight.over || flight.closest < ObstacleGuard.STOP_DISTANCE || Math.abs(flight.speed) > 0.3) {
                            problems.add(line);
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "Wand mit Oberkante knapp über der Flughöhe: " + problems + " (alle: " + all + ")");
    }

    @Test
    void brakingInFrontOfAWallHoldsTheHeight() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (double height : new double[] {3.0, 6.0, 10.0}) {
                Flight flight = new Flight(flightClass, NavLogic.StickMode.NORMAL, height, true).fly(20.0);
                String line = describe(flightClass, NavLogic.StickMode.NORMAL, WALL_TOP - height, flight);
                all.add(line);
                if (flight.rise() > MAX_RISE) {
                    problems.add(line);
                }
            }
        }
        assertTrue(problems.isEmpty(), "steigt beim Bremsen mehr als " + MAX_RISE + " m: " + problems + " (alle: " + all + ")");
    }

    @Test
    void withoutTheWallTheHeightStillHoldsAndTheSpeedIsReached() {
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            Flight flight = new Flight(flightClass, NavLogic.StickMode.NORMAL, 6.0, true);
            flight.travelled = -1000.0;
            flight.fly(10.0);
            assertTrue(flight.peak >= 0.85 * flightClass.normalSpeed(), flightClass.key() + " nur " + flight.peak + " m/s");
            assertTrue(Math.abs(flight.height - flight.start) < 0.5, flightClass.key() + " Höhe " + flight.height + " statt " + flight.start);
        }
    }
}
