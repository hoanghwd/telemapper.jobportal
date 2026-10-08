package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.widget.*;
import android.view.View;
import android.view.ViewGroup;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TimecardSaveTest {
    private static class Form {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<JSONObject> writes = new ArrayList<>();
        AppHost.Result success;
        AppHost.ProblemHandler problem;
        Button save;
        Popup edit;
        Popup reasonDialog;
        EditText reason;
        boolean busy;

        Form(boolean busy) throws Exception {
            this.busy = busy;
            AppHost host = (AppHost) Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class}, (p, m, a) -> {
                if (m.getName().equals("activity")) return activity;
                if (m.getName().equals("token")) return "timecard-test-token";
                if (m.getName().equals("request") && a[0].equals("timeclock/correct")) {
                    if (this.busy) return null;
                    writes.add((JSONObject) a[1]);
                    success = (AppHost.Result) a[3];
                    problem = a.length > 4 ? (AppHost.ProblemHandler) a[5] : null;
                    if (a[2] != null) ((Button) a[2]).setEnabled(false);
                }
                return null;
            });
            DayClock clock = new DayClock(host, new LinearLayout(activity), "2026-10-06", false, null, ZoneId.of("UTC"), false, null);
            edit = new Popup.Builder(activity).setTitle("Edit times").setPositiveButton("Continue", null).show();
            LocalTime[][] holders = new LocalTime[6][];
            for (int i=0;i<6;i++) holders[i] = new LocalTime[]{LocalTime.of(i == 1 ? 17 : 9, 15)};
            Method show = DayClock.class.getDeclaredMethod("showTimeEditReasonDialog", Popup.class, LocalTime[][].class, int[].class, List.class);
            show.setAccessible(true);
            show.invoke(clock, edit, holders, new int[]{11, 12, 0, 0, 0, 0}, Arrays.asList(0, 1));
            reasonDialog = (Popup) ShadowDialog.getLatestDialog();
            reason = findInput(reasonDialog.getWindow().getDecorView());
            save = reasonDialog.getButton(Popup.BUTTON_POSITIVE);
            reason.setText("Correcting missed punches");
        }
    }
    private static EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            EditText found = findInput(((ViewGroup)view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    @Test public void changingTwoPunchesUsesOneRequestAndClosesOnlyAfterSuccess() throws Exception {
        Form f = new Form(false); f.save.performClick();
        assertEquals(1, f.writes.size());
        JSONObject body = f.writes.get(0);
        assertEquals(2, body.getJSONArray("corrections").length());
        assertEquals(11, body.getJSONArray("corrections").getJSONObject(0).getInt("original_event_id"));
        assertEquals("17:15", body.getJSONArray("corrections").getJSONObject(1).getString("corrected_time"));
        assertEquals("2026-10-06", body.getString("work_date"));
        assertEquals("Correcting missed punches", body.getString("reason"));
        assertTrue(f.edit.isShowing()); assertTrue(f.reasonDialog.isShowing());
        f.save.performClick(); assertEquals(1, f.writes.size());
        f.save.setEnabled(true); f.success.accept(new JSONObject().put("success", true));
        assertFalse(f.edit.isShowing()); assertFalse(f.reasonDialog.isShowing());
        assertEquals(1, f.writes.size());
    }
    @Test public void failedSaveKeepsTheFormAndAllowsRetryWithoutSplittingTheBatch() throws Exception {
        Form f = new Form(false); f.save.performClick();
        assertNotNull(f.problem);
        f.save.setEnabled(true); assertTrue(f.problem.handle(0, "Unable to connect."));
        assertTrue(f.edit.isShowing()); assertTrue(f.reasonDialog.isShowing());
        assertTrue(f.reason.isEnabled()); assertTrue(f.save.isEnabled());
        assertEquals("Correcting missed punches", f.reason.getText().toString());
        f.save.performClick(); assertEquals(2, f.writes.size());
        assertEquals(f.writes.get(0).toString(), f.writes.get(1).toString());
    }
    @Test public void busyUploadDoesNotTrapTheTimeEditor() throws Exception {
        Form f = new Form(true); f.save.performClick();
        assertTrue(f.reason.isEnabled()); assertTrue(f.save.isEnabled());
        assertTrue(f.reasonDialog.getButton(Popup.BUTTON_NEGATIVE).isEnabled());
        assertEquals(0, f.writes.size());
        f.busy = false; f.save.performClick(); assertEquals(1, f.writes.size());
    }
    @Test public void oversizedReasonCannotBeSubmitted() throws Exception {
        Form f = new Form(false); f.reason.setText(String.join("", Collections.nCopies(501, "a")));
        assertFalse(f.save.isEnabled()); f.save.performClick(); assertEquals(0, f.writes.size());
    }
}
