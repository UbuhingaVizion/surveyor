package io.rapidpro.surveyor.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Verifies the network resilience behaviour: transient connection failures are retried for
 * idempotent requests (GET, multipart media uploads) but NOT for the submit POST.
 */
public class RetryInterceptorTest {

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(2, 10))
                .build();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void retriesGetAfterConnectionFailure() throws IOException {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        Request request = new Request.Builder().url(server.url("/x")).build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void doesNotRetryPlainPost() throws IOException {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        Request request = new Request.Builder()
                .url(server.url("/x"))
                .post(RequestBody.create("hi", MediaType.get("text/plain")))
                .build();

        try {
            client.newCall(request).execute();
            fail("expected IOException");
        } catch (IOException expected) {
            // submit POST must not be retried here (resume is handled by the upload worker)
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void retriesMultipartUpload() throws IOException {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "a.txt", RequestBody.create("data", MediaType.get("text/plain")))
                .build();
        Request request = new Request.Builder().url(server.url("/upload")).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void retriesGetAfterTimeout() throws IOException {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        OkHttpClient timeoutClient = new OkHttpClient.Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .addInterceptor(new RetryInterceptor(2, 10))
                .build();

        Request request = new Request.Builder().url(server.url("/x")).build();

        try (Response response = timeoutClient.newCall(request).execute()) {
            assertEquals(200, response.code());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void doesNotRetryServerErrors() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        Request request = new Request.Builder().url(server.url("/x")).build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(500, response.code());
        }
        // 5xx is handled by the worker's backoff, not the interceptor
        assertEquals(1, server.getRequestCount());
    }
}
