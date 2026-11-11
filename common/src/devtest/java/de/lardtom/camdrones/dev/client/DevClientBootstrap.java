package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.NoticeProbe;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;

public final class DevClientBootstrap {
    private DevClientBootstrap() {
    }

    public static void init() {
        if (SelfTest.enabled()) {
            StatusProbe.install();
            NoticeProbe.install();
            WorkbenchSteps.register();
            RecipeSteps.register();
            FlightSteps.register();
            PlannerSteps.register();
            OsdSteps.register();
            RulesSteps.register();
            KeySteps.register();
            GimbalSteps.register();
            MissionSteps.register();
            SmartSteps.register();
            CompassSteps.register();
            AutonomyRuleSteps.register();
            CourseSteps.register();
            AutoShotSteps.register();
            GimbalLockSteps.register();
            FlashbackSteps.register();
            PairingSteps.register();
            TakeoverSteps.register();
            TripodSteps.register();
            FollowRideSteps.register();
            ClimbOverSteps.register();
            CompanionSteps.register();
            CleanupStep.register();
            CovCamASteps.register();
            DamageSteps.register();
            SurvivalCraftSteps.register();
            LandingPadSteps.register();
            ViewToggleSteps.register();
            SchoolSteps.register();
            OsdNameSteps.register();
            CovCamBSteps.register();
            FollowTabletSteps.register();
            HudSettingsSteps.register();
            LiveShotSteps.register();
            WaypointTaskSteps.register();
            MapDragSteps.register();
            WallStopSteps.register();
            PilotViewSteps.register();
            LiveShotSteps.registerCleanView();
            StickStepSteps.register();
            FlightModeSteps.register();
            RecModeSteps.register();
            StormStepSteps.register();
            TabletHoldSteps.register();
            BudgetSteps.register();
            MissionTimerSteps.register();
            GimbalSmoothSteps.register();
            GimbalHoldSteps.register();
            SportLoadSteps.register();
            CurveSteps.register();
            NoAntennaSteps.register();
            GroundStartSteps.register();
            NotReadyMessageSteps.register();
            ConsentNameSteps.register();
            AwardSteps.register();
            MultiEditSteps.register();
            CrewHeadingSteps.register();
            GripperMissionSteps.register();
            SoloCameraSteps.register();
            OperatorBannerSteps.register();
            SelfTestRunner.install();
        } else if (SelfTest.smokeEnabled()) {
            StatusProbe.install();
            NoticeProbe.install();
            if (JoinEnv.target() != null) {
                JoinProbe.install();
            } else {
                SelfTestRunner.installSmoke();
            }
        }
    }
}
