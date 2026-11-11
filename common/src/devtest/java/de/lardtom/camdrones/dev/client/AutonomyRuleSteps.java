package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;

public final class AutonomyRuleSteps {
    public static final String NAME = "welt-regeln: autonome Flüge erlauben und Höchstdauer";
    public static final int LIMIT_MINUTES = 1;
    public static final double LIMIT_TOLERANCE_SECONDS = 4.0;
    private static final String STARTED_AT = "rules.startedAt";
    private static final String ENDED_AFTER = "rules.endedAfter";
    private static final String ANCHOR = "rules.anchor";
    private static final String TRIPOD = "rules.tripod";
    private static final String TRIPOD_TICKS = "rules.tripodTicks";
    public static final double TRIPOD_LIMIT = 1.0;
    private static final int DX = 64;
    private static final int DZ = 20;

    private AutonomyRuleSteps() {
    }

    public static void register() {
        SelfTest.clientStep(23, NAME, 20 * 240, Script.create()
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .server("„Autonome Flüge erlauben“ aus", (server, state) -> Arena.rule(server, CamWorldRules.ALLOW_AUTONOMY_ID, "false"))
                .then("Halten anfordern", (ctx, ticks) -> CamFlights.send(ctx, hold(ctx.state())))
                .then("verweigert", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.DISABLED, 100))
                .then("bleibt am Boden", AutonomyRuleSteps::staysDown)
                .server("Regel an, Höchstdauer " + LIMIT_MINUTES + " min", (server, state) -> {
                    Arena.rule(server, CamWorldRules.ALLOW_AUTONOMY_ID, "true");
                    Arena.rule(server, CamWorldRules.MAX_AUTONOMY_MINUTES_ID, Integer.toString(LIMIT_MINUTES));
                })
                .then("Halten anfordern", (ctx, ticks) -> CamFlights.send(ctx, hold(ctx.state())))
                .then("Halten gestartet", (ctx, ticks) -> {
                    StepResult result = CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.HOLD_STARTED, 100);
                    if (result.done() && !result.failed()) {
                        ctx.state().put(STARTED_AT, System.currentTimeMillis());
                    }
                    return result;
                })
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "hold"))
                .then("Ende nach der Höchstdauer", AutonomyRuleSteps::timeout)
                .then("landet bzw. fliegt heim", AutonomyRuleSteps::down));
    }

    private static SmartMode.Request hold(Map<String, Object> state) {
        Entity drone = CamFlights.clientDrone(state).orElseThrow(() -> new IllegalStateException("Drohne fehlt am Client"));
        return new SmartMode.Request(CamFlights.serial(state), SmartState.hold(drone.getX(), drone.getY(), drone.getZ()));
    }

    private static StepResult staysDown(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double height = drone.get().getY() - CamFlights.surface();
        boolean flying = CamFlights.clientView(ctx.state()).map(DroneView::flying).orElse(false);
        if (flying || height > 0.5) {
            return StepResult.fail("startet trotz ausgeschalteter Regel (Höhe " + Flight.format(height) + " m)");
        }
        if (ticks < 60) {
            return StepResult.pending();
        }
        return StepResult.pass("Meldung " + StatusProbe.describe(CamFlights.mark(ctx)) + ", Drohne bleibt am Boden");
    }

    private static StepResult timeout(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        drone.ifPresent(CamFlights::lookAt);
        if (drone.isPresent() && ticks >= 20 * 10) {
            Vec3 p = drone.get().position();
            Vec3 anchor = (Vec3) ctx.state().computeIfAbsent(ANCHOR, key -> p);
            ctx.state().put(TRIPOD, Math.max((double) ctx.state().getOrDefault(TRIPOD, 0.0), p.distanceTo(anchor)));
            ctx.state().put(TRIPOD_TICKS, ticks - 20 * 10);
        }
        int mark = CamFlights.mark(ctx);
        Optional<StatusMessage.Payload> other = StatusProbe.since(mark).stream()
                .filter(p -> p.status().name().startsWith("ENDED_") && p.status() != AutonomyStatus.ENDED_TIMEOUT).findFirst();
        if (other.isPresent()) {
            return StepResult.fail("Flug endet mit " + other.get().status() + " statt nach der Höchstdauer");
        }
        if (!StatusProbe.seen(mark, AutonomyStatus.ENDED_TIMEOUT)) {
            return ticks > 20 * (60 * LIMIT_MINUTES + 30) ? StepResult.fail("kein Ende nach " + LIMIT_MINUTES + " min (" + StatusProbe.describe(mark) + ")")
                    : StepResult.pending();
        }
        double seconds = (System.currentTimeMillis() - (long) ctx.state().get(STARTED_AT)) / 1000.0;
        ctx.state().put(ENDED_AFTER, seconds);
        if (Math.abs(seconds - 60.0 * LIMIT_MINUTES) > LIMIT_TOLERANCE_SECONDS) {
            return StepResult.fail("Flug endet nach " + Flight.format(seconds) + " s statt nach " + LIMIT_MINUTES + " min");
        }
        double tripod = (double) ctx.state().getOrDefault(TRIPOD, Double.NaN);
        double held = (int) ctx.state().getOrDefault(TRIPOD_TICKS, 0) / 20.0;
        if (!(tripod <= TRIPOD_LIMIT) || held < 40.0) {
            return StepResult.fail("hält als Luftstativ nur auf " + Flight.format(tripod) + " m genau über " + Flight.format(held) + " s (Grenze "
                    + Flight.format(TRIPOD_LIMIT) + " m)");
        }
        return StepResult.pass("hält als Luftstativ " + Flight.format(held) + " s auf " + Flight.format(tripod) + " m genau; Flug ohne Pilot endet nach "
                + Flight.format(seconds) + " s mit TIMEOUT (Regel " + LIMIT_MINUTES + " min)");
    }

    private static StepResult down(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        boolean flying = CamFlights.clientView(ctx.state()).map(DroneView::flying).orElse(true);
        double height = drone.get().getY() - CamFlights.surface();
        if (!flying && height < 0.5) {
            return StepResult.pass("nach dem Ende gelandet (" + Flight.format(ticks / 20.0) + " s)");
        }
        return ticks > 20 * 60 ? StepResult.fail("landet nach dem Ende nicht: Höhe " + Flight.format(height) + " m") : StepResult.pending();
    }
}
