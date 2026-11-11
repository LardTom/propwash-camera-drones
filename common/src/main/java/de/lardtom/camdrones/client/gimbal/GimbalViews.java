package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.gimbal.model.TiltGlide;
import de.lardtom.propwash.api.flight.GimbalModel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntPredicate;

public final class GimbalViews {
    public static final long FRESH_NANOS = 1_000_000_000L;
    public static final long STALE_NANOS = 10_000_000_000L;

    public static final class View {
        private final GimbalLimits limits;
        private final GimbalModel camera;
        private final GimbalModel head;
        private final TiltGlide cameraGlide = new TiltGlide();
        private final TiltGlide headGlide = new TiltGlide();
        private long cameraNanos;
        private long headNanos;
        private long touchedNanos;
        private GimbalResolver.Aim aim;
        private double tilt = Double.NaN;
        private boolean stop;

        View(GimbalLimits limits) {
            this.limits = limits;
            this.camera = GimbalPose.create(limits);
            this.head = GimbalPose.create(limits);
        }

        public GimbalLimits limits() {
            return this.limits;
        }

        public GimbalModel camera() {
            return this.camera;
        }

        public GimbalModel head() {
            return this.head;
        }

        public TiltGlide cameraGlide() {
            return this.cameraGlide;
        }

        public TiltGlide headGlide() {
            return this.headGlide;
        }

        public double cameraStep(long nowNanos) {
            double dt = GimbalPose.seconds(this.cameraNanos, nowNanos);
            this.cameraNanos = nowNanos;
            this.touchedNanos = nowNanos;
            return dt;
        }

        public double headStep(long nowNanos) {
            double dt = GimbalPose.seconds(this.headNanos, nowNanos);
            this.headNanos = nowNanos;
            this.touchedNanos = nowNanos;
            return dt;
        }

        public void aimed(GimbalResolver.Aim value, double modelTilt) {
            this.aim = value;
            this.tilt = modelTilt;
        }

        public GimbalResolver.Aim aim() {
            return this.aim;
        }

        public void stop(boolean value) {
            this.stop = value;
        }

        public boolean atStop() {
            return this.stop;
        }

        public double tilt() {
            return this.tilt;
        }

        public boolean freshCamera(long nowNanos) {
            return this.cameraNanos != 0L && nowNanos - this.cameraNanos < FRESH_NANOS;
        }

        public boolean freshHead(long nowNanos) {
            return this.headNanos != 0L && nowNanos - this.headNanos < FRESH_NANOS;
        }

        public boolean fresh(long nowNanos) {
            return this.touchedNanos != 0L && nowNanos - this.touchedNanos < FRESH_NANOS;
        }
    }

    private final Map<Integer, View> views = new HashMap<>();

    public synchronized View view(int droneId, GimbalLimits limits) {
        View view = this.views.get(droneId);
        if (view == null || !view.limits.equals(limits)) {
            view = new View(limits);
            this.views.put(droneId, view);
        }
        return view;
    }

    public synchronized View peek(int droneId) {
        return this.views.get(droneId);
    }

    public synchronized int size() {
        return this.views.size();
    }

    public synchronized void prune(long nowNanos, IntPredicate alive) {
        Iterator<Map.Entry<Integer, View>> it = this.views.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, View> entry = it.next();
            if (nowNanos - entry.getValue().touchedNanos > STALE_NANOS || !alive.test(entry.getKey())) {
                it.remove();
            }
        }
    }

    public synchronized void clear() {
        this.views.clear();
    }
}
