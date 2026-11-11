package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.client.planner.WaypointPreview;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.WaypointTaskServer;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerSupport;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.WaypointField;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WaypointTaskSteps {
    public static final int MISSION_NUMBER = 701;
    public static final int PLANNER_NUMBER = 702;
    public static final int GUARD_NUMBER = 703;
    public static final int PILOTED_NUMBER = 704;
    public static final String PILOTED = "mission: Kamera-Drohne im Pilotenflug – Wegpunkt-Aktion läuft am Server";
    public static final int COMPANION_NUMBER = 705;
    public static final String COMPANION = "zusammenspiel: Wegpunkt-Aktion löst den Werfer von Mob Defense an einer Freestyle aus";
    public static final Identifier MD_DISPENSER = Identifier.fromNamespaceAndPath("mobdefense", "dispenser_mount");
    public static final int ARROWS = 8;
    public static final String MISSION = "mission: Freestyle-5″ fliegt ohne Pilot eine Mission mit Licht- und Zubehör-Aktion";
    public static final String PLANNER = "planner: Wegpunkt-Aktionen und Hinweise für FPV-Drohnen im Editor";
    public static final String GUARD = "mission: FPV-Drohne startet keine Mission mit Pilot und keine ohne GPS";
    public static final Identifier FREESTYLE = Identifier.fromNamespaceAndPath("propwash", "freestyle");
    public static final Identifier LED_STRIP = Identifier.fromNamespaceAndPath("propwash", "led_strip");
    public static final int SNOWBALLS = 6;
    public static final int LIGHT_POINT = 1;
    public static final int DROP_POINT = 2;
    private static final int MISSION_DX = 110;
    private static final int MISSION_DZ = -40;
    private static final int PLANNER_DX = 130;
    private static final int PLANNER_DZ = -40;
    private static final int GUARD_DX = 150;
    private static final int GUARD_DZ = -40;
    private static final int PILOTED_DX = 120;
    private static final int PILOTED_DZ = 30;
    private static final int COMPANION_DX = 120;
    private static final int COMPANION_DZ = 70;
    private static final int STEP_TICKS = 20 * 240;
    private static final String MISSION_KEY = "tasks.mission";
    private static final String DONE_AT = "tasks.done";
    private static final int SMALL_WIDTH = 320;
    private static final int SMALL_HEIGHT = 240;
    private static final int SMALL_SCALE = 3;

    private WaypointTaskSteps() {
    }

    public static void register() {
        ExampleDropper.install();
        mission();
        planner();
        guard();
        piloted();
        companion();
        WaypointEverySteps.register();
    }

    private static void companion() {
        if (!Services.PLATFORM.isModLoaded("mobdefense")) {
            CamDrones.LOG.info("[Kamera-Selbsttest] {} nicht angemeldet: Mob Defense fehlt im mods-Ordner", COMPANION);
            return;
        }
        SelfTest.clientStep(COMPANION_NUMBER, COMPANION, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit Dispenser-Halterung von Mob Defense, Pfeile laden, koppeln, Mission mit Zubehör-Aktion 1 an Punkt 3",
                        (server, state) -> {
                            Arena.calmWind(server);
                            DronePreset preset = PropwashApi.builds().presets().stream().filter(p -> p.id().equals(FREESTYLE)).findFirst().orElseThrow();
                            DroneData data = preset.data().withBuild(preset.data().build().withAccessory(AccessorySlot.FRONT, MD_DISPENSER));
                            Pilot.spawn(server, state, data, "freestyle", Arena.ground(COMPANION_DX, COMPANION_DZ), 0.0F);
                            Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            Arena.player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                            CamFlights.pair(server, state);
                            Entity drone = Pilot.serverDrone(server, state);
                            PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).orElseThrow(() -> new IllegalStateException("Halterung ohne Fach"))
                                    .set(0, new ItemStack(Items.ARROW, ARROWS));
                            List<Waypoint> waypoints = new ArrayList<>();
                            for (double dz : new double[]{5.0, 11.0, 17.0, 23.0}) {
                                waypoints.add(Waypoint.at(drone.getX(), drone.getY() + 6.0, drone.getZ() + dz));
                            }
                            waypoints.set(2, waypoints.get(2).withAccessory(Waypoint.Accessory.ACTION_1, false).withWait(2.0));
                            CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
                            WaypointTaskServer.clearReports();
                        })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .then("Planer erkennt den Werfer", (ctx, ticks) -> {
                    DroneSupport support = PlannerSupport.of(CamFlights.clientDrone(ctx.state()));
                    if (!support.known()) {
                        return ticks > 60 ? StepResult.fail("Bau unbekannt") : StepResult.pending();
                    }
                    return support.actionAccessory() ? StepResult.pass("Aktion 1: " + support.actionName(1).map(I18n::get).orElse("ohne Namen"))
                            : StepResult.fail("Dispenser-Halterung ohne Zubehör-Verhalten");
                })
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission fliegt zu Ende", WaypointTaskSteps::finished)
                .then("Aktion an Punkt 3 ausgelöst, Pfeil geflogen", WaypointTaskSteps::companionFired));
    }

    private static StepResult companionFired(SelfTest.StepContext ctx, int ticks) {
        List<String> seen = WaypointTaskServer.reports().stream().map(r -> (r.index() + 1) + ":" + r.task() + ":" + r.outcome()).toList();
        if (!seen.equals(List.of("3:accessory.1:DONE"))) {
            return ticks > 60 ? StepResult.fail("Berichte " + seen) : StepResult.pending();
        }
        StepResult arrows = Script.server(ctx, ticks, (server, state) -> {
            Entity drone = Pilot.serverDrone(server, ctx.state());
            int flying = drone.level().getEntitiesOfClass(AbstractArrow.class, drone.getBoundingBox().inflate(48.0)).size();
            int left = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).map(inv -> inv.get(0).getCount()).orElse(-1);
            if (flying < 1 && left >= ARROWS) {
                throw new IllegalStateException("kein Pfeil in der Luft oder am Boden, " + left + " Pfeile im Fach");
            }
            ctx.state().put("tasks.arrows", flying + " Pfeile in der Welt, " + left + " im Fach");
        });
        if (!arrows.done() || arrows.failed()) {
            return arrows;
        }
        return StepResult.pass("Berichte " + seen + ", " + ctx.state().get("tasks.arrows"));
    }

    private static void piloted() {
        SelfTest.clientStep(PILOTED_NUMBER, PILOTED, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, PILOTED_DX, PILOTED_DZ, 0.0F))
                .server("koppeln, Mission mit Licht an Punkt 2 und 3", (server, state) -> {
                    CamFlights.pair(server, state);
                    Entity drone = Pilot.serverDrone(server, state);
                    double y = drone.getY() + 5.0;
                    List<Waypoint> waypoints = new ArrayList<>();
                    for (double dz : new double[]{4.0, 9.0, 14.0}) {
                        waypoints.add(Waypoint.at(drone.getX(), y, drone.getZ() + dz));
                    }
                    waypoints.set(1, waypoints.get(1).withLight(Waypoint.Light.ON));
                    waypoints.set(2, waypoints.get(2).withLight(Waypoint.Light.RAINBOW));
                    CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
                    WaypointTaskServer.clearReports();
                })
                .append(Flight.takeoff(4.0))
                .then("Mission im Pilotenflug starten", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(CamControl.MISSION_START, ticks < 4);
                    return ticks >= 6 ? StepResult.pass() : StepResult.pending();
                })
                .then("Server meldet die Licht-Aktionen aus dem Pilotenflug", WaypointTaskSteps::pilotedReports));
    }

    private static StepResult pilotedReports(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        List<String> seen = WaypointTaskServer.reports().stream().map(r -> (r.index() + 1) + ":" + r.task() + ":" + r.outcome() + ":" + r.skip())
                .toList();
        List<String> wanted = List.of("2:light.on:SKIPPED:NO_LIGHT_API", "3:light.rainbow:SKIPPED:NO_LIGHT_API");
        if (seen.equals(wanted)) {
            return StepResult.pass("Pilot fliegt, Server meldet " + seen + (PropwashClientApi.input().flying() ? ", Pilot noch im FPV" : ""));
        }
        if (!PropwashClientApi.input().flying()) {
            return StepResult.fail("Pilot nicht mehr im FPV, Berichte " + seen);
        }
        return ticks > 20 * 60 ? StepResult.fail("Berichte " + seen + " statt " + wanted) : StepResult.pending();
    }

    static DroneData freestyle(boolean dropper) {
        DronePreset preset = PropwashApi.builds().presets().stream().filter(p -> p.id().equals(FREESTYLE)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Preset " + FREESTYLE + " fehlt"));
        BuildSpec build = preset.data().build().withAccessory(AccessorySlot.BOTTOM, LED_STRIP);
        if (dropper) {
            build = build.withAccessory(AccessorySlot.FRONT, ExampleDropper.ID);
        }
        return preset.data().withBuild(build);
    }

    static Mission tasks(Entity drone) {
        double y = drone.getY() + 6.0;
        double[][] offsets = {{0.0, 6.0}, {0.0, 13.0}, {3.0, 20.0}, {0.0, 27.0}, {0.0, 33.0}};
        List<Waypoint> waypoints = new ArrayList<>();
        for (double[] offset : offsets) {
            waypoints.add(Waypoint.at(drone.getX() + offset[0], y, drone.getZ() + offset[1]));
        }
        waypoints.set(LIGHT_POINT, waypoints.get(LIGHT_POINT).withLight(Waypoint.Light.ON));
        waypoints.set(DROP_POINT, waypoints.get(DROP_POINT).withAccessory(Waypoint.Accessory.ACTION_1, true));
        waypoints.set(4, waypoints.get(4).withAccessory(Waypoint.Accessory.END_REPEAT, false).withLight(Waypoint.Light.BLINK));
        return Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(4.0);
    }

    private static void mission() {
        SelfTest.clientStep(MISSION_NUMBER, MISSION, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit Beispiel-Werfer vorn und LED-Streifen unten setzen, koppeln, Schneebälle laden, Mission mit Licht an Punkt 2 "
                        + "und Zubehör-Aktion 1 ab Punkt 3 bis Punkt 4", (server, state) -> {
                    Arena.calmWind(server);
                    Pilot.spawn(server, state, freestyle(true), "freestyle", Arena.ground(MISSION_DX, MISSION_DZ), 0.0F);
                    Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    Arena.player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    CamFlights.pair(server, state);
                    Entity drone = Pilot.serverDrone(server, state);
                    AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                            .orElseThrow(() -> new IllegalStateException("Werfer ohne Fach"));
                    inventory.set(0, new ItemStack(Items.SNOWBALL, SNOWBALLS));
                    Mission mission = tasks(drone);
                    state.put(MISSION_KEY, mission);
                    CamFlights.mission(server, state, mission);
                    ExampleDropper.reset();
                    WaypointTaskServer.clearReports();
                })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .then("Planer erkennt Freestyle mit Werfer und LED-Streifen", WaypointTaskSteps::recognised)
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission fliegt zu Ende", WaypointTaskSteps::finished)
                .then("Zubehör-Aktion 1 an Punkt 3 und 4 ausgelöst, Licht übersprungen und gemeldet", WaypointTaskSteps::reported)
                .then("Werfer hat zwei Schneebälle geworfen", WaypointTaskSteps::thrown));
    }

    private static StepResult recognised(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (!ExampleDropper.available()) {
            return StepResult.fail("Beispiel-Werfer nicht angemeldet");
        }
        DroneSupport support = PlannerSupport.of(drone);
        if (!support.known()) {
            return ticks > 60 ? StepResult.fail("Bau der Drohne am Client unbekannt") : StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        if (support.kind() != DroneSupport.Kind.FPV) {
            problems.add("Art " + support.kind());
        }
        if (!support.role().filter("freestyle"::equals).isPresent()) {
            problems.add("Rolle " + support.role());
        }
        if (!support.actionAccessory()) {
            problems.add("kein Zubehör mit Aktionen");
        }
        if (!support.ledStrip()) {
            problems.add("kein LED-Streifen");
        }
        if (!support.gpsFix()) {
            problems.add("kein GPS (" + support.satellites() + " Satelliten)");
        }
        if (!support.actionName(1).filter(ExampleDropper.NAME_KEY::equals).isPresent()) {
            problems.add("Name der Aktion 1: " + support.actionName(1));
        }
        Mission mission = (Mission) ctx.state().get(MISSION_KEY);
        if (support.skip(mission, DROP_POINT) != DroneSupport.Skip.NONE || support.skip(mission, LIGHT_POINT) != DroneSupport.Skip.NO_LIGHT_API) {
            problems.add("Markierung " + support.skip(mission, DROP_POINT) + "/" + support.skip(mission, LIGHT_POINT));
        }
        return problems.isEmpty() ? StepResult.pass("FPV (Freestyle), " + support.satellites() + " Satelliten, Werfer mit „"
                + I18n.get(ExampleDropper.NAME_KEY) + "“, LED-Streifen, Licht-Aktion als übersprungen markiert (" + support.skipped(mission)
                + " von 4 Aktionen)") : StepResult.fail(String.join(", ", problems));
    }

    private static StepResult finished(SelfTest.StepContext ctx, int ticks) {
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        if (status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false)) {
            ctx.state().putIfAbsent(DONE_AT, ticks);
            int since = ticks - (int) ctx.state().get(DONE_AT);
            return since > 30 ? StepResult.pass("Mission fertig nach " + ticks / 20 + " s") : StepResult.pending();
        }
        if (status.map(s -> s.state() == NavLogic.MissionState.ABORTED).orElse(false)) {
            return StepResult.fail("Mission abgebrochen: " + StatusProbe.describe(CamFlights.mark(ctx)));
        }
        return ticks > 20 * 150 ? StepResult.fail("Mission kommt nicht zu Ende, Zustand " + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("-"))
                : StepResult.pending();
    }

    private static StepResult reported(SelfTest.StepContext ctx, int ticks) {
        List<WaypointTaskServer.Report> reports = WaypointTaskServer.reports();
        List<String> seen = reports.stream().map(r -> (r.index() + 1) + ":" + r.task() + ":" + r.outcome()).toList();
        List<String> wanted = List.of("2:light.on:SKIPPED", "3:accessory.1:DONE", "4:accessory.1:DONE", "5:light.blink:SKIPPED");
        if (!seen.equals(wanted)) {
            return ticks > 60 ? StepResult.fail("Berichte " + seen + " statt " + wanted) : StepResult.pending();
        }
        boolean lightReason = reports.stream().filter(r -> r.task().startsWith("light.")).allMatch(r -> r.skip() == DroneSupport.Skip.NO_LIGHT_API);
        if (!lightReason) {
            return StepResult.fail("Licht ohne Grund NO_LIGHT_API übersprungen: " + reports);
        }
        return StepResult.pass("Berichte " + seen);
    }

    private static StepResult thrown(SelfTest.StepContext ctx, int ticks) {
        List<ExampleDropper.Press> presses = ExampleDropper.presses();
        List<ExampleDropper.Press> down = presses.stream().filter(ExampleDropper.Press::pressed).toList();
        String player = Minecraft.getInstance().player == null ? "?" : Minecraft.getInstance().player.getGameProfile().name();
        if (down.size() != 2 || presses.size() != 4) {
            return StepResult.fail("Werfer bekam " + presses + " statt zweimal Drücken und Loslassen");
        }
        for (ExampleDropper.Press press : down) {
            if (press.number() != ExampleDropper.DROP || !press.actor().equals(player) || press.piloted()
                    || press.result() != DispenseResult.Status.FIRED) {
                return StepResult.fail("Druck " + press + " (erwartet Aktion 1 von " + player + " ohne Pilot, geworfen)");
            }
        }
        StepResult left = Script.server(ctx, ticks, (server, state) -> {
            Entity drone = Pilot.serverDrone(server, ctx.state());
            int count = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).map(inv -> inv.get(0).getCount()).orElse(-1);
            if (count != SNOWBALLS - 2) {
                throw new IllegalStateException(count + " Schneebälle im Fach statt " + (SNOWBALLS - 2));
            }
        });
        if (!left.done() || left.failed()) {
            return left;
        }
        return StepResult.pass("zwei Würfe als " + player + " ohne Pilot, " + (SNOWBALLS - 2) + " Schneebälle übrig");
    }

    private static void planner() {
        SelfTest.clientStep(PLANNER_NUMBER, PLANNER, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit Beispiel-Werfer setzen, Flugplaner mit Mission voller Aktionen in die Hand", (server, state) -> {
                    Pilot.spawn(server, state, freestyle(true), "freestyle", Arena.ground(PLANNER_DX, PLANNER_DZ), 0.0F);
                    CamFlights.pair(server, state);
                    tablet(server, state, tasks(Pilot.serverDrone(server, state)));
                })
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .then("Seriennummer der Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state())
                        .filter(drone -> CamFlights.serial(ctx.state()).equals(PlannerTablets.serialOf(drone))).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Seriennummer fehlt am Client") : StepResult.pending())
                .run("Editor öffnen", WaypointTaskSteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .append(Shots.photoWindow())
                .then("Wegpunkt 3 wählen, Seite Aktionen", (ctx, ticks) -> select(DROP_POINT, 1))
                .then("FPV, Aktionen und Markierungen im Editor", WaypointTaskSteps::editorShows)
                .append(Shots.photos("planner_waypoint_actions", WaypointTaskSteps::refresh))
                .then("Wegpunkt 2 wählen: Licht als übersprungen markiert", (ctx, ticks) -> select(LIGHT_POINT, 1))
                .append(Shots.photos("planner_waypoint_light", WaypointTaskSteps::refresh))
                .then("Wegpunkt 4: wiederholte Zubehör-Aktion", (ctx, ticks) -> select(3, 1))
                .then("Wert zeigt die Wiederholung von Punkt 3", WaypointTaskSteps::repeatShown)
                .then("Fenster mit GUI 320×240", (ctx, ticks) -> Shots.window(ctx, ticks, SMALL_WIDTH * SMALL_SCALE, SMALL_HEIGHT * SMALL_SCALE,
                        SMALL_SCALE))
                .then("Wegpunkt 3 wählen, Klick auf „Aktionen ›“", (ctx, ticks) -> toggle(ctx, ticks, DROP_POINT, 1))
                .append(Shots.photos("planner_waypoint_actions_320", WaypointTaskSteps::refresh))
                .then("Klick auf „‹ Flug“", (ctx, ticks) -> toggle(ctx, ticks, DROP_POINT, 0))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .append(Shots.photoWindow())
                .server("vor die Mission stellen, Blick auf die Marker", WaypointTaskSteps::lookAtMarkers)
                .then("Marker mit Aktionen in der Welt", WaypointTaskSteps::markersShown)
                .append(Shots.photos("planner_waypoint_actions_world"))
                .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
    }

    private static StepResult toggle(SelfTest.StepContext ctx, int ticks, int index, int wanted) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        if (!screen.layout().detailPaged()) {
            return ticks > 20 ? StepResult.fail("Detail bei " + screen.width + "×" + screen.height + " nicht in Seiten geteilt") : StepResult.pending();
        }
        screen.state().select(EditorState.Selection.waypoint(index));
        if (screen.detailPage() == wanted) {
            return StepResult.pass("Seite " + (wanted + 1) + " von 2 bei " + screen.width + "×" + screen.height + ", Umschalter "
                    + screen.layout().pageToggle());
        }
        if (ticks % 10 == 0) {
            Rect toggle = screen.layout().pageToggle();
            MouseButtonEvent event = new MouseButtonEvent(toggle.centerX(), toggle.centerY(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
        }
        return ticks > 40 ? StepResult.fail("Klick auf den Umschalter wechselt die Seite nicht") : StepResult.pending();
    }

    private static void lookAtMarkers(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        Vec3 stand = drone.position().add(-6.0, 4.5, 9.0);
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), -40.0F, -4.0F, true);
    }

    private static StepResult markersShown(SelfTest.StepContext ctx, int ticks) {
        int drawn = WaypointPreview.lastDrawn();
        if (drawn >= 5) {
            return StepResult.pass(drawn + " Marker, Aktionen als Text darüber: " + WaypointPreview.taskLabel(
                    WaypointTasks.due(heldMission(), DROP_POINT)));
        }
        return ticks > 60 ? StepResult.fail("nur " + drawn + " Marker in der Welt") : StepResult.pending();
    }

    private static Mission heldMission() {
        return held().map(stack -> PlannerTablets.read(stack).activeMission()).orElse(Mission.empty());
    }

    private static void refresh() {
        if (Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen) {
            screen.refreshSupport();
        }
    }

    private static StepResult select(int index, int page) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        screen.state().select(EditorState.Selection.waypoint(index));
        screen.detailPage(page);
        screen.refreshSupport();
        return StepResult.pass("Wegpunkt " + (index + 1) + ", Seite " + (screen.detailPage() + 1) + " von " + screen.layout().detailPages());
    }

    private static StepResult editorShows(SelfTest.StepContext ctx, int ticks) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        screen.refreshSupport();
        DroneSupport support = screen.support();
        if (!support.known()) {
            return ticks > 60 ? StepResult.fail("Ausrüstung bleibt unbekannt") : StepResult.pending();
        }
        Mission mission = screen.state().mission();
        List<String> problems = new ArrayList<>();
        if (support.header().stream().map(FieldText::key).noneMatch((FieldText.PREFIX + "support.header.fpv")::equals)) {
            problems.add("Kopf ohne FPV");
        }
        if (mission.waypoint(DROP_POINT).accessory() != Waypoint.Accessory.ACTION_1 || !mission.waypoint(DROP_POINT).repeat()) {
            problems.add("Mission im Editor ohne Zubehör-Aktion: " + mission.waypoint(DROP_POINT));
        }
        if (support.skipped(mission) != 2) {
            problems.add(support.skipped(mission) + " übersprungene Aktionen statt 2 (Licht)");
        }
        if (screen.layout().detailPaged() && screen.layout().detailPage(WaypointField.LIGHT.ordinal()) != 1) {
            problems.add("Licht nicht auf der Seite Aktionen");
        }
        return problems.isEmpty() ? StepResult.pass("Kopf " + support.header().stream().map(t -> I18n.get(t.key())).toList() + ", "
                + support.skipped(mission) + " Licht-Aktionen markiert, Detail " + screen.layout().detailPages() + " Seiten bei "
                + screen.width + "×" + screen.height) : StepResult.fail(String.join(", ", problems));
    }

    private static StepResult repeatShown(SelfTest.StepContext ctx, int ticks) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        Mission mission = screen.state().mission();
        Waypoint w = mission.waypoint(3);
        if (w.accessory() != Waypoint.Accessory.NONE || screen.support().skip(mission, 3) != DroneSupport.Skip.NONE) {
            return StepResult.fail("Punkt 4: " + w.accessory() + ", " + screen.support().skip(mission, 3));
        }
        return StepResult.pass("Punkt 4 wiederholt Aktion 1 von Punkt 3: „" + I18n.get(FieldText.PREFIX + "value.accessory.repeated", 1, 3) + "“");
    }

    private static void guard() {
        SelfTest.clientStep(GUARD_NUMBER, GUARD, STEP_TICKS, Script.create()
                .server("Freestyle-5″ unter einem Steindach setzen, koppeln, Mission an der Drohne", (server, state) -> {
                    BlockPos ground = Arena.ground(GUARD_DX, GUARD_DZ);
                    Arena.fill(Arena.level(server), ground.offset(-3, 4, -3), ground.offset(3, 4, 3), Blocks.STONE.defaultBlockState());
                    Pilot.spawn(server, state, freestyle(false), "freestyle", ground, 0.0F);
                    Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    CamFlights.pair(server, state);
                    CamFlights.mission(server, state, tasks(Pilot.serverDrone(server, state)));
                })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .then("Planer meldet kein GPS", WaypointTaskSteps::noGps)
                .server("Flugplaner in die Hand", (server, state) -> tablet(server, state, tasks(Pilot.serverDrone(server, state))))
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .run("Editor öffnen", WaypointTaskSteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Kopf und Statuszeile: kein GPS", WaypointTaskSteps::noGpsInEditor)
                .append(Shots.photoWindow())
                .append(Shots.photos("planner_fpv_no_gps", WaypointTaskSteps::refresh))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
                .then("Start ohne GPS", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Meldung kein GPS-Fix", (ctx, ticks) -> reason(ctx, ticks, AutonomyStatus.NOT_READY, AutonomyStatus.Reason.GPS))
                .server("Dach entfernen", (server, state) -> {
                    BlockPos ground = Arena.ground(GUARD_DX, GUARD_DZ);
                    Arena.fill(Arena.level(server), ground.offset(-3, 4, -3), ground.offset(3, 4, 3), Blocks.AIR.defaultBlockState());
                })
                .server("Funke binden", Pilot::bind)
                .sleep(14)
                .server("Flug starten", Pilot::start)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying())
                .then("Start mit Pilot", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Meldung: FPV-Drohne nur ohne Pilot", (ctx, ticks) -> reason(ctx, ticks, AutonomyStatus.PILOTED, AutonomyStatus.Reason.FPV_PILOTED))
                .then("kein Flug ohne Pilot, Mission bleibt bereit", WaypointTaskSteps::stillIdle));
    }

    private static StepResult noGps(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        DroneSupport support = PlannerSupport.of(drone);
        if (!support.known()) {
            return ticks > 60 ? StepResult.fail("Bau unbekannt") : StepResult.pending();
        }
        if (support.gpsFix()) {
            return StepResult.fail("GPS-Fix unter dem Dach (" + support.satellites() + " Satelliten)");
        }
        FieldText notice = support.notice().orElseThrow();
        return StepResult.pass("Hinweis „" + I18n.get(notice.key(), notice.args().toArray()) + "“");
    }

    private static StepResult noGpsInEditor(SelfTest.StepContext ctx, int ticks) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        screen.refreshSupport();
        DroneSupport support = screen.support();
        if (!support.known()) {
            return ticks > 60 ? StepResult.fail("Ausrüstung bleibt unbekannt") : StepResult.pending();
        }
        boolean header = support.header().stream().map(FieldText::key).anyMatch((FieldText.PREFIX + "support.header.no_gps")::equals);
        Optional<EditorState.Status> status = screen.state().status();
        boolean notice = status.filter(s -> s.error() && s.text().key().equals(FieldText.PREFIX + "support.notice.no_gps")).isPresent();
        return header && notice ? StepResult.pass("Kopf „" + I18n.get(FieldText.PREFIX + "support.header.no_gps") + "“, Statuszeile „"
                + I18n.get(status.get().text().key(), status.get().text().args().toArray()) + "“")
                : StepResult.fail("Kopf " + header + ", Statuszeile " + status.map(s -> s.text().key()).orElse("-"));
    }

    private static StepResult reason(SelfTest.StepContext ctx, int ticks, AutonomyStatus status, AutonomyStatus.Reason reason) {
        Optional<StatusMessage.Payload> seen = StatusProbe.first(CamFlights.mark(ctx), status);
        if (seen.isPresent()) {
            return seen.get().detail().equals(reason.code()) ? StepResult.pass("„" + I18n.get(reason.key(), seen.get().drone()) + "“")
                    : StepResult.fail("Meldung " + status + " mit Grund „" + seen.get().detail() + "“ statt „" + reason.code() + "“");
        }
        return ticks > 100 ? StepResult.fail("keine Meldung " + status + " (bekommen: " + StatusProbe.describe(CamFlights.mark(ctx)) + ")")
                : StepResult.pending();
    }

    private static StepResult stillIdle(SelfTest.StepContext ctx, int ticks) {
        StepResult checked = Script.server(ctx, ticks, (server, state) -> {
            Entity drone = Pilot.serverDrone(server, ctx.state());
            if (PropwashApi.drones().autonomy().active(drone).isPresent()) {
                throw new IllegalStateException("Flug ohne Pilot läuft trotz Pilot");
            }
        });
        if (!checked.done() || checked.failed()) {
            return checked;
        }
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        return status.map(s -> s.state() == NavLogic.MissionState.IDLE).orElse(false) ? StepResult.pass("Mission bereit, Pilot fliegt weiter")
                : StepResult.fail("Missionszustand " + status.map(MissionStatus::state).orElse(null));
    }

    private static Optional<ItemStack> held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) ? Optional.of(stack) : Optional.empty();
    }

    private static void tablet(MinecraftServer server, Map<String, Object> state, Mission mission) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow(() -> new IllegalStateException("Tablet fehlt")));
        tablet.set(PlannerItems.link(), PlannerLink.of(CamFlights.serial(state), "", FlightClass.TOUR));
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of("Aktionen", mission)), 0, TabletState.DEFAULT_GROUND_HEIGHT));
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        Vec3 stand = drone.position().add(4.0, 0.0, -2.0);
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), 90.0F, -10.0F, true);
    }

    private static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        mc.player.setXRot(-80.0F);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }
}
