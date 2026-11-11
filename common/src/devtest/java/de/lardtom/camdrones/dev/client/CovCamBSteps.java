package de.lardtom.camdrones.dev.client;

public final class CovCamBSteps {
    private CovCamBSteps() {
    }

    public static void register() {
        CovCamBKeySteps.addonsPage();
        CovCamBKeySteps.defaultKeys();
        CovCamBKeySteps.propwashKeys();
        CovCamBKeySteps.letters();
        CovCamBKeySteps.serverSwitches();
        CovCamBSmartSteps.styles();
        CovCamBSmartSteps.rides();
        CovCamBSmartSteps.fast();
        CovCamBSmartSteps.poiRefused();
        CovCamBAutoSteps.register();
        CovCamBSmartSteps.outOfRangeMission();
        CovCamBSmartSteps.outOfRangeMessage();
        CovCamBCrewSteps.classes();
        CovCamBHudSteps.cameraView();
        CovCamBHudSteps.units();
        CovCamBHudSteps.sounds();
        CovCamBHudSteps.school();
        CovCamBHudSteps.soundGimbal();
        CovCamBHudSteps.rulesPhotos();
        CovCamBShareSteps.register();
        CovCamBCompanionSteps.jmp();
        CovCamBCompanionSteps.mobDefense();
        CovCamBSmartSteps.poiMessage();
        CovCamBHudSteps.rulesSmall();
        CovCamBHudSteps.soundsMissing();
        CovCamBHudSteps.soundGimbalAfterStop();
    }
}
