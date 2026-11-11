package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GimbalTable {
    public static final Identifier COMPACT = CamDrones.id("gimbal_compact");
    public static final Identifier TOUR = CamDrones.id("gimbal_tour");
    public static final Identifier CINE = CamDrones.id("gimbal_cine");
    public static final int DEFAULT_FOCAL_MM = 24;

    private static final Map<Identifier, Spec> SPECS = Map.of(
            COMPACT, new Spec(-90.0, 30.0, false, false, List.of(DEFAULT_FOCAL_MM)),
            TOUR, new Spec(-90.0, 30.0, false, false, List.of(DEFAULT_FOCAL_MM, 70)),
            CINE, new Spec(-90.0, 60.0, true, true, List.of()));

    private GimbalTable() {
    }

    public static boolean isGimbal(Identifier accessory) {
        return SPECS.containsKey(accessory);
    }

    public static Optional<Spec> spec(Identifier accessory) {
        return Optional.ofNullable(SPECS.get(accessory));
    }

    public record Spec(double minTiltDeg, double maxTiltDeg, boolean fullPan, boolean lensBay, List<Integer> builtInFocalMm) {
        public Spec {
            builtInFocalMm = List.copyOf(builtInFocalMm);
        }
    }
}
