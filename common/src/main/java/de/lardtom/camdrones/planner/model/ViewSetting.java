package de.lardtom.camdrones.planner.model;

import java.util.Locale;
import java.util.Optional;

public enum ViewSetting {
    OSD,
    CAMERA;

    public static final String LABEL_KEY = FieldText.PREFIX + "mission.view";
    public static final String TOOLTIP_KEY = FieldText.PREFIX + "mission.view.tooltip";
    public static final String RESULT_PREFIX = FieldText.PREFIX + "view.result.";
    public static final double RATE_PER_SECOND = ExitSetting.RATE_PER_SECOND;
    public static final double RATE_BURST = ExitSetting.RATE_BURST;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public FieldText value() {
        return FieldText.of("value.view." + this.key());
    }

    public static FieldText value(Optional<ViewSetting> setting) {
        return setting.map(ViewSetting::value).orElseGet(() -> FieldText.of(ExitSetting.UNKNOWN_KEY));
    }

    public ViewSetting step(int direction) {
        ViewSetting[] values = values();
        return values[Math.floorMod(this.ordinal() + Integer.signum(direction), values.length)];
    }

    public static Optional<ViewSetting> byId(int id) {
        ViewSetting[] values = values();
        return id >= 0 && id < values.length ? Optional.of(values[id]) : Optional.empty();
    }

    public static String messageKey(ExitSetting.Result result) {
        return RESULT_PREFIX + result.name().toLowerCase(Locale.ROOT);
    }

    public static ExitSetting.Result check(boolean withinRate, int id, boolean linked, UploadCheck.Drone drone, boolean operator) {
        if (!withinRate) {
            return ExitSetting.Result.RATE_LIMITED;
        }
        if (byId(id).isEmpty()) {
            return ExitSetting.Result.INVALID;
        }
        if (!linked) {
            return ExitSetting.Result.NOT_LINKED;
        }
        if (!drone.found()) {
            return ExitSetting.Result.NOT_FOUND;
        }
        return UploadCheck.owner(operator, drone) ? ExitSetting.Result.OK : ExitSetting.Result.NOT_OWNER;
    }
}
