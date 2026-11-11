package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.school.CameraDroneScene;
import de.lardtom.camdrones.client.school.CamSchool;
import de.lardtom.camdrones.client.school.FirstMissionScene;
import de.lardtom.camdrones.client.school.FollowPoiScene;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.pad.PadBlocks;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.part.CamTab;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SchoolSteps {
    public static final String NAME = "flugschule: Kapitel der Kamera-Drohnen";
    private static final int PLAY_TICKS = 110;
    private static final int STEP_TICKS = 20 * 120;
    private static final int OBSTACLE_HOLD_TICKS = 12;
    private static final int OBSTACLE_MAX_TICKS = 400;
    private static final String OBSTACLE_SEEN = "school.obstacle.seen";

    private SchoolSteps() {
    }

    public static void register() {
        Script script = Script.create()
                .append(Shots.photoWindow())
                .check("Kapitel angemeldet und mit Items verknüpft", SchoolSteps::links);
        for (String id : CamSchool.SCENES) {
            String name = id.substring(id.indexOf(':') + 1);
            script.then("Kapitel " + name + " öffnen", (ctx, ticks) -> open(ticks, id))
                    .then("Kapitel " + name + " spielt", (ctx, ticks) -> playing(ticks, id))
                    .append(Shots.photos(SurvivalCraftSteps.PHOTO + "flight_school_" + name));
            if (id.equals(CameraDroneScene.ID)) {
                script.then("Kapitel " + name + " Schritt 5/5 wählen", (ctx, ticks) -> lastStep())
                        .then("Kapitel " + name + " hält am Hindernis-Stopp", SchoolSteps::obstacle)
                        .append(Shots.photos(SurvivalCraftSteps.PHOTO + "flight_school_" + name + "_obstacle"));
            }
            script.run("Kapitel schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                    .sleep(5);
        }
        SelfTest.clientStep(405, NAME, STEP_TICKS, script);
    }

    private static List<String> scenes(Identifier item) {
        Optional<Item> found = BuiltInRegistries.ITEM.getOptional(item);
        return found.map(value -> PropwashClientApi.flightSchoolScenes(new ItemStack(value))).orElse(List.of());
    }

    private static StepResult links() {
        Map<Identifier, List<String>> wanted = Map.of(
                PlannerItems.FLIGHT_PLANNER_ID, List.of(FirstMissionScene.ID, FollowPoiScene.ID),
                PadBlocks.LANDING_PAD_ID, List.of(FirstMissionScene.ID),
                CamPresets.part("nav_fc_pro"), List.of(CameraDroneScene.ID, FollowPoiScene.ID),
                CamPresets.part("tour_fold_frame"), List.of(CameraDroneScene.ID),
                CamTab.ICON, List.of(CameraDroneScene.ID, FollowPoiScene.ID));
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Map.Entry<Identifier, List<String>> entry : wanted.entrySet()) {
            List<String> scenes = scenes(entry.getKey());
            if (!scenes.containsAll(entry.getValue())) {
                problems.add(entry.getKey() + " bietet " + scenes + " statt " + entry.getValue());
            }
            notes.add(entry.getKey().getPath() + " → " + scenes.size() + " Kapitel");
        }
        for (String id : CamSchool.SCENES) {
            String prefix = "camdrones.guide.scene." + id.substring(id.indexOf(':') + 1) + ".";
            for (String lang : List.of("en_us", "de_de")) {
                if (PropwashApi.text(lang, prefix + "title").isEmpty()) {
                    problems.add(id + " ohne Titel in " + lang);
                }
            }
        }
        return problems.isEmpty() ? StepResult.pass("Flugschul-Taste: " + String.join(", ", notes)) : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult open(int ticks, String id) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            return PropwashClientApi.openFlightSchoolScene(id, null) ? StepResult.pending() : StepResult.fail("Kapitel " + id + " nicht angemeldet");
        }
        Screen screen = mc.gui.screen();
        if (screen != null && screen.getClass().getSimpleName().contains("Scene")) {
            return StepResult.pass(screen.getClass().getSimpleName() + " offen");
        }
        return ticks > 60 ? StepResult.fail("Kapitel öffnet nicht (" + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
    }

    private static StepResult lastStep() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return StepResult.fail("Kapitel geschlossen");
        }
        for (int i = 1; i < CameraDroneScene.STEPS.size(); i++) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        }
        return StepResult.pass((CameraDroneScene.STEPS.size() - 1) + "× Pfeil rechts");
    }

    private static String start(String text) {
        String[] words = text.split(" ");
        return words.length < 2 ? text : words[0] + " " + words[1];
    }

    private static StepResult obstacle(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Kapitel geschlossen");
        }
        String prefix = "camdrones.guide.scene.camera_drone.";
        String title = I18n.get(prefix + "step.obstacle.title");
        String callout = start(I18n.get(prefix + "callout.obstacle"));
        LayoutCheck.Frame frame;
        try {
            frame = LayoutCheck.capture(mc, screen);
        } catch (RuntimeException e) {
            return StepResult.fail("Schritt 5/5 lässt sich nicht zeichnen: " + e);
        }
        Object seen = ctx.state().get(OBSTACLE_SEEN);
        if (seen == null) {
            if (frame.shows(title) && frame.shows(callout)) {
                ctx.state().put(OBSTACLE_SEEN, ticks);
            } else if (ticks > OBSTACLE_MAX_TICKS) {
                return StepResult.fail("Schritt 5/5 ohne \"" + title + "\" und \"" + callout + "\", Texte " + frame.values());
            }
            return StepResult.pending();
        }
        if (ticks - (Integer) seen < OBSTACLE_HOLD_TICKS) {
            return StepResult.pending();
        }
        ctx.state().remove(OBSTACLE_SEEN);
        screen.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
        return StepResult.pass("Schritt \"" + title + "\" mit \"" + callout + "…\" angehalten, Drohne am Stopp");
    }

    private static StepResult playing(int ticks, String id) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Kapitel geschlossen");
        }
        if (ticks < PLAY_TICKS) {
            return StepResult.pending();
        }
        String title = I18n.get("camdrones.guide.scene." + id.substring(id.indexOf(':') + 1) + ".title");
        LayoutCheck.Frame frame;
        try {
            frame = LayoutCheck.capture(mc, screen);
        } catch (RuntimeException e) {
            return StepResult.fail("Kapitel lässt sich nicht zeichnen: " + e);
        }
        if (frame.shows(title)) {
            return StepResult.pass("Kapitel \"" + title + "\" läuft");
        }
        return ticks > PLAY_TICKS + 60 ? StepResult.fail("Titel \"" + title + "\" nicht im Bild, Texte " + frame.values()) : StepResult.pending();
    }
}
