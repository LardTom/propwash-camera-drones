package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CleanupStep {
    public static final String NAME = "aufräumen";
    private static final String PROBLEMS = "aufräumen.problems";

    private CleanupStep() {
    }

    public static void register() {
        SelfTest.baseStep(20, NAME, 600, Script.create()
                .server("Welt prüfen", CleanupStep::checkServer)
                .then("Client prüfen", (ctx, ticks) -> checkClient(ctx)));
    }

    private static void checkServer(MinecraftServer server, Map<String, Object> state) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = Arena.player(server);
        int drones = Arena.drones(player.level()).size();
        if (drones > 0) {
            problems.add(drones + " Drohnen in der Arena");
        }
        if (Arena.changedBlocks() > 0) {
            problems.add(Arena.changedBlocks() + " gesetzte Blöcke nicht zurückgesetzt");
        }
        for (WorldRule<?> rule : CamWorldRules.registered()) {
            Object value = rule.get(player.level());
            if (!rule.defaultValue().equals(value)) {
                problems.add("Welt-Regel " + rule.id() + " ist " + value + " statt " + rule.defaultValue());
            }
        }
        String wind = Arena.gameRule(server, Arena.WIND);
        if (!String.valueOf(Arena.CALM_WIND).equals(wind)) {
            problems.add("Wind " + wind + " statt " + Arena.CALM_WIND);
        }
        if (!player.getInventory().isEmpty()) {
            problems.add("Inventar nicht leer");
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                problems.add("Ausrüstung " + slot.getName() + " nicht leer");
            }
        }
        state.put(PROBLEMS, problems);
    }

    @SuppressWarnings("unchecked")
    private static StepResult checkClient(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        List<String> problems = new ArrayList<>((List<String>) ctx.state().getOrDefault(PROBLEMS, List.of()));
        if (PropwashClientApi.input().flying()) {
            problems.add("Flug läuft noch");
        }
        if (mc.gui.screen() != null) {
            problems.add("Fenster offen: " + mc.gui.screen().getClass().getSimpleName());
        }
        if (!Shots.ENGLISH.equals(mc.getLanguageManager().getSelected())) {
            problems.add("Sprache " + mc.getLanguageManager().getSelected());
        }
        for (KeyMapping key : mc.options.keyMappings) {
            if (key.isDown()) {
                problems.add("Taste gedrückt: " + key.getName());
            }
        }
        if (!Arena.clientDrones(mc).isEmpty()) {
            problems.add(Arena.clientDrones(mc).size() + " Drohnen am Client");
        }
        String deflection = KeyboardDeflection.saved();
        if (!deflection.equals("0.6")) {
            problems.add("Tastatur-Ausschlag von Propwash " + deflection + " statt 0.6");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass("Arena leer, Welt-Regeln auf Standard, Wind " + Arena.CALM_WIND + ", Inventar leer, Sprache Englisch, keine Taste gedrückt, Tastatur-Ausschlag 60 %");
    }
}
