package de.lardtom.camdrones.logic.nav;

public final class CineMode extends NormalMode {
    public static final double YAW_RATE = 30.0;
    public static final double SMOOTHING = 0.8;
    public static final String LABEL = "C";

    public CineMode(FlightClass flightClass) {
        super(flightClass, flightClass.cineSpeed(), YAW_RATE, SMOOTHING, LABEL);
    }
}
