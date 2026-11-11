package de.lardtom.camdrones.client.hud;

import java.util.Locale;
import java.util.Optional;

public enum SpeedUnit {
    METRES_PER_SECOND("m/s", 1.0),
    KILOMETRES_PER_HOUR("km/h", 3.6);

    public static final SpeedUnit DEFAULT = METRES_PER_SECOND;

    private final String symbol;
    private final double factor;

    SpeedUnit(String symbol, double factor) {
        this.symbol = symbol;
        this.factor = factor;
    }

    public String symbol() {
        return this.symbol;
    }

    public String key() {
        return "camdrones.hud.unit." + this.name().toLowerCase(Locale.ROOT);
    }

    public double factor() {
        return this.factor;
    }

    public double convert(double metresPerSecond) {
        return metresPerSecond * this.factor;
    }

    public SpeedUnit next() {
        SpeedUnit[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    public static Optional<SpeedUnit> bySymbol(String symbol) {
        for (SpeedUnit unit : values()) {
            if (unit.symbol.equals(symbol)) {
                return Optional.of(unit);
            }
        }
        return Optional.empty();
    }
}
