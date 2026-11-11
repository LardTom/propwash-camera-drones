package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.pad.LandingPadBlock;
import de.lardtom.camdrones.pad.LandingPadItem;
import de.lardtom.camdrones.pad.PadBlocks;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.part.CamTab;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LandingPadSteps {
    public static final String NAME = "landeplatz: setzen, Drohne darauf, abbauen";
    private static final String PAD = "landeplatz.pos";
    private static final String NOTES = "landeplatz.notes";
    private static final int DX = 76;
    private static final int DZ = 64;
    private static final int STEP_TICKS = 20 * 90;

    private LandingPadSteps() {
    }

    public static void register() {
        SelfTest.clientStep(402, NAME, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .check("Block, Item, Namen und Kreativ-Tab", LandingPadSteps::registered)
                .server("im Überleben mit dem Item setzen", LandingPadSteps::placeInSurvival)
                .then("Landeplatz am Client", (ctx, ticks) -> clientPad(ctx) ? StepResult.pass((String) ctx.state().get(NOTES))
                        : ticks > 100 ? StepResult.fail("Landeplatz fehlt am Client") : StepResult.pending())
                .server("Tour auf den Landeplatz", LandingPadSteps::droneOnTop)
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne fehlt am Client") : StepResult.pending())
                .sleep(30)
                .then("Drohne steht auf dem Landeplatz", LandingPadSteps::droneOnPad)
                .then("Blick auf den Landeplatz, leerer Platz in der Leiste", (ctx, ticks) -> look(ctx, 1))
                .sleep(3)
                .then("Landeplatz in der Hand, Name über der Leiste", (ctx, ticks) -> look(ctx, 0))
                .sleep(4)
                .append(Shots.photos(SurvivalCraftSteps.PHOTO + "landing_pad_drone"))
                .server("abbauen im Überleben", LandingPadSteps::breakInSurvival)
                .server("Block darunter weg", LandingPadSteps::loseSupport));
    }

    private static StepResult registered() {
        List<String> problems = new ArrayList<>();
        if (!PadBlocks.registered() || !BuiltInRegistries.BLOCK.containsKey(PadBlocks.LANDING_PAD_ID) || !BuiltInRegistries.ITEM.containsKey(PadBlocks.LANDING_PAD_ID)) {
            problems.add("Block oder Item " + PadBlocks.LANDING_PAD_ID + " nicht angemeldet");
        }
        for (String lang : List.of("en_us", "de_de")) {
            for (String key : List.of("block.camdrones.landing_pad", LandingPadItem.TOOLTIP_KEY)) {
                if (PropwashApi.text(lang, key).isEmpty()) {
                    problems.add(key + " fehlt in " + lang);
                }
            }
        }
        boolean inTab = CamTab.tab().map(tab -> tab.contents().stream().anyMatch(stack -> stack.is(PadBlocks.landingPadItem().orElseThrow())))
                .orElse(false);
        if (!inTab) {
            problems.add("nicht im Kreativ-Tab des Add-ons");
        }
        return problems.isEmpty() ? StepResult.pass("Block und Item angemeldet, Namen \"" + PropwashApi.text("en_us", "block.camdrones.landing_pad").orElse("")
                + "\" und \"" + PropwashApi.text("de_de", "block.camdrones.landing_pad").orElse("") + "\", im Kreativ-Tab")
                : StepResult.fail(String.join("; ", problems));
    }

    private static Block padBlock() {
        return PadBlocks.landingPad().orElseThrow();
    }

    private static void placeInSurvival(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos ground = Arena.ground(DX, DZ);
        BlockPos pos = ground.above();
        Arena.place(level, pos, Blocks.AIR.defaultBlockState());
        Vec3 stand = Vec3.atBottomCenterOf(pos).add(0.0, 0.0, -3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 35.0F, true);
        player.getInventory().setSelectedSlot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PadBlocks.landingPadItem().orElseThrow(), 2));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
        InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        BlockState placed = level.getBlockState(pos);
        if (!placed.is(padBlock())) {
            throw new IllegalStateException("Landeplatz nicht gesetzt (" + result + ", Block " + placed + ")");
        }
        VoxelShape shape = placed.getCollisionShape(level, pos);
        double top = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
        if (Math.abs(top - LandingPadBlock.HEIGHT) > 1.0E-6) {
            throw new IllegalStateException("Kollision bis " + top + " statt " + LandingPadBlock.HEIGHT);
        }
        int left = player.getMainHandItem().getCount();
        if (left != 1) {
            throw new IllegalStateException("im Überleben bleiben " + left + " statt 1 Landeplatz in der Hand");
        }
        state.put(PAD, pos);
        state.put(NOTES, "gesetzt mit dem Item im Überleben (" + result + "), Kollision " + Flight.format(top) + " m hoch, 1 Item verbraucht");
    }

    private static void droneOnTop(MinecraftServer server, Map<String, Object> state) {
        BlockPos pos = (BlockPos) state.get(PAD);
        Entity drone = CovCamAKit.spawnPreset(server, state, CamPresets.TOUR, pos, 0.0F);
        Vec3 top = Vec3.atBottomCenterOf(pos).add(0.0, LandingPadBlock.HEIGHT + 0.02, 0.0);
        drone.setPos(top.x, top.y, top.z);
        drone.setDeltaMovement(Vec3.ZERO);
        ServerPlayer player = Arena.player(server);
        Vec3 stand = Vec3.atBottomCenterOf(pos).add(0.0, 0.0, -2.0);
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 45.0F, true);
        player.getInventory().setSelectedSlot(0);
    }

    private static boolean clientPad(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = (BlockPos) ctx.state().get(PAD);
        return mc.level != null && pos != null && mc.level.getBlockState(pos).is(padBlock());
    }

    private static StepResult droneOnPad(SelfTest.StepContext ctx, int ticks) {
        BlockPos pos = (BlockPos) ctx.state().get(PAD);
        Entity drone = CamFlights.clientDrone(ctx.state()).orElse(null);
        if (drone == null) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double top = pos.getY() + LandingPadBlock.HEIGHT;
        double height = drone.getY() - top;
        double off = CamFlights.horizontal(drone.position(), Vec3.atBottomCenterOf(pos));
        if (Math.abs(height) < 0.05 && off < 0.6) {
            return StepResult.pass("Drohne steht auf dem Landeplatz (" + Flight.format(height) + " m über der Oberseite, " + Flight.format(off) + " m neben der Mitte)");
        }
        return ticks > 100 ? StepResult.fail("Drohne " + Flight.format(height) + " m über der Oberseite, " + Flight.format(off) + " m neben der Mitte")
                : StepResult.pending();
    }

    private static StepResult look(SelfTest.StepContext ctx, int slot) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = (BlockPos) ctx.state().get(PAD);
        if (mc.player == null || pos == null) {
            return StepResult.fail("kein Spieler oder Landeplatz");
        }
        Shots.clearOverlays(mc);
        mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(pos).add(0.0, 0.3, 0.0));
        mc.player.getInventory().setSelectedSlot(slot);
        return StepResult.pass();
    }

    private static void breakInSurvival(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos pos = (BlockPos) state.get(PAD);
        for (Entity drone : Arena.drones(level)) {
            drone.discard();
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!player.gameMode.destroyBlock(pos)) {
            throw new IllegalStateException("Landeplatz lässt sich im Überleben nicht abbauen");
        }
        if (!level.getBlockState(pos).isAir()) {
            throw new IllegalStateException("nach dem Abbauen bleibt " + level.getBlockState(pos));
        }
        if (drops(level, pos) != 1) {
            throw new IllegalStateException(drops(level, pos) + " Landeplätze gedroppt statt 1");
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0))) {
            item.discard();
        }
    }

    private static void loseSupport(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos base = Arena.ground(DX + 3, DZ).above();
        Arena.place(level, base, Blocks.STONE.defaultBlockState());
        Arena.place(level, base.above(), padBlock().defaultBlockState());
        if (!level.getBlockState(base.above()).is(padBlock())) {
            throw new IllegalStateException("Landeplatz auf Stein nicht gesetzt");
        }
        Arena.place(level, base, Blocks.AIR.defaultBlockState());
        if (!level.getBlockState(base.above()).isAir()) {
            throw new IllegalStateException("Landeplatz schwebt ohne Block darunter");
        }
        if (drops(level, base.above()) != 1) {
            throw new IllegalStateException("ohne Block darunter " + drops(level, base.above()) + " Landeplätze gedroppt statt 1");
        }
    }

    private static int drops(ServerLevel level, BlockPos pos) {
        int count = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0))) {
            if (item.getItem().is(PadBlocks.landingPadItem().orElseThrow())) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }
}
