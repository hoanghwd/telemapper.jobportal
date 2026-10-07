package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DayClockReasonTest {
    @Test public void reasonIsRequiredBeforeSavingAndBackKeepsTimeEditor() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicReference<JSONObject> submitted = new AtomicReference<>();
        AppHost host = (AppHost) Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("activity")) return activity;
                    if (method.getName().equals("token")) return "test-token";
                    if (method.getName().equals("request")) {
                        assertEquals("timeclock/correct", args[0]);
                        submitted.set((JSONObject) args[1]);
                    }
                    return null;
                });
        DayClock clock = new DayClock(host, new LinearLayout(activity), "2026-10-07", true, null,
                ZoneId.of("UTC"), true, null);
        Popup editor = new Popup.Builder(activity).setTitle("Edit today's times").setPositiveButton("Continue", null).show();
        LocalTime[][] times = new LocalTime[6][];
        times[0] = new LocalTime[]{LocalTime.of(7, 18)};
        int[] ids = new int[]{42, 0, 0, 0, 0, 0};
        Method prompt = DayClock.class.getDeclaredMethod("showTimeEditReasonDialog", Popup.class,
                LocalTime[][].class, int[].class, java.util.List.class);
        prompt.setAccessible(true);
        prompt.invoke(clock, editor, times, ids, Collections.singletonList(0));
        Popup reasonDialog = (Popup) ShadowDialog.getLatestDialog();
        EditText reason = findReason(reasonDialog.getWindow().getDecorView());
        assertNotNull(reason);
        assertFalse(reasonDialog.getButton(Popup.BUTTON_POSITIVE).isEnabled());
        reason.setText("   \n ");
        assertFalse(reasonDialog.getButton(Popup.BUTTON_POSITIVE).isEnabled());
        assertNull(submitted.get());
        reasonDialog.getButton(Popup.BUTTON_NEGATIVE).performClick();
        assertTrue(editor.isShowing());
        assertNull(submitted.get());

        prompt.invoke(clock, editor, times, ids, Collections.singletonList(0));
        reasonDialog = (Popup) ShadowDialog.getLatestDialog();
        reason = findReason(reasonDialog.getWindow().getDecorView());
        reason.setText("  Missed my clock-in  ");
        assertTrue(reasonDialog.getButton(Popup.BUTTON_POSITIVE).isEnabled());
        reasonDialog.getButton(Popup.BUTTON_POSITIVE).performClick();
        assertFalse(editor.isShowing());
        assertFalse(reasonDialog.isShowing());
        assertEquals("Missed my clock-in", submitted.get().getString("reason"));
        assertEquals("07:18", submitted.get().getString("corrected_time"));
        assertEquals(42, submitted.get().getInt("original_event_id"));
    }

    private static EditText findReason(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findReason(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
