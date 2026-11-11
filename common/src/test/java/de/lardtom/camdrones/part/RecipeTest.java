package de.lardtom.camdrones.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeTest {
    public static final String JMP_RECIPES_ENV = "CAMDRONES_JMP_RECIPES";
    public static final String MD_RECIPES_ENV = "CAMDRONES_MD_RECIPES";
    public static final String FLIGHT_PLANNER = "camdrones:flight_planner";
    public static final String LANDING_PAD = "camdrones:landing_pad";
    private static final Path OWN_RECIPES = Path.of("common/src/main/resources/data/camdrones/recipe");
    private static final Path OWN_ADVANCEMENTS = Path.of("common/src/main/resources/data/camdrones/advancement/recipes");

    private static Map<String, JsonObject> ownRecipes() {
        return ClasspathData.jsonTree(RepoFiles.root().resolve(OWN_RECIPES));
    }

    private static List<RecipeShape> ownShapes() {
        List<RecipeShape> out = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : ownRecipes().entrySet()) {
            out.add(RecipeShape.of("camdrones:" + entry.getKey(), entry.getValue()).orElseThrow());
        }
        return out;
    }

    private static List<RecipeShape> foreignShapes(String folder) {
        List<RecipeShape> out = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : ClasspathData.jsonTree(folder).entrySet()) {
            RecipeShape.of(folder + "/" + entry.getKey(), entry.getValue()).ifPresent(out::add);
        }
        return out;
    }

    private static Set<String> craftableIds() {
        Set<String> out = new TreeSet<>();
        PartFiles.parseAll().keySet().forEach(id -> out.add(id.toString()));
        for (LensItems.Lens lens : LensItems.ALL) {
            out.add(lens.id().toString());
        }
        out.add(FLIGHT_PLANNER);
        out.add(LANDING_PAD);
        return out;
    }

    @Test
    public void everyPartAndLensHasExactlyOneRecipe() {
        Map<String, JsonObject> recipes = ownRecipes();
        Set<String> results = new TreeSet<>();
        for (Map.Entry<String, JsonObject> entry : recipes.entrySet()) {
            String result = entry.getValue().getAsJsonObject("result").get("id").getAsString();
            assertEquals("camdrones:" + entry.getKey().replace(".json", ""), result, entry.getKey() + ": Datei heißt wie das Ergebnis");
            assertTrue(results.add(result), result + " hat zwei Rezepte");
        }
        assertEquals(craftableIds(), results);
    }

    @Test
    public void recipesFollowThePropwashStyle() {
        for (Map.Entry<String, JsonObject> entry : ownRecipes().entrySet()) {
            String name = entry.getKey();
            JsonObject recipe = entry.getValue();
            assertEquals(RecipeShape.SHAPED, recipe.get("type").getAsString(), name);
            String result = recipe.getAsJsonObject("result").get("id").getAsString();
            assertEquals(LANDING_PAD.equals(result) ? "building" : "equipment", recipe.get("category").getAsString(), name);
            Optional<String> group = recipe.has("group") ? Optional.of(recipe.get("group").getAsString()) : Optional.empty();
            assertEquals(expectedGroup(result), group, name);
            List<String> rows = new ArrayList<>();
            recipe.getAsJsonArray("pattern").forEach(row -> rows.add(row.getAsString()));
            assertTrue(rows.size() >= 1 && rows.size() <= 3, name);
            Set<Character> used = new TreeSet<>();
            for (String row : rows) {
                assertEquals(rows.get(0).length(), row.length(), name + ": Zeilen gleich lang");
                assertTrue(row.length() <= 3, name);
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        used.add(c);
                    }
                }
            }
            Set<Character> defined = new TreeSet<>();
            for (String key : recipe.getAsJsonObject("key").keySet()) {
                assertEquals(1, key.length(), name);
                defined.add(key.charAt(0));
            }
            assertEquals(defined, used, name + ": Schlüssel und Muster passen nicht zusammen");
            int count = recipe.getAsJsonObject("result").has("count") ? recipe.getAsJsonObject("result").get("count").getAsInt() : 1;
            assertTrue(count >= 1 && count <= 4, name + ": Ausbeute " + count);
        }
    }

    @Test
    public void onlyExistingItemsAreUsed() {
        Set<String> own = craftableIds();
        for (Map.Entry<String, JsonObject> entry : ownRecipes().entrySet()) {
            for (Map.Entry<String, JsonElement> key : entry.getValue().getAsJsonObject("key").entrySet()) {
                String ingredient = key.getValue().getAsString();
                if (ingredient.startsWith("#")) {
                    assertTrue(ClasspathData.itemTagExists(ingredient.substring(1)), entry.getKey() + ": Tag fehlt " + ingredient);
                } else if (ingredient.startsWith("camdrones:")) {
                    assertTrue(own.contains(ingredient), entry.getKey() + ": unbekannt " + ingredient);
                } else {
                    assertTrue(ingredient.startsWith("minecraft:") || ingredient.startsWith("propwash:"), entry.getKey() + ": fremder Namensraum " + ingredient);
                    assertTrue(ClasspathData.itemExists(ingredient), entry.getKey() + ": Item fehlt " + ingredient);
                }
            }
        }
    }

    @Test
    public void ownRecipesAreUniqueAmongEachOther() {
        List<RecipeShape> shapes = ownShapes();
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                assertFalse(shapes.get(i).conflictsWith(shapes.get(j)), shapes.get(i).name() + " gleicht " + shapes.get(j).name());
            }
        }
    }

    @Test
    public void uniqueAgainstPropwashAndVanilla() {
        List<RecipeShape> foreign = new ArrayList<>(foreignShapes("data/propwash/recipe"));
        int propwash = foreign.size();
        foreign.addAll(foreignShapes("data/minecraft/recipe"));
        assertTrue(propwash >= 40, "nur " + propwash + " Propwash-Rezepte gefunden");
        assertTrue(foreign.size() - propwash >= 1000, "nur " + (foreign.size() - propwash) + " Vanilla-Rezepte gefunden");
        List<String> hits = new ArrayList<>();
        for (RecipeShape mine : ownShapes()) {
            for (RecipeShape other : foreign) {
                if (mine.conflictsWith(other)) {
                    hits.add(mine.name() + " gleicht " + other.name());
                }
            }
        }
        assertEquals(List.of(), hits);
    }

    @Test
    public void uniqueAgainstJustMoreParts() {
        Set<String> jmp = new HashSet<>(JmpRecipeSnapshot.HASHES.keySet());
        assertTrue(jmp.size() >= 140, "Schnappschuss zu klein: " + jmp.size());
        List<String> hits = new ArrayList<>();
        for (RecipeShape mine : ownShapes()) {
            for (String hash : mine.concreteHashes()) {
                if (jmp.contains(hash)) {
                    hits.add(mine.name() + " gleicht " + JmpRecipeSnapshot.HASHES.get(hash));
                }
            }
        }
        assertEquals(List.of(), hits);
    }

    @Test
    public void uniqueAgainstALiveJustMorePartsCheckout() {
        String dir = System.getenv(JMP_RECIPES_ENV);
        if (dir == null || dir.isBlank()) {
            return;
        }
        Map<String, JsonObject> live = ClasspathData.jsonTree(Path.of(dir));
        assertFalse(live.isEmpty(), JMP_RECIPES_ENV + " ohne Rezepte");
        List<String> hits = new ArrayList<>();
        List<String> snapshot = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : live.entrySet()) {
            RecipeShape.of("justmoreparts:" + entry.getKey(), entry.getValue()).ifPresent(other -> {
                for (String hash : other.concreteHashes()) {
                    snapshot.add("            Map.entry(\"" + hash + "\", \"" + other.result() + "\"),");
                }
                for (RecipeShape mine : ownShapes()) {
                    if (mine.conflictsWith(other)) {
                        hits.add(mine.name() + " gleicht " + other.name());
                    }
                }
            });
        }
        System.out.println(String.join("\n", snapshot));
        assertEquals(List.of(), hits);
    }

    @Test
    public void uniqueAgainstMobDefense() {
        Set<String> md = new HashSet<>(MobDefenseRecipeSnapshot.HASHES.keySet());
        assertTrue(md.size() >= 3, "Schnappschuss zu klein: " + md.size());
        List<String> hits = new ArrayList<>();
        for (RecipeShape mine : ownShapes()) {
            for (String hash : mine.concreteHashes()) {
                if (md.contains(hash)) {
                    hits.add(mine.name() + " gleicht " + MobDefenseRecipeSnapshot.HASHES.get(hash));
                }
            }
        }
        assertEquals(List.of(), hits);
    }

    @Test
    public void uniqueAgainstALiveMobDefenseCheckout() {
        String dir = System.getenv(MD_RECIPES_ENV);
        if (dir == null || dir.isBlank()) {
            return;
        }
        Map<String, JsonObject> live = ClasspathData.jsonTree(Path.of(dir));
        assertFalse(live.isEmpty(), MD_RECIPES_ENV + " ohne Rezepte");
        List<String> hits = new ArrayList<>();
        List<String> snapshot = new ArrayList<>();
        Set<String> missing = new TreeSet<>();
        for (Map.Entry<String, JsonObject> entry : live.entrySet()) {
            RecipeShape.of("mobdefense:" + entry.getKey(), entry.getValue()).ifPresent(other -> {
                for (String hash : other.concreteHashes()) {
                    snapshot.add("            Map.entry(\"" + hash + "\", \"" + other.result() + "\"),");
                    if (!MobDefenseRecipeSnapshot.HASHES.containsKey(hash)) {
                        missing.add(other.result());
                    }
                }
                for (RecipeShape mine : ownShapes()) {
                    if (mine.conflictsWith(other)) {
                        hits.add(mine.name() + " gleicht " + other.name());
                    }
                }
            });
        }
        System.out.println(String.join("\n", snapshot));
        assertEquals(List.of(), hits);
        assertEquals(Set.of(), missing, "Schnappschuss " + MobDefenseRecipeSnapshot.SOURCE + " veraltet");
    }

    @Test
    public void flightPlannerAndLandingPadFollowTheDesign() {
        Map<String, JsonObject> recipes = ownRecipes();
        JsonObject planner = recipes.get("flight_planner.json");
        assertEquals(List.of("IGI", "IMI", "IBI"), pattern(planner));
        assertEquals(Map.of("I", "minecraft:iron_ingot", "G", "minecraft:glass_pane", "M", "minecraft:map", "B", "propwash:circuit_board"),
                keys(planner));
        JsonObject pad = recipes.get("landing_pad.json");
        assertEquals(List.of("KWK", "SSS"), pattern(pad));
        assertEquals(Map.of("K", "minecraft:gray_carpet", "W", "minecraft:white_dye", "S", "minecraft:smooth_stone_slab"), keys(pad));
        Map<String, JsonObject> advancements = ClasspathData.jsonTree(RepoFiles.root().resolve(OWN_ADVANCEMENTS));
        assertEquals("#propwash:parts", unlockItems(advancements.get("tools/flight_planner.json")));
        assertEquals(FLIGHT_PLANNER, unlockItems(advancements.get("tools/landing_pad.json")));
    }

    private static List<String> pattern(JsonObject recipe) {
        List<String> rows = new ArrayList<>();
        recipe.getAsJsonArray("pattern").forEach(row -> rows.add(row.getAsString()));
        return rows;
    }

    private static Map<String, String> keys(JsonObject recipe) {
        Map<String, String> out = new TreeMap<>();
        recipe.getAsJsonObject("key").entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        return out;
    }

    private static String unlockItems(JsonObject advancement) {
        for (Map.Entry<String, JsonElement> criterion : advancement.getAsJsonObject("criteria").entrySet()) {
            JsonObject value = criterion.getValue().getAsJsonObject();
            if ("minecraft:inventory_changed".equals(value.get("trigger").getAsString())) {
                return value.getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject().get("items").getAsString();
            }
        }
        throw new IllegalArgumentException("kein Auslöser über das Inventar");
    }

    @Test
    public void detectorFindsMirrorsTagsAndShapelessTwins() {
        RecipeShape prop = RecipeShape.of("p7_2b", ClasspathData.json("data/propwash/recipe/p7_2b.json").orElseThrow()).orElseThrow();
        assertTrue(prop.conflictsWith(prop.mirrored()));
        assertEquals(prop.signature(), prop.mirrored().signature());
        for (RecipeShape mine : ownShapes()) {
            JsonObject shapeless = new JsonObject();
            shapeless.addProperty("type", RecipeShape.SHAPELESS);
            JsonArray ingredients = new JsonArray();
            mine.nonEmpty().forEach(ingredients::add);
            shapeless.add("ingredients", ingredients);
            assertTrue(mine.conflictsWith(RecipeShape.of("twin", shapeless).orElseThrow()), mine.name());
        }
        RecipeShape wool = ownShapes().stream().filter(s -> s.cells().contains("#minecraft:wool")).findFirst().orElseThrow();
        assertTrue(wool.concreteHashes().size() >= 16, "Wolle wird nicht aufgelöst");
        assertTrue(RecipeShape.overlap("#minecraft:wool", "minecraft:white_wool"));
    }

    @Test
    public void everyRecipeUnlocksLikeJustMoreParts() {
        Map<String, JsonObject> advancements = ClasspathData.jsonTree(RepoFiles.root().resolve(OWN_ADVANCEMENTS));
        Set<String> unlocked = new TreeSet<>();
        for (Map.Entry<String, JsonObject> entry : advancements.entrySet()) {
            JsonObject advancement = entry.getValue();
            assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString(), entry.getKey());
            String recipe = advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString();
            String category = entry.getKey().substring(0, entry.getKey().indexOf('/'));
            assertEquals(category + "/" + recipe.substring("camdrones:".length()) + ".json", entry.getKey());
            assertEquals(recipe, advancement.getAsJsonObject("criteria").getAsJsonObject("has_the_recipe").getAsJsonObject("conditions")
                    .get("recipes").getAsString(), entry.getKey());
            assertEquals(2, advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().size(), entry.getKey());
            assertTrue(unlocked.add(recipe), recipe + " doppelt freigeschaltet");
        }
        assertEquals(craftableIds(), unlocked);
    }

    private static Optional<String> expectedGroup(String result) {
        if (FLIGHT_PLANNER.equals(result) || LANDING_PAD.equals(result)) {
            return Optional.empty();
        }
        for (LensItems.Lens lens : LensItems.ALL) {
            if (lens.id().toString().equals(result)) {
                return Optional.of("camdrones_lens");
            }
        }
        for (String category : PartFiles.ORDER) {
            if (PartFiles.rawFiles(category).keySet().stream().anyMatch(id -> id.toString().equals(result))) {
                return Optional.of("propwash_" + category);
            }
        }
        throw new IllegalArgumentException("unbekanntes Ergebnis " + result);
    }
}
