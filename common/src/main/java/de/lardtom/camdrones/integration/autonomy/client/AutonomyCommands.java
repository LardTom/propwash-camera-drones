package de.lardtom.camdrones.integration.autonomy.client;

import de.lardtom.camdrones.client.planner.MissionCommands;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.SmartMode;

import java.util.UUID;

public final class AutonomyCommands implements MissionCommands {
    public static MissionCommand.Command map(Command command) {
        return switch (command) {
            case START -> MissionCommand.Command.START;
            case PAUSE -> MissionCommand.Command.PAUSE;
            case STOP -> MissionCommand.Command.STOP;
        };
    }

    @Override
    public boolean available() {
        return CamNetwork.canSendToServer(MissionCommand.TYPE);
    }

    @Override
    public boolean send(UUID serial, Command command) {
        if (serial == null || command == null) {
            return false;
        }
        return CamNetwork.sendToServer(MissionCommand.Request.of(serial, map(command)));
    }

    @Override
    public boolean autoShotAvailable() {
        return CamNetwork.canSendToServer(SmartMode.TYPE);
    }

    @Override
    public boolean autoShot(UUID serial, AutoShots.Shot shot) {
        if (serial == null || shot == null) {
            return false;
        }
        return CamNetwork.sendToServer(new SmartMode.Request(serial, SmartState.autoShot(shot)));
    }

    public static SmartMode.Request followRequest(UUID serial, UUID target, FollowController.Style style) {
        return new SmartMode.Request(serial, SmartState.follow(target, style));
    }

    @Override
    public boolean followAvailable() {
        return CamNetwork.canSendToServer(SmartMode.TYPE);
    }

    @Override
    public boolean follow(UUID serial, UUID target, FollowController.Style style) {
        if (serial == null || target == null || style == null) {
            return false;
        }
        return CamNetwork.sendToServer(followRequest(serial, target, style));
    }
}
