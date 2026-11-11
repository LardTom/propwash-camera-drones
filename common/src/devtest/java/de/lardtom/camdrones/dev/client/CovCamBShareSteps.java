package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CovCamBShareSteps {
    public static final String NAME = "sharecode: Kamera-Drohne mit Mission und Umlack als PW3";
    public static final int FRAME_COLOR = 0xC0392B;
    public static final int ACCENT_COLOR = 0x27AE60;
    private static final String CODE = "covcamb.share.code";
    private static final String DATA = "covcamb.share.data";
    private static final String MISSION = "covcamb.share.mission";
    private static final String IMPORT = "covcamb.share.import";
    private static final String BEFORE = "covcamb.share.before";
    private static final String NOTES = "covcamb.share.notes";
    private static final int STEP_TICKS = 20 * 120;

    private CovCamBShareSteps() {
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    static void register() {
        SelfTest.clientStep(541, NAME, STEP_TICKS, Script.create()
                .append(CovCamBKit.goTo(150, 20))
                .append(CamFlights.groundDrone(CamPresets.TOUR, 150, 20, 0.0F))
                .server("Mission senden, aufheben und umlackieren", CovCamBShareSteps::prepare)
                .then("PW3-Code erzeugen und lesen", CovCamBShareSteps::roundTrip)
                .then("Code importieren (neue Drohne)", CovCamBShareSteps::importCode)
                .then("neue Drohne ohne Mission", (ctx, ticks) -> Script.server(ctx, ticks, (server, state) -> check(server, state, notes(ctx))))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx)))));
    }

    private static void prepare(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        Mission mission = Mission.of(List.of(Waypoint.at(drone.getX(), drone.getY() + 8.0, drone.getZ() + 10.0),
                Waypoint.at(drone.getX() + 10.0, drone.getY() + 8.0, drone.getZ() + 10.0)));
        PropwashApi.droneData().set(drone, CamDroneData.mission(), MissionData.of(mission));
        state.put(MISSION, MissionData.of(mission));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
        Optional<ItemStack> picked = player.getInventory().getNonEquipmentItems().stream().filter(PropwashApi.builds()::isDrone).findFirst();
        if (!drone.isRemoved() || picked.isEmpty()) {
            throw new IllegalStateException("Drohne lässt sich nicht aufheben");
        }
        ItemStack stack = picked.get();
        MissionData onItem = PropwashApi.droneData().get(stack, CamDroneData.mission());
        if (!MissionData.of(mission).equals(onItem)) {
            throw new IllegalStateException("Mission geht beim Aufheben verloren");
        }
        DroneData before = PropwashApi.builds().read(stack).orElseThrow();
        PaintSpec paint = before.paint().with("frame", FRAME_COLOR).with("accessories", ACCENT_COLOR);
        ItemStack painted = PropwashApi.builds().withPaint(stack, paint);
        player.getInventory().removeItem(stack);
        player.setItemInHand(InteractionHand.MAIN_HAND, painted);
        MissionData stillThere = PropwashApi.droneData().get(painted, CamDroneData.mission());
        DroneData data = PropwashApi.builds().read(painted).orElseThrow();
        state.put(DATA, data);
        state.put(BEFORE, stillThere.mission().map(Mission::size).orElse(0));
    }

    private static StepResult roundTrip(SelfTest.StepContext ctx, int ticks) {
        DroneData data = (DroneData) ctx.state().get(DATA);
        if (data == null) {
            return StepResult.fail("keine Drohnen-Daten");
        }
        String code = PropwashApi.shareCodes().encode(data, ShareOptions.defaults());
        if (!code.startsWith("PW3")) {
            return StepResult.fail("Code beginnt nicht mit PW3: " + code.substring(0, Math.min(8, code.length())));
        }
        ShareResult result = PropwashApi.shareCodes().decode(code);
        if (!result.ok() || result.data().isEmpty() || result.format().orElse(null) != ShareResult.Format.PW3) {
            return StepResult.fail("Code nicht lesbar: " + result.status() + " " + result.format());
        }
        DroneData back = result.data().get();
        if (!back.build().equals(data.build())) {
            return StepResult.fail("Teile ändern sich: " + data.build() + " → " + back.build());
        }
        if (!back.paint().equals(data.paint())) {
            return StepResult.fail("Lack ändert sich: " + data.paint() + " → " + back.paint());
        }
        boolean table = result.namespaces().stream().anyMatch(n -> CamDrones.MOD_ID.equals(n.namespace()) && n.check() == ShareResult.Check.MATCH);
        if (!table) {
            return StepResult.fail("Namensraum " + CamDrones.MOD_ID + " nicht über die PW3-Tabelle erkannt: " + result.namespaces());
        }
        ctx.state().put(CODE, code);
        notes(ctx).add("Tour mit Mission (" + ctx.state().get(BEFORE) + " Punkte am Item) und Lack " + Integer.toHexString(FRAME_COLOR) + "/"
                + Integer.toHexString(ACCENT_COLOR) + " als " + code.length() + " Zeichen PW3, gelesen mit gleichen Teilen und Lack, Tabelle "
                + CamDrones.MOD_ID + " erkannt");
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static StepResult importCode(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(IMPORT)) {
            ShareResult decoded = PropwashApi.shareCodes().decode((String) state.get(CODE));
            state.put(IMPORT, PropwashClientApi.sendImport(ImportRequest.createDrone(decoded.data().orElseThrow())));
            return StepResult.pending();
        }
        CompletableFuture<ImportResult> future = (CompletableFuture<ImportResult>) state.get(IMPORT);
        if (!future.isDone()) {
            return ticks > 200 ? StepResult.fail("Import antwortet nicht") : StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Import: " + future.exceptionNow());
        }
        ImportResult result = future.join();
        if (result.status() != ImportStatus.APPLIED) {
            return StepResult.fail("Import " + result.status() + " (fehlende Teile " + result.missingParts() + ")");
        }
        return ticks >= 10 ? StepResult.pass() : StepResult.pending();
    }

    private static void check(MinecraftServer server, Map<String, Object> state, List<String> notes) {
        ServerPlayer player = Arena.player(server);
        DroneData original = (DroneData) state.get(DATA);
        List<ItemStack> drones = player.getInventory().getNonEquipmentItems().stream().filter(PropwashApi.builds()::isDrone).toList();
        ItemStack imported = null;
        ItemStack kept = null;
        for (ItemStack stack : drones) {
            MissionData mission = PropwashApi.droneData().get(stack, CamDroneData.mission());
            if (mission.mission().isPresent()) {
                kept = stack;
            } else {
                imported = stack;
            }
        }
        if (imported == null) {
            throw new IllegalStateException("keine neue Drohne ohne Mission im Inventar (" + drones.size() + " Drohnen)");
        }
        DroneData data = PropwashApi.builds().read(imported).orElseThrow();
        if (!data.build().equals(original.build()) || !data.paint().equals(original.paint())) {
            throw new IllegalStateException("neue Drohne hat andere Teile oder anderen Lack: " + data.build() + " " + data.paint());
        }
        UUID serial = PropwashApi.droneData().get(imported, CamDroneData.serial());
        if (!CamDroneData.NO_SERIAL.equals(serial)) {
            throw new IllegalStateException("neue Drohne trägt eine Seriennummer " + serial);
        }
        notes.add("Import: neue Drohne mit gleichen Teilen und Lack, ohne camdrones:mission und ohne Seriennummer"
                + (kept != null ? ", die alte behält ihre Mission" : ""));
        player.getInventory().clearContent();
    }
}
