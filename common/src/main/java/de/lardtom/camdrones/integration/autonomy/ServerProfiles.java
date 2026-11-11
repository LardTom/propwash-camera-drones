package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.NavAssist;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.part.GimbalTable;
import de.lardtom.camdrones.part.SensorTable;
import de.lardtom.propwash.api.flight.SensorDirection;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.Set;

public final class ServerProfiles {
    private ServerProfiles() {
    }

    public static FlightClass flightClass(double massKg, Optional<Identifier> front) {
        Optional<FlightClass> byGimbal = front.flatMap(ServerProfiles::byGimbal);
        if (byGimbal.isPresent()) {
            return byGimbal.get();
        }
        if (!(massKg > 0.0) || massKg < NavAssist.COMPACT_MAX_KG) {
            return FlightClass.COMPACT;
        }
        return massKg < NavAssist.TOUR_MAX_KG ? FlightClass.TOUR : FlightClass.CINE;
    }

    private static Optional<FlightClass> byGimbal(Identifier accessory) {
        if (GimbalTable.COMPACT.equals(accessory)) {
            return Optional.of(FlightClass.COMPACT);
        }
        if (GimbalTable.TOUR.equals(accessory)) {
            return Optional.of(FlightClass.TOUR);
        }
        if (GimbalTable.CINE.equals(accessory)) {
            return Optional.of(FlightClass.CINE);
        }
        return Optional.empty();
    }

    public static Set<SensorDirection> sensors(FlightClass flightClass) {
        Identifier board = flightClass == FlightClass.COMPACT ? SensorTable.NAV_FC_COMPACT : SensorTable.NAV_FC_PRO;
        return SensorTable.sensors(board).orElse(Set.of());
    }

    public static boolean cameraDrone(Optional<Identifier> front, boolean paired) {
        return paired || front.filter(GimbalTable::isGimbal).isPresent();
    }
}
