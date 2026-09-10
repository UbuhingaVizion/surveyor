package io.rapidpro.surveyor.net;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Retries transient network failures (timeouts, connection resets) with exponential backoff.
 *
 * Only idempotent requests are retried: GET/HEAD, and multipart uploads (media). The submission
 * POST is deliberately NOT retried here - resuming a submit is handled by the upload worker using
 * the persisted upload state, which prevents duplicate submissions.
 */
public class RetryInterceptor implements Interceptor {

    private final int maxRetries;
    private final long initialBackoffMs;

    public RetryInterceptor(int maxRetries) {
        this(maxRetries, 500);
    }

    public RetryInterceptor(int maxRetries, long initialBackoffMs) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        boolean retryable = isRetryable(request);
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return chain.proceed(request);
            } catch (IOException e) {
                lastException = e;

                if (!retryable || attempt == maxRetries) {
                    throw e;
                }

                try {
                    Thread.sleep(initialBackoffMs * (1L << attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw lastException != null ? lastException : new IOException("Request failed");
    }

    private boolean isRetryable(Request request) {
        String method = request.method();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return true;
        }
        return request.body() instanceof MultipartBody;
    }
}
