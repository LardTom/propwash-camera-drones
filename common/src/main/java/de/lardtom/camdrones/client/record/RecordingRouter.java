package de.lardtom.camdrones.client.record;

import de.lardtom.camdrones.award.client.VideoAwards;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.gimbal.model.MissionShots;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.client.record.model.RecordingRoute;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RecordingRouter {
    public static final int FORGET_TICKS = 20 * 30;

    private static final Map<Integer, Background> BACKGROUND = new HashMap<>();
    private static boolean initialized;
    private static int hints;
    private static int ticks;

    private RecordingRouter() {
    }

    private static final class Background {
        final MissionShots shots = new MissionShots();
        int lastSeen;
        int hintEpoch = -1;
        boolean camera;
        boolean running;
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerFlightSchoolScene(RecordingScene::create);
        PropwashClientApi.linkFlightSchoolItems(RecordingScene.ID, RecordingScene.PLANNER);
    }

    public static RecordingMode chosen() {
        return CamClientConfig.get().recordingMode();
    }

    public static boolean flashbackReady() {
        return FlashbackLink.ready();
    }

    public static RecordingMode effective() {
        return chosen().effective(flashbackReady());
    }

    public static RecordingRoute route(boolean liveView) {
        return RecordingRoute.of(chosen(), flashbackReady(), liveView);
    }

    public static int hints() {
        return hints;
    }

    public static Set<Integer> background() {
        return Set.copyOf(BACKGROUND.keySet());
    }

    public static CameraActions.Camera camera(Entity drone, boolean liveView, CameraActions.Camera direct) {
        RecordingRoute route = route(liveView);
        return switch (route) {
            case DIRECT -> direct;
            case FLASHBACK -> flashback(drone, direct);
            case NEEDS_LIVE_VIEW -> needsLiveView(direct);
        };
    }

    public static CameraActions.Camera flashback(Entity drone, CameraActions.Camera direct) {
        GimbalPathRecorder recorder = GimbalPathRecorder.INSTANCE;
        return new CameraActions.Camera() {
            @Override
            public boolean recording() {
                return recorder.recording(drone.getId());
            }

            @Override
            public boolean start() {
                return recorder.start(Minecraft.getInstance(), drone) == GimbalPathRecorder.Result.STARTED;
            }

            @Override
            public void stop() {
                recorder.stop(Minecraft.getInstance(), drone.getId());
            }

            @Override
            public boolean photo() {
                return recorder.photo(Minecraft.getInstance(), drone);
            }

            @Override
            public void leave() {
                direct.leave();
            }
        };
    }

    private static CameraActions.Camera needsLiveView(CameraActions.Camera direct) {
        return new CameraActions.Camera() {
            @Override
            public boolean recording() {
                return false;
            }

            @Override
            public boolean start() {
                hint(Minecraft.getInstance());
                return false;
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean photo() {
                hint(Minecraft.getInstance());
                return false;
            }

            @Override
            public void leave() {
                direct.leave();
            }
        };
    }

    public static void hint(Minecraft minecraft) {
        hints++;
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(RecordTexts.NEEDS_LIVE_VIEW).withStyle(ChatFormatting.GOLD));
            minecraft.player.sendSystemMessage(Component.translatable(RecordTexts.NEEDS_LIVE_VIEW).withStyle(ChatFormatting.GOLD));
        }
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        GimbalPathRecorder recorder = GimbalPathRecorder.INSTANCE;
        if (minecraft.level == null || minecraft.player == null) {
            BACKGROUND.clear();
            recorder.tick(minecraft, false);
            recorder.chunks().releaseAll();
            VideoAwards.tick(minecraft);
            return;
        }
        boolean needed = backgroundMissions(minecraft, recorder);
        recorder.tick(minecraft, needed);
        recorder.flushChunks();
        VideoAwards.tick(minecraft);
    }

    private static boolean backgroundMissions(Minecraft minecraft, GimbalPathRecorder recorder) {
        if (!CamDroneData.registered()) {
            return false;
        }
        Set<UUID> mine = tablets(minecraft.player);
        Set<Integer> seen = new HashSet<>();
        boolean needed = false;
        if (!mine.isEmpty()) {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (!CrewDrones.hasGimbal(entity) || !mine.contains(serial(entity))) {
                    continue;
                }
                seen.add(entity.getId());
                needed |= background(minecraft, recorder, entity);
            }
        }
        for (Map.Entry<Integer, Background> entry : List.copyOf(BACKGROUND.entrySet())) {
            int id = entry.getKey();
            if (seen.contains(id)) {
                continue;
            }
            Background state = entry.getValue();
            boolean waiting = state.running && state.camera && effective() == RecordingMode.FLASHBACK && ticks - state.lastSeen < FORGET_TICKS;
            if (waiting) {
                recorder.chunks().want(id);
                needed = true;
            } else {
                BACKGROUND.remove(id);
                if (recorder.recording(id)) {
                    recorder.stop(minecraft, id);
                }
            }
        }
        return needed;
    }

    private static boolean background(Minecraft minecraft, GimbalPathRecorder recorder, Entity drone) {
        Background state = BACKGROUND.computeIfAbsent(drone.getId(), id -> new Background());
        state.lastSeen = ticks;
        Optional<Mission> mission;
        MissionStatus status;
        try {
            MissionData packed = PropwashApi.droneData().get(drone, CamDroneData.mission());
            mission = packed == null ? Optional.empty() : packed.mission();
            status = PropwashApi.droneData().get(drone, CamDroneData.missionState());
        } catch (RuntimeException e) {
            return false;
        }
        boolean running = status != null && (status.state() == NavLogic.MissionState.RUNNING || status.state() == NavLogic.MissionState.PAUSED);
        boolean camera = mission.isPresent() && RecordingRoute.films(mission.get());
        state.running = running;
        state.camera = camera;
        if (status == null || liveView(minecraft, drone)) {
            return false;
        }
        RecordingRoute route = route(false);
        List<Waypoint.Action> actions = state.shots.update(mission, status.state(), status.index(), status.epoch(), drone.getX(), drone.getY(),
                drone.getZ());
        if (running && camera) {
            if (route == RecordingRoute.NEEDS_LIVE_VIEW && state.hintEpoch != status.epoch()) {
                state.hintEpoch = status.epoch();
                hint(minecraft);
            }
            if (route == RecordingRoute.FLASHBACK) {
                recorder.chunkWanted(minecraft, drone, drone.getId());
            }
        }
        for (Waypoint.Action action : actions) {
            act(minecraft, recorder, drone, state, status, action);
        }
        if (!running && recorder.recording(drone.getId())) {
            recorder.stop(minecraft, drone.getId());
        }
        return running && camera && route == RecordingRoute.FLASHBACK;
    }

    private static void act(Minecraft minecraft, GimbalPathRecorder recorder, Entity drone, Background state, MissionStatus status,
                            Waypoint.Action action) {
        RecordingRoute route = route(false);
        if (route == RecordingRoute.FLASHBACK) {
            CameraActions.waypoint(action, flashback(drone, GimbalClient.directCamera(drone)));
        } else if (route == RecordingRoute.NEEDS_LIVE_VIEW && RecordingRoute.films(action) && action != Waypoint.Action.RECORD_STOP
                && state.hintEpoch != status.epoch()) {
            state.hintEpoch = status.epoch();
            hint(minecraft);
        }
    }

    public static boolean liveView(Minecraft minecraft, Entity drone) {
        if (minecraft.getCameraEntity() == drone) {
            return true;
        }
        ClientInput input = PropwashClientApi.input();
        if (input.flying() && input.drone().filter(flown -> flown == drone).isPresent()) {
            return true;
        }
        GimbalControl control = GimbalClient.control();
        return control.role() != GimbalControl.Role.NONE && control.droneId() == drone.getId();
    }

    private static UUID serial(Entity drone) {
        try {
            UUID serial = PropwashApi.droneData().get(drone, CamDroneData.serial());
            return serial == null ? CamDroneData.NO_SERIAL : serial;
        } catch (RuntimeException e) {
            return CamDroneData.NO_SERIAL;
        }
    }

    private static Set<UUID> tablets(LocalPlayer player) {
        Set<UUID> out = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                PlannerTablets.link(stack).map(PlannerLink::serial).filter(serial -> !CamDroneData.NO_SERIAL.equals(serial)).ifPresent(out::add);
            }
        }
        return out;
    }
}
