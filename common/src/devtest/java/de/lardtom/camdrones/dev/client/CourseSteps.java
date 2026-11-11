package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class CourseSteps {
    public static final String NAME = "course-lock: Richtung fest beim Gieren";
    public static final double COURSE_TOLERANCE = 12.0;
    public static final double MIN_TURN = 60.0;
    public static final int FLY_TICKS = 100;
    private static final String COURSE = "course.course";
    private static final String HEADING = "course.heading";
    private static final String WORST = "course.worst";
    private static final String TURN = "course.turn";
    private static final String SAMPLES = "course.samples";
    private static final String TRACK = "course.track";
    private static final String TEST_KEY = "key.keyboard.home";
    private static @Nullable String saved;

    private CourseSteps() {
    }

    public static void register() {
        SelfTest.clientStep(24, NAME, 20 * 150, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, 80, -40, 0.0F))
                .append(Flight.takeoff(4.0))
                .run("Taste für Course Lock belegen (ohne Standard)", () -> bind(true))
                .then("Course Lock einschalten", (ctx, ticks) -> toggle(ctx, ticks, true))
                .then("vorwärts und gieren", CourseSteps::fly)
                .then("loslassen", (ctx, ticks) -> {
                    Pilot.hold(Pilot.PITCH_FORWARD, false);
                    Pilot.hold(Minecraft.getInstance().options.keyRight, false);
                    return Flight.brake(ctx, ticks);
                })
                .then("Course Lock ausschalten", (ctx, ticks) -> toggle(ctx, ticks, false)), ctx -> {
                    bind(false);
                    return StepResult.pass();
                });
    }

    private static void bind(boolean on) {
        KeyMapping key = CamKeys.key(CamControl.COURSE_LOCK).orElseThrow(() -> new IllegalStateException("Taste für Course Lock fehlt"));
        if (on && saved == null) {
            saved = key.saveString();
            key.setKey(InputConstants.getKey(TEST_KEY));
        } else if (!on && saved != null) {
            key.setKey(InputConstants.getKey(saved));
            saved = null;
        }
        KeyMapping.resetMapping();
    }

    private static boolean locked() {
        return Pilot.status().map(NavStatus::function).filter(f -> f == NavLogic.Function.COURSE_LOCK).isPresent();
    }

    private static StepResult toggle(SelfTest.StepContext ctx, int ticks, boolean on) {
        Flight.track(ctx);
        if (ticks % 40 == 0 && locked() != on) {
            KeyMapping key = CamKeys.key(CamControl.COURSE_LOCK).orElseThrow(() -> new IllegalStateException("Taste für Course Lock fehlt"));
            KeyMapping.click(InputConstants.getKey(key.saveString()));
        }
        if (locked() == on && ticks >= 4) {
            if (on) {
                Optional<Entity> drone = Pilot.drone();
                double heading = drone.flatMap(d -> PropwashApi.drones().view(d)).map(v -> Compass.headingTo(v.forward().x, v.forward().z)).orElse(0.0);
                ctx.state().put(COURSE, heading);
                ctx.state().put(HEADING, heading);
                return StepResult.pass("Course Lock per Schalter an, Kurs " + Flight.format(heading) + "°");
            }
            return StepResult.pass("Course Lock per Schalter aus, " + Flight.describe());
        }
        return ticks > 130 ? StepResult.fail("Course Lock schaltet nicht " + (on ? "ein" : "aus") + ": " + Flight.describe()) : StepResult.pending();
    }

    private static StepResult fly(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        Pilot.hold(Minecraft.getInstance().options.keyRight, true);
        Optional<Entity> drone = Pilot.drone();
        Optional<DroneView> view = drone.flatMap(d -> PropwashApi.drones().view(d));
        if (view.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (!locked()) {
            return StepResult.fail("Course Lock fällt beim Fliegen aus: " + Flight.describe());
        }
        double course = (double) ctx.state().get(COURSE);
        Vec3 forward = view.get().forward();
        double heading = Compass.headingTo(forward.x, forward.z);
        double previous = (double) ctx.state().get(HEADING);
        ctx.state().put(TURN, (double) ctx.state().getOrDefault(TURN, 0.0) + Math.abs(Compass.wrap180(heading - previous)));
        ctx.state().put(HEADING, heading);
        Vec3 now = drone.get().position();
        @SuppressWarnings("unchecked")
        Deque<Vec3> track = (Deque<Vec3>) ctx.state().computeIfAbsent(TRACK, key -> new ArrayDeque<Vec3>());
        track.addLast(now);
        while (track.size() > 11) {
            track.removeFirst();
        }
        Vec3 then = track.peekFirst();
        double dx = now.x - then.x;
        double dz = now.z - then.z;
        double speed = Math.hypot(dx, dz) * 20.0 / Math.max(1, track.size() - 1);
        if (ticks >= 30 && speed > 1.0) {
            double direction = Compass.headingTo(dx, dz);
            double error = Math.abs(Compass.wrap180(direction - course));
            ctx.state().put(WORST, Math.max((double) ctx.state().getOrDefault(WORST, 0.0), error));
            ctx.state().put(SAMPLES, (int) ctx.state().getOrDefault(SAMPLES, 0) + 1);
        }
        if (ticks < FLY_TICKS) {
            return StepResult.pending();
        }
        double worst = (double) ctx.state().getOrDefault(WORST, Double.NaN);
        double turned = (double) ctx.state().get(TURN);
        int samples = (int) ctx.state().getOrDefault(SAMPLES, 0);
        if (samples < 20) {
            return StepResult.fail("fliegt mit Course Lock nicht vorwärts (" + samples + " Messungen über 1 m/s)");
        }
        if (turned < MIN_TURN) {
            return StepResult.fail("Nase dreht nur " + Flight.format(turned) + "° beim Gieren");
        }
        if (!(worst <= COURSE_TOLERANCE)) {
            return StepResult.fail("Flugrichtung weicht " + Flight.format(worst) + "° vom gespeicherten Kurs ab (Grenze " + Flight.format(COURSE_TOLERANCE)
                    + "°)");
        }
        return StepResult.pass("Nase dreht " + Flight.format(turned) + "°, Flugrichtung bleibt auf " + Flight.format(worst) + "° am Kurs "
                + Flight.format(course) + "°");
    }
}
