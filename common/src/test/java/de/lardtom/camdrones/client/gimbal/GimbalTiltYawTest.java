package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.propwash.api.flight.GimbalModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalTiltYawTest {
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final double FPS = 60.0;
    private static final double SETTLE_SECONDS = 8.0;

    private static Quaternionf tilted(double headingDeg, double tiltDeg, double towardDeg) {
        float a = (float) Math.toRadians(towardDeg);
        Vector3f axis = new Vector3f((float) Math.cos(a), 0.0F, (float) Math.sin(a)).normalize();
        return new Quaternionf().rotateY((float) Math.toRadians(-headingDeg)).rotateAxis((float) Math.toRadians(tiltDeg), axis);
    }

    private static double forwardHeading(Quaternionf q) {
        Vector3f forward = q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return Compass.headingTo(forward.x, forward.z);
    }

    private static double roll(Quaternionf q) {
        Vector3f right = q.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        return Math.toDegrees(Math.asin(Math.clamp(right.y, -1.0F, 1.0F)));
    }

    private static double pitch(Quaternionf q) {
        Vector3f forward = q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return Math.toDegrees(Math.asin(Math.clamp(forward.y, -1.0F, 1.0F)));
    }

    private static double ramp(double t, double rise, double hold, double fall) {
        if (t < rise) {
            return t / rise;
        }
        if (t < rise + hold) {
            return 1.0;
        }
        if (t < rise + hold + fall) {
            return 1.0 - (t - rise - hold) / fall;
        }
        return 0.0;
    }

    private static List<Quaternionf> fly(GimbalLimits limits, GimbalSetting setting, double tiltDeg, double towardDeg, double yawFrom,
                                         double yawTo, double seconds) {
        GimbalModel model = GimbalPose.create(limits);
        List<Quaternionf> out = new ArrayList<>();
        int settle = (int) Math.round(FPS * SETTLE_SECONDS);
        int frames = (int) Math.round(FPS * seconds);
        for (int i = -settle; i < frames; i++) {
            double t = Math.max(0, i) / FPS;
            double share = ramp(t, 0.4, 1.6, 0.4);
            double yaw = yawFrom + (yawTo - yawFrom) * Math.min(1.0, t / 1.0);
            Quaternionf frame = tilted(yaw, tiltDeg * share, towardDeg);
            double heading = GimbalPose.heading(frame);
            GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalResolver.Scene.free(limits, 0, 0, 0, heading), setting);
            GimbalPose.aim(model, aim, heading);
            GimbalPose.step(model, 1.0 / FPS, frame);
            if (i >= 0) {
                out.add(GimbalPose.rotation(model, new Quaternionf()));
            }
        }
        return out;
    }

    private static double yawSpan(List<Quaternionf> poses) {
        double base = GimbalPose.heading(poses.getFirst());
        double min = 0.0;
        double max = 0.0;
        for (Quaternionf q : poses) {
            double d = Compass.wrap180(GimbalPose.heading(q) - base);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return max - min;
    }

    @Test
    void forwardOfADiagonallyTiltedDroneLeavesItsHeading() {
        Quaternionf frame = tilted(180.0, 45.0, 50.0);
        assertTrue(Math.abs(Compass.wrap180(forwardHeading(frame) - 180.0)) > 5.0,
                "Bezug: vorne zeigt bei 45° schräg geneigt nach " + forwardHeading(frame) + "°");
    }

    @Test
    void headingIsTheYawOfTheDroneAndIgnoresTilt() {
        for (double toward : new double[]{0.0, 30.0, 50.0, 90.0, 135.0, 225.0, 300.0}) {
            for (double tilt : new double[]{10.0, 25.0, 35.0, 45.0}) {
                for (double heading : new double[]{0.0, 90.0, 180.0, 271.0}) {
                    double got = GimbalPose.heading(tilted(heading, tilt, toward));
                    assertEquals(0.0, Compass.wrap180(got - heading), 0.01, "Gieren " + heading + "°, " + tilt + "° geneigt Richtung " + toward
                            + "° → " + got + "°");
                }
            }
        }
        assertEquals(0.0, GimbalPose.heading(new Quaternionf()), 1.0E-4);
        assertEquals(90.0, GimbalPose.heading(new Quaternionf().rotateY((float) Math.toRadians(-90.0))), 1.0E-3);
    }

    @Test
    void followDoesNotPanWhileTheDroneTiltsDiagonallyAndLevelsAgain() {
        List<Quaternionf> poses = fly(CINE, GimbalSetting.CENTER, 45.0, 50.0, 180.0, 180.0, 3.5);
        double span = yawSpan(poses);
        assertTrue(span < 0.05, "Kamera schwenkt " + span + "°, obwohl die Drohne nicht giert");
        for (Quaternionf q : poses) {
            assertEquals(0.0, roll(q), 0.01, "Horizont");
            assertEquals(0.0, pitch(q), 0.01, "Neigung");
        }
    }

    @Test
    void followAndPanHoldWithTourAndCineInEveryTiltDirection() {
        for (double toward : new double[]{20.0, 50.0, 110.0, 160.0, 250.0, 320.0}) {
            GimbalSetting panned = new GimbalSetting(GimbalSetting.Mode.FOLLOW, -30.0, 40.0, 0.0, 0.0);
            double cine = yawSpan(fly(CINE, panned, 35.0, toward, 90.0, 90.0, 3.5));
            assertTrue(cine < 0.05, "Kino mit Schwenk, Richtung " + toward + "°: Kamera schwenkt " + cine + "°");
            double tour = yawSpan(fly(TOUR, GimbalSetting.CENTER.withTilt(-30.0), 35.0, toward, 90.0, 90.0, 3.5));
            assertTrue(tour < 0.05, "Tour, Richtung " + toward + "°: Kamera schwenkt " + tour + "°");
        }
    }

    @Test
    void cameraStillFollowsARealYawWhileTilted() {
        List<Quaternionf> poses = fly(CINE, GimbalSetting.CENTER, 30.0, 50.0, 180.0, 210.0, 4.0);
        double end = GimbalPose.heading(poses.getLast());
        assertEquals(0.0, Compass.wrap180(end - 210.0), 0.5, "Kamera folgt dem Gieren nach " + end + "°");
    }

    @Test
    void lockHoldsItsCompassHeadingWhileTheDroneTiltsDiagonally() {
        GimbalSetting locked = new GimbalSetting(GimbalSetting.Mode.LOCK, -20.0, 0.0, 135.0, 0.0);
        List<Quaternionf> poses = fly(TOUR, locked, 35.0, 50.0, 130.0, 130.0, 3.5);
        for (Quaternionf q : poses.subList(poses.size() / 2, poses.size())) {
            assertEquals(0.0, Compass.wrap180(GimbalPose.heading(q) - 135.0), 0.5, "Sperre");
            assertEquals(-20.0, pitch(q), 0.01, "Neigung");
        }
    }

    @Test
    void fpvRollStillFollowsTheFrameWhenTiltedDiagonally() {
        GimbalSetting fpv = new GimbalSetting(GimbalSetting.Mode.FPV, -10.0, 0.0, 0.0, 0.0);
        GimbalModel model = GimbalPose.create(TOUR);
        Quaternionf frame = tilted(40.0, 30.0, 50.0);
        double heading = GimbalPose.heading(frame);
        for (int i = 0; i < 120; i++) {
            GimbalPose.aim(model, GimbalResolver.resolve(GimbalResolver.Scene.free(TOUR, 0, 0, 0, heading), fpv), heading);
            GimbalPose.step(model, 1.0 / FPS, frame);
        }
        Quaternionf q = GimbalPose.rotation(model, new Quaternionf());
        assertEquals(roll(frame), roll(q), 0.5, "Modus FPV rollt mit dem Rahmen");
        assertEquals(-10.0, pitch(q), 0.5, "Modus FPV hält die Neigung");
        assertEquals(40.0, model.headingDeg(), 0.05, "Modus FPV folgt dem Gieren");
    }

    @Test
    void headModelStaysRelativeToTheTrueFrame() {
        Quaternionf frame = tilted(200.0, 40.0, 60.0);
        GimbalModel model = GimbalPose.create(CINE);
        double heading = GimbalPose.heading(frame);
        for (int i = 0; i < 120; i++) {
            GimbalPose.aim(model, GimbalResolver.resolve(GimbalResolver.Scene.free(CINE, 0, 0, 0, heading), GimbalSetting.CENTER), heading);
            GimbalPose.step(model, 1.0 / FPS, frame);
        }
        Quaternionf world = GimbalPose.rotation(model, new Quaternionf());
        Quaternionf rebuilt = new Quaternionf(frame).mul(GimbalPose.relative(frame, world, new Quaternionf()));
        assertTrue(Math.abs(rebuilt.dot(world)) > 0.99999F, "Rahmen mal Kopf ergibt die Weltlage");
        assertEquals(200.0, GimbalPose.heading(world), 0.05, "Kopf blickt in Gierrichtung der Drohne");
    }
}
