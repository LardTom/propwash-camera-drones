package de.lardtom.camdrones.logic.nav;

public enum FlightClass {
    COMPACT("compact", 249, 3.0, 10.0, 16.0, 5.0, 3.5, 26, 32, 28.0, false, 30.0, false),
    TOUR("tour", 720, 3.0, 12.0, 20.0, 6.0, 5.0, 38, 46, 63.0, true, 30.0, false),
    CINE("cine", 3950, 4.0, 15.0, 26.0, 8.0, 6.0, 23, 29, 190.0, true, 60.0, true);

    public static final double GIMBAL_MIN_TILT = -90.0;

    private final String key;
    private final int takeoffGrams;
    private final double cineSpeed;
    private final double normalSpeed;
    private final double sportSpeed;
    private final double climbSpeed;
    private final double descendSpeed;
    private final int minHoverMinutes;
    private final int maxHoverMinutes;
    private final double batteryWh;
    private final boolean allAroundSensors;
    private final double gimbalMaxTilt;
    private final boolean gimbalFullPan;

    FlightClass(String key, int takeoffGrams, double cineSpeed, double normalSpeed, double sportSpeed, double climbSpeed,
                double descendSpeed, int minHoverMinutes, int maxHoverMinutes, double batteryWh, boolean allAroundSensors,
                double gimbalMaxTilt, boolean gimbalFullPan) {
        this.key = key;
        this.takeoffGrams = takeoffGrams;
        this.cineSpeed = cineSpeed;
        this.normalSpeed = normalSpeed;
        this.sportSpeed = sportSpeed;
        this.climbSpeed = climbSpeed;
        this.descendSpeed = descendSpeed;
        this.minHoverMinutes = minHoverMinutes;
        this.maxHoverMinutes = maxHoverMinutes;
        this.batteryWh = batteryWh;
        this.allAroundSensors = allAroundSensors;
        this.gimbalMaxTilt = gimbalMaxTilt;
        this.gimbalFullPan = gimbalFullPan;
    }

    public String key() {
        return this.key;
    }

    public int takeoffGrams() {
        return this.takeoffGrams;
    }

    public double cineSpeed() {
        return this.cineSpeed;
    }

    public double normalSpeed() {
        return this.normalSpeed;
    }

    public double sportSpeed() {
        return this.sportSpeed;
    }

    public double climbSpeed() {
        return this.climbSpeed;
    }

    public double descendSpeed() {
        return this.descendSpeed;
    }

    public int minHoverMinutes() {
        return this.minHoverMinutes;
    }

    public int maxHoverMinutes() {
        return this.maxHoverMinutes;
    }

    public double hoverMinutes() {
        return (this.minHoverMinutes + this.maxHoverMinutes) / 2.0;
    }

    public double batteryWh() {
        return this.batteryWh;
    }

    public boolean allAroundSensors() {
        return this.allAroundSensors;
    }

    public double gimbalMaxTilt() {
        return this.gimbalMaxTilt;
    }

    public boolean gimbalFullPan() {
        return this.gimbalFullPan;
    }

    public double curveAccel() {
        return switch (this) {
            case COMPACT, TOUR -> 2.2;
            case CINE -> 2.0;
        };
    }

    public double assistedSpeed(double metresPerSecond) {
        return Math.clamp(metresPerSecond, 0.0, Math.min(this.normalSpeed, NavOutput.MAX_SPEED));
    }
}
