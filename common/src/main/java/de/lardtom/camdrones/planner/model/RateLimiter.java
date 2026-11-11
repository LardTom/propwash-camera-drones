package de.lardtom.camdrones.planner.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class RateLimiter {
    private static final long IDLE_MILLIS = 60_000L;

    private final double perSecond;
    private final double burst;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    public RateLimiter(double perSecond) {
        this(perSecond, perSecond);
    }

    public RateLimiter(double perSecond, double burst) {
        if (!(perSecond > 0.0) || !(burst >= 1.0)) {
            throw new IllegalArgumentException("Rate ungültig: " + perSecond + "/" + burst);
        }
        this.perSecond = perSecond;
        this.burst = burst;
    }

    public synchronized boolean tryAcquire(UUID key, long nowMillis) {
        Bucket bucket = this.buckets.get(key);
        if (bucket == null) {
            bucket = new Bucket(this.burst, nowMillis);
            this.buckets.put(key, bucket);
        } else {
            double elapsed = Math.max(0L, nowMillis - bucket.last) / 1000.0;
            bucket.tokens = Math.min(this.burst, bucket.tokens + elapsed * this.perSecond);
            bucket.last = Math.max(bucket.last, nowMillis);
        }
        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized void forget(UUID key) {
        this.buckets.remove(key);
    }

    public synchronized int tracked(long nowMillis) {
        Iterator<Bucket> it = this.buckets.values().iterator();
        while (it.hasNext()) {
            if (nowMillis - it.next().last > IDLE_MILLIS) {
                it.remove();
            }
        }
        return this.buckets.size();
    }

    private static final class Bucket {
        private double tokens;
        private long last;

        private Bucket(double tokens, long last) {
            this.tokens = tokens;
            this.last = last;
        }
    }
}
