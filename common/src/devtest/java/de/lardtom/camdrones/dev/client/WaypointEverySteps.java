package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.client.planner.WaypointPreview;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.WaypointTaskServer;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.EditorLayout;
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
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class WaypointEverySteps {
    public static final int EXAMPLE_NUMBER = 706;
    public static final String EXAMPLE = "mission: Zubehör-Aktion im Takt am Beispiel-Werfer – alle 10 Ticks, bei 2 Ticks nie schneller als sein Mindestabstand";
    public static final int PLANNER_NUMBER = 707;
    public static final String PLANNER = "planner: Takt im Editor – Wiederholen „alle … s“, Feld Takt in Ticks, Hinweis „höchstens so oft, wie das Zubehör erlaubt“";
    public static final int COMPANION_NUMBER = 708;
    public static final String COMPANION = "zusammenspiel: Zubehör-Aktion im Takt am Werfer von Mob Defense – alle 10 Ticks, bei 2 Ticks höchstens so oft, wie er erlaubt";
    public static final int SLOW_TICKS = 10;
    public static final int FAST_TICKS = 2;
    public static final int SLOW_POINT = 1;
    public static final int FAST_POINT = 3;
    public static final int END_POINT = 4;
    public static final Identifier MD_COOLDOWN = Identifier.fromNamespaceAndPath("mobdefense", "dispenser_cooldown_ticks");
    public static final int MD_DEFAULT_COOLDOWN = 4;
    private static final int ARROWS = 64;
    private static final int EXAMPLE_DX = 100;
    private static final int EXAMPLE_DZ = -120;
    private static final int PLANNER_DX = 140;
    private static final int PLANNER_DZ = -100;
    private static final int COMPANION_DX = 140;
    private static final int COMPANION_DZ = 100;
    private static final int STEP_TICKS = 20 * 240;
    private static final int AFTER_DONE_TICKS = 40;
    private static final String MISSION_KEY = "every.mission";
    private static final String DONE_AT = "every.done";
    private static final String DONE_REPORTS = "every.done_reports";
    private static final String START_TICK = "every.start";
    private static final String SUMMARY = "every.summary";
    private static final String CADENCE = "every.cadence";
    private static final int SMALL_WIDTH = 320;
    private static final int SMALL_HEIGHT = 240;
    private static final int SMALL_SCALE = 3;
    private static final int HOVER_TICKS = 10;
    private static final String HOVER_SHOT = "every.hover_shot";

    private WaypointEverySteps() {
    }

    public static void register() {
        example();
        planner();
        companion();
    }

    static Mission timed(Entity drone) {
        double y = drone.getY() + 6.0;
        List<Waypoint> waypoints = new ArrayList<>();
        for (double dz : new double[]{4.0, 8.0, 12.0, 16.0, 20.0, 24.0}) {
            waypoints.add(Waypoint.at(drone.getX(), y, drone.getZ() + dz));
        }
        waypoints.set(SLOW_POINT, waypoints.get(SLOW_POINT).withAccessory(Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EVERY, SLOW_TICKS).withWait(1.0));
        waypoints.set(FAST_POINT, waypoints.get(FAST_POINT).withAccessory(Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EVERY, FAST_TICKS));
        waypoints.set(END_POINT, waypoints.get(END_POINT).withAccessory(Waypoint.Accessory.END_REPEAT, false));
        return Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(4.0);
    }

    private static void example() {
        SelfTest.clientStep(EXAMPLE_NUMBER, EXAMPLE, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit Beispiel-Werfer (Mindestabstand " + ExampleDropper.MIN_TICKS + " Ticks), Pfeile in beiden Fächern, "
                        + "koppeln, Mission: Punkt 2 Aktion 1 alle 10 Ticks und 1 s Warten, Punkt 4 alle 2 Ticks, Punkt 5 Wiederholung aus",
                        (server, state) -> {
                            Arena.calmWind(server);
                            Pilot.spawn(server, state, WaypointTaskSteps.freestyle(true), "freestyle", Arena.ground(EXAMPLE_DX, EXAMPLE_DZ), 0.0F);
                            Entity drone = prepare(server, state);
                            AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                                    .orElseThrow(() -> new IllegalStateException("Werfer ohne Fach"));
                            inventory.set(0, new ItemStack(Items.ARROW, ARROWS));
                            inventory.set(1, new ItemStack(Items.ARROW, ARROWS));
                            ExampleDropper.reset();
                        })
                .append(fly())
                .then("Beispiel-Werfer: im 10er-Takt jeder Druck ein Wurf, im 2er-Takt nie schneller als " + ExampleDropper.MIN_TICKS + " Ticks",
                        WaypointEverySteps::exampleDrops));
    }

    private static void companion() {
        if (!Services.PLATFORM.isModLoaded("mobdefense")) {
            CamDrones.LOG.info("[Kamera-Selbsttest] {} nicht angemeldet: Mob Defense fehlt im mods-Ordner", COMPANION);
            return;
        }
        SelfTest.clientStep(COMPANION_NUMBER, COMPANION, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit Dispenser-Halterung von Mob Defense, 64 Pfeile, koppeln, Mission: Punkt 2 Aktion 1 alle 10 Ticks und "
                        + "1 s Warten, Punkt 4 alle 2 Ticks, Punkt 5 Wiederholung aus", (server, state) -> {
                    Arena.calmWind(server);
                    DronePreset preset = PropwashApi.builds().presets().stream().filter(p -> p.id().equals(WaypointTaskSteps.FREESTYLE)).findFirst()
                            .orElseThrow();
                    DroneData data = preset.data().withBuild(preset.data().build().withAccessory(AccessorySlot.FRONT, WaypointTaskSteps.MD_DISPENSER));
                    Pilot.spawn(server, state, data, "freestyle", Arena.ground(COMPANION_DX, COMPANION_DZ), 0.0F);
                    Entity drone = prepare(server, state);
                    PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).orElseThrow(() -> new IllegalStateException("Halterung ohne Fach"))
                            .set(0, new ItemStack(Items.ARROW, ARROWS));
                })
                .append(fly())
                .then("Werfer von Mob Defense: im 10er-Takt jeder Auslöser ein Pfeil, im 2er-Takt nie schneller als seine Abklingzeit",
                        WaypointEverySteps::companionShots));
    }

    private static Entity prepare(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        Mission mission = timed(drone);
        state.put(MISSION_KEY, mission);
        state.put(START_TICK, drone.level().getGameTime());
        CamFlights.mission(server, state, mission);
        WaypointTaskServer.clearReports();
        return drone;
    }

    private static Script fly() {
        return Script.create()
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission fliegt zu Ende, danach kein Auslöser mehr", WaypointEverySteps::finished)
                .then("Takt am Server: Punkt 2 alle 10 Ticks, Punkt 4 alle 2 Ticks, ab Punkt 5 nichts mehr", WaypointEverySteps::cadence);
    }

    private static List<WaypointTaskServer.Report> reports(SelfTest.StepContext ctx) {
        Object id = ctx.state().get(Pilot.DRONE);
        return WaypointTaskServer.reports().stream().filter(r -> r.drone().equals(id)).toList();
    }

    private static StepResult finished(SelfTest.StepContext ctx, int ticks) {
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        if (status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false)) {
            ctx.state().putIfAbsent(DONE_AT, ticks);
            ctx.state().putIfAbsent(DONE_REPORTS, reports(ctx).size());
            int since = ticks - (int) ctx.state().get(DONE_AT);
            if (since < AFTER_DONE_TICKS) {
                return StepResult.pending();
            }
            int before = (int) ctx.state().get(DONE_REPORTS);
            int after = reports(ctx).size();
            return after == before ? StepResult.pass("Mission fertig nach " + ticks / 20 + " s, " + after + " Auslöser, " + AFTER_DONE_TICKS
                    + " Ticks danach keiner mehr") : StepResult.fail("nach dem Ende " + (after - before) + " weitere Auslöser");
        }
        if (status.map(s -> s.state() == NavLogic.MissionState.ABORTED).orElse(false)) {
            return StepResult.fail("Mission abgebrochen: " + StatusProbe.describe(CamFlights.mark(ctx)));
        }
        return ticks > 20 * 150 ? StepResult.fail("Mission kommt nicht zu Ende, Zustand " + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("-"))
                : StepResult.pending();
    }

    private static StepResult cadence(SelfTest.StepContext ctx, int ticks) {
        List<WaypointTaskServer.Report> all = reports(ctx);
        List<String> problems = new ArrayList<>();
        List<WaypointTaskServer.Report> slow = new ArrayList<>();
        List<WaypointTaskServer.Report> fast = new ArrayList<>();
        for (WaypointTaskServer.Report r : all) {
            if (!r.task().equals("accessory.1") || r.outcome() != WaypointTaskServer.Outcome.DONE) {
                problems.add("Bericht " + (r.index() + 1) + ":" + r.task() + ":" + r.outcome() + ":" + r.skip());
            } else if (r.index() == SLOW_POINT) {
                slow.add(r);
            } else if (r.index() == FAST_POINT) {
                fast.add(r);
            } else {
                problems.add("Auslöser an Punkt " + (r.index() + 1));
            }
        }
        problems.addAll(range("Punkt 2", slow, SLOW_TICKS, 6));
        problems.addAll(range("Punkt 4", fast, FAST_TICKS, 5));
        if (!slow.isEmpty() && !fast.isEmpty() && slow.getLast().tick() >= fast.getFirst().tick()) {
            problems.add("Takt von Punkt 2 läuft nach Punkt 4 weiter");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join(", ", problems));
        }
        ctx.state().put(CADENCE, new long[]{slow.getLast().tick(), fast.getFirst().tick(), slow.size(), fast.size()});
        String summary = "Punkt 2: " + slow.size() + " Auslöser im Abstand " + SLOW_TICKS + " Ticks (" + gaps(ticksOf(slow)) + "), Punkt 4: " + fast.size()
                + " im Abstand " + FAST_TICKS + " Ticks, je der erste bei Ankunft, ab Punkt 5 keiner";
        ctx.state().put(SUMMARY, summary);
        return StepResult.pass(summary);
    }

    private static List<String> range(String label, List<WaypointTaskServer.Report> reports, int every, int min) {
        List<String> problems = new ArrayList<>();
        if (reports.size() < min) {
            problems.add(label + ": nur " + reports.size() + " Auslöser");
            return problems;
        }
        if (reports.getFirst().timed()) {
            problems.add(label + ": erster Auslöser nicht bei Ankunft");
        }
        for (int i = 1; i < reports.size(); i++) {
            if (!reports.get(i).timed()) {
                problems.add(label + ": Auslöser " + (i + 1) + " nicht aus dem Takt");
            }
            long gap = reports.get(i).tick() - reports.get(i - 1).tick();
            if (gap != every) {
                problems.add(label + ": Abstand " + gap + " statt " + every + " Ticks bei Auslöser " + (i + 1));
            }
        }
        return problems;
    }

    private static List<Long> ticksOf(List<WaypointTaskServer.Report> reports) {
        return reports.stream().map(WaypointTaskServer.Report::tick).toList();
    }

    private static String gaps(List<Long> ticks) {
        List<Long> out = new ArrayList<>();
        for (int i = 1; i < ticks.size(); i++) {
            out.add(ticks.get(i) - ticks.get(i - 1));
        }
        return out.toString();
    }

    private static StepResult exampleDrops(SelfTest.StepContext ctx, int ticks) {
        long[] cadence = (long[]) ctx.state().get(CADENCE);
        long lastSlow = cadence[0];
        long firstFast = cadence[1];
        List<ExampleDropper.Press> presses = ExampleDropper.presses().stream().filter(p -> p.pressed() && p.number() == ExampleDropper.DROP).toList();
        List<String> problems = new ArrayList<>();
        if (presses.size() != cadence[2] + cadence[3]) {
            problems.add(presses.size() + " Drücke statt " + (cadence[2] + cadence[3]) + " Auslöser");
        }
        List<Long> slowDrops = new ArrayList<>();
        List<Long> fastDrops = new ArrayList<>();
        List<Long> drops = new ArrayList<>();
        int refused = 0;
        for (ExampleDropper.Press press : presses) {
            boolean dropped = press.result() == DispenseResult.Status.FIRED;
            if (dropped) {
                drops.add(press.tick());
            }
            if (press.tick() <= lastSlow) {
                if (!dropped) {
                    problems.add("im 10er-Takt nicht geworfen: " + press);
                } else {
                    slowDrops.add(press.tick());
                }
            } else if (press.tick() >= firstFast) {
                if (dropped) {
                    fastDrops.add(press.tick());
                } else if (press.result() == DispenseResult.Status.REFUSED) {
                    refused++;
                } else {
                    problems.add("im 2er-Takt " + press.result());
                }
            }
        }
        for (int i = 1; i < drops.size(); i++) {
            long gap = drops.get(i) - drops.get(i - 1);
            if (gap < ExampleDropper.MIN_TICKS) {
                problems.add("Würfe nur " + gap + " Ticks auseinander");
            }
        }
        for (int i = 1; i < slowDrops.size(); i++) {
            if (slowDrops.get(i) - slowDrops.get(i - 1) != SLOW_TICKS) {
                problems.add("10er-Takt mit Abstand " + (slowDrops.get(i) - slowDrops.get(i - 1)));
            }
        }
        for (int i = 1; i < fastDrops.size(); i++) {
            if (fastDrops.get(i) - fastDrops.get(i - 1) != ExampleDropper.MIN_TICKS) {
                problems.add("2er-Takt wirft im Abstand " + (fastDrops.get(i) - fastDrops.get(i - 1)) + " statt " + ExampleDropper.MIN_TICKS);
            }
        }
        if (refused == 0 || fastDrops.size() < 2) {
            problems.add("2er-Takt: " + fastDrops.size() + " Würfe, " + refused + " abgelehnt");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join(", ", problems));
        }
        int thrown = drops.size();
        StepResult left = Script.server(ctx, ticks, (server, state) -> {
            Entity drone = Pilot.serverDrone(server, ctx.state());
            int count = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                    .map(inv -> inv.get(0).getCount() + inv.get(1).getCount()).orElse(-1);
            if (count != 2 * ARROWS - thrown) {
                throw new IllegalStateException(count + " Pfeile im Fach statt " + (2 * ARROWS - thrown));
            }
        });
        if (!left.done() || left.failed()) {
            return left;
        }
        return StepResult.pass("10er-Takt " + slowDrops.size() + " Würfe im Abstand " + gaps(slowDrops) + ", 2er-Takt " + fastDrops.size()
                + " Würfe im Abstand " + gaps(fastDrops) + " und " + refused + " abgelehnte Drücke (Mindestabstand " + ExampleDropper.MIN_TICKS
                + " Ticks), " + (2 * ARROWS - thrown) + " Pfeile übrig");
    }

    private static StepResult companionShots(SelfTest.StepContext ctx, int ticks) {
        long[] cadence = (long[]) ctx.state().get(CADENCE);
        int slowCount = (int) cadence[2];
        int fastCount = (int) cadence[3];
        StepResult checked = Script.server(ctx, ticks, (server, state) -> {
            Entity drone = Pilot.serverDrone(server, ctx.state());
            ServerLevel level = (ServerLevel) drone.level();
            long now = level.getGameTime();
            long start = (long) ctx.state().get(START_TICK);
            int cooldown = MD_DEFAULT_COOLDOWN;
            Optional<WorldRule<?>> rule = PropwashApi.worldRules().find(MD_COOLDOWN);
            if (rule.isPresent() && rule.get().get(level) instanceof Integer value) {
                cooldown = Math.max(1, value);
            }
            Vec3 origin = Vec3.atBottomCenterOf(Arena.ground(COMPANION_DX, COMPANION_DZ));
            AABB box = new AABB(origin, origin).inflate(64.0, 48.0, 96.0);
            List<Long> spawns = new ArrayList<>();
            for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, box)) {
                long spawned = now - arrow.tickCount;
                if (spawned >= start) {
                    spawns.add(spawned);
                }
            }
            spawns.sort(Long::compare);
            int left = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).map(inv -> inv.get(0).getCount()).orElse(-1);
            List<String> problems = new ArrayList<>();
            if (ARROWS - left != spawns.size()) {
                problems.add((ARROWS - left) + " Pfeile verbraucht, " + spawns.size() + " in der Welt");
            }
            if (spawns.size() < slowCount + 2) {
                problems.add("nur " + spawns.size() + " Pfeile bei " + slowCount + " + " + fastCount + " Auslösern");
            } else {
                for (int i = 1; i < slowCount; i++) {
                    if (spawns.get(i) - spawns.get(i - 1) != SLOW_TICKS) {
                        problems.add("10er-Takt: Pfeil " + (i + 1) + " nach " + (spawns.get(i) - spawns.get(i - 1)) + " Ticks");
                    }
                }
                for (int i = 1; i < spawns.size(); i++) {
                    if (spawns.get(i) - spawns.get(i - 1) < cooldown) {
                        problems.add("Pfeile nur " + (spawns.get(i) - spawns.get(i - 1)) + " Ticks auseinander, Abklingzeit " + cooldown);
                    }
                }
                int fastShots = spawns.size() - slowCount;
                if (fastShots >= fastCount) {
                    problems.add("2er-Takt: " + fastShots + " Pfeile bei " + fastCount + " Auslösern – schneller als die Abklingzeit");
                }
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException(String.join(", ", problems) + " (Pfeile bei " + spawns + ")");
            }
            List<Long> fastSpawns = spawns.subList(slowCount, spawns.size());
            ctx.state().put("every.shots", "10er-Takt " + slowCount + " Pfeile im Abstand " + gaps(spawns.subList(0, slowCount)) + ", 2er-Takt "
                    + fastSpawns.size() + " Pfeile bei " + fastCount + " Auslösern im Abstand " + gaps(fastSpawns) + " (Abklingzeit " + cooldown
                    + " Ticks), " + left + " Pfeile im Fach");
        });
        if (!checked.done() || checked.failed()) {
            return checked;
        }
        return StepResult.pass(String.valueOf(ctx.state().get("every.shots")));
    }

    private static void planner() {
        SelfTest.clientStep(PLANNER_NUMBER, PLANNER, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit Beispiel-Werfer setzen, Flugplaner mit Takt-Mission in die Hand", (server, state) -> {
                    Pilot.spawn(server, state, WaypointTaskSteps.freestyle(true), "freestyle", Arena.ground(PLANNER_DX, PLANNER_DZ), 0.0F);
                    CamFlights.pair(server, state);
                    tablet(server, state, timed(Pilot.serverDrone(server, state)));
                })
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .run("Editor öffnen", WaypointEverySteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .append(Shots.photoWindow())
                .then("Wegpunkt 2 wählen, Seite Aktionen", (ctx, ticks) -> select(SLOW_POINT, 1))
                .then("Wiederholen „alle 0.5 s“, Takt „10 Ticks“, Hinweis im Tooltip", WaypointEverySteps::editorShows)
                .append(Shots.photos("planner_waypoint_every", WaypointEverySteps::refresh))
                .append(tooltipPhotos("planner_waypoint_every_tooltip"))
                .then("Klick rechts auf Takt: +1 Tick, mit Umschalt +20, links zurück", WaypointEverySteps::clickInterval)
                .then("Wegpunkt 3: läuft im Takt von Punkt 2 weiter", WaypointEverySteps::followerShows)
                .then("Fenster mit GUI 320×240", (ctx, ticks) -> Shots.window(ctx, ticks, SMALL_WIDTH * SMALL_SCALE, SMALL_HEIGHT * SMALL_SCALE,
                        SMALL_SCALE))
                .then("Wegpunkt 2, Seite Aktionen: Takt ganz zu sehen", WaypointEverySteps::smallPage)
                .append(Shots.photos("planner_waypoint_every_320", WaypointEverySteps::refresh))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .append(Shots.photoWindow())
                .then("Marker in der Welt nennen den Takt", WaypointEverySteps::markerShows)
                .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
    }

    private static Optional<PlannerScreen> screen() {
        return Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen ? Optional.of(screen) : Optional.empty();
    }

    private static StepResult select(int index, int page) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        screen.state().select(EditorState.Selection.waypoint(index));
        screen.detailPage(Math.min(page, screen.layout().detailPages() - 1));
        screen.refreshSupport();
        return StepResult.pass("Wegpunkt " + (index + 1) + ", Seite " + (screen.detailPage() + 1) + " von " + screen.layout().detailPages());
    }

    private static String shown(FieldText text) {
        return I18n.get(text.key(), text.args().toArray());
    }

    private static StepResult editorShows(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        Mission mission = screen.state().mission();
        Waypoint w = mission.waypoint(SLOW_POINT);
        List<String> problems = new ArrayList<>();
        if (w.repeatMode() != Waypoint.Repeat.EVERY || w.everyTicks() != SLOW_TICKS) {
            problems.add("Wegpunkt 2 im Editor: " + w.repeatMode() + " " + w.everyTicks());
        }
        if (!WaypointField.EVERY.enabled(w) || !WaypointField.REPEAT.enabled(w)) {
            problems.add("Felder Wiederholen/Takt gesperrt");
        }
        String repeat = shown(WaypointField.REPEAT.value(w, '.', 0.0));
        String every = shown(WaypointField.EVERY.value(w, '.', 0.0));
        if (!repeat.equals(I18n.get(FieldText.PREFIX + "value.repeat.every", "0.5")) || !every.equals(I18n.get(FieldText.PREFIX + "value.every", "10"))) {
            problems.add("Werte „" + repeat + "“ / „" + every + "“");
        }
        EditorLayout layout = screen.layout();
        int row = WaypointField.EVERY.ordinal();
        if (layout.detailPage(row) != screen.detailPage()) {
            problems.add("Takt nicht auf der gezeigten Seite");
        }
        if (layout.detailRows().get(row).bottom() > layout.detail().bottom()) {
            problems.add("Zeile Takt unter dem Detailfeld");
        }
        String limit = I18n.get(FieldText.PREFIX + "support.every_limit");
        List<String> tooltip = screen.detailTooltip(row).stream().map(Component::getString).toList();
        if (tooltip.stream().noneMatch(limit::equals)) {
            problems.add("Tooltip Takt ohne Hinweis: " + tooltip);
        }
        List<String> repeatTip = screen.detailTooltip(WaypointField.REPEAT.ordinal()).stream().map(Component::getString).toList();
        if (repeatTip.stream().noneMatch(limit::equals)) {
            problems.add("Tooltip Wiederholen ohne Hinweis: " + repeatTip);
        }
        return problems.isEmpty() ? StepResult.pass("Wiederholen „" + repeat + "“, Takt „" + every + "“, Tooltip „" + limit + "“, Detail "
                + layout.detailPages() + " Seite(n) bei " + screen.width + "×" + screen.height) : StepResult.fail(String.join(", ", problems));
    }

    private static StepResult clickInterval(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        screen.state().select(EditorState.Selection.waypoint(SLOW_POINT));
        EditorLayout layout = screen.layout();
        screen.detailPage(layout.detailPage(WaypointField.EVERY.ordinal()));
        Rect box = EditorLayout.valueBox(layout.detailRows().get(WaypointField.EVERY.ordinal()), layout.detailLabelWidth());
        List<Integer> seen = new ArrayList<>();
        seen.add(screen.state().mission().waypoint(SLOW_POINT).everyTicks());
        click(screen, box.right() - 3, box.centerY(), 0);
        seen.add(screen.state().mission().waypoint(SLOW_POINT).everyTicks());
        click(screen, box.right() - 3, box.centerY(), 1);
        seen.add(screen.state().mission().waypoint(SLOW_POINT).everyTicks());
        click(screen, box.x() + 3, box.centerY(), 1);
        click(screen, box.x() + 3, box.centerY(), 0);
        seen.add(screen.state().mission().waypoint(SLOW_POINT).everyTicks());
        List<Integer> wanted = List.of(SLOW_TICKS, SLOW_TICKS + 1, SLOW_TICKS + 21, SLOW_TICKS);
        return seen.equals(wanted) ? StepResult.pass("Takt " + seen + " Ticks") : StepResult.fail("Takt " + seen + " statt " + wanted);
    }

    private static void click(PlannerScreen screen, int x, int y, int modifiers) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, modifiers));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static StepResult followerShows(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        screen.state().select(EditorState.Selection.waypoint(SLOW_POINT + 1));
        Mission mission = screen.state().mission();
        if (WaypointTasks.timedSource(mission, SLOW_POINT + 1) != SLOW_POINT) {
            return StepResult.fail("Punkt 3 nicht im Takt von Punkt 2");
        }
        String line = I18n.get(FieldText.PREFIX + "support.timed", SLOW_POINT + 1, SLOW_TICKS, "0.5");
        List<String> tooltip = screen.detailTooltip(WaypointField.ACCESSORY.ordinal()).stream().map(Component::getString).toList();
        if (!tooltip.contains(line)) {
            return StepResult.fail("Tooltip Zubehör an Punkt 3: " + tooltip);
        }
        return StepResult.pass("Punkt 3 „" + I18n.get(FieldText.PREFIX + "value.accessory.timed", 1) + "“, Tooltip „" + line + "“");
    }

    private static StepResult smallPage(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        EditorLayout layout = screen.layout();
        if (!layout.detailPaged()) {
            return ticks > 20 ? StepResult.fail("Detail bei " + screen.width + "×" + screen.height + " nicht in Seiten geteilt") : StepResult.pending();
        }
        screen.state().select(EditorState.Selection.waypoint(SLOW_POINT));
        screen.detailPage(1);
        screen.refreshSupport();
        Rect row = layout.detailRows().get(WaypointField.EVERY.ordinal());
        Rect toggle = layout.pageToggle();
        if (layout.detailPage(WaypointField.EVERY.ordinal()) != 1 || row.bottom() > toggle.y() || !toggle.inside(layout.detail())) {
            return StepResult.fail("Takt " + row + ", Umschalter " + toggle + ", Detail " + layout.detail());
        }
        Rect d = layout.detail();
        LayoutCheck.Box box = LayoutCheck.Box.of(d.x(), d.y(), d.w(), d.h());
        List<LayoutCheck.Text> inside = LayoutCheck.capture(Minecraft.getInstance(), screen).texts().stream().filter(t -> box.contains(t.full(), 2))
                .toList();
        List<String> problems = new ArrayList<>(LayoutCheck.audit(box, new LayoutCheck.Frame(inside, List.of(), List.of())));
        String separator = I18n.get(FieldText.PREFIX + "decimal");
        Waypoint w = screen.state().mission().waypoint(SLOW_POINT);
        String value = shown(WaypointField.EVERY.value(w, separator.length() == 1 ? separator.charAt(0) : '.', 0.0));
        String label = I18n.get(WaypointField.EVERY.labelKey());
        if (inside.stream().noneMatch(t -> t.value().equals(value)) || inside.stream().noneMatch(t -> t.value().equals(label))) {
            problems.add("Takt „" + label + "“ / „" + value + "“ nicht ganz im Detailfeld gezeichnet");
        }
        return problems.isEmpty() ? StepResult.pass("Seite Aktionen mit 5 Zeilen bei " + screen.width + "×" + screen.height + ", „" + label + " "
                + value + "“ ganz zu sehen, " + inside.size() + " Texte im Detailfeld ohne Befund") : StepResult.fail(String.join(", ", problems));
    }

    private static StepResult markerShows(SelfTest.StepContext ctx, int ticks) {
        Mission mission = held().map(stack -> PlannerTablets.read(stack).activeMission()).orElse(Mission.empty());
        if (mission.isEmpty()) {
            return StepResult.fail("kein Tablet in der Hand");
        }
        String label = WaypointPreview.taskLabel(WaypointTasks.due(mission, SLOW_POINT));
        String wanted = I18n.get(FieldText.PREFIX + "support.marker.every", 1, SLOW_TICKS, "0.5");
        return label.equals(wanted) ? StepResult.pass("Marker an Punkt 2: „" + label + "“") : StepResult.fail("Marker „" + label + "“ statt „" + wanted + "“");
    }

    private static Script tooltipPhotos(String name) {
        if (!Shots.enabled()) {
            return Script.create();
        }
        return Script.create()
                .then("Bildschirmfoto " + name + " (Maus auf Takt)", (ctx, ticks) -> hoverShot(ctx, ticks, name))
                .run("Sprache Deutsch", () -> {
                    Shots.language(Minecraft.getInstance(), Shots.GERMAN);
                    refresh();
                })
                .then("Bildschirmfoto " + name + "_de (Maus auf Takt)", (ctx, ticks) -> hoverShot(ctx, ticks, name + "_de"))
                .run("Sprache zurück, Maus parken", () -> {
                    Shots.restoreLanguage(Minecraft.getInstance());
                    refresh();
                    Shots.parkMouse(Minecraft.getInstance());
                });
    }

    private static StepResult hoverShot(SelfTest.StepContext ctx, int ticks, String name) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        Minecraft mc = Minecraft.getInstance();
        EditorLayout layout = open.get().layout();
        Rect box = EditorLayout.valueBox(layout.detailRows().get(WaypointField.EVERY.ordinal()), layout.detailLabelWidth());
        double sx = mc.getWindow().getScreenWidth() / (double) mc.getWindow().getGuiScaledWidth();
        double sy = mc.getWindow().getScreenHeight() / (double) mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), box.centerX() * sx, box.centerY() * sy, 0.0, 0.0);
        if (ticks == 0) {
            Shots.clearOverlays(mc);
        }
        if (ticks < HOVER_TICKS) {
            return StepResult.pending();
        }
        Object pending = ctx.state().get(HOVER_SHOT);
        if (pending == null) {
            ctx.state().put(HOVER_SHOT, Shots.capture(name));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) pending;
        if (!future.isDone()) {
            return ticks > 400 ? StepResult.fail("Bildschirmfoto " + name + " hängt") : StepResult.pending();
        }
        ctx.state().remove(HOVER_SHOT);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + name + ": " + future.exceptionNow());
        }
        ctx.log("Bildschirmfoto " + Shots.dir().resolve(name + ".png") + " mit Tooltip von Takt");
        return StepResult.pass();
    }

    private static void refresh() {
        screen().ifPresent(PlannerScreen::refreshSupport);
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
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of("Takt", mission)), 0, TabletState.DEFAULT_GROUND_HEIGHT));
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
