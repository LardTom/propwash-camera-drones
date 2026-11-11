package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlightClassTest {
    private static final String ASSIST_OUTPUT = "de.lardtom.propwash.api.flight.AssistOutput";

    private static Class<?> assistOutput() {
        try {
            return Class.forName(ASSIST_OUTPUT);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Test
    void assistedSpeedsStayInsidePropwashLimits() {
        for (FlightClass c : FlightClass.values()) {
            assertTrue(c.cineSpeed() < c.normalSpeed() && c.normalSpeed() < c.sportSpeed(), c.key());
            assertTrue(c.normalSpeed() <= NavOutput.MAX_SPEED, c.key());
            assertTrue(c.climbSpeed() <= NavOutput.MAX_CLIMB && c.descendSpeed() <= c.climbSpeed(), c.key());
            assertTrue(c.minHoverMinutes() > 10 && c.minHoverMinutes() < c.maxHoverMinutes(), c.key());
            assertEquals(c.normalSpeed(), c.assistedSpeed(c.sportSpeed()), 1.0E-9, c.key());
        }
    }

    @Test
    void portableLimitsMatchThePropwashApi() throws ReflectiveOperationException {
        Class<?> api = assistOutput();
        assumeTrue(api != null, "Propwash-API nicht im Klassenpfad (Backport)");
        assertEquals(api.getField("MAX_SPEED").getDouble(null), NavOutput.MAX_SPEED, 0.0);
        assertEquals(api.getField("MAX_CLIMB").getDouble(null), NavOutput.MAX_CLIMB, 0.0);
        assertEquals(api.getField("MAX_LABEL").getInt(null), NavOutput.MAX_LABEL);
    }

    @Test
    void classesGrowInMassAndCompactStaysUnder250Grams() {
        FlightClass[] all = FlightClass.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i - 1].takeoffGrams() < all[i].takeoffGrams(), all[i].key());
            assertTrue(all[i - 1].batteryWh() < all[i].batteryWh(), all[i].key());
        }
        assertTrue(FlightClass.COMPACT.takeoffGrams() < 250);
    }

    @Test
    void sensorsAndGimbalsFollowTheClassTable() {
        assertFalse(FlightClass.COMPACT.allAroundSensors());
        assertTrue(FlightClass.TOUR.allAroundSensors() && FlightClass.CINE.allAroundSensors());
        assertEquals(30.0, FlightClass.COMPACT.gimbalMaxTilt(), 0.0);
        assertEquals(30.0, FlightClass.TOUR.gimbalMaxTilt(), 0.0);
        assertEquals(60.0, FlightClass.CINE.gimbalMaxTilt(), 0.0);
        assertEquals(-90.0, FlightClass.GIMBAL_MIN_TILT, 0.0);
        assertTrue(FlightClass.CINE.gimbalFullPan() && !FlightClass.TOUR.gimbalFullPan() && !FlightClass.COMPACT.gimbalFullPan());
        assertEquals(42.0, FlightClass.TOUR.hoverMinutes(), 1.0E-9);
    }

    @Test
    void fullMissionFitsIntoDroneData() {
        assertTrue(MissionLimits.fits(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST));
        assertFalse(MissionLimits.fits(MissionLimits.MAX_WAYPOINTS + 1, 0));
        assertFalse(MissionLimits.fits(0, MissionLimits.MAX_POINTS_OF_INTEREST + 1));
        assertFalse(MissionLimits.fits(-1, 0));
    }
}
