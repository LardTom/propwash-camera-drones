package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class TripodSteps {
    public static final String NAME = "halten: Luftstativ über drei Minuten";
    public static final int HOLD_SECONDS = 180;
    public static final double HORIZONTAL_LIMIT = 0.5;
    public static final double VERTICAL_LIMIT = 0.3;
    public static final double CALM_SPEED = 0.1;
    private static final String ANCHOR = "tripod.anchor";
    private static final String WORST = "tripod.worst";
    private static final String LAST = "tripod.last";
    private static final String CALM = "tripod.calm";
    private static final String SAMPLES = "tripod.samples";
    private static final int DX = 100;
    private static final int DZ = 60;

    private TripodSteps() {
    }

    public static void register() {
        SelfTest.clientStep(30, NAME, 20 * (HOLD_SECONDS + 120), Script.create()
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .then("Halten anfordern", (ctx, ticks) -> {
                    Entity drone = CamFlights.clientDrone(ctx.state()).orElseThrow(() -> new IllegalStateException("Drohne fehlt am Client"));
                    return CamFlights.send(ctx, new SmartMode.Request(CamFlights.serial(ctx.state()),
                            SmartState.hold(drone.getX(), drone.getY(), drone.getZ())));
                })
                .then("Halten gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.HOLD_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "hold"))
                .then("abheben und zur Ruhe kommen", TripodSteps::settle)
                .then("drei Minuten halten", TripodSteps::tripod)
                .then("Flug läuft noch", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "hold")));
    }

    private static StepResult settle(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        Vec3 p = drone.get().position();
        double speed = ctx.state().get(LAST) instanceof Vec3 last ? p.distanceTo(last) * 20.0 : Double.MAX_VALUE;
        ctx.state().put(LAST, p);
        double height = p.y - CamFlights.surface();
        int calm = height > 2.0 && speed < CALM_SPEED ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calm);
        if (calm >= 40) {
            ctx.state().put(ANCHOR, p);
            return StepResult.pass("schwebt in " + Flight.format(height) + " m Höhe");
        }
        return ticks > 20 * 40 ? StepResult.fail("kommt nicht zur Ruhe: Höhe " + Flight.format(height) + " m, Tempo " + Flight.format(speed) + " m/s")
                : StepResult.pending();
    }

    private static StepResult tripod(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        Vec3 anchor = (Vec3) ctx.state().get(ANCHOR);
        Vec3 p = drone.get().position();
        double[] worst = (double[]) ctx.state().computeIfAbsent(WORST, key -> new double[2]);
        worst[0] = Math.max(worst[0], Math.hypot(p.x - anchor.x, p.z - anchor.z));
        worst[1] = Math.max(worst[1], Math.abs(p.y - anchor.y));
        ctx.state().put(SAMPLES, (int) ctx.state().getOrDefault(SAMPLES, 0) + 1);
        int mark = CamFlights.mark(ctx);
        Optional<StatusMessage.Payload> ended = StatusProbe.since(mark).stream().filter(s -> s.status().name().startsWith("ENDED_")).findFirst();
        if (ended.isPresent()) {
            return StepResult.fail("Halten endet nach " + Flight.format(ticks / 20.0) + " s mit " + ended.get().status());
        }
        if (worst[0] > HORIZONTAL_LIMIT || worst[1] > VERTICAL_LIMIT) {
            return StepResult.fail("weicht als Luftstativ " + Flight.format(worst[0]) + " m seitlich und " + Flight.format(worst[1])
                    + " m in der Höhe ab (Grenze " + Flight.format(HORIZONTAL_LIMIT) + " bzw. " + Flight.format(VERTICAL_LIMIT) + " m) nach "
                    + Flight.format(ticks / 20.0) + " s");
        }
        if (ticks % 600 == 0 && ticks > 0) {
            ctx.log("Luftstativ nach " + ticks / 20 + " s: seitlich " + Flight.format(worst[0]) + " m, Höhe " + Flight.format(worst[1]) + " m");
        }
        if (ticks < 20 * HOLD_SECONDS) {
            return StepResult.pending();
        }
        return StepResult.pass("hält als Luftstativ " + HOLD_SECONDS + " s bei Wind " + Arena.CALM_WIND + " auf " + Flight.format(worst[0])
                + " m seitlich und " + Flight.format(worst[1]) + " m in der Höhe (Grenzen " + Flight.format(HORIZONTAL_LIMIT) + " und "
                + Flight.format(VERTICAL_LIMIT) + " m, " + ctx.state().get(SAMPLES) + " Messungen)");
    }
}
