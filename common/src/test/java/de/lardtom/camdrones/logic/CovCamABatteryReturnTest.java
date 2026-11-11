package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamABatteryReturnTest {
    private static int[] fly(double charge) {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).sticks(0.0, 0.0, 0.0, 0.5);
        drone.in.battery(charge);
        int[] seen = {0, 0};
        drone.run(logic::update, 5.0, d -> {
            seen[0] += d.out.returnHome() ? 1 : 0;
            seen[1] += d.out.warnings().stream().anyMatch(w -> w.name().contains("BATTERY")) ? 1 : 0;
        });
        return seen;
    }

    @Test
    void lowBatteryRequestsReturnHomeOnceWithWarning() {
        int[] seen = fly(0.08);
        assertEquals(1, seen[0], "Rückkehr bei 8 % Akku " + seen[0] + "-mal angefordert");
        assertTrue(seen[1] > 0, "keine Warnung bei knappem Akku");
    }

    @Test
    void fullBatteryDoesNotReturn() {
        assertEquals(0, fly(0.9)[0]);
    }
}
