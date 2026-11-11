package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.List;

public final class CameraActions {
    public interface Camera {
        boolean recording();

        boolean start();

        void stop();

        boolean photo();

        void leave();
    }

    public enum Done {
        NOTHING,
        STARTED,
        STOPPED,
        PHOTO,
        LEFT,
        REFUSED
    }

    private CameraActions() {
    }

    public static Done operator(OperatorGimbalInput.Event event, Camera camera) {
        return switch (event) {
            case RECORD -> camera.recording() ? stop(camera) : start(camera);
            case PHOTO -> camera.photo() ? Done.PHOTO : Done.REFUSED;
            case LEAVE -> {
                camera.leave();
                yield Done.LEFT;
            }
            default -> Done.NOTHING;
        };
    }

    public static Done waypoint(Waypoint.Action action, Camera camera) {
        return switch (action) {
            case PHOTO -> camera.photo() ? Done.PHOTO : Done.REFUSED;
            case RECORD_START -> camera.recording() ? Done.NOTHING : start(camera);
            case RECORD_STOP -> camera.recording() ? stop(camera) : Done.NOTHING;
            case NONE -> Done.NOTHING;
        };
    }

    public static int operatorAll(List<OperatorGimbalInput.Event> events, Camera camera) {
        int done = 0;
        for (OperatorGimbalInput.Event event : events) {
            Done result = operator(event, camera);
            if (result != Done.NOTHING && result != Done.REFUSED) {
                done++;
            }
        }
        return done;
    }

    private static Done start(Camera camera) {
        return camera.start() ? Done.STARTED : Done.REFUSED;
    }

    private static Done stop(Camera camera) {
        camera.stop();
        return Done.STOPPED;
    }
}
