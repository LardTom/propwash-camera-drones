package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.logic.nav.NavLogic;

public final class MissionGripper {
    public static final int RENEW_TICKS = 20;
    public static final int GRAB_WINDOW_TICKS = 100;
    private static final long READY = Long.MIN_VALUE;

    private long nextAt = READY;
    private long openUntil = READY;

    public boolean renew(NavLogic.MissionState state, boolean loaded, long now) {
        if (state != NavLogic.MissionState.RUNNING || loaded) {
            this.nextAt = READY;
            if (loaded) {
                this.openUntil = READY;
            }
            return false;
        }
        if (now < this.nextAt) {
            return false;
        }
        this.nextAt = now + RENEW_TICKS;
        this.openUntil = now + GRAB_WINDOW_TICKS;
        return true;
    }

    public boolean close(NavLogic.MissionState state, boolean loaded, long now) {
        if (state == NavLogic.MissionState.RUNNING || loaded || this.openUntil == READY || now >= this.openUntil) {
            return false;
        }
        this.openUntil = READY;
        return true;
    }
}
