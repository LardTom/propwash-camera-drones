package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.PilotView;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.RecordingControl;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3fc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class GimbalSteps {
    public static final String HORIZON = "gimbal: Horizont gerade bei 30° Rollen";
    public static final String TILT = "gimbal: Neigung −90° per Achse";
    public static final String RECORDING = "aufnahme: stabilisierte Aufnahme, Rollwinkel im Bild < 2°";
    public static final String ROLL_RIGHT = "key.propwash.kb_roll_right";
    public static final double ROLL_TARGET = 28.0;
    public static final double ROLL_MIN = 25.0;
    public static final double HORIZON_LIMIT = 2.0;
    public static final double DOWN_LIMIT = -89.0;
    public static final double DOWN_LOOK = 88.0;
    public static final int ROLL_TICKS = 40;
    private static final String MAX_ROLL = "gimbal.maxRoll";
    private static final String MAX_HORIZON = "gimbal.maxHorizon";
    private static final String SAMPLES = "gimbal.samples";
    private static final String SHOT = "gimbal.shot";
    private static final String STARTED = "gimbal.started";
    private static final String PHOTO_AT = "gimbal.photoAt";
    private static final String PHOTO_ROLL = "gimbal.photoRoll";
    private static final String PHOTO_TAKEN = "gimbal.photoTaken";
    private static final String PHOTO_IMAGE = "gimbal.photoImage";
    private static final String VIDEO = "gimbal.video";
    private static final String VIDEO_SIZE = "gimbal.videoSize";
    private static final String VIDEO_STABLE = "gimbal.videoStable";
    private static final String FRAMES = "gimbal.frames";
    private static final int STEP_TICKS = 20 * 180;

    private GimbalSteps() {
    }

    public static void register() {
        SelfTest.clientStep(7, HORIZON, STEP_TICKS, Script.create()
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Shots.photoWindow())
                .append(Pilot.launch(CamPresets.TOUR, -40, 40, 0.0F))
                .append(PilotViewSteps.gimbalImage())
                .append(Flight.takeoff(8.0))
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("nach rechts rollen", (ctx, ticks) -> roll(ctx, ticks, false))
                .then("loslassen", (ctx, ticks) -> {
                    Pilot.hold(ROLL_RIGHT, false);
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake)
                .then("Horizont auswerten", GimbalSteps::horizon), KeyboardDeflection::restore);
        SelfTest.clientStep(8, TILT, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(Pilot.launch(CamPresets.TOUR, -52, 40, 0.0F))
                .append(PilotViewSteps.gimbalImage())
                .append(Flight.takeoff(6.0))
                .then("Neigung per Achse nach unten", GimbalSteps::tiltDown)
                .then("Neigung bleibt", GimbalSteps::tiltHeld)
                .append(Shots.photos("gimbal_tilt_down"))
                .then("Neigung zurück", GimbalSteps::tiltBack), GimbalSteps::resetTilt);
        SelfTest.clientStep(18, RECORDING, STEP_TICKS, Script.create()
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Pilot.launch(CamPresets.TOUR, -64, 40, 0.0F))
                .append(Flight.takeoff(8.0))
                .append(PilotViewSteps.fpvImage("vor der Aufnahme"))
                .then("Aufnahme mit Propwashs Taste starten", GimbalSteps::startRecording)
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("nach rechts rollen, Foto der Aufnahme", (ctx, ticks) -> roll(ctx, ticks, true))
                .then("loslassen", (ctx, ticks) -> {
                    Pilot.hold(ROLL_RIGHT, false);
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake)
                .then("Aufnahme beenden", GimbalSteps::stopRecording)
                .append(PilotViewSteps.fpvImage("nach dem Stopp"))
                .then("Foto der Aufnahme auswerten", GimbalSteps::photo)
                .then("Video auswerten", GimbalSteps::video), ctx -> {
                    PropwashClientApi.recording().stop();
                    return KeyboardDeflection.restore(ctx);
                });
    }

    static double frameTilt(Entity drone) {
        return PropwashApi.drones().view(drone).map(DroneView::up).map(up -> Math.toDegrees(Math.acos(Math.clamp(up.y, -1.0, 1.0)))).orElse(0.0);
    }

    static double cameraHorizon() {
        Vector3fc left = Minecraft.getInstance().gameRenderer.mainCamera().leftVector();
        return Math.toDegrees(Math.asin(Math.clamp(Math.abs(left.y()), 0.0, 1.0)));
    }

    static double cameraDown() {
        Vector3fc forward = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
        return Math.toDegrees(Math.asin(Math.clamp(-forward.y(), -1.0, 1.0)));
    }

    private static StepResult roll(SelfTest.StepContext ctx, int ticks, boolean recording) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Pilot.hold(ROLL_RIGHT, true);
        double tilt = frameTilt(drone.get());
        double horizon = cameraHorizon();
        ctx.state().put(MAX_ROLL, Math.max((double) ctx.state().getOrDefault(MAX_ROLL, 0.0), tilt));
        if (tilt >= 20.0) {
            ctx.state().put(MAX_HORIZON, Math.max((double) ctx.state().getOrDefault(MAX_HORIZON, 0.0), horizon));
            ctx.state().put(SAMPLES, (int) ctx.state().getOrDefault(SAMPLES, 0) + 1);
        }
        if (tilt >= ROLL_TARGET && !ctx.state().containsKey(SHOT)) {
            if (recording) {
                boolean taken = PropwashClientApi.recording().photo(drone.get());
                ctx.state().put(SHOT, CompletableFuture.completedFuture(taken ? "Foto" : "abgelehnt"));
                ctx.state().put(PHOTO_TAKEN, taken);
                ctx.state().put(PHOTO_AT, System.currentTimeMillis());
                ctx.state().put(PHOTO_ROLL, tilt);
                ctx.state().put(PHOTO_IMAGE, GimbalClient.provider().image(drone.get(), false));
            } else if (Shots.enabled()) {
                Shots.clearOverlays(Minecraft.getInstance());
                ctx.state().put(SHOT, Shots.capture("gimbal_horizon_roll"));
            } else {
                ctx.state().put(SHOT, CompletableFuture.completedFuture(""));
            }
        }
        boolean shot = ctx.state().containsKey(SHOT);
        int samples = (int) ctx.state().getOrDefault(SAMPLES, 0);
        if (shot && samples >= 6 || ticks >= ROLL_TICKS) {
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static StepResult horizon(SelfTest.StepContext ctx, int ticks) {
        Object shot = ctx.state().get(SHOT);
        if (shot instanceof CompletableFuture<?> future && !future.isDone()) {
            return ticks > 200 ? StepResult.fail("Bildschirmfoto hängt") : StepResult.pending();
        }
        double maxRoll = (double) ctx.state().getOrDefault(MAX_ROLL, 0.0);
        double maxHorizon = (double) ctx.state().getOrDefault(MAX_HORIZON, Double.NaN);
        int samples = (int) ctx.state().getOrDefault(SAMPLES, 0);
        if (maxRoll < ROLL_MIN) {
            return StepResult.fail("Drohne rollt nur " + Flight.format(maxRoll) + "° (mindestens " + Flight.format(ROLL_MIN) + "°)");
        }
        if (samples < 4 || !(maxHorizon < HORIZON_LIMIT)) {
            return StepResult.fail("Horizont im FPV-Bild um " + Flight.format(maxHorizon) + "° geneigt bei " + Flight.format(maxRoll)
                    + "° Rollen (Grenze " + Flight.format(HORIZON_LIMIT) + "°, " + samples + " Bilder)");
        }
        return StepResult.pass("Rahmen bis " + Flight.format(maxRoll) + "° gerollt, Horizont der Kamera höchstens " + Flight.format(maxHorizon)
                + "° geneigt in " + samples + " Bildern ab 20° Rollen");
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
        Pilot.hold(tiltKey(false), true);
        double tilt = GimbalClient.tilt(drone.get());
        if (tilt <= DOWN_LIMIT && ticks >= 10) {
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass("Neigung " + Flight.format(tilt) + "° nach " + Flight.format(ticks / 20.0) + " s Bild↓, Achse "
                    + Flight.format(PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id())));
        }
        return ticks > 200 ? StepResult.fail("Neigung bleibt bei " + Flight.format(tilt) + "°, Achse "
                + Flight.format(PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id()))) : StepResult.pending();
    }

    private static StepResult tiltHeld(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double tilt = GimbalClient.tilt(drone.get());
        if (tilt > DOWN_LIMIT) {
            return StepResult.fail("Neigung springt nach dem Loslassen auf " + Flight.format(tilt) + "°");
        }
        if (ticks < 30) {
            return StepResult.pending();
        }
        double down = cameraDown();
        if (down < DOWN_LOOK) {
            return ticks > 60 ? StepResult.fail("FPV-Bild blickt nur " + Flight.format(down) + "° nach unten") : StepResult.pending();
        }
        return StepResult.pass("hält " + Flight.format(tilt) + "°, FPV-Bild blickt " + Flight.format(down) + "° nach unten");
    }

    private static StepResult tiltBack(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        boolean centered = Math.abs(axis) < 0.02;
        Pilot.hold(tiltKey(true), !centered && axis < 0.0);
        Pilot.hold(tiltKey(false), !centered && axis > 0.0);
        if (centered) {
            Pilot.hold(tiltKey(true), false);
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass("Bild↑ zurück auf " + Flight.format(GimbalClient.tilt(drone.get())) + "°");
        }
        return ticks > 200 ? StepResult.fail("Achse kommt nicht zurück: " + Flight.format(axis)) : StepResult.pending();
    }

    private static StepResult resetTilt(SelfTest.StepContext ctx) {
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (Math.abs(axis) < 0.02 || !PropwashClientApi.input().flying()) {
            Pilot.hold(tiltKey(true), false);
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass();
        }
        Pilot.hold(tiltKey(true), axis < 0.0);
        Pilot.hold(tiltKey(false), axis > 0.0);
        return ctx.tick() > 300 ? StepResult.fail("Gimbal-Achse bleibt bei " + Flight.format(axis)) : StepResult.pending();
    }

    private static Path recordings() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("propwash").resolve("recordings");
    }

    private static Optional<Path> newest(String prefix, String suffix, long since) {
        Path dir = recordings();
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(suffix);
            }).filter(p -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis() >= since - 1000L;
                } catch (IOException e) {
                    return false;
                }
            }).max(Comparator.comparingLong(p -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static StepResult startRecording(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        RecordingControl recording = PropwashClientApi.recording();
        if (ticks == 0) {
            ctx.state().put(STARTED, System.currentTimeMillis());
            Pilot.click(FlashbackSteps.RECORD_KEY);
        }
        if (recording.recording()) {
            return ticks >= 20 ? StepResult.pass("Aufnahme läuft, Pilotenbild " + GimbalClient.provider().image(drone.get(), false))
                    : StepResult.pending();
        }
        return ticks > 100 ? StepResult.fail("Aufnahme startet nicht mit der Taste " + FlashbackSteps.RECORD_KEY
                + " (Gimbal mit Action-Cam-Daten vorn)") : StepResult.pending();
    }

    private static StepResult stopRecording(SelfTest.StepContext ctx, int ticks) {
        RecordingControl recording = PropwashClientApi.recording();
        if (ticks == 0) {
            recording.stop();
        }
        if (!recording.recording()) {
            return StepResult.pass();
        }
        return ticks > 100 ? StepResult.fail("Aufnahme endet nicht") : StepResult.pending();
    }

    private static StepResult photo(SelfTest.StepContext ctx, int ticks) {
        if (!(boolean) ctx.state().getOrDefault(PHOTO_TAKEN, false)) {
            return StepResult.fail("Foto der Aufnahme beim Rollen abgelehnt (Rollen bis " + Flight.format((double) ctx.state().getOrDefault(MAX_ROLL, 0.0))
                    + "°)");
        }
        long at = (long) ctx.state().get(PHOTO_AT);
        Optional<Path> file = newest("propwash_photo_", ".png", at);
        if (file.isEmpty()) {
            return ticks > 200 ? StepResult.fail("kein Foto unter " + recordings()) : StepResult.pending();
        }
        Horizon.Check check;
        try {
            check = Horizon.check(file.get());
        } catch (IOException e) {
            return ticks > 200 ? StepResult.fail("Foto nicht lesbar: " + e) : StepResult.pending();
        }
        Horizon.Result result = check.level();
        double roll = (double) ctx.state().get(PHOTO_ROLL);
        if (!Double.isFinite(result.angleDeg())) {
            return StepResult.fail("kein Horizont im Foto " + file.get().getFileName() + " (" + result.columns() + " Spalten)");
        }
        if (!check.controlOk()) {
            return StepResult.fail("Bildauswertung erkennt eine Neigung nicht: " + check.describe());
        }
        Object image = ctx.state().get(PHOTO_IMAGE);
        if (Math.abs(result.angleDeg()) >= HORIZON_LIMIT) {
            return StepResult.fail("Foto der Aufnahme bei " + Flight.format(roll) + "° Rollen: " + check.describe() + ", Pilotenbild beim Foto "
                    + image);
        }
        if (image != PilotView.Image.GIMBAL) {
            return StepResult.fail("Pilotenbild während der Aufnahme " + image + " statt GIMBAL");
        }
        if (Shots.enabled()) {
            try {
                Files.copy(file.get(), Shots.dir().resolve("recording_photo_roll.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                ctx.log("Foto nicht kopiert: " + e);
            }
        }
        return StepResult.pass("Foto der Aufnahme bei " + Flight.format(roll) + "° Rollen: " + check.describe() + ", Pilotenbild beim Foto "
                + image);
    }

    static Optional<Path> ffmpeg() {
        List<Path> candidates = new ArrayList<>(List.of(Path.of("/opt/homebrew/bin/ffmpeg"), Path.of("/usr/local/bin/ffmpeg"), Path.of("/usr/bin/ffmpeg")));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) {
                    candidates.add(Path.of(dir, "ffmpeg"));
                }
            }
        }
        return candidates.stream().filter(Files::isExecutable).findFirst();
    }

    @SuppressWarnings("unchecked")
    private static StepResult video(SelfTest.StepContext ctx, int ticks) {
        long started = (long) ctx.state().get(STARTED);
        Optional<Path> file = newest("propwash_", ".mp4", started);
        if (file.isEmpty()) {
            return ticks > 20 * 60 ? StepResult.fail("kein Video unter " + recordings()) : StepResult.pending();
        }
        long size;
        try {
            size = Files.size(file.get());
        } catch (IOException e) {
            return StepResult.pending();
        }
        long last = (long) ctx.state().getOrDefault(VIDEO_SIZE, -1L);
        ctx.state().put(VIDEO_SIZE, size);
        int stable = size > 0 && size == last ? (int) ctx.state().getOrDefault(VIDEO_STABLE, 0) + 1 : 0;
        ctx.state().put(VIDEO_STABLE, stable);
        if (stable < 40) {
            return ticks > 20 * 90 ? StepResult.fail("Video wird nicht fertig: " + file.get().getFileName() + " " + size + " Bytes") : StepResult.pending();
        }
        Optional<Path> ffmpeg = ffmpeg();
        String video = file.get().getFileName() + " (" + size / 1024 + " KiB)";
        if (ffmpeg.isEmpty()) {
            return StepResult.pass("Video " + video + " gespeichert; ffmpeg fehlt, Bild nur aus dem Foto geprüft");
        }
        Object pending = ctx.state().get(FRAMES);
        if (pending == null) {
            double seconds = ((long) ctx.state().get(PHOTO_AT) - started) / 1000.0;
            ctx.state().put(FRAMES, CompletableFuture.supplyAsync(() -> frames(ffmpeg.get(), file.get(), seconds)));
            return StepResult.pending();
        }
        CompletableFuture<String> frames = (CompletableFuture<String>) pending;
        if (!frames.isDone()) {
            return ticks > 20 * 120 ? StepResult.fail("ffmpeg hängt") : StepResult.pending();
        }
        String outcome = frames.join();
        if (outcome.startsWith("FAIL ")) {
            return StepResult.fail("Video " + video + ": " + outcome.substring(5));
        }
        return StepResult.pass("Video " + video + ": " + outcome);
    }

    private static String frames(Path ffmpeg, Path video, double seconds) {
        List<String> found = new ArrayList<>();
        double worst = 0.0;
        int measured = 0;
        for (double offset : new double[]{-0.2, 0.0, 0.2}) {
            double at = Math.max(0.0, seconds + offset);
            Path out = video.resolveSibling("camdrones_selftest_frame_" + measured + ".png");
            try {
                Files.deleteIfExists(out);
                Process process = new ProcessBuilder(ffmpeg.toString(), "-v", "error", "-ss", String.format(java.util.Locale.ROOT, "%.2f", at), "-i",
                        video.toString(), "-frames:v", "1", "-y", out.toString()).redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0 || !Files.exists(out)) {
                    found.add(Flight.format(at) + " s: kein Bild");
                    continue;
                }
                Horizon.Check check = Horizon.check(out);
                Files.deleteIfExists(out);
                Horizon.Result result = check.level();
                if (!Double.isFinite(result.angleDeg()) || !check.controlOk()) {
                    found.add(Flight.format(at) + " s: kein Horizont");
                    continue;
                }
                measured++;
                worst = Math.max(worst, Math.abs(result.angleDeg()));
                found.add(Flight.format(at) + " s: " + Flight.format(result.angleDeg()) + "°");
            } catch (IOException e) {
                found.add(Flight.format(at) + " s: " + e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "FAIL unterbrochen";
            }
        }
        if (measured == 0) {
            return "FAIL kein Bild mit Horizont (" + String.join(", ", found) + ")";
        }
        if (worst >= HORIZON_LIMIT) {
            return "FAIL Horizont im Video bis " + Flight.format(worst) + "° geneigt (" + String.join(", ", found) + ")";
        }
        return "Horizont in " + measured + " Bildern um den Zeitpunkt des Rollens höchstens " + Flight.format(worst) + "° geneigt ("
                + String.join(", ", found) + ")";
    }
}
