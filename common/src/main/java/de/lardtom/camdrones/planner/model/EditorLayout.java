package de.lardtom.camdrones.planner.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class EditorLayout {
    public static final int WIDE_MIN_WIDTH = 520;
    public static final int COMPACT_WIDTH = 480;
    public static final int COMPACT_HEIGHT = 320;
    public static final int MIN_NAME_WIDTH = 56;
    public static final int MIN_LIST_WIDTH = 90;
    public static final int MIN_MAP_SIZE = 80;
    public static final int ARROW_WIDTH = 9;
    public static final int VALUE_INSET = ARROW_WIDTH + 3;
    public static final int LABEL_GAP = 4;
    public static final int TEXT_HEIGHT = 8;
    public static final int BUTTON_PAD = 10;
    public static final int TIGHT_BUTTON_PAD = 6;
    public static final int MIN_STATUS_WIDTH = 40;
    public static final int FIELD_GAP = 1;
    public static final int SETTING_FIELDS = MissionField.values().length + 4;
    public static final int SETTING_GROUND = MissionField.values().length;
    public static final int SETTING_RECORD = MissionField.values().length + 1;
    public static final int SETTING_EXIT = MissionField.values().length + 2;
    public static final int SETTING_VIEW = MissionField.values().length + 3;
    public static final int DETAIL_ROWS = WaypointField.values().length;
    public static final int TASK_PAGE_FIRST = WaypointField.ACTION.ordinal();
    public static final String TASK_PAGE_KEY = FieldText.PREFIX + "detail.page.tasks";
    public static final String FLIGHT_PAGE_KEY = FieldText.PREFIX + "detail.page.flight";

    public enum Mode {
        WIDE,
        NARROW
    }

    private final int width;
    private final int height;
    private final Mode mode;
    private final boolean compact;
    private final boolean mapShown;
    private final boolean listShown;
    private final int row;
    private final int gap;
    private final int pad;
    private final Rect window;
    private final Rect header;
    private final Rect title;
    private final Rect live;
    private final Rect mapToggle;
    private final Rect close;
    private final Rect previous;
    private final Rect name;
    private final Rect next;
    private final Rect counter;
    private final Rect missionAdd;
    private final Rect missionCopy;
    private final Rect missionDelete;
    private final Rect map;
    private final Rect zoomIn;
    private final Rect zoomOut;
    private final Rect recenter;
    private final Rect list;
    private final Rect listAddWaypoint;
    private final Rect listAddPoi;
    private final Rect listRows;
    private final Rect detail;
    private final Rect detailTitle;
    private final Rect up;
    private final Rect down;
    private final Rect remove;
    private final List<Rect> detailRows;
    private final int detailLabelWidth;
    private final boolean detailPaged;
    private final Rect pageToggle;
    private final Rect preview;
    private final List<Rect> settings;
    private final int settingLabelWidth;
    private final Rect footer;
    private final List<Rect> footerButtons;
    private final boolean footerCompact;
    private final Rect status;
    private final List<Rect> templateItems;
    private final List<Rect> autoItems;
    private final List<Rect> followItems;

    private EditorLayout(Builder b) {
        this.width = b.width;
        this.height = b.height;
        this.mode = b.mode;
        this.compact = b.compact;
        this.mapShown = b.mapShown;
        this.listShown = b.listShown;
        this.row = b.row;
        this.gap = b.gap;
        this.pad = b.pad;
        this.window = b.window;
        this.header = b.header;
        this.title = b.title;
        this.live = b.live;
        this.mapToggle = b.mapToggle;
        this.close = b.close;
        this.previous = b.previous;
        this.name = b.name;
        this.next = b.next;
        this.counter = b.counter;
        this.missionAdd = b.missionAdd;
        this.missionCopy = b.missionCopy;
        this.missionDelete = b.missionDelete;
        this.map = b.map;
        this.zoomIn = b.zoomIn;
        this.zoomOut = b.zoomOut;
        this.recenter = b.recenter;
        this.list = b.list;
        this.listAddWaypoint = b.listAddWaypoint;
        this.listAddPoi = b.listAddPoi;
        this.listRows = b.listRows;
        this.detail = b.detail;
        this.detailTitle = b.detailTitle;
        this.up = b.up;
        this.down = b.down;
        this.remove = b.remove;
        this.detailRows = List.copyOf(b.detailRows);
        this.detailLabelWidth = b.detailLabelWidth;
        this.detailPaged = b.detailPaged;
        this.pageToggle = b.pageToggle;
        this.preview = b.preview;
        this.settings = List.copyOf(b.settings);
        this.settingLabelWidth = b.settingLabelWidth;
        this.footer = b.footer;
        this.footerButtons = List.copyOf(b.footerButtons);
        this.footerCompact = b.footerCompact;
        this.status = b.status;
        this.templateItems = List.copyOf(b.templateItems);
        this.autoItems = List.copyOf(b.autoItems);
        this.followItems = List.copyOf(b.followItems);
    }

    public static EditorLayout compute(int width, int height, boolean mapExpanded, EditorTexts texts, ToIntFunction<String> measure) {
        Builder b = new Builder();
        b.width = Math.max(1, width);
        b.height = Math.max(1, height);
        b.compact = b.width < COMPACT_WIDTH || b.height < COMPACT_HEIGHT;
        int margin = b.compact ? 2 : 6;
        b.pad = b.compact ? 3 : 6;
        b.gap = b.compact ? 2 : 4;
        b.row = b.compact ? 12 : 14;
        int headerHeight = b.compact ? 18 : 24;
        int footerHeight = b.compact ? b.row + 4 : 24;
        b.window = new Rect(margin, margin, b.width - 2 * margin, b.height - 2 * margin);
        int x0 = b.window.x() + b.pad;
        int x1 = b.window.right() - b.pad;
        int innerWidth = x1 - x0;

        b.header = new Rect(b.window.x(), b.window.y(), b.window.w(), headerHeight);
        int headerControlY = b.header.y() + (headerHeight - b.row) / 2;
        b.close = new Rect(x1 - b.row, headerControlY, b.row, b.row);
        int toggleWidth = Math.max(measure.applyAsInt(texts.mapShow()), measure.applyAsInt(texts.mapHide())) + BUTTON_PAD;
        b.mapToggle = new Rect(b.close.x() - b.gap - toggleWidth, headerControlY, toggleWidth, b.row);
        int liveWidth = measure.applyAsInt(texts.live()) + BUTTON_PAD;
        b.live = new Rect(b.mapToggle.x() - b.gap - liveWidth, headerControlY, liveWidth, b.row);
        b.title = new Rect(x0, b.header.y(), Math.max(0, b.live.x() - b.gap - x0), headerHeight);

        int stripY = b.header.bottom() + b.gap;
        int arrow = b.row;
        int counterWidth = measure.applyAsInt(texts.counterSample()) + 6;
        int addWidth = measure.applyAsInt(texts.missionAdd()) + BUTTON_PAD;
        int copyWidth = measure.applyAsInt(texts.missionCopy()) + BUTTON_PAD;
        int deleteWidth = measure.applyAsInt(texts.missionDelete()) + BUTTON_PAD;
        b.missionDelete = new Rect(x1 - deleteWidth, stripY, deleteWidth, b.row);
        b.missionCopy = new Rect(b.missionDelete.x() - b.gap - copyWidth, stripY, copyWidth, b.row);
        b.missionAdd = new Rect(b.missionCopy.x() - b.gap - addWidth, stripY, addWidth, b.row);
        b.previous = new Rect(x0, stripY, arrow, b.row);
        int nameRight = b.missionAdd.x() - 2 * b.gap - counterWidth - b.gap - arrow;
        b.name = new Rect(b.previous.right() + b.gap, stripY, nameRight - b.previous.right() - b.gap, b.row);
        b.next = new Rect(b.name.right() + b.gap, stripY, arrow, b.row);
        b.counter = new Rect(b.next.right() + b.gap, stripY, counterWidth, b.row);
        int stripBottom = stripY + b.row;

        b.footer = new Rect(b.window.x(), b.window.bottom() - footerHeight, b.window.w(), footerHeight);
        layoutFooter(b, texts, measure, x0, x1);

        int settingLabelWidth = EditorTexts.widest(texts.settingLabels(), measure) + LABEL_GAP + 2;
        int settingValueWidth = EditorTexts.widest(texts.settingValues(), measure) + 2 * VALUE_INSET;
        int settingWidth = settingLabelWidth + settingValueWidth;
        int columns = 1;
        for (int c = SETTING_FIELDS; c >= 1; c--) {
            int rows = (SETTING_FIELDS + c - 1) / c;
            int balanced = (SETTING_FIELDS + rows - 1) / rows;
            int w = (innerWidth - (balanced - 1) * b.gap) / balanced;
            if (w >= settingWidth) {
                columns = balanced;
                break;
            }
        }
        int settingRows = (SETTING_FIELDS + columns - 1) / columns;
        int columnWidth = (innerWidth - (columns - 1) * b.gap) / columns;
        int settingsHeight = settingRows * b.row + (settingRows - 1) * b.gap;
        int settingsTop = b.footer.y() - b.gap - settingsHeight;
        b.settingLabelWidth = settingLabelWidth;
        for (int i = 0; i < SETTING_FIELDS; i++) {
            int r = i / columns;
            int c = i % columns;
            b.settings.add(new Rect(x0 + c * (columnWidth + b.gap), settingsTop + r * (b.row + b.gap), columnWidth, b.row));
        }
        b.preview = new Rect(x0, settingsTop - b.gap - TEXT_HEIGHT - 2, innerWidth, TEXT_HEIGHT + 2);

        int mainTop = stripBottom + b.gap;
        int mainBottom = b.preview.y() - b.gap;
        int mainHeight = Math.max(0, mainBottom - mainTop);
        b.mode = innerWidth >= WIDE_MIN_WIDTH ? Mode.WIDE : Mode.NARROW;

        b.detailLabelWidth = EditorTexts.widest(texts.fieldLabels(), measure) + LABEL_GAP + 2;
        int detailValueWidth = EditorTexts.widest(texts.fieldValues(), measure) + 2 * VALUE_INSET;
        int detailNeed = b.detailLabelWidth + detailValueWidth + 2 * b.pad;
        int detailWidth;
        int leftWidth;
        if (b.mode == Mode.WIDE) {
            detailWidth = Math.max(detailNeed, Math.clamp(Math.round(innerWidth * 0.34F), 200, 300));
        } else {
            detailWidth = Math.max(detailNeed, Math.clamp(Math.round(innerWidth * 0.56F), 160, 210));
        }
        leftWidth = innerWidth - detailWidth - b.gap;
        b.detail = new Rect(x1 - detailWidth, mainTop, detailWidth, mainHeight);

        Rect listArea = Rect.EMPTY;
        Rect mapArea = Rect.EMPTY;
        if (b.mode == Mode.WIDE && mapExpanded) {
            int listWidth = Math.clamp(Math.round((leftWidth - b.gap) * 0.4F), MIN_LIST_WIDTH, 200);
            int mapWidth = leftWidth - b.gap - listWidth;
            mapArea = new Rect(x0, mainTop, mapWidth, mainHeight);
            listArea = new Rect(mapArea.right() + b.gap, mainTop, listWidth, mainHeight);
        } else if (mapExpanded) {
            mapArea = new Rect(x0, mainTop, leftWidth, mainHeight);
        } else {
            listArea = new Rect(x0, mainTop, leftWidth, mainHeight);
        }
        b.mapShown = !mapArea.isEmpty();
        b.listShown = !listArea.isEmpty();
        b.map = mapArea;
        if (b.mapShown) {
            int s = b.row;
            int bx = mapArea.right() - 2 - s;
            b.zoomIn = new Rect(bx, mapArea.y() + 2, s, s);
            b.zoomOut = new Rect(bx, b.zoomIn.bottom() + 1, s, s);
            b.recenter = new Rect(bx, b.zoomOut.bottom() + 1, s, s);
        } else {
            b.zoomIn = Rect.EMPTY;
            b.zoomOut = Rect.EMPTY;
            b.recenter = Rect.EMPTY;
        }
        b.list = listArea;
        if (b.listShown) {
            int inner = listArea.w() - 2;
            int addPoiWidth = measure.applyAsInt(texts.listAddPoi()) + BUTTON_PAD;
            int addWaypointWidth = Math.max(measure.applyAsInt(texts.listAddWaypoint()) + BUTTON_PAD, inner - addPoiWidth - b.gap);
            addWaypointWidth = Math.min(addWaypointWidth, inner - addPoiWidth - b.gap);
            b.listAddWaypoint = new Rect(listArea.x() + 1, listArea.y() + 1, addWaypointWidth, b.row);
            b.listAddPoi = new Rect(b.listAddWaypoint.right() + b.gap, listArea.y() + 1, addPoiWidth, b.row);
            int rowsTop = b.listAddWaypoint.bottom() + b.gap;
            b.listRows = new Rect(listArea.x() + 1, rowsTop, listArea.w() - 2, Math.max(0, listArea.bottom() - 1 - rowsTop));
        } else {
            b.listAddWaypoint = Rect.EMPTY;
            b.listAddPoi = Rect.EMPTY;
            b.listRows = Rect.EMPTY;
        }

        int dx0 = b.detail.x() + b.pad;
        int dx1 = b.detail.right() - b.pad;
        int dy = b.detail.y() + Math.max(1, b.pad / 2);
        int small = b.row;
        b.remove = new Rect(dx1 - small, dy, small, b.row);
        b.down = new Rect(b.remove.x() - b.gap - small, dy, small, b.row);
        b.up = new Rect(b.down.x() - b.gap - small, dy, small, b.row);
        b.detailTitle = new Rect(dx0, dy, Math.max(0, b.up.x() - b.gap - dx0), b.row);
        int rowY = b.detailTitle.bottom() + b.gap;
        int step = b.row + FIELD_GAP;
        b.detailPaged = rowY + DETAIL_ROWS * step - FIELD_GAP > b.detail.bottom() - 1;
        for (int i = 0; i < DETAIL_ROWS; i++) {
            int position = b.detailPaged && i >= TASK_PAGE_FIRST ? i - TASK_PAGE_FIRST : i;
            b.detailRows.add(new Rect(dx0, rowY + position * step, dx1 - dx0, b.row));
        }
        b.pageToggle = b.detailPaged ? new Rect(dx0, rowY + TASK_PAGE_FIRST * step + b.gap, dx1 - dx0, b.row) : Rect.EMPTY;

        int templateWidth = Math.max(b.footerButtons.get(EditorTexts.Footer.TEMPLATE.ordinal()).w(),
                EditorTexts.widest(texts.templates(), measure) + BUTTON_PAD);
        Rect templateButton = b.footerButtons.get(EditorTexts.Footer.TEMPLATE.ordinal());
        int tx = Math.min(templateButton.x(), x1 - templateWidth);
        for (int i = 0; i < texts.templates().size(); i++) {
            int ty = b.footer.y() - (texts.templates().size() - i) * (b.row + 1);
            b.templateItems.add(new Rect(tx, ty, templateWidth, b.row));
        }
        Rect autoButton = b.footerButtons.get(EditorTexts.Footer.AUTO.ordinal());
        int autoWidth = Math.max(autoButton.w(), EditorTexts.widest(texts.autoShots(), measure) + BUTTON_PAD);
        int ax = Math.min(autoButton.x(), x1 - autoWidth);
        for (int i = 0; i < texts.autoShots().size(); i++) {
            int ay = b.footer.y() - (texts.autoShots().size() - i) * (b.row + 1);
            b.autoItems.add(new Rect(ax, ay, autoWidth, b.row));
        }
        Rect followButton = b.footerButtons.get(EditorTexts.Footer.FOLLOW.ordinal());
        int followWidth = Math.max(followButton.w(), EditorTexts.widest(texts.followStyles(), measure) + BUTTON_PAD);
        int fx = Math.min(followButton.x(), x1 - followWidth);
        for (int i = 0; i < texts.followStyles().size(); i++) {
            int fy = b.footer.y() - (texts.followStyles().size() - i) * (b.row + 1);
            b.followItems.add(new Rect(fx, fy, followWidth, b.row));
        }
        return new EditorLayout(b);
    }

    private static void layoutFooter(Builder b, EditorTexts texts, ToIntFunction<String> measure, int x0, int x1) {
        int available = x1 - x0;
        int count = EditorTexts.Footer.values().length;
        int[] full = new int[count];
        int[] small = new int[count];
        int[] tight = new int[count];
        int fullSum = 0;
        int smallSum = 0;
        for (EditorTexts.Footer f : EditorTexts.Footer.values()) {
            EditorTexts.FooterLabel label = texts.footer(f);
            full[f.ordinal()] = measure.applyAsInt(label.full()) + 2 * BUTTON_PAD;
            small[f.ordinal()] = Math.max(b.row + 4, measure.applyAsInt(label.compact()) + BUTTON_PAD);
            tight[f.ordinal()] = Math.max(b.row, measure.applyAsInt(label.compact()) + TIGHT_BUTTON_PAD);
            fullSum += full[f.ordinal()];
            smallSum += small[f.ordinal()];
        }
        int gaps = (count - 1) * b.gap + 2 * b.gap;
        b.footerCompact = fullSum + gaps > available;
        boolean squeezed = b.footerCompact && available - smallSum - (count + 2) * b.gap < MIN_STATUS_WIDTH;
        int[] widths = b.footerCompact ? squeezed ? tight : small : full;
        int y = b.footer.y() + (b.footer.h() - b.row) / 2;
        int x = x0;
        for (int i = 0; i <= EditorTexts.Footer.FOLLOW.ordinal(); i++) {
            b.footerButtons.add(new Rect(x, y, widths[i], b.row));
            x += widths[i] + b.gap;
        }
        int leftEnd = x - b.gap;
        int saveWidth = widths[EditorTexts.Footer.SAVE.ordinal()];
        int templateWidth = widths[EditorTexts.Footer.TEMPLATE.ordinal()];
        Rect save = new Rect(x1 - saveWidth, y, saveWidth, b.row);
        Rect template = new Rect(save.x() - b.gap - templateWidth, y, templateWidth, b.row);
        b.footerButtons.add(template);
        b.footerButtons.add(save);
        b.status = new Rect(leftEnd + 2 * b.gap, y, Math.max(0, template.x() - 2 * b.gap - leftEnd - 2 * b.gap), b.row);
    }

    public static int stepDirection(Rect valueBox, double mouseX) {
        return mouseX < valueBox.x() + valueBox.w() / 2.0 ? -1 : 1;
    }

    public static boolean onValueText(Rect valueBox, double mouseX) {
        return mouseX >= valueBox.x() + VALUE_INSET && mouseX < valueBox.right() - VALUE_INSET;
    }

    public static Rect valueBox(Rect row, int labelWidth) {
        int offset = Math.min(Math.max(0, labelWidth), row.w());
        return new Rect(row.x() + offset, row.y(), row.w() - offset, row.h());
    }

    public static int valueTextWidth(Rect valueBox) {
        return Math.max(0, valueBox.w() - 2 * VALUE_INSET);
    }

    public int visibleRows() {
        return this.listRows.isEmpty() ? 0 : Math.max(0, this.listRows.h() / this.row);
    }

    public Rect listRow(int visibleIndex) {
        return new Rect(this.listRows.x(), this.listRows.y() + visibleIndex * this.row, this.listRows.w(), this.row);
    }

    public int listRowAt(double mouseX, double mouseY) {
        if (!this.listRows.contains(mouseX, mouseY)) {
            return -1;
        }
        int index = (int) ((mouseY - this.listRows.y()) / this.row);
        return index < this.visibleRows() ? index : -1;
    }

    public int detailRowAt(double mouseX, double mouseY) {
        return this.detailRowAt(mouseX, mouseY, 0);
    }

    public int detailRowAt(double mouseX, double mouseY, int page) {
        for (int i = 0; i < this.detailRows.size(); i++) {
            if (this.detailPage(i) == page && valueBox(this.detailRows.get(i), this.detailLabelWidth).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    public int detailPage(int row) {
        return this.detailPaged && row >= TASK_PAGE_FIRST ? 1 : 0;
    }

    public int detailPages() {
        return this.detailPaged ? 2 : 1;
    }

    public boolean detailPaged() {
        return this.detailPaged;
    }

    public Rect pageToggle() {
        return this.pageToggle;
    }

    public int settingAt(double mouseX, double mouseY) {
        for (int i = 0; i < this.settings.size(); i++) {
            if (valueBox(this.settings.get(i), this.settingLabelWidth).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    public Map<String, Rect> controls() {
        Map<String, Rect> out = new LinkedHashMap<>();
        out.put("live", this.live);
        out.put("mapToggle", this.mapToggle);
        out.put("close", this.close);
        out.put("previous", this.previous);
        out.put("name", this.name);
        out.put("next", this.next);
        out.put("counter", this.counter);
        out.put("missionAdd", this.missionAdd);
        out.put("missionCopy", this.missionCopy);
        out.put("missionDelete", this.missionDelete);
        if (this.mapShown) {
            out.put("map", this.map);
            out.put("zoomIn", this.zoomIn);
            out.put("zoomOut", this.zoomOut);
            out.put("recenter", this.recenter);
        }
        if (this.listShown) {
            out.put("listAddWaypoint", this.listAddWaypoint);
            out.put("listAddPoi", this.listAddPoi);
            out.put("listRows", this.listRows);
        }
        out.put("detailTitle", this.detailTitle);
        out.put("up", this.up);
        out.put("down", this.down);
        out.put("remove", this.remove);
        for (int i = 0; i < this.detailRows.size(); i++) {
            out.put("detailRow" + i + (this.detailPage(i) == 1 ? "@2" : ""), this.detailRows.get(i));
        }
        if (this.detailPaged) {
            out.put("pageToggle", this.pageToggle);
        }
        out.put("preview", this.preview);
        for (int i = 0; i < this.settings.size(); i++) {
            out.put("setting" + i, this.settings.get(i));
        }
        for (EditorTexts.Footer f : EditorTexts.Footer.values()) {
            out.put("footer." + f.name().toLowerCase(Locale.ROOT), this.footerButtons.get(f.ordinal()));
        }
        return out;
    }

    public List<String> problems(EditorTexts texts, ToIntFunction<String> measure) {
        List<String> problems = new ArrayList<>();
        Map<String, Rect> controls = this.controls();
        for (Map.Entry<String, Rect> e : controls.entrySet()) {
            Rect r = e.getValue();
            if (r.isEmpty()) {
                problems.add(e.getKey() + " leer");
            } else if (!r.inside(this.window)) {
                problems.add(e.getKey() + " außerhalb des Fensters " + r);
            }
        }
        List<Map.Entry<String, Rect>> list = new ArrayList<>(controls.entrySet());
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Rect a = list.get(i).getValue();
                Rect c = list.get(j).getValue();
                if (a.overlaps(c) && !nested(list.get(i).getKey(), list.get(j).getKey())) {
                    problems.add(list.get(i).getKey() + " überlappt " + list.get(j).getKey());
                }
            }
        }
        if (this.name.w() < MIN_NAME_WIDTH) {
            problems.add("Namensfeld zu schmal: " + this.name.w());
        }
        if (this.mapShown && (this.map.w() < MIN_MAP_SIZE || this.map.h() < MIN_MAP_SIZE)) {
            problems.add("Karte zu klein: " + this.map);
        }
        if (this.listShown && this.visibleRows() < 3) {
            problems.add("Liste zeigt nur " + this.visibleRows() + " Zeilen");
        }
        if (this.listShown && this.listAddWaypoint.w() < measure.applyAsInt(texts.listAddWaypoint()) + 4) {
            problems.add("Knopf Wegpunkt zu schmal");
        }
        Rect lastRow = this.detailPaged ? this.pageToggle : this.detailRows.getLast();
        if (lastRow.bottom() > this.detail.bottom()) {
            problems.add("Detailzeilen passen nicht: " + lastRow.bottom() + " > " + this.detail.bottom());
        }
        if (this.detailPaged && EditorTexts.widest(texts.pageLabels(), measure) + 4 > this.pageToggle.w()) {
            problems.add("Knopf für die Detailseite zu schmal");
        }
        int titleNeed = EditorTexts.widest(texts.detailTitles(), measure);
        if (titleNeed > this.detailTitle.w()) {
            problems.add("Detailtitel zu breit: " + titleNeed + " > " + this.detailTitle.w());
        }
        int fieldNeed = EditorTexts.widest(texts.fieldValues(), measure);
        int fieldRoom = valueTextWidth(valueBox(this.detailRows.getFirst(), this.detailLabelWidth));
        if (fieldNeed > fieldRoom) {
            problems.add("Werte der Wegpunkte zu breit: " + fieldNeed + " > " + fieldRoom);
        }
        int labelNeed = EditorTexts.widest(texts.fieldLabels(), measure);
        if (labelNeed > this.detailLabelWidth - LABEL_GAP) {
            problems.add("Beschriftung der Wegpunkte zu breit");
        }
        int settingNeed = EditorTexts.widest(texts.settingValues(), measure);
        int settingRoom = valueTextWidth(valueBox(this.settings.getFirst(), this.settingLabelWidth));
        if (settingNeed > settingRoom) {
            problems.add("Werte der Mission zu breit: " + settingNeed + " > " + settingRoom);
        }
        if (EditorTexts.widest(texts.settingLabels(), measure) > this.settingLabelWidth - LABEL_GAP) {
            problems.add("Beschriftung der Mission zu breit");
        }
        if (measure.applyAsInt(texts.previewSample()) > this.preview.w()) {
            problems.add("Vorschau zu breit: " + measure.applyAsInt(texts.previewSample()) + " > " + this.preview.w());
        }
        if (measure.applyAsInt(texts.title()) > this.title.w()) {
            problems.add("Titel zu breit");
        }
        if (measure.applyAsInt(texts.live()) + 4 > this.live.w()) {
            problems.add("Knopf Livebild zu schmal");
        }
        for (EditorTexts.Footer f : EditorTexts.Footer.values()) {
            Rect r = this.footerButtons.get(f.ordinal());
            EditorTexts.FooterLabel label = texts.footer(f);
            String shown = this.footerCompact ? label.compact() : label.full();
            if (measure.applyAsInt(shown) + 4 > r.w()) {
                problems.add("Knopf " + f + " zu schmal");
            }
        }
        for (int i = 0; i < this.templateItems.size(); i++) {
            Rect r = this.templateItems.get(i);
            if (!r.inside(this.window)) {
                problems.add("Vorlage " + i + " außerhalb");
            }
            if (measure.applyAsInt(texts.templates().get(i)) + 4 > r.w()) {
                problems.add("Vorlage " + i + " zu schmal");
            }
        }
        for (int i = 0; i < this.autoItems.size(); i++) {
            Rect r = this.autoItems.get(i);
            if (!r.inside(this.window)) {
                problems.add("Automatik-Flug " + i + " außerhalb");
            }
            if (measure.applyAsInt(texts.autoShots().get(i)) + 4 > r.w()) {
                problems.add("Automatik-Flug " + i + " zu schmal");
            }
        }
        for (int i = 0; i < this.followItems.size(); i++) {
            Rect r = this.followItems.get(i);
            if (!r.inside(this.window)) {
                problems.add("Follow-me-Stil " + i + " außerhalb");
            }
            if (measure.applyAsInt(texts.followStyles().get(i)) + 4 > r.w()) {
                problems.add("Follow-me-Stil " + i + " zu schmal");
            }
        }
        if (this.status.w() < MIN_STATUS_WIDTH) {
            problems.add("Statuszeile zu schmal: " + this.status.w());
        }
        return problems;
    }

    private static boolean nested(String a, String b) {
        return a.equals("map") && (b.equals("zoomIn") || b.equals("zoomOut") || b.equals("recenter"))
                || b.equals("map") && (a.equals("zoomIn") || a.equals("zoomOut") || a.equals("recenter"))
                || otherPage(a, b) || otherPage(b, a);
    }

    private static boolean otherPage(String a, String b) {
        return a.startsWith("detailRow") && a.endsWith("@2") && (b.startsWith("detailRow") && !b.endsWith("@2") || b.equals("pageToggle"));
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public Mode mode() {
        return this.mode;
    }

    public boolean compact() {
        return this.compact;
    }

    public boolean mapShown() {
        return this.mapShown;
    }

    public boolean listShown() {
        return this.listShown;
    }

    public int row() {
        return this.row;
    }

    public int gap() {
        return this.gap;
    }

    public int pad() {
        return this.pad;
    }

    public Rect window() {
        return this.window;
    }

    public Rect header() {
        return this.header;
    }

    public Rect title() {
        return this.title;
    }

    public Rect live() {
        return this.live;
    }

    public Rect mapToggle() {
        return this.mapToggle;
    }

    public Rect close() {
        return this.close;
    }

    public Rect previous() {
        return this.previous;
    }

    public Rect name() {
        return this.name;
    }

    public Rect next() {
        return this.next;
    }

    public Rect counter() {
        return this.counter;
    }

    public Rect missionAdd() {
        return this.missionAdd;
    }

    public Rect missionCopy() {
        return this.missionCopy;
    }

    public Rect missionDelete() {
        return this.missionDelete;
    }

    public Rect map() {
        return this.map;
    }

    public Rect zoomIn() {
        return this.zoomIn;
    }

    public Rect zoomOut() {
        return this.zoomOut;
    }

    public Rect recenter() {
        return this.recenter;
    }

    public Rect list() {
        return this.list;
    }

    public Rect listAddWaypoint() {
        return this.listAddWaypoint;
    }

    public Rect listAddPoi() {
        return this.listAddPoi;
    }

    public Rect listRows() {
        return this.listRows;
    }

    public Rect detail() {
        return this.detail;
    }

    public Rect detailTitle() {
        return this.detailTitle;
    }

    public Rect up() {
        return this.up;
    }

    public Rect down() {
        return this.down;
    }

    public Rect remove() {
        return this.remove;
    }

    public List<Rect> detailRows() {
        return this.detailRows;
    }

    public int detailLabelWidth() {
        return this.detailLabelWidth;
    }

    public Rect preview() {
        return this.preview;
    }

    public List<Rect> settings() {
        return this.settings;
    }

    public int settingLabelWidth() {
        return this.settingLabelWidth;
    }

    public Rect footer() {
        return this.footer;
    }

    public Rect footerButton(EditorTexts.Footer which) {
        return this.footerButtons.get(which.ordinal());
    }

    public boolean footerCompact() {
        return this.footerCompact;
    }

    public Rect status() {
        return this.status;
    }

    public List<Rect> templateItems() {
        return this.templateItems;
    }

    public List<Rect> autoItems() {
        return this.autoItems;
    }

    public List<Rect> followItems() {
        return this.followItems;
    }

    private static final class Builder {
        private int width;
        private int height;
        private Mode mode = Mode.NARROW;
        private boolean compact;
        private boolean mapShown;
        private boolean listShown;
        private int row;
        private int gap;
        private int pad;
        private Rect window = Rect.EMPTY;
        private Rect header = Rect.EMPTY;
        private Rect title = Rect.EMPTY;
        private Rect live = Rect.EMPTY;
        private Rect mapToggle = Rect.EMPTY;
        private Rect close = Rect.EMPTY;
        private Rect previous = Rect.EMPTY;
        private Rect name = Rect.EMPTY;
        private Rect next = Rect.EMPTY;
        private Rect counter = Rect.EMPTY;
        private Rect missionAdd = Rect.EMPTY;
        private Rect missionCopy = Rect.EMPTY;
        private Rect missionDelete = Rect.EMPTY;
        private Rect map = Rect.EMPTY;
        private Rect zoomIn = Rect.EMPTY;
        private Rect zoomOut = Rect.EMPTY;
        private Rect recenter = Rect.EMPTY;
        private Rect list = Rect.EMPTY;
        private Rect listAddWaypoint = Rect.EMPTY;
        private Rect listAddPoi = Rect.EMPTY;
        private Rect listRows = Rect.EMPTY;
        private Rect detail = Rect.EMPTY;
        private Rect detailTitle = Rect.EMPTY;
        private Rect up = Rect.EMPTY;
        private Rect down = Rect.EMPTY;
        private Rect remove = Rect.EMPTY;
        private final List<Rect> detailRows = new ArrayList<>();
        private int detailLabelWidth;
        private boolean detailPaged;
        private Rect pageToggle = Rect.EMPTY;
        private Rect preview = Rect.EMPTY;
        private final List<Rect> settings = new ArrayList<>();
        private int settingLabelWidth;
        private Rect footer = Rect.EMPTY;
        private final List<Rect> footerButtons = new ArrayList<>();
        private boolean footerCompact;
        private Rect status = Rect.EMPTY;
        private final List<Rect> templateItems = new ArrayList<>();
        private final List<Rect> autoItems = new ArrayList<>();
        private final List<Rect> followItems = new ArrayList<>();
    }
}
