package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.NormalMode;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.logic.nav.OrbitController;
import de.lardtom.camdrones.logic.nav.VerticalControl;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.part.SensorTable;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.DroneProfile;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class NavAssist implements FlightAssist {
    public static final String ID = CamDrones.MOD_ID + ":nav";
    public static final double COMPACT_MAX_KG = 0.5;
    public static final double TOUR_MAX_KG = 2.0;
    public static final double LIMIT_REFRESH_SECONDS = 0.5;
    public static final double POI_RANGE = 64.0;
    public static final String POI_REFUSED_LABEL = "NO POI";
    public static final double POI_REFUSED_SECONDS = 2.0;
    public static final double CRASH_SPEED = 2.0;
    public static final double GROUND_RAY_MIN = 0.05;
    public static final double GROUND_SHARE = 0.9;
    public static final double MEMORY_SIDE = 1.5;
    public static final double MEMORY_TURN = 20.0;
    public static final double MEMORY_PASSED = 1.0;
    public static final double MEMORY_RISE = 0.5;
    public static final double LEVEL_BAND = 0.25;
    public static final double PROBE_BAND = 0.5;
    public static final double PROBE_CLEARANCE = 0.5;
    public static final double PROBE_MIN_OFFSET = 0.05;
    public static final double PROBE_PERIOD = 0.05;
    public static final double PROBE_TURN = 5.0;
    public static final String MODE_NAME_PREFIX = CamDrones.MOD_ID + ".mode.";

    private static final CamControl[] CONTROLS = CamControl.switches().toArray(CamControl[]::new);
    private static final NavInput.Sensor[] SENSORS = NavInput.Sensor.values();

    private final FlightClass flightClass;
    private final Set<SensorDirection> sensors;
    private final boolean piloted;
    private final NavContext context;
    private final NavInput in = new NavInput();
    private final NavOutput out = new NavOutput();
    private final ForwardSink sink = new ForwardSink();
    private final HorizontalControl control = new HorizontalControl();
    private final VerticalControl vertical = new VerticalControl();
    private final boolean[] previous = new boolean[CamControl.values().length];
    private final boolean[] seen = new boolean[SENSORS.length];
    private final double[] seenDistance = new double[SENSORS.length];
    private final double[] seenX = new double[SENSORS.length];
    private final double[] seenZ = new double[SENSORS.length];
    private final double[] seenHeading = new double[SENSORS.length];
    private final double[] seenY = new double[SENSORS.length];
    private final double[] probed = new double[SENSORS.length];
    private final double[] probedAt = new double[SENSORS.length];
    private final double[] probedX = new double[SENSORS.length];
    private final double[] probedZ = new double[SENSORS.length];
    private final double[] probedHeading = new double[SENSORS.length];
    private NavLogic logic;
    private boolean started;
    private boolean homeKnown;
    private double homeX;
    private double homeY;
    private double homeZ;
    private double limitsAt = Double.NaN;
    private int maxAltitude = -1;
    private int maxDistance = -1;
    private NavLogic.MissionState reportedState = NavLogic.MissionState.IDLE;
    private int reportedIndex;
    private NavStatus status;
    private boolean armedBefore;
    private double speedBefore;
    private double refusedAt = Double.NaN;
    private NavLogic.Function refusedDuring = NavLogic.Function.NONE;
    private StabilizationMode pilotMode;

    public NavAssist(FlightClass flightClass, Set<SensorDirection> sensors, boolean piloted, NavContext context) {
        this.flightClass = Objects.requireNonNull(flightClass, "flightClass");
        this.sensors = sensors == null || sensors.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(sensors));
        this.piloted = piloted;
        this.context = context == null ? NavContext.NONE : context;
        this.logic = new NavLogic(flightClass);
        Arrays.fill(this.probedAt, Double.NaN);
    }

    public static boolean equipped(DroneProfile profile) {
        return profile != null && board(profile).filter(SensorTable::isNavBoard).isPresent();
    }

    private static Optional<Identifier> board(DroneProfile profile) {
        return Optional.ofNullable(Identifier.tryParse(profile.stack()));
    }

    public static FlightClass flightClass(DroneProfile profile) {
        Identifier frame = Identifier.tryParse(profile.frame());
        Optional<FlightClass> byFrame = frame == null ? Optional.empty() : CamPresets.classOf(frame);
        if (byFrame.isPresent()) {
            return byFrame.get();
        }
        if (profile.massKg() < COMPACT_MAX_KG) {
            return FlightClass.COMPACT;
        }
        return profile.massKg() < TOUR_MAX_KG ? FlightClass.TOUR : FlightClass.CINE;
    }

    public static Set<SensorDirection> sensors(DroneProfile profile) {
        return board(profile).flatMap(SensorTable::sensors).orElse(Set.of());
    }

    public static NavAssist of(DroneProfile profile, boolean piloted, NavContext context) {
        return new NavAssist(flightClass(profile), sensors(profile), piloted, context);
    }

    public static NavAssist pilotFor(DroneProfile profile) {
        return equipped(profile) ? of(profile, true, NavContexts.pilot()) : null;
    }

    public FlightClass flightClass() {
        return this.flightClass;
    }

    public Set<SensorDirection> sensors() {
        return this.sensors;
    }

    public boolean piloted() {
        return this.piloted;
    }

    public NavContext context() {
        return this.context;
    }

    public NavLogic logic() {
        return this.logic;
    }

    public NavInput input() {
        return this.in;
    }

    public NavOutput output() {
        return this.out;
    }

    public HorizontalControl control() {
        return this.control;
    }

    public VerticalControl vertical() {
        return this.vertical;
    }

    public Optional<NavStatus> status() {
        return Optional.ofNullable(this.status);
    }

    public Optional<NavTarget> home() {
        return this.homeKnown ? Optional.of(NavTarget.at(this.homeX, this.homeY, this.homeZ)) : Optional.empty();
    }

    public static NavLogic.StickMode stickMode(StabilizationMode mode) {
        return switch (mode) {
            case ANGLE -> NavLogic.StickMode.NORMAL;
            case HORIZON -> NavLogic.StickMode.CINE;
            case ACRO -> NavLogic.StickMode.SPORT;
        };
    }

    public static String modeNameKey(StabilizationMode mode, boolean atti) {
        return MODE_NAME_PREFIX + switch (stickMode(mode)) {
            case NORMAL -> atti ? "atti" : "n";
            case CINE -> "c";
            case SPORT -> "s";
        };
    }

    public Optional<String> modeNameKey(StabilizationMode mode) {
        return mode == null ? Optional.empty() : Optional.of(modeNameKey(mode, this.logic.atti()));
    }

    @Override
    public double sensorRange() {
        return this.sensors.isEmpty() ? 0.0 : ObstacleGuard.SENSOR_RANGE;
    }

    @Override
    public void reset() {
        NavLogic.StickMode mode = this.logic.stickMode();
        boolean returnRequested = this.logic.battery().returnRequested();
        this.logic = new NavLogic(this.flightClass);
        this.logic.stickMode(mode);
        this.logic.battery().returnRequested(returnRequested);
        this.armedBefore = false;
        this.speedBefore = 0.0;
        this.started = false;
        this.homeKnown = false;
        this.limitsAt = Double.NaN;
        this.maxAltitude = -1;
        this.maxDistance = -1;
        this.reportedState = NavLogic.MissionState.IDLE;
        this.reportedIndex = 0;
        this.status = null;
        this.refusedAt = Double.NaN;
        this.control.reset();
        this.vertical.reset();
        Arrays.fill(this.seen, false);
        Arrays.fill(this.probedAt, Double.NaN);
    }

    @Override
    public void update(FlightState state, AssistOutput output) {
        this.read(state);
        this.crash(state);
        this.controls(state);
        this.target();
        this.home(state);
        this.limits();
        this.logic.update(this.in, this.out);
        this.refusedLabel();
        if (this.piloted) {
            this.control.apply(this.in, this.out);
            this.vertical.apply(this.in, this.out, state.estimatedY(), state.estimatedVy(), this.logic.guard().braking());
        }
        this.report();
        forward(this.out, output, this.sink);
    }

    private static double pick(double estimate, double truth) {
        return Double.isFinite(estimate) ? estimate : truth;
    }

    private void read(FlightState state) {
        this.in.time(state.time())
                .position(state.x(), this.piloted ? state.y() : pick(state.estimatedY(), state.y()), state.z())
                .velocity(state.vx(), this.piloted ? state.vy() : pick(state.estimatedVy(), state.vy()), state.vz())
                .attitude(state.headingDeg(), state.pitchDeg(), state.rollDeg())
                .gps(state.gpsFix() && state.positionValid(), state.satellites())
                .compass(CompassCheck.healthy(state), CompassCheck.errorDeg(state))
                .heightAboveGround(state.heightAboveGround())
                .grounded(state.grounded())
                .battery(state.batteryCharge())
                .pilot(this.piloted)
                .sticks(state.stickRoll(), state.stickPitch(), state.stickYaw(), state.stickThrottle())
                .angleLimit(state.angleLimitDeg())
                .hoverThrottle(state.hoverThrottle());
        double height = groundHeight(state);
        this.in.sightHeight(Double.isNaN(height) || height < 0.0 ? Double.NaN : GROUND_SHARE * height);
        boolean lifting = this.lifting();
        for (NavInput.Sensor sensor : SENSORS) {
            SensorDirection direction = direction(sensor);
            if (!this.sensors.contains(direction)) {
                this.in.obstacle(sensor, Double.NaN).range(sensor, Double.NaN);
                this.seen[sensor.ordinal()] = false;
                continue;
            }
            double distance = state.obstacleDistance(direction);
            if (sensor == NavInput.Sensor.UP || sensor == NavInput.Sensor.DOWN) {
                this.in.obstacle(sensor, distance).range(sensor, Double.NaN);
                continue;
            }
            double down = downward(sensor, state);
            double level = Math.sqrt(Math.max(0.0, 1.0 - down * down));
            double reach = ObstacleGuard.SENSOR_RANGE * level;
            double filtered = withoutGround(sensor, distance, state);
            double horizontal = Double.isFinite(filtered) ? filtered * level : filtered;
            double band = this.band(sensor, state, height);
            double nearest = Double.isNaN(band) ? horizontal : Double.isNaN(horizontal) ? band : Math.min(horizontal, band);
            this.in.obstacle(sensor, this.remember(sensor, nearest, down, level, Double.isFinite(distance) ? distance * level : reach,
                            !Double.isNaN(band), lifting))
                    .range(sensor, reach);
        }
    }

    private boolean lifting() {
        NavLogic.Function function = this.logic.function();
        boolean automatic = function != NavLogic.Function.NONE && function != NavLogic.Function.HOLD && function != NavLogic.Function.COURSE_LOCK;
        double throttle = Math.abs(this.in.stickThrottle() - NavInput.THROTTLE_CENTER) * 2.0;
        return !this.piloted || automatic || this.logic.guard().climbing() || throttle > NormalMode.THROTTLE_DEADBAND;
    }

    private double band(NavInput.Sensor sensor, FlightState state, double height) {
        int i = sensor.ordinal();
        double now = this.in.time();
        double heading = this.in.heading();
        double axisX = axisX(sensor, heading);
        double axisZ = axisZ(sensor, heading);
        double age = now - this.probedAt[i];
        if (!(age >= 0.0 && age < PROBE_PERIOD) || Math.abs(Compass.wrap180(heading - this.probedHeading[i])) > PROBE_TURN) {
            this.probed[i] = this.probe(axisX, axisZ, state, height);
            this.probedAt[i] = now;
            this.probedX[i] = this.in.x();
            this.probedZ[i] = this.in.z();
            this.probedHeading[i] = heading;
        }
        double value = this.probed[i];
        if (!Double.isFinite(value)) {
            return value;
        }
        double moved = (this.in.x() - this.probedX[i]) * axisX + (this.in.z() - this.probedZ[i]) * axisZ;
        return Math.max(0.0, value - moved);
    }

    private double probe(double axisX, double axisZ, FlightState state, double height) {
        double up = state.obstacleDistance(SensorDirection.UP);
        double ceiling = Double.isFinite(up) && state.upY() > GROUND_RAY_MIN ? up * state.upY() : Double.POSITIVE_INFINITY;
        double below = Double.isNaN(height) ? 0.0 : Math.clamp(height - PROBE_CLEARANCE, 0.0, PROBE_BAND);
        double above = Math.clamp(ceiling - PROBE_CLEARANCE, 0.0, PROBE_BAND);
        double x = this.in.x();
        double y = this.in.y();
        double z = this.in.z();
        double range = ObstacleGuard.SENSOR_RANGE;
        double nearest = this.context.probe(x, y, z, axisX, axisZ, range);
        if (Double.isNaN(nearest)) {
            return Double.NaN;
        }
        for (double offset : new double[] {-below, above}) {
            if (Math.abs(offset) < PROBE_MIN_OFFSET) {
                continue;
            }
            double distance = this.context.probe(x, y + offset, z, axisX, axisZ, range);
            if (Double.isNaN(distance)) {
                return Double.NaN;
            }
            nearest = Math.min(nearest, distance);
        }
        return nearest;
    }

    public static double downward(NavInput.Sensor sensor, FlightState state) {
        return switch (sensor) {
            case FORWARD -> -state.forwardY();
            case BACK -> state.forwardY();
            case RIGHT -> -state.rightY();
            case LEFT -> state.rightY();
            case UP, DOWN -> 0.0;
        };
    }

    public static double groundHeight(FlightState state) {
        double below = state.obstacleDistance(SensorDirection.DOWN);
        return Double.isFinite(below) && state.upY() > GROUND_RAY_MIN ? below * state.upY() : state.heightAboveGround();
    }

    public static double withoutGround(NavInput.Sensor sensor, double distance, FlightState state) {
        double down = downward(sensor, state);
        if (!(down > GROUND_RAY_MIN) || !Double.isFinite(distance)) {
            return distance;
        }
        double height = groundHeight(state);
        if (!Double.isFinite(height) || height < 0.0) {
            return distance;
        }
        return distance >= GROUND_SHARE * height / down ? Double.POSITIVE_INFINITY : distance;
    }

    private double remember(NavInput.Sensor sensor, double horizontal, double down, double level, double reached, boolean probes,
                            boolean lifting) {
        int i = sensor.ordinal();
        double heading = this.in.heading();
        if (Double.isNaN(horizontal)) {
            this.seen[i] = false;
            return horizontal;
        }
        if (Double.isFinite(horizontal)) {
            if (!this.seen[i] || lifting) {
                this.seenY[i] = this.in.y();
            }
            this.seen[i] = true;
            this.seenDistance[i] = horizontal;
            this.seenX[i] = this.in.x();
            this.seenZ[i] = this.in.z();
            this.seenHeading[i] = heading;
            return horizontal;
        }
        if (!this.seen[i]) {
            return horizontal;
        }
        if (lifting) {
            this.seenY[i] = this.in.y();
        }
        double axisX = axisX(sensor, this.seenHeading[i]);
        double axisZ = axisZ(sensor, this.seenHeading[i]);
        double dx = this.in.x() - this.seenX[i];
        double dz = this.in.z() - this.seenZ[i];
        double left = this.seenDistance[i] - (dx * axisX + dz * axisZ);
        double side = Math.abs(dx * axisZ - dz * axisX);
        boolean evaded = side > MEMORY_SIDE || left < -MEMORY_PASSED || left > ObstacleGuard.SENSOR_RANGE
                || Math.abs(Compass.wrap180(heading - this.seenHeading[i])) > MEMORY_TURN;
        boolean looked = probes || reached >= left && (level < 1.0E-6 ? 0.0 : -down * Math.max(0.0, left) / level) <= LEVEL_BAND;
        boolean risen = this.in.y() - this.seenY[i] > MEMORY_RISE;
        if (evaded || looked && !risen) {
            this.seen[i] = false;
            return horizontal;
        }
        return Math.max(0.0, left);
    }

    private static double axisX(NavInput.Sensor sensor, double heading) {
        return switch (sensor) {
            case FORWARD -> Compass.forwardX(heading);
            case BACK -> -Compass.forwardX(heading);
            case RIGHT -> Compass.rightX(heading);
            case LEFT -> -Compass.rightX(heading);
            case UP, DOWN -> 0.0;
        };
    }

    private static double axisZ(NavInput.Sensor sensor, double heading) {
        return switch (sensor) {
            case FORWARD -> Compass.forwardZ(heading);
            case BACK -> -Compass.forwardZ(heading);
            case RIGHT -> Compass.rightZ(heading);
            case LEFT -> -Compass.rightZ(heading);
            case UP, DOWN -> 0.0;
        };
    }

    private void crash(FlightState state) {
        boolean armed = state.armed();
        if (this.armedBefore && !armed && (!state.grounded() || this.speedBefore > CRASH_SPEED)) {
            this.logic.crash();
        }
        this.armedBefore = armed;
        double speed = Math.sqrt(state.vx() * state.vx() + state.vy() * state.vy() + state.vz() * state.vz());
        this.speedBefore = Double.isFinite(speed) ? speed : 0.0;
    }

    public static SensorDirection direction(NavInput.Sensor sensor) {
        return switch (sensor) {
            case FORWARD -> SensorDirection.FORWARD;
            case BACK -> SensorDirection.BACK;
            case LEFT -> SensorDirection.LEFT;
            case RIGHT -> SensorDirection.RIGHT;
            case UP -> SensorDirection.UP;
            case DOWN -> SensorDirection.DOWN;
        };
    }

    private boolean pressed(FlightState state, CamControl control) {
        return state.addonSwitch(control.idString()) || this.context.active(control);
    }

    private void pilotMode(FlightState state) {
        StabilizationMode mode = this.piloted ? state.pilotMode() : null;
        if (mode == null || mode == this.pilotMode) {
            return;
        }
        this.pilotMode = mode;
        this.logic.stickMode(stickMode(mode));
    }

    private void controls(FlightState state) {
        boolean first = !this.started;
        this.started = true;
        this.pilotMode(state);
        for (CamControl control : CONTROLS) {
            boolean now = this.pressed(state, control);
            int slot = control.ordinal();
            boolean before = first ? !control.toggle() && now : this.previous[slot];
            this.previous[slot] = now;
            if (now && !before) {
                this.rising(control, state);
            } else if (!now && before) {
                this.falling(control);
            }
        }
    }

    private void rising(CamControl control, FlightState state) {
        switch (control) {
            case MODE_NORMAL -> this.logic.stickMode(NavLogic.StickMode.NORMAL);
            case MODE_CINE -> this.logic.stickMode(NavLogic.StickMode.CINE);
            case MODE_SPORT -> this.logic.stickMode(NavLogic.StickMode.SPORT);
            case MODE_CYCLE -> this.logic.cycleStickMode();
            case PAUSE -> this.pause();
            case MISSION_START -> this.startMission();
            case WAYPOINT_ADD -> this.context.waypointAdded(this.in.x(), this.in.y(), this.in.z(), this.in.heading());
            case FOLLOW_ME -> this.logic.follow(this.context.followStyle());
            case POI_HERE -> this.pointOfInterest(state);
            case COURSE_LOCK -> this.logic.courseLock(this.in.heading());
            default -> {
            }
        }
    }

    private void falling(CamControl control) {
        NavLogic.Function function = switch (control) {
            case FOLLOW_ME -> NavLogic.Function.FOLLOW;
            case COURSE_LOCK -> NavLogic.Function.COURSE_LOCK;
            default -> null;
        };
        if (function != null && this.logic.function() == function) {
            this.logic.cancel();
        }
    }

    private void pause() {
        if (this.logic.function() == NavLogic.Function.MISSION && this.logic.missionState() == NavLogic.MissionState.RUNNING) {
            this.logic.pauseMission();
        } else {
            this.logic.hold();
        }
    }

    private void startMission() {
        if (this.logic.resumeMission()) {
            return;
        }
        this.context.mission().ifPresent(mission -> this.logic.startMission(mission, 0));
    }

    private void pointOfInterest(FlightState state) {
        Optional<NavTarget> point = this.context.pointOfInterest(this.in.x(), this.in.y(), this.in.z(), state.forwardX(), state.forwardY(),
                state.forwardZ(), POI_RANGE);
        if (point.isEmpty()) {
            this.refusedAt = this.in.time();
            this.refusedDuring = this.logic.function();
            return;
        }
        NavTarget p = point.get();
        this.refusedAt = Double.NaN;
        this.logic.orbit(OrbitController.around(this.flightClass, this.in, p.x(), p.y(), p.z(), 0.0, this.flightClass.cineSpeed(), true));
    }

    private void refusedLabel() {
        if (Double.isNaN(this.refusedAt)) {
            return;
        }
        double age = this.in.time() - this.refusedAt;
        if (!(age >= 0.0 && age < POI_REFUSED_SECONDS) || this.logic.function() != this.refusedDuring) {
            this.refusedAt = Double.NaN;
            return;
        }
        this.out.label(POI_REFUSED_LABEL);
    }

    private void target() {
        Optional<NavTarget> target = this.logic.function() == NavLogic.Function.FOLLOW ? this.context.followTarget() : Optional.empty();
        if (target.isPresent()) {
            NavTarget t = target.get();
            this.in.target(t.x(), t.y(), t.z(), t.vx(), t.vy(), t.vz());
        } else {
            this.in.clearTarget();
        }
    }

    private void home(FlightState state) {
        if (this.homeKnown && !state.grounded()) {
            return;
        }
        Optional<NavTarget> given = this.homeKnown ? Optional.empty() : this.context.home();
        NavTarget home = given.orElseGet(() -> NavTarget.at(this.in.x(), this.in.y(), this.in.z()));
        this.homeX = home.x();
        this.homeY = home.y();
        this.homeZ = home.z();
        this.homeKnown = true;
    }

    private void limits() {
        double now = this.in.time();
        if (!Double.isNaN(this.limitsAt) && now - this.limitsAt < LIMIT_REFRESH_SECONDS && now >= this.limitsAt) {
            return;
        }
        this.limitsAt = now;
        this.maxAltitude = Math.max(0, this.context.maxAltitude());
        this.maxDistance = Math.max(0, this.context.maxDistance());
        this.logic.heightLimit(CamWorldRules.limit(this.homeX, this.homeY, this.homeZ, this.maxAltitude, this.maxDistance));
    }

    public int maxAltitude() {
        return this.maxAltitude;
    }

    public int maxDistance() {
        return this.maxDistance;
    }

    private void report() {
        NavLogic.MissionState state = this.logic.missionState();
        int index = this.logic.missionIndex();
        if (state != this.reportedState || index != this.reportedIndex) {
            this.reportedState = state;
            this.reportedIndex = index;
            this.context.missionProgress(state, index);
        }
        ObstacleGuard guard = this.logic.guard();
        double nearest = guard.nearestDistance();
        this.status = new NavStatus(this.flightClass, this.out.label(), this.logic.stickMode(), this.logic.function(), state, index,
                this.out.warnings(), this.logic.atti(), this.out.gimbalTilt(),
                this.out.hasGimbalTarget() ? Optional.of(NavTarget.at(this.out.gimbalX(), this.out.gimbalY(), this.out.gimbalZ())) : Optional.empty(),
                this.out.action(), Optional.ofNullable(guard.nearestSensor()), Double.isFinite(nearest) ? nearest : Double.NaN);
        this.context.status(this.status);
    }

    public static void forward(NavOutput out, AssistOutput output) {
        forward(out, output, new ForwardSink());
    }

    private static void forward(NavOutput out, AssistOutput output, ForwardSink sink) {
        sink.target = output;
        try {
            out.writeTo(sink);
        } finally {
            sink.target = null;
        }
    }

    private static final class ForwardSink implements NavOutput.Sink {
        private AssistOutput target;

        @Override
        public void sticks(double roll, double pitch, double yaw, double throttle) {
            this.target.sticks(roll, pitch, yaw, throttle);
        }

        @Override
        public void angleMode() {
            this.target.mode(StabilizationMode.ANGLE);
        }

        @Override
        public void holdAltitude(double y) {
            this.target.holdAltitude(y);
        }

        @Override
        public void climb(double metresPerSecond) {
            this.target.climb(metresPerSecond);
        }

        @Override
        public void holdPosition(double x, double z) {
            this.target.holdPosition(x, z);
        }

        @Override
        public void velocity(double vx, double vz) {
            this.target.velocity(vx, vz);
        }

        @Override
        public void maxSpeed(double metresPerSecond) {
            this.target.maxSpeed(metresPerSecond);
        }

        @Override
        public void yawRate(double degreesPerSecond) {
            this.target.yawRate(degreesPerSecond);
        }

        @Override
        public void heading(double compassDegrees) {
            this.target.heading(compassDegrees);
        }

        @Override
        public void label(String text) {
            this.target.label(text);
        }

        @Override
        public void requestReturnHome() {
            this.target.requestReturnHome();
        }

        @Override
        public void land() {
            this.target.land();
        }
    }
}
