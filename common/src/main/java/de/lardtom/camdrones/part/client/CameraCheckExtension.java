package de.lardtom.camdrones.part.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.part.CameraCheck;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class CameraCheckExtension implements WorkbenchExtension {
    public static final Identifier ID = CamDrones.id("camera_check");
    public static final int ORDER = 1100;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void init(WorkbenchContext context) {
        context.addButton(Component.translatable(CameraCheck.TITLE), Component.translatable(CameraCheck.SHORT),
                Component.translatable(CameraCheck.TOOLTIP), () -> open(context));
    }

    public static CameraCheck.Input read(WorkbenchContext context) {
        List<Identifier> accessories = new ArrayList<>();
        for (int position = 0; position < AccessorySlot.values().length; position++) {
            context.slot(PartKind.ACCESSORY, position).ifPresent(accessories::add);
        }
        return new CameraCheck.Input(context.slot(PartKind.FRAME, 0), context.slot(PartKind.STACK, 0), context.slot(PartKind.VIDEO, 0),
                context.slot(PartKind.BATTERY, 0), accessories);
    }

    public static void open(WorkbenchContext context) {
        CameraCheck.Result result = CameraCheck.check(read(context));
        context.openChild(parent -> new CameraCheckScreen(parent, result));
    }
}
