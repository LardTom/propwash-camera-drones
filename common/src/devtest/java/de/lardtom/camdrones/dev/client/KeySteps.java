package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.input.CamKeyDefaults;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class KeySteps {
    public static final String NAME = "tasten: keine doppelten Standardtasten";

    private KeySteps() {
    }

    public static void register() {
        SelfTest.clientStep(19, NAME, 40, KeySteps::check);
    }

    static String origin(KeyMapping key) {
        String namespace = key.getCategory().id().getNamespace();
        return "minecraft".equals(namespace) ? "Vanilla" : "propwash".equals(namespace) ? "Propwash" : namespace;
    }

    private static StepResult check(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] all = mc.options.keyMappings;
        List<KeyMapping> own = CamKeys.mappings();
        if (own.size() != CamKeyDefaults.ALL.size()) {
            return StepResult.fail("eigene Tasten: " + own.size() + " statt " + CamKeyDefaults.ALL.size());
        }
        Set<KeyMapping> ownSet = Collections.newSetFromMap(new IdentityHashMap<>());
        ownSet.addAll(own);
        int registered = 0;
        for (KeyMapping key : all) {
            if (ownSet.contains(key)) {
                registered++;
            }
        }
        if (registered != own.size()) {
            return StepResult.fail("nur " + registered + " von " + own.size() + " eigenen Tasten stehen in den Spiel-Optionen");
        }
        Map<String, Integer> origins = new TreeMap<>();
        for (KeyMapping key : all) {
            if (!ownSet.contains(key)) {
                origins.merge(origin(key), 1, Integer::sum);
            }
        }
        Set<String> problems = new LinkedHashSet<>();
        int withDefault = 0;
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            KeyMapping mapping = KeyMapping.get(entry.translationKey());
            if (mapping == null || !ownSet.contains(mapping)) {
                problems.add(entry.translationKey() + " fehlt im Spiel");
                continue;
            }
            InputConstants.Key expected = entry.bound() ? InputConstants.Type.KEYBOARD.getOrCreate(entry.defaultKey()) : InputConstants.UNKNOWN;
            if (!mapping.getDefaultKey().equals(expected)) {
                problems.add(entry.translationKey() + " hat Standard " + mapping.getDefaultKey().getName() + " statt " + expected.getName());
            }
        }
        for (KeyMapping mine : own) {
            InputConstants.Key key = mine.getDefaultKey();
            if (key.equals(InputConstants.UNKNOWN)) {
                continue;
            }
            withDefault++;
            for (KeyMapping other : all) {
                if (other == mine || !other.getDefaultKey().equals(key)) {
                    continue;
                }
                boolean twin = ownSet.contains(other);
                String first = twin && other.getName().compareTo(mine.getName()) < 0 ? other.getName() : mine.getName();
                String second = first.equals(mine.getName()) ? other.getName() : mine.getName();
                problems.add(first + " (" + key.getDisplayName().getString() + ") doppelt mit " + second + " ["
                        + (twin ? "eigene Taste" : origin(other)) + "]");
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(problems.size() + " Treffer: " + String.join(", ", problems));
        }
        return StepResult.pass(all.length + " Tasten im Spiel (" + origins + ", eigene " + own.size() + "), " + withDefault
                + " eigene mit Standard, keiner doppelt");
    }
}
