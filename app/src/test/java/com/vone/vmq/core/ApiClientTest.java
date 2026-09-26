package com.vone.vmq.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiClientTest {
    private boolean request(MockResponse response) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response); server.start();
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] success = {false};
            ApiClient.get(server.url("/appHeart"), (ok, message) -> { success[0] = ok; latch.countDown(); });
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, server.getRequestCount());
            return success[0];
        }
    }
    @Test public void checksApplicationSuccess() throws Exception {
        assertTrue(request(new MockResponse().setBody("{\"code\":1}")));
        assertFalse(request(new MockResponse().setBody("{\"code\":-1,\"msg\":\"bad signature\"}")));
    }
    @Test public void rejectsHtmlHttpErrorsAndOversizedBodies() throws Exception {
        assertFalse(request(new MockResponse().setBody("<html>login</html>")));
        assertFalse(request(new MockResponse().setResponseCode(500).setBody("{\"code\":1}")));
        assertFalse(request(new MockResponse().setBody(new String(new char[17000]).replace('\0', 'x'))));
    }
    @Test public void doesNotFollowRedirects() throws Exception {
        assertFalse(request(new MockResponse().setResponseCode(302).setHeader("Location", "/other")));
    }
}
