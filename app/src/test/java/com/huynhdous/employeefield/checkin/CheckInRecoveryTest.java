package com.huynhdous.employeefield.checkin;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.huynhdous.employeefield.core.session.SessionWork;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CheckInRecoveryTest {
    private static void set(CheckInTab tab, String name, Object value) throws Exception {
        Field f = CheckInTab.class.getDeclaredField(name); f.setAccessible(true); f.set(tab, value);
    }
    private static Object get(CheckInTab tab, String name) throws Exception {
        Field f = CheckInTab.class.getDeclaredField(name); f.setAccessible(true); return f.get(tab);
    }

    /** A host for one signed-in employee. {@code current} says whether the screen that started an upload is still the live one. */
    private static AppHost host(Activity activity, String token, boolean current) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "activity": return activity;
                case "token": return token;
                case "employeeId": return 5L;
                case "isCurrent": return current;
                case "leaveOutcome": {   // the same rule as AppHost.leaveOutcome: post for the next screen if the sign-in is unchanged
                    SessionWork.Lease w = (SessionWork.Lease) args[0];
                    if (w.matches(token, 5L)) SessionWork.postOutcome(w.token, (SessionWork.Outcome) args[1]);
                    return null;
                }
                default: return null;
            }
        };
        return (AppHost) Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class}, handler);
    }

    @SuppressWarnings("unchecked")
    private static File addAcceptedPhoto(CheckInTab tab) throws Exception {
        File photo = File.createTempFile("checkin-test", ".jpg");
        Files.write(photo.toPath(), new byte[]{1, 2, 3});
        photo.deleteOnExit();
        ((List<File>) get(tab, "acceptedStorePhotos")).add(photo);
        return photo;
    }

    @Test public void cameraRecreationPreservesVerifiedCoordinates() throws Exception {
        CheckInTab original = new CheckInTab();
        set(original, "activeLat", 34.25); set(original, "activeLon", -118.5);
        Bundle state = new Bundle(); original.saveState(state);
        CheckInTab restored = new CheckInTab(); restored.restoreState(state);
        assertEquals(34.25, (Double)get(restored, "activeLat"), 0.0);
        assertEquals(-118.5, (Double)get(restored, "activeLon"), 0.0);
    }

    @Test public void transientUploadFailureKeepsAssignmentForRetry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "checkin-retry-test", true));
        JSONObject assignment = new JSONObject().put("assignment_id", 123);
        set(tab, "activeAssignment", assignment);
        set(tab, "activeLat", 34.25);
        addAcceptedPhoto(tab);
        SessionWork.Lease work = SessionWork.begin("checkin-retry-test", 5);
        work.close();
        tab.finishSubmission(work, "Connection timed out");
        assertSame(assignment, get(tab, "activeAssignment"));
        assertEquals(34.25, (Double)get(tab, "activeLat"), 0.0);
        assertEquals(Boolean.TRUE, get(tab, "unsent"));
        tab.finishSubmission(work, null);
        assertNull(get(tab, "activeAssignment"));
    }

    @Test public void refusedCheckInStartsOverInsteadOfOfferingAHopelessRetry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "checkin-refused-test", true));
        set(tab, "activeAssignment", new JSONObject().put("assignment_id", 9));
        addAcceptedPhoto(tab);
        SessionWork.Lease work = SessionWork.begin("checkin-refused-test", 5);
        work.close();
        tab.finishSubmission(work, "You are too far from the worksite.", false);
        assertNull(get(tab, "activeAssignment"));
        assertEquals(Boolean.FALSE, get(tab, "unsent"));
    }

    @Test public void waitingCheckInSurvivesRecreation() throws Exception {
        CheckInTab original = new CheckInTab();
        set(original, "activeAssignment", new JSONObject().put("assignment_id", 7).put("site_name", "Costco"));
        File photo = addAcceptedPhoto(original);
        set(original, "unsent", true);
        Bundle state = new Bundle(); original.saveState(state);

        CheckInTab restored = new CheckInTab(); restored.restoreState(state);
        assertEquals(Boolean.TRUE, get(restored, "unsent"));
        assertEquals("Costco", ((JSONObject) get(restored, "activeAssignment")).getString("site_name"));
        assertEquals(1, ((List<?>) get(restored, "acceptedStorePhotos")).size());

        // the photos are gone (cache cleared): nothing is waiting any more
        assertTrue(photo.delete());
        CheckInTab lost = new CheckInTab(); lost.restoreState(state);
        assertEquals(Boolean.FALSE, get(lost, "unsent"));
    }

    @Test public void resultOfAnUploadThatFinishedDuringARotationReachesTheNewScreen() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String token = "checkin-rotation-test";

        // The old screen is gone when the upload ends without a connection: it leaves the result behind.
        CheckInTab old = new CheckInTab(); old.attach(host(activity, token, false));
        SessionWork.Lease work = SessionWork.begin(token, 5);
        work.close();
        old.finishSubmission(work, "Unable to check in. Check your connection and try again.", true);

        // The new screen was restored with the same pending check-in and picks the result up.
        CheckInTab fresh = new CheckInTab(); fresh.attach(host(activity, token, true));
        fresh.buildContent(new LinearLayout(activity));
        set(fresh, "activeAssignment", new JSONObject().put("assignment_id", 3).put("site_name", "Walmart"));
        addAcceptedPhoto(fresh);
        fresh.onUploadOutcome();

        assertEquals(Boolean.TRUE, get(fresh, "unsent"));
        assertNotNull(get(fresh, "activeAssignment"));
        assertTrue(SessionWork.takeOutcomes(token, "checkin").isEmpty());   // taken exactly once
    }
}
