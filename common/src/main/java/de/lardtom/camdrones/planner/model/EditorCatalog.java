package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EditorCatalog {
    public static final String POI_HEIGHT_KEY = FieldText.PREFIX + "field.poi_height";
    public static final String POI_RADIUS_KEY = FieldText.PREFIX + "field.poi_radius";
    public static final String GROUND_KEY = FieldText.PREFIX + "mission.ground";
    public static final String RECORD_KEY = FieldText.PREFIX + "mission.record";
    public static final String PREVIEW_KEY = "preview";
    public static final String WAYPOINT_TITLE_KEY = "detail.waypoint";
    public static final String POI_TITLE_KEY = "detail.poi";
    public static final String WAYPOINTS_TITLE_KEY = "detail.waypoints";

    public interface Translator {
        String text(String key, List<String> args);

        default String text(FieldText value) {
            return this.text(value.key(), value.args());
        }

        default String text(String key) {
            return this.text(key, List.of());
        }
    }

    private EditorCatalog() {
    }

    public static List<String> fieldLabelKeys() {
        List<String> keys = new ArrayList<>();
        for (WaypointField field : WaypointField.values()) {
            keys.add(field.labelKey());
        }
        keys.add(POI_HEIGHT_KEY);
        keys.add(POI_RADIUS_KEY);
        return keys;
    }

    public static List<String> settingLabelKeys() {
        List<String> keys = new ArrayList<>();
        for (MissionField field : MissionField.values()) {
            keys.add(field.labelKey());
        }
        keys.add(GROUND_KEY);
        keys.add(RECORD_KEY);
        keys.add(ExitSetting.LABEL_KEY);
        keys.add(ViewSetting.LABEL_KEY);
        return keys;
    }

    public static FieldText groundValue(int metres) {
        return FieldText.of("value.metres", Integer.toString(metres));
    }

    public static FieldText recordValue(boolean active, int recorded) {
        return active ? FieldText.of("value.record.on", Integer.toString(recorded)) : FieldText.of("value.record.off");
    }

    public static FieldText poiHeight(double aboveStart, char decimal) {
        return WaypointField.height(aboveStart, decimal);
    }

    public static FieldText poiRadius(double radius, char decimal) {
        return radius <= 0.0 ? FieldText.of("value.radius_none")
                : FieldText.of("value.metres", FieldText.number(radius, Math.round(radius * 10.0) % 10 == 0 ? 0 : 1, decimal));
    }

    public static FieldText preview(double length, double seconds, double charge, char decimal) {
        return FieldText.of(PREVIEW_KEY, FieldText.number(length, 0, decimal), FieldText.duration(seconds),
                FieldText.number(Math.max(0.0, charge) * 100.0, 0, decimal));
    }

    public static FieldText waypointTitle(int index, int count) {
        return FieldText.of(WAYPOINT_TITLE_KEY, Integer.toString(index + 1), Integer.toString(count));
    }

    public static FieldText waypointsTitle(int count) {
        return FieldText.of(WAYPOINTS_TITLE_KEY, Integer.toString(count));
    }

    public static FieldText poiTitle(int index) {
        return FieldText.of(POI_TITLE_KEY, Integer.toString(index + 1));
    }

    public static List<FieldText> fieldValueSamples(char decimal) {
        List<Waypoint> samples = new ArrayList<>();
        Waypoint base = new Waypoint(0, MissionLimits.MIN_Y_DM + 5, 0, 0, 0, MissionLimits.MIN_TILT, Waypoint.Heading.COURSE, 0,
                Waypoint.Action.NONE, 0);
        samples.add(base);
        samples.add(new Waypoint(0, MissionLimits.MAX_Y_DM - 5, 0, MissionLimits.MAX_SPEED_DM_S - 5, MissionLimits.MAX_WAIT_DS - 5,
                MissionLimits.MAX_TILT, Waypoint.Heading.FIXED, 0, Waypoint.Action.RECORD_START, 345));
        samples.add(new Waypoint(0, 0, 0, MissionLimits.MAX_SPEED_DM_S, MissionLimits.MAX_WAIT_DS, -45, Waypoint.Heading.POI,
                MissionLimits.MAX_POINTS_OF_INTEREST - 1, Waypoint.Action.RECORD_STOP, 0));
        samples.add(new Waypoint(0, 0, 0, 5, 5, 0, Waypoint.Heading.FREE, 0, Waypoint.Action.PHOTO, 0));
        for (Waypoint.Light light : Waypoint.Light.values()) {
            samples.add(base.withLight(light));
        }
        for (Waypoint.Accessory accessory : Waypoint.Accessory.values()) {
            samples.add(base.withAccessory(accessory, false));
            samples.add(base.withAccessory(accessory, true));
            for (int ticks : new int[]{MissionLimits.MIN_EVERY_TICKS, MissionLimits.MAX_EVERY_TICKS - 1, MissionLimits.MAX_EVERY_TICKS}) {
                samples.add(base.withAccessory(accessory, Waypoint.Repeat.EVERY, ticks));
            }
        }
        List<FieldText> values = new ArrayList<>();
        for (Waypoint w : samples) {
            for (WaypointField field : WaypointField.values()) {
                values.add(field.value(w, decimal, 0.0));
            }
        }
        double span = (MissionLimits.MAX_Y_DM - MissionLimits.MIN_Y_DM) / 10.0;
        values.add(WaypointField.height(-span + 0.5, decimal));
        values.add(WaypointField.height(span - 0.5, decimal));
        values.add(poiHeight(-span + 0.5, decimal));
        values.add(poiRadius(MissionLimits.MAX_RADIUS_DM / 10.0 - 0.5, decimal));
        values.add(poiRadius(0.0, decimal));
        values.add(repeatedAccessory(Waypoint.Accessory.ACTIONS, MissionLimits.MAX_WAYPOINTS - 1));
        values.add(timedAccessory(Waypoint.Accessory.ACTIONS));
        return values;
    }

    public static FieldText timedAccessory(int number) {
        return FieldText.of("value.accessory.timed", Integer.toString(number));
    }

    public static FieldText repeatedAccessory(int number, int sourceIndex) {
        return FieldText.of("value.accessory.repeated", Integer.toString(number), Integer.toString(sourceIndex + 1));
    }

    public static List<FieldText> settingValueSamples(char decimal) {
        List<FieldText> values = new ArrayList<>();
        Mission mission = Mission.empty();
        for (int speed : new int[]{MissionLimits.MAX_SPEED_DM_S - 5, MissionLimits.MAX_SPEED_DM_S, MissionField.SPEED_STEP_DM_S}) {
            Mission m = new Mission(false, false, Mission.End.HOVER, speed, ObstacleGuard.Mode.STOP, List.of(), List.of());
            values.add(MissionField.BASE_SPEED.value(m, decimal));
        }
        values.add(MissionField.CURVES.value(mission, decimal));
        values.add(MissionField.CURVES.value(mission.withSmooth(true), decimal));
        for (MissionField.Ending ending : MissionField.Ending.values()) {
            values.add(MissionField.END.value(ending.apply(mission), decimal));
        }
        for (ObstacleGuard.Mode mode : ObstacleGuard.Mode.values()) {
            values.add(MissionField.OBSTACLE.value(mission.withObstacle(mode), decimal));
        }
        values.add(groundValue(TabletState.MAX_GROUND_HEIGHT));
        values.add(recordValue(false, 0));
        values.add(recordValue(true, MissionLimits.MAX_WAYPOINTS));
        for (ExitSetting setting : ExitSetting.values()) {
            values.add(setting.value());
        }
        values.add(ExitSetting.value(Optional.empty()));
        for (ViewSetting setting : ViewSetting.values()) {
            values.add(setting.value());
        }
        return values;
    }

    public static EditorTexts texts(Translator translator, char decimal) {
        List<String> fieldValues = fieldValueSamples(decimal).stream().map(translator::text).toList();
        List<String> settingValues = settingValueSamples(decimal).stream().map(translator::text).toList();
        String preview = translator.text(preview(99999.0, 3600.0 * 9 + 59 * 60 + 59, 9.99, decimal));
        List<String> titles = List.of(translator.text(waypointTitle(MissionLimits.MAX_WAYPOINTS - 1, MissionLimits.MAX_WAYPOINTS)),
                translator.text(poiTitle(MissionLimits.MAX_POINTS_OF_INTEREST - 1)), translator.text(FieldText.of("detail.none")),
                translator.text(waypointsTitle(MissionLimits.MAX_WAYPOINTS)));
        return EditorTexts.from(translator::text, fieldLabelKeys(), fieldValues, settingLabelKeys(), settingValues, preview, titles);
    }
}
