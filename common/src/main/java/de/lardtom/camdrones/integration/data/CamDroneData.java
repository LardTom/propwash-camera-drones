package de.lardtom.camdrones.integration.data;

import com.mojang.serialization.Codec;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

public final class CamDroneData {
    public static final Identifier SERIAL_ID = CamDrones.id("serial");
    public static final Identifier MISSION_ID = CamDrones.id("mission");
    public static final Identifier MISSION_STATE_ID = CamDrones.id("mission_state");
    public static final Identifier SMART_ID = CamDrones.id("smart");
    public static final Identifier SETTINGS_ID = CamDrones.id("settings");
    public static final List<Identifier> IDS = List.of(SERIAL_ID, MISSION_ID, MISSION_STATE_ID, SMART_ID, SETTINGS_ID);
    public static final UUID NO_SERIAL = new UUID(0L, 0L);
    public static final int MAX_BYTES_PER_KEY = DroneDataAccess.MAX_BYTES_PER_KEY;

    private static volatile Keys keys;

    private CamDroneData() {
    }

    public record Keys(DroneDataKey<UUID> serial, DroneDataKey<MissionData> mission, DroneDataKey<MissionStatus> missionState,
                       DroneDataKey<SmartState> smart, DroneDataKey<DroneSettings> settings) {
        public List<DroneDataKey<?>> all() {
            return List.of(this.serial, this.mission, this.missionState, this.smart, this.settings);
        }
    }

    public static Keys create(DroneDataAccess access) {
        return new Keys(
                access.register(SERIAL_ID, UUIDUtil.CODEC, NO_SERIAL, DroneDataKey.Sync.TRACKING),
                access.register(MISSION_ID, MissionData.CODEC, MissionData.EMPTY, DroneDataKey.Sync.TRACKING),
                access.register(MISSION_STATE_ID, MissionStatus.CODEC, MissionStatus.IDLE, DroneDataKey.Sync.TRACKING),
                access.register(SMART_ID, SmartState.CODEC, SmartState.NONE, DroneDataKey.Sync.TRACKING),
                access.register(SETTINGS_ID, DroneSettings.CODEC, DroneSettings.DEFAULT, DroneDataKey.Sync.TRACKING));
    }

    public static synchronized Keys register(DroneDataAccess access) {
        Keys current = keys;
        if (current == null) {
            current = create(access);
            keys = current;
        }
        return current;
    }

    public static void init() {
        register(PropwashApi.droneData());
    }

    public static Keys keys() {
        Keys current = keys;
        if (current == null) {
            throw new IllegalStateException("Drohnen-Daten von " + CamDrones.MOD_ID + " sind noch nicht angemeldet");
        }
        return current;
    }

    public static boolean registered() {
        return keys != null;
    }

    public static DroneDataKey<UUID> serial() {
        return keys().serial();
    }

    public static DroneDataKey<MissionData> mission() {
        return keys().mission();
    }

    public static DroneDataKey<MissionStatus> missionState() {
        return keys().missionState();
    }

    public static DroneDataKey<SmartState> smart() {
        return keys().smart();
    }

    public static DroneDataKey<DroneSettings> settings() {
        return keys().settings();
    }

    public static <T> int encodedBytes(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(IllegalArgumentException::new);
        Counter counter = new Counter();
        try (DataOutputStream out = new DataOutputStream(counter)) {
            NbtIo.writeAnyTag(tag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return counter.count;
    }

    public static <T> boolean fits(Codec<T> codec, T value) {
        return encodedBytes(codec, value) <= MAX_BYTES_PER_KEY;
    }

    private static final class Counter extends OutputStream {
        private int count;

        @Override
        public void write(int b) {
            this.count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            this.count += len;
        }
    }
}
