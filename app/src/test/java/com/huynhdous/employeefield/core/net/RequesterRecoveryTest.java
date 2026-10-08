package com.huynhdous.employeefield.core.net;

import android.app.Activity;
import android.os.Looper;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.session.SessionWork;
import java.io.*;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RequesterRecoveryTest {
    @Test public void concurrentTabReadsBothComplete() throws Exception {
        Harness h = new Harness("parallel-reads");
        CountDownLatch opened = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Requester requester = h.requester(action -> new Connection(opened, release));
        AtomicInteger answers = new AtomicInteger();
        requester.request("schedule", new JSONObject(), null, data -> answers.incrementAndGet(), false, null);
        requester.request("trip", new JSONObject(), null, data -> answers.incrementAndGet(), false, null);
        try { assertTrue(opened.await(5, TimeUnit.SECONDS)); }
        finally { release.countDown(); }
        await(() -> answers.get() == 2);
    }

    @Test public void recreatedRequesterSeesOutstandingMutationAndIgnoresOldSessionAnswer() throws Exception {
        Harness h = new Harness("old-mutation");
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Requester requester = h.requester(action -> new Connection(opened, release));
        AtomicInteger answers = new AtomicInteger();
        requester.request("timeclock/clock-in", new JSONObject(), null, data -> answers.incrementAndGet(), false, null);
        try {
            assertTrue(opened.await(5, TimeUnit.SECONDS));
            assertTrue(h.requester(action -> { throw new AssertionError("No request expected"); }).isBusy());
            h.session.token = "replacement-session";
        } finally { release.countDown(); }
        await(() -> !requester.isBusy() && !SessionWork.isBusy("old-mutation"));
        assertEquals(0, answers.get());
    }

    @Test public void malformedFeatureAnswerDoesNotEndAuthentication() throws Exception {
        Harness h = new Harness("feature-error");
        CountDownLatch opened = new CountDownLatch(1);
        Requester requester = h.requester(action -> new Connection(opened, new CountDownLatch(0)));
        AtomicInteger callbacks = new AtomicInteger();
        requester.request("schedule", new JSONObject(), null, data -> {
            callbacks.incrementAndGet();
            throw new IOException("invalid feature payload");
        }, false, null);
        await(() -> callbacks.get() == 1);
        assertEquals(0, h.expired.get());
        assertEquals(0, h.unusable.get());
        assertEquals("feature-error", h.session.token);
    }

    @Test public void recreatedAuthenticationVerifiesDuringAnUploadWithoutAllowingAnotherMutation() throws Exception {
        Harness h = new Harness("recreated-auth-upload");
        h.session.employeeId = 0; // App.start() restores the token before me restores the employee.
        h.activity.getSharedPreferences("employee_field", Activity.MODE_PRIVATE).edit()
                .putBoolean("tracking_notice_confirmed_7", true).commit();
        AtomicInteger opened = new AtomicInteger(), ready = new AtomicInteger();
        Requester requester = h.requester(action -> {
            assertEquals("me", action);
            opened.incrementAndGet();
            return new Connection(new CountDownLatch(0), new CountDownLatch(0),
                    "{\"success\":true,\"employee_id\":7,\"employee_name\":\"Test Employee\","
                    + "\"username\":\"test\",\"expires_utc\":\"2030-01-01 00:00:00\",\"program_code\":\"S2S\"}");
        });
        com.huynhdous.employeefield.auth.SignOut signOut = new com.huynhdous.employeefield.auth.SignOut(
                h.activity, h.session, requester,
                new com.huynhdous.employeefield.location.TrackingStarter(h.activity, h.session), () -> { });
        try (SessionWork.Lease upload = SessionWork.begin(h.session.token, 7)) {
            assertNotNull(upload);
            com.huynhdous.employeefield.auth.AuthFlow auth = new com.huynhdous.employeefield.auth.AuthFlow(
                    h.activity, h.session, requester, signOut, ready::incrementAndGet);
            auth.verify();
            await(() -> ready.get() == 1);
            assertEquals(7, h.session.employeeId);
            assertTrue(requester.isBusy()); // The upload still owns the mutation slot.
            requester.request("timeclock/punch", new JSONObject(), null, data -> fail("Write must stay blocked"), false, null);
            assertEquals(1, opened.get());
        } finally {
            signOut.close();
        }
        assertFalse(requester.isBusy());
    }

    @Test public void sessionVerificationCannotReviveAnEndedSession() throws Exception {
        Harness h = new Harness("ended-verification");
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger answers = new AtomicInteger();
        Requester requester = h.requester(action -> new Connection(opened, release));
        requester.request("me", new JSONObject(), null, data -> answers.incrementAndGet(), false, null);
        try {
            assertTrue(opened.await(5, TimeUnit.SECONDS));
            SessionWork.end(h.session.token);
        } finally { release.countDown(); }
        await(() -> !requester.isBusy());
        assertEquals(0, answers.get());
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Callback did not complete", condition.getAsBoolean());
    }

    private static final class Harness {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final Session session = new Session();
        final AtomicInteger expired = new AtomicInteger(), unusable = new AtomicInteger();
        Harness(String token) { session.token = token; session.employeeId = 7; }
        Requester requester(Requester.ConnectionFactory factory) {
            return new Requester(activity, session, new Requester.Listener() {
                public void sessionExpired() { expired.incrementAndGet(); }
                public void unusableAnswer() { unusable.incrementAndGet(); }
            }, factory);
        }
    }

    private static final class Connection extends HttpsURLConnection {
        final CountDownLatch opened, release;
        final String payload;
        Connection(CountDownLatch opened, CountDownLatch release) throws java.net.MalformedURLException {
            this(opened, release, "{\"success\":true}");
        }
        Connection(CountDownLatch opened, CountDownLatch release, String payload) throws java.net.MalformedURLException {
            super(new URL("https://example.invalid/"));
            this.opened = opened; this.release = release; this.payload = payload;
        }
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        public int getResponseCode() throws IOException {
            opened.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            return 200;
        }
        public InputStream getInputStream() {
            return new ByteArrayInputStream(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        public void disconnect() { }
        public boolean usingProxy() { return false; }
        public void connect() { }
        public String getCipherSuite() { return "test"; }
        public Certificate[] getLocalCertificates() { return null; }
        public Certificate[] getServerCertificates() { return new Certificate[0]; }
    }
}
