package de.lardtom.camdrones.dev.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class LayoutCheck {
    public static final int SLACK = 1;
    public static final int SIDE_SLACK = 2;
    private static final int OVERLAP_MIN = 2;
    private static final int OVERLAP_MIN_WIDTH = 3;
    private static final int TWIN_OFFSET = 2;
    private static final long TOOLTIP_PAUSE_MILLIS = 2L;

    private LayoutCheck() {
    }

    public record Box(int left, int top, int right, int bottom) {
        public static Box of(int x, int y, int width, int height) {
            return new Box(x, y, x + width, y + height);
        }

        static Box of(ScreenRectangle rect) {
            return of(rect.left(), rect.top(), rect.width(), rect.height());
        }

        public int width() {
            return this.right - this.left;
        }

        public int height() {
            return this.bottom - this.top;
        }

        public boolean empty() {
            return this.right <= this.left || this.bottom <= this.top;
        }

        public boolean contains(Box other, int slack) {
            return other.left >= this.left - slack && other.top >= this.top - slack && other.right <= this.right + slack
                    && other.bottom <= this.bottom + slack;
        }

        public @Nullable Box intersection(Box other) {
            Box box = new Box(Math.max(this.left, other.left), Math.max(this.top, other.top), Math.min(this.right, other.right),
                    Math.min(this.bottom, other.bottom));
            return box.empty() ? null : box;
        }

        public String describe() {
            return this.left + "," + this.top + " " + this.width() + "x" + this.height();
        }
    }

    public record Text(String value, Box full, @Nullable Box clip, int layer) {
        public @Nullable Box visible() {
            return this.clip == null ? this.full : this.clip.intersection(this.full);
        }

        boolean above() {
            return this.clip != null && this.full.top() < this.clip.top() - SLACK;
        }

        boolean below() {
            return this.clip != null && this.full.bottom() > this.clip.bottom() + SLACK;
        }

        boolean sideways() {
            return this.clip != null && (this.full.left() < this.clip.left() - SIDE_SLACK || this.full.right() > this.clip.right() + SIDE_SLACK);
        }
    }

    public record Widget(String name, Box box) {
    }

    public record Frame(List<Text> texts, List<Widget> widgets, List<Text> tooltip) {
        public boolean shows(String value) {
            for (Text text : this.texts) {
                Box visible = text.visible();
                if (visible != null && text.value().contains(value)) {
                    return true;
                }
            }
            return false;
        }

        public Optional<Box> find(String value) {
            for (Text text : this.texts) {
                if (text.value().contains(value) && text.visible() != null) {
                    return Optional.of(text.visible());
                }
            }
            return Optional.empty();
        }

        public List<String> values() {
            List<String> out = new ArrayList<>();
            for (Text text : this.texts) {
                out.add(text.value());
            }
            return out;
        }
    }

    public static Frame capture(Minecraft mc, Screen screen) {
        return capture(mc, screen, -1, -1);
    }

    public static Frame capture(Minecraft mc, Screen screen, int mouseX, int mouseY) {
        return capture(mc, g -> screen.extractRenderStateWithTooltipAndSubtitles(g, mouseX, mouseY, 0.0F), widgets(screen), mouseX, mouseY);
    }

    public static Frame capture(Minecraft mc, Consumer<GuiGraphicsExtractor> draw, List<Widget> widgets) {
        return capture(mc, draw, widgets, -1, -1);
    }

    private static Frame capture(Minecraft mc, Consumer<GuiGraphicsExtractor> draw, List<Widget> widgets, int mouseX, int mouseY) {
        State state = new State();
        draw.accept(new Graphics(mc, state, mouseX, mouseY));
        return new Frame(List.copyOf(state.texts), widgets, List.copyOf(state.tooltip));
    }

    public static List<String> tooltips(Minecraft mc, Screen screen, int[] seen) {
        Box bounds = Box.of(0, 0, screen.width, screen.height);
        Set<String> out = new LinkedHashSet<>();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible || widget.getWidth() <= 0 || widget.getHeight() <= 0) {
                continue;
            }
            int x = widget.getX() + widget.getWidth() / 2;
            int y = widget.getY() + widget.getHeight() / 2;
            Frame frame = capture(mc, screen, x, y);
            if (frame.tooltip().isEmpty()) {
                pause();
                frame = capture(mc, screen, x, y);
            }
            if (frame.tooltip().isEmpty()) {
                continue;
            }
            seen[0]++;
            for (Text line : frame.tooltip()) {
                if (!bounds.contains(line.full(), SLACK)) {
                    out.add("TOOLTIP \"" + line.value() + "\" von \"" + widget.getMessage().getString() + "\" ragt aus dem Bildschirm: "
                            + line.full().describe() + " in " + bounds.describe());
                }
            }
        }
        return new ArrayList<>(out);
    }

    public static List<Widget> widgets(Screen screen) {
        List<Widget> out = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible || widget.getWidth() <= 0 || widget.getHeight() <= 0) {
                continue;
            }
            out.add(new Widget(widget.getClass().getSimpleName() + " \"" + widget.getMessage().getString() + "\"",
                    Box.of(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())));
        }
        return out;
    }

    public static List<String> audit(Minecraft mc, Screen screen) {
        return audit(Box.of(0, 0, screen.width, screen.height), capture(mc, screen));
    }

    public static List<String> audit(Box screen, Frame frame) {
        Set<String> out = new LinkedHashSet<>();
        List<Text> texts = frame.texts();
        List<Widget> widgets = frame.widgets();
        for (Text text : texts) {
            Box clip = text.clip();
            if (clip != null && clip.empty()) {
                continue;
            }
            Box visible = text.visible();
            if (visible == null) {
                if (!text.above() && !text.below()) {
                    out.add("HIDDEN \"" + text.value() + "\" seitlich außerhalb von " + clip.describe());
                }
                continue;
            }
            if (!screen.contains(visible, SLACK)) {
                out.add("OFFSCREEN \"" + text.value() + "\" Text " + visible.describe() + " außerhalb von " + screen.describe());
            }
            if (text.sideways()) {
                out.add("CLIPPED \"" + text.value() + "\" seitlich: Text " + text.full().describe() + " in " + clip.describe());
            }
            if (text.value().endsWith("…") || text.value().endsWith("...")) {
                out.add("CUT \"" + text.value() + "\" gekürzt");
            }
        }
        for (Widget widget : widgets) {
            if (!screen.contains(widget.box(), 0)) {
                out.add("OFFSCREEN " + widget.name() + " Widget " + widget.box().describe() + " außerhalb von " + screen.describe());
            }
        }
        for (int i = 0; i < texts.size(); i++) {
            Text a = texts.get(i);
            Box boxA = shown(a);
            if (boxA == null) {
                continue;
            }
            for (int j = i + 1; j < texts.size(); j++) {
                Text b = texts.get(j);
                if (a.layer() != b.layer()) {
                    continue;
                }
                Box boxB = shown(b);
                if (boxB == null || !collides(boxA, boxB) || twins(a, b)) {
                    continue;
                }
                out.add("TEXT_OVERLAP \"" + a.value() + "\" über \"" + b.value() + "\": " + boxA.describe() + " und " + boxB.describe());
            }
        }
        for (int i = 0; i < widgets.size(); i++) {
            Widget a = widgets.get(i);
            for (int j = i + 1; j < widgets.size(); j++) {
                Widget b = widgets.get(j);
                if (a.box().intersection(b.box()) == null || a.box().contains(b.box(), 0) || b.box().contains(a.box(), 0)) {
                    continue;
                }
                out.add("WIDGET_OVERLAP " + a.name() + " über " + b.name() + ": " + a.box().describe() + " und " + b.box().describe());
            }
        }
        for (Text text : texts) {
            Box box = shown(text);
            if (box == null) {
                continue;
            }
            for (Widget widget : widgets) {
                if (!collides(box, widget.box()) || widget.box().contains(box, SIDE_SLACK)) {
                    continue;
                }
                out.add("TEXT_ON_WIDGET \"" + text.value() + "\" ragt in " + widget.name() + ": " + box.describe() + " und "
                        + widget.box().describe());
            }
        }
        return new ArrayList<>(out);
    }

    private static void pause() {
        try {
            Thread.sleep(TOOLTIP_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static @Nullable Box shown(Text text) {
        Box clip = text.clip();
        if (clip != null && clip.empty()) {
            return null;
        }
        return text.visible();
    }

    private static boolean collides(Box a, Box b) {
        Box common = a.intersection(b);
        return common != null && common.width() >= OVERLAP_MIN_WIDTH && common.height() >= OVERLAP_MIN;
    }

    private static boolean twins(Text a, Text b) {
        return a.value().equals(b.value()) && Math.abs(a.full().left() - b.full().left()) <= TWIN_OFFSET
                && Math.abs(a.full().top() - b.full().top()) <= TWIN_OFFSET;
    }

    static String plain(FormattedCharSequence sequence) {
        StringBuilder out = new StringBuilder();
        sequence.accept((position, style, codepoint) -> {
            out.appendCodePoint(codepoint);
            return true;
        });
        return out.toString();
    }

    private static final class State extends GuiRenderState {
        private final List<Text> texts = new ArrayList<>();
        private final List<Text> tooltip = new ArrayList<>();
        private boolean deferred;
        private int layer;
        private @Nullable FormattedCharSequence pending;

        @Override
        public void nextStratum() {
            this.layer++;
            super.nextStratum();
        }

        @Override
        public void addText(GuiTextRenderState text) {
            super.addText(text);
            if (this.pending == null) {
                return;
            }
            ScreenRectangle bounds = text.ensurePrepared().bounds();
            if (bounds == null) {
                return;
            }
            int advance = Minecraft.getInstance().font.width(this.pending) + 1;
            if (advance < bounds.width()) {
                bounds = new ScreenRectangle(bounds.left(), bounds.top(), advance, bounds.height());
            }
            ScreenRectangle full = bounds.transformMaxBounds(text.pose);
            if (full.width() <= 0 || full.height() <= 0) {
                return;
            }
            String value = plain(this.pending);
            if (value.isBlank()) {
                return;
            }
            Text entry = new Text(value, Box.of(full), text.scissor == null ? null : Box.of(text.scissor), this.layer);
            (this.deferred ? this.tooltip : this.texts).add(entry);
        }
    }

    private static final class Graphics extends GuiGraphicsExtractor {
        private final State state;

        private Graphics(Minecraft minecraft, State state, int mouseX, int mouseY) {
            super(minecraft, state, mouseX, mouseY);
            this.state = state;
        }

        @Override
        public void extractDeferredElements(int mouseX, int mouseY, float a) {
            this.state.deferred = true;
            try {
                super.extractDeferredElements(mouseX, mouseY, a);
            } finally {
                this.state.deferred = false;
            }
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
            this.state.pending = text;
            try {
                super.text(font, text, x, y, color, dropShadow);
            } finally {
                this.state.pending = null;
            }
        }
    }
}
