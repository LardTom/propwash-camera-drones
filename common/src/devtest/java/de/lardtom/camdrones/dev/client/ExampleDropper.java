package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExampleDropper {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("camdrones_example", "dropper");
    public static final int SLOTS = 2;
    public static final int DROP = 1;
    public static final String NAME_KEY = "block.minecraft.dispenser";
    public static final int MIN_TICKS = 6;

    public record Press(int number, boolean pressed, String actor, boolean piloted, DispenseResult.Status result, long tick) {
    }

    private static final List<Press> PRESSES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long> LAST_DROP = new ConcurrentHashMap<>();
    private static boolean installed;

    private ExampleDropper() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            PropwashApi.parts().register(AccessoryDefinition.builder(ID)
                    .basics(PartBasics.of(38.0).withCreativeListed(false).withVisual(PartVisual.accent(0x6E6E6E)))
                    .slot(AccessorySlot.FRONT).size(30.0, 24.0, 36.0).frontalArea(0.0008)
                    .build());
            PropwashApi.accessories().register(ID, new Behavior());
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Beispiel-Werfer für den Selbsttest nicht angemeldet: {}", CamDrones.MOD_NAME, e.toString());
        }
    }

    public static boolean available() {
        return PropwashApi.accessories().behavior(ID).isPresent();
    }

    public static List<Press> presses() {
        return List.copyOf(PRESSES);
    }

    public static void reset() {
        PRESSES.clear();
        LAST_DROP.clear();
    }

    static final class Behavior implements AccessoryBehavior {
        @Override
        public int slots() {
            return SLOTS;
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return true;
        }

        @Override
        public void onAction(AccessoryAction action) {
            DispenseResult.Status status = DispenseResult.Status.EMPTY;
            long now = action.level().getGameTime();
            if (action.number() == DROP && action.pressed()) {
                Long last = LAST_DROP.get(action.drone().getUUID());
                int slot = action.inventory().firstFilled();
                if (last != null && now - last < MIN_TICKS) {
                    status = DispenseResult.Status.REFUSED;
                } else if (slot >= 0) {
                    status = action.dispense(slot, DispenseOptions.defaults()).status();
                    if (status.success()) {
                        LAST_DROP.put(action.drone().getUUID(), now);
                    }
                }
            }
            PRESSES.add(new Press(action.number(), action.pressed(), action.player().getGameProfile().name(), action.pilot().isPresent(), status,
                    now));
        }

        @Override
        public Optional<String> actionNameKey(int number) {
            return number == DROP ? Optional.of(NAME_KEY) : Optional.empty();
        }
    }
}
