package de.lardtom.camdrones.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CovCamASmartBatteryTest {
    private static final String DISPLAY_CONTEXT = "minecraft:display_context";
    private static final String COMPONENT = "minecraft:component";
    private static final String CHARGE = "propwash:battery_charge";
    private static final int LEDS = 4;
    private static final int CHARGE_STEPS = 1000;
    private static final int LED_ON = 0xE9FBFF;
    private static final List<String> HELD_CONTEXTS = List.of("gui", "firstperson_righthand", "firstperson_lefthand",
            "thirdperson_righthand", "ground", "fixed", "head");

    private static void selectors(JsonElement element, List<String> out) {
        if (element instanceof JsonObject object) {
            if (object.has("property")) {
                out.add(object.get("property").getAsString());
            }
            if (object.has("type") && object.get("type").getAsString().endsWith("range_dispatch")) {
                out.add("range_dispatch");
            }
            for (String key : object.keySet()) {
                selectors(object.get(key), out);
            }
        } else if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                selectors(child, out);
            }
        }
    }

    private static JsonObject itemDefinition(String key) throws IOException {
        String path = "assets/camdrones/items/" + key + ".json";
        try (InputStream in = CovCamASmartBatteryTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " fehlt");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean matches(JsonElement when, String context, Float charge, boolean component) {
        if (when instanceof JsonArray array) {
            for (JsonElement value : array) {
                if (matches(value, context, charge, component)) {
                    return true;
                }
            }
            return false;
        }
        if (component) {
            return charge != null && Float.floatToIntBits(when.getAsFloat()) == Float.floatToIntBits(charge);
        }
        return when.getAsString().equals(context);
    }

    private static String resolve(JsonObject node, String context, Float charge) {
        String type = node.get("type").getAsString();
        switch (type) {
            case "minecraft:model":
                return node.get("model").getAsString();
            case "minecraft:select": {
                String property = node.get("property").getAsString();
                boolean component = COMPONENT.equals(property);
                if (component) {
                    assertEquals(CHARGE, node.get("component").getAsString(), "Auswahl nach einer anderen Komponente");
                } else if (!DISPLAY_CONTEXT.equals(property)) {
                    fail("unbekannte Auswahl " + property);
                }
                for (JsonElement entry : node.getAsJsonArray("cases")) {
                    JsonObject c = entry.getAsJsonObject();
                    if (matches(c.get("when"), context, charge, component)) {
                        return resolve(c.getAsJsonObject("model"), context, charge);
                    }
                }
                assertTrue(node.has("fallback"), "Auswahl ohne fallback");
                return resolve(node.getAsJsonObject("fallback"), context, charge);
            }
            default:
                fail("unbekannter Modell-Typ " + type);
                return "";
        }
    }

    private static int litLeds(int step) {
        return (step * LEDS + CHARGE_STEPS - 1) / CHARGE_STEPS;
    }

    private static String ledModel(String key, int lit) {
        return "camdrones:part/battery/" + key + (lit == LEDS ? "" : "_led" + lit);
    }

    private static int ledPixels(String model) throws IOException {
        String path = "assets/camdrones/textures/item/" + model.substring("camdrones:".length()) + ".png";
        try (InputStream in = CovCamASmartBatteryTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " fehlt");
            BufferedImage image = ImageIO.read(in);
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) > 0 && (argb & 0xFFFFFF) == LED_ON) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    @Test
    void smartBatteryLedsFollowTheCharge() throws IOException {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            String path = "assets/camdrones/items/" + entry.battery() + ".json";
            JsonElement json = itemDefinition(entry.battery());
            List<String> found = new ArrayList<>();
            selectors(json, found);
            boolean charge = found.stream().anyMatch(p -> !p.equals(DISPLAY_CONTEXT));
            assertTrue(charge, path + " wählt das Modell nur nach " + found + ", die LED-Punkte hängen nicht vom Ladestand ab");
        }
    }

    @Test
    void everyChargeStepLightsOneLedPerQuarter() throws IOException {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            String key = entry.battery();
            JsonObject model = itemDefinition(key).getAsJsonObject("model");
            for (String context : HELD_CONTEXTS) {
                assertEquals(ledModel(key, LEDS), resolve(model, context, null), key + " ohne Ladestand (" + context + ") nicht voll");
                for (int step = 0; step <= CHARGE_STEPS; step++) {
                    float charge = step / (float) CHARGE_STEPS;
                    assertEquals(ledModel(key, litLeds(step)), resolve(model, context, charge),
                            key + " bei Ladestand " + charge + " (" + context + ")");
                }
            }
            assertEquals(ledModel(key, LEDS), resolve(model, "none", 0.5F), key + ": das Modell an der Drohne ändert sich");
        }
    }

    @Test
    void halfChargeShowsTwoOfFourLeds() throws IOException {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            JsonObject model = itemDefinition(entry.battery()).getAsJsonObject("model");
            assertEquals(ledModel(entry.battery(), 4), resolve(model, "gui", 1.0F), entry.battery() + " voll");
            assertEquals(ledModel(entry.battery(), 3), resolve(model, "gui", 0.75F), entry.battery() + " bei 75 %");
            assertEquals(ledModel(entry.battery(), 2), resolve(model, "gui", 0.5F), entry.battery() + " halb leer");
            assertEquals(ledModel(entry.battery(), 1), resolve(model, "gui", 0.1F), entry.battery() + " bei 10 %");
            assertEquals(ledModel(entry.battery(), 0), resolve(model, "gui", 0.0F), entry.battery() + " leer");
        }
    }

    @Test
    void fewerLitLedsShowFewerLitPixels() throws IOException {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            String key = entry.battery();
            int previous = -1;
            for (int lit = 0; lit <= LEDS; lit++) {
                int pixels = ledPixels(ledModel(key, lit));
                if (lit == 0) {
                    assertEquals(0, pixels, key + ": leerer Akku mit leuchtenden LED-Punkten");
                } else {
                    assertTrue(pixels > previous, key + ": " + lit + " LED-Punkte leuchten nicht heller als " + (lit - 1)
                            + " (" + pixels + " gegen " + previous + " Pixel)");
                }
                previous = pixels;
            }
        }
    }
}
