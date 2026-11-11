package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyResult;

import java.util.Locale;
import java.util.Optional;

public enum AutonomyStatus {
    MISSION_STARTED(Tone.START),
    MISSION_RESUMED(Tone.START),
    MISSION_PAUSED(Tone.NONE),
    MISSION_DONE(Tone.DONE),
    MISSION_STOPPED(Tone.NONE),
    HOLD_STARTED(Tone.START),
    FOLLOW_STARTED(Tone.START),
    POI_STARTED(Tone.START),
    LAND_STARTED(Tone.NONE),
    RELAYED(Tone.NONE),
    NO_BUDGET(Tone.ERROR),
    PILOTED(Tone.ERROR),
    NOT_READY(Tone.ERROR),
    DENIED(Tone.ERROR),
    NOT_ARMABLE(Tone.ERROR),
    DISABLED(Tone.ERROR),
    BUSY(Tone.ERROR),
    NO_MISSION(Tone.ERROR),
    NOT_OWNER(Tone.ERROR),
    NOT_FOUND(Tone.ERROR),
    AMBIGUOUS(Tone.ERROR),
    TOO_FAST(Tone.ERROR),
    NOT_RUNNING(Tone.ERROR),
    NOT_CAMERA(Tone.ERROR),
    ENDED_PILOT(Tone.NONE),
    ENDED_LANDED(Tone.DONE),
    ENDED_BATTERY(Tone.ERROR),
    ENDED_OWNER_OFFLINE(Tone.ERROR),
    ENDED_OUT_OF_RANGE(Tone.ERROR),
    ENDED_STOPPED(Tone.NONE),
    ENDED_TIMEOUT(Tone.ERROR),
    ENDED_ERROR(Tone.ERROR),
    ENDED_REPLACED(Tone.NONE),
    FOLLOW_REQUESTED(Tone.NONE),
    FOLLOW_ACCEPTED(Tone.START),
    FOLLOW_DECLINED(Tone.ERROR),
    FOLLOW_EXPIRED(Tone.ERROR),
    FOLLOW_RULE_OFF(Tone.ERROR),
    FOLLOW_NOT_ALLOWED(Tone.ERROR),
    FOLLOW_TARGET_LOST(Tone.ERROR),
    FOLLOW_STOPPED_BY_TARGET(Tone.ERROR),
    FOLLOW_NO_REQUEST(Tone.ERROR),
    CONSENT_GIVEN(Tone.NONE),
    CONSENT_REFUSED(Tone.NONE),
    FOLLOW_STOPPED(Tone.NONE),
    AUTO_SHOT_STARTED(Tone.START),
    AUTO_SHOT_DONE(Tone.DONE),
    HOME_STARTED(Tone.NONE),
    ENDED_TERRAIN_LIMIT(Tone.ERROR);

    public enum Tone {
        NONE,
        START,
        DONE,
        ERROR
    }

    public enum Reason {
        GPS("gps", NOT_READY),
        BATTERY_LOW("battery_low", NOT_READY),
        NOT_ON_GROUND("not_on_ground", NOT_READY),
        NO_TAKEOFF("no_takeoff", NOT_READY),
        NO_BATTERY_IN_AIR("no_battery", NOT_READY),
        REMOVED("removed", NOT_READY),
        NO_BATTERY("no_battery", NOT_ARMABLE),
        BATTERY_EMPTY("battery_empty", NOT_ARMABLE),
        FPV_PILOTED("fpv", PILOTED);

        private final String code;
        private final AutonomyStatus status;

        Reason(String code, AutonomyStatus status) {
            this.code = code;
            this.status = status;
        }

        public String code() {
            return this.code;
        }

        public AutonomyStatus status() {
            return this.status;
        }

        public String key() {
            return this.status.key() + "." + this.code;
        }

        public static Optional<Reason> notReady(AutonomyNotReady why) {
            if (why == null) {
                return Optional.empty();
            }
            return switch (why) {
                case NOT_ON_GROUND -> Optional.of(NOT_ON_GROUND);
                case NO_TAKEOFF -> Optional.of(NO_TAKEOFF);
                case NO_BATTERY -> Optional.of(NO_BATTERY_IN_AIR);
                case REMOVED -> Optional.of(REMOVED);
                default -> Optional.empty();
            };
        }

        public static Optional<Reason> of(AutonomyStatus status, String code) {
            if (status == null || code == null || code.isEmpty()) {
                return Optional.empty();
            }
            for (Reason reason : values()) {
                if (reason.status == status && reason.code.equals(code)) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        }
    }

    public static final String PREFIX = CamDrones.MOD_ID + ".autonomy.status.";

    private static final AutonomyStatus[] VALUES = values();

    private final Tone tone;
    private final String key;

    AutonomyStatus(Tone tone) {
        this.tone = tone;
        this.key = PREFIX + this.name().toLowerCase(Locale.ROOT);
    }

    public Tone tone() {
        return this.tone;
    }

    public boolean error() {
        return this.tone == Tone.ERROR;
    }

    public String key() {
        return this.key;
    }

    public boolean chat() {
        return this.error() || this.tone == Tone.DONE || this.name().startsWith("ENDED_") || this.name().startsWith("FOLLOW_")
                || this.name().startsWith("CONSENT_");
    }

    public static boolean overlay(AutonomyStatus status, String detail) {
        return status != NO_BUDGET && (status != NOT_ARMABLE || Reason.of(status, detail).isPresent());
    }

    public static AutonomyStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DENIED;
    }

    public static AutonomyStatus of(AutonomyResult result, AutonomyPlan.Kind kind) {
        return switch (result) {
            case STARTED -> started(kind);
            case NO_BUDGET -> NO_BUDGET;
            case PILOTED -> PILOTED;
            case NOT_READY -> NOT_READY;
            case DENIED -> DENIED;
            case NOT_ARMABLE -> NOT_ARMABLE;
        };
    }

    public static AutonomyStatus started(AutonomyPlan.Kind kind) {
        return switch (kind) {
            case MISSION -> MISSION_STARTED;
            case HOLD -> HOLD_STARTED;
            case FOLLOW -> FOLLOW_STARTED;
            case ORBIT -> POI_STARTED;
            case LAND -> LAND_STARTED;
            case AUTO_SHOT -> AUTO_SHOT_STARTED;
            case HOME -> HOME_STARTED;
        };
    }

    public static AutonomyStatus ended(AutonomyEnd reason) {
        return switch (reason) {
            case PILOT -> ENDED_PILOT;
            case LANDED -> ENDED_LANDED;
            case BATTERY -> ENDED_BATTERY;
            case OWNER_OFFLINE -> ENDED_OWNER_OFFLINE;
            case OUT_OF_RANGE -> ENDED_OUT_OF_RANGE;
            case STOPPED -> ENDED_STOPPED;
            case TIMEOUT -> ENDED_TIMEOUT;
            case ERROR -> ENDED_ERROR;
            case REPLACED -> ENDED_REPLACED;
            default -> endedByName(reason.name());
        };
    }

    static AutonomyStatus endedByName(String reason) {
        return switch (reason) {
            case "TERRAIN_LIMIT" -> ENDED_TERRAIN_LIMIT;
            default -> ENDED_STOPPED;
        };
    }
}
