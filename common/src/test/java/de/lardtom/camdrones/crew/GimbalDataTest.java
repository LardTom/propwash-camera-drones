package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.integration.data.CamDroneData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalDataTest {
    private static GimbalData roundTrip(GimbalData value) {
        Tag tag = GimbalData.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return GimbalData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void settingIsSanitized() {
        GimbalSetting s = new GimbalSetting(null, -400.0, 270.0, -30.0, 3.0);
        assertEquals(GimbalSetting.Mode.FOLLOW, s.mode());
        assertEquals(-90.0, s.tilt());
        assertEquals(-90.0, s.pan(), 1.0E-9);
        assertEquals(330.0, s.heading(), 1.0E-9);
        assertEquals(1.0, s.zoom());
        GimbalSetting nan = new GimbalSetting(GimbalSetting.Mode.LOCK, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN);
        assertEquals(0.0, nan.tilt());
        assertEquals(0.0, nan.pan());
        assertEquals(0.0, nan.heading());
        assertEquals(0.0, nan.zoom());
    }

    @Test
    void differsIgnoresTinyChangesAndWrapsAngles() {
        GimbalSetting a = new GimbalSetting(GimbalSetting.Mode.FOLLOW, -30.0, 179.95, 359.95, 0.5);
        assertFalse(a.differs(a.withTilt(-30.1)));
        assertFalse(a.differs(a.withPan(-179.95)));
        assertFalse(a.differs(a.withHeading(0.05)));
        assertTrue(a.differs(a.withTilt(-31.0)));
        assertTrue(a.differs(a.withZoom(0.52)));
        assertTrue(a.differs(a.withMode(GimbalSetting.Mode.LOCK)));
        assertTrue(a.differs(null));
    }

    @Test
    void codecRoundTripKeepsOperatorAndSetting() {
        UUID operator = UUID.randomUUID();
        GimbalData data = new GimbalData(Optional.of(operator), new GimbalSetting(GimbalSetting.Mode.LOCK, -45.5F, 12.25F, 271.5F, 0.75F), 41);
        GimbalData back = roundTrip(data);
        assertEquals(data, back);
        assertTrue(back.operatedBy(operator));
        assertFalse(back.operatedBy(UUID.randomUUID()));
        assertEquals(GimbalData.NONE, roundTrip(GimbalData.NONE));
    }

    @Test
    void codecReadsBrokenFieldsAsDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.put("mode", StringTag.valueOf("sideways"));
        tag.putFloat("tilt", -1000.0F);
        tag.putInt("seq", -5);
        GimbalData data = GimbalData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(GimbalSetting.Mode.FOLLOW, data.setting().mode());
        assertEquals(-90.0, data.setting().tilt());
        assertEquals(0, data.seq());
        assertFalse(data.operated());
    }

    @Test
    void sequenceCountsEveryChangeAndWraps() {
        GimbalData data = GimbalData.NONE.withSetting(GimbalSetting.CENTER.withTilt(-10.0)).withOperator(Optional.of(UUID.randomUUID()));
        assertEquals(2, data.seq());
        GimbalData wrapped = new GimbalData(Optional.empty(), GimbalSetting.CENTER, Integer.MAX_VALUE).withSetting(GimbalSetting.CENTER);
        assertEquals(1, wrapped.seq());
    }

    @Test
    void fitsTheDroneDataLimit() {
        GimbalData data = new GimbalData(Optional.of(UUID.randomUUID()), new GimbalSetting(GimbalSetting.Mode.LOCK, -89.0, 179.0, 359.0, 1.0),
                Integer.MAX_VALUE);
        int bytes = CamDroneData.encodedBytes(GimbalData.CODEC, data);
        assertTrue(bytes < 256, "Bytes " + bytes);
        assertTrue(CamDroneData.fits(GimbalData.CODEC, data));
    }

    @Test
    void keyIdIsInOwnNamespace() {
        assertEquals("camdrones:gimbal", CrewData.GIMBAL_ID.toString());
        assertFalse(CamDroneData.IDS.contains(CrewData.GIMBAL_ID));
    }

    @Test
    void codecKeepsTheFpvMode() {
        GimbalData data = new GimbalData(Optional.empty(), new GimbalSetting(GimbalSetting.Mode.FPV, -20.0F, 0.0F, 90.0F, 0.0F), 3);
        GimbalData back = roundTrip(data);
        assertEquals(data, back);
        assertTrue(back.setting().fpv());
    }
    @Test
    void soloFlagsRoundTripAndEndWithTheOperator() {
        UUID owner = UUID.randomUUID();
        GimbalData solo = GimbalData.NONE.withSolo(owner);
        assertTrue(solo.solo());
        assertTrue(solo.soloBy(owner));
        assertFalse(solo.soloBy(UUID.randomUUID()));
        assertTrue(solo.operatedBy(owner), "der Besitzer ist sein eigener Kameramann");
        assertFalse(solo.manual(), "erst die erste Bewegung übernimmt");
        GimbalData steered = solo.withSteered().withSetting(GimbalSetting.CENTER.withTilt(-60.0));
        assertTrue(steered.manual());
        assertEquals(steered, roundTrip(steered));
        GimbalData ended = steered.withOperator(Optional.empty());
        assertFalse(ended.solo());
        assertFalse(ended.manual());
        assertFalse(GimbalData.NONE.withOperator(Optional.of(owner)).solo(), "Kameramann der Crew ist nicht allein");
        assertFalse(new GimbalData(Optional.empty(), GimbalSetting.CENTER, 1, true, true).manual(), "ohne Operator kein Steuern");
    }

    @Test
    void dataWithoutSoloEncodesLikeBefore() {
        GimbalData crew = new GimbalData(Optional.of(UUID.randomUUID()), new GimbalSetting(GimbalSetting.Mode.LOCK, -45.5F, 12.25F, 271.5F, 0.75F), 41);
        CompoundTag tag = (CompoundTag) GimbalData.CODEC.encodeStart(NbtOps.INSTANCE, crew).getOrThrow();
        assertFalse(tag.contains("solo"));
        assertFalse(tag.contains("steered"));
        int before = CamDroneData.encodedBytes(GimbalData.CODEC, crew);
        GimbalData solo = new GimbalData(crew.operator(), crew.setting(), 41, true, true);
        assertTrue(CamDroneData.encodedBytes(GimbalData.CODEC, solo) - before < 32);
        assertTrue(CamDroneData.fits(GimbalData.CODEC, solo));
    }
}
