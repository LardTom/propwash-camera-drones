package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.NavAssist;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.client.PilotNavContext;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.estimate.MissionClock;
import de.lardtom.camdrones.logic.gimbal.GimbalAim;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.CourseLock;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.HoldController;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.OrbitController;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CamOsdSource {
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double GROUND_HINT = 1.0;
    private static final CamOsdSource INSTANCE = new CamOsdSource();

    private final StatusWatch watch = new StatusWatch();
    private final WindWatch wind = new WindWatch();
    private final FlightLeft flightLeft = new FlightLeft();
    private final MissionClock eta = new MissionClock();
    private final ModeHint hint = new ModeHint();
    private UUID drone;
    private long builtAt = Long.MIN_VALUE;
    private CamOsdState state = CamOsdState.NONE;
    private MissionData missionData;
    private Mission mission;

    private CamOsdSource() {
    }

    public static CamOsdState state(OsdContext context) {
        Optional<Entity> drone = context.drone();
        return drone.isPresent() ? state(drone.get(), context.telemetry()) : CamOsdState.NONE;
    }

    public static CamOsdState state(Entity drone, FlightTelemetry telemetry) {
        if (drone == null || telemetry == null) {
            return CamOsdState.NONE;
        }
        synchronized (INSTANCE) {
            return INSTANCE.build(drone, telemetry);
        }
    }

    public static CamOsdState last() {
        synchronized (INSTANCE) {
            return INSTANCE.state;
        }
    }

    public static String lastHintMode() {
        synchronized (INSTANCE) {
            return INSTANCE.hint.last();
        }
    }

    public static boolean flightLeftMeasured() {
        synchronized (INSTANCE) {
            return INSTANCE.flightLeft.measured();
        }
    }

    public static Optional<NavStatus> pilotStatus(Entity drone) {
        if (drone == null) {
            return Optional.empty();
        }
        synchronized (INSTANCE) {
            return INSTANCE.freshStatus(drone, drone.level().getGameTime());
        }
    }

    private Optional<NavStatus> freshStatus(Entity drone, long tick) {
        Optional<NavStatus> last = PilotNavContext.INSTANCE.lastStatus();
        NavStatus status = last.orElse(null);
        this.watch.observe(status, tick);
        ClientInput input = PropwashClientApi.input();
        boolean flying = input.flying() && input.drone().filter(own -> own == drone).isPresent();
        return flying && this.watch.fresh(status, tick) ? last : Optional.empty();
    }

    private CamOsdState build(Entity drone, FlightTelemetry telemetry) {
        long tick = drone.level().getGameTime();
        UUID id = drone.getUUID();
        if (id.equals(this.drone) && tick == this.builtAt) {
            return this.state;
        }
        if (!id.equals(this.drone)) {
            this.drone = id;
            this.wind.reset();
            this.flightLeft.reset();
            this.flightLeft.bench(benchSeconds(drone));
            this.eta.reset();
            this.hint.reset();
        }
        this.builtAt = tick;
        this.state = this.compute(drone, telemetry, tick);
        return this.state;
    }

    public static boolean nearGround(Entity drone) {
        if (drone.onGround()) {
            return true;
        }
        Level level = drone.level();
        int blockX = (int) Math.floor(drone.getX());
        int blockZ = (int) Math.floor(drone.getZ());
        return level.hasChunk(blockX >> 4, blockZ >> 4)
                && drone.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) < GROUND_HINT;
    }

    public static double benchSeconds(Entity drone) {
        try {
            ItemStack stack = drone.getPickResult();
            if (stack == null || stack.isEmpty()) {
                return Double.NaN;
            }
            return PropwashApi.builds().read(stack).flatMap(data -> PropwashApi.builds().analyze(data.build()))
                    .map(stats -> stats.mixedFlightTimeMin() * 60.0).orElse(Double.NaN);
        } catch (RuntimeException e) {
            CamDrones.LOG.debug("Werkbank-Schätzung der Drohne {} nicht verfügbar", drone.getUUID(), e);
            return Double.NaN;
        }
    }

    private CamOsdState compute(Entity drone, FlightTelemetry t, long tick) {
        double time = tick / TICKS_PER_SECOND;
        Optional<NavStatus> status = this.freshStatus(drone, tick);
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        boolean registered = CamDroneData.registered();
        UUID serial = registered ? PropwashApi.droneData().get(drone, CamDroneData.serial()) : CamDroneData.NO_SERIAL;
        boolean cameraDrone = status.isPresent() || !CamDroneData.NO_SERIAL.equals(serial) || view.flatMap(v -> v.accessory(AccessorySlot.FRONT))
                .filter(id -> CamDrones.MOD_ID.equals(id.getNamespace())).isPresent();
        if (!cameraDrone) {
            this.wind.reset();
            this.hint.reset();
            return CamOsdState.NONE;
        }
        MissionStatus missionStatus = registered ? PropwashApi.droneData().get(drone, CamDroneData.missionState()) : MissionStatus.IDLE;
        SmartState smart = registered ? PropwashApi.droneData().get(drone, CamDroneData.smart()) : SmartState.NONE;
        DroneSettings settings = registered ? PropwashApi.droneData().get(drone, CamDroneData.settings()) : DroneSettings.DEFAULT;
        Mission current = registered ? this.mission(PropwashApi.droneData().get(drone, CamDroneData.mission())) : null;
        Optional<FlightClass> flightClass = status.map(NavStatus::flightClass).or(() -> view.map(v -> byMass(v.massKg())));

        NavLogic.MissionState missionState = status.map(NavStatus::missionState).orElse(missionStatus.state());
        int missionIndex = status.map(NavStatus::missionIndex).orElse(missionStatus.index());
        int missionSize = current == null ? 0 : current.size();
        double missionLeft = this.eta.update(flightClass.orElse(null), current, missionState, missionIndex, time, t.x(), t.y(), t.z(),
                nearGround(drone));

        String label = status.map(NavStatus::label).filter(text -> !text.isBlank())
                .orElseGet(() -> label(missionState, missionIndex, missionSize, smart));
        Set<NavOutput.Warning> warnings = warnings(status, missionState);
        boolean atti = status.map(NavStatus::atti).orElse(false);
        String hint = this.hint.update(status.isPresent(), status.map(NavStatus::label).orElse(""), atti, time);

        boolean airborne = FlightLeft.airborne(t.armed(), t.hasHome() ? t.homeHeight() : 0.0, t.speed(), t.verticalSpeed());
        flightClass.ifPresent(fc -> this.flightLeft.sample(fc, time, t.batteryCharge(), airborne));
        double horizontal = horizontalSpeed(t);
        double left = flightClass.isPresent() ? this.flightLeft.remaining(t.batteryCharge(), horizontal, t.verticalSpeed()) : Double.NaN;

        boolean holding = status.filter(s -> s.stickMode() != NavLogic.StickMode.SPORT && !s.atti()
                && (s.function() == NavLogic.Function.NONE || s.function() == NavLogic.Function.HOLD)).isPresent()
                && WindWatch.centered(t.stickRoll(), t.stickPitch(), t.stickYaw()) && t.armed();
        boolean highWind = this.wind.update(time, holding, t.pitchDeg(), t.rollDeg(), horizontal);

        Optional<NavInput.Sensor> sensor = status.flatMap(NavStatus::nearestSensor);
        double distance = status.map(NavStatus::nearestDistance).orElse(Double.NaN);

        return new CamOsdState(true, status.isPresent(), flightClass, label, atti, warnings, missionState, missionIndex, missionSize,
                missionLeft, gimbalTilt(drone, status, t), sensor, distance, left, highWind, HudHooks.operatorBound(drone),
                settings.view() == DroneSettings.CameraView.HUD, hint, HudHooks.gimbalStop(drone));
    }

    private Mission mission(MissionData data) {
        if (data == null || data.isEmpty()) {
            this.missionData = data;
            this.mission = null;
            return null;
        }
        if (!data.equals(this.missionData)) {
            this.missionData = data;
            this.mission = data.mission().orElse(null);
        }
        return this.mission;
    }

    public static FlightClass byMass(double massKg) {
        if (!Double.isFinite(massKg) || massKg < NavAssist.COMPACT_MAX_KG) {
            return FlightClass.COMPACT;
        }
        return massKg < NavAssist.TOUR_MAX_KG ? FlightClass.TOUR : FlightClass.CINE;
    }

    public static Set<NavOutput.Warning> warnings(Optional<NavStatus> status, NavLogic.MissionState missionState) {
        Set<NavOutput.Warning> warnings = EnumSet.noneOf(NavOutput.Warning.class);
        status.map(NavStatus::warnings).ifPresent(warnings::addAll);
        boolean holding = status.filter(s -> s.function() == NavLogic.Function.HOLD).isPresent();
        if (missionState == NavLogic.MissionState.PAUSED || holding) {
            warnings.add(NavOutput.Warning.PAUSED);
        }
        return warnings;
    }

    public static String label(NavLogic.MissionState state, int index, int size, SmartState smart) {
        boolean paused = state == NavLogic.MissionState.PAUSED && smart.function() == SmartState.Function.NONE;
        if ((state == NavLogic.MissionState.RUNNING || paused) && size > 0) {
            return HudText.mission(index, size, Double.NaN);
        }
        return switch (smart.function()) {
            case HOLD -> HoldController.LABEL;
            case FOLLOW -> FollowController.LABEL;
            case POI -> OrbitController.LABEL;
            case COURSE_LOCK -> CourseLock.LABEL;
            case AUTO_SHOT -> AutoShots.LABEL;
            case NONE -> "";
        };
    }

    public static double horizontalSpeed(FlightTelemetry t) {
        double speed = t.speed();
        double vertical = t.verticalSpeed();
        if (!Double.isFinite(speed)) {
            return Double.NaN;
        }
        if (!Double.isFinite(vertical)) {
            return Math.abs(speed);
        }
        return Math.sqrt(Math.max(0.0, speed * speed - vertical * vertical));
    }

    private static double gimbalTilt(Entity drone, Optional<NavStatus> status, FlightTelemetry t) {
        double hooked = HudHooks.gimbalTilt(drone);
        if (Double.isFinite(hooked)) {
            return hooked;
        }
        Optional<CameraPose> pose = PropwashApi.drones().cameraPose(drone);
        if (pose.isPresent()) {
            Vec3 look = pose.get().look();
            double length = look.length();
            if (length > 1.0E-6) {
                return Math.toDegrees(Math.asin(Math.clamp(look.y / length, -1.0, 1.0)));
            }
        }
        if (status.isPresent()) {
            NavStatus s = status.get();
            if (Double.isFinite(s.gimbalTilt())) {
                return s.gimbalTilt();
            }
            if (s.gimbalTarget().isPresent()) {
                NavTarget target = s.gimbalTarget().get();
                return GimbalAim.tiltTo(target.x() - t.x(), target.y() - t.y(), target.z() - t.z());
            }
        }
        return Double.NaN;
    }
}
