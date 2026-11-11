package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.NormalMode;
import de.lardtom.camdrones.logic.nav.SportMode;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickResponseTest {
    static final double PULL_BACK = 0.1;
    static final double HOVER_SECONDS = 3.0;
    static final double PUSH_SECONDS = 5.0;
    static final double AFTER_SECONDS = 6.0;
    static final double SINKING_COST = 8.0;
    static final double SLIGHT_SINK_COST = 3.0;

    private static BiConsumer<NavInput, NavOutput> piloted(NavLogic logic, HorizontalControl control) {
        return (in, out) -> {
            logic.update(in, out);
            control.apply(in, out);
        };
    }

    private record Release(double furthest, double pulled, double speedAtRelease) {
    }

    private static Release release(FlightClass c, NavLogic.StickMode mode, double windX, double pitch) {
        NavLogic logic = new NavLogic(c);
        logic.stickMode(mode);
        HorizontalControl control = new HorizontalControl();
        BiConsumer<NavInput, NavOutput> chain = piloted(logic, control);
        PointMass drone = new PointMass(c).at(0, 30, 0, 90).wind(windX, 0.0, 0.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(chain, HOVER_SECONDS);
        drone.sticks(0, pitch, 0, 0.5);
        drone.run(chain, PUSH_SECONDS);
        double startX = drone.x;
        double startZ = drone.z;
        double speed = drone.speed();
        double axisX = drone.vx / Math.max(1.0E-9, speed);
        double axisZ = drone.vz / Math.max(1.0E-9, speed);
        drone.sticks(0, 0, 0, 0.5);
        double[] furthest = {0.0};
        double[] pulled = {0.0};
        drone.run(chain, AFTER_SECONDS, d -> {
            double along = (d.x - startX) * axisX + (d.z - startZ) * axisZ;
            furthest[0] = Math.max(furthest[0], along);
            pulled[0] = Math.max(pulled[0], furthest[0] - along);
        });
        return new Release(furthest[0], pulled[0], speed);
    }

    @Test
    void releasingTheSticksBrakesWithoutPullingBack() {
        for (FlightClass c : FlightClass.values()) {
            for (NavLogic.StickMode mode : new NavLogic.StickMode[] {NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE}) {
                for (double pitch : new double[] {1.0, 0.6}) {
                    Release r = release(c, mode, 0.0, pitch);
                    String name = c + " " + mode + " Knüppel " + pitch + " (" + r.speedAtRelease() + " m/s, Bremsweg " + r.furthest() + " m)";
                    assertTrue(r.speedAtRelease() > 0.5 * pitch * (mode == NavLogic.StickMode.CINE ? c.cineSpeed() : c.normalSpeed()), name);
                    assertTrue(r.pulled() <= PULL_BACK, name + ": zieht nach dem Loslassen " + r.pulled() + " m zurück");
                }
            }
        }
    }

    @Test
    void inLightWindTheReleasedDroneStillDoesNotPullBack() {
        for (FlightClass c : FlightClass.values()) {
            for (double wind : new double[] {HorizontalControlTest.LIGHT_WIND, -HorizontalControlTest.LIGHT_WIND}) {
                Release r = release(c, NavLogic.StickMode.NORMAL, wind, 1.0);
                assertTrue(r.pulled() <= 2.0 * PULL_BACK, c + " Wind " + wind + ": zieht " + r.pulled() + " m zurück");
            }
        }
    }

    @Test
    void aSmallStickLeavesTheHoverHoldLikeInNormal() {
        for (FlightClass c : FlightClass.values()) {
            for (double stick : new double[] {0.08, 0.15, -0.2}) {
                NavLogic logic = new NavLogic(c);
                HorizontalControl control = new HorizontalControl();
                BiConsumer<NavInput, NavOutput> chain = piloted(logic, control);
                PointMass drone = new PointMass(c).at(0, 30, 0, 90);
                drone.sticks(0, 0, 0, 0.5);
                drone.run(chain, 1.0);
                logic.hold();
                drone.run(chain, 2.0);
                assertEquals(NavLogic.Function.HOLD, logic.function());
                drone.sticks(0, stick, 0, 0.5);
                drone.run(chain, 4.0);
                String name = c + " Knüppel " + stick + " aus dem Halten";
                assertEquals(NavLogic.Function.NONE, logic.function(), name + ": hält weiter die Position");
                double expected = Math.abs((Math.abs(stick) - NormalMode.STICK_DEADBAND) / (1.0 - NormalMode.STICK_DEADBAND)) * c.normalSpeed();
                assertEquals(expected, drone.speed(), 0.15 * expected + 0.05, name);
            }
            NavLogic logic = new NavLogic(c);
            logic.hold();
            NavOutput out = new NavOutput();
            logic.update(new NavInput().time(0.0).position(0, 30, 0).attitude(90, 0, 0).heightAboveGround(30).pilot(true)
                    .sticks(0.04, -0.04, 0.04, 0.5), out);
            assertEquals(NavLogic.Function.HOLD, logic.function(), c + ": Knüppel im Totband lässt das Halten stehen");
        }
    }

    @Test
    void theStickIsLinearBehindTheDeadbandWithoutExpo() {
        for (FlightClass c : FlightClass.values()) {
            NormalMode mode = new NormalMode(c);
            for (double stick : new double[] {0.0, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0}) {
                NavOutput out = new NavOutput();
                mode.update(new NavInput().time(1.0).position(0, 30, 0).attitude(0, 0, 0).heightAboveGround(30).pilot(true)
                        .sticks(0.0, stick, 0.0, 0.5), out);
                double expected = stick <= NormalMode.STICK_DEADBAND ? 0.0
                        : (stick - NormalMode.STICK_DEADBAND) / (1.0 - NormalMode.STICK_DEADBAND) * c.normalSpeed();
                double commanded = out.horizontal() == NavOutput.Horizontal.VELOCITY ? Math.hypot(out.velocityX(), out.velocityZ()) : 0.0;
                assertEquals(expected, commanded, 1.0E-9, c + " Knüppel " + stick);
            }
        }
    }

    private static double sportPitch(SportMode sport, double time, double y, double vy) {
        NavOutput out = new NavOutput();
        sport.update(new NavInput().time(time).position(0, y, 0).velocity(15.0, vy, 0).attitude(90, 0, 0).heightAboveGround(y).pilot(true)
                .sticks(0.0, 1.0, 0.0, 0.5), out);
        return out.stickPitch();
    }

    @Test
    void sportLeansLessWhileItSinksAndFullyAgainOnceTheHeightHolds() {
        for (FlightClass c : FlightClass.values()) {
            SportMode sport = new SportMode(c);
            double full = sportPitch(sport, 0.0, 30.0, 0.0);
            assertEquals(SportMode.MAX_ANGLE / NavInput.DEFAULT_ANGLE_LIMIT, full, 1.0E-9, c + " volle Neigung");
            double time = 0.0;
            double y = 30.0;
            double pitch = full;
            for (int i = 0; i < 100; i++) {
                time += 0.01;
                y -= 0.01;
                pitch = sportPitch(sport, time, y, -1.0);
            }
            assertTrue(pitch * NavInput.DEFAULT_ANGLE_LIMIT <= SportMode.MAX_ANGLE - SINKING_COST,
                    c + ": sinkt 1 s mit 1 m/s und neigt weiter " + pitch * NavInput.DEFAULT_ANGLE_LIMIT + "°");
            assertTrue(pitch * NavInput.DEFAULT_ANGLE_LIMIT >= SportMode.MIN_ANGLE - 1.0E-6, c + ": nie unter " + SportMode.MIN_ANGLE + "°");
            for (int i = 0; i < 200; i++) {
                time += 0.01;
                pitch = sportPitch(sport, time, y, 0.0);
            }
            assertTrue(pitch < full - 1.0E-6, c + ": noch 1 m unter der gehaltenen Höhe und neigt schon wieder voll");
            y = 30.0;
            for (int i = 0; i < 600; i++) {
                time += 0.01;
                pitch = sportPitch(sport, time, y, 0.0);
            }
            assertEquals(full, pitch, 1.0E-9, c + ": hält die Höhe wieder und neigt wieder voll");
            for (int i = 0; i < 100; i++) {
                time += 0.01;
                pitch = sportPitch(sport, time, y - 0.005 * i, -0.25);
            }
            assertTrue(pitch <= full && (full - pitch) * NavInput.DEFAULT_ANGLE_LIMIT <= SLIGHT_SINK_COST,
                    c + ": leichtes Sinken mit 0,25 m/s kostet " + (full - pitch) * NavInput.DEFAULT_ANGLE_LIMIT + "° Neigung");
        }
    }

    @Test
    void fromSportToNormalAtSpeedItBrakesWithoutPullingBack() {
        for (FlightClass c : FlightClass.values()) {
            NavLogic logic = new NavLogic(c);
            HorizontalControl control = new HorizontalControl();
            BiConsumer<NavInput, NavOutput> chain = piloted(logic, control);
            PointMass drone = new PointMass(c).at(0, 30, 0, 90);
            drone.sticks(0, 0, 0, 0.5);
            drone.run(chain, HOVER_SECONDS);
            logic.stickMode(NavLogic.StickMode.SPORT);
            drone.sticks(0, 1.0, 0, 0.5);
            drone.run(chain, PUSH_SECONDS);
            drone.sticks(0, 0, 0, 0.5);
            logic.stickMode(NavLogic.StickMode.NORMAL);
            double startX = drone.x;
            double startZ = drone.z;
            double speed = drone.speed();
            double axisX = drone.vx / Math.max(1.0E-9, speed);
            double axisZ = drone.vz / Math.max(1.0E-9, speed);
            double[] furthest = {0.0};
            double[] pulled = {0.0};
            drone.run(chain, AFTER_SECONDS + 4.0, d -> {
                double along = (d.x - startX) * axisX + (d.z - startZ) * axisZ;
                furthest[0] = Math.max(furthest[0], along);
                pulled[0] = Math.max(pulled[0], furthest[0] - along);
            });
            assertTrue(speed > c.normalSpeed(), c + ": Sport nur " + speed + " m/s");
            assertTrue(pulled[0] <= PULL_BACK, c + " von S nach N bei " + speed + " m/s: zieht " + pulled[0] + " m zurück");
            assertTrue(drone.speed() < 0.1, c + ": steht nicht, " + drone.speed() + " m/s");
        }
    }
}
