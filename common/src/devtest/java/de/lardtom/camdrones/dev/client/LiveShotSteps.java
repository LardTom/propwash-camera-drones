package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.gimbal.model.PilotView;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class LiveShotSteps {
    public static final int LIVE_NUMBER = 601;
    public static final int CLEAN_NUMBER = 602;
    public static final String LIVE = "mission: Aufnahme und Foto der Kino im Livebild ohne Pilot, mit Pfad im Chat, ohne Props";
    public static final String CLEAN = "hud: Foto im FPV-Bild kommt aus der Gimbal-Kamera der Kino, ohne Props";
    public static final double REACHED = 1.5;
    public static final double DARK = 0.85;
    public static final double MAX_DARK_SHARE = 0.003;
    public static final double TILT_UP = 25.0;
    public static final double LOOK_UP = 20.0;
    public static final double[] FRAME_SECONDS = {1.0, 4.0};
    private static final int LIVE_DX = 60;
    private static final int LIVE_DZ = -160;
    private static final int CLEAN_DX = 90;
    private static final int CLEAN_DZ = -160;
    private static final String POINTS = "live.points";
    private static final String REACHED_AT = "live.reached";
    private static final String BEFORE = "live.before";
    private static final String SIZE = "live.size";
    private static final String STABLE = "live.stable";
    private static final String PHOTO_AT = "live.photoAt";
    private static final String SHOWN = "live.shown";
    private static final String LOOK = "live.look";
    private static final String VIDEO = "live.video";
    private static final String PHOTO = "live.photo";
    private static final String FRAMES = "live.frames";
    private static final int STEP_TICKS = 20 * 240;
    private static final List<Component> CHAT = new CopyOnWriteArrayList<>();

    private LiveShotSteps() {
    }

    public static void register() {
        SelfTest.clientStep(LIVE_NUMBER, LIVE, STEP_TICKS, Script.create()
                .server("Kino abstellen und koppeln, Ansicht Kamera, Mission mit Aufnahme an Punkt 2, Foto an 3, Aufnahme aus an 4",
                        (server, state) -> {
                            Pilot.spawn(server, state, CamPresets.CINE, Arena.ground(LIVE_DX, LIVE_DZ), 0.0F);
                            Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            CamFlights.pair(server, state);
                            cameraView(server, state);
                            mission(server, state);
                        })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .then("Chat mitlesen, Aufnahme-Ordner merken", (ctx, ticks) -> {
                    listen();
                    ctx.state().put(BEFORE, files());
                    return StepResult.pass();
                })
                .sleep(10)
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Livebild öffnen", LiveShotSteps::watch)
                .then("Mission im Livebild abfliegen", LiveShotSteps::watchMission)
                .then("Video und Foto im Aufnahme-Ordner, Pfad im Chat", LiveShotSteps::reported)
                .then("keine Props im Foto und im Video aus dem Livebild", LiveShotSteps::liveNoProps), LiveShotSteps::stopListening);
    }

    public static void registerCleanView() {
        SelfTest.clientStep(CLEAN_NUMBER, CLEAN, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.CINE, CLEAN_DX, CLEAN_DZ, 0.0F))
                .server("Ansicht Kamera an der Drohne", LiveShotSteps::cameraView)
                .append(Flight.takeoff(10.0))
                .then("Kamera-Ansicht statt OSD", (ctx, ticks) -> {
                    Flight.track(ctx);
                    return CamOsdSource.last().cameraView() ? StepResult.pass("Kamera-Ansicht aktiv")
                            : ticks > 100 ? StepResult.fail("Kamera-Ansicht kommt nicht") : StepResult.pending();
                })
                .then("Gimbal in den Himmel neigen", LiveShotSteps::tiltUp)
                .append(PilotViewSteps.fpvImage("vor dem Foto"))
                .then("Foto wie an einem Foto-Wegpunkt", LiveShotSteps::photo)
                .then("keine Props im Bild", LiveShotSteps::noProps)
                .append(PilotViewSteps.fpvImage("nach dem Foto")), LiveShotSteps::resetTilt);
    }

    static void cameraView(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        DroneSettings settings = PropwashApi.droneData().get(drone, CamDroneData.settings());
        PropwashApi.droneData().set(drone, CamDroneData.settings(),
                (settings == null ? DroneSettings.DEFAULT : settings).withView(DroneSettings.CameraView.HUD));
    }

    private static void mission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + 6.0;
        double[][] offsets = {{0.0, 6.0}, {0.0, 14.0}, {4.0, 22.0}, {0.0, 30.0}, {0.0, 36.0}};
        Waypoint.Action[] actions = {Waypoint.Action.NONE, Waypoint.Action.RECORD_START, Waypoint.Action.PHOTO, Waypoint.Action.RECORD_STOP,
                Waypoint.Action.NONE};
        List<Vec3> points = new ArrayList<>();
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            Vec3 p = new Vec3(drone.getX() + offsets[i][0], y, drone.getZ() + offsets[i][1]);
            points.add(p);
            waypoints.add(Waypoint.at(p.x, p.y, p.z).withAction(actions[i]));
        }
        state.put(POINTS, points);
        CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
    }

    private static StepResult watch(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == drone.get() && !PropwashClientApi.input().flying()) {
            return StepResult.pass("Livebild zeigt die Kino, kein Pilot");
        }
        if (ticks % 20 == 0) {
            PropwashClientApi.spectate(drone.get());
        }
        return ticks > 300 ? StepResult.fail("Livebild zeigt nicht die Drohne (Kamera " + mc.getCameraEntity() + ")") : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult watchMission(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (Minecraft.getInstance().getCameraEntity() != drone.get()) {
            return StepResult.fail("Livebild endet während der Mission");
        }
        List<Vec3> points = (List<Vec3>) ctx.state().get(POINTS);
        Map<Integer, Long> reached = (Map<Integer, Long>) ctx.state().computeIfAbsent(REACHED_AT, key -> new HashMap<Integer, Long>());
        for (int i = 0; i < points.size(); i++) {
            if (!reached.containsKey(i) && drone.get().position().distanceTo(points.get(i)) <= REACHED) {
                reached.put(i, System.currentTimeMillis());
            }
        }
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        boolean done = status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
        if (done && reached.size() >= points.size()) {
            return StepResult.pass("Mission ohne Pilot fertig, alle " + points.size() + " Punkte erreicht, Livebild durchgehend");
        }
        return ticks > 20 * 150 ? StepResult.fail("Mission kommt nicht zu Ende: " + reached.keySet() + " erreicht, Zustand "
                + status.map(s -> s.state().name()).orElse("-")) : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult reported(SelfTest.StepContext ctx, int ticks) {
        Set<String> before = (Set<String>) ctx.state().get(BEFORE);
        List<Path> videos = created(before, "propwash_", ".mp4");
        List<Path> photos = created(before, "propwash_photo_", ".png");
        if (videos.isEmpty() || photos.isEmpty()) {
            return ticks > 20 * 60 ? StepResult.fail("nach der Mission im Livebild " + videos.size() + " neue Videos und " + photos.size()
                    + " neue Fotos unter " + recordings() + " (Ordner der Action-Cam-Aufnahmen)") : StepResult.pending();
        }
        if (videos.size() != 1 || photos.size() != 1) {
            return StepResult.fail(videos.size() + " neue Videos und " + photos.size() + " neue Fotos statt je einem: " + videos + " " + photos);
        }
        Path video = videos.getFirst();
        Path photo = photos.getFirst();
        long size;
        try {
            size = Files.size(video);
        } catch (IOException e) {
            return StepResult.pending();
        }
        long last = (long) ctx.state().getOrDefault(SIZE, -1L);
        ctx.state().put(SIZE, size);
        int stable = size > 0 && size == last ? (int) ctx.state().getOrDefault(STABLE, 0) + 1 : 0;
        ctx.state().put(STABLE, stable);
        if (stable < 40) {
            return ticks > 20 * 90 ? StepResult.fail("Video wird nicht fertig: " + video.getFileName()) : StepResult.pending();
        }
        Set<Path> linked = linkedFiles();
        boolean videoLinked = linked.contains(video.toAbsolutePath().normalize());
        boolean photoLinked = linked.contains(photo.toAbsolutePath().normalize());
        if (!videoLinked || !photoLinked) {
            return ticks > 20 * 120 ? StepResult.fail("Chat meldet " + (videoLinked ? "" : "das Video ") + (photoLinked ? "" : "das Foto ")
                    + "nicht mit Pfad; Links im Chat: " + linked) : StepResult.pending();
        }
        keep(video.getFileName().toString().replace(".mp4", "") + "_frame.png", photo);
        ctx.state().put(VIDEO, video);
        ctx.state().put(PHOTO, photo);
        return StepResult.pass("Video " + video.getFileName() + " (" + size / 1024 + " KiB) und Foto " + photo.getFileName() + " unter "
                + recordings() + ", beide im Chat mit Pfad gemeldet");
    }

    @SuppressWarnings("unchecked")
    private static StepResult liveNoProps(SelfTest.StepContext ctx, int ticks) {
        Path photo = (Path) ctx.state().get(PHOTO);
        Path video = (Path) ctx.state().get(VIDEO);
        Optional<Path> ffmpeg = GimbalSteps.ffmpeg();
        Object pending = ctx.state().get(FRAMES);
        if (pending == null && ffmpeg.isPresent()) {
            ctx.state().put(FRAMES, CompletableFuture.supplyAsync(() -> frames(ffmpeg.get(), video)));
            return StepResult.pending();
        }
        if (pending != null && !((CompletableFuture<List<Path>>) pending).isDone()) {
            return ticks > 20 * 120 ? StepResult.fail("ffmpeg hängt") : StepResult.pending();
        }
        List<String> found = new ArrayList<>();
        double worst;
        try {
            worst = skyShare(photo);
        } catch (IOException e) {
            return StepResult.fail("Foto aus dem Livebild nicht lesbar: " + e);
        }
        found.add(String.format(Locale.GERMANY, "Foto %.2f %%", worst * 100.0));
        int measured = 0;
        if (pending != null) {
            List<Path> frames = ((CompletableFuture<List<Path>>) pending).join();
            for (int i = 0; i < FRAME_SECONDS.length; i++) {
                Path frame = frames.get(i);
                String at = String.format(Locale.GERMANY, "Video %.1f s", FRAME_SECONDS[i]);
                if (frame == null) {
                    found.add(at + " kein Bild");
                    continue;
                }
                try {
                    double share = skyShare(frame);
                    worst = Math.max(worst, share);
                    measured++;
                    found.add(String.format(Locale.GERMANY, "%s %.2f %%", at, share * 100.0));
                } catch (IOException e) {
                    found.add(at + " nicht lesbar");
                }
            }
        }
        String line = String.format(Locale.GERMANY, "Pixel im oberen Drittel deutlich dunkler als der Himmel in derselben Zeile (Grenze %.2f %%): %s",
                MAX_DARK_SHARE * 100.0, String.join(", ", found));
        if (worst > MAX_DARK_SHARE) {
            return StepResult.fail("Props im Livebild: " + line);
        }
        if (ffmpeg.isEmpty()) {
            return StepResult.pass(line + "; ffmpeg fehlt, Video nicht geprüft");
        }
        if (measured == 0) {
            return StepResult.fail("kein Bild aus dem Video " + video.getFileName() + ": " + line);
        }
        return StepResult.pass(line);
    }

    private static List<Path> frames(Path ffmpeg, Path video) {
        List<Path> out = new ArrayList<>();
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("ownc2").toAbsolutePath();
        for (double seconds : FRAME_SECONDS) {
            Path frame = dir.resolve(String.format(Locale.ROOT, "live_video_%.1fs.png", seconds));
            try {
                Files.createDirectories(dir);
                Files.deleteIfExists(frame);
                Process process = new ProcessBuilder(ffmpeg.toString(), "-v", "error", "-ss", String.format(Locale.ROOT, "%.2f", seconds), "-i",
                        video.toAbsolutePath().toString(), "-frames:v", "1", "-y", frame.toString()).redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                boolean done = process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0 && Files.exists(frame);
                out.add(done ? frame : null);
                if (done && Shots.enabled()) {
                    Files.copy(frame, Shots.dir().resolve(frame.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                out.add(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.add(null);
            }
        }
        return out;
    }

    private static double skyShare(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            return darkShare(image, image.getHeight() / 3);
        }
    }

    private static StepResult tiltUp(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double tilt = GimbalClient.tilt(drone.get());
        if (tilt >= TILT_UP) {
            Pilot.hold(tiltKey(true), false);
            return ticks >= 40 ? StepResult.pass("Neigung " + Flight.format(tilt) + "°") : StepResult.pending();
        }
        Pilot.hold(tiltKey(true), true);
        return ticks > 300 ? StepResult.fail("Neigung bleibt bei " + Flight.format(tilt) + "°") : StepResult.pending();
    }

    private static StepResult photo(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(BEFORE, files());
            ctx.state().put(PHOTO_AT, System.currentTimeMillis());
            CameraActions.Done done = CameraActions.waypoint(Waypoint.Action.PHOTO, GimbalClient.camera(drone.get()));
            if (done != CameraActions.Done.PHOTO) {
                return StepResult.fail("Foto abgelehnt: " + done);
            }
            ctx.state().put(SHOWN, GimbalClient.provider().image(drone.get(), false));
        }
        if (ticks == 1) {
            double up = -GimbalSteps.cameraDown();
            ctx.state().put(LOOK, up);
        }
        @SuppressWarnings("unchecked")
        List<Path> photos = created((Set<String>) ctx.state().get(BEFORE), "propwash_photo_", ".png");
        if (photos.size() == 1) {
            ctx.state().put(PHOTO_AT, photos.getFirst());
            return StepResult.pass("Foto " + photos.getFirst().getFileName());
        }
        return ticks > 200 ? StepResult.fail("kein Foto unter " + recordings()) : StepResult.pending();
    }

    private static StepResult noProps(SelfTest.StepContext ctx, int ticks) {
        Path photo = (Path) ctx.state().get(PHOTO_AT);
        double share;
        try (InputStream in = Files.newInputStream(photo); NativeImage image = NativeImage.read(in)) {
            share = darkShare(image);
        } catch (IOException e) {
            return ticks > 100 ? StepResult.fail("Foto nicht lesbar: " + e) : StepResult.pending();
        }
        keep("clean_view_cine.png", photo);
        String props = PropwashApi.builds().analyze(Pilot.preset(CamPresets.CINE).data().build()).map(BuildStats::propsInViewPercent)
                .map(p -> String.format(Locale.GERMANY, ", Propwash-Analyse des Baus: %.1f %% Props im Bild", p)).orElse("");
        String line = String.format(Locale.GERMANY, "%.2f %% der Pixel deutlich dunkler als der Himmel in derselben Zeile (Grenze %.2f %%)%s",
                share * 100.0, MAX_DARK_SHARE * 100.0, props);
        Object shown = ctx.state().get(SHOWN);
        String view = String.format(Locale.GERMANY, ", Pilotenbild beim Foto %s, Bild blickt %.1f° nach oben", shown,
                (double) ctx.state().getOrDefault(LOOK, Double.NaN));
        if (share > MAX_DARK_SHARE) {
            return StepResult.fail("Props im Foto: " + line + view);
        }
        if (shown != PilotView.Image.GIMBAL || !((double) ctx.state().getOrDefault(LOOK, Double.NaN) >= LOOK_UP)) {
            return StepResult.fail("Foto nicht aus der Gimbal-Kamera: " + line + view);
        }
        return StepResult.pass(line + view);
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

    static double darkShare(NativeImage image) {
        return darkShare(image, image.getHeight());
    }

    static double darkShare(NativeImage image, int height) {
        int width = image.getWidth();
        int x0 = width * 2 / 5;
        int x1 = Math.max(x0 + 1, width * 3 / 5);
        int[] centre = new int[x1 - x0];
        long dark = 0;
        long total = 0;
        for (int y = 0; y < height; y += 2) {
            for (int x = x0; x < x1; x++) {
                centre[x - x0] = luma(image.getPixel(x, y));
            }
            Arrays.sort(centre);
            double limit = centre[centre.length / 2] * DARK;
            for (int x = 0; x < width; x += 2) {
                total++;
                if (luma(image.getPixel(x, y)) < limit) {
                    dark++;
                }
            }
        }
        return total == 0 ? 0.0 : dark / (double) total;
    }

    private static int luma(int argb) {
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        return (299 * r + 587 * g + 114 * b) / 1000;
    }

    private static KeyMapping tiltKey(boolean up) {
        return (up ? CamKeys.increase(CamControl.GIMBAL_TILT) : CamKeys.decrease(CamControl.GIMBAL_TILT))
                .orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Neigung fehlt"));
    }

    private static void listen() {
        CHAT.clear();
        Minecraft.getInstance().gui.hud.getChat().setVisibleMessageFilter(message -> {
            CHAT.add(message.content());
            return true;
        });
    }

    private static StepResult stopListening(SelfTest.StepContext ctx) {
        Minecraft.getInstance().gui.hud.getChat().setVisibleMessageFilter(message -> true);
        CHAT.clear();
        return StepResult.pass();
    }

    private static Set<Path> linkedFiles() {
        Set<Path> out = new HashSet<>();
        for (Component message : CHAT) {
            message.visit((Style style, String text) -> {
                if (style.getClickEvent() instanceof ClickEvent.OpenFile file) {
                    out.add(Path.of(file.path()).toAbsolutePath().normalize());
                }
                return Optional.empty();
            }, Style.EMPTY);
        }
        return out;
    }

    private static Path recordings() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("propwash").resolve("recordings");
    }

    private static Set<String> files() {
        Set<String> out = new HashSet<>();
        Path dir = recordings();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(p -> out.add(p.getFileName().toString()));
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private static List<Path> created(Set<String> before, String prefix, String suffix) {
        List<Path> out = new ArrayList<>();
        Path dir = recordings();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return !before.contains(name) && name.startsWith(prefix) && name.endsWith(suffix);
            }).forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private static void keep(String name, Path file) {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("ownc2");
            Files.createDirectories(dir);
            Files.copy(file, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            if (Shots.enabled()) {
                Files.copy(file, Shots.dir().resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CamDrones.LOG.warn("Foto {} nicht kopiert: {}", file, e.toString());
        }
    }
}
