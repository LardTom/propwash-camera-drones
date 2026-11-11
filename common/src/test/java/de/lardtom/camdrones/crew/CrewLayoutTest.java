package de.lardtom.camdrones.crew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.crew.client.CrewLayout;
import de.lardtom.camdrones.planner.model.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewLayoutTest {
    private static final int CHAR = 7;
    private static final int[][] SIZES = {{320, 240}, {640, 480}, {854, 480}};

    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/crew/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int width(JsonObject lang, String key) {
        return lang.get(key).getAsString().length() * CHAR;
    }

    private static CrewLayout layout(JsonObject lang, int w, int h, int players) {
        return CrewLayout.compute(w, h, players, width(lang, CrewTexts.SCREEN_INVITED) + 2, width(lang, CrewTexts.SCREEN_VIEW),
                width(lang, CrewTexts.SCREEN_RELEASE), width(lang, CrewTexts.SCREEN_CLOSE), 6 * CHAR);
    }

    @Test
    void everythingFitsInSmallAndLargeWindowsInBothLanguages() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = lang(language);
            for (int[] size : SIZES) {
                for (int players : new int[]{0, 1, 4, 12, 40}) {
                    CrewLayout l = layout(lang, size[0], size[1], players);
                    String where = language + " " + size[0] + "x" + size[1] + " mit " + players + ": ";
                    Rect screen = new Rect(0, 0, size[0], size[1]);
                    List<Rect> parts = new ArrayList<>(List.of(l.header(), l.title(), l.operatorLine(), l.viewButton(), l.releaseButton(),
                            l.nearbyTitle(), l.footer(), l.hint(), l.closeButton()));
                    if (!l.window().inside(screen)) {
                        problems.add(where + "Fenster ragt hinaus");
                    }
                    for (CrewLayout.Row row : l.rows()) {
                        parts.add(row.area());
                        parts.add(row.name());
                        parts.add(row.distance());
                        parts.add(row.button());
                        if (row.area().bottom() > l.footer().y()) {
                            problems.add(where + "Zeile im Fuß");
                        }
                        if (row.name().overlaps(row.distance()) || row.distance().overlaps(row.button())) {
                            problems.add(where + "Zeile überlappt");
                        }
                    }
                    for (Rect part : parts) {
                        if (!part.isEmpty() && !part.inside(l.window())) {
                            problems.add(where + "Teil außerhalb " + part);
                        }
                    }
                    if (l.viewButton().overlaps(l.releaseButton()) || l.hint().overlaps(l.closeButton())) {
                        problems.add(where + "Knöpfe überlappen");
                    }
                    if (l.rows().size() + l.hidden() != players) {
                        problems.add(where + "Zeilen fehlen");
                    }
                    if (l.hidden() > 0 && (l.more().isEmpty() || l.more().bottom() > l.footer().y())) {
                        problems.add(where + "Hinweis auf weitere fehlt");
                    }
                    if (players > 0 && l.rows().isEmpty()) {
                        problems.add(where + "keine Zeile sichtbar");
                    }
                    if (l.viewButton().w() < width(lang, CrewTexts.SCREEN_VIEW) + 4
                            || l.releaseButton().w() < width(lang, CrewTexts.SCREEN_RELEASE) + 4
                            || l.closeButton().w() < width(lang, CrewTexts.SCREEN_CLOSE) + 4) {
                        problems.add(where + "Knopftext passt nicht");
                    }
                    for (CrewLayout.Row row : l.rows()) {
                        if (row.button().w() < width(lang, CrewTexts.SCREEN_INVITED) + 4) {
                            problems.add(where + "Einladen-Text passt nicht");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void smallestGuiStillShowsSeveralPlayers() throws IOException {
        CrewLayout l = layout(lang("de_de"), 320, 240, 12);
        assertTrue(l.rows().size() >= 5, "Zeilen " + l.rows().size());
        assertEquals(12, l.rows().size() + l.hidden());
        CrewLayout big = layout(lang("en_us"), 854, 480, 12);
        assertEquals(12, big.rows().size());
        assertEquals(0, big.hidden());
    }
}
