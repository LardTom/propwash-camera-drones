package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NavAssistFollowStyleTest {
    private static final double DT = 0.01;

    static final class Context implements NavContext {
        FollowController.Style style = FollowController.Style.BEHIND;

        @Override
        public Optional<NavTarget> followTarget() {
            return Optional.of(new NavTarget(0.0, 64.0, 30.0, 0.0, 0.0, 0.0));
        }

        @Override
        public FollowController.Style followStyle() {
            return this.style;
        }
    }

    private static FollowController.Style followWithSwitch(Context context) {
        NavAssist assist = new NavAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), true, context);
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0);
        for (int i = 0; i < 5; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
        state.on(CamControl.FOLLOW_ME);
        for (int i = 0; i < 20; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
        assertEquals(NavLogic.Function.FOLLOW, assist.logic().function());
        return assertInstanceOf(FollowController.class, assist.logic().activeMode()).style();
    }

    @Test
    void switchFollowsInTheStyleChosenForThePilot() {
        for (FollowController.Style style : FollowController.Style.values()) {
            Context context = new Context();
            context.style = style;
            assertEquals(style, followWithSwitch(context), "Schalter Follow me mit gewähltem Stil " + style);
        }
    }

    @Test
    void withoutChoiceTheSwitchFollowsBehind() {
        assertEquals(FollowController.Style.BEHIND, followWithSwitch(new Context()));
    }
}
