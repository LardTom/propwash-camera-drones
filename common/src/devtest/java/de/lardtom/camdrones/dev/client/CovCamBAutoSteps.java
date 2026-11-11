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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CovCamBAutoSteps {
    public static final String NAME = "auto: Rückzug, Aufstieg, Kreis, Spirale";
    public static final double SHARE = 0.75;
    public static final double OVER = 1.3;
    public static final double HOLD_DRIFT = 0.5;
    public static final double MIN_SECONDS = 10.0;
    public static final double MAX_SECONDS = 20.0;
    public static final double LATENCY = 1.5;
    private static final int DX = 120;
    private static final int DZ = 60;
    private static final int WATCH_DX = 24;
    private static final String START = "covcamb.auto.start";
    private static final String SUBJECT = "covcamb.auto.subject";
    private static final String FORWARD = "covcamb.auto.forward";
    private static final String PATH = "covcamb.auto.path";
    private static final String STARTED = "covcamb.auto.started";
    private static final String DONE_AT = "covcamb.auto.doneAt";
    private static final String HOLD = "covcamb.auto.hold";
    private static final String NOTES = "covcamb.auto.notes";
    private static final String RADIUS = "covcamb.auto.radius";
    private static final int STEP_TICKS = 20 * 330;

    private CovCamBAutoSteps() {
    }

    static void register() {
        Script script = Script.create()
                .append(CovCamBKit.goTo(DX, DZ))
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .server("gekoppeltes Tablet und Zuschauerplatz", CovCamBAutoSteps::tablet)
                .await("Tablet am Client", 100, () -> held().isPresent());
        for (AutoShots.Shot shot : new AutoShots.Shot[]{AutoShots.Shot.RISE, AutoShots.Shot.RETREAT, AutoShots.Shot.CIRCLE, AutoShots.Shot.SPIRAL}) {
            script.append(shot(shot));
        }
        script.then("vier Automatik-Flüge", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
        SelfTest.clientStep(516, NAME, STEP_TICKS, script);
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    private static Script shot(AutoShots.Shot shot) {
        String name = shot.name().toLowerCase(Locale.ROOT);
        return Script.create()
                .run("Editor öffnen", CovCamBAutoSteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Knopf Automatik", CovCamBAutoSteps::openMenu)
                .then(name + " im Tablet wählen", (ctx, ticks) -> choose(ctx, shot))
                .then("Meldung Automatik-Flug gestartet", (ctx, ticks) -> {
                    StepResult result = CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.AUTO_SHOT_STARTED, 100);
                    if (result.done() && !result.failed()) {
                        ctx.state().put(STARTED, System.nanoTime());
                    }
                    return result;
                })
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .then(name + " fliegen und mit dem Soll vergleichen", (ctx, ticks) -> fly(ctx, ticks, shot))
                .then(name + ": danach Halten", (ctx, ticks) -> holds(ctx, ticks, shot));
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
            return StepResult.pass();
        }
        if (ticks > 40) {
            return StepResult.fail("Knopf \"" + I18n.get("camdrones.planner.button.auto") + "\" öffnet kein Menü");
        }
        if (ticks % 10 == 0) {
            Rect r = screen.layout().footerButton(EditorTexts.Footer.AUTO);
            MouseButtonEvent event = new MouseButtonEvent(r.centerX(), r.centerY(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
        }
        return StepResult.pending();
    }

    private static StepResult choose(SelfTest.StepContext ctx, AutoShots.Shot shot) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        String label = I18n.get(AutoShotMenu.labelKey(shot));
        Map<String, Object> state = ctx.state();
        state.put(CamFlights.MARK, StatusProbe.mark());
        for (String key : new String[]{START, SUBJECT, FORWARD, PATH, STARTED, DONE_AT, HOLD, RADIUS}) {
            state.remove(key);
        }
        if (!SelfTestRunner.clickWidget(screen, label)) {
            return StepResult.fail("Eintrag \"" + label + "\" fehlt im Automatik-Menü");
        }
        Optional<Entity> drone = CamFlights.clientDrone(state);
        Optional<DroneView> view = CamFlights.clientView(state);
        if (drone.isEmpty() || view.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        state.put(START, drone.get().position());
        state.put(FORWARD, Compass.headingTo(view.get().forward().x, view.get().forward().z));
        return StepResult.pass();
    }

    private static Optional<SmartState> smart(Entity drone) {
        return Optional.ofNullable(PropwashApi.droneData().get(drone, CamDroneData.smart()));
    }

    @SuppressWarnings("unchecked")
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
            Vec3 subject = new Vec3(smart.get().x(), smart.get().y(), smart.get().z());
            state.put(SUBJECT, subject);
            state.put(RADIUS, Math.max(AutoShots.MIN_RADIUS, CamFlights.horizontal((Vec3) state.get(START), subject)));
        }
        List<Vec3> path = (List<Vec3>) state.computeIfAbsent(PATH, key -> new ArrayList<Vec3>());
        path.add(p);
        int mark = CamFlights.mark(ctx);
        if (!StatusProbe.seen(mark, AutonomyStatus.AUTO_SHOT_DONE)) {
            int limit = (int) Math.round((AutoShots.duration(shot) + 25.0) * 20.0);
            return ticks > limit ? StepResult.fail("kein Ende nach " + Flight.format(ticks / 20.0) + " s") : StepResult.pending();
        }
        double seconds = (System.nanoTime() - (long) state.get(STARTED)) / 1.0E9;
        state.put(DONE_AT, p);
        writePath(shot, path, (Vec3) state.get(SUBJECT));
        Vec3 start = (Vec3) state.get(START);
        Vec3 subject = (Vec3) state.get(SUBJECT);
        double heading = (double) state.get(FORWARD);
        double radius = (double) state.get(RADIUS);
        List<String> wrong = new ArrayList<>();
        String compared;
        double dx = p.x - start.x;
        double dz = p.z - start.z;
        double climb = p.y - start.y;
        switch (shot) {
            case RISE -> {
                double soll = AutoShots.RISE_CLIMB * AutoShots.RISE_SECONDS;
                double drift = maxDrift(path, start);
                compared = "Aufstieg " + Flight.format(climb) + " m (Soll " + Flight.format(soll) + " m), seitlich höchstens " + Flight.format(drift) + " m";
                check(wrong, climb, soll, "Steigen");
                if (drift > 1.5) {
                    wrong.add("seitlich " + Flight.format(drift) + " m (Soll 0)");
                }
            }
            case RETREAT -> {
                double back = -(dx * Compass.forwardX(heading) + dz * Compass.forwardZ(heading));
                double side = Math.abs(dx * Compass.rightX(heading) + dz * Compass.rightZ(heading));
                double sollBack = Math.min(AutoShots.RETREAT_SPEED, FlightClass.TOUR.cineSpeed()) * AutoShots.RETREAT_SECONDS;
                double sollClimb = Math.min(AutoShots.RETREAT_CLIMB, FlightClass.TOUR.climbSpeed()) * AutoShots.RETREAT_SECONDS;
                compared = "Rückzug " + Flight.format(back) + " m rückwärts (Soll " + Flight.format(sollBack) + "), " + Flight.format(climb) + " m hoch (Soll "
                        + Flight.format(sollClimb) + "), " + Flight.format(side) + " m seitlich";
                check(wrong, back, sollBack, "rückwärts");
                check(wrong, climb, sollClimb, "Steigen");
                if (side > 3.0) {
                    wrong.add("seitlich " + Flight.format(side) + " m");
                }
            }
            case CIRCLE -> {
                double swept = swept(path, subject);
                double worst = worstRadius(path, subject, radius, 0.0, 0.25);
                double height = heightSpread(path, 0.25);
                compared = "Kreis " + Flight.format(swept) + "° (Soll 360°), Radius auf " + Flight.format(worst) + " m genau (Soll " + Flight.format(radius)
                        + " m), Höhe schwankt " + Flight.format(height) + " m";
                if (swept < 300.0 || swept > 420.0) {
                    wrong.add("Winkel " + Flight.format(swept) + "°");
                }
                if (worst > 2.5) {
                    wrong.add("Radius weicht " + Flight.format(worst) + " m ab");
                }
                if (height > 1.5) {
                    wrong.add("Höhe schwankt " + Flight.format(height) + " m");
                }
            }
            case SPIRAL -> {
                double swept = swept(path, subject);
                double widen = CamFlights.horizontal(p, subject) - radius;
                double sollClimb = AutoShots.SPIRAL_CLIMB * AutoShots.SPIRAL_SECONDS;
                compared = "Spirale " + Flight.format(swept) + "° (Soll 360°), " + Flight.format(widen) + " m weiter (Soll " + Flight.format(AutoShots.SPIRAL_WIDEN)
                        + "), " + Flight.format(climb) + " m hoch (Soll " + Flight.format(sollClimb) + ")";
                if (swept < 270.0 || swept > 420.0) {
                    wrong.add("Winkel " + Flight.format(swept) + "°");
                }
                check(wrong, widen, AutoShots.SPIRAL_WIDEN, "weiter");
                check(wrong, climb, sollClimb, "Steigen");
            }
            default -> compared = "";
        }
        if (seconds < MIN_SECONDS - LATENCY || seconds > MAX_SECONDS + LATENCY) {
            wrong.add("Dauer " + Flight.format(seconds) + " s statt " + (int) MIN_SECONDS + "–" + (int) MAX_SECONDS + " s");
        }
        String result = compared + ", Dauer " + Flight.format(seconds) + " s (Soll " + Flight.format(AutoShots.duration(shot)) + " s)";
        if (!wrong.isEmpty()) {
            return StepResult.fail(result + ": " + String.join(", ", wrong));
        }
        notes(ctx).add(result);
        return StepResult.pass(result);
    }

    private static void check(List<String> wrong, double ist, double soll, String what) {
        if (ist < SHARE * soll || ist > OVER * soll) {
            wrong.add(what + " " + Flight.format(ist) + " m statt " + Flight.format(soll) + " m");
        }
    }

    private static double maxDrift(List<Vec3> path, Vec3 start) {
        double worst = 0.0;
        for (Vec3 v : path) {
            worst = Math.max(worst, CamFlights.horizontal(v, start));
        }
        return worst;
    }

    private static double swept(List<Vec3> path, Vec3 subject) {
        double total = 0.0;
        Double last = null;
        for (Vec3 v : path) {
            double angle = Math.toDegrees(Math.atan2(v.z - subject.z, v.x - subject.x));
            if (last != null) {
                total += Math.abs(Compass.wrap180(angle - last));
            }
            last = angle;
        }
        return total;
    }

    private static double worstRadius(List<Vec3> path, Vec3 subject, double radius, double widen, double skip) {
        double worst = 0.0;
        int from = (int) (path.size() * skip);
        for (int i = from; i < path.size(); i++) {
            double t = path.size() <= 1 ? 0.0 : i / (double) (path.size() - 1);
            worst = Math.max(worst, Math.abs(CamFlights.horizontal(path.get(i), subject) - (radius + widen * t)));
        }
        return worst;
    }

    private static double heightSpread(List<Vec3> path, double skip) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = (int) (path.size() * skip); i < path.size(); i++) {
            min = Math.min(min, path.get(i).y);
            max = Math.max(max, path.get(i).y);
        }
        return max - min;
    }

    private static void writePath(AutoShots.Shot shot, List<Vec3> path, Vec3 subject) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + shot + " Motiv " + CamFlights.format(subject) + " (t s, x, y, z, Abstand zum Motiv m)");
        for (int i = 0; i < path.size(); i += 10) {
            Vec3 v = path.get(i);
            lines.add(String.format(Locale.ROOT, "%.1f %.2f %.2f %.2f %.2f", i / 20.0, v.x, v.y, v.z, CamFlights.horizontal(v, subject)));
        }
        CovCamBKit.write("auto_" + shot.name().toLowerCase(Locale.ROOT) + ".txt", lines);
    }

    @SuppressWarnings("unchecked")
    private static StepResult holds(SelfTest.StepContext ctx, int ticks, AutoShots.Shot shot) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> found = CamFlights.clientDrone(state);
        if (found.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Entity drone = found.get();
        CamFlights.lookAt(drone);
        Vec3 p = drone.position();
        boolean hold = smart(drone).map(s -> s.function() == SmartState.Function.HOLD).orElse(false);
        if (!hold) {
            return ticks > 100 ? StepResult.fail("nach dem Flug kein Halten: " + smart(drone).map(s -> s.function().name()).orElse("keine")) : StepResult.pending();
        }
        List<Vec3> samples = (List<Vec3>) state.computeIfAbsent(HOLD, key -> new ArrayList<Vec3>());
        if (ticks < 40) {
            return StepResult.pending();
        }
        samples.add(p);
        if (samples.size() < 60) {
            return StepResult.pending();
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vec3 v : samples) {
            minX = Math.min(minX, v.x);
            maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
            minZ = Math.min(minZ, v.z);
            maxZ = Math.max(maxZ, v.z);
        }
        double drift = Math.max(Math.hypot(maxX - minX, maxZ - minZ), maxY - minY);
        if (drift > HOLD_DRIFT) {
            return StepResult.fail(shot + ": hält danach nicht auf " + Flight.format(HOLD_DRIFT) + " m genau (" + Flight.format(drift) + " m in 3 s)");
        }
        notes(ctx).add(shot.name().toLowerCase(Locale.ROOT) + " hält danach auf " + Flight.format(drift) + " m");
        state.remove(HOLD);
        return StepResult.pass();
    }
}
