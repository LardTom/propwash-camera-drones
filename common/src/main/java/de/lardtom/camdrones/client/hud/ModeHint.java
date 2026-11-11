package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.AttiMode;
import de.lardtom.camdrones.logic.nav.CineMode;
import de.lardtom.camdrones.logic.nav.NormalMode;
import de.lardtom.camdrones.logic.nav.SportMode;

import java.util.List;
import java.util.Locale;

public final class ModeHint {
    public static final double SECONDS = 5.0;
    public static final List<String> MODES = List.of(NormalMode.LABEL, CineMode.LABEL, SportMode.LABEL, AttiMode.LABEL);

    private String last = "";
    private String shown = "";
    private double since = Double.NaN;

    public static String mode(String label, boolean atti) {
        if (atti) {
            return AttiMode.LABEL;
        }
        String text = label == null ? "" : label.strip();
        return MODES.contains(text) ? text : "";
    }

    public static String key(String mode) {
        return "camdrones.osd.hint." + mode.toLowerCase(Locale.ROOT);
    }

    public String update(boolean flying, String label, boolean atti, double time) {
        if (!flying) {
            this.reset();
            return "";
        }
        String mode = mode(label, atti);
        if (!mode.isEmpty() && !mode.equals(this.last)) {
            this.last = mode;
            this.shown = mode;
            this.since = time;
        }
        if (!this.shown.isEmpty()) {
            double age = time - this.since;
            if (!mode.equals(this.shown) || !(age >= 0.0 && age < SECONDS)) {
                this.shown = "";
            }
        }
        return this.shown;
    }

    public String shown() {
        return this.shown;
    }

    public String last() {
        return this.last;
    }

    public void reset() {
        this.last = "";
        this.shown = "";
        this.since = Double.NaN;
    }
}
