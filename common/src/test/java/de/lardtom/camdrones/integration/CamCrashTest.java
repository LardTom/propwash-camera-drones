package de.lardtom.camdrones.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamCrashTest {
    @Test
    void damageEndsTheMissionOfACameraDrone() {
        assertTrue(CamCrash.endsMission(true, false, 2.0F, false));
    }

    @Test
    void cancelledEmptyOrForeignDamageAndPlayerHitsDoNot() {
        assertFalse(CamCrash.endsMission(false, false, 2.0F, false), "keine Kamera-Drohne");
        assertFalse(CamCrash.endsMission(true, true, 2.0F, false), "abgesagt");
        assertFalse(CamCrash.endsMission(true, false, 0.0F, false), "kein Schaden");
        assertFalse(CamCrash.endsMission(true, false, 4.0F, true), "Spieler bauen ab oder dürfen nicht, beides ist kein Crash");
    }
}
