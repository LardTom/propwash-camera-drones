package de.lardtom.camdrones.part;

import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class LensBay implements AccessoryBehavior {
    public static final int SLOTS = 1;

    @Override
    public int slots() {
        return SLOTS;
    }

    @Override
    public boolean accepts(int slot, ItemStack stack) {
        return LensItems.lensOf(stack).isPresent();
    }

    @Override
    public int maxStackSize(int slot, ItemStack stack) {
        return 1;
    }

    @Override
    public OptionalDouble payloadGrams(List<ItemStack> contents) {
        double grams = 0.0;
        for (ItemStack stack : contents) {
            Optional<LensItems.Lens> lens = LensItems.lensOf(stack);
            if (lens.isPresent()) {
                grams += lens.get().grams() * stack.getCount();
            }
        }
        return OptionalDouble.of(grams);
    }

    public static OptionalInt focalLength(List<ItemStack> contents) {
        for (ItemStack stack : contents) {
            Optional<LensItems.Lens> lens = LensItems.lensOf(stack);
            if (lens.isPresent()) {
                return OptionalInt.of(lens.get().focalMm());
            }
        }
        return OptionalInt.empty();
    }
}
