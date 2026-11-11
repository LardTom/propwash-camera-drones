package de.lardtom.camdrones.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record RecipeShape(String name, String result, boolean shaped, int width, int height, List<String> cells) {
    public static final String SHAPED = "minecraft:crafting_shaped";
    public static final String SHAPELESS = "minecraft:crafting_shapeless";
    private static final int MAX_EXPANSIONS = 4096;

    public static Optional<RecipeShape> of(String name, JsonObject recipe) {
        String type = recipe.has("type") ? recipe.get("type").getAsString() : "";
        String result = recipe.has("result") && recipe.get("result").isJsonObject() && recipe.getAsJsonObject("result").has("id")
                ? recipe.getAsJsonObject("result").get("id").getAsString() : "";
        if (SHAPED.equals(type)) {
            JsonObject key = recipe.getAsJsonObject("key");
            List<String> rows = new ArrayList<>();
            for (JsonElement row : recipe.getAsJsonArray("pattern")) {
                rows.add(row.getAsString());
            }
            int top = 0;
            while (top < rows.size() && rows.get(top).isBlank()) {
                top++;
            }
            int bottom = rows.size() - 1;
            while (bottom >= top && rows.get(bottom).isBlank()) {
                bottom--;
            }
            int maxWidth = rows.stream().mapToInt(String::length).max().orElse(0);
            int left = maxWidth;
            int right = -1;
            for (int r = top; r <= bottom; r++) {
                String row = rows.get(r);
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) != ' ') {
                        left = Math.min(left, c);
                        right = Math.max(right, c);
                    }
                }
            }
            List<String> cells = new ArrayList<>();
            for (int r = top; r <= bottom; r++) {
                String row = rows.get(r);
                for (int c = left; c <= right; c++) {
                    char ch = c < row.length() ? row.charAt(c) : ' ';
                    cells.add(ch == ' ' ? "" : ingredient(key.get(String.valueOf(ch))));
                }
            }
            return Optional.of(new RecipeShape(name, result, true, right - left + 1, bottom - top + 1, List.copyOf(cells)));
        }
        if (SHAPELESS.equals(type)) {
            List<String> cells = new ArrayList<>();
            for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
                cells.add(ingredient(element));
            }
            Collections.sort(cells);
            return Optional.of(new RecipeShape(name, result, false, cells.size(), 1, List.copyOf(cells)));
        }
        return Optional.empty();
    }

    public static String ingredient(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Zutat fehlt");
        }
        if (element.isJsonArray()) {
            Set<String> options = new TreeSet<>();
            for (JsonElement option : (JsonArray) element) {
                options.add(ingredient(option));
            }
            return String.join("|", options);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("item")) {
                return object.get("item").getAsString();
            }
            if (object.has("tag")) {
                return "#" + object.get("tag").getAsString();
            }
            return object.toString();
        }
        return element.getAsString();
    }

    public List<String> nonEmpty() {
        return this.cells.stream().filter(cell -> !cell.isEmpty()).toList();
    }

    public RecipeShape mirrored() {
        if (!this.shaped) {
            return this;
        }
        List<String> out = new ArrayList<>(this.cells.size());
        for (int r = 0; r < this.height; r++) {
            for (int c = this.width - 1; c >= 0; c--) {
                out.add(this.cells.get(r * this.width + c));
            }
        }
        return new RecipeShape(this.name, this.result, true, this.width, this.height, List.copyOf(out));
    }

    public String signature() {
        if (!this.shaped) {
            return "shapeless " + String.join(",", this.cells);
        }
        String direct = this.rawSignature();
        String mirror = this.mirrored().rawSignature();
        return direct.compareTo(mirror) <= 0 ? direct : mirror;
    }

    public String hash() {
        return hash(this.signature());
    }

    public static String hash(String signature) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<String> concreteHashes() {
        List<List<String>> options = new ArrayList<>();
        long combinations = 1;
        for (String cell : this.cells) {
            List<String> choices = new ArrayList<>(alternatives(cell));
            options.add(choices);
            combinations *= choices.size();
            if (combinations > MAX_EXPANSIONS) {
                throw new IllegalStateException(this.name + ": zu viele Varianten für den Vergleich");
            }
        }
        Set<String> hashes = new LinkedHashSet<>();
        expand(options, 0, new ArrayList<>(), hashes);
        return List.copyOf(hashes);
    }

    private void expand(List<List<String>> options, int index, List<String> chosen, Set<String> out) {
        if (index == options.size()) {
            List<String> cells = new ArrayList<>(chosen);
            if (!this.shaped) {
                Collections.sort(cells);
            }
            out.add(new RecipeShape(this.name, this.result, this.shaped, this.width, this.height, List.copyOf(cells)).hash());
            return;
        }
        for (String option : options.get(index)) {
            chosen.add(option);
            expand(options, index + 1, chosen, out);
            chosen.remove(chosen.size() - 1);
        }
    }

    public boolean conflictsWith(RecipeShape other) {
        if (this.shaped && other.shaped) {
            return this.width == other.width && this.height == other.height
                    && (sameCells(this.cells, other.cells) || sameCells(this.mirrored().cells, other.cells));
        }
        List<String> mine = this.nonEmpty();
        List<String> theirs = other.nonEmpty();
        return mine.size() == theirs.size() && matches(mine, theirs, 0, new boolean[theirs.size()]);
    }

    private static boolean sameCells(List<String> a, List<String> b) {
        for (int i = 0; i < a.size(); i++) {
            String x = a.get(i);
            String y = b.get(i);
            if (x.isEmpty() != y.isEmpty()) {
                return false;
            }
            if (!x.isEmpty() && !overlap(x, y)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(List<String> mine, List<String> theirs, int index, boolean[] used) {
        if (index == mine.size()) {
            return true;
        }
        for (int j = 0; j < theirs.size(); j++) {
            if (!used[j] && overlap(mine.get(index), theirs.get(j))) {
                used[j] = true;
                if (matches(mine, theirs, index + 1, used)) {
                    return true;
                }
                used[j] = false;
            }
        }
        return false;
    }

    public static boolean overlap(String a, String b) {
        Set<String> left = alternatives(a);
        for (String item : alternatives(b)) {
            if (left.contains(item)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> alternatives(String ingredient) {
        if (ingredient.isEmpty()) {
            return Set.of("");
        }
        Set<String> out = new TreeSet<>();
        for (String option : ingredient.split("\\|")) {
            if (option.startsWith("#")) {
                out.addAll(ClasspathData.itemTag(option.substring(1)));
            } else {
                out.add(option);
            }
        }
        return out;
    }

    private String rawSignature() {
        StringBuilder out = new StringBuilder("shaped ").append(this.width).append('x').append(this.height).append(' ');
        for (int r = 0; r < this.height; r++) {
            if (r > 0) {
                out.append(';');
            }
            for (int c = 0; c < this.width; c++) {
                if (c > 0) {
                    out.append(',');
                }
                String cell = this.cells.get(r * this.width + c);
                out.append(cell.isEmpty() ? "-" : cell);
            }
        }
        return out.toString();
    }
}
