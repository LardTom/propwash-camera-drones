package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.DroneSelector;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class DamageSteps {
    public static final String NAME = "flug: Schaden am Server beendet die Mission ohne Pilot";
    public static final double AIRBORNE = 3.0;
    private static final int DX = -150;
    private static final int DZ = 150;
    private static final double HEIGHT = 6.0;
    private static final String LANDED = "damage.landed";
    private static final String LOG_MARK = "damage.log";
    private static final String LOGGED = "beschädigt";

    private DamageSteps() {
    }

    public static void register() {
        SelfTest.clientStep(325, NAME, 20 * 150, Script.create()
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .server("Mission mit zwei weiten Punkten", (server, state) -> {
                    Vec3 first = CamFlights.above(DX, DZ - 40, HEIGHT);
                    Vec3 second = CamFlights.above(DX + 40, DZ - 40, HEIGHT);
                    CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(first.x, first.y, first.z),
                            Waypoint.at(second.x, second.y, second.z))).withEnd(Mission.End.HOVER).withBaseSpeed(3.0));
                })
                .sleep(10)
                .then("Mission-Befehl START vom Boden", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then("Meldung Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission läuft in der Luft", DamageSteps::airborne)
                .then("Log mitlesen", (ctx, ticks) -> {
                    if (!LogProbe.install()) {
                        return StepResult.fail(LogProbe.problem());
                    }
                    ctx.state().put(LOG_MARK, LogProbe.mark());
                    return StepResult.pass();
                })
                .server("Drohne nimmt Schaden", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    ServerLevel level = (ServerLevel) drone.level();
                    drone.hurtServer(level, level.damageSources().generic(), 1.0F);
                })
                .then("Mission abgebrochen, Drohne landet", DamageSteps::ended)
                .then("Log nennt die Drohne mit ID", DamageSteps::logged));
    }

    private static StepResult airborne(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        double height = drone.get().getY() - CamFlights.surface();
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        boolean running = status.map(s -> s.state() == NavLogic.MissionState.RUNNING).orElse(false);
        if (running && height > AIRBORNE && ticks >= 40) {
            return StepResult.pass("Mission RUNNING in " + Flight.format(height) + " m Höhe");
        }
        return ticks > 20 * 30 ? StepResult.fail("Mission läuft nicht in der Luft: " + status.map(s -> s.state().name()).orElse("?") + ", Höhe "
                + Flight.format(height) + " m") : StepResult.pending();
    }

    private static StepResult ended(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        double height = drone.get().getY() - CamFlights.surface();
        NavLogic.MissionState state = CamFlights.clientMissionStatus(ctx.state()).map(MissionStatus::state).orElse(NavLogic.MissionState.IDLE);
        if (state == NavLogic.MissionState.RUNNING && ticks > 100) {
            return StepResult.fail("Mission läuft nach dem Schaden weiter (" + Flight.format(ticks / 20.0) + " s, Höhe " + Flight.format(height) + " m)");
        }
        if (state == NavLogic.MissionState.ABORTED && height < 0.4) {
            int since = (int) ctx.state().computeIfAbsent(LANDED, key -> ticks);
            if (ticks - since >= 40) {
                return StepResult.pass("Mission ABORTED, gelandet nach " + Flight.format(ticks / 20.0) + " s");
            }
        } else {
            ctx.state().remove(LANDED);
        }
        return ticks > 20 * 60 ? StepResult.fail("nach dem Schaden: Mission " + state + ", Höhe " + Flight.format(height) + " m") : StepResult.pending();
    }

    private static StepResult logged(SelfTest.StepContext ctx, int ticks) {
        String id = DroneSelector.shortSerial(CamFlights.serial(ctx.state()));
        Optional<String> line = LogProbe.since((int) ctx.state().getOrDefault(LOG_MARK, 0)).stream().filter(l -> l.contains(LOGGED)).findFirst();
        if (line.isEmpty()) {
            return ticks > 40 ? StepResult.fail("kein Log-Eintrag „" + LOGGED + "“") : StepResult.pending();
        }
        boolean key = line.get().contains(".frame_short.") || line.get().contains("part.");
        return line.get().contains(id) && !key ? StepResult.pass("Log: " + line.get())
                : StepResult.fail("Log nennt nicht die ID " + id + (key ? ", sondern einen Sprachschlüssel" : "") + ": " + line.get());
    }
}
