package de.lardtom.camdrones.bench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamBJmpBuildTest {
    static final String ENV = "CAMDRONES_JMP_PARTS";
    static final String NAMESPACE = "justmoreparts";

    private static List<Path> files(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Identifier id(Path file) {
        String name = file.getFileName().toString();
        return Identifier.fromNamespaceAndPath(NAMESPACE, name.substring(0, name.length() - ".json".length()));
    }

    private static List<Identifier> register(Path root, String category) {
        List<Identifier> out = new ArrayList<>();
        for (Path file : files(root.resolve(category))) {
            Identifier id = id(file);
            if ("chemistry".equals(category)) {
                CellChemistry chemistry = PropwashApi.parts().chemistryFromJson(id, read(file));
                PropwashApi.parts().registerChemistry(chemistry);
            } else {
                PartKind kind = PartKind.byKey(category).orElseThrow();
                PartDefinition definition = PropwashApi.parts().fromJson(kind, id, read(file));
                PropwashApi.parts().register(definition);
            }
            out.add(id);
        }
        return out;
    }

    private static boolean buildable(BuildSpec spec) {
        BuildCheck check = Bench.check(spec);
        return check.buildable();
    }

    @Test
    void tourWithJustMorePartsBatteryMotorAndPropBuildsAndFlies() {
        String dir = System.getenv(ENV);
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path root = Path.of(dir);
        assertTrue(Files.isDirectory(root.resolve("motor")), ENV + " zeigt nicht auf data/justmoreparts/propwash_parts: " + root);
        PartFiles.registerAll();
        register(root, "chemistry");
        List<Identifier> batteries = register(root, "battery");
        List<Identifier> motors = register(root, "motor");
        List<Identifier> props = register(root, "prop");
        BuildSpec tour = CamPresets.TOUR.build();
        assertTrue(buildable(tour), "Tour ohne Fremdteile baubar");
        List<Identifier> fitBatteries = batteries.stream().filter(b -> buildable(tour.withPart(PartKind.BATTERY, b))).toList();
        List<Identifier> fitMotors = motors.stream().filter(m -> buildable(tour.withPart(PartKind.MOTOR, m))).toList();
        List<Identifier> fitProps = props.stream().filter(p -> buildable(tour.withPart(PartKind.PROP, p))).toList();
        System.out.println("Tour mit JMP: " + fitBatteries.size() + " von " + batteries.size() + " Akkus, " + fitMotors.size() + " von "
                + motors.size() + " Motoren, " + fitProps.size() + " von " + props.size() + " Props passen einzeln");
        assertFalse(fitBatteries.isEmpty(), "kein JMP-Akku passt an die Tour");
        assertFalse(fitMotors.isEmpty(), "kein JMP-Motor passt an die Tour");
        assertFalse(fitProps.isEmpty(), "kein JMP-Prop passt an die Tour");
        BuildSpec mixed = null;
        int fewest = Integer.MAX_VALUE;
        double longest = 0.0;
        int combos = 0;
        for (Identifier motor : fitMotors) {
            for (Identifier prop : fitProps) {
                for (Identifier battery : fitBatteries) {
                    BuildSpec candidate = tour.withPart(PartKind.MOTOR, motor).withPart(PartKind.PROP, prop).withPart(PartKind.BATTERY, battery);
                    BuildCheck check = Bench.check(candidate);
                    if (!check.buildable()) {
                        continue;
                    }
                    combos++;
                    BuildStats stats = Bench.stats(candidate);
                    int warnings = check.warnings().size() + stats.warnings().size();
                    if (warnings < fewest || warnings == fewest && stats.mixedFlightTimeMin() > longest) {
                        fewest = warnings;
                        longest = stats.mixedFlightTimeMin();
                        mixed = candidate;
                    }
                }
            }
        }
        assertTrue(mixed != null, "keine baubare Tour mit JMP-Akku, -Motor und -Prop zugleich");
        System.out.println(combos + " baubare Kombinationen, beste mit " + fewest + " Warnungen");
        assertTrue(fewest <= 3, "jede Tour mit JMP-Akku, -Motor und -Prop hat mehr als 3 Warnungen, beste: " + Bench.row("Tour+JMP", mixed));
        BuildStats stock = Bench.stats(tour);
        BuildStats jmp = Bench.stats(mixed);
        System.out.println(Bench.header());
        System.out.println(Bench.row("Tour", tour));
        System.out.println(Bench.row("Tour+JMP", mixed));
        System.out.println(String.format(Locale.ROOT, "Tour+JMP: Motor %s, Prop %s, Akku %s", mixed.motor(), mixed.prop(), mixed.battery()));
        assertNotEquals(stock.massGrams(), jmp.massGrams(), "Masse ändert sich mit den JMP-Teilen");
        assertTrue(jmp.massGrams() > 300.0 && jmp.massGrams() < 3000.0, "Masse " + jmp.massGrams() + " g");
        assertTrue(jmp.thrustToWeight() > 1.5, "Schub/Gewicht " + jmp.thrustToWeight());
        assertTrue(jmp.hoverFlightTimeMin() > 5.0 && jmp.hoverFlightTimeMin() < 90.0, "Schwebezeit " + jmp.hoverFlightTimeMin() + " min");
        assertTrue(jmp.topSpeedKmh() > 20.0 && jmp.topSpeedKmh() < 250.0, "Höchsttempo " + jmp.topSpeedKmh() + " km/h");
        assertTrue(jmp.hoverThrottlePercent() > 5.0 && jmp.hoverThrottlePercent() < 70.0, "Schwebegas " + jmp.hoverThrottlePercent() + " %");
        double massRatio = jmp.massGrams() / stock.massGrams();
        assertTrue(massRatio > 0.5 && massRatio < 2.0, "Masse springt um den Faktor " + massRatio);
        BuildSpec base = mixed;
        Map<Integer, List<Identifier>> byCells = new TreeMap<>();
        for (Identifier battery : fitBatteries) {
            JsonObject json = read(root.resolve("battery").resolve(battery.getPath() + ".json"));
            if (Bench.check(base.withPart(PartKind.BATTERY, battery)).buildable()) {
                byCells.computeIfAbsent(json.get("cells").getAsInt(), key -> new ArrayList<>()).add(battery);
            }
        }
        int compared = 0;
        for (Map.Entry<Integer, List<Identifier>> group : byCells.entrySet()) {
            List<Identifier> sorted = group.getValue().stream()
                    .sorted(Comparator.comparingInt(b -> read(root.resolve("battery").resolve(b.getPath() + ".json")).get("capacity_mah").getAsInt()))
                    .toList();
            if (sorted.size() < 2) {
                continue;
            }
            BuildSpec small = base.withPart(PartKind.BATTERY, sorted.getFirst());
            BuildSpec large = base.withPart(PartKind.BATTERY, sorted.getLast());
            BuildStats smallStats = Bench.stats(small);
            BuildStats largeStats = Bench.stats(large);
            System.out.println(String.format(Locale.ROOT, "%dS: %s %.1f g %.1f min, %s %.1f g %.1f min", group.getKey(), sorted.getFirst().getPath(),
                    smallStats.massGrams(), smallStats.hoverFlightTimeMin(), sorted.getLast().getPath(), largeStats.massGrams(),
                    largeStats.hoverFlightTimeMin()));
            assertTrue(largeStats.massGrams() > smallStats.massGrams(), group.getKey() + "S: größerer Akku ist schwerer");
            assertTrue(largeStats.hoverFlightTimeMin() > smallStats.hoverFlightTimeMin(), group.getKey() + "S: größerer Akku schwebt länger");
            compared++;
        }
        assertTrue(compared > 0, "keine zwei JMP-Akkus gleicher Zellzahl an der Tour vergleichbar");
    }
}
