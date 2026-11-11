package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.platform.Services;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class TabletHoldSteps {
    public static final int NUMBER = 612;
    public static final String NAME = "planer: Tablet in der dritten Person vor dem Körper, Bildschirm zum Spieler";
    public static final String DIR = "own2810c";
    public static final float MAX_SIDE = 3.0F;
    public static final float MAX_FRONT_Z = -4.0F;
    public static final float MIN_HEIGHT_Y = 2.0F;
    public static final float MAX_HEIGHT_Y = 12.0F;
    public static final float MIN_EYE = 0.6F;
    public static final float MAX_UPRIGHT = 0.9F;
    public static final float MIN_ACROSS = 0.9F;
    public static final float MAX_MIRROR = 0.6F;
    private static final int STEP_TICKS = 20 * 120;
    private static final int MEASURE_TICKS = 20;
    private static final int SETTLE_TICKS = 10;
    private static final double CAMERA_DISTANCE = 2.5;
    private static final float BACK_PITCH = 20.0F;
    private static final float FRONT_PITCH = 0.0F;
    private static final float[] TORSO_MIN = {-4.0F, 0.0F, -2.0F};
    private static final float[] TORSO_MAX = {4.0F, 12.0F, 2.0F};
    private static final float TORSO_MARGIN = 0.05F;
    private static final int GRID = 9;
    private static final float PX = 16.0F;
    private static final String WORST = "tablet.worst";
    private static final String MIRROR = "tablet.mirror";
    private static final String SHOT = "tablet.shot";
    private static final String PROBLEMS = "tablet.problems";
    private static boolean hidHud;

    private TabletHoldSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .server("Kamera-Abstand 2,5 m, Flugplaner in die Haupthand, Nebenhand leer", (server, state) -> {
                    cameraDistance(server, CAMERA_DISTANCE);
                    hands(server, true);
                })
                .await("Flugplaner am Client in der Haupthand", 100, () -> holds(InteractionHand.MAIN_HAND))
                .run("Blick geradeaus", () -> look(0.0F))
                .sleep(SETTLE_TICKS)
                .then("Haupthand: Lage des Tablets", (ctx, ticks) -> measure(ctx, ticks, InteractionHand.MAIN_HAND))
                .append(photos("haupthand"))
                .server("Flugplaner in die Nebenhand, Haupthand leer", (server, state) -> hands(server, false))
                .await("Flugplaner am Client in der Nebenhand", 100, () -> holds(InteractionHand.OFF_HAND))
                .run("Blick geradeaus", () -> look(0.0F))
                .sleep(SETTLE_TICKS)
                .then("Nebenhand: Lage des Tablets", (ctx, ticks) -> measure(ctx, ticks, InteractionHand.OFF_HAND))
                .then("Neben- und Haupthand spiegelbildlich", (ctx, ticks) -> mirrored(ctx))
                .append(photos("nebenhand"))
                .then("Bewertung", (ctx, ticks) -> verdict(ctx)), TabletHoldSteps::cleanup);
    }

    public record Hold(String arm, Vector3f center, Vector3f normal, Vector3f up, Vector3f across, float eye, boolean clips) {
        public List<String> problems() {
            List<String> out = new ArrayList<>();
            if (Math.abs(this.center.x) > MAX_SIDE) {
                out.add("Mitte " + format(Math.abs(this.center.x)) + " px seitlich (erlaubt " + format(MAX_SIDE) + ", Rumpf ±4)");
            }
            if (this.center.z > MAX_FRONT_Z) {
                out.add("Mitte nur " + format(-this.center.z) + " px vor der Körpermitte (verlangt " + format(-MAX_FRONT_Z) + ")");
            }
            if (this.center.y < MIN_HEIGHT_Y || this.center.y > MAX_HEIGHT_Y) {
                out.add("Mitte " + format(this.center.y) + " px unter den Schultern (verlangt " + format(MIN_HEIGHT_Y) + "–" + format(MAX_HEIGHT_Y) + ")");
            }
            if (this.eye < MIN_EYE) {
                out.add("Bildschirm zeigt nicht zum Spieler (Richtung zum Auge " + format(this.eye) + ", verlangt " + format(MIN_EYE) + ")");
            }
            if (Math.abs(this.up.y) > MAX_UPRIGHT) {
                out.add("hochkant (Hochachse " + format(Math.abs(this.up.y)) + " senkrecht, erlaubt " + format(MAX_UPRIGHT) + ")");
            }
            if (Math.abs(this.across.x) < MIN_ACROSS) {
                out.add("nicht quer vor dem Körper (Breite " + format(Math.abs(this.across.x)) + " quer, verlangt " + format(MIN_ACROSS) + ")");
            }
            if (this.clips) {
                out.add("ragt in den Rumpf");
            }
            return out;
        }

        public String describe() {
            return this.arm + ": Mitte x " + format(this.center.x) + " y " + format(this.center.y) + " z " + format(this.center.z)
                    + " px, Bildschirm zum Auge " + format(this.eye) + ", Hochachse senkrecht " + format(Math.abs(this.up.y))
                    + ", Breite quer " + format(Math.abs(this.across.x)) + ", im Rumpf " + (this.clips ? "ja" : "nein");
        }
    }

    public static Hold hold(LocalPlayer player, HumanoidArm arm, @Nullable Float age) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super LocalPlayer, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof AvatarRenderer<?> avatar)) {
            throw new IllegalStateException("kein Spieler-Renderer: " + renderer);
        }
        EntityRenderState raw = renderer.createRenderState(player, 1.0F);
        if (!(raw instanceof AvatarRenderState state)) {
            throw new IllegalStateException("kein Spieler-Zustand: " + raw);
        }
        if (age != null) {
            state.ageInTicks = age;
        }
        PlayerModel model = avatar.getModel();
        model.setupAnim(state);
        ItemStack stack = state.getUseItemStackForArm(arm);
        ItemStackRenderState neutral = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(neutral, stack, ItemDisplayContext.NONE, mc.level, null, 0);
        List<Vector3f> from = extents(neutral);
        List<Vector3f> to = extents(arm == HumanoidArm.RIGHT ? state.rightHandItemState : state.leftHandItemState);
        if (from.size() < 4 || from.size() != to.size()) {
            throw new IllegalStateException("Eckpunkte des Tablets fehlen (" + from.size() + " / " + to.size() + ")");
        }
        Matrix4f display = affine(from, to);
        PoseStack hand = new PoseStack();
        model.translateToHand(state, arm, hand);
        hand.rotateDegrees(Axis.XP, -90.0F);
        hand.rotateDegrees(Axis.YP, 180.0F);
        hand.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        Matrix4f toBody = frame(model.root(), model.body).invert();
        Matrix4f item = new Matrix4f(toBody).mul(hand.last().pose()).mul(display);
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (Vector3f p : from) {
            min.min(p);
            max.max(p);
        }
        Vector3f middle = new Vector3f(min).add(max).mul(0.5F);
        Vector3f center = px(item.transformPosition(new Vector3f(middle)));
        Vector3f screen = px(item.transformPosition(new Vector3f(middle.x, middle.y, max.z)));
        Vector3f normal = item.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        Vector3f up = item.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        Vector3f across = item.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
        Vector3f eye = px(new Matrix4f(toBody).mul(frame(model.root(), model.head)).transformPosition(new Vector3f(0.0F, -4.0F / PX, -4.0F / PX)));
        float toEye = normal.dot(new Vector3f(eye).sub(screen).normalize());
        boolean clips = false;
        for (int i = 0; i < GRID && !clips; i++) {
            for (int j = 0; j < GRID && !clips; j++) {
                for (float z : new float[]{min.z, max.z}) {
                    float x = min.x + (max.x - min.x) * i / (GRID - 1);
                    float y = min.y + (max.y - min.y) * j / (GRID - 1);
                    if (inTorso(px(item.transformPosition(new Vector3f(x, y, z))))) {
                        clips = true;
                    }
                }
            }
        }
        String name = (arm == HumanoidArm.RIGHT ? "rechter" : "linker") + " Arm";
        return new Hold(name, center, normal, up, across, toEye, clips);
    }

    private static StepResult measure(SelfTest.StepContext ctx, int ticks, InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return StepResult.fail("kein Spieler");
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Hold now = hold(player, arm, null);
        String key = WORST + "." + hand;
        Hold worst = (Hold) ctx.state().get(key);
        if (worst == null || now.problems().size() > worst.problems().size()) {
            ctx.state().put(key, now);
            worst = now;
        }
        if (ticks < MEASURE_TICKS) {
            return StepResult.pending();
        }
        String label = (hand == InteractionHand.MAIN_HAND ? "Haupthand" : "Nebenhand") + " (" + worst.describe() + ")";
        List<String> problems = worst.problems();
        if (!problems.isEmpty()) {
            problem(ctx, label + ": " + String.join("; ", problems));
        }
        ctx.state().put(MIRROR + "." + hand, hold(player, arm, 0.0F));
        return StepResult.pass(label);
    }

    private static StepResult mirrored(SelfTest.StepContext ctx) {
        Hold main = (Hold) ctx.state().get(MIRROR + "." + InteractionHand.MAIN_HAND);
        Hold off = (Hold) ctx.state().get(MIRROR + "." + InteractionHand.OFF_HAND);
        if (main == null || off == null) {
            return StepResult.fail("Lage einer Hand fehlt");
        }
        float dx = Math.abs(main.center().x + off.center().x);
        float dy = Math.abs(main.center().y - off.center().y);
        float dz = Math.abs(main.center().z - off.center().z);
        String text = "Abweichung vom Spiegelbild x " + format(dx) + " y " + format(dy) + " z " + format(dz) + " px";
        if (dx > MAX_MIRROR || dy > MAX_MIRROR || dz > MAX_MIRROR) {
            problem(ctx, text + " (erlaubt " + format(MAX_MIRROR) + ")");
        }
        return StepResult.pass(text);
    }

    @SuppressWarnings("unchecked")
    private static void problem(SelfTest.StepContext ctx, String text) {
        ((List<String>) ctx.state().computeIfAbsent(PROBLEMS, key -> new ArrayList<String>())).add(text);
    }

    @SuppressWarnings("unchecked")
    private static StepResult verdict(SelfTest.StepContext ctx) {
        List<String> problems = (List<String>) ctx.state().getOrDefault(PROBLEMS, List.of());
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static Script photos(String hand) {
        return Script.create()
                .run("dritte Person von hinten", () -> view(CameraType.THIRD_PERSON_BACK, BACK_PITCH))
                .then("Prüfbild " + hand + " von hinten", (ctx, ticks) -> shot(ctx, ticks, "tablet_" + hand + "_hinten"))
                .run("dritte Person von vorn", () -> view(CameraType.THIRD_PERSON_FRONT, FRONT_PITCH))
                .then("Prüfbild " + hand + " von vorn", (ctx, ticks) -> shot(ctx, ticks, "tablet_" + hand + "_vorn"))
                .run("erste Person", () -> view(CameraType.FIRST_PERSON, 0.0F));
    }

    private static void view(CameraType type, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(type);
        if (type == CameraType.FIRST_PERSON) {
            showHud(mc);
        } else if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hidHud = true;
        }
        look(pitch);
    }

    private static void showHud(Minecraft mc) {
        if (hidHud) {
            hidHud = false;
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
    }

    private static StepResult shot(SelfTest.StepContext ctx, int ticks, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks < SETTLE_TICKS) {
            if (ticks == 0) {
                Shots.clearOverlays(mc);
            }
            return StepResult.pending();
        }
        Path file = out().resolve(name + ".png");
        Object pending = ctx.state().get(SHOT);
        if (pending == null) {
            ctx.state().put(SHOT, Shots.capture(file));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<String> future = (CompletableFuture<String>) pending;
        if (!future.isDone()) {
            return ticks > 400 ? StepResult.fail("Prüfbild " + name + " hängt") : StepResult.pending();
        }
        ctx.state().remove(SHOT);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Prüfbild " + name + ": " + future.exceptionNow());
        }
        return StepResult.pass("Prüfbild " + file + " (" + future.join() + ")");
    }

    public static Path out() {
        return Services.PLATFORM.gameDir().resolve(DIR);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        showHud(mc);
        ctx.onServer(server -> cameraDistance(server, Attributes.CAMERA_DISTANCE.value().getDefaultValue()));
        return StepResult.pass();
    }

    private static void hands(MinecraftServer server, boolean main) {
        ServerPlayer player = Arena.player(server);
        ItemStack planner = new ItemStack(PlannerItems.flightPlanner().orElseThrow());
        player.setItemInHand(InteractionHand.MAIN_HAND, main ? planner : ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, main ? ItemStack.EMPTY : planner);
    }

    private static void cameraDistance(MinecraftServer server, double distance) {
        AttributeInstance attribute = Arena.player(server).getAttribute(Attributes.CAMERA_DISTANCE);
        if (attribute != null) {
            attribute.setBaseValue(distance);
        }
    }

    private static boolean holds(InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return PlannerItems.flightPlanner().map(player.getItemInHand(hand)::is).orElse(false) && player.getItemInHand(other).isEmpty();
    }

    private static void look(float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.setXRot(pitch);
            player.xRotO = pitch;
        }
    }

    private static List<Vector3f> extents(ItemStackRenderState state) {
        List<Vector3f> out = new ArrayList<>();
        state.visitExtents(p -> out.add(new Vector3f(p)));
        return out;
    }

    private static Matrix4f affine(List<Vector3f> from, List<Vector3f> to) {
        double[][] a = new double[4][7];
        for (int i = 0; i < from.size(); i++) {
            Vector3f f = from.get(i);
            Vector3f t = to.get(i);
            double[] b = {f.x, f.y, f.z, 1.0};
            double[] q = {t.x, t.y, t.z};
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    a[r][c] += b[r] * b[c];
                }
                for (int k = 0; k < 3; k++) {
                    a[r][4 + k] += b[r] * q[k];
                }
            }
        }
        for (int col = 0; col < 4; col++) {
            int pivot = col;
            for (int r = col + 1; r < 4; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            double[] swap = a[col];
            a[col] = a[pivot];
            a[pivot] = swap;
            if (Math.abs(a[col][col]) < 1.0E-12) {
                throw new IllegalStateException("Eckpunkte des Tablets liegen in einer Ebene");
            }
            for (int r = 0; r < 4; r++) {
                if (r != col) {
                    double factor = a[r][col] / a[col][col];
                    for (int c = col; c < 7; c++) {
                        a[r][c] -= factor * a[col][c];
                    }
                }
            }
        }
        Matrix4f m = new Matrix4f();
        for (int r = 0; r < 4; r++) {
            for (int k = 0; k < 3; k++) {
                m.setRowColumn(k, r, (float) (a[r][4 + k] / a[r][r]));
            }
        }
        for (int i = 0; i < from.size(); i++) {
            if (m.transformPosition(new Vector3f(from.get(i))).distance(to.get(i)) > 1.0E-4F) {
                throw new IllegalStateException("Anzeige des Tablets ist keine feste Transformation (Punkt " + i + ")");
            }
        }
        return m;
    }

    private static Matrix4f frame(ModelPart root, ModelPart part) {
        PoseStack pose = new PoseStack();
        root.translateAndRotate(pose);
        part.translateAndRotate(pose);
        return new Matrix4f(pose.last().pose());
    }

    private static boolean inTorso(Vector3f p) {
        return p.x > TORSO_MIN[0] + TORSO_MARGIN && p.x < TORSO_MAX[0] - TORSO_MARGIN
                && p.y > TORSO_MIN[1] + TORSO_MARGIN && p.y < TORSO_MAX[1] - TORSO_MARGIN
                && p.z > TORSO_MIN[2] + TORSO_MARGIN && p.z < TORSO_MAX[2] - TORSO_MARGIN;
    }

    private static Vector3f px(Vector3f blocks) {
        return blocks.mul(PX);
    }

    private static String format(double value) {
        return String.format(Locale.GERMANY, "%.2f", value);
    }
}
