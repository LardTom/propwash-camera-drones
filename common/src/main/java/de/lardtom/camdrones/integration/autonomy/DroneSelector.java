package de.lardtom.camdrones.integration.autonomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DroneSelector {
    public static final int MIN_SERIAL_PREFIX = 4;
    public static final int SHORT_SERIAL = 8;

    public record Candidate<T>(T handle, UUID serial, String name) {
        public Candidate {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(serial, "serial");
            name = name == null ? "" : name.strip();
        }

        public String label() {
            return DroneSelector.label(this.serial, this.name);
        }
    }

    public enum Outcome {
        FOUND,
        NONE,
        AMBIGUOUS
    }

    public record Result<T>(Outcome outcome, Optional<Candidate<T>> chosen, List<Candidate<T>> matches) {
        public Result {
            chosen = Objects.requireNonNullElse(chosen, Optional.empty());
            matches = List.copyOf(matches);
        }

        static <T> Result<T> of(List<Candidate<T>> matches) {
            if (matches.size() == 1) {
                return new Result<>(Outcome.FOUND, Optional.of(matches.getFirst()), matches);
            }
            return new Result<>(matches.isEmpty() ? Outcome.NONE : Outcome.AMBIGUOUS, Optional.empty(), matches);
        }
    }

    private DroneSelector() {
    }

    public static String shortSerial(UUID serial) {
        String text = serial.toString();
        return text.substring(0, Math.min(SHORT_SERIAL, text.length())).toUpperCase(Locale.ROOT);
    }

    public static String label(UUID serial, String name) {
        return name == null || name.isBlank() ? shortSerial(serial) : name.strip();
    }

    public static boolean matches(Candidate<?> candidate, String query) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) {
            return false;
        }
        if (!candidate.name().isEmpty() && candidate.name().equalsIgnoreCase(q)) {
            return true;
        }
        if (q.length() < MIN_SERIAL_PREFIX) {
            return false;
        }
        String serial = candidate.serial().toString().toLowerCase(Locale.ROOT);
        String lower = q.toLowerCase(Locale.ROOT);
        return serial.startsWith(lower) || serial.replace("-", "").startsWith(lower.replace("-", ""));
    }

    public static <T> Result<T> select(List<Candidate<T>> candidates, Optional<String> query, Optional<UUID> held) {
        List<Candidate<T>> all = List.copyOf(candidates);
        if (query.isPresent() && !query.get().isBlank()) {
            List<Candidate<T>> exact = new ArrayList<>();
            for (Candidate<T> candidate : all) {
                if (!candidate.name().isEmpty() && candidate.name().equalsIgnoreCase(query.get().strip())) {
                    exact.add(candidate);
                }
            }
            if (!exact.isEmpty()) {
                return Result.of(exact);
            }
            List<Candidate<T>> found = new ArrayList<>();
            for (Candidate<T> candidate : all) {
                if (matches(candidate, query.get())) {
                    found.add(candidate);
                }
            }
            return Result.of(found);
        }
        if (held.isPresent()) {
            for (Candidate<T> candidate : all) {
                if (candidate.serial().equals(held.get())) {
                    return Result.of(List.of(candidate));
                }
            }
        }
        return Result.of(all);
    }

    public static List<String> suggestions(List<? extends Candidate<?>> candidates) {
        List<String> out = new ArrayList<>();
        for (Candidate<?> candidate : candidates) {
            String name = candidate.name();
            out.add(!name.isEmpty() && name.indexOf(' ') < 0 ? name : shortSerial(candidate.serial()));
        }
        return out;
    }
}
