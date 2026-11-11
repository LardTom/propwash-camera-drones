package de.lardtom.camdrones.dev.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.model.GimbalOptics;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class FlashbackSteps {
    public static final String NAME = "aufnahme: Flashback-Kamerapfad folgt dem Gimbal, Rollwinkel < 2°";
    public static final String MOD_ID = "flashback";
    public static final Identifier CAMERA = Identifier.fromNamespaceAndPath("propwash", "action_cam_flashback");
    public static final String RECORD_KEY = "key.propwash.action_cam_record";
    public static final double ROLL_MIN = 25.0;
    public static final double PATH_ROLL_LIMIT = 2.0;
    public static final int MIN_KEYFRAMES = 10;
    public static final double TILT_TARGET = -45.0;
    public static final double PATH_PITCH_MIN = 35.0;
    public static final double EXPORT_ASPECT = 16.0 / 9.0;
    public static final double FOV_LIMIT = 1.0;
    private static final String STARTED = "flashback.started";
    private static final String MAX_ROLL = "flashback.maxRoll";
    private static final String SAVED = "flashback.saved";
    private static final String TILT_HELD = "flashback.tiltHeld";
    private static final String SAVE_WAIT = "flashback.saveWait";
    private static final String GIMBAL_FOV = "flashback.gimbalFov";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int STEP_TICKS = 20 * 240;

    private FlashbackSteps() {
    }

    public static void register() {
        if (!Services.PLATFORM.isModLoaded(MOD_ID)) {
            CamDrones.LOG.info("[Kamera-Selbsttest] {} nicht angemeldet: Flashback fehlt im mods-Ordner", NAME);
            return;
        }
        SelfTest.clientStep(26, NAME, STEP_TICKS, Script.create()
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Pilot.launch("tour mit Flashback-Kamera oben", tourWithFlashbackCamera(), -100, 80, 0.0F))
                .append(Flight.takeoff(8.0))
                .append(PilotViewSteps.fpvImage("vor dem Kamerapfad"))
                .then("Kamerapfad starten", (ctx, ticks) -> {
                    Flight.track(ctx);
                    ctx.state().put(STARTED, System.currentTimeMillis());
                    RUNNING.set(true);
                    Pilot.click(RECORD_KEY);
                    return StepResult.pass();
                })
                .then("Flashback nimmt auf", (ctx, ticks) -> {
                    Flight.track(ctx);
                    return ticks >= 60 ? StepResult.pass() : StepResult.pending();
                })
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("nach rechts rollen", FlashbackSteps::roll)
                .then("loslassen", (ctx, ticks) -> {
                    Pilot.hold(GimbalSteps.ROLL_RIGHT, false);
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake)
                .then("Gimbal nach unten neigen", FlashbackSteps::tiltDown)
                .append(PilotViewSteps.fpvImage("mit Gimbal unten"))
                .then("Kamerapfad beenden", (ctx, ticks) -> {
                    Pilot.drone().ifPresent(drone -> ctx.state().put(GIMBAL_FOV, gimbalFov(drone)));
                    RUNNING.set(false);
                    Pilot.click(RECORD_KEY);
                    return StepResult.pass();
                })
                .then("Flashback speichert, Kamerapfad auswerten", FlashbackSteps::evaluate), FlashbackSteps::cleanup);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (RUNNING.getAndSet(false)) {
            Pilot.click(RECORD_KEY);
            ctx.state().put(SAVE_WAIT, true);
        }
        if (ctx.state().containsKey(SAVE_WAIT)) {
            Screen screen = mc.gui.screen();
            if (screen != null && screen.getClass().getSimpleName().equals("SaveReplayScreen")) {
                SelfTestRunner.pressButton(screen, I18n.get("flashback.save_replay"));
                ctx.state().remove(SAVE_WAIT);
            } else if (ctx.tick() < 200) {
                return StepResult.pending();
            }
        }
        Pilot.hold(GimbalSteps.ROLL_RIGHT, false);
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        boolean centered = Math.abs(axis) < 0.02 || !PropwashClientApi.input().flying();
        Pilot.hold(tiltKey(true), !centered && axis < 0.0);
        Pilot.hold(tiltKey(false), !centered && axis > 0.0);
        if (!centered && ctx.tick() < 300) {
            return StepResult.pending();
        }
        Pilot.hold(tiltKey(true), false);
        Pilot.hold(tiltKey(false), false);
        return KeyboardDeflection.restore(ctx);
    }

    private static KeyMapping tiltKey(boolean up) {
        return (up ? CamKeys.increase(CamControl.GIMBAL_TILT) : CamKeys.decrease(CamControl.GIMBAL_TILT))
                .orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Neigung fehlt"));
    }

    private static StepResult tiltDown(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double tilt = GimbalClient.tilt(drone.get());
        boolean down = tilt <= TILT_TARGET;
        Pilot.hold(tiltKey(false), !down);
        if (down) {
            int held = (int) ctx.state().merge(TILT_HELD, 1, (a, b) -> (int) a + (int) b);
            return held >= 30 ? StepResult.pass("Gimbal auf " + Flight.format(tilt) + "° geneigt") : StepResult.pending();
        }
        return ticks > 200 ? StepResult.fail("Gimbal neigt sich nicht (" + Flight.format(tilt) + "°)") : StepResult.pending();
    }

    static double gimbalFov(Entity drone) {
        GimbalSetting setting = GimbalClient.control().local(drone.getId(), System.nanoTime()).orElseGet(() -> CrewData.get(drone).setting());
        return GimbalOptics.fov(GimbalScenes.optics(drone), setting.zoom());
    }

    static double vertical(double horizontalDegrees) {
        if (!Double.isFinite(horizontalDegrees)) {
            return Double.NaN;
        }
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(horizontalDegrees) / 2.0) / EXPORT_ASPECT));
    }

    private static DroneData tourWithFlashbackCamera() {
        DroneData data = Pilot.preset(CamPresets.TOUR).data();
        return data.withBuild(data.build().withAccessory(AccessorySlot.TOP, CAMERA));
    }

    private static StepResult roll(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Pilot.hold(GimbalSteps.ROLL_RIGHT, true);
        double tilt = GimbalSteps.frameTilt(drone.get());
        double max = Math.max((double) ctx.state().getOrDefault(MAX_ROLL, 0.0), tilt);
        ctx.state().put(MAX_ROLL, max);
        return max >= GimbalSteps.ROLL_TARGET && ticks >= 20 || ticks >= GimbalSteps.ROLL_TICKS ? StepResult.pass() : StepResult.pending();
    }

    private static Path editorStates() {
        return Services.PLATFORM.gameDir().resolve(MOD_ID).resolve("editor_states");
    }

    private static Optional<Path> newestState(long since) {
        Path dir = editorStates();
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json")).filter(p -> modified(p) >= since - 1000L)
                    .max(Comparator.comparingLong(FlashbackSteps::modified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        Screen screen = mc.gui.screen();
        if (screen != null && screen.getClass().getSimpleName().equals("SaveReplayScreen") && !state.containsKey(SAVED)) {
            if (SelfTestRunner.pressButton(screen, I18n.get("flashback.save_replay"))) {
                state.put(SAVED, true);
                ctx.log("Flashback fragt nach dem Speichern, „" + I18n.get("flashback.save_replay") + "“ gedrückt");
            }
        }
        double maxRoll = (double) state.getOrDefault(MAX_ROLL, 0.0);
        if (maxRoll < ROLL_MIN) {
            return StepResult.fail("Drohne rollt nur " + Flight.format(maxRoll) + "° (mindestens " + Flight.format(ROLL_MIN) + "°)");
        }
        Optional<Path> file = newestState((long) state.get(STARTED));
        if (file.isEmpty()) {
            return ticks > 20 * 90 ? StepResult.fail("kein Flashback-Kamerapfad unter " + editorStates() + " (Fenster " + SelfTestRunner.screenName(mc) + ")")
                    : StepResult.pending();
        }
        double worstRoll = 0.0;
        double minPitch = Double.MAX_VALUE;
        double maxPitch = -Double.MAX_VALUE;
        int keyframes = 0;
        double worstFov = 0.0;
        int fovKeyframes = 0;
        double expectedFov = vertical((double) state.getOrDefault(GIMBAL_FOV, Double.NaN));
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.get(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement scene : root.getAsJsonArray("scenes")) {
                for (JsonElement track : scene.getAsJsonObject().getAsJsonArray("keyframeTracks")) {
                    JsonObject t = track.getAsJsonObject();
                    if ("FOV".equals(t.get("keyframeType").getAsString())) {
                        for (Map.Entry<String, JsonElement> key : t.getAsJsonObject("keyframesByTick").entrySet()) {
                            fovKeyframes++;
                            worstFov = Math.max(worstFov, Math.abs(key.getValue().getAsJsonObject().get("fov").getAsDouble() - expectedFov));
                        }
                        continue;
                    }
                    if (!"CAMERA".equals(t.get("keyframeType").getAsString())) {
                        continue;
                    }
                    for (Map.Entry<String, JsonElement> key : t.getAsJsonObject("keyframesByTick").entrySet()) {
                        JsonObject k = key.getValue().getAsJsonObject();
                        keyframes++;
                        worstRoll = Math.max(worstRoll, Math.abs(k.get("roll").getAsDouble()));
                        double pitch = k.get("pitch").getAsDouble();
                        minPitch = Math.min(minPitch, pitch);
                        maxPitch = Math.max(maxPitch, pitch);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return ticks > 20 * 90 ? StepResult.fail("Kamerapfad nicht lesbar: " + e) : StepResult.pending();
        }
        if (keyframes < MIN_KEYFRAMES) {
            return ticks > 20 * 90 ? StepResult.fail("nur " + keyframes + " Kamera-Keyframes in " + file.get().getFileName()) : StepResult.pending();
        }
        String measured = keyframes + " Kamera-Keyframes in " + file.get().getFileName() + ", Rahmen bis " + Flight.format(maxRoll)
                + "° gerollt, Kamerapfad höchstens " + Flight.format(worstRoll) + "° gerollt, Neigung " + Flight.format(minPitch) + "° bis "
                + Flight.format(maxPitch) + "°, Sichtfeld " + fovKeyframes + " Keyframes höchstens " + Flight.format(worstFov)
                + "° neben der Gimbal-Kamera (" + Flight.format(expectedFov) + "° senkrecht)";
        if (worstRoll >= PATH_ROLL_LIMIT) {
            return StepResult.fail(measured + " (Grenze " + Flight.format(PATH_ROLL_LIMIT) + "°)");
        }
        if (maxPitch < PATH_PITCH_MIN) {
            return StepResult.fail(measured + ": Kamerapfad folgt der Gimbal-Neigung " + Flight.format(TILT_TARGET) + "° nicht (mindestens "
                    + Flight.format(PATH_PITCH_MIN) + "° nach unten)");
        }
        if (fovKeyframes == 0 || !Double.isFinite(expectedFov) || !(worstFov <= FOV_LIMIT)) {
            return StepResult.fail(measured + ": Sichtfeld des Kamerapfads ist nicht das der Gimbal-Kamera (Grenze " + Flight.format(FOV_LIMIT)
                    + "°)");
        }
        return StepResult.pass(measured);
    }
}
