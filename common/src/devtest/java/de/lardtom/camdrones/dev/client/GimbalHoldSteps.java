package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalViews;
import de.lardtom.camdrones.client.gimbal.model.PilotView;
import de.lardtom.camdrones.client.record.model.CameraAngles;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.RecordingControl;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.hud.HudContext;
import de.lardtom.propwash.api.client.hud.HudElement;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.GimbalModel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public final class GimbalHoldSteps {
    public static final int NUMBER = 627;
    public static final String NAME = "gimbal: Handflug der Kino in S, Ausgleich von Neigen und Rollen in jedem Bild ohne 20-Hz-Stufen";
    public static final String PROBE_ID = CamDrones.MOD_ID + ":selftest_hold";
    public static final String FORWARD = "key.propwash.kb_pitch_forward";
    public static final String BACK = "key.propwash.kb_pitch_back";
    public static final String RIGHT = "key.propwash.kb_roll_right";
    public static final String LEFT = "key.propwash.kb_roll_left";
    public static final String CSV = "camdrones-gimbal-hold.csv";
    public static final int STICK_TICKS = 50;
    public static final int RELEASE_TICKS = 40;
    public static final int SETTLE_TICKS = 6;
    public static final double REF_AHEAD = 40.0;
    public static final double MIN_FRAME_TILT = 20.0;
    public static final double MAX_ROLL = 0.5;
    public static final double MAX_PITCH_SPAN = 0.5;
    public static final double MAX_YAW_SPAN = 1.5;
    public static final double MIN_SMOOTH = 0.75;
    public static final double MAX_BACK = 0.25;
    public static final double MOVING_DEG = 0.05;
    public static final int MIN_PAIRS = 30;
    private static final String REF = "hold.ref";
    private static final String BEFORE = "hold.before";
    private static final String MAX_TILT = "hold.maxTilt";
    private static final int STEP_TICKS = 20 * 240;
    private static final Probe PROBE = new Probe();
    private static final Set<String> STICKS = new HashSet<>();
    private static boolean installed;

    private GimbalHoldSteps() {
    }

    public static void register() {
        install();
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Pilot.launch(CamPresets.CINE, -20, -120, 0.0F))
                .append(Flight.takeoff(8.0))
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("Aufnahme mit Propwashs Taste starten, Pilotenbild auf dem Gimbal", GimbalHoldSteps::startRecording)
                .then("Bezugspunkt 40 m voraus am Boden, Messung leeren", GimbalHoldSteps::reference)
                .then("voll vorwärts", (ctx, ticks) -> maneuver(ctx, ticks, "vorwärts", true, STICK_TICKS, FORWARD))
                .then("loslassen, bremsen", (ctx, ticks) -> maneuver(ctx, ticks, "bremsen vorwärts", false, RELEASE_TICKS))
                .then("voll rechts", (ctx, ticks) -> maneuver(ctx, ticks, "rechts", true, STICK_TICKS, RIGHT))
                .then("loslassen, bremsen", (ctx, ticks) -> maneuver(ctx, ticks, "bremsen rechts", false, RELEASE_TICKS))
                .then("voll schräg vorwärts links", (ctx, ticks) -> maneuver(ctx, ticks, "schräg", true, STICK_TICKS, FORWARD, LEFT))
                .then("loslassen, bremsen", (ctx, ticks) -> maneuver(ctx, ticks, "bremsen schräg", false, RELEASE_TICKS))
                .then("Aufnahme beenden", GimbalHoldSteps::stopRecording)
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake)
                .then("jedes Bild auswerten", GimbalHoldSteps::judge), ctx -> {
                    PROBE.stop();
                    Pilot.releaseKeys();
                    if (PropwashClientApi.recording().recording()) {
                        PropwashClientApi.recording().stop();
                    }
                    return KeyboardDeflection.restore(ctx);
                });
    }

    private static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        PropwashClientApi.registerHudElement(PROBE_ID, PROBE);
    }

    private static StepResult startRecording(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        RecordingControl recording = PropwashClientApi.recording();
        if (ticks == 0) {
            ctx.state().put(BEFORE, videos());
            Pilot.click(FlashbackSteps.RECORD_KEY);
        }
        if (recording.recording() && GimbalClient.provider().image(drone.get(), false) == PilotView.Image.GIMBAL) {
            return ticks >= 20 ? StepResult.pass() : StepResult.pending();
        }
        return ticks > 100 ? StepResult.fail("Aufnahme startet nicht oder Pilotenbild bleibt auf FPV") : StepResult.pending();
    }

    private static StepResult stopRecording(SelfTest.StepContext ctx, int ticks) {
        PROBE.stop();
        RecordingControl recording = PropwashClientApi.recording();
        if (ticks == 0) {
            recording.stop();
        }
        if (!recording.recording()) {
            return StepResult.pass();
        }
        return ticks > 100 ? StepResult.fail("Aufnahme endet nicht") : StepResult.pending();
    }

    private static StepResult reference(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = Pilot.drone();
        Optional<DroneView> view = drone.flatMap(d -> PropwashApi.drones().view(d));
        if (view.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Vec3 forward = view.get().forward();
        double h = Math.hypot(forward.x, forward.z);
        if (h < 1.0E-3) {
            return StepResult.fail("Drohne zeigt senkrecht");
        }
        Vec3 at = drone.get().position();
        Vec3 ref = new Vec3(at.x + forward.x / h * REF_AHEAD, Flight.surface(), at.z + forward.z / h * REF_AHEAD);
        ctx.state().put(REF, ref);
        PROBE.clear();
        STICKS.clear();
        return StepResult.pass();
    }

    private static StepResult maneuver(SelfTest.StepContext ctx, int ticks, String label, boolean stick, int length, String... keys) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            PROBE.stop();
            return StepResult.fail("Drohne verloren");
        }
        Minecraft.getInstance().getFramerateLimitTracker().onInputReceived();
        for (String key : List.of(FORWARD, BACK, RIGHT, LEFT)) {
            Pilot.hold(key, List.of(keys).contains(key) && ticks < length);
        }
        double tilt = GimbalSteps.frameTilt(drone.get());
        ctx.state().put(MAX_TILT, Math.max((double) ctx.state().getOrDefault(MAX_TILT, 0.0), tilt));
        if (stick) {
            STICKS.add(label);
        }
        if (ticks >= SETTLE_TICKS) {
            PROBE.record(label, drone.get().getId(), (Vec3) ctx.state().get(REF));
        } else {
            PROBE.pause();
        }
        if (ticks >= length) {
            PROBE.pause();
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static StepResult judge(SelfTest.StepContext ctx, int ticks) {
        List<Sample> all = PROBE.all();
        Path csv = Minecraft.getInstance().gameDirectory.toPath().resolve(CSV);
        write(csv, all);
        List<String> problems = new ArrayList<>();
        StringBuilder out = new StringBuilder(String.format(Locale.GERMANY, "%d Bilder, Rahmen bis %.1f° geneigt", all.size(),
                (double) ctx.state().getOrDefault(MAX_TILT, 0.0)));
        for (String label : PROBE.labels()) {
            List<Sample> samples = PROBE.samples(label);
            out.append("; ").append(describe(label, samples, problems));
        }
        newest(beforeVideos(ctx)).ifPresent(p -> out.append("; Video ").append(p.getFileName()));
        out.append("; Messung ").append(csv.getFileName());
        String line = out.toString();
        CamDrones.LOG.info("[Kamera-Selbsttest] Ausgleich im Handflug: {}", line);
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join(", ", problems) + " – " + line);
        }
        return StepResult.pass(line);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> beforeVideos(SelfTest.StepContext ctx) {
        return (Set<String>) ctx.state().getOrDefault(BEFORE, Set.of());
    }

    private static String describe(String label, List<Sample> samples, List<String> problems) {
        if (samples.size() < MIN_PAIRS) {
            problems.add(label + ": nur " + samples.size() + " Bilder");
            return label + ": " + samples.size() + " Bilder";
        }
        Steps refV = steps(samples, Sample::refV, false);
        Steps refH = steps(samples, Sample::refH, false);
        Steps yaw = steps(samples, Sample::camYaw, true);
        double maxRoll = samples.stream().mapToDouble(s -> Math.abs(s.camRoll())).max().orElse(0.0);
        double pitchSpan = span(samples, Sample::camPitch);
        double yawSpan = spanAngle(samples, Sample::camYaw);
        double droneYawSpan = spanAngle(samples, Sample::droneYaw);
        double frameTilt = samples.stream().mapToDouble(Sample::frameTilt).max().orElse(0.0);
        long fpv = samples.stream().filter(s -> !s.gimbalImage()).count();
        String text = String.format(Locale.GERMANY,
                "%s: %d Bilder, Rahmen bis %.1f°, Drohne giert %.2f°; Kamera rollt höchstens %.2f°, Neigung Spanne %.2f°, Gieren Spanne %.2f°; "
                        + "Bild des Bezugspunkts senkrecht %s, Rücklauf %.0f %%, waagerecht %s, Rücklauf %.0f %%",
                label, samples.size(), frameTilt, droneYawSpan, maxRoll, pitchSpan, yawSpan, refV.text("°"), refV.back() * 100.0, refH.text("°"),
                refH.back() * 100.0);
        if (fpv > 0) {
            problems.add(label + ": " + fpv + " Bilder nicht von der Gimbal-Kamera");
        }
        if (STICKS.contains(label) && frameTilt < MIN_FRAME_TILT) {
            problems.add(String.format(Locale.GERMANY, "%s: Rahmen neigt nur %.1f° (mindestens %.0f°)", label, frameTilt, MIN_FRAME_TILT));
        }
        if (maxRoll > MAX_ROLL) {
            problems.add(String.format(Locale.GERMANY, "%s: Horizont kippt um %.2f° (erlaubt %.1f°)", label, maxRoll, MAX_ROLL));
        }
        if (pitchSpan > MAX_PITCH_SPAN) {
            problems.add(String.format(Locale.GERMANY, "%s: Neigung der Kamera wandert um %.2f° (erlaubt %.1f°)", label, pitchSpan, MAX_PITCH_SPAN));
        }
        if (yawSpan > droneYawSpan + MAX_YAW_SPAN) {
            problems.add(String.format(Locale.GERMANY, "%s: Kamera schwenkt um %.2f°, die Drohne giert nur %.2f° (erlaubt %.1f° mehr)", label, yawSpan,
                    droneYawSpan, MAX_YAW_SPAN));
        }
        for (Steps image : List.of(refV, refH)) {
            if (image.crossRate() >= MOVING_DEG && !(image.ratio() >= MIN_SMOOTH)) {
                problems.add(String.format(Locale.GERMANY, "%s: Bild springt im Tick-Takt (zwischen den Ticks %.0f %% der Bewegung über die Tickgrenze, "
                        + "nötig %.0f %%)", label, image.ratio() * 100.0, MIN_SMOOTH * 100.0));
            }
            if (image.back() > MAX_BACK) {
                problems.add(String.format(Locale.GERMANY, "%s: Bild läuft zwischen den Ticks zurück (%.0f %% der Ticks, erlaubt %.0f %%)", label,
                        image.back() * 100.0, MAX_BACK * 100.0));
            }
        }
        return text + ", " + yaw.text("°").replace("im Tick", "Gieren im Tick");
    }

    static double span(List<Sample> samples, ToDoubleFunction<Sample> value) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Sample s : samples) {
            double v = value.applyAsDouble(s);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    static double spanAngle(List<Sample> samples, ToDoubleFunction<Sample> value) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        double base = value.applyAsDouble(samples.getFirst());
        double min = 0.0;
        double max = 0.0;
        for (Sample s : samples) {
            double d = Compass.wrap180(value.applyAsDouble(s) - base);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return max - min;
    }

    record Sample(String label, long tick, float partial, long nanos, double camPitch, double camRoll, double camYaw, double eyeX, double eyeY,
                  double eyeZ, double refV, double refH, double framePitch, double frameRoll, double frameHeading, double frameTilt,
                  double modelPitch, double modelRoll, double modelHeading, double droneYaw, boolean gimbalImage) {
        double seconds() {
            return this.nanos / 1.0E9;
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
            return String.format(Locale.GERMANY, "im Tick %.2f%s/s, über die Tickgrenze %.2f%s/s (%.0f %%)", this.intraRate, unit,
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
            double dt = b.seconds() - a.seconds();
            if (!(dt > 1.0E-5) || dt > 0.1) {
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
            if (tick[2] == 0.0 || Math.abs(tick[1]) <= GimbalSmoothSteps.BACK_MIN_DEG) {
                continue;
            }
            moving++;
            if (Math.abs(tick[0]) > GimbalSmoothSteps.BACK_MIN_DEG && Math.signum(tick[0]) != Math.signum(tick[1])) {
                back++;
            }
        }
        return new Steps(intra, cross, intraTime > 0.0 ? intraAbs / intraTime : 0.0, crossTime > 0.0 ? crossAbs / crossTime : 0.0, back, moving);
    }

    static double droneYaw(Entity drone) {
        Optional<DroneView> view = drone == null ? Optional.empty() : PropwashApi.drones().view(drone);
        if (view.isEmpty()) {
            return Double.NaN;
        }
        Vec3 f = view.get().forward();
        Vec3 u = view.get().up();
        Quaternionf q = CameraAngles.frame(f.x, f.y, f.z, u.x, u.y, u.z, new Quaternionf());
        return Compass.normalize(-Math.toDegrees(2.0 * Math.atan2(q.y, q.w)));
    }

    private static void write(Path file, List<Sample> samples) {
        StringBuilder out = new StringBuilder("label;tick;partial;nanos;camPitch;camRoll;camYaw;eyeX;eyeY;eyeZ;refV;refH;framePitch;frameRoll;"
                + "frameHeading;frameTilt;modelPitch;modelRoll;modelHeading;droneYaw;gimbal\n");
        for (Sample s : samples) {
            out.append(String.format(Locale.ROOT, "%s;%d;%.4f;%d;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.5f;%.4f;%.4f;%.4f;%.4f;%.5f;%.5f;%.5f;%.5f;%b%n",
                    s.label(), s.tick(), s.partial(), s.nanos(), s.camPitch(), s.camRoll(), s.camYaw(), s.eyeX(), s.eyeY(), s.eyeZ(), s.refV(),
                    s.refH(), s.framePitch(), s.frameRoll(), s.frameHeading(), s.frameTilt(), s.modelPitch(), s.modelRoll(), s.modelHeading(),
                    s.droneYaw(), s.gimbalImage()));
        }
        try {
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            CamDrones.LOG.warn("[Kamera-Selbsttest] Messung nicht geschrieben: {}", e.toString());
        }
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
        private final List<String> labels = new ArrayList<>();
        private String label;
        private int drone = -1;
        private Vec3 reference;

        synchronized void record(String value, int droneId, Vec3 ref) {
            this.label = value;
            this.drone = droneId;
            this.reference = ref;
            if (!this.labels.contains(value)) {
                this.labels.add(value);
            }
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
            this.labels.clear();
            this.label = null;
            this.reference = null;
        }

        synchronized List<String> labels() {
            return List.copyOf(this.labels);
        }

        synchronized List<Sample> all() {
            return List.copyOf(this.samples);
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
            Vector3fc left = camera.leftVector();
            Vec3 to = this.reference.subtract(eye).normalize();
            double pitch = Math.toDegrees(Math.asin(Math.clamp(forward.y(), -1.0, 1.0)));
            double roll = Math.toDegrees(Math.asin(Math.clamp(left.y(), -1.0, 1.0)));
            double yaw = Compass.headingTo(forward.x(), forward.z());
            double refV = Math.toDegrees(Math.asin(Math.clamp(to.x * up.x() + to.y * up.y() + to.z * up.z(), -1.0, 1.0)));
            double refH = Math.toDegrees(Math.asin(Math.clamp(to.x * left.x() + to.y * left.y() + to.z * left.z(), -1.0, 1.0)));
            FlightTelemetry telemetry = context.telemetry();
            Entity drone = context.drone();
            double frameTilt = drone == null ? Double.NaN : GimbalSteps.frameTilt(drone);
            GimbalViews.View view = GimbalClient.views().peek(this.drone);
            GimbalModel model = view == null ? null : view.camera();
            boolean gimbal = drone != null && GimbalClient.provider().image(drone, false) == PilotView.Image.GIMBAL;
            this.samples.add(new Sample(this.label, mc.level.getGameTime(), context.partialTick(), System.nanoTime(), pitch, roll, yaw, eye.x,
                    eye.y, eye.z, refV, refH, telemetry.pitchDeg(), telemetry.rollDeg(), telemetry.headingDeg(), frameTilt,
                    model == null ? Double.NaN : model.pitchDeg(), model == null ? Double.NaN : model.rollDeg(),
                    model == null ? Double.NaN : model.headingDeg(), droneYaw(drone), gimbal));
        }
    }
}
