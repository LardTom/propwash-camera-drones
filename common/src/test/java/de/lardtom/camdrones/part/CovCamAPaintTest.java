package de.lardtom.camdrones.part;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamAPaintTest {
    private static final double GREY_SPREAD = 0.08;
    private static final int ACCENT_SPREAD = 60;
    private static final double HUE_TOLERANCE = 25.0;
    private static final List<String> ACCENT_TEXTURES = List.of("accessory/gimbal_compact", "accessory/gimbal_tour", "accessory/gimbal_cine",
            "prop/fold_6", "prop/fold_9", "prop/fold_15");

    private static double luminance(int rgb) {
        double r = (rgb >> 16 & 0xFF) / 255.0;
        double g = (rgb >> 8 & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double spread(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255.0;
    }

    private static int frame(CamPresets.Entry entry) {
        return entry.paint().color("frame").orElseThrow(() -> new AssertionError(entry.path() + " ohne Rahmenlack"));
    }

    @Test
    void presetsArePaintedLightGreyDarkGreyAndBlack() {
        double compact = luminance(frame(CamPresets.COMPACT));
        double tour = luminance(frame(CamPresets.TOUR));
        double cine = luminance(frame(CamPresets.CINE));
        assertTrue(compact >= 0.65, "Kompakt nicht hellgrau: " + compact);
        assertTrue(tour >= 0.2 && tour <= 0.5, "Tour nicht dunkelgrau: " + tour);
        assertTrue(cine <= 0.15, "Kino nicht schwarz: " + cine);
        for (CamPresets.Entry entry : CamPresets.ALL) {
            assertTrue(spread(frame(entry)) <= GREY_SPREAD, entry.path() + " ist kein Grau: " + Integer.toHexString(frame(entry)));
        }
    }

    @Test
    void paintCoversFrameMotorsAndGimbal() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            assertTrue(entry.paint().color("frame").isPresent(), entry.path() + " ohne Lack für den Rahmen");
            assertTrue(entry.paint().color("motors").isPresent(), entry.path() + " ohne Lack für die Motoren");
            assertTrue(entry.paint().color("accessories").isPresent(), entry.path() + " ohne Lack für den Gimbal");
            assertEquals(entry.paint().color("frame"), entry.paint().color("motors"), entry.path() + ": Schale und Motorgondeln verschieden lackiert");
        }
    }

    private static double hue(int r, int g, int b) {
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        return hsb[0] * 360.0;
    }

    private static double accentHue(String texture) throws IOException {
        String path = "assets/camdrones/textures/item/part/" + texture + ".png";
        try (InputStream in = CovCamAPaintTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " fehlt");
            BufferedImage image = ImageIO.read(in);
            Map<Integer, Integer> buckets = new TreeMap<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) < 128) {
                        continue;
                    }
                    int r = argb >> 16 & 0xFF;
                    int g = argb >> 8 & 0xFF;
                    int b = argb & 0xFF;
                    if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > ACCENT_SPREAD) {
                        buckets.merge((int) Math.round(hue(r, g, b) / 10.0), 1, Integer::sum);
                    }
                }
            }
            assertTrue(!buckets.isEmpty(), texture + " ohne Akzentfarbe");
            int best = buckets.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            return best * 10.0;
        }
    }

    @Test
    void gimbalsAndPropTipsShareOneAccentColour() throws IOException {
        double reference = accentHue(ACCENT_TEXTURES.getFirst());
        for (String texture : ACCENT_TEXTURES) {
            double hue = accentHue(texture);
            double difference = Math.abs(((hue - reference) % 360.0 + 540.0) % 360.0 - 180.0);
            assertTrue(difference <= HUE_TOLERANCE, texture + ": Akzent " + hue + "° statt " + reference + "°");
        }
    }
}
