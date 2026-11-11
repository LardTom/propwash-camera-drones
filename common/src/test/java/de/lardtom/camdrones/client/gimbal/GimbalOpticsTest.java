package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalOptics;
import de.lardtom.camdrones.part.GimbalTable;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalOpticsTest {
    @Test
    void focalLengthsGiveTheExpectedHorizontalFieldOfView() {
        assertEquals(73.74, GimbalOptics.horizontalFov(24.0), 0.01);
        assertEquals(39.60, GimbalOptics.horizontalFov(50.0), 0.01);
        assertEquals(28.84, GimbalOptics.horizontalFov(70.0), 0.01);
        assertEquals(23.91, GimbalOptics.horizontalFov(85.0), 0.01);
    }

    @Test
    void rangesFollowTheGimbalTable() {
        GimbalTable.Spec compact = GimbalTable.spec(GimbalTable.COMPACT).orElseThrow();
        GimbalTable.Spec tour = GimbalTable.spec(GimbalTable.TOUR).orElseThrow();
        GimbalTable.Spec cine = GimbalTable.spec(GimbalTable.CINE).orElseThrow();
        GimbalOptics.Range c = GimbalOptics.range(compact.builtInFocalMm(), compact.lensBay(), OptionalInt.empty());
        GimbalOptics.Range t = GimbalOptics.range(tour.builtInFocalMm(), tour.lensBay(), OptionalInt.empty());
        GimbalOptics.Range k = GimbalOptics.range(cine.builtInFocalMm(), cine.lensBay(), OptionalInt.of(85));
        GimbalOptics.Range empty = GimbalOptics.range(cine.builtInFocalMm(), cine.lensBay(), OptionalInt.empty());
        assertEquals(new GimbalOptics.Range(24, 48), c);
        assertEquals(new GimbalOptics.Range(24, 70), t);
        assertEquals(new GimbalOptics.Range(85, 170), k);
        assertEquals(new GimbalOptics.Range(24, 48), empty, "ohne Objektiv feste 24 mm");
    }

    @Test
    void zoomIsMonotonicAndClampedToTheApiRange() {
        GimbalOptics.Range tour = new GimbalOptics.Range(24, 70);
        double last = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 10; i++) {
            double fov = GimbalOptics.fov(tour, i / 10.0);
            assertTrue(fov < last, "Zoom " + i);
            last = fov;
        }
        assertEquals(GimbalOptics.horizontalFov(24), GimbalOptics.fov(tour, 0.0), 1.0E-9);
        assertEquals(GimbalOptics.horizontalFov(70), GimbalOptics.fov(tour, 1.0), 1.0E-9);
        assertEquals(GimbalProvider.MIN_FOV_DEGREES, GimbalOptics.fov(new GimbalOptics.Range(85, 170), 1.0));
        assertEquals(GimbalProvider.MIN_FOV_DEGREES, GimbalOptics.MIN_FOV);
        assertEquals(GimbalProvider.MAX_FOV_DEGREES, GimbalOptics.MAX_FOV);
        assertEquals(GimbalOptics.MAX_FOV, GimbalOptics.horizontalFov(0.0));
    }

    @Test
    void rangeWithoutEntriesUsesDigitalZoom() {
        assertEquals(new GimbalOptics.Range(24, 48), GimbalOptics.range(List.of(), false, OptionalInt.empty()));
        assertEquals(new GimbalOptics.Range(35, 70), GimbalOptics.range(List.of(35), false, OptionalInt.empty()));
    }
}
