package de.lardtom.camdrones.client.record;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.record.model.PathSample;
import de.lardtom.camdrones.platform.Services;
import org.joml.Vector3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FlashbackLink {
    public static final String MOD_ID = "flashback";
    private static final String ROOT = "com.moulberry.flashback.";

    private static Status status;
    private static String problem;
    private static Handles handles;

    private FlashbackLink() {
    }

    public enum Status {
        ABSENT,
        READY,
        INCOMPATIBLE
    }

    public record Scene(String name, String cameraTrack, String fovTrack, int colour, double eyeHeight, List<PathSample> camera,
                        List<PathSample> fov, int startTick, int endTick) {
    }

    public static boolean installed() {
        return Services.PLATFORM.isModLoaded(MOD_ID);
    }

    public static synchronized Status status() {
        if (status == null) {
            status = resolve();
        }
        return status;
    }

    public static boolean ready() {
        return status() == Status.READY;
    }

    public static synchronized String problem() {
        status();
        return problem;
    }

    public static Optional<String> version() {
        return installed() ? Services.PLATFORM.modVersion(MOD_ID) : Optional.empty();
    }

    private static Status resolve() {
        if (!installed()) {
            problem = null;
            return Status.ABSENT;
        }
        try {
            handles = Handles.load();
            problem = null;
            CamDrones.LOG.info("{}: Flashback {} erkannt, Flashback-Aufnahme des Gimbals bereit", CamDrones.MOD_NAME, version().orElse("?"));
            return Status.READY;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            problem = e.getClass().getSimpleName() + ": " + e.getMessage();
            CamDrones.LOG.warn("{}: diese Flashback-Version wird nicht unterstützt ({})", CamDrones.MOD_NAME, problem);
            return Status.INCOMPATIBLE;
        }
    }

    private static synchronized void broken(Throwable e) {
        problem = e.getClass().getSimpleName() + ": " + e.getMessage();
        status = Status.INCOMPATIBLE;
        CamDrones.LOG.warn("{}: Zugriff auf Flashback fehlgeschlagen ({})", CamDrones.MOD_NAME, problem);
    }

    private static Handles handles() {
        Handles h = handles;
        if (h == null || status() != Status.READY) {
            throw new IllegalStateException("Flashback not ready");
        }
        return h;
    }

    public static Object recorder() {
        if (!ready()) {
            return null;
        }
        try {
            return handles().recorder.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return null;
        }
    }

    public static boolean recording() {
        return recorder() != null;
    }

    public static boolean writing() {
        Object recorder = recorder();
        if (recorder == null) {
            return false;
        }
        try {
            return (boolean) handles().readyToWrite.invoke(recorder) && !(boolean) handles().isPaused.invoke(recorder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static int writtenTicks(Object recorder) {
        try {
            return handles().writtenTicks.getInt(recorder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return -1;
        }
    }

    public static UUID replayId(Object recorder) {
        try {
            Object meta = handles().metadata.get(recorder);
            return meta == null ? null : (UUID) handles().replayIdentifier.get(meta);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return null;
        }
    }

    public static boolean inReplay() {
        if (!ready()) {
            return false;
        }
        try {
            return (boolean) handles().isInReplay.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static boolean startRecording() {
        if (!ready()) {
            return false;
        }
        try {
            handles().startRecording.invoke(null);
            return recording();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static boolean finishRecording() {
        if (!recording()) {
            return false;
        }
        try {
            handles().finishRecording.invoke(null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static boolean addMarker(int rgb, String description) {
        Object recorder = recorder();
        Handles h = handles;
        if (recorder == null || h == null || h.markerCtor == null || h.addMarker == null) {
            return false;
        }
        try {
            h.addMarker.invoke(recorder, h.markerCtor.newInstance(rgb & 0xFFFFFF, null, description));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CamDrones.LOG.debug("{}: Flashback-Marker nicht gesetzt ({})", CamDrones.MOD_NAME, e.toString());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static int writeScene(UUID replay, Scene scene) {
        Handles h = handles;
        if (h == null || replay == null || status() != Status.READY) {
            return -1;
        }
        try {
            Object state = h.editorGet.invoke(null, replay);
            Object built = h.buildScene(scene);
            long stamp = (long) h.acquireWrite.invoke(state);
            int index;
            try {
                List<Object> scenes = (List<Object>) h.getScenes.invoke(state, stamp);
                boolean replaceDefault = scenes.size() == 1 && ((List<?>) h.sceneTracks.get(scenes.getFirst())).isEmpty();
                if (replaceDefault) {
                    scenes.set(0, built);
                    index = 0;
                    h.setSceneIndex.invoke(state, 0, stamp);
                } else {
                    scenes.add(built);
                    index = scenes.size() - 1;
                }
            } finally {
                h.release.invoke(state, stamp);
            }
            h.markDirty.invoke(state);
            h.editorReset.invoke(null);
            return index;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return -1;
        }
    }

    public static int imguiColour(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static final class Handles {
        Field recorder;
        Method startRecording;
        Method finishRecording;
        Method isInReplay;
        Method isPaused;
        Method readyToWrite;
        Field writtenTicks;
        Field metadata;
        Field replayIdentifier;
        Method addMarker;
        Constructor<?> markerCtor;
        Method editorGet;
        Method editorReset;
        Method acquireWrite;
        Method getScenes;
        Method release;
        Method markDirty;
        Method setSceneIndex;
        Constructor<?> sceneCtor;
        Field sceneTracks;
        Field sceneExportStart;
        Field sceneExportEnd;
        Constructor<?> trackCtor;
        Field trackKeyframes;
        Field trackName;
        Field trackColour;
        Object cameraType;
        Object fovType;
        Constructor<?> cameraKeyframe;
        Constructor<?> fovKeyframe;
        Object smooth;
        Object hold;

        static Handles load() throws ReflectiveOperationException {
            Handles h = new Handles();
            Class<?> flashback = type("Flashback");
            Class<?> recorderType = type("record.Recorder");
            Class<?> metaType = type("record.FlashbackMeta");
            Class<?> manager = type("state.EditorStateManager");
            Class<?> state = type("state.EditorState");
            Class<?> scene = type("state.EditorScene");
            Class<?> track = type("state.KeyframeTrack");
            Class<?> keyframeType = type("keyframe.KeyframeType");
            Class<?> interpolation = type("keyframe.interpolation.InterpolationType");
            h.recorder = flashback.getField("RECORDER");
            h.startRecording = flashback.getMethod("startRecordingReplay");
            h.finishRecording = flashback.getMethod("finishRecordingReplay");
            h.isInReplay = flashback.getMethod("isInReplay");
            h.isPaused = recorderType.getMethod("isPaused");
            h.readyToWrite = recorderType.getMethod("readyToWrite");
            h.writtenTicks = open(recorderType.getDeclaredField("writtenTicks"));
            h.metadata = open(recorderType.getDeclaredField("metadata"));
            h.replayIdentifier = metaType.getField("replayIdentifier");
            try {
                Class<?> marker = type("record.ReplayMarker");
                Class<?> markerPosition = type("record.ReplayMarker$MarkerPosition");
                h.markerCtor = marker.getConstructor(int.class, markerPosition, String.class);
                h.addMarker = recorderType.getMethod("addMarker", marker);
            } catch (ReflectiveOperationException e) {
                h.markerCtor = null;
                h.addMarker = null;
            }
            h.editorGet = manager.getMethod("get", UUID.class);
            h.editorReset = manager.getMethod("reset");
            h.acquireWrite = state.getMethod("acquireWrite");
            h.getScenes = state.getMethod("getScenes", long.class);
            h.release = state.getMethod("release", long.class);
            h.markDirty = state.getMethod("markDirty");
            h.setSceneIndex = state.getMethod("setSceneIndex", int.class, long.class);
            h.sceneCtor = scene.getConstructor(String.class);
            h.sceneTracks = scene.getField("keyframeTracks");
            h.sceneExportStart = scene.getField("exportStartTicks");
            h.sceneExportEnd = scene.getField("exportEndTicks");
            h.trackCtor = track.getConstructor(keyframeType);
            h.trackKeyframes = track.getField("keyframesByTick");
            h.trackName = track.getField("customName");
            h.trackColour = track.getField("customColour");
            h.cameraType = type("keyframe.types.CameraKeyframeType").getField("INSTANCE").get(null);
            h.fovType = type("keyframe.types.FOVKeyframeType").getField("INSTANCE").get(null);
            h.cameraKeyframe = type("keyframe.impl.CameraKeyframe").getConstructor(Vector3d.class, float.class, float.class, float.class,
                    interpolation);
            h.fovKeyframe = type("keyframe.impl.FOVKeyframe").getConstructor(float.class, interpolation);
            h.smooth = interpolation.getMethod("valueOf", String.class).invoke(null, "SMOOTH");
            h.hold = interpolation.getMethod("valueOf", String.class).invoke(null, "HOLD");
            if (h.cameraType == null || h.fovType == null) {
                throw new NoSuchFieldException("keyframe types");
            }
            return h;
        }

        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(ROOT + name, false, FlashbackLink.class.getClassLoader());
        }

        private static Field open(Field field) {
            field.setAccessible(true);
            return field;
        }

        @SuppressWarnings("unchecked")
        Object buildScene(Scene scene) throws ReflectiveOperationException {
            Object built = this.sceneCtor.newInstance(scene.name());
            List<Object> tracks = (List<Object>) this.sceneTracks.get(built);
            int colour = imguiColour(scene.colour());
            Object camera = this.trackCtor.newInstance(this.cameraType);
            this.trackName.set(camera, scene.cameraTrack());
            this.trackColour.setInt(camera, colour);
            Map<Integer, Object> cameraKeys = (Map<Integer, Object>) this.trackKeyframes.get(camera);
            for (PathSample key : scene.camera()) {
                cameraKeys.put(key.tick(), this.cameraKeyframe.newInstance(new Vector3d(key.x(), key.y() - scene.eyeHeight(), key.z()),
                        (float) key.yaw(), (float) key.pitch(), (float) key.roll(), this.smooth));
            }
            tracks.add(camera);
            Object fov = this.trackCtor.newInstance(this.fovType);
            this.trackName.set(fov, scene.fovTrack());
            this.trackColour.setInt(fov, colour);
            Map<Integer, Object> fovKeys = (Map<Integer, Object>) this.trackKeyframes.get(fov);
            for (PathSample key : scene.fov()) {
                fovKeys.put(key.tick(), this.fovKeyframe.newInstance((float) key.fov(), this.hold));
            }
            tracks.add(fov);
            this.sceneExportStart.setInt(built, scene.startTick());
            this.sceneExportEnd.setInt(built, scene.endTick());
            return built;
        }
    }
}
