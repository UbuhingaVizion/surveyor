package io.rapidpro.surveyor.net;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okio.Buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ContentUriRequestBodyTest {

    @Test
    public void streamsAllBytes() throws Exception {
        byte[] data = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

        ContentUriRequestBody body = new ContentUriRequestBody(
                () -> new ByteArrayInputStream(data),
                data.length,
                MediaType.get("multipart/form-data"));

        Buffer buffer = new Buffer();
        body.writeTo(buffer);

        assertArrayEquals(data, buffer.readByteArray());
    }

    @Test
    public void unknownLengthIsChunked() {
        ContentUriRequestBody body = new ContentUriRequestBody(
                () -> new ByteArrayInputStream(new byte[0]),
                -1,
                MediaType.get("multipart/form-data"));

        assertEquals(-1, body.contentLength());
    }
}
