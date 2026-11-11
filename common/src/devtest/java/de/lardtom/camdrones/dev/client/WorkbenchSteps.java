package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WorkbenchSteps {
    public static final String NAME = "teile: Werkbank zeigt die drei Presets";
    public static final Identifier WORKBENCH = Identifier.fromNamespaceAndPath("propwash", "drone_workbench");
    public static final float BATTERY_CHARGE = 0.86F;
    private static final String NOTE = "werkbank.note";

    private WorkbenchSteps() {
    }

    public static void register() {
        Script script = Script.create().append(Shots.photoWindow()).server("Werkbank aufstellen", (server, state) -> place(server));
        for (CamPresets.Entry entry : CamPresets.ALL) {
            script.server("Teile von " + entry.path() + " einlegen", (server, state) -> build(server, state, entry))
                    .then("Werkbank zeigt " + entry.path(), (ctx, ticks) -> shown(ctx, ticks, entry))
                    .append(Shots.photos("workbench_" + entry.path()))
                    .server("Werkbank leeren", (server, state) -> empty(server));
        }
        SelfTest.clientStep(1, NAME, 20 * 120, script);
    }

    private static BlockPos bench() {
        return Arena.ground(0, 2).above();
    }

    private static void place(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        Block block = BuiltInRegistries.BLOCK.getOptional(WORKBENCH).orElseThrow(() -> new IllegalStateException("Block " + WORKBENCH + " fehlt"));
        Arena.place(player.level(), bench(), block.defaultBlockState());
        Vec3 spawn = Arena.spawn();
        player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 30.0F, true);
    }

    private static List<ItemStack> parts(BuildSpec build) {
        List<ItemStack> out = new ArrayList<>();
        out.add(PropwashApi.partItems().stack(build.frame()));
        out.add(PropwashApi.partItems().stack(build.stack()));
        out.add(PropwashApi.partItems().stack(build.video()));
        out.add(PropwashApi.partItems().stack(build.battery(), BATTERY_CHARGE));
        for (int i = 0; i < 4; i++) {
            out.add(PropwashApi.partItems().stack(build.motor()));
        }
        for (int i = 0; i < 4; i++) {
            out.add(PropwashApi.partItems().stack(build.prop()));
        }
        for (Identifier accessory : build.accessories().values()) {
            out.add(PropwashApi.partItems().stack(accessory));
        }
        return out;
    }

    private static void build(MinecraftServer server, Map<String, Object> state, CamPresets.Entry entry) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockState bench = level.getBlockState(bench());
        if (player.containerMenu == player.inventoryMenu && player.openMenu(bench.getMenuProvider(level, bench())).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü öffnet nicht");
        }
        AbstractContainerMenu menu = player.containerMenu;
        BuildSpec spec = Pilot.preset(entry).data().build();
        for (ItemStack part : parts(spec)) {
            if (part.isEmpty()) {
                throw new IllegalStateException("kein Item für ein Teil von " + entry.path());
            }
            Slot target = null;
            for (Slot slot : menu.slots) {
                if (!(slot.container instanceof Inventory) && !slot.hasItem() && slot.mayPlace(part)) {
                    target = slot;
                    break;
                }
            }
            if (target == null) {
                throw new IllegalStateException("kein freier Platz für " + BuiltInRegistries.ITEM.getKey(part.getItem()));
            }
            menu.setCarried(part);
            menu.clicked(target.index, 0, ContainerInput.PICKUP, player);
            if (!menu.getCarried().isEmpty()) {
                menu.setCarried(ItemStack.EMPTY);
                throw new IllegalStateException("Platz " + target.index + " nimmt " + BuiltInRegistries.ITEM.getKey(part.getItem()) + " nicht");
            }
        }
        Optional<ItemStack> result = Optional.empty();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && PropwashApi.builds().isDrone(slot.getItem())) {
                result = Optional.of(slot.getItem());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("keine fertige Drohne in der Werkbank für " + entry.path());
        }
        Optional<DroneData> data = PropwashApi.builds().read(result.get());
        if (data.isEmpty() || !data.get().build().equals(spec)) {
            throw new IllegalStateException("Werkbank baut " + data.map(d -> d.build().toString()).orElse("nichts") + " statt " + spec);
        }
        state.put(NOTE, entry.path() + " fertig gebaut (" + spec.allParts().size() + " Teile-Sorten)");
    }

    private static StepResult shown(SelfTest.StepContext ctx, int ticks, CamPresets.Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return ticks > 100 ? StepResult.fail("Werkbank-Fenster fehlt: " + SelfTestRunner.screenName(mc)) : StepResult.pending();
        }
        if (ticks < 5) {
            return StepResult.pending();
        }
        Shots.parkMouse(mc);
        String label = I18n.get(entry.translationKey());
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        if (!frame.shows(label)) {
            if (ticks < 40) {
                return StepResult.pending();
            }
            ctx.log("Texte in der Werkbank: " + frame.values());
            return StepResult.fail("Werkbank zeigt nicht \"" + label + "\"");
        }
        return StepResult.pass(ctx.state().get(NOTE) + ", Werkbank zeigt \"" + label + "\"");
    }

    private static void empty(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && slot.hasItem() && slot.mayPickup(player)) {
                slot.set(ItemStack.EMPTY);
            }
        }
        menu.broadcastChanges();
        player.getInventory().clearContent();
    }
}
