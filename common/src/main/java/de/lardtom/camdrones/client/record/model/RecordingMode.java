package de.lardtom.camdrones.client.record.model;

import java.util.Locale;
import java.util.Optional;

public enum RecordingMode {
    FLASHBACK("flashback"),
    DIRECT("direct");

    public static final String KEY_PREFIX = "camdrones.record.mode.";

    private final String key;

    RecordingMode(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return KEY_PREFIX + this.key;
    }

    public String tooltipKey() {
        return KEY_PREFIX + this.key + ".tooltip";
    }

    public static Optional<RecordingMode> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.strip().toLowerCase(Locale.ROOT);
        for (RecordingMode mode : values()) {
            if (mode.key.equals(wanted)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    public static RecordingMode defaultFor(boolean flashbackInstalled) {
        return flashbackInstalled ? FLASHBACK : DIRECT;
    }

    public boolean selectable(boolean flashbackInstalled) {
        return this != FLASHBACK || flashbackInstalled;
    }

    public RecordingMode effective(boolean flashbackReady) {
        return this == FLASHBACK && !flashbackReady ? DIRECT : this;
    }
}
