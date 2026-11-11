package de.lardtom.camdrones.client.record;

import de.lardtom.camdrones.client.record.model.CameraAngles;
import de.lardtom.camdrones.client.record.model.PathSample;
import de.lardtom.camdrones.client.record.model.PathThinner;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraPathTest {
    @Test
    void anglesRoundTripThroughTheCameraRotation() {
        double[][] cases = {{0, 0, 0}, {90, -45, 0}, {-135, 30, 5}, {179, -89, 0}, {45, 60, -20}};
        for (double[] c : cases) {
            CameraAngles angles = new CameraAngles(c[0], c[1], c[2]);
            CameraAngles back = CameraAngles.of(angles.rotation());
            Quaterniond a = angles.rotation();
            Quaterniond b = back.rotation();
            double dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
            assertTrue(dot > 0.99999, "Winkel " + c[0] + "/" + c[1] + "/" + c[2] + " → " + back);
        }
    }

    @Test
    void frameMapsMinusZToForwardAndYToUp() {
        Vector3f forward = new Vector3f(0.6F, -0.2F, -0.77F).normalize();
        Vector3f up = new Vector3f(0.1F, 0.98F, 0.0F).normalize();
        Quaternionf frame = CameraAngles.frame(forward.x, forward.y, forward.z, up.x, up.y, up.z, new Quaternionf());
        Vector3f f = frame.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f u = frame.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertTrue(f.distance(forward) < 1.0E-4F, "vorne " + f);
        assertTrue(u.dot(up) > 0.98F, "oben " + u);
        Quaternionf level = CameraAngles.frame(0.0, 0.0, 0.0, 0.0, 1.0, 0.0, new Quaternionf());
        assertEquals(1.0F, level.w, 1.0E-6F);
    }

    @Test
    void verticalFieldOfViewForSixteenByNine() {
        assertEquals(90.0, CameraAngles.verticalFov(90.0, 1.0), 1.0E-9);
        assertEquals(58.7155, CameraAngles.verticalFov(90.0, 16.0 / 9.0), 1.0E-3);
        assertTrue(Double.isNaN(CameraAngles.verticalFov(Double.NaN, 16.0 / 9.0)));
    }

    @Test
    void yawStaysContinuousAcrossTheSeam() {
        PathSample a = new PathSample(0, 0, 0, 0, 179.0, 0, 0, 60);
        PathSample b = new PathSample(1, 0, 0, 0, -179.0, 0, 0, 60).continuing(a);
        assertEquals(181.0, b.yaw(), 1.0E-9);
    }

    @Test
    void straightFlightNeedsTwoKeyframes() {
        List<PathSample> samples = new ArrayList<>();
        for (int t = 0; t <= 80; t++) {
            samples.add(new PathSample(t, t * 0.4, 70.0, -t * 0.2, 30.0, -20.0, 0.0, 50.0));
        }
        List<PathSample> keys = PathThinner.camera(samples);
        assertEquals(2, keys.size());
        assertEquals(0, keys.getFirst().tick());
        assertEquals(80, keys.getLast().tick());
        assertEquals(1, PathThinner.fov(samples).size());
    }

    @Test
    void longFlightsKeepAKeyframeAtLeastEveryFiveSeconds() {
        List<PathSample> samples = new ArrayList<>();
        for (int t = 0; t <= 1000; t++) {
            samples.add(new PathSample(t, t * 0.2, 70.0, 0.0, 0.0, -10.0, 0.0, 50.0));
        }
        List<PathSample> keys = PathThinner.camera(samples);
        for (int i = 1; i < keys.size(); i++) {
            assertTrue(keys.get(i).tick() - keys.get(i - 1).tick() <= PathThinner.MAX_SPAN_TICKS);
        }
    }

    @Test
    void curvedFlightStaysWithinTheToleranceAndTheLimit() {
        List<PathSample> samples = new ArrayList<>();
        for (int t = 0; t <= 2400; t++) {
            double a = t * 0.01;
            samples.add(new PathSample(t, 20.0 * Math.cos(a), 70.0 + Math.sin(a * 3.0), 20.0 * Math.sin(a), Math.toDegrees(a) + 90.0,
                    -30.0 + 10.0 * Math.sin(a * 2.0), 0.0, 40.0 + 10.0 * Math.sin(a)));
        }
        List<PathSample> keys = PathThinner.camera(samples);
        assertTrue(keys.size() < samples.size() / 4, "nur auf " + keys.size() + " ausgedünnt");
        assertTrue(PathThinner.worstError(samples, keys, 0.0) <= PathThinner.POSITION_TOLERANCE + 1.0E-9);
        List<PathSample> capped = PathThinner.camera(samples, 60);
        assertTrue(capped.size() <= 60, "Obergrenze: " + capped.size());
        List<PathSample> fov = PathThinner.fov(samples);
        assertTrue(fov.size() > 10 && fov.size() < samples.size() / 4, "Sichtfeld: " + fov.size());
        assertEquals(samples.getLast().fov(), fov.getLast().fov(), 1.0E-9);
    }

    @Test
    void imguiColourSwapsRedAndBlue() {
        assertEquals(0xFFF7C34F, FlashbackLink.imguiColour(0x4FC3F7));
    }
}
