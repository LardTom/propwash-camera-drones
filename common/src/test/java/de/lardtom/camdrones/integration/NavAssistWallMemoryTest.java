package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistWallMemoryTest {
    private static final double DT = 0.01;
    private static final double GROUND = 64.0;
    private static final double HEIGHT = 10.0;
    private static final double WALL = 3.0;

    static final class Probes implements NavContext {
        final List<double[]> calls = new ArrayList<>();
        DoubleUnaryOperator distanceAt = y -> Double.POSITIVE_INFINITY;

        public double probe(double x, double y, double z, double dirX, double dirZ, double range) {
            this.calls.add(new double[] {x, y, z, dirX, dirZ, range});
            return this.distanceAt.applyAsDouble(y);
        }
    }

    private static FakeFlight.State level() {
        FakeFlight.State state = new FakeFlight.State().at(0.0, GROUND + HEIGHT, 0.0);
        state.heightAboveGround = HEIGHT;
        state.estimatedY = state.y;
        state.estimatedVy = 0.0;
        state.obstacle(SensorDirection.DOWN, HEIGHT);
        return state;
    }

    private static void fly(NavAssist assist, FakeFlight.State state, double seconds) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
    }

    private static NavAssist seeWall(FakeFlight.State state, NavContext context) {
        NavAssist assist = new NavAssist(FlightClass.CINE, EnumSet.allOf(SensorDirection.class), true, context);
        state.obstacle(SensorDirection.FORWARD, WALL);
        fly(assist, state, 0.5);
        assertEquals(WALL, assist.input().obstacle(NavInput.Sensor.FORWARD), 1.0E-9);
        state.obstacle(SensorDirection.FORWARD, Double.POSITIVE_INFINITY);
        return assist;
    }

    private static double forward(NavAssist assist) {
        return assist.input().obstacle(NavInput.Sensor.FORWARD);
    }

    @Test
    void aWallThatDropsOutOfTheLevelRayBecauseTheDroneRoseStaysRemembered() {
        for (boolean probes : new boolean[] {false, true}) {
            FakeFlight.State state = level();
            Probes context = new Probes();
            context.distanceAt = y -> probes ? Double.POSITIVE_INFINITY : Double.NaN;
            NavAssist assist = seeWall(state, context);
            state.y += 1.1;
            state.heightAboveGround += 1.1;
            state.obstacle(SensorDirection.DOWN, state.heightAboveGround);
            state.estimatedY = state.y;
            fly(assist, state, 5.0);
            assertEquals(WALL, forward(assist), 1.0E-6, "mit Strahlen über und unter der Flughöhe " + probes);
        }
    }

    @Test
    void theRememberedWallEndsWhenThePilotEvadesOrMovesAway() {
        FakeFlight.State side = level();
        NavAssist sideways = seeWall(side, new Probes());
        side.y += 1.1;
        side.x += 2.0;
        fly(sideways, side, 0.2);
        assertTrue(Double.isInfinite(forward(sideways)), "seitlich ausgewichen: " + forward(sideways));

        FakeFlight.State turn = level();
        NavAssist turned = seeWall(turn, new Probes());
        turn.y += 1.1;
        turn.heading = 30.0;
        fly(turned, turn, 0.2);
        assertTrue(Double.isInfinite(forward(turned)), "weggedreht: " + forward(turned));

        FakeFlight.State away = level();
        NavAssist back = seeWall(away, new Probes());
        away.y += 1.1;
        away.z += 14.0;
        fly(back, away, 0.2);
        assertTrue(Double.isInfinite(forward(back)), "entfernt: " + forward(back));

        FakeFlight.State up = level();
        NavAssist climbed = seeWall(up, new Probes());
        up.stickThrottle = 1.0;
        up.stickPitch = 0.0;
        for (int i = 0; i < 20; i++) {
            up.y += 0.1;
            up.heightAboveGround += 0.1;
            up.estimatedY = up.y;
            up.obstacle(SensorDirection.DOWN, up.heightAboveGround);
            fly(climbed, up, 0.05);
        }
        assertTrue(Double.isInfinite(forward(climbed)), "mit Gas über die Wand gestiegen: " + forward(climbed));
    }

    @Test
    void atTheSameHeightAFreeViewStillClearsTheWall() {
        FakeFlight.State state = level();
        Probes context = new Probes();
        NavAssist assist = seeWall(state, context);
        fly(assist, state, 0.2);
        assertTrue(Double.isInfinite(forward(assist)), "frei auf Flughöhe: " + forward(assist));
    }

    @Test
    void theForwardSensorAlsoLooksABitAboveAndBelowTheFlightHeight() {
        FakeFlight.State state = level();
        state.noseDown = -30.0;
        state.obstacle(SensorDirection.DOWN, HEIGHT / Math.cos(Math.toRadians(30.0)));
        Probes context = new Probes();
        double flight = state.y;
        context.distanceAt = y -> y < flight - 0.2 ? 4.0 : Double.POSITIVE_INFINITY;
        NavAssist assist = new NavAssist(FlightClass.CINE, EnumSet.allOf(SensorDirection.class), true, context);
        fly(assist, state, 0.2);
        assertEquals(4.0, forward(assist), 1.0E-9, "Strahl unter der Flughöhe sieht die Wand");
        boolean below = false;
        boolean above = false;
        boolean levelForward = false;
        for (double[] call : context.calls) {
            below |= call[1] < flight - 0.2;
            above |= call[1] > flight + 0.2;
            levelForward |= Math.abs(call[3]) < 1.0E-6 && call[4] < -0.99;
        }
        assertTrue(below && above && levelForward, "Strahlen unter und über der Flughöhe, waagrecht nach vorn: " + below + " " + above + " "
                + levelForward);
        state.noseDown = 0.0;
        Probes none = new Probes();
        none.distanceAt = y -> Double.NaN;
        NavAssist without = new NavAssist(FlightClass.CINE, EnumSet.allOf(SensorDirection.class), true, none);
        fly(without, level(), 0.2);
        assertTrue(Double.isInfinite(forward(without)), "ohne Strahlen der Welt bleibt der Sensor am Rahmen: " + forward(without));
    }
}
