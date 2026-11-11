package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.integration.NavContext;
import de.lardtom.camdrones.logic.nav.FollowController;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerFollowStyleTest {
    @Test
    void styleChosenInThePlannerAppliesToTheSwitch() {
        AtomicReference<Optional<FollowController.Style>> chosen = new AtomicReference<>(Optional.empty());
        PlannerPilotContext context = new PlannerPilotContext(NavContext.NONE, chosen::get);
        assertEquals(FollowController.Style.BEHIND, context.followStyle(), "ohne Wahl im Planer hinterher");
        for (FollowController.Style style : FollowController.Style.values()) {
            chosen.set(Optional.of(style));
            assertEquals(style, context.followStyle());
        }
    }

    @Test
    void withoutChoiceTheInnerContextDecides() {
        NavContext inner = new NavContext() {
            @Override
            public FollowController.Style followStyle() {
                return FollowController.Style.WATCH;
            }
        };
        assertEquals(FollowController.Style.WATCH, new PlannerPilotContext(inner, Optional::empty).followStyle());
    }
}
