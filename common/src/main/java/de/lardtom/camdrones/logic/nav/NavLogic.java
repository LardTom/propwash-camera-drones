package de.lardtom.camdrones.logic.nav;

import de.lardtom.camdrones.logic.mission.Mission;

public final class NavLogic {
    public static final double STICK_OVERRIDE = 0.25;
    public static final double THROTTLE_OVERRIDE = 0.3;

    public enum StickMode {
        NORMAL,
        CINE,
        SPORT
    }

    public enum Function {
        NONE,
        HOLD,
        FOLLOW,
        ORBIT,
        COURSE_LOCK,
        MISSION,
        AUTO_SHOT
    }

    public enum MissionState {
        IDLE,
        RUNNING,
        PAUSED,
        DONE,
        ABORTED
    }

    private final FlightClass flightClass;
    private final AttiMode atti;
    private final ObstacleGuard guard = new ObstacleGuard();
    private final AttiWallHold wallHold = new AttiWallHold();
    private final BatteryGuard battery = new BatteryGuard();
    private final SportThrottle sportThrottle = new SportThrottle();
    private StickMode stickMode = StickMode.NORMAL;
    private NavMode stick;
    private ObstacleGuard.Mode obstacleMode = ObstacleGuard.Mode.STOP;
    private HeightLimit limit = HeightLimit.off();
    private Function function = Function.NONE;
    private NavMode active;
    private Mission mission;
    private int missionIndex;
    private MissionState missionState = MissionState.IDLE;
    private WaypointFollower follower;
    private boolean inAtti;
    private boolean returnHome;

    public NavLogic(FlightClass flightClass) {
        this.flightClass = flightClass;
        this.atti = new AttiMode(flightClass);
        this.stick = this.create(StickMode.NORMAL);
    }

    private NavMode create(StickMode mode) {
        return switch (mode) {
            case NORMAL -> new NormalMode(this.flightClass);
            case CINE -> new CineMode(this.flightClass);
            case SPORT -> new SportMode(this.flightClass, this.sportThrottle);
        };
    }

    public FlightClass flightClass() {
        return this.flightClass;
    }

    public StickMode stickMode() {
        return this.stickMode;
    }

    public void stickMode(StickMode mode) {
        if (mode != null && mode != this.stickMode) {
            this.stickMode = mode;
            this.stick = this.create(mode);
        }
    }

    public StickMode cycleStickMode() {
        StickMode[] all = StickMode.values();
        this.stickMode(all[(this.stickMode.ordinal() + 1) % all.length]);
        return this.stickMode;
    }

    public ObstacleGuard.Mode obstacleMode() {
        return this.obstacleMode;
    }

    public void obstacleMode(ObstacleGuard.Mode mode) {
        if (mode != null) {
            this.obstacleMode = mode;
        }
    }

    public HeightLimit heightLimit() {
        return this.limit;
    }

    public void heightLimit(HeightLimit value) {
        this.limit = value == null ? HeightLimit.off() : value;
    }

    public Function function() {
        return this.function;
    }

    public NavMode activeMode() {
        return this.function == Function.NONE ? this.stick : this.active;
    }

    public boolean atti() {
        return this.inAtti;
    }

    public NavOutput.Warning attiCause() {
        return this.atti.cause();
    }

    public ObstacleGuard guard() {
        return this.guard;
    }

    public AttiWallHold wallHold() {
        return this.wallHold;
    }

    public SportThrottle sportThrottle() {
        return this.sportThrottle;
    }

    public BatteryGuard battery() {
        return this.battery;
    }

    public Mission mission() {
        return this.mission;
    }

    public int missionIndex() {
        return this.function == Function.MISSION && this.follower != null ? this.follower.index() : this.missionIndex;
    }

    public MissionState missionState() {
        return this.missionState;
    }

    public WaypointFollower follower() {
        return this.function == Function.MISSION ? this.follower : null;
    }

    public double sensorRange() {
        return ObstacleGuard.SENSOR_RANGE;
    }

    public void hold() {
        this.set(Function.HOLD, new HoldController());
    }

    public void follow(FollowController.Style style) {
        this.set(Function.FOLLOW, new FollowController(this.flightClass, style));
    }

    public void follow(FollowController controller) {
        this.set(Function.FOLLOW, controller);
    }

    public void orbit(OrbitController controller) {
        this.set(Function.ORBIT, controller);
    }

    public void courseLock(double course) {
        this.set(Function.COURSE_LOCK, new CourseLock(this.flightClass, course, this.stickMode == StickMode.CINE));
    }

    public void autoShot(AutoShots shot) {
        this.set(Function.AUTO_SHOT, shot);
    }

    public void startMission(Mission value, int index) {
        if (value == null || value.isEmpty()) {
            return;
        }
        this.mission = value;
        this.missionIndex = Math.clamp(index, 0, value.size() - 1);
        this.follower = new WaypointFollower(this.flightClass, value, this.missionIndex);
        this.missionState = MissionState.RUNNING;
        this.set(Function.MISSION, this.follower);
    }

    public void pauseMission() {
        if (this.function == Function.MISSION && this.missionState == MissionState.RUNNING) {
            this.missionIndex = this.follower.index();
            this.missionState = MissionState.PAUSED;
            this.follower = null;
            this.hold();
        }
    }

    public boolean resumeMission() {
        if (this.mission == null || this.missionState != MissionState.PAUSED) {
            return false;
        }
        this.startMission(this.mission, this.missionIndex);
        return true;
    }

    public void stopMission() {
        this.endMission(MissionState.IDLE);
    }

    public void abortMission() {
        this.endMission(MissionState.ABORTED);
    }

    private void endMission(MissionState state) {
        boolean flying = this.function == Function.MISSION;
        this.missionState = this.mission == null ? MissionState.IDLE : state;
        this.mission = null;
        this.follower = null;
        this.missionIndex = 0;
        if (flying) {
            this.hold();
        }
    }

    public void requestReturnHome() {
        this.returnHome = true;
    }

    public void crash() {
        if (this.missionState == MissionState.RUNNING || this.missionState == MissionState.PAUSED) {
            this.abortMission();
        }
        this.set(Function.NONE, null);
        this.guard.reset();
        this.wallHold.reset();
    }

    public void cancel() {
        if (this.function == Function.MISSION) {
            this.suspend();
            return;
        }
        this.set(Function.NONE, null);
    }

    private void set(Function value, NavMode mode) {
        if (this.function == Function.MISSION && value != Function.MISSION && this.missionState == MissionState.RUNNING) {
            this.missionIndex = this.follower.index();
            this.missionState = MissionState.PAUSED;
            this.follower = null;
        }
        this.function = mode == null ? Function.NONE : value;
        this.active = mode;
        this.stick.reset();
    }

    private void suspend() {
        this.set(Function.NONE, null);
    }

    private boolean overridable() {
        return this.function == Function.MISSION || this.function == Function.AUTO_SHOT || this.function == Function.HOLD;
    }

    public static boolean sticksMoved(NavInput in) {
        return in.pilot() && (Math.abs(in.stickRoll()) > NormalMode.STICK_DEADBAND || Math.abs(in.stickPitch()) > NormalMode.STICK_DEADBAND
                || Math.abs(in.stickYaw()) > NormalMode.STICK_DEADBAND || Math.abs(in.stickThrottle() - NavInput.THROTTLE_CENTER) > THROTTLE_OVERRIDE);
    }

    public static boolean sticksActive(NavInput in) {
        return in.pilot() && (Math.abs(in.stickRoll()) > STICK_OVERRIDE || Math.abs(in.stickPitch()) > STICK_OVERRIDE
                || Math.abs(in.stickYaw()) > STICK_OVERRIDE || Math.abs(in.stickThrottle() - NavInput.THROTTLE_CENTER) > THROTTLE_OVERRIDE);
    }

    public void update(NavInput in, NavOutput out) {
        out.reset();
        if (this.returnHome) {
            this.returnHome = false;
            out.requestReturnHome();
        }
        if (this.battery.startsLanding(in) && this.function != Function.NONE && this.function != Function.HOLD) {
            this.hold();
        }
        if (this.atti.engaged(in)) {
            if (!this.inAtti) {
                this.inAtti = true;
                this.wallHold.engage(in, this.flightClass, this.guarded());
                this.suspend();
                this.guard.reset();
            }
            this.atti.update(in, out);
            this.wallHold.apply(in, out);
            if (AttiWallHold.sensing(in, this.flightClass)) {
                out.warn(NavOutput.Warning.OBSTACLE_OFF);
            }
            this.finish(in, out);
            this.sportThrottle.reset();
            return;
        }
        if (this.inAtti) {
            this.inAtti = false;
            this.wallHold.reset();
            this.stick.reset();
        }
        if (this.overridable() && (this.function == Function.HOLD ? sticksMoved(in) : sticksActive(in))) {
            this.suspend();
        }
        if (!in.pilot() && this.function == Function.NONE) {
            this.hold();
        }
        NavMode mode = this.activeMode();
        mode.update(in, out);
        if (this.function == Function.MISSION && this.follower != null && this.follower.complete()
                && this.missionState == MissionState.RUNNING) {
            this.missionState = MissionState.DONE;
        }
        if (this.guarded()) {
            ObstacleGuard.Mode guardMode = this.function == Function.MISSION && this.mission != null ? this.mission.obstacle() : this.obstacleMode;
            boolean automatic = this.function != Function.NONE && this.function != Function.COURSE_LOCK;
            this.guard.apply(in, out, this.flightClass, guardMode, automatic);
        }
        this.finish(in, out);
        if (this.function == Function.NONE && this.stickMode == StickMode.SPORT) {
            this.sportThrottle.apply(in, out);
        } else {
            this.sportThrottle.reset();
        }
    }

    private boolean guarded() {
        return !(this.function == Function.NONE && this.stickMode == StickMode.SPORT);
    }

    private void finish(NavInput in, NavOutput out) {
        this.limit.apply(in, out);
        this.battery.apply(in, out, this.flightClass);
        this.flagPaused(out);
    }

    private void flagPaused(NavOutput out) {
        if (this.missionState == MissionState.PAUSED) {
            out.warn(NavOutput.Warning.PAUSED);
        }
    }
}
