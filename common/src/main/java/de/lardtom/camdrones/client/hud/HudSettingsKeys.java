package de.lardtom.camdrones.client.hud;

import java.util.ArrayList;
import java.util.List;

public final class HudSettingsKeys {
    public static final String BASE = "camdrones.hud.settings.";
    public static final String TITLE = BASE + "title";
    public static final String UNIT = BASE + "unit";
    public static final String UNIT_TOOLTIP = BASE + "unit.tooltip";
    public static final String VOLUME = BASE + "volume";
    public static final String VOLUME_VALUE = BASE + "volume.value";
    public static final String VOLUME_TOOLTIP = BASE + "volume.tooltip";

    private HudSettingsKeys() {
    }

    public static List<String> all() {
        List<String> keys = new ArrayList<>(List.of(TITLE, UNIT, UNIT_TOOLTIP, VOLUME, VOLUME_VALUE, VOLUME_TOOLTIP));
        for (SpeedUnit unit : SpeedUnit.values()) {
            keys.add(unit.key());
        }
        return List.copyOf(keys);
    }
}
