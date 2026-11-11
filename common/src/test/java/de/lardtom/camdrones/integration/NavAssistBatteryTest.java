package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavAssistBatteryTest {
    private static final double DT = 0.01;

    private static int returns(NavAssist assist, FakeFlight.State state, int steps) {
        int seen = 0;
        for (int i = 0; i < steps; i++) {
            FakeFlight.Output output = new FakeFlight.Output();
            state.step(DT);
            assist.update(state, output);
            seen += output.returnHome ? 1 : 0;
        }
        return seen;
    }

    private static NavAssist tour() {
        return new NavAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), true, NavContext.NONE);
    }

    @Test
    void lowBatteryAsksPropwashForTheReturnOnce() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0);
        state.battery = 0.15;
        NavAssist assist = tour();
        assertEquals(1, returns(assist, state, 300));
    }

    @Test
    void aCancelledReturnIsNotAskedForAgainAfterTheReset() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0);
        state.battery = 0.15;
        NavAssist assist = tour();
        assertEquals(1, returns(assist, state, 50));
        assist.reset();
        assertEquals(0, returns(assist, state, 300), "Propwash setzt die Flughilfe nach dem Heimflug zurück, der Pilot hat abgebrochen");
        state.battery = 0.05;
        returns(assist, state, 10);
        assertEquals("LAND", assist.status().orElseThrow().label(), "bei kritischem Akku landet die Drohne trotzdem");
    }
}
