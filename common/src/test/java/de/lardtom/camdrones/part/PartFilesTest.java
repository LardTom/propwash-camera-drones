package de.lardtom.camdrones.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.StackDefinition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PartFilesTest {
    private static final Set<String> EXPECTED = Set.of("compact_fold_frame", "tour_fold_frame", "cine_frame", "nav_fc_compact", "nav_fc_pro",
            "m1404_1600", "m2008_500", "m4114_320", "fold_6", "fold_9", "fold_15", "smart_2s_3850_lion", "smart_4s_4240_hv",
            "smart_6s_8560_hv", "cam_link_compact", "cam_link_pro", "gimbal_compact", "gimbal_tour", "gimbal_cine");

    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/parts/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static <D extends PartDefinition> List<D> parts(Class<D> type) {
        return PartFiles.registerAll().values().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    public void everyPartFileParsesAndRegisters() {
        Map<Identifier, Object> parts = PartFiles.registerAll();
        for (Identifier id : parts.keySet()) {
            assertTrue(PropwashApi.parts().get(id).isPresent() || PropwashApi.parts().chemistry(id).isPresent(), id + " nicht angemeldet");
        }
        assertEquals(parts.size(), PropwashApi.parts().all().stream().filter(d -> PartFiles.NAMESPACE.equals(d.id().getNamespace())).count()
                + PropwashApi.parts().chemistries().stream().filter(c -> PartFiles.NAMESPACE.equals(c.id().getNamespace())).count());
    }

    @Test
    public void theDesignedPartsAndNothingElse() {
        Set<String> paths = new TreeSet<>();
        for (Identifier id : PartFiles.parseAll().keySet()) {
            assertEquals(PartFiles.NAMESPACE, id.getNamespace());
            paths.add(id.getPath());
        }
        assertEquals(new TreeSet<>(EXPECTED), paths);
    }

    @Test
    public void framesOnlyCarryFrontAndTop() {
        for (FrameDefinition frame : parts(FrameDefinition.class)) {
            assertEquals(Set.of(AccessorySlot.FRONT, AccessorySlot.TOP), Set.copyOf(frame.accessoryMounts()), frame.id().toString());
            assertTrue(frame.role() == FrameRole.LONG_RANGE || frame.role() == FrameRole.CINELIFTER, frame.id().toString());
            assertEquals(0.0, frame.geometry().cameraTiltDeg(), 1e-9, frame.id() + ": Kamera-Drohnen filmen ohne Uptilt");
        }
    }

    @Test
    public void gimbalsSitAtTheFront() {
        List<AccessoryDefinition> accessories = parts(AccessoryDefinition.class);
        assertEquals(3, accessories.size());
        for (AccessoryDefinition gimbal : accessories) {
            assertEquals(AccessorySlot.FRONT, gimbal.slot(), gimbal.id().toString());
            assertTrue(GimbalTable.isGimbal(gimbal.id()), gimbal.id().toString());
            assertTrue(gimbal.flightBatteryWatts() > 0.0, gimbal.id() + ": Gimbal braucht Strom");
        }
    }

    @Test
    public void ownMotorMountHasANameAndFitsTheCineFrame() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        Set<Identifier> frameMounts = new HashSet<>();
        for (FrameDefinition frame : parts(FrameDefinition.class)) {
            frameMounts.addAll(frame.motorMounts());
        }
        for (MotorDefinition motor : parts(MotorDefinition.class)) {
            assertTrue(frameMounts.contains(motor.mount()), motor.id() + ": kein Rahmen nimmt " + motor.mount());
            if (PartFiles.NAMESPACE.equals(motor.mount().getNamespace())) {
                String key = PartFiles.NAMESPACE + ".mount.motor." + motor.mount().getPath();
                assertTrue(en.has(key) && de.has(key), key);
            }
        }
        for (StackDefinition stack : parts(StackDefinition.class)) {
            assertTrue(SensorTable.isNavBoard(stack.id()), stack.id() + ": jeder Stack des Add-ons ist ein Nav-Board");
        }
    }

    @Test
    public void everyFrameMountTakesAMotorWithoutOtherAddons() {
        PartFiles.registerAll();
        Set<Identifier> motorMounts = new HashSet<>();
        Set<Identifier> stackMounts = new HashSet<>();
        for (PartDefinition definition : PropwashApi.parts().all()) {
            if (definition instanceof MotorDefinition motor) {
                motorMounts.add(motor.mount());
            } else if (definition instanceof StackDefinition stack) {
                stackMounts.add(stack.mount());
            }
        }
        for (FrameDefinition frame : parts(FrameDefinition.class)) {
            for (Identifier mount : frame.motorMounts()) {
                assertTrue(motorMounts.contains(mount), frame.id() + ": kein Motor für " + mount);
            }
            for (Identifier mount : frame.stackMounts()) {
                assertTrue(stackMounts.contains(mount), frame.id() + ": kein Stack für " + mount);
            }
        }
    }

    @Test
    public void everyPartHasNameAndHelpInBothLanguages() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        for (Identifier id : PartFiles.parseAll().keySet()) {
            String path = id.getPath();
            for (String key : List.of("part.camdrones." + path, "camdrones.help." + path + ".summary", "camdrones.help." + path + ".what.1",
                    "camdrones.help." + path + ".use.1")) {
                assertTrue(en.has(key), "en_us fehlt " + key);
                assertTrue(de.has(key), "de_de fehlt " + key);
            }
        }
        for (FrameDefinition frame : parts(FrameDefinition.class)) {
            String key = "camdrones.frame_short." + frame.id().getPath();
            assertTrue(en.has(key) && de.has(key), key);
        }
        for (CamPresets.Entry entry : CamPresets.ALL) {
            assertTrue(en.has(entry.translationKey()) && de.has(entry.translationKey()), entry.translationKey());
            assertEquals(entry.preset().translationKey(), entry.translationKey());
        }
        for (LensItems.Lens lens : LensItems.ALL) {
            assertTrue(en.has(lens.translationKey()) && de.has(lens.translationKey()), lens.translationKey());
            assertTrue(en.has(lens.hintKey()) && de.has(lens.hintKey()), lens.hintKey());
        }
    }

    @Test
    public void presetsUseOnlyAddonPartsAndOneClassEach() {
        Set<Identifier> known = PartFiles.parseAll().keySet();
        Set<FlightClass> classes = EnumSet.noneOf(FlightClass.class);
        for (CamPresets.Entry entry : CamPresets.ALL) {
            for (Identifier part : entry.build().allParts()) {
                assertTrue(known.contains(part), entry.path() + ": unbekanntes Teil " + part);
            }
            assertTrue(classes.add(entry.flightClass()), entry.path());
            assertEquals(entry.flightClass(), CamPresets.classOf(CamPresets.part(entry.frame())).orElseThrow());
            assertTrue(SensorTable.isNavBoard(CamPresets.part(entry.stack())), entry.path());
            assertEquals(CamPresets.part(entry.gimbal()), entry.build().accessory(AccessorySlot.FRONT).orElseThrow());
        }
        assertEquals(EnumSet.allOf(FlightClass.class), classes);
        assertTrue(CamPresets.classOf(Identifier.fromNamespaceAndPath("propwash", "freestyle_5")).isEmpty());
    }

    @Test
    public void sensorsFollowTheDesign() {
        assertEquals(Set.of(SensorDirection.DOWN), SensorTable.sensors(SensorTable.NAV_FC_COMPACT).orElseThrow());
        assertEquals(EnumSet.allOf(SensorDirection.class), SensorTable.sensors(SensorTable.NAV_FC_PRO).orElseThrow());
        assertFalse(SensorTable.canClimbOver(SensorTable.NAV_FC_COMPACT));
        assertTrue(SensorTable.canClimbOver(SensorTable.NAV_FC_PRO));
        assertTrue(SensorTable.sensors(Identifier.fromNamespaceAndPath("propwash", "stack_30")).isEmpty());
        Set<Identifier> stacks = new HashSet<>();
        parts(StackDefinition.class).forEach(stack -> stacks.add(stack.id()));
        assertEquals(stacks, SensorTable.navBoards());
    }

    @Test
    public void gimbalTableMatchesTheParts() {
        Set<Identifier> gimbals = new HashSet<>();
        parts(AccessoryDefinition.class).forEach(gimbal -> gimbals.add(gimbal.id()));
        assertEquals(gimbals, Set.of(GimbalTable.COMPACT, GimbalTable.TOUR, GimbalTable.CINE));
        for (Identifier gimbal : gimbals) {
            GimbalTable.Spec spec = GimbalTable.spec(gimbal).orElseThrow();
            assertEquals(-90.0, spec.minTiltDeg(), 1e-9, gimbal.toString());
            assertEquals(spec.lensBay(), spec.builtInFocalMm().isEmpty(), gimbal.toString());
        }
        assertTrue(GimbalTable.spec(GimbalTable.CINE).orElseThrow().fullPan());
        assertEquals(60.0, GimbalTable.spec(GimbalTable.CINE).orElseThrow().maxTiltDeg(), 1e-9);
    }

    @Test
    public void lensesAreDistinctAndHeavierWithLongerFocalLength() {
        Set<Integer> focal = new HashSet<>();
        double last = 0.0;
        for (LensItems.Lens lens : LensItems.ALL) {
            assertTrue(focal.add(lens.focalMm()), lens.path());
            assertEquals(PartFiles.NAMESPACE, lens.id().getNamespace());
            assertEquals("lens_" + lens.focalMm(), lens.path());
            assertTrue(lens.grams() > last, lens.path());
            last = lens.grams();
        }
    }

    @Test
    public void sortOrdersAreUniqueAndAfterJustMoreParts() {
        Set<Integer> seen = new HashSet<>();
        for (Object value : PartFiles.registerAll().values()) {
            if (value instanceof PartDefinition definition) {
                int sort = definition.basics().sortOrder();
                assertTrue(sort >= 700 && sort < 800, definition.id() + ": sort " + sort);
                assertTrue(seen.add(sort), definition.id() + ": sort doppelt");
            }
        }
    }

    @Test
    public void mapperReadsPropwashBuiltinFilesExactlyLikePropwash() {
        int compared = 0;
        for (PartDefinition definition : PropwashApi.parts().all()) {
            if (!PropwashApi.parts().isBuiltin(definition.id())) {
                continue;
            }
            JsonObject json = ClasspathData.json("data/propwash/propwash_parts/" + definition.kind().key() + "/" + definition.id().getPath() + ".json")
                    .orElseThrow();
            assertEquals(definition, PartFiles.parse(definition.kind().key(), definition.id(), json), "Abweichung bei " + definition.id());
            compared++;
        }
        for (CellChemistry chemistry : PropwashApi.parts().chemistries()) {
            if ("propwash".equals(chemistry.id().getNamespace())) {
                JsonObject json = ClasspathData.json("data/propwash/propwash_parts/chemistry/" + chemistry.id().getPath() + ".json").orElseThrow();
                assertEquals(chemistry, PartFiles.parse("chemistry", chemistry.id(), json));
                compared++;
            }
        }
        assertTrue(compared >= 30, "nur " + compared + " eingebaute Teile verglichen");
    }
}
