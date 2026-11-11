package de.lardtom.camdrones.integration;

import com.mojang.serialization.Codec;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.EnumCodec;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamDroneDataTest {
    private record Key<T>(Identifier id, Codec<T> codec, T defaultValue, Sync sync) implements DroneDataKey<T> {
    }

    private static final class Access implements DroneDataAccess {
        final List<DroneDataKey<?>> registered = new ArrayList<>();

        @Override
        public <T> DroneDataKey<T> register(Identifier id, Codec<T> codec, T defaultValue, DroneDataKey.Sync sync) {
            DroneDataKey<T> key = new Key<>(id, codec, defaultValue, sync);
            this.registered.add(key);
            return key;
        }

        @Override
        public <T> T get(Entity drone, DroneDataKey<T> key) {
            return key.defaultValue();
        }

        @Override
        public <T> T get(ItemStack droneItem, DroneDataKey<T> key) {
            return key.defaultValue();
        }

        @Override
        public <T> void set(Entity drone, DroneDataKey<T> key, T value) {
        }

        @Override
        public void remove(Entity drone, DroneDataKey<?> key) {
        }

        @Override
        public List<DroneDataKey<?>> keys() {
            return List.copyOf(this.registered);
        }

        @Override
        public Optional<DroneDataKey<?>> find(Identifier id) {
            return Optional.empty();
        }
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static Mission fullMission() {
        List<PointOfInterest> points = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            points.add(new PointOfInterest(-MissionLimits.MAX_HORIZONTAL_DM + i, MissionLimits.MAX_Y_DM, MissionLimits.MAX_HORIZONTAL_DM - i,
                    MissionLimits.MAX_RADIUS_DM));
        }
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS; i++) {
            waypoints.add(new Waypoint(MissionLimits.MAX_HORIZONTAL_DM - i, MissionLimits.MIN_Y_DM + i, -MissionLimits.MAX_HORIZONTAL_DM + i,
                    MissionLimits.MAX_SPEED_DM_S, MissionLimits.MAX_WAIT_DS, MissionLimits.MIN_TILT, Waypoint.Heading.values()[i % 4], i % 4,
                    Waypoint.Action.values()[i % 4], 359));
        }
        return new Mission(true, true, Mission.End.LAND, MissionLimits.MAX_SPEED_DM_S, ObstacleGuard.Mode.CLIMB_OVER, waypoints, points);
    }

    @Test
    void fiveTrackedKeysWithDesignIdsAndDefaults() {
        Access access = new Access();
        CamDroneData.Keys keys = CamDroneData.create(access);
        assertEquals(5, access.registered.size());
        assertEquals(CamDroneData.IDS, access.registered.stream().map(DroneDataKey::id).toList());
        assertEquals(List.of("camdrones:serial", "camdrones:mission", "camdrones:mission_state", "camdrones:smart", "camdrones:settings"),
                CamDroneData.IDS.stream().map(Identifier::toString).toList());
        for (DroneDataKey<?> key : keys.all()) {
            assertEquals(DroneDataKey.Sync.TRACKING, key.sync(), key.id().toString());
        }
        assertEquals(CamDroneData.NO_SERIAL, keys.serial().defaultValue());
        assertEquals(MissionData.EMPTY, keys.mission().defaultValue());
        assertEquals(MissionStatus.IDLE, keys.missionState().defaultValue());
        assertEquals(SmartState.NONE, keys.smart().defaultValue());
        assertEquals(DroneSettings.DEFAULT, keys.settings().defaultValue());
        assertEquals(access.registered, keys.all());
    }

    @Test
    void fullMissionRoundTripsAsIntArrayBelowTwoKibibytes() {
        Mission mission = fullMission();
        MissionData data = MissionData.of(mission);
        assertEquals(MissionLimits.packedInts(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST), data.length());
        Tag tag = MissionData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        assertInstanceOf(IntArrayTag.class, tag);
        MissionData back = roundTrip(MissionData.CODEC, data);
        assertEquals(data, back);
        assertEquals(data.hashCode(), back.hashCode());
        assertEquals(Optional.of(mission), back.mission());
        int bytes = CamDroneData.encodedBytes(MissionData.CODEC, data);
        assertTrue(bytes <= DroneDataAccess.MAX_BYTES_PER_KEY, "Mission " + bytes + " Byte");
        assertEquals(MissionLimits.packedBytes(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST) + 5, bytes);
        assertTrue(CamDroneData.fits(MissionData.CODEC, data));
    }

    @Test
    void unknownMissionVersionStaysUnchanged() {
        int[] future = {MissionLimits.FORMAT_VERSION + 1, 7, 3, 99, 1, 0, 42, 43};
        MissionData data = MissionData.of(future);
        future[1] = 0;
        MissionData back = roundTrip(MissionData.CODEC, data);
        assertArrayEquals(new int[]{MissionLimits.FORMAT_VERSION + 1, 7, 3, 99, 1, 0, 42, 43}, back.ints());
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, back.decode().status());
        assertEquals(Optional.empty(), back.mission());
        assertEquals(MissionLimits.FORMAT_VERSION + 1, back.version());
        assertSame(MissionData.EMPTY, roundTrip(MissionData.CODEC, MissionData.EMPTY));
        assertSame(MissionData.EMPTY, MissionData.of(Mission.empty()));
        assertEquals(Optional.empty(), MissionData.EMPTY.mission());
    }

    @Test
    void smallKeysRoundTripAndStayFarBelowTheLimit() {
        UUID serial = UUID.fromString("0b6f6f6e-1c2d-4e5f-8a9b-0c1d2e3f4a5b");
        assertEquals(serial, roundTrip(UUIDUtil.CODEC, serial));
        MissionStatus status = new MissionStatus(NavLogic.MissionState.PAUSED, 17, 4);
        assertEquals(status, roundTrip(MissionStatus.CODEC, status));
        SmartState poi = SmartState.pointOfInterest(120.5, 70.0, -33.25, 18.0, 4.5, false);
        SmartState follow = SmartState.follow(serial, FollowController.Style.PARALLEL);
        SmartState lock = SmartState.courseLock(-30.0);
        for (SmartState smart : List.of(SmartState.NONE, SmartState.hold(1.0, 2.0, 3.0), poi, follow, lock)) {
            assertEquals(smart, roundTrip(SmartState.CODEC, smart));
            assertTrue(CamDroneData.encodedBytes(SmartState.CODEC, smart) < 512, smart.toString());
        }
        assertEquals(330.0, lock.course(), 1.0E-9);
        DroneSettings settings = DroneSettings.DEFAULT.withExit(DroneSettings.ExitBehaviour.LAND).withObstacle(ObstacleGuard.Mode.CLIMB_OVER)
                .withView(DroneSettings.CameraView.HUD);
        assertEquals(settings, roundTrip(DroneSettings.CODEC, settings));
        int total = CamDroneData.encodedBytes(UUIDUtil.CODEC, serial) + CamDroneData.encodedBytes(MissionData.CODEC, MissionData.of(fullMission()))
                + CamDroneData.encodedBytes(MissionStatus.CODEC, status) + CamDroneData.encodedBytes(SmartState.CODEC, follow)
                + CamDroneData.encodedBytes(DroneSettings.CODEC, settings);
        assertTrue(total <= DroneDataAccess.MAX_BYTES_PER_DRONE / 2, "alle Schlüssel zusammen " + total + " Byte");
    }

    @Test
    void readingIsTolerantAgainstMissingAndUnknownValues() {
        CompoundTag tag = new CompoundTag();
        tag.put("state", StringTag.valueOf("future_state"));
        tag.putInt("index", 999);
        MissionStatus status = MissionStatus.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(NavLogic.MissionState.IDLE, status.state());
        assertEquals(MissionLimits.MAX_WAYPOINTS - 1, status.index());
        assertEquals(DroneSettings.DEFAULT, DroneSettings.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow());
        assertEquals(SmartState.NONE, SmartState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow());
        CompoundTag odd = new CompoundTag();
        odd.putDouble("speed", 999.0);
        odd.putDouble("radius", -5.0);
        odd.putString("function", "POI");
        SmartState clamped = SmartState.CODEC.parse(NbtOps.INSTANCE, odd).getOrThrow();
        assertEquals(SmartState.Function.POI, clamped.function());
        assertEquals(20.0, clamped.speed(), 1.0E-9);
        assertEquals(0.0, clamped.radius(), 1.0E-9);
        assertEquals(ObstacleGuard.Mode.CLIMB_OVER, EnumCodec.parse(ObstacleGuard.Mode.class, " climb_over ", ObstacleGuard.Mode.STOP));
        assertEquals(ObstacleGuard.Mode.STOP, EnumCodec.parse(ObstacleGuard.Mode.class, null, ObstacleGuard.Mode.STOP));
    }

    @Test
    void valuesAreImmutable() {
        int[] packed = MissionCodec.encode(fullMission());
        MissionData data = MissionData.of(packed);
        int[] copy = data.ints();
        copy[0] = 99;
        assertEquals(MissionLimits.FIRST_FORMAT_VERSION, data.version());
        assertNotEquals(MissionData.EMPTY, data);
        assertFalse(data.isEmpty());
        MissionStatus status = MissionStatus.IDLE.with(NavLogic.MissionState.RUNNING, 3).nextEpoch();
        assertEquals(new MissionStatus(NavLogic.MissionState.RUNNING, 3, 1), status);
        assertTrue(status.active());
        assertEquals(0, new MissionStatus(NavLogic.MissionState.DONE, 0, Integer.MAX_VALUE).nextEpoch().epoch());
        assertFalse(SmartState.NONE.active());
        assertTrue(SmartState.hold(0.0, 0.0, 0.0).active());
    }
}
