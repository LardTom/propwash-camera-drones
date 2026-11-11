package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.CamDronesClient;
import de.lardtom.camdrones.dev.DevResult;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SelfTestRunner {
    public static final String WORLD_NAME = "Camdrones Selftest";
    public static final String REPORT_FILE = "camdrones-selftest-steps.txt";
    private static final String TAG = "[Kamera-Selbsttest]";
    private static final long WATCHDOG_MILLIS = 40L * 60L * 1000L;
    private static final long SMOKE_WATCHDOG_MILLIS = 5L * 60L * 1000L;
    private static final int SETTLE_TICKS = 60;
    private static final int TITLE_TIMEOUT_TICKS = 20 * 90;
    private static final int WORLD_TIMEOUT_TICKS = 20 * 180;
    private static final int LEAVE_TIMEOUT_TICKS = 20 * 60;
    private static final int CLEANUP_TICKS = 20 * 30;

    private enum Phase { WAIT_TITLE, WAIT_WORLD, SETTLE, RUN, WAIT_LEFT, FINISH, DONE }

    private enum StepPhase { BODY, CLEANUP, RESET }

    private static Phase phase = Phase.WAIT_TITLE;
    private static int phaseTicks;
    private static List<SelfTest.Step> steps = List.of();
    private static int stepIndex;
    private static int stepTicks;
    private static StepPhase stepPhase = StepPhase.BODY;
    private static @Nullable StepResult stepResult;
    private static Map<String, Object> stepState = SelfTest.newState();
    private static Map<String, Object> cleanupState = SelfTest.newState();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int passed;
    private static volatile boolean finished;
    private static boolean smoke;
    private static boolean installed;
    private static long started;

    private SelfTestRunner() {
    }

    public static void install() {
        if (SelfTest.enabled()) {
            start(false);
        }
    }

    public static void installSmoke() {
        if (SelfTest.smokeEnabled()) {
            start(true);
        }
    }

    public static boolean running() {
        return phase == Phase.RUN;
    }

    private static void start(boolean smokeMode) {
        if (installed) {
            return;
        }
        installed = true;
        smoke = smokeMode;
        started = System.currentTimeMillis();
        CamDrones.LOG.info("{} {} aktiv ({}), Auswahl: {}", TAG, smoke ? "Rauchtest" : "Selbsttest", Services.PLATFORM.name(),
                SelfTest.filter() == null ? "alle Schritte" : SelfTest.filter());
        CamDronesClient.devTick(SelfTestRunner::tick);
        long timeout = smoke ? SMOKE_WATCHDOG_MILLIS : WATCHDOG_MILLIS;
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                return;
            }
            if (!finished) {
                String where = phase == Phase.RUN && stepIndex < steps.size() ? " in Schritt " + steps.get(stepIndex).name() : "";
                CamDrones.LOG.error("{} FAIL Watchdog: Client hängt (Phase {}{})", marker(), phase, where);
                DevResult.write("FAIL Watchdog: Client hängt in Phase " + phase + where);
                Runtime.getRuntime().halt(3);
            }
        }, "Camdrones-Selftest-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static String marker() {
        return smoke ? "CAMDRONES SMOKE" : "CAMDRONES SELFTEST";
    }

    private static void tick(Minecraft mc) {
        phaseTicks++;
        try {
            switch (phase) {
                case WAIT_TITLE -> {
                    if (mc.gui.screen() instanceof TitleScreen && phaseTicks > 10) {
                        createWorld(mc);
                        setPhase(Phase.WAIT_WORLD);
                    } else if (phaseTicks > TITLE_TIMEOUT_TICKS) {
                        FAILURES.add("Titelbildschirm nicht erreicht, aktueller Screen: " + screenName(mc));
                        setPhase(Phase.FINISH);
                    }
                }
                case WAIT_WORLD -> {
                    Screen screen = mc.gui.screen();
                    if (screen instanceof ConfirmScreen confirm && phaseTicks % 20 == 0) {
                        CamDrones.LOG.info("{} bestätige {}", TAG, confirm.getTitle().getString());
                        pressButton(confirm, CommonComponents.GUI_YES.getString());
                    }
                    if (mc.player != null && mc.level != null && screen == null) {
                        CamDrones.LOG.info("{} Welt geladen nach {} Ticks", TAG, phaseTicks);
                        setPhase(Phase.SETTLE);
                    } else if (phaseTicks > WORLD_TIMEOUT_TICKS) {
                        FAILURES.add("Welt nicht geladen (Screen: " + screenName(mc) + ")");
                        leaveWorld(mc);
                    }
                }
                case SETTLE -> {
                    if (phaseTicks >= SETTLE_TICKS && smoke) {
                        CamDrones.LOG.info("{} Rauchtest: Welt betreten, keine Schritte", TAG);
                        leaveWorld(mc);
                    } else if (phaseTicks >= SETTLE_TICKS) {
                        Arena.remember(mc);
                        MinecraftServer server = mc.getSingleplayerServer();
                        if (server == null) {
                            throw new IllegalStateException("kein integrierter Server für die Arena");
                        }
                        server.executeBlocking(() -> Arena.calmWind(server));
                        steps = SelfTest.startSteps();
                        CamDrones.LOG.info("{} starte {} von {} Schritten", TAG, steps.size(), SelfTest.all().size());
                        startStep(0);
                        setPhase(Phase.RUN);
                    }
                }
                case RUN -> {
                    if (runStep(mc)) {
                        leaveWorld(mc);
                    }
                }
                case WAIT_LEFT -> {
                    if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && phaseTicks > 5) {
                        CamDrones.LOG.info("{} Welt verlassen nach {} Ticks", TAG, phaseTicks);
                        setPhase(Phase.FINISH);
                    } else if (phaseTicks > LEAVE_TIMEOUT_TICKS) {
                        FAILURES.add("Welt nicht verlassen (Screen: " + screenName(mc) + ")");
                        setPhase(Phase.FINISH);
                    }
                }
                case FINISH -> finish(mc);
                case DONE -> {
                }
            }
        } catch (Throwable t) {
            CamDrones.LOG.error("{} Fehler im Läufer (Phase {})", TAG, phase, t);
            if (phase.ordinal() < Phase.WAIT_LEFT.ordinal()) {
                FAILURES.add("Läufer-Fehler in Phase " + phase + ": " + t);
                leaveWorld(mc);
            } else if (phase.ordinal() < Phase.FINISH.ordinal()) {
                FAILURES.add("Läufer-Fehler in Phase " + phase + ": " + t);
                setPhase(Phase.FINISH);
            } else if (phase != Phase.DONE) {
                setPhase(Phase.DONE);
                mc.stop();
            }
        }
    }

    private static void leaveWorld(Minecraft mc) {
        setPhase(Phase.WAIT_LEFT);
        if (mc.level != null) {
            CamDrones.LOG.info("{} verlasse die Welt", TAG);
            mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Kamera-Selbsttest beendet")));
        }
    }

    private static void createWorld(Minecraft mc) {
        deleteOldWorlds(mc);
        CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
        if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) {
            throw new IllegalStateException("CreateWorldScreen wurde nicht geöffnet, sondern " + screenName(mc));
        }
        WorldCreationUiState ui = screen.getUiState();
        ui.setName(WORLD_NAME);
        Holder<WorldPreset> flat = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
        ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
        ui.setSeed("1");
        ui.setGenerateStructures(false);
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        ui.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
        ui.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
        ui.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
        CamDrones.LOG.info("{} lege Welt '{}' an", TAG, ui.getTargetFolder());
        if (!pressButton(screen, Component.translatable("selectWorld.create").getString())) {
            throw new IllegalStateException("Knopf 'selectWorld.create' nicht gefunden");
        }
    }

    private static void deleteOldWorlds(Minecraft mc) {
        Path saves = mc.getLevelSource().getBaseDir();
        if (!Files.isDirectory(saves)) {
            return;
        }
        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(p -> p.getFileName().toString().startsWith(WORLD_NAME)).toList()) {
                try (Stream<Path> files = Files.walk(world)) {
                    for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        } catch (IOException e) {
            CamDrones.LOG.warn("{} alte Welten nicht gelöscht: {}", TAG, e.toString());
        }
    }

    static boolean pressButton(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    static boolean clickWidget(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.active && text.equals(widget.getMessage().getString())) {
                MouseButtonEvent event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                boolean handled = screen.mouseClicked(event, false);
                screen.mouseReleased(event);
                return handled;
            }
        }
        return false;
    }

    private static void startStep(int index) {
        stepIndex = index;
        stepTicks = 0;
        stepPhase = StepPhase.BODY;
        stepResult = null;
        stepState = SelfTest.newState();
        cleanupState = SelfTest.newState();
        if (index < steps.size()) {
            SelfTest.Step step = steps.get(index);
            CamDrones.LOG.info("{} Schritt {}/{} (Nr. {}): {}", TAG, index + 1, steps.size(), step.number(), step.name());
        }
    }

    private static boolean runStep(Minecraft mc) {
        if (stepIndex >= steps.size()) {
            return true;
        }
        SelfTest.Step step = steps.get(stepIndex);
        MinecraftServer server = mc.getSingleplayerServer();
        switch (stepPhase) {
            case BODY -> {
                StepResult result = call(step.body(), new Context(stepTicks, server, stepState, step.name()), step.name());
                stepTicks++;
                if (!result.done() && stepTicks >= step.maxTicks()) {
                    result = StepResult.fail("Zeitüberschreitung nach " + step.maxTicks() + " Ticks");
                }
                if (result.done()) {
                    stepResult = result;
                    REPORT.add((result.failed() ? "FAIL " : "PASS ") + step.number() + " " + step.name() + " (" + stepTicks + " Ticks)"
                            + (result.message().isEmpty() ? "" : ": " + result.message()));
                    if (result.failed()) {
                        FAILURES.add(step.name() + ": " + result.message());
                        CamDrones.LOG.error("{} FAIL {}: {}", TAG, step.name(), result.message());
                    } else {
                        passed++;
                        CamDrones.LOG.info("{} PASS {} ({} Ticks){}", TAG, step.name(), stepTicks,
                                result.message().isEmpty() ? "" : ": " + result.message());
                    }
                    stepPhase = StepPhase.CLEANUP;
                    stepTicks = 0;
                }
            }
            case CLEANUP -> {
                StepResult result = call(step.cleanup(), new Context(stepTicks, server, cleanupState, step.name() + " – aufräumen"), step.name());
                stepTicks++;
                if (result.done() || stepTicks >= CLEANUP_TICKS) {
                    if (!result.done() || result.failed()) {
                        String why = result.done() ? result.message() : "Zeitüberschreitung";
                        FAILURES.add(step.name() + ": Aufräumen des Schritts fehlgeschlagen: " + why);
                        REPORT.add("FAIL " + step.number() + " " + step.name() + ": Aufräumen des Schritts: " + why);
                    }
                    stepPhase = StepPhase.RESET;
                    stepTicks = 0;
                    cleanupState = SelfTest.newState();
                }
            }
            case RESET -> {
                StepResult result = call(Arena::reset, new Context(stepTicks, server, cleanupState, step.name() + " – Arena"), step.name());
                stepTicks++;
                if (result.done() || stepTicks >= CLEANUP_TICKS) {
                    if (!result.done() || result.failed()) {
                        String why = result.done() ? result.message() : "Zeitüberschreitung";
                        FAILURES.add(step.name() + ": Aufräumen nach dem Schritt fehlgeschlagen: " + why);
                        REPORT.add("FAIL " + step.number() + " " + step.name() + ": Aufräumen danach: " + why);
                    }
                    startStep(stepIndex + 1);
                }
            }
        }
        return stepIndex >= steps.size();
    }

    private static StepResult call(SelfTest.ClientStep body, Context context, String name) {
        try {
            return body.tick(context);
        } catch (Throwable t) {
            CamDrones.LOG.error("{} Schritt '{}' warf", TAG, name, t);
            return StepResult.fail("Ausnahme: " + t);
        }
    }

    private static void finish(Minecraft mc) {
        finished = true;
        String result;
        long seconds = (System.currentTimeMillis() - started) / 1000L;
        if (smoke && FAILURES.isEmpty()) {
            result = "PASS";
            CamDrones.LOG.info("CAMDRONES SMOKE PASS (Welt angelegt, betreten und verlassen, {})", Services.PLATFORM.name());
        } else if (!smoke && FAILURES.isEmpty() && passed > 0) {
            result = "PASS " + passed + " Schritte (" + Services.PLATFORM.name() + ", " + seconds + " s)";
            CamDrones.LOG.info("CAMDRONES SELFTEST PASS ({} Schritte, {}, {} s)", passed, Services.PLATFORM.name(), seconds);
        } else {
            if (!smoke && passed == 0 && FAILURES.isEmpty()) {
                FAILURES.add("keine Schritte ausgeführt");
            }
            result = "FAIL " + String.join(" | ", FAILURES);
            CamDrones.LOG.error("{} {} ({})", marker(), result, Services.PLATFORM.name());
        }
        if (!smoke) {
            writeReport(mc, result);
        }
        DevResult.write(result);
        setPhase(Phase.DONE);
        CamDrones.LOG.info("{} beende das Spiel", TAG);
        mc.stop();
    }

    private static void writeReport(Minecraft mc, String result) {
        List<String> lines = new ArrayList<>();
        lines.add("Kamera-Drohnen-Selbsttest (" + Services.PLATFORM.name() + ")");
        lines.add("Auswahl: " + (SelfTest.filter() == null ? "alle Schritte" : SelfTest.filter()));
        lines.addAll(REPORT);
        lines.add(result);
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(REPORT_FILE), String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.warn("{} Schrittliste nicht geschrieben: {}", TAG, e.toString());
        }
    }

    private static void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    static String screenName(Minecraft mc) {
        Screen s = mc.gui.screen();
        return s == null ? "keiner" : s.getClass().getName();
    }

    private record Context(int tick, @Nullable MinecraftServer server, Map<String, Object> state, String name) implements SelfTest.StepContext {
        @Override
        public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
            MinecraftServer s = this.server;
            if (s == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("kein integrierter Server"));
            }
            return s.submit(() -> action.accept(s)).whenComplete((v, t) -> {
                if (t != null) {
                    CamDrones.LOG.error("{} Server-Aktion in '{}' fehlgeschlagen", TAG, this.name, t);
                }
            });
        }

        @Override
        public void log(String message) {
            CamDrones.LOG.info("{} {}: {}", TAG, this.name, message);
        }
    }
}
