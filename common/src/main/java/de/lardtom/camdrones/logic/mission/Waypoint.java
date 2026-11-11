package de.lardtom.camdrones.logic.mission;

public record Waypoint(int xDm, int yDm, int zDm, int speedDmS, int waitDs, int tiltDeg, Heading heading, int poi,
                       Action action, int yawDeg, Light light, Accessory accessory, boolean repeat, int everyTicks) {
    public enum Heading {
        COURSE,
        FIXED,
        POI,
        FREE
    }

    public enum Action {
        NONE,
        PHOTO,
        RECORD_START,
        RECORD_STOP
    }

    public enum Light {
        NONE,
        ON,
        OFF,
        STATIC,
        BLINK,
        ARMED,
        LOW_BATTERY,
        RAINBOW;

        public boolean mode() {
            return this.ordinal() >= STATIC.ordinal();
        }
    }

    public enum Repeat {
        ONCE,
        EACH,
        EVERY
    }

    public enum Accessory {
        NONE,
        ACTION_1,
        ACTION_2,
        ACTION_3,
        ACTION_4,
        END_REPEAT,
        GRIPPER_RELEASE;

        public static final int ACTIONS = 4;

        public int number() {
            return this.ordinal() >= ACTION_1.ordinal() && this.ordinal() <= ACTION_4.ordinal() ? this.ordinal() : 0;
        }

        public static Accessory of(int number) {
            return number >= 1 && number <= ACTIONS ? values()[number] : NONE;
        }
    }

    public Waypoint {
        MissionLimits.requirePosition(xDm, yDm, zDm);
        MissionLimits.require(speedDmS == 0 || speedDmS >= MissionLimits.MIN_SPEED_DM_S && speedDmS <= MissionLimits.MAX_SPEED_DM_S,
                "Tempo ungültig: " + speedDmS);
        MissionLimits.require(waitDs >= 0 && waitDs <= MissionLimits.MAX_WAIT_DS, "Wartezeit ungültig: " + waitDs);
        MissionLimits.require(tiltDeg >= MissionLimits.MIN_TILT && tiltDeg <= MissionLimits.MAX_TILT, "Neigung ungültig: " + tiltDeg);
        MissionLimits.require(heading != null, "Blickmodus fehlt");
        MissionLimits.require(poi >= 0 && poi < MissionLimits.MAX_POINTS_OF_INTEREST, "Point of Interest ungültig: " + poi);
        MissionLimits.require(action != null, "Aktion fehlt");
        MissionLimits.require(yawDeg >= 0 && yawDeg < 360, "Richtung ungültig: " + yawDeg);
        MissionLimits.require(light != null, "Licht fehlt");
        MissionLimits.require(accessory != null, "Zubehör-Aktion fehlt");
        MissionLimits.require(!repeat || accessory.number() > 0, "Wiederholen nur mit Zubehör-Aktion 1 bis 4");
        MissionLimits.require(everyTicks == 0 || everyTicks >= MissionLimits.MIN_EVERY_TICKS && everyTicks <= MissionLimits.MAX_EVERY_TICKS,
                "Takt ungültig: " + everyTicks);
        MissionLimits.require(everyTicks == 0 || accessory.number() > 0 && !repeat, "Takt nur mit Zubehör-Aktion 1 bis 4 und ohne „ab hier“");
    }

    public Waypoint(int xDm, int yDm, int zDm, int speedDmS, int waitDs, int tiltDeg, Heading heading, int poi, Action action, int yawDeg,
                    Light light, Accessory accessory, boolean repeat) {
        this(xDm, yDm, zDm, speedDmS, waitDs, tiltDeg, heading, poi, action, yawDeg, light, accessory, repeat, 0);
    }

    public Waypoint(int xDm, int yDm, int zDm, int speedDmS, int waitDs, int tiltDeg, Heading heading, int poi, Action action, int yawDeg) {
        this(xDm, yDm, zDm, speedDmS, waitDs, tiltDeg, heading, poi, action, yawDeg, Light.NONE, Accessory.NONE, false);
    }

    public double everySeconds() {
        return this.everyTicks / (double) MissionLimits.TICKS_PER_SECOND;
    }

    public Repeat repeatMode() {
        return this.everyTicks > 0 ? Repeat.EVERY : this.repeat ? Repeat.EACH : Repeat.ONCE;
    }

    public static Waypoint at(double x, double y, double z) {
        return new Waypoint(MissionLimits.toDecimetres(x), MissionLimits.toDecimetres(y), MissionLimits.toDecimetres(z), 0, 0, 0,
                Heading.COURSE, 0, Action.NONE, 0);
    }

    public double x() {
        return this.xDm / 10.0;
    }

    public double y() {
        return this.yDm / 10.0;
    }

    public double z() {
        return this.zDm / 10.0;
    }

    public double speed() {
        return this.speedDmS / 10.0;
    }

    public double waitSeconds() {
        return this.waitDs / 10.0;
    }

    public boolean hasTasks() {
        return this.light != Light.NONE || this.accessory != Accessory.NONE;
    }

    public Waypoint withPosition(double x, double y, double z) {
        return this.with(MissionLimits.toDecimetres(x), MissionLimits.toDecimetres(y), MissionLimits.toDecimetres(z), this.speedDmS, this.waitDs,
                this.tiltDeg, this.heading, this.poi, this.action, this.yawDeg);
    }

    public Waypoint withHeight(int yDecimetres) {
        return this.with(this.xDm, yDecimetres, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, this.heading, this.poi, this.action, this.yawDeg);
    }

    public Waypoint withSpeed(double metresPerSecond) {
        int dm = metresPerSecond <= 0.0 ? 0 : (int) Math.round(metresPerSecond * 10.0);
        return this.withSpeedDm(dm);
    }

    public Waypoint withSpeedDm(int decimetresPerSecond) {
        return this.with(this.xDm, this.yDm, this.zDm, decimetresPerSecond, this.waitDs, this.tiltDeg, this.heading, this.poi, this.action,
                this.yawDeg);
    }

    public Waypoint withWait(double seconds) {
        return this.withWaitDs((int) Math.round(seconds * 10.0));
    }

    public Waypoint withWaitDs(int deciseconds) {
        return this.with(this.xDm, this.yDm, this.zDm, this.speedDmS, deciseconds, this.tiltDeg, this.heading, this.poi, this.action, this.yawDeg);
    }

    public Waypoint withTilt(int degrees) {
        return this.with(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, degrees, this.heading, this.poi, this.action, this.yawDeg);
    }

    public Waypoint withHeading(Heading mode, double compassDegrees) {
        int yaw = Math.floorMod((int) Math.round(compassDegrees), 360);
        return this.with(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, mode, this.poi, this.action, yaw);
    }

    public Waypoint withLook(Heading mode, int pointOfInterest) {
        return this.with(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, mode, pointOfInterest, this.action, this.yawDeg);
    }

    public Waypoint withYaw(int degrees) {
        return this.with(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, this.heading, this.poi, this.action,
                Math.floorMod(degrees, 360));
    }

    public Waypoint facing(int pointOfInterest) {
        return this.withLook(Heading.POI, pointOfInterest);
    }

    public Waypoint withAction(Action value) {
        return this.with(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, this.heading, this.poi, value, this.yawDeg);
    }

    public Waypoint withLight(Light value) {
        return new Waypoint(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, this.heading, this.poi, this.action, this.yawDeg,
                value, this.accessory, this.repeat, this.everyTicks);
    }

    public Waypoint withAccessory(Accessory value, boolean repeating) {
        return this.withAccessory(value, repeating ? Repeat.EACH : Repeat.ONCE, 0);
    }

    public Waypoint withAccessory(Accessory value, Repeat mode, int ticks) {
        Repeat chosen = value != null && value.number() > 0 && mode != null ? mode : Repeat.ONCE;
        int every = chosen == Repeat.EVERY ? MissionLimits.clampEvery(ticks > 0 ? ticks : MissionLimits.DEFAULT_EVERY_TICKS) : 0;
        return new Waypoint(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, this.heading, this.poi, this.action, this.yawDeg,
                this.light, value, chosen == Repeat.EACH, every);
    }

    public Waypoint withRepeat(Repeat mode) {
        return this.withAccessory(this.accessory, mode, this.everyTicks);
    }

    public Waypoint withEveryTicks(int ticks) {
        return this.everyTicks > 0 ? this.withAccessory(this.accessory, Repeat.EVERY, MissionLimits.clampEvery(ticks)) : this;
    }

    public Waypoint withoutTasks() {
        return new Waypoint(this.xDm, this.yDm, this.zDm, this.speedDmS, this.waitDs, this.tiltDeg, this.heading, this.poi, this.action, this.yawDeg);
    }

    public double distanceTo(double x, double y, double z) {
        double dx = x - this.x();
        double dy = y - this.y();
        double dz = z - this.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Waypoint with(int x, int y, int z, int speed, int wait, int tilt, Heading look, int pointOfInterest, Action shot, int yaw) {
        return new Waypoint(x, y, z, speed, wait, tilt, look, pointOfInterest, shot, yaw, this.light, this.accessory, this.repeat, this.everyTicks);
    }
}
