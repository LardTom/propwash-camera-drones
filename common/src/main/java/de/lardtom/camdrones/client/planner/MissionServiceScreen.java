package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.net.ExitSettingUpload;
import de.lardtom.camdrones.net.ViewSettingUpload;
import de.lardtom.camdrones.planner.ExitSettings;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.ViewSettings;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MissionField;
import de.lardtom.camdrones.planner.model.MissionWindowLayout;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.ViewSetting;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class MissionServiceScreen extends Screen implements ThemedScreen {
    private final Entity drone;
    private final @Nullable Screen parent;
    private MissionWindowLayout layout;
    private FlatButton startButton;
    private FlatButton pauseButton;
    private FlatButton stopButton;
    private ExitSetting pendingExit;
    private int pendingExitRequest = -1;
    private ViewSetting pendingView;
    private int pendingViewRequest = -1;
    private @Nullable Component status;
    private boolean statusError;

    public MissionServiceScreen(Entity drone, @Nullable Screen parent) {
        super(Component.translatable(FieldText.PREFIX + "service.title"));
        this.drone = drone;
        this.parent = parent;
    }

    public Entity drone() {
        return this.drone;
    }

    public MissionWindowLayout layout() {
        return this.layout;
    }

    public @Nullable Component status() {
        return this.status;
    }

    public static MissionWindowLayout.Widths widths(Font font) {
        int value = 0;
        for (ExitSetting setting : ExitSetting.values()) {
            value = Math.max(value, font.width(text(setting.value())));
        }
        value = Math.max(value, font.width(text(ExitSetting.value(Optional.empty()))));
        int view = font.width(text(ViewSetting.value(Optional.empty())));
        for (ViewSetting setting : ViewSetting.values()) {
            view = Math.max(view, font.width(text(setting.value())));
        }
        String p = FieldText.PREFIX;
        return new MissionWindowLayout.Widths(font.width(Component.translatable(ExitSetting.LABEL_KEY)), value,
                font.width(Component.translatable(p + "button.start")), font.width(Component.translatable(p + "button.pause")),
                font.width(Component.translatable(p + "button.stop")), font.width(Component.translatable(p + "service.close")),
                font.width(Component.translatable(ViewSetting.LABEL_KEY)), view);
    }

    @Override
    protected void init() {
        this.layout = MissionWindowLayout.compute(this.width, this.height, widths(this.font));
        MissionWindowLayout l = this.layout;
        String p = FieldText.PREFIX;
        this.startButton = this.button(l.start(), Component.translatable(p + "button.start"), FlatButton.Style.PRIMARY,
                () -> this.command(MissionCommands.Command.START));
        this.startButton.setTooltip(Tooltip.create(Component.translatable(p + "button.start.tooltip")));
        this.pauseButton = this.button(l.pause(), Component.translatable(p + "button.pause"), FlatButton.Style.NORMAL,
                () -> this.command(MissionCommands.Command.PAUSE));
        this.pauseButton.setTooltip(Tooltip.create(Component.translatable(p + "button.pause.tooltip")));
        this.stopButton = this.button(l.stop(), Component.translatable(p + "button.stop"), FlatButton.Style.DANGER,
                () -> this.command(MissionCommands.Command.STOP));
        this.stopButton.setTooltip(Tooltip.create(Component.translatable(p + "button.stop.tooltip")));
        this.button(l.close(), Component.translatable(p + "service.close"), FlatButton.Style.NORMAL, this::onClose);
        this.refreshButtons();
    }

    private FlatButton button(Rect r, Component label, FlatButton.Style style, Runnable action) {
        return this.addRenderableWidget(new FlatButton(r.x(), r.y(), r.w(), r.h(), label, style, action));
    }

    private UUID serial() {
        return PlannerTablets.serialOf(this.drone);
    }

    private boolean paired() {
        return !CamDroneData.NO_SERIAL.equals(this.serial());
    }

    private void refreshButtons() {
        boolean commands = this.paired() && PlannerClient.commands().available();
        this.startButton.active = commands;
        this.pauseButton.active = commands;
        this.stopButton.active = commands;
    }

    private void command(MissionCommands.Command command) {
        if (!this.paired() || !PlannerClient.commands().send(this.serial(), command)) {
            this.status(Component.translatable(FieldText.PREFIX + "status.command_failed"), true);
            return;
        }
        this.status(Component.translatable(FieldText.PREFIX + "status.command." + command.name().toLowerCase(Locale.ROOT)), false);
    }

    private void status(Component text, boolean error) {
        this.status = text;
        this.statusError = error;
    }

    public Optional<ExitSetting> shownExit() {
        Optional<ExitSetting> actual = ExitSettings.read(this.drone);
        if (this.pendingExit == null) {
            return actual;
        }
        if (this.pendingExitRequest < 0 && actual.filter(this.pendingExit::equals).isPresent()) {
            this.pendingExit = null;
            return actual;
        }
        return Optional.of(this.pendingExit);
    }

    private void stepExit(int direction) {
        if (!this.paired()) {
            this.status(Component.translatable(FieldText.PREFIX + "status.not_linked"), true);
            return;
        }
        ExitSetting next = this.shownExit().orElse(ExitSetting.HOVER).step(direction);
        OptionalInt id = PlannerClient.sendExit(this.serial(), next, this::onExitReply);
        if (id.isEmpty()) {
            this.status(Component.translatable(FieldText.PREFIX + "status.offline"), true);
            return;
        }
        this.pendingExit = next;
        this.pendingExitRequest = id.getAsInt();
    }

    private void onExitReply(ExitSettingUpload.Reply reply) {
        if (reply.requestId() != this.pendingExitRequest) {
            return;
        }
        this.pendingExitRequest = -1;
        if (!reply.result().ok()) {
            this.pendingExit = null;
        }
        this.status(Component.translatable(reply.result().messageKey()), !reply.result().ok());
    }

    public Optional<ViewSetting> shownView() {
        Optional<ViewSetting> actual = ViewSettings.read(this.drone);
        if (this.pendingView == null) {
            return actual;
        }
        if (this.pendingViewRequest < 0 && actual.filter(this.pendingView::equals).isPresent()) {
            this.pendingView = null;
            return actual;
        }
        return Optional.of(this.pendingView);
    }

    public void stepView(int direction) {
        if (!this.paired()) {
            this.status(Component.translatable(FieldText.PREFIX + "status.not_linked"), true);
            return;
        }
        ViewSetting next = this.shownView().orElse(ViewSetting.OSD).step(direction);
        OptionalInt id = PlannerClient.sendView(this.serial(), next, this::onViewReply);
        if (id.isEmpty()) {
            this.status(Component.translatable(FieldText.PREFIX + "status.offline"), true);
            return;
        }
        this.pendingView = next;
        this.pendingViewRequest = id.getAsInt();
    }

    private void onViewReply(ViewSettingUpload.Reply reply) {
        if (reply.requestId() != this.pendingViewRequest) {
            return;
        }
        this.pendingViewRequest = -1;
        if (!reply.result().ok()) {
            this.pendingView = null;
        }
        this.status(Component.translatable(ViewSetting.messageKey(reply.result())), !reply.result().ok());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        Rect box = this.layout.exitValue();
        if (box.contains(event.x(), event.y())) {
            this.stepExit(EditorLayout.stepDirection(box, event.x()));
            PlannerSounds.click(1.0F);
            return true;
        }
        Rect view = this.layout.viewValue();
        if (view.contains(event.x(), event.y())) {
            this.stepView(EditorLayout.stepDirection(view, event.x()));
            PlannerSounds.click(1.0F);
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (this.drone.isRemoved()) {
            this.onClose();
            return;
        }
        this.refreshButtons();
    }

    private Component droneText() {
        String name = this.drone.hasCustomName() && this.drone.getCustomName() != null ? this.drone.getCustomName().getString() : "";
        UUID serial = this.serial();
        if (name.isEmpty()) {
            name = CamDroneData.NO_SERIAL.equals(serial) ? "" : serial.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        return name.isEmpty() ? Component.translatable(FieldText.PREFIX + "header.unlinked")
                : Component.translatable(FieldText.PREFIX + "header.linked", name);
    }

    private Component missionText() {
        MissionData data = CamDroneData.registered() ? PropwashApi.droneData().get(this.drone, CamDroneData.mission()) : MissionData.EMPTY;
        if (data == null || data.isEmpty()) {
            return Component.translatable(FieldText.PREFIX + "service.none");
        }
        Optional<Mission> mission = data.mission();
        if (mission.isEmpty()) {
            return Component.translatable(FieldText.PREFIX + "status.locked");
        }
        Mission m = mission.get();
        return Component.translatable(FieldText.PREFIX + "service.mission", m.size(), m.pointsOfInterest().size(),
                text(MissionField.END.value(m, '.')));
    }

    private Component stateText() {
        MissionStatus state = CamDroneData.registered() ? PropwashApi.droneData().get(this.drone, CamDroneData.missionState()) : null;
        MissionStatus shown = state == null ? MissionStatus.IDLE : state;
        Component value = switch (shown.state()) {
            case IDLE -> Component.translatable(FieldText.PREFIX + "service.state.idle");
            case RUNNING -> Component.translatable(FieldText.PREFIX + "service.state.running", shown.index() + 1);
            case PAUSED -> Component.translatable(FieldText.PREFIX + "service.state.paused", shown.index() + 1);
            case DONE -> Component.translatable(FieldText.PREFIX + "service.state.done");
            case ABORTED -> Component.translatable(FieldText.PREFIX + "service.state.aborted");
        };
        return Component.translatable(FieldText.PREFIX + "service.state", value);
    }

    private static Component text(FieldText value) {
        return Component.translatable(value.key(), value.args().toArray());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        MissionWindowLayout l = this.layout;
        Rect w = l.window();
        ThemeDraw.window(g, w.x(), w.y(), w.right(), w.bottom());
        ThemeDraw.headerBar(g, w.x(), w.y(), w.right(), l.header().h());
        ThemeText.fitted(g, this.font, this.title, l.title().x(), Metrics.textY(l.title().y(), l.title().h()), l.title().w(), Palette.TEXT);
        this.line(g, l.droneLine(), this.droneText(), this.paired() ? Palette.TEXT : Palette.WARNING);
        this.line(g, l.missionLine(), this.missionText(), Palette.TEXT_MUTED);
        this.line(g, l.stateLine(), this.stateText(), Palette.TEXT_MUTED);
        this.exitRow(g, mouseX, mouseY);
        this.settingRow(g, mouseX, mouseY, l.viewRow(), l.viewValue(), Component.translatable(ViewSetting.LABEL_KEY), text(ViewSetting.value(this.shownView())));
        ThemeDraw.footerBar(g, w.x(), l.footer().y(), w.right(), w.bottom());
        Component hint = this.status != null ? this.status : Component.translatable(FieldText.PREFIX + "service.hint");
        int color = this.status == null ? Palette.TEXT_FAINT : this.statusError ? Palette.ERROR : Palette.SUCCESS;
        ThemeText.fitted(g, this.font, hint, l.hint().x(), Metrics.textY(l.hint().y(), l.hint().h()), l.hint().w(), color);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, w.x(), w.y(), w.right(), w.bottom());
        if (l.exitValue().contains(mouseX, mouseY)) {
            ThemeText.tooltip(g, this.font, Component.translatable(ExitSetting.TOOLTIP_KEY), mouseX, mouseY);
        } else if (l.viewValue().contains(mouseX, mouseY)) {
            ThemeText.tooltip(g, this.font, Component.translatable(ViewSetting.TOOLTIP_KEY), mouseX, mouseY);
        }
    }

    private void line(GuiGraphicsExtractor g, Rect r, Component text, int color) {
        ThemeText.fitted(g, this.font, text, r.x(), Metrics.textY(r.y(), r.h()), r.w(), color);
    }

    private void exitRow(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        MissionWindowLayout l = this.layout;
        this.settingRow(g, mouseX, mouseY, l.exitRow(), l.exitValue(), Component.translatable(ExitSetting.LABEL_KEY), text(ExitSetting.value(this.shownExit())));
    }

    private void settingRow(GuiGraphicsExtractor g, int mouseX, int mouseY, Rect row, Rect box, Component label, Component value) {
        MissionWindowLayout l = this.layout;
        boolean active = this.paired();
        int ty = Metrics.textY(row.y(), row.h());
        ThemeText.fitted(g, this.font, label, row.x(), ty, l.exitLabelWidth() - EditorLayout.LABEL_GAP,
                active ? Palette.TEXT_MUTED : Palette.TEXT_DISABLED);
        boolean hot = active && box.contains(mouseX, mouseY);
        ThemeDraw.framedBox(g, box.x(), box.y(), box.right(), box.bottom(), active ? (hot ? Palette.FIELD_HOVER : Palette.FIELD) : Palette.DISABLED,
                hot ? Palette.BORDER_STRONG : Palette.BORDER);
        int arrowColor = !active ? Palette.TEXT_DISABLED : hot ? Palette.ACCENT : Palette.TEXT_FAINT;
        ThemeText.text(g, this.font, EditorTexts.PREVIOUS, box.x() + 3, ty, arrowColor);
        ThemeText.textRight(g, this.font, EditorTexts.NEXT, box.right() - 3, ty, arrowColor);
        ThemeText.fittedCentered(g, this.font, value, box.centerX(), ty, EditorLayout.valueTextWidth(box),
                active ? Palette.TEXT : Palette.TEXT_DISABLED);
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
