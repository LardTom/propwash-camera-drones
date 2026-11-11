package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.logic.mission.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraActionsTest {
    private static final class FakeRecording implements CameraActions.Camera {
        final List<String> calls = new ArrayList<>();
        boolean recording;
        boolean allowed = true;

        @Override
        public boolean recording() {
            return this.recording;
        }

        @Override
        public boolean start() {
            this.calls.add("start");
            if (!this.allowed || this.recording) {
                return false;
            }
            this.recording = true;
            return true;
        }

        @Override
        public void stop() {
            this.calls.add("stop");
            this.recording = false;
        }

        @Override
        public boolean photo() {
            this.calls.add("photo");
            return this.allowed;
        }

        @Override
        public void leave() {
            this.calls.add("leave");
        }
    }

    @Test
    void operatorRecordTogglesAndPhotoAndLeave() {
        FakeRecording camera = new FakeRecording();
        assertEquals(CameraActions.Done.STARTED, CameraActions.operator(OperatorGimbalInput.Event.RECORD, camera));
        assertEquals(CameraActions.Done.STOPPED, CameraActions.operator(OperatorGimbalInput.Event.RECORD, camera));
        assertEquals(CameraActions.Done.PHOTO, CameraActions.operator(OperatorGimbalInput.Event.PHOTO, camera));
        assertEquals(CameraActions.Done.LEFT, CameraActions.operator(OperatorGimbalInput.Event.LEAVE, camera));
        assertEquals(CameraActions.Done.NOTHING, CameraActions.operator(OperatorGimbalInput.Event.RECENTER, camera));
        assertEquals(List.of("start", "stop", "photo", "leave"), camera.calls);
    }

    @Test
    void refusedByPropwashIsReported() {
        FakeRecording camera = new FakeRecording();
        camera.allowed = false;
        assertEquals(CameraActions.Done.REFUSED, CameraActions.operator(OperatorGimbalInput.Event.RECORD, camera));
        assertEquals(CameraActions.Done.REFUSED, CameraActions.waypoint(Waypoint.Action.PHOTO, camera));
        assertEquals(0, CameraActions.operatorAll(List.of(OperatorGimbalInput.Event.RECORD, OperatorGimbalInput.Event.PHOTO), camera));
    }

    @Test
    void waypointActionsDoNotToggleBlindly() {
        FakeRecording camera = new FakeRecording();
        assertEquals(CameraActions.Done.NOTHING, CameraActions.waypoint(Waypoint.Action.RECORD_STOP, camera));
        assertEquals(CameraActions.Done.STARTED, CameraActions.waypoint(Waypoint.Action.RECORD_START, camera));
        assertEquals(CameraActions.Done.NOTHING, CameraActions.waypoint(Waypoint.Action.RECORD_START, camera));
        assertEquals(CameraActions.Done.STOPPED, CameraActions.waypoint(Waypoint.Action.RECORD_STOP, camera));
        assertEquals(CameraActions.Done.NOTHING, CameraActions.waypoint(Waypoint.Action.NONE, camera));
        assertEquals(List.of("start", "stop"), camera.calls);
    }
}
