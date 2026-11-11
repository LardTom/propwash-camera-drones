package de.lardtom.camdrones.client.hud;

public record CameraViewLayout(Box image, Box topBar, Box recording, Box info, Box map, Box tiltScale, int warningY, int lineHeight) {
    public static final int MARGIN = 4;
    public static final int MIN_MAP = 32;
    public static final int MAX_MAP = 72;
    public static final int SCALE_WIDTH = 5;
    public static final int SCALE_LABEL = 24;
    public static final int INFO_CHARS = 26;
    public static final int CHAR_WIDTH = 6;

    public record Box(int x0, int y0, int x1, int y1) {
        public int width() {
            return this.x1 - this.x0;
        }

        public int height() {
            return this.y1 - this.y0;
        }

        public boolean empty() {
            return this.x1 <= this.x0 || this.y1 <= this.y0;
        }

        public boolean inside(Box other) {
            return this.x0 >= other.x0 && this.y0 >= other.y0 && this.x1 <= other.x1 && this.y1 <= other.y1;
        }

        public boolean overlaps(Box other) {
            return this.x0 < other.x1 && other.x0 < this.x1 && this.y0 < other.y1 && other.y0 < this.y1;
        }

        public int centerX() {
            return (this.x0 + this.x1) / 2;
        }

        public int centerY() {
            return (this.y0 + this.y1) / 2;
        }
    }

    public static CameraViewLayout of(int x0, int y0, int x1, int y1, int lineHeight) {
        int line = Math.max(1, lineHeight);
        Box image = new Box(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
        int width = image.width();
        int height = image.height();
        Box topBar = new Box(x0, y0, x1, Math.min(image.y1, y0 + line + 4));
        Box recording = new Box(x0 + MARGIN, topBar.y1 + MARGIN, Math.min(image.x1, x0 + MARGIN + 10 * CHAR_WIDTH), topBar.y1 + MARGIN + line);
        int map = Math.clamp(Math.min(width, height) / 4, MIN_MAP, MAX_MAP);
        map = Math.max(0, Math.min(map, Math.min(width, height) - 2 * MARGIN));
        Box mapBox = new Box(x1 - MARGIN - map, y1 - MARGIN - map, x1 - MARGIN, y1 - MARGIN);
        int infoHeight = 2 * line + 2;
        int infoRight = Math.min(mapBox.x0 - MARGIN, x0 + MARGIN + INFO_CHARS * CHAR_WIDTH);
        Box info = new Box(x0 + MARGIN, y1 - MARGIN - infoHeight, Math.max(x0 + MARGIN, infoRight), y1 - MARGIN);
        int scaleTop = recording.y1 + MARGIN;
        int scaleBottom = mapBox.y0 - MARGIN - line / 2;
        Box scale = new Box(x1 - MARGIN - SCALE_WIDTH, scaleTop, x1 - MARGIN, Math.max(scaleTop, scaleBottom));
        int warningY = y0 + height * 2 / 3 - line / 2;
        return new CameraViewLayout(image, topBar, recording, info, mapBox, scale, warningY, line);
    }

    public Box tiltLabels() {
        return new Box(this.tiltScale.x0 - SCALE_LABEL, this.tiltScale.y0, this.tiltScale.x0, this.tiltScale.y1);
    }

    public int tiltY(double tilt, double minTilt, double maxTilt) {
        if (this.tiltScale.empty() || !(maxTilt > minTilt)) {
            return this.tiltScale.centerY();
        }
        double t = (maxTilt - Math.clamp(tilt, minTilt, maxTilt)) / (maxTilt - minTilt);
        return this.tiltScale.y0 + (int) Math.round(t * (this.tiltScale.height() - 1));
    }
}
