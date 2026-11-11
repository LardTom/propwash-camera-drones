package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.propwash.api.flight.GimbalModel;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class GimbalPose {
    public static final double RELATIVE_PITCH_MIN = -135.0;
    public static final double RELATIVE_PITCH_MAX = 75.0;
    public static final double RELATIVE_ROLL_LIMIT = 45.0;
    public static final double FOLLOW_SECONDS = 0.6;
    public static final double YAW_RATE = 120.0;
    public static final double PITCH_RATE = 90.0;
    public static final double MAX_STEP = 0.25;
    public static final double TWIST_MIN = 1.0E-6;
    public static final double LEVEL_MIN = 1.0E-8;

    private GimbalPose() {
    }

    public static GimbalModel create(GimbalLimits limits) {
        return new GimbalModel()
                .pitchRange(limits.minTilt(), limits.maxTilt())
                .mechanicalLimits(RELATIVE_PITCH_MIN, RELATIVE_PITCH_MAX, RELATIVE_ROLL_LIMIT, limits.yawLimit())
                .smoothing(FOLLOW_SECONDS, YAW_RATE, PITCH_RATE);
    }

    public static double heading(Quaternionfc frame) {
        double y = frame.y();
        double w = frame.w();
        if (!(y * y + w * w > TWIST_MIN)) {
            return forwardHeading(frame);
        }
        return Compass.normalize(-Math.toDegrees(2.0 * Math.atan2(y, w)));
    }

    public static double forwardHeading(Quaternionfc frame) {
        Vector3f forward = frame.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return Compass.headingTo(forward.x, forward.z);
    }

    public static Quaternionf steady(Quaternionfc frame, Quaternionf dest) {
        Vector3f forward = frame.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        if (!(forward.x * forward.x + forward.z * forward.z > LEVEL_MIN)) {
            return dest.set(frame);
        }
        double shift = Compass.wrap180(heading(frame) - Compass.headingTo(forward.x, forward.z));
        return dest.rotationY((float) Math.toRadians(-shift)).mul(frame).normalize();
    }

    public static void aim(GimbalModel model, GimbalResolver.Aim aim, double frameHeading) {
        if (aim.fpv()) {
            model.mode(GimbalModel.Mode.FPV);
        } else if (aim.locked()) {
            model.mode(GimbalModel.Mode.LOCK);
            model.lockHeading(aim.heading());
        } else if (Math.abs(aim.pan()) > 1.0E-6) {
            model.mode(GimbalModel.Mode.LOCK);
            model.lockHeading(frameHeading + aim.pan());
        } else {
            model.mode(GimbalModel.Mode.FOLLOW);
        }
        model.pitch(aim.tilt());
    }

    public static void step(GimbalModel model, double seconds, Quaternionfc frame) {
        double dt = Double.isFinite(seconds) ? Math.clamp(seconds, 0.0, MAX_STEP) : 0.0;
        Quaternionf steady = steady(frame, new Quaternionf());
        model.update(dt, steady.x(), steady.y(), steady.z(), steady.w());
    }

    public static Quaternionf rotation(GimbalModel model, Quaternionf dest) {
        return dest.set((float) model.qx(), (float) model.qy(), (float) model.qz(), (float) model.qw()).normalize();
    }

    public static Quaternionf relative(Quaternionfc frame, Quaternionfc world, Quaternionf dest) {
        return dest.set(frame).conjugate().mul(world).normalize();
    }

    public static double seconds(long lastNanos, long nowNanos) {
        if (lastNanos == 0L || nowNanos <= lastNanos) {
            return 0.0;
        }
        return Math.min(MAX_STEP, (nowNanos - lastNanos) / 1.0E9);
    }
}
