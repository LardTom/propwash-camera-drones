package de.lardtom.camdrones.client.school;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.PartKind;

import java.util.List;

public final class CamSchool {
    public static final List<String> SCENES = List.of(CameraDroneScene.ID, FirstMissionScene.ID, FollowPoiScene.ID);
    private static boolean initialized;

    private CamSchool() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerFlightSchoolScene(CameraDroneScene::create);
        PropwashClientApi.registerFlightSchoolScene(FirstMissionScene::create);
        PropwashClientApi.registerFlightSchoolScene(FollowPoiScene::create);
        PropwashClientApi.linkFlightSchoolParts(CameraDroneScene.ID, CamDrones.MOD_ID, PartKind.FRAME, PartKind.STACK, PartKind.ACCESSORY);
        PropwashClientApi.linkFlightSchoolItems(FirstMissionScene.ID, SchoolKit.PLANNER, SchoolKit.LANDING_PAD);
        PropwashClientApi.linkFlightSchoolParts(FollowPoiScene.ID, CamDrones.MOD_ID, PartKind.STACK, PartKind.ACCESSORY);
        PropwashClientApi.linkFlightSchoolItems(FollowPoiScene.ID, SchoolKit.PLANNER);
    }

    public static synchronized boolean initialized() {
        return initialized;
    }
}
