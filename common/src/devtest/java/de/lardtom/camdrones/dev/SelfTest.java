package de.lardtom.camdrones.dev;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelfTest {
    public static final String PROPERTY = "camdrones.selftest";
    public static final String SMOKE_PROPERTY = "camdrones.smoketest";
    public static final String ONLY_PROPERTY = "camdrones.selftest.only";
    public static final String ONLY_ENV = "CAMDRONES_SELFTEST_ONLY";

    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final boolean SMOKE = !ENABLED && Boolean.getBoolean(SMOKE_PROPERTY);
    private static final Pattern RANGE = Pattern.compile("(\\d+)(?:-(\\d+))?");
    private static final List<Step> STEPS = new ArrayList<>();
    private static final Set<String> NAMES = new HashSet<>();
    private static final Set<Integer> NUMBERS = new HashSet<>();
    private static boolean started;

    private SelfTest() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static boolean smokeEnabled() {
        return SMOKE;
    }

    public static void clientStep(int number, String name, int maxTicks, ClientStep body) {
        add(number, name, maxTicks, body, ctx -> StepResult.pass(), false);
    }

    public static void clientStep(int number, String name, int maxTicks, ClientStep body, ClientStep cleanup) {
        add(number, name, maxTicks, body, cleanup, false);
    }

    public static void baseStep(int number, String name, int maxTicks, ClientStep body) {
        add(number, name, maxTicks, body, ctx -> StepResult.pass(), true);
    }

    private static void add(int number, String name, int maxTicks, ClientStep body, ClientStep cleanup, boolean base) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(cleanup, "cleanup");
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks muss > 0 sein");
        }
        if (started) {
            throw new IllegalStateException("Selbsttest-Schritt '" + name + "' zu spät angemeldet");
        }
        if (!NAMES.add(name)) {
            throw new IllegalArgumentException("Doppelter Selbsttest-Schritt: " + name);
        }
        if (!NUMBERS.add(number)) {
            throw new IllegalArgumentException("Doppelte Schritt-Nummer " + number + ": " + name);
        }
        STEPS.add(new Step(number, name, maxTicks, body, cleanup, base));
        STEPS.sort(Comparator.comparing(Step::base).thenComparingInt(Step::number));
    }

    public static List<Step> all() {
        return Collections.unmodifiableList(STEPS);
    }

    public static @Nullable String filter() {
        String only = System.getProperty(ONLY_PROPERTY);
        if (only == null || only.isBlank()) {
            only = System.getenv(ONLY_ENV);
        }
        return only == null || only.isBlank() ? null : only.trim();
    }

    public static List<Step> startSteps() {
        started = true;
        return select(STEPS, filter());
    }

    public static List<Step> select(List<Step> steps, @Nullable String filter) {
        if (filter == null || filter.isBlank()) {
            return List.copyOf(steps);
        }
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        List<int[]> includeRanges = new ArrayList<>();
        List<int[]> excludeRanges = new ArrayList<>();
        for (String token : filter.split("[,|]")) {
            String prefix = token.trim();
            boolean excluded = prefix.startsWith("!");
            if (excluded) {
                prefix = prefix.substring(1).trim();
            }
            if (prefix.isEmpty()) {
                continue;
            }
            Matcher range = RANGE.matcher(prefix);
            if (range.matches()) {
                int from = Integer.parseInt(range.group(1));
                int to = range.group(2) == null ? from : Integer.parseInt(range.group(2));
                (excluded ? excludeRanges : includeRanges).add(new int[]{Math.min(from, to), Math.max(from, to)});
            } else {
                (excluded ? exclude : include).add(prefix);
            }
        }
        Selection selection = new Selection(include, exclude, includeRanges, excludeRanges);
        boolean any = steps.stream().anyMatch(step -> !step.base() && selection.chosen(step));
        List<Step> kept = new ArrayList<>();
        for (Step step : steps) {
            if (step.base() ? any : selection.chosen(step)) {
                kept.add(step);
            }
        }
        return List.copyOf(kept);
    }

    private record Selection(List<String> include, List<String> exclude, List<int[]> includeRanges, List<int[]> excludeRanges) {
        boolean chosen(Step step) {
            boolean included = this.include.isEmpty() && this.includeRanges.isEmpty() || matches(step, this.include, this.includeRanges);
            return included && !matches(step, this.exclude, this.excludeRanges);
        }

        private static boolean matches(Step step, List<String> prefixes, List<int[]> ranges) {
            return prefixes.stream().anyMatch(prefix -> startsWith(step.name(), prefix))
                    || ranges.stream().anyMatch(range -> step.number() >= range[0] && step.number() <= range[1]);
        }
    }

    private static boolean startsWith(String name, String prefix) {
        return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static Map<String, Object> newState() {
        return new HashMap<>();
    }

    public record Step(int number, String name, int maxTicks, ClientStep body, ClientStep cleanup, boolean base) {
    }

    @FunctionalInterface
    public interface ClientStep {
        StepResult tick(StepContext context);
    }

    public interface StepContext {
        int tick();

        String name();

        @Nullable MinecraftServer server();

        CompletableFuture<Void> onServer(Consumer<MinecraftServer> action);

        Map<String, Object> state();

        void log(String message);
    }

    public record StepResult(Status status, String message) {
        public enum Status { PENDING, PASS, FAIL }

        private static final StepResult PENDING = new StepResult(Status.PENDING, "");
        private static final StepResult PASS = new StepResult(Status.PASS, "");

        public static StepResult pending() {
            return PENDING;
        }

        public static StepResult pass() {
            return PASS;
        }

        public static StepResult pass(String message) {
            return new StepResult(Status.PASS, message);
        }

        public static StepResult fail(String reason) {
            return new StepResult(Status.FAIL, reason);
        }

        public static StepResult check(boolean condition, String reason) {
            return condition ? PASS : fail(reason);
        }

        public boolean done() {
            return this.status != Status.PENDING;
        }

        public boolean failed() {
            return this.status == Status.FAIL;
        }
    }
}
