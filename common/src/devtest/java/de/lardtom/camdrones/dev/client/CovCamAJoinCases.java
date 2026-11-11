package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.GimbalViews;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.client.planner.WaypointPreview;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CovCamAJoinCases {
    public static final String CASE = "covcama";
    public static final int OWN_POINTS = 3;
    private static final String DRONE = "covcama.drone";
    private static final String MAX = "covcama.max";
    private static final String SHOTS = "covcama.shots";
    private static final String NEXT = "covcama.next";
    private static final String READY = "covcama.ready";
    private static final int WAIT = 20 * 120;
    private static final double TILT_TOLERANCE = 1.0;

    private CovCamAJoinCases() {
    }

    public static Script script() {
        return Script.create()
                .then("Nr. 68: fremde Tour und leerer Flugplaner", (ctx, ticks) -> {
                    Optional<Entity> drone = nearbyPaired(4.5);
                    if (drone.isPresent() && heldPlanner().isPresent() && ticks >= 20) {
                        ctx.state().put(DRONE, drone.get());
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("keine fremde Tour mit Seriennummer vor dem Client") : StepResult.pending();
                })
                .run("Meldung leeren", CovCamAKit::clearOverlay)
                .then("Nr. 68: Rechtsklick mit dem Flugplaner auf die fremde Drohne", (ctx, ticks) -> CovCamAKit.use(ctx, ticks,
                        (Entity) ctx.state().get(DRONE), false))
                .then("Nr. 68: Ablehnung", (ctx, ticks) -> {
                    Optional<String> key = CovCamAKit.overlayKey();
                    boolean linked = heldPlanner().flatMap(PlannerTablets::link).isPresent();
                    if (key.filter((FieldText.PREFIX + "pair.not_owner")::equals).isPresent() && !linked) {
                        JoinProbe.line("pair foreign not_owner \"" + CovCamAKit.overlayText() + "\"");
                        JoinProbe.line("pair foreign no_link");
                        return StepResult.pass();
                    }
                    if (linked) {
                        return StepResult.fail("Flugplaner koppelt die fremde Drohne");
                    }
                    return ticks > 80 ? StepResult.fail("keine Meldung pair.not_owner (Meldung \"" + CovCamAKit.overlayText() + "\" " + key.orElse("-") + ")")
                            : StepResult.pending();
                })
                .then("Nr. 69: ohne eigenen Flugplaner", (ctx, ticks) -> markers(ctx, ticks, false))
                .then("Nr. 69: mit eigenem Flugplaner", (ctx, ticks) -> markers(ctx, ticks, true))
                .then("Nr. 50: Kino mit Operator", (ctx, ticks) -> {
                    Optional<Entity> drone = operatedDrone();
                    if (drone.isPresent()) {
                        ctx.state().put(DRONE, drone.get());
                        JoinProbe.line("gimbal operator bound on " + drone.get().getUUID());
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("keine Kino mit Operator in der Nähe") : StepResult.pending();
                })
                .then("Nr. 50: Gimbal-Kopf beobachten, 5 Bilder", CovCamAJoinCases::gimbal);
    }

    private static Optional<ItemStack> heldPlanner() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) ? Optional.of(stack) : Optional.empty();
    }

    private static Optional<Entity> nearbyPaired(double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return Optional.empty();
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!PropwashApi.drones().isDrone(entity) || entity.distanceTo(mc.player) > range) {
                continue;
            }
            UUID serial = PropwashApi.droneData().get(entity, CamDroneData.serial());
            if (serial != null && !CamDroneData.NO_SERIAL.equals(serial)) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private static Optional<Entity> operatedDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return Optional.empty();
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (PropwashApi.drones().isDrone(entity) && entity.distanceTo(mc.player) < 32.0F && CrewData.get(entity).operated()) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private static StepResult markers(SelfTest.StepContext ctx, int ticks, boolean own) {
        Minecraft mc = Minecraft.getInstance();
        Optional<ItemStack> held = heldPlanner();
        boolean ready = own ? held.map(s -> mc.player != null && PlannerClient.tabletState(mc.player, s).activeMission().size() == OWN_POINTS).orElse(false)
                : held.isEmpty() && mc.player != null && mc.player.getMainHandItem().isEmpty();
        if (!ready) {
            ctx.state().remove(READY);
            return ticks > WAIT ? StepResult.fail(own ? "eigener Flugplaner mit " + OWN_POINTS + " Punkten kommt nicht an" : "Hand wird nicht leer")
                    : StepResult.pending();
        }
        int since = (int) ctx.state().computeIfAbsent(READY, key -> ticks);
        int drawn = WaypointPreview.lastDrawn();
        int max = Math.max((int) ctx.state().getOrDefault(MAX + own, 0), drawn);
        ctx.state().put(MAX + own, max);
        if (ticks - since < 60) {
            return StepResult.pending();
        }
        ctx.state().remove(READY);
        if (own) {
            JoinProbe.line("markers own " + max + " (simulierter Spieler hält 5 Punkte)");
            return max == OWN_POINTS ? StepResult.pass() : StepResult.fail("mit eigenem Flugplaner " + max + " Marker statt " + OWN_POINTS);
        }
        JoinProbe.line("markers none " + max + " (simulierter Spieler hält 5 Punkte)");
        return max == 0 ? StepResult.pass() : StepResult.fail("ohne eigenen Flugplaner " + max + " Marker sichtbar");
    }

    @SuppressWarnings("unchecked")
    private static StepResult gimbal(SelfTest.StepContext ctx, int ticks) {
        Entity drone = (Entity) ctx.state().get(DRONE);
        if (drone.isRemoved()) {
            return StepResult.fail("Kino verschwindet");
        }
        CovCamAKit.lookAt(drone);
        GimbalData data = CrewData.get(drone);
        double tilt = data.setting().tilt();
        double pan = data.setting().pan();
        GimbalViews.View view = GimbalClient.views().peek(drone.getId());
        String head = view == null ? "kein Modell-Kopf" : String.format(Locale.ROOT, "head pitch=%.1f heading=%.1f", view.head().pitchDeg(),
                view.head().headingDeg());
        if (ticks % 10 == 0) {
            JoinProbe.line(String.format(Locale.ROOT, "gimbal t=%d tilt=%.1f pan=%.1f %s frame=%.1f", ticks, tilt, pan, head,
                    GimbalScenes.heading(drone)));
        }
        List<CompletableFuture<String>> shots = (List<CompletableFuture<String>>) ctx.state().computeIfAbsent(SHOTS,
                key -> new ArrayList<CompletableFuture<String>>());
        int next = shots.size();
        if (next < 5) {
            boolean reached = switch (next) {
                case 0 -> ticks >= 30 && Math.abs(tilt) < TILT_TOLERANCE && Math.abs(pan) < TILT_TOLERANCE;
                case 1 -> tilt <= -30.0 + TILT_TOLERANCE && Math.abs(pan) < TILT_TOLERANCE;
                case 2 -> tilt <= -60.0 + TILT_TOLERANCE && Math.abs(pan) < TILT_TOLERANCE;
                case 3 -> tilt <= -60.0 + TILT_TOLERANCE && Math.abs(pan - 90.0) < TILT_TOLERANCE;
                default -> tilt <= -60.0 + TILT_TOLERANCE && Math.abs(pan) > 180.0 - TILT_TOLERANCE;
            };
            int since = (int) ctx.state().getOrDefault(NEXT, -1);
            if (reached && since < 0) {
                ctx.state().put(NEXT, ticks);
            } else if (reached && ticks - since >= 15) {
                ctx.state().put(NEXT, -1);
                shots.add(Shots.capture(JoinProbe.dir().resolve(String.format(Locale.ROOT, "covcama_gimbal_%02d.png", next + 1))));
                JoinProbe.line(String.format(Locale.ROOT, "gimbal photo %d tilt=%.1f pan=%.1f %s", next + 1, tilt, pan, head));
            } else if (!reached) {
                ctx.state().put(NEXT, -1);
            }
            return ticks > WAIT ? StepResult.fail("Gimbal-Lage erreicht Bild " + (next + 1) + " nicht (Neigung " + tilt + "°, Schwenk " + pan + "°)")
                    : StepResult.pending();
        }
        for (CompletableFuture<String> shot : shots) {
            if (!shot.isDone()) {
                return StepResult.pending();
            }
            if (shot.isCompletedExceptionally()) {
                return StepResult.fail("Bild: " + shot.exceptionNow());
            }
        }
        if (view == null) {
            return StepResult.fail("Propwash fragt modelPose für den Kopf nicht ab (kein Modell-Kopf)");
        }
        JoinProbe.line("gimbal photos 5 " + head);
        return StepResult.pass();
    }
}
