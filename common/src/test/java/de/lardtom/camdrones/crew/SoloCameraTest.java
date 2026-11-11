package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.crew.model.SoloCamera;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoloCameraTest {
    private static final SoloCamera.Facts READY = new SoloCamera.Facts(true, true, false, true, false);

    @Test
    void ownerGetsTheCameraOnlyWhileTheDroneFliesItself() {
        assertEquals(SoloCamera.Verdict.OK, SoloCamera.check(READY));
        assertEquals(SoloCamera.Verdict.NOT_AUTONOMOUS, SoloCamera.check(new SoloCamera.Facts(true, true, false, false, false)),
                "ohne Flug ohne Pilot bleibt das Livebild wie bisher");
        assertEquals(SoloCamera.Verdict.PILOTED, SoloCamera.check(new SoloCamera.Facts(true, true, true, true, false)),
                "im Handflug bleibt alles wie heute");
        assertEquals(SoloCamera.Verdict.NOT_OWNER, SoloCamera.check(new SoloCamera.Facts(true, false, false, true, false)));
        assertEquals(SoloCamera.Verdict.NO_GIMBAL, SoloCamera.check(new SoloCamera.Facts(false, true, false, true, false)));
    }

    @Test
    void aSecondPlayerAsCameraOperatorHasPriority() {
        assertEquals(SoloCamera.Verdict.CREW, SoloCamera.check(new SoloCamera.Facts(true, true, false, true, true)));
        SoloCamera.Watch crew = new SoloCamera.Watch(true, true, true, false, true, true);
        assertEquals(Optional.of(SoloCamera.End.CREW), SoloCamera.keep(crew), "Kameramann der Crew löst die Kamera allein ab");
    }

    @Test
    void sessionEndsWithTheFlightThePilotOrTheBinding() {
        assertEquals(Optional.empty(), SoloCamera.keep(new SoloCamera.Watch(true, true, true, false, true, false)));
        assertEquals(Optional.of(SoloCamera.End.NOT_AUTONOMOUS), SoloCamera.keep(new SoloCamera.Watch(true, true, true, false, false, false)),
                "Landung oder Ende des Flugs ohne Pilot bringt den Spieler zurück");
        assertEquals(Optional.of(SoloCamera.End.PILOTED), SoloCamera.keep(new SoloCamera.Watch(true, true, true, true, true, false)));
        assertEquals(Optional.of(SoloCamera.End.UNBOUND), SoloCamera.keep(new SoloCamera.Watch(true, true, false, false, true, false)),
                "Esc beendet Propwashs Bindung");
        assertEquals(Optional.of(SoloCamera.End.OFFLINE), SoloCamera.keep(new SoloCamera.Watch(true, false, true, false, true, false)));
        assertEquals(Optional.of(SoloCamera.End.DRONE_GONE), SoloCamera.keep(new SoloCamera.Watch(false, true, false, false, false, false)));
    }

    @Test
    void offeredOnlyOutsideFpvForAnArmedDroneWithoutPilot() {
        assertTrue(SoloCamera.offered(true, false, true, false, false, false));
        assertTrue(SoloCamera.offered(true, false, true, false, true, true), "eigene Kamera allein erneut öffnen");
        assertFalse(SoloCamera.offered(true, false, false, false, false, false), "abgestellte Drohne: Livebild wie bisher mit Meldung");
        assertFalse(SoloCamera.offered(true, true, true, false, false, false), "im FPV-Flug nicht");
        assertFalse(SoloCamera.offered(true, false, true, true, false, false), "fremder Pilot");
        assertFalse(SoloCamera.offered(true, false, true, false, true, false), "Kameramann der Crew");
        assertFalse(SoloCamera.offered(false, false, true, false, false, false));
    }

    @Test
    void onePlayerHoldsOneSoloCamera() {
        SoloCamera solo = new SoloCamera();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        solo.start(first, player);
        assertEquals(Optional.of(player), solo.player(first));
        solo.start(second, player);
        assertEquals(Optional.empty(), solo.player(first));
        assertEquals(Optional.of(second), solo.droneOf(player));
        assertEquals(Optional.of(player), solo.end(second));
        assertTrue(solo.sessions().isEmpty());
    }
}
