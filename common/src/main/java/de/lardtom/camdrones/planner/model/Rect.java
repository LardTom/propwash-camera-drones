package de.lardtom.camdrones.planner.model;

public record Rect(int x, int y, int w, int h) {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public Rect {
        w = Math.max(0, w);
        h = Math.max(0, h);
    }

    public int right() {
        return this.x + this.w;
    }

    public int bottom() {
        return this.y + this.h;
    }

    public int centerX() {
        return this.x + this.w / 2;
    }

    public int centerY() {
        return this.y + this.h / 2;
    }

    public boolean isEmpty() {
        return this.w <= 0 || this.h <= 0;
    }

    public boolean contains(double px, double py) {
        return !this.isEmpty() && px >= this.x && px < this.right() && py >= this.y && py < this.bottom();
    }

    public boolean inside(Rect outer) {
        return this.x >= outer.x && this.y >= outer.y && this.right() <= outer.right() && this.bottom() <= outer.bottom();
    }

    public boolean overlaps(Rect other) {
        return !this.isEmpty() && !other.isEmpty() && this.x < other.right() && other.x < this.right() && this.y < other.bottom()
                && other.y < this.bottom();
    }

    public Rect inset(int by) {
        return new Rect(this.x + by, this.y + by, this.w - 2 * by, this.h - 2 * by);
    }

    public Rect withHeight(int height) {
        return new Rect(this.x, this.y, this.w, height);
    }

    public Rect below(int gap, int height) {
        return new Rect(this.x, this.bottom() + gap, this.w, height);
    }
}
