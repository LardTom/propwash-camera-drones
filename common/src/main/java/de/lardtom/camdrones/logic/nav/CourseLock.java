package de.lardtom.camdrones.logic.nav;

public final class CourseLock extends NormalMode {
    public static final String LABEL = "LOCK";

    private final double course;

    public CourseLock(FlightClass flightClass, double course, boolean cine) {
        super(flightClass, cine ? flightClass.cineSpeed() : flightClass.normalSpeed(), cine ? CineMode.YAW_RATE : YAW_RATE,
                cine ? CineMode.SMOOTHING : 0.0, LABEL);
        this.course = Compass.normalize(course);
    }

    public double course() {
        return this.course;
    }

    @Override
    protected double frameHeading(NavInput in) {
        return this.course;
    }
}
