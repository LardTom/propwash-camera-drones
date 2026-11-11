package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum WaypointField {
    HEIGHT,
    SPEED,
    WAIT,
    TILT,
    HEADING,
    YAW,
    ACTION,
    LIGHT,
    ACCESSORY,
    REPEAT,
    EVERY;

    public static final int HEIGHT_STEP_DM = 10;
    public static final int HEIGHT_BIG_STEP_DM = 50;
    public static final int SPEED_STEP_DM_S = 5;
    public static final int SPEED_BIG_STEP_DM_S = 20;
    public static final int WAIT_STEP_DS = 10;
    public static final int WAIT_BIG_STEP_DS = 100;
    public static final int TILT_STEP = 5;
    public static final int TILT_BIG_STEP = 15;
    public static final int YAW_STEP = 15;
    public static final int YAW_BIG_STEP = 45;
    public static final int EVERY_STEP_TICKS = 1;
    public static final int EVERY_BIG_STEP_TICKS = MissionLimits.TICKS_PER_SECOND;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String labelKey() {
        return FieldText.PREFIX + "field." + this.key();
    }

    public String tooltipKey() {
        return FieldText.PREFIX + "field." + this.key() + ".tooltip";
    }

    public boolean enabled(Waypoint waypoint) {
        return switch (this) {
            case YAW -> waypoint.heading() == Waypoint.Heading.FIXED;
            case REPEAT -> waypoint.accessory().number() > 0;
            case EVERY -> waypoint.repeatMode() == Waypoint.Repeat.EVERY;
            default -> true;
        };
    }

    public boolean task() {
        return this == LIGHT || this == ACCESSORY || this == REPEAT || this == EVERY;
    }

    public Waypoint step(Waypoint w, int steps, boolean big, int pointsOfInterest, FlightClass flightClass) {
        if (steps == 0 || !this.enabled(w)) {
            return w;
        }
        return switch (this) {
            case HEIGHT -> w.withHeight(Math.clamp((long) w.yDm() + (long) steps * (big ? HEIGHT_BIG_STEP_DM : HEIGHT_STEP_DM),
                    MissionLimits.MIN_Y_DM, MissionLimits.MAX_Y_DM));
            case SPEED -> {
                int speed = Math.clamp(w.speedDmS() + steps * (big ? SPEED_BIG_STEP_DM_S : SPEED_STEP_DM_S), 0, MissionLimits.MAX_SPEED_DM_S);
                if (steps > 0 && w.speedDmS() == 0) {
                    speed = big ? SPEED_BIG_STEP_DM_S : SPEED_STEP_DM_S;
                }
                yield w.withSpeedDm(speed);
            }
            case WAIT -> w.withWaitDs(Math.clamp(w.waitDs() + steps * (big ? WAIT_BIG_STEP_DS : WAIT_STEP_DS), 0, MissionLimits.MAX_WAIT_DS));
            case TILT -> w.withTilt(WaypointPlacement.clampTilt(w.tiltDeg() + steps * (big ? TILT_BIG_STEP : TILT_STEP), flightClass));
            case HEADING -> {
                List<HeadingChoice> choices = headingChoices(pointsOfInterest);
                int current = Math.max(0, choices.indexOf(HeadingChoice.of(w)));
                HeadingChoice next = choices.get(Math.floorMod(current + Integer.signum(steps), choices.size()));
                yield w.withLook(next.heading(), next.poi());
            }
            case YAW -> w.withYaw(w.yawDeg() + steps * (big ? YAW_BIG_STEP : YAW_STEP));
            case ACTION -> w.withAction(cycle(Waypoint.Action.values(), w.action(), steps));
            case LIGHT -> w.withLight(cycle(Waypoint.Light.values(), w.light(), steps));
            case ACCESSORY -> {
                Waypoint.Accessory next = cycle(Waypoint.Accessory.values(), w.accessory(), steps);
                yield w.withAccessory(next, w.repeatMode(), w.everyTicks());
            }
            case REPEAT -> w.withRepeat(cycle(Waypoint.Repeat.values(), w.repeatMode(), steps));
            case EVERY -> w.withEveryTicks(w.everyTicks() + steps * (big ? EVERY_BIG_STEP_TICKS : EVERY_STEP_TICKS));
        };
    }

    public Waypoint copy(Waypoint source, Waypoint target) {
        if (!this.enabled(target)) {
            return target;
        }
        return switch (this) {
            case HEIGHT -> target.withHeight(source.yDm());
            case SPEED -> target.withSpeedDm(source.speedDmS());
            case WAIT -> target.withWaitDs(source.waitDs());
            case TILT -> target.withTilt(source.tiltDeg());
            case HEADING -> target.withLook(source.heading(), source.poi());
            case YAW -> target.withYaw(source.yawDeg());
            case ACTION -> target.withAction(source.action());
            case LIGHT -> target.withLight(source.light());
            case ACCESSORY -> target.withAccessory(source.accessory(), target.repeatMode(), target.everyTicks());
            case REPEAT -> target.withAccessory(target.accessory(), source.repeatMode(), source.everyTicks());
            case EVERY -> target.withEveryTicks(source.everyTicks());
        };
    }

    public boolean typeable() {
        return switch (this) {
            case HEIGHT, SPEED, WAIT, TILT, YAW, EVERY -> true;
            default -> false;
        };
    }

    public ValueEntry.Range range(double startY, FlightClass flightClass) {
        return switch (this) {
            case HEIGHT -> heightRange(startY);
            case SPEED -> ValueEntry.Range.of(0.0, MissionLimits.MAX_SPEED_DM_S / 10.0, 1);
            case WAIT -> ValueEntry.Range.of(0.0, MissionLimits.MAX_WAIT_DS / 10.0, 1);
            case TILT -> ValueEntry.Range.of(MissionLimits.MIN_TILT, WaypointPlacement.clampTilt(MissionLimits.MAX_TILT, flightClass), 0);
            case YAW -> ValueEntry.Range.wrapping(0, 360);
            case EVERY -> ValueEntry.Range.of(MissionLimits.MIN_EVERY_TICKS, MissionLimits.MAX_EVERY_TICKS, 0);
            default -> throw new IllegalStateException("Feld ohne Texteingabe: " + this);
        };
    }

    public static ValueEntry.Range heightRange(double startY) {
        double start = MissionLimits.toDecimetres(startY) / 10.0;
        return ValueEntry.Range.of(MissionLimits.MIN_Y_DM / 10.0 - start, MissionLimits.MAX_Y_DM / 10.0 - start, 1);
    }

    public static int heightDm(double aboveStart, double startY) {
        long dm = (long) MissionLimits.toDecimetres(startY) + Math.round(aboveStart * 10.0);
        return (int) Math.clamp(dm, MissionLimits.MIN_Y_DM, MissionLimits.MAX_Y_DM);
    }

    public double shown(Waypoint w, double startY) {
        return switch (this) {
            case HEIGHT -> (w.yDm() - MissionLimits.toDecimetres(startY)) / 10.0;
            case SPEED -> w.speed();
            case WAIT -> w.waitSeconds();
            case TILT -> w.tiltDeg();
            case YAW -> w.yawDeg();
            case EVERY -> w.everyTicks();
            default -> throw new IllegalStateException("Feld ohne Texteingabe: " + this);
        };
    }

    public Waypoint typed(Waypoint w, double value, double startY, FlightClass flightClass) {
        if (!this.typeable() || !this.enabled(w) || !Double.isFinite(value)) {
            return w;
        }
        return switch (this) {
            case HEIGHT -> w.withHeight(heightDm(value, startY));
            case SPEED -> {
                long dm = Math.round(value * 10.0);
                yield w.withSpeedDm(dm <= 0 ? 0 : (int) Math.clamp(dm, MissionLimits.MIN_SPEED_DM_S, MissionLimits.MAX_SPEED_DM_S));
            }
            case WAIT -> w.withWaitDs((int) Math.clamp(Math.round(value * 10.0), 0, MissionLimits.MAX_WAIT_DS));
            case TILT -> w.withTilt(WaypointPlacement.clampTilt((int) Math.clamp(Math.round(value), Integer.MIN_VALUE, Integer.MAX_VALUE),
                    flightClass));
            case YAW -> w.withYaw((int) Math.floorMod(Math.round(value), 360L));
            case EVERY -> w.withEveryTicks((int) Math.clamp(Math.round(value), MissionLimits.MIN_EVERY_TICKS, MissionLimits.MAX_EVERY_TICKS));
            default -> w;
        };
    }

    private static <E extends Enum<E>> E cycle(E[] values, E current, int steps) {
        return values[Math.floorMod(current.ordinal() + Integer.signum(steps), values.length)];
    }

    public FieldText value(Waypoint w, char decimal, double startY) {
        return switch (this) {
            case HEIGHT -> height(w.y() - startY, decimal);
            case SPEED -> w.speedDmS() == 0 ? FieldText.of("value.speed_base")
                    : FieldText.of("value.speed", FieldText.number(w.speed(), w.speedDmS() % 10 == 0 ? 0 : 1, decimal));
            case WAIT -> w.waitDs() == 0 ? FieldText.of("value.wait_none")
                    : FieldText.of("value.wait", FieldText.number(w.waitSeconds(), w.waitDs() % 10 == 0 ? 0 : 1, decimal));
            case TILT -> FieldText.of("value.degrees", FieldText.signed(w.tiltDeg()));
            case HEADING -> switch (w.heading()) {
                case COURSE -> FieldText.of("value.heading.course");
                case FIXED -> FieldText.of("value.heading.fixed");
                case POI -> FieldText.of("value.heading.poi", Integer.toString(w.poi() + 1));
                case FREE -> FieldText.of("value.heading.free");
            };
            case YAW -> w.heading() == Waypoint.Heading.FIXED ? FieldText.of("value.yaw", Integer.toString(w.yawDeg()))
                    : FieldText.of("value.none");
            case ACTION -> FieldText.of("value.action." + w.action().name().toLowerCase(Locale.ROOT));
            case LIGHT -> FieldText.of("value.light." + w.light().name().toLowerCase(Locale.ROOT));
            case ACCESSORY -> w.accessory().number() > 0 ? FieldText.of("value.accessory.action", Integer.toString(w.accessory().number()))
                    : FieldText.of("value.accessory." + w.accessory().name().toLowerCase(Locale.ROOT));
            case REPEAT -> w.accessory().number() == 0 ? FieldText.of("value.none") : switch (w.repeatMode()) {
                case ONCE -> FieldText.of("value.repeat.off");
                case EACH -> FieldText.of("value.repeat.on");
                case EVERY -> FieldText.of("value.repeat.every", seconds(w.everyTicks(), decimal));
            };
            case EVERY -> w.repeatMode() == Waypoint.Repeat.EVERY ? every(w.everyTicks()) : FieldText.of("value.none");
        };
    }

    public static FieldText every(int ticks) {
        return ticks == 1 ? FieldText.of("value.every.one") : FieldText.of("value.every", Integer.toString(ticks));
    }

    public static String seconds(int ticks, char decimal) {
        int decimals = ticks % MissionLimits.TICKS_PER_SECOND == 0 ? 0 : ticks % 2 == 0 ? 1 : 2;
        return FieldText.number(ticks / (double) MissionLimits.TICKS_PER_SECOND, decimals, decimal);
    }

    public static FieldText height(double aboveStart, char decimal) {
        double rounded = Math.round(aboveStart * 10.0) / 10.0;
        return FieldText.of("value.height", FieldText.number(rounded, Math.round(rounded * 10.0) % 10 == 0 ? 0 : 1, decimal));
    }

    public static List<HeadingChoice> headingChoices(int pointsOfInterest) {
        List<HeadingChoice> list = new ArrayList<>();
        list.add(new HeadingChoice(Waypoint.Heading.COURSE, 0));
        list.add(new HeadingChoice(Waypoint.Heading.FIXED, 0));
        for (int i = 0; i < Math.clamp(pointsOfInterest, 0, MissionLimits.MAX_POINTS_OF_INTEREST); i++) {
            list.add(new HeadingChoice(Waypoint.Heading.POI, i));
        }
        list.add(new HeadingChoice(Waypoint.Heading.FREE, 0));
        return list;
    }

    public record HeadingChoice(Waypoint.Heading heading, int poi) {
        static HeadingChoice of(Waypoint w) {
            return new HeadingChoice(w.heading(), w.heading() == Waypoint.Heading.POI ? w.poi() : 0);
        }
    }
}
