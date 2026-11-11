package de.lardtom.camdrones.part;

import de.lardtom.camdrones.bench.Bench;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalFramesTest {
    private static final Identifier FREESTYLE_5 = Identifier.fromNamespaceAndPath("propwash", "freestyle_5");
    private static final String ACCESSORY_FRAME = "accessory_frame";
    private static final List<Identifier> GIMBALS = List.of(GimbalTable.COMPACT, GimbalTable.TOUR, GimbalTable.CINE);

    private static Set<Identifier> cameraFrames() {
        Set<Identifier> out = new HashSet<>();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            out.add(CamPresets.part(entry.frame()));
        }
        return Set.copyOf(out);
    }

    private static FrameDefinition frame(Identifier id) {
        PartFiles.registerAll();
        PartDefinition definition = PropwashApi.parts().get(id).orElseThrow(() -> new AssertionError("Rahmen fehlt: " + id));
        return (FrameDefinition) definition;
    }

    private static BuildSpec propwashPreset(Identifier frame) {
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.data().build().frame().equals(frame)) {
                return preset.data().build();
            }
        }
        throw new AssertionError("kein Propwash-Preset mit " + frame);
    }

    @Test
    void everyGimbalNamesExactlyTheThreeCameraFrames() {
        Set<Identifier> addonFrames = new HashSet<>();
        for (Object value : PartFiles.registerAll().values()) {
            if (value instanceof FrameDefinition definition) {
                addonFrames.add(definition.id());
            }
        }
        assertEquals(cameraFrames(), addonFrames);
        for (Identifier gimbal : GIMBALS) {
            AccessoryDefinition definition = (AccessoryDefinition) PropwashApi.parts().get(gimbal).orElseThrow();
            assertEquals(cameraFrames(), definition.frames(), gimbal.toString());
        }
    }

    @Test
    void noGimbalBuildsOnFreestyle5() {
        BuildSpec freestyle = propwashPreset(FREESTYLE_5).withoutAccessory(AccessorySlot.FRONT);
        assertTrue(frame(FREESTYLE_5).accessoryMounts().contains(AccessorySlot.FRONT), "freestyle_5 hat eine Halterung vorne");
        BuildCheck bare = Bench.check(freestyle);
        assertTrue(bare.buildable(), "freestyle_5 ohne Gimbal: " + bare.problems() + " " + bare.unknownParts());
        for (Identifier gimbal : GIMBALS) {
            BuildCheck check = Bench.check(freestyle.withAccessory(AccessorySlot.FRONT, gimbal));
            assertFalse(check.buildable(), "freestyle_5 + " + gimbal + " ist baubar");
            assertEquals(List.of(ACCESSORY_FRAME), check.problems(), "freestyle_5 + " + gimbal);
        }
    }

    @Test
    void noGimbalBuildsOnAnyPropwashFrameWithAFrontMount() {
        PartFiles.registerAll();
        Set<Identifier> tested = new HashSet<>();
        for (DronePreset preset : PropwashApi.builds().presets()) {
            BuildSpec spec = preset.data().build().withoutAccessory(AccessorySlot.FRONT);
            if (cameraFrames().contains(spec.frame()) || !frame(spec.frame()).accessoryMounts().contains(AccessorySlot.FRONT)
                    || !Bench.check(spec).buildable()) {
                continue;
            }
            for (Identifier gimbal : GIMBALS) {
                BuildCheck check = Bench.check(spec.withAccessory(AccessorySlot.FRONT, gimbal));
                assertTrue(check.problems().contains(ACCESSORY_FRAME), spec.frame() + " + " + gimbal + ": " + check.problems());
                assertFalse(check.buildable(), spec.frame() + " + " + gimbal + " ist baubar");
            }
            tested.add(spec.frame());
        }
        assertTrue(tested.contains(FREESTYLE_5), "geprüfte Rahmen: " + tested);
        assertTrue(tested.size() >= 4, "geprüfte Rahmen: " + tested);
    }

    @Test
    void everyGimbalBuildsOnEveryCameraFrame() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            for (Identifier gimbal : GIMBALS) {
                BuildCheck check = Bench.check(entry.build().withAccessory(AccessorySlot.FRONT, gimbal));
                assertTrue(check.buildable(), entry.path() + " + " + gimbal + ": " + check.problems() + " " + check.unknownParts());
                assertFalse(check.problems().contains(ACCESSORY_FRAME), entry.path() + " + " + gimbal);
            }
        }
    }
}
