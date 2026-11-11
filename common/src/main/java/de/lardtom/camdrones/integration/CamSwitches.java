package de.lardtom.camdrones.integration;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class CamSwitches {
    private static final Map<CamControl, AddonSwitch> SWITCHES = new EnumMap<>(CamControl.class);
    private static final Map<CamControl, AddonAxis> AXES = new EnumMap<>(CamControl.class);

    private CamSwitches() {
    }

    public static SwitchSpec switchSpec(CamControl control) {
        if (control.axis()) {
            throw new IllegalArgumentException(control + " ist eine Achse");
        }
        return SwitchSpec.builder(control.id())
                .toggle(control.toggle())
                .latchesOnGamepad(control.toggle())
                .serverSync(control.serverSync())
                .nameKey(control.nameKey())
                .descriptionKey(control.descriptionKey())
                .build();
    }

    public static AxisSpec axisSpec(CamControl control) {
        if (!control.axis()) {
            throw new IllegalArgumentException(control + " ist keine Achse");
        }
        return AxisSpec.builder(control.id())
                .center(control.center())
                .keyRateDps(control.keyRateDps())
                .serverSync(control.serverSync())
                .nameKey(control.nameKey())
                .build();
    }

    public record Handles(Map<CamControl, AddonSwitch> switches, Map<CamControl, AddonAxis> axes) {
        public Handles {
            switches = Collections.unmodifiableMap(new EnumMap<>(switches));
            axes = Collections.unmodifiableMap(new EnumMap<>(axes));
        }
    }

    public static Handles create(SwitchRegistry registry) {
        Map<CamControl, AddonSwitch> switches = new EnumMap<>(CamControl.class);
        Map<CamControl, AddonAxis> axes = new EnumMap<>(CamControl.class);
        for (CamControl control : CamControl.values()) {
            if (control.axis()) {
                axes.put(control, registry.registerAxis(axisSpec(control)));
            } else {
                switches.put(control, registry.register(switchSpec(control)));
            }
        }
        return new Handles(switches, axes);
    }

    public static synchronized void register(SwitchRegistry registry) {
        if (!SWITCHES.isEmpty() || !AXES.isEmpty()) {
            return;
        }
        Handles handles = create(registry);
        SWITCHES.putAll(handles.switches());
        AXES.putAll(handles.axes());
    }

    public static void init() {
        register(PropwashApi.switches());
    }

    public static synchronized Map<CamControl, AddonSwitch> switches() {
        return Collections.unmodifiableMap(new EnumMap<>(SWITCHES));
    }

    public static synchronized Map<CamControl, AddonAxis> axes() {
        return Collections.unmodifiableMap(new EnumMap<>(AXES));
    }

    public static synchronized Optional<AddonSwitch> handle(CamControl control) {
        return Optional.ofNullable(SWITCHES.get(control));
    }

    public static synchronized Optional<AddonAxis> axisHandle(CamControl control) {
        return Optional.ofNullable(AXES.get(control));
    }

    public static boolean active(Entity drone, CamControl control) {
        return handle(control).map(handle -> handle.active(drone)).orElse(false);
    }

    public static double value(Entity drone, CamControl control) {
        return axisHandle(control).map(handle -> handle.value(drone)).orElse(control.center());
    }
}
