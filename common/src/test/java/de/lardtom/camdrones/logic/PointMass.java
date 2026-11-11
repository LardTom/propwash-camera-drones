package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class PointMass {
    static final double G = 9.81;
    static final double DT = 0.01;
    static final double VEL_GAIN = 1.4;
    static final double VEL_INTEGRAL = 0.35;
    static final double INTEGRAL_LIMIT = 2.5;
    static final double POS_GAIN = 0.5;
    static final double MAX_TILT = 20.0;
    static final double ATTITUDE_LAG = 0.12;
    static final double DRAG = 0.15;
    static final double ALT_POS_GAIN = 0.9;
    static final double ALT_VEL_GAIN = 2.2;
    static final double HOLD_CLIMB = 1.5;
    static final double YAW_GAIN = 2.5;
    static final double MAX_YAW = 180.0;
    static final double YAW_LAG = 0.1;
    static final double STICK_YAW_RATE = 200.0;
    static final double DEFAULT_MAX_SPEED = 4.0;
    static final double SENSOR_RANGE = 15.0;

    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean contains(double x, double y, double z) {
            return x > this.minX && x < this.maxX && y > this.minY && y < this.maxY && z > this.minZ && z < this.maxZ;
        }

        double distanceTo(double x, double y, double z) {
            double dx = Math.max(Math.max(this.minX - x, 0.0), x - this.maxX);
            double dy = Math.max(Math.max(this.minY - y, 0.0), y - this.maxY);
            double dz = Math.max(Math.max(this.minZ - z, 0.0), z - this.maxZ);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    final FlightClass flightClass;
    final NavInput in = new NavInput();
    final NavOutput out = new NavOutput();
    final List<Box> boxes = new ArrayList<>();
    double time;
    double x;
    double y;
    double z;
    double vx;
    double vy;
    double vz;
    double heading;
    double yawRate;
    double windX;
    double windZ;
    double gust;
    double gustPeriod = 7.0;
    double quadraticDrag;
    double ground = 0.0;
    boolean gps = true;
    int satellites = 14;
    double compassError;
    boolean pilot;
    double stickRoll;
    double stickPitch;
    double stickYaw;
    double stickThrottle = NavInput.THROTTLE_CENTER;
    double angleLimit = NavInput.DEFAULT_ANGLE_LIMIT;
    boolean target;
    double targetX;
    double targetY;
    double targetZ;
    double targetVx;
    double targetVy;
    double targetVz;
    private double ix;
    private double iz;
    private double ax;
    private double az;
    private double holdX;
    private double holdZ;
    private boolean holdSet;
    private double altTarget;
    private boolean altSet;
    boolean crashed;

    PointMass(FlightClass flightClass) {
        this.flightClass = flightClass;
    }

    PointMass at(double x, double y, double z, double heading) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = Compass.normalize(heading);
        return this;
    }

    PointMass wind(double x, double z, double gust) {
        this.windX = x;
        this.windZ = z;
        this.gust = gust;
        return this;
    }

    PointMass sticks(double roll, double pitch, double yaw, double throttle) {
        this.pilot = true;
        this.stickRoll = roll;
        this.stickPitch = pitch;
        this.stickYaw = yaw;
        this.stickThrottle = throttle;
        return this;
    }

    PointMass target(double x, double y, double z, double vx, double vy, double vz) {
        this.target = true;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetVx = vx;
        this.targetVy = vy;
        this.targetVz = vz;
        return this;
    }

    boolean grounded() {
        return this.y <= this.ground + 1.0E-6 && this.vy <= 1.0E-6;
    }

    double speed() {
        return Math.hypot(this.vx, this.vz);
    }

    NavInput input() {
        this.in.time(this.time).position(this.x, this.y, this.z).velocity(this.vx, this.vy, this.vz).attitude(this.heading, 0.0, 0.0)
                .gps(this.gps, this.satellites).compass(Math.abs(this.compassError) < 15.0, this.compassError).heightAboveGround(this.y - this.ground).grounded(this.grounded()).pilot(this.pilot)
                .sticks(this.stickRoll, this.stickPitch, this.stickYaw, this.stickThrottle).angleLimit(this.angleLimit);
        for (NavInput.Sensor sensor : NavInput.Sensor.values()) {
            this.in.obstacle(sensor, this.sense(sensor));
        }
        if (this.target) {
            this.in.target(this.targetX, this.targetY, this.targetZ, this.targetVx, this.targetVy, this.targetVz);
        } else {
            this.in.clearTarget();
        }
        return this.in;
    }

    private double sense(NavInput.Sensor sensor) {
        if (!this.flightClass.allAroundSensors() && sensor != NavInput.Sensor.DOWN) {
            return Double.NaN;
        }
        double dx;
        double dy = 0.0;
        double dz;
        switch (sensor) {
            case FORWARD -> {
                dx = Compass.forwardX(this.heading);
                dz = Compass.forwardZ(this.heading);
            }
            case BACK -> {
                dx = -Compass.forwardX(this.heading);
                dz = -Compass.forwardZ(this.heading);
            }
            case RIGHT -> {
                dx = Compass.rightX(this.heading);
                dz = Compass.rightZ(this.heading);
            }
            case LEFT -> {
                dx = -Compass.rightX(this.heading);
                dz = -Compass.rightZ(this.heading);
            }
            case UP -> {
                dx = 0.0;
                dz = 0.0;
                dy = 1.0;
            }
            default -> {
                dx = 0.0;
                dz = 0.0;
                dy = -1.0;
            }
        }
        double best = Double.POSITIVE_INFINITY;
        if (dy < 0.0) {
            best = this.y - this.ground;
        }
        for (Box box : this.boxes) {
            best = Math.min(best, ray(box, this.x, this.y, this.z, dx, dy, dz));
        }
        return best > SENSOR_RANGE ? Double.POSITIVE_INFINITY : best;
    }

    private static double ray(Box b, double ox, double oy, double oz, double dx, double dy, double dz) {
        double tMin = 0.0;
        double tMax = Double.POSITIVE_INFINITY;
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double[] lo = {b.minX(), b.minY(), b.minZ()};
        double[] hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-12) {
                if (o[i] < lo[i] || o[i] > hi[i]) {
                    return Double.POSITIVE_INFINITY;
                }
                continue;
            }
            double t1 = (lo[i] - o[i]) / d[i];
            double t2 = (hi[i] - o[i]) / d[i];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }
        return tMax >= tMin ? tMin : Double.POSITIVE_INFINITY;
    }

    void step(NavOutput o) {
        double windNowX = this.windX;
        double windNowZ = this.windZ;
        if (this.gust != 0.0) {
            double g = this.gust * Math.sin(2.0 * Math.PI * this.time / this.gustPeriod);
            double w = Math.hypot(this.windX, this.windZ);
            double ux = w > 1.0E-9 ? this.windX / w : 1.0;
            double uz = w > 1.0E-9 ? this.windZ / w : 0.0;
            windNowX += ux * g;
            windNowZ += uz * g;
        }
        double wantAx;
        double wantAz;
        double aMax = G * Math.tan(Math.toRadians(MAX_TILT));
        if (o.hasSticks()) {
            double pitch = Math.toRadians(o.stickPitch() * this.angleLimit);
            double roll = Math.toRadians(o.stickRoll() * this.angleLimit);
            double f = G * Math.tan(pitch);
            double r = G * Math.tan(roll);
            wantAx = f * Compass.forwardX(this.heading) + r * Compass.rightX(this.heading);
            wantAz = f * Compass.forwardZ(this.heading) + r * Compass.rightZ(this.heading);
            this.holdSet = false;
            this.ix = 0.0;
            this.iz = 0.0;
        } else if (o.horizontal() != NavOutput.Horizontal.NONE) {
            double cmdX;
            double cmdZ;
            if (o.horizontal() == NavOutput.Horizontal.POSITION) {
                this.holdX = o.targetX();
                this.holdZ = o.targetZ();
                this.holdSet = true;
                cmdX = POS_GAIN * (this.holdX - this.x);
                cmdZ = POS_GAIN * (this.holdZ - this.z);
            } else {
                cmdX = o.velocityX();
                cmdZ = o.velocityZ();
                this.holdX = this.x;
                this.holdZ = this.z;
                this.holdSet = true;
            }
            double limit = Double.isNaN(o.maxSpeed()) ? DEFAULT_MAX_SPEED : o.maxSpeed();
            double c = Math.hypot(cmdX, cmdZ);
            if (c > limit) {
                cmdX *= limit / c;
                cmdZ *= limit / c;
            }
            double ex = cmdX - this.vx;
            double ez = cmdZ - this.vz;
            if (Math.hypot(VEL_GAIN * ex + this.ix, VEL_GAIN * ez + this.iz) < aMax) {
                this.ix += VEL_INTEGRAL * ex * DT;
                this.iz += VEL_INTEGRAL * ez * DT;
                double im = Math.hypot(this.ix, this.iz);
                if (im > INTEGRAL_LIMIT) {
                    this.ix *= INTEGRAL_LIMIT / im;
                    this.iz *= INTEGRAL_LIMIT / im;
                }
            }
            wantAx = VEL_GAIN * ex + this.ix;
            wantAz = VEL_GAIN * ez + this.iz;
            double a = Math.hypot(wantAx, wantAz);
            if (a > aMax) {
                wantAx *= aMax / a;
                wantAz *= aMax / a;
            }
        } else {
            wantAx = 0.0;
            wantAz = 0.0;
        }
        this.ax += (wantAx - this.ax) * DT / ATTITUDE_LAG;
        this.az += (wantAz - this.az) * DT / ATTITUDE_LAG;
        boolean onGround = this.grounded();
        double wantVy;
        double climbLimit = HOLD_CLIMB;
        if (o.vertical() == NavOutput.Vertical.CLIMB) {
            double climb = o.climbRate();
            if (!this.altSet) {
                this.altTarget = this.y;
                this.altSet = true;
            }
            this.altTarget = Math.clamp(this.altTarget + climb * DT, this.y - 1.0, this.y + 1.0);
            climbLimit = Math.max(HOLD_CLIMB, Math.abs(climb));
            wantVy = Math.clamp(climb + ALT_POS_GAIN * (this.altTarget - this.y), -climbLimit, climbLimit);
        } else if (o.vertical() == NavOutput.Vertical.ALTITUDE) {
            this.altTarget = o.altitudeTarget();
            this.altSet = true;
            wantVy = Math.clamp(ALT_POS_GAIN * (this.altTarget - this.y), -climbLimit, climbLimit);
        } else {
            if (!this.altSet) {
                this.altTarget = this.y;
                this.altSet = true;
            }
            wantVy = Math.clamp(ALT_POS_GAIN * (this.altTarget - this.y), -climbLimit, climbLimit);
        }
        if (o.landing()) {
            wantVy = Math.min(wantVy, -0.5);
        }
        double ay = Math.clamp(ALT_VEL_GAIN * (wantVy - this.vy), -0.5 * G, 0.8 * G);
        if (onGround && wantVy <= 0.0) {
            this.vx = 0.0;
            this.vz = 0.0;
            this.vy = 0.0;
            this.ax = 0.0;
            this.az = 0.0;
            this.ix = 0.0;
            this.iz = 0.0;
            this.altTarget = this.y;
        } else {
            double airX = windNowX - this.vx;
            double airZ = windNowZ - this.vz;
            double air = Math.hypot(airX, airZ);
            this.vx += (this.ax + DRAG * airX + this.quadraticDrag * air * airX) * DT;
            this.vz += (this.az + DRAG * airZ + this.quadraticDrag * air * airZ) * DT;
            this.vy += ay * DT;
        }
        this.x += this.vx * DT;
        this.y += this.vy * DT;
        this.z += this.vz * DT;
        if (this.y < this.ground) {
            this.y = this.ground;
            this.vy = Math.max(0.0, this.vy);
        }
        double wantYaw;
        switch (o.yaw()) {
            case HEADING -> wantYaw = Math.clamp(YAW_GAIN * Compass.wrap180(o.headingTarget() - this.heading), -MAX_YAW, MAX_YAW);
            case RATE -> wantYaw = o.yawRateTarget();
            default -> wantYaw = o.hasSticks() ? o.stickYaw() * STICK_YAW_RATE : 0.0;
        }
        this.yawRate += (wantYaw - this.yawRate) * DT / YAW_LAG;
        this.heading = Compass.normalize(this.heading + this.yawRate * DT);
        for (Box box : this.boxes) {
            if (box.contains(this.x, this.y, this.z)) {
                this.crashed = true;
            }
        }
        this.targetX += this.targetVx * DT;
        this.targetY += this.targetVy * DT;
        this.targetZ += this.targetVz * DT;
        this.time += DT;
    }

    void run(BiConsumer<NavInput, NavOutput> controller, double seconds, Consumer<PointMass> probe) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            NavInput input = this.input();
            this.out.reset();
            controller.accept(input, this.out);
            this.step(this.out);
            if (probe != null) {
                probe.accept(this);
            }
        }
    }

    void run(BiConsumer<NavInput, NavOutput> controller, double seconds) {
        this.run(controller, seconds, null);
    }
}
