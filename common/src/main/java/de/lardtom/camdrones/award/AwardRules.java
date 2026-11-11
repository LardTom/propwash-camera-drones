package de.lardtom.camdrones.award;

import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class AwardRules {
    public static final double AIR_GAP = 1.0;
    public static final int NO_DRONE = -1;

    private AwardRules() {
    }

    public static boolean crewFlight(Optional<UUID> pilot, UUID operator, boolean armed, boolean airborne) {
        return operator != null && pilot != null && pilot.isPresent() && !pilot.get().equals(operator) && armed && airborne;
    }

    public static boolean missionCompleted(NavLogic.MissionState previous, NavLogic.MissionState next) {
        boolean running = previous == NavLogic.MissionState.RUNNING || previous == NavLogic.MissionState.PAUSED;
        return running && next == NavLogic.MissionState.DONE;
    }

    public static final class Take {
        private boolean recording;
        private int drone = NO_DRONE;

        public OptionalInt update(boolean recording, int cameraDrone) {
            if (recording) {
                this.recording = true;
                if (cameraDrone != NO_DRONE) {
                    this.drone = cameraDrone;
                }
                return OptionalInt.empty();
            }
            int finished = this.recording ? this.drone : NO_DRONE;
            this.reset();
            return finished == NO_DRONE ? OptionalInt.empty() : OptionalInt.of(finished);
        }

        public void reset() {
            this.recording = false;
            this.drone = NO_DRONE;
        }
    }
}
