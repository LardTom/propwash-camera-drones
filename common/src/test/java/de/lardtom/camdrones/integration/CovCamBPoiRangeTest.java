package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamBPoiRangeTest {
    private static final double DT = 0.01;
    private static final Set<SensorDirection> ALL = EnumSet.allOf(SensorDirection.class);

    private static final class World implements NavContext {
        final double hitDistance;
        final List<double[]> rays = new ArrayList<>();
        final List<NavStatus> statuses = new ArrayList<>();

        World(double hitDistance) {
            this.hitDistance = hitDistance;
        }

        @Override
        public Optional<NavTarget> pointOfInterest(double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
            this.rays.add(new double[]{x, y, z, dirX, dirY, dirZ, range});
            if (!Double.isFinite(this.hitDistance) || this.hitDistance > range) {
                return Optional.empty();
            }
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            double s = this.hitDistance / length;
            return Optional.of(NavTarget.at(x + dirX * s, y + dirY * s, z + dirZ * s));
        }

        @Override
        public void status(NavStatus status) {
            this.statuses.add(status);
        }
    }

    private static NavAssist press(World world) {
        NavAssist assist = new NavAssist(FlightClass.TOUR, ALL, true, world);
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0);
        for (int i = 0; i < 20; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
        state.on(CamControl.POI_HERE);
        for (int i = 0; i < 5; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
        state.off(CamControl.POI_HERE);
        for (int i = 0; i < 100; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
        return assist;
    }

    @Test
    void pointOfInterestBeyondSixtyFourMetresOrSkyIsRefused() {
        World near = new World(40.0);
        assertEquals(NavLogic.Function.ORBIT, press(near).logic().function(), "Treffer in 40 m: Kreis");
        assertEquals(1, near.rays.size());
        assertEquals(64.0, near.rays.getFirst()[6], 1.0E-9, "Strahl bis 64 m");
        assertEquals(NavAssist.POI_RANGE, near.rays.getFirst()[6], 1.0E-9);

        World far = new World(80.0);
        NavAssist farAssist = press(far);
        assertEquals(NavLogic.Function.NONE, farAssist.logic().function(), "Treffer erst in 80 m: kein Kreis");
        assertEquals(1, far.rays.size());

        World sky = new World(Double.POSITIVE_INFINITY);
        NavAssist skyAssist = press(sky);
        assertEquals(NavLogic.Function.NONE, skyAssist.logic().function(), "Himmel: kein Kreis");
        assertTrue(skyAssist.status().map(s -> !"POI".equals(s.label())).orElse(true), "kein POI im OSD");
    }

    @Test
    void refusedPointOfInterestTellsThePilot() {
        World sky = new World(Double.POSITIVE_INFINITY);
        press(sky);
        Set<NavOutput.Warning> before = new HashSet<>(sky.statuses.get(10).warnings());
        String labelBefore = sky.statuses.get(10).label();
        boolean told = false;
        for (NavStatus status : sky.statuses.subList(20, sky.statuses.size())) {
            if (!before.containsAll(status.warnings()) || !labelBefore.equals(status.label())) {
                told = true;
                break;
            }
        }
        assertTrue(told, "Point of Interest ohne Ziel bis 64 m wird abgelehnt, ohne dass der Pilot eine Meldung bekommt");
    }
}
