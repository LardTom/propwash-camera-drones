package de.lardtom.camdrones.bench;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildStats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlightTimeTest {
    private static final Set<String> ALLOWED_WARNINGS = Set.of("sluggish_motors");
    private static final double MASS_TOLERANCE = 0.05;
    private static final double MIN_THRUST_TO_WEIGHT = 1.8;
    private static final double MAX_THRUST_TO_WEIGHT = 4.0;
    private static final double MAX_SPORT_RESERVE = 1.6;

    @BeforeAll
    public static void printTable() {
        System.out.println(Bench.header());
        for (CamPresets.Entry entry : CamPresets.ALL) {
            System.out.println(Bench.row(entry.path(), entry.build()));
        }
    }

    @Test
    public void presetsAreBuildableWithoutProblems() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            BuildCheck check = Bench.check(entry.build());
            assertTrue(check.buildable(), entry.path() + ": " + check.unknownParts() + " " + check.problems());
            assertEquals(List.of(), check.warnings(), entry.path());
        }
    }

    @Test
    public void hoverTimeMatchesTheFlightClass() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            FlightClass c = entry.flightClass();
            double minutes = Bench.stats(entry.build()).hoverFlightTimeMin();
            assertTrue(minutes >= c.minHoverMinutes() && minutes <= c.maxHoverMinutes(),
                    entry.path() + ": Schweben " + minutes + " min, erwartet " + c.minHoverMinutes() + "–" + c.maxHoverMinutes());
        }
    }

    @Test
    public void sportSpeedIsReachableButNoRacer() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            FlightClass c = entry.flightClass();
            double top = Bench.stats(entry.build()).topSpeedKmh() / 3.6;
            assertTrue(top >= c.sportSpeed(), entry.path() + ": Höchsttempo " + top + " m/s unter Sport " + c.sportSpeed());
            assertTrue(top <= c.sportSpeed() * MAX_SPORT_RESERVE, entry.path() + ": Höchsttempo " + top + " m/s zu hoch");
        }
    }

    @Test
    public void takeoffMassMatchesTheFlightClass() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            FlightClass c = entry.flightClass();
            double grams = Bench.stats(entry.build()).massGrams();
            assertEquals(c.takeoffGrams(), grams, c.takeoffGrams() * MASS_TOLERANCE, entry.path() + ": Abflugmasse");
        }
        assertTrue(Bench.stats(CamPresets.COMPACT.build()).massGrams() < 250.0, "Kompakt muss unter 250 g bleiben");
    }

    @Test
    public void enoughThrustToClimbButCalm() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            BuildStats stats = Bench.stats(entry.build());
            assertTrue(stats.thrustToWeight() >= MIN_THRUST_TO_WEIGHT && stats.thrustToWeight() <= MAX_THRUST_TO_WEIGHT,
                    entry.path() + ": Schub/Gewicht " + stats.thrustToWeight());
        }
    }

    @Test
    public void noSeriousWarnings() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            for (String warning : Bench.stats(entry.build()).warnings()) {
                assertTrue(ALLOWED_WARNINGS.contains(warning), entry.path() + ": Warnung " + warning);
            }
        }
    }

    @Test
    public void classesKeepTheirOrder() {
        double compact = Bench.stats(CamPresets.COMPACT.build()).hoverFlightTimeMin();
        double tour = Bench.stats(CamPresets.TOUR.build()).hoverFlightTimeMin();
        double cine = Bench.stats(CamPresets.CINE.build()).hoverFlightTimeMin();
        assertTrue(tour > compact && compact > cine, "Tour schwebt am längsten, Kino am kürzesten");
        double compactTop = Bench.stats(CamPresets.COMPACT.build()).topSpeedKmh();
        double tourTop = Bench.stats(CamPresets.TOUR.build()).topSpeedKmh();
        double cineTop = Bench.stats(CamPresets.CINE.build()).topSpeedKmh();
        assertTrue(compactTop < tourTop && tourTop < cineTop, "Höchsttempo steigt von Kompakt über Tour zu Kino");
    }
}
