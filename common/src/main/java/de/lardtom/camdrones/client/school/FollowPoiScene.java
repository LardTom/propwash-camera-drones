package de.lardtom.camdrones.client.school;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.List;

public final class FollowPoiScene {
    public static final String ID = CamDrones.MOD_ID + ":follow_poi";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final String FRIEND = "friend";
    public static final List<String> STEPS = List.of("follow", "consent", "poi", "stop");
    public static final List<String> CALLOUTS = List.of("follow", "distance", "consent", "poi", "radius", "stop");
    public static final List<String> HINTS = List.of("pause");
    public static final ScenePos WALK_START = new ScenePos(-2.4F, 1.0F, 2.0F);
    public static final List<ScenePos> WALK = List.of(new ScenePos(-1.0F, 1.0F, 1.0F), new ScenePos(1.0F, 1.0F, 1.6F), new ScenePos(2.6F, 1.0F, 0.0F));
    public static final ScenePos BEACON = new ScenePos(0.5F, 1.0F, -1.5F);
    public static final float ORBIT_RADIUS = 2.4F;
    public static final float ORBIT_HEIGHT = 3.2F;
    public static final float FOLLOW_BACK = 2.2F;
    public static final float FOLLOW_UP = 2.2F;

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.2F, 0.4F);

    private FollowPoiScene() {
    }

    public static ScenePos behind(ScenePos walker, ScenePos previous) {
        float dx = walker.x() - previous.x();
        float dz = walker.z() - previous.z();
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4F) {
            return walker.add(0.0F, FOLLOW_UP, FOLLOW_BACK);
        }
        return new ScenePos(walker.x() - dx / length * FOLLOW_BACK, walker.y() + FOLLOW_UP, walker.z() - dz / length * FOLLOW_BACK);
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.25F, 0.0F);
        ScenePos first = WALK.getFirst();
        ScenePos droneStart = behind(WALK_START, new ScenePos(2.0F * WALK_START.x() - first.x(), WALK_START.y(), 2.0F * WALK_START.z() - first.z()));
        ScenePos last = WALK.getLast();
        ScenePos droneEnd = behind(last, WALK.get(WALK.size() - 2));
        List<ScenePos> circle = SchoolKit.circle(BEACON, ORBIT_RADIUS, ORBIT_HEIGHT, 24, 90.0F);
        return SceneBuilder.scene(ID)
                .icon(CamPresets.part("nav_fc_pro").toString())
                .view(4.6F)
                .camera(OVERVIEW, 30.0F, 30.0F, 1.0F)
                .step("follow", 220.0F, s -> {
                    SchoolKit.ground(s.at(0.0F), -4, -4, 4, 4, SchoolKit.GRASS, 0.4F, 10.0F);
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot("", SchoolKit.PLANNER), WALK_START, SchoolKit.pilotYaw(WALK_START, WALK.getFirst()),
                            Cue.Appear.POP, 10.0F);
                    s.at(18.0F).spawn(DRONE, SchoolKit.drone(), droneStart, SchoolKit.heading(droneStart, WALK_START), Cue.Appear.POP, 10.0F);
                    s.at(18.0F).arm(DRONE, true);
                    s.at(18.0F).throttle(DRONE, 0.45F, 6.0F);
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 30.0F, 1.1F, 30.0F);
                    s.at(30.0F).callout("follow", onDrone, Cue.Side.RIGHT, 70.0F);
                    ScenePos from = WALK_START;
                    float start = 40.0F;
                    float leg = 46.0F;
                    for (ScenePos to : WALK) {
                        s.at(start).turn(PILOT, SchoolKit.pilotYaw(from, to), 0.0F, 0.0F, 6.0F);
                        s.at(start).move(PILOT, leg, Ease.LINEAR, to);
                        ScenePos target = behind(to, from);
                        s.at(start + 4.0F).move(DRONE, leg, Ease.IN_OUT, target);
                        s.at(start + 4.0F).turn(DRONE, SchoolKit.heading(target, to), -6.0F, 0.0F, leg, Ease.IN_OUT);
                        from = to;
                        start += leg;
                    }
                    s.at(120.0F).callout("distance", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(start + 6.0F).turn(DRONE, SchoolKit.heading(droneEnd, last), 0.0F, 0.0F, 10.0F);
                })
                .step("consent", 130.0F, s -> {
                    ScenePos friendAt = new ScenePos(1.6F, 1.0F, 2.8F);
                    s.at(4.0F).spawn(FRIEND, new ActorSpec.Pilot("", ""), friendAt, SchoolKit.pilotYaw(friendAt, last), Cue.Appear.POP, 10.0F);
                    s.at(0.0F).camera(new ScenePos(2.0F, 1.8F, 1.4F), 20.0F, 20.0F, 1.5F, 24.0F);
                    s.at(16.0F).callout("consent", Anchor.on(FRIEND, 0.0F, 2.1F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(100.0F).despawn(FRIEND, 10.0F);
                })
                .step("poi", 240.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(BEACON.x(), 2.2F, BEACON.z()), 30.0F, 36.0F, 1.0F, 26.0F);
                    s.at(4.0F).pop((int) Math.floor(BEACON.x()), 1, (int) Math.floor(BEACON.z()), SchoolKit.BEACON, 8.0F);
                    s.at(12.0F).highlightBlock((int) Math.floor(BEACON.x()), 1, (int) Math.floor(BEACON.z()), 40.0F);
                    s.at(14.0F).callout("poi", onDrone, Cue.Side.RIGHT, 60.0F);
                    SchoolKit.fly(s, DRONE, droneEnd, List.of(circle.getFirst()), Ease.IN_OUT, 12.0F, 40.0F, 6, SchoolKit.GENTLE_TILT);
                    SchoolKit.orbit(s, DRONE, BEACON.add(0.0F, ORBIT_HEIGHT - 1.0F, 0.0F), circle, 56.0F, 170.0F);
                    s.at(80.0F).callout("radius", Anchor.at(BEACON.add(0.0F, 1.2F, 0.0F)), Cue.Side.ABOVE, 0.0F);
                })
                .step("stop", 110.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 30.0F, 1.05F, 24.0F);
                    s.at(6.0F).callout("stop", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(20.0F).keys("pause", 0.0F, KeyToken.mapping(SchoolKit.KEY_PAUSE));
                    s.at(60.0F).throttle(DRONE, 0.0F, 10.0F);
                    s.at(60.0F).move(DRONE, 40.0F, Ease.IN_OUT, new ScenePos(circle.getLast().x(), 1.0F, circle.getLast().z()));
                    s.at(102.0F).arm(DRONE, false);
                })
                .build();
    }
}
