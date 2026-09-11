package io.rapidpro.surveyor.utils;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lightweight check that a Temba host is reachable, used to give immediate feedback when the user
 * sends submissions or changes the host.
 */
public class HostCheck {

    private HostCheck() {
    }

    /**
     * Gets whether the given host responds to an HTTP request (any status counts as reachable)
     */
    public static boolean reachable(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(host + "/api/v2/").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
