package de.lardtom.camdrones.client.record.model;

import org.joml.Matrix3f;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record CameraAngles(double yaw, double pitch, double roll) {
    private static final double GIMBAL_LOCK = 1.0E-9;

    public static CameraAngles of(Quaterniondc rotation) {
        Quaterniond q = new Quaterniond(rotation).normalize();
        double x = q.x();
        double y = q.y();
        double z = q.z();
        double w = q.w();
        double m00 = 1.0 - 2.0 * (y * y + z * z);
        double m02 = 2.0 * (x * z + y * w);
        double m10 = 2.0 * (x * y + z * w);
        double m11 = 1.0 - 2.0 * (x * x + z * z);
        double m12 = 2.0 * (y * z - x * w);
        double m20 = 2.0 * (x * z - y * w);
        double m22 = 1.0 - 2.0 * (x * x + y * y);
        double b = Math.asin(Math.clamp(-m12, -1.0, 1.0));
        double a;
        double c;
        if (Math.abs(Math.cos(b)) < GIMBAL_LOCK) {
            a = Math.atan2(-m20, m00);
            c = 0.0;
        } else {
            a = Math.atan2(m02, m22);
            c = Math.atan2(m10, m11);
        }
        return new CameraAngles(wrap(180.0 - Math.toDegrees(a)), -Math.toDegrees(b), wrap(Math.toDegrees(c)));
    }

    public Quaterniond rotation() {
        return new Quaterniond().rotationYXZ(Math.PI - Math.toRadians(this.yaw), -Math.toRadians(this.pitch), Math.toRadians(this.roll));
    }

    public static Quaternionf frame(double forwardX, double forwardY, double forwardZ, double upX, double upY, double upZ, Quaternionf dest) {
        Vector3f forward = new Vector3f((float) forwardX, (float) forwardY, (float) forwardZ);
        Vector3f up = new Vector3f((float) upX, (float) upY, (float) upZ);
        if (!(forward.lengthSquared() > 1.0E-8F) || !Float.isFinite(forward.lengthSquared())) {
            return dest.identity();
        }
        forward.normalize();
        Vector3f right = new Vector3f(forward).cross(up);
        if (!(right.lengthSquared() > 1.0E-8F) || !Float.isFinite(right.lengthSquared())) {
            right.set(forward).cross(Math.abs(forward.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F));
        }
        right.normalize();
        Vector3f trueUp = new Vector3f(right).cross(forward).normalize();
        Matrix3f basis = new Matrix3f(right.x, right.y, right.z, trueUp.x, trueUp.y, trueUp.z, -forward.x, -forward.y, -forward.z);
        return dest.setFromNormalized(basis).normalize();
    }

    public static double verticalFov(double horizontalDegrees, double aspect) {
        if (!Double.isFinite(horizontalDegrees) || !(aspect > 0.0)) {
            return Double.NaN;
        }
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(horizontalDegrees) / 2.0) / aspect));
    }

    public static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }
}
