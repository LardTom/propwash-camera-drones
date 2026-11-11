package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.FollowMenu;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FollowTabletSteps {
    public static final String NAME = "follow: Hinterher, Parallel und Blick über das Tablet, Stil gilt für den Schalter";
    public static final int NUMBER = 40;
    public static final double DISTANCE_TOLERANCE = 2.5;
    public static final double WATCH_DRIFT = 1.5;
    public static final double WATCH_HEADING = 10.0;
    public static final double WATCH_WALK = 5.0;
    private static final int DX = 60;
    private static final int DZ = 140;
    private static final int WATCH_DX = 24;
    private static final int STEP_TICKS = 20 * 300;
    private static final String TEST_KEY = "key.keyboard.end";
    private static final String NOTES = "followtab.notes";
    private static final String START = "followtab.start";
    private static final String WORST = "followtab.worst";
    private static final String CALM = "followtab.calm";
    private static final String WALKER = "followtab.walker";
    private static @Nullable String saved;

    private FollowTabletSteps() {
    }

    public static void register() {
        Script script = Script.create()
                .append(Shots.photoWindow())
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .server("gekoppeltes Tablet und Platz neben der Drohne", FollowTabletSteps::tablet)
                .await("Tablet am Client", 100, () -> held().isPresent());
        boolean first = true;
        for (FollowController.Style style : new FollowController.Style[]{FollowController.Style.PARALLEL, FollowController.Style.BEHIND,
                FollowController.Style.WATCH}) {
            script.append(choose(style, first));
            first = false;
        }
        script.server("Flug ohne Pilot beenden", (server, state) -> CamFlights.stopFlights(server))
                .then("Drohne gelandet", FollowTabletSteps::landed)
                .server("Funke binden", Pilot::bind)
                .sleep(14)
                .server("Brille auf", Pilot::start)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent())
                .sleep(20)
                .then("scharf schalten", Pilot::arm)
                .then("Kompass prüfen", Pilot::compass)
                .append(Flight.takeoff(4.0))
                .run("Taste für Follow me belegen (ohne Standard)", () -> bind(true))
                .then("Schalter Follow me ein", (ctx, ticks) -> toggle(ctx, ticks, true))
                .then("Pilotenflug folgt im Stil aus dem Tablet", FollowTabletSteps::pilotStyle)
                .then("Brille ab", (ctx, ticks) -> {
                    ctx.state().put(CamFlights.MARK, StatusProbe.mark());
                    ctx.onServer(Pilot::release);
                    return StepResult.pass();
                })
                .then("Server folgt weiter", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then("Stil bleibt nach der Übergabe", (ctx, ticks) -> {
                    StepResult result = synced(ctx, ticks, FollowController.Style.WATCH);
                    if (result.done() && !result.failed()) {
                        notes(ctx).add("nach der Übergabe an den Server weiter FOLLOW/WATCH");
                    }
                    return result;
                })
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, script, FollowTabletSteps::cleanup);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        bind(false);
        Pilot.releaseKeys();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof PlannerScreen) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> {
            Pilot.release(server);
            CamFlights.stopFlights(server);
        });
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    private static Script choose(FollowController.Style style, boolean menuPhoto) {
        String name = style.name().toLowerCase(Locale.ROOT);
        Script script = Script.create()
                .run("Editor öffnen", FollowTabletSteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Knopf Follow me", FollowTabletSteps::openMenu);
        if (menuPhoto) {
            script.append(Shots.photos("planner_follow_menu"));
        }
        return script
                .then(name + " im Tablet wählen", (ctx, ticks) -> pick(ctx, style))
                .then("Meldung Follow me gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.FOLLOW_STARTED, 100))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then("Drohnen-Daten zeigen " + style, (ctx, ticks) -> synced(ctx, ticks, style))
                .then(name + " fliegen", (ctx, ticks) -> style == FollowController.Style.WATCH ? watch(ctx, ticks) : distance(ctx, ticks, style));
    }

    private static Optional<ItemStack> held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) && stack.get(PlannerItems.link()) != null ? Optional.of(stack) : Optional.empty();
    }

    private static void tablet(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow(() -> new IllegalStateException("Tablet fehlt")));
        tablet.set(PlannerItems.link(), PlannerLink.of(CamFlights.serial(state), "", FlightClass.TOUR));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        player.teleportTo(player.level(), drone.getX() + WATCH_DX, drone.getY(), drone.getZ(), Set.<Relative>of(), 90.0F, -10.0F, true);
    }

    private static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        mc.player.setXRot(-80.0F);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    private static StepResult openMenu(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        if (screen.state().followMenu()) {
            return StepResult.pass("Menü mit " + FollowMenu.STYLES.size() + " Stilen offen");
        }
        if (ticks > 40) {
            return StepResult.fail("Knopf \"" + I18n.get("camdrones.planner.button.follow") + "\" öffnet kein Menü");
        }
        if (ticks % 10 == 0) {
            Rect r = screen.layout().footerButton(EditorTexts.Footer.FOLLOW);
            MouseButtonEvent event = new MouseButtonEvent(r.centerX(), r.centerY(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
        }
        return StepResult.pending();
    }

    private static StepResult pick(SelfTest.StepContext ctx, FollowController.Style style) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        String label = I18n.get(FollowMenu.labelKey(style));
        Map<String, Object> state = ctx.state();
        state.put(CamFlights.MARK, StatusProbe.mark());
        state.remove(START);
        state.remove(WORST);
        state.remove(CALM);
        state.remove(WALKER);
        if (!SelfTestRunner.clickWidget(screen, label)) {
            return StepResult.fail("Eintrag \"" + label + "\" fehlt im Menü Follow me");
        }
        if (screen.state().followMenu()) {
            return StepResult.fail("Menü bleibt nach \"" + label + "\" offen");
        }
        Optional<FollowController.Style> remembered = PlannerClient.followStyle(CamFlights.serial(state));
        if (remembered.isEmpty() || remembered.get() != style) {
            return StepResult.fail("Flugplaner merkt sich " + remembered.map(Enum::name).orElse("nichts") + " statt " + style + " für den Schalter");
        }
        return StepResult.pass("\"" + label + "\" gewählt");
    }

    private static Optional<SmartState> smart(Entity drone) {
        return Optional.ofNullable(PropwashApi.droneData().get(drone, CamDroneData.smart()));
    }

    private static StepResult synced(SelfTest.StepContext ctx, int ticks, FollowController.Style style) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Optional<SmartState> smart = smart(drone.get());
        Minecraft mc = Minecraft.getInstance();
        boolean target = mc.player != null && smart.flatMap(SmartState::target).filter(mc.player.getUUID()::equals).isPresent();
        if (smart.isPresent() && smart.get().function() == SmartState.Function.FOLLOW && smart.get().followStyle() == style && target) {
            return StepResult.pass("FOLLOW/" + style + " auf den Spieler");
        }
        return ticks > 100 ? StepResult.fail("Drohnen-Daten zeigen " + smart.map(s -> s.function() + "/" + s.followStyle()).orElse("nichts")
                + " statt FOLLOW/" + style) : StepResult.pending();
    }

    private static StepResult distance(SelfTest.StepContext ctx, int ticks, FollowController.Style style) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty() || mc.player == null) {
            return StepResult.fail("Drohne oder Spieler fehlt");
        }
        CamFlights.lookAt(drone.get());
        double d = CamFlights.horizontal(drone.get().position(), mc.player.position());
        int calm = Math.abs(d - FollowController.DISTANCE) <= DISTANCE_TOLERANCE ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calm);
        if (calm >= 40) {
            String note = style + ": " + Flight.format(d) + " m vom Spieler";
            notes(ctx).add(note);
            return StepResult.pass(note);
        }
        return ticks > 20 * 40 ? StepResult.fail(style + ": Drohne " + Flight.format(d) + " m vom Spieler, erwartet "
                + Flight.format(FollowController.DISTANCE) + " ± " + Flight.format(DISTANCE_TOLERANCE) + " m") : StepResult.pending();
    }

    private static StepResult watch(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<DroneView> view = CamFlights.clientView(ctx.state());
        if (drone.isEmpty() || view.isEmpty() || mc.player == null) {
            return StepResult.fail("Drohne oder Spieler fehlt");
        }
        if (ticks < 40) {
            return StepResult.pending();
        }
        mc.player.setYRot(0.0F);
        mc.player.setXRot(0.0F);
        int walk = 40 + 20 * 4;
        Pilot.hold(mc.options.keyUp, ticks < walk);
        Vec3 p = drone.get().position();
        Vec3 start = (Vec3) ctx.state().computeIfAbsent(START, key -> p);
        double drift = CamFlights.horizontal(p, start);
        Vec3 walker = mc.player.position();
        double walked = CamFlights.horizontal(walker, (Vec3) ctx.state().computeIfAbsent(WALKER, key -> walker));
        Vec3 target = mc.player.getBoundingBox().getCenter();
        double nose = Compass.headingTo(view.get().forward().x, view.get().forward().z);
        double error = Math.abs(Compass.wrap180(nose - Compass.headingTo(target.x - p.x, target.z - p.z)));
        double[] worst = (double[]) ctx.state().computeIfAbsent(WORST, key -> new double[]{0.0, 0.0});
        worst[0] = Math.max(worst[0], drift);
        if (ticks >= walk) {
            worst[1] = Math.max(worst[1], error);
        }
        if (ticks < walk + 30) {
            return StepResult.pending();
        }
        Pilot.hold(mc.options.keyUp, false);
        String note = "WATCH: Spieler geht " + Flight.format(walked) + " m, Drohne bewegt sich höchstens " + Flight.format(worst[0])
                + " m, Nase nach dem Gehen höchstens " + Flight.format(worst[1]) + "° neben dem Spieler";
        if (walked < WATCH_WALK) {
            return StepResult.fail(note + " (Spieler muss mindestens " + Flight.format(WATCH_WALK) + " m gehen)");
        }
        if (worst[0] > WATCH_DRIFT || worst[1] > WATCH_HEADING) {
            return StepResult.fail(note + " (erwartet unter " + Flight.format(WATCH_DRIFT) + " m und " + Flight.format(WATCH_HEADING) + "°)");
        }
        notes(ctx).add(note);
        return StepResult.pass(note);
    }

    private static StepResult landed(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Vec3 p = drone.get().position();
        boolean down = p.y - CamFlights.surface() < 0.5 && drone.get().getDeltaMovement().lengthSqr() < 1.0E-3;
        int calm = down ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calm);
        if (calm >= 20) {
            ctx.state().remove(CALM);
            return StepResult.pass();
        }
        return ticks > 20 * 40 ? StepResult.fail("Drohne landet nicht: " + Flight.format(p.y - CamFlights.surface()) + " m über Grund") : StepResult.pending();
    }

    private static void bind(boolean on) {
        KeyMapping key = CamKeys.key(CamControl.FOLLOW_ME).orElseThrow(() -> new IllegalStateException("Taste für Follow me fehlt"));
        if (on && saved == null) {
            saved = key.saveString();
            key.setKey(InputConstants.getKey(TEST_KEY));
        } else if (!on && saved != null) {
            key.setKey(InputConstants.getKey(saved));
            saved = null;
        }
        KeyMapping.resetMapping();
    }

    private static boolean following() {
        return Pilot.status().map(NavStatus::function).filter(f -> f == NavLogic.Function.FOLLOW).isPresent();
    }

    private static StepResult toggle(SelfTest.StepContext ctx, int ticks, boolean on) {
        Flight.track(ctx);
        if (ticks % 40 == 0 && following() != on) {
            KeyMapping key = CamKeys.key(CamControl.FOLLOW_ME).orElseThrow(() -> new IllegalStateException("Taste für Follow me fehlt"));
            KeyMapping.click(InputConstants.getKey(key.saveString()));
        }
        if (following() == on && ticks >= 4) {
            return StepResult.pass("Follow me per Schalter " + (on ? "an" : "aus"));
        }
        return ticks > 130 ? StepResult.fail("Follow me schaltet nicht " + (on ? "ein" : "aus") + ": " + Flight.describe()) : StepResult.pending();
    }

    private static StepResult pilotStyle(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Optional<SmartState> smart = smart(drone.get());
        if (smart.isPresent() && smart.get().function() == SmartState.Function.FOLLOW && smart.get().followStyle() == FollowController.Style.WATCH) {
            String note = "Pilotenflug: Schalter Follow me folgt im zuletzt im Tablet gewählten Stil WATCH";
            notes(ctx).add(note);
            return StepResult.pass(note);
        }
        return ticks > 100 ? StepResult.fail("Pilotenflug meldet " + smart.map(s -> s.function() + "/" + s.followStyle()).orElse("nichts")
                + " statt FOLLOW/WATCH") : StepResult.pending();
    }
}
