package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class PlannerOwners {
    public static final Identifier OWNER_ID = CamDrones.id("planner_owner");
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    private static volatile DroneDataKey<UUID> key;

    private PlannerOwners() {
    }

    public static synchronized DroneDataKey<UUID> register(DroneDataAccess access) {
        DroneDataKey<UUID> current = key;
        if (current == null) {
            current = access.register(OWNER_ID, UUIDUtil.CODEC, NO_OWNER, DroneDataKey.Sync.NONE);
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

    public static UUID owner(Entity drone) {
        DroneDataKey<UUID> current = key;
        if (current == null || drone.level().isClientSide()) {
            return NO_OWNER;
        }
        UUID owner = PropwashApi.droneData().get(drone, current);
        return owner == null ? NO_OWNER : owner;
    }

    public static boolean claimable(Entity drone, UUID player) {
        if (key == null) {
            return false;
        }
        UUID owner = owner(drone);
        return NO_OWNER.equals(owner) || owner.equals(player);
    }

    public static void claim(Entity drone, UUID player) {
        DroneDataKey<UUID> current = key;
        if (current != null && !drone.level().isClientSide() && NO_OWNER.equals(owner(drone))) {
            PropwashApi.droneData().set(drone, current, player);
        }
    }
}
