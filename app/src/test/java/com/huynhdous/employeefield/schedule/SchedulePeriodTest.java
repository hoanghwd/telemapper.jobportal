package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.ScheduleGate;
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
public class SchedulePeriodTest {
    private final List<Object[]> requests = new ArrayList<>();
    private final ScheduleGate gate = new ScheduleGate();
    private ScheduleTab tab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppHost host = (AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)-> {
            if (m.getName().equals("activity")) return activity;
            if (m.getName().equals("token")) return "fixture";
            if (m.getName().equals("gate")) return gate;
            if (m.getName().equals("request")) requests.add(args);
            return null;
        });
        ScheduleTab tab = new ScheduleTab();tab.attach(host);tab.buildContent(new LinearLayout(activity));return tab;
    }
    private JSONObject schedule(String start, boolean confirmed) throws Exception {
        return new JSONObject().put("week_start",start).put("week_end",java.time.LocalDate.parse(start).plusDays(5).toString())
            .put("employee",new JSONObject().put("timezone","America/Los_Angeles"))
            .put("confirmed_utc",confirmed ? "2025-12-01 12:00:00" : JSONObject.NULL)
            .put("assignments",new JSONArray().put(new JSONObject().put("work_date",start).put("start_time","08:00:00").put("end_time","16:00:00").put("location_name","Fixture")));
    }
    private JSONObject sheet(String start, int minutes) throws Exception { return new JSONObject().put("week_start",start).put("total_minutes",minutes).put("submission_status", "accepted"); }
    private void reply(int i, JSONObject value) throws Exception { ((AppHost.Result)requests.get(i)[3]).accept(value); }
    private void select(ScheduleTab tab,int n) throws Exception { Method m=ScheduleTab.class.getDeclaredMethod("selectPeriod",int.class);m.setAccessible(true);m.invoke(tab,n); }
    private Object field(ScheduleTab tab,String name) throws Exception { Field f=ScheduleTab.class.getDeclaredField(name);f.setAccessible(true);return f.get(tab); }
    @Test public void previousWeeksUseSelectedTotalsWithoutChangingCurrentGate() throws Exception {
        ScheduleTab tab=tab();select(tab,2);reply(0,schedule("2026-01-05",false));
        assertEquals("2025-12-22",((JSONObject)requests.get(1)[1]).getString("week_start"));
        reply(1,schedule("2025-12-22",true));reply(2,sheet("2025-12-22",60));
        assertEquals("2025-12-29",((JSONObject)requests.get(3)[1]).getString("week_start"));
        reply(3,schedule("2025-12-29",true));reply(4,sheet("2025-12-29",120));
        assertEquals(View.VISIBLE, ((View)field(tab,"accountingNotice")).getVisibility());
        assertEquals(2, ((LinearLayout)field(tab,"accountingNotice")).getChildCount());
        assertEquals("3h 00m worked",((TextView)field(tab,"periodHours")).getText().toString());
        assertEquals(View.GONE,((View)field(tab,"confirmButton")).getVisibility());
        // Current week's confirmation state must survive reading confirmed historical weeks.
        assertTrue(gate.isLocked()); assertEquals("2026-01-05", gate.week());
    }
    @Test public void combinedSixDayWeeksDoNotAddExcludedSundays() throws Exception {
        JSONObject data=SchedulePeriod.combine(Arrays.asList(schedule("2025-12-22",true),schedule("2025-12-29",true)));
        assertEquals(12,data.getJSONArray("period_days").length());assertEquals(2,data.getJSONArray("assignments").length());
        assertEquals("2025-12-29",data.getJSONArray("period_days").getString(6));
    }
    @Test public void failureLeavesNoMisleadingPartialPeriod() throws Exception {
        ScheduleTab tab=tab();select(tab,2);reply(0,schedule("2026-01-05",true));
        ((AppHost.ProblemHandler)requests.get(1)[5]).handle(500,"unavailable");
        assertEquals(View.GONE,((View)field(tab,"periodSummary")).getVisibility());
        assertTrue(((TextView)field(tab,"loadStatus")).getText().toString().contains("Refresh"));
    }
    @Test public void lateReplyDoesNotOverrideNewPeriod() throws Exception {
        ScheduleTab tab=tab();select(tab,1);select(tab,3);reply(0,schedule("2026-01-05",false));
        assertEquals(2,requests.size());reply(1,schedule("2026-01-05",true));
        assertEquals("2025-12-15",((JSONObject)requests.get(2)[1]).getString("week_start"));
    }
}
