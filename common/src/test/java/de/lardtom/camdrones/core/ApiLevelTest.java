package de.lardtom.camdrones.core;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiLevelTest {
    private static final String BASE = "1.7";

    private record Since(String version, Class<?> owner, String name, String descriptor) {
        String ownerName() {
            return this.owner.getName().replace('.', '/');
        }

        boolean matches(MemberRefEntry ref) {
            return ref.owner().asInternalName().equals(this.ownerName()) && ref.name().stringValue().equals(this.name)
                    && (this.descriptor == null || ref.type().stringValue().equals(this.descriptor));
        }

        String label() {
            return this.owner.getSimpleName() + "." + this.name + (this.descriptor == null ? "" : this.descriptor);
        }
    }

    private static final List<Since> NEWER = List.of(
            new Since("1.8", AccessoryRegistry.class, "cargo", null),
            new Since("1.8", AccessoryRegistry.class, "grabCargo", null),
            new Since("1.8", AccessoryRegistry.class, "releaseCargo", null),
            new Since("1.8", AccessoryDefinition.class, "recessMm", null),
            new Since("1.8", AccessoryDefinition.class, "dropMm", null),
            new Since("1.8", AccessoryDefinition.Builder.class, "recess", null),
            new Since("1.8", AccessoryDefinition.Builder.class, "drop", null),
            new Since("1.8", AccessoryDefinition.class, "<init>", canonical(AccessoryDefinition.class)));

    private static String canonical(Class<? extends Record> type) {
        Class<?>[] components = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new);
        return MethodType.methodType(void.class, components).toMethodDescriptorString();
    }

    private static int minor(String version) {
        String[] parts = version.split("\\.");
        assertEquals("1", parts[0], version);
        return Integer.parseInt(parts[1]);
    }

    private static Path mainClasses() throws URISyntaxException {
        Path location = Path.of(CamDrones.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(location), "Klassen des Add-ons nicht als Ordner: " + location);
        return location;
    }

    private static Map<String, List<MemberRefEntry>> apiRefs() throws IOException, URISyntaxException {
        Path root = mainClasses();
        Map<String, List<MemberRefEntry>> refs = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "keine Klassen unter " + root);
        for (Path file : files) {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
            List<MemberRefEntry> found = new ArrayList<>();
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof MemberRefEntry ref && ref.owner().asInternalName().startsWith("de/lardtom/propwash/api/")) {
                    found.add(ref);
                }
            }
            if (!found.isEmpty()) {
                refs.put(model.thisClass().asInternalName().replace('/', '.'), found);
            }
        }
        return refs;
    }

    @Test
    void everyNewerApiMemberExistsInTheCompiledApi() throws NoSuchMethodException {
        assertTrue(minor(PropwashApi.API_VERSION) >= NEWER.stream().mapToInt(s -> minor(s.version())).max().orElse(minor(BASE)), PropwashApi.API_VERSION);
        for (Since since : NEWER) {
            assertTrue(minor(since.version()) > minor(BASE), since.label());
            if (since.name().equals("<init>")) {
                Constructor<?> constructor = since.owner().getConstructor(Arrays.stream(since.owner().getRecordComponents())
                        .map(RecordComponent::getType).toArray(Class<?>[]::new));
                assertTrue(Modifier.isPublic(constructor.getModifiers()), since.label());
                continue;
            }
            List<Method> methods = Arrays.stream(since.owner().getMethods()).filter(m -> m.getName().equals(since.name())).toList();
            assertFalse(methods.isEmpty(), "nicht in der API: " + since.label());
        }
    }

    @Test
    void requiredApiCoversEveryApiMemberTheAddonCalls() throws IOException, URISyntaxException {
        Map<String, List<MemberRefEntry>> refs = apiRefs();
        assertTrue(refs.getOrDefault(CamDrones.class.getName(), List.of()).stream()
                        .anyMatch(ref -> ref.owner().asInternalName().equals("de/lardtom/propwash/api/PropwashApi")
                                && ref.name().stringValue().equals("requireVersion")),
                "Prüfung liest die Klassen nicht: CamDrones ohne PropwashApi.requireVersion");
        int needed = minor(BASE);
        TreeSet<String> newer = new TreeSet<>();
        for (Map.Entry<String, List<MemberRefEntry>> entry : refs.entrySet()) {
            for (MemberRefEntry ref : entry.getValue()) {
                for (Since since : NEWER) {
                    if (since.matches(ref)) {
                        needed = Math.max(needed, minor(since.version()));
                        newer.add("API " + since.version() + ": " + since.owner().getSimpleName() + "." + since.name() + " in "
                                + entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1));
                    }
                }
            }
        }
        String required = CamDrones.REQUIRED_API;
        assertTrue(minor(required) >= needed, "REQUIRED_API " + required + " lässt Propwash mit API " + required + ".x zu, dort fehlen "
                + newer + " (NoSuchMethodError im Spiel); nötig ist mindestens 1." + needed);
    }
}
