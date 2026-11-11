package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.PilotView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PilotViewTest {
    private static final int DRONE = 7;

    @Test
    void operatorAndSpectatorsAlwaysSeeTheGimbal() {
        for (PilotView.Image chosen : PilotView.Image.values()) {
            assertEquals(PilotView.Image.GIMBAL, PilotView.shown(true, false, chosen));
            assertEquals(PilotView.Image.GIMBAL, PilotView.shown(true, true, chosen));
        }
    }

    @Test
    void pilotNeverSeesTheGimbalWhileAnOperatorIsBound() {
        for (PilotView.Image chosen : PilotView.Image.values()) {
            assertEquals(PilotView.Image.FPV, PilotView.shown(false, true, chosen));
        }
        PilotView view = new PilotView();
        view.update(DRONE, false, false);
        assertEquals(PilotView.Event.GIMBAL, view.update(DRONE, true, false));
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false));
        assertEquals(PilotView.Event.OPERATOR, view.update(DRONE, false, true), "Hinweis, wenn der Operator das Gimbal-Bild übernimmt");
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, true));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, true));
        assertEquals(PilotView.Event.OPERATOR, view.update(DRONE, true, true));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, true));
        view.update(DRONE, false, false);
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false), "nach dem Operator wieder FPV, nicht das alte Gimbal-Bild");
    }

    @Test
    void soloPilotStartsWithFpvAndTogglesOnEachPress() {
        PilotView view = new PilotView();
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false));
        assertEquals(PilotView.Event.GIMBAL, view.update(DRONE, true, false));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, true, false), "gehaltene Taste schaltet nur einmal");
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false));
        assertEquals(PilotView.Event.FPV, view.update(DRONE, true, false));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false));
    }

    @Test
    void everyNewFlightStartsWithFpv() {
        PilotView view = new PilotView();
        view.update(DRONE, false, false);
        view.update(DRONE, true, false);
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE + 1, false, false), "andere Drohne sieht die Wahl nicht");
        assertEquals(PilotView.Event.NONE, view.update(PilotView.NONE, false, false));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, true, false), "Taste schon beim Start gedrückt schaltet nicht");
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false));
        view.update(DRONE, false, false);
        view.update(DRONE, true, false);
        assertEquals(PilotView.Event.NONE, view.update(DRONE + 1, true, false));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE + 1, false, false));
    }

    @Test
    void aRecordingShowsTheGimbalUntilItStops() {
        PilotView view = new PilotView();
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false, false));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false, false, 0L));
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, true, 0L), "Aufnahme ohne Operator nimmt das Gimbal-Bild");
        assertEquals(PilotView.Event.RECORDING, view.update(DRONE, false, false, true), "Hinweis beim Wechsel aufs Gimbal");
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false, true));
        assertEquals(PilotView.Event.RECORDING, view.update(DRONE, true, false, true), "Taste während der Aufnahme schaltet nicht um");
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false, true));
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, true, 0L));
        assertEquals(PilotView.Image.FPV, view.chosen(DRONE));
        assertEquals(PilotView.Event.FPV, view.update(DRONE, false, false, false), "nach dem Stopp zurück zur FPV-Kamera");
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false, false, 0L));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false, false));
    }

    @Test
    void aChosenGimbalStaysAfterTheRecording() {
        PilotView view = new PilotView();
        view.update(DRONE, false, false, false);
        assertEquals(PilotView.Event.GIMBAL, view.update(DRONE, true, false, false));
        assertEquals(PilotView.Event.RECORDING, view.update(DRONE, true, false, true));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, false, false), "Bild bleibt Gimbal, kein Hinweis");
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, false, 0L));
    }

    @Test
    void aRecordingRunningAtTheStartOfAFlightShowsTheGimbal() {
        PilotView view = new PilotView();
        assertEquals(PilotView.Event.RECORDING, view.update(DRONE, false, false, true));
        assertEquals(PilotView.Event.RECORDING, view.update(DRONE + 1, false, false, true), "neue Drohne, Aufnahme läuft weiter");
        assertEquals(PilotView.Event.NONE, view.update(PilotView.NONE, false, false, true));
    }

    @Test
    void withAnOperatorThePilotKeepsFpvWhileRecording() {
        PilotView view = new PilotView();
        view.update(DRONE, false, false, false);
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, true, true, 0L));
        assertEquals(PilotView.Event.NONE, view.update(DRONE, false, true, true));
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, true, true, true, 0L), "Operator und Zuschauer sehen das Gimbal-Bild");
        view.photo(DRONE, 0L);
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, true, false, 0L), "auch ein Foto ändert das Bild des Piloten nicht");
        PilotView solo = new PilotView();
        solo.update(DRONE, false, false, false);
        assertEquals(PilotView.Event.RECORDING, solo.update(DRONE, false, false, true));
        assertEquals(PilotView.Event.OPERATOR, solo.update(DRONE, false, true, true), "Operator übernimmt während der Aufnahme");
        assertEquals(PilotView.Image.FPV, solo.shown(DRONE, false, true, true, 0L));
    }

    @Test
    void aPhotoShowsTheGimbalBriefly() {
        PilotView view = new PilotView();
        long t0 = 5_000_000_000L;
        view.update(DRONE, false, false, false);
        view.photo(DRONE, t0);
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, false, t0), "Foto ohne Operator nimmt das Gimbal-Bild");
        assertEquals(PilotView.Image.FPV, view.shown(DRONE + 1, false, false, false, t0), "nur die Drohne des Fotos");
        for (int i = 0; i < PilotView.PHOTO_FRAMES; i++) {
            view.frame(DRONE);
        }
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, false, t0 + PilotView.PHOTO_NANOS - 1L));
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false, false, t0 + PilotView.PHOTO_NANOS), "nach kurzer Zeit wieder FPV");
        assertEquals(PilotView.Image.FPV, view.chosen(DRONE));
    }

    @Test
    void aPhotoWaitsForEnoughFramesButNotForever() {
        PilotView view = new PilotView();
        long t0 = 1_000L;
        view.update(DRONE, false, false, false);
        view.photo(DRONE, t0);
        view.frame(DRONE);
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, false, t0 + PilotView.PHOTO_NANOS), "erst nach "
                + PilotView.PHOTO_FRAMES + " Bildern zurück");
        view.frame(DRONE + 1);
        view.frame(DRONE);
        assertEquals(PilotView.Image.GIMBAL, view.shown(DRONE, false, false, false, t0 + PilotView.PHOTO_NANOS));
        view.frame(DRONE);
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false, false, t0 + PilotView.PHOTO_NANOS));
        view.photo(DRONE, t0);
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false, false, t0 + PilotView.PHOTO_MAX_NANOS), "ohne Bilder höchstens "
                + PilotView.PHOTO_MAX_NANOS / 1_000_000_000L + " s");
        view.photo(DRONE, t0);
        view.reset();
        assertEquals(PilotView.Image.FPV, view.shown(DRONE, false, false, false, t0), "Ende des Flugs löscht das Foto");
    }

    @Test
    void noDroneMeansFpv() {
        PilotView view = new PilotView();
        assertEquals(PilotView.Image.FPV, view.chosen(PilotView.NONE));
        assertEquals(PilotView.Image.GIMBAL, view.shown(PilotView.NONE, true, false));
    }
}
