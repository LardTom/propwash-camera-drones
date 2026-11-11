package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.pad.PadBlocks;
import de.lardtom.camdrones.planner.PlannerItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SurvivalCraftSteps {
    public static final String NAME = "rezepte: Flugplaner und Landeplatz im Überleben über das Rezeptbuch";
    public static final Identifier PLANNER_ADVANCEMENT = CamDrones.id("recipes/tools/flight_planner");
    public static final Identifier PAD_ADVANCEMENT = CamDrones.id("recipes/tools/landing_pad");
    public static final String PHOTO = "";
    private static final TagKey<Item> PARTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("propwash", "parts"));
    private static final String TABLE = "survival.table";
    private static final String PLACED = "survival.placed";
    private static final int DX = 70;
    private static final int DZ = 70;
    private static final int STEP_TICKS = 20 * 120;

    private SurvivalCraftSteps() {
    }

    public static void register() {
        SelfTest.clientStep(401, NAME, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Überleben, leeres Inventar, Rezepte gesperrt, Werkbank", SurvivalCraftSteps::prepare)
                .await("Flugplaner und Landeplatz nicht im Rezeptbuch", 100, () -> entry(PlannerItems.FLIGHT_PLANNER_ID).isEmpty()
                        && entry(PadBlocks.LANDING_PAD_ID).isEmpty())
                .server("erstes Propwash-Teil und Zutaten des Flugplaners", SurvivalCraftSteps::plannerIngredients)
                .then("Flugplaner freigeschaltet", (ctx, ticks) -> unlocked(ticks, PlannerItems.FLIGHT_PLANNER_ID))
                .run("Rezeptbuch aufklappen", () -> Minecraft.getInstance().player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true))
                .then("Werkbank öffnen", SurvivalCraftSteps::openTable)
                .then("Flugplaner über das Rezeptbuch einlegen", (ctx, ticks) -> place(ctx, ticks, PlannerItems.FLIGHT_PLANNER_ID))
                .append(Shots.photos(PHOTO + "survival_recipe_flight_planner"))
                .then("Flugplaner herausnehmen", (ctx, ticks) -> take(ticks, PlannerItems.FLIGHT_PLANNER_ID))
                .server("Zutaten des Landeplatzes", SurvivalCraftSteps::padIngredients)
                .then("Landeplatz mit dem Flugplaner freigeschaltet", (ctx, ticks) -> unlocked(ticks, PadBlocks.LANDING_PAD_ID))
                .then("Landeplatz über das Rezeptbuch einlegen", (ctx, ticks) -> place(ctx, ticks, PadBlocks.LANDING_PAD_ID))
                .append(Shots.photos(PHOTO + "survival_recipe_landing_pad"))
                .then("Landeplatz herausnehmen", (ctx, ticks) -> take(ticks, PadBlocks.LANDING_PAD_ID))
                .run("Werkbank schließen", () -> Minecraft.getInstance().player.closeContainer())
                .server("Server: Inventar nach dem Herstellen", SurvivalCraftSteps::checkInventory));
    }

    private static ResourceKey<Recipe<?>> recipe(Identifier item) {
        return ResourceKey.create(Registries.RECIPE, item);
    }

    private static void prepare(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        List<RecipeHolder<?>> own = new ArrayList<>();
        for (Identifier id : List.of(PlannerItems.FLIGHT_PLANNER_ID, PadBlocks.LANDING_PAD_ID)) {
            own.add(server.getRecipeManager().byKey(recipe(id)).orElseThrow(() -> new IllegalStateException("Rezept " + id + " nicht geladen")));
        }
        player.resetRecipes(own);
        for (Identifier id : List.of(PLANNER_ADVANCEMENT, PAD_ADVANCEMENT)) {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder == null) {
                throw new IllegalStateException("Fortschritt " + id + " fehlt");
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            List<String> done = new ArrayList<>();
            progress.getCompletedCriteria().forEach(done::add);
            done.forEach(criterion -> player.getAdvancements().revoke(holder, criterion));
        }
        for (Identifier id : List.of(PlannerItems.FLIGHT_PLANNER_ID, PadBlocks.LANDING_PAD_ID)) {
            if (player.getRecipeBook().contains(recipe(id))) {
                throw new IllegalStateException(id + " bleibt im Rezeptbuch");
            }
        }
        BlockPos table = Arena.ground(DX, DZ).above();
        Arena.place(level, table, Blocks.CRAFTING_TABLE.defaultBlockState());
        Vec3 stand = Vec3.atBottomCenterOf(table).add(0.0, 0.0, -2.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 30.0F, true);
        state.put(TABLE, table);
    }

    private static void plannerIngredients(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Iterator<Holder<Item>> parts = BuiltInRegistries.ITEM.getTagOrEmpty(PARTS).iterator();
        if (!parts.hasNext()) {
            throw new IllegalStateException("Tag #propwash:parts ist leer");
        }
        Inventory inventory = player.getInventory();
        inventory.add(new ItemStack(parts.next().value()));
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:iron_ingot")), 6));
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:glass_pane"))));
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:map"))));
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("propwash:circuit_board"))));
        player.inventoryMenu.broadcastChanges();
    }

    private static void padIngredients(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Inventory inventory = player.getInventory();
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:gray_carpet")), 2));
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:white_dye"))));
        inventory.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:smooth_stone_slab")), 3));
        player.containerMenu.broadcastChanges();
    }

    static Optional<RecipeDisplayEntry> entry(Identifier item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return Optional.empty();
        }
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                for (ItemStack result : entry.resultItems(context)) {
                    if (BuiltInRegistries.ITEM.getKey(result.getItem()).equals(item)) {
                        return Optional.of(entry);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static StepResult unlocked(int ticks, Identifier item) {
        if (entry(item).isPresent()) {
            return StepResult.pass(item + " im Rezeptbuch freigeschaltet");
        }
        return ticks > 200 ? StepResult.fail(item + " wird nicht freigeschaltet") : StepResult.pending();
    }

    private static StepResult openTable(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return StepResult.fail("kein Spieler");
        }
        if (ticks == 0) {
            BlockPos table = (BlockPos) ctx.state().get(TABLE);
            mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(table));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(table).add(0.0, 0.0, -0.5), Direction.NORTH, table, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return StepResult.pending();
        }
        if (mc.gui.screen() instanceof CraftingScreen && mc.player.containerMenu instanceof CraftingMenu) {
            return StepResult.pass("Werkbank offen im Überleben (" + (mc.player.isCreative() ? "Kreativ" : "Überleben") + ")");
        }
        return ticks > 100 ? StepResult.fail("Werkbank öffnet nicht (" + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
    }

    private static StepResult place(SelfTest.StepContext ctx, int ticks, Identifier item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !(mc.player.containerMenu instanceof CraftingMenu menu)) {
            return StepResult.fail("Werkbank geschlossen");
        }
        if (ticks == 0) {
            Optional<RecipeDisplayEntry> entry = entry(item);
            if (entry.isEmpty()) {
                return StepResult.fail(item + " nicht im Rezeptbuch");
            }
            if (mc.player.isCreative()) {
                return StepResult.fail("Spieler ist im Kreativmodus");
            }
            mc.gameMode.handlePlaceRecipe(menu.containerId, entry.get().id(), false);
            ctx.state().put(PLACED, item);
            return StepResult.pending();
        }
        ItemStack result = menu.getResultSlot().getItem();
        if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(item)) {
            return StepResult.pass("Rezeptbuch legt die Zutaten ein, Ergebnis " + result.getHoverName().getString());
        }
        return ticks > 100 ? StepResult.fail("Ergebnisfeld zeigt " + BuiltInRegistries.ITEM.getKey(result.getItem()) + " statt " + item)
                : StepResult.pending();
    }

    private static StepResult take(int ticks, Identifier item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !(mc.player.containerMenu instanceof CraftingMenu menu)) {
            return StepResult.fail("Werkbank geschlossen");
        }
        if (ticks == 0) {
            mc.gameMode.handleContainerInput(menu.containerId, CraftingMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);
            return StepResult.pending();
        }
        int count = count(mc.player.getInventory(), item);
        if (count >= 1) {
            return StepResult.pass(count + "× " + item + " im Inventar");
        }
        return ticks > 100 ? StepResult.fail(item + " nicht im Inventar") : StepResult.pending();
    }

    static int count(Inventory inventory, Identifier item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void checkInventory(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Inventory inventory = player.getInventory();
        List<String> problems = new ArrayList<>();
        if (player.isCreative()) {
            problems.add("Spieler im Kreativmodus");
        }
        if (count(inventory, PlannerItems.FLIGHT_PLANNER_ID) != 1) {
            problems.add(count(inventory, PlannerItems.FLIGHT_PLANNER_ID) + " Flugplaner statt 1");
        }
        if (count(inventory, PadBlocks.LANDING_PAD_ID) != 1) {
            problems.add(count(inventory, PadBlocks.LANDING_PAD_ID) + " Landeplätze statt 1");
        }
        for (String used : List.of("minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:map", "propwash:circuit_board", "minecraft:gray_carpet",
                "minecraft:white_dye", "minecraft:smooth_stone_slab")) {
            int left = count(inventory, Identifier.parse(used));
            if (left != 0) {
                problems.add(left + "× " + used + " übrig");
            }
        }
        for (Identifier id : List.of(PlannerItems.FLIGHT_PLANNER_ID, PadBlocks.LANDING_PAD_ID)) {
            if (!player.getRecipeBook().contains(recipe(id))) {
                problems.add(id + " am Server nicht im Rezeptbuch");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
    }
}
