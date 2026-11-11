package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.logic.gimbal.GimbalAim;
import de.lardtom.camdrones.logic.nav.Compass;

import java.util.Objects;

public final class PilotGimbalInput {
    public static final double PICKUP = 0.03;

    private GimbalSetting setting = GimbalSetting.CENTER;
    private boolean started;
    private boolean tiltHeld;
    private double tiltAnchor;
    private boolean panHeld;
    private double panAnchor;
    private double heldPan;
    private double lockBase;
    private GimbalSetting.Mode lastMode = GimbalSetting.Mode.FOLLOW;
    private boolean lastRecenter;
    private boolean recentered;

    public GimbalSetting setting() {
        return this.setting;
    }

    public boolean recentered() {
        return this.recentered;
    }

    public static GimbalSetting.Mode mode(boolean lock, boolean fpv) {
        if (fpv) {
            return GimbalSetting.Mode.FPV;
        }
        return lock ? GimbalSetting.Mode.LOCK : GimbalSetting.Mode.FOLLOW;
    }

    public void takeOver(GimbalSetting from, double tiltDial, double panDial, boolean lock, boolean fpv) {
        this.setting = Objects.requireNonNullElse(from, GimbalSetting.CENTER);
        this.started = true;
        this.tiltHeld = true;
        this.tiltAnchor = tiltDial;
        this.panHeld = true;
        this.panAnchor = panDial;
        this.heldPan = this.setting.locked() ? 0.0 : this.setting.pan();
        this.lockBase = this.setting.heading();
        this.lastMode = mode(lock, fpv);
        this.lastRecenter = false;
        this.recentered = false;
    }

    public GimbalSetting update(GimbalLimits limits, double tiltDial, double panDial, double zoomDial, boolean lock, boolean fpv,
                                boolean recenter, double frameHeading) {
        if (!this.started) {
            this.started = true;
            this.lastMode = GimbalSetting.Mode.FOLLOW;
        }
        if (this.tiltHeld && Math.abs(tiltDial - this.tiltAnchor) > PICKUP) {
            this.tiltHeld = false;
        }
        if (this.panHeld && Math.abs(panDial - this.panAnchor) > PICKUP) {
            this.panHeld = false;
        }
        double tilt = this.tiltHeld ? this.setting.tilt() : limits.tiltFromDial(tiltDial);
        double pan = limits.fullPan() ? (this.panHeld ? this.heldPan : limits.panFromDial(panDial)) : 0.0;
        this.recentered = recenter && !this.lastRecenter;
        if (this.recentered) {
            tilt = GimbalAim.recenter(tilt);
            this.tiltHeld = true;
            this.tiltAnchor = tiltDial;
            pan = 0.0;
            this.heldPan = 0.0;
            this.panHeld = true;
            this.panAnchor = panDial;
            this.lockBase = Compass.normalize(frameHeading);
        }
        this.lastRecenter = recenter;
        GimbalSetting.Mode mode = mode(lock, fpv);
        if (mode == GimbalSetting.Mode.LOCK && this.lastMode != GimbalSetting.Mode.LOCK) {
            this.lockBase = Compass.normalize(frameHeading);
        }
        this.lastMode = mode;
        if (!this.panHeld) {
            this.heldPan = pan;
        }
        double zoom = (Math.clamp(Double.isFinite(zoomDial) ? zoomDial : -1.0, -1.0, 1.0) + 1.0) / 2.0;
        double heading = switch (mode) {
            case LOCK -> Compass.normalize(this.lockBase + pan);
            case FPV -> Compass.normalize(frameHeading);
            case FOLLOW -> Compass.normalize(frameHeading + pan);
        };
        this.setting = new GimbalSetting(mode, limits.clampTilt(tilt), pan, heading, zoom);
        return this.setting;
    }
}
