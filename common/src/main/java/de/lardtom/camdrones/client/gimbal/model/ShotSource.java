package de.lardtom.camdrones.client.gimbal.model;

public enum ShotSource {
    NONE,
    CONTROL,
    WATCHING;

    public static ShotSource of(boolean controlling, boolean watchingCameraDrone, boolean flying) {
        if (controlling) {
            return CONTROL;
        }
        return watchingCameraDrone && !flying ? WATCHING : NONE;
    }

    public boolean takesShots() {
        return this != NONE;
    }

    public boolean shows(CameraActions.Done done) {
        return this == CONTROL || done != CameraActions.Done.REFUSED;
    }
}
