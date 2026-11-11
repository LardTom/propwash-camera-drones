package de.lardtom.camdrones.logic.nav;

public final class ObstacleGuard {
    public static final double SENSOR_RANGE = 15.0;
    public static final double STOP_DISTANCE = 1.5;
    public static final double SLOW_GAIN = 1.2;
    public static final double WARN_DISTANCE = 8.0;
    public static final double LOOP_ACCEL = 3.0;
    public static final double LOOP_LAG = 0.5;
    public static final double LOOP_MARGIN = 0.8;
    public static final double BRAKE_ANGLE = HorizontalControl.MAX_TILT;
    public static final double BRAKE_ACCEL = HorizontalControl.maxAcceleration();
    public static final double REACTION = 0.25;
    public static final double CLIMB_LIMIT = 10.0;
    public static final double CLIMB_TRIGGER = STOP_DISTANCE + 1.0;
    public static final double CLEAR_DISTANCE = 4.0;
    public static final double UP_CLEARANCE = 3.0;
    public static final double FLOOR_MARGIN = 1.0;
    public static final double FLOOR_DISTANCE = 8.0;
    public static final double GROUND_SLOW_SPEED = 0.5;
    public static final double GROUND_SLOW_DISTANCE = 1.0;
    public static final double GROUND_SLOW_GAIN = 0.8;
    public static final double HOLD_GAIN = 0.5;
    public static final double REPEL_GAIN = 2.0;

    public enum Mode {
        STOP,
        CLIMB_OVER
    }

    private boolean emergency;
    private boolean braking;
    private boolean climbing;
    private boolean gaveUp;
    private double climbStartY;
    private boolean floor;
    private double floorY;
    private double floorX;
    private double floorZ;
    private double nearest = Double.POSITIVE_INFINITY;
    private NavInput.Sensor nearestSensor;

    public static double brakeAngle(NavInput in) {
        return HorizontalControl.maxTilt(in);
    }

    public static double brakeAcceleration(NavInput in) {
        return HorizontalControl.maxAcceleration(in);
    }

    public static double emergencyLimit(double gap) {
        return emergencyLimit(gap, BRAKE_ACCEL);
    }

    public static double emergencyLimit(double gap, double brake) {
        return stopSpeed(gap, brake, REACTION);
    }

    public static double loopLimit(double gap) {
        return loopLimit(gap, BRAKE_ACCEL);
    }

    public static double loopLimit(double gap, double brake) {
        return stopSpeed(gap - LOOP_MARGIN, Math.min(LOOP_ACCEL, brake), LOOP_LAG);
    }

    public static double stopSpeed(double gap, double accel, double reaction) {
        if (gap <= 0.0) {
            return 0.0;
        }
        return accel * (-reaction + Math.sqrt(reaction * reaction + 2.0 * gap / accel));
    }

    public static double sightNeeded(double speed) {
        return sightNeeded(speed, BRAKE_ACCEL);
    }

    public static double sightNeeded(double speed, double brake) {
        double v = Math.max(0.0, speed);
        return STOP_DISTANCE + v * REACTION + v * v / (2.0 * brake);
    }

    public static double commandLimit(double distance) {
        return commandLimit(distance, SENSOR_RANGE);
    }

    public static double commandLimit(double distance, double range) {
        return commandLimit(distance, range, BRAKE_ACCEL);
    }

    public static double commandLimit(double distance, double range, double brake) {
        if (Double.isNaN(distance)) {
            return Double.POSITIVE_INFINITY;
        }
        double reach = Double.isNaN(range) ? SENSOR_RANGE : Math.min(range, SENSOR_RANGE);
        double free = emergencyLimit(reach - STOP_DISTANCE, brake);
        if (distance >= reach) {
            return free;
        }
        double gap = distance - STOP_DISTANCE;
        if (gap <= 0.0) {
            return 0.0;
        }
        return Math.min(SLOW_GAIN * gap, Math.min(loopLimit(gap, brake), free));
    }

    public boolean emergency() {
        return this.emergency;
    }

    public boolean braking() {
        return this.braking;
    }

    public boolean climbing() {
        return this.climbing;
    }

    public boolean floorActive() {
        return this.floor;
    }

    public double nearestDistance() {
        return this.nearest;
    }

    public NavInput.Sensor nearestSensor() {
        return this.nearestSensor;
    }

    public void reset() {
        this.emergency = false;
        this.braking = false;
        this.climbing = false;
        this.gaveUp = false;
        this.floor = false;
        this.nearest = Double.POSITIVE_INFINITY;
        this.nearestSensor = null;
    }

    private static double sensor(NavInput in, NavInput.Sensor sensor, boolean allAround) {
        if (!allAround && sensor != NavInput.Sensor.DOWN) {
            return Double.NaN;
        }
        return in.obstacle(sensor);
    }

    public void apply(NavInput in, NavOutput out, FlightClass flightClass, Mode mode, boolean automatic) {
        boolean allAround = flightClass.allAroundSensors();
        double forward = sensor(in, NavInput.Sensor.FORWARD, allAround);
        double back = sensor(in, NavInput.Sensor.BACK, allAround);
        double left = sensor(in, NavInput.Sensor.LEFT, allAround);
        double right = sensor(in, NavInput.Sensor.RIGHT, allAround);
        double up = sensor(in, NavInput.Sensor.UP, allAround);
        double down = sensor(in, NavInput.Sensor.DOWN, allAround);
        this.trackNearest(forward, back, left, right);
        double brake = brakeAcceleration(in);
        this.braking = false;
        if (out.hasSticks()) {
            this.emergency = false;
            this.climbing = false;
            this.gaveUp = false;
            return;
        }
        double h = in.heading();
        double fx = Compass.forwardX(h);
        double fz = Compass.forwardZ(h);
        double rx = Compass.rightX(h);
        double rz = Compass.rightZ(h);
        double actualForward = in.vx() * fx + in.vz() * fz;
        double actualRight = in.vx() * rx + in.vz() * rz;
        double towardForward = Math.max(0.0, actualForward);
        double towardBack = Math.max(0.0, -actualForward);
        double towardRight = Math.max(0.0, actualRight);
        double towardLeft = Math.max(0.0, -actualRight);
        boolean overspeed = over(towardForward, forward, brake) || over(towardBack, back, brake) || over(towardRight, right, brake)
                || over(towardLeft, left, brake);
        if (overspeed) {
            this.emergency = true;
        } else if (this.emergency && fine(towardForward, forward, brake) && fine(towardBack, back, brake) && fine(towardRight, right, brake)
                && fine(towardLeft, left, brake)) {
            this.emergency = false;
        }
        double wantX;
        double wantZ;
        boolean position = out.horizontal() == NavOutput.Horizontal.POSITION;
        if (position) {
            wantX = HOLD_GAIN * (out.targetX() - in.x());
            wantZ = HOLD_GAIN * (out.targetZ() - in.z());
            double cap = Double.isNaN(out.maxSpeed()) ? HoldController.HOLD_SPEED : out.maxSpeed();
            double v = Math.hypot(wantX, wantZ);
            if (v > cap) {
                wantX *= cap / v;
                wantZ *= cap / v;
            }
        } else if (out.horizontal() == NavOutput.Horizontal.VELOCITY) {
            wantX = out.velocityX();
            wantZ = out.velocityZ();
        } else {
            wantX = 0.0;
            wantZ = 0.0;
        }
        double f = wantX * fx + wantZ * fz;
        double r = wantX * rx + wantZ * rz;
        double wantedForward = f;
        double limitedF = limit(f, forward, in.range(NavInput.Sensor.FORWARD), back, in.range(NavInput.Sensor.BACK), brake);
        double limitedR = limit(r, right, in.range(NavInput.Sensor.RIGHT), left, in.range(NavInput.Sensor.LEFT), brake);
        boolean overClimb = mode == Mode.CLIMB_OVER && allAround && !Double.isNaN(forward) && !Double.isNaN(up);
        this.overClimb(in, out, flightClass, overClimb, wantedForward, forward, up);
        if (this.climbing || this.gaveUp) {
            limitedF = Math.min(limitedF, 0.0);
        }
        boolean limited = limitedF != f || limitedR != r;
        if (limited && out.horizontal() != NavOutput.Horizontal.NONE) {
            out.velocity(limitedF * fx + limitedR * rx, limitedF * fz + limitedR * rz);
        }
        this.braking = this.emergency || limited && !this.climbing;
        if (this.emergency) {
            double speed = Math.hypot(actualForward, actualRight);
            double k = Math.min(1.0, brakeAngle(in) / in.angleLimit());
            double pitch = speed > 1.0E-6 ? -k * actualForward / speed : 0.0;
            double roll = speed > 1.0E-6 ? -k * actualRight / speed : 0.0;
            out.clearHorizontal();
            out.sticks(roll, pitch, 0.0, NavInput.THROTTLE_CENTER);
            if (out.vertical() == NavOutput.Vertical.NONE) {
                out.holdAltitude(in.y());
            }
        }
        this.vertical(in, out, up, down, automatic);
        if (this.emergency || limited || this.climbing || this.gaveUp || this.nearestInMotion(in, forward, back, left, right)) {
            out.warn(NavOutput.Warning.OBSTACLE);
        }
        boolean blockedF = f > 0.0 && limitedF <= 0.0 || f < 0.0 && limitedF >= 0.0;
        boolean blockedR = r > 0.0 && limitedR <= 0.0 || r < 0.0 && limitedR >= 0.0;
        if (this.gaveUp || !this.climbing && (blockedF || blockedR)) {
            out.warn(NavOutput.Warning.OBSTACLE_STOP);
        }
    }

    private void overClimb(NavInput in, NavOutput out, FlightClass flightClass, boolean allowed, double wantedForward, double forward,
                           double up) {
        boolean wantsForward = wantedForward > 0.3;
        boolean blocked = forward < CLIMB_TRIGGER;
        boolean upFree = up > UP_CLEARANCE;
        if (!allowed || !wantsForward) {
            this.climbing = false;
            this.gaveUp = false;
        } else if (this.climbing) {
            if (forward > CLEAR_DISTANCE) {
                this.climbing = false;
                this.floor = true;
                this.floorY = in.y() + FLOOR_MARGIN;
                this.floorX = in.x();
                this.floorZ = in.z();
            } else if (in.y() - this.climbStartY >= CLIMB_LIMIT || !upFree) {
                this.climbing = false;
                this.gaveUp = true;
            }
        } else if (!this.gaveUp && blocked && upFree) {
            this.climbing = true;
            this.climbStartY = in.y();
        } else if (this.gaveUp && forward > CLEAR_DISTANCE) {
            this.gaveUp = false;
        }
        if (this.climbing) {
            double room = this.climbStartY + CLIMB_LIMIT - in.y();
            out.climb(Math.clamp(room, 0.5, flightClass.climbSpeed() * 0.5));
        }
        if (this.floor && Math.hypot(in.x() - this.floorX, in.z() - this.floorZ) > FLOOR_DISTANCE) {
            this.floor = false;
        }
        if (this.floor && !this.climbing) {
            if (out.vertical() == NavOutput.Vertical.ALTITUDE && out.altitudeTarget() < this.floorY) {
                out.holdAltitude(this.floorY);
            } else if (out.vertical() == NavOutput.Vertical.CLIMB && in.y() + out.climbRate() < this.floorY) {
                out.climb(Math.max(out.climbRate(), this.floorY - in.y()));
            }
        }
    }

    private void vertical(NavInput in, NavOutput out, double up, double down, boolean automatic) {
        if (out.landing()) {
            return;
        }
        if (!Double.isNaN(up)) {
            double climbLimit = commandLimit(up);
            if (out.vertical() == NavOutput.Vertical.CLIMB && out.climbRate() > climbLimit) {
                out.climb(climbLimit);
            } else if (out.vertical() == NavOutput.Vertical.ALTITUDE && up < SENSOR_RANGE) {
                double ceiling = in.y() + Math.max(0.0, up - STOP_DISTANCE);
                if (out.altitudeTarget() > ceiling) {
                    out.holdAltitude(ceiling);
                }
            }
        }
        if (!Double.isNaN(down) && down < SENSOR_RANGE) {
            double sink;
            double floorAltitude;
            if (automatic) {
                sink = commandLimit(down);
                floorAltitude = in.y() - down + STOP_DISTANCE;
            } else {
                sink = Math.max(GROUND_SLOW_SPEED, GROUND_SLOW_GAIN * (down - GROUND_SLOW_DISTANCE));
                floorAltitude = Double.NEGATIVE_INFINITY;
            }
            if (out.vertical() == NavOutput.Vertical.CLIMB && -out.climbRate() > sink) {
                out.climb(-sink);
            } else if (out.vertical() == NavOutput.Vertical.ALTITUDE && out.altitudeTarget() < floorAltitude && !in.grounded()
                    && out.altitudeTarget() < in.y()) {
                out.holdAltitude(Math.min(in.y(), floorAltitude));
            }
        }
    }

    public static double allowedToward(double distance) {
        return allowedToward(distance, SENSOR_RANGE);
    }

    public static double allowedToward(double distance, double range) {
        return allowedToward(distance, range, BRAKE_ACCEL);
    }

    public static double allowedToward(double distance, double range, double brake) {
        if (Double.isNaN(distance)) {
            return Double.POSITIVE_INFINITY;
        }
        double zone = STOP_DISTANCE + LOOP_MARGIN;
        if (distance < zone) {
            return -REPEL_GAIN * (zone - distance);
        }
        return commandLimit(distance, range, brake);
    }

    private static double limit(double component, double positiveSensor, double positiveRange, double negativeSensor, double negativeRange,
                                double brake) {
        double high = allowedToward(positiveSensor, positiveRange, brake);
        double low = -allowedToward(negativeSensor, negativeRange, brake);
        if (low > high) {
            return (low + high) / 2.0;
        }
        return Math.clamp(component, low, high);
    }

    private static boolean over(double toward, double distance, double brake) {
        if (Double.isNaN(distance) || distance >= SENSOR_RANGE || toward <= 0.0) {
            return false;
        }
        return toward > emergencyLimit(distance - STOP_DISTANCE, brake) + 0.05;
    }

    private static boolean fine(double toward, double distance, double brake) {
        if (Double.isNaN(distance) || distance >= SENSOR_RANGE || toward <= 0.3) {
            return true;
        }
        return toward <= loopLimit(distance - STOP_DISTANCE, brake);
    }

    private void trackNearest(double forward, double back, double left, double right) {
        this.nearest = Double.POSITIVE_INFINITY;
        this.nearestSensor = null;
        this.consider(NavInput.Sensor.FORWARD, forward);
        this.consider(NavInput.Sensor.BACK, back);
        this.consider(NavInput.Sensor.LEFT, left);
        this.consider(NavInput.Sensor.RIGHT, right);
    }

    private void consider(NavInput.Sensor sensor, double distance) {
        if (!Double.isNaN(distance) && distance < this.nearest) {
            this.nearest = distance;
            this.nearestSensor = sensor;
        }
    }

    private boolean nearestInMotion(NavInput in, double forward, double back, double left, double right) {
        double h = in.heading();
        double f = in.vx() * Compass.forwardX(h) + in.vz() * Compass.forwardZ(h);
        double r = in.vx() * Compass.rightX(h) + in.vz() * Compass.rightZ(h);
        return f > 0.3 && forward < WARN_DISTANCE || f < -0.3 && back < WARN_DISTANCE || r > 0.3 && right < WARN_DISTANCE
                || r < -0.3 && left < WARN_DISTANCE;
    }
}
