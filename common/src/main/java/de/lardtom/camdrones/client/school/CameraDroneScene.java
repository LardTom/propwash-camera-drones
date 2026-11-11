package de.lardtom.camdrones.client.school;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.part.CamTab;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.List;

public final class CameraDroneScene {
    public static final String ID = CamDrones.MOD_ID + ":camera_drone";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final List<String> STEPS = List.of("hold", "modes", "gimbal", "atti", "obstacle");
    public static final List<String> CALLOUTS = List.of("hold", "sensors", "cine", "sport", "horizon", "atti", "obstacle");
    public static final List<String> HINTS = List.of("arm", "mode", "tilt");
    public static final ScenePos PILOT_POS = new ScenePos(-3.0F, 1.0F, 2.6F);
    public static final ScenePos START = new ScenePos(-1.5F, 1.0F, 1.5F);
    public static final ScenePos HOVER = new ScenePos(-1.5F, 3.0F, 1.5F);
    public static final ScenePos DRIFTED = new ScenePos(-0.3F, 3.0F, 0.9F);
    public static final ScenePos STOP = new ScenePos(1.6F, 3.0F, 0.5F);
    public static final int WALL_X = 3;
    public static final int WALL_BOTTOM = 1;
    public static final int WALL_TOP = 4;
    public static final int WALL_FROM_Z = -1;
    public static final int WALL_TO_Z = 2;

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.4F, 0.6F);
    private static final ScenePos CINE_END = new ScenePos(1.4F, 3.0F, 1.6F);
    private static final ScenePos SPORT_TURN = new ScenePos(1.8F, 3.4F, -1.8F);

    private CameraDroneScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.25F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(CamTab.ICON.toString())
                .view(4.4F)
                .camera(OVERVIEW, 30.0F, 28.0F, 1.0F)
                .step("hold", 160.0F, s -> {
                    SchoolKit.ground(s.at(0.0F), -4, -3, 4, 3, SchoolKit.GRASS, 0.5F, 10.0F);
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot(SchoolKit.GOGGLES, SchoolKit.REMOTE), PILOT_POS, SchoolKit.pilotYaw(PILOT_POS, START),
                            Cue.Appear.POP, 10.0F);
                    s.at(18.0F).spawn(DRONE, SchoolKit.drone(), START, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(0.0F).camera(new ScenePos(-1.6F, 2.0F, 1.8F), 30.0F, 24.0F, 1.5F, 30.0F);
                    s.at(30.0F).keys("arm", 30.0F, KeyToken.mapping(SchoolKit.KEY_ARM));
                    s.at(42.0F).arm(DRONE, true);
                    s.at(42.0F).throttle(DRONE, 0.55F, 8.0F);
                    s.at(46.0F).move(DRONE, 40.0F, Ease.OUT, HOVER);
                    s.at(88.0F).throttle(DRONE, 0.45F, 8.0F);
                    s.at(90.0F).callout("hold", onDrone, Cue.Side.RIGHT, 40.0F);
                    s.at(130.0F).callout("sensors", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("modes", 190.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 28.0F, 1.05F, 24.0F);
                    s.at(4.0F).keys("mode", 40.0F, KeyToken.mapping(SchoolKit.KEY_FLIGHT_MODE));
                    s.at(8.0F).callout("cine", onDrone, Cue.Side.RIGHT, 80.0F);
                    SchoolKit.fly(s, DRONE, HOVER, List.of(CINE_END), Ease.IN_OUT, 10.0F, 80.0F, 10, SchoolKit.GENTLE_TILT);
                    s.at(100.0F).callout("sport", onDrone, Cue.Side.RIGHT, 0.0F);
                    SchoolKit.fly(s, DRONE, CINE_END, List.of(SPORT_TURN, HOVER), Ease.IN_OUT, 100.0F, 60.0F, 14, SchoolKit.SPORT_TILT);
                })
                .step("gimbal", 150.0F, s -> {
                    s.at(0.0F).camera(HOVER.add(0.0F, -0.1F, 0.0F), 70.0F, 8.0F, 2.6F, 26.0F);
                    s.at(10.0F).callout("horizon", onDrone, Cue.Side.ABOVE, 0.0F);
                    s.at(30.0F).turn(DRONE, 0.0F, -6.0F, 22.0F, 20.0F, Ease.IN_OUT);
                    s.at(60.0F).turn(DRONE, 0.0F, 4.0F, -22.0F, 24.0F, Ease.IN_OUT);
                    s.at(92.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 16.0F, Ease.IN_OUT);
                    s.at(96.0F).keys("tilt", 0.0F, KeyToken.mapping(SchoolKit.KEY_TILT_UP), KeyToken.mapping(SchoolKit.KEY_TILT_DOWN));
                })
                .step("atti", 170.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(-0.8F, 3.0F, 1.2F), 34.0F, 20.0F, 1.4F, 24.0F);
                    for (int x = -3; x <= 1; x++) {
                        for (int z = 0; z <= 2; z++) {
                            s.at(6.0F + (x + 3 + z) * 1.5F).pop(x, 4, z, SchoolKit.STONE, 8.0F);
                        }
                    }
                    s.at(24.0F).callout("atti", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(30.0F).move(DRONE, 110.0F, Ease.LINEAR, DRIFTED);
                    s.at(30.0F).turn(DRONE, 0.0F, 0.0F, 3.0F, 20.0F, Ease.IN_OUT);
                    s.at(140.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 10.0F, Ease.IN_OUT);
                })
                .step("obstacle", 170.0F, s -> {
                    for (int x = -3; x <= 1; x++) {
                        for (int z = 0; z <= 2; z++) {
                            s.at((x + 3 + z) * 0.8F).remove(x, 4, z, 6.0F);
                        }
                    }
                    s.at(0.0F).camera(new ScenePos(1.0F, 2.6F, 0.6F), -30.0F, 24.0F, 1.25F, 24.0F);
                    for (int y = WALL_BOTTOM; y <= WALL_TOP; y++) {
                        for (int z = WALL_FROM_Z; z <= WALL_TO_Z; z++) {
                            s.at(8.0F + (y + z) * 1.5F).pop(WALL_X, y, z, SchoolKit.STONE_BRICKS, 8.0F);
                        }
                    }
                    SchoolKit.fly(s, DRONE, DRIFTED, List.of(STOP), Ease.OUT, 40.0F, 50.0F, 8, SchoolKit.GENTLE_TILT);
                    s.at(84.0F).highlight(new ScenePos(STOP.x() + 0.3F, 2.5F, 0.0F), new ScenePos(WALL_X, 3.5F, 1.0F), 60.0F);
                    s.at(86.0F).callout("obstacle", onDrone, Cue.Side.LEFT, 0.0F);
                })
                .build();
    }
}
