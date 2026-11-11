package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.logic.nav.HeightLimit;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.List;

public final class CamWorldRules {
    public static final Identifier ALLOW_AUTONOMY_ID = CamDrones.id("allow_autonomy");
    public static final Identifier ALLOW_FOLLOW_PLAYERS_ID = CamDrones.id("allow_follow_players");
    public static final Identifier ALLOW_OPERATOR_ID = CamDrones.id("allow_operator");
    public static final Identifier MAX_ALTITUDE_ID = CamDrones.id("max_altitude");
    public static final Identifier MAX_DISTANCE_ID = CamDrones.id("max_distance");
    public static final Identifier MAX_AUTONOMY_MINUTES_ID = CamDrones.id("max_autonomy_minutes");
    public static final List<Identifier> IDS = List.of(ALLOW_AUTONOMY_ID, ALLOW_FOLLOW_PLAYERS_ID, ALLOW_OPERATOR_ID, MAX_ALTITUDE_ID,
            MAX_DISTANCE_ID, MAX_AUTONOMY_MINUTES_ID);

    public static final boolean DEFAULT_ALLOW_AUTONOMY = true;
    public static final boolean DEFAULT_ALLOW_FOLLOW_PLAYERS = false;
    public static final boolean DEFAULT_ALLOW_OPERATOR = true;
    public static final int DEFAULT_MAX_ALTITUDE = 120;
    public static final int MAX_ALTITUDE_LIMIT = 512;
    public static final int DEFAULT_MAX_DISTANCE = 0;
    public static final int MAX_DISTANCE_LIMIT = 4096;
    public static final int MIN_AUTONOMY_MINUTES = 1;
    public static final int MAX_AUTONOMY_MINUTES = (int) AutonomyOptions.MAX_DURATION.toMinutes();
    public static final int DEFAULT_AUTONOMY_MINUTES = MAX_AUTONOMY_MINUTES;

    private static volatile WorldRule<Boolean> allowAutonomy;
    private static volatile WorldRule<Boolean> allowFollowPlayers;
    private static volatile WorldRule<Boolean> allowOperator;
    private static volatile WorldRule<Integer> maxAltitude;
    private static volatile WorldRule<Integer> maxDistance;
    private static volatile WorldRule<Integer> maxAutonomyMinutes;

    private CamWorldRules() {
    }

    public record Registered(WorldRule<Boolean> allowAutonomy, WorldRule<Boolean> allowFollowPlayers, WorldRule<Boolean> allowOperator,
                             WorldRule<Integer> maxAltitude, WorldRule<Integer> maxDistance, WorldRule<Integer> maxAutonomyMinutes) {
        public List<WorldRule<?>> all() {
            return List.of(this.allowAutonomy, this.allowFollowPlayers, this.allowOperator, this.maxAltitude, this.maxDistance,
                    this.maxAutonomyMinutes);
        }
    }

    public static Registered create(WorldRules rules) {
        Registered registered = new Registered(
                rules.registerBoolean(ALLOW_AUTONOMY_ID, DEFAULT_ALLOW_AUTONOMY),
                rules.registerBoolean(ALLOW_FOLLOW_PLAYERS_ID, DEFAULT_ALLOW_FOLLOW_PLAYERS),
                rules.registerBoolean(ALLOW_OPERATOR_ID, DEFAULT_ALLOW_OPERATOR),
                rules.registerInt(MAX_ALTITUDE_ID, DEFAULT_MAX_ALTITUDE, 0, MAX_ALTITUDE_LIMIT),
                rules.registerInt(MAX_DISTANCE_ID, DEFAULT_MAX_DISTANCE, 0, MAX_DISTANCE_LIMIT),
                rules.registerInt(MAX_AUTONOMY_MINUTES_ID, DEFAULT_AUTONOMY_MINUTES, MIN_AUTONOMY_MINUTES, MAX_AUTONOMY_MINUTES));
        for (WorldRule<?> rule : registered.all()) {
            rules.raceNeutral(rule);
        }
        return registered;
    }

    public static synchronized void register(WorldRules rules) {
        if (maxAutonomyMinutes != null) {
            return;
        }
        Registered registered = create(rules);
        allowAutonomy = registered.allowAutonomy();
        allowFollowPlayers = registered.allowFollowPlayers();
        allowOperator = registered.allowOperator();
        maxAltitude = registered.maxAltitude();
        maxDistance = registered.maxDistance();
        maxAutonomyMinutes = registered.maxAutonomyMinutes();
    }

    public static void init() {
        register(PropwashApi.worldRules());
    }

    public static synchronized List<WorldRule<?>> registered() {
        return maxAutonomyMinutes == null ? List.of()
                : List.of(allowAutonomy, allowFollowPlayers, allowOperator, maxAltitude, maxDistance, maxAutonomyMinutes);
    }

    private static <T> T read(WorldRule<T> rule, Level level, T fallback) {
        if (rule == null || level == null) {
            return fallback;
        }
        T value = rule.get(level);
        return value == null ? fallback : value;
    }

    public static boolean allowAutonomy(Level level) {
        return read(allowAutonomy, level, DEFAULT_ALLOW_AUTONOMY);
    }

    public static boolean allowFollowPlayers(Level level) {
        return read(allowFollowPlayers, level, DEFAULT_ALLOW_FOLLOW_PLAYERS);
    }

    public static boolean allowOperator(Level level) {
        return read(allowOperator, level, DEFAULT_ALLOW_OPERATOR);
    }

    public static int maxAltitude(Level level) {
        return Math.clamp(read(maxAltitude, level, DEFAULT_MAX_ALTITUDE), 0, MAX_ALTITUDE_LIMIT);
    }

    public static int maxDistance(Level level) {
        return Math.clamp(read(maxDistance, level, DEFAULT_MAX_DISTANCE), 0, MAX_DISTANCE_LIMIT);
    }

    public static int maxAutonomyMinutes(Level level) {
        return Math.clamp(read(maxAutonomyMinutes, level, DEFAULT_AUTONOMY_MINUTES), MIN_AUTONOMY_MINUTES, MAX_AUTONOMY_MINUTES);
    }

    public static HeightLimit limit(double homeX, double homeY, double homeZ, int maxAltitude, int maxDistance) {
        if (maxAltitude <= 0 && maxDistance <= 0) {
            return HeightLimit.off();
        }
        return new HeightLimit(homeX, homeY, homeZ, maxAltitude, maxDistance);
    }
}
