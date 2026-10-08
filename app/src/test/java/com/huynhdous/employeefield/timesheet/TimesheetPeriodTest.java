package com.huynhdous.employeefield.timesheet;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class TimesheetPeriodTest {
    private final List<Object[]> requests = new ArrayList<>();
    private TimesheetTab tab() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        AppHost host = (AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class}, (p,m,args) -> {
            if (m.getName().equals("activity")) return a;
            if (m.getName().equals("token")) return "fixture";
            if (m.getName().equals("request")) requests.add(args);
            return null;
        });
        TimesheetTab tab = new TimesheetTab();tab.attach(host);tab.buildContent(new LinearLayout(a));return tab;
    }
    private JSONObject sheet(String start, int minutes, String status) throws Exception {
        return new JSONObject().put("week_start",start).put("week_end",java.time.LocalDate.parse(start).plusDays(6).toString())
                .put("total_minutes",minutes).put("approved_minutes",minutes).put("pending_minutes",0)
                .put("submission_status",status).put("days",new JSONArray());
    }
    private void reply(int i, JSONObject data) throws Exception { ((AppHost.Result)requests.get(i)[3]).accept(data); }
    private void select(TimesheetTab tab,int n) throws Exception { Method m=TimesheetTab.class.getDeclaredMethod("selectPeriod",int.class);m.setAccessible(true);m.invoke(tab,n); }
    private String text(TimesheetTab tab,String name) throws Exception { Field f=TimesheetTab.class.getDeclaredField(name);f.setAccessible(true);return ((TextView)f.get(tab)).getText().toString(); }
    @Test public void lastThreeWeeksExcludeCurrentAndCrossYearBoundary() throws Exception {
        TimesheetTab tab=tab();select(tab,3);reply(0,sheet("2026-01-05",999,"accepted"));
        assertEquals("2025-12-15",((JSONObject)requests.get(1)[1]).getString("week_start"));
        reply(1,sheet("2025-12-15",60,"accepted"));
        assertEquals("2025-12-22",((JSONObject)requests.get(2)[1]).getString("week_start"));
        reply(2,sheet("2025-12-22",120,"pending"));reply(3,sheet("2025-12-29",180,"accepted"));
        assertEquals("6h 00m",text(tab,"totalText"));assertTrue(text(tab,"message").contains("Jan 4, 2026"));
    }
    @Test public void oldReplyCannotReplaceNewSelection() throws Exception {
        TimesheetTab tab=tab();tab.onShown();select(tab,1);reply(0,sheet("2026-10-05",999,"accepted"));
        assertEquals(2,requests.size());reply(1,sheet("2026-10-05",999,"accepted"));reply(2,sheet("2026-09-28",60,"accepted"));
        assertEquals("1h 00m",text(tab,"totalText"));
    }
    @Test public void failedWeekDoesNotShowPartialTotal() throws Exception {
        TimesheetTab tab=tab();select(tab,2);reply(0,sheet("2026-10-05",999,"accepted"));reply(1,sheet("2026-09-21",60,"accepted"));
        ((AppHost.ProblemHandler)requests.get(2)[5]).handle(500,"unavailable");
        assertEquals("—",text(tab,"totalText"));assertTrue(text(tab,"message").contains("Refresh"));
    }
    @Test public void acceptedRequiresEveryWeekAndDaysAreKept() throws Exception {
        JSONObject a=sheet("2026-09-21",60,"accepted"), b=sheet("2026-09-28",120,"pending");
        a.getJSONArray("days").put(new JSONObject().put("date","2026-09-21"));b.getJSONArray("days").put(new JSONObject().put("date","2026-09-28"));
        JSONObject combined=TimesheetPeriod.combine(Arrays.asList(a,b));assertEquals(180,combined.getInt("total_minutes"));
        assertEquals("pending",combined.getString("submission_status"));assertEquals(2,combined.getJSONArray("days").length());
        assertTrue(combined.getJSONArray("days").getJSONObject(0).getBoolean("locked"));
        assertFalse(combined.getJSONArray("days").getJSONObject(1).getBoolean("locked"));
    }
}
