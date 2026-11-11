package de.lardtom.camdrones.logic.nav;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.ArrayList;
import java.util.List;

public final class CurvedTurn {
    public static final double LEG_SHARE = 0.45;
    public static final double MIN_LEG = 1.0;
    public static final double STRAIGHT = Math.toRadians(1.0);
    public static final double MIN_SPEED = 0.5;
    public static final int SAMPLES = 16;
    public static final int LOOKAHEAD = 8;

    private final double x;
    private final double y;
    private final double z;
    private final double inX;
    private final double inZ;
    private final double outX;
    private final double outZ;
    private final double tangent;
    private final double angle;
    private final double side;
    private final double inSlope;
    private final double outSlope;
    private final double radius;
    private final double centerX;
    private final double centerZ;
    private final double length;

    private CurvedTurn(double x, double y, double z, double inX, double inZ, double outX, double outZ, double tangent, double angle,
                       double side, double inSlope, double outSlope) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.inX = inX;
        this.inZ = inZ;
        this.outX = outX;
        this.outZ = outZ;
        this.tangent = tangent;
        this.angle = angle;
        this.side = side;
        this.inSlope = inSlope;
        this.outSlope = outSlope;
        if (angle < STRAIGHT) {
            this.radius = Double.POSITIVE_INFINITY;
            this.centerX = Double.NaN;
            this.centerZ = Double.NaN;
            this.length = Math.hypot(this.exitX() - this.entryX(), this.exitZ() - this.entryZ());
        } else {
            this.radius = tangent / Math.tan(angle / 2.0);
            this.centerX = this.entryX() - side * inZ * this.radius;
            this.centerZ = this.entryZ() + side * inX * this.radius;
            this.length = this.radius * angle;
        }
    }

    public static CurvedTurn of(double fromX, double fromY, double fromZ, Waypoint w, Waypoint next) {
        double ax = w.x() - fromX;
        double az = w.z() - fromZ;
        double bx = next.x() - w.x();
        double bz = next.z() - w.z();
        double la = Math.hypot(ax, az);
        double lb = Math.hypot(bx, bz);
        if (la < MIN_LEG || lb < MIN_LEG || hairpin(fromX, fromY, fromZ, w, next)) {
            return null;
        }
        double ux = ax / la;
        double uz = az / la;
        double vx = bx / lb;
        double vz = bz / lb;
        double angle = Math.acos(Math.clamp(ux * vx + uz * vz, -1.0, 1.0));
        double side = ux * vz - uz * vx < 0.0 ? -1.0 : 1.0;
        return new CurvedTurn(w.x(), w.y(), w.z(), ux, uz, vx, vz, LEG_SHARE * Math.min(la, lb), angle, side, (w.y() - fromY) / la,
                (next.y() - w.y()) / lb);
    }

    public static boolean hairpin(double fromX, double fromY, double fromZ, Waypoint w, Waypoint next) {
        double ax = w.x() - fromX;
        double az = w.z() - fromZ;
        double bx = next.x() - w.x();
        double bz = next.z() - w.z();
        double la = Math.hypot(ax, az);
        double lb = Math.hypot(bx, bz);
        double cos;
        if (la >= MIN_LEG && lb >= MIN_LEG) {
            cos = ax / la * (bx / lb) + az / la * (bz / lb);
        } else {
            double ay = w.y() - fromY;
            double by = next.y() - w.y();
            double sa = Math.sqrt(ax * ax + ay * ay + az * az);
            double sb = Math.sqrt(bx * bx + by * by + bz * bz);
            if (sa < 1.0E-9 || sb < 1.0E-9) {
                return false;
            }
            cos = (ax * bx + ay * by + az * bz) / (sa * sb);
        }
        return Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0))) > WaypointFollower.MAX_TURN;
    }

    public static int next(Mission mission, int index) {
        if (index + 1 < mission.size()) {
            return index + 1;
        }
        return mission.loop() && mission.size() > 1 ? 0 : -1;
    }

    public static CurvedTurn at(Mission mission, int index, double fromX, double fromY, double fromZ) {
        if (!mission.smooth() || index < 0 || index >= mission.size()) {
            return null;
        }
        Waypoint w = mission.waypoint(index);
        int next = next(mission, index);
        if (w.waitDs() > 0 || next < 0) {
            return null;
        }
        return of(fromX, fromY, fromZ, w, mission.waypoint(next));
    }

    public static CurvedTurn at(Mission mission, int index) {
        int previous = index > 0 ? index - 1 : mission.loop() && mission.size() > 2 ? mission.size() - 1 : -1;
        if (previous < 0 || index >= mission.size()) {
            return null;
        }
        Waypoint p = mission.waypoint(previous);
        return at(mission, index, p.x(), p.y(), p.z());
    }

    public static List<double[]> path(Mission mission, double[] start) {
        List<double[]> points = new ArrayList<>();
        if (mission.isEmpty()) {
            return points;
        }
        double[] from = start;
        if (from != null) {
            points.add(from.clone());
        }
        for (int i = 0; i < mission.size(); i++) {
            Waypoint w = mission.waypoint(i);
            CurvedTurn turn = from == null ? null : at(mission, i, from[0], from[1], from[2]);
            if (turn == null) {
                points.add(new double[]{w.x(), w.y(), w.z()});
            } else {
                turn.sample(points, 0.0, 1.0);
            }
            from = new double[]{w.x(), w.y(), w.z()};
        }
        return points;
    }

    public static List<double[]> loopPath(Mission mission) {
        List<double[]> points = new ArrayList<>();
        if (!mission.loop() || mission.size() < 3) {
            return points;
        }
        Waypoint last = mission.waypoint(mission.size() - 1);
        CurvedTurn end = at(mission, mission.size() - 1);
        if (end == null) {
            points.add(new double[]{last.x(), last.y(), last.z()});
        } else {
            points.add(end.point(1.0));
        }
        CurvedTurn first = at(mission, 0);
        Waypoint w = mission.waypoint(0);
        if (first == null) {
            points.add(new double[]{w.x(), w.y(), w.z()});
        } else {
            first.sample(points, 0.0, 1.0);
        }
        return points;
    }

    private void sample(List<double[]> points, double from, double to) {
        int steps = this.straight() ? 1 : Math.max(2, (int) Math.ceil(SAMPLES * this.angle / Math.PI * 2.0));
        for (int k = 0; k <= steps; k++) {
            points.add(this.point(from + (to - from) * k / steps));
        }
    }

    public boolean beforeEntry(double px, double pz) {
        return (px - this.x) * this.inX + (pz - this.z) * this.inZ < -this.tangent;
    }

    public boolean afterExit(double px, double pz) {
        return (px - this.x) * this.outX + (pz - this.z) * this.outZ > this.tangent;
    }

    public boolean straight() {
        return this.angle < STRAIGHT;
    }

    public double tangent() {
        return this.tangent;
    }

    public double apexShift() {
        return this.tangent * (1.0 - Math.cos(this.angle / 2.0));
    }

    public static double maxApexShare() {
        return LEG_SHARE * (1.0 - Math.cos(Math.toRadians(WaypointFollower.MAX_TURN) / 2.0));
    }

    public double angle() {
        return this.angle;
    }

    public double radius() {
        return this.radius;
    }

    public double length() {
        return this.length;
    }

    public double entryX() {
        return this.x - this.inX * this.tangent;
    }

    public double entryZ() {
        return this.z - this.inZ * this.tangent;
    }

    public double entryY() {
        return this.y - this.inSlope * this.tangent;
    }

    public double exitX() {
        return this.x + this.outX * this.tangent;
    }

    public double exitZ() {
        return this.z + this.outZ * this.tangent;
    }

    public double exitY() {
        return this.y + this.outSlope * this.tangent;
    }

    public double inSlope() {
        return this.inSlope;
    }

    public double outSlope() {
        return this.outSlope;
    }

    public double[] point(double u) {
        double f = Math.clamp(u, 0.0, 1.0);
        if (this.straight()) {
            return new double[]{this.entryX() + (this.exitX() - this.entryX()) * f, this.height(f), this.entryZ() + (this.exitZ() - this.entryZ()) * f};
        }
        double phi = this.side * this.angle * f;
        double sx = (this.entryX() - this.centerX) / this.radius;
        double sz = (this.entryZ() - this.centerZ) / this.radius;
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        return new double[]{this.centerX + this.radius * (sx * c - sz * s), this.height(f), this.centerZ + this.radius * (sx * s + sz * c)};
    }

    public double[] direction(double u) {
        if (this.straight()) {
            double dx = this.exitX() - this.entryX();
            double dz = this.exitZ() - this.entryZ();
            double l = Math.hypot(dx, dz);
            return l < 1.0E-9 ? new double[]{this.inX, this.inZ} : new double[]{dx / l, dz / l};
        }
        double phi = this.side * this.angle * Math.clamp(u, 0.0, 1.0);
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        return new double[]{this.inX * c - this.inZ * s, this.inX * s + this.inZ * c};
    }

    public double progress(double px, double pz) {
        if (this.straight()) {
            double dx = this.exitX() - this.entryX();
            double dz = this.exitZ() - this.entryZ();
            double l2 = dx * dx + dz * dz;
            return l2 < 1.0E-12 ? 1.0 : ((px - this.entryX()) * dx + (pz - this.entryZ()) * dz) / l2;
        }
        double sx = this.entryX() - this.centerX;
        double sz = this.entryZ() - this.centerZ;
        double qx = px - this.centerX;
        double qz = pz - this.centerZ;
        double phi = Math.atan2(sx * qz - sz * qx, sx * qx + sz * qz) * this.side;
        return phi / this.angle;
    }

    public double offset(double px, double pz) {
        if (this.straight()) {
            double[] d = this.direction(0.0);
            return (px - this.entryX()) * -d[1] + (pz - this.entryZ()) * d[0];
        }
        return Math.hypot(px - this.centerX, pz - this.centerZ) - this.radius;
    }

    public double inwardX(double px, double pz) {
        if (this.straight()) {
            return 0.0;
        }
        double d = Math.hypot(px - this.centerX, pz - this.centerZ);
        return d < 1.0E-9 ? 0.0 : (this.centerX - px) / d;
    }

    public double inwardZ(double px, double pz) {
        if (this.straight()) {
            return 0.0;
        }
        double d = Math.hypot(px - this.centerX, pz - this.centerZ);
        return d < 1.0E-9 ? 0.0 : (this.centerZ - pz) / d;
    }

    public double height(double u) {
        double f = Math.clamp(u, 0.0, 1.0);
        double f2 = f * f;
        double f3 = f2 * f;
        return (2.0 * f3 - 3.0 * f2 + 1.0) * this.entryY() + (f3 - 2.0 * f2 + f) * this.inSlope * this.length
                + (-2.0 * f3 + 3.0 * f2) * this.exitY() + (f3 - f2) * this.outSlope * this.length;
    }

    public double slope(double u) {
        if (this.length < 1.0E-9) {
            return 0.0;
        }
        double f = Math.clamp(u, 0.0, 1.0);
        double f2 = f * f;
        double d = (6.0 * f2 - 6.0 * f) * this.entryY() + (3.0 * f2 - 4.0 * f + 1.0) * this.inSlope * this.length
                + (-6.0 * f2 + 6.0 * f) * this.exitY() + (3.0 * f2 - 2.0 * f) * this.outSlope * this.length;
        return d / this.length;
    }

    public static double legSpeed(FlightClass flightClass, double speed, double slope) {
        double v = speed / Math.sqrt(1.0 + slope * slope);
        if (slope > 1.0E-6) {
            v = Math.min(v, flightClass.climbSpeed() / slope);
        } else if (slope < -1.0E-6) {
            v = Math.min(v, flightClass.descendSpeed() / -slope);
        }
        return v;
    }

    public static double cruise(FlightClass flightClass, Mission mission, int index) {
        return Math.max(MIN_SPEED, flightClass.assistedSpeed(mission.speedTo(index)));
    }

    public static double plannedSpeed(FlightClass flightClass, Mission mission, int index, CurvedTurn turn) {
        double own = turn.speed(flightClass, cruise(flightClass, mission, index), cruise(flightClass, mission, next(mission, index)));
        double[] straights = new double[LOOKAHEAD + 1];
        double[] speeds = new double[LOOKAHEAD + 1];
        int count = 0;
        double end = Double.POSITIVE_INFINITY;
        int i = index;
        double tangent = turn.tangent();
        while (count < LOOKAHEAD) {
            int n = next(mission, i);
            if (n < 0) {
                break;
            }
            Waypoint a = mission.waypoint(i);
            Waypoint b = mission.waypoint(n);
            CurvedTurn following = at(mission, n, a.x(), a.y(), a.z());
            straights[count] = Math.max(0.0, Math.hypot(b.x() - a.x(), b.z() - a.z()) - tangent - (following == null ? 0.0 : following.tangent()));
            if (following == null) {
                speeds[count] = 0.0;
                end = 0.0;
                count++;
                break;
            }
            speeds[count] = following.speed(flightClass, cruise(flightClass, mission, n), cruise(flightClass, mission, next(mission, n)));
            count++;
            i = n;
            tangent = following.tangent();
        }
        double cap = end;
        for (int k = count - 1; k >= 0; k--) {
            double v = Math.min(speeds[k], cap);
            cap = Math.sqrt(v * v + 2.0 * WaypointFollower.BRAKE_ACCEL * straights[k]);
        }
        return Math.max(MIN_SPEED, Math.min(own, cap));
    }

    public double speed(FlightClass flightClass, double speedIn, double speedOut) {
        double v = Math.min(legSpeed(flightClass, speedIn, this.inSlope), legSpeed(flightClass, speedOut, this.outSlope));
        if (!this.straight()) {
            v = Math.min(v, Math.sqrt(flightClass.curveAccel() * this.radius));
        }
        for (int k = 0; k <= SAMPLES; k++) {
            v = Math.min(v, legSpeed(flightClass, Double.POSITIVE_INFINITY, this.slope((double) k / SAMPLES)));
        }
        return Math.max(MIN_SPEED, v);
    }
}
