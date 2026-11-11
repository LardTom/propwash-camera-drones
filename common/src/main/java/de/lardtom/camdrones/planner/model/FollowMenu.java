package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.nav.FollowController;

import java.util.List;
import java.util.Locale;

public final class FollowMenu {
    public static final List<FollowController.Style> STYLES = List.of(FollowController.Style.values());

    private FollowMenu() {
    }

    public static String labelKey(FollowController.Style style) {
        return FieldText.PREFIX + "follow." + style.name().toLowerCase(Locale.ROOT);
    }

    public static String tooltipKey(FollowController.Style style) {
        return labelKey(style) + ".tooltip";
    }

    public static List<String> tooltipArgs() {
        return List.of(whole(FollowController.DISTANCE), whole(FollowController.HEIGHT));
    }

    private static String whole(double value) {
        return Long.toString(Math.round(value));
    }
}
