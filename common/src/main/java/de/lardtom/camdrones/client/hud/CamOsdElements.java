package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class CamOsdElements {
    public static final Identifier MODE = CamDrones.id("mode");
    public static final Identifier GIMBAL = CamDrones.id("gimbal");
    public static final Identifier MISSION = CamDrones.id("mission");
    public static final Identifier OBSTACLE = CamDrones.id("obstacle");
    public static final Identifier FLIGHT_LEFT = CamDrones.id("flight_left");
    public static final Identifier MODE_HINT = CamDrones.id("mode_hint");

    public record Element(Identifier id, int column, int row, OsdAlign align, int maxChars, String preview, boolean enabledByDefault,
                          Function<CamOsdState, OsdText> text) {
        public Element {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(align, "align");
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(text, "text");
        }

        public int firstColumn() {
            return switch (this.align) {
                case LEFT -> this.column;
                case CENTER -> this.column - this.maxChars / 2;
                case RIGHT -> this.column - this.maxChars + 1;
            };
        }

        public int lastColumn() {
            return this.firstColumn() + this.maxChars - 1;
        }
    }

    public static final List<Element> ELEMENTS = List.of(
            new Element(MODE, 28, 11, OsdAlign.RIGHT, 8, "N", false, CamOsd::mode),
            new Element(GIMBAL, 1, 9, OsdAlign.LEFT, 6, "G -45", true, CamOsd::gimbal),
            new Element(MISSION, 15, 4, OsdAlign.CENTER, 16, "WP 3/12 0:42", true, CamOsd::mission),
            new Element(OBSTACLE, 15, 11, OsdAlign.CENTER, 8, "< 4.2M", true, CamOsd::obstacle),
            new Element(FLIGHT_LEFT, 1, 10, OsdAlign.LEFT, 10, "LEFT 24:10", true, CamOsd::flightLeft),
            new Element(MODE_HINT, 15, 5, OsdAlign.CENTER, CamOsd.MODE_HINT_CHARS, "N: GPS HOLDS POS + HORIZON", true, CamOsd::modeHint));

    @FunctionalInterface
    public interface StateSource {
        CamOsdState state(OsdContext context);
    }

    private CamOsdElements() {
    }

    public static Identifier warningId(CamOsd.Warning warning) {
        return CamDrones.id(warning.path());
    }

    public static OsdElementSpec spec(Element element, StateSource source) {
        Objects.requireNonNull(source, "source");
        Function<CamOsdState, OsdText> text = element.text();
        return OsdElementSpec.builder(element.id())
                .position(element.column(), element.row())
                .align(element.align())
                .enabledByDefault(element.enabledByDefault())
                .group(OsdGroup.ADDON)
                .maxChars(element.maxChars())
                .preview(element.preview())
                .content(context -> context.preview() ? null : text.apply(source.state(context)))
                .build();
    }

    public static List<OsdElementSpec> specs(StateSource source) {
        List<OsdElementSpec> specs = new ArrayList<>();
        for (Element element : ELEMENTS) {
            specs.add(spec(element, source));
        }
        return List.copyOf(specs);
    }

    public static Map<Identifier, OsdWarning> warnings(StateSource source) {
        Objects.requireNonNull(source, "source");
        Map<Identifier, OsdWarning> warnings = new LinkedHashMap<>();
        for (CamOsd.Warning warning : CamOsd.Warning.values()) {
            warnings.put(warningId(warning), context -> context.preview() ? null : CamOsd.warning(warning, source.state(context)));
        }
        return warnings;
    }
}
