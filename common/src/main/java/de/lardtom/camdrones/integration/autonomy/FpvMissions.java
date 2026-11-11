package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.net.MissionCommand;

public final class FpvMissions {
    private FpvMissions() {
    }

    public static boolean refusedWithPilot(MissionCommand.Command command, boolean fpv) {
        return fpv && (command == MissionCommand.Command.START || command == MissionCommand.Command.RESUME);
    }

    public static boolean keepsPropwashExit(boolean fpv, boolean missionActive, SmartState.Function smart) {
        return fpv && !missionActive && (smart == null || smart == SmartState.Function.NONE);
    }
}
