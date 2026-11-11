package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.flight.SensorDirection;
import net.minecraft.resources.Identifier;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SensorTable {
    public static final double RANGE_METRES = 15.0;
    public static final Identifier NAV_FC_COMPACT = CamDrones.id("nav_fc_compact");
    public static final Identifier NAV_FC_PRO = CamDrones.id("nav_fc_pro");

    private static final Map<Identifier, Set<SensorDirection>> SENSORS = Map.of(
            NAV_FC_COMPACT, Set.copyOf(EnumSet.of(SensorDirection.DOWN)),
            NAV_FC_PRO, Set.copyOf(EnumSet.allOf(SensorDirection.class)));

    private SensorTable() {
    }

    public static boolean isNavBoard(Identifier stack) {
        return SENSORS.containsKey(stack);
    }

    public static Set<Identifier> navBoards() {
        return SENSORS.keySet();
    }

    public static Optional<Set<SensorDirection>> sensors(Identifier stack) {
        return Optional.ofNullable(SENSORS.get(stack));
    }

    public static boolean senses(Identifier stack, SensorDirection direction) {
        return sensors(stack).map(set -> set.contains(direction)).orElse(false);
    }

    public static boolean canClimbOver(Identifier stack) {
        return senses(stack, SensorDirection.FORWARD) && senses(stack, SensorDirection.UP);
    }
}
