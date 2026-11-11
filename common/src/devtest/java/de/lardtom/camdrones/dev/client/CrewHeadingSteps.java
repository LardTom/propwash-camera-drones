package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.hud.HudHooks;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CrewHeadingSteps {
    public static final int CINE_NUMBER = 561;
    public static final int COMPACT_NUMBER = 562;
    public static final String CINE = "gimbal: Kameramann – Kino hält die Weltrichtung, wenn der Pilot giert, Schwenk nach Osten bleibt im Osten";
    public static final String COMPACT = "gimbal: Kameramann – Kompakt hält die Richtung bis zum Anschlag 25° und zeigt GIMBAL LIMIT";
    public static final Identifier FLIGHT = CamDrones.id("selftest_yaw");
    public static final String OPERATOR_INPUT = "propwash.test.operatorInput";
    public static final String NEUTRAL = "0,0,0,0";
    public static final String GIMBAL_SOUND = "gimbal.calibrate";
    public static final double HEIGHT = 6.0;
    public static final double TOLERANCE = 1.0;
    public static final double EAST = 90.0;
    public static final double EAST_TOLERANCE = 2.0;
    public static final double EAST_NEAR = 0.8;
    public static final double ARRIVED = 1.5;
    public static final double STILL = 0.2;
    public static final int STILL_TICKS = 20;
    public static final int SETTLE_TICKS = 60;
    public static final int TURN_TICKS = 20 * 20;
    public static final int TILT_TICKS = 10;
    public static final int RECENTER_TICKS = 6;
    private static final UUID PILOT = UUID.nameUUIDFromBytes("camdrones-selftest-crew-pilot".getBytes(StandardCharsets.UTF_8));
    private static final String CAMERA_AT = "crewheading.camera";
    private static final String FRAME_AT = "crewheading.frame";
    private static final String WORLD = "crewheading.world";
    private static final String MARK = "crewheading.mark";
    private static final String DEVIATION = "crewheading.deviation";
    private static final String SINCE = "crewheading.since";
    private static final String LAST_FRAME = "crewheading.lastFrame";
    private static final String NOTES = "crewheading.notes";
    private static final String DIRECTION = "crewheading.direction";
    private static final int STEP_TICKS = 20 * 180;
    private static final List<String> PLAYED = new CopyOnWriteArrayList<>();
    private static final SoundEventListener LISTENER = CrewHeadingSteps::played;
    private static volatile @Nullable YawAssist assist;
    private static @Nullable String savedInput;
    private static boolean inputSaved;
    private static boolean listening;

    private CrewHeadingSteps() {
    }

    public static void register() {
        SelfTest.clientStep(CINE_NUMBER, CINE, STEP_TICKS, start(CamPresets.CINE, -100, 100)
                .then("Pilot giert 90°: Bild bleibt in der Weltrichtung", (ctx, ticks) -> yaw(ctx, ticks, 90.0, true, false))
                .then("Pilot giert auf 180°: Bild bleibt in der Weltrichtung", (ctx, ticks) -> yaw(ctx, ticks, 180.0, true, false))
                .then("Kameramann schwenkt nach Osten", CrewHeadingSteps::panEast)
                .then("Pilot giert auf 225°: Bild bleibt im Osten", (ctx, ticks) -> yaw(ctx, ticks, 225.0, true, false))
                .then("kein Ton beim Gieren und Schwenken", CrewHeadingSteps::quiet)
                .then("Kameramann neigt und zentriert (Aktion 2): Drohnen-Vorne, ein Ton", CrewHeadingSteps::recenter)
                .then("Ergebnis", CrewHeadingSteps::result), CrewHeadingSteps::cleanup);
        SelfTest.clientStep(COMPACT_NUMBER, COMPACT, STEP_TICKS, start(CamPresets.COMPACT, -112, 100)
                .then("Pilot giert 15°: Bild bleibt, kein Anschlag", (ctx, ticks) -> yaw(ctx, ticks, 15.0, false, false))
                .then("Pilot giert 40°: Gimbal am Anschlag 25°, GIMBAL LIMIT", (ctx, ticks) -> yaw(ctx, ticks, 40.0, false, true))
                .then("Bildschirmfoto Anschlag", (ctx, ticks) -> Shots.shot(ctx, ticks, "crew_gimbal_limit"))
                .then("Kameramann schwenkt vom Anschlag zurück in die Grenze", (ctx, ticks) -> pan(ctx, ticks, 6, false))
                .then("Kameramann schwenkt bis zum anderen Anschlag", (ctx, ticks) -> pan(ctx, ticks, 50, true))
                .then("kein Ton beim Gieren und Schwenken", CrewHeadingSteps::quiet)
                .then("Ergebnis", CrewHeadingSteps::result), CrewHeadingSteps::cleanup);
    }

    private static Script start(CamPresets.Entry entry, int dx, int dz) {
        return Script.create()
                .run("Tonliste starten, Kameramann-Eingaben neutral", () -> {
                    listen();
                    input(NEUTRAL);
                })
                .server("Drohne " + entry.path() + " setzen und ohne Pilot auf " + Flight.format(HEIGHT) + " m steigen",
                        (server, state) -> launch(server, state, entry, dx, dz))
                .then("Drohne schwebt", CrewHeadingSteps::hovering)
                .run("Tonmarke", () -> PLAYED.clear())
                .server("Spieler als Kameramann binden", CrewHeadingSteps::bind)
                .then("Kameramann-Bild", CrewHeadingSteps::operating)
                .then("Übernahme: Blick nach Drohnen-Vorne, ein Ton " + GIMBAL_SOUND, CrewHeadingSteps::takeOver);
    }

    private static void launch(MinecraftServer server, Map<String, Object> state, CamPresets.Entry entry, int dx, int dz) {
        Pilot.spawn(server, state, entry, Arena.ground(dx, dz), 0.0F);
        ServerPlayer player = Arena.player(server);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        YawAssist yaw = new YawAssist();
        assist = yaw;
        AutonomyResult result = PropwashApi.drones().autonomy().start(drone, player, yaw,
                AutonomyOptions.of(FLIGHT).label("YAW").takeoff(HEIGHT));
        if (!result.started()) {
            throw new IllegalStateException("Flug ohne Pilot startet nicht: " + result);
        }
    }

    private static void bind(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        long now = System.currentTimeMillis();
        CrewBook.Facts facts = new CrewBook.Facts(true, true, true, 0.0, false);
        CrewBook book = CrewService.book();
        CrewBook.Verdict invited = book.invite(drone.getUUID(), PILOT, player.getUUID(), facts, now);
        CrewBook.Verdict accepted = book.accept(player.getUUID(), facts, now, (droneId, operator) -> PropwashApi.drones().operators().bind(drone, player));
        if (!invited.ok() || !accepted.ok()) {
            throw new IllegalStateException("Kameramann nicht gebunden: Einladung " + invited + ", Annahme " + accepted);
        }
        CrewData.set(drone, CrewData.get(drone).withOperator(Optional.of(player.getUUID())));
    }

    private static StepResult hovering(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        YawAssist yaw = assist;
        boolean ready = yaw != null && yaw.anchored;
        double height = drone.map(d -> d.getY() - CamFlights.surface()).orElse(Double.NaN);
        if (ready && height > HEIGHT - 1.5 && ticks > 40) {
            return StepResult.pass("schwebt " + Flight.format(height) + " m über dem Boden, Flug " + FLIGHT);
        }
        return ticks > 600 ? StepResult.fail("Drohne steigt nicht (Höhe " + Flight.format(height) + " m, Flughilfe " + ready + ")")
                : StepResult.pending();
    }

    private static StepResult operating(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        GimbalControl control = GimbalClient.control();
        boolean operator = drone.isPresent() && PropwashClientApi.input().operating() && control.role() == GimbalControl.Role.OPERATOR
                && control.droneId() == drone.get().getId();
        if (operator && Minecraft.getInstance().getCameraEntity() == drone.get()) {
            return StepResult.pass("Kameramann sieht das Bild der Drohne");
        }
        return ticks > 400 ? StepResult.fail("kein Kameramann-Bild (operating " + PropwashClientApi.input().operating() + ", Rolle "
                + control.role() + ")") : StepResult.pending();
    }

    private static StepResult takeOver(SelfTest.StepContext ctx, int ticks) {
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        Optional<Double> frame = frameHeading(ctx);
        if (frame.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double camera = cameraHeading();
        GimbalControl control = GimbalClient.control();
        if (!control.setting().locked()) {
            return StepResult.fail("Kameramann schwenkt nicht weltbezogen: Modus " + control.setting().mode());
        }
        double offNose = Compass.wrap180(camera - frame.get());
        if (Math.abs(offNose) > TOLERANCE) {
            return StepResult.fail("Bild nach der Übernahme " + Flight.format(offNose) + "° neben Drohnen-Vorne");
        }
        long tones = PLAYED.stream().filter(GIMBAL_SOUND::equals).count();
        if (tones != 1) {
            return StepResult.fail(GIMBAL_SOUND + " " + tones + "-mal bei der Übernahme statt einmal");
        }
        ctx.state().put(CAMERA_AT, camera);
        ctx.state().put(FRAME_AT, frame.get());
        ctx.state().put(WORLD, control.setting().heading());
        ctx.state().put(MARK, PLAYED.size());
        notes(ctx).add("Übernahme: Bild " + Flight.format(camera) + "°, Nase " + Flight.format(frame.get()) + "°, einmal " + GIMBAL_SOUND);
        return StepResult.pass(notes(ctx).getLast());
    }

    private static StepResult yaw(SelfTest.StepContext ctx, int ticks, double offset, boolean fullPan, boolean stop) {
        Map<String, Object> state = ctx.state();
        YawAssist yaw = assist;
        Optional<Entity> drone = CamFlights.clientDrone(state);
        Optional<Double> frame = frameHeading(ctx);
        if (yaw == null || drone.isEmpty() || frame.isEmpty()) {
            return StepResult.fail("Drohne oder Flughilfe fehlt");
        }
        double startFrame = (double) state.get(FRAME_AT);
        double world = GimbalClient.control().setting().heading();
        double camera = cameraHeading();
        GimbalLimits limits = GimbalScenes.limits(drone.get()).orElse(GimbalLimits.DEFAULT);
        if (ticks == 0) {
            yaw.turn(offset);
            state.put(DEVIATION, 0.0);
            state.put(SINCE, 0);
            state.put(LAST_FRAME, frame.get());
            return StepResult.pending();
        }
        if (fullPan) {
            state.put(DEVIATION, Math.max((double) state.get(DEVIATION), Math.abs(Compass.wrap180(camera - world))));
        }
        double last = (double) state.get(LAST_FRAME);
        state.put(LAST_FRAME, frame.get());
        if (Math.abs(Compass.wrap180(frame.get() - last)) > STILL) {
            state.put(SINCE, ticks);
        }
        double turned = Math.abs(Compass.wrap180(frame.get() - startFrame));
        boolean arrived = Math.abs(turned - Math.abs(Compass.wrap180(offset))) < ARRIVED && ticks - (int) state.get(SINCE) >= STILL_TICKS;
        if (!arrived) {
            return ticks > TURN_TICKS ? StepResult.fail("Drohne giert nicht um " + Flight.format(offset) + "° (gedreht " + Flight.format(turned) + "°)")
                    : StepResult.pending();
        }
        double expected = limits.heading(world, frame.get());
        double error = Math.abs(Compass.wrap180(camera - expected));
        boolean shown = limitShown(drone.get());
        String measured = "Nase " + Flight.format(turned) + "° gedreht, Bild " + Flight.format(camera) + "°, Weltrichtung "
                + Flight.format(world) + "°, erwartet " + Flight.format(expected) + "° (Abweichung " + Flight.format(error) + "°"
                + (fullPan ? ", größte beim Gieren " + Flight.format((double) state.get(DEVIATION)) + "°" : "") + "), Bild "
                + Flight.format(Compass.wrap180(camera - frame.get())) + "° neben der Nase, GIMBAL LIMIT " + (shown ? "an" : "aus");
        if (error > TOLERANCE || fullPan && (double) state.get(DEVIATION) > TOLERANCE) {
            return StepResult.fail(measured);
        }
        if (shown != stop) {
            return StepResult.fail(measured + ", erwartet GIMBAL LIMIT " + (stop ? "an" : "aus") + " (OSD " + osdWarnings() + ")");
        }
        if (stop && !(Math.abs(Math.abs(Compass.wrap180(camera - frame.get())) - GimbalLimits.LIMITED_YAW) <= TOLERANCE)) {
            return StepResult.fail(measured + ", nicht am Anschlag " + Flight.format(GimbalLimits.LIMITED_YAW) + "°");
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    private static StepResult panEast(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        double world = GimbalClient.control().setting().heading();
        double error = Compass.wrap180(EAST - world);
        if (!state.containsKey(SINCE + ".east")) {
            boolean near = Math.abs(error) < EAST_NEAR;
            input(near ? NEUTRAL : String.format(Locale.ROOT, "%.3f,0,0,0", Math.clamp(error / 10.0, -1.0, 1.0)));
            int nearSince = (int) state.computeIfAbsent(SINCE + ".near", key -> ticks);
            if (!near) {
                state.put(SINCE + ".near", ticks);
            } else if (ticks - nearSince >= STILL_TICKS) {
                state.put(SINCE + ".east", ticks);
            }
            return ticks > 600 ? StepResult.fail("Schwenk kommt nicht nach Osten (Weltrichtung " + Flight.format(world) + "°)") : StepResult.pending();
        }
        if (ticks - (int) state.get(SINCE + ".east") < SETTLE_TICKS) {
            return StepResult.pending();
        }
        state.remove(SINCE + ".east");
        state.remove(SINCE + ".near");
        double camera = cameraHeading();
        double off = Compass.wrap180(camera - EAST);
        String measured = "Kameramann schwenkt nach Osten: Weltrichtung " + Flight.format(world) + "°, Bild " + Flight.format(camera) + "°";
        if (Math.abs(off) > EAST_TOLERANCE || Math.abs(Compass.wrap180(camera - world)) > TOLERANCE) {
            return StepResult.fail(measured + " (Osten ± " + Flight.format(EAST_TOLERANCE) + "°)");
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    private static StepResult pan(SelfTest.StepContext ctx, int ticks, int holdTicks, boolean stop) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<Double> frame = frameHeading(ctx);
        if (drone.isEmpty() || frame.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (ticks == 0) {
            ctx.state().put(CAMERA_AT + ".pan", cameraHeading());
            ctx.state().computeIfAbsent(DIRECTION, key -> -Math.signum(Compass.wrap180(cameraHeading() - frame.get())));
        }
        double stick = (double) ctx.state().get(DIRECTION);
        input(ticks < holdTicks ? String.format(Locale.ROOT, "%.3f,0,0,0", stick) : NEUTRAL);
        if (ticks < holdTicks + SETTLE_TICKS) {
            return StepResult.pending();
        }
        GimbalLimits limits = GimbalScenes.limits(drone.get()).orElse(GimbalLimits.DEFAULT);
        double world = GimbalClient.control().setting().heading();
        double camera = cameraHeading();
        double before = (double) ctx.state().get(CAMERA_AT + ".pan");
        double expected = limits.heading(world, frame.get());
        double moved = Math.abs(Compass.wrap180(camera - before));
        double relative = Compass.wrap180(camera - frame.get());
        boolean shown = limitShown(drone.get());
        String measured = "Schwenk " + Flight.format(moved) + "°: Bild " + Flight.format(camera) + "°, " + Flight.format(relative)
                + "° neben der Nase, Weltrichtung " + Flight.format(world) + "°, GIMBAL LIMIT " + (shown ? "an" : "aus");
        if (moved < 5.0 || Math.abs(Compass.wrap180(camera - expected)) > TOLERANCE || Math.abs(relative) > limits.yawLimit() + TOLERANCE) {
            return StepResult.fail(measured + ", erwartet " + Flight.format(expected) + "° innerhalb ± " + Flight.format(limits.yawLimit()) + "°");
        }
        if (shown != stop) {
            return StepResult.fail(measured + ", erwartet GIMBAL LIMIT " + (stop ? "an" : "aus") + " (OSD " + osdWarnings() + ")");
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    private static StepResult quiet(SelfTest.StepContext ctx, int ticks) {
        long tones = tonesSinceMark(ctx);
        if (tones > 0) {
            return StepResult.fail(GIMBAL_SOUND + " " + tones + "-mal beim Gieren und Schwenken");
        }
        ctx.state().put(MARK, PLAYED.size());
        notes(ctx).add("Gieren und Schwenken ohne " + GIMBAL_SOUND);
        return StepResult.pass(notes(ctx).getLast());
    }

    private static StepResult recenter(SelfTest.StepContext ctx, int ticks) {
        Optional<Double> frame = frameHeading(ctx);
        if (frame.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (ticks == TILT_TICKS) {
            ctx.state().put(CAMERA_AT + ".recenter", Compass.wrap180(cameraHeading() - frame.get()));
        }
        input(ticks < TILT_TICKS ? "0,-1,0,0" : ticks < TILT_TICKS + RECENTER_TICKS ? NEUTRAL + ",2" : NEUTRAL);
        if (ticks < TILT_TICKS + RECENTER_TICKS + SETTLE_TICKS) {
            return StepResult.pending();
        }
        double camera = cameraHeading();
        double offNose = Compass.wrap180(camera - frame.get());
        double before = (double) ctx.state().get(CAMERA_AT + ".recenter");
        double tilt = GimbalClient.tilt(CamFlights.clientDrone(ctx.state()).orElseThrow());
        long tones = tonesSinceMark(ctx);
        String measured = "Zentrieren: Bild vorher " + Flight.format(before) + "°, danach " + Flight.format(offNose) + "° neben der Nase, Neigung "
                + Flight.format(tilt) + "°, " + tones + "-mal " + GIMBAL_SOUND;
        if (Math.abs(before) < 10.0 || Math.abs(offNose) > TOLERANCE || Math.abs(tilt) > TOLERANCE || tones != 1) {
            return StepResult.fail(measured);
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    private static long tonesSinceMark(SelfTest.StepContext ctx) {
        List<String> all = List.copyOf(PLAYED);
        int mark = Math.min((int) ctx.state().get(MARK), all.size());
        return all.subList(mark, all.size()).stream().filter(GIMBAL_SOUND::equals).count();
    }

    private static StepResult result(SelfTest.StepContext ctx, int ticks) {
        return StepResult.pass(String.join("; ", notes(ctx)));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        restoreInput();
        StepResult result = Script.server(ctx, ctx.tick(), (server, state) -> {
            for (Entity drone : Arena.drones(Arena.level(server))) {
                if (CrewService.book().operator(drone.getUUID()).isPresent()) {
                    CrewService.book().release(drone.getUUID());
                    PropwashApi.drones().operators().unbind(drone);
                    CrewData.set(drone, CrewData.get(drone).withOperator(Optional.empty()));
                }
            }
            CamFlights.stopFlights(server);
        });
        if (result.done()) {
            assist = null;
        }
        return result;
    }

    private static boolean limitShown(Entity drone) {
        return HudHooks.gimbalStop(drone) && osdWarnings().contains(CamOsd.Warning.GIMBAL_LIMIT.text());
    }

    private static List<String> osdWarnings() {
        return CamOsd.warnings(CamOsdSource.last()).stream().map(OsdText::text).toList();
    }

    private static double cameraHeading() {
        Vector3fc forward = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
        return Compass.headingTo(forward.x(), forward.z());
    }

    private static Optional<Double> frameHeading(SelfTest.StepContext ctx) {
        return CamFlights.clientDrone(ctx.state()).flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::forward)
                .map(f -> Compass.headingTo(f.x, f.z));
    }

    private static synchronized void input(String value) {
        if (!inputSaved) {
            savedInput = System.getProperty(OPERATOR_INPUT);
            inputSaved = true;
        }
        System.setProperty(OPERATOR_INPUT, value);
    }

    private static synchronized void restoreInput() {
        if (!inputSaved) {
            return;
        }
        if (savedInput == null) {
            System.clearProperty(OPERATOR_INPUT);
        } else {
            System.setProperty(OPERATOR_INPUT, savedInput);
        }
        savedInput = null;
        inputSaved = false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    private static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        Minecraft.getInstance().getSoundManager().addListener(LISTENER);
    }

    private static void played(SoundInstance sound, WeighedSoundEvents events, float range) {
        Identifier id = sound.getIdentifier();
        if (id != null && CamDrones.MOD_ID.equals(id.getNamespace())) {
            PLAYED.add(id.getPath());
        }
    }

    private static final class YawAssist implements FlightAssist {
        private volatile boolean anchored;
        private volatile double turn;
        private double x;
        private double y;
        private double z;
        private double heading;

        void turn(double degrees) {
            this.turn = degrees;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            if (!this.anchored) {
                this.x = state.x();
                this.y = state.y();
                this.z = state.z();
                this.heading = state.headingDeg();
                this.anchored = true;
            }
            output.holdPosition(this.x, this.z);
            output.holdAltitude(this.y);
            output.heading(Compass.normalize(this.heading + this.turn));
            output.label("YAW");
        }
    }
}
