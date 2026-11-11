package de.lardtom.camdrones.planner.model;

import java.util.Locale;
import java.util.Optional;

public enum ExitSetting {
    HOVER,
    HOME,
    LAND;

    public static final String LABEL_KEY = FieldText.PREFIX + "mission.exit";
    public static final String TOOLTIP_KEY = FieldText.PREFIX + "mission.exit.tooltip";
    public static final String UNKNOWN_KEY = "value.none";
    public static final double RATE_PER_SECOND = 4.0;
    public static final double RATE_BURST = 8.0;

    public enum Result {
        OK,
        RATE_LIMITED,
        INVALID,
        NOT_LINKED,
        NOT_FOUND,
        NOT_OWNER,
        UNAVAILABLE;

        public boolean ok() {
            return this == OK;
        }

        public String messageKey() {
            return FieldText.PREFIX + "exit.result." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public FieldText value() {
        return FieldText.of("value.exit." + this.key());
    }

    public static FieldText value(Optional<ExitSetting> setting) {
        return setting.map(ExitSetting::value).orElseGet(() -> FieldText.of(UNKNOWN_KEY));
    }

    public ExitSetting step(int direction) {
        ExitSetting[] values = values();
        return values[Math.floorMod(this.ordinal() + Integer.signum(direction), values.length)];
    }

    public static Optional<ExitSetting> byId(int id) {
        ExitSetting[] values = values();
        return id >= 0 && id < values.length ? Optional.of(values[id]) : Optional.empty();
    }

    public static Result check(boolean withinRate, int id, boolean linked, UploadCheck.Drone drone, boolean operator) {
        if (!withinRate) {
            return Result.RATE_LIMITED;
        }
        if (byId(id).isEmpty()) {
            return Result.INVALID;
        }
        if (!linked) {
            return Result.NOT_LINKED;
        }
        if (!drone.found()) {
            return Result.NOT_FOUND;
        }
        return UploadCheck.owner(operator, drone) ? Result.OK : Result.NOT_OWNER;
    }
}
