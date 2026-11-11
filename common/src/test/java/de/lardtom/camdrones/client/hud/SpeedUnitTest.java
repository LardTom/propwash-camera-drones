package de.lardtom.camdrones.client.hud;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedUnitTest {
    @Test
    void metresPerSecondStayTheDefaultAndUnchanged() {
        assertEquals(SpeedUnit.METRES_PER_SECOND, SpeedUnit.DEFAULT);
        assertEquals("4.2m/s", HudText.hudSpeed(4.24, SpeedUnit.METRES_PER_SECOND));
        assertEquals(HudText.hudSpeed(-12.5), HudText.hudSpeed(-12.5, SpeedUnit.METRES_PER_SECOND));
    }

    @Test
    void kilometresPerHourInWholeNumbers() {
        assertEquals("36km/h", HudText.hudSpeedKmh(10.0));
        assertEquals("36km/h", HudText.hudSpeed(10.0, SpeedUnit.KILOMETRES_PER_HOUR));
        assertEquals("15km/h", HudText.hudSpeedKmh(4.24));
        assertEquals("0km/h", HudText.hudSpeedKmh(-0.1));
        assertEquals("-11km/h", HudText.hudSpeedKmh(-3.0));
        assertEquals("108km/h", HudText.hudSpeedKmh(30.0));
        assertEquals(HudText.UNKNOWN, HudText.hudSpeedKmh(Double.NaN));
        assertEquals(HudText.UNKNOWN, HudText.hudSpeed(Double.POSITIVE_INFINITY, SpeedUnit.KILOMETRES_PER_HOUR));
    }

    @Test
    void speedsFitTheInfoBlockInBothUnits() {
        for (SpeedUnit unit : SpeedUnit.values()) {
            for (double speed = -30.0; speed <= 30.0; speed += 0.37) {
                String text = HudText.hudSpeed(speed, unit);
                assertTrue(HudText.printable(text), text);
                assertTrue(text.length() <= 8, unit + " zu lang: " + text);
            }
            String line = "H.S. " + HudText.hudSpeed(30.0, unit) + "  V.S. " + HudText.hudSpeed(-9.9, unit);
            assertTrue(line.length() <= CameraViewLayout.INFO_CHARS, unit + ": " + line);
        }
    }

    @Test
    void symbolsRoundTripAndTheButtonCycles() {
        for (SpeedUnit unit : SpeedUnit.values()) {
            assertEquals(Optional.of(unit), SpeedUnit.bySymbol(unit.symbol()));
            assertTrue(unit.key().startsWith("camdrones.hud.unit."), unit.key());
        }
        assertEquals(SpeedUnit.KILOMETRES_PER_HOUR, SpeedUnit.METRES_PER_SECOND.next());
        assertEquals(SpeedUnit.METRES_PER_SECOND, SpeedUnit.KILOMETRES_PER_HOUR.next());
        assertEquals(Optional.empty(), SpeedUnit.bySymbol("mph"));
        assertEquals(36.0, SpeedUnit.KILOMETRES_PER_HOUR.convert(10.0), 1.0E-9);
    }
}
