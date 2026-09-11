package io.rapidpro.surveyor;

public interface SurveyorPreferences {
    /**
     * Host we are connected to
     */
    String HOST = "host";

    /**
     * Username/email we are logged in as. If this is set, we are logged in
     */
    String AUTH_USERNAME = "auth_username";

    /**
     * Username/email we were previously logged in as - used to prepopulate login form
     */
    String PREV_USERNAME = "prev_username";

    /**
     * UUIDs of the orgs this user has access to
     */
    String AUTH_ORGS = "auth_orgs";

    /**
     * Whether submissions may be sent over mobile data, or only over Wi-Fi
     */
    String SEND_OVER = "send_over";

    String SEND_OVER_WIFI = "wifi";

    String SEND_OVER_ANY = "any";

    /**
     * Preferred flow language code (ISO 639-3, e.g. "eng", "fra", "run"). Empty means use the
     * org's primary (first) language.
     */
    String LANGUAGE = "language";

    /**
     * Prefix for per-flow language overrides. The full key is the prefix plus the flow UUID.
     */
    String LANGUAGE_PREFIX = "language_";

    /**
     * Whether we have already asked for the notification permission on this device
     */
    String NOTIFICATIONS_ASKED = "notifications_asked";

    /**
     * Gets the preference key for a per-flow language override
     */
    static String languageKey(String flowUuid) {
        return LANGUAGE_PREFIX + flowUuid;
    }
}
