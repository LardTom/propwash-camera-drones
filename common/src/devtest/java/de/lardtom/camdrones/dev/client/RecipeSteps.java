package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class RecipeSteps {
    public static final String NAME = "rezepte: alle Rezepte im Rezeptbuch und herstellbar";
    private static final String RESULTS = "rezepte.results";
    private static final String COUNT = "rezepte.count";
    private static final String FIRST = "rezepte.first";
    private static final String STABLE = "rezepte.stable";
    private static final String STABLE_SINCE = "rezepte.stableSince";
    private static final TagKey<Item> PARTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("propwash", "parts"));

    private RecipeSteps() {
    }

    public static void register() {
        SelfTest.clientStep(2, NAME, 1600, Script.create()
                .server("Rezepte am Server prüfen", RecipeSteps::checkServer)
                .server("Teil von Propwash ins Inventar", RecipeSteps::unlock)
                .then("Rezeptbuch nach dem ersten Teil", RecipeSteps::settle)
                .server("freigeschaltete Teile herstellen", RecipeSteps::craftUnlocked)
                .then("Rezeptbuch am Client", RecipeSteps::checkClient));
    }

    private static List<RecipeHolder<?>> own(MinecraftServer server) {
        List<RecipeHolder<?>> out = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (CamDrones.MOD_ID.equals(holder.id().identifier().getNamespace())) {
                out.add(holder);
            }
        }
        out.sort((a, b) -> a.id().identifier().compareTo(b.id().identifier()));
        return out;
    }

    private static void checkServer(MinecraftServer server, Map<String, Object> state) {
        Set<Identifier> files = new TreeSet<>();
        server.getResourceManager().listResources("recipe", id -> CamDrones.MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith(".json"))
                .keySet().forEach(id -> files.add(Identifier.fromNamespaceAndPath(id.getNamespace(),
                        id.getPath().substring("recipe/".length(), id.getPath().length() - ".json".length()))));
        List<RecipeHolder<?>> recipes = own(server);
        Set<Identifier> loaded = new TreeSet<>();
        recipes.forEach(holder -> loaded.add(holder.id().identifier()));
        if (files.isEmpty()) {
            throw new IllegalStateException("keine Rezept-Dateien im Namensraum " + CamDrones.MOD_ID);
        }
        if (!loaded.equals(files)) {
            Set<Identifier> missing = new TreeSet<>(files);
            missing.removeAll(loaded);
            throw new IllegalStateException("nicht geladen: " + missing + " (Dateien " + files.size() + ", geladen " + loaded.size() + ")");
        }
        ServerPlayer player = Arena.player(server);
        Set<Identifier> unlocked = new HashSet<>();
        for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
            for (ResourceKey<Recipe<?>> key : advancement.value().rewards().recipes()) {
                unlocked.add(key.identifier());
            }
        }
        List<String> problems = new ArrayList<>();
        Set<Item> results = new HashSet<>();
        for (RecipeHolder<?> holder : recipes) {
            Identifier id = holder.id().identifier();
            if (!unlocked.contains(id)) {
                problems.add(id + " ohne Fortschritt zum Freischalten");
            }
            if (!(holder.value() instanceof CraftingRecipe crafting)) {
                problems.add(id + " ist kein Werkbank-Rezept: " + holder.value().getType());
                continue;
            }
            Optional<CraftingInput> input = input(crafting, SlotDisplayContext.fromLevel(player.level()));
            if (input.isEmpty()) {
                problems.add(id + " hat eine leere Zutat");
                continue;
            }
            if (!crafting.matches(input.get(), player.level())) {
                problems.add(id + " passt nicht zu den eigenen Zutaten");
                continue;
            }
            ItemStack result = crafting.assemble(input.get());
            Identifier item = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (result.isEmpty() || !CamDrones.MOD_ID.equals(item.getNamespace())) {
                problems.add(id + " liefert " + item);
            }
            results.add(result.getItem());
            Optional<? extends RecipeHolder<?>> chosen = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input.get(), player.level());
            if (chosen.isEmpty() || !chosen.get().id().equals(holder.id())) {
                problems.add(id + ": dieselben Zutaten ergeben " + chosen.map(h -> h.id().identifier().toString()).orElse("nichts"));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        state.put(RESULTS, results);
        state.put(COUNT, recipes.size());
    }

    static Optional<CraftingInput> input(CraftingRecipe recipe, ContextMap context) {
        List<ItemStack> items = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
                if (ingredient.isEmpty()) {
                    items.add(ItemStack.EMPTY);
                    continue;
                }
                Optional<ItemStack> stack = first(ingredient.get(), context);
                if (stack.isEmpty()) {
                    return Optional.empty();
                }
                items.add(stack.get());
            }
            return Optional.of(CraftingInput.of(shaped.getWidth(), shaped.getHeight(), items));
        }
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            Optional<ItemStack> stack = first(ingredient, context);
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            items.add(stack.get());
        }
        int width = Math.min(3, Math.max(1, items.size()));
        int height = (items.size() + 2) / 3;
        while (items.size() < width * height) {
            items.add(ItemStack.EMPTY);
        }
        return Optional.of(CraftingInput.of(width, height, items));
    }

    private static Optional<ItemStack> first(Ingredient ingredient, ContextMap context) {
        ItemStack stack = ingredient.display().resolveForFirstStack(context);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.copyWithCount(1));
    }

    private static void unlock(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Iterator<Holder<Item>> parts = BuiltInRegistries.ITEM.getTagOrEmpty(PARTS).iterator();
        if (!parts.hasNext()) {
            throw new IllegalStateException("Tag #propwash:parts ist leer");
        }
        player.getInventory().add(new ItemStack(parts.next().value()));
        player.inventoryMenu.broadcastChanges();
    }

    private static Set<Item> book(Minecraft mc) {
        Set<Item> found = new HashSet<>();
        if (mc.player == null || mc.level == null) {
            return found;
        }
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                for (ItemStack result : entry.resultItems(context)) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(result.getItem());
                    if (CamDrones.MOD_ID.equals(id.getNamespace())) {
                        found.add(result.getItem());
                    }
                }
            }
        }
        return found;
    }

    private static StepResult settle(SelfTest.StepContext ctx, int ticks) {
        Set<Item> found = book(Minecraft.getInstance());
        int last = (int) ctx.state().getOrDefault(STABLE, -1);
        if (found.size() != last) {
            ctx.state().put(STABLE, found.size());
            ctx.state().put(STABLE_SINCE, ticks);
            return ticks > 400 ? StepResult.fail("Rezeptbuch wird nicht fertig") : StepResult.pending();
        }
        if (found.isEmpty() && ticks < 200 || ticks - (int) ctx.state().get(STABLE_SINCE) < 40) {
            return StepResult.pending();
        }
        if (found.isEmpty()) {
            return StepResult.fail("kein Rezept nach dem ersten Teil im Rezeptbuch");
        }
        ctx.state().put(FIRST, found);
        return StepResult.pass(found.size() + " Rezepte mit dem ersten Teil von Propwash freigeschaltet");
    }

    @SuppressWarnings("unchecked")
    private static void craftUnlocked(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        for (Item item : (Set<Item>) state.get(FIRST)) {
            player.getInventory().add(new ItemStack(item));
        }
        player.inventoryMenu.broadcastChanges();
    }

    @SuppressWarnings("unchecked")
    private static StepResult checkClient(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.fail("kein Spieler");
        }
        Set<Item> wanted = (Set<Item>) ctx.state().get(RESULTS);
        Set<Item> found = new HashSet<>();
        int craftable = 0;
        List<String> problems = new ArrayList<>();
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                for (ItemStack result : entry.resultItems(context)) {
                    if (!wanted.contains(result.getItem())) {
                        continue;
                    }
                    found.add(result.getItem());
                    StackedItemContents contents = new StackedItemContents();
                    entry.craftingRequirements().orElse(List.of())
                            .forEach(ingredient -> first(ingredient, context).ifPresent(contents::accountSimpleStack));
                    if (entry.canCraft(contents)) {
                        craftable++;
                    } else {
                        problems.add(BuiltInRegistries.ITEM.getKey(result.getItem()) + " im Rezeptbuch nicht herstellbar");
                    }
                }
            }
        }
        if (found.size() < wanted.size()) {
            if (ticks < 200) {
                return StepResult.pending();
            }
            List<String> missing = new ArrayList<>();
            for (Item item : wanted) {
                if (!found.contains(item)) {
                    missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
                }
            }
            return StepResult.fail("nicht im Rezeptbuch: " + missing);
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass(ctx.state().get(COUNT) + " Rezepte geladen, eindeutig und herstellbar, alle " + found.size()
                + " Ergebnisse nach dem Herstellen der freigeschalteten Teile im Rezeptbuch (" + craftable + " Einträge herstellbar)");
    }
}
