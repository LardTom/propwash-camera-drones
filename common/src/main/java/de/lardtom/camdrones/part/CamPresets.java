package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public final class CamPresets {
    public static final Entry COMPACT = new Entry("compact", FlightClass.COMPACT, "compact_fold_frame", "nav_fc_compact", "m1404_1600",
            "fold_6", "cam_link_compact", "smart_2s_3850_lion", "gimbal_compact", 0xC9CDD2, 0xC9CDD2, 0x3A3F45);
    public static final Entry TOUR = new Entry("tour", FlightClass.TOUR, "tour_fold_frame", "nav_fc_pro", "m2008_500",
            "fold_9", "cam_link_pro", "smart_4s_4240_hv", "gimbal_tour", 0x5A6068, 0x5A6068, 0x2B2F33);
    public static final Entry CINE = new Entry("cine", FlightClass.CINE, "cine_frame", "nav_fc_pro", "m4114_320",
            "fold_15", "cam_link_pro", "smart_6s_8560_hv", "gimbal_cine", 0x1B1D20, 0x1B1D20, 0x1B1D20);
    public static final List<Entry> ALL = List.of(COMPACT, TOUR, CINE);

    private CamPresets() {
    }

    public static Identifier part(String path) {
        return CamDrones.id(path);
    }

    public static Optional<Entry> byPath(String path) {
        for (Entry entry : ALL) {
            if (entry.path().equals(path)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static Optional<FlightClass> classOf(Identifier frame) {
        for (Entry entry : ALL) {
            if (part(entry.frame()).equals(frame)) {
                return Optional.of(entry.flightClass());
            }
        }
        return Optional.empty();
    }

    public record Entry(String path, FlightClass flightClass, String frame, String stack, String motor, String prop, String video,
                        String battery, String gimbal, int frameColor, int motorColor, int gimbalColor) {
        public Identifier id() {
            return CamDrones.id(this.path);
        }

        public String translationKey() {
            return CamDrones.MOD_ID + ".preset." + this.path;
        }

        public BuildSpec build() {
            return BuildSpec.of(part(this.frame), part(this.stack), part(this.motor), part(this.prop), part(this.video), part(this.battery))
                    .withAccessory(AccessorySlot.FRONT, part(this.gimbal));
        }

        public PaintSpec paint() {
            return PaintSpec.DEFAULT.with("frame", this.frameColor).with("motors", this.motorColor).with("accessories", this.gimbalColor);
        }

        public DroneData data() {
            return new DroneData(this.build(), this.paint(), TuneSpec.EMPTY, Optional.empty());
        }

        public DronePreset preset() {
            return DronePreset.of(this.id(), this.data());
        }
    }
}
