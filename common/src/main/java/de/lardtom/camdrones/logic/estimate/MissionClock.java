package de.lardtom.camdrones.logic.estimate;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.WaypointFollower;

import java.util.ArrayList;
import java.util.List;

public final class MissionClock {
    public static final double STEP = 0.025;
    public static final double REFRESH = 0.5;
    public static final double DEAD_BAND = 0.5;
    public static final double CATCH_UP = 8.0;
    public static final double MAX_RATE = 2.0;
    public static final double MAX_GAP = 2.0;
    public static final double MAX_BACK_STEP = 1.0;
    public static final double HORIZON = 3600.0;
    public static final double VELOCITY_LAG = 0.15;
    public static final double VELOCITY_GAIN = 1.6;
    public static final double VELOCITY_INTEGRAL = 0.8;
    public static final double INTEGRAL_LIMIT = 1.5;
    public static final double MAX_TILT = 35.0;
    public static final double POSITION_GAIN = 0.9;
    public static final double POSITION_BRAKE = 2.5;
    public static final double HOLD_SPEED = 4.0;
    public static final double CLIMB_GAIN = 2.5;
    public static final double CLIMB_INTEGRAL = 1.2;
    public static final double CLIMB_INTEGRAL_LIMIT = 3.0;
    public static final double CLIMB_INTEGRAL_BAND = 1.0;
    public static final double ALTITUDE_GAIN = 1.2;
    public static final double MAX_DESCENT = 3.0;
    public static final double MIN_LIFT = 0.3;
    public static final double MAX_LIFT = 0.9;
    public static final double DESCENT_RESPONSE = 0.95;
    public static final double TAKEOFF_TOLERANCE = 0.3;
    public static final double TAKEOFF_SETTLE = 0.5;
    public static final double TAKEOFF_RADIUS = 1.0;
    public static final double TAKEOFF_TIMEOUT = 20.0;
    public static final double TAKEOFF_DRIFT = 1.0;
    public static final double TAKEOFF_MIN = 1.0;
    public static final double TAKEOFF_MAX = 30.0;
    public static final double TAKEOFF_NEAR = 1.0;
    public static final double SECOND_PASS = 6.0;
    public static final double TAKEOFF_LAG_UP = 0.024;
    public static final double TAKEOFF_LAG_DOWN = 0.082;
    public static final double TAKEOFF_LOSS_EXPONENT = 0.485;
    public static final double SECOND_PASS_INSIDE = 1.25;
    public static final double SECOND_PASS_OUTSIDE = 1.05;
    static final double[][] SECOND_PASS_CHANCE = {{-0.045, 1.0}, {-0.035, 0.5}, {0.04, 0.5}, {0.065, 0.0}};
    public static final double TAKEOFF_ABOVE = 0.05;
    public static final double TAKEOFF_HELD = 0.3;
    public static final double TAKEOFF_LEFT = 0.5;
    public static final double TAKEOFF_GRACE = 1.0;
    public static final double G = 9.81;

    record Airframe(double thrustToWeight, double dragPerMass, double linearDrag, double quadraticDrag, double climbLoss, double descentLoss,
                    double verticalLag, double takeoffResponse) {
        static Airframe of(FlightClass flightClass) {
            return switch (flightClass) {
                case COMPACT -> new Airframe(2.928, 0.01999, 0.2, 0.0132, 0.7625, -0.20625, 0.15, 0.744);
                case TOUR -> new Airframe(3.5, 0.01274, 0.1925, 0.0066, 0.74375, -0.1125, 0.15, 0.949);
                case CINE -> new Airframe(3.465, 0.00879, 0.135, 0.0055, 0.4625, 0.0, 0.045, 0.868);
            };
        }

        double climbRate() {
            return Math.clamp(1.0 + 0.6 * this.thrustToWeight, 1.5, 4.0);
        }

        double maxLift() {
            return MAX_LIFT * this.thrustToWeight * G * Math.cos(Math.toRadians(MAX_TILT));
        }
    }

    private final Sim sim = new Sim();
    private Mission mission;
    private FlightClass flightClass;
    private NavLogic.MissionState state;
    private int index = -1;
    private double lastTime = Double.NaN;
    private double movedAt = Double.NaN;
    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double vz;
    private double startX;
    private double startZ;
    private boolean groundStart;
    private double fromX;
    private double fromY;
    private double fromZ;
    private double climbIntegral;
    private double[] risePath;
    private int riseSize;
    private double riseTotal = Double.NaN;
    private double riseEarly = Double.NaN;
    private double riseLate = Double.NaN;
    private double riseStart = Double.NaN;
    private boolean riseAbove;
    private double planEnd = Double.NaN;
    private double fixEnd = Double.NaN;
    private double arrivedAt = Double.NaN;
    private boolean finished;
    private double tail = Double.NaN;
    private double target = Double.NaN;
    private double targetAt = Double.NaN;
    private double shown = Double.NaN;
    private double shownAt = Double.NaN;

    public double update(FlightClass flightClass, Mission mission, NavLogic.MissionState state, int index, double time, double x, double y,
                         double z, boolean grounded) {
        if (flightClass == null || mission == null || mission.isEmpty() || mission.loop() || index < 0 || index >= mission.size()
                || state != NavLogic.MissionState.RUNNING && state != NavLogic.MissionState.PAUSED || !Double.isFinite(time)
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            this.reset();
            return Double.NaN;
        }
        if (this.mission != null && time < this.lastTime && this.lastTime - time <= MAX_BACK_STEP) {
            time = this.lastTime;
        }
        boolean plan = this.mission == null || flightClass != this.flightClass || index < this.index || index > this.index + 1
                || time < this.lastTime || mission != this.mission && !mission.equals(this.mission);
        if (plan) {
            this.begin(flightClass, mission, index, time, x, y, z, grounded);
        } else {
            this.track(time, x, y, z);
        }
        if (this.state == NavLogic.MissionState.PAUSED && state == NavLogic.MissionState.RUNNING) {
            this.arrivedAt = Double.NaN;
            this.targetAt = Double.NaN;
            this.risePath = null;
            this.planEnd = Double.NaN;
            this.fixEnd = Double.NaN;
            this.fromX = x;
            this.fromY = y;
            this.fromZ = z;
        }
        this.state = state;
        if (index != this.index) {
            if (this.takingOff()) {
                this.endTakeoff();
            }
            Waypoint previous = mission.waypoint(this.index);
            this.index = index;
            this.fromX = previous.x();
            this.fromY = previous.y();
            this.fromZ = previous.z();
            this.arrivedAt = Double.NaN;
            this.tail = Double.NaN;
            this.targetAt = Double.NaN;
            this.groundStart = false;
        }
        this.followTakeoff();
        Waypoint w = mission.waypoint(index);
        if (state == NavLogic.MissionState.RUNNING && !this.finished && Double.isNaN(this.arrivedAt) && this.arrived(w, x, y, z)) {
            if (index == mission.size() - 1) {
                this.finished = true;
            } else if (w.waitDs() > 0) {
                this.arrivedAt = time;
                this.targetAt = Double.NaN;
            }
        }
        if (this.finished) {
            this.target = 0.0;
            this.shown = 0.0;
            return 0.0;
        }
        if (Double.isNaN(this.targetAt) || time - this.targetAt >= REFRESH && !this.holding(time)) {
            this.target = this.estimate(time);
            this.targetAt = time;
            if (Double.isNaN(this.planEnd) && this.takingOff() && this.risePath != null && Double.isFinite(this.target)) {
                this.planEnd = time + this.target;
            }
        }
        if (Double.isNaN(this.target)) {
            this.shown = Double.NaN;
            return Double.NaN;
        }
        double now = state == NavLogic.MissionState.RUNNING ? Math.max(0.0, this.target - (time - this.targetAt)) : this.target;
        return this.show(time, state, now, plan);
    }

    public double seconds() {
        return this.shown;
    }

    public double target() {
        return this.target;
    }

    public boolean finished() {
        return this.finished;
    }

    public void reset() {
        this.mission = null;
        this.flightClass = null;
        this.state = null;
        this.index = -1;
        this.lastTime = Double.NaN;
        this.movedAt = Double.NaN;
        this.arrivedAt = Double.NaN;
        this.finished = false;
        this.tail = Double.NaN;
        this.target = Double.NaN;
        this.targetAt = Double.NaN;
        this.shown = Double.NaN;
        this.shownAt = Double.NaN;
        this.groundStart = false;
        this.climbIntegral = 0.0;
        this.risePath = null;
        this.riseSize = 0;
        this.riseTotal = Double.NaN;
        this.riseEarly = Double.NaN;
        this.riseLate = Double.NaN;
        this.riseStart = Double.NaN;
        this.riseAbove = false;
        this.planEnd = Double.NaN;
        this.fixEnd = Double.NaN;
    }

    private void begin(FlightClass flightClass, Mission mission, int index, double time, double x, double y, double z, boolean grounded) {
        this.reset();
        this.mission = mission;
        this.flightClass = flightClass;
        this.index = index;
        this.lastTime = time;
        this.movedAt = time;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = 0.0;
        this.vy = 0.0;
        this.vz = 0.0;
        this.startX = x;
        this.startZ = z;
        this.fromX = x;
        this.fromY = y;
        this.fromZ = z;
        this.groundStart = grounded;
        if (index > 0) {
            Waypoint previous = mission.waypoint(index - 1);
            this.fromX = previous.x();
            this.fromY = previous.y();
            this.fromZ = previous.z();
        } else if (grounded) {
            this.fromY = y + Math.clamp(mission.waypoint(0).y() - y, TAKEOFF_MIN, TAKEOFF_MAX);
            this.sim.start(flightClass, x, y, z, 0.0, 0.0, 0.0);
            this.sim.climbing(this.fromY, 0.0);
            this.sim.path = new double[(int) Math.ceil(TAKEOFF_TIMEOUT / STEP) + 1];
            this.riseTotal = this.sim.takeoff(this.fromY, x, z);
            this.riseEarly = this.sim.early;
            this.riseLate = this.sim.late;
            this.riseStart = time;
            this.risePath = this.sim.path;
            this.riseSize = this.sim.pathSize;
            this.sim.path = null;
        }
    }

    private void track(double time, double x, double y, double z) {
        double dt = time - this.lastTime;
        boolean stalled = x == this.x && y == this.y && z == this.z && Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz) > TAKEOFF_SETTLE;
        if (dt > MAX_GAP) {
            this.vx = 0.0;
            this.vy = 0.0;
            this.vz = 0.0;
            this.movedAt = time;
        } else if (dt > 0.0 && !stalled) {
            double span = Double.isNaN(this.movedAt) ? dt : Math.max(dt, time - this.movedAt);
            double a = Math.min(1.0, dt / VELOCITY_LAG);
            this.vx += ((x - this.x) / span - this.vx) * a;
            this.vy += ((y - this.y) / span - this.vy) * a;
            this.vz += ((z - this.z) / span - this.vz) * a;
            if (this.takingOff()) {
                this.observeClimb(span, y - this.y);
            }
            this.movedAt = time;
        }
        if (dt > 0.0) {
            this.lastTime = time;
            if (!stalled) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }
    }

    private void followTakeoff() {
        if (this.takingOff() && this.y - this.fromY >= TAKEOFF_TOLERANCE + TAKEOFF_ABOVE) {
            this.riseAbove = true;
        }
        double away = Math.hypot(this.x - this.startX, this.z - this.startZ);
        if (this.groundStart && (this.index != 0 || away > TAKEOFF_RADIUS || away > TAKEOFF_LEFT && Math.abs(this.y - this.fromY) < TAKEOFF_NEAR
                || Math.abs(this.y - this.fromY) < TAKEOFF_TOLERANCE && Math.abs(this.vy) < TAKEOFF_SETTLE)) {
            if (this.takingOff()) {
                this.endTakeoff();
            }
            this.groundStart = false;
            this.targetAt = Double.NaN;
        }
    }

    private void endTakeoff() {
        if (this.risePath == null || !Double.isFinite(this.planEnd) || !Double.isFinite(this.riseEarly) || !Double.isFinite(this.riseLate)) {
            return;
        }
        boolean late = this.riseAbove && Math.abs(this.y - this.fromY) < TAKEOFF_TOLERANCE
                && Math.hypot(this.x - this.startX, this.z - this.startZ) < TAKEOFF_HELD;
        this.fixEnd = this.planEnd + (late ? this.riseLate : this.riseEarly) - this.riseTotal;
    }

    private boolean holding(double time) {
        return this.takingOff() && this.risePath != null && Double.isFinite(this.planEnd) && Math.abs(this.y - this.fromY) < TAKEOFF_NEAR
                && Double.isNaN(this.riseRest()) && time - this.riseStart <= this.riseLate + TAKEOFF_GRACE;
    }

    private void observeClimb(double dt, double rise) {
        Airframe air = Airframe.of(this.flightClass);
        double command = climbCommand(this.fromY - this.y, air.climbRate(), air.climbRate());
        double error = command - rise / dt;
        double thrust = G + CLIMB_GAIN * error + this.climbIntegral;
        if (thrust >= MIN_LIFT * G && thrust <= air.maxLift() && Math.abs(error) < CLIMB_INTEGRAL_BAND) {
            this.climbIntegral = Math.clamp(this.climbIntegral + CLIMB_INTEGRAL * (command * dt - rise), -CLIMB_INTEGRAL_LIMIT, CLIMB_INTEGRAL_LIMIT);
        }
    }

    static double climbCommand(double error, double climbRate, double maxClimb) {
        double up = Math.max(0.3, Math.min(maxClimb, climbRate * 2.0));
        return Math.max(-Math.min(MAX_DESCENT, up), Math.min(up, ALTITUDE_GAIN * error));
    }

    private boolean arrived(Waypoint w, double x, double y, double z) {
        double distance = w.distanceTo(x, y, z);
        if (distance <= WaypointFollower.ARRIVE_RADIUS) {
            return true;
        }
        double sx = w.x() - this.fromX;
        double sz = w.z() - this.fromZ;
        return distance <= WaypointFollower.PASS_RADIUS && Math.hypot(sx, sz) >= WaypointFollower.MIN_HORIZONTAL
                && (x - w.x()) * sx + (z - w.z()) * sz > 0.0;
    }

    private boolean takingOff() {
        return this.groundStart && this.index == 0;
    }

    private double riseRest() {
        if (!this.takingOff() || this.risePath == null || this.riseSize < 2 || !(this.vy > TAKEOFF_SETTLE)
                || this.y - this.fromY >= TAKEOFF_TOLERANCE || this.y > this.risePath[this.riseSize - 1]) {
            return Double.NaN;
        }
        int i = 0;
        while (i < this.riseSize && this.risePath[i] < this.y) {
            i++;
        }
        double at = i == 0 ? 0.0 : (i - 1 + (this.y - this.risePath[i - 1]) / Math.max(1.0E-9, this.risePath[i] - this.risePath[i - 1])) * STEP;
        return Math.max(0.0, this.riseTotal - at);
    }

    private double estimate(double time) {
        Mission m = this.mission;
        Waypoint w = m.waypoint(this.index);
        boolean takeoff = this.takingOff();
        if (!Double.isNaN(this.arrivedAt)) {
            if (Double.isNaN(this.tail)) {
                this.tail = this.flight(this.index, true, takeoff, w.waitSeconds())[1];
            }
            return Math.max(0.0, w.waitSeconds() - (time - this.arrivedAt)) + this.tail;
        }
        if (this.index == m.size() - 1) {
            return this.flight(this.index, false, takeoff, 0.0)[0];
        }
        if (Double.isNaN(this.tail)) {
            double[] full = this.flight(this.index, true, takeoff, w.waitSeconds());
            this.tail = full[1];
            return full[0] + w.waitSeconds() + full[1];
        }
        return this.flight(this.index, false, takeoff, 0.0)[0] + w.waitSeconds() + this.tail;
    }

    private double[] flight(int from, boolean toEnd, boolean takeoff, double firstWait) {
        Mission m = this.mission;
        int to = toEnd ? m.size() : Math.min(m.size(), from + 2);
        List<Waypoint> rest = new ArrayList<>(m.waypoints().subList(from, to));
        rest.set(0, rest.getFirst().withWait(Math.max(firstWait, rest.getFirst().waitSeconds())));
        Mission plan = m.withWaypoints(rest).withEnd(Mission.End.HOVER).withLoop(false);
        return this.sim.run(this.flightClass, plan, firstWait, toEnd, takeoff, this.x, this.y, this.z, this.vx, this.vy, this.vz, this.fromX, this.fromY,
                this.fromZ, this.climbIntegral, takeoff ? this.riseRest() : Double.NaN);
    }

    private double show(double time, NavLogic.MissionState state, double now, boolean snap) {
        double dt = time - this.shownAt;
        if (snap || Double.isNaN(this.shown) || !(dt >= 0.0) || dt > MAX_GAP) {
            this.shown = now;
            this.fixEnd = Double.NaN;
        } else if (state == NavLogic.MissionState.RUNNING && Double.isFinite(this.fixEnd)) {
            double wanted = Math.max(0.0, this.fixEnd - time);
            double next = this.shown - dt;
            if (next > wanted) {
                next = Math.max(wanted, this.shown - MAX_RATE * dt);
            } else if (next < wanted) {
                next = Math.min(wanted, this.shown);
            }
            if (Math.abs(next - wanted) < 1.0E-9) {
                this.fixEnd = Double.NaN;
            }
            this.shown = Math.max(0.0, next);
        } else if (state == NavLogic.MissionState.RUNNING) {
            double error = this.shown - now;
            double excess = Math.abs(error) - DEAD_BAND;
            double rate = 1.0 + (excess > 0.0 ? Math.copySign(excess, error) / CATCH_UP : 0.0);
            this.shown = Math.max(0.0, this.shown - Math.clamp(rate, 0.0, MAX_RATE) * dt);
        }
        this.shownAt = time;
        return this.shown;
    }

    static final class Sim {
        private final NavInput in = new NavInput();
        private final NavOutput out = new NavOutput();
        private Airframe air;
        private double climbRate;
        private double maxTilt;
        private double x;
        private double y;
        private double z;
        private double vx;
        private double vy;
        private double vz;
        private double lift;
        private double ix;
        private double iz;
        private double iy;
        private double holdX;
        private double holdZ;
        private double holdY;
        private int takeoffSteps;
        private double early;
        private double late;
        private double[] path;
        private int pathSize;

        double[] run(FlightClass flightClass, Mission plan, double firstWait, boolean toEnd, boolean takeoff, double x, double y, double z, double vx,
                     double vy, double vz) {
            return this.run(flightClass, plan, firstWait, toEnd, takeoff, x, y, z, vx, vy, vz, Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN);
        }

        double[] run(FlightClass flightClass, Mission plan, double firstWait, boolean toEnd, boolean takeoff, double x, double y, double z, double vx,
                     double vy, double vz, double fromX, double fromY, double fromZ, double climbIntegral, double rest) {
            this.start(flightClass, x, y, z, vx, vy, vz);
            double t = 0.0;
            int steps = (int) Math.ceil(HORIZON / STEP);
            int step = 0;
            if (takeoff) {
                double top = Double.isFinite(fromY) ? fromY : plan.waypoint(0).y();
                double sx = Double.isFinite(fromX) ? fromX : x;
                double sz = Double.isFinite(fromZ) ? fromZ : z;
                this.climbing(top, climbIntegral);
                t = this.takeoff(top, sx, sz);
                if (Double.isFinite(rest)) {
                    t = rest;
                }
                step = this.takeoffSteps;
                this.holdX = this.x;
                this.holdZ = this.z;
                this.holdY = this.y;
            }
            WaypointFollower follower = new WaypointFollower(flightClass, plan, 0);
            int last = plan.size() - 1;
            double reach = Double.NaN;
            double start = t;
            if (!takeoff && Double.isFinite(fromX) && Double.isFinite(fromY) && Double.isFinite(fromZ)) {
                this.input(0.0, fromX, fromY, fromZ);
                this.out.reset();
                follower.update(this.in, this.out);
            }
            for (; step < steps; step++) {
                this.input(t - start, this.x, this.y, this.z);
                this.out.reset();
                follower.update(this.in, this.out);
                WaypointFollower.Phase phase = follower.phase();
                boolean done = phase == WaypointFollower.Phase.DONE || phase == WaypointFollower.Phase.RETURN || phase == WaypointFollower.Phase.LAND;
                if (Double.isNaN(reach) && (follower.index() > 0 || phase == WaypointFollower.Phase.WAIT || done)) {
                    reach = t;
                    if (!toEnd) {
                        return new double[]{reach, Double.NaN};
                    }
                }
                if (follower.index() == last && phase == WaypointFollower.Phase.WAIT || done) {
                    return new double[]{reach, Math.max(0.0, t - reach - firstWait)};
                }
                this.apply();
                t += STEP;
            }
            return new double[]{reach, Double.NaN};
        }

        void start(FlightClass flightClass, double x, double y, double z, double vx, double vy, double vz) {
            this.air = Airframe.of(flightClass);
            this.climbRate = this.air.climbRate();
            this.maxTilt = Math.toRadians(MAX_TILT);
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.lift = G;
            double speed = Math.hypot(vx, vz);
            double drag = this.air.linearDrag() + this.air.quadraticDrag() * speed - this.air.dragPerMass() * speed;
            this.ix = Math.clamp(drag * vx, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
            this.iz = Math.clamp(drag * vz, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
            this.iy = 0.0;
            this.holdX = x;
            this.holdZ = z;
            this.holdY = y;
        }

        void climbing(double top, double integral) {
            this.iy = integral;
            double ty = Math.clamp(G + CLIMB_GAIN * (this.climbToward(top - this.y, this.climbRate) - this.vy) + integral, MIN_LIFT * G, this.air.maxLift());
            this.lift = G + (ty - G) * (ty > G ? 1.0 : this.air.takeoffResponse());
        }

        double takeoff(double top, double sx, double sz) {
            double t = 0.0;
            int step = 0;
            boolean ahead = this.vy > TAKEOFF_SETTLE || top - this.y > TAKEOFF_NEAR;
            double first = Double.NaN;
            double margin = Double.NaN;
            boolean above = false;
            this.pathSize = 0;
            while (t < TAKEOFF_TIMEOUT) {
                double error = top - this.y;
                if (ahead && Double.isNaN(first) && Math.abs(this.vy) < TAKEOFF_SETTLE && Math.abs(error) < TAKEOFF_NEAR) {
                    first = t;
                    margin = TAKEOFF_TOLERANCE - Math.abs(error);
                    above = error < 0.0;
                }
                if (this.path != null && Double.isNaN(first) && this.pathSize < this.path.length) {
                    this.path[this.pathSize++] = this.y;
                }
                if (Math.abs(error) < TAKEOFF_TOLERANCE && Math.abs(this.vy) < TAKEOFF_SETTLE) {
                    break;
                }
                this.climb(top, sx, sz);
                t += STEP;
                step++;
            }
            this.takeoffSteps = step;
            this.early = t;
            this.late = t;
            if (Double.isNaN(first) || !above) {
                return t;
            }
            double chance = secondPassChance(margin);
            if (margin < 0.0) {
                this.early = first;
                this.late = first + SECOND_PASS_OUTSIDE * (t - first);
                return first + chance * SECOND_PASS_OUTSIDE * (t - first);
            }
            if (chance > 0.0) {
                double later = this.secondPass(top, sx, sz);
                if (Double.isFinite(later)) {
                    this.late = t + SECOND_PASS_INSIDE * later;
                    t += chance * SECOND_PASS_INSIDE * later;
                }
            }
            return t;
        }

        static double secondPassChance(double margin) {
            double[][] c = SECOND_PASS_CHANCE;
            if (margin <= c[0][0]) {
                return c[0][1];
            }
            for (int i = 1; i < c.length; i++) {
                if (margin <= c[i][0]) {
                    return c[i - 1][1] + (c[i][1] - c[i - 1][1]) * (margin - c[i - 1][0]) / (c[i][0] - c[i - 1][0]);
                }
            }
            return c[c.length - 1][1];
        }

        private void input(double time, double x, double y, double z) {
            this.in.time(time).position(x, y, z).velocity(this.vx, this.vy, this.vz).attitude(0.0, 0.0, 0.0).gps(true, 14).compass(true, 0.0)
                    .heightAboveGround(Double.POSITIVE_INFINITY).grounded(false).pilot(false);
        }

        private void climb(double top, double sx, double sz) {
            double[] h = speed(sx - this.x, sz - this.z, TAKEOFF_DRIFT);
            this.control(h[0], h[1], this.climbToward(top - this.y, this.climbRate), true);
        }

        private double secondPass(double top, double sx, double sz) {
            double[] saved = {this.x, this.y, this.z, this.vx, this.vy, this.vz, this.lift, this.ix, this.iz, this.iy};
            double result = Double.NaN;
            boolean away = false;
            for (double t = 0.0; t < SECOND_PASS; t += STEP) {
                double error = top - this.y;
                if (Math.abs(error) >= TAKEOFF_TOLERANCE) {
                    away = true;
                } else if (away && Math.abs(this.vy) < TAKEOFF_SETTLE) {
                    result = t;
                    break;
                }
                this.climb(top, sx, sz);
            }
            this.x = saved[0];
            this.y = saved[1];
            this.z = saved[2];
            this.vx = saved[3];
            this.vy = saved[4];
            this.vz = saved[5];
            this.lift = saved[6];
            this.ix = saved[7];
            this.iz = saved[8];
            this.iy = saved[9];
            return result;
        }

        private double climbToward(double error, double maxClimb) {
            return climbCommand(error, this.climbRate, maxClimb);
        }

        private static double[] speed(double ex, double ez, double maxSpeed) {
            double e = Math.hypot(ex, ez);
            if (e < 1.0E-6) {
                return new double[]{0.0, 0.0};
            }
            double v = Math.min(maxSpeed, Math.min(POSITION_GAIN * e, Math.sqrt(2.0 * POSITION_BRAKE * e)));
            return new double[]{ex / e * v, ez / e * v};
        }

        private void apply() {
            NavOutput o = this.out;
            double limit = Double.isNaN(o.maxSpeed()) ? HOLD_SPEED : o.maxSpeed();
            double climb = switch (o.vertical()) {
                case CLIMB -> {
                    this.holdY = this.y;
                    yield o.climbRate();
                }
                case ALTITUDE -> {
                    this.holdY = this.y;
                    yield this.climbToward(o.altitudeTarget() - this.y, Math.max(this.climbRate, limit));
                }
                default -> this.climbToward(this.holdY - this.y, this.climbRate);
            };
            double cx;
            double cz;
            switch (o.horizontal()) {
                case VELOCITY -> {
                    cx = o.velocityX();
                    cz = o.velocityZ();
                    double v = Math.hypot(cx, cz);
                    if (v > limit && v > 1.0E-9) {
                        cx *= limit / v;
                        cz *= limit / v;
                    }
                    this.holdX = this.x;
                    this.holdZ = this.z;
                }
                case POSITION -> {
                    double[] h = speed(o.targetX() - this.x, o.targetZ() - this.z, limit);
                    cx = h[0];
                    cz = h[1];
                    this.holdX = this.x;
                    this.holdZ = this.z;
                }
                default -> {
                    double[] h = speed(this.holdX - this.x, this.holdZ - this.z, HOLD_SPEED);
                    cx = h[0];
                    cz = h[1];
                }
            }
            this.control(cx, cz, climb, false);
        }

        private void control(double vdx, double vdz, double climb, boolean takeoff) {
            double evx = vdx - this.vx;
            double evz = vdz - this.vz;
            double desired = Math.hypot(vdx, vdz);
            double ax = VELOCITY_GAIN * evx + this.ix + this.air.dragPerMass() * desired * vdx;
            double az = VELOCITY_GAIN * evz + this.iz + this.air.dragPerMass() * desired * vdz;
            double maxH = G * Math.tan(this.maxTilt);
            double ah = Math.hypot(ax, az);
            if (ah > maxH) {
                ax *= maxH / ah;
                az *= maxH / ah;
            } else {
                this.ix = Math.clamp(this.ix + VELOCITY_INTEGRAL * evx * STEP, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
                this.iz = Math.clamp(this.iz + VELOCITY_INTEGRAL * evz * STEP, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
            }
            double evy = climb - this.vy;
            double ty = G + CLIMB_GAIN * evy + this.iy;
            double minTy = MIN_LIFT * G;
            double maxTy = this.air.maxLift();
            boolean saturated = ty < minTy || ty > maxTy;
            ty = Math.clamp(ty, minTy, maxTy);
            if (!saturated && Math.abs(evy) < CLIMB_INTEGRAL_BAND) {
                this.iy = Math.clamp(this.iy + CLIMB_INTEGRAL * evy * STEP, -CLIMB_INTEGRAL_LIMIT, CLIMB_INTEGRAL_LIMIT);
            }
            double th = Math.hypot(ax, az);
            double maxTh = ty * Math.tan(this.maxTilt);
            if (th > maxTh && th > 1.0E-9) {
                ax *= maxTh / th;
                az *= maxTh / th;
            }
            double wanted = G + (ty - G) * (ty > G ? 1.0 : takeoff ? this.air.takeoffResponse() : DESCENT_RESPONSE);
            double lag = !takeoff ? this.air.verticalLag() : wanted > this.lift ? TAKEOFF_LAG_UP : TAKEOFF_LAG_DOWN;
            this.lift += (wanted - this.lift) * Math.min(1.0, STEP / lag);
            double speed = Math.hypot(this.vx, this.vz);
            double drag = this.air.linearDrag() + this.air.quadraticDrag() * speed;
            double loss = this.vy > 0.0 ? this.air.climbLoss() : this.air.descentLoss();
            if (takeoff && this.vy > 0.0) {
                loss *= Math.pow(this.lift / (G + this.air.climbLoss() * this.climbRate), TAKEOFF_LOSS_EXPONENT);
            }
            this.vx += (ax - drag * this.vx) * STEP;
            this.vz += (az - drag * this.vz) * STEP;
            this.vy += (this.lift - G - loss * this.vy) * STEP;
            this.x += this.vx * STEP;
            this.y += this.vy * STEP;
            this.z += this.vz * STEP;
        }
    }
}
