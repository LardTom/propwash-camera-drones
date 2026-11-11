package de.lardtom.camdrones.logic.nav;

public final class OrbitController implements NavMode {
    public static final double MIN_RADIUS = 2.0;
    public static final double MAX_RADIUS = 200.0;
    public static final double LAG = 0.7;
    public static final double RADIAL_GAIN = 0.8;
    public static final double RADIAL_INTEGRAL = 0.25;
    public static final double MAX_RADIAL_SPEED = 3.0;
    public static final double LATERAL_ACCEL = 1.5;
    public static final double SPEED_STICK_RATE = 1.5;
    public static final double RADIUS_STICK_RATE = 3.0;
    public static final double HEADING_GAIN = 3.0;
    public static final double MAX_YAW_RATE = 120.0;
    public static final String LABEL = "POI";

    private final FlightClass flightClass;
    private final NormalMode.AltitudeStick altitudeStick;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private double radius;
    private double altitude;
    private double speed;
    private double radialIntegral;
    private double lastTime = Double.NaN;
    private String label = LABEL;

    public OrbitController(FlightClass flightClass, double centerX, double centerY, double centerZ, double radius, double altitude,
                           double speed, boolean clockwise) {
        this.flightClass = flightClass;
        this.altitudeStick = new NormalMode.AltitudeStick(flightClass, 0.0);
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        this.altitude = altitude;
        this.speed = (clockwise ? 1.0 : -1.0) * Math.abs(speed);
    }

    public static OrbitController around(FlightClass flightClass, NavInput in, double centerX, double centerY, double centerZ,
                                         double radius, double speed, boolean clockwise) {
        double r = radius > 0.0 ? radius : Math.hypot(in.x() - centerX, in.z() - centerZ);
        return new OrbitController(flightClass, centerX, centerY, centerZ, r, in.y(), speed, clockwise);
    }

    public double centerX() {
        return this.centerX;
    }

    public double centerY() {
        return this.centerY;
    }

    public double centerZ() {
        return this.centerZ;
    }

    public double radius() {
        return this.radius;
    }

    public double altitude() {
        return this.altitude;
    }

    public double speed() {
        return Math.abs(this.speed);
    }

    public boolean clockwise() {
        return this.speed >= 0.0;
    }

    public void radius(double metres) {
        this.radius = Math.clamp(metres, MIN_RADIUS, MAX_RADIUS);
    }

    public void altitude(double y) {
        this.altitude = y;
    }

    public void speed(double metresPerSecond, boolean clockwise) {
        this.speed = (clockwise ? 1.0 : -1.0) * Math.abs(metresPerSecond);
    }

    void label(String text) {
        this.label = text;
    }

    public double speedLimit() {
        return Math.min(this.flightClass.assistedSpeed(this.flightClass.normalSpeed()), Math.sqrt(LATERAL_ACCEL * this.radius));
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        this.speed -= NavMode.deadband(in.stickRoll(), NormalMode.STICK_DEADBAND) * SPEED_STICK_RATE * dt;
        this.radius(this.radius - NavMode.deadband(in.stickPitch(), NormalMode.STICK_DEADBAND) * RADIUS_STICK_RATE * dt);
        this.altitude += this.altitudeStick.wanted(in) * dt;
        double limit = this.speedLimit();
        this.speed = Math.clamp(this.speed, -limit, limit);
        double dx = in.x() - this.centerX;
        double dz = in.z() - this.centerZ;
        double d = Math.hypot(dx, dz);
        double nx;
        double nz;
        if (d < 0.01) {
            nx = -Compass.forwardX(in.heading());
            nz = -Compass.forwardZ(in.heading());
        } else {
            nx = dx / d;
            nz = dz / d;
        }
        double tx = -nz;
        double tz = nx;
        double error = d - this.radius;
        this.radialIntegral = Math.clamp(this.radialIntegral + error * dt, -6.0, 6.0);
        double radial = Math.clamp(-(RADIAL_GAIN * error + RADIAL_INTEGRAL * this.radialIntegral), -MAX_RADIAL_SPEED, MAX_RADIAL_SPEED);
        double centripetal = this.speed * this.speed * LAG / this.radius;
        double vx = this.speed * tx + nx * (radial - centripetal);
        double vz = this.speed * tz + nz * (radial - centripetal);
        out.velocity(vx, vz);
        out.maxSpeed(Math.abs(this.speed) + MAX_RADIAL_SPEED + 1.0);
        out.holdAltitude(this.altitude);
        double toX = this.centerX - in.x();
        double toZ = this.centerZ - in.z();
        double bearing = Compass.headingTo(toX, toZ);
        double feedForward = Compass.bearingRate(toX, toZ, -in.vx(), -in.vz());
        double yaw = feedForward + HEADING_GAIN * Compass.wrap180(bearing - in.heading());
        out.yawRate(Math.clamp(yaw, -MAX_YAW_RATE, MAX_YAW_RATE));
        out.gimbalTarget(this.centerX, this.centerY, this.centerZ);
        out.label(this.label);
    }

    @Override
    public void reset() {
        this.radialIntegral = 0.0;
        this.lastTime = Double.NaN;
        this.altitudeStick.reset();
    }
}
