package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.NavInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTextTest {
    private static final double[] VALUES = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0E9, -123.456, -0.04, -0.0, 0.0,
            0.04, 0.05, 4.2, 9.94, 9.96, 10.0, 99.5, 4242.0, 1.0E12};

    @Test
    void asciiReplacesEverythingOutsidePrintableAscii() {
        assertEquals("G -45?", HudText.ascii("G -45°"));
        assertEquals("? 4,2 m", HudText.ascii("◀ 4,2 m"));
        assertEquals("H?he", HudText.ascii("Höhe"));
        assertEquals("", HudText.ascii(null));
        assertTrue(HudText.printable(" ~"));
        assertFalse(HudText.printable("a\tb"));
    }

    @Test
    void designValuesInAscii() {
        assertEquals("G -45", HudText.tilt(-45.2));
        assertEquals("G 0", HudText.tilt(-0.3));
        assertEquals("G 30", HudText.tilt(29.6));
        assertEquals("WP 3/12 0:42", HudText.mission(2, 12, 42.9));
        assertEquals("WP 3/12", HudText.mission(2, 12, Double.NaN));
        assertEquals("WP 50/50 1:00:00", HudText.mission(49, 50, 3600.0));
        assertEquals("< 4.2M", HudText.obstacle(NavInput.Sensor.LEFT, 4.2));
        assertEquals("> 7.9M", HudText.obstacle(NavInput.Sensor.RIGHT, 7.94));
        assertEquals("^ 1.5M", HudText.obstacle(NavInput.Sensor.FORWARD, 1.5));
        assertEquals("v 0.0M", HudText.obstacle(NavInput.Sensor.BACK, -0.2));
        assertEquals("LEFT 24:10", HudText.flightLeft(1450.0));
        assertEquals("LEFT -:--", HudText.flightLeft(Double.NaN));
        assertEquals("LEFT 99:59", HudText.flightLeft(7200.0));
        assertEquals("WP 50/50 9:59:59", HudText.mission(49, 50, 1.0E9));
    }

    @Test
    void numbersUseOneDecimalBelowTenAndPointAsSeparator() {
        assertEquals("4.2M", HudText.metres(4.2));
        assertEquals("12M", HudText.metres(12.4));
        assertEquals("--", HudText.metres(Double.NaN));
        assertEquals("0.0", HudText.number(-0.04));
        assertEquals("-3.4", HudText.number(-3.42));
        assertEquals("12m", HudText.hudMetres(12.3));
        assertEquals("8.5m", HudText.hudMetres(8.46));
        assertEquals("4.2m/s", HudText.hudSpeed(4.24));
        assertEquals("78%", HudText.percent(0.781));
        assertEquals("100%", HudText.percent(1.7));
        assertEquals("0%", HudText.percent(-0.5));
    }

    @Test
    void durations() {
        assertEquals("0:00", HudText.duration(0.0));
        assertEquals("0:42", HudText.duration(42.99));
        assertEquals("24:10", HudText.duration(1450.0));
        assertEquals("1:02:03", HudText.duration(3723.0));
        assertEquals("99:59:59", HudText.duration(1.0E12));
        assertEquals("-:--", HudText.duration(-1.0));
        assertEquals("-:--", HudText.duration(Double.NaN));
        assertEquals("75:00", HudText.minutes(4500.0));
        assertEquals("99:59", HudText.minutes(1.0E9));
        assertEquals("-:--", HudText.minutes(-3.0));
    }

    @Test
    void everyFormatterStaysPrintableAscii() {
        for (double value : VALUES) {
            for (String text : new String[]{HudText.metres(value), HudText.duration(value), HudText.minutes(value), HudText.tilt(value), HudText.flightLeft(value),
                    HudText.mission(3, 12, value), HudText.hudMetres(value), HudText.hudSpeed(value), HudText.percent(value),
                    HudText.decimal(value, 1), HudText.number(value)}) {
                assertTrue(HudText.printable(text), text);
            }
            for (NavInput.Sensor sensor : NavInput.Sensor.values()) {
                assertTrue(HudText.printable(HudText.obstacle(sensor, value)), sensor + " " + value);
            }
        }
    }
}
