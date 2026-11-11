package de.lardtom.camdrones.client.planner;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.nav.CurvedTurn;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.ExitSettingUpload;
import de.lardtom.camdrones.net.ViewSettingUpload;
import de.lardtom.camdrones.net.MissionUpload;
import de.lardtom.camdrones.planner.ExitSettings;
import de.lardtom.camdrones.planner.ViewSettings;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerSupport;
import de.lardtom.camdrones.planner.model.AutoShotMenu;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FollowMenu;
import de.lardtom.camdrones.planner.model.ViewSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MapLabels;
import de.lardtom.camdrones.planner.model.MapView;
import de.lardtom.camdrones.planner.model.MissionField;
import de.lardtom.camdrones.planner.model.MissionNames;
import de.lardtom.camdrones.planner.model.MissionTemplates;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.ValueEntry;
import de.lardtom.camdrones.planner.model.WaypointField;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class PlannerScreen extends Screen implements ThemedScreen {
    public static final int DRAG_THRESHOLD = 3;
    public static final int MARKER_HIT = 5;
    public static final int MAP_ROWS_PER_TICK = 64;
    public static final int SUPPORT_TICKS = 20;
    public static final int TASK_COLOR = 0xFF7FD7FF;
    private static final int MARKER = 2;
    private static Boolean mapPreference;

    private enum Drag {
        NONE,
        PRESS,
        PAN,
        WAYPOINT,
        POI
    }

    private record LastStep(int row, TabletState before) {
    }

    private final int slot;
    private final EditorState state;
    private final Optional<PlannerLink> link;
    private final float playerYaw;
    private final MapImage mapImage = new MapImage();
    private final Map<EditorTexts.Footer, FlatButton> footerButtons = new EnumMap<>(EditorTexts.Footer.class);
    private final List<FlatButton> templateButtons = new ArrayList<>();
    private final List<FlatButton> autoButtons = new ArrayList<>();
    private final List<FlatButton> followButtons = new ArrayList<>();
    private EditorTexts texts;
    private EditorLayout layout;
    private char decimal = '.';
    private boolean mapDecided;
    private TextField nameField;
    private FlatButton liveButton;
    private FlatButton previousButton;
    private FlatButton nextButton;
    private FlatButton addButton;
    private FlatButton copyButton;
    private FlatButton deleteButton;
    private FlatButton upButton;
    private FlatButton downButton;
    private FlatButton removeButton;
    private FlatButton addWaypointButton;
    private FlatButton addPoiButton;
    private int pendingUpload = -1;
    private ExitSetting pendingExit;
    private int pendingExitRequest = -1;
    private ViewSetting pendingView;
    private int pendingViewRequest = -1;
    private Drag drag = Drag.NONE;
    private int dragIndex = -1;
    private int pressButton;
    private double pressX;
    private double pressY;
    private boolean syncingName;
    private DroneSupport support = DroneSupport.UNKNOWN;
    private int supportTicks;
    private boolean noticed;
    private int detailPage;
    private TextField entry;
    private int entryRow = -1;
    private ValueEntry.Range entryRange;
    private LastStep lastStep;

    public PlannerScreen(int slot, TabletState tablet, Optional<PlannerLink> link, FlightClass flightClass, double startX, double startY,
                         double startZ, float playerYaw) {
        super(Component.translatable(FieldText.PREFIX + "title"));
        this.slot = slot;
        this.link = link;
        this.playerYaw = playerYaw;
        this.state = new EditorState(tablet, flightClass, startX, startY, startZ, false);
    }

    public EditorState state() {
        return this.state;
    }

    public EditorLayout layout() {
        return this.layout;
    }

    public int slot() {
        return this.slot;
    }

    @Override
    protected void init() {
        PlannerClient.opened(this);
        String reopen = this.entry != null ? this.entry.getValue() : null;
        int reopenRow = this.entryRow;
        this.entry = null;
        this.entryRow = -1;
        this.entryRange = null;
        this.lastStep = null;
        String separator = I18n.get(FieldText.PREFIX + "decimal");
        this.decimal = separator.length() == 1 ? separator.charAt(0) : '.';
        this.texts = EditorCatalog.texts((key, args) -> I18n.get(key, args.toArray()), this.decimal);
        if (!this.mapDecided) {
            boolean wanted = mapPreference != null ? mapPreference : this.width >= EditorLayout.COMPACT_WIDTH;
            if (this.state.mapExpanded() != wanted) {
                this.state.toggleMap();
            }
        }
        this.layout = EditorLayout.compute(this.width, this.height, this.state.mapExpanded(), this.texts, this.font::width);
        if (!this.mapDecided) {
            this.mapDecided = true;
            Rect fitRect = this.layout.mapShown() ? this.layout.map().inset(1) : new Rect(0, 0, 160, 160);
            Mission mission = this.state.mission();
            this.state.view().fit(mission.waypoints(), mission.pointsOfInterest(), this.state.startX(), this.state.startZ(), fitRect);
        }
        this.footerButtons.clear();
        this.templateButtons.clear();
        this.autoButtons.clear();
        this.followButtons.clear();
        EditorLayout l = this.layout;
        String p = FieldText.PREFIX;
        this.liveButton = this.button(l.live(), Component.translatable(p + "live"), FlatButton.Style.NORMAL, this::live);
        this.liveButton.setTooltip(Tooltip.create(Component.translatable(p + "live.tooltip")));
        this.button(l.mapToggle(), Component.translatable(p + (this.state.mapExpanded() ? "map.hide" : "map.show")), FlatButton.Style.NORMAL,
                this::toggleMap).setTooltip(Tooltip.create(Component.translatable(p + "map.tooltip")));
        this.button(l.close(), Component.literal(EditorTexts.CLOSE), FlatButton.Style.NORMAL, this::onClose)
                .setTooltip(Tooltip.create(Component.translatable(p + "close.tooltip")));
        this.previousButton = this.button(l.previous(), Component.literal(EditorTexts.PREVIOUS), FlatButton.Style.NORMAL,
                () -> this.changeMission(() -> this.state.stepMission(-1)));
        this.nextButton = this.button(l.next(), Component.literal(EditorTexts.NEXT), FlatButton.Style.NORMAL,
                () -> this.changeMission(() -> this.state.stepMission(1)));
        this.addButton = this.button(l.missionAdd(), Component.translatable(p + "mission.add"), FlatButton.Style.NORMAL,
                () -> this.changeMission(this.state::addMission));
        this.addButton.setTooltip(Tooltip.create(Component.translatable(p + "mission.add.tooltip", TabletState.MAX_MISSIONS)));
        this.copyButton = this.button(l.missionCopy(), Component.translatable(p + "mission.copy"), FlatButton.Style.NORMAL,
                () -> this.changeMission(this.state::duplicateMission));
        this.copyButton.setTooltip(Tooltip.create(Component.translatable(p + "mission.copy.tooltip")));
        this.deleteButton = this.button(l.missionDelete(), Component.translatable(p + "mission.delete"), FlatButton.Style.DANGER,
                () -> this.changeMission(this.state::deleteMission));
        this.deleteButton.setTooltip(Tooltip.create(Component.translatable(p + "mission.delete.tooltip")));
        Rect n = l.name();
        this.nameField = new TextField(this.font, n.x(), n.y(), n.w(), n.h(), Component.translatable(p + "mission.name"));
        this.nameField.setMaxLength(MissionNames.MAX_LENGTH);
        this.nameField.setResponder(value -> {
            if (!this.syncingName) {
                this.state.rename(value);
            }
        });
        this.addRenderableWidget(this.nameField);
        this.syncName();
        if (l.mapShown()) {
            this.button(l.zoomIn(), Component.literal(EditorTexts.ZOOM_IN), FlatButton.Style.NORMAL, () -> this.zoom(1))
                    .setTooltip(Tooltip.create(Component.translatable(p + "map.zoom_in")));
            this.button(l.zoomOut(), Component.literal(EditorTexts.ZOOM_OUT), FlatButton.Style.NORMAL, () -> this.zoom(-1))
                    .setTooltip(Tooltip.create(Component.translatable(p + "map.zoom_out")));
            this.button(l.recenter(), Component.literal(EditorTexts.RECENTER), FlatButton.Style.NORMAL, this::recenter)
                    .setTooltip(Tooltip.create(Component.translatable(p + "map.recenter")));
        }
        if (l.listShown()) {
            this.addWaypointButton = this.button(l.listAddWaypoint(), Component.translatable(p + "list.add_waypoint"), FlatButton.Style.NORMAL,
                    this::addWaypointFromList);
            this.addWaypointButton.setTooltip(Tooltip.create(Component.translatable(p + "list.add_waypoint.tooltip")));
            this.addPoiButton = this.button(l.listAddPoi(), Component.translatable(p + "list.add_poi"), FlatButton.Style.NORMAL,
                    this::addPoiFromList);
            this.addPoiButton.setTooltip(Tooltip.create(Component.translatable(p + "list.add_poi.tooltip")));
        } else {
            this.addWaypointButton = null;
            this.addPoiButton = null;
        }
        this.upButton = this.button(l.up(), Component.literal(EditorTexts.UP), FlatButton.Style.NORMAL, () -> this.state.moveSelected(-1));
        this.upButton.setTooltip(Tooltip.create(Component.translatable(p + "detail.up")));
        this.downButton = this.button(l.down(), Component.literal(EditorTexts.DOWN), FlatButton.Style.NORMAL, () -> this.state.moveSelected(1));
        this.downButton.setTooltip(Tooltip.create(Component.translatable(p + "detail.down")));
        this.removeButton = this.button(l.remove(), Component.literal(EditorTexts.DELETE), FlatButton.Style.DANGER, this.state::deleteSelected);
        this.removeButton.setTooltip(Tooltip.create(Component.translatable(p + "detail.delete")));
        for (EditorTexts.Footer f : EditorTexts.Footer.values()) {
            EditorTexts.FooterLabel label = this.texts.footer(f);
            FlatButton.Style style = f == EditorTexts.Footer.SEND ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL;
            FlatButton button = this.button(l.footerButton(f), Component.literal(l.footerCompact() ? label.compact() : label.full()), style,
                    () -> this.footer(f));
            button.setTooltip(Tooltip.create(Component.translatable(p + "button." + f.name().toLowerCase(Locale.ROOT) + ".tooltip")));
            this.footerButtons.put(f, button);
        }
        MissionTemplates.Template[] templates = MissionTemplates.Template.values();
        for (int i = 0; i < templates.length; i++) {
            MissionTemplates.Template template = templates[i];
            FlatButton button = this.button(l.templateItems().get(i), Component.translatable(template.labelKey()), FlatButton.Style.NORMAL,
                    () -> this.applyTemplate(template));
            button.setTooltip(Tooltip.create(Component.translatable(template.tooltipKey())));
            this.templateButtons.add(button);
        }
        for (int i = 0; i < AutoShotMenu.SHOTS.size(); i++) {
            AutoShots.Shot shot = AutoShotMenu.SHOTS.get(i);
            FlatButton button = this.button(l.autoItems().get(i), Component.translatable(AutoShotMenu.labelKey(shot)), FlatButton.Style.NORMAL,
                    () -> this.autoShot(shot));
            button.setTooltip(Tooltip.create(Component.translatable(AutoShotMenu.tooltipKey(shot), AutoShotMenu.tooltipArgs(shot).toArray())));
            this.autoButtons.add(button);
        }
        for (int i = 0; i < FollowMenu.STYLES.size(); i++) {
            FollowController.Style style = FollowMenu.STYLES.get(i);
            FlatButton button = this.button(l.followItems().get(i), Component.translatable(FollowMenu.labelKey(style)), FlatButton.Style.NORMAL,
                    () -> this.follow(style));
            button.setTooltip(Tooltip.create(Component.translatable(FollowMenu.tooltipKey(style), FollowMenu.tooltipArgs().toArray())));
            this.followButtons.add(button);
        }
        if (reopen != null && this.layout.detailPage(reopenRow) == this.detailPage()) {
            this.startEntry(reopenRow);
            if (this.entry != null) {
                this.entry.setValue(reopen);
            }
        }
        this.refreshButtons();
    }

    private FlatButton button(Rect r, Component label, FlatButton.Style style, Runnable action) {
        return this.addRenderableWidget(new FlatButton(r.x(), r.y(), r.w(), r.h(), label, style, () -> {
            action.run();
            this.refreshButtons();
        }));
    }

    private void refreshButtons() {
        boolean editable = this.state.editable();
        Mission mission = this.state.mission();
        this.liveButton.active = this.link.isPresent();
        this.previousButton.active = this.state.missionCount() > 1;
        this.nextButton.active = this.state.missionCount() > 1;
        this.addButton.active = this.state.missionCount() < TabletState.MAX_MISSIONS;
        this.copyButton.active = this.state.missionCount() < TabletState.MAX_MISSIONS;
        this.nameField.setEditable(true);
        boolean waypoint = this.state.selection().kind() == EditorState.Kind.WAYPOINT && !this.state.multiple();
        int index = this.state.selection().index();
        this.upButton.active = editable && waypoint && index > 0;
        this.downButton.active = editable && waypoint && index < mission.size() - 1;
        this.removeButton.active = editable && this.state.selection().kind() != EditorState.Kind.NONE;
        if (this.addWaypointButton != null) {
            this.addWaypointButton.active = editable && mission.size() < MissionLimits.MAX_WAYPOINTS;
        }
        if (this.addPoiButton != null) {
            this.addPoiButton.active = editable
                    && mission.pointsOfInterest().size() < MissionLimits.MAX_POINTS_OF_INTEREST;
        }
        boolean commands = PlannerClient.commands().available() && this.link.isPresent();
        this.footerButtons.get(EditorTexts.Footer.SEND).active = this.link.isPresent() && !mission.isEmpty() && editable;
        this.footerButtons.get(EditorTexts.Footer.START).active = commands;
        this.footerButtons.get(EditorTexts.Footer.PAUSE).active = commands;
        this.footerButtons.get(EditorTexts.Footer.STOP).active = commands;
        boolean autoShots = PlannerClient.commands().autoShotAvailable() && this.link.isPresent();
        this.footerButtons.get(EditorTexts.Footer.AUTO).active = autoShots;
        if (!autoShots && this.state.autoMenu()) {
            this.state.autoMenu(false);
        }
        boolean follow = PlannerClient.commands().followAvailable() && this.link.isPresent();
        this.footerButtons.get(EditorTexts.Footer.FOLLOW).active = follow;
        if (!follow && this.state.followMenu()) {
            this.state.followMenu(false);
        }
        this.footerButtons.get(EditorTexts.Footer.TEMPLATE).active = editable;
        boolean menu = this.state.templateMenu();
        for (FlatButton button : this.templateButtons) {
            button.visible = menu;
            button.active = menu;
        }
        boolean autoMenu = this.state.autoMenu();
        for (FlatButton button : this.autoButtons) {
            button.visible = autoMenu;
            button.active = autoMenu;
        }
        boolean followMenu = this.state.followMenu();
        for (FlatButton button : this.followButtons) {
            button.visible = followMenu;
            button.active = followMenu;
        }
    }

    private void syncName() {
        this.syncingName = true;
        this.nameField.setValue(this.state.entry().name());
        this.nameField.hint(Component.literal(MissionNames.defaultName(this.state.missionIndex() + 1)));
        this.syncingName = false;
    }

    private void changeMission(BooleanSupplier change) {
        if (change.getAsBoolean()) {
            this.syncName();
            Mission mission = this.state.mission();
            if (this.layout.mapShown() && !mission.isEmpty()) {
                this.state.view().fit(mission.waypoints(), mission.pointsOfInterest(), this.state.startX(), this.state.startZ(),
                        this.layout.map().inset(1));
            }
        }
    }

    private void toggleMap() {
        this.state.toggleMap();
        mapPreference = this.state.mapExpanded();
        this.rebuildWidgets();
    }

    private void live() {
        if (this.link.isEmpty()) {
            this.state.status(EditorState.Status.error("status.not_linked"));
            return;
        }
        if (PlannerClient.liveView(PlannerClient.pairedDrone(this.link))) {
            this.onClose();
            return;
        }
        this.state.status(EditorState.Status.error("live.unavailable"));
    }

    public Optional<ExitSetting> shownExit() {
        Optional<ExitSetting> actual = PlannerClient.pairedDrone(this.link).flatMap(ExitSettings::read);
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
        if (this.link.isEmpty()) {
            this.state.status(EditorState.Status.error("status.not_linked"));
            return;
        }
        ExitSetting next = this.shownExit().orElse(ExitSetting.HOVER).step(direction);
        OptionalInt id = PlannerClient.sendExit(this.link.get().serial(), next, this::onExitReply);
        if (id.isEmpty()) {
            this.state.status(EditorState.Status.error("status.offline"));
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
        String key = reply.result().messageKey().substring(FieldText.PREFIX.length());
        this.state.status(reply.result().ok() ? EditorState.Status.info(key) : EditorState.Status.error(key));
    }

    public Optional<ViewSetting> shownView() {
        Optional<ViewSetting> actual = PlannerClient.pairedDrone(this.link).flatMap(ViewSettings::read);
        if (this.pendingView == null) {
            return actual;
        }
        if (this.pendingViewRequest < 0 && actual.filter(this.pendingView::equals).isPresent()) {
            this.pendingView = null;
            return actual;
        }
        return Optional.of(this.pendingView);
    }

    private void stepView(int direction) {
        if (this.link.isEmpty()) {
            this.state.status(EditorState.Status.error("status.not_linked"));
            return;
        }
        ViewSetting next = this.shownView().orElse(ViewSetting.OSD).step(direction);
        OptionalInt id = PlannerClient.sendView(this.link.get().serial(), next, this::onViewReply);
        if (id.isEmpty()) {
            this.state.status(EditorState.Status.error("status.offline"));
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
        String key = ViewSetting.messageKey(reply.result()).substring(FieldText.PREFIX.length());
        this.state.status(reply.result().ok() ? EditorState.Status.info(key) : EditorState.Status.error(key));
    }

    private void zoom(int steps) {
        Rect inner = this.layout.map().inset(1);
        this.state.view().zoomBy(steps, inner.centerX(), inner.centerY(), inner);
    }

    private void recenter() {
        Mission mission = this.state.mission();
        Optional<Entity> drone = PlannerClient.linkedDrone(this.link);
        if (mission.isEmpty() && mission.pointsOfInterest().isEmpty()) {
            double x = drone.map(Entity::getX).orElse(this.state.startX());
            double z = drone.map(Entity::getZ).orElse(this.state.startZ());
            this.state.view().center(x, z);
            return;
        }
        this.state.view().fit(mission.waypoints(), mission.pointsOfInterest(), this.state.startX(), this.state.startZ(), this.layout.map().inset(1));
    }

    private double groundAt(double x, double z) {
        ClientLevel level = this.minecraft.level;
        if (level != null && level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        }
        return this.state.startY();
    }

    private void addWaypointFromList() {
        double groundY = this.groundAt(this.state.startX(), this.state.startZ());
        Waypoint guess = this.state.nextWaypointGuess(groundY);
        this.state.addWaypoint(guess);
        this.scrollToSelection();
    }

    private void addPoiFromList() {
        Optional<Waypoint> selected = this.state.selectedWaypoint();
        double x = selected.map(Waypoint::x).orElse(this.state.view().centerX());
        double z = selected.map(Waypoint::z).orElse(this.state.view().centerZ());
        this.state.addPoi(x, this.groundAt(x, z), z);
        this.scrollToSelection();
    }

    private void applyTemplate(MissionTemplates.Template template) {
        double groundY = this.state.selectedPoi().map(PointOfInterest::y)
                .orElseGet(() -> this.groundAt(this.state.view().centerX(), this.state.view().centerZ()));
        this.state.applyTemplate(template, this.state.anchor(groundY, this.playerYaw));
        this.scrollToSelection();
    }

    private void footer(EditorTexts.Footer which) {
        switch (which) {
            case SEND -> {
                if (this.link.isEmpty()) {
                    this.state.status(EditorState.Status.error("status.not_linked"));
                    return;
                }
                OptionalInt id = PlannerClient.upload(this.link.get(), this.state.mission());
                if (id.isEmpty()) {
                    this.state.status(EditorState.Status.error("status.offline"));
                    return;
                }
                this.pendingUpload = id.getAsInt();
                this.state.status(EditorState.Status.info("status.sending"));
            }
            case START -> this.command(MissionCommands.Command.START);
            case PAUSE -> this.command(MissionCommands.Command.PAUSE);
            case STOP -> this.command(MissionCommands.Command.STOP);
            case AUTO -> this.state.autoMenu(!this.state.autoMenu());
            case FOLLOW -> this.state.followMenu(!this.state.followMenu());
            case TEMPLATE -> this.state.templateMenu(!this.state.templateMenu());
            case SAVE -> this.save();
        }
    }

    private void command(MissionCommands.Command command) {
        if (this.link.isEmpty() || !PlannerClient.commands().send(this.link.get().serial(), command)) {
            this.state.status(EditorState.Status.error("status.command_failed"));
            return;
        }
        this.state.status(EditorState.Status.info("status.command." + command.name().toLowerCase(Locale.ROOT)));
    }

    private void autoShot(AutoShots.Shot shot) {
        this.state.autoMenu(false);
        if (this.link.isEmpty() || !PlannerClient.commands().autoShot(this.link.get().serial(), shot)) {
            this.state.status(EditorState.Status.error("status.command_failed"));
            return;
        }
        this.state.status(EditorState.Status.info("status.auto_shot"));
    }

    private void follow(FollowController.Style style) {
        this.state.followMenu(false);
        if (this.link.isEmpty() || this.minecraft.player == null) {
            this.state.status(EditorState.Status.error("status.command_failed"));
            return;
        }
        UUID serial = this.link.get().serial();
        if (PlannerClient.flies(serial)) {
            PlannerClient.followStyle(serial, style);
            this.state.status(EditorState.Status.info("status.follow_switch"));
            return;
        }
        if (!PlannerClient.commands().follow(serial, this.minecraft.player.getUUID(), style)) {
            this.state.status(EditorState.Status.error("status.command_failed"));
            return;
        }
        PlannerClient.followStyle(serial, style);
        this.state.status(EditorState.Status.info("status.follow"));
    }

    private void save() {
        if (PlannerClient.save(this.slot, this.state.tablet())) {
            this.state.markSaved();
            this.state.status(EditorState.Status.info("status.saved"));
        } else {
            this.state.status(EditorState.Status.error("status.offline"));
        }
    }

    boolean onUploadReply(MissionUpload.Reply reply) {
        if (reply.requestId() != this.pendingUpload) {
            return false;
        }
        this.pendingUpload = -1;
        String key = reply.result().messageKey().substring(FieldText.PREFIX.length());
        this.state.status(reply.result().ok() ? EditorState.Status.info(key) : EditorState.Status.error(key, Integer.toString(reply.waypoint() + 1)));
        return true;
    }

    private void scrollToSelection() {
        int row = this.state.rowOf(this.state.selection());
        int visible = this.layout.visibleRows();
        if (row < 0 || visible <= 0) {
            return;
        }
        if (row < this.state.listScroll()) {
            this.state.listScroll(row, visible);
        } else if (row >= this.state.listScroll() + visible) {
            this.state.listScroll(row - visible + 1, visible);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.layout != null && this.layout.mapShown()) {
            Rect inner = this.layout.map().inset(1);
            this.mapImage.ensure(inner.w(), inner.h());
            this.mapImage.request(this.state.view());
            this.mapImage.build(this.minecraft.level, MAP_ROWS_PER_TICK);
        }
        if (this.supportTicks-- <= 0) {
            this.supportTicks = SUPPORT_TICKS;
            this.refreshSupport();
        }
        this.refreshButtons();
    }

    public DroneSupport support() {
        return this.support;
    }

    public int detailPage() {
        return this.layout != null && this.layout.detailPaged() && this.state.selection().kind() == EditorState.Kind.WAYPOINT ? this.detailPage : 0;
    }

    public void detailPage(int page) {
        this.detailPage = Math.clamp(page, 0, 1);
    }

    public void refreshSupport() {
        this.support = PlannerSupport.of(PlannerClient.pairedDrone(this.link));
        if (!this.noticed && this.support.known()) {
            this.noticed = true;
            this.support.notice().ifPresent(text -> this.state.status(new EditorState.Status(text, !this.support.gpsFix())));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.state.dirty()) {
            this.save();
        }
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        this.mapImage.close();
        PlannerClient.closed(this);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        EditorLayout l = this.layout;
        Rect w = l.window();
        ThemeDraw.window(g, w.x(), w.y(), w.right(), w.bottom());
        ThemeDraw.headerBar(g, w.x(), w.y(), w.right(), l.header().h());
        this.drawTitle(g);
        this.drawStrip(g);
        if (l.listShown()) {
            this.drawList(g, mouseX, mouseY);
        }
        if (l.mapShown()) {
            this.drawMap(g, mouseX, mouseY);
        }
        this.drawDetail(g, mouseX, mouseY);
        this.drawSettings(g, mouseX, mouseY);
        ThemeDraw.footerBar(g, w.x(), l.footer().y(), w.right(), w.bottom());
        this.drawStatus(g);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, w.x(), w.y(), w.right(), w.bottom());
        this.drawTooltips(g, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphicsExtractor g) {
        Rect t = this.layout.title();
        int y = Metrics.textY(t.y(), t.h() - 1);
        int titleWidth = this.font.width(this.title);
        ThemeText.fitted(g, this.font, this.title, t.x(), y, t.w(), Palette.TEXT);
        int x = t.x() + titleWidth + 8;
        if (x < t.right() - 12) {
            Component linkText = this.link.map(value -> Component.translatable(FieldText.PREFIX + "header.linked",
                            value.name().isEmpty() ? value.shortSerial() : value.name()))
                    .orElseGet(() -> Component.translatable(FieldText.PREFIX + "header.unlinked"));
            int color = this.link.isPresent() ? Palette.TEXT_MUTED : Palette.WARNING;
            if (this.link.isPresent()) {
                for (FieldText part : this.support.header()) {
                    linkText = Component.empty().append(linkText).append(Component.literal(" · ")).append(this.text(part));
                }
                if (!this.support.gpsFix()) {
                    color = Palette.WARNING;
                }
            }
            ThemeText.fitted(g, this.font, linkText, x, y, t.right() - x, color);
        }
    }

    private void drawStrip(GuiGraphicsExtractor g) {
        Rect c = this.layout.counter();
        String counter = (this.state.missionIndex() + 1) + "/" + this.state.missionCount();
        ThemeText.textCentered(g, this.font, counter, c.centerX(), Metrics.textY(c.y(), c.h()), Palette.TEXT_MUTED);
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EditorLayout l = this.layout;
        Rect list = l.list();
        ThemeDraw.panel(g, list.x(), list.y(), list.right(), list.bottom());
        Rect rows = l.listRows();
        int visible = l.visibleRows();
        this.state.listScroll(this.state.listScroll(), visible);
        if (this.state.rowCount() == 0) {
            ThemeText.wrapped(g, this.font, Component.translatable(FieldText.PREFIX + "list.empty"), rows.x() + 4, rows.y() + 2, rows.w() - 8,
                    Palette.TEXT_FAINT);
            return;
        }
        Mission mission = this.state.mission();
        int hovered = l.listRowAt(mouseX, mouseY);
        for (int i = 0; i < visible; i++) {
            int index = this.state.listScroll() + i;
            if (index >= this.state.rowCount()) {
                break;
            }
            Rect r = l.listRow(i);
            EditorState.Selection row = this.state.row(index);
            ThemeDraw.selection(g, r.x(), r.y(), r.right(), r.bottom(), this.state.selected(row), hovered == i);
            int ty = Metrics.textY(r.y(), r.h());
            String text;
            int color;
            int reserve = 0;
            if (row.kind() == EditorState.Kind.WAYPOINT) {
                Waypoint w = mission.waypoint(row.index());
                text = I18n.get(FieldText.PREFIX + "list.waypoint", row.index() + 1, FieldText.number(w.y() - this.state.startY(), 0, this.decimal),
                        I18n.get(this.speedKey(w), FieldText.number(mission.speedTo(row.index()), 1, this.decimal)));
                color = Palette.TEXT;
                WaypointTasks.Due due = WaypointTasks.due(mission, row.index());
                if (due.any()) {
                    boolean skipped = this.support.skip(due).skipped();
                    ThemeText.textRight(g, this.font, skipped ? "!" : "+", r.right() - Metrics.SCROLLBAR_WIDTH - 2, ty,
                            skipped ? Palette.WARNING : TASK_COLOR);
                    reserve = 8;
                }
            } else {
                PointOfInterest poi = mission.pointsOfInterest().get(row.index());
                text = I18n.get(FieldText.PREFIX + "list.poi", row.index() + 1, FieldText.number(poi.radius(), 0, this.decimal));
                color = 0xFF4EA1F3;
            }
            ThemeText.fitted(g, this.font, text, r.x() + 5, ty, r.w() - 8 - reserve, color);
        }
        ThemeDraw.scrollbar(g, rows.right() - Metrics.SCROLLBAR_WIDTH, rows.y(), visible * l.row(), this.state.rowCount() * l.row(),
                this.state.listScroll() * (double) l.row(), false);
    }

    private String speedKey(Waypoint w) {
        return FieldText.PREFIX + (w.speedDmS() == 0 ? "list.speed_base" : "list.speed");
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect r = this.layout.map();
        ThemeDraw.framedBox(g, r.x(), r.y(), r.right(), r.bottom(), Palette.FIELD, Palette.BORDER);
        Rect inner = r.inset(1);
        g.enableScissor(inner.x(), inner.y(), inner.right(), inner.bottom());
        this.mapImage.draw(g, inner, this.state.view(), this.minecraft.level);
        MapView view = this.state.view();
        Mission mission = this.state.mission();
        List<PointOfInterest> points = mission.pointsOfInterest();
        List<MapLabel> labels = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            PointOfInterest poi = points.get(i);
            double sx = view.screenX(poi.x(), inner);
            double sy = view.screenY(poi.z(), inner);
            double radius = poi.radius() / view.blocksPerPixel();
            boolean selected = this.state.selected(EditorState.Selection.poi(i));
            int color = selected ? Palette.ACCENT : 0xFF4EA1F3;
            if (radius > 2.0) {
                circle(g, sx, sy, radius, Palette.withAlpha(color, 200));
            }
            square(g, sx, sy, MARKER + 1, color);
            labels.add(new MapLabel("P" + (i + 1), sx, sy, color, MARKER + 1, selected ? 0 : 1));
        }
        List<Waypoint> waypoints = mission.waypoints();
        boolean fromStart = mission.smooth() && !waypoints.isEmpty();
        List<double[]> path = CurvedTurn.path(mission, fromStart ? new double[]{this.state.startX(), this.state.startY(), this.state.startZ()} : null);
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1);
            double[] b = path.get(i);
            line(g, view.screenX(a[0], inner), view.screenY(a[2], inner), view.screenX(b[0], inner), view.screenY(b[2], inner),
                    Palette.withAlpha(Palette.ACCENT, fromStart && i == 1 ? 110 : 220));
        }
        List<double[]> loop = CurvedTurn.loopPath(mission);
        for (int i = 1; i < loop.size(); i++) {
            double[] a = loop.get(i - 1);
            double[] b = loop.get(i);
            line(g, view.screenX(a[0], inner), view.screenY(a[2], inner), view.screenX(b[0], inner), view.screenY(b[2], inner),
                    Palette.withAlpha(Palette.ACCENT, 110));
        }
        double startX = view.screenX(this.state.startX(), inner);
        double startY = view.screenY(this.state.startZ(), inner);
        square(g, startX, startY, MARKER + 1, Palette.SUCCESS);
        labels.add(new MapLabel("H", startX, startY, Palette.SUCCESS, MARKER + 1, 1));
        if (this.minecraft.player != null) {
            double px = view.screenX(this.minecraft.player.getX(), inner);
            double py = view.screenY(this.minecraft.player.getZ(), inner);
            arrow(g, px, py, this.minecraft.player.getYRot(), Palette.TEXT);
        }
        PlannerClient.linkedDrone(this.link).ifPresent(drone -> square(g, view.screenX(drone.getX(), inner), view.screenY(drone.getZ(), inner),
                MARKER + 1, Palette.ERROR));
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.get(i);
            double sx = view.screenX(w.x(), inner);
            double sy = view.screenY(w.z(), inner);
            boolean selected = this.state.selected(EditorState.Selection.waypoint(i));
            square(g, sx, sy, selected ? MARKER + 2 : MARKER + 1, Palette.SHADOW);
            square(g, sx, sy, selected ? MARKER + 1 : MARKER, selected ? Palette.ACCENT : Palette.TEXT);
            labels.add(new MapLabel(Integer.toString(i + 1), sx, sy, selected ? Palette.ACCENT : Palette.TEXT, MARKER + 2, selected ? 0 : 2));
        }
        String scale = I18n.get(FieldText.PREFIX + "map.scale", FieldText.number(view.blocksPerPixel(), view.blocksPerPixel() < 1.0 ? 2 : 0,
                this.decimal));
        Rect north = new Rect(inner.x() + 3, inner.y() + 3, this.font.width("N"), this.font.lineHeight);
        Rect scaleBox = new Rect(inner.x() + 3, inner.bottom() - 11, this.font.width(scale), this.font.lineHeight);
        labels.sort(Comparator.comparingInt(MapLabel::priority));
        List<MapLabels.Anchor> anchors = new ArrayList<>();
        for (MapLabel label : labels) {
            anchors.add(new MapLabels.Anchor(label.x(), label.y(), this.font.width(label.text()), this.font.lineHeight, label.marker()));
        }
        List<Optional<Rect>> placed = MapLabels.place(anchors, inner, List.of(north, scaleBox));
        for (int i = 0; i < labels.size(); i++) {
            MapLabel label = labels.get(i);
            placed.get(i).ifPresent(box -> ThemeText.text(g, this.font, label.text(), box.x(), box.y(), label.color()));
        }
        g.disableScissor();
        ThemeText.text(g, this.font, "N", north.x(), north.y(), Palette.TEXT_MUTED);
        ThemeText.text(g, this.font, scale, scaleBox.x(), scaleBox.y(), Palette.TEXT_MUTED);
    }

    MapImage mapImage() {
        return this.mapImage;
    }

    private record MapLabel(String text, double x, double y, int color, int marker, int priority) {
    }

    private static void square(GuiGraphicsExtractor g, double x, double y, int half, int color) {
        int cx = (int) Math.round(x);
        int cy = (int) Math.round(y);
        g.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color);
    }

    private static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.hypot(dx, dy);
        if (length < 0.5 || !Double.isFinite(length) || length > 4096.0) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x0, (float) y0);
        pose.rotate((float) Math.atan2(dy, dx));
        g.fill(0, -1, (int) Math.ceil(length), 1, color);
        pose.popMatrix();
    }

    private static void circle(GuiGraphicsExtractor g, double cx, double cy, double radius, int color) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;
            line(g, cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius, cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, color);
        }
    }

    private static void arrow(GuiGraphicsExtractor g, double x, double y, float yaw, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.rotate((float) Math.toRadians(yaw + 180.0F));
        g.fill(-1, -5, 2, 4, color);
        g.fill(-3, -3, 4, -1, color);
        pose.popMatrix();
    }

    private void drawDetail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EditorLayout l = this.layout;
        Rect d = l.detail();
        ThemeDraw.panel(g, d.x(), d.y(), d.right(), d.bottom());
        Rect title = l.detailTitle();
        int ty = Metrics.textY(title.y(), title.h());
        EditorState.Selection selection = this.state.selection();
        Mission mission = this.state.mission();
        if (!this.state.editable()) {
            ThemeText.fitted(g, this.font, Component.translatable(FieldText.PREFIX + "detail.locked"), title.x(), ty, title.w(), Palette.WARNING);
            return;
        }
        int page = this.detailPage();
        int hovered = l.detailRowAt(mouseX, mouseY, page);
        switch (selection.kind()) {
            case WAYPOINT -> {
                FieldText heading = this.state.multiple() ? EditorCatalog.waypointsTitle(this.state.selectedWaypoints().size())
                        : EditorCatalog.waypointTitle(selection.index(), mission.size());
                ThemeText.fitted(g, this.font, this.text(heading), title.x(), ty, title.w(), Palette.ACCENT);
                WaypointField[] fields = WaypointField.values();
                for (int i = 0; i < fields.length; i++) {
                    WaypointField field = fields[i];
                    if (l.detailPage(i) != page) {
                        continue;
                    }
                    if (this.entry != null && this.entryRow == i) {
                        this.labelOnly(g, l.detailRows().get(i), l.detailLabelWidth(), Component.translatable(field.labelKey()));
                        continue;
                    }
                    boolean mixed = this.state.mixed(field);
                    DroneSupport.Skip skip = mixed ? DroneSupport.Skip.NONE : this.skip(field, mission, selection.index());
                    Component value = mixed ? Component.literal(EditorTexts.MIXED) : this.fieldValue(field, mission, selection.index());
                    int color = skip.skipped() ? Palette.WARNING : !mixed && this.repeated(field, mission, selection.index()) ? TASK_COLOR : Palette.TEXT;
                    this.valueRow(g, l.detailRows().get(i), l.detailLabelWidth(), Component.translatable(field.labelKey()), value,
                            this.state.enabled(field), hovered == i, color, skip.skipped());
                }
                if (l.detailPaged()) {
                    Rect toggle = l.pageToggle();
                    boolean hot = toggle.contains(mouseX, mouseY);
                    ThemeDraw.framedBox(g, toggle.x(), toggle.y(), toggle.right(), toggle.bottom(), hot ? Palette.FIELD_HOVER : Palette.FIELD,
                            hot ? Palette.BORDER_STRONG : Palette.BORDER);
                    int tasks = WaypointTasks.due(mission, selection.index()).any() ? TASK_COLOR : Palette.ACCENT;
                    ThemeText.fittedCentered(g, this.font, Component.translatable(page == 0 ? EditorLayout.TASK_PAGE_KEY : EditorLayout.FLIGHT_PAGE_KEY),
                            toggle.centerX(), Metrics.textY(toggle.y(), toggle.h()), toggle.w() - 4, page == 0 ? tasks : Palette.ACCENT);
                }
            }
            case POI -> {
                PointOfInterest poi = mission.pointsOfInterest().get(selection.index());
                ThemeText.fitted(g, this.font, this.text(EditorCatalog.poiTitle(selection.index())), title.x(), ty, title.w(), 0xFF4EA1F3);
                if (this.entry != null && this.entryRow == 0) {
                    this.labelOnly(g, l.detailRows().get(0), l.detailLabelWidth(), Component.translatable(EditorCatalog.POI_HEIGHT_KEY));
                } else {
                    this.valueRow(g, l.detailRows().get(0), l.detailLabelWidth(), Component.translatable(EditorCatalog.POI_HEIGHT_KEY),
                            this.text(EditorCatalog.poiHeight(poi.y() - this.state.startY(), this.decimal)), true, hovered == 0);
                }
                if (this.entry != null && this.entryRow == 1) {
                    this.labelOnly(g, l.detailRows().get(1), l.detailLabelWidth(), Component.translatable(EditorCatalog.POI_RADIUS_KEY));
                } else {
                    this.valueRow(g, l.detailRows().get(1), l.detailLabelWidth(), Component.translatable(EditorCatalog.POI_RADIUS_KEY),
                            this.text(EditorCatalog.poiRadius(poi.radius(), this.decimal)), true, hovered == 1);
                }
                Rect hint = l.detailRows().get(2);
                ThemeText.wrapped(g, this.font, Component.translatable(FieldText.PREFIX + "detail.poi_hint"), hint.x(), hint.y() + 2, hint.w(),
                        Palette.TEXT_FAINT);
            }
            case NONE -> {
                ThemeText.fitted(g, this.font, Component.translatable(FieldText.PREFIX + "detail.none"), title.x(), ty, title.w(), Palette.TEXT_MUTED);
                Rect hint = l.detailRows().getFirst();
                ThemeText.wrapped(g, this.font, Component.translatable(FieldText.PREFIX + (l.mapShown() ? "detail.hint_map" : "detail.hint_list")),
                        hint.x(), hint.y() + 2, hint.w(), Palette.TEXT_FAINT);
            }
        }
    }

    private void drawSettings(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EditorLayout l = this.layout;
        FlightTimeEstimate.Preview preview = this.state.preview();
        Rect pr = l.preview();
        Component previewText = this.text(EditorCatalog.preview(preview.length(), preview.seconds(), preview.charge(), this.decimal));
        ThemeText.fitted(g, this.font, previewText, pr.x(), pr.y() + 1, pr.w(), preview.charge() > 1.0 ? Palette.ERROR : Palette.TEXT_MUTED);
        Mission mission = this.state.mission();
        int tasks = WaypointTasks.all(mission).size();
        Component summary = null;
        int summaryColor = TASK_COLOR;
        if (this.link.isPresent() && !this.support.gpsFix()) {
            summary = this.support.notice().map(this::text).orElse(null);
            summaryColor = Palette.WARNING;
        } else if (tasks > 0) {
            int skipped = this.support.skipped(mission);
            summary = skipped > 0 ? Component.translatable(FieldText.PREFIX + "support.preview.skipped", tasks, skipped)
                    : Component.translatable(FieldText.PREFIX + "support.preview.tasks", tasks);
            summaryColor = skipped > 0 ? Palette.WARNING : TASK_COLOR;
        }
        if (summary != null && this.font.width(previewText) + 12 + this.font.width(summary) <= pr.w()) {
            ThemeText.textRight(g, this.font, summary, pr.right(), pr.y() + 1, summaryColor);
        }
        int hovered = l.settingAt(mouseX, mouseY);
        boolean editable = this.state.editable();
        MissionField[] fields = MissionField.values();
        for (int i = 0; i < fields.length; i++) {
            this.valueRow(g, l.settings().get(i), l.settingLabelWidth(), Component.translatable(fields[i].labelKey()),
                    this.text(fields[i].value(mission, this.decimal)), editable, hovered == i);
        }
        this.valueRow(g, l.settings().get(EditorLayout.SETTING_GROUND), l.settingLabelWidth(), Component.translatable(EditorCatalog.GROUND_KEY),
                this.text(EditorCatalog.groundValue(this.state.tablet().groundHeight())), true, hovered == EditorLayout.SETTING_GROUND);
        this.valueRow(g, l.settings().get(EditorLayout.SETTING_RECORD), l.settingLabelWidth(), Component.translatable(EditorCatalog.RECORD_KEY),
                this.text(EditorCatalog.recordValue(PlannerClient.recorder().active(), PlannerClient.recorder().recorded())), true,
                hovered == EditorLayout.SETTING_RECORD);
        this.valueRow(g, l.settings().get(EditorLayout.SETTING_EXIT), l.settingLabelWidth(), Component.translatable(ExitSetting.LABEL_KEY),
                this.text(ExitSetting.value(this.shownExit())), this.link.isPresent(), hovered == EditorLayout.SETTING_EXIT);
        this.valueRow(g, l.settings().get(EditorLayout.SETTING_VIEW), l.settingLabelWidth(), Component.translatable(ViewSetting.LABEL_KEY),
                this.text(ViewSetting.value(this.shownView())), this.link.isPresent(), hovered == EditorLayout.SETTING_VIEW);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        Rect s = this.layout.status();
        Optional<EditorState.Status> status = this.state.status();
        if (s.w() < 12) {
            return;
        }
        Component text;
        int color;
        if (status.isPresent()) {
            text = this.text(status.get().text());
            color = status.get().error() ? Palette.ERROR : Palette.SUCCESS;
        } else if (this.state.dirty()) {
            text = Component.translatable(FieldText.PREFIX + "status.unsaved");
            color = Palette.TEXT_FAINT;
        } else {
            return;
        }
        ThemeText.fitted(g, this.font, text, s.x(), Metrics.textY(s.y(), s.h()), s.w(), color);
    }

    private void valueRow(GuiGraphicsExtractor g, Rect row, int labelWidth, Component label, Component value, boolean active, boolean hovered) {
        this.valueRow(g, row, labelWidth, label, value, active, hovered, Palette.TEXT, false);
    }

    private void labelOnly(GuiGraphicsExtractor g, Rect row, int labelWidth, Component label) {
        ThemeText.fitted(g, this.font, label, row.x(), Metrics.textY(row.y(), row.h()), labelWidth - EditorLayout.LABEL_GAP, Palette.TEXT_MUTED);
    }

    private Component fieldValue(WaypointField field, Mission mission, int index) {
        Waypoint w = mission.waypoint(index);
        if (field == WaypointField.ACCESSORY && w.accessory() == Waypoint.Accessory.NONE) {
            int source = WaypointTasks.repeatSource(mission, index);
            if (source >= 0) {
                return this.text(EditorCatalog.repeatedAccessory(mission.waypoint(source).accessory().number(), source));
            }
            int timed = WaypointTasks.timedSource(mission, index);
            if (timed >= 0) {
                return this.text(EditorCatalog.timedAccessory(mission.waypoint(timed).accessory().number()));
            }
        }
        return this.text(field.value(w, this.decimal, this.state.startY()));
    }

    private boolean repeated(WaypointField field, Mission mission, int index) {
        return field == WaypointField.ACCESSORY && (WaypointTasks.repeatSource(mission, index) >= 0 || WaypointTasks.timedSource(mission, index) >= 0);
    }

    private DroneSupport.Skip skip(WaypointField field, Mission mission, int index) {
        Waypoint w = mission.waypoint(index);
        return switch (field) {
            case LIGHT -> this.support.light(w.light());
            case ACCESSORY -> this.support.accessory(mission, index);
            default -> DroneSupport.Skip.NONE;
        };
    }

    private List<Component> taskLines(WaypointField field, Mission mission, int index) {
        List<Component> lines = new ArrayList<>();
        DroneSupport.Skip skip = this.skip(field, mission, index);
        if (skip.skipped()) {
            lines.add(Component.translatable(FieldText.PREFIX + "support.skipped", Component.translatable(skip.key()))
                    .withStyle(style -> style.withColor(Palette.WARNING & 0xFFFFFF)));
        } else if (skip == DroneSupport.Skip.UNKNOWN) {
            lines.add(Component.translatable(DroneSupport.Skip.UNKNOWN.key()));
        }
        if (field == WaypointField.ACCESSORY) {
            int number = WaypointTasks.accessoryAt(mission, index);
            this.support.actionName(number).ifPresent(name -> lines.add(Component.translatable(FieldText.PREFIX + "support.action_name", number,
                    Component.translatable(name))));
            int source = WaypointTasks.repeatSource(mission, index);
            if (source >= 0) {
                lines.add(Component.translatable(FieldText.PREFIX + "support.repeated", source + 1));
            }
            int timed = WaypointTasks.timedSource(mission, index);
            if (timed < 0 && WaypointTasks.continues(mission, index)) {
                timed = WaypointTasks.timedAt(mission, index - 1).map(WaypointTasks.Timed::source).orElse(-1);
            }
            if (timed >= 0) {
                int ticks = mission.waypoint(timed).everyTicks();
                lines.add(Component.translatable(FieldText.PREFIX + "support.timed", timed + 1, ticks, WaypointField.seconds(ticks, this.decimal)));
            }
        }
        if (field == WaypointField.EVERY || WaypointTasks.timedAt(mission, index).isPresent() && field != WaypointField.LIGHT) {
            lines.add(Component.translatable(FieldText.PREFIX + "support.every_limit").withStyle(style -> style.withColor(TASK_COLOR & 0xFFFFFF)));
        }
        return lines;
    }

    private void valueRow(GuiGraphicsExtractor g, Rect row, int labelWidth, Component label, Component value, boolean active, boolean hovered,
                          int valueColor, boolean marked) {
        int ty = Metrics.textY(row.y(), row.h());
        ThemeText.fitted(g, this.font, label, row.x(), ty, labelWidth - EditorLayout.LABEL_GAP, active ? Palette.TEXT_MUTED : Palette.TEXT_DISABLED);
        Rect box = EditorLayout.valueBox(row, labelWidth);
        boolean hot = active && hovered;
        ThemeDraw.framedBox(g, box.x(), box.y(), box.right(), box.bottom(), active ? (hot ? Palette.FIELD_HOVER : Palette.FIELD) : Palette.DISABLED,
                hot ? Palette.BORDER_STRONG : Palette.BORDER);
        int arrowColor = !active ? Palette.TEXT_DISABLED : hot ? Palette.ACCENT : Palette.TEXT_FAINT;
        ThemeText.text(g, this.font, EditorTexts.PREVIOUS, box.x() + 3, ty, arrowColor);
        ThemeText.textRight(g, this.font, EditorTexts.NEXT, box.right() - 3, ty, arrowColor);
        ThemeText.fittedCentered(g, this.font, value, box.centerX(), ty, EditorLayout.valueTextWidth(box), active ? valueColor : Palette.TEXT_DISABLED);
        if (marked) {
            ThemeText.textRight(g, this.font, "!", box.x() - 1, ty, Palette.WARNING);
        }
    }

    public List<Component> detailTooltip(int row) {
        List<Component> lines = new ArrayList<>();
        if (row < 0 || row >= WaypointField.values().length || this.state.selection().kind() != EditorState.Kind.WAYPOINT) {
            return lines;
        }
        WaypointField field = WaypointField.values()[row];
        lines.add(Component.translatable(field.tooltipKey()));
        if (field.task() && this.state.editable()) {
            lines.addAll(this.taskLines(field, this.state.mission(), this.state.selection().index()));
        }
        if (this.state.multiple()) {
            lines.add(ThemeText.muted(Component.translatable(FieldText.PREFIX + "detail.multi", this.state.selectedWaypoints().size())));
        }
        if (this.state.typeable(row)) {
            lines.add(ThemeText.muted(Component.translatable(FieldText.PREFIX + "detail.type_hint")));
        }
        return lines;
    }

    private void drawTooltips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EditorLayout l = this.layout;
        if (this.entry != null) {
            List<FieldText> hint = this.state.entryHint(this.entryRow, this.entry.getValue(), this.decimal);
            if (!hint.isEmpty()) {
                ValueEntry.Status status = ValueEntry.evaluate(this.entry.getValue(), this.entryRange).status();
                int color = status == ValueEntry.Status.INVALID ? Palette.ERROR : status == ValueEntry.Status.CLAMPED ? Palette.WARNING : Palette.TEXT;
                MutableComponent text = ThemeText.colored(this.text(hint.getFirst()), color);
                for (int i = 1; i < hint.size(); i++) {
                    text.append("\n").append(ThemeText.muted(this.text(hint.get(i))));
                }
                ThemeText.tooltipBelow(g, this.font, text, 170, this.entry.getRectangle());
            }
            return;
        }
        int detail = l.detailRowAt(mouseX, mouseY, this.detailPage());
        if (detail >= 0 && this.state.selection().kind() == EditorState.Kind.WAYPOINT) {
            ThemeText.tooltipLines(g, this.font, this.detailTooltip(detail), mouseX, mouseY);
            return;
        }
        if (this.link.isPresent() && l.title().contains(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            for (FieldText line : this.support.details()) {
                lines.add(this.text(line));
            }
            ThemeText.tooltipLines(g, this.font, lines, mouseX, mouseY);
            return;
        }
        int setting = l.settingAt(mouseX, mouseY);
        if (setting >= 0) {
            String key = setting < MissionField.values().length ? MissionField.values()[setting].tooltipKey()
                    : setting == EditorLayout.SETTING_EXIT ? ExitSetting.TOOLTIP_KEY
                    : setting == EditorLayout.SETTING_VIEW ? ViewSetting.TOOLTIP_KEY
                    : (setting == EditorLayout.SETTING_GROUND ? EditorCatalog.GROUND_KEY : EditorCatalog.RECORD_KEY) + ".tooltip";
            ThemeText.tooltip(g, this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        if (l.mapShown() && l.map().contains(mouseX, mouseY) && this.drag == Drag.NONE) {
            ThemeText.tooltip(g, this.font, Component.translatable(FieldText.PREFIX + "map.help"), mouseX, mouseY);
        }
    }

    private Component text(FieldText value) {
        return Component.translatable(value.key(), value.args().toArray());
    }

    private void startEntry(int row) {
        this.closeEntry(false);
        Optional<ValueEntry.Range> range = this.state.entryRange(row);
        if (range.isEmpty() || !this.state.typeable(row)) {
            return;
        }
        Rect box = EditorLayout.valueBox(this.layout.detailRows().get(row), this.layout.detailLabelWidth());
        TextField field = new TextField(this.font, box.x(), box.y(), box.w(), box.h(), Component.translatable(this.entryLabel(row)));
        field.setMaxLength(ValueEntry.MAX_LENGTH);
        field.setCentered(true);
        String text = this.state.entryText(row, this.decimal);
        field.setValue(text);
        field.moveCursorToEnd(false);
        field.setHighlightPos(0);
        if (text.isEmpty()) {
            field.hint(Component.literal(EditorTexts.MIXED));
        }
        this.entryRange = range.get();
        this.entryRow = row;
        this.entry = this.addRenderableWidget(field);
        field.setResponder(value -> this.entryChanged());
        this.setFocused(field);
        field.setFocused(true);
        this.entryChanged();
    }

    private String entryLabel(int row) {
        if (this.state.selection().kind() == EditorState.Kind.POI) {
            return row == 0 ? EditorCatalog.POI_HEIGHT_KEY : EditorCatalog.POI_RADIUS_KEY;
        }
        return WaypointField.values()[row].labelKey();
    }

    private void entryChanged() {
        if (this.entry == null) {
            return;
        }
        ValueEntry.Status status = ValueEntry.evaluate(this.entry.getValue(), this.entryRange).status();
        this.entry.setTextColor(status == ValueEntry.Status.INVALID ? Palette.ERROR : status == ValueEntry.Status.CLAMPED ? Palette.WARNING : Palette.TEXT);
    }

    private boolean closeEntry(boolean commit) {
        if (this.entry == null) {
            return false;
        }
        if (commit) {
            ValueEntry.Status status = this.state.commitEntry(this.entryRow, this.entry.getValue(), this.decimal);
            if (status == ValueEntry.Status.INVALID) {
                return false;
            }
            if (status == ValueEntry.Status.OK || status == ValueEntry.Status.CLAMPED) {
                PlannerSounds.click(1.0F);
            }
        }
        TextField field = this.entry;
        this.entry = null;
        this.entryRow = -1;
        this.entryRange = null;
        this.removeWidget(field);
        if (this.getFocused() == field) {
            this.setFocused(null);
        }
        this.refreshButtons();
        return true;
    }

    private void commitOrCancelEntry() {
        if (!this.closeEntry(true)) {
            this.closeEntry(false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        LastStep previous = this.lastStep;
        this.lastStep = null;
        if (this.entry != null && !this.entry.isMouseOver(event.x(), event.y())) {
            this.commitOrCancelEntry();
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        this.setFocused(null);
        double x = event.x();
        double y = event.y();
        boolean big = event.hasShiftDown();
        EditorLayout l = this.layout;
        if (this.state.templateMenu() || this.state.autoMenu() || this.state.followMenu()) {
            this.state.templateMenu(false);
            this.state.autoMenu(false);
            this.state.followMenu(false);
            this.refreshButtons();
        }
        int row = l.listRowAt(x, y);
        if (l.listShown() && row >= 0) {
            EditorState.Selection selection = this.state.row(this.state.listScroll() + row);
            if (selection.kind() != EditorState.Kind.NONE) {
                boolean toggle = event.hasControlDownWithQuirk();
                boolean range = event.hasShiftDown();
                if (toggle) {
                    this.state.toggle(selection);
                } else if (range) {
                    this.state.extendTo(selection);
                } else {
                    this.state.select(selection);
                }
                if (doubleClick && !toggle && !range && !l.mapShown() && selection.kind() == EditorState.Kind.WAYPOINT) {
                    Waypoint w = this.state.mission().waypoint(selection.index());
                    this.state.view().center(w.x(), w.z());
                }
                this.refreshButtons();
                return true;
            }
        }
        if (l.detailPaged() && this.state.selection().kind() == EditorState.Kind.WAYPOINT && l.pageToggle().contains(x, y)) {
            this.detailPage(1 - this.detailPage());
            PlannerSounds.click(1.0F);
            return true;
        }
        int detail = l.detailRowAt(x, y, this.detailPage());
        if (detail >= 0) {
            Rect box = EditorLayout.valueBox(l.detailRows().get(detail), l.detailLabelWidth());
            if (doubleClick && EditorLayout.onValueText(box, x) && this.state.typeable(detail)) {
                if (previous != null && previous.row() == detail) {
                    this.state.revert(previous.before());
                }
                this.startEntry(detail);
                this.refreshButtons();
                return true;
            }
            TabletState before = this.state.tablet();
            if (this.stepDetail(detail, EditorLayout.stepDirection(box, x), big, event.hasAltDown())) {
                this.lastStep = new LastStep(detail, before);
                PlannerSounds.click(1.0F);
                this.refreshButtons();
                return true;
            }
        }
        int setting = l.settingAt(x, y);
        if (setting >= 0) {
            this.stepSetting(setting, EditorLayout.stepDirection(EditorLayout.valueBox(l.settings().get(setting), l.settingLabelWidth()), x), big);
            PlannerSounds.click(1.0F);
            this.refreshButtons();
            return true;
        }
        if (l.mapShown() && l.map().inset(1).contains(x, y)) {
            this.pressMap(event);
            return true;
        }
        return false;
    }

    private boolean stepDetail(int row, int direction, boolean big, boolean each) {
        return switch (this.state.selection().kind()) {
            case WAYPOINT -> this.state.stepWaypoint(WaypointField.values()[row], direction, big, each);
            case POI -> row == 0 ? this.state.stepPoiHeight(direction, big) : row == 1 && this.state.stepPoiRadius(direction, big);
            case NONE -> false;
        };
    }

    private void stepSetting(int setting, int direction, boolean big) {
        if (setting < MissionField.values().length) {
            this.state.stepMissionField(MissionField.values()[setting], direction, big);
        } else if (setting == EditorLayout.SETTING_GROUND) {
            this.state.stepGroundHeight(direction * (big ? 5 : 1));
        } else if (setting == EditorLayout.SETTING_EXIT) {
            this.stepExit(direction);
        } else if (setting == EditorLayout.SETTING_VIEW) {
            this.stepView(direction);
        } else if (PlannerClient.recorder().active()) {
            PlannerClient.recorder().stop();
            this.state.status(EditorState.Status.info("status.record_stopped"));
        } else {
            PlannerClient.recorder().start();
            this.state.status(EditorState.Status.info("status.record_started"));
        }
    }

    private void pressMap(MouseButtonEvent event) {
        Rect inner = this.layout.map().inset(1);
        this.pressX = event.x();
        this.pressY = event.y();
        this.pressButton = event.button();
        Mission mission = this.state.mission();
        MapView view = this.state.view();
        int waypoint = nearest(mission.waypoints().stream().map(w -> new double[]{view.screenX(w.x(), inner), view.screenY(w.z(), inner)}).toList(),
                event.x(), event.y());
        if (waypoint >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT && (event.hasControlDownWithQuirk() || event.hasShiftDown())) {
            if (event.hasControlDownWithQuirk()) {
                this.state.toggle(EditorState.Selection.waypoint(waypoint));
            } else {
                this.state.extendTo(EditorState.Selection.waypoint(waypoint));
            }
            this.scrollToSelection();
            this.drag = Drag.NONE;
            this.dragIndex = -1;
            this.refreshButtons();
            return;
        }
        if (waypoint >= 0) {
            this.state.select(EditorState.Selection.waypoint(waypoint));
            this.scrollToSelection();
            this.drag = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? Drag.WAYPOINT : Drag.NONE;
            this.dragIndex = waypoint;
            this.refreshButtons();
            return;
        }
        int poi = nearest(mission.pointsOfInterest().stream().map(p -> new double[]{view.screenX(p.x(), inner), view.screenY(p.z(), inner)}).toList(),
                event.x(), event.y());
        if (poi >= 0) {
            this.state.select(EditorState.Selection.poi(poi));
            this.scrollToSelection();
            this.drag = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? Drag.POI : Drag.NONE;
            this.dragIndex = poi;
            this.refreshButtons();
            return;
        }
        this.drag = Drag.PRESS;
        this.dragIndex = -1;
    }

    private static int nearest(List<double[]> points, double x, double y) {
        int best = -1;
        double bestDistance = MARKER_HIT * MARKER_HIT;
        for (int i = 0; i < points.size(); i++) {
            double dx = points.get(i)[0] - x;
            double dy = points.get(i)[1] - y;
            double d = dx * dx + dy * dy;
            if (d <= bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.drag == Drag.NONE) {
            return super.mouseDragged(event, dx, dy);
        }
        Rect inner = this.layout.map().inset(1);
        MapView view = this.state.view();
        boolean moved = Math.abs(event.x() - this.pressX) > DRAG_THRESHOLD || Math.abs(event.y() - this.pressY) > DRAG_THRESHOLD;
        switch (this.drag) {
            case PRESS -> {
                if (moved) {
                    this.drag = Drag.PAN;
                    view.pan(event.x() - this.pressX, event.y() - this.pressY);
                }
            }
            case PAN -> view.pan(dx, dy);
            case WAYPOINT -> {
                if (moved) {
                    this.state.moveWaypoint(this.dragIndex, view.worldX(event.x(), inner), view.worldZ(event.y(), inner));
                }
            }
            case POI -> {
                if (moved) {
                    this.state.movePoi(this.dragIndex, view.worldX(event.x(), inner), view.worldZ(event.y(), inner));
                }
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.drag == Drag.NONE) {
            return super.mouseReleased(event);
        }
        if (this.drag == Drag.PRESS) {
            Rect inner = this.layout.map().inset(1);
            MapView view = this.state.view();
            double x = view.worldX(event.x(), inner);
            double z = view.worldZ(event.y(), inner);
            double ground = this.groundAt(x, z);
            boolean added = this.pressButton == InputConstants.MOUSE_BUTTON_RIGHT ? this.state.addPoi(x, ground, z)
                    : this.state.addWaypointAt(x, ground + this.state.tablet().groundHeight(), z);
            if (added) {
                PlannerSounds.click(1.2F);
            }
            this.scrollToSelection();
        }
        this.drag = Drag.NONE;
        this.dragIndex = -1;
        this.refreshButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        int direction = scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0;
        if (direction == 0) {
            return false;
        }
        this.lastStep = null;
        if (this.entry != null) {
            this.commitOrCancelEntry();
        }
        EditorLayout l = this.layout;
        boolean big = this.minecraft.hasShiftDown();
        if (l.listShown() && l.list().contains(x, y)) {
            this.state.listScroll(this.state.listScroll() - direction, l.visibleRows());
            return true;
        }
        if (l.mapShown() && l.map().contains(x, y)) {
            this.state.view().zoomBy(direction, x, y, l.map().inset(1));
            return true;
        }
        int detail = l.detailRowAt(x, y, this.detailPage());
        if (detail >= 0) {
            this.stepDetail(detail, direction, big, this.minecraft.hasAltDown());
            this.refreshButtons();
            return true;
        }
        int setting = l.settingAt(x, y);
        if (setting >= 0 && setting != EditorLayout.SETTING_RECORD) {
            this.stepSetting(setting, direction, big);
            this.refreshButtons();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.entry != null) {
            if (!ValueEntry.allowed(event.codepoint(), this.entryRange)) {
                return true;
            }
            return this.entry.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.entry != null) {
            if (event.isConfirmation()) {
                this.closeEntry(true);
            } else if (event.isEscape()) {
                this.closeEntry(false);
            } else if (event.isCycleFocus()) {
                this.commitOrCancelEntry();
            } else {
                this.entry.keyPressed(event);
            }
            return true;
        }
        if (this.getFocused() == this.nameField && this.nameField.isFocused()) {
            if (event.isConfirmation() || event.isEscape()) {
                this.setFocused(null);
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.isSelectAll() && this.state.selectAllWaypoints()) {
            this.scrollToSelection();
            this.refreshButtons();
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE && this.state.deleteSelected()) {
            this.refreshButtons();
            return true;
        }
        if (event.isUp() || event.isDown()) {
            int row = this.state.rowOf(this.state.selection());
            EditorState.Selection next = this.state.row(Math.clamp(row + (event.isUp() ? -1 : 1), 0, Math.max(0, this.state.rowCount() - 1)));
            if (next.kind() != EditorState.Kind.NONE) {
                this.state.select(next);
                this.scrollToSelection();
                this.refreshButtons();
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
