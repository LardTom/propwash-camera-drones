package de.lardtom.camdrones.logic.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WaypointTasks {
    public record Due(int index, Waypoint.Light light, int accessory, boolean repeated, int everyTicks, boolean timed, boolean release) {
        public Due {
            Objects.requireNonNull(light, "light");
        }

        public Due(int index, Waypoint.Light light, int accessory, boolean repeated, int everyTicks, boolean timed) {
            this(index, light, accessory, repeated, everyTicks, timed, false);
        }

        public Due(int index, Waypoint.Light light, int accessory, boolean repeated) {
            this(index, light, accessory, repeated, 0, false);
        }

        public boolean any() {
            return this.light != Waypoint.Light.NONE || this.accessory > 0 || this.release;
        }

        public Due withoutAccessory() {
            return new Due(this.index, this.light, 0, false, 0, false, this.release);
        }
    }

    public record Timed(int source, int accessory, int ticks) {
        public boolean sameSetting(Timed other) {
            return other != null && other.accessory == this.accessory && other.ticks == this.ticks;
        }
    }

    private WaypointTasks() {
    }

    public static int accessoryAt(Mission mission, int index) {
        if (mission == null || index < 0 || index >= mission.size()) {
            return 0;
        }
        Waypoint own = mission.waypoint(index);
        if (own.accessory().number() > 0) {
            return own.accessory().number();
        }
        if (endsRepeat(own)) {
            return 0;
        }
        for (int i = index - 1; i >= 0; i--) {
            Waypoint w = mission.waypoint(i);
            if (endsRepeat(w)) {
                return 0;
            }
            if (w.accessory().number() > 0) {
                return w.repeat() ? w.accessory().number() : 0;
            }
        }
        return 0;
    }

    public static int repeatSource(Mission mission, int index) {
        if (mission == null || index < 0 || index >= mission.size() || mission.waypoint(index).accessory() != Waypoint.Accessory.NONE) {
            return -1;
        }
        for (int i = index - 1; i >= 0; i--) {
            Waypoint w = mission.waypoint(i);
            if (endsRepeat(w)) {
                return -1;
            }
            if (w.accessory().number() > 0) {
                return w.repeat() ? i : -1;
            }
        }
        return -1;
    }

    public static Due due(Mission mission, int index) {
        if (mission == null || index < 0 || index >= mission.size()) {
            return new Due(index, Waypoint.Light.NONE, 0, false);
        }
        Waypoint own = mission.waypoint(index);
        int accessory = continues(mission, index) ? 0 : accessoryAt(mission, index);
        boolean repeated = accessory > 0 && own.accessory().number() == 0;
        return new Due(index, own.light(), accessory, repeated, accessory > 0 ? own.everyTicks() : 0, false,
                own.accessory() == Waypoint.Accessory.GRIPPER_RELEASE);
    }

    public static List<Due> all(Mission mission) {
        List<Due> out = new ArrayList<>();
        if (mission == null) {
            return out;
        }
        for (int i = 0; i < mission.size(); i++) {
            Due due = due(mission, i);
            if (due.any()) {
                out.add(due);
            }
        }
        return out;
    }

    public static Optional<Timed> timedAt(Mission mission, int index) {
        if (mission == null || index < 0 || index >= mission.size()) {
            return Optional.empty();
        }
        for (int i = index; i >= 0; i--) {
            Waypoint w = mission.waypoint(i);
            if (w.accessory().number() > 0) {
                return w.everyTicks() > 0 ? Optional.of(new Timed(i, w.accessory().number(), w.everyTicks())) : Optional.empty();
            }
            if (endsRepeat(w)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static int timedSource(Mission mission, int index) {
        if (mission == null || index < 0 || index >= mission.size() || mission.waypoint(index).accessory() != Waypoint.Accessory.NONE) {
            return -1;
        }
        return timedAt(mission, index).map(Timed::source).orElse(-1);
    }

    public static boolean continues(Mission mission, int index) {
        if (mission == null || index <= 0 || index >= mission.size() || mission.waypoint(index).everyTicks() == 0) {
            return false;
        }
        Optional<Timed> own = timedAt(mission, index);
        return own.isPresent() && timedAt(mission, index - 1).filter(own.get()::sameSetting).isPresent();
    }

    public static boolean any(Mission mission) {
        return !all(mission).isEmpty();
    }

    private static boolean endsRepeat(Waypoint w) {
        return w.accessory() == Waypoint.Accessory.END_REPEAT || w.accessory() == Waypoint.Accessory.GRIPPER_RELEASE;
    }
}
