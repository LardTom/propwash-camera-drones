package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class DroneNames {
    public static final String PROPWASH = "propwash";
    public static final String OWN_SHORT = CamDrones.MOD_ID + ".frame_short.";
    public static final String PROPWASH_SHORT = PROPWASH + ".content.frame_short.";
    private static final Set<String> OWN = Set.of(CamDrones.MOD_ID, PROPWASH);

    private DroneNames() {
    }

    public static String messageName(String custom, Optional<Identifier> frame, UUID serial, Predicate<String> known) {
        if (custom != null && !custom.isBlank()) {
            return custom.strip();
        }
        return frame.flatMap(id -> frameKey(id, known)).orElseGet(() -> DroneSelector.shortSerial(serial));
    }

    public static Component component(String custom, Optional<Identifier> frame, UUID serial, Predicate<String> known) {
        String name = messageName(custom, frame, serial, known);
        boolean named = custom != null && !custom.isBlank();
        return !named && frame.flatMap(id -> frameKey(id, known)).isPresent() ? Component.translatable(name) : Component.literal(name);
    }

    public static Optional<String> frameKey(Identifier frame, Predicate<String> known) {
        String shortKey = PROPWASH.equals(frame.getNamespace()) ? PROPWASH_SHORT + frame.getPath()
                : frame.getNamespace() + ".frame_short." + frame.getPath();
        if (OWN.contains(frame.getNamespace()) || known.test(shortKey)) {
            return Optional.of(shortKey);
        }
        String partKey = "part." + frame.getNamespace() + "." + frame.getPath();
        return known.test(partKey) ? Optional.of(partKey) : Optional.empty();
    }

    public static Component text(String drone, Predicate<String> known) {
        String name = drone == null ? "" : drone;
        boolean key = name.startsWith(OWN_SHORT) || name.startsWith(PROPWASH_SHORT) || !name.isEmpty() && known.test(name);
        return key ? Component.translatable(name) : Component.literal(name);
    }
}
