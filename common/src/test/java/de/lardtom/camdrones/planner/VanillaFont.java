package de.lardtom.camdrones.planner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class VanillaFont {
    static final int UNKNOWN_ADVANCE = 9;
    private static VanillaFont instance;

    private final Map<Integer, Integer> advances = new HashMap<>();

    private VanillaFont() {
    }

    static synchronized VanillaFont get() {
        if (instance == null) {
            VanillaFont font = new VanillaFont();
            font.loadReference("minecraft:include/space");
            font.loadReference("minecraft:include/default");
            instance = font;
        }
        return instance;
    }

    int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += this.advances.getOrDefault(cp, UNKNOWN_ADVANCE);
            i += Character.charCount(cp);
        }
        return width;
    }

    boolean knows(int codePoint) {
        return this.advances.containsKey(codePoint);
    }

    int size() {
        return this.advances.size();
    }

    private void loadReference(String id) {
        String path = "assets/" + id.replace(":", "/font/") + ".json";
        JsonObject root = readJson(path);
        for (JsonElement element : root.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            String type = provider.get("type").getAsString();
            switch (type) {
                case "space" -> {
                    for (Map.Entry<String, JsonElement> entry : provider.getAsJsonObject("advances").entrySet()) {
                        this.advances.putIfAbsent(entry.getKey().codePointAt(0), (int) Math.floor(entry.getValue().getAsFloat()));
                    }
                }
                case "bitmap" -> this.loadBitmap(provider);
                case "reference" -> this.loadReference(provider.get("id").getAsString());
                default -> {
                }
            }
        }
    }

    private void loadBitmap(JsonObject provider) {
        String file = provider.get("file").getAsString();
        String path = "assets/" + file.replace(":", "/textures/");
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        JsonArray rows = provider.getAsJsonArray("chars");
        BufferedImage image;
        try (InputStream in = resource(path)) {
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
        int glyphWidth = image.getWidth() / columns;
        int glyphHeight = image.getHeight() / rows.size();
        float scale = (float) height / glyphHeight;
        for (int row = 0; row < rows.size(); row++) {
            String line = rows.get(row).getAsString();
            int column = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                i += Character.charCount(cp);
                int slot = column++;
                if (cp == 0 || this.advances.containsKey(cp)) {
                    continue;
                }
                int actual = actualWidth(image, glyphWidth, glyphHeight, slot, row);
                this.advances.put(cp, (int) (0.5 + actual * scale) + 1);
            }
        }
    }

    private static int actualWidth(BufferedImage image, int glyphWidth, int glyphHeight, int column, int row) {
        boolean alpha = image.getColorModel().hasAlpha();
        for (int width = glyphWidth - 1; width >= 0; width--) {
            int x = column * glyphWidth + width;
            for (int y = 0; y < glyphHeight; y++) {
                int argb = image.getRGB(x, row * glyphHeight + y);
                int value = alpha ? argb >>> 24 : (argb & 0xFFFFFF);
                if (value != 0) {
                    return width + 1;
                }
            }
        }
        return 0;
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = resource(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream resource(String path) throws IOException {
        InputStream in = VanillaFont.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Schriftdatei fehlt im Testpfad: " + path);
        }
        return in;
    }
}
