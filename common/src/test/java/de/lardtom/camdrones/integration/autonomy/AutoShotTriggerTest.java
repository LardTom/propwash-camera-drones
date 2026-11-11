package de.lardtom.camdrones.integration.autonomy;

import com.mojang.serialization.Codec;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.autonomy.client.PilotRelay;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.planner.model.AutoShotMenu;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.propwash.api.flight.SensorDirection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoShotTriggerTest {
    private static final List<String> SHOTS = List.of("retreat", "rise", "circle", "spiral");
    private static final double START_Y = 74.0;
    private static final double GROUND = 64.0;
    private static final double HEADING = 0.0;

    private final FakeServerFlight.Recorder recorder = new FakeServerFlight.Recorder();
    private final AtomicLong clock = new AtomicLong(1_000L);

    @Test
    void tabletFooterHasAnAutoShotButton() {
        assertTrue(Arrays.stream(EditorTexts.Footer.values()).anyMatch(f -> f.name().equals("AUTO")),
                "Fußleiste des Flugplaners: " + Arrays.toString(EditorTexts.Footer.values()));
    }

    @Test
    void tabletNamesTheFourAutoShotsInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            String text = Files.readString(RepoFiles.root().resolve("common/src/lang/planner/" + language + ".json"), StandardCharsets.UTF_8);
            for (String shot : SHOTS) {
                String key = "\"camdrones.planner.auto." + shot + "\"";
                assertTrue(text.contains(key), language + " fehlt " + key);
                assertTrue(text.contains("\"camdrones.planner.auto." + shot + ".tooltip\""), language + " fehlt Tooltip zu " + shot);
            }
        }
    }

    @Test
    void smartModeKnowsAutoShots() {
        assertTrue(Arrays.stream(SmartState.Function.values()).anyMatch(f -> f.name().equals("AUTO_SHOT")),
                "SmartState.Function: " + Arrays.toString(SmartState.Function.values()));
    }

    @Test
    void serverHasAnAutoShotFlight() {
        assertTrue(AutonomyPlan.Kind.byId(CamDrones.id("auto_shot")).isPresent(), "Flüge ohne Pilot: " + Arrays.toString(AutonomyPlan.Kind.values()));
    }

    @Test
    void pilotExitDuringAnAutoShotHolds() {
        assertEquals(SmartState.Function.HOLD, PilotRelay.function(NavLogic.Function.AUTO_SHOT));
    }

    @Test
    void menuListsEveryShotInFlightOrder() {
        assertEquals(List.of(AutoShots.Shot.values()), AutoShotMenu.SHOTS);
        assertEquals(SHOTS, AutoShotMenu.SHOTS.stream().map(shot -> AutoShotMenu.labelKey(shot).substring("camdrones.planner.auto.".length())).toList());
        assertEquals(List.of("15", "10", "10"), AutoShotMenu.tooltipArgs(AutoShots.Shot.RETREAT));
        assertEquals("12", AutoShotMenu.tooltipArgs(AutoShots.Shot.RISE).getFirst());
    }

    @Test
    void subjectLiesTenMetresAheadOnTheGround() {
        AutonomyPlan east = AutonomyPlan.autoShotAhead(AutoShots.Shot.CIRCLE, 3.0, -2.0, 90.0, 63.0);
        assertEquals(AutonomyPlan.Kind.AUTO_SHOT, east.kind());
        assertEquals(AutoShots.Shot.CIRCLE, east.shot());
        assertEquals(13.0, east.x(), 1e-9);
        assertEquals(63.0, east.y(), 1e-9);
        assertEquals(-2.0, east.z(), 1e-9);
        AutonomyPlan north = AutonomyPlan.autoShotAhead(AutoShots.Shot.RETREAT, 0.0, 0.0, 0.0, 64.0);
        assertEquals(0.0, north.x(), 1e-9);
        assertEquals(-AutoShots.SUBJECT_DISTANCE, north.z(), 1e-9);
        assertEquals(AutoShots.Shot.SPIRAL, AutonomyPlan.autoShot(AutoShots.Shot.SPIRAL, 1.0, 2.0, 3.0).withStartIndex(4).withPaused(true).shot());
        assertEquals(AutonomyStatus.AUTO_SHOT_STARTED, east.startedStatus());
        assertEquals(AutoShots.LABEL, east.label());
    }

    @Test
    void statusesOfAutoShotsStartAndFinishWithTone() {
        assertEquals(AutonomyStatus.AUTO_SHOT_STARTED, AutonomyStatus.started(AutonomyPlan.Kind.AUTO_SHOT));
        assertEquals(AutonomyStatus.Tone.START, AutonomyStatus.AUTO_SHOT_STARTED.tone());
        assertEquals(AutonomyStatus.Tone.DONE, AutonomyStatus.AUTO_SHOT_DONE.tone());
        assertTrue(AutonomyStatus.AUTO_SHOT_DONE.chat());
        assertFalse(AutonomyStatus.AUTO_SHOT_STARTED.error());
        AutonomyPlan plan = AutonomyPlan.autoShot(AutoShots.Shot.RISE, 0.0, 64.0, -10.0);
        assertEquals(FlightOptions.HOLD_TAKEOFF, FlightOptions.takeoff(false, plan, 64.0).orElseThrow(), 1e-9);
        assertEquals(OptionalDouble.empty(), FlightOptions.takeoff(true, plan, 64.0));
    }

    @Test
    void smartStateCarriesShotAndSubjectThroughBothCodecs() {
        for (AutoShots.Shot shot : AutoShots.Shot.values()) {
            SmartState smart = SmartState.autoShot(shot, 12.5, 63.0, -40.25);
            assertEquals(SmartState.Function.AUTO_SHOT, smart.function());
            assertTrue(smart.active());
            assertEquals(smart, roundTrip(SmartState.CODEC, smart));
            ByteBuf buf = Unpooled.buffer();
            UUID serial = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
            SmartMode.Request.CODEC.encode(buf, new SmartMode.Request(serial, smart));
            SmartMode.Request back = SmartMode.Request.CODEC.decode(buf);
            assertEquals(serial, back.serial());
            assertEquals(smart, back.smart());
            assertEquals(0, buf.readableBytes());
        }
        assertEquals(AutoShots.Shot.RETREAT, SmartState.NONE.shot());
        assertEquals(AutoShots.Shot.RETREAT, SmartState.hold(1.0, 2.0, 3.0).shot());
    }

    @Test
    void exitDuringAServerAutoShotHolds() {
        ExitDecision.Situation situation = new ExitDecision.Situation(true, true, true, Optional.empty(), MissionStatus.IDLE,
                SmartState.autoShot(AutoShots.Shot.SPIRAL, 0.0, 64.0, -10.0), DroneSettings.ExitBehaviour.LAND, UUID.randomUUID(), false,
                FlightClass.TOUR, 0.0, 80.0, 0.0);
        assertEquals(AutonomyPlan.Kind.HOLD, ExitDecision.decide(situation).orElseThrow().kind());
    }

    private AutonomyAssist assist(AutonomyPlan plan) {
        return new AutonomyAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), plan, false, ObstacleGuard.Mode.STOP,
                NavTarget.at(0.0, GROUND, 0.0), new FakeServerFlight.Environment(), this.recorder, this.clock::get);
    }

    private static AutonomyPlan ahead(AutoShots.Shot shot) {
        return AutonomyPlan.autoShotAhead(shot, 0.0, 0.0, HEADING, GROUND);
    }

    @Test
    void retreatFliesBackwardsAndClimbsThenHolds() {
        AutonomyAssist assist = this.assist(ahead(AutoShots.Shot.RETREAT));
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, START_Y, 0.0);
        sim.step(assist);
        assertEquals(NavLogic.Function.AUTO_SHOT, assist.nav().logic().function());
        assertEquals(AutoShots.LABEL, sim.out.label);
        sim.run(assist, AutoShots.RETREAT_SECONDS);
        double back = -(sim.state.x * Compass.forwardX(HEADING) + sim.state.z * Compass.forwardZ(HEADING));
        double side = Math.abs(sim.state.x * Compass.rightX(HEADING) + sim.state.z * Compass.rightZ(HEADING));
        assertTrue(back > 0.8 * AutoShots.RETREAT_SPEED * AutoShots.RETREAT_SECONDS, "rückwärts " + back + " m");
        assertTrue(side < 0.5, "seitlich " + side + " m");
        assertTrue(sim.state.y - START_Y > 0.8 * AutoShots.RETREAT_CLIMB * AutoShots.RETREAT_SECONDS, "gestiegen " + (sim.state.y - START_Y) + " m");
        this.assertHoldsAfterwards(assist, sim, AutoShots.Shot.RETREAT);
    }

    @Test
    void riseClimbsStraightUpThenHolds() {
        AutonomyAssist assist = this.assist(ahead(AutoShots.Shot.RISE));
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, START_Y, 0.0);
        sim.run(assist, AutoShots.RISE_SECONDS + 0.2);
        assertTrue(Math.hypot(sim.state.x, sim.state.z) < 0.5, "Drift " + Math.hypot(sim.state.x, sim.state.z) + " m");
        double climb = sim.state.y - START_Y;
        assertTrue(climb > 0.8 * AutoShots.RISE_CLIMB * AutoShots.RISE_SECONDS, "gestiegen " + climb + " m");
        assertTrue(assist.status().orElseThrow().gimbalTilt() < -80.0, "Kamera schwenkt nach unten: " + assist.status().orElseThrow().gimbalTilt());
        this.assertHoldsAfterwards(assist, sim, AutoShots.Shot.RISE);
    }

    @Test
    void circleGoesOnceAroundTheSubjectThenHolds() {
        AutonomyPlan plan = ahead(AutoShots.Shot.CIRCLE);
        AutonomyAssist assist = this.assist(plan);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, START_Y, 0.0);
        double[] sweep = this.sweep(assist, sim, plan, AutoShots.CIRCLE_SECONDS);
        assertTrue(sweep[0] > 300.0, "Kreis " + sweep[0] + "°");
        assertTrue(sweep[1] < 1.5, "Radius weicht " + sweep[1] + " m ab");
        assertTrue(Math.abs(sim.state.y - START_Y) < 1.0, "Höhe " + sim.state.y);
        assertTrue(assist.status().orElseThrow().gimbalTarget().isPresent(), "Kamera auf das Motiv");
        this.assertHoldsAfterwards(assist, sim, AutoShots.Shot.CIRCLE);
    }

    @Test
    void spiralWidensAndClimbsAroundTheSubjectThenHolds() {
        AutonomyPlan plan = ahead(AutoShots.Shot.SPIRAL);
        AutonomyAssist assist = this.assist(plan);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, START_Y, 0.0);
        double[] sweep = this.sweep(assist, sim, plan, AutoShots.SPIRAL_SECONDS);
        double radius = Math.hypot(sim.state.x - plan.x(), sim.state.z - plan.z());
        assertTrue(sweep[0] > 270.0, "Spirale " + sweep[0] + "°");
        assertTrue(radius > AutoShots.SUBJECT_DISTANCE + 0.7 * AutoShots.SPIRAL_WIDEN, "Radius am Ende " + radius + " m");
        double climb = sim.state.y - START_Y;
        assertTrue(climb > 0.7 * AutoShots.SPIRAL_CLIMB * AutoShots.SPIRAL_SECONDS, "gestiegen " + climb + " m");
        this.assertHoldsAfterwards(assist, sim, AutoShots.Shot.SPIRAL);
    }

    @Test
    void resetAfterTheShotStartedHoldsInsteadOfStartingOver() {
        AutonomyAssist assist = this.assist(ahead(AutoShots.Shot.RETREAT));
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, START_Y, 0.0);
        sim.run(assist, 3.0);
        assist.reset();
        assertEquals(AutonomyPlan.Kind.HOLD, assist.plan().kind());
        sim.step(assist);
        assertEquals(NavLogic.Function.HOLD, assist.nav().logic().function());
    }

    @Test
    void replanFromHoldStartsTheShotInTheSameFlight() {
        AutonomyAssist assist = this.assist(AutonomyPlan.hold());
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, START_Y, 0.0);
        sim.step(assist);
        assist.replan(ahead(AutoShots.Shot.RISE));
        sim.step(assist);
        assertEquals(NavLogic.Function.AUTO_SHOT, assist.nav().logic().function());
        assertEquals(1, this.recorder.started);
    }

    private double[] sweep(AutonomyAssist assist, FakeServerFlight.Sim sim, AutonomyPlan plan, double seconds) {
        double swept = 0.0;
        double worst = 0.0;
        double last = Double.NaN;
        double expected = AutoShots.SUBJECT_DISTANCE;
        int steps = (int) Math.round(seconds / FakeServerFlight.DT);
        for (int i = 0; i < steps; i++) {
            sim.step(assist);
            double dx = sim.state.x - plan.x();
            double dz = sim.state.z - plan.z();
            double angle = Math.toDegrees(Math.atan2(dz, dx));
            if (!Double.isNaN(last)) {
                swept += Math.abs(Compass.wrap180(angle - last));
            }
            last = angle;
            if (plan.shot() == AutoShots.Shot.CIRCLE && i > steps / 10) {
                worst = Math.max(worst, Math.abs(Math.hypot(dx, dz) - expected));
            }
        }
        return new double[]{swept, worst};
    }

    private void assertHoldsAfterwards(AutonomyAssist assist, FakeServerFlight.Sim sim, AutoShots.Shot shot) {
        assertTrue(sim.runUntil(assist, 2.0, () -> !this.recorder.autoShots.isEmpty()), "Automatik-Flug endet nicht");
        assertEquals(List.of(shot), this.recorder.autoShots);
        assertEquals(AutonomyPlan.Kind.HOLD, assist.plan().kind());
        sim.run(assist, 4.0);
        double speed = Math.hypot(sim.state.vx, sim.state.vz);
        assertTrue(speed < 0.3 && Math.abs(sim.state.vy) < 0.3, "hält nicht: " + speed + " m/s, steigt " + sim.state.vy);
        assertEquals(List.of(shot), this.recorder.autoShots, "Ende nur einmal gemeldet");
        assertFalse(sim.out.land, "landet nicht");
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
