package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum CamControl {
    MODE_NORMAL("mode_normal", Kind.SWITCH, true),
    MODE_CINE("mode_cine", Kind.SWITCH, true),
    MODE_SPORT("mode_sport", Kind.SWITCH, true),
    MODE_CYCLE("mode_cycle", Kind.BUTTON, true),
    PAUSE("pause", Kind.BUTTON, true),
    MISSION_START("mission_start", Kind.BUTTON, true),
    WAYPOINT_ADD("waypoint_add", Kind.BUTTON, true),
    FOLLOW_ME("follow_me", Kind.SWITCH, true),
    POI_HERE("poi_here", Kind.BUTTON, true),
    COURSE_LOCK("course_lock", Kind.SWITCH, true),
    GIMBAL_LOCK("gimbal_lock", Kind.SWITCH, false),
    GIMBAL_RECENTER("gimbal_recenter", Kind.BUTTON, false),
    GIMBAL_FPV("gimbal_fpv", Kind.SWITCH, false),
    PILOT_VIEW("pilot_view", Kind.BUTTON, false),
    GIMBAL_TILT("gimbal_tilt", Kind.AXIS, false, 0.0, 60.0),
    GIMBAL_PAN("gimbal_pan", Kind.AXIS, false, 0.0, 90.0),
    ZOOM("zoom", Kind.AXIS, false, -1.0, 45.0);

    public enum Kind {
        SWITCH,
        BUTTON,
        AXIS
    }

    private final String path;
    private final Kind kind;
    private final boolean serverSync;
    private final double center;
    private final double keyRateDps;
    private final Identifier id;
    private final String idString;

    CamControl(String path, Kind kind, boolean serverSync) {
        this(path, kind, serverSync, 0.0, 0.0);
    }

    CamControl(String path, Kind kind, boolean serverSync, double center, double keyRateDps) {
        this.path = path;
        this.kind = kind;
        this.serverSync = serverSync;
        this.center = center;
        this.keyRateDps = keyRateDps;
        this.id = CamDrones.id(path);
        this.idString = this.id.toString();
    }

    public String path() {
        return this.path;
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean axis() {
        return this.kind == Kind.AXIS;
    }

    public boolean toggle() {
        return this.kind == Kind.SWITCH;
    }

    public boolean serverSync() {
        return this.serverSync;
    }

    public double center() {
        return this.center;
    }

    public double keyRateDps() {
        return this.keyRateDps;
    }

    public Identifier id() {
        return this.id;
    }

    public String idString() {
        return this.idString;
    }

    public String nameKey() {
        return CamDrones.MOD_ID + (this.axis() ? ".axis." : ".switch.") + this.path;
    }

    public String descriptionKey() {
        return this.nameKey() + ".description";
    }

    public static List<CamControl> switches() {
        return Arrays.stream(values()).filter(control -> !control.axis()).toList();
    }

    public static List<CamControl> axes() {
        return Arrays.stream(values()).filter(CamControl::axis).toList();
    }

    public static Optional<CamControl> byPath(String path) {
        for (CamControl control : values()) {
            if (control.path.equals(path)) {
                return Optional.of(control);
            }
        }
        return Optional.empty();
    }
}
