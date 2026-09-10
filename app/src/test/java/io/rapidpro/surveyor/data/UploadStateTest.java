package io.rapidpro.surveyor.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UploadStateTest {

    @Test
    public void recordsMediaAndRoundTrips() {
        UploadState state = new UploadState();

        assertNull(state.getUploadedUrl("content://local/a.jpg"));
        assertFalse(state.isSubmitted());

        state.recordMedia("content://local/a.jpg", "https://cdn/a.jpg");
        state.setSubmitted(true);

        assertEquals("https://cdn/a.jpg", state.getUploadedUrl("content://local/a.jpg"));

        UploadState restored = UploadState.fromJson(state.toJson());
        assertEquals("https://cdn/a.jpg", restored.getUploadedUrl("content://local/a.jpg"));
        assertTrue(restored.isSubmitted());
    }

    @Test
    public void tracksAttemptsAndErrors() {
        UploadState state = new UploadState();
        state.incrementAttempts();
        state.incrementAttempts();
        state.setLastError("timeout");

        UploadState restored = UploadState.fromJson(state.toJson());
        assertEquals(2, restored.getAttempts());
        assertEquals("timeout", restored.getLastError());

        restored.resetAttempts();
        assertEquals(0, restored.getAttempts());
    }

    @Test
    public void missingMediaReturnsNull() {
        UploadState restored = UploadState.fromJson("{}");
        assertNull(restored.getUploadedUrl("content://local/x.jpg"));
        assertEquals(0, restored.getAttempts());
    }
}
