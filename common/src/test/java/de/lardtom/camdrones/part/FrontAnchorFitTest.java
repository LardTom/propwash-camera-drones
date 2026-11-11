package de.lardtom.camdrones.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import net.minecraft.resources.Identifier;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontAnchorFitTest {
    private static final double[][] BOXES = {{62.0, 58.0, 64.0}, {34.0, 26.0, 40.0}, {12.0, 10.0, 20.0}, {44.0, 44.0, 46.0},
            {30.0, 24.0, 36.0}};

    private static List<FrameDefinition> frontFrames() {
        List<FrameDefinition> out = new ArrayList<>();
        for (Object value : PartFiles.registerAll().values()) {
            if (value instanceof FrameDefinition frame && frame.accessoryMounts().contains(AccessorySlot.FRONT)) {
                out.add(frame);
            }
        }
        return out;
    }

    private static List<AccessoryDefinition> frontAccessories() {
        List<AccessoryDefinition> out = new ArrayList<>();
        for (Object value : PartFiles.registerAll().values()) {
            if (value instanceof AccessoryDefinition accessory && accessory.slot() == AccessorySlot.FRONT) {
                out.add(accessory);
            }
        }
        return out;
    }

    static List<Vector3d[]> frameSolids(Identifier frame) {
        String base = "assets/" + frame.getNamespace() + "/";
        JsonObject render = ClasspathData.json(base + "items/drone_render/frame/" + frame.getPath() + ".json")
                .orElseThrow(() -> new AssertionError("Render-Definition fehlt: " + frame)).getAsJsonObject("model");
        Identifier model = Identifier.parse(render.get("model").getAsString());
        JsonObject json = ClasspathData.json("assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json")
                .orElseThrow(() -> new AssertionError("Modell fehlt: " + model));
        return FrontFit.solids(render, json);
    }

    private static String size(double width, double height, double depth) {
        return String.format(Locale.ROOT, "%.0f×%.0f×%.0f", width, height, depth);
    }

    @Test
    void frontAccessoriesTouchEveryCameraFrame() {
        List<FrameDefinition> frames = frontFrames();
        assertEquals(3, frames.size(), "Kamera-Rahmen mit Halterung vorne: " + frames);
        List<AccessoryDefinition> accessories = frontAccessories();
        assertEquals(3, accessories.size(), "Gimbals: " + accessories);
        List<String> misses = new ArrayList<>();
        int measured = 0;
        for (FrameDefinition frame : frames) {
            AnchorSpec anchor = frame.geometry().accessoryAnchors().get(AccessorySlot.FRONT);
            assertNotNull(anchor, frame.id() + " ohne Anker vorne");
            List<Vector3d[]> solids = frameSolids(frame.id());
            List<double[]> sizes = new ArrayList<>(List.of(BOXES));
            for (AccessoryDefinition accessory : accessories) {
                sizes.add(new double[]{accessory.widthMm(), accessory.heightMm(), accessory.depthMm()});
            }
            for (double[] box : sizes) {
                FrontFit.Fit fit = FrontFit.measure(solids, anchor, box[0], box[1], box[2]);
                measured++;
                if (!fit.ok()) {
                    misses.add(String.format(Locale.ROOT, "%s + Quader %s: %s, Anker (%.1f; %.1f; %.1f)", frame.id(), size(box[0], box[1], box[2]),
                            fit.describe(), anchor.x(), anchor.y(), anchor.z()));
                }
            }
        }
        assertEquals(3 * (BOXES.length + 3), measured);
        assertTrue(misses.isEmpty(), "Zubehör vorne nicht am Rahmen (Abstand höchstens " + FrontFit.MAX_GAP_MM + " mm, Überschneidung höchstens "
                + FrontFit.MAX_OVERLAP_MM + " mm):\n" + String.join("\n", misses));
    }

    @Test
    void theMeasureSeesGapsAndOverlaps() {
        List<Vector3d[]> plate = List.<Vector3d[]>of(new Vector3d[]{new Vector3d(-10, 0, 0), new Vector3d(10, 0, 0), new Vector3d(-10, 10, 0),
                new Vector3d(10, 10, 0), new Vector3d(-10, 0, 20), new Vector3d(10, 0, 20), new Vector3d(-10, 10, 20), new Vector3d(10, 10, 20)});
        FrontFit.Fit flush = FrontFit.measure(plate, new AnchorSpec(0.0, 10.0, 0.0, 0.0, 0.0), 20.0, 10.0, 20.0);
        assertEquals(0.0, flush.gapMm(), 1.0E-9);
        assertEquals(0.0, flush.overlapMm(), 1.0E-9);
        assertTrue(flush.ok());
        FrontFit.Fit apart = FrontFit.measure(plate, new AnchorSpec(0.0, 10.0, -4.5, 0.0, 0.0), 20.0, 10.0, 20.0);
        assertEquals(4.5, apart.gapMm(), 1.0E-9);
        assertFalse(apart.ok());
        FrontFit.Fit below = FrontFit.measure(plate, new AnchorSpec(0.0, -4.5, 10.0, 0.0, 0.0), 20.0, 10.0, 20.0);
        assertEquals(4.5, below.gapMm(), 1.0E-9);
        FrontFit.Fit inside = FrontFit.measure(plate, new AnchorSpec(0.0, 10.0, 5.0, 0.0, 0.0), 20.0, 10.0, 20.0);
        assertEquals(0.0, inside.gapMm(), 1.0E-9);
        assertEquals(5.0, inside.overlapMm(), 0.26);
        assertFalse(inside.ok());
    }

    @Test
    void renderTransformationScalesAboutTheModelCentre() {
        JsonObject element = new JsonObject();
        JsonArray from = new JsonArray();
        JsonArray to = new JsonArray();
        for (int i = 0; i < 3; i++) {
            from.add(8.0);
            to.add(9.0);
        }
        element.add("from", from);
        element.add("to", to);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject model = new JsonObject();
        model.add("elements", elements);
        JsonObject plain = new JsonObject();
        Vector3d[] unscaled = FrontFit.solids(plain, model).getFirst();
        assertEquals(0.0, unscaled[0].x, 1.0E-9);
        assertEquals(8.0, unscaled[7].x, 1.0E-9);
        JsonObject render = JsonParser.parseString(
                "{\"transformation\": {\"left_rotation\": [0,0,0,1], \"right_rotation\": [0,0,0,1], \"scale\": [1.73,1.73,1.73],"
                        + " \"translation\": [-0.365,-0.365,-0.365]}}").getAsJsonObject();
        Vector3d[] scaled = FrontFit.solids(render, model).getFirst();
        assertEquals(0.0, scaled[0].x, 1.0E-9);
        assertEquals(8.0 * 1.73, scaled[7].y, 1.0E-9);
    }
}
