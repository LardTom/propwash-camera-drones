package de.lardtom.camdrones.client.hud;

public final class StatusWatch {
    public static final int FRESH_TICKS = 10;

    private final int freshTicks;
    private Object last;
    private long seenAt = Long.MIN_VALUE;

    public StatusWatch() {
        this(FRESH_TICKS);
    }

    public StatusWatch(int freshTicks) {
        this.freshTicks = Math.max(1, freshTicks);
    }

    public void observe(Object status, long tick) {
        if (status == null) {
            this.last = null;
            this.seenAt = Long.MIN_VALUE;
            return;
        }
        if (status != this.last || tick < this.seenAt) {
            this.last = status;
            this.seenAt = tick;
        }
    }

    public boolean fresh(Object status, long tick) {
        return status != null && status == this.last && this.seenAt != Long.MIN_VALUE && tick - this.seenAt <= this.freshTicks;
    }
}
