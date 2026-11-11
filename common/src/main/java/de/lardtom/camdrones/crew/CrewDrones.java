package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.part.GimbalTable;
import de.lardtom.camdrones.part.LensBay;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class CrewDrones {
    private CrewDrones() {
    }

    public static Optional<Identifier> frontAccessory(Entity drone) {
        if (drone == null) {
            return Optional.empty();
        }
        try {
            return PropwashApi.accessories().accessoryAt(drone, AccessorySlot.FRONT);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<GimbalTable.Spec> gimbal(Entity drone) {
        return frontAccessory(drone).flatMap(GimbalTable::spec);
    }

    public static boolean hasGimbal(Entity drone) {
        return gimbal(drone).isPresent();
    }

    public static OptionalInt lensFocal(Entity drone) {
        try {
            return LensBay.focalLength(PropwashApi.accessories().contents(drone, AccessorySlot.FRONT));
        } catch (RuntimeException e) {
            return OptionalInt.empty();
        }
    }

    public static Optional<UUID> pilot(Entity drone) {
        if (drone == null) {
            return Optional.empty();
        }
        try {
            return PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean pilotedBy(Entity drone, UUID player) {
        return player != null && pilot(drone).filter(player::equals).isPresent();
    }
}
