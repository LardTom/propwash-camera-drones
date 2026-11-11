package de.lardtom.camdrones.net;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.planner.PlannerCodecs;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerMissions;
import de.lardtom.camdrones.planner.PlannerSettings;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.UploadCheck;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerPacketsTest {
    private static Mission fullMission() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS; i++) {
            list.add(new Waypoint(-29_999_000 + i * 37, -640 + i * 13, 29_999_000 - i * 41, 200, 3000, -90 + i, Waypoint.Heading.POI, i % 4,
                    Waypoint.Action.values()[i % 4], i * 7 % 360));
        }
        List<PointOfInterest> pois = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            pois.add(PointOfInterest.at(i * 50.5, 64, -i * 20.25, 199.9));
        }
        return new Mission(true, true, Mission.End.LAND, 155, ObstacleGuard.Mode.CLIMB_OVER, list, pois);
    }

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "alles gelesen");
        return decoded;
    }

    @Test
    void uploadRequestRoundTripsAFullMission() {
        int[] packed = MissionCodec.encode(fullMission());
        UUID serial = UUID.randomUUID();
        MissionUpload.Request request = roundTrip(MissionUpload.Request.CODEC, new MissionUpload.Request(42, serial, packed));
        assertEquals(42, request.requestId());
        assertEquals(serial, request.serial());
        assertArrayEquals(packed, request.mission());
        assertEquals(fullMission(), MissionCodec.decode(request.mission()).mission().orElseThrow());
        ByteBuf buf = Unpooled.buffer();
        MissionUpload.Request.CODEC.encode(buf, request);
        assertTrue(buf.readableBytes() < 2048, "Paketgröße " + buf.readableBytes());
    }

    @Test
    void exitSettingRequestAndReplyRoundTrip() {
        UUID serial = UUID.randomUUID();
        for (ExitSetting setting : ExitSetting.values()) {
            ExitSettingUpload.Request request = roundTrip(ExitSettingUpload.Request.CODEC, ExitSettingUpload.Request.of(7, serial, setting));
            assertEquals(7, request.requestId());
            assertEquals(serial, request.serial());
            assertEquals(setting, ExitSetting.byId(request.exit()).orElseThrow());
        }
        for (ExitSetting.Result result : ExitSetting.Result.values()) {
            ExitSettingUpload.Reply reply = roundTrip(ExitSettingUpload.Reply.CODEC, new ExitSettingUpload.Reply(3, result, -1));
            assertEquals(result, reply.result());
            assertEquals(-1, reply.exit());
        }
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 1);
        ByteBufCodecs.VAR_INT.encode(buf, 99);
        ByteBufCodecs.VAR_INT.encode(buf, 0);
        assertEquals(ExitSetting.Result.INVALID, ExitSettingUpload.Reply.CODEC.decode(buf).result());
    }

    @Test
    void uploadRequestRejectsOversizedArrays() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 1);
        buf.writeLong(1L);
        buf.writeLong(2L);
        ByteBufCodecs.VAR_INT.encode(buf, PlannerEntry.MAX_INTS + 1);
        assertThrows(DecoderException.class, () -> MissionUpload.Request.CODEC.decode(buf));
        assertThrows(IllegalArgumentException.class, () -> PlannerCodecs.PACKED.encode(Unpooled.buffer(), new int[PlannerEntry.MAX_INTS + 1]));
        ByteBuf shortBuf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(shortBuf, 10);
        shortBuf.writeInt(1);
        assertThrows(DecoderException.class, () -> PlannerCodecs.PACKED.decode(shortBuf));
    }

    @Test
    void replyRoundTripsEveryResult() {
        for (UploadCheck.Result result : UploadCheck.Result.values()) {
            MissionUpload.Reply reply = roundTrip(MissionUpload.Reply.CODEC, new MissionUpload.Reply(7, result, -1));
            assertEquals(result, reply.result());
            assertEquals(-1, reply.waypoint());
        }
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 1);
        ByteBufCodecs.VAR_INT.encode(buf, 999);
        ByteBufCodecs.VAR_INT.encode(buf, 3);
        assertEquals(UploadCheck.Result.INVALID, MissionUpload.Reply.CODEC.decode(buf).result());
    }

    @Test
    void saveRoundTripsEightMissions() {
        List<PlannerEntry> entries = new ArrayList<>();
        for (int i = 0; i < TabletState.MAX_MISSIONS; i++) {
            entries.add(PlannerEntry.of("Mission ä" + i, fullMission()));
        }
        PlannerSave.Request request = new PlannerSave.Request(40, new PlannerMissions(entries), new PlannerSettings(25, 7));
        PlannerSave.Request decoded = roundTrip(PlannerSave.Request.CODEC, request);
        assertEquals(request.missions(), decoded.missions());
        assertEquals(request.settings(), decoded.settings());
        assertEquals(40, decoded.slot());
        ByteBuf buf = Unpooled.buffer();
        PlannerSave.Request.CODEC.encode(buf, request);
        assertTrue(buf.readableBytes() < 32_767, "unter der Grenze für Pakete zum Server: " + buf.readableBytes());
    }

    @Test
    void componentsRoundTripAsJson() {
        PlannerMissions missions = new PlannerMissions(List.of(PlannerEntry.of("Burg", fullMission()), PlannerEntry.empty("")));
        JsonElement json = PlannerCodecs.MISSIONS_CODEC.encodeStart(JsonOps.INSTANCE, missions).getOrThrow();
        assertEquals(missions, PlannerCodecs.MISSIONS_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        PlannerSettings settings = new PlannerSettings(33, 2);
        JsonElement settingsJson = PlannerCodecs.SETTINGS_CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, PlannerCodecs.SETTINGS_CODEC.parse(JsonOps.INSTANCE, settingsJson).getOrThrow());
        PlannerLink link = PlannerLink.of(UUID.randomUUID(), "Kino-Drohne", FlightClass.CINE);
        JsonElement linkJson = PlannerLink.CODEC.encodeStart(JsonOps.INSTANCE, link).getOrThrow();
        PlannerLink back = PlannerLink.CODEC.parse(JsonOps.INSTANCE, linkJson).getOrThrow();
        assertEquals(link, back);
        assertEquals(FlightClass.CINE, back.flightClassValue().orElseThrow());
        assertEquals(link, roundTrip(PlannerLink.STREAM_CODEC, link));
        assertEquals(8, link.shortSerial().length());
    }

    @Test
    void settingsAndMissionsAreClamped() {
        assertEquals(TabletState.MAX_GROUND_HEIGHT, new PlannerSettings(9999, 0).groundHeight());
        assertEquals(TabletState.MAX_MISSIONS - 1, new PlannerSettings(10, 99).active());
        List<PlannerEntry> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(PlannerEntry.empty("M" + i));
        }
        assertEquals(TabletState.MAX_MISSIONS, new PlannerMissions(many).entries().size());
    }

    @Test
    void unknownMissionVersionSurvivesTheComponent() {
        int[] future = MissionCodec.encode(Mission.of(List.of(Waypoint.at(1, 70, 1))));
        future[0] = MissionLimits.FORMAT_VERSION + 1;
        PlannerMissions missions = new PlannerMissions(List.of(PlannerEntry.fromPacked("neu", future)));
        JsonElement json = PlannerCodecs.MISSIONS_CODEC.encodeStart(JsonOps.INSTANCE, missions).getOrThrow();
        PlannerMissions back = PlannerCodecs.MISSIONS_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertArrayEquals(future, back.entries().getFirst().packed());
        assertArrayEquals(future, roundTrip(PlannerCodecs.MISSIONS_STREAM_CODEC, missions).entries().getFirst().packed());
    }

    @Test
    void networkDefinitionIsCompleteAndInOwnNamespace() {
        CamNetwork.define();
        List<CamNetwork.ServerBound<?>> server = CamNetwork.serverBound();
        List<CamNetwork.ClientBound<?>> client = CamNetwork.clientBound();
        Set<CustomPacketPayload.Type<?>> types = new HashSet<>();
        for (CamNetwork.ServerBound<?> entry : server) {
            assertTrue(types.add(entry.type()));
            assertEquals(CamDrones.MOD_ID, entry.type().id().getNamespace());
        }
        for (CamNetwork.ClientBound<?> entry : client) {
            assertTrue(types.add(entry.type()));
            assertEquals(CamDrones.MOD_ID, entry.type().id().getNamespace());
        }
        assertTrue(types.contains(MissionUpload.REQUEST));
        assertTrue(types.contains(MissionUpload.REPLY));
        assertTrue(types.contains(PlannerSave.REQUEST));
        assertTrue(types.contains(ExitSettingUpload.REQUEST));
        assertTrue(types.contains(ExitSettingUpload.REPLY));
        assertEquals("camdrones:mission_upload", MissionUpload.REQUEST.id().toString());
        assertThrows(IllegalStateException.class, () -> CamNetwork.clientBound(MissionUpload.REPLY, MissionUpload.Reply.CODEC));
        assertEquals("1", CamNetwork.VERSION);
    }

    @Test
    void clientDispatchReachesTheRegisteredHandler() {
        List<MissionUpload.Reply> seen = new ArrayList<>();
        CamNetwork.onClient(MissionUpload.REPLY, seen::add);
        CamNetwork.dispatchClient(new MissionUpload.Reply(3, UploadCheck.Result.OK, -1));
        assertEquals(1, seen.size());
        assertEquals(3, seen.getFirst().requestId());
        assertFalse(CamNetwork.sendToServer(new MissionUpload.Request(1, UUID.randomUUID(), new int[0])), "ohne Loader nichts senden");
    }
}
