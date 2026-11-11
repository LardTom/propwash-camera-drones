package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.hud.CamOsdState;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class MissionTimerSteps {
    public static final int CINE_NUMBER = 386;
    public static final int TOUR_NUMBER = 387;
    public static final String CINE = "hud: Missions-Timer zählt im Sekundentakt bis 0:00 am letzten Wegpunkt – Kino, eckig, Ende zum Start";
    public static final String TOUR = "hud: Missions-Timer zählt im Sekundentakt bis 0:00 am letzten Wegpunkt – Tour, weich, Ende schweben";
    public static final int CINE_ELEVEN_NUMBER = 388;
    public static final int COMPACT_NUMBER = 389;
    public static final int CINE_SMOOTH_NUMBER = 390;
    public static final int TOUR_SHARP_NUMBER = 391;
    public static final String CINE_ELEVEN = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Kino, eckig, 11 Punkte, 2–15 m/s, Ende zum Start";
    public static final String COMPACT = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Compact, weich, 10 Punkte, Ende landen";
    public static final String CINE_SMOOTH = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Kino, weich, 8–15 m/s, Ende schweben";
    public static final String TOUR_SHARP = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Tour, eckig, Warten und Sinkflug, Ende landen";
    public static final int COMPACT_CLIMB_NUMBER = 392;
    public static final int TOUR_CLIMB_NUMBER = 393;
    public static final int TOUR_CLIMB_SHARP_NUMBER = 394;
    public static final int COMPACT_CLIMB_SHARP_NUMBER = 395;
    public static final String COMPACT_CLIMB = "hud: Missions-Timer im Aufstieg höchstens 1,8 s, danach 1 s neben der Ankunft – Compact, weich, Aufstieg 25 m vom Boden, Ende landen";
    public static final String TOUR_CLIMB = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Tour, weich, Aufstieg 24 m vom Boden, Ende landen";
    public static final String TOUR_CLIMB_SHARP = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Tour, eckig, Aufstieg 30 m vom Boden, Ende schweben";
    public static final String COMPACT_CLIMB_SHARP = "hud: Missions-Timer höchstens 1 s neben der Ankunft – Compact, eckig, Aufstieg 30 m vom Boden, Ende schweben";
    public static final int COMPACT_BORDER_NUMBER = 396;
    public static final String COMPACT_BORDER = "hud: Missions-Timer im Grenzbereich des Aufstiegs – Compact, eckig, Aufstieg 20 m vom Boden, Ende schweben";
    public static final double TOLERANCE = 1.0;
    public static final double LONG_CLIMB_TOLERANCE = 1.8;
    public static final int AFTER_CLIMB_TICKS = 30;
    public static final double MOVED = 0.25;
    public static final double MAX_STEP = 1.0;
    public static final double REAL_TIME_SHARE = 0.025;
    public static final int SETTLE_TICKS = 10;
    public static final long NANOS_PER_TICK = 50_000_000L;
    public static final String REPORT_FILE = "camdrones-mission-timer-%d.csv";
    private static final int STEP_TICKS = 20 * 300;
    private static final int FLY_TICKS = 20 * 200;
    private static final int AFTER_TICKS = 20 * 3;
    private static final String MISSION = "timer.mission";
    private static final String SAMPLES = "timer.samples";
    private static final String LABELS = "timer.labels";
    private static final String START = "timer.start";
    private static final String LABEL_ARRIVAL = "timer.labelArrival";
    private static final String LABEL_TRACE = "timer.labelTrace";
    private static final String CLIMB_END = "timer.climbEnd";

    private record Sample(long tick, long nanos, int index, double x, double y, double z, double seconds, String text) {
    }

    private record Label(long tick, String text, int index, double distance) {
    }

    private enum Climb {
        NONE(TOLERANCE), LONG(LONG_CLIMB_TOLERANCE);

        private final double tolerance;

        Climb(double tolerance) {
            this.tolerance = tolerance;
        }
    }

    private MissionTimerSteps() {
    }

    public static void register() {
        step(CINE_NUMBER, CINE, CamPresets.CINE, -100, 60, MissionTimerSteps::cine);
        step(TOUR_NUMBER, TOUR, CamPresets.TOUR, 40, 60, MissionTimerSteps::tour);
        step(CINE_ELEVEN_NUMBER, CINE_ELEVEN, CamPresets.CINE, -60, -60, MissionTimerSteps::cineEleven);
        step(COMPACT_NUMBER, COMPACT, CamPresets.COMPACT, 60, -60, MissionTimerSteps::compact);
        step(CINE_SMOOTH_NUMBER, CINE_SMOOTH, CamPresets.CINE, -20, 110, MissionTimerSteps::cineSmooth);
        step(TOUR_SHARP_NUMBER, TOUR_SHARP, CamPresets.TOUR, 100, -10, MissionTimerSteps::tourSharp);
        step(COMPACT_CLIMB_NUMBER, COMPACT_CLIMB, CamPresets.COMPACT, 70, -70, drone -> climb(drone, 25.0, true), Climb.LONG);
        step(TOUR_CLIMB_NUMBER, TOUR_CLIMB, CamPresets.TOUR, 70, 70, drone -> climb(drone, 24.0, true));
        step(TOUR_CLIMB_SHARP_NUMBER, TOUR_CLIMB_SHARP, CamPresets.TOUR, -70, -70, drone -> climb(drone, 30.0, false));
        step(COMPACT_CLIMB_SHARP_NUMBER, COMPACT_CLIMB_SHARP, CamPresets.COMPACT, -90, 0, drone -> climb(drone, 30.0, false));
        step(COMPACT_BORDER_NUMBER, COMPACT_BORDER, CamPresets.COMPACT, 110, 40, drone -> climb(drone, 20.0, false), Climb.LONG);
    }

    static Mission climb(Entity drone, double height, boolean smooth) {
        double[][] points = {{0, height, 4, 6.0, 0}, {20, height - 2, 10, 10.0, 0}, {25, height - 8, -10, 7.0, 0}, {5, height - 5, -20, 9.0, 0}};
        Mission mission = Mission.of(waypoints(drone, points)).withSmooth(smooth).withEnd(smooth ? Mission.End.LAND : Mission.End.HOVER);
        return mission.withBaseSpeed(7.0);
    }

    static Mission cineEleven(Entity drone) {
        double[][] points = {{0, 10, 10, 0, 0}, {0, 12, 60, 15.0, 0}, {20, 12, 70, 0, 0}, {20, 18, 70, 4.0, 0}, {60, 18, 40, 12.0, 3.0},
                {60, 9, 0, 6.0, 0}, {30, 9, -10, 0, 0}, {30, 9, -14, 2.0, 0}, {0, 14, -40, 10.0, 0}, {-20, 14, -40, 5.0, 0}, {-20, 10, 0, 15.0, 0}};
        return Mission.of(waypoints(drone, points)).withEnd(Mission.End.START).withBaseSpeed(8.0);
    }

    static Mission compact(Entity drone) {
        double[][] points = new double[10][];
        for (int i = 0; i < points.length; i++) {
            points[i] = new double[]{12.0 * (i + 1) - 60.0, 8 + (i % 4) * 4, (i % 2 == 0 ? -15 : 15) + (i % 3) * 5, 4.0 + (i % 3) * 3.0, 0};
        }
        return Mission.of(waypoints(drone, points)).withSmooth(true).withEnd(Mission.End.LAND).withBaseSpeed(7.0);
    }

    static Mission cineSmooth(Entity drone) {
        double[][] points = {{0, 12, -30, 8.0, 0}, {40, 15, -60, 15.0, 0}, {80, 12, -30, 12.0, 0}, {60, 20, 10, 10.0, 0}, {20, 14, 0, 0, 0},
                {-20, 10, -20, 15.0, 0}, {-40, 16, -60, 9.0, 0}, {0, 12, -90, 13.0, 0}};
        return Mission.of(waypoints(drone, points)).withSmooth(true).withEnd(Mission.End.HOVER).withBaseSpeed(10.0);
    }

    static Mission tourSharp(Entity drone) {
        double[][] points = {{0, 6, 8, 5.0, 0}, {30, 8, 8, 12.0, 0}, {30, 8, -20, 9.0, 2.5}, {10, 14, -35, 6.0, 0}, {10, 4, -35, 4.0, 0},
                {-25, 7, -10, 11.0, 0}, {-25, 7, 15, 3.0, 0}, {-5, 12, 30, 10.0, 0}, {15, 9, 30, 7.0, 0}};
        return Mission.of(waypoints(drone, points)).withEnd(Mission.End.LAND).withBaseSpeed(7.0);
    }

    static List<Waypoint> waypoints(Entity drone, double[][] points) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (double[] p : points) {
            Waypoint w = Waypoint.at(drone.getX() + p[0], drone.getY() + p[1], drone.getZ() + p[2]);
            if (p[3] > 0) {
                w = w.withSpeed(p[3]);
            }
            if (p[4] > 0) {
                w = w.withWait(p[4]);
            }
            waypoints.add(w);
        }
        return waypoints;
    }

    static Mission cine(Entity drone) {
        double[][] points = {{0, 8, 6, 3.0, 0}, {0, 8, 46, 10.0, 0}, {5, 11, 46, 2.0, 2.0}, {30, 7, 30, 6.0, 0}, {30, 7, 27, 0, 0},
                {55, 10, 0, 12.0, 0}};
        List<Waypoint> waypoints = new ArrayList<>();
        for (double[] p : points) {
            Waypoint w = Waypoint.at(drone.getX() + p[0], drone.getY() + p[1], drone.getZ() + p[2]);
            if (p[3] > 0) {
                w = w.withSpeed(p[3]);
            }
            if (p[4] > 0) {
                w = w.withWait(p[4]);
            }
            waypoints.add(w);
        }
        return Mission.of(waypoints).withEnd(Mission.End.START).withBaseSpeed(5.0);
    }

    static Mission tour(Entity drone) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double x = 7.0 * (i + 1) + (i % 3) * 4.0;
            double y = 8.0 + (i % 3) * 2.0;
            double z = i % 2 == 0 ? -10.0 : 10.0;
            waypoints.add(Waypoint.at(drone.getX() + x, drone.getY() + y, drone.getZ() + z).withSpeed(3.0 + (i % 4) * 2.5));
        }
        return Mission.of(waypoints).withSmooth(true).withEnd(Mission.End.HOVER).withBaseSpeed(6.0);
    }

    private static void step(int number, String name, CamPresets.Entry entry, int dx, int dz, Function<Entity, Mission> plan) {
        step(number, name, entry, dx, dz, plan, Climb.NONE);
    }

    private static void step(int number, String name, CamPresets.Entry entry, int dx, int dz, Function<Entity, Mission> plan, Climb climb) {
        SelfTest.clientStep(number, name, STEP_TICKS, Script.create()
                .server("Wind schwach", (server, state) -> Arena.calmWind(server))
                .append(CamFlights.groundDrone(entry, dx, dz, 0.0F))
                .server("Mission mit verschiedenen Längen und Tempi", (server, state) -> {
                    Mission mission = plan.apply(Pilot.serverDrone(server, state));
                    state.put(MISSION, mission);
                    CamFlights.mission(server, state, mission);
                })
                .sleep(10)
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Meldung Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Timer in jedem Tick gegen die Ankunft am letzten Wegpunkt", (ctx, ticks) -> sample(ctx, ticks, number, climb)));
    }

    @SuppressWarnings("unchecked")
    private static StepResult sample(SelfTest.StepContext ctx, int ticks, int number, Climb climb) {
        Map<String, Object> state = ctx.state();
        Mission mission = (Mission) state.get(MISSION);
        Optional<Entity> found = CamFlights.clientDrone(state);
        if (found.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Entity drone = found.get();
        List<Sample> samples = (List<Sample>) state.computeIfAbsent(SAMPLES, key -> new ArrayList<Sample>());
        List<Label> labels = (List<Label>) state.computeIfAbsent(LABELS, key -> new ArrayList<Label>());
        CamOsdState osd = CamOsdSource.state(drone, telemetry(drone));
        long tick = drone.level().getGameTime();
        if (osd.missionActive()) {
            state.putIfAbsent(START, tick);
            samples.add(new Sample(tick, System.nanoTime(), osd.missionIndex(), drone.getX(), drone.getY(), drone.getZ(), osd.missionSecondsLeft(),
                    CamOsd.mission(osd).text()));
        }
        ctx.onServer(server -> {
            Entity serverDrone = Pilot.serverDrone(server, state);
            String label = AutonomyServer.navStatus(serverDrone).map(NavStatus::label).orElse("");
            long now = serverDrone.level().getGameTime();
            MissionStatus status = AutonomyServer.missionStatus(serverDrone);
            int index = status.state() == NavLogic.MissionState.RUNNING || status.state() == NavLogic.MissionState.DONE ? status.index() : -1;
            double distance = mission.waypoint(mission.size() - 1).distanceTo(serverDrone.getX(), serverDrone.getY(), serverDrone.getZ());
            synchronized (labels) {
                labels.add(new Label(now, label, index, distance));
            }
        });
        long arrival;
        synchronized (labels) {
            arrival = arrival(labels, mission);
            state.put(LABEL_ARRIVAL, labelArrival(labels, mission));
            state.put(CLIMB_END, climbEnd(labels));
        }
        state.put(LABEL_TRACE, lastLabel(labels));
        if (arrival < 0) {
            if (ticks <= FLY_TICKS) {
                return StepResult.pending();
            }
            int highest = -1;
            double closest = Double.POSITIVE_INFINITY;
            synchronized (labels) {
                for (Label label : labels) {
                    highest = Math.max(highest, label.index());
                    closest = Math.min(closest, label.distance());
                }
            }
            write(number, mission, samples, -1L, (long) state.getOrDefault(START, tick), lastLabel(labels));
            return StepResult.fail(String.format(Locale.ROOT, "Drohne kommt nicht am letzten Wegpunkt an: höchster Index am Server %d, "
                    + "am nächsten %.2f m, %d Proben mit Timer, Server %s", highest, closest, samples.size(), lastLabel(labels)));
        }
        boolean ended = !osd.missionActive();
        if (tick < arrival + AFTER_TICKS && !ended) {
            return StepResult.pending();
        }
        return evaluate(ctx, number, mission, samples, arrival, (long) state.getOrDefault(START, tick), (long) state.get(LABEL_ARRIVAL),
                (long) state.get(CLIMB_END), climb);
    }

    private static long climbEnd(List<Label> labels) {
        for (Label label : labels) {
            if (!label.text().isEmpty()) {
                return label.tick();
            }
        }
        return -1L;
    }

    private static long onward(List<Sample> samples, long arrival) {
        if (samples.isEmpty()) {
            return arrival;
        }
        Sample first = samples.getFirst();
        for (Sample s : samples) {
            if (s.index() > 0 || Math.hypot(s.x() - first.x(), s.z() - first.z()) > MOVED) {
                return s.tick();
            }
        }
        return arrival;
    }

    private static long arrival(List<Label> labels, Mission mission) {
        for (Label label : labels) {
            if (label.index() == mission.size() - 1 && label.distance() <= WaypointFollower.ARRIVE_RADIUS) {
                return label.tick();
            }
        }
        return -1L;
    }

    private static long labelArrival(List<Label> labels, Mission mission) {
        String flying = "WP " + mission.size() + "/" + mission.size();
        boolean seen = false;
        for (Label label : labels) {
            if (label.text().equals(flying)) {
                seen = true;
            } else if (seen) {
                return label.tick();
            }
        }
        return -1L;
    }

    private static String lastLabel(List<Label> labels) {
        List<String> seen = new ArrayList<>();
        synchronized (labels) {
            for (Label label : labels) {
                if (seen.isEmpty() || !seen.getLast().equals(label.text())) {
                    seen.add(label.text());
                }
            }
        }
        return seen.isEmpty() ? "ohne Meldung" : String.join(" → ", seen);
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, int number, Mission mission, List<Sample> samples, long arrival, long start,
                                       long labelArrival, long climbEnd, Climb climb) {
        List<String> problems = new ArrayList<>();
        boolean border = climb != Climb.NONE;
        double climbTolerance = climb.tolerance;
        long onward = onward(samples, arrival);
        long end = climb == Climb.LONG ? onward : climbEnd;
        long settled = end < 0 ? arrival : end + AFTER_CLIMB_TICKS;
        double worst = 0.0;
        double worstAt = Double.NaN;
        double worstAfter = 0.0;
        double worstAfterAt = Double.NaN;
        double worstRealAfter = 0.0;
        double biggestStep = 0.0;
        double first = Double.NaN;
        double firstActual = Double.NaN;
        Sample previous = null;
        long arrivalNanos = -1L;
        Sample lastBefore = null;
        for (Sample s : samples) {
            if (s.tick() >= arrival && arrivalNanos < 0) {
                arrivalNanos = s.nanos();
            } else if (s.tick() < arrival) {
                lastBefore = s;
            }
        }
        if (arrivalNanos < 0 && lastBefore != null) {
            arrivalNanos = lastBefore.nanos() + (arrival - lastBefore.tick()) * NANOS_PER_TICK;
        }
        double worstReal = 0.0;
        for (Sample s : samples) {
            double actual = (arrival - s.tick()) / 20.0;
            if (!Double.isFinite(s.seconds())) {
                problems.add("kein Timer bei " + Flight.format((s.tick() - start) / 20.0) + " s");
                break;
            }
            if (Double.isNaN(first)) {
                first = s.seconds();
                firstActual = actual;
            }
            if (s.tick() < arrival) {
                double deviation = s.seconds() - actual;
                if (Math.abs(deviation) > Math.abs(worst)) {
                    worst = deviation;
                    worstAt = (s.tick() - start) / 20.0;
                }
                if (s.tick() >= settled && Math.abs(deviation) > Math.abs(worstAfter)) {
                    worstAfter = deviation;
                    worstAfterAt = (s.tick() - start) / 20.0;
                }
                if (arrivalNanos > 0) {
                    double real = Math.abs(s.seconds() - (arrivalNanos - s.nanos()) / 1.0E9);
                    worstReal = Math.max(worstReal, real);
                    if (s.tick() >= settled) {
                        worstRealAfter = Math.max(worstRealAfter, real);
                    }
                }
            } else if ((!s.text().endsWith(" 0:00") || s.tick() >= arrival + SETTLE_TICKS && s.seconds() != 0.0)
                    && problems.stream().noneMatch(p -> p.startsWith("nach"))) {
                problems.add(String.format(Locale.ROOT, "nach der Ankunft %s statt 0:00 (%.1f s danach)", s.text(), (s.tick() - arrival) / 20.0));
            }
            if (previous != null && Double.isFinite(previous.seconds())) {
                double drop = previous.seconds() - s.seconds();
                if (drop < -1.0E-9 && problems.stream().noneMatch(p -> p.startsWith("steigt"))) {
                    problems.add(String.format(Locale.ROOT, "steigt bei %.2f s von %.2f auf %.2f s", (s.tick() - start) / 20.0, previous.seconds(),
                            s.seconds()));
                }
                biggestStep = Math.max(biggestStep, drop);
            }
            previous = s;
        }
        if (border) {
            if (Math.abs(worst) > climbTolerance) {
                problems.add(String.format(Locale.ROOT, "weicht bei %.1f s um %+.2f s von der echten Restzeit ab (Grenze %.1f s bis 1,5 s nach dem Aufstieg)", worstAt,
                        worst, climbTolerance));
            }
            if (Math.abs(worstAfter) > TOLERANCE) {
                problems.add(String.format(Locale.ROOT, "weicht ab 1,5 s nach dem Aufstieg bei %.1f s um %+.2f s von der echten Restzeit ab (Grenze %.1f s)",
                        worstAfterAt, worstAfter, TOLERANCE));
            }
        } else if (Math.abs(worst) > TOLERANCE) {
            problems.add(String.format(Locale.ROOT, "weicht bei %.1f s um %+.2f s von der echten Restzeit ab (Grenze %.1f s)", worstAt, worst, TOLERANCE));
        }
        if (biggestStep > MAX_STEP) {
            problems.add(String.format(Locale.ROOT, "springt um %.2f s (Grenze %.1f s)", biggestStep, MAX_STEP));
        }
        long flown = arrival - start;
        double realSeconds = arrivalNanos > 0 && !samples.isEmpty() ? (arrivalNanos - samples.getFirst().nanos()) / 1.0E9 : Double.NaN;
        boolean realTime = Double.isFinite(realSeconds) && Math.abs(realSeconds - flown / 20.0) <= REAL_TIME_SHARE * realSeconds;
        if (realTime && border && worstRealAfter > TOLERANCE) {
            problems.add(String.format(Locale.ROOT, "weicht ab 1,5 s nach dem Aufstieg um %.2f s von der Uhr ab (Grenze %.1f s)", worstRealAfter, TOLERANCE));
        } else if (realTime && !border && worstReal > TOLERANCE) {
            problems.add(String.format(Locale.ROOT, "weicht um %.2f s von der Uhr ab (Grenze %.1f s)", worstReal, TOLERANCE));
        }
        write(number, mission, samples, arrival, start, String.valueOf(ctx.state().get(LABEL_TRACE)));
        String summary = String.format(Locale.ROOT, "Timer zu Beginn %.1f s bei %.1f s bis Punkt %d, größte Abweichung %+.2f s, größter Schritt %.2f s, "
                        + "%s (%.1f s Uhr für %.1f s Spielzeit, Abweichung zur Uhr %.2f s), vor der Ankunft „%s“, Folger am Server %s, %d Proben",
                first, firstActual, mission.size(), worst, biggestStep, realTime ? "Spielzeit = Echtzeit" : "Server langsamer als Echtzeit, Vergleich mit Spielzeit",
                realSeconds, flown / 20.0, worstReal, lastBefore == null ? "" : lastBefore.text(), labelArrival < 0 ? "ohne Meldung" : (labelArrival - arrival) + " Ticks danach", samples.size());
        if (climb == Climb.LONG) {
            summary += String.format(Locale.ROOT, ", Aufstieg endet am Server bei %.1f s, Weiterflug am Client ab %.1f s, ab 1,5 s danach größte Abweichung %+.2f s, "
                    + "zur Uhr %.2f s", (climbEnd - start) / 20.0, (onward - start) / 20.0, worstAfter, worstRealAfter);
        }
        CamDrones.LOG.info("[Kamera-Selbsttest] Missions-Timer {}: {}", number, summary);
        return problems.isEmpty() ? StepResult.pass(summary) : StepResult.fail(String.join("; ", problems) + " – " + summary);
    }

    private static void write(int number, Mission mission, List<Sample> samples, long arrival, long start, String labels) {
        List<String> lines = new ArrayList<>();
        lines.add("# arrival=" + arrival + " start=" + start + " size=" + mission.size());
        lines.add("# server " + labels);
        for (int i = 0; i < mission.size(); i++) {
            Waypoint w = mission.waypoint(i);
            lines.add(String.format(Locale.ROOT, "# wp %d %.2f %.2f %.2f %.1f %.1f", i, w.x(), w.y(), w.z(), mission.speedTo(i), w.waitSeconds()));
        }
        lines.add("tick,nanos,index,x,y,z,seconds,text");
        for (Sample s : samples) {
            lines.add(String.format(Locale.ROOT, "%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%s", s.tick(), s.nanos(), s.index(), s.x(), s.y(), s.z(), s.seconds(),
                    s.text()));
        }
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(String.format(Locale.ROOT, REPORT_FILE, number)),
                    String.join(System.lineSeparator(), lines) + System.lineSeparator());
        } catch (IOException e) {
            CamDrones.LOG.warn("[Kamera-Selbsttest] Timer-Aufzeichnung {} nicht geschrieben", number, e);
        }
    }

    private static FlightTelemetry telemetry(Entity drone) {
        return new FlightTelemetry(true, "ANGLE", Optional.empty(), drone.getX(), drone.getY(), drone.getZ(), drone.getDeltaMovement().horizontalDistance() * 20.0,
                drone.getDeltaMovement().y * 20.0, drone.getYRot(), 0.0, 0.0, 0.5, 16.0, 4.0, 0.9, false, 0.0, 0.0, 0.0, true, 14, 1.0, 1.0, 0.0, 0.0, 0.0,
                0.0, 0.5);
    }
}
