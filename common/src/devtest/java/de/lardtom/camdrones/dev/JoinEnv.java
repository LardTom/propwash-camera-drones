package de.lardtom.camdrones.dev;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class JoinEnv {
    public static final String TARGET = "CAMDRONES_JOINTEST";
    public static final String LABEL = "CAMDRONES_JOINTEST_LABEL";
    public static final String EXPECT = "CAMDRONES_JOINTEST_EXPECT";
    public static final String HOLD = "CAMDRONES_JOINTEST_HOLD";
    public static final String CASE = "CAMDRONES_SERVERTEST";
    public static final String STOP_FILE = "jointest.stop";
    public static final String REPORT_FILE = "camdrones-servertest.txt";
    public static final String JOINED = "joined";
    public static final String GUEST_NAME = "camdrones_guest";
    public static final UUID GUEST_ID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + GUEST_NAME).getBytes(StandardCharsets.UTF_8));
    public static final String MISSION = "mission";
    public static final String CREW = "crew";
    public static final String FOLLOW = "follow";
    public static final String PERSIST_WRITE = "persist-write";
    public static final String PERSIST_CHECK = "persist-check";
    public static final String CREW_REJOIN = "crew-rejoin";

    private JoinEnv() {
    }

    private static @Nullable String get(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name.toLowerCase(Locale.ROOT).replace('_', '.'));
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static @Nullable String target() {
        return get(TARGET);
    }

    public static String label() {
        String value = get(LABEL);
        return value == null ? "join" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public static String serverCase() {
        String value = get(CASE);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static int holdSeconds() {
        String value = get(HOLD);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static List<String> expectations() {
        String value = get(EXPECT);
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split("\\|")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public static @Nullable String unmet(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String expected : expectations()) {
            boolean negate = expected.startsWith("!");
            String needle = (negate ? expected.substring(1) : expected).toLowerCase(Locale.ROOT);
            if (lower.contains(needle) == negate) {
                return (negate ? "unerwartet: " : "fehlt: ") + needle;
            }
        }
        return null;
    }
}
