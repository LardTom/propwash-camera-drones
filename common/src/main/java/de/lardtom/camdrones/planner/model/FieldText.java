package de.lardtom.camdrones.planner.model;

import java.util.List;
import java.util.Locale;

public record FieldText(String key, List<String> args) {
    public static final String PREFIX = "camdrones.planner.";

    public FieldText {
        args = List.copyOf(args);
    }

    public static FieldText of(String key, String... args) {
        return new FieldText(PREFIX + key, List.of(args));
    }

    public static String number(double value, int decimals, char separator) {
        String text = String.format(Locale.ROOT, "%." + Math.max(0, decimals) + "f", value);
        if (text.startsWith("-") && text.matches("-0([.,]0*)?")) {
            text = text.substring(1);
        }
        return separator == '.' ? text : text.replace('.', separator);
    }

    public static String signed(int value) {
        return value > 0 ? "+" + value : value < 0 ? "−" + -value : "0";
    }

    public static String duration(double seconds) {
        long total = Math.max(0L, Math.round(seconds));
        long hours = total / 3600L;
        long minutes = total % 3600L / 60L;
        long secs = total % 60L;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }
}
