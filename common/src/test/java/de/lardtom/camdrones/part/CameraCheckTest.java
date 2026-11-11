package de.lardtom.camdrones.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.part.CameraCheck.Input;
import de.lardtom.camdrones.part.CameraCheck.Line;
import de.lardtom.camdrones.part.CameraCheck.Result;
import de.lardtom.camdrones.part.CameraCheck.Role;
import de.lardtom.camdrones.part.CameraCheck.State;
import de.lardtom.camdrones.part.client.CameraCheckExtension;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraCheckTest {
    private static final Identifier FOREIGN_STACK = Identifier.fromNamespaceAndPath("propwash", "f4_aio_20x20");
    private static final Identifier FOREIGN_BATTERY = Identifier.fromNamespaceAndPath("propwash", "lipo_4s_1500");
    private static final Identifier FOREIGN_VIDEO = Identifier.fromNamespaceAndPath("propwash", "analog_vtx");
    private static final Identifier FOREIGN_FRAME = Identifier.fromNamespaceAndPath("propwash", "freestyle_5");
    private static final Identifier TOP_LIGHT = Identifier.fromNamespaceAndPath("propwash", "led_strip");
    private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?s");

    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/parts/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int args(String text) {
        Matcher m = ARG.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private static Line single(Result result, Role role) {
        List<Line> problems = result.problems();
        assertEquals(1, problems.size(), role + ": " + problems);
        assertEquals(role, problems.get(0).role());
        assertFalse(result.complete());
        return problems.get(0);
    }

    @Test
    void everyPresetIsACompleteCameraDrone() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            Result result = CameraCheck.check(entry.build());
            assertTrue(result.complete(), entry.path() + ": " + result.problems());
            assertEquals(Optional.of(entry), result.preset());
            assertEquals(List.of(Role.values()), result.lines().stream().map(Line::role).toList());
            for (Line line : result.lines()) {
                assertEquals(State.FITTED, line.state());
                assertTrue(line.fitted().isPresent() && line.advice().isEmpty(), line.toString());
            }
        }
    }

    @Test
    void eachMissingCameraPartIsExactlyOneNamedProblem() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        Map<Role, Function<Input, Input>> removals = Map.of(
                Role.NAV_FC, input -> input.withStack(Optional.empty()),
                Role.GIMBAL, input -> input.withAccessories(List.of()),
                Role.BATTERY, input -> input.withBattery(Optional.empty()),
                Role.LINK, input -> input.withVideo(Optional.empty()));
        for (CamPresets.Entry entry : CamPresets.ALL) {
            Map<Role, String> expected = Map.of(Role.NAV_FC, entry.stack(), Role.GIMBAL, entry.gimbal(), Role.BATTERY, entry.battery(),
                    Role.LINK, entry.video());
            for (Role role : Role.values()) {
                Line line = single(CameraCheck.check(removals.get(role).apply(Input.of(entry.build()))), role);
                assertEquals(State.MISSING, line.state());
                assertTrue(line.fitted().isEmpty());
                assertEquals(Optional.of(CamPresets.part(expected.get(role))), line.advice(), entry.path() + " " + role);
                String key = "part." + line.advice().get().getNamespace() + "." + line.advice().get().getPath();
                assertTrue(en.has(key) && de.has(key), key);
            }
        }
    }

    @Test
    void aStackWithoutGpsIsNamedAsWrong() {
        Input tour = Input.of(CamPresets.TOUR.build());
        Line stack = single(CameraCheck.check(tour.withStack(Optional.of(FOREIGN_STACK))), Role.NAV_FC);
        assertEquals(State.WRONG, stack.state());
        assertEquals(Optional.of(FOREIGN_STACK), stack.fitted());
        assertEquals(Optional.of(CamPresets.part(CamPresets.TOUR.stack())), stack.advice());
        Line gimbal = single(CameraCheck.check(tour.withAccessories(List.of(TOP_LIGHT))), Role.GIMBAL);
        assertEquals(State.MISSING, gimbal.state());
        assertTrue(CameraCheck.check(tour.withAccessories(List.of(TOP_LIGHT, CamPresets.part(CamPresets.TOUR.gimbal())))).complete());
    }

    @Test
    void otherBatteriesAndVideoLinksAreAcceptedWithARecommendation() {
        Input tour = Input.of(CamPresets.TOUR.build());
        Result battery = CameraCheck.check(tour.withBattery(Optional.of(FOREIGN_BATTERY)));
        assertTrue(battery.complete(), battery.problems().toString());
        assertEquals(State.OTHER, battery.line(Role.BATTERY).state());
        assertEquals(Optional.of(FOREIGN_BATTERY), battery.line(Role.BATTERY).fitted());
        assertEquals(Optional.of(CamPresets.part(CamPresets.TOUR.battery())), battery.line(Role.BATTERY).advice());
        Result video = CameraCheck.check(tour.withVideo(Optional.of(FOREIGN_VIDEO)));
        assertTrue(video.complete(), video.problems().toString());
        assertEquals(State.OTHER, video.line(Role.LINK).state());
        assertEquals(Optional.of(CamPresets.part(CamPresets.TOUR.video())), video.line(Role.LINK).advice());
        assertTrue(Role.BATTERY.acceptsOthers() && Role.LINK.acceptsOthers());
        assertFalse(Role.NAV_FC.acceptsOthers() || Role.GIMBAL.acceptsOthers());
    }

    @Test
    void withoutACameraFrameNothingIsAdvised() {
        Input empty = new Input(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        Result nothing = CameraCheck.check(empty);
        assertTrue(nothing.preset().isEmpty());
        assertEquals(Role.values().length, nothing.problems().size());
        assertTrue(nothing.problems().stream().allMatch(line -> line.state() == State.MISSING && line.advice().isEmpty()));
        Input foreignFrame = new Input(Optional.of(FOREIGN_FRAME), Optional.of(FOREIGN_STACK), Optional.empty(), Optional.empty(), List.of());
        Result foreign = CameraCheck.check(foreignFrame);
        assertTrue(foreign.preset().isEmpty());
        assertTrue(foreign.problems().stream().allMatch(line -> line.advice().isEmpty()));
        assertEquals(State.WRONG, foreign.line(Role.NAV_FC).state());
    }

    @Test
    void cameraPartTablesMatchThePresets() {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            assertEquals(Optional.of(entry), CameraCheck.presetFor(CamPresets.part(entry.frame())));
            assertTrue(CameraCheck.isSmartBattery(CamPresets.part(entry.battery())), entry.battery());
            assertTrue(CameraCheck.isCameraLink(CamPresets.part(entry.video())), entry.video());
        }
        assertFalse(CameraCheck.isSmartBattery(FOREIGN_BATTERY));
        assertFalse(CameraCheck.isCameraLink(FOREIGN_VIDEO));
        assertTrue(CameraCheck.presetFor(FOREIGN_FRAME).isEmpty());
    }

    @Test
    void textsExistInBothLanguagesWithMatchingPlaceholders() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        for (String key : CameraCheck.keys()) {
            assertTrue(en.has(key), "en_us fehlt " + key);
            assertTrue(de.has(key), "de_de fehlt " + key);
            assertEquals(args(en.get(key).getAsString()), args(de.get(key).getAsString()), key);
            assertFalse(en.get(key).getAsString().isBlank() || de.get(key).getAsString().isBlank(), key);
        }
        assertEquals(1, args(en.get(CameraCheck.ADVICE).getAsString()));
        assertEquals(1, args(en.get(CameraCheck.RECOMMENDED).getAsString()));
        assertEquals(2, args(en.get(CameraCheck.INCOMPLETE_HINT).getAsString()));
        assertEquals(1, args(en.get(Role.NAV_FC.wrongKey()).getAsString()));
        assertEquals("Kamera-Drohne prüfen", de.get(CameraCheck.TITLE).getAsString());
        assertTrue(en.get(CameraCheck.SHORT).getAsString().toLowerCase(Locale.ROOT).contains("camera"));
        assertTrue(de.get(CameraCheck.SHORT).getAsString().toLowerCase(Locale.ROOT).contains("kamera"));
    }

    @Test
    void workbenchExtensionUsesTheAddonNamespaceAndComesAfterPropwash() {
        assertEquals("camdrones", CameraCheckExtension.ID.getNamespace());
        assertTrue(CameraCheckExtension.ORDER > 1000, "Propwash-Knöpfe zuerst, das Add-on danach");
    }
}
