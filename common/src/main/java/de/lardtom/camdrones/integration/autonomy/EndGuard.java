package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.event.PropwashEvents;

import java.util.Objects;

final class EndGuard {
    private EndGuard() {
    }

    static PropwashEvents.AutonomyEnded of(PropwashEvents.AutonomyEnded listener) {
        Objects.requireNonNull(listener, "listener");
        return (drone, owner, id, reason) -> {
            try {
                listener.onAutonomyEnded(drone, owner, id, reason);
            } catch (RuntimeException e) {
                CamDrones.LOG.warn("{}: Ende des Flugs ohne Pilot {} ({}) nicht verarbeitet", CamDrones.MOD_NAME, id, reason, e);
            }
        };
    }
}
