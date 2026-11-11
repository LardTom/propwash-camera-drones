package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;

public final class Flight {
    public static final double CLIMB_THROTTLE = 0.72;
    public static final double LEVEL_OFF = 0.7;
    public static final double STOPPED_SPEED = 0.25;
    private static final String TRACK = "flight.track";
    private static final String CALM = "flight.calm";
    private static final int TRACK_TICKS = 60;

    private Flight() {
    }

    public static String format(double value) {
        return String.format(Locale.GERMANY, "%.2f", value);
    }

    public static double surface() {
        return Arena.ground().getY() + 1.0;
    }

    public static Optional<Vec3> position() {
        return Pilot.drone().map(Entity::position);
    }

    public static double height() {
        return position().map(p -> p.y - surface()).orElse(Double.NaN);
    }

    @SuppressWarnings("unchecked")
    public static void track(SelfTest.StepContext ctx) {
        Deque<Vec3> track = (Deque<Vec3>) ctx.state().computeIfAbsent(TRACK, key -> new ArrayDeque<Vec3>());
        position().ifPresent(p -> {
            track.addLast(p);
            while (track.size() > TRACK_TICKS) {
                track.removeFirst();
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static double horizontalSpeed(SelfTest.StepContext ctx, int ticks) {
        Deque<Vec3> track = (Deque<Vec3>) ctx.state().get(TRACK);
        if (track == null || track.size() <= ticks) {
            return Double.NaN;
        }
        Vec3 now = track.peekLast();
        Vec3 then = track.stream().skip(track.size() - 1L - ticks).findFirst().orElse(now);
        double dx = now.x - then.x;
        double dz = now.z - then.z;
        return Math.sqrt(dx * dx + dz * dz) * 20.0 / ticks;
    }

    @SuppressWarnings("unchecked")
    public static double verticalSpeed(SelfTest.StepContext ctx, int ticks) {
        Deque<Vec3> track = (Deque<Vec3>) ctx.state().get(TRACK);
        if (track == null || track.size() <= ticks) {
            return Double.NaN;
        }
        Vec3 now = track.peekLast();
        Vec3 then = track.stream().skip(track.size() - 1L - ticks).findFirst().orElse(now);
        return (now.y - then.y) * 20.0 / ticks;
    }

    public static Script gpsFix() {
        return Script.create().then("GPS-Fix abwarten", (ctx, ticks) -> {
            track(ctx);
            Optional<NavStatus> status = Pilot.status();
            if (status.isPresent() && !status.get().atti() && ticks >= 10) {
                return StepResult.pass();
            }
            return ticks > 600 ? StepResult.fail("kein GPS-Fix nach 30 s: " + describe()) : StepResult.pending();
        });
    }

    public static String describe() {
        return Pilot.status().map(s -> s.label() + ", " + s.function() + (s.atti() ? ", ATTI" : "") + ", Warnungen " + s.warnings())
                .orElse("kein Status der Flughilfe");
    }

    public static Script takeoff(double metres) {
        return gpsFix().append(climb(metres));
    }

    public static Script climb(double metres) {
        return Script.create()
                .then("steigen auf " + format(metres) + " m", (ctx, ticks) -> {
                    track(ctx);
                    double height = height();
                    if (Double.isNaN(height)) {
                        return StepResult.fail("keine Drohne am Client");
                    }
                    if (height >= metres - LEVEL_OFF) {
                        return StepResult.pass();
                    }
                    Pilot.steer(CLIMB_THROTTLE);
                    return ticks > 400 ? StepResult.fail("steigt nicht, Höhe " + format(height) + " m, Gas "
                            + format(PropwashClientApi.input().throttle())) : StepResult.pending();
                })
                .then("Gas in die Mitte", (ctx, ticks) -> {
                    track(ctx);
                    return Pilot.throttleTo(ctx, ticks, Pilot.THROTTLE_CENTER);
                })
                .then("Höhe einpendeln", (ctx, ticks) -> {
                    track(ctx);
                    return calm(ctx, ticks, 300);
                });
    }

    public static StepResult calm(SelfTest.StepContext ctx, int ticks, int maxTicks) {
        double horizontal = horizontalSpeed(ctx, 10);
        double vertical = verticalSpeed(ctx, 10);
        boolean still = Double.isFinite(horizontal) && horizontal < STOPPED_SPEED && Math.abs(vertical) < STOPPED_SPEED;
        int calm = still ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calm);
        if (calm >= 10) {
            ctx.state().put(CALM, 0);
            return StepResult.pass();
        }
        return ticks > maxTicks ? StepResult.fail("kommt nicht zur Ruhe (waagrecht " + format(horizontal) + " m/s, senkrecht "
                + format(vertical) + " m/s, " + describe() + ")") : StepResult.pending();
    }

    public static Script mode(NavLogic.StickMode wanted) {
        return Script.create()
                .then("Modus-Taste drücken", (ctx, ticks) -> {
                    track(ctx);
                    Pilot.hold(CamControl.MODE_CYCLE, ticks < 4);
                    return ticks >= 6 ? StepResult.pass() : StepResult.pending();
                })
                .then("Modus " + wanted, (ctx, ticks) -> {
                    track(ctx);
                    Optional<NavStatus> status = Pilot.status();
                    if (status.isPresent() && status.get().stickMode() == wanted) {
                        return StepResult.pass();
                    }
                    return ticks > 60 ? StepResult.fail("Modus bleibt " + status.map(s -> s.stickMode() + " (" + s.label() + ")").orElse("unbekannt"))
                            : StepResult.pending();
                });
    }

    public static StepResult brake(SelfTest.StepContext ctx, int ticks) {
        track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, false);
        return calm(ctx, ticks, 240);
    }

    public static String stick() {
        return format(PropwashClientApi.input().pitch());
    }
}
