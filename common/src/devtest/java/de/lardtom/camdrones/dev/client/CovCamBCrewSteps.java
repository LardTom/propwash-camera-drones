package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovCamBCrewSteps {
    public static final String CLASSES = "crew: Klassen";
    public static final String NO_GIMBAL = "crew: Drohne ohne Gimbal – Befehl meldet NO_GIMBAL";
    private static final String NOTES = "covcamb.crew.notes";
    private static final String BUTTON = "covcamb.crew.button";
    private static final String DRONE = "covcamb.crew.drone";
    private static final int STEP_TICKS = 20 * 240;

    record Case(String label, DroneData data, int dx) {
    }

    private CovCamBCrewSteps() {
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        int dx = 100;
        for (CamPresets.Entry entry : CamPresets.ALL) {
            out.add(new Case(entry.path(), Pilot.preset(entry).data(), dx));
            dx += 10;
        }
        DroneData tour = Pilot.preset(CamPresets.TOUR).data();
        BuildSpec build = tour.build();
        Map<AccessorySlot, net.minecraft.resources.Identifier> accessories = new EnumMap<>(AccessorySlot.class);
        accessories.putAll(build.accessories());
        accessories.remove(AccessorySlot.FRONT);
        BuildSpec bare = new BuildSpec(build.frame(), build.stack(), build.motor(), build.prop(), build.video(), build.battery(), accessories);
        out.add(new Case("tour ohne Gimbal", tour.withBuild(bare), dx));
        return out;
    }

    static void classes() {
        SelfTest.clientStep(521, CLASSES, STEP_TICKS, script(cases(), false), cleanup());
    }

    static void noGimbal() {
        List<Case> bare = cases().stream().filter(c -> c.label().contains("ohne")).toList();
        SelfTest.clientStep(522, NO_GIMBAL, STEP_TICKS, script(bare, true), cleanup());
    }

    private static Script cleanup() {
        return Script.create().server("Einladungen löschen, " + JoinEnv.GUEST_NAME + " abmelden", (server, state) -> {
            CrewService.book().decline(JoinEnv.GUEST_ID);
            CovCamBKit.leave(server);
        });
    }

    private static Script script(List<Case> cases, boolean strict) {
        Script script = Script.create().append(CovCamBKit.goTo(140, -40));
        for (Case c : cases) {
            script.server(c.label() + ": Drohne, " + JoinEnv.GUEST_NAME + " daneben", (server, state) -> place(server, state, c))
                    .then(c.label() + ": Drohne am Client", (ctx, ticks) -> clientDrone(ctx).isPresent() ? StepResult.pass()
                            : ticks > 100 ? StepResult.fail("Drohne fehlt am Client") : StepResult.pending())
                    .run(c.label() + ": Meldung leeren", CovCamAKit::clearOverlay)
                    .then(c.label() + ": Schleichen + Rechtsklick", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, clientDrone(ctx).orElseThrow(), true))
                    .then(c.label() + ": Wartungsmenü", (ctx, ticks) -> service(ctx, ticks, c))
                    .run(c.label() + ": Wartung schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                    .then(c.label() + ": /camdrones crew invite " + JoinEnv.GUEST_NAME, (ctx, ticks) -> Script.server(ctx, ticks,
                            (server, state) -> invite(server, state, c, notes(ctx), strict)));
        }
        script.then("Knopf und Einladung passen zusammen", (ctx, ticks) -> {
            List<String> notes = notes(ctx);
            List<String> bad = notes.stream().filter(n -> n.startsWith("FAIL ")).toList();
            if (!bad.isEmpty()) {
                return StepResult.fail(String.join("; ", bad.stream().map(n -> n.substring(5)).toList()));
            }
            return StepResult.pass(String.join("; ", notes));
        });
        return script;
    }

    private static java.util.Optional<Entity> clientDrone(SelfTest.StepContext ctx) {
        Object id = ctx.state().get(DRONE);
        Minecraft mc = Minecraft.getInstance();
        if (!(id instanceof UUID uuid) || mc.level == null) {
            return java.util.Optional.empty();
        }
        for (Entity entity : Arena.clientDrones(mc)) {
            if (entity.getUUID().equals(uuid)) {
                return java.util.Optional.of(entity);
            }
        }
        return java.util.Optional.empty();
    }

    private static void place(MinecraftServer server, Map<String, Object> state, Case c) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        CrewService.book().decline(JoinEnv.GUEST_ID);
        Object previous = state.get(DRONE);
        if (previous instanceof UUID uuid && level.getEntity(uuid) != null) {
            level.getEntity(uuid).discard();
        }
        BlockPos ground = Arena.ground(c.dx(), -40);
        Entity drone = CovCamAKit.place(player, PropwashApi.builds().createDrone(c.data()), ground, 0.0F);
        PropwashApi.droneData().set(drone, CamDroneData.serial(), UUID.randomUUID());
        PlannerOwners.claim(drone, player.getUUID());
        state.put(DRONE, drone.getUUID());
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, -3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 20.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CovCamBKit.guest(server, level, stand.add(4.0, 0.0, 0.0));
    }

    private static StepResult service(SelfTest.StepContext ctx, int ticks, Case c) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null || ticks < 10) {
            return ticks > 100 ? StepResult.fail(c.label() + ": Wartungsmenü öffnet nicht") : StepResult.pending();
        }
        String crew = I18n.get(CrewTexts.BUTTON);
        boolean button = CovCamAKit.widget(screen, crew).isPresent();
        Object more = ctx.state().get(BUTTON + c.label() + ".more");
        if (!button && more == null) {
            java.util.Optional<net.minecraft.client.gui.components.AbstractWidget> overflow = CovCamAKit.widget(screen, "…");
            if (overflow.isPresent()) {
                ctx.log(c.label() + ": Wartung " + screen.getClass().getSimpleName() + " mit Knöpfen " + CovCamAKit.labels(screen) + ", öffne …");
                ctx.state().put(BUTTON + c.label() + ".more", ticks);
                CovCamAKit.click(screen, overflow.get());
                return StepResult.pending();
            }
        }
        if (!button && more instanceof Integer since && ticks - since < 20) {
            return StepResult.pending();
        }
        ctx.log(c.label() + ": " + screen.getClass().getSimpleName() + " mit Knöpfen " + CovCamAKit.labels(screen) + (more != null ? " (unter …)" : ""));
        ctx.state().put(BUTTON + c.label(), button);
        ctx.state().put(BUTTON + c.label() + ".where", more != null ? "unter …" : "im Wartungsmenü");
        return StepResult.pass();
    }

    private static void invite(MinecraftServer server, Map<String, Object> state, Case c, List<String> notes, boolean strict) {
        ServerPlayer player = Arena.player(server);
        CrewService.book().decline(JoinEnv.GUEST_ID);
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "camdrones crew invite " + JoinEnv.GUEST_NAME);
        boolean invited = CrewService.book().pending(JoinEnv.GUEST_ID, Util.getMillis()).isPresent();
        CrewService.book().decline(JoinEnv.GUEST_ID);
        Entity drone = Arena.level(server).getEntity((UUID) state.get(DRONE));
        ServerPlayer guest = server.getPlayerList().getPlayer(JoinEnv.GUEST_ID);
        CrewBook.Verdict direct = CrewService.invite(player, drone, guest);
        CrewService.book().decline(JoinEnv.GUEST_ID);
        CrewBook.Verdict command = invited ? CrewBook.Verdict.OK : CrewService.resolveDrone(player).isEmpty() ? CrewBook.Verdict.NO_DRONE : direct;
        boolean button = (boolean) state.getOrDefault(BUTTON + c.label(), false);
        String line = c.label() + ": Knopf „Crew“ " + (button ? "sichtbar " + state.getOrDefault(BUTTON + c.label() + ".where", "") : "fehlt")
                + ", Befehl " + command + ", Einladung für genau diese Drohne " + direct;
        if (button != invited) {
            notes.add("FAIL " + line + " (Knopf und Befehl widersprechen sich)");
        } else if (strict && !invited && command != CrewBook.Verdict.NO_GIMBAL) {
            notes.add("FAIL " + line + " (Befehl lehnt mit " + command + " statt NO_GIMBAL ab: „" + I18n.get(CrewTexts.verdict(command)) + "“)");
        } else {
            notes.add(line + (invited ? "" : " („" + I18n.get(CrewTexts.verdict(command)) + "“)"));
        }
        if (drone != null) {
            drone.discard();
        }
    }
}
