package io.rapidpro.surveyor.net;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * A streaming request body that reads from a stream, so large media (e.g. video) is never
 * buffered fully into memory. Uses chunked transfer when the size is unknown.
 */
public class ContentUriRequestBody extends RequestBody {

    /**
     * Opens a fresh stream for the body (called once per attempt)
     */
    public interface StreamProvider {
        InputStream open() throws IOException;
    }

    private final StreamProvider provider;
    private final long length;
    private final MediaType contentType;

    public ContentUriRequestBody(StreamProvider provider, long length, MediaType contentType) {
        this.provider = provider;
        this.length = length;
        this.contentType = contentType;
    }

    /**
     * Creates a body that streams from a content URI, with its size resolved when possible
     */
    public static ContentUriRequestBody forUri(ContentResolver resolver, Uri uri, MediaType contentType) {
        return new ContentUriRequestBody(
                () -> resolver.openInputStream(uri),
                sizeOf(resolver, uri),
                contentType);
    }

    private static long sizeOf(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception e) {
            // fall through to unknown length
        }
        return -1;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        InputStream stream = provider.open();
        if (stream == null) {
            throw new IOException("Unable to open stream");
        }

        try (Source source = Okio.source(stream)) {
            sink.writeAll(source);
        }
    }
}
