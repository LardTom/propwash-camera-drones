package de.lardtom.camdrones.integration.autonomy;

public final class FollowPolicy {
    public enum Verdict {
        ALLOWED,
        NEEDS_CONSENT,
        RULE_OFF,
        NOT_ALLOWED;

        public AutonomyStatus status() {
            return switch (this) {
                case ALLOWED -> AutonomyStatus.FOLLOW_STARTED;
                case NEEDS_CONSENT -> AutonomyStatus.FOLLOW_REQUESTED;
                case RULE_OFF -> AutonomyStatus.FOLLOW_RULE_OFF;
                case NOT_ALLOWED -> AutonomyStatus.FOLLOW_NOT_ALLOWED;
            };
        }
    }

    public record Target(boolean owner, boolean player, boolean ownedAnimal, boolean ownersMount, boolean ridersWithoutConsent) {
        public static Target self() {
            return new Target(true, true, false, false, false);
        }

        public static Target otherPlayer() {
            return new Target(false, true, false, false, false);
        }
    }

    private FollowPolicy() {
    }

    public static Verdict check(Target target, boolean allowFollowPlayers, boolean consent) {
        if (target.owner()) {
            return Verdict.ALLOWED;
        }
        if (target.player()) {
            if (!allowFollowPlayers) {
                return Verdict.RULE_OFF;
            }
            return consent ? Verdict.ALLOWED : Verdict.NEEDS_CONSENT;
        }
        return (target.ownedAnimal() || target.ownersMount()) && !target.ridersWithoutConsent() ? Verdict.ALLOWED : Verdict.NOT_ALLOWED;
    }
}
