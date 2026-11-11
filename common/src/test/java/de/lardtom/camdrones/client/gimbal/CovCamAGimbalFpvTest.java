package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.propwash.api.flight.GimbalModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamAGimbalFpvTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);

    private static Quaternionf frame(double headingDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-headingDeg)).rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(-rollDeg));
    }

    private static Quaternionf run(GimbalResolver.Aim aim, Quaternionf frame, double seconds) {
        GimbalModel model = GimbalPose.create(TOUR);
        double heading = GimbalPose.heading(frame);
        for (int i = 0; i < (int) Math.round(60.0 * seconds); i++) {
            GimbalPose.aim(model, aim, heading);
            GimbalPose.step(model, 1.0 / 60.0, frame);
        }
        return GimbalPose.rotation(model, new Quaternionf());
    }

    @Test
    void fpvRollFollowsTheFrame() {
        Optional<GimbalSetting.Mode> fpv = Arrays.stream(GimbalSetting.Mode.values()).filter(m -> m.name().equals("FPV")).findFirst();
        assertTrue(fpv.isPresent(), "kein Gimbal-Modus FPV (Entwurf §2.8: Folgen, Sperre, FPV), vorhanden " + Arrays.toString(GimbalSetting.Mode.values()));
        Quaternionf frame = frame(10.0, 0.0, 30.0);
        GimbalSetting setting = new GimbalSetting(fpv.get(), -20.0, 0.0, 0.0, 0.0);
        GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalResolver.Scene.free(TOUR, 0, 0, 0, 10.0), setting);
        Quaternionf q = run(aim, frame, 2.0);
        Vector3f right = q.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f forward = q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double roll = Math.toDegrees(Math.asin(Math.abs(right.y)));
        double pitch = Math.toDegrees(Math.asin(forward.y));
        assertEquals(30.0, roll, 3.0, "Bild rollt im Modus FPV nicht mit dem Rahmen");
        assertEquals(-20.0, pitch, 3.0, "Neigung im Modus FPV nicht stabilisiert");
    }
}
