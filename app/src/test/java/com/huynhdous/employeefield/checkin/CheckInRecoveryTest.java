package com.huynhdous.employeefield.checkin;

import android.app.Activity;
import android.os.Bundle;
import com.huynhdous.employeefield.core.session.SessionWork;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
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
        AppHost host = (AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class},
                (p, method, args) -> {
                    switch(method.getName()) {
                        case "activity": return activity;
                        case "token": return "checkin-retry-test";
                        case "employeeId": return 5L;
                        case "isCurrent": return true;
                        default: return null;
                    }
                });
        CheckInTab tab = new CheckInTab(); tab.attach(host);
        JSONObject assignment = new JSONObject().put("assignment_id", 123);
        set(tab, "activeAssignment", assignment);
        set(tab, "activeLat", 34.25);
        SessionWork.Lease work = SessionWork.begin("checkin-retry-test", 5);
        work.close();
        tab.finishSubmission(work, "Connection timed out");
        assertSame(assignment, get(tab, "activeAssignment"));
        assertEquals(34.25, (Double)get(tab, "activeLat"), 0.0);
        tab.finishSubmission(work, null);
        assertNull(get(tab, "activeAssignment"));
    }
}
