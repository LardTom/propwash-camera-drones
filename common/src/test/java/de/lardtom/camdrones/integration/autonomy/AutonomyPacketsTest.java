package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.client.AutonomyCommands;
import de.lardtom.camdrones.client.planner.MissionCommands;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.FollowConsent;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.MissionProgress;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.net.StatusMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyPacketsTest {
    private static final UUID SERIAL = UUID.fromString("0f0e0d0c-0b0a-0908-0706-050403020100");

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "alles gelesen");
        return decoded;
    }

    @Test
    void missionCommandRoundTripsAndClampsTheIndex() {
        for (MissionCommand.Command command : MissionCommand.Command.values()) {
            MissionCommand.Request request = new MissionCommand.Request(SERIAL, command, 7);
            assertEquals(request, roundTrip(MissionCommand.Request.CODEC, request));
        }
        assertEquals(MissionCommand.AUTO_INDEX, new MissionCommand.Request(SERIAL, MissionCommand.Command.START, -9).startIndex());
        assertEquals(MissionLimits.MAX_WAYPOINTS - 1, new MissionCommand.Request(SERIAL, MissionCommand.Command.START, 999).startIndex());
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 77);
        assertEquals(MissionCommand.Command.PAUSE, MissionCommand.COMMAND_CODEC.decode(buf), "Unbekanntes wird harmlos");
    }

    @Test
    void progressSmartModeConsentAndStatusRoundTrip() {
        MissionProgress.Report report = new MissionProgress.Report(SERIAL, NavLogic.MissionState.PAUSED, 12, 3);
        assertEquals(report, roundTrip(MissionProgress.Report.CODEC, report));
        SmartState smart = new SmartState(SmartState.Function.FOLLOW, Optional.of(SERIAL), 1.5, 70.25, -3.0, 12.0, 4.0, false, 270.0,
                FollowController.Style.WATCH);
        SmartMode.Request mode = new SmartMode.Request(SERIAL, smart);
        assertEquals(mode, roundTrip(SmartMode.Request.CODEC, mode));
        SmartMode.Request none = new SmartMode.Request(SERIAL, SmartState.NONE);
        assertEquals(none, roundTrip(SmartMode.Request.CODEC, none));
        FollowConsent.Request ask = new FollowConsent.Request(9, "lardtom", "Tour Süd", false, 60);
        assertEquals(ask, roundTrip(FollowConsent.Request.CODEC, ask));
        FollowConsent.Request frame = new FollowConsent.Request(10, "lardtom", "camdrones.frame_short.tour_fold_frame", true, 60);
        assertEquals(frame, roundTrip(FollowConsent.Request.CODEC, frame));
        FollowConsent.Reply reply = new FollowConsent.Reply(9, true);
        assertEquals(reply, roundTrip(FollowConsent.Reply.CODEC, reply));
        for (AutonomyStatus status : AutonomyStatus.values()) {
            StatusMessage.Payload payload = new StatusMessage.Payload(status, "Kino", "3");
            assertEquals(payload, roundTrip(StatusMessage.Payload.CODEC, payload));
        }
    }

    @Test
    void autonomyPacketsAreRegisteredOnceInTheOwnNamespace() {
        CamNetwork.define();
        Set<CustomPacketPayload.Type<?>> server = new HashSet<>();
        for (CamNetwork.ServerBound<?> entry : CamNetwork.serverBound()) {
            server.add(entry.type());
            assertEquals(CamDrones.MOD_ID, entry.type().id().getNamespace());
        }
        Set<CustomPacketPayload.Type<?>> client = new HashSet<>();
        for (CamNetwork.ClientBound<?> entry : CamNetwork.clientBound()) {
            client.add(entry.type());
        }
        assertTrue(server.contains(MissionCommand.TYPE));
        assertTrue(server.contains(MissionProgress.TYPE));
        assertTrue(server.contains(SmartMode.TYPE));
        assertTrue(server.contains(FollowConsent.REPLY));
        assertTrue(client.contains(FollowConsent.REQUEST));
        assertTrue(client.contains(StatusMessage.TYPE));
        assertEquals("camdrones:mission_command", MissionCommand.TYPE.id().toString());
        assertEquals("camdrones:mission_progress", MissionProgress.TYPE.id().toString());
        assertEquals("camdrones:smart_mode", SmartMode.TYPE.id().toString());
        assertEquals("camdrones:follow_consent", FollowConsent.REQUEST.id().toString());
        assertEquals("camdrones:status", StatusMessage.TYPE.id().toString());
    }

    @Test
    void clientStatusDispatchAndTabletCommandsWithoutConnection() {
        List<StatusMessage.Payload> seen = new ArrayList<>();
        CamNetwork.onClient(StatusMessage.TYPE, seen::add);
        CamNetwork.dispatchClient(new StatusMessage.Payload(AutonomyStatus.NO_BUDGET, "Tour", ""));
        assertEquals(AutonomyStatus.NO_BUDGET, seen.getFirst().status());
        AutonomyCommands commands = new AutonomyCommands();
        assertFalse(commands.available());
        assertFalse(commands.send(SERIAL, MissionCommands.Command.START), "ohne Loader nichts senden");
        assertEquals(MissionCommand.Command.START, AutonomyCommands.map(MissionCommands.Command.START));
        assertEquals(MissionCommand.Command.PAUSE, AutonomyCommands.map(MissionCommands.Command.PAUSE));
        assertEquals(MissionCommand.Command.STOP, AutonomyCommands.map(MissionCommands.Command.STOP));
    }
}
