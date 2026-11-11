package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.logic.gimbal.GimbalAim;
import de.lardtom.camdrones.logic.nav.Compass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OperatorGimbalInput {
    public static final int AXIS_PAN = 0;
    public static final int AXIS_TILT = 1;
    public static final int AXIS_ZOOM = 3;
    public static final int ACTION_MISSION = 1;
    public static final int ACTION_RECENTER = 2;
    public static final int ACTION_RECORD = 3;
    public static final int ACTION_PHOTO = 4;
    public static final int ACTIONS = 4;
    public static final double DEADBAND = 0.06;
    public static final double TILT_RATE = 60.0;
    public static final double PAN_RATE = 90.0;
    public static final double MAX_STEP_SECONDS = 0.25;
    public static final long LONG_PRESS_MILLIS = 800L;

    public enum Event {
        RECENTER,
        RECORD,
        PHOTO,
        LEAVE,
        MISSION,
        ABORT
    }

    private final boolean[] last = new boolean[ACTIONS + 1];
    private GimbalSetting setting = GimbalSetting.CENTER;
    private long photoPressed = -1L;
    private boolean leaveFired;
    private boolean missionControl;
    private boolean lastMission;
    private long missionPressed = -1L;
    private boolean abortFired;

    public GimbalSetting setting() {
        return this.setting;
    }

    public boolean missionControl() {
        return this.missionControl;
    }

    public void missionControl(boolean value) {
        this.missionControl = value;
    }

    public void adopt(GimbalSetting value) {
        this.setting = Objects.requireNonNullElse(value, GimbalSetting.CENTER);
    }

    public void takeOver(GimbalSetting from, boolean[] actions) {
        this.setting = Objects.requireNonNullElse(from, GimbalSetting.CENTER);
        for (int n = 1; n <= ACTIONS; n++) {
            this.last[n] = pressed(actions, n);
        }
        this.photoPressed = this.last[ACTION_PHOTO] ? Long.MAX_VALUE : -1L;
        this.leaveFired = this.last[ACTION_PHOTO];
        this.lastMission = this.missionControl && this.last[ACTION_MISSION];
        this.missionPressed = this.lastMission ? Long.MAX_VALUE : -1L;
        this.abortFired = this.lastMission;
    }

    public static double deadband(double value) {
        double v = Math.clamp(Double.isFinite(value) ? value : 0.0, -1.0, 1.0);
        double magnitude = Math.abs(v);
        if (magnitude <= DEADBAND) {
            return 0.0;
        }
        return Math.copySign((magnitude - DEADBAND) / (1.0 - DEADBAND), v);
    }

    public List<Event> update(GimbalLimits limits, double[] axes, boolean[] actions, double seconds, long nowMillis, double frameHeading) {
        List<Event> events = new ArrayList<>();
        double dt = Math.clamp(Double.isFinite(seconds) ? seconds : 0.0, 0.0, MAX_STEP_SECONDS);
        GimbalSetting next = world(limits, this.setting, frameHeading);
        double tilt = limits.clampTilt(next.tilt() + deadband(axis(axes, AXIS_TILT)) * TILT_RATE * dt);
        next = next.withTilt(tilt);
        double turn = deadband(axis(axes, AXIS_PAN)) * PAN_RATE * dt;
        if (turn != 0.0) {
            next = next.withHeading(limits.heading(limits.heading(next.heading(), frameHeading) + turn, frameHeading));
        }
        next = next.withZoom(Math.clamp(axis(axes, AXIS_ZOOM), 0.0, 1.0));
        if (rising(actions, ACTION_RECENTER)) {
            next = next.withTilt(limits.clampTilt(GimbalAim.recenter(next.tilt()))).withHeading(Compass.normalize(frameHeading));
            events.add(Event.RECENTER);
        }
        if (rising(actions, ACTION_RECORD)) {
            events.add(Event.RECORD);
        }
        boolean photo = pressed(actions, ACTION_PHOTO);
        if (photo && !this.last[ACTION_PHOTO]) {
            this.photoPressed = nowMillis;
            this.leaveFired = false;
        }
        if (photo && !this.leaveFired && this.photoPressed != Long.MAX_VALUE && nowMillis - this.photoPressed >= LONG_PRESS_MILLIS) {
            this.leaveFired = true;
            events.add(Event.LEAVE);
        }
        if (!photo && this.last[ACTION_PHOTO]) {
            if (!this.leaveFired && this.photoPressed != Long.MAX_VALUE) {
                events.add(Event.PHOTO);
            }
            this.photoPressed = -1L;
            this.leaveFired = false;
        }
        this.mission(this.missionControl && pressed(actions, ACTION_MISSION), nowMillis, events);
        for (int n = 1; n <= ACTIONS; n++) {
            this.last[n] = pressed(actions, n);
        }
        this.setting = next;
        return events;
    }

    private void mission(boolean down, long nowMillis, List<Event> events) {
        if (down && !this.lastMission) {
            this.missionPressed = nowMillis;
            this.abortFired = false;
        }
        if (down && !this.abortFired && this.missionPressed != Long.MAX_VALUE && nowMillis - this.missionPressed >= LONG_PRESS_MILLIS) {
            this.abortFired = true;
            events.add(Event.ABORT);
        }
        if (!down && this.lastMission) {
            if (!this.abortFired && this.missionPressed != Long.MAX_VALUE) {
                events.add(Event.MISSION);
            }
            this.missionPressed = -1L;
            this.abortFired = false;
        }
        this.lastMission = down;
    }

    public static GimbalSetting world(GimbalLimits limits, GimbalSetting setting, double frameHeading) {
        GimbalSetting from = Objects.requireNonNullElse(setting, GimbalSetting.CENTER);
        if (from.locked()) {
            return from.pan() == 0.0 ? from : from.withPan(0.0);
        }
        double heading = from.fpv() || !limits.fullPan() ? frameHeading : frameHeading + from.pan();
        return from.withMode(GimbalSetting.Mode.LOCK).withPan(0.0).withHeading(heading);
    }

    private boolean rising(boolean[] actions, int number) {
        return pressed(actions, number) && !this.last[number];
    }

    private static boolean pressed(boolean[] actions, int number) {
        return actions != null && number - 1 < actions.length && number >= 1 && actions[number - 1];
    }

    private static double axis(double[] axes, int index) {
        if (axes == null || index >= axes.length) {
            return 0.0;
        }
        return Double.isFinite(axes[index]) ? axes[index] : 0.0;
    }
}
