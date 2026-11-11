package de.lardtom.camdrones.logic.mission;

import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

public final class TaskTracker {
    public static final double ARRIVE_RADIUS = 1.5;
    public static final double LEAVE_RADIUS = 2.0 * ARRIVE_RADIUS;
    private static final long OPEN = Long.MAX_VALUE;

    private final BitSet fired = new BitSet();
    private int lastIndex = -1;
    private int laps;
    private boolean finished;
    private long now;
    private WaypointTasks.Timed timer;
    private long nextAt;
    private long stayUntil = OPEN;
    private boolean near;
    private double fromX;
    private double fromZ;
    private NavLogic.MissionState state;

    public List<WaypointTasks.Due> update(Mission mission, NavLogic.MissionState state, int index, double x, double y, double z) {
        return this.update(mission, state, index, x, y, z, this.now + 1);
    }

    public List<WaypointTasks.Due> update(Mission mission, NavLogic.MissionState state, int index, double x, double y, double z, long now) {
        NavLogic.MissionState previous = this.state;
        this.now = now;
        this.state = state;
        List<WaypointTasks.Due> out = new ArrayList<>();
        if (mission == null || mission.isEmpty() || state == null || state == NavLogic.MissionState.IDLE
                || state == NavLogic.MissionState.ABORTED) {
            this.reset();
            return out;
        }
        int last = mission.size() - 1;
        if (state == NavLogic.MissionState.DONE) {
            if (!this.finished && this.lastIndex >= 0) {
                for (int i = this.lastIndex; i <= last; i++) {
                    this.passed(mission, i, out);
                }
            }
            this.finished = true;
            this.lastIndex = last;
            this.timer = null;
            return out;
        }
        if (this.finished) {
            this.reset();
        }
        int i = Math.clamp(index, 0, last);
        if (i == last && i != this.lastIndex) {
            this.approach(mission, this.lastIndex == last - 1 && last > 0, x, z);
        }
        if (i == last && previous == NavLogic.MissionState.PAUSED && state == NavLogic.MissionState.RUNNING) {
            this.approach(mission, false, x, z);
        }
        if (this.lastIndex >= 0 && i != this.lastIndex) {
            if (i == this.lastIndex + 1) {
                this.passed(mission, this.lastIndex, out);
            } else if (i < this.lastIndex) {
                if (mission.loop() && this.lastIndex == last) {
                    this.passed(mission, last, out);
                    this.laps++;
                } else {
                    this.timer = null;
                }
                this.fired.clear();
                this.stayUntil = OPEN;
                this.near = false;
            } else {
                this.timer = null;
            }
        }
        this.lastIndex = i;
        if (state == NavLogic.MissionState.RUNNING && mission.waypoint(i).distanceTo(x, y, z) <= ARRIVE_RADIUS) {
            this.passed(mission, i, out);
        }
        this.endAtLastWaypoint(mission, state, i, x, y, z);
        if (state == NavLogic.MissionState.RUNNING && this.timer != null && now >= this.nextAt) {
            out.add(new WaypointTasks.Due(this.timer.source(), Waypoint.Light.NONE, this.timer.accessory(), true, this.timer.ticks(), true));
            this.nextAt = now + this.timer.ticks();
        }
        return out;
    }

    public Optional<WaypointTasks.Timed> timer() {
        return Optional.ofNullable(this.timer);
    }

    public int laps() {
        return this.laps;
    }

    public boolean finished() {
        return this.finished;
    }

    public boolean fired(int index) {
        return index >= 0 && this.fired.get(index);
    }

    public void reset() {
        this.fired.clear();
        this.lastIndex = -1;
        this.laps = 0;
        this.finished = false;
        this.timer = null;
        this.stayUntil = OPEN;
        this.near = false;
    }

    private void approach(Mission mission, boolean fromPrevious, double x, double z) {
        this.stayUntil = OPEN;
        this.near = false;
        Waypoint previous = fromPrevious ? mission.waypoint(mission.size() - 2) : null;
        this.fromX = previous == null ? x : previous.x();
        this.fromZ = previous == null ? z : previous.z();
    }

    private void endAtLastWaypoint(Mission mission, NavLogic.MissionState state, int index, double x, double y, double z) {
        int last = mission.size() - 1;
        if (mission.loop() || index != last || state != NavLogic.MissionState.RUNNING) {
            return;
        }
        Waypoint w = mission.waypoint(last);
        double distance = w.distanceTo(x, y, z);
        if (distance <= ARRIVE_RADIUS) {
            this.near = true;
        }
        if (this.stayUntil == OPEN && this.arrived(w, distance, x, z)) {
            this.stayUntil = this.now + Math.round(w.waitSeconds() * MissionLimits.TICKS_PER_SECOND);
        }
        if (this.now >= this.stayUntil || this.near && distance > LEAVE_RADIUS) {
            this.timer = null;
        }
    }

    private boolean arrived(Waypoint w, double distance, double x, double z) {
        if (distance <= WaypointFollower.ARRIVE_RADIUS) {
            return true;
        }
        double sx = w.x() - this.fromX;
        double sz = w.z() - this.fromZ;
        return distance <= WaypointFollower.PASS_RADIUS && Math.hypot(sx, sz) >= WaypointFollower.MIN_HORIZONTAL
                && (x - w.x()) * sx + (z - w.z()) * sz > 0.0;
    }

    private void passed(Mission mission, int index, List<WaypointTasks.Due> out) {
        if (index < 0 || index >= mission.size() || this.fired.get(index)) {
            return;
        }
        this.fired.set(index);
        WaypointTasks.Due due = WaypointTasks.due(mission, index);
        Optional<WaypointTasks.Timed> range = WaypointTasks.timedAt(mission, index);
        boolean keep = range.isPresent() && range.get().sameSetting(this.timer);
        if (keep && due.everyTicks() > 0) {
            due = due.withoutAccessory();
        }
        if (due.any()) {
            out.add(due);
        }
        if (range.isEmpty()) {
            this.timer = null;
        } else if (!keep) {
            this.timer = range.get();
            this.nextAt = due.accessory() > 0 ? this.now + this.timer.ticks() : this.now;
        }
    }
}
