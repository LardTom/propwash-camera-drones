package de.lardtom.camdrones.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoAntennaTest {
    private static final String ANTENNA = "antenna";
    private static final String PAINT = "assets/camdrones/drone_paint.json";
    private static final int TEXTURE_COLOUR = 0xFFFFFF;

    private static VideoDefinition video(Identifier id) {
        PartFiles.registerAll();
        return (VideoDefinition) PropwashApi.parts().get(id).orElseThrow(() -> new AssertionError("Videoteil fehlt: " + id));
    }

    private static String antenna(VideoDefinition video) {
        return video.antenna().map(a -> a.isNone() ? "keine" : a.count() + " × " + a.lengthMm() + " mm " + a.cap())
                .orElse("abgeleitet aus den Videodaten");
    }

    private static JsonObject paint() {
        return ClasspathData.json(PAINT).orElseThrow(() -> new AssertionError(PAINT + " fehlt"));
    }

    @Test
    void everyCameraLinkHasNoExternalAntennas() {
        Map<Identifier, JsonObject> files = PartFiles.rawFiles("video");
        assertEquals(2, files.size(), "Videoteile: " + files.keySet());
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<Identifier, JsonObject> file : files.entrySet()) {
            JsonElement raw = file.getValue().get(ANTENNA);
            if (raw == null || !raw.isJsonPrimitive() || !"none".equals(raw.getAsString())) {
                wrong.add(file.getKey() + ": \"antenna\" ist " + raw + " statt \"none\"");
            }
            VideoDefinition video = video(file.getKey());
            if (!video.antenna().map(VideoAntenna::isNone).orElse(false)) {
                wrong.add(file.getKey() + ": Propwash zeichnet Antennen (" + antenna(video) + ")");
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void everyPresetFliesWithoutAntennas() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            VideoDefinition video = video(CamPresets.part(entry.video()));
            assertTrue(video.antenna().isPresent() && video.antenna().get().isNone(), entry.path() + ": Antennen " + antenna(video));
            assertEquals(0, video.antenna().get().count(), entry.path());
        }
    }

    @Test
    void paintHasNoAntennaChannel() {
        JsonObject paint = paint();
        List<String> found = new ArrayList<>();
        if (paint.has("channels") && paint.getAsJsonObject("channels").has(ANTENNA)) {
            found.add("channels." + ANTENNA);
        }
        if (paint.has("slots")) {
            for (JsonElement slot : paint.getAsJsonArray("slots")) {
                if (ANTENNA.equals(slot.getAsString())) {
                    found.add("slots: " + ANTENNA);
                }
            }
        }
        for (Map.Entry<String, JsonElement> model : paint.getAsJsonObject("models").entrySet()) {
            for (JsonElement group : model.getValue().getAsJsonArray()) {
                if (ANTENNA.equals(group.getAsJsonObject().get("channel").getAsString())) {
                    found.add(model.getKey() + ": Lack-Ebene " + group);
                }
            }
        }
        assertTrue(found.isEmpty(), "Lack-Kanal antenna in " + PAINT + ":\n" + String.join("\n", found));
    }

    @Test
    void linkPaintLayersKeepTheirTintNumbers() {
        JsonObject paint = paint();
        JsonObject references = paint.getAsJsonObject("channels");
        int checked = 0;
        for (Map.Entry<String, JsonElement> model : paint.getAsJsonObject("models").entrySet()) {
            if (!model.getKey().startsWith("video/")) {
                continue;
            }
            JsonArray groups = model.getValue().getAsJsonArray();
            String render = "assets/camdrones/items/drone_render/" + model.getKey() + "_paint.json";
            JsonArray tints = ClasspathData.json(render).orElseThrow(() -> new AssertionError(render + " fehlt"))
                    .getAsJsonObject("model").getAsJsonArray("tints");
            assertTrue(groups.size() >= 1 && groups.size() <= tints.size(), model.getKey() + ": " + groups.size() + " Lack-Ebenen, " + tints.size() + " Tints");
            for (int i = 0; i < tints.size(); i++) {
                JsonObject tint = tints.get(i).getAsJsonObject();
                assertEquals(i, tint.get("index").getAsInt(), render);
                int expected = TEXTURE_COLOUR;
                if (i < groups.size()) {
                    JsonObject group = groups.get(i).getAsJsonObject();
                    int reference = references.get(group.get("channel").getAsString()).getAsInt();
                    expected = tint(Integer.parseInt(group.get("base").getAsString().substring(1), 16), reference);
                }
                assertEquals(expected, tint.get("default").getAsInt(), model.getKey() + " Tint " + i);
            }
            checked++;
        }
        assertEquals(2, checked);
    }

    private static int tint(int rgb, int reference) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (rgb >> shift) & 0xFF;
            out = (out << 8) | Math.min(255, (c * 255 + reference / 2) / reference);
        }
        return out;
    }
}
