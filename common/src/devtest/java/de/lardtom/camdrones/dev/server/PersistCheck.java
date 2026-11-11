package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class PersistCheck extends ServerTest.Scenario {
    static final String MARKER = "camdrones-persist.txt";
    static final int START_TICKS = 40;
    static final int FIND_TICKS = 20 * 30;
    static final int LOAD_TICKS = 40;
    private final boolean write;
    private int stage;
    private boolean ok;
    private @Nullable UUID serial;
    private @Nullable MissionData expected;
    private @Nullable ServerPlayer guest;
    private BlockPos ground = BlockPos.ZERO;
    private int mark;

    PersistCheck(boolean write) {
        this.write = write;
    }

    private static Path marker() {
        return Services.PLATFORM.gameDir().resolve(MARKER);
    }

    static Mission mission(BlockPos ground) {
        double y = ground.getY() + 9.0;
        double x = ground.getX() + 0.5;
        double z = ground.getZ() + 0.5;
        return Mission.of(List.of(Waypoint.at(x, y, z + 10.0).withWait(2.0), Waypoint.at(x + 10.0, y + 2.0, z + 10.0).withTilt(-30),
                Waypoint.at(x + 10.0, y, z), Waypoint.at(x, y, z))).withEnd(Mission.End.LAND).withSmooth(true).withBaseSpeed(6.0);
    }

    @Override
    void tick(MinecraftServer server, int tick) {
        if (this.stage == 9 || tick < START_TICKS) {
            return;
        }
        if (this.write) {
            if (this.stage == 0) {
                this.prepare(server, tick);
                return;
            }
            if (tick - this.mark < LOAD_TICKS) {
                return;
            }
            this.stage = 9;
            this.write(server);
            return;
        }
        this.check(server, tick);
    }

    private void prepare(MinecraftServer server, int tick) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX() + ServerSetup.ARENA_OFFSET;
        int z = spawn.getZ() - ServerSetup.ARENA_OFFSET;
        ServerSetup.command(server, "forceload add " + x + " " + z);
        this.ground = ServerSetup.ground(level, x, z);
        this.guest = ServerSetup.guest(server, level, Vec3.atBottomCenterOf(this.ground.above()).add(0.0, 0.0, -1.5));
        this.stage = 1;
        this.mark = tick;
    }

    private void write(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos ground = ServerSetup.ground(level, this.ground.getX(), this.ground.getZ());
        Entity drone = ServerSetup.spawn(this.guest, CamPresets.TOUR, ground);
        UUID serial = ServerSetup.pair(drone, this.guest.getUUID());
        Mission mission = mission(ground);
        ServerSetup.mission(drone, mission);
        PropwashApi.droneData().set(drone, CamDroneData.missionState(), MissionStatus.IDLE.with(NavLogic.MissionState.PAUSED, 2));
        MissionData data = MissionData.of(mission);
        ServerTest.line("persist: Drohne " + serial + " mit Mission (" + mission.size() + " Punkte, Ende LAND, angehalten bei Punkt 3) gesetzt");
        this.guest.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        this.guest.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
        Optional<ItemStack> picked = this.guest.getInventory().getNonEquipmentItems().stream().filter(PropwashApi.builds()::isDrone).findFirst();
        if (!drone.isRemoved() || picked.isEmpty()) {
            ServerTest.fail("persist: Drohne lässt sich nicht aufheben");
            ServerSetup.leave(server, this.guest);
            return;
        }
        ItemStack stack = picked.get();
        MissionData onItem = PropwashApi.droneData().get(stack, CamDroneData.mission());
        UUID serialOnItem = PropwashApi.droneData().get(stack, CamDroneData.serial());
        if (!data.equals(onItem) || !serial.equals(serialOnItem)) {
            ServerTest.fail("persist: Mission oder Seriennummer am Item verloren (" + (data.equals(onItem) ? "Mission ok" : "Mission weg") + ", "
                    + (serial.equals(serialOnItem) ? "Seriennummer ok" : "Seriennummer weg") + ")");
        } else {
            ServerTest.line("persist: aufgehoben, Mission und Seriennummer am Item");
        }
        this.guest.getInventory().removeItem(stack);
        Entity placed = ServerSetup.place(this.guest, stack, ground);
        MissionData again = PropwashApi.droneData().get(placed, CamDroneData.mission());
        MissionStatus status = PropwashApi.droneData().get(placed, CamDroneData.missionState());
        if (!data.equals(again) || !serial.equals(PropwashApi.droneData().get(placed, CamDroneData.serial()))) {
            ServerTest.fail("persist: Mission nach dem Ablegen verloren");
        } else {
            ServerTest.line("persist: wieder abgelegt, Mission " + again.mission().map(m -> m.size() + " Punkte").orElse("leer") + ", Zustand "
                    + status.state() + " bei Punkt " + (status.index() + 1));
        }
        ServerSetup.leave(server, this.guest);
        try {
            Files.writeString(marker(), serial + "\n" + Arrays.toString(data.ints()) + "\n" + ground.getX() + " " + ground.getZ() + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            ServerTest.fail("persist: Merker nicht geschrieben: " + e);
            return;
        }
        server.saveEverything(true, true, true);
        this.ok = true;
        ServerTest.line("persist: Chunk festgehalten, Welt gespeichert, Merker " + MARKER + " geschrieben");
    }

    private void check(MinecraftServer server, int tick) {
        ServerLevel level = server.overworld();
        if (this.serial == null) {
            List<String> lines;
            try {
                lines = Files.readAllLines(marker(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                ServerTest.fail("persist: kein Merker vom ersten Lauf (" + e + ")");
                this.stage = 9;
                return;
            }
            this.serial = UUID.fromString(lines.get(0).trim());
            int[] ints = Arrays.stream(lines.get(1).replace("[", "").replace("]", "").split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .mapToInt(Integer::parseInt).toArray();
            this.expected = MissionData.of(ints);
            ServerTest.line("persist: suche Drohne " + this.serial + " nach dem Neustart");
        }
        Optional<Entity> found = PlannerTablets.findDrone(level, this.serial);
        if (found.isEmpty()) {
            if (tick > START_TICKS + FIND_TICKS) {
                ServerTest.fail("persist: Drohne nach dem Neustart nicht gefunden");
                this.stage = 9;
            }
            return;
        }
        Entity drone = found.get();
        MissionData data = PropwashApi.droneData().get(drone, CamDroneData.mission());
        MissionStatus status = PropwashApi.droneData().get(drone, CamDroneData.missionState());
        if (!this.expected.equals(data)) {
            ServerTest.fail("persist: Mission nach dem Neustart anders oder leer");
        } else if (status.state() != NavLogic.MissionState.PAUSED || status.index() != 2) {
            ServerTest.fail("persist: Zustand nach dem Neustart " + status.state() + " bei Punkt " + (status.index() + 1) + " statt PAUSED bei Punkt 3");
        } else {
            this.ok = true;
            ServerTest.line("persist: nach dem Neustart Drohne mit derselben Mission (" + data.mission().map(m -> m.size() + " Punkte").orElse("leer")
                    + ") und Zustand PAUSED bei Punkt 3");
        }
        drone.discard();
        ServerSetup.command(server, "forceload remove " + drone.getBlockX() + " " + drone.getBlockZ());
        this.stage = 9;
    }

    @Override
    void abort() {
        super.abort();
        MinecraftServer server = this.guest == null ? null : this.guest.level().getServer();
        if (server != null) {
            ServerSetup.leave(server, this.guest);
        }
    }

    @Override
    boolean done() {
        return this.stage == 9;
    }

    @Override
    @Nullable String missing() {
        return this.ok ? null : "Dauer-Prüfung nicht bestanden";
    }
}
