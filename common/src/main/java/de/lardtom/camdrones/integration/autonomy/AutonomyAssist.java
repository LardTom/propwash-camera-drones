package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.NavAssist;
import de.lardtom.camdrones.integration.NavContext;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.logic.nav.OrbitController;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class AutonomyAssist implements FlightAssist {
    public static final double TARGET_LOST_SECONDS = 2.0;
    public static final double PAD_ALIGN_RADIUS = 0.08;
    public static final double PAD_ALIGN_SPEED = 0.15;
    public static final double PAD_APPROACH_SPEED = 1.0;
    public static final double PAD_ALIGN_SECONDS = 20.0;
    public static final double HOME_CLIMB = 1.0;
    public static final double HOME_SETTLE_MIN_SECONDS = 0.5;
    public static final double HOME_SETTLE_MAX_SECONDS = 4.0;
    public static final double HOME_SETTLE_HEIGHT = 0.15;
    public static final double HOME_SETTLE_CLIMB = 0.25;

    public enum Control {
        PAUSE,
        RESUME,
        HOLD,
        STOP
    }

    public interface Environment {
        Environment NONE = new Environment() {
        };

        default int maxAltitude() {
            return CamWorldRules.DEFAULT_MAX_ALTITUDE;
        }

        default int maxDistance() {
            return CamWorldRules.DEFAULT_MAX_DISTANCE;
        }

        default Optional<NavTarget> followTarget(UUID target) {
            return Optional.empty();
        }

        default Optional<NavTarget> landingPad(double x, double y, double z) {
            return Optional.empty();
        }
    }

    public interface Listener {
        Listener NONE = new Listener() {
        };

        default void started(AutonomyAssist assist) {
        }

        default void progress(AutonomyAssist assist, NavLogic.MissionState state, int index) {
        }

        default void missionDone(AutonomyAssist assist) {
        }

        default void targetLost(AutonomyAssist assist) {
        }

        default void autoShotDone(AutonomyAssist assist, AutoShots.Shot shot) {
        }
    }

    private final FlightClass flightClass;
    private final Context context = new Context();
    private final NavAssist nav;
    private final boolean groundStart;
    private final ObstacleGuard.Mode obstacle;
    private final NavTarget home;
    private final Environment environment;
    private final Listener listener;
    private final LongSupplier clock;
    private volatile AutonomyPlan plan;
    private volatile AutonomyPlan pendingPlan;
    private volatile Control pendingControl;
    private boolean applied;
    private volatile boolean started;
    private volatile boolean landing;
    private boolean doneReported;
    private double lostSince = Double.NaN;
    private boolean targetFound;
    private volatile NavLogic.MissionState missionState = NavLogic.MissionState.IDLE;
    private volatile int missionIndex;
    private volatile long lastUpdate = Long.MIN_VALUE;
    private volatile NavStatus status;
    private final LandGate gate = new LandGate();
    private boolean padChecked;
    private boolean padReleased;
    private double padSince = Double.NaN;
    private NavTarget pad;
    private double homeSince = Double.NaN;
    private double homeY = Double.NaN;

    public AutonomyAssist(FlightClass flightClass, Set<SensorDirection> sensors, AutonomyPlan plan, boolean groundStart, ObstacleGuard.Mode obstacle,
                          NavTarget home, Environment environment, Listener listener, LongSupplier clock) {
        this.flightClass = Objects.requireNonNull(flightClass, "flightClass");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.groundStart = groundStart;
        this.obstacle = Objects.requireNonNullElse(obstacle, ObstacleGuard.Mode.STOP);
        this.home = home;
        this.environment = Objects.requireNonNullElse(environment, Environment.NONE);
        this.listener = Objects.requireNonNullElse(listener, Listener.NONE);
        this.clock = Objects.requireNonNullElse(clock, System::currentTimeMillis);
        this.nav = new NavAssist(flightClass, sensors, false, this.context);
        this.missionIndex = plan.startIndex();
    }

    public FlightClass flightClass() {
        return this.flightClass;
    }

    public AutonomyPlan plan() {
        AutonomyPlan next = this.pendingPlan;
        return next != null ? next : this.plan;
    }

    public NavAssist nav() {
        return this.nav;
    }

    public boolean groundStart() {
        return this.groundStart;
    }

    public boolean started() {
        return this.started;
    }

    public boolean landing() {
        return this.landing;
    }

    public long lastUpdate() {
        return this.lastUpdate;
    }

    public NavLogic.MissionState missionState() {
        return this.missionState;
    }

    public int missionIndex() {
        return this.missionIndex;
    }

    public boolean missionLoaded() {
        return this.plan().kind() == AutonomyPlan.Kind.MISSION;
    }

    public Optional<NavStatus> status() {
        return Optional.ofNullable(this.status);
    }

    public Optional<NavTarget> pad() {
        return Optional.ofNullable(this.pad);
    }

    public boolean padReleased() {
        return this.padReleased;
    }

    public void replan(AutonomyPlan next) {
        this.pendingPlan = Objects.requireNonNull(next, "next");
    }

    public void control(Control control) {
        this.pendingControl = Objects.requireNonNull(control, "control");
    }

    @Override
    public double sensorRange() {
        return this.nav.sensorRange();
    }

    @Override
    public void reset() {
        this.nav.reset();
        AutonomyPlan current = this.plan;
        if (current.kind() == AutonomyPlan.Kind.MISSION) {
            this.plan = switch (this.missionState) {
                case RUNNING -> current.withStartIndex(this.missionIndex).withPaused(false);
                case PAUSED -> current.withStartIndex(this.missionIndex).withPaused(true);
                case DONE, ABORTED -> this.landing ? AutonomyPlan.land() : AutonomyPlan.hold();
                case IDLE -> current;
            };
        } else if (this.landing) {
            this.plan = AutonomyPlan.land();
        } else if (current.kind() == AutonomyPlan.Kind.AUTO_SHOT && this.applied) {
            this.plan = AutonomyPlan.hold();
        }
        this.applied = false;
        this.doneReported = false;
        this.lostSince = Double.NaN;
        this.clearPad();
    }

    @Override
    public void update(FlightState state, AssistOutput output) {
        this.lastUpdate = this.clock.getAsLong();
        if (!this.started) {
            this.started = true;
            this.listener.started(this);
        }
        AutonomyPlan next = this.pendingPlan;
        if (next != null) {
            this.pendingPlan = null;
            this.plan = next;
            this.applied = false;
            this.doneReported = false;
        }
        if (!this.applied) {
            this.applied = true;
            this.apply(state);
        }
        Control control = this.pendingControl;
        if (control != null) {
            this.pendingControl = null;
            this.apply(control);
        }
        this.targetFound = false;
        this.gate.open(output);
        try {
            this.nav.update(state, this.gate);
        } finally {
            this.gate.close();
        }
        this.after(state, output);
    }

    private void apply(FlightState state) {
        NavLogic logic = this.nav.logic();
        logic.obstacleMode(this.obstacle);
        this.landing = false;
        this.lostSince = Double.NaN;
        AutonomyPlan current = this.plan;
        switch (current.kind()) {
            case MISSION -> {
                Mission mission = current.mission().orElseThrow();
                logic.startMission(mission, current.startIndex());
                if (current.paused()) {
                    logic.pauseMission();
                }
            }
            case HOLD -> logic.hold();
            case FOLLOW -> logic.follow(new FollowController(this.flightClass, current.style()));
            case ORBIT -> {
                double radius = current.radius() > 0.0 ? current.radius() : Math.hypot(state.x() - current.x(), state.z() - current.z());
                double speed = current.speed() > 0.0 ? current.speed() : this.flightClass.cineSpeed();
                logic.orbit(new OrbitController(this.flightClass, current.x(), current.y(), current.z(), Math.max(OrbitController.MIN_RADIUS, radius),
                        state.y(), speed, current.clockwise()));
            }
            case LAND -> {
                logic.hold();
                this.landing = true;
            }
            case AUTO_SHOT -> logic.autoShot(new AutoShots(this.flightClass, current.shot(), current.x(), current.y(), current.z()));
            case HOME -> {
                logic.hold();
                this.homeSince = state.time();
                this.homeY = state.y();
            }
        }
        this.publish(logic);
    }

    private void apply(Control control) {
        NavLogic logic = this.nav.logic();
        AutonomyPlan current = this.plan;
        boolean mission = current.kind() == AutonomyPlan.Kind.MISSION;
        switch (control) {
            case PAUSE -> logic.pauseMission();
            case RESUME -> {
                if (!logic.resumeMission() && mission && logic.missionState() != NavLogic.MissionState.RUNNING) {
                    logic.startMission(current.mission().orElseThrow(), this.missionIndex);
                }
                this.landing = false;
            }
            case HOLD -> {
                if (!mission) {
                    this.plan = AutonomyPlan.hold();
                }
                logic.hold();
                this.landing = false;
            }
            case STOP -> {
                if (mission) {
                    logic.abortMission();
                } else {
                    this.plan = AutonomyPlan.land();
                    logic.hold();
                }
                this.landing = true;
            }
        }
        this.publish(logic);
    }

    private void publish(NavLogic logic) {
        NavLogic.MissionState state = logic.missionState();
        int index = logic.missionIndex();
        if (state != this.missionState || index != this.missionIndex) {
            this.missionState = state;
            this.missionIndex = index;
            this.listener.progress(this, state, index);
        }
    }

    private void after(FlightState state, AssistOutput output) {
        NavLogic logic = this.nav.logic();
        this.publish(logic);
        AutonomyPlan current = this.plan;
        if (current.kind() == AutonomyPlan.Kind.MISSION && this.missionState == NavLogic.MissionState.DONE && !this.doneReported) {
            this.doneReported = true;
            Optional<Mission> mission = current.mission();
            if (this.groundStart && mission.isPresent() && mission.get().end() == Mission.End.START) {
                this.landing = true;
            }
            this.listener.missionDone(this);
        }
        if (current.kind() == AutonomyPlan.Kind.AUTO_SHOT && logic.activeMode() instanceof AutoShots shot && shot.done()) {
            this.plan = AutonomyPlan.hold();
            this.listener.autoShotDone(this, shot.shot());
        }
        if (current.kind() == AutonomyPlan.Kind.FOLLOW && logic.function() == NavLogic.Function.FOLLOW) {
            if (this.targetFound) {
                this.lostSince = Double.NaN;
            } else if (Double.isNaN(this.lostSince)) {
                this.lostSince = state.time();
            } else if (state.time() - this.lostSince >= TARGET_LOST_SECONDS) {
                this.plan = AutonomyPlan.hold();
                this.applied = false;
                this.lostSince = Double.NaN;
                this.listener.targetLost(this);
            }
        }
        if (this.landing || this.gate.landRequested) {
            this.land(state, output);
            output.label(AutonomyPlan.Kind.LAND.label());
            return;
        }
        this.clearPad();
        if (this.plan.kind() == AutonomyPlan.Kind.HOME) {
            output.holdAltitude(this.homeY + HOME_CLIMB);
            if (this.readyForHome(state)) {
                output.requestReturnHome();
            }
            output.label(AutonomyPlan.Kind.HOME.label());
        }
    }

    private boolean readyForHome(FlightState state) {
        double held = state.time() - this.homeSince;
        boolean steady = Math.abs(state.y() - (this.homeY + HOME_CLIMB)) <= HOME_SETTLE_HEIGHT && Math.abs(state.vy()) <= HOME_SETTLE_CLIMB;
        return held >= HOME_SETTLE_MAX_SECONDS || held >= HOME_SETTLE_MIN_SECONDS && steady;
    }

    private void land(FlightState state, AssistOutput output) {
        if (!this.padChecked) {
            this.padChecked = true;
            this.padSince = state.time();
            this.pad = this.environment.landingPad(state.x(), state.y(), state.z()).orElse(null);
            this.padReleased = this.pad == null;
        }
        NavTarget target = this.pad;
        if (target != null) {
            output.holdPosition(target.x(), target.z());
            if (!this.padReleased) {
                double off = Math.hypot(state.x() - target.x(), state.z() - target.z());
                double speed = Math.hypot(state.vx(), state.vz());
                if (off <= PAD_ALIGN_RADIUS && speed <= PAD_ALIGN_SPEED || state.time() - this.padSince >= PAD_ALIGN_SECONDS) {
                    this.padReleased = true;
                } else {
                    output.maxSpeed(PAD_APPROACH_SPEED);
                    output.climb(0.0);
                    return;
                }
            }
        }
        output.land();
    }

    private void clearPad() {
        this.padChecked = false;
        this.padReleased = false;
        this.padSince = Double.NaN;
        this.pad = null;
    }

    private static final class LandGate implements AssistOutput {
        private AssistOutput target;
        private boolean landRequested;

        void open(AssistOutput output) {
            this.target = output;
            this.landRequested = false;
        }

        void close() {
            this.target = null;
        }

        @Override
        public void sticks(double roll, double pitch, double yaw, double throttle) {
            this.target.sticks(roll, pitch, yaw, throttle);
        }

        @Override
        public void mode(StabilizationMode mode) {
            this.target.mode(mode);
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
            this.landRequested = true;
        }
    }

    private final class Context implements NavContext {
        @Override
        public boolean active(CamControl control) {
            return false;
        }

        @Override
        public int maxAltitude() {
            return AutonomyAssist.this.environment.maxAltitude();
        }

        @Override
        public int maxDistance() {
            return AutonomyAssist.this.environment.maxDistance();
        }

        @Override
        public Optional<NavTarget> home() {
            return Optional.ofNullable(AutonomyAssist.this.home);
        }

        @Override
        public Optional<Mission> mission() {
            return AutonomyAssist.this.plan.mission();
        }

        @Override
        public Optional<NavTarget> followTarget() {
            AutonomyPlan current = AutonomyAssist.this.plan;
            if (current.kind() != AutonomyPlan.Kind.FOLLOW || current.target().isEmpty()) {
                return Optional.empty();
            }
            Optional<NavTarget> target = AutonomyAssist.this.environment.followTarget(current.target().get());
            AutonomyAssist.this.targetFound = target.isPresent();
            return target;
        }

        @Override
        public void missionProgress(NavLogic.MissionState state, int index) {
            AutonomyAssist.this.publish(AutonomyAssist.this.nav.logic());
        }

        @Override
        public void status(NavStatus value) {
            AutonomyAssist.this.status = value;
        }
    }
}
