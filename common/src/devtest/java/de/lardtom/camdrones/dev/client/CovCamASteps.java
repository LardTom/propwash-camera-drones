package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.integration.data.DroneSettings;

public final class CovCamASteps {
    private CovCamASteps() {
    }

    public static void register() {
        CovCamAPartSteps.install();
        CovCamAPartSteps.tab();
        CovCamAPartSteps.smartBattery();
        CovCamAPartSteps.paint();
        CovCamAPartSteps.lensBay();
        CovCamAFlightSteps.windLean();
        CovCamAFlightSteps.rescue();
        CovCamAFlightSteps.exit(DroneSettings.ExitBehaviour.HOVER);
        CovCamAFlightSteps.exit(DroneSettings.ExitBehaviour.LAND);
        CovCamAInputSteps.wheel();
        CovCamAInputSteps.fpvMode();
        CovCamAPlannerSteps.pairPlain();
        CovCamAMissionSteps.actions();
        CovCamAPartSteps.checkButton();
        CovCamAFlightSteps.paused();
        CovCamAPlannerSteps.pair();
        CovCamAPlannerSteps.service();
        CovCamAPlannerSteps.preview();
        CovCamAPlannerSteps.live();
        CovCamAPlannerSteps.exitSetting();
        CovCamAFlightSteps.lowBattery();
        CovCamAFlightSteps.crash();
        CovCamAFlightSteps.speeds();
        CovCamAFlightSteps.tripod();
        CovCamAFlightSteps.exit(DroneSettings.ExitBehaviour.HOME);
        CovCamAMissionSteps.noGps();
        CovCamAMissionSteps.notReady();
        CovCamAMissionSteps.landingPad();
    }
}
