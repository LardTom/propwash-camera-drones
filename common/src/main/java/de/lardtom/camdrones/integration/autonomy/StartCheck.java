package de.lardtom.camdrones.integration.autonomy;

import java.util.Objects;
import java.util.Optional;

public final class StartCheck {
    public static final int FIX_SATELLITES = 5;
    public static final double OPEN_SKY_SATELLITES = 18.0;
    public static final double SKY_EXPONENT = 1.6;
    public static final double FIX_SKY_VIEW = Math.pow(FIX_SATELLITES / OPEN_SKY_SATELLITES, 1.0 / SKY_EXPONENT);
    public static final double EMPTY_CHARGE = 0.03;
    public static final double RESERVE_CHARGE = 0.15;

    public record Battery(boolean known, boolean installed, double charge) {
        public static final Battery UNKNOWN = new Battery(false, false, Double.NaN);
        public static final Battery NONE = new Battery(true, false, 0.0);

        public static Battery charged(double charge) {
            return Double.isFinite(charge) ? new Battery(true, true, Math.clamp(charge, 0.0, 1.0)) : UNKNOWN;
        }
    }

    public record Refusal(AutonomyStatus status, AutonomyStatus.Reason reason) {
        public Refusal {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
        }

        public static Refusal of(AutonomyStatus.Reason reason) {
            return new Refusal(reason.status(), reason);
        }
    }

    private StartCheck() {
    }

    public static int satellites(double skyView) {
        double sky = Double.isFinite(skyView) ? Math.clamp(skyView, 0.0, 1.0) : 1.0;
        return (int) Math.floor(OPEN_SKY_SATELLITES * Math.pow(sky, SKY_EXPONENT) + 1.0E-9);
    }

    public static boolean gpsFix(double skyView) {
        return satellites(skyView) >= FIX_SATELLITES;
    }

    public static boolean needsChecks(AutonomyPlan.Kind kind) {
        return kind != AutonomyPlan.Kind.LAND && kind != AutonomyPlan.Kind.HOME;
    }

    public static Optional<Refusal> check(boolean airborne, AutonomyPlan.Kind kind, Battery battery, double skyView) {
        Objects.requireNonNull(kind, "kind");
        if (!needsChecks(kind)) {
            return Optional.empty();
        }
        Battery reading = Objects.requireNonNullElse(battery, Battery.UNKNOWN);
        if (!airborne && reading.known()) {
            if (!reading.installed()) {
                return Optional.of(Refusal.of(AutonomyStatus.Reason.NO_BATTERY));
            }
            if (reading.charge() <= EMPTY_CHARGE) {
                return Optional.of(Refusal.of(AutonomyStatus.Reason.BATTERY_EMPTY));
            }
            if (reading.charge() < RESERVE_CHARGE) {
                return Optional.of(Refusal.of(AutonomyStatus.Reason.BATTERY_LOW));
            }
        }
        if (!gpsFix(skyView)) {
            return Optional.of(Refusal.of(AutonomyStatus.Reason.GPS));
        }
        return Optional.empty();
    }
}
