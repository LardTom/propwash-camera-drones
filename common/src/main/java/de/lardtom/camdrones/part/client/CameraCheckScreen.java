package de.lardtom.camdrones.part.client;

import de.lardtom.camdrones.part.CameraCheck;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CameraCheckScreen extends Screen implements ThemedScreen {
    public static final String OK_MARK = "✔";
    public static final String BAD_MARK = "✘";
    public static final int MIN_WIDTH = 240;
    public static final int MAX_WIDTH = 360;
    public static final int MARGIN = 4;
    public static final int INDENT = 12;
    public static final int SECTION_GAP = 5;

    private final Screen parent;
    private final CameraCheck.Result result;
    private final List<Row> rows = new ArrayList<>();
    private int left;
    private int top;
    private int right;
    private int bottom;
    private boolean wrap;

    public record Row(Component text, Component status, int color, int statusColor, int indent, boolean section) {
    }

    public CameraCheckScreen(Screen parent, CameraCheck.Result result) {
        super(Component.translatable(CameraCheck.TITLE));
        this.parent = parent;
        this.result = result;
    }

    public CameraCheck.Result result() {
        return this.result;
    }

    public List<Row> rows() {
        return List.copyOf(this.rows);
    }

    public static List<Row> rows(CameraCheck.Result result) {
        List<Row> out = new ArrayList<>();
        boolean complete = result.complete();
        out.add(new Row(Component.translatable(complete ? CameraCheck.COMPLETE : CameraCheck.INCOMPLETE), Component.empty(),
                complete ? Palette.SUCCESS : Palette.WARNING, 0, 0, false));
        out.add(new Row(complete ? Component.translatable(CameraCheck.COMPLETE_HINT)
                : Component.translatable(CameraCheck.INCOMPLETE_HINT, result.problems().size(), result.lines().size()), Component.empty(),
                Palette.TEXT_MUTED, 0, 0, false));
        if (!complete && result.preset().isEmpty()) {
            out.add(new Row(Component.translatable(CameraCheck.NO_CAMERA_FRAME), Component.empty(), Palette.TEXT_MUTED, 0, 0, false));
        }
        boolean first = true;
        for (CameraCheck.Line line : result.lines()) {
            Component mark = ThemeText.colored(Component.literal(line.ok() ? OK_MARK : BAD_MARK), line.ok() ? Palette.SUCCESS : Palette.ERROR);
            Component name = Component.empty().append(mark).append(" ").append(Component.translatable(line.role().nameKey()));
            out.add(new Row(name, Component.translatable(statusKey(line.state())), Palette.TEXT, statusColor(line.state()), 0, first));
            first = false;
            if (line.state() == CameraCheck.State.WRONG && line.fitted().isPresent()) {
                out.add(new Row(Component.translatable(line.role().wrongKey(), partName(line.fitted().get())), Component.empty(), Palette.TEXT_MUTED,
                        0, INDENT, false));
            }
            if (line.advice().isPresent()) {
                boolean other = line.state() == CameraCheck.State.OTHER;
                out.add(new Row(Component.translatable(other ? CameraCheck.RECOMMENDED : CameraCheck.ADVICE, partName(line.advice().get())),
                        Component.empty(), other ? Palette.TEXT_MUTED : Palette.TEXT, 0, INDENT, false));
            }
        }
        return out;
    }

    public static String statusKey(CameraCheck.State state) {
        return switch (state) {
            case FITTED -> CameraCheck.FITTED;
            case OTHER -> CameraCheck.OTHER;
            case MISSING -> CameraCheck.MISSING;
            case WRONG -> CameraCheck.WRONG;
        };
    }

    private static int statusColor(CameraCheck.State state) {
        return switch (state) {
            case FITTED -> Palette.SUCCESS;
            case OTHER -> Palette.TEXT_MUTED;
            case MISSING -> Palette.ERROR;
            case WRONG -> Palette.WARNING;
        };
    }

    public static Component partName(Identifier part) {
        ItemStack stack = PropwashApi.partItems().stack(part);
        return stack.isEmpty() ? Component.literal(part.toString()) : stack.getHoverName();
    }

    @Override
    protected void init() {
        this.rows.clear();
        this.rows.addAll(rows(this.result));
        int need = ThemeDraw.brandedTitleX(this.font, 0) + this.font.width(this.title) + Metrics.PADDING;
        for (Row row : this.rows) {
            need = Math.max(need, 2 * Metrics.PADDING + row.indent() + this.font.width(row.text()) + this.statusRoom(row));
        }
        int width = Math.max(0, Math.min(Math.min(Math.max(MIN_WIDTH, need), MAX_WIDTH), this.width - 2 * MARGIN));
        int chrome = Metrics.HEADER_HEIGHT + 3 * Metrics.PADDING + SECTION_GAP + Metrics.CONTROL_HEIGHT;
        int room = this.height - 2 * MARGIN;
        this.wrap = true;
        int content = this.contentHeight(width);
        if (chrome + content > room) {
            this.wrap = false;
            content = this.contentHeight(width);
        }
        int height = Math.min(chrome + content, room);
        this.left = (this.width - width) / 2;
        this.right = this.left + width;
        this.top = Math.max(MARGIN, (this.height - height) / 2);
        this.bottom = this.top + height;
        int x = this.left + Metrics.PADDING;
        int inner = width - 2 * Metrics.PADDING;
        this.addRenderableWidget(new FlatButton(x, this.bottom - Metrics.PADDING - Metrics.CONTROL_HEIGHT, inner, Metrics.CONTROL_HEIGHT,
                Component.translatable("gui.back"), FlatButton.Style.NORMAL, this::onClose));
    }

    private int statusRoom(Row row) {
        return row.status().getString().isEmpty() ? 0 : this.font.width(row.status()) + Metrics.GAP;
    }

    private int textWidth(Row row, int width) {
        return Math.max(1, width - 2 * Metrics.PADDING - row.indent() - this.statusRoom(row));
    }

    private int rowHeight(Row row, int width) {
        return this.wrap ? ThemeText.wrappedHeight(this.font, row.text(), this.textWidth(row, width)) : Metrics.LINE_HEIGHT;
    }

    private int contentHeight(int width) {
        int height = 0;
        for (Row row : this.rows) {
            height += this.rowHeight(row, width);
        }
        return height;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title, this.right - Metrics.PADDING);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int width = this.right - this.left;
        int x = this.left + Metrics.PADDING;
        int textRight = this.right - Metrics.PADDING;
        int limit = this.bottom - 2 * Metrics.PADDING - Metrics.CONTROL_HEIGHT;
        int y = this.top + Metrics.HEADER_HEIGHT + Metrics.PADDING;
        for (Row row : this.rows) {
            if (row.section()) {
                ThemeDraw.separator(g, x, textRight, y + SECTION_GAP / 2);
                y += SECTION_GAP;
            }
            int height = this.rowHeight(row, width);
            if (y + Metrics.TEXT_HEIGHT > limit) {
                break;
            }
            int textWidth = this.textWidth(row, width);
            if (this.wrap) {
                ThemeText.wrapped(g, this.font, row.text(), x + row.indent(), y, textWidth, row.color());
            } else {
                ThemeText.fitted(g, this.font, row.text(), x + row.indent(), y, textWidth, row.color());
            }
            if (this.statusRoom(row) > 0) {
                ThemeText.fittedRight(g, this.font, row.status(), textRight, y, this.font.width(row.status()), row.statusColor());
            }
            y += height;
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
