package de.lardtom.camdrones.client.school;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.ArrayList;
import java.util.List;

public final class FirstMissionScene {
    public static final String ID = CamDrones.MOD_ID + ":first_mission";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final String MARKER = "wp";
    public static final List<String> STEPS = List.of("pair", "waypoints", "send", "fly", "land");
    public static final List<String> CALLOUTS = List.of("pair", "click", "editor", "send", "fly", "pad");
    public static final List<String> HINTS = List.of("pair", "click", "start");
    public static final int PAD_X = -2;
    public static final int PAD_Z = 2;
    public static final ScenePos PAD = SchoolKit.padCentre(PAD_X, 1, PAD_Z);
    public static final ScenePos PILOT_POS = new ScenePos(-3.4F, 1.0F, 3.8F);
    public static final List<int[]> CLICKED = List.of(new int[]{0, 0, 1}, new int[]{2, 0, -1}, new int[]{0, 0, -2}, new int[]{-2, 0, -1});
    public static final float WAYPOINT_HEIGHT = 3.2F;

    private static final ScenePos OVERVIEW = new ScenePos(-0.2F, 2.0F, 0.4F);
    private static final String MARKER_ITEM = "minecraft:lime_dye";

    private FirstMissionScene() {
    }

    public static List<ScenePos> waypoints() {
        List<ScenePos> out = new ArrayList<>();
        for (int[] block : CLICKED) {
            out.add(new ScenePos(block[0] + 0.5F, WAYPOINT_HEIGHT, block[2] + 0.5F));
        }
        return out;
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.25F, 0.0F);
        Anchor overPilot = Anchor.on(PILOT, 0.0F, 2.1F, 0.0F);
        List<ScenePos> points = waypoints();
        ScenePos above = new ScenePos(PAD.x(), WAYPOINT_HEIGHT, PAD.z());
        return SceneBuilder.scene(ID)
                .icon(SchoolKit.PLANNER)
                .items(SchoolKit.LANDING_PAD)
                .view(4.2F)
                .camera(OVERVIEW, 30.0F, 30.0F, 1.0F)
                .step("pair", 150.0F, s -> {
                    SchoolKit.ground(s.at(0.0F), -4, -3, 3, 4, SchoolKit.GRASS, 0.5F, 10.0F);
                    s.at(14.0F).pop(PAD_X, 1, PAD_Z, SchoolKit.PAD, 8.0F);
                    s.at(18.0F).spawn(DRONE, SchoolKit.drone(), PAD, 45.0F, Cue.Appear.POP, 10.0F);
                    s.at(20.0F).spawn(PILOT, new ActorSpec.Pilot("", SchoolKit.PLANNER), PILOT_POS, SchoolKit.pilotYaw(PILOT_POS, PAD),
                            Cue.Appear.POP, 10.0F);
                    s.at(0.0F).camera(new ScenePos(-2.4F, 1.6F, 3.0F), 34.0F, 26.0F, 1.7F, 30.0F);
                    s.at(36.0F).crouch(PILOT, true);
                    s.at(38.0F).keysAt("pair", overPilot, 40.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(50.0F).callout("pair", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(84.0F).crouch(PILOT, false);
                })
                .step("waypoints", 190.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 34.0F, 1.1F, 24.0F);
                    for (int i = 0; i < CLICKED.size(); i++) {
                        int[] block = CLICKED.get(i);
                        ScenePos point = points.get(i);
                        float at = 14.0F + i * 30.0F;
                        s.at(at).turn(PILOT, SchoolKit.pilotYaw(PILOT_POS, point), 0.0F, 0.0F, 8.0F);
                        s.at(at + 4.0F).highlightBlock(block[0], block[1], block[2], 20.0F);
                        s.at(at + 6.0F).spawn(MARKER + (i + 1), new ActorSpec.Item(MARKER_ITEM, 0.7F, true), point, 0.0F, Cue.Appear.POP, 8.0F);
                    }
                    s.at(16.0F).keysAt("click", overPilot, 60.0F, KeyToken.use());
                    s.at(20.0F).callout("click", Anchor.at(points.getFirst().add(0.0F, 0.3F, 0.0F)), Cue.Side.ABOVE, 100.0F);
                    s.at(140.0F).callout("editor", overPilot, Cue.Side.LEFT, 0.0F);
                })
                .step("send", 120.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(-2.6F, 1.8F, 3.2F), 20.0F, 20.0F, 1.6F, 24.0F);
                    s.at(0.0F).turn(PILOT, SchoolKit.pilotYaw(PILOT_POS, PAD), 0.0F, 0.0F, 10.0F);
                    s.at(8.0F).callout("send", overPilot, Cue.Side.LEFT, 0.0F);
                    s.at(60.0F).keysAt("start", overPilot, 0.0F, KeyToken.mapping(SchoolKit.KEY_MISSION_START));
                })
                .step("fly", 220.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.0F, 0.8F, 0.0F), 30.0F, 32.0F, 1.0F, 24.0F);
                    s.at(0.0F).arm(DRONE, true);
                    s.at(0.0F).throttle(DRONE, 0.55F, 8.0F);
                    s.at(4.0F).move(DRONE, 30.0F, Ease.OUT, above);
                    s.at(10.0F).callout("fly", onDrone, Cue.Side.RIGHT, 0.0F);
                    float leg = 40.0F;
                    ScenePos from = above;
                    for (int i = 0; i < points.size(); i++) {
                        float start = 36.0F + i * leg;
                        SchoolKit.fly(s, DRONE, from, List.of(points.get(i)), Ease.IN_OUT, start, leg - 4.0F, 6, SchoolKit.GENTLE_TILT);
                        s.at(start + leg - 6.0F).despawn(MARKER + (i + 1), 6.0F);
                        from = points.get(i);
                    }
                })
                .step("land", 170.0F, s -> {
                    ScenePos last = points.getLast();
                    s.at(0.0F).camera(new ScenePos(PAD.x(), 1.8F, PAD.z()), 30.0F, 30.0F, 1.6F, 30.0F);
                    SchoolKit.fly(s, DRONE, last, List.of(above), Ease.IN_OUT, 0.0F, 50.0F, 8, SchoolKit.GENTLE_TILT);
                    s.at(56.0F).highlightBlock(PAD_X, 1, PAD_Z, 60.0F);
                    s.at(58.0F).callout("pad", Anchor.at(PAD.add(0.0F, 0.05F, 0.0F)), Cue.Side.LEFT, 0.0F);
                    s.at(70.0F).move(DRONE, 60.0F, Ease.IN_OUT, PAD);
                    s.at(70.0F).throttle(DRONE, 0.3F, 20.0F);
                    s.at(132.0F).throttle(DRONE, 0.0F, 6.0F);
                    s.at(140.0F).arm(DRONE, false);
                })
                .build();
    }
}
