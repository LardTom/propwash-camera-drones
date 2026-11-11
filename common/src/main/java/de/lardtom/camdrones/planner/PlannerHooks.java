package de.lardtom.camdrones.planner;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class PlannerHooks {
    public interface Client {
        Client NONE = new Client() {
        };

        default boolean aimsAtDrone() {
            return false;
        }

        default boolean aimsAtMarker(ItemStack stack) {
            return false;
        }

        default void openEditor(InteractionHand hand) {
        }
    }

    private static volatile Client client = Client.NONE;

    private PlannerHooks() {
    }

    public static void client(Client value) {
        client = Objects.requireNonNull(value, "client");
    }

    public static Client client() {
        return client;
    }
}
