package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.PilotView;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CameraPose;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.Locale;
import java.util.Optional;

public final class PilotViewSteps {
    public static final String CINE = "gimbal: Kino – Pilot fliegt mit fester FPV-Kamera, Gimbal-Bild per Taste";
    public static final String TOUR = "gimbal: Tour – Pilot fliegt mit fester FPV-Kamera, Gimbal-Bild per Taste";
    public static final String COMPACT = "gimbal: Kompakt – Pilot fliegt mit fester FPV-Kamera, Gimbal-Bild per Taste";
    public static final int CINE_NUMBER = 41;
    public static final int TOUR_NUMBER = 42;
    public static final int COMPACT_NUMBER = 43;
    public static final String CONTROL = "pilot_view";
    public static final double TILT_TARGET = -60.0;
    public static final double TILT_REACHED = -55.0;
    public static final double FPV_LIMIT = 5.0;
    public static final double GIMBAL_LIMIT = 8.0;
    public static final int STABLE_TICKS = 10;
    public static final int PRESS_TICKS = 4;
    private static final String STABLE = "pilotview.stable";
    private static final String WORST = "pilotview.worst";
    private static final int STEP_TICKS = 20 * 150;

    private PilotViewSteps() {
    }

    public static void register() {
        SelfTest.clientStep(CINE_NUMBER, CINE, STEP_TICKS, script(CamPresets.CINE, 100), PilotViewSteps::cleanup);
        SelfTest.clientStep(TOUR_NUMBER, TOUR, STEP_TICKS, script(CamPresets.TOUR, 112), PilotViewSteps::cleanup);
        SelfTest.clientStep(COMPACT_NUMBER, COMPACT, STEP_TICKS, script(CamPresets.COMPACT, 124), PilotViewSteps::cleanup);
    }

    private static Script script(CamPresets.Entry entry, int dx) {
        return Script.create()
                .append(Pilot.launch(entry, dx, 40, 0.0F))
                .append(Flight.takeoff(6.0))
                .then("Pilotenbild nach dem Start: FPV-Kamera", (ctx, ticks) -> fpv(ctx, ticks, "nach dem Start"))
                .then("Gimbal per Achse auf −60°", PilotViewSteps::tiltDown)
                .then("Pilotenbild bleibt die FPV-Kamera", (ctx, ticks) -> fpv(ctx, ticks, "mit Gimbal unten"))
                .then("Taste Pilotenbild: Gimbal-Kamera", (ctx, ticks) -> press(ctx, ticks, true))
                .then("Taste Pilotenbild: zurück zur FPV-Kamera", (ctx, ticks) -> press(ctx, ticks, false))
                .then("Gimbal zurück", PilotViewSteps::tiltBack);
    }

    public static Script gimbalImage() {
        return Script.create().then("Pilotenbild auf die Gimbal-Kamera (Taste Pilotenbild)", PilotViewSteps::toGimbal);
    }

    public static Script fpvImage(String when) {
        return Script.create().then("Pilotenbild " + when + ": FPV-Kamera", (ctx, ticks) -> fpv(ctx, ticks, when));
    }

    private static StepResult toGimbal(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = Pilot.drone();
        Optional<KeyMapping> key = viewKey();
        if (drone.isEmpty() || key.isEmpty()) {
            return StepResult.fail(drone.isEmpty() ? "Drohne verloren" : "keine Taste für das Pilotenbild");
        }
        if (GimbalClient.provider().image(drone.get(), false) == PilotView.Image.GIMBAL) {
            Pilot.hold(key.get(), false);
            return StepResult.pass();
        }
        Pilot.hold(key.get(), ticks % 20 < PRESS_TICKS);
        return ticks > 120 ? StepResult.fail("Taste Pilotenbild schaltet nicht auf die Gimbal-Kamera") : StepResult.pending();
    }

    static Optional<KeyMapping> viewKey() {
        return CamControl.byPath(CONTROL).flatMap(CamKeys::key);
    }

    static double fpvOffset(Entity drone) {
        Optional<CameraPose> pose = PropwashApi.drones().cameraPose(drone);
        if (pose.isEmpty()) {
            return Double.NaN;
        }
        Vector3fc forward = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
        Vec3 look = pose.get().look().normalize();
        double dot = Math.clamp(forward.x() * look.x + forward.y() * look.y + forward.z() * look.z, -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    static double fpvDown(Entity drone) {
        return PropwashApi.drones().cameraPose(drone).map(pose -> Math.toDegrees(Math.asin(Math.clamp(-pose.look().normalize().y, -1.0, 1.0))))
                .orElse(Double.NaN);
    }

    private static String describe(Entity drone) {
        return String.format(Locale.ROOT, "Bild blickt %.1f° nach unten, FPV-Kamera %.1f°, Abstand zur FPV-Kamera %.1f°, Gimbal %.1f°",
                GimbalSteps.cameraDown(), fpvDown(drone), fpvOffset(drone), GimbalClient.tilt(drone));
    }

    private static StepResult fpv(SelfTest.StepContext ctx, int ticks, String when) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double offset = fpvOffset(drone.get());
        if (Double.isNaN(offset)) {
            return StepResult.fail("Propwash liefert keine FPV-Kamera der Drohne");
        }
        ctx.state().put(WORST, describe(drone.get()));
        int stable = offset < FPV_LIMIT ? (int) ctx.state().getOrDefault(STABLE, 0) + 1 : 0;
        ctx.state().put(STABLE, stable);
        if (stable >= STABLE_TICKS) {
            ctx.state().remove(STABLE);
            return StepResult.pass(when + ": " + describe(drone.get()));
        }
        return ticks > 80 ? StepResult.fail("Pilotenbild folgt nicht der FPV-Kamera am Rahmen (" + when + "): " + ctx.state().get(WORST)
                + ", Grenze " + Flight.format(FPV_LIMIT) + "°") : StepResult.pending();
    }

    private static KeyMapping tiltKey(boolean up) {
        return (up ? CamKeys.increase(CamControl.GIMBAL_TILT) : CamKeys.decrease(CamControl.GIMBAL_TILT))
                .orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Neigung fehlt"));
    }

    private static StepResult tiltDown(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double tilt = GimbalClient.tilt(drone.get());
        if (tilt <= TILT_TARGET) {
            Pilot.hold(tiltKey(false), false);
            return ticks >= 20 ? StepResult.pass(String.format(Locale.ROOT, "Gimbal %.1f° nach %.1f s", tilt, ticks / 20.0)) : StepResult.pending();
        }
        Pilot.hold(tiltKey(false), true);
        return ticks > 200 ? StepResult.fail("Neigung bleibt bei " + Flight.format(tilt) + "°") : StepResult.pending();
    }

    private static StepResult press(SelfTest.StepContext ctx, int ticks, boolean gimbal) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Optional<KeyMapping> key = viewKey();
        if (key.isEmpty()) {
            return StepResult.fail("keine Taste für das Pilotenbild (Schalter camdrones:" + CONTROL + " fehlt)");
        }
        if (ticks < PRESS_TICKS) {
            Pilot.hold(key.get(), true);
            return StepResult.pending();
        }
        Pilot.hold(key.get(), false);
        if (ticks < PRESS_TICKS + 10) {
            return StepResult.pending();
        }
        double tilt = GimbalClient.tilt(drone.get());
        double down = GimbalSteps.cameraDown();
        double offset = fpvOffset(drone.get());
        boolean ok;
        if (gimbal) {
            ok = tilt <= TILT_REACHED && Math.abs(down + tilt) < GIMBAL_LIMIT && offset > 30.0;
        } else {
            ok = offset < FPV_LIMIT;
        }
        int stable = ok ? (int) ctx.state().getOrDefault(STABLE, 0) + 1 : 0;
        ctx.state().put(STABLE, stable);
        ctx.state().put(WORST, describe(drone.get()));
        if (stable >= STABLE_TICKS) {
            ctx.state().remove(STABLE);
            return StepResult.pass((gimbal ? "Gimbal-Bild: " : "wieder FPV: ") + describe(drone.get()) + ", Taste "
                    + key.get().getTranslatedKeyMessage().getString());
        }
        return ticks > 100 ? StepResult.fail((gimbal ? "Taste schaltet nicht auf das Gimbal-Bild: " : "Taste schaltet nicht zurück auf die FPV-Kamera: ")
                + ctx.state().get(WORST)) : StepResult.pending();
    }

    private static StepResult tiltBack(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        boolean centered = Math.abs(axis) < 0.02;
        Pilot.hold(tiltKey(true), !centered && axis < 0.0);
        Pilot.hold(tiltKey(false), !centered && axis > 0.0);
        if (centered) {
            Pilot.hold(tiltKey(true), false);
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("Achse kommt nicht zurück: " + Flight.format(axis)) : StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        viewKey().ifPresent(key -> Pilot.hold(key, false));
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (Math.abs(axis) < 0.02 || !PropwashClientApi.input().flying()) {
            Pilot.hold(tiltKey(true), false);
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass();
        }
        Pilot.hold(tiltKey(true), axis < 0.0);
        Pilot.hold(tiltKey(false), axis > 0.0);
        return ctx.tick() > 300 ? StepResult.fail("Gimbal-Achse bleibt bei " + Flight.format(axis)) : StepResult.pending();
    }
}
