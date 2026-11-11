package de.lardtom.camdrones.core;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModInfoTest {
    private static Properties gradleProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(RepoFiles.root().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void modIdMatchesBuildAndIsNotReserved() throws IOException {
        Properties properties = gradleProperties();
        assertEquals(CamDrones.MOD_ID, properties.getProperty("mod_id"));
        assertNotEquals(PropwashApi.MOD_ID, CamDrones.MOD_ID);
        assertTrue(CamDrones.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"), CamDrones.MOD_ID);
    }

    @Test
    void requiredApiMatchesBuild() throws IOException {
        assertEquals(gradleProperties().getProperty("propwash_api_version"), CamDrones.REQUIRED_API);
        assertTrue(CamDrones.REQUIRED_API.matches("1\\.\\d+"), CamDrones.REQUIRED_API);
        String[] compiled = PropwashApi.API_VERSION.split("\\.");
        String[] required = CamDrones.REQUIRED_API.split("\\.");
        assertEquals(compiled[0], required[0]);
        assertTrue(Integer.parseInt(compiled[1]) >= Integer.parseInt(required[1]), PropwashApi.API_VERSION);
    }

    @Test
    void versionFollowsReleaseScheme() throws IOException {
        String version = gradleProperties().getProperty("version");
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-(dev|rc)\\.\\d+)?"), version);
    }
}
