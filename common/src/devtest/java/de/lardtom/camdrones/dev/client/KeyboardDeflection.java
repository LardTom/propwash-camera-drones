package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KeyboardDeflection {
    public static final String TAB = "receiver";
    public static final String LIMIT_KEY = "propwash.input.keyboard.limit";
    public static final String SAVE_KEY = "propwash.ui.button.save";
    public static final String CLOSE_KEY = "propwash.ui.button.close";
    public static final int DEFAULT_PERCENT = 60;
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 100;
    private static final String PREFIX = "deflection.";
    private static final Pattern SAVED = Pattern.compile("\"rollPitchLimit\"\\s*:\\s*([0-9.]+)");

    private KeyboardDeflection() {
    }

    public static Script set(int percent) {
        return Script.create()
                .then("Tab Empfänger öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, TAB))
                .then("Tastatur-Ausschlag " + percent + " %", (ctx, ticks) -> slide(ctx, ticks, percent))
                .then("speichern", KeyboardDeflection::save);
    }

    public static StepResult restore(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (PropwashClientApi.input().flying()) {
            Pilot.releaseKeys();
            if (ctx.tick() % 30 == 0) {
                ctx.onServer(Pilot::release);
            }
            return ctx.tick() > 400 ? StepResult.fail("Flug endet nicht vor dem Zurückstellen") : StepResult.pending();
        }
        if (!ctx.state().containsKey(PREFIX + "window")) {
            ctx.state().put(PREFIX + "window", ctx.tick());
            Shots.restoreWindow(mc);
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get(PREFIX + "window") < 10) {
            return StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault(PREFIX + "stage", 0);
        int since = (int) ctx.state().computeIfAbsent(PREFIX + "since", key -> ctx.tick());
        int ticks = ctx.tick() - since;
        StepResult result = switch (stage) {
            case 0 -> OsdSteps.openTab(ctx, ticks, TAB);
            case 1 -> slide(ctx, ticks, DEFAULT_PERCENT);
            case 2 -> save(ctx, ticks);
            default -> StepResult.pass();
        };
        if (!result.done()) {
            return result;
        }
        if (result.failed() || stage >= 2) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            return result;
        }
        ctx.state().put(PREFIX + "stage", stage + 1);
        ctx.state().put(PREFIX + "since", ctx.tick() + 1);
        return StepResult.pending();
    }

    static List<AbstractWidget> widgets(GuiEventListener root) {
        List<AbstractWidget> out = new ArrayList<>();
        collect(root, out, 0);
        return out;
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

    private static Optional<AbstractWidget> slider(Screen screen) {
        String probe = I18n.get(LIMIT_KEY, 12345);
        int cut = probe.indexOf("12345");
        String prefix = (cut > 0 ? probe.substring(0, cut) : probe).strip();
        if (prefix.isEmpty()) {
            return Optional.empty();
        }
        for (AbstractWidget widget : widgets(screen)) {
            if (widget.getMessage().getString().startsWith(prefix)) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    private static int percent(AbstractWidget widget) {
        String digits = widget.getMessage().getString().replaceAll("[^0-9]", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }

    private static void click(AbstractWidget widget, double x) {
        MouseButtonEvent event = new MouseButtonEvent(x, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        widget.mouseClicked(event, false);
        widget.mouseReleased(event);
    }

    private static StepResult slide(SelfTest.StepContext ctx, int ticks, int percent) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Empfänger geschlossen");
        }
        String wanted = I18n.get(LIMIT_KEY, percent);
        Optional<AbstractWidget> slider = slider(screen);
        if (slider.isEmpty()) {
            return ticks > 40 ? StepResult.fail("Regler „Tastatur-Ausschlag“ fehlt im Tab Empfänger") : StepResult.pending();
        }
        AbstractWidget widget = slider.get();
        double lo = widget.getX() + 1.0;
        double hi = widget.getX() + widget.getWidth() - 1.0;
        for (int i = 0; i < 24 && percent(widget) != percent; i++) {
            double mid = (lo + hi) / 2.0;
            click(widget, mid);
            int now = percent(widget);
            if (now < percent) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        if (percent(widget) != percent && percent == MAX_PERCENT) {
            click(widget, widget.getX() + widget.getWidth() - 1.0);
        }
        if (wanted.equals(widget.getMessage().getString())) {
            ctx.state().put(PREFIX + "changed", true);
            return StepResult.pass(wanted);
        }
        return ticks > 20 ? StepResult.fail("Regler bleibt bei „" + widget.getMessage().getString() + "“ statt „" + wanted + "“") : StepResult.pending();
    }

    private static StepResult save(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.pass();
        }
        if (ticks == 0) {
            for (String key : List.of(SAVE_KEY, CLOSE_KEY)) {
                String label = I18n.get(key);
                for (AbstractWidget widget : widgets(screen)) {
                    if (label.equals(widget.getMessage().getString()) && widget.visible && widget.active) {
                        click(widget, widget.getX() + widget.getWidth() / 2.0);
                        return StepResult.pending();
                    }
                }
            }
            mc.gui.setScreen(null);
            return StepResult.pending();
        }
        return ticks > 60 ? StepResult.fail("Einstellungen schließen nicht nach dem Speichern") : StepResult.pending();
    }

    public static String saved() {
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("propwash").resolve("input.json");
        try {
            Matcher matcher = SAVED.matcher(Files.readString(file, StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1) : "fehlt";
        } catch (IOException e) {
            return "nicht lesbar";
        }
    }
}
