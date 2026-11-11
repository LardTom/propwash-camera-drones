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
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TakeoverSteps {
    public static final String NAME = "mission: Brille wieder auf, Pilot übernimmt, Mission pausiert";
    public static final String SEAMLESS = "mission: Brille wieder auf, Drohne bleibt bei der Übernahme scharf in der Luft";
    public static final double HEIGHT = 8.0;
    public static final double HANDOVER_DROP = 0.5;
    public static final double MIN_HOLD_HEIGHT = 1.5;
    public static final double REACHED = 1.5;
    public static final double ARRIVED = 2.5;
    public static final double HOLD_LIMIT = 1.0;
    public static final double HOLD_HEIGHT_LIMIT = 0.6;
    public static final int BRAKE_TICKS = 60;
    public static final int HOLD_TICKS = 60;
    public static final int[][] POINTS = {{0, 12}, {10, 20}, {20, 12}, {20, 26}, {8, 32}};
    private static final int DX = -112;
    private static final int DZ = -40;
    private static final String POINT_LIST = "takeover.points";
    private static final String CLOSEST = "takeover.closest";
    private static final String PAUSED_AT = "takeover.pausedAt";
    private static final String ANCHOR = "takeover.anchor";
    private static final String WORST = "takeover.worst";
    private static final String SERVER = "takeover.server";
    private static final String BEFORE = "takeover.before";
    private static final String LOWEST = "takeover.lowest";
    private static final String HOLD_SINCE = "takeover.holdSince";
    private static final String RETAKE_PHASE = "takeover.retakePhase";
    private static final String RETAKE_SINCE = "takeover.retakeSince";
    private static final String RETAKE_ATTEMPT = "takeover.retakeAttempt";
    private static final int RETAKE_ATTEMPTS = 4;
    private static final String ARM_CLICK = "takeover.armClick";
    private static final int ARM_RETRY_TICKS = 30;
    private static final int STEP_TICKS = 20 * 240;

    private TakeoverSteps() {
    }

    public static void register() {
        SelfTest.clientStep(29, NAME, STEP_TICKS, untilTakeover(DX, DZ)
                .then("sofort scharf schalten, Gas in die Mitte", TakeoverSteps::armAndCenter)
                .then("Server gibt ab, Mission pausiert", TakeoverSteps::paused)
                .then("Flug am Server beendet", TakeoverSteps::serverIdle)
                .then("Kompass nach der Übernahme", TakeoverSteps::compassAfterTakeover)
                .then("Pilot bremst und hält", TakeoverSteps::holds)
                .then("Mission fortsetzen", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    Pilot.hold(CamControl.MISSION_START, ticks < 4);
                    return ticks >= 6 ? StepResult.pass() : StepResult.pending();
                })
                .then("Pilot fliegt den Rest der Mission", TakeoverSteps::resumed), TakeoverSteps::cleanup);
    }

    public static void registerSeamless() {
        SelfTest.clientStep(37, SEAMLESS, STEP_TICKS, untilTakeover(DX, DZ + 50)
                .then("Drohne bleibt bei der Übernahme scharf in der Luft", TakeoverSteps::seamless), TakeoverSteps::cleanup);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Pilot.releaseKeys();
        return StepResult.pass();
    }

    private static Script untilTakeover(int dx, int dz) {
        return Script.create()
                .append(Pilot.launch(CamPresets.TOUR, dx, dz, 0.0F))
                .server("koppeln und Mission an die Drohne", (server, state) -> {
                    CamFlights.pair(server, state);
                    mission(server, state, dx, dz);
                })
                .append(Flight.takeoff(4.0))
                .then("Mission starten", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(CamControl.MISSION_START, ticks < 4);
                    return ticks >= 6 ? StepResult.pass() : StepResult.pending();
                })
                .then("Mission läuft im Pilotenflug", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Optional<NavStatus> status = Pilot.status();
                    if (status.isPresent() && status.get().function() == NavLogic.Function.MISSION
                            && status.get().missionState() == NavLogic.MissionState.RUNNING) {
                        return StepResult.pass();
                    }
                    return ticks > 20 * 20 ? StepResult.fail("Mission startet nicht im Pilotenflug: " + Flight.describe()) : StepResult.pending();
                })
                .then("FPV verlassen", (ctx, ticks) -> {
                    ctx.state().put(CamFlights.MARK, StatusProbe.mark());
                    ctx.onServer(Pilot::release);
                    return StepResult.pass();
                })
                .then("Server fliegt weiter", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Server erreicht Punkt 3", TakeoverSteps::serverProgress)
                .then("Brille wieder auf", (ctx, ticks) -> {
                    CamFlights.clientDrone(ctx.state()).ifPresent(d -> ctx.state().put(BEFORE, d.getY()));
                    ctx.state().put(CamFlights.MARK, StatusProbe.mark());
                    ctx.onServer(server -> Pilot.start(server, ctx.state()));
                    return StepResult.pass();
                })
                .then("FPV-Ansicht am Client", (ctx, ticks) -> {
                    if (PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent()) {
                        ctx.log("Übernahme: Höhe " + Flight.format(Flight.height()) + " m, scharf " + Pilot.armed() + ", Gas "
                                + Flight.format(PropwashClientApi.input().throttle()) + ", " + Flight.describe());
                        return StepResult.pass();
                    }
                    return ticks > 200 ? StepResult.fail("FPV startet nicht wieder") : StepResult.pending();
                });
    }

    private static StepResult armAndCenter(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        armOnce(ctx, ticks);
        boolean centered = Pilot.steer(Pilot.THROTTLE_CENTER);
        if (Pilot.armed() && centered) {
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("nach der Übernahme nicht scharf (" + Pilot.armed() + ") oder Gas nicht in der Mitte ("
                + Flight.format(PropwashClientApi.input().throttle()) + ")") : StepResult.pending();
    }

    private static StepResult compassAfterTakeover(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(RETAKE_PHASE, 0);
        int since = (int) state.getOrDefault(RETAKE_SINCE, 0);
        int attempt = (int) state.getOrDefault(RETAKE_ATTEMPT, 0);
        int waited = ticks - since;
        switch (phase) {
            case 0 -> {
                Pilot.steer(Pilot.THROTTLE_CENTER);
                if (waited < 20) {
                    return StepResult.pending();
                }
                boolean disturbed = Pilot.status().map(s -> s.warns(NavOutput.Warning.COMPASS)).orElse(false);
                if (!disturbed) {
                    return StepResult.pass(attempt == 0 ? "" : "Kompass nach " + attempt + " erneuter Übernahme(n) in Ordnung");
                }
                if (attempt >= RETAKE_ATTEMPTS) {
                    return StepResult.fail("Kompass nach " + attempt + " erneuten Übernahmen gestört");
                }
                ctx.log("Kompass laut Propwash in diesem Flug gestört (Zufall), FPV verlassen und erneut übernehmen (" + (attempt + 1) + ")");
                ctx.onServer(Pilot::release);
                state.put(RETAKE_ATTEMPT, attempt + 1);
                next(state, 1, ticks);
            }
            case 1 -> {
                if (PropwashClientApi.input().flying()) {
                    return waited > 200 ? StepResult.fail("FPV endet für die erneute Übernahme nicht") : StepResult.pending();
                }
                if (waited < 40) {
                    return StepResult.pending();
                }
                ctx.onServer(server -> {
                    try {
                        Pilot.start(server, state);
                    } catch (IllegalStateException e) {
                        ctx.log("erneute Übernahme: " + e.getMessage());
                    }
                });
                next(state, 2, ticks);
            }
            case 2 -> {
                if (!PropwashClientApi.input().flying() || PropwashClientApi.input().drone().isEmpty()) {
                    return waited > 200 ? StepResult.fail("FPV startet für die erneute Übernahme nicht") : StepResult.pending();
                }
                armOnce(ctx, ticks);
                boolean centered = Pilot.steer(Pilot.THROTTLE_CENTER);
                if (Pilot.armed() && centered) {
                    next(state, 0, ticks);
                } else if (waited > 200) {
                    return StepResult.fail("nach der erneuten Übernahme nicht scharf");
                }
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static void armOnce(SelfTest.StepContext ctx, int ticks) {
        int last = (int) ctx.state().getOrDefault(ARM_CLICK, Integer.MIN_VALUE / 2);
        if (!Pilot.armed() && ticks - last >= ARM_RETRY_TICKS) {
            Pilot.click(Pilot.ARM);
            ctx.state().put(ARM_CLICK, ticks);
        }
    }

    private static void next(Map<String, Object> state, int phase, int ticks) {
        state.put(RETAKE_PHASE, phase);
        state.put(RETAKE_SINCE, ticks);
    }

    private static StepResult seamless(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double before = (double) ctx.state().get(BEFORE);
        double drop = before - drone.get().getY();
        ctx.state().put(LOWEST, Math.max((double) ctx.state().getOrDefault(LOWEST, 0.0), drop));
        double worst = (double) ctx.state().get(LOWEST);
        if (ticks == 10 && !Pilot.armed()) {
            return StepResult.fail("Drohne nach der Übernahme nicht scharf, Höhe " + Flight.format(Flight.height()) + " m, " + Flight.format(worst)
                    + " m abgesackt");
        }
        if (worst > HANDOVER_DROP) {
            return StepResult.fail("sackt bei der Übernahme " + Flight.format(worst) + " m ab (Grenze " + Flight.format(HANDOVER_DROP) + " m), scharf "
                    + Pilot.armed() + ", Gas " + Flight.format(PropwashClientApi.input().throttle()));
        }
        if (ticks < HOLD_TICKS) {
            return StepResult.pending();
        }
        return StepResult.pass("Übernahme ohne Absacken: " + Flight.format(worst) + " m in " + Flight.format(HOLD_TICKS / 20.0) + " s, scharf, "
                + Flight.describe());
    }

    private static void mission(MinecraftServer server, Map<String, Object> state, int dx, int dz) {
        List<Vec3> points = new ArrayList<>();
        List<Waypoint> waypoints = new ArrayList<>();
        for (int[] offset : POINTS) {
            Vec3 point = CamFlights.above(dx + offset[0], dz + offset[1], HEIGHT);
            points.add(point);
            waypoints.add(Waypoint.at(point.x, point.y, point.z));
        }
        state.put(POINT_LIST, points);
        CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.HOVER).withBaseSpeed(5.0));
    }

    @SuppressWarnings("unchecked")
    private static List<Vec3> points(Map<String, Object> state) {
        return (List<Vec3>) state.get(POINT_LIST);
    }

    private static double[] closest(Map<String, Object> state) {
        return (double[]) state.computeIfAbsent(CLOSEST, key -> {
            double[] out = new double[POINTS.length];
            java.util.Arrays.fill(out, Double.MAX_VALUE);
            return out;
        });
    }

    private static void track(Map<String, Object> state, Entity drone) {
        double[] closest = closest(state);
        List<Vec3> points = points(state);
        for (int i = 0; i < points.size(); i++) {
            closest[i] = Math.min(closest[i], drone.position().distanceTo(points.get(i)));
        }
    }

    private static StepResult serverProgress(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        track(state, drone.get());
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        if (status.isPresent() && status.get().state() == NavLogic.MissionState.RUNNING && status.get().index() >= 2 && closest(state)[1] <= REACHED) {
            return StepResult.pass("Server fliegt die Mission ohne Pilot bis Punkt " + (status.get().index() + 1) + ", Meldungen "
                    + StatusProbe.describe(CamFlights.mark(ctx)));
        }
        return ticks > 20 * 60 ? StepResult.fail("Server fliegt nicht bis Punkt 3: " + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("?"))
                : StepResult.pending();
    }

    private static StepResult paused(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        Pilot.drone().ifPresent(d -> track(state, d));
        int mark = CamFlights.mark(ctx);
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(state);
        boolean ended = StatusProbe.seen(mark, AutonomyStatus.ENDED_PILOT);
        boolean pausedState = status.map(s -> s.state() == NavLogic.MissionState.PAUSED).orElse(false);
        boolean pilotIdle = Pilot.status().map(s -> s.missionState() != NavLogic.MissionState.RUNNING).orElse(false);
        if (ended && pausedState && pilotIdle) {
            state.put(PAUSED_AT, status.get().index());
            return StepResult.pass("Pilot übernimmt: Meldungen " + StatusProbe.describe(mark) + ", Mission pausiert bei Punkt " + (status.get().index() + 1)
                    + ", Flughilfe " + Flight.describe());
        }
        return ticks > 200 ? StepResult.fail("keine Übernahme mit Pause: Meldungen " + StatusProbe.describe(mark) + ", Mission "
                + status.map(s -> s.state() + " " + (s.index() + 1)).orElse("?") + ", Flughilfe " + Flight.describe()) : StepResult.pending();
    }

    private static StepResult serverIdle(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        Object pending = state.get(SERVER);
        if (pending == null) {
            CompletableFuture<Optional<Identifier>> future = new CompletableFuture<>();
            ctx.onServer(server -> future.complete(PropwashApi.drones().autonomy().active(Pilot.serverDrone(server, state))))
                    .whenComplete((v, t) -> {
                        if (t != null) {
                            future.completeExceptionally(t);
                        }
                    });
            state.put(SERVER, future);
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Optional<Identifier>> future = (CompletableFuture<Optional<Identifier>>) pending;
        if (!future.isDone()) {
            return ticks > 200 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
        }
        state.remove(SERVER);
        Optional<Identifier> active = future.isCompletedExceptionally() ? Optional.empty() : future.join();
        if (active.isPresent()) {
            return ticks > 100 ? StepResult.fail("Flug ohne Pilot läuft nach der Übernahme weiter: " + active.get()) : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult holds(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<Vec3> now = Flight.position();
        if (now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Pilot.drone().ifPresent(d -> track(ctx.state(), d));
        if (Pilot.status().map(s -> s.missionState() == NavLogic.MissionState.RUNNING).orElse(false)) {
            return StepResult.fail("Mission läuft nach der Übernahme ohne Befehl weiter: " + Flight.describe());
        }
        if (Flight.height() < MIN_HOLD_HEIGHT || !Pilot.armed()) {
            return StepResult.fail("nach der Übernahme nicht in der Luft: Höhe " + Flight.format(Flight.height()) + " m, scharf " + Pilot.armed());
        }
        if (!ctx.state().containsKey(ANCHOR)) {
            boolean fix = Pilot.status().map(s -> !s.atti()).orElse(false);
            StepResult calm = fix && ticks >= BRAKE_TICKS ? Flight.calm(ctx, ticks, 20 * 30) : StepResult.pending();
            if (!calm.done()) {
                return ticks > 20 * 40 ? StepResult.fail("nach der Übernahme kein GPS-Halten: " + Flight.describe()) : calm;
            }
            if (calm.failed()) {
                return calm;
            }
            ctx.state().put(HOLD_SINCE, ticks);
            ctx.log("nach der Übernahme GPS-Halten nach " + Flight.format(ticks / 20.0) + " s");
        }
        Vec3 anchor = (Vec3) ctx.state().computeIfAbsent(ANCHOR, key -> now.get());
        int held = ticks - (int) ctx.state().get(HOLD_SINCE);
        double horizontal = Math.hypot(now.get().x - anchor.x, now.get().z - anchor.z);
        double vertical = Math.abs(now.get().y - anchor.y);
        double[] worst = (double[]) ctx.state().computeIfAbsent(WORST, key -> new double[2]);
        worst[0] = Math.max(worst[0], horizontal);
        worst[1] = Math.max(worst[1], vertical);
        if (worst[0] > HOLD_LIMIT || worst[1] > HOLD_HEIGHT_LIMIT) {
            return StepResult.fail("hält nach der Übernahme nicht: " + Flight.format(worst[0]) + " m seitlich, " + Flight.format(worst[1])
                    + " m in der Höhe in " + Flight.format(held / 20.0) + " s");
        }
        if (held < HOLD_TICKS) {
            return StepResult.pending();
        }
        return StepResult.pass("Pilot hält mit losgelassenen Knüppeln " + Flight.format(HOLD_TICKS / 20.0) + " s auf " + Flight.format(worst[0])
                + " m (Höhe " + Flight.format(worst[1]) + " m), Mission bleibt pausiert");
    }

    private static StepResult resumed(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        track(state, drone.get());
        int last = POINTS.length - 1;
        boolean running = Pilot.status().map(s -> s.function() == NavLogic.Function.MISSION).orElse(false);
        boolean done = Pilot.status().map(s -> s.missionState() == NavLogic.MissionState.DONE).orElse(false);
        if (done || closest(state)[last] <= REACHED) {
            int pausedAt = (int) state.get(PAUSED_AT);
            if (closest(state)[last] > ARRIVED) {
                return StepResult.fail("Mission endet " + Flight.format(closest(state)[last]) + " m vor dem letzten Punkt");
            }
            return StepResult.pass("Mission per Taste fortgesetzt ab Punkt " + (pausedAt + 1) + ", letzter Punkt auf " + Flight.format(closest(state)[last])
                    + " m erreicht");
        }
        if (ticks > 40 && !running) {
            return StepResult.fail("Mission läuft nach dem Fortsetzen nicht: " + Flight.describe());
        }
        return ticks > 20 * 90 ? StepResult.fail("letzter Punkt nicht erreicht (" + Flight.format(closest(state)[last]) + " m)") : StepResult.pending();
    }
}
