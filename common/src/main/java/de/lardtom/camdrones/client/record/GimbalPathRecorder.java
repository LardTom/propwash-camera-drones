package de.lardtom.camdrones.client.record;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalPose;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.record.model.CameraAngles;
import de.lardtom.camdrones.client.record.model.PathSample;
import de.lardtom.camdrones.client.record.model.PathThinner;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.GimbalModel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GimbalPathRecorder {
    public static final GimbalPathRecorder INSTANCE = new GimbalPathRecorder();
    public static final int TRACK_COLOUR = 0x4FC3F7;
    public static final int PHOTO_COLOUR = 0xFFFFFF;
    public static final double EXPORT_ASPECT = 16.0 / 9.0;
    public static final double STEP_SECONDS = 0.05;
    public static final int MAX_TICKS = 20 * 60 * 30;
    public static final int RELEASE_GRACE_TICKS = 40;
    public static final int SETTLE_STEPS = 60;
    public static final int PHOTO_WAIT_TICKS = 100;

    private final Map<Integer, Take> takes = new LinkedHashMap<>();
    private final Map<Integer, GimbalModel> models = new LinkedHashMap<>();
    private final Map<Integer, Integer> pendingPhotos = new LinkedHashMap<>();
    private int ticks;
    private final CaptureChunks chunks = new CaptureChunks();
    private boolean startedFlashback;
    private int idleTicks;
    private int recordings;
    private int photos;
    private Handover lastHandover;
    private Result lastResult = Result.NONE;

    private GimbalPathRecorder() {
    }

    public enum Result {
        NONE,
        STARTED,
        ALREADY,
        NO_GIMBAL,
        NOT_READY,
        IN_REPLAY,
        REFUSED
    }

    public record Handover(String scene, int droneId, int samples, int keyframes, int fovKeyframes, int startTick, int endTick, boolean photo,
                           UUID replay, int sceneIndex, boolean far) {
    }

    private static final class Take {
        final int droneId;
        final String name;
        final int number;
        final Object recorder;
        final UUID replay;
        final List<PathSample> samples = new ArrayList<>();
        PathSample last;
        boolean far;

        Take(int droneId, String name, int number, Object recorder, UUID replay) {
            this.droneId = droneId;
            this.name = name;
            this.number = number;
            this.recorder = recorder;
            this.replay = replay;
        }
    }

    public boolean recording(int droneId) {
        return this.takes.containsKey(droneId);
    }

    public boolean recordingAny() {
        return !this.takes.isEmpty();
    }

    public int samples(int droneId) {
        Take take = this.takes.get(droneId);
        return take == null ? 0 : take.samples.size();
    }

    public Optional<Handover> lastHandover() {
        return Optional.ofNullable(this.lastHandover);
    }

    public Result lastResult() {
        return this.lastResult;
    }

    public CaptureChunks chunks() {
        return this.chunks;
    }

    public int photos() {
        return this.photos;
    }

    public int recordings() {
        return this.recordings;
    }

    public boolean startedFlashback() {
        return this.startedFlashback;
    }

    public Result start(Minecraft minecraft, Entity drone) {
        this.lastResult = this.tryStart(minecraft, drone);
        return this.lastResult;
    }

    private Result tryStart(Minecraft minecraft, Entity drone) {
        if (drone == null || !CrewDrones.hasGimbal(drone)) {
            return Result.NO_GIMBAL;
        }
        if (this.takes.containsKey(drone.getId())) {
            return Result.ALREADY;
        }
        if (!this.ensureFlashback()) {
            return FlashbackLink.ready() && FlashbackLink.inReplay() ? Result.IN_REPLAY : FlashbackLink.ready() ? Result.REFUSED : Result.NOT_READY;
        }
        Object recorder = FlashbackLink.recorder();
        UUID replay = recorder == null ? null : FlashbackLink.replayId(recorder);
        if (recorder == null || replay == null) {
            return Result.REFUSED;
        }
        this.recordings++;
        Take take = new Take(drone.getId(), name(drone), this.recordings, recorder, replay);
        this.takes.put(drone.getId(), take);
        this.models.remove(drone.getId());
        FlashbackLink.addMarker(TRACK_COLOUR, Component.translatable(RecordTexts.MARKER, take.number).getString());
        this.sample(minecraft, take, drone);
        notify(minecraft, Component.translatable(RecordTexts.STARTED), false);
        return Result.STARTED;
    }

    private boolean ensureFlashback() {
        if (!FlashbackLink.ready() || FlashbackLink.inReplay()) {
            return false;
        }
        if (FlashbackLink.recording()) {
            return true;
        }
        if (!FlashbackLink.startRecording()) {
            return false;
        }
        this.startedFlashback = true;
        return true;
    }

    public boolean photo(Minecraft minecraft, Entity drone) {
        if (drone == null || !CrewDrones.hasGimbal(drone) || !this.ensureFlashback()) {
            this.lastResult = drone == null || !CrewDrones.hasGimbal(drone) ? Result.NO_GIMBAL : Result.NOT_READY;
            return false;
        }
        this.pendingPhotos.putIfAbsent(drone.getId(), this.ticks);
        this.idleTicks = 0;
        this.tryPhotos(minecraft);
        return true;
    }

    public boolean stop(Minecraft minecraft, int droneId) {
        Take take = this.takes.remove(droneId);
        if (take == null) {
            return false;
        }
        this.handOver(minecraft, take);
        return true;
    }

    public void stopAll(Minecraft minecraft) {
        for (Integer id : List.copyOf(this.takes.keySet())) {
            this.stop(minecraft, id);
        }
        this.pendingPhotos.clear();
        this.chunks.releaseAll();
    }

    public void tick(Minecraft minecraft, boolean needed) {
        this.ticks++;
        if (minecraft.level == null) {
            if (!this.takes.isEmpty()) {
                this.stopAll(minecraft);
            }
            this.models.clear();
            this.pendingPhotos.clear();
            this.startedFlashback = false;
            return;
        }
        Object current = FlashbackLink.recorder();
        for (Take take : List.copyOf(this.takes.values())) {
            if (current != take.recorder) {
                this.takes.remove(take.droneId);
                this.handOver(minecraft, take);
                continue;
            }
            Entity drone = minecraft.level.getEntity(take.droneId);
            if (this.chunkWanted(minecraft, drone, take.droneId)) {
                take.far = true;
            }
            this.sample(minecraft, take, drone);
            if (!take.samples.isEmpty() && take.samples.getLast().tick() - take.samples.getFirst().tick() >= MAX_TICKS) {
                this.stop(minecraft, take.droneId);
            }
        }
        this.tryPhotos(minecraft);
        this.pendingPhotos.values().removeIf(since -> this.ticks - since > PHOTO_WAIT_TICKS);
        this.models.keySet().removeIf(id -> !this.takes.containsKey(id) && !this.pendingPhotos.containsKey(id));
        if (this.startedFlashback && this.takes.isEmpty() && this.pendingPhotos.isEmpty() && !needed) {
            if (++this.idleTicks >= RELEASE_GRACE_TICKS) {
                this.idleTicks = 0;
                this.startedFlashback = false;
                if (FlashbackLink.recording()) {
                    FlashbackLink.finishRecording();
                }
            }
        } else {
            this.idleTicks = 0;
        }
        if (!FlashbackLink.recording()) {
            this.startedFlashback = false;
        }
    }

    public boolean chunkWanted(Minecraft minecraft, Entity drone, int droneId) {
        if (CaptureChunks.far(minecraft, drone, this.chunks.requested(droneId))) {
            this.chunks.want(droneId);
            return true;
        }
        return false;
    }

    public void flushChunks() {
        this.chunks.flush();
    }

    private void tryPhotos(Minecraft minecraft) {
        if (this.pendingPhotos.isEmpty() || minecraft.level == null || !FlashbackLink.writing()) {
            return;
        }
        Object recorder = FlashbackLink.recorder();
        UUID replay = recorder == null ? null : FlashbackLink.replayId(recorder);
        if (recorder == null || replay == null) {
            return;
        }
        for (Integer id : List.copyOf(this.pendingPhotos.keySet())) {
            Entity drone = minecraft.level.getEntity(id);
            if (drone == null) {
                continue;
            }
            PathSample shot = this.pose(drone, FlashbackLink.writtenTicks(recorder), null);
            if (shot == null) {
                continue;
            }
            this.pendingPhotos.remove(id);
            this.photos++;
            String scene = Component.translatable(RecordTexts.SCENE_PHOTO, name(drone), this.photos).getString();
            FlashbackLink.addMarker(PHOTO_COLOUR, Component.translatable(RecordTexts.MARKER_PHOTO, this.photos).getString());
            int index = FlashbackLink.writeScene(replay, scene(scene, List.of(shot), List.of(shot), shot.tick(), shot.tick() + 1));
            this.lastHandover = new Handover(scene, id, 1, 1, 1, shot.tick(), shot.tick() + 1, true, replay, index,
                    CaptureChunks.far(minecraft, drone, false));
            notify(minecraft, Component.translatable(RecordTexts.PHOTO, scene), false);
        }
    }

    private void sample(Minecraft minecraft, Take take, Entity drone) {
        if (drone == null || minecraft.isPaused() || !FlashbackLink.writing()) {
            return;
        }
        int tick = FlashbackLink.writtenTicks(take.recorder);
        if (tick < 0 || take.last != null && tick <= take.last.tick()) {
            return;
        }
        PathSample sample = this.pose(drone, tick, take.last);
        if (sample != null) {
            take.samples.add(sample);
            take.last = sample;
        }
    }

    private PathSample pose(Entity drone, int tick, PathSample previous) {
        Optional<GimbalLimits> limits = GimbalScenes.limits(drone);
        if (limits.isEmpty()) {
            return null;
        }
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        Vec3 center = view.map(DroneView::center).orElse(drone.position());
        Quaternionf frame = view.map(v -> CameraAngles.frame(v.forward().x, v.forward().y, v.forward().z, v.up().x, v.up().y, v.up().z,
                new Quaternionf())).orElseGet(() -> new Quaternionf().rotationY((float) Math.toRadians(-drone.getYRot())));
        double heading = GimbalPose.heading(frame);
        long now = System.nanoTime();
        GimbalSetting setting = GimbalClient.control().local(drone.getId(), now).orElseGet(() -> CrewData.get(drone).setting());
        GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalScenes.scene(drone, limits.get(), heading), setting);
        boolean fresh = !this.models.containsKey(drone.getId());
        GimbalModel model = this.models.computeIfAbsent(drone.getId(), id -> GimbalPose.create(limits.get()));
        GimbalPose.aim(model, aim, heading);
        for (int i = fresh ? SETTLE_STEPS : 1; i > 0; i--) {
            GimbalPose.step(model, STEP_SECONDS, frame);
        }
        Quaternionf rotation = GimbalPose.rotation(model, new Quaternionf());
        CameraAngles angles = CameraAngles.of(new Quaterniond(rotation.x, rotation.y, rotation.z, rotation.w));
        double fov = CameraAngles.verticalFov(GimbalClient.provider().gimbalFov(drone), EXPORT_ASPECT);
        PathSample sample = new PathSample(tick, center.x, center.y, center.z, angles.yaw(), angles.pitch(), angles.roll(), fov);
        return sample.finite() ? sample.continuing(previous) : null;
    }

    private void handOver(Minecraft minecraft, Take take) {
        this.chunks.flush();
        if (take.samples.isEmpty()) {
            this.lastHandover = new Handover("", take.droneId, 0, 0, 0, 0, 0, false, take.replay, -1, take.far);
            notify(minecraft, Component.translatable(RecordTexts.EMPTY), true);
            return;
        }
        List<PathSample> camera = PathThinner.camera(take.samples);
        List<PathSample> fov = PathThinner.fov(take.samples);
        int start = take.samples.getFirst().tick();
        int end = Math.max(start + 1, take.samples.getLast().tick());
        String scene = Component.translatable(RecordTexts.SCENE, take.name, take.number).getString();
        int index = FlashbackLink.writeScene(take.replay, scene(scene, camera, fov, start, end));
        this.lastHandover = new Handover(scene, take.droneId, take.samples.size(), camera.size(), fov.size(), start, end, false, take.replay, index,
                take.far);
        CamDrones.LOG.info("{}: Gimbal-Kamerapfad an Flashback übergeben: {} ({} Proben, {} Keyframes, Szene {})", CamDrones.MOD_NAME, scene,
                take.samples.size(), camera.size(), index);
        if (index < 0) {
            notify(minecraft, Component.translatable(RecordTexts.REFUSED), true);
            return;
        }
        notify(minecraft, Component.translatable(RecordTexts.HANDED_OVER, scene, camera.size()), false);
    }

    private static FlashbackLink.Scene scene(String name, List<PathSample> camera, List<PathSample> fov, int start, int end) {
        return new FlashbackLink.Scene(name, Component.translatable(RecordTexts.TRACK_CAMERA).getString(),
                Component.translatable(RecordTexts.TRACK_FOV).getString(), TRACK_COLOUR, Avatar.DEFAULT_EYE_HEIGHT, camera, fov, start, end);
    }

    private static String name(Entity drone) {
        Component custom = drone.getCustomName();
        if (custom != null && !custom.getString().isBlank()) {
            return custom.getString();
        }
        return Component.translatable(RecordTexts.DRONE).getString();
    }

    private static void notify(Minecraft minecraft, Component text, boolean problem) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(text.copy().withStyle(problem ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
    }
}
