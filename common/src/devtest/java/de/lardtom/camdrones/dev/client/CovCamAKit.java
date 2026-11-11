package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CovCamAKit {
    public static final String PHOTO = "covcama_";
    public static final String DRONE = "covcama.drone";
    public static final String WALL_AXIS = "covcama.wallAxis";
    public static final String WALL_SIGN = "covcama.wallSign";
    public static final String WALL_FACE = "covcama.wallFace";
    private static final String USE_STAGE = "covcama.useStage";
    private static final String USE_SINCE = "covcama.useSince";
    private static @Nullable Field overlayField;

    private CovCamAKit() {
    }

    public static Path out() {
        return Services.PLATFORM.gameDir().resolve("covcama");
    }

    public static String format(double value) {
        return String.format(Locale.GERMANY, "%.2f", value);
    }

    public static Optional<Component> overlay() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Field field = overlayField;
            if (field == null) {
                field = Hud.class.getDeclaredField("overlayMessageString");
                field.setAccessible(true);
                overlayField = field;
            }
            Object value = field.get(mc.gui.hud);
            return value instanceof Component component ? Optional.of(component) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> overlayKey() {
        return overlay().map(Component::getContents).filter(TranslatableContents.class::isInstance).map(c -> ((TranslatableContents) c).getKey());
    }

    public static String overlayText() {
        return overlay().map(Component::getString).orElse("");
    }

    public static void clearOverlay() {
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.empty(), false);
    }

    public static Optional<AbstractWidget> widget(Screen screen, String label) {
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            if (widget.visible && label.equals(widget.getMessage().getString())) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    public static List<String> labels(Screen screen) {
        List<String> out = new ArrayList<>();
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            if (widget.visible) {
                out.add(widget.getMessage().getString());
            }
        }
        return out;
    }

    public static boolean click(Screen screen, AbstractWidget widget) {
        MouseButtonEvent event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return handled;
    }

    public static StepResult use(SelfTest.StepContext ctx, int ticks, Entity target, boolean sneak) {
        Minecraft mc = Minecraft.getInstance();
        int stage = (int) ctx.state().getOrDefault(USE_STAGE, 0);
        int since = (int) ctx.state().getOrDefault(USE_SINCE, 0);
        int waited = ticks - since;
        lookAt(target);
        switch (stage) {
            case 0 -> {
                mc.options.keyShift.setDown(sneak);
                ctx.state().put(USE_STAGE, 1);
                ctx.state().put(USE_SINCE, ticks);
            }
            case 1 -> {
                if (waited < 6) {
                    return StepResult.pending();
                }
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                ctx.state().put(USE_STAGE, 2);
                ctx.state().put(USE_SINCE, ticks);
            }
            case 2 -> {
                if (waited < 6) {
                    return StepResult.pending();
                }
                mc.options.keyShift.setDown(false);
                ctx.state().remove(USE_STAGE);
                ctx.state().remove(USE_SINCE);
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage);
            }
        }
        return StepResult.pending();
    }

    public static void lookAt(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        }
    }

    public static Entity place(ServerPlayer player, ItemStack stack, BlockPos ground, float yaw) {
        ServerLevel level = player.level();
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, -3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), yaw, 10.0F, true);
        ItemStack previous = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Set<UUID> before = new HashSet<>();
        for (Entity existing : Arena.drones(level)) {
            before.add(existing.getUUID());
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, previous.isEmpty() ? ItemStack.EMPTY : previous);
        for (Entity candidate : Arena.drones(level)) {
            if (!before.contains(candidate.getUUID())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Drohne nicht gesetzt: " + result);
    }

    public static Entity spawnPreset(MinecraftServer server, Map<String, Object> state, CamPresets.Entry entry, BlockPos ground, float yaw) {
        ServerPlayer player = Arena.player(server);
        Entity drone = place(player, PropwashApi.builds().createDrone(Pilot.preset(entry).data()), ground, yaw);
        state.put(Pilot.DRONE, drone.getUUID());
        return drone;
    }

    public static Optional<Entity> clientDrone(Map<String, Object> state) {
        return CamFlights.clientDrone(state);
    }

    public static BlockPos bench() {
        return Arena.ground(0, 2).above();
    }

    public static void placeBench(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        Block block = BuiltInRegistries.BLOCK.getOptional(WorkbenchSteps.WORKBENCH)
                .orElseThrow(() -> new IllegalStateException("Block " + WorkbenchSteps.WORKBENCH + " fehlt"));
        Arena.place(player.level(), bench(), block.defaultBlockState());
        Vec3 spawn = Arena.spawn();
        player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 30.0F, true);
    }

    public static void openBench(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockState bench = level.getBlockState(bench());
        if (player.containerMenu == player.inventoryMenu && player.openMenu(bench.getMenuProvider(level, bench())).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü öffnet nicht");
        }
    }

    public static List<ItemStack> parts(BuildSpec build, float charge) {
        List<ItemStack> out = new ArrayList<>();
        out.add(PropwashApi.partItems().stack(build.frame()));
        out.add(PropwashApi.partItems().stack(build.stack()));
        out.add(PropwashApi.partItems().stack(build.video()));
        out.add(PropwashApi.partItems().stack(build.battery(), charge));
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

    public static Optional<ItemStack> buildInBench(MinecraftServer server, BuildSpec spec, float charge) {
        openBench(server);
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        for (ItemStack part : parts(spec, charge)) {
            if (part.isEmpty()) {
                throw new IllegalStateException("kein Item für ein Teil");
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
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && PropwashApi.builds().isDrone(slot.getItem())) {
                Optional<DroneData> data = PropwashApi.builds().read(slot.getItem());
                if (data.isPresent() && data.get().build().equals(spec)) {
                    return Optional.of(slot.getItem().copy());
                }
            }
        }
        return Optional.empty();
    }

    public static void emptyBench(MinecraftServer server) {
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

    public static void closeBench(MinecraftServer server) {
        emptyBench(server);
        Arena.player(server).closeContainer();
    }

    public static void wall(MinecraftServer server, Map<String, Object> state, int distance) {
        Entity drone = Pilot.serverDrone(server, state);
        DroneView view = PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Ansicht der Drohne"));
        Vec3 forward = view.forward();
        boolean alongX = Math.abs(forward.x) > Math.abs(forward.z);
        int sign = (alongX ? forward.x : forward.z) >= 0.0 ? 1 : -1;
        BlockPos base = BlockPos.containing(drone.getX(), Arena.ground().getY(), drone.getZ());
        BlockPos center = alongX ? base.offset(sign * distance, 0, 0) : base.offset(0, 0, sign * distance);
        BlockPos from = alongX ? center.offset(0, 1, -6) : center.offset(-6, 1, 0);
        BlockPos to = alongX ? center.offset(0, 12, 6) : center.offset(6, 12, 0);
        Arena.fill(Arena.level(server), from, to, Blocks.STONE.defaultBlockState());
        double face = sign > 0 ? (alongX ? center.getX() : center.getZ()) : (alongX ? center.getX() + 1 : center.getZ() + 1);
        state.put(WALL_AXIS, alongX);
        state.put(WALL_SIGN, sign);
        state.put(WALL_FACE, face);
    }

    public static double wallDistance(Map<String, Object> state) {
        Optional<Vec3> position = Flight.position();
        if (position.isEmpty() || !state.containsKey(WALL_FACE)) {
            return Double.NaN;
        }
        boolean alongX = (boolean) state.get(WALL_AXIS);
        int sign = (int) state.get(WALL_SIGN);
        double face = (double) state.get(WALL_FACE);
        double coordinate = alongX ? position.get().x : position.get().z;
        return (face - coordinate) * sign;
    }

    public static CompletableFuture<String> capture(String name) {
        return Shots.capture(out().resolve(name + ".png"));
    }

    public static int magentaPixels(Path file, double top, double bottom) throws IOException {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            int count = 0;
            int y0 = (int) (image.getHeight() * top);
            int y1 = (int) (image.getHeight() * bottom);
            for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getPixel(x, y);
                    int r = argb >> 16 & 0xFF;
                    int g = argb >> 8 & 0xFF;
                    int b = argb & 0xFF;
                    if (r >= 0xE8 && g <= 0x18 && b >= 0xE8) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    public static StepResult awaitShot(SelfTest.StepContext ctx, int ticks, String key, String name) {
        Object pending = ctx.state().get(key);
        if (pending == null) {
            if (ticks < 8) {
                if (ticks == 0) {
                    Shots.clearOverlays(Minecraft.getInstance());
                }
                return StepResult.pending();
            }
            ctx.state().put(key, capture(name));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<String> future = (CompletableFuture<String>) pending;
        if (!future.isDone()) {
            return ticks > 400 ? StepResult.fail("Bild " + name + " hängt") : StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Bild " + name + ": " + future.exceptionNow());
        }
        return StepResult.pass();
    }
}
