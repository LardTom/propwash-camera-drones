package de.lardtom.camdrones.client.record;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.school.SchoolKit;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.List;

public final class RecordingScene {
    public static final String ID = CamDrones.MOD_ID + ":recording";
    public static final String PLANNER = SchoolKit.PLANNER;
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final List<String> STEPS = List.of("mode", "flashback", "direct");
    public static final List<String> CALLOUTS = List.of("mode", "default", "flashback", "far", "direct", "hint");
    public static final ScenePos PILOT_POS = new ScenePos(-2.6F, 1.0F, 1.6F);
    public static final ScenePos START = new ScenePos(-1.2F, 1.0F, 0.6F);
    public static final ScenePos HOVER = new ScenePos(-1.0F, 2.6F, 0.2F);
    public static final ScenePos AWAY = new ScenePos(3.4F, 3.4F, -3.0F);
    public static final ScenePos BACK = new ScenePos(-0.4F, 2.4F, 0.0F);

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.2F, 0.0F);

    private RecordingScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.25F, 0.0F);
        Anchor onPilot = Anchor.on(PILOT, 0.0F, 2.1F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(SchoolKit.PLANNER)
                .view(4.8F)
                .camera(OVERVIEW, 30.0F, 30.0F, 1.0F)
                .step("mode", 150.0F, s -> {
                    SchoolKit.ground(s.at(0.0F), -4, -4, 4, 4, SchoolKit.GRASS, 0.4F, 10.0F);
                    s.at(12.0F).spawn(PILOT, new ActorSpec.Pilot("", SchoolKit.PLANNER), PILOT_POS, SchoolKit.pilotYaw(PILOT_POS, START),
                            Cue.Appear.POP, 10.0F);
                    s.at(16.0F).spawn(DRONE, SchoolKit.drone(), START, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(0.0F).camera(new ScenePos(-1.6F, 1.8F, 1.0F), 30.0F, 24.0F, 1.4F, 30.0F);
                    s.at(28.0F).callout("mode", onPilot, Cue.Side.LEFT, 60.0F);
                    s.at(92.0F).callout("default", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("flashback", 230.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 30.0F, 1.0F, 30.0F);
                    s.at(6.0F).arm(DRONE, true);
                    s.at(6.0F).throttle(DRONE, 0.55F, 8.0F);
                    s.at(10.0F).move(DRONE, 30.0F, Ease.OUT, HOVER);
                    s.at(20.0F).callout("flashback", onDrone, Cue.Side.RIGHT, 80.0F);
                    SchoolKit.fly(s, DRONE, HOVER, List.of(AWAY), Ease.IN_OUT, 50.0F, 90.0F, 8, SchoolKit.GENTLE_TILT);
                    s.at(0.0F).camera(new ScenePos(1.2F, 2.6F, -1.2F), 30.0F, 34.0F, 0.9F, 60.0F);
                    s.at(140.0F).callout("far", onDrone, Cue.Side.LEFT, 0.0F);
                })
                .step("direct", 200.0F, s -> {
                    SchoolKit.fly(s, DRONE, AWAY, List.of(BACK), Ease.IN_OUT, 4.0F, 80.0F, 8, SchoolKit.GENTLE_TILT);
                    s.at(0.0F).camera(new ScenePos(-0.8F, 2.2F, 0.6F), 24.0F, 24.0F, 1.4F, 50.0F);
                    s.at(60.0F).callout("direct", onDrone, Cue.Side.RIGHT, 70.0F);
                    s.at(136.0F).callout("hint", onPilot, Cue.Side.LEFT, 0.0F);
                })
                .build();
    }
}
