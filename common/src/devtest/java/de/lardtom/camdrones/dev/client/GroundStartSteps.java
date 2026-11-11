package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GroundStartSteps {
    public static final int NUMBER = 554;
    public static final String NAME = "mission: Start vom Boden für Compact, Tour und Kino (Kino steht auf dem Gimbal)";
    public static final int DZ = 100;
    public static final int SPACING = 40;
    public static final double HEIGHT = 12.0;
    public static final double AHEAD = 10.0;
    public static final int SETTLE_TICKS = 40;
    public static final int CLEAR_TICKS = 10;
    private static final String REST = "groundstart.rest";
    private static final int STEP_TICKS = 20 * 120;

    private GroundStartSteps() {
    }

    public static void register() {
        List<CamPresets.Entry> entries = List.of(CamPresets.COMPACT, CamPresets.TOUR, CamPresets.CINE);
        Script script = Script.create().server("Wind schwach", (server, state) -> Arena.calmWind(server));
        for (int k = 0; k < entries.size(); k++) {
            script.append(start(entries.get(k), (k - 1) * SPACING, DZ));
        }
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, script);
    }

    private static Script start(CamPresets.Entry entry, int dx, int dz) {
        String name = entry.path();
        return Script.create()
                .append(CamFlights.groundDrone(entry, dx, dz, 0.0F))
                .sleep(SETTLE_TICKS)
                .server(name + ": Lage am Boden und Mission", (server, state) -> rest(server, state, name, dx, dz))
                .then(name + ": am Boden", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(REST)))
                .then(name + ": Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then(name + ": Meldung Mission gestartet", GroundStartSteps::started)
                .then(name + ": Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .server(name + ": Flug beenden, Drohne abräumen", (server, state) -> {
                    CamFlights.stopFlights(server);
                    Pilot.serverDrone(server, state).discard();
                })
                .sleep(CLEAR_TICKS);
    }

    private static void rest(MinecraftServer server, Map<String, Object> state, String name, int dx, int dz) {
        Entity drone = Pilot.serverDrone(server, state);
        double surface = Arena.ground(dx, dz).getY() + 1.0;
        double gap = drone.getBoundingBox().minY - surface;
        boolean flying = PropwashApi.drones().view(drone).map(DroneView::flying).orElse(true);
        state.put(REST, String.format(Locale.ROOT, "%s Hitbox %.3f m über dem Boden, onGround %s, fliegt %s", name, gap, drone.onGround(),
                flying));
        CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), surface + HEIGHT, drone.getZ() + AHEAD)))
                .withEnd(Mission.End.HOVER));
    }

    private static StepResult started(SelfTest.StepContext ctx, int ticks) {
        StepResult result = CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100);
        return result.failed() ? StepResult.fail(result.message() + "; " + ctx.state().get(REST)) : result;
    }
}
