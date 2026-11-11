package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.NavInput;

import java.util.Locale;

public final class HudText {
    public static final String UNKNOWN = "--";
    public static final String UNKNOWN_TIME = "-:--";
    public static final double SHORT_DISTANCE = 10.0;
    public static final double MAX_SECONDS = 100.0 * 3600.0 - 1.0;
    public static final double MAX_MISSION_SECONDS = 10.0 * 3600.0 - 1.0;
    public static final double MAX_FLIGHT_LEFT_SECONDS = 100.0 * 60.0 - 1.0;

    private HudText() {
    }

    public static boolean printable(char c) {
        return c >= ' ' && c <= '~';
    }

    public static boolean printable(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!printable(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String ascii(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(printable(c) ? c : '?');
        }
        return out.toString();
    }

    public static String decimal(double value, int digits) {
        if (!Double.isFinite(value)) {
            return UNKNOWN;
        }
        String text = String.format(Locale.ROOT, "%." + digits + "f", value);
        return text.matches("-0(\\.0+)?") ? text.substring(1) : text;
    }

    public static String number(double value) {
        if (!Double.isFinite(value)) {
            return UNKNOWN;
        }
        return Math.abs(value) < SHORT_DISTANCE ? decimal(value, 1) : decimal(value, 0);
    }

    public static String metres(double value) {
        return Double.isFinite(value) ? number(value) + "M" : UNKNOWN;
    }

    public static String duration(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            return UNKNOWN_TIME;
        }
        long total = (long) Math.floor(Math.min(seconds, MAX_SECONDS));
        long hours = total / 3600L;
        long minutes = total / 60L % 60L;
        long secs = total % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    public static String minutes(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            return UNKNOWN_TIME;
        }
        long total = (long) Math.floor(Math.min(seconds, MAX_FLIGHT_LEFT_SECONDS));
        return String.format(Locale.ROOT, "%d:%02d", total / 60L, total % 60L);
    }

    public static String tilt(double degrees) {
        if (!Double.isFinite(degrees)) {
            return "G " + UNKNOWN;
        }
        long rounded = Math.round(Math.clamp(degrees, -180.0, 180.0));
        return "G " + rounded;
    }

    public static String mission(int index, int size, double secondsLeft) {
        int shown = Math.clamp(index + 1L, 1, Math.max(1, size));
        String head = "WP " + shown + "/" + Math.max(0, size);
        return Double.isFinite(secondsLeft) && secondsLeft >= 0.0 ? head + " " + duration(Math.min(secondsLeft, MAX_MISSION_SECONDS)) : head;
    }

    public static String arrow(NavInput.Sensor sensor) {
        return switch (sensor) {
            case FORWARD -> "^";
            case BACK -> "v";
            case LEFT -> "<";
            case RIGHT -> ">";
            case UP -> "UP";
            case DOWN -> "DN";
        };
    }

    public static String obstacle(NavInput.Sensor sensor, double distance) {
        return arrow(sensor) + " " + metres(Math.max(0.0, distance));
    }

    public static String flightLeft(double seconds) {
        return "LEFT " + minutes(seconds);
    }

    public static String hudMetres(double value) {
        return Double.isFinite(value) ? number(value) + "m" : UNKNOWN;
    }

    public static String hudSpeed(double metresPerSecond) {
        return Double.isFinite(metresPerSecond) ? decimal(metresPerSecond, 1) + "m/s" : UNKNOWN;
    }

    public static String hudSpeedKmh(double metresPerSecond) {
        if (!Double.isFinite(metresPerSecond)) {
            return UNKNOWN;
        }
        return decimal(SpeedUnit.KILOMETRES_PER_HOUR.convert(metresPerSecond), 0) + "km/h";
    }

    public static String hudSpeed(double metresPerSecond, SpeedUnit unit) {
        return unit == SpeedUnit.KILOMETRES_PER_HOUR ? hudSpeedKmh(metresPerSecond) : hudSpeed(metresPerSecond);
    }

    public static String percent(double fraction) {
        if (!Double.isFinite(fraction)) {
            return UNKNOWN;
        }
        return Math.round(Math.clamp(fraction, 0.0, 1.0) * 100.0) + "%";
    }
}
