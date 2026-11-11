package de.lardtom.camdrones.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamControlsTest {
    private static final Map<String, CamControl.Kind> DESIGN = Map.ofEntries(
            Map.entry("mode_normal", CamControl.Kind.SWITCH),
            Map.entry("mode_cine", CamControl.Kind.SWITCH),
            Map.entry("mode_sport", CamControl.Kind.SWITCH),
            Map.entry("mode_cycle", CamControl.Kind.BUTTON),
            Map.entry("pause", CamControl.Kind.BUTTON),
            Map.entry("mission_start", CamControl.Kind.BUTTON),
            Map.entry("waypoint_add", CamControl.Kind.BUTTON),
            Map.entry("follow_me", CamControl.Kind.SWITCH),
            Map.entry("poi_here", CamControl.Kind.BUTTON),
            Map.entry("course_lock", CamControl.Kind.SWITCH),
            Map.entry("gimbal_lock", CamControl.Kind.SWITCH),
            Map.entry("gimbal_recenter", CamControl.Kind.BUTTON),
            Map.entry("gimbal_fpv", CamControl.Kind.SWITCH),
            Map.entry("pilot_view", CamControl.Kind.BUTTON),
            Map.entry("gimbal_tilt", CamControl.Kind.AXIS),
            Map.entry("gimbal_pan", CamControl.Kind.AXIS),
            Map.entry("zoom", CamControl.Kind.AXIS));
    private static final Set<String> SERVER_SYNC = Set.of("mode_normal", "mode_cine", "mode_sport", "mode_cycle", "pause", "mission_start",
            "waypoint_add", "follow_me", "poi_here", "course_lock");

    private static final class Registry implements SwitchRegistry {
        final List<SwitchSpec> switches = new ArrayList<>();
        final List<AxisSpec> axes = new ArrayList<>();

        @Override
        public AddonSwitch register(SwitchSpec spec) {
            this.switches.add(spec);
            return new AddonSwitch() {
                @Override
                public Identifier id() {
                    return spec.id();
                }

                @Override
                public SwitchSpec spec() {
                    return spec;
                }

                @Override
                public boolean active(Entity drone) {
                    return false;
                }
            };
        }

        @Override
        public AddonAxis registerAxis(AxisSpec spec) {
            this.axes.add(spec);
            return new AddonAxis() {
                @Override
                public Identifier id() {
                    return spec.id();
                }

                @Override
                public AxisSpec spec() {
                    return spec;
                }

                @Override
                public double value(Entity drone) {
                    return spec.center();
                }
            };
        }

        @Override
        public List<AddonSwitch> switches() {
            return List.of();
        }

        @Override
        public List<AddonAxis> axes() {
            return List.of();
        }

        @Override
        public Optional<AddonSwitch> find(Identifier id) {
            return Optional.empty();
        }

        @Override
        public Optional<AddonAxis> findAxis(Identifier id) {
            return Optional.empty();
        }
    }

    @Test
    void controlsMatchTheDesignTable() {
        assertEquals(DESIGN.size(), CamControl.values().length);
        for (CamControl control : CamControl.values()) {
            assertEquals(DESIGN.get(control.path()), control.kind(), control.path());
            assertEquals(SERVER_SYNC.contains(control.path()), control.serverSync(), control.path());
            assertEquals(CamDrones.MOD_ID, control.id().getNamespace());
            assertTrue(control.idString().length() <= SwitchSpec.MAX_ID_LENGTH, control.idString());
            assertTrue(control.path().matches("[a-z0-9_]+"), control.path());
            assertEquals(Optional.of(control), CamControl.byPath(control.path()));
        }
        assertEquals(14, CamControl.switches().size());
        assertEquals(3, CamControl.axes().size());
        assertTrue(CamControl.switches().size() <= SwitchRegistry.MAX_SWITCHES / 4, "höchstens ein Viertel der Plätze aller Add-ons");
        assertTrue(CamControl.axes().size() <= SwitchRegistry.MAX_AXES / 4, "höchstens ein Viertel der Achsen aller Add-ons");
        assertEquals(Optional.empty(), CamControl.byPath("unbekannt"));
    }

    @Test
    void specsFollowTheControls() {
        for (CamControl control : CamControl.switches()) {
            SwitchSpec spec = CamSwitches.switchSpec(control);
            assertEquals(control.id(), spec.id());
            assertEquals(control.kind() == CamControl.Kind.SWITCH, spec.toggle(), control.path());
            assertEquals(spec.toggle(), spec.latchesOnGamepad(), control.path());
            assertEquals(control.serverSync(), spec.serverSync(), control.path());
            assertEquals(SwitchSpec.defaultNameKey(control.id()), spec.nameKey());
            assertEquals(Optional.of(spec.nameKey() + ".description"), spec.descriptionKey());
            assertThrows(IllegalArgumentException.class, () -> CamSwitches.axisSpec(control));
        }
        for (CamControl control : CamControl.axes()) {
            AxisSpec spec = CamSwitches.axisSpec(control);
            assertEquals(AxisSpec.defaultNameKey(control.id()), spec.nameKey());
            assertFalse(spec.serverSync(), control.path());
            assertTrue(spec.center() >= -1.0 && spec.center() <= 1.0);
            assertTrue(spec.keyRateDps() > 0.0 && spec.keyRateDps() <= AxisSpec.MAX_KEY_RATE_DPS);
            assertThrows(IllegalArgumentException.class, () -> CamSwitches.switchSpec(control));
        }
        assertEquals(-1.0, CamSwitches.axisSpec(CamControl.ZOOM).center(), 1.0E-9);
        assertEquals(60.0, CamSwitches.axisSpec(CamControl.GIMBAL_TILT).keyRateDps(), 1.0E-9);
    }

    @Test
    void registrationFollowsTheDesignOrder() {
        Registry registry = new Registry();
        CamSwitches.Handles handles = CamSwitches.create(registry);
        assertEquals(14, registry.switches.size());
        assertEquals(3, registry.axes.size());
        assertEquals(CamControl.switches().stream().map(CamControl::id).toList(), registry.switches.stream().map(SwitchSpec::id).toList());
        assertEquals(CamControl.axes().stream().map(CamControl::id).toList(), registry.axes.stream().map(AxisSpec::id).toList());
        assertEquals(14, handles.switches().size());
        assertEquals(3, handles.axes().size());
        assertEquals(CamControl.ZOOM.center(), handles.axes().get(CamControl.ZOOM).value(null), 1.0E-9);
        assertFalse(handles.switches().get(CamControl.PAUSE).active(null));
        assertEquals(CamControl.PAUSE.id(), handles.switches().get(CamControl.PAUSE).id());
    }

    @Test
    void everyControlHasTextsInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/core/" + language + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (CamControl control : CamControl.values()) {
                assertTrue(lang.has(control.nameKey()), language + ": " + control.nameKey());
                assertTrue(names.add(lang.get(control.nameKey()).getAsString()), language + ": doppelter Name " + control.nameKey());
                if (!control.axis()) {
                    assertTrue(lang.has(control.descriptionKey()), language + ": " + control.descriptionKey());
                }
            }
            for (var id : CamWorldRules.IDS) {
                String key = "gamerule." + id.getNamespace() + "." + id.getPath();
                assertTrue(lang.has(key), language + ": " + key);
                assertTrue(lang.has(key + ".description"), language + ": " + key + ".description");
            }
        }
    }
}
