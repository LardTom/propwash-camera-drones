package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class FakeServerFlight {
    static final double DT = 0.05;
    static final double LAG = 0.25;
    static final double HOLD_GAIN = 1.0;
    static final double ALT_GAIN = 1.0;
    static final double LAND_SPEED = 1.5;
    static final double HALF_WIDTH = 0.3;
    static final double FIT_MARGIN = 0.05;
    static final double SPOT_MARGIN = 0.4;
    static final double FLAT_STEP = 0.25;
    static final double SPOT_RADIUS = 8.0;
    static final int SPOT_DIRECTIONS = 16;
    static final double LAND_DRIFT_SPEED = 1.5;
    static final double LAND_OVER_SPOT = 0.3;

    private FakeServerFlight() {
    }

    static final class State implements FlightState {
        double time;
        double x;
        double y = 74.0;
        double z;
        double vx;
        double vy;
        double vz;
        double heading;
        double ground = 64.0;
        boolean grounded;
        double battery = 0.9;
        final double[] obstacles = new double[SensorDirection.values().length];

        State() {
            Arrays.fill(this.obstacles, Double.POSITIVE_INFINITY);
        }

        State at(double px, double py, double pz) {
            this.x = px;
            this.y = py;
            this.z = pz;
            return this;
        }

        @Override
        public double time() {
            return this.time;
        }

        @Override
        public double massKg() {
            return 0.72;
        }

        @Override
        public double gravity() {
            return 9.81;
        }

        @Override
        public double x() {
            return this.x;
        }

        @Override
        public double y() {
            return this.y;
        }

        @Override
        public double z() {
            return this.z;
        }

        @Override
        public double vx() {
            return this.vx;
        }

        @Override
        public double vy() {
            return this.vy;
        }

        @Override
        public double vz() {
            return this.vz;
        }

        @Override
        public double estimatedX() {
            return Double.NaN;
        }

        @Override
        public double estimatedY() {
            return Double.NaN;
        }

        @Override
        public double estimatedZ() {
            return Double.NaN;
        }

        @Override
        public double estimatedVx() {
            return Double.NaN;
        }

        @Override
        public double estimatedVy() {
            return Double.NaN;
        }

        @Override
        public double estimatedVz() {
            return Double.NaN;
        }

        @Override
        public boolean positionValid() {
            return true;
        }

        @Override
        public boolean gpsFix() {
            return true;
        }

        @Override
        public int satellites() {
            return 14;
        }

        @Override
        public double headingDeg() {
            return this.heading;
        }

        @Override
        public double pitchDeg() {
            return 0.0;
        }

        @Override
        public double rollDeg() {
            return 0.0;
        }

        @Override
        public double rollRateDps() {
            return 0.0;
        }

        @Override
        public double pitchRateDps() {
            return 0.0;
        }

        @Override
        public double yawRateDps() {
            return 0.0;
        }

        @Override
        public double forwardX() {
            return -Math.sin(Math.toRadians(this.heading));
        }

        @Override
        public double forwardY() {
            return 0.0;
        }

        @Override
        public double forwardZ() {
            return Math.cos(Math.toRadians(this.heading));
        }

        @Override
        public double upX() {
            return 0.0;
        }

        @Override
        public double upY() {
            return 1.0;
        }

        @Override
        public double upZ() {
            return 0.0;
        }

        @Override
        public double rightX() {
            return -Math.cos(Math.toRadians(this.heading));
        }

        @Override
        public double rightY() {
            return 0.0;
        }

        @Override
        public double rightZ() {
            return -Math.sin(Math.toRadians(this.heading));
        }

        @Override
        public double heightAboveGround() {
            return this.y - this.ground;
        }

        @Override
        public boolean armed() {
            return true;
        }

        @Override
        public boolean grounded() {
            return this.grounded;
        }

        @Override
        public boolean failsafe() {
            return false;
        }

        @Override
        public double hoverThrottle() {
            return 0.5;
        }

        @Override
        public double batteryCharge() {
            return this.battery;
        }

        @Override
        public double stickRoll() {
            return 0.0;
        }

        @Override
        public double stickPitch() {
            return 0.0;
        }

        @Override
        public double stickYaw() {
            return 0.0;
        }

        @Override
        public double stickThrottle() {
            return 0.5;
        }

        @Override
        public StabilizationMode pilotMode() {
            return StabilizationMode.ANGLE;
        }

        @Override
        public boolean action(int number) {
            return false;
        }

        @Override
        public double obstacleDistance(SensorDirection direction) {
            return this.obstacles[direction.ordinal()];
        }
    }

    static final class Output implements AssistOutput {
        double holdX = Double.NaN;
        double holdZ = Double.NaN;
        double velocityX = Double.NaN;
        double velocityZ = Double.NaN;
        double altitude = Double.NaN;
        double climb = Double.NaN;
        double maxSpeed = Double.NaN;
        double heading = Double.NaN;
        String label = "";
        boolean land;
        boolean returnHome;
        boolean sticks;

        void clear() {
            this.holdX = Double.NaN;
            this.holdZ = Double.NaN;
            this.velocityX = Double.NaN;
            this.velocityZ = Double.NaN;
            this.altitude = Double.NaN;
            this.climb = Double.NaN;
            this.maxSpeed = Double.NaN;
            this.heading = Double.NaN;
            this.label = "";
            this.land = false;
            this.returnHome = false;
            this.sticks = false;
        }

        @Override
        public void sticks(double roll, double pitch, double yaw, double throttle) {
            this.sticks = true;
        }

        @Override
        public void mode(StabilizationMode mode) {
        }

        @Override
        public void holdAltitude(double y) {
            this.altitude = y;
        }

        @Override
        public void climb(double metresPerSecond) {
            this.climb = metresPerSecond;
        }

        @Override
        public void holdPosition(double x, double z) {
            this.holdX = x;
            this.holdZ = z;
        }

        @Override
        public void velocity(double vx, double vz) {
            this.velocityX = vx;
            this.velocityZ = vz;
        }

        @Override
        public void maxSpeed(double metresPerSecond) {
            this.maxSpeed = metresPerSecond;
        }

        @Override
        public void yawRate(double degreesPerSecond) {
        }

        @Override
        public void heading(double compassDegrees) {
            this.heading = compassDegrees;
        }

        @Override
        public void label(String text) {
            this.label = text;
        }

        @Override
        public void requestReturnHome() {
            this.returnHome = true;
        }

        @Override
        public void land() {
            this.land = true;
        }
    }

    static final class Terrain {
        private final Map<Long, Double> tops = new HashMap<>();
        private final Set<Long> uneven = new HashSet<>();

        Terrain block(int x, int z, double top) {
            this.tops.put(key(x, z), top);
            return this;
        }

        Terrain uneven(int x, int z, double top) {
            this.block(x, z, top);
            this.uneven.add(key(x, z));
            return this;
        }

        double top(int x, int z, double ground) {
            return this.tops.getOrDefault(key(x, z), ground);
        }

        boolean level(int x, int z) {
            return !this.uneven.contains(key(x, z));
        }

        boolean fits(double x, double z, double ground) {
            double half = HALF_WIDTH + FIT_MARGIN;
            double low = Double.POSITIVE_INFINITY;
            double high = Double.NEGATIVE_INFINITY;
            for (int cx = first(x - half); cx <= last(x + half); cx++) {
                for (int cz = first(z - half); cz <= last(z + half); cz++) {
                    if (!this.level(cx, cz)) {
                        return false;
                    }
                    double top = this.top(cx, cz, ground);
                    low = Math.min(low, top);
                    high = Math.max(high, top);
                }
            }
            return high - low <= FLAT_STEP;
        }

        boolean even(double x, double z, double ground) {
            double half = HALF_WIDTH + SPOT_MARGIN;
            double reference = Double.NaN;
            for (int cx = first(x - half); cx <= last(x + half); cx++) {
                for (int cz = first(z - half); cz <= last(z + half); cz++) {
                    if (!this.level(cx, cz)) {
                        return false;
                    }
                    double top = this.top(cx, cz, ground);
                    if (Double.isNaN(reference)) {
                        reference = top;
                    } else if (Math.abs(top - reference) > 1.0E-9) {
                        return false;
                    }
                }
            }
            return true;
        }

        double groundAt(double x, double z, double half, double ground) {
            double high = Double.NEGATIVE_INFINITY;
            for (int cx = first(x - half); cx <= last(x + half); cx++) {
                for (int cz = first(z - half); cz <= last(z + half); cz++) {
                    high = Math.max(high, this.top(cx, cz, ground));
                }
            }
            return high;
        }

        static int first(double v) {
            return (int) Math.floor(v + 1.0E-7);
        }

        static int last(double v) {
            return (int) Math.floor(v - 1.0E-7);
        }

        private static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }
    }

    static final class Sim {
        final State state = new State();
        final Output out = new Output();
        final Terrain terrain = new Terrain();
        int landCalls;
        boolean propwashLands;
        double landX = Double.NaN;
        double landZ = Double.NaN;

        void step(AutonomyAssist assist) {
            if (!this.propwashLands) {
                this.out.clear();
                assist.update(this.state, this.out);
                if (this.out.land) {
                    this.landCalls++;
                    this.propwashLands = true;
                    this.chooseLandingSpot();
                }
            }
            this.integrate();
            this.state.time += DT;
        }

        private void chooseLandingSpot() {
            State s = this.state;
            this.landX = s.x;
            this.landZ = s.z;
            if (this.terrain.even(s.x, s.z, s.ground) || this.terrain.fits(s.x, s.z, s.ground)) {
                return;
            }
            double base = Math.toRadians(s.heading);
            double step = 2.0 * Math.PI / SPOT_DIRECTIONS;
            for (double r = 1.0; r <= SPOT_RADIUS; r += 1.0) {
                for (int k = 0; k < SPOT_DIRECTIONS; k++) {
                    int n = (k + 1) / 2;
                    double angle = base + (k % 2 == 1 ? n : -n) * step;
                    double px = s.x + r * Math.sin(angle);
                    double pz = s.z + r * Math.cos(angle);
                    if (this.terrain.even(px, pz, s.ground)) {
                        this.landX = px;
                        this.landZ = pz;
                        return;
                    }
                }
            }
        }

        void run(AutonomyAssist assist, double seconds) {
            int steps = (int) Math.round(seconds / DT);
            for (int i = 0; i < steps; i++) {
                this.step(assist);
            }
        }

        boolean runUntil(AutonomyAssist assist, double seconds, java.util.function.BooleanSupplier done) {
            int steps = (int) Math.round(seconds / DT);
            for (int i = 0; i < steps; i++) {
                this.step(assist);
                if (done.getAsBoolean()) {
                    return true;
                }
            }
            return false;
        }

        private void integrate() {
            State s = this.state;
            double tx;
            double tz;
            if (this.propwashLands) {
                tx = (this.landX - s.x) * HOLD_GAIN;
                tz = (this.landZ - s.z) * HOLD_GAIN;
            } else if (!Double.isNaN(this.out.velocityX)) {
                tx = this.out.velocityX;
                tz = this.out.velocityZ;
            } else if (!Double.isNaN(this.out.holdX)) {
                tx = (this.out.holdX - s.x) * HOLD_GAIN;
                tz = (this.out.holdZ - s.z) * HOLD_GAIN;
            } else {
                tx = 0.0;
                tz = 0.0;
            }
            double limit = this.propwashLands ? LAND_DRIFT_SPEED
                    : Double.isNaN(this.out.maxSpeed) ? AssistOutput.MAX_SPEED : this.out.maxSpeed;
            double speed = Math.hypot(tx, tz);
            if (speed > limit && speed > 0.0) {
                tx *= limit / speed;
                tz *= limit / speed;
            }
            double ty;
            if (this.propwashLands) {
                ty = Math.hypot(this.landX - s.x, this.landZ - s.z) <= LAND_OVER_SPOT ? -LAND_SPEED : 0.0;
            } else if (!Double.isNaN(this.out.climb)) {
                ty = this.out.climb;
            } else if (!Double.isNaN(this.out.altitude)) {
                ty = (this.out.altitude - s.y) * ALT_GAIN;
            } else {
                ty = 0.0;
            }
            ty = Math.clamp(ty, -AssistOutput.MAX_CLIMB, AssistOutput.MAX_CLIMB);
            double k = Math.min(1.0, DT / LAG);
            s.vx += (tx - s.vx) * k;
            s.vz += (tz - s.vz) * k;
            s.vy += (ty - s.vy) * k;
            s.x += s.vx * DT;
            s.y += s.vy * DT;
            s.z += s.vz * DT;
            double ground = this.terrain.groundAt(s.x, s.z, HALF_WIDTH, s.ground);
            if (s.y <= ground) {
                s.y = ground;
                s.vy = 0.0;
                s.grounded = true;
            } else {
                s.grounded = false;
            }
            if (!this.propwashLands && !Double.isNaN(this.out.heading)) {
                s.heading = this.out.heading;
            }
        }
    }

    static final class Recorder implements AutonomyAssist.Listener {
        final List<String> events = new ArrayList<>();
        final List<NavLogic.MissionState> states = new ArrayList<>();
        int started;
        int done;
        int lost;
        final List<AutoShots.Shot> autoShots = new ArrayList<>();

        @Override
        public void started(AutonomyAssist assist) {
            this.started++;
            this.events.add("started");
        }

        @Override
        public void progress(AutonomyAssist assist, NavLogic.MissionState state, int index) {
            this.states.add(state);
            this.events.add(state + " " + index);
        }

        @Override
        public void missionDone(AutonomyAssist assist) {
            this.done++;
            this.events.add("done");
        }

        @Override
        public void targetLost(AutonomyAssist assist) {
            this.lost++;
            this.events.add("lost");
        }

        @Override
        public void autoShotDone(AutonomyAssist assist, AutoShots.Shot shot) {
            this.autoShots.add(shot);
            this.events.add("auto " + shot);
        }
    }

    static final class Environment implements AutonomyAssist.Environment {
        static final double PAD_REACH = 1.2;

        final Map<UUID, NavTarget> targets = new HashMap<>();
        int maxAltitude = 120;
        int maxDistance;
        int followCalls;
        NavTarget pad;
        int padCalls;

        @Override
        public Optional<NavTarget> landingPad(double x, double y, double z) {
            this.padCalls++;
            return this.pad != null && Math.hypot(this.pad.x() - x, this.pad.z() - z) <= PAD_REACH && this.pad.y() <= y
                    ? Optional.of(this.pad) : Optional.empty();
        }

        @Override
        public int maxAltitude() {
            return this.maxAltitude;
        }

        @Override
        public int maxDistance() {
            return this.maxDistance;
        }

        @Override
        public Optional<NavTarget> followTarget(UUID target) {
            this.followCalls++;
            return Optional.ofNullable(this.targets.get(target));
        }
    }
}
