package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.gimbal.model.GimbalServoTone;
import de.lardtom.camdrones.client.gimbal.model.MissionShots;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.client.gimbal.model.PilotView;
import de.lardtom.camdrones.client.gimbal.model.ShotSource;
import de.lardtom.camdrones.client.gimbal.model.SoloMission;
import de.lardtom.camdrones.client.hud.HudHooks;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.record.RecordingRouter;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.client.CrewClient;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamSwitches;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.RecordingControl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class GimbalClient {
    public static final String PROVIDER_ID = CamDrones.MOD_ID + ":gimbal";
    public static final SoundEvent CREW_REQUEST = SoundEvent.createVariableRangeEvent(CamDrones.id("crew.request"));
    public static final SoundEvent CALIBRATE = SoundEvent.createVariableRangeEvent(CamDrones.id("gimbal.calibrate"));
    public static final long SPECTATE_DELAY_MILLIS = 1_000L;
    public static final int PRUNE_TICKS = 100;

    private static final GimbalViews VIEWS = new GimbalViews();
    private static final GimbalControl CONTROL = new GimbalControl();
    private static final PilotView PILOT_VIEW = new PilotView();
    private static final CamGimbal PROVIDER = new CamGimbal(VIEWS, CONTROL, PILOT_VIEW);
    private static final GimbalServoTone SERVO = new GimbalServoTone();
    private static boolean initialized;
    private static int pendingSpectate = -1;
    private static long spectateAt;
    private static int ticks;
    private static int shotDrone = -1;
    private static MissionShots shots = new MissionShots();

    private GimbalClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerGimbal(PROVIDER_ID, PROVIDER);
        HudHooks.gimbalTilt(GimbalClient::tilt);
        HudHooks.operatorBound(GimbalClient::operated);
        HudHooks.gimbalStop(GimbalClient::atStop);
        CamNetwork.onClient(CrewPackets.NOTICE, GimbalClient::notice);
        CrewClient.init();
        RecordingRouter.init();
        CamDrones.LOG.info("{}: Gimbal {} am Client angemeldet", CamDrones.MOD_NAME, PROVIDER_ID);
    }

    public static CamGimbal provider() {
        return PROVIDER;
    }

    public static GimbalControl control() {
        return CONTROL;
    }

    public static GimbalViews views() {
        return VIEWS;
    }

    public static PilotView pilotView() {
        return PILOT_VIEW;
    }

    public static double tilt(Entity drone) {
        if (drone == null) {
            return Double.NaN;
        }
        GimbalViews.View view = VIEWS.peek(drone.getId());
        if (view != null && view.fresh(System.nanoTime()) && Double.isFinite(view.tilt())) {
            return view.tilt();
        }
        Optional<GimbalLimits> limits = GimbalScenes.limits(drone);
        if (limits.isEmpty()) {
            return Double.NaN;
        }
        GimbalSetting manual = CONTROL.local(drone.getId(), System.nanoTime()).orElseGet(() -> CrewData.get(drone).setting());
        return GimbalResolver.resolve(GimbalScenes.scene(drone, limits.get(), GimbalScenes.heading(drone)), manual).tilt();
    }

    public static boolean operated(Entity drone) {
        return drone != null && CrewData.get(drone).operated();
    }

    public static boolean atStop(Entity drone) {
        if (drone == null || CONTROL.role() != GimbalControl.Role.OPERATOR || CONTROL.droneId() != drone.getId()) {
            return false;
        }
        GimbalViews.View view = VIEWS.peek(drone.getId());
        return view != null && view.freshCamera(System.nanoTime()) && view.atStop();
    }

    public static void tick(Minecraft minecraft) {
        RecordingRouter.tick(minecraft);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            CONTROL.release();
            VIEWS.clear();
            SERVO.reset();
            PILOT_VIEW.reset();
            pendingSpectate = -1;
            return;
        }
        long nowNanos = System.nanoTime();
        long nowMillis = System.currentTimeMillis();
        if (++ticks % PRUNE_TICKS == 0) {
            VIEWS.prune(nowNanos, id -> level.getEntity(id) != null);
        }
        pilotView(minecraft);
        spectateIfNeeded(level, nowMillis);
        if (CONTROL.role() == GimbalControl.Role.NONE) {
            SERVO.reset();
            watchedShots(minecraft);
            return;
        }
        Entity drone = level.getEntity(CONTROL.droneId());
        if (drone == null || !CONTROL.active(nowNanos)) {
            CONTROL.release();
            SERVO.reset();
            return;
        }
        if (SERVO.update(drone.getId(), CONTROL.role() == GimbalControl.Role.OPERATOR, CONTROL.drainRecentered(), ticks)) {
            servo(minecraft, drone);
        }
        GimbalData data = CrewData.get(drone);
        CameraActions.Camera camera = camera(drone);
        for (OperatorGimbalInput.Event event : CONTROL.drainEvents()) {
            if (event == OperatorGimbalInput.Event.MISSION || event == OperatorGimbalInput.Event.ABORT) {
                if (CONTROL.solo()) {
                    mission(drone, event);
                }
                continue;
            }
            feedback(minecraft, CameraActions.operator(event, camera));
        }
        CONTROL.due(data.setting(), nowMillis).ifPresent(setting -> CamNetwork.sendToServer(new CrewPackets.Gimbal(drone.getId(), setting)));
        shots(minecraft, drone, camera, ShotSource.CONTROL);
    }

    static void mission(Entity drone, OperatorGimbalInput.Event event) {
        if (!CamDroneData.registered()) {
            return;
        }
        MissionStatus status;
        try {
            status = PropwashApi.droneData().get(drone, CamDroneData.missionState());
        } catch (RuntimeException e) {
            return;
        }
        UUID serial = PlannerTablets.serialOf(drone);
        if (CamDroneData.NO_SERIAL.equals(serial)) {
            return;
        }
        SoloMission.command(event, status == null ? null : status.state()).ifPresent(command -> CamNetwork.sendToServer(MissionCommand.Request.of(serial,
                switch (command) {
                    case PAUSE -> MissionCommand.Command.PAUSE;
                    case RESUME -> MissionCommand.Command.RESUME;
                    case STOP -> MissionCommand.Command.STOP;
                })));
    }

    private static void pilotView(Minecraft minecraft) {
        ClientInput input = PropwashClientApi.input();
        Entity flown = input.flying() ? input.drone().filter(CrewDrones::hasGimbal).orElse(null) : null;
        if (flown == null) {
            PILOT_VIEW.reset();
            return;
        }
        PilotView.Event event = PILOT_VIEW.update(flown.getId(), CamSwitches.active(flown, CamControl.PILOT_VIEW), CrewData.get(flown).operated(),
                CamGimbal.recording());
        String key = switch (event) {
            case FPV -> CrewTexts.VIEW_FPV;
            case GIMBAL -> CrewTexts.VIEW_GIMBAL;
            case OPERATOR -> CrewTexts.VIEW_OPERATOR;
            case RECORDING -> CrewTexts.VIEW_RECORDING;
            case NONE -> null;
        };
        if (key != null && minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.WHITE));
        }
    }

    private static void watchedShots(Minecraft minecraft) {
        Entity watched = minecraft.getCameraEntity();
        boolean cameraDrone = watched != null && watched != minecraft.player && CrewDrones.hasGimbal(watched);
        ShotSource source = ShotSource.of(false, cameraDrone, PropwashClientApi.input().flying());
        if (source.takesShots()) {
            shots(minecraft, watched, camera(watched), source);
        }
    }

    private static void shots(Minecraft minecraft, Entity drone, CameraActions.Camera camera, ShotSource source) {
        if (!CamDroneData.registered()) {
            return;
        }
        if (shotDrone != drone.getId()) {
            shotDrone = drone.getId();
            shots = new MissionShots();
        }
        Optional<Mission> mission;
        MissionStatus status;
        try {
            MissionData packed = PropwashApi.droneData().get(drone, CamDroneData.mission());
            mission = packed == null ? Optional.empty() : packed.mission();
            status = PropwashApi.droneData().get(drone, CamDroneData.missionState());
        } catch (RuntimeException e) {
            return;
        }
        if (status == null) {
            return;
        }
        List<Waypoint.Action> actions = shots.update(mission, status.state(), status.index(), status.epoch(), drone.getX(), drone.getY(),
                drone.getZ());
        for (Waypoint.Action action : actions) {
            CameraActions.Done done = CameraActions.waypoint(action, camera);
            if (source.shows(done)) {
                feedback(minecraft, done);
            }
        }
    }

    private static void spectateIfNeeded(ClientLevel level, long nowMillis) {
        if (pendingSpectate < 0 || nowMillis < spectateAt) {
            return;
        }
        int id = pendingSpectate;
        pendingSpectate = -1;
        Entity drone = level.getEntity(id);
        if (drone != null && !PropwashClientApi.input().operating()) {
            PropwashClientApi.spectate(drone);
        }
    }

    public static float servoVolume() {
        return (float) CamClientConfig.get().servoVolume();
    }

    private static void servo(Minecraft minecraft, Entity drone) {
        float volume = servoVolume();
        if (!GimbalServoTone.audible(volume) || minecraft.getSoundManager().getSoundEvent(CALIBRATE.location()) == null) {
            return;
        }
        minecraft.getSoundManager().play(new EntityBoundSoundInstance(CALIBRATE, SoundSource.PLAYERS, volume, 1.0F, drone, drone.getRandom().nextLong()));
    }

    static void notice(CrewPackets.Notice notice) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (notice.kind()) {
            case INVITED -> {
                if (minecraft.getSoundManager().getSoundEvent(CREW_REQUEST.location()) != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(CREW_REQUEST, 1.0F, 0.8F));
                }
            }
            case BOUND -> {
                pendingSpectate = notice.drone();
                spectateAt = System.currentTimeMillis() + SPECTATE_DELAY_MILLIS;
            }
            case RELEASED -> {
                pendingSpectate = -1;
                if (CONTROL.role() == GimbalControl.Role.OPERATOR) {
                    CONTROL.release();
                }
            }
            case SOLO_REFUSED -> CrewClient.soloRefused(notice.drone());
        }
    }

    public static CameraActions.Camera camera(Entity drone) {
        return RecordingRouter.camera(drone, true, directCamera(drone));
    }

    public static CameraActions.Camera directCamera(Entity drone) {
        RecordingControl recording = PropwashClientApi.recording();
        return new CameraActions.Camera() {
            @Override
            public boolean recording() {
                return recording.recording();
            }

            @Override
            public boolean start() {
                return recording.start(drone);
            }

            @Override
            public void stop() {
                recording.stop();
            }

            @Override
            public boolean photo() {
                boolean taken = recording.photo(drone);
                if (taken) {
                    PILOT_VIEW.photo(drone.getId(), System.nanoTime());
                }
                return taken;
            }

            @Override
            public void leave() {
                CamNetwork.sendToServer(CrewPackets.Request.of(CrewPackets.Action.LEAVE, drone.getId()));
            }
        };
    }

    private static void feedback(Minecraft minecraft, CameraActions.Done done) {
        if (minecraft.player == null) {
            return;
        }
        String key = switch (done) {
            case STARTED -> CrewTexts.CAMERA_RECORDING;
            case STOPPED -> CrewTexts.CAMERA_STOPPED;
            case PHOTO -> CrewTexts.CAMERA_PHOTO;
            case REFUSED -> CrewTexts.CAMERA_REFUSED;
            default -> null;
        };
        if (key != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(key).withStyle(done == CameraActions.Done.REFUSED ? ChatFormatting.RED
                    : ChatFormatting.WHITE));
        }
    }
}
