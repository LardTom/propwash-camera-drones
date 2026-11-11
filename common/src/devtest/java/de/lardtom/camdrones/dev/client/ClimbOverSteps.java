package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ClimbOverSteps {
    public static final String TOUR = "hindernis: Übersteigen in der Mission (Tour)";
    public static final String CINE = "hindernis: Übersteigen in der Mission (Kino)";
    public static final double HEIGHT = 5.0;
    public static final int WALL_HEIGHT = 8;
    public static final int WALL_HALF_WIDTH = 6;
    public static final int WALL_DZ = 22;
    public static final double REACHED = 1.5;
    public static final double CLEARANCE = 0.2;
    private static final String WALL = "climb.wall";
    private static final String POINTS = "climb.points";
    private static final String TOP = "climb.top";
    private static final String CLOSEST = "climb.closest";
    private static final String INSIDE = "climb.inside";
    private static final String CROSSED = "climb.crossed";
    private static final int STEP_TICKS = 20 * 180;

    private ClimbOverSteps() {
    }

    public static void register() {
        SelfTest.clientStep(33, TOUR, STEP_TICKS, script(CamPresets.TOUR, 120, -100));
        SelfTest.clientStep(34, CINE, STEP_TICKS, script(CamPresets.CINE, 136, -100));
    }

    private static Script script(CamPresets.Entry entry, int dx, int dz) {
        return Script.create()
                .append(CamFlights.groundDrone(entry, dx, dz, 0.0F))
                .server("Wand und Mission mit „Übersteigen“", (server, state) -> setup(server, state, dx, dz))
                .sleep(10)
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Meldung Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("über die Wand zum Ziel", ClimbOverSteps::fly);
    }

    private static void setup(MinecraftServer server, Map<String, Object> state, int dx, int dz) {
        BlockPos from = Arena.ground(dx - WALL_HALF_WIDTH, dz + WALL_DZ).above();
        BlockPos to = Arena.ground(dx + WALL_HALF_WIDTH, dz + WALL_DZ).above(WALL_HEIGHT);
        Arena.fill(Arena.level(server), from, to, Blocks.STONE.defaultBlockState());
        state.put(WALL, new double[]{from.getX(), to.getX() + 1.0, from.getZ(), from.getZ() + 1.0, to.getY() + 1.0});
        Vec3 first = CamFlights.above(dx, dz + 8, HEIGHT);
        Vec3 second = CamFlights.above(dx, dz + 38, HEIGHT);
        state.put(POINTS, List.of(first, second));
        Mission mission = Mission.of(List.of(Waypoint.at(first.x, first.y, first.z), Waypoint.at(second.x, second.y, second.z)))
                .withEnd(Mission.End.HOVER).withBaseSpeed(4.0).withObstacle(ObstacleGuard.Mode.CLIMB_OVER);
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
        double[] wall = (double[]) state.get(WALL);
        List<Vec3> points = (List<Vec3>) state.get(POINTS);
        Vec3 p = drone.get().position();
        double halfWidth = drone.get().getBbWidth() / 2.0;
        boolean overWall = p.x + halfWidth > wall[0] && p.x - halfWidth < wall[1] && p.z + halfWidth > wall[2] && p.z - halfWidth < wall[3];
        if (overWall) {
            state.put(TOP, Math.min((double) state.getOrDefault(TOP, Double.MAX_VALUE), p.y - wall[4]));
            if (p.y < wall[4] - CLEARANCE) {
                state.put(INSIDE, true);
            }
        }
        if (p.z > wall[3] + 1.0) {
            state.put(CROSSED, true);
        }
        double closest = Math.min((double) state.getOrDefault(CLOSEST, Double.MAX_VALUE), p.distanceTo(points.get(1)));
        state.put(CLOSEST, closest);
        if ((boolean) state.getOrDefault(INSIDE, false)) {
            return StepResult.fail("fliegt in die Wand: " + Flight.format(p.y - wall[4]) + " m unter der Oberkante");
        }
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        boolean done = status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
        if (closest <= REACHED && done) {
            double top = (double) state.getOrDefault(TOP, Double.NaN);
            if (!(boolean) state.getOrDefault(CROSSED, false) || !Double.isFinite(top)) {
                return StepResult.fail("kommt ans Ziel, ohne die Wand zu überfliegen");
            }
            return StepResult.pass("steigt über die " + WALL_HEIGHT + " m hohe Wand (" + Flight.format(top) + " m über der Oberkante) und erreicht "
                    + "den Punkt dahinter auf " + Flight.format(closest) + " m, Zustand " + status.get().state());
        }
        if (ticks > 20 * 120) {
            return StepResult.fail("erreicht den Punkt hinter der Wand nicht: nächster Abstand " + Flight.format(closest) + " m, Höhe "
                    + Flight.format(p.y - CamFlights.surface()) + " m, Mission " + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("?")
                    + ", überflogen " + state.getOrDefault(CROSSED, false));
        }
        return StepResult.pending();
    }
}
