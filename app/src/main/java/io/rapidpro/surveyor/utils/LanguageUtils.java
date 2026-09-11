package io.rapidpro.surveyor.utils;

/**
 * Helpers for flow language codes (ISO 639-3)
 */
public class LanguageUtils {

    /**
     * Gets a human readable name for a language code, falling back to the code itself
     */
    public static String displayName(String code) {
        if (code == null) {
            return "";
        }

        switch (code) {
            case "eng":
                return "English";
            case "fra":
                return "Français";
            case "run":
                return "Ikirundi";
            default:
                return code;
        }
    }
}
