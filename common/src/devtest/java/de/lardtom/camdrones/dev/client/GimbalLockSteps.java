package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamSwitches;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class GimbalLockSteps {
    public static final String TOUR = "gimbal: Sperre hält die Richtung beim Gieren, Folgen dreht mit (Tour)";
    public static final String CINE = "gimbal: Sperre hält die Richtung beim Gieren, Folgen dreht mit (Kino)";
    public static final double TOUR_TURN = 8.0;
    public static final double CINE_TURN = 80.0;
    public static final double LOCK_TOLERANCE = 3.0;
    public static final double FOLLOW_TOLERANCE = 3.0;
    public static final int SETTLE_TICKS = 30;
    private static final String TEST_KEY = "key.keyboard.end";
    private static final String LOCKED_AT = "lock.heading";
    private static final String FRAME_AT = "lock.frame";
    private static final int STEP_TICKS = 20 * 150;
    private static @Nullable String saved;

    private GimbalLockSteps() {
    }

    public static void register() {
        SelfTest.clientStep(38, TOUR, STEP_TICKS, script(CamPresets.TOUR, -76, 40, TOUR_TURN, GimbalLimits.LIMITED_YAW), GimbalLockSteps::cleanup);
        SelfTest.clientStep(36, CINE, STEP_TICKS, script(CamPresets.CINE, -88, 40, CINE_TURN, GimbalLimits.PAN_RANGE), GimbalLockSteps::cleanup);
    }

    private static Script script(CamPresets.Entry entry, int dx, int dz, double turn, double yawLimit) {
        return Script.create()
                .append(Pilot.launch(entry, dx, dz, 0.0F))
                .append(PilotViewSteps.gimbalImage())
                .append(Flight.takeoff(6.0))
                .run("Taste für die Gimbal-Sperre belegen (ohne Standard)", () -> bind(true))
                .then("Ausgangslage Folgen", (ctx, ticks) -> toggle(ctx, ticks, false))
                .then("Folgen: Kamera blickt mit der Nase", (ctx, ticks) -> follows(ctx, ticks, "vor der Sperre"))
                .then("Sperre einschalten", (ctx, ticks) -> toggle(ctx, ticks, true))
                .then("gieren um " + Flight.format(turn) + "°", (ctx, ticks) -> yawLocked(ctx, ticks, turn))
                .then("Sperre hält nach dem Gieren", (ctx, ticks) -> lockHeld(ctx, ticks, yawLimit))
                .then("Sperre ausschalten", (ctx, ticks) -> toggle(ctx, ticks, false))
                .then("Folgen: Kamera dreht zur Nase zurück", (ctx, ticks) -> follows(ctx, ticks, "nach der Sperre"));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Pilot.hold(Minecraft.getInstance().options.keyRight, false);
        boolean locked = Pilot.drone().map(d -> CamSwitches.active(d, CamControl.GIMBAL_LOCK)).orElse(false);
        if (locked && saved != null && ctx.tick() < 200) {
            if (ctx.tick() % 40 == 0) {
                KeyMapping key = CamKeys.key(CamControl.GIMBAL_LOCK).orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Sperre fehlt"));
                KeyMapping.click(InputConstants.getKey(key.saveString()));
            }
            return StepResult.pending();
        }
        bind(false);
        return locked ? StepResult.fail("Gimbal-Sperre bleibt an") : StepResult.pass();
    }

    private static void bind(boolean on) {
        KeyMapping key = CamKeys.key(CamControl.GIMBAL_LOCK).orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Sperre fehlt"));
        if (on && saved == null) {
            saved = key.saveString();
            key.setKey(InputConstants.getKey(TEST_KEY));
        } else if (!on && saved != null) {
            key.setKey(InputConstants.getKey(saved));
            saved = null;
        }
        KeyMapping.resetMapping();
    }

    static double cameraHeading() {
        Vector3fc forward = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
        return Compass.headingTo(forward.x(), forward.z());
    }

    static Optional<Double> frameHeading() {
        return Pilot.drone().flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::forward).map(f -> Compass.headingTo(f.x, f.z));
    }

    private static GimbalSetting.Mode mode() {
        return GimbalClient.control().setting().mode();
    }

    private static StepResult follows(SelfTest.StepContext ctx, int ticks, String when) {
        Flight.track(ctx);
        Optional<Double> frame = frameHeading();
        if (frame.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double error = Math.abs(Compass.wrap180(cameraHeading() - frame.get()));
        if (ticks >= SETTLE_TICKS && mode() == GimbalSetting.Mode.FOLLOW && error <= FOLLOW_TOLERANCE) {
            return StepResult.pass("Folgen " + when + ": Kamera " + Flight.format(error) + "° neben der Nase");
        }
        return ticks > 20 * 8 ? StepResult.fail("Folgen " + when + ": Kamera " + Flight.format(error) + "° neben der Nase (Grenze "
                + Flight.format(FOLLOW_TOLERANCE) + "°), Gimbal " + mode()) : StepResult.pending();
    }

    private static StepResult toggle(SelfTest.StepContext ctx, int ticks, boolean on) {
        Flight.track(ctx);
        GimbalSetting.Mode wanted = on ? GimbalSetting.Mode.LOCK : GimbalSetting.Mode.FOLLOW;
        if (ticks % 40 == 0 && mode() != wanted) {
            KeyMapping key = CamKeys.key(CamControl.GIMBAL_LOCK).orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Sperre fehlt"));
            KeyMapping.click(InputConstants.getKey(key.saveString()));
        }
        if (mode() == wanted && ticks >= 10) {
            if (on) {
                ctx.state().put(LOCKED_AT, cameraHeading());
                ctx.state().put(FRAME_AT, frameHeading().orElse(0.0));
                return StepResult.pass("Sperre per Schalter an, Richtung " + Flight.format(cameraHeading()) + "°");
            }
            return StepResult.pass("Sperre per Schalter aus");
        }
        return ticks > 130 ? StepResult.fail("Gimbal-Sperre schaltet nicht " + (on ? "ein" : "aus") + ", Gimbal " + mode()) : StepResult.pending();
    }

    private static StepResult yawLocked(SelfTest.StepContext ctx, int ticks, double turn) {
        Flight.track(ctx);
        Optional<Double> frame = frameHeading();
        if (frame.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double turned = Math.abs(Compass.wrap180(frame.get() - (double) ctx.state().get(FRAME_AT)));
        if (mode() != GimbalSetting.Mode.LOCK) {
            return StepResult.fail("Sperre fällt beim Gieren aus, Gimbal " + mode());
        }
        boolean done = turned >= turn;
        Pilot.hold(Minecraft.getInstance().options.keyRight, !done);
        if (done) {
            return StepResult.pass();
        }
        return ticks > 20 * 10 ? StepResult.fail("Nase dreht nur " + Flight.format(turned) + "° statt " + Flight.format(turn) + "°") : StepResult.pending();
    }

    private static StepResult lockHeld(SelfTest.StepContext ctx, int ticks, double yawLimit) {
        Flight.track(ctx);
        Pilot.hold(Minecraft.getInstance().options.keyRight, false);
        Optional<Double> frame = frameHeading();
        if (frame.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (mode() != GimbalSetting.Mode.LOCK) {
            return StepResult.fail("Sperre fällt nach dem Gieren aus, Gimbal " + mode());
        }
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        double locked = (double) ctx.state().get(LOCKED_AT);
        double turned = Math.abs(Compass.wrap180(frame.get() - (double) ctx.state().get(FRAME_AT)));
        double error = Math.abs(Compass.wrap180(cameraHeading() - locked));
        double offNose = Math.abs(Compass.wrap180(cameraHeading() - frame.get()));
        double expected = Math.max(0.0, turned - yawLimit);
        String measured = "Nase dreht " + Flight.format(turned) + "°, Kamera " + Flight.format(error) + "° neben der gesperrten Richtung und "
                + Flight.format(offNose) + "° neben der Nase (Schwenkgrenze " + Flight.format(yawLimit) + "°)";
        if (Math.abs(error - expected) > LOCK_TOLERANCE) {
            return ticks > SETTLE_TICKS + 40 ? StepResult.fail(measured + ", erwartet " + Flight.format(expected) + " ± "
                    + Flight.format(LOCK_TOLERANCE) + "°") : StepResult.pending();
        }
        return StepResult.pass(measured);
    }
}
