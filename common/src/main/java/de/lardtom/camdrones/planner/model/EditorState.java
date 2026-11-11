package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

public final class EditorState {
    public static final double NEXT_WAYPOINT_DISTANCE = 10.0;
    public static final int RADIUS_STEP_DM = 10;
    public static final int RADIUS_BIG_STEP_DM = 50;
    public static final double DEFAULT_POI_RADIUS = 15.0;

    public enum Kind {
        NONE,
        WAYPOINT,
        POI
    }

    public record Selection(Kind kind, int index) {
        public static final Selection NONE = new Selection(Kind.NONE, -1);

        public static Selection waypoint(int index) {
            return new Selection(Kind.WAYPOINT, index);
        }

        public static Selection poi(int index) {
            return new Selection(Kind.POI, index);
        }
    }

    public record Status(FieldText text, boolean error) {
        public static Status info(String key, String... args) {
            return new Status(FieldText.of(key, args), false);
        }

        public static Status error(String key, String... args) {
            return new Status(FieldText.of(key, args), true);
        }
    }

    private TabletState tablet;
    private TabletState saved;
    private Selection selection = Selection.NONE;
    private final TreeSet<Integer> picked = new TreeSet<>();
    private int anchor = -1;
    private boolean mapExpanded;
    private boolean templateMenu;
    private boolean autoMenu;
    private boolean followMenu;
    private final MapView view = new MapView();
    private FlightClass flightClass;
    private double startX;
    private double startY;
    private double startZ;
    private Status status;
    private int listScroll;

    public EditorState(TabletState tablet, FlightClass flightClass, double startX, double startY, double startZ, boolean mapExpanded) {
        this.tablet = Objects.requireNonNull(tablet, "tablet").ensureActive();
        this.saved = this.tablet;
        this.flightClass = flightClass == null ? FlightClass.TOUR : flightClass;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.mapExpanded = mapExpanded;
        this.view.center(startX, startZ);
        if (this.mission().size() > 0) {
            this.single(Selection.waypoint(0));
        }
    }

    public TabletState tablet() {
        return this.tablet;
    }

    public boolean dirty() {
        return !this.tablet.equals(this.saved);
    }

    public void markSaved() {
        this.saved = this.tablet;
    }

    public MapView view() {
        return this.view;
    }

    public boolean mapExpanded() {
        return this.mapExpanded;
    }

    public void toggleMap() {
        this.mapExpanded = !this.mapExpanded;
    }

    public boolean templateMenu() {
        return this.templateMenu;
    }

    public void templateMenu(boolean open) {
        this.templateMenu = open;
        if (open) {
            this.autoMenu = false;
            this.followMenu = false;
        }
    }

    public boolean autoMenu() {
        return this.autoMenu;
    }

    public void autoMenu(boolean open) {
        this.autoMenu = open;
        if (open) {
            this.templateMenu = false;
            this.followMenu = false;
        }
    }

    public boolean followMenu() {
        return this.followMenu;
    }

    public void followMenu(boolean open) {
        this.followMenu = open;
        if (open) {
            this.templateMenu = false;
            this.autoMenu = false;
        }
    }

    public FlightClass flightClass() {
        return this.flightClass;
    }

    public void flightClass(FlightClass value) {
        if (value != null) {
            this.flightClass = value;
        }
    }

    public double startX() {
        return this.startX;
    }

    public double startY() {
        return this.startY;
    }

    public double startZ() {
        return this.startZ;
    }

    public Optional<Status> status() {
        return Optional.ofNullable(this.status);
    }

    public void status(Status value) {
        this.status = value;
    }

    public int listScroll() {
        return this.listScroll;
    }

    public void listScroll(int value, int visibleRows) {
        this.listScroll = Math.clamp(value, 0, Math.max(0, this.rowCount() - Math.max(1, visibleRows)));
    }

    public PlannerEntry entry() {
        return this.tablet.activeEntry().orElseThrow();
    }

    public boolean editable() {
        return this.entry().readable();
    }

    public Mission mission() {
        return this.tablet.activeMission();
    }

    public int missionIndex() {
        return this.tablet.active();
    }

    public int missionCount() {
        return this.tablet.size();
    }

    public String missionName() {
        return this.tablet.activeName();
    }

    public Selection selection() {
        return this.selection;
    }

    public Optional<Waypoint> selectedWaypoint() {
        Mission mission = this.mission();
        if (this.selection.kind() == Kind.WAYPOINT && this.selection.index() >= 0 && this.selection.index() < mission.size()) {
            return Optional.of(mission.waypoint(this.selection.index()));
        }
        return Optional.empty();
    }

    public Optional<PointOfInterest> selectedPoi() {
        List<PointOfInterest> points = this.mission().pointsOfInterest();
        if (this.selection.kind() == Kind.POI && this.selection.index() >= 0 && this.selection.index() < points.size()) {
            return Optional.of(points.get(this.selection.index()));
        }
        return Optional.empty();
    }

    public void select(Selection value) {
        Mission mission = this.mission();
        boolean valid = switch (value.kind()) {
            case NONE -> true;
            case WAYPOINT -> value.index() >= 0 && value.index() < mission.size();
            case POI -> value.index() >= 0 && value.index() < mission.pointsOfInterest().size();
        };
        this.single(valid ? value : Selection.NONE);
        this.closeMenus();
    }

    private void single(Selection value) {
        this.selection = value;
        this.picked.clear();
        if (value.kind() == Kind.WAYPOINT) {
            this.picked.add(value.index());
            this.anchor = value.index();
        } else {
            this.anchor = -1;
        }
    }

    private void closeMenus() {
        this.templateMenu = false;
        this.autoMenu = false;
        this.followMenu = false;
    }

    private boolean waypointRow(Selection value) {
        return value.kind() == Kind.WAYPOINT && value.index() >= 0 && value.index() < this.mission().size();
    }

    public List<Integer> selectedWaypoints() {
        return List.copyOf(this.picked);
    }

    public boolean multiple() {
        return this.picked.size() > 1;
    }

    public boolean selected(Selection row) {
        if (row.kind() == Kind.WAYPOINT) {
            return this.picked.contains(row.index());
        }
        return row.kind() != Kind.NONE && row.equals(this.selection);
    }

    public boolean toggle(Selection row) {
        if (!this.waypointRow(row) || this.selection.kind() != Kind.WAYPOINT) {
            this.select(row);
            return true;
        }
        int index = row.index();
        if (this.picked.contains(index)) {
            if (this.picked.size() == 1) {
                return false;
            }
            this.picked.remove(index);
            if (this.selection.index() == index) {
                Integer lower = this.picked.lower(index);
                this.selection = Selection.waypoint(lower != null ? lower : this.picked.first());
            }
        } else {
            this.picked.add(index);
            this.selection = Selection.waypoint(index);
        }
        this.anchor = index;
        this.closeMenus();
        return true;
    }

    public boolean extendTo(Selection row) {
        if (!this.waypointRow(row) || this.selection.kind() != Kind.WAYPOINT || this.anchor < 0 || this.anchor >= this.mission().size()) {
            this.select(row);
            return true;
        }
        this.picked.clear();
        for (int i = Math.min(this.anchor, row.index()); i <= Math.max(this.anchor, row.index()); i++) {
            this.picked.add(i);
        }
        this.selection = Selection.waypoint(row.index());
        this.closeMenus();
        return true;
    }

    public boolean selectAllWaypoints() {
        int size = this.mission().size();
        if (size == 0) {
            return false;
        }
        if (this.selection.kind() != Kind.WAYPOINT) {
            this.single(Selection.waypoint(0));
        }
        for (int i = 0; i < size; i++) {
            this.picked.add(i);
        }
        this.closeMenus();
        return true;
    }

    public boolean enabled(WaypointField field) {
        Mission mission = this.mission();
        for (int index : this.picked) {
            if (field.enabled(mission.waypoint(index))) {
                return true;
            }
        }
        return false;
    }

    public boolean mixed(WaypointField field) {
        if (this.picked.size() < 2) {
            return false;
        }
        Mission mission = this.mission();
        FieldText first = null;
        for (int index : this.picked) {
            FieldText value = field.value(mission.waypoint(index), '.', 0.0);
            if (first == null) {
                first = value;
            } else if (!first.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public int rowCount() {
        Mission mission = this.mission();
        return mission.size() + mission.pointsOfInterest().size();
    }

    public Selection row(int index) {
        Mission mission = this.mission();
        if (index < 0 || index >= this.rowCount()) {
            return Selection.NONE;
        }
        return index < mission.size() ? Selection.waypoint(index) : Selection.poi(index - mission.size());
    }

    public int rowOf(Selection value) {
        return switch (value.kind()) {
            case NONE -> -1;
            case WAYPOINT -> value.index();
            case POI -> this.mission().size() + value.index();
        };
    }

    public boolean selectMission(int index) {
        if (index < 0 || index >= this.tablet.size() || index == this.tablet.active()) {
            return false;
        }
        this.tablet = this.tablet.withActive(index);
        this.afterMissionSwitch();
        return true;
    }

    public boolean stepMission(int delta) {
        if (this.tablet.size() < 2 || delta == 0) {
            return false;
        }
        return this.selectMission(Math.floorMod(this.tablet.active() + delta, this.tablet.size()));
    }

    public boolean addMission() {
        Optional<TabletState> next = this.tablet.addMission("");
        if (next.isEmpty()) {
            this.status = Status.error("status.missions_full", Integer.toString(TabletState.MAX_MISSIONS));
            return false;
        }
        this.tablet = next.get();
        this.afterMissionSwitch();
        return true;
    }

    public boolean duplicateMission() {
        Optional<TabletState> next = this.tablet.duplicateActive();
        if (next.isEmpty()) {
            this.status = Status.error("status.missions_full", Integer.toString(TabletState.MAX_MISSIONS));
            return false;
        }
        this.tablet = next.get();
        this.afterMissionSwitch();
        return true;
    }

    public boolean deleteMission() {
        this.tablet = this.tablet.removeActive().ensureActive();
        this.afterMissionSwitch();
        return true;
    }

    public boolean rename(String name) {
        String clean = MissionNames.clean(name);
        if (clean.equals(this.entry().name())) {
            return false;
        }
        this.tablet = this.tablet.withEntry(this.tablet.active(), this.entry().withName(clean));
        return true;
    }

    public boolean stepGroundHeight(int steps) {
        int next = TabletState.clampGroundHeight(this.tablet.groundHeight() + steps);
        if (next == this.tablet.groundHeight()) {
            return false;
        }
        this.tablet = this.tablet.withGroundHeight(next);
        return true;
    }

    private void afterMissionSwitch() {
        this.single(this.mission().size() > 0 ? Selection.waypoint(0) : Selection.NONE);
        this.listScroll = 0;
        this.templateMenu = false;
        this.autoMenu = false;
        this.followMenu = false;
    }

    private boolean replace(Mission next) {
        if (!this.editable()) {
            this.status = Status.error("status.locked");
            return false;
        }
        if (next.equals(this.mission())) {
            return false;
        }
        this.tablet = this.tablet.withEntry(this.tablet.active(), this.entry().withMission(next));
        this.prune();
        return true;
    }

    private void prune() {
        Mission mission = this.mission();
        this.picked.removeIf(index -> index >= mission.size());
        boolean valid = switch (this.selection.kind()) {
            case NONE -> true;
            case WAYPOINT -> this.selection.index() < mission.size();
            case POI -> this.selection.index() < mission.pointsOfInterest().size();
        };
        if (!valid) {
            this.single(this.picked.isEmpty() ? Selection.NONE : Selection.waypoint(this.picked.last()));
        } else if (this.selection.kind() == Kind.WAYPOINT && this.picked.isEmpty()) {
            this.single(this.selection);
        }
    }

    public boolean revert(TabletState before) {
        if (before == null || before.active() != this.tablet.active() || before.equals(this.tablet)) {
            return false;
        }
        this.tablet = before;
        this.prune();
        return true;
    }

    public boolean addWaypoint(Waypoint waypoint) {
        Mission mission = this.mission();
        if (mission.size() >= MissionLimits.MAX_WAYPOINTS) {
            this.status = Status.error("status.waypoints_full", Integer.toString(MissionLimits.MAX_WAYPOINTS));
            return false;
        }
        int at = this.selection.kind() == Kind.WAYPOINT ? this.selection.index() + 1 : mission.size();
        List<Waypoint> list = new ArrayList<>(mission.waypoints());
        list.add(at, waypoint);
        if (!this.replace(mission.withWaypoints(list))) {
            return false;
        }
        this.single(Selection.waypoint(at));
        return true;
    }

    public boolean addWaypointAt(double x, double y, double z) {
        return this.addWaypoint(Waypoint.at(x, WaypointPlacement.clampY(y), z));
    }

    public Waypoint nextWaypointGuess(double groundY) {
        Mission mission = this.mission();
        Optional<Waypoint> selected = this.selectedWaypoint();
        if (selected.isEmpty() && mission.size() > 0) {
            selected = Optional.of(mission.waypoint(mission.size() - 1));
        }
        if (selected.isEmpty()) {
            return Waypoint.at(this.startX, WaypointPlacement.clampY(groundY + this.tablet.groundHeight()), this.startZ);
        }
        Waypoint base = selected.get();
        int index = mission.waypoints().indexOf(base);
        double dx = 1.0;
        double dz = 0.0;
        if (index > 0) {
            Waypoint previous = mission.waypoint(index - 1);
            double len = Math.hypot(base.x() - previous.x(), base.z() - previous.z());
            if (len > 0.1) {
                dx = (base.x() - previous.x()) / len;
                dz = (base.z() - previous.z()) / len;
            }
        }
        return Waypoint.at(base.x() + dx * NEXT_WAYPOINT_DISTANCE, base.y(), base.z() + dz * NEXT_WAYPOINT_DISTANCE);
    }

    public boolean moveWaypoint(int index, double x, double z) {
        Mission mission = this.mission();
        if (index < 0 || index >= mission.size()) {
            return false;
        }
        Waypoint w = mission.waypoint(index);
        return this.replace(mission.replace(index, w.withPosition(x, w.y(), z)));
    }

    public boolean deleteSelected() {
        Mission mission = this.mission();
        return switch (this.selection.kind()) {
            case NONE -> false;
            case WAYPOINT -> {
                List<Integer> gone = this.selectedWaypoints();
                if (gone.isEmpty()) {
                    yield false;
                }
                List<Waypoint> list = new ArrayList<>(mission.waypoints());
                for (int i = gone.size() - 1; i >= 0; i--) {
                    list.remove((int) gone.get(i));
                }
                if (!this.replace(mission.withWaypoints(list))) {
                    yield false;
                }
                int left = this.mission().size();
                this.single(left == 0 ? Selection.NONE : Selection.waypoint(Math.min(gone.getFirst(), left - 1)));
                yield true;
            }
            case POI -> this.deletePoi(this.selection.index());
        };
    }

    public boolean moveSelected(int delta) {
        if (this.selection.kind() != Kind.WAYPOINT || delta == 0 || this.multiple()) {
            return false;
        }
        Mission mission = this.mission();
        int from = this.selection.index();
        int to = from + Integer.signum(delta);
        if (from < 0 || from >= mission.size() || to < 0 || to >= mission.size()) {
            return false;
        }
        List<Waypoint> list = new ArrayList<>(mission.waypoints());
        Waypoint moved = list.remove(from);
        list.add(to, moved);
        if (!this.replace(mission.withWaypoints(list))) {
            return false;
        }
        this.single(Selection.waypoint(to));
        return true;
    }

    public boolean stepWaypoint(WaypointField field, int steps, boolean big) {
        return this.stepWaypoint(field, steps, big, false);
    }

    public boolean stepWaypoint(WaypointField field, int steps, boolean big, boolean each) {
        Optional<Waypoint> selected = this.selectedWaypoint();
        if (selected.isEmpty()) {
            return false;
        }
        Mission mission = this.mission();
        int points = mission.pointsOfInterest().size();
        List<Waypoint> list = new ArrayList<>(mission.waypoints());
        if (each || !this.multiple()) {
            for (int index : this.picked) {
                list.set(index, field.step(list.get(index), steps, big, points, this.flightClass));
            }
        } else {
            Optional<Waypoint> reference = this.reference(field);
            if (reference.isEmpty()) {
                return false;
            }
            Waypoint next = field.step(reference.get(), steps, big, points, this.flightClass);
            for (int index : this.picked) {
                list.set(index, field.copy(next, list.get(index)));
            }
        }
        return this.replace(mission.withWaypoints(list));
    }

    private Optional<Waypoint> reference(WaypointField field) {
        Optional<Waypoint> lead = this.selectedWaypoint();
        if (lead.isPresent() && field.enabled(lead.get())) {
            return lead;
        }
        Mission mission = this.mission();
        for (int index : this.picked) {
            if (field.enabled(mission.waypoint(index))) {
                return Optional.of(mission.waypoint(index));
            }
        }
        return Optional.empty();
    }

    public Optional<ValueEntry.Range> entryRange(int row) {
        return switch (this.selection.kind()) {
            case WAYPOINT -> {
                if (row < 0 || row >= WaypointField.values().length) {
                    yield Optional.empty();
                }
                WaypointField field = WaypointField.values()[row];
                yield field.typeable() && this.enabled(field) ? Optional.of(field.range(this.startY, this.flightClass)) : Optional.empty();
            }
            case POI -> row == 0 ? Optional.of(WaypointField.heightRange(this.startY))
                    : row == 1 ? Optional.of(ValueEntry.Range.of(0.0, MissionLimits.MAX_RADIUS_DM / 10.0, 1)) : Optional.empty();
            case NONE -> Optional.empty();
        };
    }

    public boolean typeable(int row) {
        return this.editable() && this.entryRange(row).isPresent();
    }

    public String entryText(int row, char decimal) {
        Optional<ValueEntry.Range> range = this.entryRange(row);
        if (range.isEmpty()) {
            return "";
        }
        if (this.selection.kind() == Kind.POI) {
            PointOfInterest p = this.selectedPoi().orElseThrow();
            double value = row == 0 ? (p.yDm() - MissionLimits.toDecimetres(this.startY)) / 10.0 : p.radius();
            return ValueEntry.text(value, range.get(), decimal);
        }
        WaypointField field = WaypointField.values()[row];
        Mission mission = this.mission();
        String shown = null;
        for (int index : this.picked) {
            Waypoint w = mission.waypoint(index);
            if (!field.enabled(w)) {
                continue;
            }
            String text = ValueEntry.text(field.shown(w, this.startY), range.get(), decimal);
            if (shown == null) {
                shown = text;
            } else if (!shown.equals(text)) {
                return "";
            }
        }
        return shown == null ? "" : shown;
    }

    public ValueEntry.Status commitEntry(int row, String text, char decimal) {
        Optional<ValueEntry.Range> range = this.entryRange(row);
        if (range.isEmpty()) {
            return ValueEntry.Status.EMPTY;
        }
        ValueEntry.Result result = ValueEntry.evaluate(text, range.get());
        if (!result.usable()) {
            return result.status();
        }
        double value = result.value();
        Mission mission = this.mission();
        if (this.selection.kind() == Kind.POI) {
            PointOfInterest p = this.selectedPoi().orElseThrow();
            PointOfInterest next = row == 0 ? new PointOfInterest(p.xDm(), WaypointField.heightDm(value, this.startY), p.zDm(), p.radiusDm())
                    : new PointOfInterest(p.xDm(), p.yDm(), p.zDm(), (int) Math.clamp(Math.round(value * 10.0), 0, MissionLimits.MAX_RADIUS_DM));
            this.replacePoi(next);
        } else {
            WaypointField field = WaypointField.values()[row];
            List<Waypoint> list = new ArrayList<>(mission.waypoints());
            for (int index : this.picked) {
                list.set(index, field.typed(list.get(index), value, this.startY, this.flightClass));
            }
            this.replace(mission.withWaypoints(list));
        }
        if (result.status() == ValueEntry.Status.CLAMPED) {
            this.status = Status.error("status.clamped", ValueEntry.text(value, range.get(), decimal), ValueEntry.text(range.get().min(), range.get(), decimal),
                    ValueEntry.text(range.get().max(), range.get(), decimal));
        }
        return result.status();
    }

    public List<FieldText> entryHint(int row, String text, char decimal) {
        List<FieldText> lines = new ArrayList<>();
        Optional<ValueEntry.Range> range = this.entryRange(row);
        if (range.isEmpty()) {
            return lines;
        }
        ValueEntry.Range r = range.get();
        ValueEntry.Result result = ValueEntry.evaluate(text, r);
        String min = ValueEntry.text(r.min(), r, decimal);
        String max = ValueEntry.text(r.max(), r, decimal);
        lines.add(switch (result.status()) {
            case INVALID -> FieldText.of("entry.invalid");
            case CLAMPED -> FieldText.of("entry.will_clamp", ValueEntry.text(result.value(), r, decimal), min, max);
            default -> FieldText.of("entry.range", min, max);
        });
        if (this.multiple() && this.selection.kind() == Kind.WAYPOINT) {
            lines.add(FieldText.of("entry.all", Integer.toString(this.picked.size())));
        }
        lines.add(FieldText.of("entry.keys"));
        return lines;
    }

    public boolean stepMissionField(MissionField field, int steps, boolean big) {
        return this.replace(field.step(this.mission(), steps, big));
    }

    public boolean addPoi(double x, double y, double z) {
        Mission mission = this.mission();
        if (mission.pointsOfInterest().size() >= MissionLimits.MAX_POINTS_OF_INTEREST) {
            this.status = Status.error("status.poi_full", Integer.toString(MissionLimits.MAX_POINTS_OF_INTEREST));
            return false;
        }
        List<PointOfInterest> points = new ArrayList<>(mission.pointsOfInterest());
        points.add(PointOfInterest.at(x, WaypointPlacement.clampY(y), z, DEFAULT_POI_RADIUS));
        if (!this.replace(mission.withPointsOfInterest(points))) {
            return false;
        }
        this.single(Selection.poi(points.size() - 1));
        return true;
    }

    public boolean movePoi(int index, double x, double z) {
        Mission mission = this.mission();
        if (index < 0 || index >= mission.pointsOfInterest().size()) {
            return false;
        }
        List<PointOfInterest> points = new ArrayList<>(mission.pointsOfInterest());
        PointOfInterest p = points.get(index);
        points.set(index, PointOfInterest.at(x, p.y(), z, p.radius()));
        return this.replace(mission.withPointsOfInterest(points));
    }

    public boolean stepPoiHeight(int steps, boolean big) {
        Optional<PointOfInterest> selected = this.selectedPoi();
        if (selected.isEmpty()) {
            return false;
        }
        PointOfInterest p = selected.get();
        int y = Math.clamp((long) p.yDm() + (long) steps * (big ? WaypointField.HEIGHT_BIG_STEP_DM : WaypointField.HEIGHT_STEP_DM),
                MissionLimits.MIN_Y_DM, MissionLimits.MAX_Y_DM);
        return this.replacePoi(new PointOfInterest(p.xDm(), y, p.zDm(), p.radiusDm()));
    }

    public boolean stepPoiRadius(int steps, boolean big) {
        Optional<PointOfInterest> selected = this.selectedPoi();
        if (selected.isEmpty()) {
            return false;
        }
        PointOfInterest p = selected.get();
        int radius = Math.clamp(p.radiusDm() + steps * (big ? RADIUS_BIG_STEP_DM : RADIUS_STEP_DM), 0, MissionLimits.MAX_RADIUS_DM);
        return this.replacePoi(new PointOfInterest(p.xDm(), p.yDm(), p.zDm(), radius));
    }

    private boolean replacePoi(PointOfInterest value) {
        Mission mission = this.mission();
        List<PointOfInterest> points = new ArrayList<>(mission.pointsOfInterest());
        points.set(this.selection.index(), value);
        return this.replace(mission.withPointsOfInterest(points));
    }

    public boolean deletePoi(int index) {
        Mission mission = this.mission();
        if (index < 0 || index >= mission.pointsOfInterest().size()) {
            return false;
        }
        List<PointOfInterest> points = new ArrayList<>(mission.pointsOfInterest());
        points.remove(index);
        List<Waypoint> waypoints = new ArrayList<>();
        for (Waypoint w : mission.waypoints()) {
            if (w.heading() == Waypoint.Heading.POI && w.poi() == index) {
                waypoints.add(w.withLook(Waypoint.Heading.COURSE, 0));
            } else if (w.heading() == Waypoint.Heading.POI && w.poi() > index) {
                waypoints.add(w.facing(w.poi() - 1));
            } else {
                waypoints.add(w);
            }
        }
        if (!this.replace(mission.withWaypoints(waypoints).withPointsOfInterest(points))) {
            return false;
        }
        this.single(points.isEmpty() ? (waypoints.isEmpty() ? Selection.NONE : Selection.waypoint(waypoints.size() - 1))
                : Selection.poi(Math.min(index, points.size() - 1)));
        return true;
    }

    public boolean applyTemplate(MissionTemplates.Template template, MissionTemplates.Anchor anchor) {
        this.templateMenu = false;
        Optional<Mission> next = MissionTemplates.apply(template, this.mission(), anchor);
        if (next.isEmpty()) {
            this.status = Status.error("status.template_full");
            return false;
        }
        int before = this.mission().size();
        if (!this.replace(next.get())) {
            return false;
        }
        int after = this.mission().size();
        if (after > before) {
            this.single(Selection.waypoint(before));
        }
        return true;
    }

    public MissionTemplates.Anchor anchor(double groundY, double yawDegrees) {
        Optional<PointOfInterest> poi = this.selectedPoi();
        if (poi.isPresent()) {
            return new MissionTemplates.Anchor(poi.get().x(), poi.get().y(), poi.get().z(), yawDegrees);
        }
        return new MissionTemplates.Anchor(this.view.centerX(), groundY, this.view.centerZ(), yawDegrees);
    }

    public FlightTimeEstimate.Preview preview() {
        Mission mission = this.mission();
        if (mission.isEmpty()) {
            return new FlightTimeEstimate.Preview(0.0, 0.0, 0.0);
        }
        return FlightTimeEstimate.mission(this.flightClass, mission, this.startX, this.startY, this.startZ);
    }
}
