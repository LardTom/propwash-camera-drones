package de.lardtom.camdrones.award;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.VideoRecorded;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwardRulesTest {
    private static final UUID PILOT = UUID.randomUUID();
    private static final UUID OPERATOR = UUID.randomUUID();

    @Test
    void crewFlightNeedsAnotherPilotArmedInTheAir() {
        assertTrue(AwardRules.crewFlight(Optional.of(PILOT), OPERATOR, true, true));
        assertFalse(AwardRules.crewFlight(Optional.empty(), OPERATOR, true, true), "Mission ohne Pilot");
        assertFalse(AwardRules.crewFlight(Optional.of(OPERATOR), OPERATOR, true, true), "Operator fliegt selbst");
        assertFalse(AwardRules.crewFlight(Optional.of(PILOT), OPERATOR, false, true), "nicht scharf");
        assertFalse(AwardRules.crewFlight(Optional.of(PILOT), OPERATOR, true, false), "am Boden");
        assertFalse(AwardRules.crewFlight(Optional.of(PILOT), null, true, true), "ohne Operator");
    }

    @Test
    void missionCountsOnlyWhenARunningMissionEnds() {
        assertTrue(AwardRules.missionCompleted(NavLogic.MissionState.RUNNING, NavLogic.MissionState.DONE));
        assertTrue(AwardRules.missionCompleted(NavLogic.MissionState.PAUSED, NavLogic.MissionState.DONE));
        assertFalse(AwardRules.missionCompleted(NavLogic.MissionState.DONE, NavLogic.MissionState.DONE));
        assertFalse(AwardRules.missionCompleted(NavLogic.MissionState.IDLE, NavLogic.MissionState.DONE));
        assertFalse(AwardRules.missionCompleted(NavLogic.MissionState.RUNNING, NavLogic.MissionState.ABORTED));
        assertFalse(AwardRules.missionCompleted(NavLogic.MissionState.RUNNING, NavLogic.MissionState.PAUSED));
    }

    @Test
    void videoCountsWhenARecordingWithACameraDroneEnds() {
        AwardRules.Take take = new AwardRules.Take();
        assertEquals(OptionalInt.empty(), take.update(false, AwardRules.NO_DRONE));
        assertEquals(OptionalInt.empty(), take.update(true, 42));
        assertEquals(OptionalInt.empty(), take.update(true, AwardRules.NO_DRONE));
        assertEquals(OptionalInt.of(42), take.update(false, AwardRules.NO_DRONE));
        assertEquals(OptionalInt.empty(), take.update(false, AwardRules.NO_DRONE), "nur einmal je Aufnahme");
    }

    @Test
    void recordingWithoutCameraDroneDoesNotCount() {
        AwardRules.Take take = new AwardRules.Take();
        for (int i = 0; i < 5; i++) {
            take.update(true, AwardRules.NO_DRONE);
        }
        assertEquals(OptionalInt.empty(), take.update(false, AwardRules.NO_DRONE));
        take.update(true, 7);
        take.reset();
        assertEquals(OptionalInt.empty(), take.update(false, AwardRules.NO_DRONE), "Weltwechsel verwirft die Aufnahme");
    }

    @Test
    void namesMatchTheAdvancementFiles() {
        assertEquals(CamAdvancementFilesTest.AWARDED, CamAwards.AWARDED);
        assertEquals(CamAdvancementFilesTest.ALL, CamAwards.ALL);
        assertEquals(List.of("root", "crew_flight", "video", "mission"), CamAwards.ALL);
    }

    @Test
    void videoReportIsAServerBoundPacketOfTheAddon() {
        ByteBuf buf = Unpooled.buffer();
        VideoRecorded.Report.CODEC.encode(buf, new VideoRecorded.Report(4711));
        assertEquals(new VideoRecorded.Report(4711), VideoRecorded.Report.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
        assertEquals(CamDrones.MOD_ID, VideoRecorded.TYPE.id().getNamespace());
        assertTrue(CamNetwork.serverBound().stream().anyMatch(entry -> entry.type() == VideoRecorded.TYPE));
        assertTrue(CamNetwork.clientBound().stream().noneMatch(entry -> entry.type() == VideoRecorded.TYPE));
    }
}
