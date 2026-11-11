package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record DroneSupport(Kind kind, Optional<String> role, int satellites, boolean ledStrip, boolean actionAccessory, boolean gripper,
                           List<Optional<String>> actionNames, boolean lightControl) {
    public static final int FIX_SATELLITES = 5;
    public static final String PREFIX = FieldText.PREFIX + "support.";
    public static final DroneSupport UNKNOWN = new DroneSupport(Kind.UNKNOWN, Optional.empty(), -1, false, false, false, List.of(), false);

    public enum Kind {
        UNKNOWN,
        CAMERA,
        FPV
    }

    public enum Skip {
        NONE,
        UNKNOWN,
        NO_LIGHT_API,
        NO_LEDS,
        NO_ACCESSORY,
        NO_GRIPPER;

        public boolean skipped() {
            return this != NONE && this != UNKNOWN;
        }

        public String key() {
            return PREFIX + "reason." + this.name().toLowerCase(Locale.ROOT);
        }

        Skip worse(Skip other) {
            return other.ordinal() > this.ordinal() ? other : this;
        }
    }

    public DroneSupport {
        Objects.requireNonNull(kind, "kind");
        role = Objects.requireNonNullElse(role, Optional.<String>empty());
        List<Optional<String>> names = new ArrayList<>();
        List<Optional<String>> given = Objects.requireNonNullElse(actionNames, List.<Optional<String>>of());
        for (int i = 0; i < Waypoint.Accessory.ACTIONS; i++) {
            names.add(i < given.size() && given.get(i) != null ? given.get(i) : Optional.empty());
        }
        actionNames = List.copyOf(names);
    }

    public static DroneSupport of(boolean navBoard, Optional<String> role, int satellites, boolean ledStrip, boolean actionAccessory,
                                  boolean gripper, List<Optional<String>> actionNames, boolean lightControl) {
        return new DroneSupport(navBoard ? Kind.CAMERA : Kind.FPV, role, satellites, ledStrip, actionAccessory, gripper, actionNames, lightControl);
    }

    public boolean known() {
        return this.kind != Kind.UNKNOWN;
    }

    public boolean fpv() {
        return this.kind == Kind.FPV;
    }

    public boolean satellitesKnown() {
        return this.known() && this.satellites >= 0;
    }

    public boolean gpsFix() {
        return !this.satellitesKnown() || this.satellites >= FIX_SATELLITES;
    }

    public boolean missionsWithPilot() {
        return this.kind != Kind.FPV;
    }

    public boolean obstacleSensors() {
        return this.kind != Kind.FPV;
    }

    public Optional<String> actionName(int number) {
        return number >= 1 && number <= Waypoint.Accessory.ACTIONS ? this.actionNames.get(number - 1) : Optional.empty();
    }

    public Skip light(Waypoint.Light light) {
        if (light == null || light == Waypoint.Light.NONE) {
            return Skip.NONE;
        }
        if (!this.lightControl) {
            return Skip.NO_LIGHT_API;
        }
        if (!this.known()) {
            return Skip.UNKNOWN;
        }
        return this.ledStrip ? Skip.NONE : Skip.NO_LEDS;
    }

    public Skip accessory(int number) {
        if (number <= 0) {
            return Skip.NONE;
        }
        if (!this.known()) {
            return Skip.UNKNOWN;
        }
        return this.actionAccessory ? Skip.NONE : Skip.NO_ACCESSORY;
    }

    public Skip release() {
        if (!this.known()) {
            return Skip.UNKNOWN;
        }
        return this.gripper ? Skip.NONE : Skip.NO_GRIPPER;
    }

    public Skip accessory(Mission mission, int index) {
        if (mission != null && index >= 0 && index < mission.size()
                && mission.waypoint(index).accessory() == Waypoint.Accessory.GRIPPER_RELEASE) {
            return this.release();
        }
        return this.accessory(WaypointTasks.accessoryAt(mission, index));
    }

    public Skip skip(WaypointTasks.Due due) {
        Skip skip = this.light(due.light()).worse(this.accessory(due.accessory()));
        return due.release() ? skip.worse(this.release()) : skip;
    }

    public Skip skip(Mission mission, int index) {
        return this.skip(WaypointTasks.due(mission, index));
    }

    public int skipped(Mission mission) {
        int count = 0;
        for (WaypointTasks.Due due : WaypointTasks.all(mission)) {
            if (this.skip(due).skipped()) {
                count++;
            }
        }
        return count;
    }

    public List<FieldText> header() {
        List<FieldText> out = new ArrayList<>();
        if (this.kind == Kind.FPV) {
            out.add(FieldText.of("support.header.fpv"));
        }
        if (!this.gpsFix()) {
            out.add(FieldText.of("support.header.no_gps"));
        }
        return out;
    }

    public List<FieldText> details() {
        List<FieldText> out = new ArrayList<>();
        switch (this.kind) {
            case UNKNOWN -> out.add(FieldText.of("support.unknown"));
            case CAMERA -> out.add(FieldText.of("support.camera"));
            case FPV -> {
                out.add(this.role.filter(DroneSupport::knownRole).map(r -> new FieldText(PREFIX + "role." + r, List.of()))
                        .orElseGet(() -> FieldText.of("support.fpv")));
                out.add(FieldText.of("support.fpv_pilot"));
                out.add(FieldText.of("support.fpv_sensors"));
            }
        }
        if (this.satellitesKnown()) {
            out.add(FieldText.of(this.gpsFix() ? "support.gps_ok" : "support.gps_missing", Integer.toString(this.satellites),
                    Integer.toString(FIX_SATELLITES)));
        }
        return out;
    }

    public static final List<String> ROLES = List.of("whoop", "toothpick", "cinewhoop", "freestyle", "race", "long_range", "cinelifter",
            "x_class");

    public static boolean knownRole(String role) {
        return ROLES.contains(role);
    }

    public Optional<FieldText> notice() {
        if (!this.gpsFix()) {
            return Optional.of(FieldText.of("support.notice.no_gps", Integer.toString(this.satellites), Integer.toString(FIX_SATELLITES)));
        }
        if (this.kind == Kind.FPV) {
            return Optional.of(FieldText.of("support.notice.fpv"));
        }
        return Optional.empty();
    }
}
