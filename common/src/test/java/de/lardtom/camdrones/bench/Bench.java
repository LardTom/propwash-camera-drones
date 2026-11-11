package de.lardtom.camdrones.bench;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;

import java.util.Locale;

public final class Bench {
    private Bench() {
    }

    public static BuildCheck check(BuildSpec spec) {
        PartFiles.registerAll();
        return PropwashApi.builds().check(spec);
    }

    public static BuildStats stats(BuildSpec spec) {
        BuildCheck check = check(spec);
        if (!check.known()) {
            throw new IllegalArgumentException("unbekannte Teile: " + check.unknownParts());
        }
        return PropwashApi.builds().analyze(spec).orElseThrow(() -> new IllegalArgumentException("keine Analyse für " + spec));
    }

    public static String header() {
        return String.format(Locale.ROOT, "%-10s %7s %5s %5s %6s %6s %6s %6s %6s %6s %5s %5s %6s %5s  %s",
                "Aufbau", "g", "T/W", "Schw%", "Schw'", "Reise'", "Misch'", "km/h", "Reise", "A", "ESC%", "Akk%", "Mot°C", "Bild%",
                "Probleme / Warnungen");
    }

    public static String row(String label, BuildSpec spec) {
        BuildCheck check = check(spec);
        BuildStats s = stats(spec);
        return String.format(Locale.ROOT, "%-10s %7.1f %5.2f %5.1f %6.1f %6.1f %6.1f %6.1f %6.1f %6.1f %5.0f %5.0f %6.1f %5.1f  %s %s",
                label, s.massGrams(), s.thrustToWeight(), s.hoverThrottlePercent(), s.hoverFlightTimeMin(), s.cruiseFlightTimeMin(),
                s.mixedFlightTimeMin(), s.topSpeedKmh(), s.cruiseSpeedKmh(), s.fullThrottleCurrentA(), s.escLoadPercent(),
                s.batteryLoadPercent(), s.sustainedMotorTempC(), s.propsInViewPercent(),
                check.problems().isEmpty() ? "" : "P" + check.problems(),
                check.warnings().isEmpty() && s.warnings().isEmpty() ? "" : "W" + check.warnings() + s.warnings());
    }
}
