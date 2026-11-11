package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamOsdElementsTest {
    private static final FlightTelemetry TELEMETRY = new FlightTelemetry(true, "ANGLE", Optional.of("N"), 0.0, 70.0, 0.0, 3.0, 0.5, 90.0, 0.0,
            0.0, 0.5, 16.4, 4.1, 0.8, true, 10.0, 64.0, 10.0, true, 12, 0.95, 0.9, 120.0, 0.0, 0.0, 0.0, 0.5);

    private record FakeContext(boolean preview) implements OsdContext {
        @Override
        public Optional<Entity> drone() {
            return Optional.empty();
        }

        @Override
        public FlightTelemetry telemetry() {
            return TELEMETRY;
        }

        @Override
        public boolean fpv() {
            return !this.preview;
        }

        @Override
        public boolean lineOfSight() {
            return false;
        }

        @Override
        public long gameTime() {
            return 100L;
        }

        @Override
        public float partialTick() {
            return 0.0F;
        }
    }

    private static JsonObject lang(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/hud/" + language + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void fiveDesignElementsAndTheModeHintInTheAddonGroupAndTheModeOnlyOnRequest() {
        List<OsdElementSpec> specs = CamOsdElements.specs(context -> CamOsdState.NONE);
        assertEquals(List.of("camdrones:mode", "camdrones:gimbal", "camdrones:mission", "camdrones:obstacle", "camdrones:flight_left",
                        "camdrones:mode_hint"),
                specs.stream().map(spec -> spec.id().toString()).toList());
        for (OsdElementSpec spec : specs) {
            assertEquals(CamDrones.MOD_ID, spec.id().getNamespace());
            assertEquals(OsdGroup.ADDON, spec.group(), spec.id().toString());
            assertEquals(!spec.id().equals(CamOsdElements.MODE), spec.enabledByDefault(),
                    spec.id() + ": der Flugmodus steht schon in Propwashs Element Flugmodus, das eigene bleibt wählbar");
            assertEquals(OsdElementSpec.defaultNameKey(spec.id()), spec.nameKey());
            assertTrue(HudText.printable(spec.preview()) && !spec.preview().isEmpty(), spec.id().toString());
            assertTrue(spec.preview().length() <= spec.maxChars(), spec.id() + " Vorschau zu lang");
        }
    }

    @Test
    void elementsFitTheGridAndDoNotOverlap() {
        Set<String> cells = new HashSet<>();
        for (CamOsdElements.Element element : CamOsdElements.ELEMENTS) {
            assertTrue(element.firstColumn() >= 0 && element.lastColumn() < OsdElementSpec.COLUMNS, element.id().toString());
            assertTrue(element.row() >= 0 && element.row() < OsdElementSpec.ROWS, element.id().toString());
            for (int column = element.firstColumn(); column <= element.lastColumn(); column++) {
                assertTrue(cells.add(column + "/" + element.row()), element.id() + " überdeckt " + column + "/" + element.row());
            }
        }
    }

    @Test
    void longestTextsFitMaxChars() {
        CamOsdState full = StateBuilder.camera().everything().build();
        for (CamOsdElements.Element element : CamOsdElements.ELEMENTS) {
            OsdText text = element.text().apply(full);
            assertTrue(text != null, element.id().toString());
            assertTrue(text.text().length() <= element.maxChars(), element.id() + ": " + text.text());
            assertTrue(HudText.printable(text.text()), element.id() + ": " + text.text());
        }
        for (OsdText text : CamOsd.warnings(full)) {
            assertTrue(text.text().length() <= OsdElementSpec.MAX_CHARS && HudText.printable(text.text()), text.text());
        }
    }

    @Test
    void namesInBothLanguages() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        assertEquals(en.keySet(), de.keySet());
        for (OsdElementSpec spec : CamOsdElements.specs(context -> CamOsdState.NONE)) {
            assertTrue(en.has(spec.nameKey()), spec.nameKey());
            assertTrue(de.has(spec.nameKey()), spec.nameKey());
        }
        assertEquals("Hindernis", de.get("camdrones.osd.obstacle").getAsString());
    }

    @Test
    void contentAsksTheSourceOnlyOutsideThePreview() {
        AtomicInteger asked = new AtomicInteger();
        CamOsdState state = StateBuilder.camera().obstacle(NavInput.Sensor.LEFT, 4.2).build();
        CamOsdElements.StateSource source = context -> {
            asked.incrementAndGet();
            return state;
        };
        OsdElementSpec obstacle = CamOsdElements.specs(source).stream().filter(spec -> spec.id().equals(CamOsdElements.OBSTACLE)).findFirst()
                .orElseThrow();
        assertNull(obstacle.content().text(new FakeContext(true)));
        assertEquals(0, asked.get());
        assertEquals("< 4.2M", obstacle.content().text(new FakeContext(false)).text());
        assertEquals(1, asked.get());
    }

    @Test
    void tenWarningsInOwnNamespace() {
        CamOsdState calm = StateBuilder.camera().build();
        StateBuilder builder = StateBuilder.camera();
        builder.highWind = true;
        CamOsdState highWind = builder.build();
        Map<Identifier, OsdWarning> quiet = CamOsdElements.warnings(context -> calm);
        Map<Identifier, OsdWarning> loud = CamOsdElements.warnings(context -> highWind);
        assertEquals(10, quiet.size());
        for (Map.Entry<Identifier, OsdWarning> entry : quiet.entrySet()) {
            assertEquals(CamDrones.MOD_ID, entry.getKey().getNamespace());
            assertTrue(entry.getKey().toString().length() <= OsdElementSpec.MAX_ID_LENGTH);
            assertNull(entry.getValue().warning(new FakeContext(false)), entry.getKey().toString());
        }
        Identifier wind = CamOsdElements.warningId(CamOsd.Warning.HIGH_WIND);
        assertEquals("camdrones:warning/high_wind", wind.toString());
        assertEquals("HIGH WIND", loud.get(wind).warning(new FakeContext(false)).text());
        assertNull(loud.get(wind).warning(new FakeContext(true)));
        assertEquals("camdrones:warning/gimbal_limit", CamOsdElements.warningId(CamOsd.Warning.GIMBAL_LIMIT).toString());
    }
}
