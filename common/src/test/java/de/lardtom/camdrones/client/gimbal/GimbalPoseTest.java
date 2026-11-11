package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.propwash.api.flight.GimbalModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalPoseTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);

    private static Quaternionf frame(double headingDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-headingDeg)).rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(-rollDeg));
    }

    private static Vector3f forward(Quaternionf q) {
        return q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
    }

    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.dot(b));
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    private static Quaternionf run(GimbalLimits limits, GimbalResolver.Aim aim, Quaternionf frame, double fps, double seconds) {
        GimbalModel model = GimbalPose.create(limits);
        double heading = GimbalPose.heading(frame);
        int frames = (int) Math.round(fps * seconds);
        for (int i = 0; i < frames; i++) {
            GimbalPose.aim(model, aim, heading);
            GimbalPose.step(model, 1.0 / fps, frame);
        }
        return GimbalPose.rotation(model, new Quaternionf());
    }

    private static GimbalResolver.Aim manual(GimbalLimits limits, GimbalSetting setting, double heading) {
        return GimbalResolver.resolve(GimbalResolver.Scene.free(limits, 0, 0, 0, heading), setting);
    }

    @Test
    void frameHeadingMatchesCompass() {
        assertEquals(0.0, GimbalPose.heading(frame(0, 0, 0)), 1.0E-4);
        assertEquals(90.0, GimbalPose.heading(frame(90, 0, 0)), 1.0E-4);
        assertEquals(225.0, GimbalPose.heading(frame(225, 10, 0)), 1.0E-3);
        assertEquals(225.0, GimbalPose.forwardHeading(frame(225, 10, 5)), 1.0E-3);
    }

    @Test
    void differentFrameRatesConvergeToTheSamePose() {
        Quaternionf frame = frame(137.0, -12.0, 21.0);
        GimbalResolver.Aim aim = manual(CINE, new GimbalSetting(GimbalSetting.Mode.FOLLOW, -47.0, 35.0, 0.0, 0.0), 137.0);
        Quaternionf pilot = run(CINE, aim, frame, 144.0, 4.0);
        Quaternionf operator = run(CINE, aim, frame, 60.0, 4.0);
        Quaternionf viewer = run(CINE, aim, frame, 20.0, 4.0);
        assertTrue(angle(pilot, operator) < 0.05, "Pilot/Operator " + angle(pilot, operator));
        assertTrue(angle(pilot, viewer) < 0.05, "Pilot/Zuschauer " + angle(pilot, viewer));
    }

    @Test
    void horizonStaysLevelWhileTheFrameRolls() {
        Quaternionf frame = frame(10.0, 0.0, 30.0);
        Quaternionf q = run(TOUR, manual(TOUR, GimbalSetting.CENTER, 10.0), frame, 60.0, 2.0);
        Vector3f right = q.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        assertTrue(Math.abs(right.y) < Math.sin(Math.toRadians(2.0)), "Rollwinkel " + Math.toDegrees(Math.asin(right.y)));
    }

    @Test
    void tiltDownReachesMinusNinety() {
        Quaternionf q = run(TOUR, manual(TOUR, GimbalSetting.CENTER.withTilt(-90.0), 0.0), frame(0, 0, 0), 60.0, 3.0);
        assertTrue(forward(q).y < -0.999, "Blick " + forward(q));
    }

    @Test
    void lockHoldsTheCompassHeadingWhileTheDroneYawsWithinLimits() {
        GimbalSetting locked = new GimbalSetting(GimbalSetting.Mode.LOCK, 0.0, 0.0, 90.0, 0.0);
        Quaternionf cine = run(CINE, manual(CINE, locked, 200.0), frame(200.0, 0, 0), 60.0, 4.0);
        assertEquals(90.0, GimbalPose.heading(cine), 0.5);
        Quaternionf near = run(TOUR, manual(TOUR, locked, 100.0), frame(100.0, 0, 0), 60.0, 4.0);
        assertEquals(90.0, GimbalPose.heading(near), 0.5);
        Quaternionf tour = run(TOUR, manual(TOUR, locked, 150.0), frame(150.0, 0, 0), 60.0, 4.0);
        assertEquals(150.0 - GimbalLimits.LIMITED_YAW, GimbalPose.heading(tour), 0.5, "Tour nur bis zur mechanischen Grenze");
    }

    @Test
    void relativePoseIsIdentityWhenAlignedAndRotatesTheHead() {
        Quaternionf frame = frame(70.0, 0.0, 0.0);
        Quaternionf world = run(TOUR, manual(TOUR, GimbalSetting.CENTER, 70.0), frame, 60.0, 2.0);
        Quaternionf rel = GimbalPose.relative(frame, world, new Quaternionf());
        assertTrue(angle(rel, new Quaternionf()) < 0.1, "Kopf ruht " + angle(rel, new Quaternionf()));
        Quaternionf down = run(TOUR, manual(TOUR, GimbalSetting.CENTER.withTilt(-45.0), 70.0), frame, 60.0, 3.0);
        Quaternionf relDown = GimbalPose.relative(frame, down, new Quaternionf());
        Vector3f look = relDown.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        assertEquals(-Math.sin(Math.toRadians(45.0)), look.y, 1.0E-3);
        assertEquals(0.0, look.x, 1.0E-3);
        Quaternionf rebuilt = new Quaternionf(frame).mul(relDown);
        assertTrue(angle(rebuilt, down) < 0.01, "Rahmen mal Kopf ergibt die Weltlage");
    }

    @Test
    void secondsAreClampedAndZeroOnFirstCall() {
        assertEquals(0.0, GimbalPose.seconds(0L, 5L));
        assertEquals(0.0, GimbalPose.seconds(10L, 5L));
        assertEquals(0.016, GimbalPose.seconds(1_000_000L, 17_000_000L), 1.0E-9);
        assertEquals(GimbalPose.MAX_STEP, GimbalPose.seconds(1L, 9_000_000_000L));
    }
}
