package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.gimbal.model.SendGate;
import de.lardtom.camdrones.client.gimbal.model.TiltGlide;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.propwash.api.flight.GimbalModel;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiltGlideTest {
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);
    private static final long FRAME_NANOS = 1_000_000_000L / 60L;
    private static final long SEND_NANOS = SendGate.SEND_MILLIS * 1_000_000L;
    private static final long GLIDE = TiltGlide.GLIDE_NANOS;

    private static double remote(long nowNanos, long[] arrivals) {
        int received = 0;
        for (long at : arrivals) {
            if (at <= nowNanos) {
                received++;
            }
        }
        return Math.max(-60.0, -1.0 * received);
    }

    private static long[] arrivals(int count, long[] jitterNanos) {
        long[] out = new long[count];
        long at = 0L;
        for (int i = 0; i < count; i++) {
            at += SEND_NANOS + jitterNanos[i % jitterNanos.length];
            out[i] = at;
        }
        return out;
    }

    private static List<Double> pitches(boolean glide, long[] arrivals, int frames) {
        GimbalModel model = GimbalPose.create(CINE);
        TiltGlide tilt = new TiltGlide();
        Quaternionf frame = new Quaternionf();
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            long now = i * FRAME_NANOS + 3_000_000L;
            GimbalSetting setting = GimbalSetting.CENTER.withTilt(remote(now, arrivals));
            GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalResolver.Scene.free(CINE, 0.0, 0.0, 0.0, 0.0), setting);
            double wanted = glide ? tilt.shown(aim.tilt(), now) : aim.tilt();
            GimbalPose.aim(model, aim.withTilt(wanted), 0.0);
            GimbalPose.step(model, FRAME_NANOS / 1.0E9, frame);
            out.add(model.pitchDeg());
        }
        return out;
    }

    private static int stillFrames(List<Double> pitches, int from, int to) {
        int still = 0;
        for (int i = from + 1; i < to; i++) {
            if (Math.abs(pitches.get(i) - pitches.get(i - 1)) < 1.0E-6) {
                still++;
            }
        }
        return still;
    }

    @Test
    void remoteTiltAtTenHertzStepsWithoutTheGlide() {
        List<Double> pitches = pitches(false, arrivals(40, new long[]{0L}), 180);
        int still = stillFrames(pitches, 12, 150);
        assertTrue(still > 100, "ohne Gleiten bleiben die meisten Bilder stehen: " + still + " von 137");
    }

    @Test
    void remoteTiltAtTenHertzMovesInEveryFrameWithTheGlide() {
        List<Double> pitches = pitches(true, arrivals(40, new long[]{0L}), 180);
        double min = Double.MAX_VALUE;
        double max = 0.0;
        for (int i = 8; i < 150; i++) {
            double d = pitches.get(i) - pitches.get(i - 1);
            assertTrue(d < -1.0E-3, "Bild " + i + " steht oder läuft zurück: " + d);
            if (i >= 60) {
                min = Math.min(min, -d);
                max = Math.max(max, -d);
            }
        }
        assertTrue(max / min < 1.05, "gleichmäßig: " + min + " bis " + max + " Grad je Bild");
        assertEquals(1.0 / 6.0, (min + max) / 2.0, 0.01, "Tempo des Kameramanns: 1° je 100 ms");
    }

    @Test
    void jitteredUpdatesNeverStandStillOrRunBack() {
        long ms = 1_000_000L;
        List<Double> pitches = pitches(true, arrivals(40, new long[]{-12 * ms, 9 * ms, 0L, 14 * ms, -7 * ms}), 180);
        for (int i = 8; i < 150; i++) {
            double d = pitches.get(i) - pitches.get(i - 1);
            assertTrue(d < -1.0E-3, "Bild " + i + " läuft zurück oder steht: " + d);
        }
    }

    @Test
    void updatesOnServerTicksEveryHundredOrHundredFiftyMillisecondsNeverStandStill() {
        long ms = 1_000_000L;
        List<Double> pitches = pitches(true, arrivals(30, new long[]{0L, 50 * ms, 0L, 0L, 50 * ms}), 200);
        List<Double> stepped = pitches(false, arrivals(30, new long[]{0L, 50 * ms, 0L, 0L, 50 * ms}), 200);
        for (int i = 8; i < 170; i++) {
            double d = pitches.get(i) - pitches.get(i - 1);
            assertTrue(d < -1.0E-3, "Bild " + i + " läuft zurück oder steht: " + d);
        }
        assertTrue(stillFrames(stepped, 8, 170) > 120, "ohne Gleiten: " + stillFrames(stepped, 8, 170) + " stehende Bilder");
    }

    @Test
    void singleChangeArrivesWithinOneSendInterval() {
        TiltGlide glide = new TiltGlide();
        assertEquals(0.0, glide.shown(0.0, 0L), 1.0E-9);
        assertEquals(0.0, glide.shown(-10.0, 1_000L), 1.0E-4);
        assertEquals(-5.0, glide.shown(-10.0, 1_000L + GLIDE / 2), 1.0E-6);
        assertTrue(glide.gliding(1_000L + GLIDE / 2));
        assertEquals(-10.0, glide.shown(-10.0, 1_000L + GLIDE), 1.0E-9);
        assertTrue(!glide.gliding(1_000L + GLIDE));
        assertEquals(-10.0, glide.shown(-10.0, 1_000L + 3 * GLIDE), 1.0E-9);
        assertTrue(GLIDE >= SEND_NANOS * 3 / 2, "Gleiten überbrückt einen ausgelassenen Server-Tick");
    }

    @Test
    void newValueDuringAGlideStartsWhereTheCameraIs() {
        TiltGlide glide = new TiltGlide();
        glide.shown(0.0, 0L);
        glide.shown(-10.0, 0L);
        double mid = glide.shown(-10.0, GLIDE / 2);
        assertEquals(-5.0, mid, 1.0E-6);
        assertEquals(mid, glide.shown(20.0, GLIDE / 2), 1.0E-9);
        assertEquals((mid + 20.0) / 2.0, glide.shown(20.0, GLIDE), 1.0E-6);
        assertEquals(20.0, glide.shown(20.0, GLIDE * 3 / 2), 1.0E-9);
    }

    @Test
    void onlyRemoteManualTiltGlides() {
        TiltGlide glide = new TiltGlide();
        GimbalResolver.Aim mission = new GimbalResolver.Aim(GimbalResolver.Source.MISSION, GimbalSetting.Mode.FOLLOW, -45.0, 0.0, 0.0, 0.0);
        GimbalResolver.Aim manual = new GimbalResolver.Aim(GimbalResolver.Source.MANUAL, GimbalSetting.Mode.FOLLOW, -20.0, 0.0, 0.0, 0.0);
        assertEquals(-20.0, glide.apply(manual, true, 0L).tilt(), 1.0E-9);
        assertEquals(-20.0, glide.apply(manual.withTilt(-30.0), true, 1_000L).tilt(), 1.0E-4);
        assertEquals(-25.0, glide.apply(manual.withTilt(-30.0), true, 1_000L + GLIDE / 2).tilt(), 1.0E-6);
        assertEquals(-45.0, glide.apply(mission, true, 2_000L + GLIDE / 2).tilt(), 1.0E-9);
        assertEquals(-10.0, glide.apply(manual.withTilt(-10.0), true, 3_000L + GLIDE / 2).tilt(), 1.0E-9);
        assertEquals(-50.0, glide.apply(manual.withTilt(-50.0), false, 4_000L + GLIDE / 2).tilt(), 1.0E-9);
        assertEquals(-5.0, glide.apply(manual.withTilt(-5.0), true, 5_000L + GLIDE / 2).tilt(), 1.0E-9);
        GimbalResolver.Aim kept = glide.apply(manual.withTilt(-5.0), true, 6_000L + GLIDE);
        assertEquals(manual.withTilt(-5.0), kept);
    }

    @Test
    void firstValueAndValueAfterResetAreShownAtOnce() {
        TiltGlide glide = new TiltGlide();
        assertEquals(-35.0, glide.shown(-35.0, 5L), 1.0E-9);
        glide.reset();
        assertEquals(12.0, glide.shown(12.0, 7L), 1.0E-9);
        assertTrue(Double.isNaN(glide.shown(Double.NaN, 9L)));
    }
}
