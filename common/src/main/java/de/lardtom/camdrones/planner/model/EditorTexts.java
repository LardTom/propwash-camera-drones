package de.lardtom.camdrones.planner.model;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public record EditorTexts(String title, String live, String mapShow, String mapHide, String missionAdd, String missionCopy, String missionDelete,
                          String counterSample, String listAddWaypoint, String listAddPoi, List<String> fieldLabels,
                          List<String> fieldValues, List<String> settingLabels, List<String> settingValues, List<String> detailTitles,
                          String previewSample, List<FooterLabel> footer, List<String> templates, List<String> autoShots,
                          List<String> followStyles, List<String> pageLabels) {
    public static final String CLOSE = "×";
    public static final String PREVIOUS = "‹";
    public static final String NEXT = "›";
    public static final String UP = "▲";
    public static final String DOWN = "▼";
    public static final String DELETE = "×";
    public static final String ZOOM_IN = "+";
    public static final String ZOOM_OUT = "−";
    public static final String RECENTER = "●";
    public static final String MIXED = "–";

    public enum Footer {
        SEND,
        START,
        PAUSE,
        STOP,
        AUTO,
        FOLLOW,
        TEMPLATE,
        SAVE
    }

    public record FooterLabel(String full, String compact) {
    }

    public EditorTexts {
        fieldLabels = List.copyOf(fieldLabels);
        fieldValues = List.copyOf(fieldValues);
        settingLabels = List.copyOf(settingLabels);
        settingValues = List.copyOf(settingValues);
        footer = List.copyOf(footer);
        templates = List.copyOf(templates);
        autoShots = List.copyOf(autoShots);
        followStyles = List.copyOf(followStyles);
        detailTitles = List.copyOf(detailTitles);
        pageLabels = List.copyOf(pageLabels);
        if (footer.size() != Footer.values().length) {
            throw new IllegalArgumentException("Fußleiste braucht " + Footer.values().length + " Beschriftungen");
        }
    }

    public static int widest(List<String> texts, ToIntFunction<String> measure) {
        int max = 0;
        for (String text : texts) {
            max = Math.max(max, measure.applyAsInt(text));
        }
        return max;
    }

    public FooterLabel footer(Footer which) {
        return this.footer.get(which.ordinal());
    }

    public static EditorTexts from(Function<String, String> translate, List<String> fieldLabelKeys, List<String> fieldValueTexts,
                                   List<String> settingLabelKeys, List<String> settingValueTexts, String previewSample,
                                   List<String> detailTitles) {
        String p = FieldText.PREFIX;
        return new EditorTexts(
                translate.apply(p + "title"),
                translate.apply(p + "live"),
                translate.apply(p + "map.show"),
                translate.apply(p + "map.hide"),
                translate.apply(p + "mission.add"),
                translate.apply(p + "mission.copy"),
                translate.apply(p + "mission.delete"),
                "8/8",
                translate.apply(p + "list.add_waypoint"),
                translate.apply(p + "list.add_poi"),
                fieldLabelKeys.stream().map(translate).toList(),
                fieldValueTexts,
                settingLabelKeys.stream().map(translate).toList(),
                settingValueTexts,
                detailTitles,
                previewSample,
                List.of(footer(translate, "send"), footer(translate, "start"), footer(translate, "pause"), footer(translate, "stop"),
                        footer(translate, "auto"), footer(translate, "follow"), footer(translate, "template"), footer(translate, "save")),
                List.of(translate.apply(MissionTemplates.Template.CIRCLE.labelKey()), translate.apply(MissionTemplates.Template.REVEAL.labelKey()),
                        translate.apply(MissionTemplates.Template.LINE.labelKey())),
                AutoShotMenu.SHOTS.stream().map(shot -> translate.apply(AutoShotMenu.labelKey(shot))).toList(),
                FollowMenu.STYLES.stream().map(style -> translate.apply(FollowMenu.labelKey(style))).toList(),
                List.of(translate.apply(EditorLayout.TASK_PAGE_KEY), translate.apply(EditorLayout.FLIGHT_PAGE_KEY)));
    }

    private static FooterLabel footer(Function<String, String> translate, String key) {
        return new FooterLabel(translate.apply(FieldText.PREFIX + "button." + key), translate.apply(FieldText.PREFIX + "button." + key + ".short"));
    }
}
