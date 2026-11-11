package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.MissionGripper;
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
import de.lardtom.camdrones.planner.PlannerSupport;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.WaypointField;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class GripperMissionSteps {
    public static final int MISSION_NUMBER = 710;
    public static final String MISSION = "mission: Freestyle-5″ mit Greifer fliegt ohne Pilot tief über 4 Bruchstein an Punkt 2, nimmt sie auf "
            + "(Flugmodell schwerer) und lässt sie mit „Greifer auslösen“ an Punkt 3 fallen";
    public static final int EMPTY_NUMBER = 711;
    public static final String EMPTY = "mission: Freestyle-5″ mit Greifer fliegt dieselbe Mission ohne Item normal – Greifer bleibt leer, "
            + "„Greifer auslösen“ ohne Fracht";
    public static final int PLANNER_NUMBER = 712;
    public static final String PLANNER = "planner: „Greifer auslösen“ an einer Freestyle ohne Greifer – Hinweis „kein Greifer an der Drohne“ "
            + "im Wegpunkt-Detail";
    public static final int CLOSE_NUMBER = 713;
    public static final String CLOSE = "mission: Freestyle-5″ mit Greifer – Pause und Missionsende schließen den offenen Griff: frisches Bruchstein direkt "
            + "unter der tief schwebenden Drohne bleibt 5 s liegen, nach dem Fortsetzen nimmt sie es sofort auf";
    public static final int COBBLE = 4;
    public static final int PICK_POINT = 1;
    public static final int DROP_POINT = 2;
    public static final double LOW = 0.5;
    public static final double HIGH = 3.0;
    public static final double PICK_DZ = 10.0;
    public static final double DROP_DZ = 18.0;
    public static final double MASS_TOLERANCE = 0.005;
    public static final int PAUSE_POINT = 1;
    public static final double LAST_DZ = 23.0;
    public static final double PAUSE_WAIT = 4.0;
    public static final double REACH_HEIGHT = 1.0;
    public static final double REACH_SIDE = 0.6;
    public static final int SPAWN_DELAY_TICKS = 20;
    public static final int WATCH_TICKS = MissionGripper.GRAB_WINDOW_TICKS + 10;
    private static final int MISSION_DX = 60;
    private static final int MISSION_DZ = 110;
    private static final int EMPTY_DX = 20;
    private static final int EMPTY_DZ = 110;
    private static final int PLANNER_DX = -20;
    private static final int PLANNER_DZ = 110;
    private static final int CLOSE_DX = -60;
    private static final int CLOSE_DZ = 110;
    private static final int STEP_TICKS = 20 * 240;
    private static final int FLIGHT_TICKS = 20 * 90;
    private static final int SETTLE_TICKS = 40;
    private static final double AREA = 30.0;
    private static final String PICK = "gripper.pick";
    private static final String DROP = "gripper.drop";
    private static final String EMPTY_MASS = "gripper.empty";
    private static final String DONE_AT = "gripper.done";
    private static final String PAUSE_REPLY = "gripper.pause";
    private static final String WATCH = "gripper.watch.";
    private static final AtomicReference<Probe> PROBE = new AtomicReference<>();

    private record Probe(int cargo, double cargoGrams, double massKg, boolean flying, boolean armed, double x, double y, double z, int nearPick,
                         int nearDrop, int groundedDrop) {
        String describe() {
            return String.format(Locale.ROOT, "Fracht %d (%.0f g), Flugmodell %.3f kg, %s, %s, Drohne %.1f/%.1f/%.1f, Bruchstein an Punkt 2: %d, an Punkt 3: %d "
                    + "(am Boden %d)", this.cargo, this.cargoGrams, this.massKg, this.flying ? "Flug ohne Pilot" : "kein Flug",
                    this.armed ? "scharf" : "nicht scharf", this.x, this.y, this.z, this.nearPick, this.nearDrop, this.groundedDrop);
        }
    }

    private GripperMissionSteps() {
    }

    public static void register() {
        mission();
        empty();
        planner();
        closing();
    }

    static DroneData freestyleWithGripper() {
        DronePreset preset = PropwashApi.builds().presets().stream().filter(p -> p.id().equals(WaypointTaskSteps.FREESTYLE)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Preset " + WaypointTaskSteps.FREESTYLE + " fehlt"));
        return preset.data().withBuild(preset.data().build().withAccessory(AccessorySlot.BOTTOM, PlannerSupport.GRIPPER));
    }

    static Mission mission(Entity drone) {
        double x = drone.getX();
        double y = drone.getY();
        double z = drone.getZ();
        List<Waypoint> waypoints = new ArrayList<>();
        waypoints.add(Waypoint.at(x, y + HIGH, z + 4.0));
        waypoints.add(Waypoint.at(x, y + LOW, z + PICK_DZ).withWait(2.0));
        waypoints.add(Waypoint.at(x, y + HIGH, z + DROP_DZ).withWait(2.0).withAccessory(Waypoint.Accessory.GRIPPER_RELEASE, false));
        return Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(3.0);
    }

    static Mission closingMission(Entity drone) {
        double x = drone.getX();
        double y = drone.getY();
        double z = drone.getZ();
        List<Waypoint> waypoints = new ArrayList<>();
        waypoints.add(Waypoint.at(x, y + HIGH, z + 4.0));
        waypoints.add(Waypoint.at(x, y + LOW, z + PICK_DZ).withWait(PAUSE_WAIT));
        waypoints.add(Waypoint.at(x, y + LOW, z + DROP_DZ).withWait(2.0).withAccessory(Waypoint.Accessory.GRIPPER_RELEASE, false));
        waypoints.add(Waypoint.at(x, y + LOW, z + LAST_DZ));
        return Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(3.0);
    }

    private static Script setup(int dx, int dz, boolean cobble) {
        return prepare(dx, dz, "Freestyle-5″ mit Propwashs Greifer unten setzen, koppeln, Mission: Punkt 2 in 0,5 m Höhe, Punkt 3 in 3 m mit "
                + "„Greifer auslösen“" + (cobble ? ", 4 Bruchstein unter Punkt 2" : ", kein Item"), cobble, GripperMissionSteps::mission)
                .then("Planer erkennt den Greifer", GripperMissionSteps::recognised)
                .append(start());
    }

    private static Script start() {
        return Script.create()
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"));
    }

    private static Script prepare(int dx, int dz, String label, boolean cobble, Function<Entity, Mission> plan) {
        return Script.create()
                .server(label, (server, state) -> {
                    PROBE.set(null);
                    Arena.calmWind(server);
                    Pilot.spawn(server, state, freestyleWithGripper(), "freestyle", Arena.ground(dx, dz), 0.0F);
                    ServerPlayer player = Arena.player(server);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    CamFlights.pair(server, state);
                    Entity drone = Pilot.serverDrone(server, state);
                    ServerLevel level = Arena.level(server);
                    clearItems(level, drone.position());
                    Vec3 pick = new Vec3(drone.getX(), drone.getY(), drone.getZ() + PICK_DZ);
                    Vec3 drop = new Vec3(drone.getX(), drone.getY(), drone.getZ() + DROP_DZ);
                    if (cobble) {
                        ItemEntity item = new ItemEntity(level, pick.x, pick.y, pick.z, new ItemStack(Items.COBBLESTONE, COBBLE), 0.0, 0.0, 0.0);
                        item.setNoPickUpDelay();
                        level.addFreshEntity(item);
                    }
                    double empty = PropwashApi.drones().view(drone).map(DroneView::massKg).orElseThrow(() -> new IllegalStateException("keine Drohne"));
                    if (!PropwashApi.accessories().cargo(drone).isEmpty()) {
                        throw new IllegalStateException("Greifer hat schon Fracht: " + PropwashApi.accessories().cargo(drone));
                    }
                    state.put(PICK, pick);
                    state.put(DROP, drop);
                    state.put(EMPTY_MASS, empty);
                    CamFlights.mission(server, state, plan.apply(drone));
                    WaypointTaskServer.clearReports();
                })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending());
    }

    private static void mission() {
        SelfTest.clientStep(MISSION_NUMBER, MISSION, STEP_TICKS, setup(MISSION_DX, MISSION_DZ, true)
                        .then("Punkt 2 tief über dem Bruchstein: aufgenommen, Flugmodell im Flug schwerer", GripperMissionSteps::picked)
                        .then("Punkt 3 „Greifer auslösen“: Bruchstein liegt dort am Boden, Flugmodell wieder leicht", GripperMissionSteps::dropped)
                        .then("Mission fliegt zu Ende", GripperMissionSteps::finished)
                        .then("Bericht: Greifer an Punkt 3 ausgelöst", GripperMissionSteps::reported)
                        .then("nach der Mission Greifer leer, abgelegtes Bruchstein bleibt an Punkt 3 liegen", (ctx, ticks) -> stays(ctx, ticks, COBBLE)),
                GripperMissionSteps::cleanup);
    }

    private static void empty() {
        SelfTest.clientStep(EMPTY_NUMBER, EMPTY, STEP_TICKS, setup(EMPTY_DX, EMPTY_DZ, false)
                        .then("Mission fliegt zu Ende", GripperMissionSteps::finished)
                        .then("Bericht: Greifer an Punkt 3 ausgelöst", GripperMissionSteps::reported)
                        .then("Drohne schwebt an Punkt 3, Greifer leer, Flugmodell unverändert", GripperMissionSteps::flewNormally)
                        .then("kein Item in der Nähe", (ctx, ticks) -> stays(ctx, ticks, 0)),
                GripperMissionSteps::cleanup);
    }

    private static void closing() {
        String label = "Freestyle-5″ mit Propwashs Greifer unten setzen, koppeln, Mission: Punkt 2 in 0,5 m Höhe mit 4 s Warten, Punkt 3 in 0,5 m "
                + "mit „Greifer auslösen“, Punkt 4 in 0,5 m, Ende schweben, kein Item";
        SelfTest.clientStep(CLOSE_NUMBER, CLOSE, STEP_TICKS, prepare(CLOSE_DX, CLOSE_DZ, label, false, GripperMissionSteps::closingMission)
                        .append(start())
                        .then("Punkt 2 tief über dem Boden: Mission pausieren", GripperMissionSteps::pauseLow)
                        .then("Pause: frisches Bruchstein direkt unter der Drohne bleibt 5 s liegen, Greifer leer",
                                (ctx, ticks) -> closed(ctx, ticks, NavLogic.MissionState.PAUSED))
                        .server("Mission fortsetzen", (server, state) -> {
                            AutonomyServer.Reply reply = AutonomyServer.execute(Arena.player(server), Pilot.serverDrone(server, state),
                                    MissionCommand.Command.RESUME, MissionCommand.AUTO_INDEX);
                            if (!reply.ok()) {
                                throw new IllegalStateException("Fortsetzen abgelehnt: " + reply.status());
                            }
                        })
                        .then("nach dem Fortsetzen nimmt der Greifer das Bruchstein sofort auf", GripperMissionSteps::pickedAgain)
                        .then("Punkt 3 „Greifer auslösen“: Bruchstein liegt dort am Boden, Flugmodell wieder leicht", GripperMissionSteps::dropped)
                        .then("Missionsende tief über Punkt 4: frisches Bruchstein direkt unter der Drohne bleibt 5 s liegen, Greifer leer",
                                (ctx, ticks) -> closed(ctx, ticks, NavLogic.MissionState.DONE)),
                GripperMissionSteps::cleanup);
    }

    private static final class CloseWatch {
        private final NavLogic.MissionState expected;
        private long since = Long.MIN_VALUE;
        private long spawnedAt = Long.MIN_VALUE;
        private @Nullable Vec3 item;
        private double spawnHeight;
        private double maxOff;
        private double maxHeight;
        private @Nullable StepResult result;

        CloseWatch(NavLogic.MissionState expected) {
            this.expected = expected;
        }

        synchronized @Nullable StepResult result() {
            return this.result;
        }

        synchronized void tick(MinecraftServer server, UUID id, double groundY) {
            if (this.result != null) {
                return;
            }
            ServerLevel level = Arena.level(server);
            Entity drone = level.getEntity(id);
            if (drone == null) {
                this.result = StepResult.fail("Drohne fehlt am Server");
                return;
            }
            long now = level.getGameTime();
            MissionStatus status = AutonomyServer.missionStatus(drone);
            int cargo = PropwashApi.accessories().cargo(drone).stream().mapToInt(ItemStack::getCount).sum();
            double height = drone.getY() - groundY;
            if (status.state() != this.expected) {
                if (this.since != Long.MIN_VALUE) {
                    this.result = StepResult.fail("Zustand " + status.state() + " statt " + this.expected);
                } else if (status.state() == NavLogic.MissionState.ABORTED) {
                    this.result = StepResult.fail("Mission abgebrochen");
                }
                return;
            }
            if (this.since == Long.MIN_VALUE) {
                this.since = now;
            }
            if (this.item == null) {
                if (now - this.since < SPAWN_DELAY_TICKS) {
                    return;
                }
                if (cargo != 0 || height > REACH_HEIGHT) {
                    this.result = StepResult.fail(String.format(Locale.ROOT, "Prüfung wirkungslos: Fracht %d, Drohne %.2f m über dem Boden (Greifer reicht "
                            + "%.1f m)", cargo, height, REACH_HEIGHT));
                    return;
                }
                Vec3 at = new Vec3(drone.getX(), groundY, drone.getZ());
                ItemEntity placed = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, COBBLE), 0.0, 0.0, 0.0);
                placed.setNoPickUpDelay();
                level.addFreshEntity(placed);
                this.item = at;
                this.spawnedAt = now;
                this.spawnHeight = height;
                return;
            }
            long watched = now - this.spawnedAt;
            if (cargo > 0) {
                this.result = StepResult.fail(String.format(Locale.ROOT, "%d Bruchstein %d Ticks nach dem Hinlegen aufgenommen (%d Ticks nach %s): der offene "
                        + "Griff aus der Mission ist nicht geschlossen", cargo, watched, now - this.since, this.expected));
                return;
            }
            double off = Math.hypot(drone.getX() - this.item.x, drone.getZ() - this.item.z);
            this.maxOff = Math.max(this.maxOff, off);
            this.maxHeight = Math.max(this.maxHeight, height);
            if (off > REACH_SIDE || height > REACH_HEIGHT) {
                this.result = StepResult.fail(String.format(Locale.ROOT, "Prüfung wirkungslos: Drohne %.2f m neben und %.2f m über dem Bruchstein", off,
                        height));
                return;
            }
            int lying = cobble(level, this.item, false);
            if (lying != COBBLE) {
                this.result = StepResult.fail("unter der Drohne liegen " + lying + " statt " + COBBLE + " Bruchstein");
                return;
            }
            if (watched >= WATCH_TICKS) {
                this.result = StepResult.pass(String.format(Locale.ROOT, "%s: 4 Bruchstein %d Ticks nach %s hingelegt, %.2f m unter der Drohne, liegen %d Ticks "
                                + "(Drohne höchstens %.2f m daneben und %.2f m darüber), Greifer leer", this.expected, SPAWN_DELAY_TICKS, this.expected,
                        this.spawnHeight, watched, this.maxOff, this.maxHeight));
            }
        }
    }

    private static StepResult pauseLow(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        @SuppressWarnings("unchecked")
        AtomicReference<String> reply = (AtomicReference<String>) state.computeIfAbsent(PAUSE_REPLY, key -> new AtomicReference<String>());
        String seen = reply.get();
        if (seen != null) {
            return seen.startsWith("!") ? StepResult.fail(seen.substring(1)) : StepResult.pass(seen);
        }
        Optional<StepResult> aborted = aborted(ctx);
        if (aborted.isPresent()) {
            return aborted.get();
        }
        UUID id = (UUID) state.get(Pilot.DRONE);
        Vec3 pick = (Vec3) state.get(PICK);
        if (ticks % 2 == 0 && id != null && pick != null) {
            ctx.onServer(server -> {
                if (reply.get() != null) {
                    return;
                }
                Entity drone = Arena.level(server).getEntity(id);
                if (drone == null) {
                    return;
                }
                MissionStatus status = AutonomyServer.missionStatus(drone);
                double height = drone.getY() - pick.y;
                double off = Math.hypot(drone.getX() - pick.x, drone.getZ() - pick.z);
                if (status.state() != NavLogic.MissionState.RUNNING || status.index() != PAUSE_POINT || height > REACH_HEIGHT - 0.2 || off > 0.5) {
                    return;
                }
                AutonomyServer.Reply answer = AutonomyServer.execute(Arena.player(server), drone, MissionCommand.Command.PAUSE, MissionCommand.AUTO_INDEX);
                reply.set(answer.ok() ? String.format(Locale.ROOT, "pausiert %.2f m über dem Boden, %.2f m neben Punkt 2: %s", height, off, answer.status())
                        : "!Pause abgelehnt: " + answer.status());
            });
        }
        return ticks > FLIGHT_TICKS ? StepResult.fail("Drohne kommt nicht tief über Punkt 2") : StepResult.pending();
    }

    private static StepResult closed(SelfTest.StepContext ctx, int ticks, NavLogic.MissionState expected) {
        Map<String, Object> state = ctx.state();
        CloseWatch watch = (CloseWatch) state.computeIfAbsent(WATCH + expected, key -> new CloseWatch(expected));
        StepResult result = watch.result();
        if (result != null) {
            return result;
        }
        UUID id = (UUID) state.get(Pilot.DRONE);
        Vec3 pick = (Vec3) state.get(PICK);
        if (ticks % 2 == 0 && id != null && pick != null) {
            ctx.onServer(server -> watch.tick(server, id, pick.y));
        }
        return ticks > FLIGHT_TICKS ? StepResult.fail("kein Ergebnis im Zustand " + expected) : StepResult.pending();
    }

    private static StepResult pickedAgain(SelfTest.StepContext ctx, int ticks) {
        StepResult result = picked(ctx, ticks);
        if (!result.done() && ticks > 60) {
            Probe p = PROBE.get();
            return StepResult.fail("nach dem Fortsetzen nicht sofort aufgenommen: " + (p == null ? "keine Messung" : p.describe()));
        }
        return result;
    }

    private static StepResult recognised(SelfTest.StepContext ctx, int ticks) {
        DroneSupport support = PlannerSupport.of(CamFlights.clientDrone(ctx.state()));
        if (!support.known()) {
            return ticks > 60 ? StepResult.fail("Bau der Drohne am Client unbekannt") : StepResult.pending();
        }
        Mission mission = mission(CamFlights.clientDrone(ctx.state()).orElseThrow());
        if (!support.gripper() || support.release() != DroneSupport.Skip.NONE || support.skip(mission, DROP_POINT) != DroneSupport.Skip.NONE) {
            return StepResult.fail("Greifer " + support.gripper() + ", „Greifer auslösen“ " + support.release() + "/" + support.skip(mission, DROP_POINT));
        }
        return StepResult.pass("Greifer erkannt, „" + I18n.get(FieldText.PREFIX + "value.accessory.gripper_release") + "“ an Punkt 3 wird ausgeführt, "
                + "Marker „" + I18n.get(FieldText.PREFIX + "support.marker.release") + "“");
    }

    private static void poll(SelfTest.StepContext ctx, int ticks) {
        if (ticks % 4 != 0) {
            return;
        }
        Map<String, Object> state = ctx.state();
        UUID id = (UUID) state.get(Pilot.DRONE);
        Vec3 pick = (Vec3) state.get(PICK);
        Vec3 drop = (Vec3) state.get(DROP);
        if (id == null || pick == null || drop == null) {
            return;
        }
        ctx.onServer(server -> PROBE.set(probe(server, id, pick, drop)));
    }

    private static Probe probe(MinecraftServer server, UUID id, Vec3 pick, Vec3 drop) {
        ServerLevel level = Arena.level(server);
        Entity drone = level.getEntity(id);
        if (drone == null) {
            return new Probe(-1, 0.0, Double.NaN, false, false, Double.NaN, Double.NaN, Double.NaN, cobble(level, pick, false), cobble(level, drop, false),
                    cobble(level, drop, true));
        }
        List<ItemStack> cargo = PropwashApi.accessories().cargo(drone);
        int count = cargo.stream().mapToInt(ItemStack::getCount).sum();
        double grams = cargo.stream().mapToDouble(stack -> PropwashApi.accessories().itemGrams(stack)).sum();
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        return new Probe(count, grams, view.map(DroneView::massKg).orElse(Double.NaN), PropwashApi.drones().autonomy().active(drone).isPresent(),
                view.map(DroneView::armed).orElse(false), drone.getX(), drone.getY(), drone.getZ(), cobble(level, pick, false), cobble(level, drop, false),
                cobble(level, drop, true));
    }

    private static int cobble(ServerLevel level, Vec3 at, boolean grounded) {
        AABB box = new AABB(at.x - 1.5, at.y - 0.5, at.z - 1.5, at.x + 1.5, at.y + 6.0, at.z + 1.5);
        int count = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, i -> i.isAlive() && i.getItem().is(Items.COBBLESTONE))) {
            if (!grounded || item.onGround() && Math.abs(item.getY() - at.y) < 0.3) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }

    private static Optional<StepResult> aborted(SelfTest.StepContext ctx) {
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        if (status.map(s -> s.state() == NavLogic.MissionState.ABORTED).orElse(false)) {
            return Optional.of(StepResult.fail("Mission abgebrochen: " + StatusProbe.describe(CamFlights.mark(ctx))));
        }
        return Optional.empty();
    }

    private static double emptyMass(SelfTest.StepContext ctx) {
        return (double) ctx.state().get(EMPTY_MASS);
    }

    private static StepResult picked(SelfTest.StepContext ctx, int ticks) {
        poll(ctx, ticks);
        Optional<StepResult> aborted = aborted(ctx);
        if (aborted.isPresent()) {
            return aborted.get();
        }
        Probe p = PROBE.get();
        if (p != null && p.cargo() > 0) {
            if (p.cargo() != COBBLE || p.nearPick() != 0) {
                return StepResult.fail("nur teilweise aufgenommen: " + p.describe());
            }
            if (!p.flying() || !p.armed()) {
                return StepResult.fail("aufgenommen, aber nicht im Flug ohne Pilot: " + p.describe());
            }
            double empty = emptyMass(ctx);
            double gained = p.massKg() - empty;
            if (Math.abs(gained - p.cargoGrams() / 1000.0) > MASS_TOLERANCE || gained <= 0.0) {
                return StepResult.fail(String.format(Locale.ROOT, "Flugmodell %.3f kg statt %.3f + %.3f kg: %s", p.massKg(), empty, p.cargoGrams() / 1000.0,
                        p.describe()));
            }
            Vec3 pick = (Vec3) ctx.state().get(PICK);
            return StepResult.pass(String.format(Locale.ROOT, "%d Bruchstein in %.2f m Höhe über dem Boden aufgenommen, Flugmodell im Flug %.3f → %.3f kg",
                    p.cargo(), p.y() - pick.y, empty, p.massKg()));
        }
        return ticks > FLIGHT_TICKS ? StepResult.fail("Greifer nimmt nichts auf: " + (p == null ? "keine Messung" : p.describe())) : StepResult.pending();
    }

    private static StepResult dropped(SelfTest.StepContext ctx, int ticks) {
        poll(ctx, ticks);
        Optional<StepResult> aborted = aborted(ctx);
        if (aborted.isPresent()) {
            return aborted.get();
        }
        Probe p = PROBE.get();
        if (p != null && p.cargo() == 0 && p.groundedDrop() == COBBLE) {
            double empty = emptyMass(ctx);
            if (Math.abs(p.massKg() - empty) > MASS_TOLERANCE) {
                return StepResult.fail(String.format(Locale.ROOT, "Flugmodell nach dem Auslösen %.3f statt %.3f kg", p.massKg(), empty));
            }
            return StepResult.pass(String.format(Locale.ROOT, "%d Bruchstein am Boden unter Punkt 3, Flugmodell %.3f kg", p.groundedDrop(), p.massKg()));
        }
        return ticks > FLIGHT_TICKS ? StepResult.fail("Fracht nicht an Punkt 3 abgelegt: " + (p == null ? "keine Messung" : p.describe()))
                : StepResult.pending();
    }

    private static StepResult finished(SelfTest.StepContext ctx, int ticks) {
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        if (status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false)) {
            ctx.state().putIfAbsent(DONE_AT, ticks);
            int since = ticks - (int) ctx.state().get(DONE_AT);
            return since > 30 ? StepResult.pass("Mission fertig nach " + ticks / 20 + " s") : StepResult.pending();
        }
        Optional<StepResult> aborted = aborted(ctx);
        if (aborted.isPresent()) {
            return aborted.get();
        }
        return ticks > FLIGHT_TICKS ? StepResult.fail("Mission kommt nicht zu Ende, Zustand "
                + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("-")) : StepResult.pending();
    }

    private static StepResult reported(SelfTest.StepContext ctx, int ticks) {
        UUID id = (UUID) ctx.state().get(Pilot.DRONE);
        List<String> seen = WaypointTaskServer.reports().stream().filter(r -> r.drone().equals(id))
                .map(r -> (r.index() + 1) + ":" + r.task() + ":" + r.outcome()).toList();
        List<String> wanted = List.of("3:" + WaypointTaskServer.RELEASE_TASK + ":DONE");
        if (!seen.equals(wanted)) {
            return ticks > 60 ? StepResult.fail("Berichte " + seen + " statt " + wanted) : StepResult.pending();
        }
        return StepResult.pass("Berichte " + seen + ", Meldung „" + I18n.get(WaypointTaskServer.PREFIX + "done", "Drohne", "3",
                I18n.get(WaypointTaskServer.PREFIX + WaypointTaskServer.RELEASE_TASK)) + "“");
    }

    private static StepResult stays(SelfTest.StepContext ctx, int ticks, int expected) {
        poll(ctx, ticks);
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        Probe p = PROBE.get();
        if (p == null) {
            return ticks > SETTLE_TICKS + 40 ? StepResult.fail("keine Messung") : StepResult.pending();
        }
        if (p.cargo() != 0 || p.groundedDrop() != expected || p.nearPick() != 0) {
            return StepResult.fail("nach der Mission: " + p.describe());
        }
        return StepResult.pass("Greifer leer, " + expected + " Bruchstein am Boden unter Punkt 3");
    }

    private static StepResult flewNormally(SelfTest.StepContext ctx, int ticks) {
        poll(ctx, ticks);
        if (ticks < 8) {
            return StepResult.pending();
        }
        Probe p = PROBE.get();
        if (p == null) {
            return ticks > 60 ? StepResult.fail("keine Messung") : StepResult.pending();
        }
        Vec3 drop = (Vec3) ctx.state().get(DROP);
        double off = Math.hypot(p.x() - drop.x, p.z() - drop.z);
        double height = p.y() - drop.y;
        double empty = emptyMass(ctx);
        if (p.cargo() != 0 || Math.abs(p.massKg() - empty) > MASS_TOLERANCE || off > 1.5 || Math.abs(height - HIGH) > 1.0) {
            return StepResult.fail(String.format(Locale.ROOT, "%.1f m neben Punkt 3, %.1f m hoch: %s", off, height, p.describe()));
        }
        return StepResult.pass(String.format(Locale.ROOT, "schwebt %.1f m neben Punkt 3 in %.1f m Höhe, Greifer leer, Flugmodell %.3f kg", off, height,
                p.massKg()));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        PROBE.set(null);
        return Script.server(ctx, 0, (server, state) -> {
            ServerLevel level = Arena.level(server);
            for (int[] at : new int[][]{{MISSION_DX, MISSION_DZ}, {EMPTY_DX, EMPTY_DZ}, {CLOSE_DX, CLOSE_DZ}}) {
                clearItems(level, Vec3.atCenterOf(Arena.ground(at[0], at[1])));
            }
        });
    }

    private static void clearItems(ServerLevel level, Vec3 center) {
        AABB box = new AABB(center.x - AREA, center.y - 4.0, center.z - AREA, center.x + AREA, center.y + 12.0, center.z + AREA);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            item.discard();
        }
    }

    private static void planner() {
        SelfTest.clientStep(PLANNER_NUMBER, PLANNER, STEP_TICKS, Script.create()
                .server("Freestyle-5″ mit LED-Streifen und ohne Greifer setzen, koppeln, Flugplaner mit „Greifer auslösen“ an Punkt 3 in die Hand",
                        (server, state) -> {
                            Pilot.spawn(server, state, WaypointTaskSteps.freestyle(false), "freestyle", Arena.ground(PLANNER_DX, PLANNER_DZ), 0.0F);
                            CamFlights.pair(server, state);
                            tablet(server, state, mission(Pilot.serverDrone(server, state)));
                        })
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .then("Seriennummer der Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state())
                        .filter(drone -> CamFlights.serial(ctx.state()).equals(PlannerTablets.serialOf(drone))).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Seriennummer fehlt am Client") : StepResult.pending())
                .run("Editor öffnen", GripperMissionSteps::openEditor)
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .append(Shots.photoWindow())
                .then("Wegpunkt 3 wählen, Seite Aktionen", (ctx, ticks) -> select(DROP_POINT, 1))
                .then("Feld Zubehör „Greifer auslösen“ mit „!“ und Hinweis im Wegpunkt-Detail", GripperMissionSteps::hintShown)
                .append(Shots.photos("planner_gripper_release", GripperMissionSteps::refresh))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
    }

    private static StepResult hintShown(SelfTest.StepContext ctx, int ticks) {
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
        if (mission.waypoint(DROP_POINT).accessory() != Waypoint.Accessory.GRIPPER_RELEASE) {
            problems.add("Punkt 3 im Editor: " + mission.waypoint(DROP_POINT).accessory());
        }
        if (support.gripper()) {
            problems.add("Greifer erkannt, obwohl keiner dran ist");
        }
        if (support.skip(mission, DROP_POINT) != DroneSupport.Skip.NO_GRIPPER) {
            problems.add("Markierung " + support.skip(mission, DROP_POINT));
        }
        if (!support.skip(WaypointTasks.due(mission, DROP_POINT)).skipped()) {
            problems.add("Liste ohne „!“ an Punkt 3");
        }
        String hint = I18n.get(FieldText.PREFIX + "support.skipped", I18n.get(DroneSupport.Skip.NO_GRIPPER.key()));
        List<String> tooltip = screen.detailTooltip(WaypointField.ACCESSORY.ordinal()).stream().map(Component::getString).toList();
        if (!tooltip.contains(hint)) {
            problems.add("Tooltip " + tooltip + " ohne „" + hint + "“");
        }
        return problems.isEmpty() ? StepResult.pass("„" + I18n.get(FieldText.PREFIX + "value.accessory.gripper_release") + "“ an Punkt 3 markiert, Hinweis „"
                + hint + "“, Detail " + screen.layout().detailPages() + " Seiten bei " + screen.width + "×" + screen.height)
                : StepResult.fail(String.join(", ", problems));
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
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of("Greifer", mission)), 0, TabletState.DEFAULT_GROUND_HEIGHT));
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
