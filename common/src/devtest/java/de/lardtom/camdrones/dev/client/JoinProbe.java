package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.CamDronesClient;
import de.lardtom.camdrones.dev.DevResult;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class JoinProbe {
    public static final String TAG = "[Kamera-Beitrittstest]";
    private static final int STABLE_TICKS = 30;
    private static final int CONNECT_TICKS = 20 * 150;
    private static final long WATCHDOG_MILLIS = 20L * 60L * 1000L;

    private enum Phase { WAIT_TITLE, CONNECTING, CASE, LEAVING, DONE, REJOIN_LEAVE, REJOIN_WAIT, REJOINING }

    private static Phase phase = Phase.WAIT_TITLE;
    private static int phaseTicks;
    private static int caseTicks;
    private static String screenKey = "";
    private static int screenSince;
    private static final List<String> LINES = new ArrayList<>();
    private static final Map<String, Object> STATE = SelfTest.newState();
    private static @Nullable Script script;
    private static @Nullable String failure;
    private static @Nullable CompletableFuture<String> shot;
    private static boolean installed;
    private static boolean leaving;
    private static volatile boolean finished;
    private static int rejoinTicks = -1;
    private static boolean rejoinLeaving;

    private JoinProbe() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        CamDrones.LOG.info("{} verbinde mit {} ({}), Fall {}, Erwartung {}", TAG, JoinEnv.target(), Services.PLATFORM.name(), JoinEnv.serverCase(),
                JoinEnv.expectations());
        CamDronesClient.devTick(JoinProbe::tick);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(WATCHDOG_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            if (!finished) {
                DevResult.write("FAIL Watchdog: Beitrittstest hängt in Phase " + phase);
                Runtime.getRuntime().halt(3);
            }
        }, "Camdrones-Jointest-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static void line(String text) {
        LINES.add(text);
        CamDrones.LOG.info("{} {}", TAG, text);
    }

    public static void rejoin(int delayTicks) {
        rejoinTicks = Math.max(0, delayTicks);
    }

    private static void connect(Minecraft mc) {
        String target = JoinEnv.target();
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                new ServerData("Camdrones join test", target, ServerData.Type.OTHER), false, null);
    }

    private static void tick(Minecraft mc) {
        phaseTicks++;
        try {
            switch (phase) {
                case WAIT_TITLE -> {
                    if (mc.gui.screen() instanceof TitleScreen && phaseTicks > 10) {
                        connect(mc);
                        next(Phase.CONNECTING);
                    } else if (phaseTicks > 20 * 120) {
                        failure = "Titelbildschirm nicht erreicht";
                        finish(mc, "kein Titelbildschirm\n");
                    }
                }
                case CONNECTING -> connecting(mc);
                case CASE -> runCase(mc);
                case REJOIN_LEAVE -> rejoinLeave(mc);
                case REJOIN_WAIT -> rejoinWait(mc);
                case REJOINING -> rejoining(mc);
                case LEAVING -> {
                    if (shot != null && !shot.isDone() && phaseTicks < 200) {
                        return;
                    }
                    if (mc.level != null) {
                        if (!leaving) {
                            leaving = true;
                            mc.disconnectFromWorld(Component.literal("Kamera-Beitrittstest beendet"));
                        }
                        if (phaseTicks > 600) {
                            failure = "Welt nicht verlassen";
                            finish(mc, null);
                        }
                        return;
                    }
                    finish(mc, null);
                }
                case DONE -> {
                }
            }
        } catch (Throwable t) {
            CamDrones.LOG.error("{} Fehler in Phase {}", TAG, phase, t);
            failure = "Ausnahme in Phase " + phase + ": " + t;
            if (phase != Phase.DONE) {
                finish(mc, null);
            }
        }
    }

    private static void next(Phase value) {
        phase = value;
        phaseTicks = 0;
    }

    private static boolean transientScreen(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof GenericMessageScreen || screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen;
    }

    private static void connecting(Minecraft mc) {
        Screen screen = mc.gui.screen();
        boolean joined = mc.level != null && mc.player != null && screen == null;
        boolean settled = joined || mc.level == null && screen != null && !transientScreen(screen);
        String key = joined ? "world" : screen == null ? "none" : screen.getClass().getName();
        if (!key.equals(screenKey)) {
            screenKey = key;
            screenSince = phaseTicks;
            return;
        }
        if (phaseTicks > CONNECT_TICKS) {
            failure = "keine Antwort vom Server";
            finish(mc, describe(mc, screen));
            return;
        }
        if (!settled || phaseTicks - screenSince < STABLE_TICKS) {
            return;
        }
        if (!joined) {
            String text = describe(mc, screen);
            shot = Shots.capture(dir().resolve(JoinEnv.label() + ".png"));
            LINES.add(text);
            next(Phase.LEAVING);
            return;
        }
        line(JoinEnv.JOINED);
        script = JoinCases.script(JoinEnv.serverCase());
        caseTicks = 0;
        next(Phase.CASE);
    }

    private static void runCase(Minecraft mc) {
        Script active = script;
        if (active == null) {
            next(Phase.LEAVING);
            return;
        }
        if (mc.level == null) {
            failure = "Verbindung im Fall verloren (" + SelfTestRunner.screenName(mc) + ")";
            next(Phase.LEAVING);
            return;
        }
        StepResult result = active.tick(new Context(caseTicks++, STATE));
        if (!result.done() && rejoinTicks >= 0) {
            rejoinLeaving = false;
            next(Phase.REJOIN_LEAVE);
            return;
        }
        if (!result.done()) {
            return;
        }
        if (result.failed()) {
            failure = result.message();
            line("FAIL " + result.message());
        }
        Pilot.releaseKeys();
        shot = Shots.capture(dir().resolve(JoinEnv.label() + ".png"));
        next(Phase.LEAVING);
    }

    private static void rejoinLeave(Minecraft mc) {
        if (mc.level != null) {
            if (!rejoinLeaving) {
                rejoinLeaving = true;
                Pilot.releaseKeys();
                mc.disconnectFromWorld(Component.literal("Kamera-Beitrittstest: trennen und wieder beitreten"));
            }
            if (phaseTicks > 600) {
                failure = "Welt zum Wiederbeitritt nicht verlassen";
                rejoinTicks = -1;
                next(Phase.LEAVING);
            }
            return;
        }
        line("left for rejoin");
        next(Phase.REJOIN_WAIT);
    }

    private static void rejoinWait(Minecraft mc) {
        Screen screen = mc.gui.screen();
        boolean idle = mc.level == null && (screen == null || !transientScreen(screen));
        if (phaseTicks >= rejoinTicks && idle) {
            line("rejoin after " + phaseTicks + " ticks");
            rejoinTicks = -1;
            screenKey = "";
            connect(mc);
            next(Phase.REJOINING);
        } else if (phaseTicks > rejoinTicks + 20 * 60) {
            failure = "Wiederbeitritt startet nicht (" + SelfTestRunner.screenName(mc) + ")";
            rejoinTicks = -1;
            finish(mc, describe(mc, screen));
        }
    }

    private static void rejoining(Minecraft mc) {
        Screen screen = mc.gui.screen();
        boolean joined = mc.level != null && mc.player != null && screen == null;
        boolean settled = joined || mc.level == null && screen != null && !transientScreen(screen);
        String key = joined ? "world" : screen == null ? "none" : screen.getClass().getName();
        if (!key.equals(screenKey)) {
            screenKey = key;
            screenSince = phaseTicks;
            return;
        }
        if (phaseTicks > CONNECT_TICKS) {
            failure = "keine Antwort vom Server beim Wiederbeitritt";
            finish(mc, describe(mc, screen));
            return;
        }
        if (!settled || phaseTicks - screenSince < STABLE_TICKS) {
            return;
        }
        if (!joined) {
            failure = "Wiederbeitritt abgelehnt";
            finish(mc, describe(mc, screen));
            return;
        }
        line("rejoined");
        next(Phase.CASE);
    }

    static Path dir() {
        return Services.PLATFORM.gameDir().resolve("jointest");
    }

    private static String describe(Minecraft mc, @Nullable Screen screen) {
        if (screen == null) {
            return "kein Screen\n";
        }
        Set<String> texts = new LinkedHashSet<>();
        texts.add("screen=" + screen.getClass().getName());
        texts.add("title=" + screen.getTitle().getString());
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            texts.add("widget=" + widget.getMessage().getString());
        }
        try {
            for (String value : LayoutCheck.capture(mc, screen).values()) {
                texts.add("text=" + value);
            }
        } catch (RuntimeException e) {
            texts.add("texte nicht lesbar: " + e);
        }
        return String.join("\n", texts) + "\n";
    }

    private static void finish(Minecraft mc, @Nullable String extra) {
        if (finished) {
            return;
        }
        finished = true;
        if (extra != null) {
            LINES.add(extra);
        }
        String text = String.join("\n", LINES) + "\n";
        String unmet = JoinEnv.unmet(text);
        String result;
        if (failure == null && unmet == null) {
            result = "PASS Beitrittstest " + JoinEnv.label() + " (" + Services.PLATFORM.name() + ")";
        } else {
            result = "FAIL Beitrittstest " + JoinEnv.label() + ": " + (failure != null ? failure : unmet);
        }
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(JoinEnv.label() + ".txt"), text + result + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.warn("{} Ergebnis nicht geschrieben: {}", TAG, e.toString());
        }
        CamDrones.LOG.info("{} {}", TAG, result);
        DevResult.write(result);
        next(Phase.DONE);
        mc.stop();
    }

    private record Context(int tick, Map<String, Object> state) implements SelfTest.StepContext {
        @Override
        public String name() {
            return "Beitrittstest " + JoinEnv.label();
        }

        @Override
        public @Nullable MinecraftServer server() {
            return null;
        }

        @Override
        public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
            return CompletableFuture.failedFuture(new IllegalStateException("kein integrierter Server im Beitrittstest"));
        }

        @Override
        public void log(String message) {
            line(message);
        }
    }
}
