package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BudgetSteps {
    public static final int NUMBER = 631;
    public static final String NAME = "welt-regeln: kein Budget – Aktionsleiste bleibt bei Propwash, Chat nennt die Welt-Regeln";
    public static final List<List<String>> RULES = List.of(
            List.of("Autonomous flights per player at once", "Autonomous flights in total"),
            List.of("Autonome Flüge je Spieler gleichzeitig", "Autonome Flüge gesamt"));
    private static final int DX = 48;
    private static final int DZ = -24;
    private static final int GAP = 8;
    private static final List<Component> CHAT = new CopyOnWriteArrayList<>();

    private BudgetSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, 20 * 90, Script.create()
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX, DZ, 0.0F))
                .then("erster Flug: Halten anfordern", (ctx, ticks) -> CamFlights.send(ctx, hold(ctx.state())))
                .then("erster Flug läuft", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.HOLD_STARTED, 100))
                .then("erster Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "hold"))
                .append(CamFlights.groundDrone(CamPresets.TOUR, DX + GAP, DZ, 0.0F))
                .then("Aktionsleiste leeren, Chat mitlesen", (ctx, ticks) -> {
                    listen();
                    CovCamAKit.clearOverlay();
                    return StepResult.pass();
                })
                .then("zweiter Flug: Halten anfordern", (ctx, ticks) -> CamFlights.send(ctx, hold(ctx.state())))
                .then("kein Budget", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.NO_BUDGET, 100))
                .then("Aktionsleiste und Chat", BudgetSteps::check), BudgetSteps::cleanup);
    }

    private static SmartMode.Request hold(Map<String, Object> state) {
        Entity drone = CamFlights.clientDrone(state).orElseThrow(() -> new IllegalStateException("Drohne fehlt am Client"));
        return new SmartMode.Request(CamFlights.serial(state), SmartState.hold(drone.getX(), drone.getY(), drone.getZ()));
    }

    private static StepResult check(SelfTest.StepContext ctx, int ticks) {
        Optional<String> overlay = CovCamAKit.overlayKey();
        if (overlay.filter(AutonomyStatus.NO_BUDGET.key()::equals).isPresent()) {
            return StepResult.fail("Add-on überschreibt Propwashs Meldung in der Aktionsleiste: „" + CovCamAKit.overlayText() + "“");
        }
        Optional<Component> line = CHAT.stream().filter(BudgetSteps::noBudget).reduce((first, second) -> second);
        if (line.isEmpty()) {
            return ticks > 20 ? StepResult.fail("keine Zeile „kein Budget“ im Chat") : StepResult.pending();
        }
        String text = line.get().getString();
        if (RULES.stream().noneMatch(names -> names.stream().allMatch(text::contains))) {
            return StepResult.fail("Chat nennt die Welt-Regeln nicht: „" + text + "“");
        }
        if (withoutDrone(line.get()).matches(".*\\d.*")) {
            return StepResult.fail("Chat nennt eine Zahl, die das Add-on nicht kennt: „" + text + "“");
        }
        return StepResult.pass("Aktionsleiste " + overlay.map(key -> "„" + CovCamAKit.overlayText() + "“ (" + key + ")").orElse("leer")
                + ", Chat „" + text + "“");
    }

    private static String withoutDrone(Component component) {
        String text = component.getString();
        if (component.getContents() instanceof TranslatableContents contents && contents.getArgs().length > 0) {
            Object drone = contents.getArgs()[0];
            String label = drone instanceof Component name ? name.getString() : String.valueOf(drone);
            return label.isEmpty() ? text : text.replace(label, "");
        }
        return text;
    }

    private static boolean noBudget(Component component) {
        return component.getContents() instanceof TranslatableContents contents && contents.getKey().equals(AutonomyStatus.NO_BUDGET.key());
    }

    private static void listen() {
        Minecraft.getInstance().gui.hud.getChat().setVisibleMessageFilter(message -> {
            CHAT.add(message.content());
            return true;
        });
        CHAT.clear();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Minecraft.getInstance().gui.hud.getChat().setVisibleMessageFilter(message -> true);
        CHAT.clear();
        CovCamAKit.clearOverlay();
        return StepResult.pass();
    }
}
