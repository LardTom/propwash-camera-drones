package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewPacketsTest {
    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "alles gelesen");
        return decoded;
    }

    @Test
    void requestsRoundTrip() {
        UUID target = UUID.randomUUID();
        assertEquals(CrewPackets.Request.invite(123, target), roundTrip(CrewPackets.Request.CODEC, CrewPackets.Request.invite(123, target)));
        for (CrewPackets.Action action : CrewPackets.Action.values()) {
            CrewPackets.Request request = CrewPackets.Request.of(action, 7);
            assertEquals(request, roundTrip(CrewPackets.Request.CODEC, request));
        }
    }

    @Test
    void unknownActionIsReadAsHarmlessDecline() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 99);
        ByteBufCodecs.VAR_INT.encode(buf, 5);
        buf.writeBoolean(false);
        CrewPackets.Request request = CrewPackets.Request.CODEC.decode(buf);
        assertEquals(CrewPackets.Action.DECLINE, request.action());
        assertEquals(Optional.empty(), request.target());
    }

    @Test
    void noticeRoundTripAndCutsLongNames() {
        CrewPackets.Notice notice = new CrewPackets.Notice(CrewPackets.Kind.INVITED, 42, "Pilotin");
        assertEquals(notice, roundTrip(CrewPackets.Notice.CODEC, notice));
        CrewPackets.Notice longName = new CrewPackets.Notice(CrewPackets.Kind.BOUND, 1, "x".repeat(200));
        assertEquals(CrewPackets.MAX_NAME, longName.name().length());
        assertEquals(longName, roundTrip(CrewPackets.Notice.CODEC, longName));
        assertEquals("", new CrewPackets.Notice(CrewPackets.Kind.RELEASED, 1, null).name());
    }

    @Test
    void gimbalUpdateIsSmallAndExactInFloat() {
        GimbalSetting setting = new GimbalSetting(GimbalSetting.Mode.LOCK, -37.25F, 101.5F, 12.75F, 0.625F);
        CrewPackets.Gimbal update = new CrewPackets.Gimbal(77, setting);
        CrewPackets.Gimbal back = roundTrip(CrewPackets.Gimbal.CODEC, update);
        assertEquals(update, back);
        ByteBuf buf = Unpooled.buffer();
        CrewPackets.Gimbal.CODEC.encode(buf, update);
        assertTrue(buf.readableBytes() <= 20, "Paketgröße " + buf.readableBytes());
    }

    @Test
    void gimbalUpdateSanitizesHostileValues() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 3);
        ByteBufCodecs.VAR_INT.encode(buf, -4);
        buf.writeFloat(Float.NaN);
        buf.writeFloat(1.0E9F);
        buf.writeFloat(-725.0F);
        buf.writeFloat(Float.POSITIVE_INFINITY);
        CrewPackets.Gimbal update = CrewPackets.Gimbal.CODEC.decode(buf);
        assertEquals(GimbalSetting.Mode.FOLLOW, update.setting().mode());
        assertEquals(0.0, update.setting().tilt());
        assertTrue(Math.abs(update.setting().pan()) <= 180.0);
        assertEquals(355.0, update.setting().heading(), 1.0E-6);
        assertEquals(0.0, update.setting().zoom());
    }

    @Test
    void packetIdsAreInOwnNamespaceAndDistinct() {
        Set<String> ids = Set.of(CrewPackets.REQUEST.id().toString(), CrewPackets.NOTICE.id().toString(), CrewPackets.GIMBAL.id().toString());
        assertEquals(3, ids.size());
        for (String id : ids) {
            assertTrue(id.startsWith(CamDrones.MOD_ID + ":crew"), id);
        }
    }

    @Test
    void gimbalUpdateKeepsTheFpvMode() {
        CrewPackets.Gimbal update = new CrewPackets.Gimbal(5, new GimbalSetting(GimbalSetting.Mode.FPV, -20.0F, 0.0F, 90.0F, 0.0F));
        CrewPackets.Gimbal back = roundTrip(CrewPackets.Gimbal.CODEC, update);
        assertEquals(update, back);
        assertEquals(GimbalSetting.Mode.FPV, back.setting().mode());
    }
}
