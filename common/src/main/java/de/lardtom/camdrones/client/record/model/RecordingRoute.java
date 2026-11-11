package de.lardtom.camdrones.client.record.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;

public enum RecordingRoute {
    FLASHBACK,
    DIRECT,
    NEEDS_LIVE_VIEW;

    public static RecordingRoute of(RecordingMode chosen, boolean flashbackReady, boolean liveView) {
        if (chosen.effective(flashbackReady) == RecordingMode.FLASHBACK) {
            return FLASHBACK;
        }
        return liveView ? DIRECT : NEEDS_LIVE_VIEW;
    }

    public boolean records() {
        return this != NEEDS_LIVE_VIEW;
    }

    public static boolean films(Waypoint.Action action) {
        return action == Waypoint.Action.PHOTO || action == Waypoint.Action.RECORD_START || action == Waypoint.Action.RECORD_STOP;
    }

    public static boolean films(Mission mission) {
        if (mission == null) {
            return false;
        }
        for (int i = 0; i < mission.size(); i++) {
            if (films(mission.waypoint(i).action())) {
                return true;
            }
        }
        return false;
    }
}
