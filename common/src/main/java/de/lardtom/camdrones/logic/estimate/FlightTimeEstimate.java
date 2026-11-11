package de.lardtom.camdrones.logic.estimate;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.CurvedTurn;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.WaypointFollower;

public final class FlightTimeEstimate {
    public static final double SPEED_POWER = 0.35;
    public static final double CLIMB_POWER = 0.4;
    public static final double RESERVE = 0.1;
    public static final double MIN_OBSERVED_SECONDS = 30.0;
    public static final double MIN_USED_CHARGE = 0.01;
    public static final double RATE_SMOOTHING = 30.0;
    public static final double HALT_SECONDS = 0.75;
    public static final double HALT_FULL_SPEED = 1.2;
    public static final double VERTICAL_SETTLE_SPEED = 2.5;
    private static final double PASS_RADIUS = WaypointFollower.ARRIVE_RADIUS * 0.4;
    private static final double PASS_SECONDS = 1.0;

    public record Preview(double length, double seconds, double charge) {
        public boolean feasible(double availableCharge) {
            return this.charge <= availableCharge - RESERVE;
        }
    }

    private final FlightClass flightClass;
    private double lastTime = Double.NaN;
    private double lastCharge;
    private double rate = Double.NaN;
    private double observed;
    private double used;

    public FlightTimeEstimate(FlightClass flightClass) {
        this.flightClass = flightClass;
    }

    public static double hoverSeconds(FlightClass flightClass) {
        return flightClass.hoverMinutes() * 60.0;
    }

    public static double powerFactor(FlightClass flightClass, double speed, double climb) {
        double s = Math.max(0.0, speed) / flightClass.normalSpeed();
        double c = Math.max(0.0, climb) / flightClass.climbSpeed();
        return 1.0 + SPEED_POWER * s * s + CLIMB_POWER * c;
    }

    public static double modelSeconds(FlightClass flightClass, double charge, double speed, double climb) {
        return Math.max(0.0, charge - RESERVE) * hoverSeconds(flightClass) / powerFactor(flightClass, speed, climb);
    }

    public static double benchSeconds(double fullPackSeconds, double charge) {
        if (!(fullPackSeconds > 0.0) || !Double.isFinite(fullPackSeconds)) {
            return Double.NaN;
        }
        return fullPackSeconds * Math.max(0.0, charge - RESERVE) / (1.0 - RESERVE);
    }

    public void sample(double time, double charge) {
        this.sample(time, charge, true);
    }

    public void sample(double time, double charge, boolean flying) {
        if (!Double.isFinite(time) || !Double.isFinite(charge)) {
            return;
        }
        boolean swapped = !Double.isNaN(this.lastTime) && charge > this.lastCharge + 0.05;
        if (Double.isNaN(this.lastTime) || time <= this.lastTime || swapped) {
            if (swapped || !Double.isNaN(this.lastTime) && time < this.lastTime) {
                this.forget();
            }
            this.lastTime = time;
            this.lastCharge = charge;
            return;
        }
        double dt = time - this.lastTime;
        double drop = Math.max(0.0, this.lastCharge - charge);
        this.lastTime = time;
        this.lastCharge = charge;
        if (!flying) {
            return;
        }
        this.observed += dt;
        this.used += drop;
        if (Double.isNaN(this.rate)) {
            if (this.observed >= MIN_OBSERVED_SECONDS && this.used >= MIN_USED_CHARGE) {
                this.rate = this.used / this.observed;
            }
            return;
        }
        double a = 1.0 - Math.exp(-dt / RATE_SMOOTHING);
        this.rate += (drop / dt - this.rate) * a;
    }

    public boolean measured() {
        return Double.isFinite(this.rate) && this.rate > 1.0E-7;
    }

    public double rate() {
        return this.rate;
    }

    public double observedSeconds() {
        return this.observed;
    }

    public double remainingSeconds(double charge, double speed, double climb) {
        return this.remainingSeconds(charge, speed, climb, Double.NaN);
    }

    public double remainingSeconds(double charge, double speed, double climb, double fullPackSeconds) {
        if (this.measured()) {
            return Math.max(0.0, charge - RESERVE) / this.rate;
        }
        double bench = benchSeconds(fullPackSeconds, charge);
        return Double.isNaN(bench) ? modelSeconds(this.flightClass, charge, speed, climb) : bench;
    }

    private void forget() {
        this.rate = Double.NaN;
        this.observed = 0.0;
        this.used = 0.0;
    }

    public void reset() {
        this.lastTime = Double.NaN;
        this.forget();
    }

    public static Preview mission(FlightClass flightClass, Mission mission, double startX, double startY, double startZ) {
        if (mission.smooth()) {
            return curved(flightClass, mission, startX, startY, startZ);
        }
        double length = 0.0;
        double seconds = 0.0;
        double energy = 0.0;
        double x = startX;
        double y = startY;
        double z = startZ;
        int count = mission.size();
        int legs = count + (mission.loop() && count > 1 ? 1 : 0);
        for (int leg = 0; leg < legs; leg++) {
            int i = leg % count;
            Waypoint w = mission.waypoint(i);
            double speed = flightClass.assistedSpeed(mission.speedTo(i));
            boolean stop = !mission.smooth() || w.waitDs() > 0 || leg == legs - 1;
            double[] part = leg(flightClass, x, y, z, w.x(), w.y(), w.z(), speed, stop);
            length += part[0];
            seconds += part[1] + w.waitSeconds();
            energy += part[2] + w.waitSeconds();
            x = w.x();
            y = w.y();
            z = w.z();
        }
        return finish(flightClass, mission, startX, startY, startZ, x, y, z, length, seconds, energy);
    }

    private static Preview finish(FlightClass flightClass, Mission mission, double startX, double startY, double startZ, double x, double y,
                                  double z, double length, double seconds, double energy) {
        int count = mission.size();
        if (count > 0 && mission.end() == Mission.End.START) {
            double[] part = leg(flightClass, x, y, z, startX, Math.max(y, startY), startZ, flightClass.assistedSpeed(mission.baseSpeed()), true);
            length += part[0];
            seconds += part[1];
            energy += part[2];
            y = Math.max(y, startY);
        }
        if (count > 0 && mission.end() != Mission.End.HOVER) {
            double drop = Math.max(0.0, y - startY);
            double descent = drop / WaypointFollower.LAND_SPEED;
            length += drop;
            seconds += descent;
            energy += descent;
        }
        return new Preview(length, seconds, energy / hoverSeconds(flightClass));
    }

    private static Preview curved(FlightClass flightClass, Mission mission, double startX, double startY, double startZ) {
        double length = 0.0;
        double seconds = 0.0;
        double energy = 0.0;
        double x = startX;
        double y = startY;
        double z = startZ;
        int count = mission.size();
        int legs = count + (mission.loop() && count > 1 ? 1 : 0);
        CurvedTurn previous = null;
        double previousSpeed = 0.0;
        boolean moving = false;
        for (int leg = 0; leg < legs; leg++) {
            int i = leg % count;
            Waypoint w = mission.waypoint(i);
            boolean last = leg == legs - 1;
            boolean stop = w.waitDs() > 0 || last;
            double speed = Math.max(CurvedTurn.MIN_SPEED, flightClass.assistedSpeed(mission.speedTo(i)));
            double h = Math.hypot(w.x() - x, w.z() - z);
            double dv = w.y() - y;
            CurvedTurn turn = last ? null : CurvedTurn.at(mission, i, x, y, z);
            if (turn == null && !stop) {
                stop = WaypointFollower.corner(x, y, z, w, mission.waypoint(CurvedTurn.next(mission, i))) == null;
            }
            if (h < CurvedTurn.MIN_LEG) {
                double[] part = leg(flightClass, x, y, z, w.x(), w.y(), w.z(), flightClass.assistedSpeed(mission.speedTo(i)), stop);
                length += part[0];
                seconds += part[1] + w.waitSeconds();
                energy += part[2] + w.waitSeconds();
                previous = null;
                moving = !stop;
            } else {
                double slope = dv / h;
                double cruise = CurvedTurn.legSpeed(flightClass, speed, slope);
                double turnSpeed = turn == null ? 0.0 : CurvedTurn.plannedSpeed(flightClass, mission, i, turn);
                double straight = Math.max(0.0, h - (previous == null ? 0.0 : previous.tangent()) - (turn == null ? 0.0 : turn.tangent()));
                double v0 = previous != null ? previousSpeed : moving ? cruise : 0.0;
                double v1 = turn != null ? turnSpeed : stop ? 0.0 : cruise;
                double stretch = Math.sqrt(1.0 + slope * slope);
                double brake = WaypointFollower.BRAKE_ACCEL / stretch;
                double start = Math.min(v0, cruise);
                double reach = Math.sqrt(start * start + 2.0 * WaypointFollower.RAMP_ACCEL * straight);
                double entry = turnSpeed;
                double time;
                if (turn == null && stop) {
                    time = halt(straight, v0, cruise, slope, previous != null);
                } else if (turn != null && reach < turnSpeed) {
                    entry = reach;
                    time = (reach - start) / WaypointFollower.RAMP_ACCEL;
                } else {
                    time = ramp(straight, v0, cruise, v1, brake);
                }
                double exit = turnSpeed;
                double horizontal = straight;
                double climb = Math.max(0.0, slope * straight);
                length += straight * stretch;
                if (turn != null) {
                    double rise = turn.exitY() - turn.entryY();
                    length += Math.sqrt(turn.length() * turn.length() + rise * rise);
                    double speedUp = (turnSpeed * turnSpeed - entry * entry) / (2.0 * WaypointFollower.RAMP_ACCEL);
                    if (entry >= turnSpeed) {
                        time += turn.length() / turnSpeed;
                    } else if (speedUp >= turn.length()) {
                        exit = Math.sqrt(entry * entry + 2.0 * WaypointFollower.RAMP_ACCEL * turn.length());
                        time += (exit - entry) / WaypointFollower.RAMP_ACCEL;
                    } else {
                        time += (turnSpeed - entry) / WaypointFollower.RAMP_ACCEL + (turn.length() - speedUp) / turnSpeed;
                    }
                    horizontal += turn.length();
                    climb += Math.max(0.0, rise);
                }
                seconds += time + w.waitSeconds();
                energy += (time > 0.0 ? time * powerFactor(flightClass, horizontal / time, climb / time) : 0.0) + w.waitSeconds();
                previous = turn;
                previousSpeed = exit;
                moving = turn == null && !stop;
            }
            x = w.x();
            y = w.y();
            z = w.z();
        }
        return finish(flightClass, mission, startX, startY, startZ, x, y, z, length, seconds, energy);
    }

    static double ramp(double distance, double from, double cruise, double to) {
        return ramp(distance, from, cruise, to, WaypointFollower.BRAKE_ACCEL);
    }

    static double ramp(double distance, double from, double cruise, double to, double brake) {
        if (distance <= 0.0 || cruise <= 0.0) {
            return 0.0;
        }
        double a = Math.min(from, cruise);
        double b = Math.min(to, cruise);
        double up = (cruise * cruise - a * a) / (2.0 * WaypointFollower.RAMP_ACCEL);
        double down = (cruise * cruise - b * b) / (2.0 * brake);
        if (up + down <= distance) {
            return (cruise - a) / WaypointFollower.RAMP_ACCEL + (cruise - b) / brake + (distance - up - down) / cruise;
        }
        double peak = Math.sqrt(Math.max((distance + a * a / (2.0 * WaypointFollower.RAMP_ACCEL) + b * b / (2.0 * brake))
                / (1.0 / (2.0 * WaypointFollower.RAMP_ACCEL) + 1.0 / (2.0 * brake)), Math.max(a, b) * Math.max(a, b)));
        return Math.max(0.0, peak - a) / WaypointFollower.RAMP_ACCEL + Math.max(0.0, peak - b) / brake;
    }

    private static double halt(double distance, double from, double cruise, double slope, boolean afterTurn) {
        double stretch = Math.sqrt(1.0 + slope * slope);
        double brake = WaypointFollower.BRAKE_ACCEL / stretch;
        double d = Math.max(0.0, distance - WaypointFollower.ARRIVE_RADIUS / stretch);
        double a = Math.min(from, cruise);
        if (afterTurn && a * Math.abs(slope) < VERTICAL_SETTLE_SPEED) {
            double speed = a * stretch;
            double path = distance * stretch;
            double rest = Math.max(0.0, path - PASS_RADIUS);
            if (path <= speed * PASS_SECONDS && speed * speed > 2.0 * WaypointFollower.BRAKE_ACCEL * rest) {
                return (speed - Math.sqrt(speed * speed - 2.0 * WaypointFollower.BRAKE_ACCEL * rest)) / WaypointFollower.BRAKE_ACCEL
                        + HALT_SECONDS * Math.min(1.0, speed / HALT_FULL_SPEED);
            }
        }
        boolean late = afterTurn && a * a > 2.0 * brake * distance;
        double time = late ? 2.0 * distance / a : ramp(d, from, cruise, 0.0, brake);
        if (cruise <= 0.0) {
            return time;
        }
        double peak = Math.max(a, Math.min(cruise, Math.sqrt((d + a * a / (2.0 * WaypointFollower.RAMP_ACCEL))
                / (1.0 / (2.0 * WaypointFollower.RAMP_ACCEL) + 1.0 / (2.0 * brake)))));
        double settle = Math.min(1.0, peak * stretch / HALT_FULL_SPEED);
        double vertical = late && a * HALT_SECONDS >= distance ? 0.0
                : Math.clamp((peak * Math.abs(slope) - VERTICAL_SETTLE_SPEED) / VERTICAL_SETTLE_SPEED, 0.0, 1.0);
        return time + HALT_SECONDS * (settle + vertical);
    }

    private static double[] leg(FlightClass flightClass, double ax, double ay, double az, double bx, double by, double bz, double speed,
                                boolean stop) {
        double h = Math.hypot(bx - ax, bz - az);
        double dv = by - ay;
        double length = Math.sqrt(h * h + dv * dv);
        double v = Math.max(0.5, speed);
        double vertical = dv >= 0.0 ? dv / flightClass.climbSpeed() : -dv / flightClass.descendSpeed();
        double time = Math.max(length / v, vertical);
        if (stop && h >= WaypointFollower.MIN_HORIZONTAL) {
            double slope = dv / h;
            time = halt(h, 0.0, CurvedTurn.legSpeed(flightClass, v, slope), slope, false);
        } else if (stop && length > 0.0) {
            time = ramp(Math.max(0.0, length - WaypointFollower.ARRIVE_RADIUS), 0.0, length / time, 0.0);
        }
        double climb = time > 0.0 ? Math.max(0.0, dv) / time : 0.0;
        double horizontalSpeed = time > 0.0 ? h / time : 0.0;
        return new double[]{length, time, time * powerFactor(flightClass, horizontalSpeed, climb)};
    }
}
