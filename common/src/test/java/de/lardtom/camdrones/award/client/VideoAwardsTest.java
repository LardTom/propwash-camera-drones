package de.lardtom.camdrones.award.client;

import de.lardtom.camdrones.client.record.GimbalPathRecorder;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoAwardsTest {
    private static final UUID REPLAY = UUID.randomUUID();
    private static final int DRONE = 42;

    private static GimbalPathRecorder.Handover recording(int samples) {
        return new GimbalPathRecorder.Handover("Drohne " + samples, DRONE, samples, Math.min(samples, 9), Math.min(samples, 4), 10, 10 + samples,
                false, REPLAY, 0, false);
    }

    private static GimbalPathRecorder.Handover photo() {
        return new GimbalPathRecorder.Handover("Foto 1", DRONE, 1, 1, 1, 50, 51, true, REPLAY, 1, false);
    }

    private static GimbalPathRecorder.Handover empty() {
        return new GimbalPathRecorder.Handover("", DRONE, 0, 0, 0, 0, 0, false, REPLAY, -1, false);
    }

    private static VideoAwards.Handovers primed() {
        VideoAwards.Handovers handovers = new VideoAwards.Handovers();
        assertEquals(OptionalInt.empty(), handovers.update(Optional.empty()));
        return handovers;
    }

    @Test
    void onlyAFlashbackRecordingWithACameraPathCounts() {
        assertTrue(VideoAwards.counts(recording(120)));
        assertTrue(VideoAwards.counts(recording(1)));
        assertFalse(VideoAwards.counts(photo()), "Foto im Flashback-Modus");
        assertFalse(VideoAwards.counts(empty()), "leere Flashback-Aufnahme");
    }

    @Test
    void flashbackRecordingIsReportedOnceForItsDrone() {
        VideoAwards.Handovers handovers = primed();
        GimbalPathRecorder.Handover take = recording(120);
        assertEquals(OptionalInt.of(DRONE), handovers.update(Optional.of(take)));
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(take)), "nur einmal je Übergabe");
        assertEquals(OptionalInt.of(DRONE), handovers.update(Optional.of(recording(80))), "nächste Aufnahme zählt wieder");
    }

    @Test
    void photoInFlashbackModeIsNotReported() {
        VideoAwards.Handovers handovers = primed();
        GimbalPathRecorder.Handover shot = photo();
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(shot)), "Foto vergibt kein Video");
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(shot)));
        assertEquals(OptionalInt.of(DRONE), handovers.update(Optional.of(recording(60))), "Aufnahme nach dem Foto zählt");
    }

    @Test
    void emptyFlashbackRecordingIsNotReported() {
        VideoAwards.Handovers handovers = primed();
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(empty())), "leere Aufnahme vergibt kein Video");
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(empty())));
        assertEquals(OptionalInt.of(DRONE), handovers.update(Optional.of(recording(30))));
    }

    @Test
    void handoverFromBeforeTheWorldDoesNotCount() {
        VideoAwards.Handovers handovers = new VideoAwards.Handovers();
        GimbalPathRecorder.Handover old = recording(200);
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(old)));
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(old)));
        handovers.reset();
        GimbalPathRecorder.Handover next = recording(90);
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(next)), "Weltwechsel setzt den Stand neu");
        assertEquals(OptionalInt.empty(), handovers.update(Optional.of(next)));
    }
}
