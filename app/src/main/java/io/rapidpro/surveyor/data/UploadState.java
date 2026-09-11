package io.rapidpro.surveyor.data;

import java.util.HashMap;
import java.util.Map;

import io.rapidpro.surveyor.utils.JsonUtils;

/**
 * Persisted state for a submission's upload, allowing uploads to be resumed after a failure
 * (media already uploaded is not uploaded again) and preventing duplicate submissions.
 */
public class UploadState {

    private Map<String, String> media = new HashMap<>();

    private boolean submitted;

    private int attempts;

    private String lastError;

    /**
     * Gets the remote URL for an already-uploaded local media URI (or null if not uploaded yet)
     */
    public String getUploadedUrl(String localUri) {
        return media.get(localUri);
    }

    public Map<String, String> getMedia() {
        return media;
    }

    /**
     * Records that a local media URI has been uploaded to the given remote URL
     */
    public void recordMedia(String localUri, String remoteUrl) {
        media.put(localUri, remoteUrl);
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void resetAttempts() {
        this.attempts = 0;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String toJson() {
        return JsonUtils.marshal(this);
    }

    public static UploadState fromJson(String json) {
        UploadState state = JsonUtils.unmarshal(json, UploadState.class);
        return state != null ? state : new UploadState();
    }
}
