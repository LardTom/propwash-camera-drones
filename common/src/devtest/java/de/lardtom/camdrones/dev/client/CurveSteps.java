package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.planner.WaypointPreview;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.CurvedTurn;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CurveSteps {
    public static final int NUMBER = 553;
    public static final String NAME = "mission: weiche Kurven ohne Abbremsen";
    public static final String PHOTO = "mission_smooth_curves";
    public static final int CINE_NUMBER = 558;
    public static final String CINE_NAME = "mission: weiche Kurven ohne Abbremsen – Kino";
    public static final int CINE_DX = -120;
    public static final int CINE_DZ = 40;
    public static final int DX = -120;
    public static final int DZ = -60;
    public static final double HEIGHT = 12.0;
    public static final double LEG = 40.0;
    public static final double SPEED = 8.0;
    public static final double MIN_SHARE = 0.7;
    public static final double PLAN_SHARE = 0.85;
    public static final double MAX_DEVIATION = 1.5;
    public static final double BRAKE_ZONE = 28.0;
    public static final int WINDOW = 10;
    public static final int VIEW_DX = 34;
    public static final int VIEW_DZ = -30;
    public static final int VIEW_HEIGHT = 60;
    private static final String MISSION = "curve.mission";
    private static final String START = "curve.start";
    private static final String TRACK = "curve.track";
    private static final String LOWEST = "curve.lowest";
    private static final String LOWEST_AT = "curve.lowest.at";
    private static final String CRUISING = "curve.cruising";
    private static final String DEVIATION = "curve.deviation";
    private static final String TAKEOFF_TICK = "curve.takeoff";
    private static final String CLASS = "curve.class";
    private static final String FLOOR = "curve.floor";
    private static final String PLANNED = "curve.planned";
    private static final int STEP_TICKS = 20 * 240;

    private CurveSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, flight(CamPresets.TOUR, DX, DZ, false)
                .server("Planer mit der Mission, Blick schräg von oben", CurveSteps::viewer)
                .sleep(40)
                .then("Bahn in der Welt-Vorschau", (ctx, ticks) -> WaypointPreview.lastDrawn() > 0
                        ? StepResult.pass(WaypointPreview.lastDrawn() + " Marker, Bahn mit Bögen")
                        : ticks > 60 ? StepResult.fail("keine Vorschau in der Welt") : StepResult.pending())
                .append(Shots.photos(PHOTO))
                .server("Planer weg, zurück zum Start", CurveSteps::leave));
        SelfTest.clientStep(CINE_NUMBER, CINE_NAME, STEP_TICKS, flight(CamPresets.CINE, CINE_DX, CINE_DZ, true));
    }

    private static Script flight(CamPresets.Entry entry, int dx, int dz, boolean againstPlan) {
        return Script.create()
                .append(CamFlights.groundDrone(entry, dx, dz, -90.0F))
                .server("Mission mit Kurven 45°, 90° und 45°, weich, 8 m/s", (server, state) -> setup(server, state, entry, dx, dz, againstPlan))
                .sleep(10)
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Meldung Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Kurven ohne Abbremsen", CurveSteps::fly);
    }

    static Mission mission(Vec3 base) {
        double d = LEG / Math.sqrt(2.0);
        return Mission.of(List.of(Waypoint.at(base.x + LEG, base.y, base.z), Waypoint.at(base.x + LEG + d, base.y, base.z + d),
                Waypoint.at(base.x + LEG, base.y, base.z + 2.0 * d), Waypoint.at(base.x, base.y, base.z + 2.0 * d)))
                .withSmooth(true).withEnd(Mission.End.HOVER).withBaseSpeed(SPEED);
    }

    private static void setup(MinecraftServer server, Map<String, Object> state, CamPresets.Entry entry, int dx, int dz, boolean againstPlan) {
        Mission mission = mission(CamFlights.above(dx, dz, HEIGHT));
        double planned = plannedCurveSpeed(entry.flightClass(), mission);
        state.put(MISSION, mission);
        state.put(CLASS, entry.flightClass());
        state.put(PLANNED, planned);
        state.put(FLOOR, againstPlan ? PLAN_SHARE * planned : MIN_SHARE * SPEED);
        CamFlights.mission(server, state, mission);
    }

    @SuppressWarnings("unchecked")
    private static StepResult fly(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        Mission mission = (Mission) state.get(MISSION);
        Waypoint first = mission.waypoint(0);
        Waypoint last = mission.waypoint(mission.size() - 1);
        Vec3 p = drone.get().position();
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        int index = status.map(MissionStatus::index).orElse(0);
        if (!state.containsKey(START) && p.y >= first.y() - 0.5) {
            state.put(START, new double[]{p.x, first.y(), p.z});
            state.put(TAKEOFF_TICK, ticks);
        }
        List<Vec3> track = (List<Vec3>) state.computeIfAbsent(TRACK, k -> new ArrayList<Vec3>());
        track.add(p);
        if (state.containsKey(START) && track.size() > WINDOW) {
            double speed = CamFlights.horizontal(p, track.get(track.size() - 1 - WINDOW)) / (WINDOW * 0.05);
            boolean cruising = (boolean) state.getOrDefault(CRUISING, false) || speed >= 0.9 * SPEED;
            state.put(CRUISING, cruising);
            boolean braking = index == mission.size() - 1 && Math.hypot(p.x - last.x(), p.z - last.z()) < BRAKE_ZONE;
            if (cruising && !braking && speed < (double) state.getOrDefault(LOWEST, Double.MAX_VALUE)) {
                state.put(LOWEST, speed);
                state.put(LOWEST_AT, index + 1);
            }
            if (cruising) {
                List<double[]> path = flat(CurvedTurn.path(mission, (double[]) state.get(START)));
                double deviation = distance(path, p.x, p.z);
                state.put(DEVIATION, Math.max((double) state.getOrDefault(DEVIATION, 0.0), deviation));
            }
        }
        boolean done = status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
        double toLast = p.distanceTo(new Vec3(last.x(), last.y(), last.z()));
        double lowest = (double) state.getOrDefault(LOWEST, Double.NaN);
        double deviation = (double) state.getOrDefault(DEVIATION, Double.NaN);
        if (done && toLast <= 1.5) {
            double[] start = (double[]) state.get(START);
            double flown = (ticks - (int) state.getOrDefault(TAKEOFF_TICK, 0)) * 0.05;
            double estimate = FlightTimeEstimate.mission((FlightClass) state.get(CLASS), mission, start[0], start[1], start[2]).seconds();
            String numbers = "langsamstes Tempo " + Flight.format(lowest) + " m/s (" + Math.round(100.0 * lowest / SPEED) + " % von 8 m/s) vor Punkt "
                    + state.get(LOWEST_AT) + ", geplant im engsten Bogen " + Flight.format((double) state.get(PLANNED)) + " m/s, Grenze "
                    + Flight.format((double) state.get(FLOOR)) + " m/s, größte Abweichung von der geplanten Bahn " + Flight.format(deviation) + " m, Flug ab Zielhöhe "
                    + Flight.format(flown) + " s, Schätzung " + Flight.format(estimate) + " s";
            if (!(lowest >= (double) state.get(FLOOR))) {
                return StepResult.fail("bremst in den Kurven: " + numbers);
            }
            if (!(deviation <= MAX_DEVIATION)) {
                return StepResult.fail("weicht von der Bahn ab: " + numbers);
            }
            return StepResult.pass(numbers);
        }
        if (ticks > 20 * 120) {
            return StepResult.fail("Mission nicht fertig: Abstand zum letzten Punkt " + Flight.format(toLast) + " m, Zustand "
                    + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("?") + ", langsamstes Tempo " + Flight.format(lowest) + " m/s");
        }
        return StepResult.pending();
    }

    static double plannedCurveSpeed(FlightClass flightClass, Mission mission) {
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 1; i < mission.size(); i++) {
            CurvedTurn turn = CurvedTurn.at(mission, i);
            if (turn != null) {
                lowest = Math.min(lowest, CurvedTurn.plannedSpeed(flightClass, mission, i, turn));
            }
        }
        return Double.isFinite(lowest) ? lowest : SPEED;
    }

    private static List<double[]> flat(List<double[]> points) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : points) {
            out.add(new double[]{p[0], p[2]});
        }
        return out;
    }

    private static void viewer(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow());
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of("Kurven", (Mission) state.get(MISSION))), 0,
                TabletState.DEFAULT_GROUND_HEIGHT));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        BlockPos foot = Arena.ground(DX + VIEW_DX, DZ + VIEW_DZ).above();
        BlockPos top = foot.above(VIEW_HEIGHT - 1);
        Arena.fill(Arena.level(server), foot, top, Blocks.STONE.defaultBlockState());
        player.teleportTo(Arena.level(server), top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, Set.<Relative>of(), 0.0F, 42.0F, true);
    }

    private static void leave(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 spawn = Arena.spawn();
        player.teleportTo(Arena.level(server), spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 0.0F, true);
    }

    private static double distance(List<double[]> points, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            double sx = b[0] - a[0];
            double sz = b[1] - a[1];
            double l2 = sx * sx + sz * sz;
            double t = l2 < 1.0E-9 ? 0.0 : Math.clamp(((x - a[0]) * sx + (z - a[1]) * sz) / l2, 0.0, 1.0);
            best = Math.min(best, Math.hypot(x - a[0] - sx * t, z - a[1] - sz * t));
        }
        return best;
    }
}
