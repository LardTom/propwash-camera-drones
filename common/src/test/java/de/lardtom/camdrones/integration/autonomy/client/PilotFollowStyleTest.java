package de.lardtom.camdrones.integration.autonomy.client;

import de.lardtom.camdrones.integration.NavContext;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.SmartMode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PilotFollowStyleTest {
    private static final UUID SERIAL = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void pilotContextPassesTheChosenStyleOn() {
        for (FollowController.Style style : FollowController.Style.values()) {
            NavContext inner = new NavContext() {
                @Override
                public FollowController.Style followStyle() {
                    return style;
                }
            };
            assertEquals(style, new AutonomyPilotContext(inner).followStyle());
        }
        assertEquals(FollowController.Style.BEHIND, new AutonomyPilotContext(NavContext.NONE).followStyle());
    }

    @Test
    void tabletRequestCarriesTargetAndStyleOverTheWire() {
        for (FollowController.Style style : FollowController.Style.values()) {
            SmartMode.Request request = AutonomyCommands.followRequest(SERIAL, PLAYER, style);
            ByteBuf buf = Unpooled.buffer();
            SmartMode.Request.CODEC.encode(buf, request);
            SmartMode.Request back = SmartMode.Request.CODEC.decode(buf);
            assertEquals(SERIAL, back.serial());
            assertEquals(SmartState.Function.FOLLOW, back.smart().function());
            assertEquals(Optional.of(PLAYER), back.smart().target());
            assertEquals(style, back.smart().followStyle());
        }
    }

    @Test
    void withoutConnectionNothingIsSent() {
        AutonomyCommands commands = new AutonomyCommands();
        assertFalse(commands.followAvailable());
        assertFalse(commands.follow(SERIAL, PLAYER, FollowController.Style.PARALLEL), "ohne Loader nichts senden");
        assertFalse(commands.follow(null, PLAYER, FollowController.Style.PARALLEL));
        assertFalse(commands.follow(SERIAL, null, FollowController.Style.PARALLEL));
        assertFalse(commands.follow(SERIAL, PLAYER, null));
    }
}
