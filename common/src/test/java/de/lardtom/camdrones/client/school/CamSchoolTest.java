package de.lardtom.camdrones.client.school;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.camdrones.client.input.CamKeyDefaults;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamSchoolTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static List<Scene> scenes;

    @BeforeAll
    static void build() {
        PartFiles.registerAll();
        scenes = List.of(CameraDroneScene.create(), FirstMissionScene.create(), FollowPoiScene.create());
    }

    private static Map<String, String> lang(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/school/" + language + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    private static List<Cue> cues(Scene scene) {
        List<Cue> out = new ArrayList<>();
        for (SceneStep step : scene.steps()) {
            for (TimedCue timed : step.cues()) {
                out.add(timed.cue());
            }
        }
        return out;
    }

    private static int limit(String key) {
        if (key.endsWith(".title")) {
            return 38;
        }
        if (key.endsWith(".description")) {
            return 64;
        }
        return key.endsWith(".text") ? 150 : 90;
    }

    @Test
    void threeChaptersInTheOwnNamespaceWithTheDesignedSteps() {
        assertEquals(CamSchool.SCENES, scenes.stream().map(Scene::id).toList());
        assertEquals(List.of("camdrones:camera_drone", "camdrones:first_mission", "camdrones:follow_poi"), CamSchool.SCENES);
        List<List<String>> steps = List.of(CameraDroneScene.STEPS, FirstMissionScene.STEPS, FollowPoiScene.STEPS);
        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            assertTrue(scene.keyPrefix().startsWith("camdrones.guide.scene."), scene.keyPrefix());
            assertEquals(steps.get(i), scene.steps().stream().map(SceneStep::name).toList(), scene.id());
            assertTrue(scene.icon().startsWith("camdrones:"), scene.icon());
            for (SceneStep step : scene.steps()) {
                for (TimedCue timed : step.cues()) {
                    assertTrue(timed.start() <= step.duration(), scene.id() + " " + step.name() + " Cue nach dem Schrittende");
                }
            }
        }
        assertTrue(scenes.get(1).items().ids().containsAll(Set.of("camdrones:flight_planner", "camdrones:landing_pad")));
    }

    @Test
    void everyTextExistsInBothLanguagesWithinTheLimitsAndNothingIsUnused() throws IOException {
        Set<String> used = new LinkedHashSet<>();
        scenes.forEach(scene -> used.addAll(scene.translationKeys()));
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            Map<String, String> lang = lang(language);
            for (String key : used) {
                String value = lang.get(key);
                if (value == null || value.isBlank()) {
                    problems.add(language + " fehlt " + key);
                } else if (value.length() > limit(key)) {
                    problems.add(language + " " + key + " zu lang: " + value.length() + " > " + limit(key));
                }
            }
            Set<String> unused = new TreeSet<>(lang.keySet());
            unused.removeAll(used);
            if (!unused.isEmpty()) {
                problems.add(language + " nicht benutzt: " + unused);
            }
            for (Map.Entry<String, String> entry : lang.entrySet()) {
                String value = entry.getValue();
                if (value.contains("\"")) {
                    problems.add(language + " " + entry.getKey() + ": gerade Anführungszeichen");
                }
                if (language.equals("de_de") && value.contains("“") && !value.contains("„")) {
                    problems.add(language + " " + entry.getKey() + ": deutsche Anführungszeichen „…“");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void keyHintsUseExistingMappingsOfTheAddOnAndPropwash() {
        Set<String> own = new HashSet<>();
        CamKeyDefaults.ALL.forEach(entry -> own.add(entry.translationKey()));
        int hints = 0;
        for (Scene scene : scenes) {
            for (Cue cue : cues(scene)) {
                if (cue instanceof Cue.Keys keys) {
                    hints++;
                    assertFalse(keys.keys().isEmpty(), keys.textKey());
                    for (KeyToken token : keys.keys()) {
                        if (token.kind() == KeyToken.Kind.MAPPING && token.value().startsWith("key.camdrones.")) {
                            assertTrue(own.contains(token.value()), token.value());
                        }
                    }
                }
            }
        }
        assertTrue(hints >= 7, "nur " + hints + " Tastenhinweise");
        assertTrue(own.contains(SchoolKit.KEY_MODE_CYCLE) && own.contains(SchoolKit.KEY_MISSION_START) && own.contains(SchoolKit.KEY_PAUSE)
                && own.contains(SchoolKit.KEY_TILT_UP) && own.contains(SchoolKit.KEY_TILT_DOWN));
    }

    @Test
    void actorsShowTheTourDroneThePlannerAndTheLandingPad() {
        for (Scene scene : scenes) {
            boolean drone = false;
            for (Cue cue : cues(scene)) {
                if (cue instanceof Cue.Spawn spawn && spawn.spec() instanceof ActorSpec.Drone) {
                    drone = true;
                }
            }
            assertTrue(drone, scene.id() + " ohne Drohne");
        }
        boolean pad = false;
        boolean planner = false;
        for (Cue cue : cues(scenes.get(1))) {
            if (cue instanceof Cue.PlaceBlock place && place.block().id().equals(SchoolKit.LANDING_PAD)) {
                pad = true;
            }
            if (cue instanceof Cue.Spawn spawn && spawn.spec() instanceof ActorSpec.Pilot pilot && pilot.handItem().equals(SchoolKit.PLANNER)) {
                planner = true;
            }
        }
        assertTrue(pad, "Erste Mission ohne Landeplatz");
        assertTrue(planner, "Erste Mission ohne Flugplaner in der Hand");
        assertEquals(FirstMissionScene.CLICKED.size(), FirstMissionScene.waypoints().size());
        assertEquals(SchoolKit.PAD_TOP, FirstMissionScene.PAD.y() - 1.0F, 1e-6);
        assertEquals(25, SchoolKit.circle(FollowPoiScene.BEACON, FollowPoiScene.ORBIT_RADIUS, FollowPoiScene.ORBIT_HEIGHT, 24, 90.0F).size());
    }
}
