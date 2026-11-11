package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class MissionSteps {
    public static final String GROUND = "mission: Start am Boden, fünf Punkte, Landung";
    public static final String HANDOVER = "mission: Pilot verlässt FPV, Server fliegt weiter";
    public static final double HEIGHT = 6.0;
    public static final double REACHED = 1.5;
    public static final double LANDED_TOLERANCE = 2.0;
    public static final double HANDOVER_DROP = 0.5;
    public static final int[][] GROUND_POINTS = {{0, 10}, {8, 16}, {16, 10}, {16, 22}, {6, 26}};
    public static final int[][] HANDOVER_POINTS = {{0, 12}, {10, 18}, {20, 12}, {20, 24}, {8, 28}};
    private static final int GROUND_DX = -60;
    private static final int GROUND_DZ = -60;
    private static final int HANDOVER_DX = 40;
    private static final int HANDOVER_DZ = -60;
    private static final String POINTS = "mission.points";
    private static final String CLOSEST = "mission.closest";
    private static final String SHOTS = "mission.shots";
    private static final String PHOTOGRAPHED = "mission.photographed";
    private static final String BEFORE = "mission.before";
    private static final String LOWEST = "mission.lowest";
    private static final String DONE_WAIT = "mission.doneWait";
    private static final int STEP_TICKS = 20 * 240;

    private MissionSteps() {
    }

    public static void register() {
        SelfTest.clientStep(11, GROUND, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(CamFlights.groundDrone(CamPresets.TOUR, GROUND_DX, GROUND_DZ, 0.0F))
                .server("Mission mit fünf Punkten und Landung", (server, state) -> groundMission(server, state))
                .sleep(10)
                .then("Mission-Befehl START vom Boden", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then("Meldung Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("fünf Punkte abfliegen", MissionSteps::fly)
                .then("Landung", MissionSteps::landed)
                .append(Shots.photos("mission_landed")));
        SelfTest.clientStep(12, HANDOVER, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(Pilot.launch(CamPresets.TOUR, HANDOVER_DX, HANDOVER_DZ, 0.0F))
                .server("koppeln und Mission an die Drohne", (server, state) -> {
                    CamFlights.pair(server, state);
                    handoverMission(server, state);
                })
                .append(Flight.takeoff(4.0))
                .then("Mission starten", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(CamControl.MISSION_START, ticks < 4);
                    return ticks >= 6 ? StepResult.pass() : StepResult.pending();
                })
                .then("Mission läuft im Pilotenflug", MissionSteps::pilotRunning)
                .then("Höhe vor dem Verlassen", (ctx, ticks) -> {
                    Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    ctx.state().put(BEFORE, drone.get().getY());
                    ctx.state().put(CamFlights.MARK, StatusProbe.mark());
                    return StepResult.pass();
                })
                .server("FPV verlassen", (server, state) -> Pilot.release(server))
                .then("Server übernimmt", MissionSteps::handedOver)
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission läuft am Server weiter", MissionSteps::serverContinues)
                .append(Shots.photos("mission_server_continues")));
    }

    private static List<Vec3> points(int dx, int dz, int[][] offsets, double height) {
        List<Vec3> out = new ArrayList<>();
        for (int[] offset : offsets) {
            out.add(CamFlights.above(dx + offset[0], dz + offset[1], height));
        }
        return out;
    }

    private static Mission build(List<Vec3> points, Mission.End end) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Vec3 point : points) {
            waypoints.add(Waypoint.at(point.x, point.y, point.z));
        }
        return Mission.of(waypoints).withEnd(end).withBaseSpeed(5.0);
    }

    private static void groundMission(MinecraftServer server, Map<String, Object> state) {
        List<Vec3> points = points(GROUND_DX, GROUND_DZ, GROUND_POINTS, HEIGHT);
        state.put(POINTS, points);
        CamFlights.mission(server, state, build(points, Mission.End.LAND));
    }

    private static void handoverMission(MinecraftServer server, Map<String, Object> state) {
        double height = 5.0;
        List<Vec3> points = points(HANDOVER_DX, HANDOVER_DZ, HANDOVER_POINTS, height);
        state.put(POINTS, points);
        CamFlights.mission(server, state, build(points, Mission.End.HOVER));
    }

    @SuppressWarnings("unchecked")
    private static List<Vec3> points(Map<String, Object> state) {
        return (List<Vec3>) state.get(POINTS);
    }

    @SuppressWarnings("unchecked")
    private static double[] closest(Map<String, Object> state) {
        return (double[]) state.computeIfAbsent(CLOSEST, key -> {
            double[] out = new double[points(state).size()];
            Arrays.fill(out, Double.MAX_VALUE);
            return out;
        });
    }

    @SuppressWarnings("unchecked")
    private static List<CompletableFuture<String>> shots(Map<String, Object> state) {
        return (List<CompletableFuture<String>>) state.computeIfAbsent(SHOTS, key -> new ArrayList<CompletableFuture<String>>());
    }

    private static void track(Map<String, Object> state, Entity drone) {
        List<Vec3> points = points(state);
        double[] closest = closest(state);
        Vec3 position = drone.position();
        for (int i = 0; i < points.size(); i++) {
            closest[i] = Math.min(closest[i], position.distanceTo(points.get(i)));
        }
        int photographed = (int) state.getOrDefault(PHOTOGRAPHED, 0);
        if (photographed < points.size() && position.distanceTo(points.get(photographed)) <= REACHED + 0.5) {
            state.put(PHOTOGRAPHED, photographed + 1);
            if (Shots.enabled()) {
                Shots.clearOverlays(Minecraft.getInstance());
                shots(state).add(Shots.capture("mission_point_" + (photographed + 1)));
            }
        }
    }

    private static StepResult fly(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        track(state, drone.get());
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        boolean done = status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
        int last = points(state).size() - 1;
        if (!done && closest(state)[last] > REACHED) {
            return ticks > 20 * 120 ? StepResult.fail("Mission kommt nicht an (" + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("?")
                    + ", Abstände " + distances(state) + ")") : StepResult.pending();
        }
        for (int i = 0; i < closest(state).length; i++) {
            if (closest(state)[i] > REACHED) {
                return StepResult.fail("Punkt " + (i + 1) + " verfehlt: " + Flight.format(closest(state)[i]) + " m (Grenze "
                        + Flight.format(REACHED) + " m), Abstände " + distances(state));
            }
        }
        return StepResult.pass("fünf Punkte abgeflogen in " + Flight.format(ticks / 20.0) + " s, nächste Abstände " + distances(state));
    }

    private static String distances(Map<String, Object> state) {
        List<String> out = new ArrayList<>();
        for (double d : closest(state)) {
            out.add(d == Double.MAX_VALUE ? "-" : Flight.format(d));
        }
        return String.join("/", out) + " m";
    }

    private static StepResult landed(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        Optional<DroneView> view = CamFlights.clientView(state);
        boolean grounded = view.map(v -> !v.flying()).orElse(false) && drone.get().getY() - CamFlights.surface() < 0.5;
        int mark = CamFlights.mark(ctx);
        boolean told = StatusProbe.seen(mark, AutonomyStatus.MISSION_DONE) || StatusProbe.seen(mark, AutonomyStatus.ENDED_LANDED);
        if (!grounded || !told) {
            return ticks > 20 * 60 ? StepResult.fail("landet nicht: Höhe " + Flight.format(drone.get().getY() - CamFlights.surface()) + " m, "
                    + "Meldungen " + StatusProbe.describe(mark)) : StepResult.pending();
        }
        for (CompletableFuture<String> shot : shots(state)) {
            if (!shot.isDone()) {
                return StepResult.pending();
            }
            if (shot.isCompletedExceptionally()) {
                return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
            }
        }
        List<Vec3> points = points(state);
        double off = CamFlights.horizontal(drone.get().position(), points.getLast());
        if (off > LANDED_TOLERANCE) {
            return StepResult.fail("landet " + Flight.format(off) + " m neben dem letzten Punkt");
        }
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        if (status.isEmpty() || status.get().state() != NavLogic.MissionState.DONE) {
            int waited = (int) state.merge(DONE_WAIT, 1, (a, b) -> (int) a + (int) b);
            return waited > 40 ? StepResult.fail("Zustand nach der Landung " + status.map(s -> s.state().name()).orElse("?") + " statt DONE")
                    : StepResult.pending();
        }
        return StepResult.pass("gelandet " + Flight.format(off) + " m neben dem letzten Punkt, Zustand DONE, Meldungen "
                + StatusProbe.describe(mark) + (Shots.enabled() ? ", " + shots(state).size() + " Fotos je Punkt" : ""));
    }

    private static StepResult pilotRunning(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        CamFlights.clientDrone(ctx.state()).ifPresent(drone -> track(ctx.state(), drone));
        Optional<NavStatus> status = Pilot.status();
        if (status.isPresent() && status.get().function() == NavLogic.Function.MISSION && status.get().missionState() == NavLogic.MissionState.RUNNING
                && status.get().missionIndex() >= 1) {
            return StepResult.pass("Mission läuft in der Flughilfe des Piloten, Ziel Punkt " + (status.get().missionIndex() + 1));
        }
        return ticks > 20 * 40 ? StepResult.fail("Mission läuft nicht im Pilotenflug: "
                + status.map(s -> s.function() + "/" + s.missionState() + " " + (s.missionIndex() + 1)).orElse("kein Status")) : StepResult.pending();
    }

    private static StepResult handedOver(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double before = (double) state.get(BEFORE);
        double lowest = Math.min((double) state.getOrDefault(LOWEST, before), drone.get().getY());
        state.put(LOWEST, lowest);
        if (PropwashClientApi.input().flying()) {
            return ticks > 200 ? StepResult.fail("Pilot bleibt im FPV") : StepResult.pending();
        }
        CamFlights.lookAt(drone.get());
        if (ticks < 40) {
            return StepResult.pending();
        }
        int mark = CamFlights.mark(ctx);
        if (!StatusProbe.seen(mark, AutonomyStatus.MISSION_STARTED)) {
            return ticks > 200 ? StepResult.fail("keine Meldung, dass der Server die Mission übernimmt (" + StatusProbe.describe(mark) + ")")
                    : StepResult.pending();
        }
        double drop = before - lowest;
        if (drop > HANDOVER_DROP) {
            return StepResult.fail("sackt bei der Übergabe " + Flight.format(drop) + " m ab (Grenze " + Flight.format(HANDOVER_DROP) + " m)");
        }
        return StepResult.pass("Übergabe ohne Absacken (" + Flight.format(Math.max(0.0, drop)) + " m), Meldungen " + StatusProbe.describe(mark));
    }

    private static StepResult serverContinues(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        track(state, drone.get());
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        boolean done = status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
        int last = points(state).size() - 1;
        if (!done || closest(state)[last] > REACHED) {
            return ticks > 20 * 120 ? StepResult.fail("Server fliegt die Mission nicht zu Ende (" + status.map(s -> s.state() + " "
                    + (s.index() + 1)).orElse("?") + ", Abstände " + distances(state) + ")") : StepResult.pending();
        }
        if (ticks < 20) {
            return StepResult.pending();
        }
        return StepResult.pass(String.format(Locale.GERMANY, "Server fliegt die Mission nach %.1f s zu Ende, nächste Abstände aller Punkte %s, "
                + "Drohne schwebt am letzten Punkt", ticks / 20.0, distances(state)));
    }
}
