package de.lardtom.camdrones.integration.autonomy.client;

import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PilotRelay {
    public static final int PULSE_UPDATES = 3;

    private UUID drone;
    private boolean seen;
    private int epoch;
    private int startPulse;
    private int pausePulse;
    private int offset;
    private SmartState.Function reported;

    public synchronized void observe(UUID drone, MissionStatus server, NavLogic.MissionState local) {
        if (!Objects.equals(drone, this.drone)) {
            this.drone = drone;
            this.seen = false;
            this.startPulse = 0;
            this.pausePulse = 0;
            this.offset = 0;
            this.reported = null;
        }
        if (drone == null || server == null) {
            return;
        }
        if (!this.seen) {
            this.seen = true;
            this.epoch = server.epoch();
            return;
        }
        if (server.epoch() == this.epoch) {
            return;
        }
        this.epoch = server.epoch();
        NavLogic.MissionState now = Objects.requireNonNullElse(local, NavLogic.MissionState.IDLE);
        if (server.state() == NavLogic.MissionState.RUNNING) {
            if (now != NavLogic.MissionState.RUNNING) {
                this.startPulse = PULSE_UPDATES;
                this.pausePulse = 0;
            }
        } else if (now == NavLogic.MissionState.RUNNING) {
            this.pausePulse = PULSE_UPDATES;
            this.startPulse = 0;
        }
    }

    public synchronized boolean pulse(CamControl control) {
        if (control == CamControl.MISSION_START && this.startPulse > 0) {
            this.startPulse--;
            return true;
        }
        if (control == CamControl.PAUSE && this.pausePulse > 0) {
            this.pausePulse--;
            return true;
        }
        return false;
    }

    public synchronized boolean pending() {
        return this.startPulse > 0 || this.pausePulse > 0;
    }

    public synchronized Optional<Mission> mission(Optional<Mission> full, MissionStatus server) {
        this.offset = 0;
        if (full == null || full.isEmpty() || server == null) {
            return full == null ? Optional.empty() : full;
        }
        Mission mission = full.get();
        boolean resumable = server.state() == NavLogic.MissionState.RUNNING || server.state() == NavLogic.MissionState.PAUSED;
        int index = server.index();
        if (mission.loop() || !resumable || index <= 0 || index >= mission.size()) {
            return full;
        }
        this.offset = index;
        return Optional.of(mission.withWaypoints(mission.waypoints().subList(index, mission.size())));
    }

    public synchronized int offset() {
        return this.offset;
    }

    public synchronized int absolute(int index) {
        return Math.clamp((long) index + this.offset, 0, MissionLimits.MAX_WAYPOINTS - 1);
    }

    public synchronized int epoch() {
        return this.epoch;
    }

    public synchronized boolean seen() {
        return this.seen;
    }

    public static SmartState.Function function(NavLogic.Function function) {
        if (function == null) {
            return SmartState.Function.NONE;
        }
        return switch (function) {
            case HOLD, AUTO_SHOT -> SmartState.Function.HOLD;
            case FOLLOW -> SmartState.Function.FOLLOW;
            case ORBIT -> SmartState.Function.POI;
            case COURSE_LOCK -> SmartState.Function.COURSE_LOCK;
            case NONE, MISSION -> SmartState.Function.NONE;
        };
    }

    public synchronized Optional<SmartState.Function> changed(NavLogic.Function function) {
        SmartState.Function mapped = function(function);
        if (this.drone == null || mapped == this.reported) {
            return Optional.empty();
        }
        this.reported = mapped;
        return Optional.of(mapped);
    }
}
