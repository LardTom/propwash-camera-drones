package de.lardtom.camdrones.logic.mission;

public final class MissionLimits {
    public static final int FORMAT_VERSION = 4;
    public static final int GRIPPER_FORMAT_VERSION = 4;
    public static final int EVERY_FORMAT_VERSION = 3;
    public static final int TASK_FORMAT_VERSION = 2;
    public static final int FIRST_FORMAT_VERSION = 1;
    public static final int TICKS_PER_SECOND = 20;
    public static final int MIN_EVERY_TICKS = 1;
    public static final int MAX_EVERY_TICKS = 1200;
    public static final int DEFAULT_EVERY_TICKS = 20;
    public static final int MAX_WAYPOINTS = 50;
    public static final int MAX_POINTS_OF_INTEREST = 4;
    public static final int MAX_SAVED_MISSIONS = 8;
    public static final int HEADER_INTS = 6;
    public static final int INTS_PER_WAYPOINT = 6;
    public static final int INTS_PER_POINT_OF_INTEREST = 4;
    public static final int DRONE_DATA_BYTES = 2048;
    public static final int NBT_OVERHEAD_BYTES = 64;
    public static final int MAX_HORIZONTAL_DM = 300_000_000;
    public static final int MIN_Y_DM = -20_480;
    public static final int MAX_Y_DM = 40_960;
    public static final int MIN_SPEED_DM_S = 1;
    public static final int MAX_SPEED_DM_S = 200;
    public static final int MAX_WAIT_DS = 3000;
    public static final int MIN_TILT = -90;
    public static final int MAX_TILT = 60;
    public static final int MAX_RADIUS_DM = 2000;

    private MissionLimits() {
    }

    public static int clampEvery(int ticks) {
        return Math.clamp(ticks, MIN_EVERY_TICKS, MAX_EVERY_TICKS);
    }

    public static int packedInts(int waypoints, int pointsOfInterest) {
        return HEADER_INTS + waypoints * INTS_PER_WAYPOINT + pointsOfInterest * INTS_PER_POINT_OF_INTEREST;
    }

    public static int packedBytes(int waypoints, int pointsOfInterest) {
        return 4 * packedInts(waypoints, pointsOfInterest);
    }

    public static boolean fits(int waypoints, int pointsOfInterest) {
        return waypoints >= 0 && waypoints <= MAX_WAYPOINTS && pointsOfInterest >= 0 && pointsOfInterest <= MAX_POINTS_OF_INTEREST
                && packedBytes(waypoints, pointsOfInterest) + NBT_OVERHEAD_BYTES <= DRONE_DATA_BYTES;
    }

    public static int toDecimetres(double metres) {
        if (!Double.isFinite(metres)) {
            throw new IllegalArgumentException("Koordinate ungültig: " + metres);
        }
        long dm = Math.round(metres * 10.0);
        if (dm < Integer.MIN_VALUE || dm > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Koordinate außerhalb der Welt: " + metres);
        }
        return (int) dm;
    }

    static void requirePosition(int xDm, int yDm, int zDm) {
        require(Math.abs((long) xDm) <= MAX_HORIZONTAL_DM, "x außerhalb der Welt: " + xDm);
        require(Math.abs((long) zDm) <= MAX_HORIZONTAL_DM, "z außerhalb der Welt: " + zDm);
        require(yDm >= MIN_Y_DM && yDm <= MAX_Y_DM, "y außerhalb der Welt: " + yDm);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
