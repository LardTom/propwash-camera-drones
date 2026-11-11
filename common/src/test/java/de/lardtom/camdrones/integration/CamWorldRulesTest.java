package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.HeightLimit;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamWorldRulesTest {
    private record Rule<T>(Identifier id, Type type, T defaultValue, int min, int max) implements WorldRule<T> {
        @Override
        public T get(Level level) {
            return this.defaultValue;
        }
    }

    private static final class Rules implements WorldRules {
        final List<WorldRule<?>> rules = new ArrayList<>();

        @Override
        public WorldRule<Boolean> registerBoolean(Identifier id, boolean defaultValue) {
            WorldRule<Boolean> rule = new Rule<>(id, WorldRule.Type.BOOLEAN, defaultValue, 0, 1);
            this.rules.add(rule);
            return rule;
        }

        @Override
        public WorldRule<Integer> registerInt(Identifier id, int defaultValue, int min, int max) {
            WorldRule<Integer> rule = new Rule<>(id, WorldRule.Type.INTEGER, defaultValue, min, max);
            this.rules.add(rule);
            return rule;
        }

        @Override
        public <T> void presetValue(WorldRule<T> rule, String presetId, T value) {
            throw new AssertionError("keine Voreinstellungen im Design");
        }

        final List<WorldRule<?>> neutral = new ArrayList<>();

        @Override
        public void raceNeutral(WorldRule<?> rule) {
            this.neutral.add(rule);
        }

        @Override
        public boolean raceRelevant(WorldRule<?> rule) {
            return !this.neutral.contains(rule);
        }

        @Override
        public List<WorldRule<?>> registered() {
            return List.copyOf(this.rules);
        }

        @Override
        public Optional<WorldRule<?>> find(Identifier id) {
            return Optional.empty();
        }
    }

    @Test
    void rulesMatchTheDesignTable() {
        Rules rules = new Rules();
        CamWorldRules.Registered registered = CamWorldRules.create(rules);
        assertEquals(6, rules.rules.size());
        assertEquals(CamWorldRules.IDS, rules.rules.stream().map(WorldRule::id).toList());
        assertEquals(List.of("camdrones:allow_autonomy", "camdrones:allow_follow_players", "camdrones:allow_operator", "camdrones:max_altitude",
                "camdrones:max_distance", "camdrones:max_autonomy_minutes"), CamWorldRules.IDS.stream().map(Identifier::toString).toList());
        check(rules.rules.get(0), WorldRule.Type.BOOLEAN, true, 0, 1);
        check(rules.rules.get(1), WorldRule.Type.BOOLEAN, false, 0, 1);
        check(rules.rules.get(2), WorldRule.Type.BOOLEAN, true, 0, 1);
        check(rules.rules.get(3), WorldRule.Type.INTEGER, 120, 0, 512);
        check(rules.rules.get(4), WorldRule.Type.INTEGER, 0, 0, 4096);
        check(rules.rules.get(5), WorldRule.Type.INTEGER, 60, 1, 60);
        for (WorldRule<?> rule : rules.rules) {
            assertEquals("gamerule.camdrones." + rule.id().getPath(), rule.nameKey());
        }
        assertEquals(rules.rules, registered.all());
        assertEquals(rules.rules, rules.neutral, "Regeln der Kamera-Drohnen wirken nicht auf FPV-Rennen und sind rennneutral");
        for (WorldRule<?> rule : rules.rules) {
            assertFalse(rules.raceRelevant(rule), rule.id().toString());
        }
    }

    private static void check(WorldRule<?> rule, WorldRule.Type type, Object value, int min, int max) {
        assertEquals(type, rule.type(), rule.id().toString());
        assertEquals(value, rule.defaultValue(), rule.id().toString());
        assertEquals(min, rule.min(), rule.id().toString());
        assertEquals(max, rule.max(), rule.id().toString());
    }

    @Test
    void readersFallBackToTheDefaultsWithoutLevel() {
        assertTrue(CamWorldRules.allowAutonomy(null));
        assertFalse(CamWorldRules.allowFollowPlayers(null));
        assertTrue(CamWorldRules.allowOperator(null));
        assertEquals(120, CamWorldRules.maxAltitude(null));
        assertEquals(0, CamWorldRules.maxDistance(null));
        assertEquals(60, CamWorldRules.maxAutonomyMinutes(null));
    }

    @Test
    void limitIsOffOnlyWhenBothRulesAreZero() {
        assertSame(HeightLimit.off(), CamWorldRules.limit(0.0, 64.0, 0.0, 0, 0));
        HeightLimit altitude = CamWorldRules.limit(10.0, 64.0, -5.0, 120, 0);
        assertTrue(altitude.active());
        assertEquals(184.0, altitude.ceiling(), 1.0E-9);
        HeightLimit distance = CamWorldRules.limit(10.0, 64.0, -5.0, 0, 300);
        assertEquals(300.0, distance.maxDistance(), 1.0E-9);
        assertEquals(Double.POSITIVE_INFINITY, distance.ceiling());
    }
}
