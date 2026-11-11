package de.lardtom.camdrones.client.gimbal.model;

public final class PilotView {
    public enum Image {
        FPV,
        GIMBAL
    }

    public enum Event {
        NONE,
        FPV,
        GIMBAL,
        OPERATOR,
        RECORDING
    }

    public static final int NONE = -1;
    public static final long PHOTO_NANOS = 1_000_000_000L;
    public static final long PHOTO_MAX_NANOS = 5_000_000_000L;
    public static final int PHOTO_FRAMES = 3;

    private int drone = NONE;
    private Image chosen = Image.FPV;
    private boolean pressed;
    private boolean recordingShown;
    private int photoDrone = NONE;
    private long photoAt;
    private int photoFrames;

    public static Image shown(boolean spectating, boolean operated, Image chosen) {
        return shown(spectating, operated, chosen, false);
    }

    public static Image shown(boolean spectating, boolean operated, Image chosen, boolean capturing) {
        if (spectating) {
            return Image.GIMBAL;
        }
        if (operated) {
            return Image.FPV;
        }
        return capturing ? Image.GIMBAL : chosen;
    }

    public synchronized Image chosen(int droneId) {
        return droneId != NONE && droneId == this.drone ? this.chosen : Image.FPV;
    }

    public synchronized Image shown(int droneId, boolean spectating, boolean operated) {
        return shown(spectating, operated, this.chosen(droneId));
    }

    public synchronized Image shown(int droneId, boolean spectating, boolean operated, boolean recording, long nowNanos) {
        return shown(spectating, operated, this.chosen(droneId), recording || this.photoHeld(droneId, nowNanos));
    }

    public synchronized void photo(int droneId, long nowNanos) {
        if (droneId == NONE) {
            return;
        }
        this.photoDrone = droneId;
        this.photoAt = nowNanos;
        this.photoFrames = 0;
    }

    public synchronized void frame(int droneId) {
        if (droneId != NONE && droneId == this.photoDrone && this.photoFrames < PHOTO_FRAMES) {
            this.photoFrames++;
        }
    }

    public synchronized boolean photoHeld(int droneId, long nowNanos) {
        if (droneId == NONE || droneId != this.photoDrone) {
            return false;
        }
        long since = nowNanos - this.photoAt;
        return since < PHOTO_NANOS || this.photoFrames < PHOTO_FRAMES && since < PHOTO_MAX_NANOS;
    }

    public synchronized Event update(int flownDrone, boolean press, boolean operated) {
        return this.update(flownDrone, press, operated, false);
    }

    public synchronized Event update(int flownDrone, boolean press, boolean operated, boolean recording) {
        if (flownDrone == NONE) {
            this.reset();
            return Event.NONE;
        }
        boolean edge;
        if (flownDrone != this.drone) {
            this.drone = flownDrone;
            this.chosen = Image.FPV;
            this.recordingShown = false;
            edge = false;
        } else {
            edge = press && !this.pressed;
        }
        this.pressed = press;
        if (operated) {
            boolean was = this.chosen == Image.GIMBAL || this.recordingShown;
            this.chosen = Image.FPV;
            this.recordingShown = false;
            return edge || was ? Event.OPERATOR : Event.NONE;
        }
        if (recording) {
            boolean started = !this.recordingShown;
            this.recordingShown = true;
            return started || edge ? Event.RECORDING : Event.NONE;
        }
        if (this.recordingShown) {
            this.recordingShown = false;
            if (!edge) {
                return this.chosen == Image.FPV ? Event.FPV : Event.NONE;
            }
        }
        if (!edge) {
            return Event.NONE;
        }
        this.chosen = this.chosen == Image.FPV ? Image.GIMBAL : Image.FPV;
        return this.chosen == Image.FPV ? Event.FPV : Event.GIMBAL;
    }

    public synchronized void reset() {
        this.drone = NONE;
        this.chosen = Image.FPV;
        this.pressed = false;
        this.recordingShown = false;
        this.photoDrone = NONE;
        this.photoAt = 0L;
        this.photoFrames = 0;
    }
}
