package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CamOsd {
    public static final double OBSTACLE_SHOWN = ObstacleGuard.WARN_DISTANCE;
    public static final double OBSTACLE_CLOSE = ObstacleGuard.STOP_DISTANCE + 1.5;
    public static final double FLIGHT_LEFT_CAUTION = 180.0;
    public static final double FLIGHT_LEFT_WARNING = 60.0;
    public static final long WARNING_CYCLE_MILLIS = 1600L;
    public static final int MODE_HINT_CHARS = 28;
    public static final Map<String, String> MODE_HINTS = Map.of(
            "N", "N: GPS HOLDS POS + HORIZON",
            "C", "C: SLOW + SMOOTH FOR VIDEO",
            "S", "S: FAST, NO OBSTACLE STOP",
            "ATTI", "ATTI: NO GPS, DRIFTS IN WIND");

    public enum Warning {
        OBSTACLE("OBSTACLE"),
        ALT_LIMIT("ALT LIMIT"),
        DIST_LIMIT("DIST LIMIT"),
        NO_GPS("NO GPS ATTI"),
        OBSTACLE_OFF("ATTI - OBSTACLE SENSING OFF", "camdrones.osd.warning.obstacle_off"),
        PAUSED("PAUSED"),
        HIGH_WIND("HIGH WIND"),
        OPERATOR("OPERATOR"),
        COMPASS("COMPASS ERROR", "camdrones.osd.warning.compass"),
        GIMBAL_LIMIT("GIMBAL LIMIT");

        private final String text;
        private final String key;

        Warning(String text) {
            this(text, null);
        }

        Warning(String text, String key) {
            this.text = text;
            this.key = key;
        }

        public String text() {
            return this.text;
        }

        public String key() {
            return this.key;
        }

        public String shown() {
            if (this.key == null) {
                return this.text;
            }
            String translated = HudText.ascii(Language.getInstance().getOrDefault(this.key, this.text)).strip();
            return translated.isEmpty() ? this.text : translated.toUpperCase(Locale.ROOT);
        }

        public String path() {
            return "warning/" + this.name().toLowerCase(Locale.ROOT);
        }
    }

    private CamOsd() {
    }

    public static OsdText mode(CamOsdState state) {
        if (!state.cameraDrone() || state.label().isBlank()) {
            return null;
        }
        return OsdText.of(HudText.ascii(state.label().strip()), state.atti() ? OsdTone.CAUTION : OsdTone.NORMAL);
    }

    public static OsdText modeHint(CamOsdState state) {
        if (!state.cameraDrone() || state.hint().isEmpty()) {
            return null;
        }
        return OsdText.of(modeHintText(state.hint()), OsdTone.ACCENT);
    }

    public static String modeHintText(String mode) {
        String fallback = MODE_HINTS.getOrDefault(mode, mode);
        return modeHintText(Language.getInstance().getOrDefault(ModeHint.key(mode), fallback), fallback);
    }

    public static String modeHintText(String translated, String fallback) {
        String text = HudText.ascii(transliterate(translated == null ? "" : translated).toUpperCase(Locale.ROOT)).strip();
        if (text.isEmpty()) {
            text = fallback;
        }
        return text.length() > MODE_HINT_CHARS ? text.substring(0, MODE_HINT_CHARS).strip() : text;
    }

    public static String transliterate(String text) {
        return text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE")
                .replace("ß", "ss").replace("–", "-").replace("—", "-");
    }

    public static OsdText gimbal(CamOsdState state) {
        if (!state.cameraDrone() || !Double.isFinite(state.gimbalTilt())) {
            return null;
        }
        return OsdText.of(HudText.tilt(state.gimbalTilt()), state.operator() ? OsdTone.ACCENT : OsdTone.NORMAL);
    }

    public static OsdText mission(CamOsdState state) {
        if (!state.cameraDrone() || !state.missionActive() || state.missionSize() <= 0) {
            return null;
        }
        OsdTone tone = state.missionState() == NavLogic.MissionState.PAUSED ? OsdTone.CAUTION : OsdTone.NORMAL;
        return OsdText.of(HudText.mission(state.missionIndex(), state.missionSize(), state.missionSecondsLeft()), tone);
    }

    public static boolean obstacleShown(CamOsdState state) {
        return state.cameraDrone() && state.obstacleSensor().isPresent() && Double.isFinite(state.obstacleDistance())
                && state.obstacleDistance() < OBSTACLE_SHOWN;
    }

    public static OsdText obstacle(CamOsdState state) {
        if (!obstacleShown(state)) {
            return null;
        }
        boolean close = state.obstacleDistance() < OBSTACLE_CLOSE || state.warns(NavOutput.Warning.OBSTACLE_STOP);
        return OsdText.of(HudText.obstacle(state.obstacleSensor().orElseThrow(), state.obstacleDistance()),
                close ? OsdTone.WARNING : OsdTone.CAUTION);
    }

    public static OsdText flightLeft(CamOsdState state) {
        if (!state.cameraDrone() || !Double.isFinite(state.flightSecondsLeft())) {
            return null;
        }
        double seconds = Math.max(0.0, state.flightSecondsLeft());
        OsdTone tone = seconds < FLIGHT_LEFT_WARNING ? OsdTone.WARNING : seconds < FLIGHT_LEFT_CAUTION ? OsdTone.CAUTION : OsdTone.NORMAL;
        return OsdText.of(HudText.flightLeft(seconds), tone);
    }

    public static OsdText warning(Warning warning, CamOsdState state) {
        if (!state.cameraDrone()) {
            return null;
        }
        return switch (warning) {
            case OBSTACLE -> state.warns(NavOutput.Warning.OBSTACLE_STOP) ? OsdText.of(warning.text(), OsdTone.WARNING)
                    : state.warns(NavOutput.Warning.OBSTACLE) ? OsdText.of(warning.text(), OsdTone.CAUTION) : null;
            case ALT_LIMIT -> state.warns(NavOutput.Warning.ALTITUDE_LIMIT) ? OsdText.of(warning.text(), OsdTone.CAUTION) : null;
            case DIST_LIMIT -> state.warns(NavOutput.Warning.DISTANCE_LIMIT) ? OsdText.of(warning.text(), OsdTone.CAUTION) : null;
            case NO_GPS -> state.warns(NavOutput.Warning.NO_GPS) || state.atti() && !state.warns(NavOutput.Warning.COMPASS)
                    ? OsdText.of(warning.text(), OsdTone.CAUTION) : null;
            case OBSTACLE_OFF -> state.warns(NavOutput.Warning.OBSTACLE_OFF) ? OsdText.of(warning.shown(), OsdTone.WARNING) : null;
            case PAUSED -> state.warns(NavOutput.Warning.PAUSED) || state.missionState() == NavLogic.MissionState.PAUSED
                    ? OsdText.of(warning.text(), OsdTone.ACCENT) : null;
            case HIGH_WIND -> state.highWind() ? OsdText.of(warning.text(), OsdTone.CAUTION) : null;
            case OPERATOR -> state.operator() ? OsdText.of(warning.text(), OsdTone.ACCENT) : null;
            case COMPASS -> state.warns(NavOutput.Warning.COMPASS) ? OsdText.of(warning.shown(), OsdTone.WARNING) : null;
            case GIMBAL_LIMIT -> state.gimbalStop() ? OsdText.of(warning.text(), OsdTone.CAUTION) : null;
        };
    }

    public static List<OsdText> warnings(CamOsdState state) {
        List<OsdText> active = new ArrayList<>();
        for (Warning warning : Warning.values()) {
            OsdText text = warning(warning, state);
            if (text != null) {
                active.add(text);
            }
        }
        return List.copyOf(active);
    }

    public static OsdText cycle(List<OsdText> warnings, long millis) {
        if (warnings.isEmpty()) {
            return null;
        }
        int index = (int) Math.floorMod(Math.floorDiv(millis, WARNING_CYCLE_MILLIS), (long) warnings.size());
        return warnings.get(index);
    }
}
