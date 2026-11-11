package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.crew.model.GimbalSetting;

import java.util.Optional;

public final class SendGate {
    public static final long SEND_MILLIS = 100L;
    public static final long RESEND_MILLIS = 1_000L;

    private GimbalSetting lastSent;
    private long lastSentMillis;

    public void start(GimbalSetting stored, long nowMillis) {
        this.lastSent = stored;
        this.lastSentMillis = nowMillis;
    }

    public Optional<GimbalSetting> due(GimbalSetting current, GimbalSetting stored, long nowMillis) {
        if (current == null || nowMillis - this.lastSentMillis < SEND_MILLIS) {
            return Optional.empty();
        }
        boolean changed = current.differs(this.lastSent);
        boolean stale = current.differs(stored) && nowMillis - this.lastSentMillis >= RESEND_MILLIS;
        if (!changed && !stale) {
            return Optional.empty();
        }
        this.lastSent = current;
        this.lastSentMillis = nowMillis;
        return Optional.of(current);
    }

    public void reset() {
        this.lastSent = null;
        this.lastSentMillis = 0L;
    }
}
