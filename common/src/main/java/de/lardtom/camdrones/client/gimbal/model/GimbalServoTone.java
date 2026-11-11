package de.lardtom.camdrones.client.gimbal.model;

public final class GimbalServoTone {
    public static final int CLIP_TICKS = 16;

    private int drone = -1;
    private boolean operator;
    private boolean played;
    private long playedAt;

    public static boolean audible(double volume) {
        return Double.isFinite(volume) && volume > 0.0;
    }

    public boolean update(int drone, boolean operator, boolean recentered, long tick) {
        boolean takeOver = operator && (drone != this.drone || !this.operator);
        this.drone = drone;
        this.operator = operator;
        if (!takeOver && !recentered) {
            return false;
        }
        if (this.played && tick - this.playedAt < CLIP_TICKS) {
            return false;
        }
        this.played = true;
        this.playedAt = tick;
        return true;
    }

    public void reset() {
        this.drone = -1;
        this.operator = false;
        this.played = false;
        this.playedAt = 0L;
    }
}
