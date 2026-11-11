package de.lardtom.camdrones.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CamKeys {
    public static final Identifier CATEGORY = CamDrones.id("camdrones");

    private static List<KeyMapping> created;
    private static final Map<CamControl, KeyMapping> PRESS = new EnumMap<>(CamControl.class);
    private static final Map<CamControl, KeyMapping> INCREASE = new EnumMap<>(CamControl.class);
    private static final Map<CamControl, KeyMapping> DECREASE = new EnumMap<>(CamControl.class);

    private CamKeys() {
    }

    public static synchronized List<KeyMapping> create(KeyMapping.Category category) {
        if (created != null) {
            return created;
        }
        List<KeyMapping> mappings = new ArrayList<>();
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            KeyMapping mapping = new KeyMapping(entry.translationKey(), InputConstants.Type.KEYBOARD, entry.defaultKey(), category);
            switch (entry.role()) {
                case PRESS -> PRESS.put(entry.control(), mapping);
                case INCREASE -> INCREASE.put(entry.control(), mapping);
                case DECREASE -> DECREASE.put(entry.control(), mapping);
            }
            mappings.add(mapping);
        }
        for (Map.Entry<CamControl, KeyMapping> entry : PRESS.entrySet()) {
            PropwashClientApi.bindSwitchKey(entry.getKey().id(), entry.getValue());
        }
        for (Map.Entry<CamControl, KeyMapping> entry : INCREASE.entrySet()) {
            PropwashClientApi.bindAxisKeys(entry.getKey().id(), entry.getValue(), DECREASE.get(entry.getKey()));
        }
        created = List.copyOf(mappings);
        CamDrones.LOG.info("{}: {} Tasten in der Kategorie {} angemeldet", CamDrones.MOD_NAME, created.size(), CATEGORY);
        return created;
    }

    public static synchronized List<KeyMapping> mappings() {
        return created == null ? List.of() : created;
    }

    public static synchronized Optional<KeyMapping> key(CamControl control) {
        return Optional.ofNullable(PRESS.get(control));
    }

    public static synchronized Optional<KeyMapping> increase(CamControl control) {
        return Optional.ofNullable(INCREASE.get(control));
    }

    public static synchronized Optional<KeyMapping> decrease(CamControl control) {
        return Optional.ofNullable(DECREASE.get(control));
    }
}
