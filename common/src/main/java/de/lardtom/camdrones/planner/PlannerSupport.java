package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.StartCheck;
import de.lardtom.camdrones.integration.autonomy.StartProbes;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.part.SensorTable;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.FrameDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class PlannerSupport {
    public static final Identifier GRIPPER = Identifier.fromNamespaceAndPath("propwash", "gripper");

    private PlannerSupport() {
    }

    public static Optional<BuildSpec> build(Entity drone) {
        if (drone == null || !PropwashApi.drones().isDrone(drone)) {
            return Optional.empty();
        }
        try {
            ItemStack stack = drone.getPickResult();
            if (stack == null || stack.isEmpty() || !PropwashApi.builds().isDrone(stack)) {
                return Optional.empty();
            }
            return PropwashApi.builds().read(stack).map(DroneData::build);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean navBoard(BuildSpec build) {
        return build != null && SensorTable.isNavBoard(build.stack());
    }

    public static Optional<Boolean> navEquipped(Entity drone) {
        return build(drone).map(PlannerSupport::navBoard);
    }

    public static boolean fpv(Entity drone) {
        return navEquipped(drone).map(nav -> !nav).orElse(false);
    }

    public static DroneSupport of(Optional<Entity> drone) {
        return drone.map(d -> of(d, true)).orElse(DroneSupport.UNKNOWN);
    }

    public static DroneSupport of(Entity drone, boolean measureGps) {
        Optional<BuildSpec> build = build(drone);
        if (build.isEmpty()) {
            return DroneSupport.UNKNOWN;
        }
        Collection<Identifier> accessories = accessories(drone, build.get());
        boolean leds = false;
        boolean actions = false;
        boolean gripper = false;
        List<Optional<String>> names = new ArrayList<>();
        for (int i = 0; i < Waypoint.Accessory.ACTIONS; i++) {
            names.add(Optional.empty());
        }
        for (Identifier id : accessories) {
            leds |= PropwashApi.parts().get(AccessoryDefinition.class, id).map(def -> def.ledCount() > 0).orElse(false);
            gripper |= GRIPPER.equals(id);
            Optional<AccessoryBehavior> behavior = CamDrones.MOD_ID.equals(id.getNamespace()) ? Optional.empty()
                    : PropwashApi.accessories().behavior(id);
            if (behavior.isEmpty()) {
                continue;
            }
            actions = true;
            for (int n = 1; n <= Waypoint.Accessory.ACTIONS; n++) {
                if (names.get(n - 1).isEmpty()) {
                    names.set(n - 1, name(behavior.get(), n));
                }
            }
        }
        Optional<String> role = PropwashApi.parts().get(FrameDefinition.class, build.get().frame()).map(def -> def.role().key());
        int satellites = measureGps ? StartCheck.satellites(StartProbes.skyView(drone)) : -1;
        return DroneSupport.of(navBoard(build.get()), role, satellites, leds, actions, gripper, names, LightControl.available());
    }

    private static Optional<String> name(AccessoryBehavior behavior, int number) {
        try {
            return behavior.actionNameKey(number).filter(key -> !key.isBlank());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Collection<Identifier> accessories(Entity drone, BuildSpec build) {
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isPresent() && !view.get().accessories().isEmpty()) {
            return view.get().accessories().values();
        }
        return build.accessories().values();
    }
}
