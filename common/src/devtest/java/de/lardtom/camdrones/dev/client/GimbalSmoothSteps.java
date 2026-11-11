package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.hud.HudContext;
import de.lardtom.propwash.api.client.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

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
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public final class GimbalSmoothSteps {
    public static final int MISSION_NUMBER = 625;
    public static final int CREW_NUMBER = 626;
    public static final String MISSION = "gimbal: Livebild der Kino in der Mission ohne Pilot, Neigung und Schwenk ohne 20-Hz-Stufen";
    public static final String CREW = "gimbal: Livebild der Kino, Neigung vom Kameramann alle 100 ms ohne Stufen";
    public static final String PROBE_ID = CamDrones.MOD_ID + ":selftest_frames";
    public static final double MIN_SMOOTH = 0.5;
    public static final double MAX_BACK = 0.25;
    public static final double STILL_FACTOR = 0.1;
    public static final double MAX_STILL = 0.2;
    public static final int MIN_PAIRS = 30;
    public static final double MOVING_DEG = 0.05;
    public static final double MOVING_M = 0.01;
    public static final double BACK_MIN_DEG = 0.02;
    public static final int SETTLE_TICKS = 20;
    public static final double HEIGHT = 8.0;
    public static final double SPEED = 4.0;
    public static final double CREW_STEP_DEG = 1.0;
    public static final double CREW_MIN_TILT = -60.0;
    public static final int CREW_EVERY_TICKS = 2;
    private static final int DX = -60;
    private static final int DZ = -160;
    private static final String TILT = "Neigung";
    private static final String PAN = "Schwenk";
    private static final String OPERATOR = "Kameramann";
    private static final String TILT_REF = "smooth.tiltRef";
    private static final String POI = "smooth.poi";
    private static final String AHEAD = "smooth.ahead";
    private static final String ENTERED = "smooth.entered";
    private static final String BEFORE = "smooth.before";
    private static final String SENT = "smooth.sent";
    private static final int STEP_TICKS = 20 * 240;
    private static final Probe PROBE = new Probe();
    private static boolean installed;

    private GimbalSmoothSteps() {
    }

    public static void register() {
        install();
        SelfTest.clientStep(MISSION_NUMBER, MISSION, STEP_TICKS, Script.create()
                .server("Kino abstellen und koppeln, Mission: Neigung 0° bis −60° auf 24 m, dann Schwenk auf einen Point of Interest mit Steigflug, "
                        + "Aufnahme an Punkt 1 bis 3", (server, state) -> {
                            spawn(server, state);
                            mission(server, state);
                        })
                .then("Drohne am Client", GimbalSmoothSteps::clientDrone)
                .then("Messung leeren, Aufnahme-Ordner merken", (ctx, ticks) -> {
                    PROBE.clear();
                    ctx.state().put(BEFORE, videos());
                    return StepResult.pass();
                })
                .sleep(10)
                .then("Mission ohne Pilot starten", GimbalSmoothSteps::start)
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Livebild öffnen", GimbalSmoothSteps::watch)
                .then("jedes Bild im Livebild messen, bis die Mission fertig ist", GimbalSmoothSteps::measureMission)
                .then("Kamera im Livebild ohne 20-Hz-Stufen und ohne Rücklauf", GimbalSmoothSteps::judgeMission), GimbalSmoothSteps::stop);
        SelfTest.clientStep(CREW_NUMBER, CREW, STEP_TICKS, Script.create()
                .server("Kino abstellen und koppeln, Mission mit freiem Gimbal", (server, state) -> {
                    spawn(server, state);
                    crewMission(server, state);
                })
                .then("Drohne am Client", GimbalSmoothSteps::clientDrone)
                .run("Messung leeren", PROBE::clear)
                .sleep(10)
                .then("Mission ohne Pilot starten", GimbalSmoothSteps::start)
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Livebild öffnen", GimbalSmoothSteps::watch)
                .then("Kameramann neigt alle 100 ms um 1°, jedes Bild im Livebild messen", GimbalSmoothSteps::measureCrew)
                .then("Kamera im Livebild ohne Stufen", GimbalSmoothSteps::judgeCrew), GimbalSmoothSteps::stop);
    }

    private static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        PropwashClientApi.registerHudElement(PROBE_ID, PROBE);
    }

    private static void spawn(MinecraftServer server, Map<String, Object> state) {
        Pilot.spawn(server, state, CamPresets.CINE, Arena.ground(DX, DZ), 0.0F);
        Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        CrewData.set(drone, CrewData.get(drone).withSetting(GimbalSetting.CENTER));
    }

    private static void mission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double x = drone.getX();
        double z = drone.getZ();
        double ground = drone.getY();
        double y = ground + HEIGHT;
        Vec3 tiltRef = new Vec3(x, ground, z + 30.0);
        Vec3 poi = new Vec3(x + 10.0, ground, z + 46.0);
        List<Waypoint> waypoints = List.of(
                Waypoint.at(x, y, z + 6.0).withTilt(0).withAction(Waypoint.Action.RECORD_START),
                Waypoint.at(x, y, z + 30.0).withTilt(-60),
                Waypoint.at(x, y + 4.0, z + 54.0).facing(0).withAction(Waypoint.Action.RECORD_STOP));
        state.put(TILT_REF, tiltRef);
        state.put(POI, poi);
        CamFlights.mission(server, state, Mission.of(waypoints, List.of(PointOfInterest.at(poi.x, poi.y, poi.z, 0.0)))
                .withEnd(Mission.End.HOVER).withBaseSpeed(SPEED));
    }

    private static void crewMission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double x = drone.getX();
        double z = drone.getZ();
        double ground = drone.getY();
        double y = ground + HEIGHT;
        List<Waypoint> waypoints = List.of(
                Waypoint.at(x, y, z + 6.0).withHeading(Waypoint.Heading.FREE, 0.0),
                Waypoint.at(x, y, z + 50.0).withHeading(Waypoint.Heading.FREE, 0.0));
        state.put(AHEAD, new Vec3(x, ground, z + 40.0));
        CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(SPEED));
    }

    private static StepResult clientDrone(SelfTest.StepContext ctx, int ticks) {
        return CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending();
    }

    private static StepResult start(SelfTest.StepContext ctx, int ticks) {
        return CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0));
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

    private static StepResult measureMission(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty() || mc.getCameraEntity() != drone.get()) {
            PROBE.stop();
            return StepResult.fail("Livebild endet während der Mission");
        }
        mc.getFramerateLimitTracker().onInputReceived();
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        NavLogic.MissionState state = status.map(MissionStatus::state).orElse(NavLogic.MissionState.IDLE);
        int index = status.map(MissionStatus::index).orElse(0);
        String wanted = state == NavLogic.MissionState.RUNNING ? index == 1 ? TILT : index == 2 ? PAN : null : null;
        Vec3 ref = TILT.equals(wanted) ? (Vec3) ctx.state().get(TILT_REF) : PAN.equals(wanted) ? (Vec3) ctx.state().get(POI) : null;
        follow(ctx, wanted, ref);
        if (state == NavLogic.MissionState.DONE) {
            PROBE.stop();
            return StepResult.pass(PROBE.count(TILT) + " Bilder in der Neigung, " + PROBE.count(PAN) + " im Schwenk");
        }
        return ticks > 20 * 150 ? StepResult.fail("Mission kommt nicht zu Ende: Zustand " + state + ", Punkt " + index) : StepResult.pending();
    }

    private static void follow(SelfTest.StepContext ctx, String wanted, Vec3 ref) {
        Object entered = ctx.state().get(ENTERED);
        String current = entered instanceof String s ? s : null;
        if (wanted == null) {
            ctx.state().remove(ENTERED);
            PROBE.pause();
            return;
        }
        if (!wanted.equals(current)) {
            ctx.state().put(ENTERED, wanted);
            ctx.state().put(ENTERED + ".at", ctx.tick());
            PROBE.pause();
            return;
        }
        int since = ctx.tick() - (int) ctx.state().getOrDefault(ENTERED + ".at", ctx.tick());
        if (since >= SETTLE_TICKS) {
            PROBE.record(wanted, ref);
        }
    }

    private static StepResult measureCrew(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty() || mc.getCameraEntity() != drone.get()) {
            PROBE.stop();
            return StepResult.fail("Livebild endet während der Mission");
        }
        mc.getFramerateLimitTracker().onInputReceived();
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        boolean flying = status.map(s -> s.state() == NavLogic.MissionState.RUNNING && s.index() == 1).orElse(false);
        int sent = (int) ctx.state().getOrDefault(SENT, 0);
        double tilt = Math.max(CREW_MIN_TILT, -CREW_STEP_DEG * sent);
        if (!flying) {
            PROBE.pause();
            if (sent > 0) {
                return StepResult.fail("Mission endet, bevor der Kameramann fertig ist (" + Flight.format(tilt) + "°)");
            }
            return ticks > 20 * 30 ? StepResult.fail("Mission kommt nicht auf die Strecke") : StepResult.pending();
        }
        int since = (int) ctx.state().computeIfAbsent(ENTERED, key -> ctx.tick());
        if (ctx.tick() - since < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (tilt <= CREW_MIN_TILT) {
            PROBE.stop();
            return StepResult.pass(PROBE.count(OPERATOR) + " Bilder, Kameramann " + sent + " Werte bis " + Flight.format(tilt) + "°");
        }
        if ((ctx.tick() - since) % CREW_EVERY_TICKS == 0) {
            double next = Math.max(CREW_MIN_TILT, -CREW_STEP_DEG * (sent + 1));
            Map<String, Object> state = ctx.state();
            ctx.onServer(server -> {
                Entity serverDrone = Pilot.serverDrone(server, state);
                CrewData.set(serverDrone, CrewData.get(serverDrone).withSetting(GimbalSetting.CENTER.withTilt(next)));
            });
            ctx.state().put(SENT, sent + 1);
        }
        if (sent > 0) {
            PROBE.record(OPERATOR, (Vec3) ctx.state().get(AHEAD));
        }
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult judgeMission(SelfTest.StepContext ctx, int ticks) {
        List<String> problems = new ArrayList<>();
        String tilt = describe(TILT, PROBE.samples(TILT), problems, true, true, false, false);
        String pan = describe(PAN, PROBE.samples(PAN), problems, true, true, true, false);
        String video = newest((Set<String>) ctx.state().getOrDefault(BEFORE, Set.of())).map(p -> ", Video " + p.getFileName()).orElse(", kein Video");
        String line = tilt + "; " + pan + video;
        CamDrones.LOG.info("[Kamera-Selbsttest] Bildmessung im Livebild: {}", line);
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join(", ", problems) + " – " + line);
        }
        return StepResult.pass(line);
    }

    private static StepResult judgeCrew(SelfTest.StepContext ctx, int ticks) {
        List<String> problems = new ArrayList<>();
        String line = describe(OPERATOR, PROBE.samples(OPERATOR), problems, false, false, false, true);
        CamDrones.LOG.info("[Kamera-Selbsttest] Bildmessung im Livebild: {}", line);
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join(", ", problems) + " – " + line);
        }
        return StepResult.pass(line);
    }

    private static String describe(String label, List<Sample> samples, List<String> problems, boolean ticks, boolean back, boolean height,
                                   boolean frames) {
        if (samples.size() < MIN_PAIRS) {
            problems.add(label + ": nur " + samples.size() + " Bilder gemessen");
            return label + ": " + samples.size() + " Bilder";
        }
        Steps pitch = steps(samples, Sample::pitch, false);
        Steps yaw = steps(samples, Sample::yaw, true);
        Steps image = steps(samples, Sample::refV, false);
        Steps eye = steps(samples, Sample::eyeY, false);
        Still still = still(samples, Sample::pitch);
        double fps = fps(samples);
        StringBuilder out = new StringBuilder(label).append(String.format(Locale.GERMANY,
                ": %d Bilder, %.0f Bilder/s, %d Paare im Tick; Neigung %s, steht in %.0f %% der Bilder, größter Schritt %.1f-fach des Mittels; "
                        + "Bild des Ziels senkrecht %s, Rücklauf %.0f %%; Gieren %s; Höhe %s",
                samples.size(), fps, pitch.intraPairs(), pitch.text("°"), still.share() * 100.0, still.peakRatio(), image.text("°"),
                image.back() * 100.0, yaw.text("°"), eye.text(" m")));
        if (pitch.intraPairs() < MIN_PAIRS) {
            problems.add(label + ": zu wenige Bilder je Tick (" + pitch.intraPairs() + " Paare, " + Math.round(fps) + " Bilder/s)");
            return out.toString();
        }
        if (!(pitch.crossRate() >= MOVING_DEG) && !(pitch.intraRate() >= MOVING_DEG)) {
            problems.add(label + ": Gimbal neigt sich kaum (" + pitch.text("°") + ")");
        } else if (ticks && !(pitch.ratio() >= MIN_SMOOTH)) {
            problems.add(String.format(Locale.GERMANY, "%s: Neigung springt im Tick-Takt (Bewegung zwischen den Ticks nur %.0f %% der Bewegung über die Tickgrenze, nötig %.0f %%)",
                    label, pitch.ratio() * 100.0, MIN_SMOOTH * 100.0));
        }
        if (back && image.back() > MAX_BACK) {
            problems.add(String.format(Locale.GERMANY, "%s: Bild läuft zwischen den Ticks zurück (%.0f %% der Ticks, erlaubt %.0f %%)", label,
                    image.back() * 100.0, MAX_BACK * 100.0));
        }
        if (height && eye.crossRate() >= MOVING_M && !(eye.ratio() >= MIN_SMOOTH)) {
            problems.add(String.format(Locale.GERMANY, "%s: Höhe der Kamera springt im Tick-Takt (%.0f %%)", label, eye.ratio() * 100.0));
        }
        if (frames && still.share() > MAX_STILL) {
            problems.add(String.format(Locale.GERMANY, "%s: Neigung steht in %.0f %% der Bilder und springt dann (größter Schritt %.1f-fach des Mittels, "
                    + "erlaubt %.0f %% stehende Bilder)", label, still.share() * 100.0, still.peakRatio(), MAX_STILL * 100.0));
        }
        return out.toString();
    }

    record Still(int pairs, int still, double mean, double peak) {
        double share() {
            return this.pairs == 0 ? 0.0 : (double) this.still / this.pairs;
        }

        double peakRatio() {
            return this.mean > 0.0 ? this.peak / this.mean : Double.NaN;
        }
    }

    static Still still(List<Sample> samples, ToDoubleFunction<Sample> value) {
        List<Double> deltas = new ArrayList<>();
        for (int i = 1; i < samples.size(); i++) {
            double dt = samples.get(i).time() - samples.get(i - 1).time();
            if (dt > 1.0E-4 && dt <= 2.0) {
                deltas.add(Math.abs(value.applyAsDouble(samples.get(i)) - value.applyAsDouble(samples.get(i - 1))));
            }
        }
        if (deltas.isEmpty()) {
            return new Still(0, 0, 0.0, 0.0);
        }
        double mean = deltas.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double peak = deltas.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        int still = (int) deltas.stream().filter(d -> d < STILL_FACTOR * mean).count();
        return new Still(deltas.size(), still, mean, peak);
    }

    record Sample(String label, long tick, float partial, double pitch, double yaw, double eyeY, double refV) {
        double time() {
            return this.tick + (double) this.partial;
        }
    }

    record Steps(int intraPairs, int crossPairs, double intraRate, double crossRate, int backTicks, int movingTicks) {
        double ratio() {
            return this.crossRate > 0.0 ? this.intraRate / this.crossRate : Double.NaN;
        }

        double back() {
            return this.movingTicks == 0 ? 0.0 : (double) this.backTicks / this.movingTicks;
        }

        String text(String unit) {
            return String.format(Locale.GERMANY, "im Tick %.3f%s/Tick, über die Tickgrenze %.3f%s/Tick (%.0f %%)", this.intraRate, unit,
                    this.crossRate, unit, this.ratio() * 100.0);
        }
    }

    static Steps steps(List<Sample> samples, ToDoubleFunction<Sample> value, boolean angle) {
        double intraAbs = 0.0;
        double intraTime = 0.0;
        double crossAbs = 0.0;
        double crossTime = 0.0;
        int intra = 0;
        int cross = 0;
        TreeMap<Long, double[]> perTick = new TreeMap<>();
        for (int i = 1; i < samples.size(); i++) {
            Sample a = samples.get(i - 1);
            Sample b = samples.get(i);
            double dt = b.time() - a.time();
            if (!(dt > 1.0E-4) || dt > 2.0) {
                continue;
            }
            double d = value.applyAsDouble(b) - value.applyAsDouble(a);
            if (angle) {
                d = Compass.wrap180(d);
            }
            double[] tick = perTick.computeIfAbsent(b.tick(), key -> new double[3]);
            if (a.tick() == b.tick()) {
                intra++;
                intraAbs += Math.abs(d);
                intraTime += dt;
                tick[0] += d;
            } else {
                cross++;
                crossAbs += Math.abs(d);
                crossTime += dt;
                tick[1] = d;
                tick[2] = 1.0;
            }
        }
        int moving = 0;
        int back = 0;
        for (double[] tick : perTick.values()) {
            if (tick[2] == 0.0 || Math.abs(tick[1]) <= BACK_MIN_DEG) {
                continue;
            }
            moving++;
            if (Math.abs(tick[0]) > BACK_MIN_DEG && Math.signum(tick[0]) != Math.signum(tick[1])) {
                back++;
            }
        }
        return new Steps(intra, cross, intraTime > 0.0 ? intraAbs / intraTime : 0.0, crossTime > 0.0 ? crossAbs / crossTime : 0.0, back, moving);
    }

    static double fps(List<Sample> samples) {
        if (samples.size() < 2) {
            return 0.0;
        }
        double ticks = samples.getLast().time() - samples.getFirst().time();
        return ticks > 0.0 ? (samples.size() - 1) / (ticks / 20.0) : 0.0;
    }

    private static StepResult stop(SelfTest.StepContext ctx) {
        PROBE.stop();
        if (PropwashClientApi.recording().recording()) {
            PropwashClientApi.recording().stop();
        }
        return StepResult.pass();
    }

    private static Path recordings() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("propwash").resolve("recordings");
    }

    private static Set<String> videos() {
        Set<String> out = new HashSet<>();
        if (!Files.isDirectory(recordings())) {
            return out;
        }
        try (Stream<Path> files = Files.list(recordings())) {
            files.filter(p -> p.getFileName().toString().endsWith(".mp4")).forEach(p -> out.add(p.getFileName().toString()));
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private static Optional<Path> newest(Set<String> before) {
        Set<String> now = videos();
        now.removeAll(before);
        return now.stream().sorted().reduce((a, b) -> b).map(name -> recordings().resolve(name));
    }

    private static final class Probe implements HudElement {
        private final List<Sample> samples = new ArrayList<>();
        private String label;
        private Vec3 reference;

        synchronized void record(String value, Vec3 ref) {
            this.label = value;
            this.reference = ref;
        }

        synchronized void pause() {
            this.label = null;
        }

        synchronized void stop() {
            this.label = null;
            this.reference = null;
        }

        synchronized void clear() {
            this.samples.clear();
            this.label = null;
            this.reference = null;
        }

        synchronized int count(String value) {
            return this.samples(value).size();
        }

        synchronized List<Sample> samples(String value) {
            List<Sample> out = new ArrayList<>();
            for (Sample sample : this.samples) {
                if (sample.label().equals(value)) {
                    out.add(sample);
                }
            }
            return out;
        }

        @Override
        public synchronized boolean active(HudContext context) {
            return this.label != null;
        }

        @Override
        public synchronized void render(GuiGraphicsExtractor graphics, HudContext context) {
            Minecraft mc = Minecraft.getInstance();
            if (this.label == null || this.reference == null || mc.level == null) {
                return;
            }
            Camera camera = mc.gameRenderer.mainCamera();
            Vec3 eye = camera.position();
            Vector3fc forward = camera.forwardVector();
            Vector3fc up = camera.upVector();
            Vec3 to = this.reference.subtract(eye).normalize();
            double pitch = Math.toDegrees(Math.asin(Math.clamp(forward.y(), -1.0, 1.0)));
            double yaw = Compass.headingTo(forward.x(), forward.z());
            double refV = Math.toDegrees(Math.asin(Math.clamp(to.x * up.x() + to.y * up.y() + to.z * up.z(), -1.0, 1.0)));
            this.samples.add(new Sample(this.label, mc.level.getGameTime(), context.partialTick(), pitch, yaw, eye.y, refV));
        }
    }
}
