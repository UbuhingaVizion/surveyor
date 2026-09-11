package io.rapidpro.surveyor.utils;

import io.rapidpro.surveyor.data.Org;

/**
 * Assigns a visual identity (color + initial) to each organization. Colors are chosen
 * deterministically from the org UUID unless a custom color has been set by a supervisor.
 *
 * The palette is made of dark shades so white text on top meets WCAG AA (contrast >= 4.5:1).
 */
public class OrgColors {

    /**
     * Curated accessible palette. Navy is first so it is the default for the default org.
     */
    public static final int[] PALETTE = {
            0xFF002B65, // Navy
            0xFF00695C, // Teal
            0xFFBF360C, // Deep Orange
            0xFF283593, // Indigo
            0xFF2E7D32, // Forest Green
            0xFF6A1B9A, // Purple
            0xFF00838F, // Cyan
            0xFFAD1457, // Crimson
            0xFF8D6E00, // Amber / Gold
            0xFF1565C0, // Blue
            0xFF33691E, // Olive
            0xFF37474F, // Slate
    };

    private OrgColors() {
    }

    /**
     * Gets the color for the given org: its custom color if set and valid, otherwise a stable
     * color picked from the palette using the org UUID.
     */
    public static int getPrimaryColor(Org org) {
        return getPrimaryColor(org == null ? null : org.getUuid(), org == null ? null : org.getColor());
    }

    /**
     * Gets the color for an org UUID and optional custom color (visible for testing)
     */
    public static int getPrimaryColor(String uuid, String customColor) {
        Integer custom = parse(customColor);
        if (custom != null) {
            return custom;
        }

        int index = uuid == null ? 0 : Math.floorMod(uuid.hashCode(), PALETTE.length);
        return PALETTE[index];
    }

    /**
     * Gets the uppercase initial to show on the org avatar
     */
    public static String getInitial(Org org) {
        return getInitial(org == null ? null : org.getName());
    }

    /**
     * Gets the uppercase initial for an org name (visible for testing)
     */
    public static String getInitial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }

    /**
     * Parses a "#RRGGBB" hex color, returning null if it isn't valid
     */
    public static Integer parse(String hex) {
        if (hex == null) {
            return null;
        }

        String value = hex.trim();
        if (!value.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }

        try {
            long rgb = Long.parseLong(value.substring(1), 16);
            return (int) (0xFF000000L | rgb);
        } catch (Exception e) {
            return null;
        }
    }
}
