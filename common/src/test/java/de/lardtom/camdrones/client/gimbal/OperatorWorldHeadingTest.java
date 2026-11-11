package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.propwash.api.flight.GimbalModel;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorWorldHeadingTest {
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);
    private static final GimbalLimits COMPACT = GimbalLimits.of(-90.0, 30.0, false);
    private static final double FPS = 60.0;
    private static final double YAW_RATE = 90.0;
    private static final double SETTLE_SECONDS = 3.0;
    private static final double TOLERANCE = 1.0;
    private static final boolean[] NONE = new boolean[OperatorGimbalInput.ACTIONS];

    private static final class Crew {
        private final GimbalLimits limits;
        private final OperatorGimbalInput operator = new OperatorGimbalInput();
        private final GimbalModel camera;
        private double frameHeading;
        private long millis;

        Crew(GimbalLimits limits, GimbalSetting stored, double frameHeading) {
            this.limits = limits;
            this.camera = GimbalPose.create(limits);
            this.frameHeading = frameHeading;
            this.operator.takeOver(stored, NONE);
        }

        void run(double seconds, double panStick, double yawRate) {
            int frames = (int) Math.round(seconds * FPS);
            double dt = 1.0 / FPS;
            for (int i = 0; i < frames; i++) {
                this.frameHeading = Compass.normalize(this.frameHeading + yawRate * dt);
                this.millis += Math.round(dt * 1000.0);
                this.operator.update(this.limits, new double[]{panStick, 0.0, 0.0, 0.0}, NONE, dt, this.millis, this.frameHeading);
                GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalResolver.Scene.free(this.limits, 0.0, 0.0, 0.0, this.frameHeading),
                        this.operator.setting());
                GimbalPose.aim(this.camera, aim, this.frameHeading);
                GimbalPose.step(this.camera, dt, yawed(this.frameHeading));
            }
        }

        void settle() {
            this.run(SETTLE_SECONDS, 0.0, 0.0);
        }

        void yaw(double degrees) {
            this.run(Math.abs(degrees) / YAW_RATE, 0.0, Math.copySign(YAW_RATE, degrees));
            this.settle();
        }

        void pan(double stick, double seconds) {
            this.run(seconds, stick, 0.0);
            this.settle();
        }

        double camera() {
            return this.camera.headingDeg();
        }

        double frame() {
            return this.frameHeading;
        }

        boolean stop() {
            return this.limits.atStop(this.operator.setting().heading(), this.frameHeading);
        }
    }

    private static Quaternionf yawed(double heading) {
        return new Quaternionf().rotateY((float) Math.toRadians(-heading));
    }

    private static void assertHeading(double expected, double actual, String message) {
        assertEquals(0.0, Compass.wrap180(actual - expected), TOLERANCE, message + ": Kamera " + actual + "° statt " + expected + "°");
    }

    @Test
    void cineKeepsTheWorldHeadingWhenThePilotYawsNinetyAndOneEightyDegrees() {
        Crew crew = new Crew(CINE, GimbalSetting.CENTER, 0.0);
        crew.settle();
        assertHeading(0.0, crew.camera(), "Übernahme");
        crew.yaw(90.0);
        assertHeading(90.0, crew.frame(), "Rahmen");
        assertHeading(0.0, crew.camera(), "Pilot giert 90°");
        crew.yaw(90.0);
        assertHeading(180.0, crew.frame(), "Rahmen");
        assertHeading(0.0, crew.camera(), "Pilot giert 180°");
        crew.yaw(-270.0);
        assertHeading(0.0, crew.camera(), "Pilot giert 270° zurück");
    }

    @Test
    void cineOperatorPansEastAndTheViewStaysEastWhileThePilotTurns() {
        Crew crew = new Crew(CINE, GimbalSetting.CENTER, 0.0);
        crew.settle();
        crew.pan(1.0, 1.0);
        assertHeading(90.0, crew.camera(), "Kameramann schwenkt nach Osten");
        crew.yaw(90.0);
        assertHeading(90.0, crew.camera(), "Pilot dreht 90°");
        crew.yaw(-180.0);
        assertHeading(90.0, crew.camera(), "Pilot dreht 180° zurück");
    }

    @Test
    void takeOverFromAPannedFollowViewKeepsThatView() {
        Crew crew = new Crew(CINE, new GimbalSetting(GimbalSetting.Mode.FOLLOW, -20.0, 40.0, 40.0, 0.0), 10.0);
        crew.settle();
        assertHeading(50.0, crew.camera(), "Übernahme mit Schwenk 40°");
        crew.yaw(120.0);
        assertHeading(50.0, crew.camera(), "Pilot giert 120°");
    }

    @Test
    void compactHoldsUntilTheStopAndComesBack() {
        Crew crew = new Crew(COMPACT, GimbalSetting.CENTER, 0.0);
        crew.settle();
        crew.yaw(15.0);
        assertHeading(0.0, crew.camera(), "Pilot giert 15°, innerhalb der Grenze");
        assertFalse(crew.stop(), "15°: kein Anschlag");
        crew.yaw(25.0);
        assertHeading(40.0 - GimbalLimits.LIMITED_YAW, crew.camera(), "Pilot giert 40°, Gimbal am Anschlag");
        assertTrue(crew.stop(), "40°: Anschlag");
        crew.yaw(-40.0);
        assertHeading(0.0, crew.camera(), "Pilot giert zurück");
        assertFalse(crew.stop());
    }

    @Test
    void compactOperatorPansOnlyWithinTheStop() {
        Crew crew = new Crew(COMPACT, GimbalSetting.CENTER, 0.0);
        crew.settle();
        crew.pan(1.0, 0.2);
        assertHeading(18.0, crew.camera(), "Kameramann schwenkt 18°");
        crew.pan(1.0, 1.0);
        assertHeading(GimbalLimits.LIMITED_YAW, crew.camera(), "Kameramann schwenkt bis zum Anschlag");
        crew.pan(-1.0, 2.0);
        assertHeading(-GimbalLimits.LIMITED_YAW, crew.camera(), "Kameramann schwenkt zum anderen Anschlag");
        crew.yaw(-30.0);
        assertHeading(-GimbalLimits.LIMITED_YAW, crew.camera(), "Pilot giert 30° nach links, Blick bleibt");
    }
}
