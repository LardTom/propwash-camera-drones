package de.lardtom.camdrones.dev;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DevResult {
    public static final String RESULT_FILE = "camdrones-selftest-result.txt";

    private DevResult() {
    }

    public static void write(String result) {
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.error("[Kamera-Selbsttest] Ergebnisdatei nicht geschrieben", e);
        }
    }
}
