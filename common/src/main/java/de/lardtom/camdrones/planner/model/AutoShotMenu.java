package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.nav.AutoShots;

import java.util.List;
import java.util.Locale;

public final class AutoShotMenu {
    public static final List<AutoShots.Shot> SHOTS = List.of(AutoShots.Shot.values());

    private AutoShotMenu() {
    }

    public static String labelKey(AutoShots.Shot shot) {
        return FieldText.PREFIX + "auto." + shot.name().toLowerCase(Locale.ROOT);
    }

    public static String tooltipKey(AutoShots.Shot shot) {
        return labelKey(shot) + ".tooltip";
    }

    public static List<String> tooltipArgs(AutoShots.Shot shot) {
        return List.of(whole(AutoShots.duration(shot)), whole(AutoShots.SUBJECT_DISTANCE), whole(AutoShots.SPIRAL_WIDEN));
    }

    private static String whole(double value) {
        return Long.toString(Math.round(value));
    }
}
