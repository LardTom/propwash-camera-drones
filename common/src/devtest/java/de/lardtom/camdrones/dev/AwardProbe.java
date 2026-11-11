package de.lardtom.camdrones.dev;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.award.CamAwards;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AwardProbe {
    public static final String CASE = "awards";
    public static final List<String> GEAR = List.of("gimbal_compact", "gimbal_tour", "gimbal_cine", "flight_planner");

    private AwardProbe() {
    }

    public static AdvancementHolder holder(MinecraftServer server, String path) {
        AdvancementHolder holder = server.getAdvancements().get(CamDrones.id(path));
        if (holder == null) {
            throw new IllegalStateException("Fortschritt " + CamDrones.id(path) + " fehlt am Server");
        }
        return holder;
    }

    public static String loaded(MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        Identifier root = CamDrones.id(CamAwards.ROOT);
        for (String path : CamAwards.ALL) {
            AdvancementHolder holder = server.getAdvancements().get(CamDrones.id(path));
            if (holder == null) {
                missing.add(path);
            } else if (!path.equals(CamAwards.ROOT) && !holder.value().parent().filter(root::equals).isPresent()) {
                missing.add(path + " (Elternteil nicht " + root + ")");
            } else if (holder.value().display().isEmpty()) {
                missing.add(path + " (ohne Anzeige)");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Fortschritte fehlen: " + missing);
        }
        AdvancementHolder holder = holder(server, CamAwards.ROOT);
        boolean tab = holder.value().parent().isEmpty() && holder.value().display().flatMap(display -> display.background()).isPresent();
        if (!tab) {
            throw new IllegalStateException(root + " öffnet keinen eigenen Tab");
        }
        return CamAwards.ALL.size() + " Fortschritte geladen (Tab " + root + " mit " + String.join(", ", CamAwards.AWARDED) + ")";
    }

    public static boolean done(@Nullable ServerPlayer player, String path) {
        if (player == null) {
            return false;
        }
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(CamDrones.id(path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static void revoke(ServerPlayer player, String path) {
        AdvancementHolder holder = holder(player.level().getServer(), path);
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : toList(progress.getCompletedCriteria())) {
            player.getAdvancements().revoke(holder, criterion);
        }
        if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
            throw new IllegalStateException(path + " lässt sich nicht zurücknehmen");
        }
    }

    public static void revokeAll(ServerPlayer player) {
        clearGear(player);
        for (String path : CamAwards.ALL) {
            revoke(player, path);
        }
    }

    public static void clearGear(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (CamDrones.MOD_ID.equals(id.getNamespace()) && GEAR.contains(id.getPath())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public static String status(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (String path : CamAwards.ALL) {
            out.add(path + "=" + (done(player, path) ? "done" : "open"));
        }
        return String.join(" ", out);
    }

    private static List<String> toList(Iterable<String> values) {
        List<String> out = new ArrayList<>();
        values.forEach(out::add);
        return out;
    }
}
