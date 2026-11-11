package de.lardtom.camdrones.client.planner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.hud.SpeedUnit;
import de.lardtom.camdrones.client.record.FlashbackLink;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class CamClientConfig {
    public static final String FILE_NAME = "camdrones-client.json";
    public static final String PREVIEW_MARKERS = "preview_markers";
    public static final boolean PREVIEW_MARKERS_DEFAULT = true;
    public static final String SPEED_UNIT = "speed_unit";
    public static final SpeedUnit SPEED_UNIT_DEFAULT = SpeedUnit.DEFAULT;
    public static final String WARNING_VOLUME = "warning_volume";
    public static final double WARNING_VOLUME_DEFAULT = 0.6;
    public static final double WARNING_VOLUME_STEP = 0.05;
    public static final String SERVO_VOLUME = "gimbal_servo_volume";
    public static final double SERVO_VOLUME_DEFAULT = 0.15;
    public static final String RECORDING_MODE = "recording_mode";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static CamClientConfig instance;

    private final Path file;
    private final JsonObject values;

    private CamClientConfig(Path file, JsonObject values) {
        this.file = file;
        this.values = values;
    }

    public static synchronized CamClientConfig get() {
        if (instance == null) {
            instance = load(Services.PLATFORM.configDir().resolve(FILE_NAME));
        }
        return instance;
    }

    public static CamClientConfig load(Path file) {
        Objects.requireNonNull(file, "file");
        JsonObject values = new JsonObject();
        boolean complete = false;
        if (Files.isRegularFile(file)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    values = parsed.getAsJsonObject();
                    complete = readBoolean(values, PREVIEW_MARKERS).isPresent() && readUnit(values).isPresent() && readVolume(values).isPresent()
                            && readVolume(values, SERVO_VOLUME).isPresent();
                } else {
                    CamDrones.LOG.warn("{}: {} ist kein JSON-Objekt, Standardwerte", CamDrones.MOD_NAME, file.getFileName());
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                CamDrones.LOG.warn("{}: {} nicht lesbar, Standardwerte: {}", CamDrones.MOD_NAME, file.getFileName(), e.toString());
                values = new JsonObject();
            }
        }
        CamClientConfig config = new CamClientConfig(file, values);
        if (!complete) {
            values.addProperty(PREVIEW_MARKERS, config.previewMarkers());
            values.addProperty(SPEED_UNIT, config.speedUnit().symbol());
            values.addProperty(WARNING_VOLUME, config.warningVolume());
            values.addProperty(SERVO_VOLUME, config.servoVolume());
            config.save();
        }
        return config;
    }

    private static Optional<Boolean> readBoolean(JsonObject values, String key) {
        JsonElement element = values.get(key);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return Optional.of(primitive.getAsBoolean());
        }
        return Optional.empty();
    }

    private static Optional<SpeedUnit> readUnit(JsonObject values) {
        JsonElement element = values.get(SPEED_UNIT);
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return SpeedUnit.bySymbol(primitive.getAsString());
        }
        return Optional.empty();
    }

    private static Optional<Double> readVolume(JsonObject values) {
        return readVolume(values, WARNING_VOLUME);
    }

    private static Optional<Double> readVolume(JsonObject values, String key) {
        JsonElement element = values.get(key);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double volume = primitive.getAsDouble();
            if (Double.isFinite(volume) && volume >= 0.0 && volume <= 1.0) {
                return Optional.of(volume);
            }
        }
        return Optional.empty();
    }

    public static double clampVolume(double volume) {
        return clampVolume(volume, WARNING_VOLUME_DEFAULT);
    }

    public static double clampVolume(double volume, double fallback) {
        if (!Double.isFinite(volume)) {
            return fallback;
        }
        return Math.round(Math.clamp(volume, 0.0, 1.0) * 100.0) / 100.0;
    }

    public Path file() {
        return this.file;
    }

    public synchronized boolean previewMarkers() {
        return readBoolean(this.values, PREVIEW_MARKERS).orElse(PREVIEW_MARKERS_DEFAULT);
    }

    public synchronized boolean previewMarkers(boolean on) {
        if (this.previewMarkers() == on && this.values.has(PREVIEW_MARKERS)) {
            return true;
        }
        this.values.addProperty(PREVIEW_MARKERS, on);
        return this.save();
    }

    public synchronized SpeedUnit speedUnit() {
        return readUnit(this.values).orElse(SPEED_UNIT_DEFAULT);
    }

    public synchronized boolean speedUnit(SpeedUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (readUnit(this.values).orElse(null) == unit) {
            return true;
        }
        this.values.addProperty(SPEED_UNIT, unit.symbol());
        return this.save();
    }

    public synchronized double warningVolume() {
        return readVolume(this.values).orElse(WARNING_VOLUME_DEFAULT);
    }

    public synchronized boolean warningVolume(double volume) {
        double clamped = clampVolume(volume);
        if (readVolume(this.values).filter(v -> v == clamped).isPresent()) {
            return true;
        }
        this.values.addProperty(WARNING_VOLUME, clamped);
        return this.save();
    }

    public synchronized double servoVolume() {
        return readVolume(this.values, SERVO_VOLUME).orElse(SERVO_VOLUME_DEFAULT);
    }

    public synchronized boolean servoVolume(double volume) {
        double clamped = clampVolume(volume, SERVO_VOLUME_DEFAULT);
        if (readVolume(this.values, SERVO_VOLUME).filter(v -> v == clamped).isPresent()) {
            return true;
        }
        this.values.addProperty(SERVO_VOLUME, clamped);
        return this.save();
    }

    public static Optional<RecordingMode> readMode(JsonObject values) {
        JsonElement element = values.get(RECORDING_MODE);
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return RecordingMode.byKey(primitive.getAsString());
        }
        return Optional.empty();
    }

    public synchronized Optional<RecordingMode> chosenRecordingMode() {
        return readMode(this.values);
    }

    public synchronized RecordingMode recordingMode() {
        return this.recordingMode(FlashbackLink.installed());
    }

    public synchronized RecordingMode recordingMode(boolean flashbackInstalled) {
        RecordingMode fallback = RecordingMode.defaultFor(flashbackInstalled);
        return readMode(this.values).filter(mode -> mode.selectable(flashbackInstalled)).orElse(fallback);
    }

    public synchronized boolean recordingMode(RecordingMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (readMode(this.values).orElse(null) == mode) {
            return true;
        }
        this.values.addProperty(RECORDING_MODE, mode.key());
        return this.save();
    }

    public synchronized boolean resetRecordingMode() {
        if (!this.values.has(RECORDING_MODE)) {
            return true;
        }
        this.values.remove(RECORDING_MODE);
        return this.save();
    }

    public synchronized boolean save() {
        try {
            Path parent = this.file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(this.values) + "\n", StandardCharsets.UTF_8);
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            CamDrones.LOG.warn("{}: {} nicht gespeichert: {}", CamDrones.MOD_NAME, this.file.getFileName(), e.toString());
            return false;
        }
    }
}
