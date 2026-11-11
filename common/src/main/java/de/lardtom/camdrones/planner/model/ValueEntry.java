package de.lardtom.camdrones.planner.model;

import java.util.OptionalDouble;

public final class ValueEntry {
    public static final int MAX_LENGTH = 12;

    public enum Status {
        EMPTY,
        INVALID,
        OK,
        CLAMPED
    }

    public record Range(double min, double max, int decimals, boolean wrap) {
        public Range {
            double lo = Math.min(min, max);
            double hi = Math.max(min, max);
            min = lo;
            max = hi;
            decimals = Math.clamp(decimals, 0, 3);
        }

        public static Range of(double min, double max, int decimals) {
            return new Range(min, max, decimals, false);
        }

        public static Range wrapping(int min, int span) {
            return new Range(min, min + span - 1, 0, true);
        }

        public double round(double value) {
            double factor = Math.pow(10.0, this.decimals);
            return Math.round(value * factor) / factor;
        }

        public boolean negatives() {
            return this.min < 0.0 || this.wrap;
        }
    }

    public record Result(Status status, double value) {
        public boolean usable() {
            return this.status == Status.OK || this.status == Status.CLAMPED;
        }
    }

    private ValueEntry() {
    }

    public static boolean allowed(int codepoint, Range range) {
        if (codepoint >= '0' && codepoint <= '9') {
            return true;
        }
        if (codepoint == '.' || codepoint == ',') {
            return range.decimals() > 0;
        }
        if (codepoint == '-' || codepoint == '−') {
            return range.negatives();
        }
        return codepoint == '+';
    }

    public static OptionalDouble parse(String raw) {
        String text = raw.strip().replace('−', '-').replace(',', '.');
        if (text.startsWith("+")) {
            text = text.substring(1);
        }
        if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
            return OptionalDouble.empty();
        }
        int dots = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c == '-') {
                if (i != 0) {
                    return OptionalDouble.empty();
                }
            } else if (c < '0' || c > '9') {
                return OptionalDouble.empty();
            }
        }
        if (dots > 1) {
            return OptionalDouble.empty();
        }
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    public static Result evaluate(String text, Range range) {
        if (text == null || text.strip().isEmpty()) {
            return new Result(Status.EMPTY, Double.NaN);
        }
        OptionalDouble parsed = parse(text);
        if (parsed.isEmpty()) {
            return new Result(Status.INVALID, Double.NaN);
        }
        double value = range.round(parsed.getAsDouble());
        if (range.wrap()) {
            double span = range.max() - range.min() + 1.0;
            return new Result(Status.OK, range.min() + ((value - range.min()) % span + span) % span);
        }
        if (value < range.min()) {
            return new Result(Status.CLAMPED, range.min());
        }
        if (value > range.max()) {
            return new Result(Status.CLAMPED, range.max());
        }
        return new Result(Status.OK, value);
    }

    public static String text(double value, Range range, char decimal) {
        double rounded = range.round(value);
        int decimals = 0;
        for (int d = range.decimals(); d > 0; d--) {
            double factor = Math.pow(10.0, d - 1);
            if (Math.abs(Math.round(rounded * factor) - rounded * factor) > 1.0E-6) {
                decimals = d;
                break;
            }
        }
        return FieldText.number(rounded, decimals, decimal);
    }
}
