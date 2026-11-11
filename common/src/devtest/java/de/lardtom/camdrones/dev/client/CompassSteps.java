package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CompassCheck;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.AttiMode;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.ForceProvider;
import de.lardtom.propwash.api.flight.ForceSink;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CompassSteps {
    public static final String NAME = "kompass: Kompassfehler, Warnung im OSD und ATTI bis der Kompass passt";
    public static final double DISTURBANCE = 40.0;
    public static final String PROBE_ID = CamDrones.MOD_ID + ":compass_probe";
    private static final Probe PROBE = new Probe();
    private static final String START = "compass.start";
    private static final String DRIFT = "compass.drift";
    private static final String SINCE = "compass.since";
    private static volatile boolean active;

    private CompassSteps() {
    }

    private static final class Probe implements ForceProvider {
        volatile int samples;
        volatile double error = Double.NaN;
        volatile double heading = Double.NaN;
        volatile double compass = Double.NaN;
        volatile boolean healthy;

        @Override
        public void apply(FlightState state, ForceSink sink) {
            if (!Minecraft.getInstance().isSameThread()) {
                return;
            }
            this.error = state.compassErrorDeg();
            this.heading = state.headingDeg();
            this.compass = state.compassHeadingDeg();
            this.healthy = state.compassHealthy();
            this.samples++;
        }

        String describe() {
            return String.format(Locale.GERMANY, "Kurs %.1f°, Kompass %.1f°, Fehler %+.1f° (%s), %d Proben", this.heading, this.compass, this.error,
                    this.healthy ? "gesund" : "gestört", this.samples);
        }
    }

    public static void register() {
        PropwashApi.flight().registerForces(PROBE_ID, profile -> active ? PROBE : null);
        SelfTest.clientStep(22, NAME, 20 * 180, Script.create()
                .append(Shots.photoWindow())
                .run("Kompass-Sonde an", () -> {
                    active = true;
                    PROBE.samples = 0;
                })
                .append(Pilot.launch(CamPresets.TOUR, 52, -16, 0.0F))
                .append(Flight.takeoff(4.0))
                .then("Kompass laut Propwash", CompassSteps::natural)
                .run("Kompass stören (" + (int) DISTURBANCE + "°)", () -> CompassCheck.testDisturbance(DISTURBANCE))
                .then("ATTI mit Warnung", CompassSteps::disturbed)
                .then("Text der Warnung auf Deutsch", (ctx, ticks) -> german())
                .append(Shots.photos("compass_error_osd"))
                .then("ATTI hält 5 s an", CompassSteps::staysAtti)
                .run("Kompass wieder gut", () -> CompassCheck.testDisturbance(0.0))
                .then("zurück in N", CompassSteps::recovered), ctx -> {
                    CompassCheck.testDisturbance(0.0);
                    active = false;
                    return StepResult.pass();
                });
    }

    private static StepResult natural(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        if (PROBE.samples < 20) {
            return ticks > 200 ? StepResult.fail("FlightState kommt nicht an der Sonde an") : StepResult.pending();
        }
        if (!Double.isFinite(PROBE.error) || !Double.isFinite(PROBE.compass)) {
            return StepResult.fail("Propwash liefert keinen Kompass: " + PROBE.describe());
        }
        Optional<NavStatus> status = Pilot.status();
        boolean atti = status.map(NavStatus::atti).orElse(false);
        boolean warned = status.map(s -> s.warns(NavOutput.Warning.COMPASS)).orElse(false);
        if (PROBE.healthy == (atti || warned)) {
            return ticks > 100 ? StepResult.fail("Flughilfe folgt dem Kompass von Propwash nicht: " + PROBE.describe() + ", " + Flight.describe())
                    : StepResult.pending();
        }
        return StepResult.pass("Kompass laut Propwash: " + PROBE.describe() + ", Flughilfe " + Flight.describe());
    }

    private static List<String> osdWarnings() {
        return CamOsd.warnings(CamOsdSource.last()).stream().map(OsdText::text).toList();
    }

    private static StepResult disturbed(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<NavStatus> status = Pilot.status();
        boolean atti = status.map(NavStatus::atti).orElse(false) && status.map(s -> AttiMode.LABEL.equals(s.label())).orElse(false);
        boolean warned = status.map(s -> s.warns(NavOutput.Warning.COMPASS)).orElse(false);
        boolean shown = osdWarnings().contains(CamOsd.Warning.COMPASS.text());
        boolean noGps = osdWarnings().contains(CamOsd.Warning.NO_GPS.text());
        if (atti && warned && shown && !noGps) {
            ctx.state().put(START, Flight.position().orElse(Vec3.ZERO));
            return StepResult.pass("nach " + Flight.format(ticks / 20.0) + " s ATTI, OSD-Warnungen " + osdWarnings());
        }
        return ticks > 60 ? StepResult.fail("kein ATTI mit Kompassfehler: " + Flight.describe() + ", OSD-Warnungen " + osdWarnings())
                : StepResult.pending();
    }

    private static StepResult german() {
        Minecraft mc = Minecraft.getInstance();
        Shots.language(mc, Shots.GERMAN);
        String text = CamOsd.Warning.COMPASS.shown();
        List<String> warnings = osdWarnings();
        Shots.restoreLanguage(mc);
        String english = CamOsd.Warning.COMPASS.shown();
        if (!"KOMPASSFEHLER".equals(text) || !warnings.contains("KOMPASSFEHLER") || !"COMPASS ERROR".equals(english)) {
            return StepResult.fail("Warnung auf Deutsch \"" + text + "\" (OSD " + warnings + "), auf Englisch \"" + english + "\"");
        }
        return StepResult.pass("OSD zeigt auf Deutsch \"" + text + "\", auf Englisch \"" + english + "\"");
    }

    private static StepResult staysAtti(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<NavStatus> status = Pilot.status();
        if (!status.map(NavStatus::atti).orElse(false) || !status.map(s -> s.warns(NavOutput.Warning.COMPASS)).orElse(false)) {
            return StepResult.fail("verlässt ATTI trotz gestörtem Kompass: " + Flight.describe());
        }
        Vec3 start = (Vec3) ctx.state().get(START);
        Flight.position().ifPresent(p -> ctx.state().put(DRIFT, CamFlights.horizontal(p, start)));
        if (ticks < 100) {
            return StepResult.pending();
        }
        return StepResult.pass("5 s ATTI mit Kompassfehler, Drift mit dem Wind " + Flight.format((double) ctx.state().getOrDefault(DRIFT, 0.0)) + " m");
    }

    private static StepResult recovered(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<NavStatus> status = Pilot.status();
        boolean atti = status.map(NavStatus::atti).orElse(true);
        if (atti) {
            if (ticks > 20 * (int) (AttiMode.REGAIN_SECONDS + 3)) {
                return StepResult.fail("bleibt in ATTI, obwohl der Kompass passt: " + Flight.describe());
            }
            if (ticks < 20 * AttiMode.REGAIN_SECONDS - 4 && !status.map(s -> s.warns(NavOutput.Warning.COMPASS)).orElse(false)) {
                return StepResult.fail("Grund der ATTI-Zeit fehlt vor der Rückkehr: " + Flight.describe());
            }
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(SINCE)) {
            ctx.state().put(SINCE, ticks);
            if (ticks < 20 * AttiMode.REGAIN_SECONDS - 4) {
                return StepResult.fail("verlässt ATTI schon nach " + Flight.format(ticks / 20.0) + " s");
            }
        }
        if (status.get().warns(NavOutput.Warning.COMPASS)) {
            return StepResult.fail("Flughilfe meldet nach der Rückkehr weiter den Kompassfehler");
        }
        boolean osd = osdWarnings().contains(CamOsd.Warning.COMPASS.text());
        if (osd && ticks - (int) ctx.state().get(SINCE) > 20) {
            return StepResult.fail("Kompassfehler bleibt nach der Rückkehr im OSD: " + osdWarnings());
        }
        StepResult calm = Flight.calm(ctx, ticks, 20 * 15);
        if (!calm.done() || osd) {
            return calm.failed() ? calm : StepResult.pending();
        }
        if (calm.failed()) {
            return calm;
        }
        return StepResult.pass("zurück in " + status.get().label() + " nach " + Flight.format((int) ctx.state().get(SINCE) / 20.0)
                + " s, hält wieder die Position");
    }
}
