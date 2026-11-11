package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.client.hud.HudSettingsKeys;
import de.lardtom.camdrones.client.hud.SpeedUnit;
import de.lardtom.camdrones.client.record.FlashbackLink;
import de.lardtom.camdrones.client.record.RecordTexts;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class CamSettingsTab extends ConfigTab {
    public static final String ID = "camdrones:settings";
    public static final int ORDER = 1100;
    public static final int TOGGLE_WIDTH = 48;
    public static final int SLIDER_WIDTH = 96;
    public static final double VOLUME_STEP_PERCENT = CamClientConfig.WARNING_VOLUME_STEP * 100.0;

    private static @Nullable CamSettingsTab active;

    private boolean previewMarkers;
    private SpeedUnit speedUnit;
    private double warningVolume;
    private double servoVolume;
    private RecordingMode recordingMode;
    private boolean modeChosen;
    private final boolean flashbackInstalled = FlashbackLink.installed();
    private @Nullable FlatButton flashbackButton;
    private @Nullable FlatButton directButton;
    private @Nullable FlatButton previewButton;
    private @Nullable FlatButton unitButton;
    private @Nullable StepSlider volumeSlider;
    private @Nullable StepSlider servoSlider;
    private int sectionY;
    private int previewY;
    private int hudSectionY;
    private int unitY;
    private int volumeY;
    private int volumeWidth;
    private int servoY;
    private int servoWidth;
    private int recordSectionY;
    private int modeLabelY;
    private int modeY;
    private int modeHintY;
    private int fileY;

    public CamSettingsTab() {
        this.read(CamClientConfig.get());
    }

    public static @Nullable CamSettingsTab active() {
        return active;
    }

    public static Component unitName(SpeedUnit unit) {
        return Component.translatable(unit.key());
    }

    public static Component volumeValue(double percent) {
        return Component.translatable(HudSettingsKeys.VOLUME_VALUE, Math.round(percent));
    }

    private void read(CamClientConfig config) {
        this.previewMarkers = config.previewMarkers();
        this.speedUnit = config.speedUnit();
        this.warningVolume = config.warningVolume();
        this.servoVolume = config.servoVolume();
        this.recordingMode = config.recordingMode(this.flashbackInstalled);
        this.modeChosen = config.chosenRecordingMode().isPresent();
    }

    public static @Nullable Component modeHint(boolean installed, FlashbackLink.Status status) {
        if (!installed) {
            return Component.translatable(RecordTexts.NEEDS_FLASHBACK);
        }
        return status == FlashbackLink.Status.INCOMPATIBLE ? Component.translatable(RecordTexts.INCOMPATIBLE) : null;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable(FieldText.PREFIX + "settings.title");
    }

    @Override
    public int order() {
        return ORDER;
    }

    public boolean previewMarkers() {
        return this.previewMarkers;
    }

    public SpeedUnit speedUnit() {
        return this.speedUnit;
    }

    public double warningVolume() {
        return this.warningVolume;
    }

    public double servoVolume() {
        return this.servoVolume;
    }

    public @Nullable FlatButton previewButton() {
        return this.previewButton;
    }

    public @Nullable FlatButton unitButton() {
        return this.unitButton;
    }

    public @Nullable StepSlider volumeSlider() {
        return this.volumeSlider;
    }

    public @Nullable StepSlider servoSlider() {
        return this.servoSlider;
    }

    public RecordingMode recordingMode() {
        return this.recordingMode;
    }

    public @Nullable FlatButton flashbackButton() {
        return this.flashbackButton;
    }

    public @Nullable FlatButton directButton() {
        return this.directButton;
    }

    @Override
    protected void build() {
        active = this;
        int left = this.x + Metrics.PADDING;
        int width = Math.max(0, this.width - 2 * Metrics.PADDING);
        int toggleX = left + width - TOGGLE_WIDTH;
        this.sectionY = this.y + Metrics.PADDING;
        this.previewY = this.sectionY + Metrics.SECTION_HEIGHT + Metrics.LABEL_GAP;
        this.previewButton = this.host.addWidget(new FlatButton(toggleX, this.previewY, TOGGLE_WIDTH, Metrics.CONTROL_HEIGHT,
                ThemedWidgets.onOff(this.previewMarkers), FlatButton.Style.NORMAL, () -> this.previewMarkers, this::togglePreview));
        this.previewButton.setTooltip(Tooltip.create(Component.translatable(FieldText.PREFIX + "settings.preview.tooltip")));

        this.hudSectionY = this.previewY + Metrics.CONTROL_HEIGHT + Metrics.SECTION_GAP;
        this.unitY = this.hudSectionY + Metrics.SECTION_HEIGHT + Metrics.LABEL_GAP;
        this.unitButton = this.host.addWidget(new FlatButton(toggleX, this.unitY, TOGGLE_WIDTH, Metrics.CONTROL_HEIGHT,
                unitName(this.speedUnit), FlatButton.Style.NORMAL, this::cycleUnit));
        this.unitButton.setTooltip(Tooltip.create(Component.translatable(HudSettingsKeys.UNIT_TOOLTIP)));

        this.volumeY = this.unitY + Metrics.CONTROL_HEIGHT + Metrics.GAP;
        int labelNeed = this.host.font().width(Component.translatable(HudSettingsKeys.VOLUME)) + Metrics.LABEL_GAP;
        this.volumeWidth = Math.clamp(width - labelNeed, TOGGLE_WIDTH, SLIDER_WIDTH);
        this.volumeSlider = this.host.addWidget(new StepSlider(left + width - this.volumeWidth, this.volumeY, this.volumeWidth,
                Metrics.CONTROL_HEIGHT, 0.0, 100.0, VOLUME_STEP_PERCENT, this.warningVolume * 100.0, CamSettingsTab::volumeValue, this::volumeChanged));
        this.volumeSlider.percentRange();
        this.volumeSlider.setTooltip(Tooltip.create(Component.translatable(HudSettingsKeys.VOLUME_TOOLTIP)));

        this.servoY = this.volumeY + Metrics.CONTROL_HEIGHT + Metrics.GAP;
        int servoNeed = this.host.font().width(Component.translatable(CrewTexts.SERVO_VOLUME)) + Metrics.LABEL_GAP;
        this.servoWidth = Math.clamp(width - servoNeed, TOGGLE_WIDTH, SLIDER_WIDTH);
        this.servoSlider = this.host.addWidget(new StepSlider(left + width - this.servoWidth, this.servoY, this.servoWidth,
                Metrics.CONTROL_HEIGHT, 0.0, 100.0, VOLUME_STEP_PERCENT, this.servoVolume * 100.0, CamSettingsTab::volumeValue, this::servoChanged));
        this.servoSlider.percentRange();
        this.servoSlider.setTooltip(Tooltip.create(Component.translatable(CrewTexts.SERVO_VOLUME_TOOLTIP)));

        this.recordSectionY = this.servoY + Metrics.CONTROL_HEIGHT + Metrics.SECTION_GAP;
        this.modeLabelY = this.recordSectionY + Metrics.SECTION_HEIGHT + Metrics.LABEL_GAP;
        this.modeY = this.modeLabelY + Metrics.LINE_HEIGHT;
        int half = Math.max(TOGGLE_WIDTH, (width - Metrics.GAP) / 2);
        this.flashbackButton = this.host.addWidget(new FlatButton(left, this.modeY, half, Metrics.CONTROL_HEIGHT,
                Component.translatable(RecordingMode.FLASHBACK.translationKey()), FlatButton.Style.NORMAL,
                () -> this.recordingMode == RecordingMode.FLASHBACK, () -> this.chooseMode(RecordingMode.FLASHBACK)));
        this.flashbackButton.active = this.flashbackInstalled;
        this.flashbackButton.setTooltip(Tooltip.create(this.flashbackInstalled ? Component.translatable(RecordingMode.FLASHBACK.tooltipKey())
                : Component.translatable(RecordTexts.NEEDS_FLASHBACK)));
        this.directButton = this.host.addWidget(new FlatButton(left + width - half, this.modeY, half, Metrics.CONTROL_HEIGHT,
                Component.translatable(RecordingMode.DIRECT.translationKey()), FlatButton.Style.NORMAL,
                () -> this.recordingMode == RecordingMode.DIRECT, () -> this.chooseMode(RecordingMode.DIRECT)));
        this.directButton.setTooltip(Tooltip.create(Component.translatable(RecordingMode.DIRECT.tooltipKey())));
        this.modeHintY = this.modeY + Metrics.CONTROL_HEIGHT + Metrics.LABEL_GAP;
        int hintLines = modeHint(this.flashbackInstalled, FlashbackLink.status()) == null ? 0 : 1;
        this.fileY = this.modeHintY + hintLines * Metrics.LINE_HEIGHT + (hintLines > 0 ? Metrics.GAP : 0);
        this.reportBottom(this.fileY + Metrics.LINE_HEIGHT);
    }

    private void chooseMode(RecordingMode mode) {
        if (!mode.selectable(this.flashbackInstalled)) {
            return;
        }
        this.recordingMode = mode;
        this.modeChosen = true;
        this.host.changed();
    }

    private void togglePreview() {
        this.previewMarkers = !this.previewMarkers;
        if (this.previewButton != null) {
            this.previewButton.setMessage(ThemedWidgets.onOff(this.previewMarkers));
        }
        this.host.changed();
    }

    private void cycleUnit() {
        this.unit(this.speedUnit.next());
    }

    private void unit(SpeedUnit unit) {
        this.speedUnit = unit;
        if (this.unitButton != null) {
            this.unitButton.setMessage(unitName(unit));
        }
        this.host.changed();
    }

    private void volumeChanged(double percent) {
        this.warningVolume = CamClientConfig.clampVolume(percent / 100.0);
        this.host.changed();
    }

    private void servoChanged(double percent) {
        this.servoVolume = CamClientConfig.clampVolume(percent / 100.0, CamClientConfig.SERVO_VOLUME_DEFAULT);
        this.host.changed();
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x + Metrics.PADDING;
        int width = Math.max(0, this.width - 2 * Metrics.PADDING);
        int labelWidth = Math.max(0, width - TOGGLE_WIDTH - Metrics.LABEL_GAP);
        ThemeDraw.sectionTitle(g, this.host.font(), Component.translatable(FieldText.PREFIX + "title"), left, this.sectionY, width);
        ThemeText.fitted(g, this.host.font(), Component.translatable(FieldText.PREFIX + "settings.preview"), left,
                Metrics.textY(this.previewY, Metrics.CONTROL_HEIGHT), labelWidth, Palette.TEXT);
        ThemeDraw.sectionTitle(g, this.host.font(), Component.translatable(HudSettingsKeys.TITLE), left, this.hudSectionY, width);
        ThemeText.fitted(g, this.host.font(), Component.translatable(HudSettingsKeys.UNIT), left,
                Metrics.textY(this.unitY, Metrics.CONTROL_HEIGHT), labelWidth, Palette.TEXT);
        ThemeText.fitted(g, this.host.font(), Component.translatable(HudSettingsKeys.VOLUME), left,
                Metrics.textY(this.volumeY, Metrics.CONTROL_HEIGHT), Math.max(0, width - this.volumeWidth - Metrics.LABEL_GAP), Palette.TEXT);
        ThemeText.fitted(g, this.host.font(), Component.translatable(CrewTexts.SERVO_VOLUME), left,
                Metrics.textY(this.servoY, Metrics.CONTROL_HEIGHT), Math.max(0, width - this.servoWidth - Metrics.LABEL_GAP), Palette.TEXT);
        ThemeDraw.sectionTitle(g, this.host.font(), Component.translatable(RecordTexts.SECTION), left, this.recordSectionY, width);
        ThemeText.fitted(g, this.host.font(), Component.translatable(RecordTexts.MODE), left, this.modeLabelY, width, Palette.TEXT);
        Component hint = modeHint(this.flashbackInstalled, FlashbackLink.status());
        if (hint != null) {
            ThemeText.fitted(g, this.host.font(), hint, left, this.modeHintY, width, Palette.TEXT_FAINT);
        }
        ThemeText.fitted(g, this.host.font(), Component.translatable(FieldText.PREFIX + "settings.file"), left, this.fileY, width,
                Palette.TEXT_FAINT);
    }

    @Override
    public void resetDefaults() {
        if (this.previewMarkers != CamClientConfig.PREVIEW_MARKERS_DEFAULT) {
            this.togglePreview();
        }
        if (this.speedUnit != CamClientConfig.SPEED_UNIT_DEFAULT) {
            this.unit(CamClientConfig.SPEED_UNIT_DEFAULT);
        }
        if (this.warningVolume != CamClientConfig.WARNING_VOLUME_DEFAULT) {
            this.warningVolume = CamClientConfig.WARNING_VOLUME_DEFAULT;
            if (this.volumeSlider != null) {
                this.volumeSlider.setCurrent(this.warningVolume * 100.0);
            }
            this.host.changed();
        }
        if (this.servoVolume != CamClientConfig.SERVO_VOLUME_DEFAULT) {
            this.servoVolume = CamClientConfig.SERVO_VOLUME_DEFAULT;
            if (this.servoSlider != null) {
                this.servoSlider.setCurrent(this.servoVolume * 100.0);
            }
            this.host.changed();
        }
        RecordingMode fallback = RecordingMode.defaultFor(this.flashbackInstalled);
        if (this.modeChosen || this.recordingMode != fallback) {
            this.recordingMode = fallback;
            this.modeChosen = false;
            this.host.changed();
        }
    }

    @Override
    public void save() {
        CamClientConfig config = CamClientConfig.get();
        config.previewMarkers(this.previewMarkers);
        config.speedUnit(this.speedUnit);
        config.warningVolume(this.warningVolume);
        config.servoVolume(this.servoVolume);
        if (this.modeChosen) {
            config.recordingMode(this.recordingMode);
        } else {
            config.resetRecordingMode();
        }
    }

    @Override
    public void discard() {
        this.read(CamClientConfig.get());
    }
}
