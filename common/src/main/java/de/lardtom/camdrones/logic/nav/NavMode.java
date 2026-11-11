package de.lardtom.camdrones.logic.nav;

public interface NavMode {
    void update(NavInput in, NavOutput out);

    default void reset() {
    }

    static double step(double last, double now) {
        if (Double.isNaN(last)) {
            return 0.0;
        }
        return Math.clamp(now - last, 0.0, 0.25);
    }

    static double deadband(double value, double band) {
        if (Math.abs(value) <= band) {
            return 0.0;
        }
        return Math.signum(value) * (Math.abs(value) - band) / (1.0 - band);
    }

    static double smoothstep(double t) {
        double c = Math.clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }
}
