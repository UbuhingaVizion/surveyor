package io.rapidpro.surveyor.utils;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OrgColorsTest {

    @Test
    public void deterministicForSameUuid() {
        String uuid = "efc63183-6de1-40fc-af93-25103a6bb53b";

        assertEquals(OrgColors.getPrimaryColor(uuid, null), OrgColors.getPrimaryColor(uuid, null));
    }

    @Test
    public void differentUuidsProduceVariety() {
        Set<Integer> colors = new HashSet<>();
        String[] uuids = {"a-uuid", "b-uuid", "c-uuid", "d-uuid", "e-uuid", "f-uuid", "g-uuid", "h-uuid"};

        for (String uuid : uuids) {
            colors.add(OrgColors.getPrimaryColor(uuid, null));
        }

        assertTrue("expected more than one color", colors.size() > 1);
    }

    @Test
    public void customColorOverridesDeterministic() {
        assertEquals(0xFFD84315, OrgColors.getPrimaryColor("some-uuid", "#D84315"));
    }

    @Test
    public void invalidCustomColorFallsBackToDeterministic() {
        int expected = OrgColors.getPrimaryColor("some-uuid", null);

        assertEquals(expected, OrgColors.getPrimaryColor("some-uuid", "not-a-color"));
        assertEquals(expected, OrgColors.getPrimaryColor("some-uuid", "#12"));
    }

    @Test
    public void parseValidatesHex() {
        assertNull(OrgColors.parse(null));
        assertNull(OrgColors.parse("red"));
        assertNull(OrgColors.parse("#12345"));
        assertEquals(Integer.valueOf(0xFFD84315), OrgColors.parse("#D84315"));
    }

    @Test
    public void initialUsesFirstLetter() {
        assertEquals("U", OrgColors.getInitial("UbuViz"));
        assertEquals("?", OrgColors.getInitial("  "));
        assertEquals("?", OrgColors.getInitial((String) null));
    }

    @Test
    public void paletteMeetsWcagAaAgainstWhite() {
        for (int color : OrgColors.PALETTE) {
            double ratio = contrastWithWhite(color);
            assertTrue("color " + Integer.toHexString(color) + " contrast was " + ratio, ratio >= 4.5);
        }
    }

    private static double contrastWithWhite(int color) {
        return 1.05 / (relativeLuminance(color) + 0.05);
    }

    private static double relativeLuminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
