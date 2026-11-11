package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.PilotGimbalInput;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotGimbalInputTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);

    @Test
    void dialMapsBothHalvesToTheTiltRange() {
        assertEquals(-90.0, TOUR.tiltFromDial(-1.0));
        assertEquals(-45.0, TOUR.tiltFromDial(-0.5));
        assertEquals(0.0, TOUR.tiltFromDial(0.0));
        assertEquals(15.0, TOUR.tiltFromDial(0.5));
        assertEquals(60.0, CINE.tiltFromDial(1.0));
        assertEquals(30.0, TOUR.tiltFromDial(7.0));
        assertEquals(0.0, TOUR.tiltFromDial(Double.NaN));
        assertEquals(0.0, TOUR.panFromDial(1.0));
        assertEquals(-90.0, CINE.panFromDial(-0.5));
    }

    @Test
    void zoomAxisRestsAtWide() {
        PilotGimbalInput input = new PilotGimbalInput();
        assertEquals(0.0, input.update(TOUR, 0.0, 0.0, -1.0, false, false, false, 0.0).zoom());
        assertEquals(0.5, input.update(TOUR, 0.0, 0.0, 0.0, false, false, false, 0.0).zoom());
        assertEquals(1.0, input.update(TOUR, 0.0, 0.0, 1.0, false, false, false, 0.0).zoom());
    }

    @Test
    void recenterThenStraightDownAndDialTakesOverAgain() {
        PilotGimbalInput input = new PilotGimbalInput();
        assertEquals(-45.0, input.update(TOUR, -0.5, 0.0, -1.0, false, false, false, 0.0).tilt());
        assertEquals(0.0, input.update(TOUR, -0.5, 0.0, -1.0, false, false, true, 0.0).tilt(), "zentrieren");
        assertEquals(0.0, input.update(TOUR, -0.5, 0.0, -1.0, false, false, false, 0.0).tilt(), "bleibt, bis das Rad bewegt wird");
        assertEquals(-90.0, input.update(TOUR, -0.5, 0.0, -1.0, false, false, true, 0.0).tilt(), "zweimal = senkrecht nach unten");
        assertEquals(-90.0, input.update(TOUR, -0.52, 0.0, -1.0, false, false, false, 0.0).tilt(), "kleines Zittern übernimmt nicht");
        assertEquals(-54.0, input.update(TOUR, -0.6, 0.0, -1.0, false, false, false, 0.0).tilt(), 1.0E-9, "Rad übernimmt");
    }

    @Test
    void lockCapturesTheHeadingOnce() {
        PilotGimbalInput input = new PilotGimbalInput();
        GimbalSetting follow = input.update(CINE, 0.0, 0.25, -1.0, false, false, false, 100.0);
        assertFalse(follow.locked());
        assertEquals(45.0, follow.pan());
        assertEquals(145.0, follow.heading(), 1.0E-9);
        GimbalSetting locked = input.update(CINE, 0.0, 0.25, -1.0, true, false, false, 100.0);
        assertTrue(locked.locked());
        assertEquals(145.0, locked.heading(), 1.0E-9);
        GimbalSetting turned = input.update(CINE, 0.0, 0.25, -1.0, true, false, false, 200.0);
        assertEquals(145.0, turned.heading(), 1.0E-9, "Rahmen dreht, Richtung bleibt");
        GimbalSetting panned = input.update(CINE, 0.0, 0.5, -1.0, true, false, false, 200.0);
        assertEquals(190.0, panned.heading(), 1.0E-9, "Schwenk dreht die feste Richtung");
    }

    @Test
    void takeOverStartsFromTheStoredSettingWithoutJump() {
        PilotGimbalInput input = new PilotGimbalInput();
        GimbalSetting stored = new GimbalSetting(GimbalSetting.Mode.FOLLOW, -60.0, 0.0, 0.0, 0.2);
        input.takeOver(stored, 0.3, 0.0, false, false);
        GimbalSetting first = input.update(TOUR, 0.3, 0.0, -0.6, false, false, false, 0.0);
        assertEquals(-60.0, first.tilt(), "Neigung vom Operator bleibt");
        assertEquals(0.2, first.zoom(), 1.0E-9);
        assertEquals(10.2, input.update(TOUR, 0.34, 0.0, -0.6, false, false, false, 0.0).tilt(), 1.0E-9, "Rad übernimmt nach Bewegung");
    }

    @Test
    void fpvSwitchSelectsFpvAndWinsOverLock() {
        PilotGimbalInput input = new PilotGimbalInput();
        GimbalSetting fpv = input.update(CINE, -0.25, 0.25, -1.0, false, true, false, 100.0);
        assertEquals(GimbalSetting.Mode.FPV, fpv.mode());
        assertTrue(fpv.fpv());
        assertFalse(fpv.locked());
        assertEquals(-22.5, fpv.tilt(), 1.0E-9, "Neigung kommt weiter vom Rad");
        assertEquals(100.0, fpv.heading(), 1.0E-9, "Richtung = Rahmen, Schwenk zählt nicht");
        assertEquals(GimbalSetting.Mode.FPV, input.update(CINE, -0.25, 0.25, -1.0, true, true, false, 150.0).mode(), "FPV vor Sperre");
        GimbalSetting locked = input.update(CINE, -0.25, 0.25, -1.0, true, false, false, 160.0);
        assertTrue(locked.locked(), "FPV aus, Sperre an");
        assertEquals(205.0, locked.heading(), 1.0E-9, "Richtung beim Verlassen von FPV festgehalten");
        assertEquals(205.0, input.update(CINE, -0.25, 0.25, -1.0, true, false, false, 300.0).heading(), 1.0E-9);
        GimbalSetting follow = input.update(CINE, -0.25, 0.25, -1.0, false, false, false, 300.0);
        assertEquals(GimbalSetting.Mode.FOLLOW, follow.mode());
        assertEquals(345.0, follow.heading(), 1.0E-9);
    }

    @Test
    void modeFollowsTheSwitches() {
        assertEquals(GimbalSetting.Mode.FOLLOW, PilotGimbalInput.mode(false, false));
        assertEquals(GimbalSetting.Mode.LOCK, PilotGimbalInput.mode(true, false));
        assertEquals(GimbalSetting.Mode.FPV, PilotGimbalInput.mode(false, true));
        assertEquals(GimbalSetting.Mode.FPV, PilotGimbalInput.mode(true, true));
    }

    @Test
    void takeOverWithFpvSwitchKeepsFpv() {
        PilotGimbalInput input = new PilotGimbalInput();
        input.takeOver(new GimbalSetting(GimbalSetting.Mode.LOCK, -30.0, 0.0, 80.0, 0.0), 0.0, 0.0, false, true);
        GimbalSetting first = input.update(TOUR, 0.0, 0.0, -1.0, false, true, false, 10.0);
        assertEquals(GimbalSetting.Mode.FPV, first.mode());
        assertEquals(-30.0, first.tilt(), "Neigung bleibt bis das Rad bewegt wird");
        assertEquals(10.0, first.heading(), 1.0E-9);
    }
}
