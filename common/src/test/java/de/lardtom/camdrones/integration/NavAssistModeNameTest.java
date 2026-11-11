package de.lardtom.camdrones.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class NavAssistModeNameTest {
    private static final double DT = 0.01;
    private static final Set<SensorDirection> ALL = EnumSet.allOf(SensorDirection.class);
    private static final Map<StabilizationMode, String> WITH_GPS = Map.of(StabilizationMode.ANGLE, "camdrones.mode.n",
            StabilizationMode.HORIZON, "camdrones.mode.c", StabilizationMode.ACRO, "camdrones.mode.s");
    private static final Map<StabilizationMode, String> WITHOUT_GPS = Map.of(StabilizationMode.ANGLE, "camdrones.mode.atti",
            StabilizationMode.HORIZON, "camdrones.mode.c", StabilizationMode.ACRO, "camdrones.mode.s");

    private static NavAssist pilot(FlightClass flightClass) {
        return new NavAssist(flightClass, ALL, true, NavContext.NONE);
    }

    private static FakeFlight.Output run(NavAssist assist, FakeFlight.State state, double seconds) {
        FakeFlight.Output output = null;
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            output = new FakeFlight.Output();
            state.step(DT);
            assist.update(state, output);
        }
        return output;
    }

    private static Method modeNameKey(FlightAssist assist) {
        Method method;
        try {
            method = assist.getClass().getMethod("modeNameKey", StabilizationMode.class);
        } catch (NoSuchMethodException e) {
            return fail(assist.getClass().getSimpleName() + " hat kein modeNameKey(StabilizationMode), Propwashs Einblendung nennt Acro, Angle und Horizon");
        }
        assertEquals(assist.getClass(), method.getDeclaringClass(), "modeNameKey kommt nicht von der Flughilfe selbst");
        assertFalse(Modifier.isStatic(method.getModifiers()), "modeNameKey ist statisch");
        assertEquals("java.util.Optional<java.lang.String>", method.getGenericReturnType().getTypeName());
        return method;
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> nameKey(FlightAssist assist, StabilizationMode mode) {
        try {
            Optional<String> key = (Optional<String>) modeNameKey(assist).invoke(assist, mode);
            assertNotNull(key, mode.toString());
            return key;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return fail("modeNameKey(" + mode + ")", e);
        }
    }

    private static Map<StabilizationMode, String> names(NavAssist assist) {
        Map<StabilizationMode, String> names = new EnumMap<>(StabilizationMode.class);
        for (StabilizationMode mode : StabilizationMode.values()) {
            names.put(mode, nameKey(assist, mode).orElse("leer"));
        }
        return names;
    }

    private static JsonObject camLang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/core/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject propwashLang(String language) throws IOException {
        try (InputStream in = FlightAssist.class.getResourceAsStream("/assets/propwash/lang/" + language + ".json")) {
            assertNotNull(in, "Sprachdatei von Propwash " + language);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String text(JsonObject lang, String key) {
        assertTrue(lang.has(key), "Text fehlt: " + key);
        return lang.get(key).getAsString();
    }

    @Test
    void angleIsNHorizonIsCAndAcroIsSOnEveryClass() {
        for (FlightClass flightClass : FlightClass.values()) {
            NavAssist assist = pilot(flightClass);
            assertEquals(WITH_GPS, names(assist), flightClass + " vor dem ersten Schritt");
            run(assist, new FakeFlight.State().at(0.0, 80.0, 0.0).mode(StabilizationMode.HORIZON), 0.5);
            assertFalse(assist.logic().atti(), flightClass.toString());
            assertEquals(WITH_GPS, names(assist), flightClass + " im Flug");
        }
    }

    @Test
    void angleWithoutGpsIsAttiUntilTheFixIsBack() {
        for (FlightClass flightClass : FlightClass.values()) {
            NavAssist assist = pilot(flightClass);
            FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0).mode(StabilizationMode.ANGLE);
            run(assist, state, 0.5);
            state.gpsFix = false;
            FakeFlight.Output output = run(assist, state, 1.0);
            assertTrue(assist.logic().atti(), flightClass + " ohne GPS");
            assertEquals("ATTI", output.label, flightClass.toString());
            assertEquals(WITHOUT_GPS, names(assist), flightClass + " ohne GPS");
            state.gpsFix = true;
            run(assist, state, 3.0);
            assertFalse(assist.logic().atti(), flightClass + " mit GPS");
            assertEquals(WITH_GPS, names(assist), flightClass + " mit GPS");
        }
    }

    @Test
    void namesAreTheLettersOfTheOsdInEnglishAndGerman() throws IOException {
        NavAssist assist = pilot(FlightClass.TOUR);
        FakeFlight.State state = new FakeFlight.State().at(0.0, 80.0, 0.0);
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = camLang(language);
            for (StabilizationMode mode : List.of(StabilizationMode.ANGLE, StabilizationMode.HORIZON, StabilizationMode.ACRO)) {
                FakeFlight.Output output = run(assist, state.mode(mode), 0.3);
                assertEquals(output.label, text(lang, nameKey(assist, mode).orElseThrow()), language + " " + mode);
            }
            assertEquals("ATTI", text(lang, WITHOUT_GPS.get(StabilizationMode.ANGLE)), language);
        }
    }

    @Test
    void popupNamesTheCameraModesInsteadOfAcroAngleAndHorizon() throws IOException {
        NavAssist assist = pilot(FlightClass.CINE);
        JsonObject en = camLang("en_us");
        JsonObject de = camLang("de_de");
        String enSwitch = text(propwashLang("en_us"), "propwash.drone.mode.changed_switch");
        String deSwitch = text(propwashLang("de_de"), "propwash.drone.mode.changed_switch");
        String enChanged = text(propwashLang("en_us"), "propwash.drone.mode.changed");
        assertEquals("Mode: C (key – radio switch: S)", String.format(enSwitch,
                text(en, nameKey(assist, StabilizationMode.HORIZON).orElseThrow()), text(en, nameKey(assist, StabilizationMode.ACRO).orElseThrow())));
        assertEquals("Modus: N (Taste – Funkenschalter: S)", String.format(deSwitch,
                text(de, nameKey(assist, StabilizationMode.ANGLE).orElseThrow()), text(de, nameKey(assist, StabilizationMode.ACRO).orElseThrow())));
        assertEquals("Mode: S", String.format(enChanged, text(en, nameKey(assist, StabilizationMode.ACRO).orElseThrow())));
    }
}
