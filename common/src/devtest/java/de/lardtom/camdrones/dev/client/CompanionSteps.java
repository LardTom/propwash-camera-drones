package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.PartDefinition;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CompanionSteps {
    public static final String NAME = "zusammenspiel: weitere Propwash-Add-ons geladen, ohne Überschneidung";
    public static final String ENV = "CAMDRONES_SELFTEST_COMPANIONS";
    public static final String PROPERTY = "camdrones.selftest.companions";

    private CompanionSteps() {
    }

    public static List<String> companions() {
        String value = System.getenv(ENV);
        if (value == null || value.isBlank()) {
            value = System.getProperty(PROPERTY);
        }
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split(",")) {
            String id = part.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty() && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public static void register() {
        List<String> companions = companions();
        if (companions.isEmpty()) {
            return;
        }
        SelfTest.clientStep(35, NAME, 40, ctx -> check(companions));
    }

    private static StepResult check(List<String> companions) {
        List<String> problems = new ArrayList<>();
        Map<String, String> found = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] all = mc.options.keyMappings;
        Set<KeyMapping> own = Collections.newSetFromMap(new IdentityHashMap<>());
        own.addAll(CamKeys.mappings());
        for (String id : companions) {
            if (CamDrones.MOD_ID.equals(id) || "propwash".equals(id)) {
                problems.add(id + " ist kein weiteres Add-on");
                continue;
            }
            if (!Services.PLATFORM.isModLoaded(id)) {
                problems.add(id + " nicht geladen");
                continue;
            }
            if (!PropwashClientApi.isAddonActive(id)) {
                problems.add(id + " bei Propwash nicht als Add-on aktiv");
            }
            long parts = PropwashApi.parts().all().stream().map(PartDefinition::id).filter(p -> id.equals(p.getNamespace())).count();
            long items = BuiltInRegistries.ITEM.keySet().stream().filter(item -> id.equals(item.getNamespace())).count();
            long keys = 0;
            for (KeyMapping key : all) {
                if (id.equals(key.getCategory().id().getNamespace()) || key.getName().startsWith("key." + id + ".")) {
                    keys++;
                }
            }
            if (parts == 0) {
                problems.add(id + " ohne Teile bei Propwash");
            }
            found.put(id, Services.PLATFORM.modVersion(id).orElse("?") + " mit " + parts + " Teilen, " + items + " Items, " + keys + " Tasten");
        }
        long ownParts = PropwashApi.parts().all().stream().map(PartDefinition::id).filter(p -> CamDrones.MOD_ID.equals(p.getNamespace())).count();
        List<Identifier> presets = PropwashApi.builds().presets().stream().map(DronePreset::id).filter(p -> CamDrones.MOD_ID.equals(p.getNamespace())).toList();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            if (!presets.contains(entry.id())) {
                problems.add("Preset " + entry.id() + " fehlt neben den anderen Add-ons");
            }
        }
        int collisions = 0;
        for (KeyMapping mine : own) {
            InputConstants.Key key = mine.getDefaultKey();
            if (key.equals(InputConstants.UNKNOWN)) {
                continue;
            }
            for (KeyMapping other : all) {
                if (!own.contains(other) && other.getDefaultKey().equals(key)) {
                    collisions++;
                    problems.add(mine.getName() + " (" + key.getDisplayName().getString() + ") doppelt mit " + other.getName() + " [" + KeySteps.origin(other)
                            + "]");
                }
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass("geladen neben " + ownParts + " eigenen Teilen und " + presets.size() + " Presets: " + found + "; " + collisions
                + " Überschneidungen der Standardtasten");
    }
}
