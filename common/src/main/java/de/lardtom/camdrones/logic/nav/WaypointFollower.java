package de.lardtom.camdrones.logic.nav;

import de.lardtom.camdrones.logic.gimbal.GimbalAim;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;

public final class WaypointFollower implements NavMode {
    public static final double ARRIVE_RADIUS = 0.5;
    public static final double PASS_RADIUS = 1.0;
    public static final double SETTLE_SPEED = 0.5;
    public static final double BRAKE_ACCEL = 1.5;
    public static final double APPROACH_GAIN = 0.8;
    public static final double CORNER_DEVIATION = 0.5;
    public static final double LATERAL_ACCEL = 2.0;
    public static final double MAX_TURN = 150.0;
    public static final double CROSS_GAIN = 1.0;
    public static final double MAX_CROSS_SPEED = 3.0;
    public static final double VERTICAL_GAIN = 1.0;
    public static final double TAKEOFF_HEIGHT = 2.0;
    public static final double HEADING_RATE = 60.0;
    public static final double LAND_SPEED = 1.0;
    public static final double MIN_HORIZONTAL = 0.3;
    public static final double ANTICIPATION = 0.5;
    public static final double RAMP_ACCEL = 1.5;
    public static final double TURN_LEAD = 0.6;
    public static final String LABEL_TAKEOFF = "TAKEOFF";
    public static final String LABEL_RETURN = "RETURN";
    public static final String LABEL_LAND = "LAND";

    public enum Phase {
        TAKEOFF,
        FLY,
        WAIT,
        RETURN,
        LAND,
        DONE
    }

    private final FlightClass flightClass;
    private final Mission mission;
    private final HoldController hold = new HoldController();
    private int index;
    private int laps;
    private Phase phase = Phase.FLY;
    private boolean started;
    private boolean startedGrounded;
    private double startX;
    private double startY;
    private double startZ;
    private double fromX;
    private double fromY;
    private double fromZ;
    private double fromHeading;
    private double returnY;
    private double waitUntil;
    private double commandHeading;
    private double tilt;
    private boolean segmentStarted;
    private double alongCommand;
    private double lastTime = Double.NaN;
    private Waypoint.Action pending = Waypoint.Action.NONE;
    private boolean entryKnown;
    private CurvedTurn entry;
    private double entrySpeed;
    private boolean turning;
    private CurvedTurn exit;
    private double exitSpeed;
    private double fromShift;
    private double toShift;

    public WaypointFollower(FlightClass flightClass, Mission mission, int startIndex) {
        this.flightClass = flightClass;
        this.mission = mission;
        this.index = mission.isEmpty() ? 0 : Math.clamp(startIndex, 0, mission.size() - 1);
    }

    public Mission mission() {
        return this.mission;
    }

    public int index() {
        return this.index;
    }

    public int laps() {
        return this.laps;
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean done() {
        return this.started && this.phase == Phase.DONE;
    }

    public boolean complete() {
        return this.started && (this.phase == Phase.DONE || this.phase == Phase.LAND);
    }

    public double gimbalTilt() {
        return this.tilt;
    }

    public double startX() {
        return this.startX;
    }

    public double startY() {
        return this.startY;
    }

    public double startZ() {
        return this.startZ;
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        if (!this.started) {
            this.start(in);
        }
        switch (this.phase) {
            case TAKEOFF -> this.takeoff(in, out);
            case FLY -> this.fly(in, out, dt);
            case WAIT -> this.waitAt(in, out, dt);
            case RETURN -> this.returnToStart(in, out, dt);
            case LAND -> this.landAt(in, out);
            case DONE -> this.hold.update(in, out);
        }
        out.action(this.pending);
        this.pending = Waypoint.Action.NONE;
        out.label(this.label());
    }

    public String label() {
        return switch (this.phase) {
            case TAKEOFF -> LABEL_TAKEOFF;
            case FLY, WAIT -> "WP " + (this.index + 1) + "/" + this.mission.size();
            case RETURN -> LABEL_RETURN;
            case LAND -> LABEL_LAND;
            case DONE -> HoldController.LABEL;
        };
    }

    private void start(NavInput in) {
        this.started = true;
        this.startX = in.x();
        this.startY = in.y();
        this.startZ = in.z();
        this.startedGrounded = in.grounded();
        this.setFrom(in.x(), in.y(), in.z(), in.heading());
        this.commandHeading = in.heading();
        if (this.mission.isEmpty()) {
            this.phase = Phase.DONE;
            this.hold.holdAt(in.x(), in.y(), in.z(), in.heading());
            return;
        }
        this.tilt = tiltAt(this.mission, this.index, in.x(), in.y(), in.z());
        this.phase = this.startedGrounded ? Phase.TAKEOFF : Phase.FLY;
    }

    private void setFrom(double x, double y, double z, double heading) {
        this.segmentStarted = false;
        this.entryKnown = false;
        this.entry = null;
        this.turning = false;
        this.exit = null;
        this.fromShift = 0.0;
        this.toShift = 0.0;
        this.fromX = x;
        this.fromY = y;
        this.fromZ = z;
        this.fromHeading = heading;
    }

    private void takeoff(NavInput in, NavOutput out) {
        double target = Math.max(this.mission.waypoint(this.index).y(), this.startY + TAKEOFF_HEIGHT);
        out.holdPosition(this.startX, this.startZ);
        out.maxSpeed(HoldController.HOLD_SPEED);
        out.climb(Math.clamp(VERTICAL_GAIN * (target - in.y()) + 0.3, 0.3, this.flightClass.climbSpeed()));
        out.heading(this.commandHeading);
        out.gimbalTilt(this.tilt);
        if (in.y() >= target - 0.3) {
            this.phase = Phase.FLY;
            this.setFrom(in.x(), in.y(), in.z(), this.commandHeading);
        }
    }

    private void fly(NavInput in, NavOutput out, double dt) {
        Waypoint w = this.mission.waypoint(this.index);
        double speed = this.segmentSpeed(this.index);
        double endSpeed = 0.0;
        double switchDistance = 0.0;
        int next = this.next(this.index);
        boolean stop = !this.mission.smooth() || w.waitDs() > 0 || next < 0;
        CurvedTurn turn = null;
        if (!stop) {
            if (!this.entryKnown) {
                this.entryKnown = true;
                this.entry = CurvedTurn.at(this.mission, this.index, this.fromX, this.fromY, this.fromZ);
                this.entrySpeed = this.entry == null ? 0.0 : CurvedTurn.plannedSpeed(this.flightClass, this.mission, this.index, this.entry);
            }
            turn = this.entry;
            if (turn != null) {
                double stretch = Math.sqrt(1.0 + turn.inSlope() * turn.inSlope());
                endSpeed = this.entrySpeed * stretch;
                switchDistance = turn.tangent() * stretch;
            } else {
                double[] corner = corner(this.fromX, this.fromY, this.fromZ, w, this.mission.waypoint(next));
                if (corner == null) {
                    stop = true;
                } else {
                    endSpeed = Math.min(Math.min(corner[0], speed), this.segmentSpeed(next));
                    switchDistance = corner[1];
                }
            }
        }
        this.toShift = turn == null ? 0.0 : turn.apexShift();
        Segment segment = null;
        if (this.exit != null) {
            if (this.exit.afterExit(in.x(), in.z()) || this.exit.progress(in.x(), in.z()) >= 1.0) {
                this.exit = null;
            } else {
                segment = this.trackTurn(in, out, this.exit, this.exitSpeed, speed, false, dt);
            }
        }
        if (segment == null && turn != null && (this.turning || !turn.beforeEntry(in.x(), in.z()))) {
            this.turning = true;
            segment = this.trackTurn(in, out, turn, this.entrySpeed, speed, true, dt);
            this.steer(in, out, w, segment, dt);
            if (turn.progress(in.x(), in.z()) >= 0.5 || turn.afterExit(in.x(), in.z())) {
                double passedSpeed = this.entrySpeed;
                this.arrive(in, w);
                this.exit = turn;
                this.exitSpeed = passedSpeed;
                this.fromShift = turn.apexShift();
            }
            return;
        }
        if (segment == null) {
            segment = this.track(in, out, w.x(), w.y(), w.z(), speed, endSpeed, switchDistance, dt);
        }
        this.steer(in, out, w, segment, dt);
        if (turn != null) {
            return;
        }
        double distance = w.distanceTo(in.x(), in.y(), in.z());
        if (stop) {
            boolean settled = distance <= ARRIVE_RADIUS && in.horizontalSpeed() < SETTLE_SPEED;
            boolean overshot = segment.remaining < 0.0 && distance <= PASS_RADIUS;
            if (settled || overshot || distance <= ARRIVE_RADIUS * 0.4) {
                this.arrive(in, w);
            }
        } else if (segment.remaining <= switchDistance && distance <= Math.max(PASS_RADIUS, switchDistance * 1.5)
                || segment.remaining < 0.0 && distance <= PASS_RADIUS * 2.0) {
            this.arrive(in, w);
        }
    }

    private void steer(NavInput in, NavOutput out, Waypoint w, Segment segment, double dt) {
        double progress = segment.progress;
        if (this.mission.smooth()) {
            double sx = w.x() - this.fromX;
            double sz = w.z() - this.fromZ;
            double hs = Math.hypot(sx, sz);
            if (hs >= CurvedTurn.MIN_LEG) {
                progress = remap(((in.x() - this.fromX) * sx + (in.z() - this.fromZ) * sz) / (hs * hs), this.fromShift / hs, 1.0 - this.toShift / hs);
            }
        }
        double desired;
        switch (w.heading()) {
            case COURSE -> desired = segment.horizontal > 0.5 && segment.speed > 0.5 ? Compass.headingTo(segment.ux, segment.uz)
                    : this.commandHeading;
            case FIXED -> desired = Compass.lerp(this.fromHeading, w.yawDeg(), NavMode.smoothstep(progress));
            case POI -> {
                PointOfInterest p = this.mission.pointsOfInterest().get(w.poi());
                desired = Compass.headingTo(p.x() - in.x(), p.z() - in.z());
                out.gimbalTarget(p.x(), p.y(), p.z());
            }
            default -> desired = Double.NaN;
        }
        if (Double.isNaN(desired)) {
            this.commandHeading = in.heading();
            out.yawRate(0.0);
        } else {
            this.commandHeading = Compass.approach(this.commandHeading, desired, HEADING_RATE * dt);
            out.heading(this.commandHeading);
        }
        this.tilt = tiltAt(this.mission, this.index, in.x(), in.y(), in.z());
        out.gimbalTilt(this.tilt);
    }

    private void arrive(NavInput in, Waypoint w) {
        this.pending = w.action();
        if (w.waitDs() > 0) {
            this.phase = Phase.WAIT;
            this.waitUntil = in.time() + w.waitSeconds();
            this.hold.holdAt(w.x(), w.y(), w.z(), this.commandHeading);
            return;
        }
        this.advance(w);
    }

    private void waitAt(NavInput in, NavOutput out, double dt) {
        Waypoint w = this.mission.waypoint(this.index);
        this.hold.update(in, out);
        if (w.heading() == Waypoint.Heading.POI) {
            PointOfInterest p = this.mission.pointsOfInterest().get(w.poi());
            this.commandHeading = Compass.approach(this.commandHeading, Compass.headingTo(p.x() - in.x(), p.z() - in.z()),
                    HEADING_RATE * dt);
            out.heading(this.commandHeading);
            out.gimbalTarget(p.x(), p.y(), p.z());
        } else if (w.heading() == Waypoint.Heading.FREE) {
            out.yawRate(0.0);
        }
        this.tilt = tiltAt(this.mission, this.index, in.x(), in.y(), in.z());
        out.gimbalTilt(this.tilt);
        if (in.time() >= this.waitUntil) {
            this.advance(w);
        }
    }

    private void advance(Waypoint w) {
        this.setFrom(w.x(), w.y(), w.z(), this.commandHeading);
        int next = this.next(this.index);
        if (next >= 0) {
            if (next <= this.index) {
                this.laps++;
            }
            this.index = next;
            this.phase = Phase.FLY;
            return;
        }
        switch (this.mission.end()) {
            case HOVER -> {
                this.phase = Phase.DONE;
                this.hold.holdAt(w.x(), w.y(), w.z(), this.commandHeading);
            }
            case START -> {
                this.phase = Phase.RETURN;
                this.returnY = Math.max(w.y(), this.startY + (this.startedGrounded ? TAKEOFF_HEIGHT : 0.0));
            }
            case LAND -> {
                this.phase = Phase.LAND;
                this.hold.holdAt(w.x(), w.y(), w.z(), this.commandHeading);
            }
        }
    }

    private void returnToStart(NavInput in, NavOutput out, double dt) {
        double speed = this.flightClass.assistedSpeed(this.mission.baseSpeed());
        Segment segment = this.track(in, out, this.startX, this.returnY, this.startZ, speed, 0.0, 0.0, dt);
        if (segment.horizontal > 0.5 && segment.speed > 0.5) {
            this.commandHeading = Compass.approach(this.commandHeading, Compass.headingTo(segment.ux, segment.uz), HEADING_RATE * dt);
        }
        out.heading(this.commandHeading);
        out.gimbalTilt(this.tilt);
        double dx = in.x() - this.startX;
        double dy = in.y() - this.returnY;
        double dz = in.z() - this.startZ;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= ARRIVE_RADIUS && in.horizontalSpeed() < SETTLE_SPEED) {
            if (this.startedGrounded) {
                this.phase = Phase.LAND;
                this.hold.holdAt(this.startX, this.startY, this.startZ, this.commandHeading);
            } else {
                this.phase = Phase.DONE;
                this.hold.holdAt(this.startX, this.startY, this.startZ, this.commandHeading);
            }
        }
    }

    private void landAt(NavInput in, NavOutput out) {
        out.holdPosition(this.hold.holdX(), this.hold.holdZ());
        out.maxSpeed(HoldController.HOLD_SPEED);
        double height = in.heightAboveGround();
        double sink = Double.isFinite(height) ? Math.clamp(0.5 * height, 0.5, Math.min(LAND_SPEED * 3.0, this.flightClass.descendSpeed()))
                : LAND_SPEED;
        out.climb(-sink);
        out.heading(this.commandHeading);
        out.land();
        out.gimbalTilt(this.tilt);
        if (in.grounded()) {
            this.phase = Phase.DONE;
            this.hold.holdAt(in.x(), in.y(), in.z(), this.commandHeading);
        }
    }

    private Segment track(NavInput in, NavOutput out, double tx, double ty, double tz, double speed, double endSpeed,
                          double switchDistance, double dt) {
        double sx = tx - this.fromX;
        double sy = ty - this.fromY;
        double sz = tz - this.fromZ;
        double hs = Math.hypot(sx, sz);
        if (hs < MIN_HORIZONTAL) {
            return this.trackVertical(in, out, tx, ty, tz, speed, endSpeed);
        }
        double hx = sx / hs;
        double hz = sz / hs;
        double slope = sy / hs;
        double stretch = Math.sqrt(1.0 + slope * slope);
        double px = in.x() - this.fromX;
        double pz = in.z() - this.fromZ;
        double along = px * hx + pz * hz;
        double remaining = (hs - along) * stretch;
        double ex = px - along * hx;
        double ez = pz - along * hz;
        double actualAlong = in.vx() * hx + in.vz() * hz;
        if (!this.segmentStarted) {
            this.segmentStarted = true;
            this.alongCommand = Math.max(0.0, actualAlong);
        }
        double toGo = Math.max(0.0, remaining - switchDistance - ANTICIPATION * Math.max(0.0, actualAlong) * stretch);
        double v = Math.min(speed, Math.min(Math.sqrt(endSpeed * endSpeed + 2.0 * BRAKE_ACCEL * toGo), endSpeed + APPROACH_GAIN * toGo));
        if (remaining <= 0.0) {
            v = Math.min(v, endSpeed);
        }
        double vh = v / stretch;
        if (slope > 1.0E-6) {
            vh = Math.min(vh, this.flightClass.climbSpeed() / slope);
        } else if (slope < -1.0E-6) {
            vh = Math.min(vh, this.flightClass.descendSpeed() / -slope);
        }
        this.alongCommand = vh < this.alongCommand ? vh : Math.min(vh, this.alongCommand + RAMP_ACCEL * dt);
        double cx = -CROSS_GAIN * ex;
        double cz = -CROSS_GAIN * ez;
        double cross = Math.hypot(cx, cz);
        if (cross > MAX_CROSS_SPEED) {
            cx *= MAX_CROSS_SPEED / cross;
            cz *= MAX_CROSS_SPEED / cross;
            cross = MAX_CROSS_SPEED;
        }
        double command = Math.min(this.alongCommand, Math.sqrt(Math.max(0.0, speed * speed - cross * cross)));
        double pathY = this.fromY + slope * Math.clamp(along, 0.0, hs);
        double feed = slope * Math.clamp(actualAlong, 0.0, Math.max(command, 0.0) * 1.2);
        double vy = Math.clamp(feed + VERTICAL_GAIN * (pathY - in.y()), -this.flightClass.descendSpeed(), this.flightClass.climbSpeed());
        out.velocity(command * hx + cx, command * hz + cz);
        out.maxSpeed(speed);
        out.climb(vy);
        return new Segment(hs * stretch, along * stretch, remaining, hx, hz, hs, command,
                Math.clamp(along * stretch / (hs * stretch), 0.0, 1.0));
    }

    private static double remap(double progress, double start, double end) {
        if (start <= 0.0 && end >= 1.0) {
            return Math.clamp(progress, 0.0, 1.0);
        }
        if (end - start < 1.0E-6) {
            return progress >= start ? 1.0 : 0.0;
        }
        return Math.clamp((progress - start) / (end - start), 0.0, 1.0);
    }

    private Segment trackTurn(NavInput in, NavOutput out, CurvedTurn turn, double turnSpeed, double limit, boolean entering, double dt) {
        double f = Math.clamp(turn.progress(in.x(), in.z()), 0.0, 1.0);
        double[] d = turn.direction(f);
        double actualAlong = in.vx() * d[0] + in.vz() * d[1];
        if (!this.segmentStarted) {
            this.segmentStarted = true;
            this.alongCommand = Math.max(0.0, actualAlong);
        }
        this.alongCommand = turnSpeed < this.alongCommand ? turnSpeed : Math.min(turnSpeed, this.alongCommand + RAMP_ACCEL * dt);
        double command = this.alongCommand;
        double offset = turn.offset(in.x(), in.z());
        double cx;
        double cz;
        double lead = 0.0;
        double ix = turn.inwardX(in.x(), in.z());
        double iz = turn.inwardZ(in.x(), in.z());
        if (turn.straight()) {
            cx = CROSS_GAIN * offset * d[1];
            cz = -CROSS_GAIN * offset * d[0];
        } else {
            cx = CROSS_GAIN * offset * ix;
            cz = CROSS_GAIN * offset * iz;
            lead = command * command / turn.radius() * TURN_LEAD;
        }
        double cross = Math.hypot(cx, cz);
        if (cross > MAX_CROSS_SPEED) {
            cx *= MAX_CROSS_SPEED / cross;
            cz *= MAX_CROSS_SPEED / cross;
        }
        double vx = command * d[0] + cx + lead * ix;
        double vz = command * d[1] + cz + lead * iz;
        double feed = turn.slope(f) * Math.clamp(actualAlong, 0.0, Math.max(command, 0.0) * 1.2);
        double vy = Math.clamp(feed + VERTICAL_GAIN * (turn.height(f) - in.y()), -this.flightClass.descendSpeed(), this.flightClass.climbSpeed());
        out.velocity(vx, vz);
        out.maxSpeed(Math.max(limit, Math.hypot(vx, vz)));
        out.climb(vy);
        return new Segment(turn.length(), turn.length() * f, turn.length() * (1.0 - f), d[0], d[1], turn.length(), command, entering ? 1.0 : 0.0);
    }

    private Segment trackVertical(NavInput in, NavOutput out, double tx, double ty, double tz, double speed, double endSpeed) {
        double dy = ty - in.y();
        double gap = Math.abs(dy);
        double limit = dy >= 0.0 ? this.flightClass.climbSpeed() : this.flightClass.descendSpeed();
        double v = Math.min(Math.min(limit, speed), Math.min(Math.sqrt(endSpeed * endSpeed + 2.0 * BRAKE_ACCEL * gap),
                endSpeed + APPROACH_GAIN * gap));
        double hx = tx - in.x();
        double hz = tz - in.z();
        double h = Math.hypot(hx, hz);
        double correction = Math.min(MAX_CROSS_SPEED, CROSS_GAIN * h);
        out.velocity(h > 1.0E-6 ? hx / h * correction : 0.0, h > 1.0E-6 ? hz / h * correction : 0.0);
        out.maxSpeed(Math.max(speed, MAX_CROSS_SPEED));
        out.climb(Math.signum(dy) * v);
        this.segmentStarted = true;
        double distance = Math.sqrt(dy * dy + h * h);
        double length = Math.abs(ty - this.fromY);
        return new Segment(length, length - gap, distance, 0.0, 0.0, 0.0, 0.0,
                length < 1.0E-6 ? 1.0 : Math.clamp((length - gap) / length, 0.0, 1.0));
    }

    public static double[] corner(double fromX, double fromY, double fromZ, Waypoint w, Waypoint n) {
        double ax = w.x() - fromX;
        double ay = w.y() - fromY;
        double az = w.z() - fromZ;
        double bx = n.x() - w.x();
        double by = n.y() - w.y();
        double bz = n.z() - w.z();
        double la = Math.sqrt(ax * ax + ay * ay + az * az);
        double lb = Math.sqrt(bx * bx + by * by + bz * bz);
        if (la < 0.1 || lb < 0.1 || CurvedTurn.hairpin(fromX, fromY, fromZ, w, n)) {
            return null;
        }
        double turn = Math.acos(Math.clamp((ax * bx + ay * by + az * bz) / (la * lb), -1.0, 1.0));
        if (turn < Math.toRadians(1.0)) {
            return new double[]{Double.POSITIVE_INFINITY, 0.0};
        }
        double half = (Math.PI - turn) / 2.0;
        double radius = CORNER_DEVIATION / (1.0 / Math.sin(half) - 1.0);
        double tangent = radius / Math.tan(half);
        double maxTangent = 0.4 * Math.min(la, lb);
        if (tangent > maxTangent) {
            tangent = maxTangent;
            radius = tangent * Math.tan(half);
        }
        return new double[]{Math.sqrt(LATERAL_ACCEL * radius), tangent};
    }

    private double segmentSpeed(int i) {
        return Math.max(0.5, this.flightClass.assistedSpeed(this.mission.speedTo(i)));
    }

    private int next(int i) {
        if (i + 1 < this.mission.size()) {
            return i + 1;
        }
        return this.mission.loop() && this.mission.size() > 1 ? 0 : -1;
    }

    private static double sq(double v) {
        return v * v;
    }

    public static double progress(Waypoint from, Waypoint to, double x, double y, double z) {
        double sx = to.x() - from.x();
        double sy = to.y() - from.y();
        double sz = to.z() - from.z();
        double l2 = sx * sx + sy * sy + sz * sz;
        if (l2 < 1.0E-9) {
            return 1.0;
        }
        return Math.clamp(((x - from.x()) * sx + (y - from.y()) * sy + (z - from.z()) * sz) / l2, 0.0, 1.0);
    }

    public static double tiltAt(Mission mission, int index, double x, double y, double z) {
        if (mission.isEmpty()) {
            return 0.0;
        }
        int i = Math.clamp(index, 0, mission.size() - 1);
        Waypoint w = mission.waypoint(i);
        if (w.heading() == Waypoint.Heading.POI) {
            PointOfInterest p = mission.pointsOfInterest().get(w.poi());
            return Math.clamp(GimbalAim.tiltTo(p.x() - x, p.y() - y, p.z() - z), FlightClass.GIMBAL_MIN_TILT, 60.0);
        }
        if (i == 0) {
            return w.tiltDeg();
        }
        Waypoint from = mission.waypoint(i - 1);
        double t = NavMode.smoothstep(mission.smooth() ? curvedProgress(mission, i, x, y, z) : progress(from, w, x, y, z));
        return from.tiltDeg() + (w.tiltDeg() - from.tiltDeg()) * t;
    }

    private static double curvedProgress(Mission mission, int i, double x, double y, double z) {
        Waypoint from = mission.waypoint(i - 1);
        Waypoint w = mission.waypoint(i);
        double sx = w.x() - from.x();
        double sz = w.z() - from.z();
        double hs = Math.hypot(sx, sz);
        if (hs < CurvedTurn.MIN_LEG) {
            return progress(from, w, x, y, z);
        }
        double p = ((x - from.x()) * sx + (z - from.z()) * sz) / (hs * hs);
        double start;
        if (i == 1) {
            start = from.waitDs() == 0 ? CurvedTurn.maxApexShare() : 0.0;
        } else {
            CurvedTurn previous = CurvedTurn.at(mission, i - 1);
            start = previous == null ? 0.0 : previous.apexShift() / hs;
        }
        CurvedTurn end = CurvedTurn.at(mission, i);
        return remap(p, start, 1.0 - (end == null ? 0.0 : end.apexShift() / hs));
    }

    public static double distanceToSegment(Waypoint from, Waypoint to, double x, double y, double z) {
        double t = progress(from, to, x, y, z);
        double px = from.x() + (to.x() - from.x()) * t;
        double py = from.y() + (to.y() - from.y()) * t;
        double pz = from.z() + (to.z() - from.z()) * t;
        return Math.sqrt(sq(x - px) + sq(y - py) + sq(z - pz));
    }

    public static int resumeIndex(Mission mission, int reported, double x, double y, double z) {
        if (mission.isEmpty()) {
            return 0;
        }
        int first = Math.clamp(reported, 0, mission.size() - 1);
        int best = first;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int j = first; j < mission.size(); j++) {
            Waypoint to = mission.waypoint(j);
            double d = j == 0 ? to.distanceTo(x, y, z) : distanceToSegment(mission.waypoint(j - 1), to, x, y, z);
            if (d < bestDistance - 1.0E-9) {
                bestDistance = d;
                best = j;
            }
        }
        return best;
    }

    private record Segment(double length, double along, double remaining, double ux, double uz, double horizontal, double speed,
                           double progress) {
    }
}
