package de.lardtom.camdrones.planner;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.ViewSettingUpload;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.UploadCheck;
import de.lardtom.camdrones.planner.model.ViewSetting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewSettingTest {
    private static final UploadCheck.Drone OWN = new UploadCheck.Drone(true, true, false, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Drone FOREIGN = new UploadCheck.Drone(true, false, false, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Drone FLOWN_BY_OTHER = new UploadCheck.Drone(true, true, true, false, 0.0, 64.0, 0.0);

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes());
        return decoded;
    }

    @Test
    void stepsToggleBetweenOsdAndCamera() {
        assertEquals(ViewSetting.CAMERA, ViewSetting.OSD.step(1));
        assertEquals(ViewSetting.OSD, ViewSetting.CAMERA.step(1));
        assertEquals(ViewSetting.CAMERA, ViewSetting.OSD.step(-1));
        assertEquals(ViewSetting.OSD, ViewSetting.OSD.step(0));
        assertEquals(Optional.empty(), ViewSetting.byId(-1));
        assertEquals(Optional.empty(), ViewSetting.byId(ViewSetting.values().length));
    }

    @Test
    void everyChoiceMapsToTheDroneSettingAndBack() {
        assertEquals(ViewSetting.values().length, DroneSettings.CameraView.values().length);
        assertEquals(DroneSettings.CameraView.HUD, ViewSettings.cameraView(ViewSetting.CAMERA));
        assertEquals(DroneSettings.CameraView.OSD, ViewSettings.cameraView(ViewSetting.OSD));
        for (ViewSetting setting : ViewSetting.values()) {
            DroneSettings.CameraView view = ViewSettings.cameraView(setting);
            assertEquals(setting, ViewSettings.of(view));
            DroneSettings stored = DroneSettings.DEFAULT.withExit(DroneSettings.ExitBehaviour.LAND).withView(view);
            assertEquals(view, stored.view());
            assertEquals(DroneSettings.ExitBehaviour.LAND, stored.exit(), "Ansicht ändert das Verhalten beim Verlassen nicht");
            JsonElement json = DroneSettings.CODEC.encodeStart(JsonOps.INSTANCE, stored).getOrThrow();
            assertEquals(stored, DroneSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        assertEquals(ViewSetting.OSD, ViewSettings.of(DroneSettings.DEFAULT.view()));
    }

    @Test
    void serverChecksRateIdPairingAndOwner() {
        int camera = ViewSetting.CAMERA.ordinal();
        assertEquals(ExitSetting.Result.RATE_LIMITED, ViewSetting.check(false, camera, true, OWN, false));
        assertEquals(ExitSetting.Result.INVALID, ViewSetting.check(true, 5, true, OWN, false));
        assertEquals(ExitSetting.Result.NOT_LINKED, ViewSetting.check(true, camera, false, OWN, false));
        assertEquals(ExitSetting.Result.NOT_FOUND, ViewSetting.check(true, camera, true, UploadCheck.Drone.MISSING, false));
        assertEquals(ExitSetting.Result.NOT_OWNER, ViewSetting.check(true, camera, true, FOREIGN, false));
        assertEquals(ExitSetting.Result.NOT_OWNER, ViewSetting.check(true, camera, true, FLOWN_BY_OTHER, false));
        assertEquals(ExitSetting.Result.OK, ViewSetting.check(true, camera, true, OWN, false));
        assertEquals(ExitSetting.Result.OK, ViewSetting.check(true, camera, true, FOREIGN, true));
    }

    @Test
    void requestAndReplyRoundTripInTheOwnNamespace() {
        UUID serial = UUID.randomUUID();
        for (ViewSetting setting : ViewSetting.values()) {
            ViewSettingUpload.Request request = roundTrip(ViewSettingUpload.Request.CODEC, ViewSettingUpload.Request.of(9, serial, setting));
            assertEquals(9, request.requestId());
            assertEquals(serial, request.serial());
            assertEquals(setting, ViewSetting.byId(request.view()).orElseThrow());
        }
        for (ExitSetting.Result result : ExitSetting.Result.values()) {
            ViewSettingUpload.Reply reply = roundTrip(ViewSettingUpload.Reply.CODEC, new ViewSettingUpload.Reply(4, result, 1));
            assertEquals(result, reply.result());
            assertEquals(1, reply.view());
        }
        assertEquals("camdrones:view_setting", ViewSettingUpload.REQUEST.id().toString());
        assertEquals("camdrones:view_setting_reply", ViewSettingUpload.REPLY.id().toString());
        CamNetwork.define();
        Set<CustomPacketPayload.Type<?>> types = new HashSet<>();
        CamNetwork.serverBound().forEach(entry -> types.add(entry.type()));
        CamNetwork.clientBound().forEach(entry -> types.add(entry.type()));
        assertTrue(types.contains(ViewSettingUpload.REQUEST));
        assertTrue(types.contains(ViewSettingUpload.REPLY));
    }

    @Test
    void editorHasAViewFieldNextToExitAndAllTextsExist() {
        assertEquals(EditorLayout.SETTING_EXIT + 1, EditorLayout.SETTING_VIEW);
        assertEquals(EditorLayout.SETTING_VIEW + 1, EditorLayout.SETTING_FIELDS);
        assertTrue(EditorCatalog.settingLabelKeys().contains(ViewSetting.LABEL_KEY));
        for (char decimal : new char[]{'.', ','}) {
            for (ViewSetting setting : ViewSetting.values()) {
                assertTrue(EditorCatalog.settingValueSamples(decimal).contains(setting.value()), setting.name());
            }
        }
        for (String language : PlannerLang.LANGUAGES) {
            PlannerLang lang = PlannerLang.of(language);
            assertTrue(lang.has(ViewSetting.LABEL_KEY), language);
            assertTrue(lang.has(ViewSetting.TOOLTIP_KEY), language);
            for (ViewSetting setting : ViewSetting.values()) {
                assertEquals(FieldText.PREFIX + "value.view." + setting.key(), setting.value().key());
                assertTrue(lang.has(setting.value().key()), language + " " + setting);
            }
            for (ExitSetting.Result result : ExitSetting.Result.values()) {
                assertTrue(lang.has(ViewSetting.messageKey(result)), language + " " + result);
            }
        }
        assertEquals("Kamera", PlannerLang.of("de_de").text(ViewSetting.CAMERA.value().key(), java.util.List.of()));
    }
}
