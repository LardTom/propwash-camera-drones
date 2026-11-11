package de.lardtom.camdrones.client.school;

import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.BlockPlacement;
import de.lardtom.propwash.guide.scene.CameraSpec;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstacleStopViewTest {
    private static final float SETTLED = 30.0F;
    private static final float SAMPLE = 2.0F;
    private static final float INSET = 0.05F;
    private static final float MIN_FACING = 0.3F;
    private static final int[][] VIEWS = {{300, 170}, {420, 236}, {600, 300}};
    private static Scene scene;
    private static int step;

    @BeforeAll
    static void build() {
        PartFiles.registerAll();
        scene = CameraDroneScene.create();
        step = CameraDroneScene.STEPS.indexOf("obstacle");
    }

    private static Vector3f towardsViewer(CameraSpec camera) {
        return new SceneProjection(camera, 1.0F, 0.0F, 0.0F).viewDirection(new Vector3f()).negate();
    }

    private static boolean hits(SceneState.BlockView block, Vector3f from, Vector3f dir) {
        BlockPlacement at = block.at();
        float half = 0.5F * block.scale();
        float[] center = {at.x() + 0.5F, at.y() + 0.5F + block.lift(), at.z() + 0.5F};
        float[] origin = {from.x, from.y, from.z};
        float[] d = {dir.x, dir.y, dir.z};
        float near = 1.0E-3F;
        float far = Float.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            float lo = center[axis] - half;
            float hi = center[axis] + half;
            if (Math.abs(d[axis]) < 1.0E-6F) {
                if (origin[axis] < lo || origin[axis] > hi) {
                    return false;
                }
                continue;
            }
            float a = (lo - origin[axis]) / d[axis];
            float b = (hi - origin[axis]) / d[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) {
                return false;
            }
        }
        return true;
    }

    private static List<String> covering(SceneState state, Vector3f dir, List<Vector3f> points) {
        List<String> out = new ArrayList<>();
        for (SceneState.BlockView block : state.blocks()) {
            if (block.scale() <= 0.0F) {
                continue;
            }
            for (Vector3f point : points) {
                if (hits(block, point, dir)) {
                    BlockPlacement at = block.at();
                    out.add(at.x() + "/" + at.y() + "/" + at.z());
                    break;
                }
            }
        }
        return out;
    }

    private static int hidden(SceneState state, Vector3f dir, List<Vector3f> points) {
        int count = 0;
        for (Vector3f point : points) {
            for (SceneState.BlockView block : state.blocks()) {
                if (block.scale() > 0.0F && hits(block, point, dir)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static List<Vector3f> dronePoints(ActorState drone) {
        ActorSpec.Drone spec = (ActorSpec.Drone) drone.spec();
        ScenePos p = drone.position();
        float radius = SceneFraming.droneRadius(spec) * 0.5F;
        float y = p.y() + 0.1F * spec.scale();
        List<Vector3f> out = new ArrayList<>();
        out.add(new Vector3f(p.x(), y, p.z()));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            out.add(new Vector3f(p.x() + (float) Math.cos(angle) * radius, y, p.z() + (float) Math.sin(angle) * radius));
        }
        return out;
    }

    private static List<Vector3f> boxPoints(ScenePos min, ScenePos max) {
        float x0 = min.x() + INSET;
        float y0 = min.y() + INSET;
        float z0 = min.z() + INSET;
        float x1 = max.x() - INSET;
        float y1 = max.y() - INSET;
        float z1 = max.z() - INSET;
        List<Vector3f> out = new ArrayList<>();
        out.add(new Vector3f((x0 + x1) * 0.5F, (y0 + y1) * 0.5F, (z0 + z1) * 0.5F));
        for (int corner = 0; corner < 8; corner++) {
            out.add(new Vector3f((corner & 1) == 0 ? x0 : x1, (corner & 2) == 0 ? y0 : y1, (corner & 4) == 0 ? z0 : z1));
        }
        return out;
    }

    private static List<Vector3f> wallFacePoints() {
        float x = CameraDroneScene.WALL_X - INSET;
        List<Vector3f> out = new ArrayList<>();
        for (int y = CameraDroneScene.WALL_BOTTOM; y <= CameraDroneScene.WALL_TOP; y++) {
            for (int z = CameraDroneScene.WALL_FROM_Z; z <= CameraDroneScene.WALL_TO_Z; z++) {
                out.add(new Vector3f(x, y + 0.5F, z + 0.5F));
            }
        }
        return out;
    }

    private static float settled() {
        return scene.stepStart(step) + SETTLED;
    }

    @Test
    void theWallStandsBehindTheStopAsSeenFromTheFlightPath() {
        assertEquals(4, step);
        assertTrue(CameraDroneScene.STOP.x() < CameraDroneScene.WALL_X, "STOP liegt nicht vor der Wand");
        assertTrue(CameraDroneScene.DRIFTED.x() < CameraDroneScene.STOP.x(), "Die Drohne fliegt nicht auf die Wand zu");
        SceneState end = SceneEvaluator.evaluate(scene, scene.stepEnd(step) - 1.0F);
        for (int y = CameraDroneScene.WALL_BOTTOM; y <= CameraDroneScene.WALL_TOP; y++) {
            for (int z = CameraDroneScene.WALL_FROM_Z; z <= CameraDroneScene.WALL_TO_Z; z++) {
                SceneState.BlockView block = end.block(CameraDroneScene.WALL_X, y, z);
                assertNotNull(block, "Wandblock " + y + "/" + z + " fehlt");
                assertEquals(1.0F, block.scale(), 1.0E-4F);
            }
        }
    }

    @Test
    void theCameraLooksAtTheSideOfTheWallFacingTheDrone() {
        List<String> problems = new ArrayList<>();
        for (float t = settled(); t < scene.stepEnd(step); t += SAMPLE) {
            CameraSpec camera = SceneEvaluator.evaluate(scene, t).camera();
            float facing = -towardsViewer(camera).x;
            if (facing < MIN_FACING) {
                problems.add(String.format("t=%.0f: Kamera (yaw %.1f, pitch %.1f) sieht die Wand von hinten oder von der Kante (%.2f < %.2f)",
                        t - scene.stepStart(step), camera.yaw(), camera.pitch(), facing, MIN_FACING));
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void noBlockHidesTheDroneOnItsWayToTheStopAndWhileItHolds() {
        List<String> problems = new ArrayList<>();
        for (float t = settled(); t < scene.stepEnd(step); t += SAMPLE) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            ActorState drone = state.actor(CameraDroneScene.DRONE);
            assertNotNull(drone);
            Vector3f dir = towardsViewer(state.camera());
            List<Vector3f> points = dronePoints(drone);
            int hidden = hidden(state, dir, points);
            if (hidden > 0) {
                problems.add(String.format("t=%.0f: Drohne bei %s zu %d von %d Punkten verdeckt von %s", t - scene.stepStart(step),
                        drone.position(), hidden, points.size(), covering(state, dir, points)));
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " Zeitpunkte mit verdeckter Drohne:\n" + String.join("\n", problems));
    }

    @Test
    void theStopMarkAndTheWallFaceAreNotHidden() {
        List<String> problems = new ArrayList<>();
        int marked = 0;
        for (float t = settled(); t < scene.stepEnd(step); t += SAMPLE) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            Vector3f dir = towardsViewer(state.camera());
            for (SceneState.HighlightView highlight : state.highlights()) {
                if (highlight.alpha() <= 0.0F) {
                    continue;
                }
                marked++;
                List<Vector3f> points = boxPoints(highlight.min(), highlight.max());
                int hidden = hidden(state, dir, points);
                if (hidden > 0) {
                    problems.add(String.format("t=%.0f: Stopp-Markierung zu %d von %d Punkten verdeckt von %s", t - scene.stepStart(step), hidden,
                            points.size(), covering(state, dir, points)));
                }
            }
            if (t >= scene.stepStart(step) + 60.0F) {
                List<Vector3f> face = wallFacePoints();
                int hidden = hidden(state, dir, face);
                if (hidden > 0) {
                    problems.add(String.format("t=%.0f: Vorderseite der Wand zu %d von %d Punkten verdeckt", t - scene.stepStart(step), hidden, face.size()));
                }
            }
        }
        assertTrue(marked > 0, "Schritt ohne Stopp-Markierung");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void droneWallAndStopMarkStayInsideTheView() {
        SceneFraming framing = SceneFraming.of(scene);
        List<String> problems = new ArrayList<>();
        ScenePos wall = new ScenePos(CameraDroneScene.WALL_X, (CameraDroneScene.WALL_BOTTOM + CameraDroneScene.WALL_TOP + 1) * 0.5F,
                (CameraDroneScene.WALL_FROM_Z + CameraDroneScene.WALL_TO_Z + 1) * 0.5F);
        for (int[] view : VIEWS) {
            float base = SceneProjection.basePixelsPerBlock(view[0], view[1], scene.viewRadius());
            for (float t = scene.stepStart(step) + 90.0F; t < scene.stepEnd(step); t += 10.0F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                SceneProjection projection = framing.projection(state.camera(), t, base, 0.0F, 0.0F, view[0], view[1], 1.0F);
                List<ScenePos> points = new ArrayList<>();
                points.add(state.actor(CameraDroneScene.DRONE).position());
                points.add(wall);
                for (SceneState.HighlightView highlight : state.highlights()) {
                    if (highlight.alpha() > 0.0F) {
                        points.add(highlight.min().lerp(highlight.max(), 0.5F));
                    }
                }
                for (ScenePos point : points) {
                    float[] at = projection.project(point);
                    if (at[0] < 0.0F || at[0] > view[0] || at[1] < 0.0F || at[1] > view[1]) {
                        problems.add(String.format("%dx%d t=%.0f: %s außerhalb (%.0f, %.0f)", view[0], view[1], t - scene.stepStart(step), point, at[0], at[1]));
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
