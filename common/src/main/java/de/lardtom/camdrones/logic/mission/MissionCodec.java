package de.lardtom.camdrones.logic.mission;

import de.lardtom.camdrones.logic.nav.ObstacleGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MissionCodec {
    public static final int FLAG_SMOOTH = 1;
    public static final int FLAG_LOOP = 2;
    private static final int KNOWN_FLAGS = FLAG_SMOOTH | FLAG_LOOP;
    private static final int TILT_OFFSET = 128;
    private static final int ACTION_SHIFT = 16;
    private static final int LIGHT_SHIFT = 20;
    private static final int ACCESSORY_SHIFT = 24;
    private static final int REPEAT_BIT = 1 << 28;
    private static final int EVERY_SHIFT = 16;
    private static final int NIBBLE = 0xF;
    private static final int TASK_BITS = ~0x1FFFFFFF;

    public enum Status {
        OK,
        EMPTY,
        UNKNOWN_VERSION,
        CORRUPT
    }

    public record Result(Status status, Optional<Mission> mission) {
        public boolean ok() {
            return this.status == Status.OK;
        }

        static Result of(Status status) {
            return new Result(status, Optional.empty());
        }
    }

    private MissionCodec() {
    }

    public static int[] encode(Mission mission) {
        List<Waypoint> waypoints = mission.waypoints();
        List<PointOfInterest> points = mission.pointsOfInterest();
        int[] data = new int[MissionLimits.packedInts(waypoints.size(), points.size())];
        data[0] = formatFor(mission);
        data[1] = (mission.smooth() ? FLAG_SMOOTH : 0) | (mission.loop() ? FLAG_LOOP : 0);
        data[2] = mission.end().ordinal();
        data[3] = mission.baseSpeedDmS();
        data[4] = mission.obstacle().ordinal();
        data[5] = waypoints.size();
        int i = MissionLimits.HEADER_INTS;
        for (Waypoint w : waypoints) {
            data[i++] = w.xDm();
            data[i++] = w.yDm();
            data[i++] = w.zDm();
            data[i++] = w.speedDmS() << 16 | w.waitDs();
            data[i++] = w.everyTicks() << EVERY_SHIFT | (w.tiltDeg() + TILT_OFFSET) << 8 | w.poi() << 4 | w.heading().ordinal();
            data[i++] = tasks(w) | w.action().ordinal() << ACTION_SHIFT | w.yawDeg();
        }
        for (PointOfInterest p : points) {
            data[i++] = p.xDm();
            data[i++] = p.yDm();
            data[i++] = p.zDm();
            data[i++] = p.radiusDm();
        }
        return data;
    }

    public static int formatFor(Mission mission) {
        int format = MissionLimits.FIRST_FORMAT_VERSION;
        for (Waypoint waypoint : mission.waypoints()) {
            if (waypoint.accessory() == Waypoint.Accessory.GRIPPER_RELEASE) {
                return MissionLimits.GRIPPER_FORMAT_VERSION;
            }
            if (waypoint.everyTicks() > 0) {
                format = MissionLimits.EVERY_FORMAT_VERSION;
            } else if (waypoint.hasTasks()) {
                format = Math.max(format, MissionLimits.TASK_FORMAT_VERSION);
            }
        }
        return format;
    }

    public static boolean known(int version) {
        return version >= MissionLimits.FIRST_FORMAT_VERSION && version <= MissionLimits.FORMAT_VERSION;
    }

    private static int tasks(Waypoint w) {
        return w.light().ordinal() << LIGHT_SHIFT | w.accessory().ordinal() << ACCESSORY_SHIFT | (w.repeat() ? REPEAT_BIT : 0);
    }

    public static int version(int[] data) {
        return data == null || data.length == 0 ? 0 : data[0];
    }

    public static Result decode(int[] data) {
        if (data == null || data.length == 0) {
            return Result.of(Status.EMPTY);
        }
        int version = data[0];
        if (!known(version)) {
            return Result.of(version > MissionLimits.FORMAT_VERSION ? Status.UNKNOWN_VERSION : Status.CORRUPT);
        }
        if (data.length < MissionLimits.HEADER_INTS) {
            return Result.of(Status.CORRUPT);
        }
        int flags = data[1];
        int count = data[5];
        if ((flags & ~KNOWN_FLAGS) != 0 || count < 0 || count > MissionLimits.MAX_WAYPOINTS) {
            return Result.of(Status.CORRUPT);
        }
        int rest = data.length - MissionLimits.HEADER_INTS - count * MissionLimits.INTS_PER_WAYPOINT;
        if (rest < 0 || rest % MissionLimits.INTS_PER_POINT_OF_INTEREST != 0
                || rest / MissionLimits.INTS_PER_POINT_OF_INTEREST > MissionLimits.MAX_POINTS_OF_INTEREST) {
            return Result.of(Status.CORRUPT);
        }
        Mission.End[] ends = Mission.End.values();
        ObstacleGuard.Mode[] obstacles = ObstacleGuard.Mode.values();
        if (data[2] < 0 || data[2] >= ends.length || data[4] < 0 || data[4] >= obstacles.length) {
            return Result.of(Status.CORRUPT);
        }
        try {
            List<Waypoint> waypoints = new ArrayList<>(count);
            int i = MissionLimits.HEADER_INTS;
            for (int n = 0; n < count; n++) {
                Waypoint waypoint = waypoint(data, i, version);
                if (waypoint == null) {
                    return Result.of(Status.CORRUPT);
                }
                waypoints.add(waypoint);
                i += MissionLimits.INTS_PER_WAYPOINT;
            }
            List<PointOfInterest> points = new ArrayList<>();
            for (; i < data.length; i += MissionLimits.INTS_PER_POINT_OF_INTEREST) {
                points.add(new PointOfInterest(data[i], data[i + 1], data[i + 2], data[i + 3]));
            }
            Mission mission = new Mission((flags & FLAG_SMOOTH) != 0, (flags & FLAG_LOOP) != 0, ends[data[2]], data[3], obstacles[data[4]],
                    waypoints, points);
            return new Result(Status.OK, Optional.of(mission));
        } catch (IllegalArgumentException e) {
            return Result.of(Status.CORRUPT);
        }
    }

    private static Waypoint waypoint(int[] data, int i, int version) {
        int timing = data[i + 3];
        int look = data[i + 4];
        int actionYaw = data[i + 5];
        int every = look >>> EVERY_SHIFT;
        if (every != 0 && version < MissionLimits.EVERY_FORMAT_VERSION) {
            return null;
        }
        int headingIndex = look & 0xF;
        int poi = look >>> 4 & 0xF;
        Waypoint.Heading[] headings = Waypoint.Heading.values();
        Waypoint.Action[] actions = Waypoint.Action.values();
        Waypoint.Light[] lights = Waypoint.Light.values();
        Waypoint.Accessory[] accessories = Waypoint.Accessory.values();
        int action;
        int light = 0;
        int accessory = 0;
        boolean repeat = false;
        if (version == MissionLimits.FIRST_FORMAT_VERSION) {
            action = actionYaw >>> ACTION_SHIFT;
        } else {
            if ((actionYaw & TASK_BITS) != 0) {
                return null;
            }
            action = actionYaw >>> ACTION_SHIFT & NIBBLE;
            light = actionYaw >>> LIGHT_SHIFT & NIBBLE;
            accessory = actionYaw >>> ACCESSORY_SHIFT & NIBBLE;
            repeat = (actionYaw & REPEAT_BIT) != 0;
        }
        if (headingIndex >= headings.length || action >= actions.length || light >= lights.length || accessory >= accessories.length) {
            return null;
        }
        if (accessories[accessory] == Waypoint.Accessory.GRIPPER_RELEASE && version < MissionLimits.GRIPPER_FORMAT_VERSION) {
            return null;
        }
        return new Waypoint(data[i], data[i + 1], data[i + 2], timing >>> 16, timing & 0xFFFF, (look >>> 8 & 0xFF) - TILT_OFFSET,
                headings[headingIndex], poi, actions[action], actionYaw & 0xFFFF, lights[light], accessories[accessory], repeat, every);
    }
}
