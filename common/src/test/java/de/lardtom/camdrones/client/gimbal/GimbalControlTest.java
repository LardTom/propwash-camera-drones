package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.SendGate;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalControlTest {
    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void roleFollowsTheOperatorBindingFirst() {
        GimbalData free = GimbalData.NONE;
        GimbalData mine = GimbalData.NONE.withOperator(Optional.of(ME));
        GimbalData theirs = GimbalData.NONE.withOperator(Optional.of(OTHER));
        assertEquals(GimbalControl.Role.PILOT, GimbalControl.roleFor(free, ME, true));
        assertEquals(GimbalControl.Role.NONE, GimbalControl.roleFor(free, ME, false));
        assertEquals(GimbalControl.Role.OPERATOR, GimbalControl.roleFor(mine, ME, false));
        assertEquals(GimbalControl.Role.OPERATOR, GimbalControl.roleFor(mine, ME, true));
        assertEquals(GimbalControl.Role.NONE, GimbalControl.roleFor(theirs, ME, true), "Pilot ohne Gimbal, solange ein Operator gebunden ist");
        assertEquals(GimbalControl.Role.NONE, GimbalControl.roleFor(free, null, true));
    }

    @Test
    void withoutRoleNothingIsSent() {
        GimbalControl control = new GimbalControl();
        assertTrue(control.due(GimbalSetting.CENTER, 10_000L).isEmpty());
        assertTrue(control.local(1, 0L).isEmpty());
        assertTrue(control.drainEvents().isEmpty());
    }

    @Test
    void sendGateLimitsToTenPerSecondAndResendsLostUpdates() {
        SendGate gate = new SendGate();
        GimbalSetting stored = GimbalSetting.CENTER;
        gate.start(stored, 0L);
        GimbalSetting moved = stored.withTilt(-20.0);
        assertTrue(gate.due(moved, stored, 50L).isEmpty(), "höchstens alle 100 ms");
        assertEquals(Optional.of(moved), gate.due(moved, stored, 100L));
        assertTrue(gate.due(moved, stored, 300L).isEmpty(), "unverändert und noch nicht verloren");
        assertTrue(gate.due(moved, moved, 2_000L).isEmpty(), "Server hat übernommen");
        assertEquals(Optional.of(moved), gate.due(moved, stored, 1_100L), "Server hat nach 1 s nichts übernommen");
        int sent = 0;
        for (long t = 1_200L; t < 2_200L; t += 10L) {
            if (gate.due(stored.withZoom((t - 1_200L) / 1_000.0), stored, t).isPresent()) {
                sent++;
            }
        }
        assertEquals(10, sent, "10 Hz bei dauernder Bewegung");
        gate.reset();
        assertTrue(gate.due(null, stored, 5_000L).isEmpty());
    }
}
