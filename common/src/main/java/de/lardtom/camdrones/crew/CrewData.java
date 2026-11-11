package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class CrewData {
    public static final Identifier GIMBAL_ID = CamDrones.id("gimbal");

    private static volatile DroneDataKey<GimbalData> key;
    private static volatile boolean failureLogged;

    private CrewData() {
    }

    public static synchronized DroneDataKey<GimbalData> register(DroneDataAccess access) {
        DroneDataKey<GimbalData> current = key;
        if (current == null) {
            current = access.register(GIMBAL_ID, GimbalData.CODEC, GimbalData.NONE, DroneDataKey.Sync.TRACKING);
            key = current;
        }
        return current;
    }

    public static void init() {
        register(PropwashApi.droneData());
    }

    public static boolean registered() {
        return key != null;
    }

    public static GimbalData get(Entity drone) {
        DroneDataKey<GimbalData> current = key;
        if (current == null || drone == null) {
            return GimbalData.NONE;
        }
        try {
            GimbalData data = PropwashApi.droneData().get(drone, current);
            return data == null ? GimbalData.NONE : data;
        } catch (RuntimeException e) {
            return GimbalData.NONE;
        }
    }

    public static boolean set(Entity drone, GimbalData data) {
        DroneDataKey<GimbalData> current = key;
        if (current == null || drone == null || drone.level().isClientSide()) {
            return false;
        }
        try {
            PropwashApi.droneData().set(drone, current, data);
            return true;
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                CamDrones.LOG.warn("{}: Gimbal-Daten nicht an der Drohne gespeichert: {}", CamDrones.MOD_NAME, e.toString());
            }
            return false;
        }
    }
}
