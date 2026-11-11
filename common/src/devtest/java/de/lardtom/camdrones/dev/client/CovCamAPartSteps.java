package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.model.GimbalOptics;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.pad.PadBlocks;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.part.CamTab;
import de.lardtom.camdrones.part.LensItems;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.registry.ExtensionTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CovCamAPartSteps {
    public static final String TAB = "teile: Kreativ-Tab vollständig";
    public static final String CHECK_BUTTON = "teile: Werkbank-Knopf „Kamera-Drohne prüfen“";
    public static final String SMART_BATTERY = "teile: Smart-Akku-Anzeige in der Hand und an der Drohne";
    public static final String PAINT = "teile: Lack je Preset in der Werkbank";
    public static final String LENS_BAY = "teile: Objektiv-Fach nur am Kino-Gimbal";
    public static final Identifier PROBE_ID = CamDrones.id("covcama_probe");
    public static final int MAGENTA_LIMIT = 24;
    public static final int REPAINT_FRAME = 0xC0392B;
    public static final int REPAINT_MOTORS = 0x2E86C1;
    public static final int REPAINT_ACCESSORIES = 0xF1C40F;
    private static final String SHOT = "covcama.shot";
    private static final String NOTE = "covcama.note";
    private static final String IMPORT = "covcama.import";
    private static final String FOV = "covcama.fov";
    private static final String HALF = "covcama.halfDrone";
    private static final String MORE = "covcama.moreMenu";
    private static final int STEP_TICKS = 20 * 180;
    private static final float[] LED_CHARGES = {1.0F, 0.75F, 0.5F, 0.25F, 0.0F};
    private static final String[] LED_SUFFIXES = {"", "_led3", "_led2", "_led1", "_led0"};
    private static final List<ItemDisplayContext> LED_CONTEXTS = List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    private static volatile @Nullable WorkbenchContext bench;

    private CovCamAPartSteps() {
    }

    static void install() {
        PropwashClientApi.registerWorkbenchExtension(new Probe());
    }

    static void tab() {
        SelfTest.clientStep(301, TAB, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .then("Inhalt des Tabs gegen die Soll-Liste", (ctx, ticks) -> tabContents(ctx))
                .then("Kreativ-Inventar mit dem Tab öffnen", CovCamAPartSteps::openTab)
                .then("Bild auf lila-schwarze Texturen prüfen", CovCamAPartSteps::magenta)
                .append(Shots.photos(CovCamAKit.PHOTO + "creative_tab"))
                .run("Inventar schließen", () -> Minecraft.getInstance().gui.setScreen(null)));
    }

    static void checkButton() {
        SelfTest.clientStep(302, CHECK_BUTTON, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Werkbank aufstellen", (server, state) -> CovCamAKit.placeBench(server))
                .server("Tour einlegen", (server, state) -> requireBuilt(server, CamPresets.TOUR.build()))
                .await("Werkbank offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>)
                .then("Knopf des Add-ons", CovCamAPartSteps::checkButtonPresent)
                .append(Shots.photos(CovCamAKit.PHOTO + "check_button"))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Knopf auf Deutsch erreichbar", CovCamAPartSteps::germanReachable)
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .await("Werkbank wieder offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>)
                .then("Tour prüfen", (ctx, ticks) -> pressCheck(ctx, ticks, null))
                .append(Shots.photos(CovCamAKit.PHOTO + "check_tour"))
                .run("Prüffenster schließen", CovCamAPartSteps::closeCheck)
                .await("Werkbank wieder offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>)
                .server("ohne Gimbal einlegen", (server, state) -> {
                    CovCamAKit.emptyBench(server);
                    requireBuilt(server, CamPresets.TOUR.build().withoutAccessory(AccessorySlot.FRONT));
                })
                .then("Bau ohne Gimbal prüfen", (ctx, ticks) -> pressCheck(ctx, ticks, CamPresets.TOUR.gimbal()))
                .append(Shots.photos(CovCamAKit.PHOTO + "check_no_gimbal"))
                .run("Prüffenster schließen", CovCamAPartSteps::closeCheck)
                .server("Werkbank schließen", (server, state) -> CovCamAKit.closeBench(server)));
    }

    static void smartBattery() {
        SelfTest.clientStep(303, SMART_BATTERY, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Werkbank aufstellen", (server, state) -> CovCamAKit.placeBench(server))
                .server("Tour mit vollem und halbem Akku bauen und aufstellen", CovCamAPartSteps::twoTours)
                .await("Drohnen am Client", 100, () -> Arena.clientDrones(Minecraft.getInstance()).size() >= 2)
                .sleep(20)
                .then("Bild an der Drohne", (ctx, ticks) -> CovCamAKit.awaitShot(ctx, ticks, SHOT, CovCamAKit.PHOTO + "smart_battery_drones"))
                .append(Shots.photos(CovCamAKit.PHOTO + "smart_battery_drones"))
                .server("Smart-Akku 100 % in die Hand", (server, state) -> hand(server, 1.0F))
                .sleep(20)
                .append(Shots.photos(CovCamAKit.PHOTO + "smart_battery_hand_100"))
                .server("Smart-Akku 50 % in die Hand", (server, state) -> hand(server, 0.5F))
                .sleep(20)
                .append(Shots.photos(CovCamAKit.PHOTO + "smart_battery_hand_50"))
                .check("Ladestand der Items", CovCamAPartSteps::batteryItems));
    }

    static void paint() {
        Script script = Script.create()
                .append(Shots.photoWindow())
                .run("Werkbank-Kontext zurücksetzen", () -> bench = null)
                .server("Werkbank aufstellen und öffnen", (server, state) -> {
                    CovCamAKit.placeBench(server);
                    CovCamAKit.openBench(server);
                })
                .await("Werkbank offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> && bench != null);
        for (CamPresets.Entry entry : CamPresets.ALL) {
            script.then(entry.path() + " mit Standardlack laden", (ctx, ticks) -> load(ctx, ticks, Pilot.preset(entry).data()))
                    .append(Shots.photos(CovCamAKit.PHOTO + "paint_" + entry.path()))
                    .then(entry.path() + " umlackieren", (ctx, ticks) -> load(ctx, ticks, repaint(Pilot.preset(entry).data())))
                    .append(Shots.photos(CovCamAKit.PHOTO + "paint_" + entry.path() + "_repaint"));
        }
        SelfTest.clientStep(304, PAINT, STEP_TICKS, script.server("Werkbank schließen", (server, state) -> CovCamAKit.closeBench(server)));
    }

    static void lensBay() {
        Script script = Script.create().append(Shots.photoWindow());
        int[] lanes = {-12, -4, 4};
        int i = 0;
        for (CamPresets.Entry entry : List.of(CamPresets.CINE, CamPresets.TOUR, CamPresets.COMPACT)) {
            int dx = lanes[i++];
            script.server(entry.path() + " aufstellen", (server, state) -> {
                        CovCamAKit.spawnPreset(server, state, entry, Arena.ground(dx, -20), 0.0F);
                        ServerPlayer player = Arena.player(server);
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        Vec3 stand = Vec3.atBottomCenterOf(Arena.ground(dx, -20).above()).add(0.0, 0.0, -2.0);
                        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 30.0F, true);
                    })
                    .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                            : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                    .then("Sichtfeld vorher", (ctx, ticks) -> {
                        ctx.state().put(FOV, fov(ctx.state()));
                        return StepResult.pass();
                    })
                    .then("Schleichen + Rechtsklick: Wartung", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, CamFlights.clientDrone(ctx.state()).orElseThrow(),
                            true))
                    .then("Zubehör-Fenster und Objektiv 50 mm bei " + entry.path(), (ctx, ticks) -> lens(ctx, ticks, entry))
                    .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                    .server("Fenster am Server schließen", (server, state) -> Arena.player(server).closeContainer())
                    .sleep(5);
        }
        SelfTest.clientStep(305, LENS_BAY, STEP_TICKS, script);
    }

    private static StepResult tabContents(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Optional<ExtensionTab> tab = CamTab.tab();
        if (tab.isEmpty() || !tab.get().registered()) {
            return StepResult.fail("Kreativ-Tab des Add-ons nicht angemeldet");
        }
        List<ItemStack> contents = tab.get().contents();
        List<String> problems = new ArrayList<>();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            BuildSpec spec = Pilot.preset(entry).data().build();
            boolean found = contents.stream().anyMatch(stack -> PropwashApi.builds().read(stack).map(d -> d.build().equals(spec)).orElse(false));
            if (!found) {
                problems.add("Preset " + entry.path() + " fehlt");
            }
            for (String lang : List.of("en_us", "de_de")) {
                if (PropwashApi.text(lang, entry.translationKey()).isEmpty()) {
                    problems.add("Preset " + entry.path() + " ohne Namen in " + lang);
                }
            }
        }
        int parts = 0;
        for (PartDefinition definition : PropwashApi.parts().all()) {
            if (!CamDrones.MOD_ID.equals(definition.id().getNamespace())) {
                continue;
            }
            parts++;
            boolean found = contents.stream().anyMatch(stack -> PropwashApi.partItems().partOf(stack).filter(definition.id()::equals).isPresent());
            if (!found) {
                problems.add("Teil " + definition.id() + " fehlt");
            }
        }
        for (LensItems.Lens lens : LensItems.ALL) {
            Optional<Item> item = LensItems.item(lens);
            if (item.isEmpty() || contents.stream().noneMatch(stack -> stack.is(item.get()))) {
                problems.add("Objektiv " + lens.path() + " fehlt");
            }
        }
        Optional<Item> planner = PlannerItems.flightPlanner();
        if (planner.isEmpty() || contents.stream().noneMatch(stack -> stack.is(planner.get()))) {
            problems.add("Flugplaner fehlt");
        }
        Optional<Item> pad = PadBlocks.landingPadItem();
        if (pad.isEmpty() || contents.stream().noneMatch(stack -> stack.is(pad.get()))) {
            problems.add("Landeplatz fehlt");
        }
        int expected = CamPresets.ALL.size() + parts + LensItems.ALL.size() + 2;
        List<String> extra = new ArrayList<>();
        for (ItemStack stack : contents) {
            Identifier model = stack.get(DataComponents.ITEM_MODEL);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (model == null) {
                problems.add(id + " ohne Item-Modell");
            } else if (mc.getModelManager().getItemModel(model) instanceof MissingItemModel) {
                problems.add(id + " mit Missing-Modell (" + model + ")");
            }
            Component name = stack.getHoverName();
            if (name.getContents() instanceof TranslatableContents translatable) {
                for (String lang : List.of("en_us", "de_de")) {
                    if (PropwashApi.text(lang, translatable.getKey()).isEmpty()) {
                        problems.add(id + " ohne Namen in " + lang + " (" + translatable.getKey() + ")");
                    }
                }
            } else if (PropwashApi.builds().read(stack).isEmpty()) {
                problems.add(id + " mit festem Namen \"" + name.getString() + "\" statt Übersetzung");
            }
        }
        if (contents.size() != expected) {
            extra.add(contents.size() + " Einträge statt " + expected);
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass(contents.size() + " Einträge: 3 Presets, " + parts + " Teile, 3 Objektive, Flugplaner, Landeplatz, alle mit Item-Modell und Namen EN/DE"
                + (extra.isEmpty() ? "" : " (" + String.join(", ", extra) + ")"));
    }

    private static StepResult openTab(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        Identifier id = CamTab.tab().orElseThrow().tabId();
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(id).orElse(null);
        if (tab == null) {
            return StepResult.fail("Kreativ-Tab " + id + " nicht in der Registry");
        }
        try {
            Field selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            selected.setAccessible(true);
            if (ticks == 0) {
                selected.set(null, tab);
                mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get()));
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof CreativeModeInventoryScreen creative)) {
                return ticks > 40 ? StepResult.fail("Kreativ-Inventar öffnet nicht: " + SelfTestRunner.screenName(mc)) : StepResult.pending();
            }
            if (ticks % 10 == 5 && selected.get(null) != tab) {
                page(creative, tab);
                Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
                select.setAccessible(true);
                select.invoke(creative, tab);
            }
            boolean shown = selected.get(null) == tab && creative.getMenu().items.stream()
                    .anyMatch(stack -> CamDrones.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()));
            if (shown && ticks >= 20) {
                return StepResult.pass("Tab des Add-ons gewählt, " + tab.getDisplayItems().size() + " Einträge");
            }
            return ticks > 120 ? StepResult.fail("Tab des Add-ons lässt sich nicht wählen (gewählt " + selected.get(null) + ")") : StepResult.pending();
        } catch (ReflectiveOperationException e) {
            return StepResult.fail("Tab nicht wählbar: " + e);
        }
    }

    private static void page(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        try {
            Method getPage = screen.getClass().getMethod("getPage", CreativeModeTab.class);
            Method switchToPage = screen.getClass().getMethod("switchToPage", int.class);
            switchToPage.invoke(screen, getPage.invoke(screen, tab));
        } catch (ReflectiveOperationException | RuntimeException e) {
            CamDrones.LOG.info("[Kamera-Selbsttest] Kreativ-Tab ohne Seitenwechsel: {}", e.toString());
        }
    }

    private static StepResult magenta(SelfTest.StepContext ctx, int ticks) {
        StepResult shot = CovCamAKit.awaitShot(ctx, ticks, SHOT, CovCamAKit.PHOTO + "creative_tab_check");
        if (!shot.done() || shot.failed()) {
            return shot;
        }
        Path file = CovCamAKit.out().resolve(CovCamAKit.PHOTO + "creative_tab_check.png");
        try {
            int pixels = CovCamAKit.magentaPixels(file, 0.0, 1.0);
            return pixels > MAGENTA_LIMIT ? StepResult.fail(pixels + " lila Pixel (Missing-Textur) im Bild des Tabs " + file)
                    : StepResult.pass(pixels + " lila Pixel im Bild des Tabs (Grenze " + MAGENTA_LIMIT + ")");
        } catch (IOException e) {
            return StepResult.fail("Bild nicht lesbar: " + e);
        }
    }

    private static void requireBuilt(MinecraftServer server, BuildSpec spec) {
        Optional<ItemStack> built = CovCamAKit.buildInBench(server, spec, WorkbenchSteps.BATTERY_CHARGE);
        if (built.isEmpty()) {
            throw new IllegalStateException("Werkbank baut " + spec + " nicht");
        }
    }

    private static Optional<WorkbenchExtension> addonExtension() {
        return PropwashClientApi.workbenchExtensions().stream()
                .filter(e -> CamDrones.MOD_ID.equals(e.id().getNamespace()) && !PROBE_ID.equals(e.id()))
                .findFirst();
    }

    private static Optional<AbstractWidget> checkWidget(Screen screen) {
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            String text = widget.getMessage().getString().toLowerCase(Locale.ROOT);
            if (widget.visible && (text.contains("kamera") || text.contains("camera"))) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    private static StepResult checkButtonPresent(SelfTest.StepContext ctx, int ticks) {
        if (ticks < 10) {
            return StepResult.pending();
        }
        Optional<WorkbenchExtension> extension = addonExtension();
        if (extension.isEmpty()) {
            return StepResult.fail("keine Werkbank-Erweiterung des Add-ons (WorkbenchExtension im Namensraum camdrones) angemeldet, Werkbank-Knöpfe: "
                    + CovCamAKit.labels(Minecraft.getInstance().gui.screen()));
        }
        Optional<AbstractWidget> button = checkWidget(Minecraft.getInstance().gui.screen());
        return button.isPresent() ? StepResult.pass("Knopf \"" + button.get().getMessage().getString() + "\" von " + extension.get().id())
                : StepResult.fail("Erweiterung " + extension.get().id() + " ohne Knopf „Kamera-Drohne prüfen“");
    }

    private static StepResult germanReachable(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (ticks < 10 || screen == null) {
            return ticks > 100 ? StepResult.fail("Werkbank nach dem Sprachwechsel geschlossen") : StepResult.pending();
        }
        if (screen instanceof AbstractContainerScreen<?>) {
            Optional<AbstractWidget> direct = checkWidget(screen);
            if (direct.isPresent()) {
                return StepResult.pass("Deutsch: Knopf \"" + direct.get().getMessage().getString() + "\" im Kopf der Werkbank");
            }
            if (ctx.state().containsKey(MORE)) {
                return ticks > 100 ? StepResult.fail("Deutsch: Menü ⋯ öffnet nicht") : StepResult.pending();
            }
            Optional<AbstractWidget> more = CovCamAKit.widget(screen, I18n.get("propwash.parts.workbench.more"));
            if (more.isEmpty()) {
                return StepResult.fail("Deutsch: weder Knopf „Kamera-Drohne prüfen“ noch ⋯ im Kopf der Werkbank, Knöpfe: " + CovCamAKit.labels(screen));
            }
            ctx.state().put(MORE, Boolean.TRUE);
            CovCamAKit.click(screen, more.get());
            return StepResult.pending();
        }
        List<String> entries = CovCamAKit.labels(screen);
        Optional<AbstractWidget> entry = checkWidget(screen);
        screen.onClose();
        return entry.isPresent() ? StepResult.pass("Deutsch: Kopf zu schmal, Eintrag \"" + entry.get().getMessage().getString() + "\" unter ⋯ " + entries)
                : StepResult.fail("Deutsch: Eintrag „Kamera-Drohne prüfen“ fehlt unter ⋯, Einträge " + entries);
    }

    private static StepResult pressCheck(SelfTest.StepContext ctx, int ticks, @Nullable String missing) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Werkbank geschlossen");
        }
        if (ticks == 5) {
            Optional<AbstractWidget> button = checkWidget(screen);
            if (button.isEmpty()) {
                return StepResult.fail("Knopf „Kamera-Drohne prüfen“ fehlt");
            }
            CovCamAKit.clearOverlay();
            CovCamAKit.click(screen, button.get());
            return StepResult.pending();
        }
        if (ticks < 20) {
            return StepResult.pending();
        }
        Screen now = mc.gui.screen();
        List<String> texts = new ArrayList<>(now == null ? List.of() : LayoutCheck.capture(mc, now).values());
        texts.add(CovCamAKit.overlayText());
        String all = String.join(" | ", texts).toLowerCase(Locale.ROOT);
        if (missing == null) {
            boolean names = CamPresets.ALL.stream().anyMatch(e -> all.contains(partName(e.gimbal())));
            return names ? StepResult.fail("vollständiger Bau meldet ein fehlendes Teil: " + texts) : StepResult.pass("vollständiger Bau: " + texts);
        }
        String name = partName(missing);
        return all.contains(name) || all.contains("gimbal") ? StepResult.pass("fehlendes Teil genannt: " + texts)
                : ticks > 60 ? StepResult.fail("fehlendes Gimbal nicht genannt: " + texts) : StepResult.pending();
    }

    private static String partName(String path) {
        return PropwashApi.partItems().stack(CamPresets.part(path)).getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static void closeCheck() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null && !(screen instanceof AbstractContainerScreen<?>)) {
            screen.onClose();
        }
    }

    private static void twoTours(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        BuildSpec spec = CamPresets.TOUR.build();
        ItemStack full = CovCamAKit.buildInBench(server, spec, 1.0F).orElseThrow(() -> new IllegalStateException("Tour mit vollem Akku nicht gebaut"));
        CovCamAKit.emptyBench(server);
        ItemStack half = CovCamAKit.buildInBench(server, spec, 0.5F).orElseThrow(() -> new IllegalStateException("Tour mit halbem Akku nicht gebaut"));
        CovCamAKit.closeBench(server);
        Entity a = CovCamAKit.place(player, full, Arena.ground(-1, 10), 0.0F);
        Entity b = CovCamAKit.place(player, half, Arena.ground(1, 10), 0.0F);
        state.put(Pilot.DRONE, a.getUUID());
        state.put(HALF, b.getUUID());
        Vec3 stand = Vec3.atBottomCenterOf(Arena.ground(0, 10).above()).add(0.0, -0.3, -1.6);
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 48.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void hand(MinecraftServer server, float charge) {
        ServerPlayer player = Arena.player(server);
        Vec3 away = Arena.spawn().add(0.0, 0.0, -24.0);
        player.teleportTo(player.level(), away.x, away.y, away.z, Set.<Relative>of(), 180.0F, 35.0F, true);
        Identifier battery = CamPresets.part(CamPresets.TOUR.battery());
        player.setItemInHand(InteractionHand.MAIN_HAND, PropwashApi.partItems().stack(battery, charge));
        player.getInventory().setItem(1, PropwashApi.partItems().stack(battery, charge > 0.75F ? 0.5F : 1.0F));
    }

    private static StepResult batteryItems() {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (CamPresets.Entry entry : CamPresets.ALL) {
            Identifier battery = CamPresets.part(entry.battery());
            for (int i = 0; i < LED_CHARGES.length; i++) {
                ItemStack stack = PropwashApi.partItems().stack(battery, LED_CHARGES[i]);
                Identifier expected = CamDrones.id("item/part/battery/" + entry.battery() + LED_SUFFIXES[i]);
                for (ItemDisplayContext context : LED_CONTEXTS) {
                    ItemStackRenderState state = new ItemStackRenderState();
                    minecraft.getItemModelResolver().updateForTopItem(state, stack, context, minecraft.level, minecraft.player, 0);
                    Identifier shown = state.isEmpty() ? null : state.pickParticleMaterial(RandomSource.create(0L)).sprite().contents().name();
                    checked++;
                    if (!expected.equals(shown)) {
                        wrong.add(entry.battery() + " " + Math.round(LED_CHARGES[i] * 100.0F) + " % " + context.getSerializedName()
                                + ": " + shown + " statt " + expected);
                    }
                }
            }
        }
        if (!wrong.isEmpty()) {
            return StepResult.fail("LED-Punkte folgen dem Ladestand nicht: " + String.join("; ", wrong));
        }
        return StepResult.pass("LED-Punkte der Smart-Akkus folgen dem Ladestand: " + checked
                + " Item-Ansichten (100/75/50/25/0 % je Akku, GUI und Hand) mit dem passenden Modell; Bild an der Drohne zeigt den Bau");
    }

    @SuppressWarnings("unchecked")
    private static StepResult load(SelfTest.StepContext ctx, int ticks, DroneData data) {
        WorkbenchContext context = bench;
        if (context == null) {
            return StepResult.fail("Werkbank-Kontext fehlt");
        }
        Object pending = ctx.state().get(IMPORT);
        if (pending == null) {
            ctx.state().put(IMPORT, context.importIntoWorkbench(data, EnumSet.of(ImportLayer.PARTS, ImportLayer.PAINT), Set.of()));
            return StepResult.pending();
        }
        CompletableFuture<ImportResult> future = (CompletableFuture<ImportResult>) pending;
        if (!future.isDone()) {
            return ticks > 200 ? StepResult.fail("Laden in die Werkbank hängt") : StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            ctx.state().remove(IMPORT);
            return StepResult.fail("Laden: " + future.exceptionNow());
        }
        ImportResult result = future.join();
        if (!result.status().success()) {
            ctx.state().remove(IMPORT);
            return StepResult.fail("Laden abgelehnt: " + result.status() + " " + result.missingParts() + " " + result.missingDyes());
        }
        Optional<DroneData> shown = context.result();
        if (shown.isEmpty() || !shown.get().paint().equals(data.paint())) {
            if (ticks < 60) {
                return StepResult.pending();
            }
            ctx.state().remove(IMPORT);
            return StepResult.fail("Werkbank zeigt Lack " + shown.map(d -> d.paint().colors().toString()).orElse("keinen") + " statt "
                    + data.paint().colors());
        }
        if (ticks < 30) {
            return StepResult.pending();
        }
        ctx.state().remove(IMPORT);
        return StepResult.pass("Werkbank zeigt Lack " + hex(data.paint()));
    }

    static DroneData repaint(DroneData standard) {
        return standard.withPaint(standard.paint().with("frame", REPAINT_FRAME).with("motors", REPAINT_MOTORS).with("accessories", REPAINT_ACCESSORIES));
    }

    private static String hex(PaintSpec paint) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : paint.colors().entrySet()) {
            out.add(e.getKey() + "=#" + String.format(Locale.ROOT, "%06X", e.getValue()));
        }
        return String.join(", ", out);
    }

    private static double fov(Map<String, Object> state) {
        return CamFlights.clientDrone(state).map(d -> GimbalOptics.fov(GimbalScenes.optics(d), 0.0)).orElse(Double.NaN);
    }

    private static StepResult lens(SelfTest.StepContext ctx, int ticks, CamPresets.Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        String label = I18n.get("propwash.accessory.service.button");
        boolean cine = entry == CamPresets.CINE;
        int stage = (int) ctx.state().getOrDefault("covcama." + entry.path() + ".lensStage", 0);
        switch (stage) {
            case 0 -> {
                if (screen == null) {
                    return ticks > 100 ? StepResult.fail("Wartungsmenü öffnet nicht") : StepResult.pending();
                }
                if (ticks < 10) {
                    return StepResult.pending();
                }
                Optional<AbstractWidget> button = CovCamAKit.widget(screen, label);
                if (!cine) {
                    ctx.state().put("covcama." + entry.path() + ".lensStage", 3);
                    ctx.state().put("covcama." + entry.path() + ".lensButton", button.isPresent() && button.get().active);
                    ctx.state().put("covcama." + entry.path() + ".lensLabels", CovCamAKit.labels(screen));
                    return StepResult.pending();
                }
                if (button.isEmpty() || !button.get().active) {
                    return StepResult.fail("Kino: Knopf \"" + label + "\" fehlt im Wartungsmenü, Knöpfe " + CovCamAKit.labels(screen));
                }
                CovCamAKit.click(screen, button.get());
                ctx.state().put("covcama." + entry.path() + ".lensStage", 1);
            }
            case 1 -> {
                if (!(screen instanceof AbstractContainerScreen<?>)) {
                    return ticks > 120 ? StepResult.fail("Kino: Zubehör-Fenster öffnet nicht (" + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
                }
                ctx.state().put("covcama." + entry.path() + ".lensStage", 2);
                ctx.onServer(server -> ctx.state().put("covcama." + entry.path() + ".lensInserted", insertLens(server)));
            }
            case 2 -> {
                Object inserted = ctx.state().get("covcama." + entry.path() + ".lensInserted");
                if (inserted == null) {
                    return ticks > 200 ? StepResult.fail("Server legt das Objektiv nicht ein") : StepResult.pending();
                }
                if (!(boolean) inserted) {
                    return StepResult.fail("Kino-Gimbal nimmt das Objektiv 50 mm nicht an");
                }
                double before = (double) ctx.state().get(FOV);
                double after = fov(ctx.state());
                if (!(after < before - 10.0)) {
                    return ticks > 160 ? StepResult.fail("Sichtfeld bleibt " + CovCamAKit.format(after) + "° (vorher " + CovCamAKit.format(before) + "°)")
                            : StepResult.pending();
                }
                StepResult shots = Shots.enabled() ? CovCamAKit.awaitShot(ctx, ticks, SHOT, CovCamAKit.PHOTO + "lens_bay_cine") : StepResult.pass();
                if (!shots.done()) {
                    return shots;
                }
                ctx.state().remove(SHOT);
                return StepResult.pass("Kino: Zubehör-Fenster nimmt Objektiv 50 mm, Sichtfeld " + CovCamAKit.format(before) + "° → " + CovCamAKit.format(after)
                        + "°");
            }
            case 3 -> {
                Object bay = ctx.state().get("covcama." + entry.path() + ".lensBay");
                if (bay == null) {
                    ctx.state().put("covcama." + entry.path() + ".lensBay", "");
                    ctx.onServer(server -> ctx.state().put("covcama." + entry.path() + ".lensBay", noBay(server, ctx.state())));
                    return StepResult.pending();
                }
                if ("".equals(bay)) {
                    return ticks > 200 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
                }
                StepResult shots = Shots.enabled() ? CovCamAKit.awaitShot(ctx, ticks, SHOT, CovCamAKit.PHOTO + "lens_bay_" + entry.path()) : StepResult.pass();
                if (!shots.done()) {
                    return shots;
                }
                ctx.state().remove(SHOT);
                boolean button = (boolean) ctx.state().get("covcama." + entry.path() + ".lensButton");
                if (!"none".equals(bay)) {
                    return StepResult.fail(entry.path() + ": Objektiv-Fach vorhanden (" + bay + ")");
                }
                double before = (double) ctx.state().get(FOV);
                double after = fov(ctx.state());
                if (Math.abs(after - before) > 0.5) {
                    return StepResult.fail(entry.path() + ": Sichtfeld ändert sich " + CovCamAKit.format(before) + "° → " + CovCamAKit.format(after) + "°");
                }
                return StepResult.pass(entry.path() + ": kein Objektiv-Fach (Knopf \"" + label + "\" " + (button ? "da, Fach leer" : "nicht da")
                        + ", Knöpfe " + ctx.state().get("covcama." + entry.path() + ".lensLabels") + "), Sichtfeld bleibt " + CovCamAKit.format(after) + "°");
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage);
            }
        }
        return StepResult.pending();
    }

    private static boolean insertLens(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack lens = new ItemStack(LensItems.item(LensItems.LENS_50).orElseThrow(() -> new IllegalStateException("Objektiv 50 mm fehlt")));
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && !slot.hasItem() && slot.mayPlace(lens)) {
                menu.setCarried(lens);
                menu.clicked(slot.index, 0, ContainerInput.PICKUP, player);
                boolean taken = menu.getCarried().isEmpty();
                menu.setCarried(ItemStack.EMPTY);
                menu.broadcastChanges();
                return taken;
            }
        }
        return false;
    }

    private static String noBay(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        Optional<AccessoryInventory> inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT);
        if (inventory.isEmpty() || inventory.get().size() == 0) {
            return "none";
        }
        ItemStack lens = new ItemStack(LensItems.item(LensItems.LENS_50).orElseThrow());
        Optional<Identifier> gimbal = PropwashApi.accessories().accessoryAt(drone, AccessorySlot.FRONT);
        return "Fach mit " + inventory.get().size() + " Platz an " + gimbal.map(Identifier::toString).orElse("?") + ", nimmt Objektiv "
                + gimbal.flatMap(id -> PropwashApi.accessories().behavior(id)).map(b -> b.accepts(0, lens)).orElse(false);
    }

    static final class Probe implements WorkbenchExtension {
        @Override
        public Identifier id() {
            return PROBE_ID;
        }

        @Override
        public int order() {
            return 100_000;
        }

        @Override
        public void init(WorkbenchContext context) {
            bench = context;
        }
    }
}
