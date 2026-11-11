package de.lardtom.camdrones.client.hud;

public final class RecordingClock {
    private long startedAt = Long.MIN_VALUE;

    public double update(boolean recording, long millis) {
        if (!recording) {
            this.startedAt = Long.MIN_VALUE;
            return Double.NaN;
        }
        if (this.startedAt == Long.MIN_VALUE || millis < this.startedAt) {
            this.startedAt = millis;
        }
        return (millis - this.startedAt) / 1000.0;
    }

    public boolean running() {
        return this.startedAt != Long.MIN_VALUE;
    }
}
