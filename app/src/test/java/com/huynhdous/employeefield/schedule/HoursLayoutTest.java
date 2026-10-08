package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.ScheduleGate;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.core.ui.TimeCardUi;
import com.huynhdous.employeefield.timesheet.TimesheetTab;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.ZoneId;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HoursLayoutTest {
    private JSONArray events() throws Exception {
        String[] kinds={"start_work","start_lunch","end_lunch","start_break","end_break","end_work"};
        String[] times={"18:01:00","19:09:00","19:58:00","21:09:00","21:24:00","01:19:00"};
        JSONArray events=new JSONArray();for(int i=0;i<kinds.length;i++)events.put(new JSONObject().put("event_type",kinds[i]).put("event_utc",(i==5?"2026-10-06 ":"2026-10-05 ")+times[i]));return events;
    }
    private AppHost host(Activity activity) {
        ScheduleGate gate=new ScheduleGate();
        return (AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,a)->{
            switch(m.getName()) {
                case "activity":return activity;
                case "token":return "fixture";
                case "gate":return gate;
                case "request":if(a[0].equals("timeclock/day"))((AppHost.Result)a[3]).accept(new JSONObject().put("events",events()));return null;
                default:return null;
            }
        });
    }
    private LinearLayout page(Activity activity,String title) {
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,24,20,24);root.setBackgroundColor(Theme.BACKGROUND);
        TextView heading=TimeCardUi.text(activity,title,24,Theme.TEXT_PRIMARY,true);heading.setPadding(0,0,0,18);root.addView(heading);return root;
    }
    private void capture(LinearLayout root,String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,400,root.getMeasuredHeight());
        checkBounds(root);
        Bitmap bitmap=Bitmap.createBitmap(400,root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        File output=new File("build/outputs/ui-preview/"+name+".png");output.getParentFile().mkdirs();try(FileOutputStream stream=new FileOutputStream(output)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
    }
    private void checkBounds(View view) {
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View child=((ViewGroup)view).getChildAt(i);if(child.getVisibility()==View.GONE)continue;
            assertTrue("Overflow: "+child.getClass().getSimpleName(),child.getRight()<=view.getWidth());checkBounds(child);
        }
    }
    @Test public void scheduleTimelineFitsPhoneWidthAndKeepsAllEntries() throws Exception {
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();AppHost host=host(a);LinearLayout root=page(a,"Schedule");
        ScheduleTab tab=new ScheduleTab();tab.attach(host);tab.buildContent(root);
        JSONObject data=new JSONObject().put("week_start","2026-10-05").put("week_end","2026-10-05").put("employee",new JSONObject().put("timezone","America/Los_Angeles")).put("confirmed_utc","2026-10-04 12:00:00").put("assignments",new JSONArray().put(new JSONObject().put("work_date","2026-10-05").put("start_time","07:04:00").put("end_time","17:04:00").put("location_name","Garfield · Huntington Beach")));
        Method render=ScheduleTab.class.getDeclaredMethod("render",JSONObject.class);render.setAccessible(true);render.invoke(tab,data);
        Field field=ScheduleTab.class.getDeclaredField("hoursCard");field.setAccessible(true);HoursCard hours=(HoursCard)field.get(tab);hours.setScheduled(660,4161);
        Field week=HoursCard.class.getDeclaredField("weekWorkedMinutes");week.setAccessible(true);week.setInt(hours,1327);hours.setScheduled(660,4161);hours.renderDayTotal(events(),ZoneId.of("America/Los_Angeles"));
        assertEquals("ended",DayClock.finalDayState(events()));capture(root,"schedule");
    }
    @Test public void weeklySummaryFitsPhoneWidthAndShowsAttentionStates() throws Exception {
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();TimesheetTab tab=new TimesheetTab();tab.attach(host(a));LinearLayout root=page(a,"Time Sheet");tab.buildContent(root);
        JSONArray days=new JSONArray();for(int i=0;i<5;i++)days.put(new JSONObject().put("date","2026-10-0"+(i+5)).put("incomplete",i==2).put("in_progress",i==3).put("has_dispute",i==2).put("dispute_note",i==2?"Clock-out needs a review":"").put("net_minutes",i<2?373:JSONObject.NULL));
        JSONObject data=new JSONObject().put("week_start","2026-10-05").put("week_end","2026-10-11").put("total_minutes",1327).put("approved_minutes",746).put("pending_minutes",581).put("submission_status","pending").put("days",days);
        Method render=TimesheetTab.class.getDeclaredMethod("render",JSONObject.class);render.setAccessible(true);render.invoke(tab,data);capture(root,"timesheet");
    }
}
