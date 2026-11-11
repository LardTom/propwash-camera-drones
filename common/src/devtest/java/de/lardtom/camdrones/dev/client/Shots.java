package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class Shots {
    public static final String PROPERTY = "camdrones.screenshots";
    public static final String ENGLISH = "en_us";
    public static final String GERMAN = "de_de";
    public static final int PHOTO_WIDTH = 1920;
    public static final int PHOTO_HEIGHT = 1080;
    public static final int PHOTO_GUI_SCALE = 3;
    private static final String DIR = System.getProperty(PROPERTY, "");
    private static final String SHOT = "shots.future";
    private static final int SETTLE_TICKS = 8;
    private static final int WINDOW_TICKS = 80;
    private static int @Nullable [] savedWindow;
    private static @Nullable String savedLanguage;

    private Shots() {
    }

    public static boolean enabled() {
        return SelfTest.enabled() && !DIR.isBlank();
    }

    public static Path dir() {
        return Path.of(DIR);
    }

    public static void clearOverlays(Minecraft mc) {
        mc.gui.hud.getChat().clearMessages(false);
        mc.gui.hud.setOverlayMessage(Component.empty(), false);
        mc.gui.hud.clearTitles();
        mc.gui.toastManager().clear();
    }

    public static void parkMouse(Minecraft mc) {
        mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
    }

    public static CompletableFuture<String> capture(String name) {
        return capture(dir().resolve(name + ".png").normalize());
    }

    public static CompletableFuture<String> capture(Path file) {
        CompletableFuture<String> done = new CompletableFuture<>();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.completeExceptionally(e);
            return done;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        String size = target.width + "x" + target.height;
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(size);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }));
        return done;
    }

    public static StepResult shot(SelfTest.StepContext ctx, int ticks, String name) {
        if (!enabled()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Object pending = ctx.state().get(SHOT);
        if (pending == null) {
            parkMouse(mc);
        }
        if (ticks < SETTLE_TICKS) {
            if (ticks == 0) {
                clearOverlays(mc);
            }
            return StepResult.pending();
        }
        if (pending == null) {
            ctx.state().put(SHOT, capture(name));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<String> future = (CompletableFuture<String>) pending;
        if (!future.isDone()) {
            return ticks > 400 ? StepResult.fail("Bildschirmfoto " + name + " hängt") : StepResult.pending();
        }
        ctx.state().remove(SHOT);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + name + ": " + future.exceptionNow());
        }
        ctx.log("Bildschirmfoto " + dir().resolve(name + ".png") + " (" + future.join() + ")");
        return StepResult.pass();
    }

    public static Script photos(String name) {
        return photos(name, () -> {
        });
    }

    public static Script photos(String name, Runnable refresh) {
        if (!enabled()) {
            return Script.create();
        }
        return Script.create()
                .then("Bildschirmfoto " + name, (ctx, ticks) -> shot(ctx, ticks, name))
                .run("Sprache Deutsch", () -> {
                    language(Minecraft.getInstance(), GERMAN);
                    refresh.run();
                })
                .then("Bildschirmfoto " + name + "_de", (ctx, ticks) -> shot(ctx, ticks, name + "_de"))
                .run("Sprache zurück", () -> {
                    restoreLanguage(Minecraft.getInstance());
                    refresh.run();
                });
    }

    public static void language(Minecraft mc, String code) {
        if (savedLanguage == null) {
            savedLanguage = mc.getLanguageManager().getSelected();
        }
        apply(mc, code);
    }

    public static void restoreLanguage(Minecraft mc) {
        String saved = savedLanguage;
        if (saved == null) {
            return;
        }
        savedLanguage = null;
        apply(mc, saved);
    }

    private static void apply(Minecraft mc, String code) {
        if (code.equals(mc.getLanguageManager().getSelected())) {
            return;
        }
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        if (mc.gui.screen() != null) {
            mc.resizeGui();
        }
    }

    public static StepResult window(SelfTest.StepContext ctx, int ticks, int width, int height, int scale) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (savedWindow == null) {
            savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
        }
        if (ticks == 0) {
            float density = Math.max(1.0F, window.getPixelDensity());
            window.setWindowed(Math.round(width / density), Math.round(height / density));
            return StepResult.pending();
        }
        boolean sized = window.getWidth() == width && window.getHeight() == height;
        if (!sized && ticks < WINDOW_TICKS) {
            return StepResult.pending();
        }
        if (!sized) {
            return StepResult.fail("Fenster wird nicht " + width + "x" + height + ", sondern " + window.getWidth() + "x" + window.getHeight());
        }
        mc.options.guiScale().set(scale);
        mc.resizeGui();
        return StepResult.pass();
    }

    public static Script photoWindow() {
        if (!enabled()) {
            return Script.create();
        }
        return Script.create().then("Fenster für Bildschirmfotos", (ctx, ticks) -> window(ctx, ticks, PHOTO_WIDTH, PHOTO_HEIGHT, PHOTO_GUI_SCALE));
    }

    public static void restoreWindow(Minecraft mc) {
        int[] saved = savedWindow;
        if (saved == null) {
            return;
        }
        savedWindow = null;
        mc.options.guiScale().set(saved[2]);
        mc.getWindow().setWindowed(saved[0], saved[1]);
        mc.resizeGui();
    }
}
