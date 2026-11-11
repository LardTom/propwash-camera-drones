package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.model.AutoShotMenu;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
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

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AutoShotSteps {
    public static final String NAME = "automatik: Rückzug, Aufstieg, Kreis und Spirale über das Tablet";
    public static final double RISE_DRIFT = 2.0;
    public static final double RETREAT_SIDE = 3.0;
    public static final double CIRCLE_RADIUS_TOLERANCE = 2.5;
    public static final double CIRCLE_HEIGHT_TOLERANCE = 1.5;
    public static final double SHARE = 0.6;
    public static final double HOLD_SPEED = 0.5;
    private static final int DX = 96;
    private static final int DZ = 96;
    private static final int WATCH_DX = 24;
    private static final String START = "auto.start";
    private static final String SUBJECT = "auto.subject";
    private static final String FORWARD = "auto.forward";
    private static final String LAST_ANGLE = "auto.lastAngle";
    private static final String SWEPT = "auto.swept";
    private static final String WORST = "auto.worst";
    private static final String SPAN = "auto.span";
    private static final String CALM = "auto.calm";
    private static final String LAST = "auto.last";
    private static final String RESULTS = "auto.results";
    private static final int STEP_TICKS = 20 * 300;

    private AutoShotSteps() {
    }

    public static void register() {
        Script script = Script.create()
                .append(Shots.photoWindow())
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .server("gekoppeltes Tablet und Zuschauerplatz", AutoShotSteps::tablet)
                .await("Tablet am Client", 100, () -> held().isPresent());
        boolean first = true;
        for (AutoShots.Shot shot : new AutoShots.Shot[]{AutoShots.Shot.RISE, AutoShots.Shot.RETREAT, AutoShots.Shot.CIRCLE, AutoShots.Shot.SPIRAL}) {
            script.append(shot(shot, first));
            first = false;
        }
        script.then("vier Automatik-Flüge", AutoShotSteps::summary);
        SelfTest.clientStep(25, NAME, STEP_TICKS, script);
    }

    private static Script shot(AutoShots.Shot shot, boolean menuPhoto) {
        String name = shot.name().toLowerCase(Locale.ROOT);
        Script script = Script.create()
                .run("Editor öffnen", AutoShotSteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Knopf Automatik", AutoShotSteps::openMenu);
        if (menuPhoto) {
            script.append(Shots.photos("planner_auto_menu"));
        }
        return script
                .then(name + " im Tablet wählen", (ctx, ticks) -> choose(ctx, shot))
                .then("Meldung Automatik-Flug gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.AUTO_SHOT_STARTED, 100))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "auto_shot"))
                .then(name + " fliegen", (ctx, ticks) -> fly(ctx, ticks, shot))
                .then("hält danach", (ctx, ticks) -> holds(ctx, ticks, shot));
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
        if (screen.state().autoMenu()) {
            return StepResult.pass("Menü mit " + AutoShotMenu.SHOTS.size() + " Automatik-Flügen offen");
        }
        if (ticks > 40) {
            return StepResult.fail("Knopf \"" + I18n.get("camdrones.planner.button.auto") + "\" öffnet kein Menü");
        }
        if (ticks % 10 == 0) {
            click(screen, screen.layout().footerButton(EditorTexts.Footer.AUTO));
        }
        return StepResult.pending();
    }

    private static void click(PlannerScreen screen, Rect r) {
        MouseButtonEvent event = new MouseButtonEvent(r.centerX(), r.centerY(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static StepResult choose(SelfTest.StepContext ctx, AutoShots.Shot shot) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        String label = I18n.get(AutoShotMenu.labelKey(shot));
        Map<String, Object> state = ctx.state();
        state.put(CamFlights.MARK, StatusProbe.mark());
        for (String key : new String[]{START, SUBJECT, FORWARD, LAST_ANGLE, SWEPT, WORST, SPAN, CALM, LAST}) {
            state.remove(key);
        }
        if (!SelfTestRunner.clickWidget(screen, label)) {
            return StepResult.fail("Eintrag \"" + label + "\" fehlt im Automatik-Menü");
        }
        if (screen.state().autoMenu()) {
            return StepResult.fail("Menü bleibt nach \"" + label + "\" offen");
        }
        Optional<Entity> drone = CamFlights.clientDrone(state);
        Optional<DroneView> view = CamFlights.clientView(state);
        if (drone.isEmpty() || view.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        state.put(START, drone.get().position());
        state.put(FORWARD, Compass.headingTo(view.get().forward().x, view.get().forward().z));
        return StepResult.pass("\"" + label + "\" gewählt");
    }

    private static Optional<SmartState> smart(Entity drone) {
        return Optional.ofNullable(PropwashApi.droneData().get(drone, CamDroneData.smart()));
    }

    private static StepResult fly(SelfTest.StepContext ctx, int ticks, AutoShots.Shot shot) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> found = CamFlights.clientDrone(state);
        if (found.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Entity drone = found.get();
        CamFlights.lookAt(drone);
        Vec3 p = drone.position();
        if (!state.containsKey(SUBJECT)) {
            Optional<SmartState> smart = smart(drone);
            if (smart.isEmpty() || smart.get().function() != SmartState.Function.AUTO_SHOT || smart.get().shot() != shot) {
                return ticks > 100 ? StepResult.fail("Drohnen-Daten zeigen " + smart.map(s -> s.function() + "/" + s.shot()).orElse("nichts")
                        + " statt AUTO_SHOT/" + shot) : StepResult.pending();
            }
            state.put(SUBJECT, new Vec3(smart.get().x(), smart.get().y(), smart.get().z()));
        }
        Vec3 subject = (Vec3) state.get(SUBJECT);
        double radius = CamFlights.horizontal(p, subject);
        double angle = Math.toDegrees(Math.atan2(p.z - subject.z, p.x - subject.x));
        if (state.get(LAST_ANGLE) instanceof Double last) {
            state.put(SWEPT, (double) state.getOrDefault(SWEPT, 0.0) + Math.abs(Compass.wrap180(angle - last)));
        }
        state.put(LAST_ANGLE, angle);
        double[] span = (double[]) state.computeIfAbsent(SPAN, key -> new double[]{radius, radius, p.y, p.y});
        span[0] = Math.min(span[0], radius);
        span[1] = Math.max(span[1], radius);
        double swept = (double) state.getOrDefault(SWEPT, 0.0);
        if (shot == AutoShots.Shot.CIRCLE && swept > 60.0) {
            state.put(WORST, Math.max((double) state.getOrDefault(WORST, 0.0), Math.abs(radius - AutoShots.SUBJECT_DISTANCE)));
            span[2] = Math.min(span[2], p.y);
            span[3] = Math.max(span[3], p.y);
        }
        int mark = CamFlights.mark(ctx);
        if (!StatusProbe.seen(mark, AutonomyStatus.AUTO_SHOT_DONE)) {
            int limit = (int) Math.round((AutoShots.duration(shot) + 25.0) * 20.0);
            return ticks > limit ? StepResult.fail("kein Ende nach " + Flight.format(ticks / 20.0) + " s (Meldungen: " + StatusProbe.describe(mark) + ")")
                    : StepResult.pending();
        }
        Vec3 start = (Vec3) state.get(START);
        double heading = (double) state.get(FORWARD);
        double dx = p.x - start.x;
        double dz = p.z - start.z;
        double climb = p.y - start.y;
        String result = switch (shot) {
            case RISE -> {
                double drift = Math.hypot(dx, dz);
                double need = SHARE * AutoShots.RISE_CLIMB * AutoShots.RISE_SECONDS;
                if (drift > RISE_DRIFT || climb < need) {
                    yield "FAIL Aufstieg: " + Flight.format(climb) + " m gestiegen (mindestens " + Flight.format(need) + "), " + Flight.format(drift)
                            + " m seitlich (höchstens " + Flight.format(RISE_DRIFT) + ")";
                }
                yield "Aufstieg " + Flight.format(climb) + " m senkrecht, " + Flight.format(drift) + " m seitlich";
            }
            case RETREAT -> {
                double back = -(dx * Compass.forwardX(heading) + dz * Compass.forwardZ(heading));
                double side = Math.abs(dx * Compass.rightX(heading) + dz * Compass.rightZ(heading));
                double needBack = SHARE * AutoShots.RETREAT_SPEED * AutoShots.RETREAT_SECONDS;
                double needClimb = SHARE * AutoShots.RETREAT_CLIMB * AutoShots.RETREAT_SECONDS;
                if (back < needBack || side > RETREAT_SIDE || climb < needClimb) {
                    yield "FAIL Rückzug: " + Flight.format(back) + " m rückwärts (mindestens " + Flight.format(needBack) + "), " + Flight.format(side)
                            + " m seitlich, " + Flight.format(climb) + " m gestiegen (mindestens " + Flight.format(needClimb) + ")";
                }
                yield "Rückzug " + Flight.format(back) + " m rückwärts und " + Flight.format(climb) + " m hoch, " + Flight.format(side) + " m seitlich";
            }
            case CIRCLE -> {
                double worst = (double) state.getOrDefault(WORST, Double.NaN);
                double height = span[3] - span[2];
                if (swept < 300.0 || !(worst <= CIRCLE_RADIUS_TOLERANCE) || height > CIRCLE_HEIGHT_TOLERANCE) {
                    yield "FAIL Kreis: " + Flight.format(swept) + "° um das Motiv, Radius auf " + Flight.format(worst) + " m genau, Höhe schwankt "
                            + Flight.format(height) + " m";
                }
                yield "Kreis " + Flight.format(swept) + "° um das Motiv, Radius " + Flight.format(AutoShots.SUBJECT_DISTANCE) + " m auf "
                        + Flight.format(worst) + " m genau";
            }
            case SPIRAL -> {
                double widen = radius - span[0];
                double needClimb = SHARE * AutoShots.SPIRAL_CLIMB * AutoShots.SPIRAL_SECONDS;
                if (swept < 240.0 || widen < SHARE * AutoShots.SPIRAL_WIDEN || climb < needClimb) {
                    yield "FAIL Spirale: " + Flight.format(swept) + "° um das Motiv, " + Flight.format(widen) + " m weiter, " + Flight.format(climb)
                            + " m gestiegen";
                }
                yield "Spirale " + Flight.format(swept) + "°, " + Flight.format(widen) + " m weiter und " + Flight.format(climb) + " m hoch";
            }
        };
        if (result.startsWith("FAIL ")) {
            return StepResult.fail(result.substring(5));
        }
        return StepResult.pass(result);
    }

    private static StepResult holds(SelfTest.StepContext ctx, int ticks, AutoShots.Shot shot) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> found = CamFlights.clientDrone(state);
        if (found.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Entity drone = found.get();
        CamFlights.lookAt(drone);
        Vec3 p = drone.position();
        double speed = 0.0;
        double vertical = 0.0;
        if (state.get(LAST) instanceof Vec3 last) {
            speed = CamFlights.horizontal(p, last) * 20.0;
            vertical = Math.abs(p.y - last.y) * 20.0;
        }
        state.put(LAST, p);
        boolean hold = smart(drone).map(s -> s.function() == SmartState.Function.HOLD).orElse(false);
        int calm = hold && ticks > 0 && speed < HOLD_SPEED && vertical < HOLD_SPEED ? (int) state.getOrDefault(CALM, 0) + 1 : 0;
        state.put(CALM, calm);
        if (calm >= 20) {
            state.remove(CALM);
            state.remove(LAST);
            @SuppressWarnings("unchecked")
            Map<AutoShots.Shot, Boolean> results = (Map<AutoShots.Shot, Boolean>) state.computeIfAbsent(RESULTS, key -> new EnumMap<>(AutoShots.Shot.class));
            results.put(shot, true);
            return StepResult.pass("hält nach dem Flug die Position (" + Flight.format(speed) + " m/s)");
        }
        return ticks > 20 * 12 ? StepResult.fail("hält nach dem Flug nicht: " + Flight.format(speed) + " m/s, Funktion "
                + smart(drone).map(s -> s.function().name()).orElse("keine")) : StepResult.pending();
    }

    private static StepResult summary(SelfTest.StepContext ctx, int ticks) {
        @SuppressWarnings("unchecked")
        Map<AutoShots.Shot, Boolean> results = (Map<AutoShots.Shot, Boolean>) ctx.state().get(RESULTS);
        if (results == null || results.size() != AutoShots.Shot.values().length) {
            return StepResult.fail("nur " + (results == null ? 0 : results.size()) + " von " + AutoShots.Shot.values().length + " Automatik-Flügen");
        }
        UUID serial = CamFlights.serial(ctx.state());
        return StepResult.pass("alle " + results.size() + " Automatik-Flüge über das Tablet ausgelöst, geflogen und danach gehalten (Drohne "
                + serial.toString().substring(0, 8) + ")");
    }
}
