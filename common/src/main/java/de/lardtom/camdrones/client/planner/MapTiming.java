package de.lardtom.camdrones.client.planner;

import java.util.Arrays;

final class MapTiming {
    static final int CAPACITY = 4096;

    private final long[] intervals = new long[CAPACITY];
    private final long[] work = new long[CAPACITY];
    private int frames;
    private long lastFrame;
    private int ticks;
    private long tickWork;
    private long tickWorst;
    private long written;
    private long uploaded;
    private long columns;
    private double drawnX = Double.NaN;
    private double drawnZ = Double.NaN;
    private double drawnScale = Double.NaN;

    void reset() {
        this.frames = 0;
        this.lastFrame = 0L;
        this.ticks = 0;
        this.tickWork = 0L;
        this.tickWorst = 0L;
        this.written = 0L;
        this.uploaded = 0L;
        this.columns = 0L;
    }

    void frame(double centerX, double centerZ, double scale, long workNanos) {
        long now = System.nanoTime();
        if (this.lastFrame != 0L && this.frames < CAPACITY) {
            this.intervals[this.frames] = now - this.lastFrame;
            this.work[this.frames] = workNanos;
            this.frames++;
        }
        this.lastFrame = now;
        this.drawnX = centerX;
        this.drawnZ = centerZ;
        this.drawnScale = scale;
    }

    void tick(long workNanos) {
        this.ticks++;
        this.tickWork += workNanos;
        this.tickWorst = Math.max(this.tickWorst, workNanos);
    }

    void written(long texels) {
        this.written += texels;
    }

    void uploaded(long texels) {
        this.uploaded += texels;
    }

    void columns(long count) {
        this.columns += count;
    }

    long[] intervals() {
        return Arrays.copyOf(this.intervals, this.frames);
    }

    long[] work() {
        return Arrays.copyOf(this.work, this.frames);
    }

    int ticks() {
        return this.ticks;
    }

    long tickWork() {
        return this.tickWork;
    }

    long tickWorst() {
        return this.tickWorst;
    }

    long written() {
        return this.written;
    }

    long uploaded() {
        return this.uploaded;
    }

    long columns() {
        return this.columns;
    }

    double drawnX() {
        return this.drawnX;
    }

    double drawnZ() {
        return this.drawnZ;
    }

    double drawnScale() {
        return this.drawnScale;
    }
}
