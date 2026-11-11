package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CovCamBCompanionSteps {
    public static final String JMP = "zusammenspiel: Tour mit Akku, Motor und Prop aus Just More Parts bauen und fliegen";
    public static final String MOB_DEFENSE = "zusammenspiel: Mob Defense an der Halterung vorne";
    public static final String JMP_ID = "justmoreparts";
    public static final String MD_ID = "mobdefense";
    public static final Identifier JMP_MOTOR = Identifier.fromNamespaceAndPath(JMP_ID, "m23065_2020");
    public static final Identifier JMP_PROP = Identifier.fromNamespaceAndPath(JMP_ID, "p203_40_3b_gfn");
    public static final Identifier JMP_BATTERY = Identifier.fromNamespaceAndPath(JMP_ID, "b6s_3000_lion");
    public static final Identifier DISPENSER = Identifier.fromNamespaceAndPath(MD_ID, "dispenser_mount");
    public static final Identifier SPEAR = Identifier.fromNamespaceAndPath(MD_ID, "spear_mount");
    private static final String SPEC = "covcamb.companion.spec";
    private static final String NOTES = "covcamb.companion.notes";
    private static final int STEP_TICKS = 20 * 180;

    private CovCamBCompanionSteps() {
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    static BuildSpec jmpTour() {
        return CamPresets.TOUR.build().withPart(PartKind.MOTOR, JMP_MOTOR).withPart(PartKind.PROP, JMP_PROP).withPart(PartKind.BATTERY, JMP_BATTERY);
    }

    static void jmp() {
        if (!Services.PLATFORM.isModLoaded(JMP_ID)) {
            CamDrones.LOG.info("[Kamera-Selbsttest] {} nicht angemeldet: Just More Parts fehlt im mods-Ordner", JMP);
            return;
        }
        SelfTest.clientStep(551, JMP, STEP_TICKS, Script.create()
                .server("Werkbank", (server, state) -> CovCamAKit.placeBench(server))
                .then("Tour mit JMP-Teilen in der Werkbank bauen", (ctx, ticks) -> Script.server(ctx, ticks, (server, state) -> bench(server, state,
                        notes(ctx))))
                .server("Werkbank schließen", (server, state) -> CovCamAKit.closeBench(server))
                .append(CovCamBKit.goTo(60, 100))
                .append(Pilot.launch("tour mit JMP-Teilen", DroneData.of(jmpTour()).withPaint(CamPresets.TOUR.paint()), 60, 100, 0.0F))
                .append(Flight.takeoff(5.0))
                .then("kurz schweben", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    double height = Flight.height();
                    if (!(height > 3.0)) {
                        return StepResult.fail("Drohne sackt ab auf " + CovCamBKit.f(height) + " m");
                    }
                    if (ticks < 100) {
                        return StepResult.pending();
                    }
                    notes(ctx).add("fliegt mit JMP-Teilen und hält " + CovCamBKit.f(height) + " m");
                    return StepResult.pass(String.join("; ", notes(ctx)));
                }));
    }

    private static void bench(MinecraftServer server, Map<String, Object> state, List<String> notes) {
        BuildSpec spec = jmpTour();
        BuildCheck check = PropwashApi.builds().check(spec);
        if (!check.buildable()) {
            throw new IllegalStateException("Tour mit JMP-Teilen nicht baubar: " + check.problems() + check.unknownParts());
        }
        Optional<ItemStack> drone = CovCamAKit.buildInBench(server, spec, 1.0F);
        if (drone.isEmpty()) {
            throw new IllegalStateException("Werkbank liefert keine Tour mit JMP-Teilen");
        }
        BuildStats stats = PropwashApi.builds().analyze(spec).orElseThrow();
        BuildStats stock = PropwashApi.builds().analyze(CamPresets.TOUR.build()).orElseThrow();
        notes.add(String.format(Locale.GERMANY, "Werkbank baut die Tour mit %s, %s und %s: %.0f g statt %.0f g, Schweben %.1f statt %.1f min, %.0f statt %.0f km/h",
                JMP_MOTOR.getPath(), JMP_PROP.getPath(), JMP_BATTERY.getPath(), stats.massGrams(), stock.massGrams(), stats.hoverFlightTimeMin(),
                stock.hoverFlightTimeMin(), stats.topSpeedKmh(), stock.topSpeedKmh()));
    }

    static void mobDefense() {
        if (!Services.PLATFORM.isModLoaded(MD_ID)) {
            CamDrones.LOG.info("[Kamera-Selbsttest] {} nicht angemeldet: Mob Defense fehlt im mods-Ordner", MOB_DEFENSE);
            return;
        }
        SelfTest.clientStep(552, MOB_DEFENSE, STEP_TICKS, Script.create()
                .server("Werkbank", (server, state) -> CovCamAKit.placeBench(server))
                .then("Halterungen an der Tour", (ctx, ticks) -> Script.server(ctx, ticks, (server, state) -> mounts(server, notes(ctx))))
                .server("Werkbank schließen", (server, state) -> CovCamAKit.closeBench(server))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx)))));
    }

    private static List<Slot> accepting(AbstractContainerMenu menu, ItemStack stack) {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && !slot.hasItem() && slot.mayPlace(stack)) {
                out.add(slot);
            }
        }
        return out;
    }

    private static Optional<BuildSpec> built(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && PropwashApi.builds().isDrone(slot.getItem())) {
                return PropwashApi.builds().read(slot.getItem()).map(DroneData::build);
            }
        }
        return Optional.empty();
    }

    private static void mounts(MinecraftServer server, List<String> notes) {
        ServerPlayer player = Arena.player(server);
        BuildSpec tour = CamPresets.TOUR.build();
        if (CovCamAKit.buildInBench(server, tour, 1.0F).isEmpty()) {
            throw new IllegalStateException("Tour mit Gimbal lässt sich nicht bauen");
        }
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack dispenser = PropwashApi.partItems().stack(DISPENSER);
        ItemStack spear = PropwashApi.partItems().stack(SPEAR);
        if (dispenser.isEmpty() || spear.isEmpty()) {
            throw new IllegalStateException("Items von Mob Defense fehlen: " + DISPENSER + " " + SPEAR);
        }
        List<Slot> withGimbal = accepting(menu, dispenser);
        if (!withGimbal.isEmpty()) {
            throw new IllegalStateException("Dispenser-Halterung passt neben dem Gimbal in Platz " + withGimbal.getFirst().index);
        }
        notes.add("Tour mit Gimbal: kein Platz für die Dispenser-Halterung (vorn belegt)");
        Identifier gimbal = tour.accessory(AccessorySlot.FRONT).orElseThrow();
        Slot gimbalSlot = null;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && slot.hasItem() && !PropwashApi.builds().isDrone(slot.getItem())
                    && PropwashApi.partItems().partOf(slot.getItem()).filter(gimbal::equals).isPresent()) {
                gimbalSlot = slot;
            }
        }
        if (gimbalSlot == null) {
            throw new IllegalStateException("Gimbal nicht in der Werkbank gefunden");
        }
        menu.clicked(gimbalSlot.index, 0, ContainerInput.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
        List<Slot> free = accepting(menu, dispenser);
        if (free.isEmpty()) {
            throw new IllegalStateException("ohne Gimbal nimmt die Werkbank die Dispenser-Halterung nicht");
        }
        menu.setCarried(dispenser.copy());
        menu.clicked(free.getFirst().index, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            menu.setCarried(ItemStack.EMPTY);
            throw new IllegalStateException("Dispenser-Halterung bleibt in der Hand");
        }
        Optional<BuildSpec> result = built(menu);
        if (result.isEmpty() || !result.get().accessory(AccessorySlot.FRONT).filter(DISPENSER::equals).isPresent()) {
            throw new IllegalStateException("Ergebnis ohne Dispenser vorn: " + result);
        }
        notes.add("Gimbal raus, Dispenser-Halterung rein: Tour mit " + DISPENSER + " vorn");
        List<Slot> spearSlots = accepting(menu, spear);
        String spearNote = "kein Platz in der Werkbank";
        if (!spearSlots.isEmpty()) {
            Slot slot = spearSlots.getFirst();
            menu.setCarried(spear.copy());
            menu.clicked(slot.index, 0, ContainerInput.PICKUP, player);
            menu.setCarried(ItemStack.EMPTY);
            Optional<BuildSpec> withSpear = built(menu);
            boolean mounted = withSpear.isPresent() && withSpear.get().accessories().containsValue(SPEAR);
            if (mounted) {
                throw new IllegalStateException("Speer-Halterung landet in Platz " + slot.index + " an der Tour: " + withSpear.get().accessories());
            }
            spearNote = "Platz " + slot.index + " nimmt das Item, die Werkbank baut aber keine Tour mit Speer (Ergebnis "
                    + withSpear.map(b -> b.accessories().toString()).orElse("keines") + ")";
        }
        BuildCheck check = PropwashApi.builds().check(tour.withAccessory(AccessorySlot.BOTTOM, SPEAR));
        if (check.buildable()) {
            throw new IllegalStateException("Tour mit Speer-Halterung unten gilt als baubar");
        }
        notes.add("Speer-Halterung: " + spearNote + ", Bauprüfung meldet " + check.problems());
    }
}
