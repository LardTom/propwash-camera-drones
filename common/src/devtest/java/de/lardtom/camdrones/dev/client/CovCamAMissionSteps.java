package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CovCamAMissionSteps {
    public static final String ACTIONS = "mission: Aktionen Aufnahme und Foto";
    public static final String LANDING_PAD = "mission: präzise Landung";
    public static final String NO_GPS = "mission: Start ohne GPS abgelehnt";
    public static final String NOT_READY = "mission: nicht startbereit";
    public static final Identifier PAD = CamDrones.id("landing_pad");
    public static final double REACHED = 1.5;
    public static final double PAD_TOLERANCE = 0.3;
    public static final double VIDEO_TOLERANCE = 2.0;
    public static final int REST_SAMPLES = 20;
    public static final double REST_SPEED = 0.05;
    public static final double REST_STEP = 0.001;
    public static final int REST_LIMIT = 20 * 30;
    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final String POINTS = "covcama.points";
    private static final String REACHED_AT = "covcama.reachedAt";
    private static final String BEFORE = "covcama.files";
    private static final String STARTED = "covcama.started";
    private static final String STABLE = "covcama.stable";
    private static final String SIZE = "covcama.size";
    private static final String PROBE = "covcama.ffmpeg";
    private static final String STAY = "covcama.stay";
    private static final String HOME = "covcama.home";
    private static final String PAD_PEAK = "covcama.padPeak";
    private static final double PAD_FLOWN = 3.0;
    private static final int STEP_TICKS = 20 * 240;
    private static final String REST = "covcama.rest";
    private static final String REST_LAST = "covcama.restLast";
    private static final String REST_QUIET = "covcama.restQuiet";

    private CovCamAMissionSteps() {
    }

    static void actions() {
        SelfTest.clientStep(350, ACTIONS, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -140, -100, 0.0F))
                .server("koppeln, Mission mit Aufnahme an Punkt 2, Foto an 3, Aufnahme aus an 4", (server, state) -> {
                    CamFlights.pair(server, state);
                    actionMission(server, state);
                })
                .append(Flight.takeoff(5.0))
                .then("Aufnahme-Ordner merken", (ctx, ticks) -> {
                    ctx.state().put(BEFORE, recordingFiles());
                    ctx.state().put(STARTED, System.currentTimeMillis());
                    return StepResult.pass();
                })
                .then("Mission starten", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(CamControl.MISSION_START, ticks < 4);
                    return ticks >= 6 ? StepResult.pass() : StepResult.pending();
                })
                .then("Mission abfliegen", CovCamAMissionSteps::flyActions)
                .then("Dateien im Aufnahme-Ordner", CovCamAMissionSteps::files));
    }

    static void landingPad() {
        SelfTest.clientStep(351, LANDING_PAD, STEP_TICKS, Script.create()
                .then("Landeplatz im Spiel", (ctx, ticks) -> BuiltInRegistries.BLOCK.containsKey(PAD) ? StepResult.pass()
                        : StepResult.fail("kein Block " + PAD + " (nur Modell und Item-Datei vorhanden)"))
                .server("Landeplatz legen, Tour darauf, Mission mit Ende „zum Start“", (server, state) -> {
                    BlockPos pad = Arena.ground(-150, 20);
                    Block block = BuiltInRegistries.BLOCK.getOptional(PAD).orElseThrow();
                    Arena.place(Arena.level(server), pad, block.defaultBlockState());
                    Entity drone = CovCamAKit.spawnPreset(server, state, CamPresets.TOUR, pad, 0.0F);
                    state.put(HOME, Vec3.atBottomCenterOf(pad.above()));
                    CamFlights.pair(server, state);
                    List<Waypoint> points = List.of(Waypoint.at(drone.getX() + 8.0, drone.getY() + 6.0, drone.getZ() + 8.0),
                            Waypoint.at(drone.getX() - 8.0, drone.getY() + 6.0, drone.getZ() + 12.0));
                    CamFlights.mission(server, state, Mission.of(points).withEnd(Mission.End.START).withBaseSpeed(5.0));
                })
                .then("Tour liegt ruhig auf dem Landeplatz", CovCamAMissionSteps::restsOnPad)
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Landung auf dem Landeplatz", (ctx, ticks) -> {
                    Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    Vec3 pad = (Vec3) ctx.state().get(HOME);
                    double height = drone.get().getY() - pad.y;
                    double off = CamFlights.horizontal(drone.get().position(), pad);
                    double peak = Math.max((double) ctx.state().getOrDefault(PAD_PEAK, height), height);
                    ctx.state().put(PAD_PEAK, peak);
                    boolean landed = CamFlights.clientView(ctx.state()).map(v -> !v.flying()).orElse(false) && height < 0.3 && ticks > 200;
                    if (landed && peak < PAD_FLOWN) {
                        return StepResult.fail("Mission nicht geflogen: höchstens " + Flight.format(peak) + " m über dem Landeplatz ("
                                + StatusProbe.describe(0) + ")");
                    }
                    if (landed) {
                        return off <= PAD_TOLERANCE ? StepResult.pass("setzt " + Flight.format(off) + " m neben der Mitte des Landeplatzes auf")
                                : StepResult.fail("setzt " + Flight.format(off) + " m neben der Mitte des Landeplatzes auf (Grenze "
                                + Flight.format(PAD_TOLERANCE) + " m)");
                    }
                    return ticks > 20 * 120 ? StepResult.fail("landet nicht auf dem Landeplatz") : StepResult.pending();
                }));
    }

    private record Rest(double y, double height, boolean onGround, double speed) {
    }

    @SuppressWarnings("unchecked")
    private static StepResult restsOnPad(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get(REST);
        if (pending == null) {
            CompletableFuture<Rest> future = new CompletableFuture<>();
            ctx.onServer(server -> {
                Entity drone = Pilot.serverDrone(server, state);
                Vec3 pad = (Vec3) state.get(HOME);
                future.complete(new Rest(drone.getY(), drone.getY() - pad.y, drone.onGround(), drone.getDeltaMovement().length() * 20.0));
            }).whenComplete((v, t) -> {
                if (t != null) {
                    future.completeExceptionally(t);
                }
            });
            state.put(REST, future);
            return StepResult.pending();
        }
        CompletableFuture<Rest> future = (CompletableFuture<Rest>) pending;
        if (!future.isDone()) {
            return ticks > REST_LIMIT ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
        }
        state.remove(REST);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(String.valueOf(future.exceptionNow()));
        }
        Rest now = future.join();
        boolean still = now.onGround() && now.speed() < REST_SPEED && state.get(REST_LAST) instanceof Rest before
                && Math.abs(now.y() - before.y()) < REST_STEP;
        int quiet = still ? (int) state.getOrDefault(REST_QUIET, 0) + 1 : 0;
        state.put(REST_QUIET, quiet);
        state.put(REST_LAST, now);
        String where = String.format(Locale.ROOT, "%.3f m zum Rand der Grube, onGround %s, Tempo %.2f m/s", now.height(), now.onGround(), now.speed());
        if (quiet >= REST_SAMPLES) {
            return StepResult.pass("liegt nach " + Flight.format(ticks / 20.0) + " s ruhig: " + where);
        }
        return ticks > REST_LIMIT ? StepResult.fail("liegt nach " + Flight.format(ticks / 20.0) + " s nicht ruhig: " + where) : StepResult.pending();
    }

    static void noGps() {
        SelfTest.clientStep(352, NO_GPS, STEP_TICKS, Script.create()
                .server("Steindecke, Tour darunter, Mission", (server, state) -> {
                    ServerLevel level = Arena.level(server);
                    Arena.fill(level, Arena.ground(-152, 60).above(6), Arena.ground(-128, 84).above(6), Blocks.STONE.defaultBlockState());
                    CovCamAKit.spawnPreset(server, state, CamPresets.TOUR, Arena.ground(-140, 72), 0.0F);
                    ServerPlayer player = Arena.player(server);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    CamFlights.pair(server, state);
                    Entity drone = Pilot.serverDrone(server, state);
                    CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), drone.getY() + 4.0, drone.getZ() + 6.0),
                            Waypoint.at(drone.getX() + 6.0, drone.getY() + 4.0, drone.getZ() + 6.0))).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
                })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .sleep(20)
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Ablehnung ohne GPS", (ctx, ticks) -> refused(ctx, ticks, true)));
    }

    static void notReady() {
        SelfTest.clientStep(353, NOT_READY, STEP_TICKS, Script.create()
                .server("Werkbank aufstellen", (server, state) -> CovCamAKit.placeBench(server))
                .server("Tour mit leerem Akku bauen und abstellen, Mission", (server, state) -> {
                    ItemStack empty = CovCamAKit.buildInBench(server, CamPresets.TOUR.build(), 0.0F)
                            .orElseThrow(() -> new IllegalStateException("Tour mit leerem Akku nicht gebaut"));
                    CovCamAKit.closeBench(server);
                    ServerPlayer player = Arena.player(server);
                    Entity drone = CovCamAKit.place(player, empty, Arena.ground(-110, 72), 0.0F);
                    state.put(Pilot.DRONE, drone.getUUID());
                    CamFlights.pair(server, state);
                    CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), drone.getY() + 4.0, drone.getZ() + 6.0),
                            Waypoint.at(drone.getX() + 6.0, drone.getY() + 4.0, drone.getZ() + 6.0))).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
                })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .sleep(20)
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Ablehnung mit Grund", (ctx, ticks) -> refused(ctx, ticks, false)));
    }

    private static void actionMission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + 5.0;
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(new Vec3(drone.getX() + (i % 2 == 0 ? 0.0 : 6.0), y, drone.getZ() + 8.0 + 10.0 * i));
        }
        List<Waypoint> waypoints = new ArrayList<>();
        Waypoint.Action[] actions = {Waypoint.Action.NONE, Waypoint.Action.RECORD_START, Waypoint.Action.PHOTO, Waypoint.Action.RECORD_STOP,
                Waypoint.Action.NONE};
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            waypoints.add(Waypoint.at(p.x, p.y, p.z).withAction(actions[i]));
        }
        state.put(POINTS, points);
        CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
    }

    private static Path recordings() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("propwash").resolve("recordings");
    }

    private static Set<String> recordingFiles() {
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

    @SuppressWarnings("unchecked")
    private static List<Path> created(SelfTest.StepContext ctx, String prefix, String suffix) {
        Set<String> before = (Set<String>) ctx.state().get(BEFORE);
        List<Path> out = new ArrayList<>();
        Path dir = recordings();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return !before.contains(name) && name.startsWith(prefix) && name.endsWith(suffix) && !name.startsWith("camdrones_selftest_frame_");
            }).forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static StepResult flyActions(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        List<Vec3> points = (List<Vec3>) ctx.state().get(POINTS);
        Map<Integer, Long> reached = (Map<Integer, Long>) ctx.state().computeIfAbsent(REACHED_AT, key -> new java.util.HashMap<Integer, Long>());
        for (int i = 0; i < points.size(); i++) {
            if (!reached.containsKey(i) && drone.get().position().distanceTo(points.get(i)) <= REACHED) {
                reached.put(i, System.currentTimeMillis());
            }
        }
        Optional<NavStatus> status = Pilot.status();
        boolean done = status.map(s -> s.missionState() == NavLogic.MissionState.DONE).orElse(false);
        if (done && reached.size() >= points.size()) {
            return StepResult.pass("Mission fertig, alle " + points.size() + " Punkte erreicht");
        }
        if (status.isPresent() && status.get().function() != NavLogic.Function.MISSION && ticks > 40 && !done) {
            return StepResult.fail("Mission läuft nicht im Pilotenflug: " + Flight.describe());
        }
        return ticks > 20 * 120 ? StepResult.fail("Mission kommt nicht zu Ende: " + reached.keySet() + " erreicht, " + Flight.describe())
                : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult files(SelfTest.StepContext ctx, int ticks) {
        List<Path> videos = created(ctx, "propwash_", ".mp4");
        List<Path> photos = created(ctx, "propwash_photo_", ".png");
        if (videos.isEmpty() || photos.isEmpty()) {
            return ticks > 20 * 60 ? StepResult.fail("nach der Mission " + videos.size() + " neue Videos und " + photos.size() + " neue Fotos unter "
                    + recordings()) : StepResult.pending();
        }
        if (videos.size() != 1 || photos.size() != 1) {
            return StepResult.fail(videos.size() + " neue Videos und " + photos.size() + " neue Fotos statt je einem: " + videos + " " + photos);
        }
        Path video = videos.getFirst();
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
        Map<Integer, Long> reached = (Map<Integer, Long>) ctx.state().get(REACHED_AT);
        double expected = (reached.get(3) - reached.get(1)) / 1000.0;
        Optional<Path> ffmpeg = ffmpeg();
        String found = "Video " + video.getFileName() + " (" + size / 1024 + " KiB), Foto " + photos.getFirst().getFileName();
        if (ffmpeg.isEmpty()) {
            return StepResult.pass(found + "; ffmpeg fehlt, Länge nicht gemessen (Flugzeit Punkt 2 → 4 " + Flight.format(expected) + " s)");
        }
        Object pending = ctx.state().get(PROBE);
        if (pending == null) {
            ctx.state().put(PROBE, CompletableFuture.supplyAsync(() -> duration(ffmpeg.get(), video)));
            return StepResult.pending();
        }
        CompletableFuture<Double> future = (CompletableFuture<Double>) pending;
        if (!future.isDone()) {
            return ticks > 20 * 120 ? StepResult.fail("ffmpeg hängt") : StepResult.pending();
        }
        double length = future.join();
        String line = found + ", Länge " + Flight.format(length) + " s, Flugzeit Punkt 2 → 4 " + Flight.format(expected) + " s";
        if (!Double.isFinite(length)) {
            return StepResult.fail("Länge des Videos nicht lesbar: " + line);
        }
        if (Math.abs(length - expected) > Math.max(VIDEO_TOLERANCE, 0.25 * expected)) {
            return StepResult.fail("Video passt nicht zur Flugzeit: " + line);
        }
        return StepResult.pass(line);
    }

    private static Optional<Path> ffmpeg() {
        List<Path> candidates = new ArrayList<>(List.of(Path.of("/opt/homebrew/bin/ffmpeg"), Path.of("/usr/local/bin/ffmpeg"), Path.of("/usr/bin/ffmpeg")));
        return candidates.stream().filter(Files::isExecutable).findFirst();
    }

    private static double duration(Path ffmpeg, Path video) {
        try {
            Process process = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-i", video.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            process.waitFor(30, TimeUnit.SECONDS);
            Matcher matcher = DURATION.matcher(output);
            if (!matcher.find()) {
                return Double.NaN;
            }
            return Integer.parseInt(matcher.group(1)) * 3600.0 + Integer.parseInt(matcher.group(2)) * 60.0 + Double.parseDouble(matcher.group(3));
        } catch (IOException e) {
            return Double.NaN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Double.NaN;
        }
    }

    private static StepResult refused(SelfTest.StepContext ctx, int ticks, boolean gps) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        int mark = CamFlights.mark(ctx);
        List<StatusMessage.Payload> seen = StatusProbe.since(mark);
        Optional<StatusMessage.Payload> error = seen.stream().filter(p -> p.status().error()).findFirst();
        Optional<StatusMessage.Payload> started = seen.stream().filter(p -> p.status() == AutonomyStatus.MISSION_STARTED).findFirst();
        double height = drone.get().getY() - CamFlights.surface();
        boolean armed = CamFlights.clientView(ctx.state()).map(DroneView::armed).orElse(false);
        if (started.isPresent()) {
            int since = (int) ctx.state().computeIfAbsent(STAY, key -> ticks);
            double highest = Math.max((double) ctx.state().getOrDefault(HOME, 0.0), height);
            ctx.state().put(HOME, highest);
            if (ticks - since < 120) {
                return StepResult.pending();
            }
            return StepResult.fail("Mission startet trotzdem (" + StatusProbe.describe(mark) + "), Drohne " + (armed ? "scharf" : "unscharf") + ", steigt auf "
                    + Flight.format(highest) + " m, nach 6 s " + Flight.format(height) + " m über Grund");
        }
        if (error.isEmpty()) {
            return ticks > 200 ? StepResult.fail("keine Ablehnung (" + StatusProbe.describe(mark) + ")") : StepResult.pending();
        }
        int stay = (int) ctx.state().computeIfAbsent(STAY, key -> ticks);
        if (height > 0.3 || armed) {
            return StepResult.fail("Drohne " + (armed ? "scharf" : "") + " in " + Flight.format(height) + " m Höhe nach der Ablehnung");
        }
        if (ticks - stay < 60) {
            return StepResult.pending();
        }
        StatusMessage.Payload payload = error.get();
        String text = AutonomyServer.text(payload.status(), payload.drone(), payload.detail()).getString();
        String lower = text.toLowerCase(Locale.ROOT);
        String line = "Status " + payload.status().name() + " \"" + text + "\", Drohne bleibt am Boden und unscharf";
        if (gps) {
            if (payload.status() != AutonomyStatus.NOT_READY) {
                return StepResult.fail("ohne GPS " + line + " statt NOT_READY");
            }
            return lower.contains("gps") ? StepResult.pass(line) : StepResult.fail("Meldung nennt den GPS-Fix nicht: " + line);
        }
        boolean battery = lower.contains("battery") || lower.contains("akku");
        boolean specific = battery && !lower.contains("gps");
        return specific ? StepResult.pass(line) : StepResult.fail("Ablehnung ohne eindeutigen Grund (leerer Akku): " + line);
    }
}
