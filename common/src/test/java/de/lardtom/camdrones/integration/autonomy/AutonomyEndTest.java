package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.event.PropwashEvent;
import de.lardtom.propwash.api.event.PropwashEvents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyEndTest {
    static final String TERRAIN_LIMIT = "TERRAIN_LIMIT";

    @Test
    void everyEndReasonOfPropwashHasItsOwnStatus() {
        for (AutonomyEnd reason : AutonomyEnd.values()) {
            AutonomyStatus status = assertDoesNotThrow(() -> AutonomyStatus.ended(reason), reason.name());
            assertEquals(AutonomyStatus.valueOf("ENDED_" + reason.name()), status, reason.name());
        }
    }

    @Test
    void worldEdgeEndsWithAHintInChatAndOverlay() {
        AutonomyStatus status = AutonomyStatus.ENDED_TERRAIN_LIMIT;
        assertTrue(status.error());
        assertTrue(status.chat());
        assertTrue(AutonomyStatus.overlay(status, "1"));
        assertFalse(AutonomyStatus.Reason.of(status, "1").isPresent());
    }

    @Test
    void endReasonsOfNewerPropwashBuildsAreKnownByName() {
        assertEquals(AutonomyStatus.ENDED_TERRAIN_LIMIT, AutonomyStatus.endedByName(TERRAIN_LIMIT));
        assertEquals(AutonomyStatus.ENDED_STOPPED, AutonomyStatus.endedByName("REASON_OF_A_LATER_VERSION"));
        assertFalse(AutonomyStatus.ENDED_STOPPED.error());
    }

    @Test
    void laterListenersGetEveryEndEvenIfTheCameraListenerFails() {
        PropwashEvent<PropwashEvents.AutonomyEnded> event = new PropwashEvent<>("autonomy_ended");
        List<AutonomyEnd> later = new ArrayList<>();
        event.register(EndGuard.of((drone, owner, id, reason) -> {
            throw new MatchException(null, null);
        }));
        event.register((drone, owner, id, reason) -> later.add(reason));
        for (AutonomyEnd reason : AutonomyEnd.values()) {
            assertDoesNotThrow(() -> event.fire(listener -> listener.onAutonomyEnded(null, null, null, reason)), reason.name());
        }
        assertEquals(List.of(AutonomyEnd.values()), later);
    }
}
