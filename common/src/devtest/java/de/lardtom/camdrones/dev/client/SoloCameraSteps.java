package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.hud.HudHooks;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SoloCameraSteps {
    public static final int NUMBER = 563;
    public static final String NAME = "gimbal: Kameramann allein – Livebild aus dem Flugplaner während der Mission ohne Pilot, Knüppel schwenken und neigen, "
            + "GIMBAL LIMIT, Aktion 2, Pause, Stopp und zurück";
    public static final String OPERATOR_INPUT = "propwash.test.operatorInput";
    public static final String NEUTRAL = "0,0,0,0";
    public static final int DX = -60;
    public static final int DZ = -150;
    public static final double HEIGHT = 8.0;
    public static final int PLAN_TILT = -30;
    public static final double LEG = 25.0;
    public static final int LEGS = 4;
    public static final double SPEED = 2.0;
    public static final double TOLERANCE = 2.0;
    public static final double TILTED = -45.0;
    public static final int TILT_TICKS = 15;
    public static final int PAN_TICKS = 30;
    public static final int PRESS_TICKS = 4;
    public static final int LONG_TICKS = 24;
    public static final int SETTLE_TICKS = 30;
    private static final String NOTES = "solo.notes";
    private static final String QUERY = "solo.query";
    private static final int STEP_TICKS = 20 * 240;
    private static @Nullable String savedInput;
    private static boolean inputSaved;

    private SoloCameraSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .run("Kameramann-Eingaben neutral", () -> input(NEUTRAL))
                .server("Tour setzen und koppeln, gerade Mission mit Neigung " + PLAN_TILT + "° an jedem Punkt", SoloCameraSteps::setup)
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .sleep(10)
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission läuft in der Luft", SoloCameraSteps::airborne)
                .then("Livebild im Flugplaner", SoloCameraSteps::openLive)
                .then("Kameramann-Bild der eigenen Drohne", SoloCameraSteps::operating)
                .then("ohne Knüppel folgt der Gimbal dem Flugplan", SoloCameraSteps::followsPlan)
                .then("Knüppel neigt den Gimbal", SoloCameraSteps::tilt)
                .then("Knüppel schwenkt bis zum Anschlag: GIMBAL LIMIT", SoloCameraSteps::panToStop)
                .append(Shots.photos("solo_gimbal_limit"))
                .then("Aktion 2 richtet nach vorn", SoloCameraSteps::recenter)
                .then("Aktion 1 pausiert die Mission", (ctx, ticks) -> missionPress(ctx, ticks, PRESS_TICKS, NavLogic.MissionState.PAUSED))
                .then("Aktion 1 setzt die Mission fort", (ctx, ticks) -> missionPress(ctx, ticks, PRESS_TICKS, NavLogic.MissionState.RUNNING))
                .then("Aktion 4 lang: zurück in die normale Ansicht, Mission läuft weiter", SoloCameraSteps::leave)
                .then("Livebild erneut", SoloCameraSteps::openLive)
                .then("wieder Kameramann-Bild", SoloCameraSteps::operating)
                .then("Aktion 1 lang bricht die Mission ab und landet", (ctx, ticks) -> missionPress(ctx, ticks, LONG_TICKS,
                        NavLogic.MissionState.ABORTED))
                .then("nach der Landung zurück in der normalen Ansicht", SoloCameraSteps::landed)
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx)))), SoloCameraSteps::cleanup);
    }

    private static void setup(MinecraftServer server, Map<String, Object> state) {
        Pilot.spawn(server, state, CamPresets.TOUR, Arena.ground(DX, DZ), 0.0F);
        Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + HEIGHT;
        List<Waypoint> points = new ArrayList<>();
        for (int i = 0; i <= LEGS; i++) {
            points.add(Waypoint.at(drone.getX(), y, drone.getZ() + 4.0 + i * LEG).withTilt(PLAN_TILT));
        }
        CamFlights.mission(server, state, Mission.of(points).withEnd(Mission.End.HOVER).withBaseSpeed(SPEED));
    }

    private static StepResult airborne(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        double height = drone.map(d -> d.getY() - CamFlights.surface()).orElse(Double.NaN);
        boolean running = status.map(s -> s.state() == NavLogic.MissionState.RUNNING).orElse(false);
        if (running && height > HEIGHT - 1.5) {
            return StepResult.pass("Mission läuft ohne Pilot, " + Flight.format(height) + " m über dem Boden");
        }
        return ticks > 600 ? StepResult.fail("Mission steigt nicht (Höhe " + Flight.format(height) + " m, Zustand "
                + status.map(s -> s.state().name()).orElse("-") + ")") : StepResult.pending();
    }

    private static StepResult openLive(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(NEUTRAL);
        if (!PlannerClient.liveView(drone)) {
            return StepResult.fail("Knopf Livebild im Flugplaner lehnt ab");
        }
        return StepResult.pass();
    }

    private static StepResult operating(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        GimbalControl control = GimbalClient.control();
        boolean watching = Minecraft.getInstance().getCameraEntity() == drone.get();
        boolean operator = PropwashClientApi.input().operating() && control.role() == GimbalControl.Role.OPERATOR
                && control.droneId() == drone.get().getId();
        boolean shown = osdWarnings().contains(CamOsd.Warning.OPERATOR.text());
        Minecraft mc = Minecraft.getInstance();
        boolean solo = mc.player != null && CrewData.get(drone.get()).soloBy(mc.player.getUUID()) && control.solo();
        if (watching && operator && shown && solo && ticks >= SETTLE_TICKS) {
            return StepResult.pass(note(ctx, "Livebild mit Gimbal-Steuerung: Kameramann-Bild, Kamera allein beim Besitzer, OSD zeigt OPERATOR"));
        }
        return ticks > 300 ? StepResult.fail("Livebild ohne Gimbal-Steuerung (Bild der Drohne " + watching + ", operating "
                + PropwashClientApi.input().operating() + ", Rolle " + control.role() + ", allein " + solo + ", OSD " + osdWarnings() + ")")
                : StepResult.pending();
    }

    private static StepResult followsPlan(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        double tilt = GimbalClient.tilt(drone.get());
        GimbalData data = CrewData.get(drone.get());
        String measured = "ohne Knüppel Neigung " + Flight.format(tilt) + "° (Flugplan " + PLAN_TILT + "°), Gimbal-Daten " + data;
        if (!(Math.abs(tilt - PLAN_TILT) <= TOLERANCE) || data.manual()) {
            return StepResult.fail(measured);
        }
        return StepResult.pass(note(ctx, measured));
    }

    private static StepResult tilt(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(ticks < TILT_TICKS ? "0,-1,0,0" : NEUTRAL);
        if (ticks < TILT_TICKS + SETTLE_TICKS) {
            return StepResult.pending();
        }
        double tilt = GimbalClient.tilt(drone.get());
        boolean synced = CrewData.get(drone.get()).manual();
        String measured = "Knüppel neigt auf " + Flight.format(tilt) + "° (Flugplan " + PLAN_TILT + "°), Steuern an alle verteilt " + synced;
        if (!(tilt < TILTED)) {
            return StepResult.fail(measured + ", erwartet unter " + Flight.format(TILTED) + "°");
        }
        if (!synced) {
            return ticks > TILT_TICKS + SETTLE_TICKS + 60 ? StepResult.fail(measured) : StepResult.pending();
        }
        return StepResult.pass(note(ctx, measured));
    }

    private static StepResult panToStop(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<Double> frame = frameHeading(ctx);
        if (drone.isEmpty() || frame.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(ticks < PAN_TICKS ? "1,0,0,0" : NEUTRAL);
        if (ticks < PAN_TICKS + SETTLE_TICKS) {
            return StepResult.pending();
        }
        GimbalLimits limits = GimbalScenes.limits(drone.get()).orElse(GimbalLimits.DEFAULT);
        double relative = Compass.wrap180(cameraHeading() - frame.get());
        boolean shown = limitShown(drone.get());
        String measured = "Schwenk: Bild " + Flight.format(relative) + "° neben der Nase, GIMBAL LIMIT " + (shown ? "an" : "aus");
        if (!shown || Math.abs(Math.abs(relative) - limits.yawLimit()) > TOLERANCE) {
            return StepResult.fail(measured + ", erwartet Anschlag " + Flight.format(limits.yawLimit()) + "° (OSD " + osdWarnings() + ")");
        }
        return StepResult.pass(note(ctx, measured));
    }

    private static StepResult recenter(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<Double> frame = frameHeading(ctx);
        if (drone.isEmpty() || frame.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(ticks < PRESS_TICKS ? NEUTRAL + ",2" : NEUTRAL);
        if (ticks < PRESS_TICKS + SETTLE_TICKS) {
            return StepResult.pending();
        }
        double offNose = Compass.wrap180(cameraHeading() - frame.get());
        double tilt = GimbalClient.tilt(drone.get());
        String measured = "Aktion 2: Bild " + Flight.format(offNose) + "° neben der Nase, Neigung " + Flight.format(tilt) + "°, GIMBAL LIMIT "
                + (limitShown(drone.get()) ? "an" : "aus");
        if (Math.abs(offNose) > TOLERANCE || Math.abs(tilt) > TOLERANCE || limitShown(drone.get())) {
            return StepResult.fail(measured);
        }
        return StepResult.pass(note(ctx, measured));
    }

    private static StepResult missionPress(SelfTest.StepContext ctx, int ticks, int hold, NavLogic.MissionState wanted) {
        input(ticks < hold ? NEUTRAL + ",1" : NEUTRAL);
        if (ticks < hold) {
            return StepResult.pending();
        }
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        NavLogic.MissionState state = status.map(MissionStatus::state).orElse(NavLogic.MissionState.IDLE);
        if (state == wanted) {
            return StepResult.pass(note(ctx, "Aktion 1 " + (hold >= LONG_TICKS ? "lang" : "kurz") + ": Mission " + state));
        }
        return ticks > hold + 100 ? StepResult.fail("Aktion 1 " + (hold >= LONG_TICKS ? "lang" : "kurz") + ": Mission " + state + " statt " + wanted)
                : StepResult.pending();
    }

    private static StepResult leave(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(ticks < LONG_TICKS ? NEUTRAL + ",4" : NEUTRAL);
        if (ticks < LONG_TICKS) {
            return StepResult.pending();
        }
        Minecraft mc = Minecraft.getInstance();
        boolean back = mc.getCameraEntity() == mc.player && !PropwashClientApi.input().operating();
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        boolean running = status.map(s -> s.state() == NavLogic.MissionState.RUNNING).orElse(false);
        boolean released = !CrewData.get(drone.get()).operated() && !CrewData.get(drone.get()).solo();
        double tilt = GimbalClient.tilt(drone.get());
        boolean plan = Math.abs(tilt - PLAN_TILT) <= TOLERANCE;
        if (back && running && released && plan && ticks >= LONG_TICKS + SETTLE_TICKS) {
            return StepResult.pass(note(ctx, "Aktion 4 lang: zurück in der normalen Ansicht, Mission läuft weiter, Kamera frei, Flugplan zielt wieder ("
                    + Flight.format(tilt) + "°)"));
        }
        return ticks > LONG_TICKS + 200 ? StepResult.fail("nicht zurück (Kamera am Spieler " + (mc.getCameraEntity() == mc.player) + ", operating "
                + PropwashClientApi.input().operating() + ", Mission " + status.map(s -> s.state().name()).orElse("-") + ", Kamera frei "
                + released + ", Neigung " + Flight.format(tilt) + "°)") : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult landed(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get(QUERY);
        if (pending == null || ((CompletableFuture<Optional<Boolean>>) pending).isDone()) {
            Optional<Boolean> flying = pending == null ? Optional.empty() : ((CompletableFuture<Optional<Boolean>>) pending).join();
            Minecraft mc = Minecraft.getInstance();
            boolean back = mc.getCameraEntity() == mc.player && !PropwashClientApi.input().operating();
            if (flying.isPresent() && !flying.get() && back) {
                return StepResult.pass(note(ctx, "Landung nach dem Abbruch, Flug ohne Pilot beendet, zurück in der normalen Ansicht"));
            }
            if (ticks > 20 * 60) {
                return StepResult.fail("nach dem Abbruch nicht zurück (Flug ohne Pilot " + flying.map(String::valueOf).orElse("?")
                        + ", Kamera am Spieler " + (mc.getCameraEntity() == mc.player) + ", operating " + PropwashClientApi.input().operating() + ")");
            }
            CompletableFuture<Optional<Boolean>> next = new CompletableFuture<>();
            ctx.onServer(server -> next.complete(Optional.of(PropwashApi.drones().autonomy().active(Pilot.serverDrone(server, state)).isPresent())))
                    .whenComplete((v, t) -> {
                        if (t != null) {
                            next.completeExceptionally(t);
                        }
                    });
            state.put(QUERY, next);
        }
        return StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        restoreInput();
        StepResult result = Script.server(ctx, ctx.tick(), (server, state) -> {
            for (Entity drone : Arena.drones(Arena.level(server))) {
                if (PropwashApi.drones().operators().operator(drone).isPresent()) {
                    PropwashApi.drones().operators().unbind(drone);
                }
                if (CrewData.get(drone).operated()) {
                    CrewService.book().release(drone.getUUID());
                    CrewData.set(drone, CrewData.get(drone).withOperator(Optional.empty()));
                }
            }
            CamFlights.stopFlights(server);
        });
        if (result.done()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getCameraEntity() != mc.player && !PropwashClientApi.input().operating()) {
                mc.setCameraEntity(mc.player);
            }
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

    private static String note(SelfTest.StepContext ctx, String text) {
        notes(ctx).add(text);
        return text;
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }
}
