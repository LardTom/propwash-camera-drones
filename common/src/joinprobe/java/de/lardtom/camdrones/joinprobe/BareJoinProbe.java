package de.lardtom.camdrones.joinprobe;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BareJoinProbe {
    public static final String TARGET = "CAMDRONES_JOINTEST";
    public static final String LABEL = "CAMDRONES_JOINTEST_LABEL";
    public static final String EXPECT = "CAMDRONES_JOINTEST_EXPECT";
    public static final String RESULT_FILE = "camdrones-selftest-result.txt";
    private static final Logger LOG = LoggerFactory.getLogger("Camera Drones Join Probe");
    private static final String TAG = "[Kamera-Beitrittstest ohne Add-on]";
    private static final int STABLE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 150;
    private static int ticks;
    private static int phase;
    private static String screenKey = "";
    private static int screenSince;
    private static String text = "";
    private static CompletableFuture<String> shot = CompletableFuture.completedFuture("");
    private static boolean leaving;

    private BareJoinProbe() {
    }

    public static boolean enabled() {
        String target = System.getenv(TARGET);
        return target != null && !target.isBlank();
    }

    public static void tick(Minecraft mc) {
        if (!enabled() || phase >= 3) {
            return;
        }
        ticks++;
        try {
            switch (phase) {
                case 0 -> {
                    if (mc.gui.screen() instanceof TitleScreen && ticks > 10) {
                        String target = System.getenv(TARGET).trim();
                        LOG.info("{} verbinde mit {}", TAG, target);
                        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                                new ServerData("Camdrones join test", target, ServerData.Type.OTHER), false, null);
                        phase = 1;
                        ticks = 0;
                    }
                }
                case 1 -> watch(mc);
                case 2 -> {
                    if (!shot.isDone() && ticks < 200) {
                        return;
                    }
                    if (mc.level != null) {
                        if (!leaving) {
                            leaving = true;
                            mc.disconnectFromWorld(Component.literal("Kamera-Beitrittstest beendet"));
                        }
                        return;
                    }
                    finish(mc);
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            LOG.error("{} Fehler", TAG, e);
            text = text + "\nAusnahme " + e;
            finish(mc);
        }
    }

    private static boolean transientScreen(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof GenericMessageScreen || screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen;
    }

    private static void watch(Minecraft mc) {
        Screen screen = mc.gui.screen();
        boolean joined = mc.level != null && mc.player != null && screen == null;
        boolean settled = joined || mc.level == null && screen != null && !transientScreen(screen);
        String key = joined ? "world" : screen == null ? "none" : screen.getClass().getName();
        if (!key.equals(screenKey)) {
            screenKey = key;
            screenSince = ticks;
            return;
        }
        if (ticks > TIMEOUT_TICKS) {
            text = "timeout\n" + describe(mc, screen);
            phase = 2;
            ticks = 0;
            return;
        }
        if (!settled || ticks - screenSince < STABLE_TICKS) {
            return;
        }
        text = joined ? "joined\n" : describe(mc, screen);
        LOG.info("{} {}", TAG, text.replace("\n", " | "));
        shot = capture(dir().resolve(label() + ".png"));
        phase = 2;
        ticks = 0;
    }

    private static String describe(Minecraft mc, Screen screen) {
        if (screen == null) {
            return "kein Screen\n";
        }
        Set<String> out = new LinkedHashSet<>();
        out.add("screen=" + screen.getClass().getName());
        out.add("title=" + screen.getTitle().getString());
        List<AbstractWidget> widgets = new ArrayList<>();
        collect(screen, widgets, 0);
        for (AbstractWidget widget : widgets) {
            out.add("widget=" + widget.getMessage().getString());
        }
        try {
            Texts texts = new Texts(mc);
            screen.extractRenderStateWithTooltipAndSubtitles(texts, -1, -1, 0.0F);
            for (String value : texts.values) {
                out.add("text=" + value);
            }
        } catch (RuntimeException e) {
            out.add("texte nicht lesbar: " + e);
        }
        return String.join("\n", out) + "\n";
    }

    private static void collect(GuiEventListener node, List<AbstractWidget> out, int depth) {
        if (depth > 8) {
            return;
        }
        if (node instanceof AbstractWidget widget) {
            out.add(widget);
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                collect(child, out, depth + 1);
            }
        }
    }

    private static final class Texts extends GuiGraphicsExtractor {
        private final List<String> values = new ArrayList<>();

        private Texts(Minecraft mc) {
            super(mc, new GuiRenderState(), -1, -1);
        }

        @Override
        public void text(Font font, FormattedCharSequence sequence, int x, int y, int color, boolean dropShadow) {
            StringBuilder plain = new StringBuilder();
            sequence.accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            if (!plain.isEmpty()) {
                this.values.add(plain.toString());
            }
            super.text(font, sequence, x, y, color, dropShadow);
        }
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("jointest");
    }

    private static String label() {
        String value = System.getenv(LABEL);
        return value == null || value.isBlank() ? "noaddon" : value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static CompletableFuture<String> capture(Path file) {
        CompletableFuture<String> done = new CompletableFuture<>();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.complete("");
            return done;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
            } catch (Exception e) {
                LOG.warn("{} Bildschirmfoto fehlgeschlagen: {}", TAG, e.toString());
            }
            done.complete(file.toString());
        }));
        return done;
    }

    private static String unmet(String value) {
        String expect = System.getenv(EXPECT);
        if (expect == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String part : expect.split("\\|")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean negate = trimmed.startsWith("!");
            String needle = (negate ? trimmed.substring(1) : trimmed).toLowerCase(Locale.ROOT);
            if (lower.contains(needle) == negate) {
                return (negate ? "unerwartet: " : "fehlt: ") + needle;
            }
        }
        return null;
    }

    private static void finish(Minecraft mc) {
        phase = 3;
        String unmet = unmet(text);
        String result = unmet == null ? "PASS Beitrittstest " + label() + " ohne Add-on" : "FAIL Beitrittstest " + label() + " ohne Add-on: " + unmet;
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(label() + ".txt"), text + result + "\n", StandardCharsets.UTF_8);
            Files.writeString(mc.gameDirectory.toPath().resolve(RESULT_FILE), result + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("{} Ergebnis nicht geschrieben: {}", TAG, e.toString());
        }
        LOG.info("{} {}", TAG, result);
        mc.stop();
    }
}
