package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

public final class MissionShots {
    public static final double ARRIVE_RADIUS = 1.5;

    private final BitSet fired = new BitSet();
    private int epoch = -1;
    private int lastIndex = -1;
    private int laps;

    public List<Waypoint.Action> update(Optional<Mission> mission, NavLogic.MissionState state, int index, int missionEpoch, double x,
                                        double y, double z) {
        List<Waypoint.Action> out = new ArrayList<>();
        if (mission.isEmpty() || mission.get().isEmpty() || state == NavLogic.MissionState.IDLE
                || state == NavLogic.MissionState.ABORTED) {
            this.reset(missionEpoch);
            return out;
        }
        if (missionEpoch != this.epoch) {
            this.reset(missionEpoch);
        }
        Mission m = mission.get();
        int i = Math.clamp(index, 0, m.size() - 1);
        if (state == NavLogic.MissionState.DONE) {
            if (this.lastIndex >= 0) {
                this.passed(m, this.lastIndex, out);
                this.passed(m, m.size() - 1, out);
            } else {
                this.fired.set(0, m.size());
            }
            this.lastIndex = m.size() - 1;
            return out;
        }
        if (this.lastIndex >= 0 && i != this.lastIndex) {
            this.passed(m, this.lastIndex, out);
            if (i < this.lastIndex) {
                this.laps++;
                this.fired.clear();
            }
        }
        this.lastIndex = i;
        if (state == NavLogic.MissionState.RUNNING && m.waypoint(i).distanceTo(x, y, z) <= ARRIVE_RADIUS) {
            this.passed(m, i, out);
        }
        return out;
    }

    public int laps() {
        return this.laps;
    }

    private void passed(Mission mission, int index, List<Waypoint.Action> out) {
        if (index < 0 || index >= mission.size() || this.fired.get(index)) {
            return;
        }
        this.fired.set(index);
        Waypoint.Action action = mission.waypoint(index).action();
        if (action != Waypoint.Action.NONE) {
            out.add(action);
        }
    }

    private void reset(int missionEpoch) {
        this.fired.clear();
        this.epoch = missionEpoch;
        this.lastIndex = -1;
        this.laps = 0;
    }
}
