package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.FlightRecorder;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PairingSteps {
    public static final String PAIR = "planer: koppeln per Rechtsklick mit dem Flugplaner";
    public static final String IN_FLIGHT = "planer: Wegpunkt im Flug per Taste und Flug aufzeichnen";
    public static final int TABLET_SLOT = 8;
    public static final double SPACING_TOLERANCE = 1.5;
    public static final double POSITION_TOLERANCE = 1.5;
    public static final double HEADING_TOLERANCE = 5.0;
    public static final double TILT_TARGET = -40.0;
    public static final double TILT_TOLERANCE = 2.0;
    private static final String RECORDED = "pair.recorded";
    private static final String PRESSED_AT = "pair.pressedAt";
    private static final String PRESSED_HEADING = "pair.pressedHeading";
    private static final String PRESSED_TILT = "pair.pressedTilt";
    private static final String TILTED_AT = "pair.tiltedAt";
    private static final String BEFORE = "pair.before";
    private static final int STEP_TICKS = 20 * 180;

    private PairingSteps() {
    }

    public static void register() {
        SelfTest.clientStep(27, PAIR, STEP_TICKS, Script.create()
                .append(pairByClick(CamPresets.TOUR, -40, -100))
                .then("Tablet trägt die Seriennummer", PairingSteps::linked));
        SelfTest.clientStep(28, IN_FLIGHT, STEP_TICKS, Script.create()
                .append(pairByClick(CamPresets.TOUR, -60, -120))
                .then("Tablet trägt die Seriennummer", PairingSteps::linked)
                .then("Editor öffnen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        mc.player.setXRot(-70.0F);
                        return StepResult.pending();
                    }
                    if (ticks == 5) {
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    }
                    if (mc.gui.screen() instanceof PlannerScreen) {
                        return StepResult.pass();
                    }
                    return ticks > 100 ? StepResult.fail("Editor öffnet nicht") : StepResult.pending();
                })
                .then("„Flug aufzeichnen“ einschalten", PairingSteps::startRecorder)
                .then("Editor schließen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof PlannerScreen screen) {
                        screen.onClose();
                    }
                    return mc.gui.screen() == null && ticks >= 5 ? StepResult.pass() : ticks > 100 ? StepResult.fail("Editor bleibt offen")
                            : StepResult.pending();
                })
                .server("Tablet ins Inventar, Funke in die Hand", PairingSteps::remoteInHand)
                .sleep(10)
                .server("Funke binden", Pilot::bind)
                .sleep(14)
                .server("Flug starten", Pilot::start)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent())
                .sleep(20)
                .then("scharf schalten", Pilot::arm)
                .then("Kompass prüfen", Pilot::compass)
                .append(Flight.takeoff(5.0))
                .then("geradeaus fliegen", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(Pilot.PITCH_FORWARD, ticks < 120);
                    return ticks >= 120 ? StepResult.pass() : StepResult.pending();
                })
                .then("gieren", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(Minecraft.getInstance().options.keyRight, ticks < 30);
                    return ticks >= 30 ? StepResult.pass() : StepResult.pending();
                })
                .then("in die neue Richtung fliegen", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(Pilot.PITCH_FORWARD, ticks < 50);
                    return ticks >= 50 ? StepResult.pass() : StepResult.pending();
                })
                .then("bremsen", Flight::brake)
                .then("aufgezeichnete Wegpunkte", PairingSteps::recorded)
                .then("Gimbal neigen", PairingSteps::tiltGimbal)
                .then("Wegpunkt per Taste", PairingSteps::pressWaypoint)
                .then("Wegpunkt im Flug im Tablet", PairingSteps::flightWaypoint)
                .then("Gimbal zurück", PairingSteps::tiltBack), ctx -> {
                    PlannerClient.recorder().stop();
                    StepResult reset = resetTilt(ctx.tick());
                    if (reset.done()) {
                        Pilot.releaseKeys();
                    }
                    return reset;
                });
    }

    static Script pairByClick(CamPresets.Entry entry, int dx, int dz) {
        return Script.create()
                .server("Drohne " + entry.path() + " setzen, leeres Tablet in die Hand", (server, state) -> {
                    Pilot.spawn(server, state, entry, Arena.ground(dx, dz), 0.0F);
                    ServerPlayer player = Arena.player(server);
                    Entity drone = Pilot.serverDrone(server, state);
                    player.getInventory().setItem(1, Arena.stack(Pilot.REMOTE));
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PlannerItems.flightPlanner()
                            .orElseThrow(() -> new IllegalStateException("Tablet fehlt"))));
                    player.teleportTo(player.level(), drone.getX(), drone.getY(), drone.getZ() - 1.8, Set.<Relative>of(), 0.0F, 30.0F, true);
                })
                .then("Drohne und Tablet am Client", (ctx, ticks) -> {
                    boolean ready = CamFlights.clientDrone(ctx.state()).isPresent() && held().isPresent();
                    if (ready && ticks >= 10) {
                        return StepResult.pass();
                    }
                    return ticks > 100 ? StepResult.fail("Drohne oder Tablet fehlt am Client") : StepResult.pending();
                })
                .then("Blick auf die Drohne", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    if (!CamDroneData.NO_SERIAL.equals(serialOf(drone.get()))) {
                        return StepResult.fail("Drohne ist vor dem Koppeln schon gekoppelt");
                    }
                    CamFlights.lookAt(drone.get());
                    boolean aimed = mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == drone.get();
                    return aimed && ticks >= 5 ? StepResult.pass() : ticks > 60 ? StepResult.fail("Fadenkreuz trifft die Drohne nicht: " + mc.hitResult)
                            : StepResult.pending();
                })
                .then("Rechtsklick mit dem Tablet", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                    return StepResult.pass();
                });
    }

    private static Optional<ItemStack> held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) ? Optional.of(stack) : Optional.empty();
    }

    private static UUID serialOf(Entity drone) {
        UUID serial = PropwashApi.droneData().get(drone, CamDroneData.serial());
        return serial == null ? CamDroneData.NO_SERIAL : serial;
    }

    private static StepResult linked(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<ItemStack> tablet = held();
        if (drone.isEmpty() || tablet.isEmpty()) {
            return StepResult.fail("Drohne oder Tablet fehlt am Client");
        }
        Optional<PlannerLink> link = PlannerTablets.link(tablet.get());
        UUID serial = serialOf(drone.get());
        if (link.isEmpty() || CamDroneData.NO_SERIAL.equals(serial) || !link.get().serial().equals(serial)) {
            return ticks > 100 ? StepResult.fail("Tablet nicht gekoppelt (Verknüpfung " + link.map(l -> l.shortSerial()).orElse("keine") + ", Drohne "
                    + serial + ")") : StepResult.pending();
        }
        ctx.state().put(CamFlights.SERIAL, serial);
        return StepResult.pass("Rechtsklick koppelt: Tablet trägt die Seriennummer " + link.get().shortSerial() + " der Drohne, Klasse "
                + link.get().flightClassValue().map(Enum::name).orElse("?"));
    }

    private static StepResult startRecorder(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        FlightRecorder recorder = PlannerClient.recorder();
        if (recorder.active()) {
            return ticks >= 5 ? StepResult.pass("„Flug aufzeichnen“ läuft") : StepResult.pending();
        }
        if (ticks % 20 == 2) {
            EditorLayout layout = screen.layout();
            Rect row = layout.settings().get(EditorLayout.SETTING_RECORD);
            Rect value = EditorLayout.valueBox(row, layout.settingLabelWidth());
            if (value.w() <= 0 || value.h() <= 0 || value.x() + value.w() > screen.width || value.y() + value.h() > screen.height) {
                return StepResult.fail("Zeile „Flug aufzeichnen“ nicht im Bild: " + value);
            }
            click(screen, value.x() + value.w() / 2.0, value.y() + value.h() / 2.0);
        }
        return ticks > 100 ? StepResult.fail("„Flug aufzeichnen“ startet nicht per Klick") : StepResult.pending();
    }

    private static void click(Screen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static void remoteInHand(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ItemStack tablet = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!PlannerItems.isPlanner(tablet)) {
            throw new IllegalStateException("Tablet nicht in der Hand am Server");
        }
        player.getInventory().setItem(TABLET_SLOT, tablet.copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(Pilot.REMOTE));
    }

    private static Optional<Mission> tabletMission() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getInventory().getItem(TABLET_SLOT);
        if (!PlannerItems.isPlanner(stack)) {
            return Optional.empty();
        }
        return Optional.of(PlannerTablets.read(stack).activeMission());
    }

    private static StepResult recorded(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Mission> mission = tabletMission();
        if (mission.isEmpty()) {
            return StepResult.fail("Tablet fehlt im Inventar");
        }
        List<Waypoint> points = mission.get().waypoints();
        if (ticks < 40) {
            return StepResult.pending();
        }
        if (points.size() < 5) {
            return ticks > 200 ? StepResult.fail("nur " + points.size() + " Wegpunkte aufgezeichnet (Aufzeichnung " + PlannerClient.recorder().active() + ", "
                    + PlannerClient.recorder().recorded() + " gezählt)") : StepResult.pending();
        }
        List<String> spacing = new ArrayList<>();
        int straight = 0;
        for (int i = 1; i < points.size(); i++) {
            Waypoint a = points.get(i - 1);
            Waypoint b = points.get(i);
            double d = Math.sqrt(Math.pow(b.x() - a.x(), 2) + Math.pow(b.y() - a.y(), 2) + Math.pow(b.z() - a.z(), 2));
            spacing.add(Flight.format(d));
            if (Math.abs(d - FlightRecorder.SPACING) <= SPACING_TOLERANCE) {
                straight++;
            }
            if (d < FlightRecorder.MIN_TURN_DISTANCE - 0.5 || d > FlightRecorder.SPACING + SPACING_TOLERANCE) {
                return StepResult.fail("Abstand der Aufzeichnung " + Flight.format(d) + " m zwischen Punkt " + i + " und " + (i + 1) + " (Abstände "
                        + String.join("/", spacing) + ")");
            }
        }
        if (straight < 2) {
            return StepResult.fail("geradeaus kein Punkt alle " + Flight.format(FlightRecorder.SPACING) + " m (Abstände " + String.join("/", spacing) + ")");
        }
        if (straight == points.size() - 1) {
            return StepResult.fail("kein Punkt beim Richtungswechsel (Abstände " + String.join("/", spacing) + ")");
        }
        for (Waypoint w : points) {
            if (w.heading() != Waypoint.Heading.COURSE) {
                return StepResult.fail("aufgezeichneter Punkt mit Blickrichtung " + w.heading());
            }
        }
        ctx.state().put(RECORDED, points.size());
        return StepResult.pass(points.size() + " Wegpunkte aufgezeichnet, Abstände " + String.join("/", spacing) + " m (alle "
                + Flight.format(FlightRecorder.SPACING) + " m und beim Richtungswechsel)");
    }

    private static StepResult pressWaypoint(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        Optional<DroneView> view = drone.flatMap(d -> PropwashApi.drones().view(d));
        if (drone.isEmpty() || view.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(BEFORE, tabletMission().map(Mission::size).orElse(0));
            ctx.state().put(PRESSED_AT, drone.get().position());
            ctx.state().put(PRESSED_HEADING, Compass.headingTo(view.get().forward().x, view.get().forward().z));
            ctx.state().put(PRESSED_TILT, GimbalClient.tilt(drone.get()));
        }
        Pilot.hold(CamControl.WAYPOINT_ADD, ticks < 4);
        return ticks >= 6 ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult flightWaypoint(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Mission> mission = tabletMission();
        if (mission.isEmpty()) {
            return StepResult.fail("Tablet fehlt im Inventar");
        }
        int before = (int) ctx.state().get(BEFORE);
        List<Waypoint> points = mission.get().waypoints();
        if (points.size() <= before) {
            return ticks > 100 ? StepResult.fail("Taste „Wegpunkt setzen“ speichert keinen Punkt (" + points.size() + " Wegpunkte)") : StepResult.pending();
        }
        if (points.size() > before + 1) {
            return StepResult.fail((points.size() - before) + " Punkte statt einem nach einem Tastendruck");
        }
        Waypoint w = points.getLast();
        Vec3 at = (Vec3) ctx.state().get(PRESSED_AT);
        double off = Math.sqrt(Math.pow(w.x() - at.x, 2) + Math.pow(w.y() - at.y, 2) + Math.pow(w.z() - at.z, 2));
        double heading = (double) ctx.state().get(PRESSED_HEADING);
        double headingError = Math.abs(Compass.wrap180(w.yawDeg() - heading));
        if (w.heading() != Waypoint.Heading.FIXED || off > POSITION_TOLERANCE || headingError > HEADING_TOLERANCE) {
            return StepResult.fail("Wegpunkt im Flug " + Flight.format(off) + " m neben der Drohne, Blickrichtung " + w.heading() + " "
                    + Flight.format(w.yawDeg()) + "° statt " + Flight.format(heading) + "°");
        }
        double tilt = (double) ctx.state().get(PRESSED_TILT);
        if (!(Math.abs(w.tiltDeg() - tilt) <= TILT_TOLERANCE)) {
            return StepResult.fail("Wegpunkt im Flug speichert Neigung " + w.tiltDeg() + "° statt " + Flight.format(tilt) + "° (Gimbal beim Tastendruck)");
        }
        return StepResult.pass("Taste „Wegpunkt setzen“ speichert Punkt " + points.size() + " " + Flight.format(off) + " m neben der Drohne, "
                + "Blickrichtung fest " + Flight.format(w.yawDeg()) + "° (Nase " + Flight.format(heading) + "°), Neigung " + w.tiltDeg()
                + "° (Gimbal " + Flight.format(tilt) + "°)");
    }

    private static KeyMapping tiltKey(boolean up) {
        return (up ? CamKeys.increase(CamControl.GIMBAL_TILT) : CamKeys.decrease(CamControl.GIMBAL_TILT))
                .orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Neigung fehlt"));
    }

    private static StepResult tiltGimbal(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().remove(TILTED_AT);
        }
        double tilt = GimbalClient.tilt(drone.get());
        if (!ctx.state().containsKey(TILTED_AT) && tilt <= TILT_TARGET) {
            ctx.state().put(TILTED_AT, ticks);
        }
        boolean reached = ctx.state().containsKey(TILTED_AT);
        Pilot.hold(tiltKey(false), !reached);
        if (reached && ticks - (int) ctx.state().get(TILTED_AT) >= 10) {
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("Gimbal neigt nicht unter " + Flight.format(TILT_TARGET) + "°: " + Flight.format(tilt) + "°")
                : StepResult.pending();
    }

    private static StepResult tiltBack(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        return resetTilt(ticks);
    }

    private static StepResult resetTilt(int ticks) {
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (Math.abs(axis) < 0.02 || !PropwashClientApi.input().flying()) {
            Pilot.hold(tiltKey(true), false);
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass();
        }
        Pilot.hold(tiltKey(true), axis < 0.0);
        Pilot.hold(tiltKey(false), axis > 0.0);
        return ticks > 300 ? StepResult.fail("Gimbal-Achse bleibt bei " + Flight.format(axis)) : StepResult.pending();
    }
}
