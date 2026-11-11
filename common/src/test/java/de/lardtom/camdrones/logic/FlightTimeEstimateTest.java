package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightTimeEstimateTest {
    @Test
    void modelUsesTheClassHoverTime() {
        for (FlightClass c : FlightClass.values()) {
            double hover = FlightTimeEstimate.modelSeconds(c, 1.0, 0.0, 0.0);
            assertEquals((1.0 - FlightTimeEstimate.RESERVE) * c.hoverMinutes() * 60.0, hover, 1.0E-6);
            assertTrue(hover / 60.0 >= c.minHoverMinutes() * (1.0 - FlightTimeEstimate.RESERVE));
            assertTrue(FlightTimeEstimate.modelSeconds(c, 1.0, c.normalSpeed(), 0.0) < hover);
            assertTrue(FlightTimeEstimate.modelSeconds(c, 1.0, 0.0, c.climbSpeed()) < hover);
            assertEquals(0.0, FlightTimeEstimate.modelSeconds(c, 0.05, 0.0, 0.0), 0.0);
        }
    }

    @Test
    void measuredDischargeReplacesTheModel() {
        FlightTimeEstimate estimate = new FlightTimeEstimate(FlightClass.TOUR);
        double charge = 0.9;
        for (int t = 0; t <= 60; t++) {
            estimate.sample(t, charge);
            charge -= 0.001;
        }
        assertTrue(estimate.measured());
        assertEquals(0.001, estimate.rate(), 1.0E-6);
        assertEquals((charge + 0.001 - FlightTimeEstimate.RESERVE) / 0.001, estimate.remainingSeconds(charge + 0.001, 0, 0), 1.0);
        estimate.sample(61, 1.0);
        assertFalse(estimate.measured(), "Akkuwechsel nicht erkannt");
        assertEquals(FlightTimeEstimate.modelSeconds(FlightClass.TOUR, 1.0, 0, 0), estimate.remainingSeconds(1.0, 0, 0), 1.0E-9);
    }

    @Test
    void noGuessFromTheFirstSecondsOrFromTheGround() {
        FlightTimeEstimate estimate = new FlightTimeEstimate(FlightClass.TOUR);
        double charge = 0.95;
        double bench = 25.6 * 60.0;
        double time = 0.0;
        for (; time < 20.0; time += 0.05) {
            estimate.sample(time, charge, false);
            charge -= 0.00001;
        }
        assertEquals(0.0, estimate.observedSeconds(), 1.0E-9, "Zeit am Boden zählt nicht");
        for (double flown = 0.0; flown < 25.0; flown += 0.05) {
            estimate.sample(time, charge, true);
            charge -= 0.00004;
            time += 0.05;
            assertFalse(estimate.measured(), "schätzt schon nach " + flown + " s");
            assertEquals(FlightTimeEstimate.benchSeconds(bench, charge), estimate.remainingSeconds(charge, 0, 0, bench), 1.0E-9);
        }
        for (double flown = 0.0; flown < 10.0; flown += 0.05) {
            estimate.sample(time, charge, true);
            charge -= 0.00004;
            time += 0.05;
        }
        assertTrue(estimate.measured());
        assertEquals(0.0008, estimate.rate(), 0.00005, "Verbrauch nur aus der Flugzeit");
        double left = estimate.remainingSeconds(charge, 0, 0, bench);
        assertEquals((charge - FlightTimeEstimate.RESERVE) / 0.0008, left, left * 0.07);
    }

    @Test
    void benchValueScalesWithTheUsableCharge() {
        double full = 25.6 * 60.0;
        assertEquals(full, FlightTimeEstimate.benchSeconds(full, 1.0), 1.0E-9);
        assertEquals(full * 0.5, FlightTimeEstimate.benchSeconds(full, 0.55), 1.0E-9);
        assertEquals(0.0, FlightTimeEstimate.benchSeconds(full, 0.05), 0.0);
        assertTrue(Double.isNaN(FlightTimeEstimate.benchSeconds(Double.NaN, 0.8)));
        assertTrue(Double.isNaN(FlightTimeEstimate.benchSeconds(0.0, 0.8)));
        FlightTimeEstimate estimate = new FlightTimeEstimate(FlightClass.CINE);
        assertEquals(FlightTimeEstimate.modelSeconds(FlightClass.CINE, 0.8, 3.0, 0.0), estimate.remainingSeconds(0.8, 3.0, 0.0, Double.NaN), 1.0E-9,
                "ohne Werkbank-Wert das Klassenmodell");
    }

    @Test
    void tinyDrainNeverShowsAHugeTime() {
        FlightTimeEstimate estimate = new FlightTimeEstimate(FlightClass.TOUR);
        double charge = 0.99;
        for (double time = 0.0; time < 60.0; time += 0.05) {
            estimate.sample(time, charge, true);
            charge -= 0.000001;
        }
        assertFalse(estimate.measured(), "weniger als 1 % verbraucht: noch keine Schätzung aus der Messung");
    }

    @Test
    void missionPreviewSumsLengthTimeAndCharge() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 30, -40), Waypoint.at(30, 30, -40).withWait(10.0)));
        FlightTimeEstimate.Preview preview = FlightTimeEstimate.mission(FlightClass.TOUR, mission, 0, 30, 0);
        assertEquals(70.0, preview.length(), 1.0E-9);
        assertTrue(preview.seconds() > 70.0 / mission.baseSpeed() + 10.0 && preview.seconds() < 70.0 / mission.baseSpeed() + 20.0,
                "Dauer " + preview.seconds());
        assertTrue(preview.charge() > 0.0 && preview.feasible(1.0));
        FlightTimeEstimate.Preview home = FlightTimeEstimate.mission(FlightClass.TOUR, mission.withEnd(Mission.End.START), 0, 30, 0);
        assertEquals(120.0, home.length(), 1.0E-9);
        assertTrue(home.charge() > preview.charge());
        FlightTimeEstimate.Preview climb = FlightTimeEstimate.mission(FlightClass.TOUR,
                Mission.of(List.of(Waypoint.at(0, 90, 0))), 0, 30, 0);
        double vertical = Math.min(Mission.empty().baseSpeed(), FlightClass.TOUR.climbSpeed());
        assertEquals((60.0 - WaypointFollower.ARRIVE_RADIUS) / vertical + vertical / WaypointFollower.BRAKE_ACCEL, climb.seconds(), 1.0E-9);
        assertEquals(0.0, FlightTimeEstimate.mission(FlightClass.TOUR, Mission.empty(), 0, 0, 0).length(), 0.0);
    }

    @Test
    void previewMatchesAFlownMissionRoughly() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 30, -60), Waypoint.at(60, 35, -60), Waypoint.at(60, 30, 0)))
                .withBaseSpeed(8.0);
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        double[] doneAt = {Double.NaN};
        drone.run(logic::update, 120.0, d -> {
            if (Double.isNaN(doneAt[0]) && logic.missionState() == NavLogic.MissionState.DONE) {
                doneAt[0] = d.time;
            }
        });
        double predicted = FlightTimeEstimate.mission(FlightClass.TOUR, mission, 0, 30, 0).seconds();
        assertEquals(doneAt[0], predicted, doneAt[0] * 0.25);
    }

    @Test
    void smoothTurnsAreEstimatedLikeTheyAreFlown() {
        for (FlightClass flightClass : FlightClass.values()) {
            for (double speed : new double[]{5.0, 8.0, 12.0}) {
                Mission mission = WaypointFollowerTest.curves().withBaseSpeed(speed);
                NavLogic logic = new NavLogic(flightClass);
                logic.startMission(mission, 0);
                PointMass drone = new PointMass(flightClass).at(0, 30, 0, 90);
                double[] doneAt = {Double.NaN};
                drone.run(logic::update, 120.0, d -> {
                    if (Double.isNaN(doneAt[0]) && logic.missionState() == NavLogic.MissionState.DONE) {
                        doneAt[0] = d.time;
                    }
                });
                double predicted = FlightTimeEstimate.mission(flightClass, mission, 0, 30, 0).seconds();
                assertEquals(doneAt[0], predicted, doneAt[0] * 0.1, flightClass.key() + " mit " + speed + " m/s");
            }
        }
    }

    static Mission hairpin() {
        return Mission.of(List.of(Waypoint.at(40, 30, 0), Waypoint.at(0, 30, 8), Waypoint.at(0, 30, 48))).withSmooth(true).withBaseSpeed(8.0);
    }

    static Mission thereAndBack() {
        return Mission.of(List.of(Waypoint.at(40, 30, 0), Waypoint.at(0, 30, 0))).withSmooth(true).withBaseSpeed(8.0);
    }

    static Mission climbingHairpin() {
        return Mission.of(List.of(Waypoint.at(40, 30, 0), Waypoint.at(30, 38, 2), Waypoint.at(30, 38, 42))).withSmooth(true).withBaseSpeed(8.0);
    }

    static Mission steepHairpin() {
        return Mission.of(List.of(Waypoint.at(40, 30, 0), Waypoint.at(30, 50, 2), Waypoint.at(30, 50, 42))).withSmooth(true).withBaseSpeed(8.0);
    }

    @Test
    void smoothHairpinsAreEstimatedWithTheStopTheFollowerMakes() {
        assertEstimatedLikeFlown(List.of(hairpin(), thereAndBack()));
    }

    @Test
    void smoothHairpinsWithAHeightChangeAreEstimatedWithTheStopTheFollowerMakes() {
        assertEstimatedLikeFlown(List.of(climbingHairpin(), steepHairpin()));
    }

    static Mission shortLegHairpin() {
        return Mission.of(List.of(Waypoint.at(40, 30, 0), Waypoint.at(37, 30, 0), Waypoint.at(37, 30, 40))).withBaseSpeed(8.0);
    }

    @Test
    void sharpMissionsWithShortLegsAreEstimatedLikeTheyAreFlown() {
        assertEstimatedLikeFlown(List.of(shortLegHairpin(), hairpin().withSmooth(false), climbingHairpin().withSmooth(false),
                steepHairpin().withSmooth(false)));
    }

    static Mission square(double side) {
        return Mission.of(List.of(Waypoint.at(side, 30, 0), Waypoint.at(side, 30, side), Waypoint.at(0, 30, side), Waypoint.at(0, 30, 0)));
    }

    static Mission zigzag(double step) {
        return Mission.of(List.of(Waypoint.at(step, 30, 0), Waypoint.at(step, 30, step), Waypoint.at(2 * step, 30, step),
                Waypoint.at(2 * step, 30, 2 * step), Waypoint.at(3 * step, 30, 2 * step)));
    }

    static Mission photoGrid() {
        return Mission.of(List.of(Waypoint.at(5, 30, 0).withWait(2.0), Waypoint.at(5, 30, 5).withWait(2.0), Waypoint.at(0, 30, 5).withWait(2.0),
                Waypoint.at(0, 30, 0).withWait(2.0)));
    }

    @Test
    void sharpMissionsWithManyShortLegsAreEstimatedWithTheHaltAtEveryPoint() {
        assertEstimatedLikeFlown(List.of(square(3), square(5), square(8), zigzag(5)), 0.05);
    }

    @Test
    void oneMetreLegsGetAShorterHaltBecauseTheDroneIsSlow() {
        assertEstimatedLikeFlown(List.of(square(1), zigzag(1)), 0.1);
    }

    @Test
    void photoGridsWithWaitsAreEstimatedWithTheHaltAtEveryPoint() {
        assertEstimatedLikeFlown(List.of(photoGrid(), photoGrid().withSmooth(true)), 0.05);
    }

    @Test
    void sharpLegsLimitedByClimbingAreEstimatedWithTheSpeedTheyAreFlown() {
        assertEstimatedLikeFlown(List.of(Mission.of(List.of(Waypoint.at(3, 40, 0), Waypoint.at(6, 30, 0)))), 0.1);
    }

    static final double[] ALL_SPEEDS = {1.0, 2.0, 3.0, 5.0, 8.0, 12.0};

    static Mission steepUpAndDown() {
        return Mission.of(List.of(Waypoint.at(2, 35, 2), Waypoint.at(4, 30, 4)));
    }

    static Mission steeperUpAndDown() {
        return Mission.of(List.of(Waypoint.at(3, 40, 0), Waypoint.at(6, 30, 0)));
    }

    @Test
    void steepSharpLegsSettleOnlyAsLongAsTheFollowerNeeds() {
        assertEstimatedLikeFlown(List.of(steepUpAndDown(), steeperUpAndDown()), 0.1, ALL_SPEEDS);
    }

    @Test
    void steepSmoothLegsStopWithTheArrivalRadiusOnTheGround() {
        assertEstimatedLikeFlown(List.of(steepUpAndDown().withSmooth(true), steeperUpAndDown().withSmooth(true)), 0.1, ALL_SPEEDS);
    }

    @Test
    void sharpLegsLimitedByClimbingBrakeFromTheHorizontalSpeedTheyReach() {
        assertEstimatedLikeFlown(List.of(steepUpAndDown(), steeperUpAndDown(), Mission.of(List.of(Waypoint.at(10, 50, 0), Waypoint.at(20, 30, 0))),
                Mission.of(List.of(Waypoint.at(20, 50, 0), Waypoint.at(40, 30, 0)))), 0.1, 8.0, 12.0);
    }

    @Test
    void reviewerScenariosStayWithinTenPercentInEveryClassAndSpeed() {
        List<Mission> sharp = List.of(square(3), square(5), square(8), zigzag(5), steepUpAndDown(), steeperUpAndDown());
        assertEstimatedLikeFlown(sharp, 0.1, ALL_SPEEDS);
        assertEstimatedLikeFlown(sharp.stream().map(m -> m.withSmooth(true)).toList(), 0.1, ALL_SPEEDS);
    }

    static Mission waypoints(double... xyz) {
        List<Waypoint> points = new ArrayList<>();
        for (int i = 0; i < xyz.length; i += 3) {
            points.add(Waypoint.at(xyz[i], xyz[i + 1], xyz[i + 2]));
        }
        return Mission.of(points).withSmooth(true);
    }

    @Test
    void shortSmoothLastLegsAfterACurveAreNotEstimatedTooLong() {
        assertEstimatedLikeFlown(List.of(waypoints(2, 33, 0, 2, 36, 2), waypoints(5, 20, 0, 5, 17, 2)), 0.1, 3.0, 5.0, 8.0, 12.0);
    }

    @Test
    void smoothLegsAfterAShortFirstLegStartWithTheSpeedTheDroneReaches() {
        assertEstimatedLikeFlown(List.of(waypoints(2, 30, 0, 12, 40, 0), waypoints(2, 33, 0, 12, 43, 0), waypoints(2, 30, 0, 22, 30, 0),
                waypoints(5, 30, 0, 15, 30, 0), waypoints(5, 30, 0, 15, 30, 2, 25, 30, 0, 35, 30, 2)), 0.1, ALL_SPEEDS);
    }

    private static void assertEstimatedLikeFlown(List<Mission> missions) {
        assertEstimatedLikeFlown(missions, 0.1);
    }

    private static void assertEstimatedLikeFlown(List<Mission> missions, double tolerance) {
        assertEstimatedLikeFlown(missions, tolerance, 5.0, 8.0, 12.0);
    }

    private static void assertEstimatedLikeFlown(List<Mission> missions, double tolerance, double... speeds) {
        for (Mission base : missions) {
            for (FlightClass flightClass : FlightClass.values()) {
                for (double speed : speeds) {
                    Mission mission = base.withBaseSpeed(speed);
                    NavLogic logic = new NavLogic(flightClass);
                    logic.startMission(mission, 0);
                    PointMass drone = new PointMass(flightClass).at(0, 30, 0, 90);
                    double[] doneAt = {Double.NaN};
                    drone.run(logic::update, 120.0, d -> {
                        if (Double.isNaN(doneAt[0]) && logic.missionState() == NavLogic.MissionState.DONE) {
                            doneAt[0] = d.time;
                        }
                    });
                    assertFalse(Double.isNaN(doneAt[0]), "Mission nicht fertig geflogen");
                    double predicted = FlightTimeEstimate.mission(flightClass, mission, 0, 30, 0).seconds();
                    assertEquals(doneAt[0], predicted, doneAt[0] * tolerance,
                            (mission.smooth() ? "weich, " : "eckig, ") + mission.size() + " Punkte, Punkt 2 bei " + mission.waypoint(1).x() + "/"
                                    + mission.waypoint(1).y() + "/" + mission.waypoint(1).z() + ", " + flightClass.key() + " mit " + speed + " m/s");
                }
            }
        }
    }
}
