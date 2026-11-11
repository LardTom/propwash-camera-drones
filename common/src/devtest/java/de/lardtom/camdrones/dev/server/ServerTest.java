package de.lardtom.camdrones.dev.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.dev.AwardProbe;
import de.lardtom.camdrones.dev.DevResult;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerTest {
    public static final String TAG = "[Kamera-Servertest]";
    public static final int RUN_TICKS = 100;
    public static final int LINGER_TICKS = 20 * 60;
    public static final long MAX_MILLIS = 30L * 60L * 1000L;
    private static final List<String> REPORT = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> FAILURES = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicBoolean FINISHED = new AtomicBoolean();
    private static final String CASE = JoinEnv.serverCase();
    private static final int HOLD = JoinEnv.holdSeconds();
    private static volatile @Nullable MinecraftServer current;
    private static @Nullable Scenario scenario;
    private static int ticks;
    private static int doneAt = -1;
    private static long started;

    private ServerTest() {
    }

    public static String caseName() {
        return CASE.isEmpty() ? "Rauchtest" : CASE;
    }

    public static void line(String text) {
        REPORT.add(text);
        CamDrones.LOG.info("{} {}", TAG, text);
    }

    public static void fail(String text) {
        FAILURES.add(text);
        REPORT.add("FAIL " + text);
        CamDrones.LOG.warn("{} FAIL {}", TAG, text);
    }

    static void tick(MinecraftServer server) {
        if (FINISHED.get()) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            current = server;
            started = System.currentTimeMillis();
            start(server);
        }
        Scenario active = scenario;
        if (active != null && !active.finished()) {
            try {
                active.tick(server, ticks);
            } catch (RuntimeException e) {
                CamDrones.LOG.error("{} Fall {} warf", TAG, CASE, e);
                fail("Fall " + CASE + " warf " + e);
                active.abort();
            }
        }
        if (active != null && active.finished()) {
            if (doneAt < 0) {
                doneAt = ticks;
            }
            if (ServerSetup.client(server) == null || ticks - doneAt > LINGER_TICKS) {
                finish(server, "Fall abgeschlossen");
            }
        } else if (active == null && HOLD == 0 && ticks >= RUN_TICKS) {
            finish(server, "Rauchtest ohne Halten");
        }
        if (System.currentTimeMillis() - started > MAX_MILLIS) {
            fail("Zeitüberschreitung nach " + MAX_MILLIS / 60000L + " min");
            finish(server, "Zeitüberschreitung");
        }
    }

    static void onEnded(Entity drone, UUID owner, Identifier id, AutonomyEnd reason) {
        Scenario active = scenario;
        if (active != null) {
            active.onEnded(drone, owner, id, reason);
        }
    }

    private static void start(MinecraftServer server) {
        line("Server gestartet (" + Services.PLATFORM.name() + ", Propwash-API " + PropwashApi.API_VERSION + "), Fall " + caseName()
                + (HOLD > 0 ? ", hält bis " + HOLD + " s oder " + JoinEnv.STOP_FILE : ""));
        checks(server);
        scenario = switch (CASE) {
            case JoinEnv.MISSION -> new MissionCheck();
            case JoinEnv.CREW -> new CrewCheck();
            case JoinEnv.FOLLOW -> new FollowCheck();
            case JoinEnv.PERSIST_WRITE -> new PersistCheck(true);
            case JoinEnv.PERSIST_CHECK -> new PersistCheck(false);
            case CovCamAJoinCheck.CASE -> new CovCamAJoinCheck();
            case CovCamBJoinCheck.CASE -> new CovCamBJoinCheck(false);
            case JoinEnv.CREW_REJOIN -> new CrewRejoinCheck();
            case AwardJoinCheck.CASE -> new AwardJoinCheck();
            default -> null;
        };
        if (HOLD > 0) {
            ServerSetup.command(server, "whitelist off");
            ServerSetup.command(server, "difficulty peaceful");
            ServerSetup.command(server, "gamerule minecraft:advance_time false");
            Thread watcher = new Thread(() -> watch(server), "Camdrones-Servertest-Watcher");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    private static void watch(MinecraftServer server) {
        Path stop = Services.PLATFORM.gameDir().resolve(JoinEnv.STOP_FILE);
        long until = System.currentTimeMillis() + HOLD * 1000L;
        while (!FINISHED.get()) {
            if (Files.exists(stop)) {
                try {
                    Files.deleteIfExists(stop);
                } catch (IOException e) {
                    CamDrones.LOG.warn("{} {} nicht gelöscht", TAG, stop);
                }
                finish(server, JoinEnv.STOP_FILE);
                return;
            }
            if (System.currentTimeMillis() > until) {
                finish(server, "Haltezeit vorbei");
                return;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void checks(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            fail("kein dedizierter Server");
        }
        if (!CamDrones.initialized()) {
            fail("Add-on nicht initialisiert");
        }
        int parts = 0;
        for (PartDefinition definition : PropwashApi.parts().all()) {
            if (CamDrones.MOD_ID.equals(definition.id().getNamespace())) {
                parts++;
                if (PropwashApi.partItems().item(definition.id()).isEmpty()) {
                    fail("Teil ohne Item: " + definition.id());
                }
            }
        }
        if (parts < 19) {
            fail("nur " + parts + " Teile des Add-ons angemeldet");
        }
        List<Identifier> presets = PropwashApi.builds().presets().stream().map(DronePreset::id).filter(id -> CamDrones.MOD_ID.equals(id.getNamespace()))
                .toList();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            if (!presets.contains(entry.id())) {
                fail("Preset " + entry.id() + " fehlt");
            }
        }
        List<WorldRule<?>> rules = CamWorldRules.registered();
        if (rules.size() != CamWorldRules.IDS.size()) {
            fail(rules.size() + " Welt-Regeln statt " + CamWorldRules.IDS.size());
        }
        for (Identifier id : CamWorldRules.IDS) {
            if (PropwashApi.worldRules().find(id).isEmpty()) {
                fail("Welt-Regel " + id + " fehlt bei Propwash");
            }
        }
        if (!CamDroneData.registered() || !CrewData.registered()) {
            fail("Drohnen-Daten nicht angemeldet");
        }
        if (PlannerItems.flightPlanner().isEmpty()) {
            fail("Flugplaner fehlt");
        }
        if (!AutonomyServer.initialized()) {
            fail("Flüge ohne Pilot nicht angemeldet");
        }
        if (server.getCommands().getDispatcher().getRoot().getChild(CamDrones.MOD_ID) == null) {
            fail("Befehl /" + CamDrones.MOD_ID + " fehlt");
        } else {
            for (String command : List.of("camdrones mission list", "gamerule camdrones:allow_autonomy", "gamerule camdrones:max_autonomy_minutes")) {
                try {
                    server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSuppressedOutput());
                } catch (CommandSyntaxException | RuntimeException e) {
                    fail("/" + command + ": " + e.getMessage());
                }
            }
        }
        line("Rauchtest: " + parts + " Teile mit Items, " + presets.size() + " Presets, " + rules.size() + " Welt-Regeln ("
                + String.join(", ", CamWorldRules.IDS.stream().map(Identifier::getPath).toList()) + "), Drohnen-Daten, Flugplaner, "
                + "Flüge ohne Pilot und /camdrones angemeldet");
        try {
            line("Rauchtest: " + AwardProbe.loaded(server));
        } catch (IllegalStateException e) {
            fail(e.getMessage());
        }
    }

    public static synchronized void finish(MinecraftServer server, String why) {
        if (FINISHED.getAndSet(true)) {
            return;
        }
        Scenario active = scenario;
        if (active != null) {
            String missing = active.missing();
            if (missing != null) {
                fail(CASE + ": " + missing);
            }
        }
        String result;
        List<String> failures = List.copyOf(FAILURES);
        if (failures.isEmpty()) {
            result = "PASS dedizierter Server (" + Services.PLATFORM.name() + ", Fall " + caseName() + ", " + ticks + " Ticks, Ende: " + why + ")";
        } else {
            result = "FAIL " + String.join(" | ", failures);
        }
        REPORT.add(result);
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(JoinEnv.REPORT_FILE), String.join(System.lineSeparator(), List.copyOf(REPORT))
                    + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.warn("{} Bericht nicht geschrieben: {}", TAG, e.toString());
        }
        DevResult.write(result);
        CamDrones.LOG.info("{} {}", TAG, result);
        server.halt(false);
    }

    abstract static class Scenario {
        abstract void tick(MinecraftServer server, int tick);

        void onEnded(Entity drone, UUID owner, Identifier id, AutonomyEnd reason) {
        }

        abstract boolean done();

        abstract @Nullable String missing();

        boolean aborted;

        void abort() {
            this.aborted = true;
        }

        boolean finished() {
            return this.aborted || this.done();
        }
    }
}
