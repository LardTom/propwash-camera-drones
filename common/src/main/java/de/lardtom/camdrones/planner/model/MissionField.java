package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;

import java.util.Locale;

public enum MissionField {
    BASE_SPEED,
    CURVES,
    END,
    OBSTACLE;

    public static final int SPEED_STEP_DM_S = 5;
    public static final int SPEED_BIG_STEP_DM_S = 20;

    public enum Ending {
        HOVER,
        START,
        LAND,
        LOOP;

        public static Ending of(Mission mission) {
            if (mission.loop()) {
                return LOOP;
            }
            return switch (mission.end()) {
                case HOVER -> HOVER;
                case START -> START;
                case LAND -> LAND;
            };
        }

        public Mission apply(Mission mission) {
            return switch (this) {
                case HOVER -> mission.withLoop(false).withEnd(Mission.End.HOVER);
                case START -> mission.withLoop(false).withEnd(Mission.End.START);
                case LAND -> mission.withLoop(false).withEnd(Mission.End.LAND);
                case LOOP -> mission.withLoop(true).withEnd(Mission.End.HOVER);
            };
        }
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String labelKey() {
        return FieldText.PREFIX + "mission." + this.key();
    }

    public String tooltipKey() {
        return FieldText.PREFIX + "mission." + this.key() + ".tooltip";
    }

    public Mission step(Mission mission, int steps, boolean big) {
        if (steps == 0) {
            return mission;
        }
        return switch (this) {
            case BASE_SPEED -> {
                int speed = Math.clamp(mission.baseSpeedDmS() + steps * (big ? SPEED_BIG_STEP_DM_S : SPEED_STEP_DM_S),
                        SPEED_STEP_DM_S, MissionLimits.MAX_SPEED_DM_S);
                yield new Mission(mission.smooth(), mission.loop(), mission.end(), speed, mission.obstacle(), mission.waypoints(),
                        mission.pointsOfInterest());
            }
            case CURVES -> mission.withSmooth(!mission.smooth());
            case END -> {
                Ending[] values = Ending.values();
                yield values[Math.floorMod(Ending.of(mission).ordinal() + Integer.signum(steps), values.length)].apply(mission);
            }
            case OBSTACLE -> {
                ObstacleGuard.Mode[] values = ObstacleGuard.Mode.values();
                yield mission.withObstacle(values[Math.floorMod(mission.obstacle().ordinal() + Integer.signum(steps), values.length)]);
            }
        };
    }

    public FieldText value(Mission mission, char decimal) {
        return switch (this) {
            case BASE_SPEED -> FieldText.of("value.speed",
                    FieldText.number(mission.baseSpeed(), mission.baseSpeedDmS() % 10 == 0 ? 0 : 1, decimal));
            case CURVES -> FieldText.of(mission.smooth() ? "value.curves.smooth" : "value.curves.sharp");
            case END -> FieldText.of("value.end." + Ending.of(mission).name().toLowerCase(Locale.ROOT));
            case OBSTACLE -> FieldText.of("value.obstacle." + mission.obstacle().name().toLowerCase(Locale.ROOT));
        };
    }
}
