package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.lang.reflect.Proxy;
import java.time.ZoneId;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DayClockLockedTest {
    private LinearLayout render(boolean locked, boolean today) {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        AppHost host=(AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)-> {
            if(m.getName().equals("activity")) return activity;
            if(m.getName().equals("token")) return "fixture";
            if(m.getName().equals("request")) ((AppHost.Result)args[3]).accept(new JSONObject().put("events",new JSONArray()).put("locked",locked));
            return null;
        });
        LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);
        new DayClock(host,column,"2026-09-21",true,null,ZoneId.of("UTC"),today,null).load();return column;
    }
    private String texts(View v) {
        StringBuilder text=new StringBuilder();if(v instanceof TextView)text.append(((TextView)v).getText()).append('\n');
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)text.append(texts(((ViewGroup)v).getChildAt(i)));
        return text.toString();
    }
    private void assertNoActions(View view) {
        assertFalse("Accepted time record must have no edit or clock actions",view.isClickable());
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertNoActions(((ViewGroup)view).getChildAt(i));
    }
    @Test public void acceptedPastDayIsReferenceOnly() {
        LinearLayout view=render(true,false);String labels=texts(view);
        assertFalse(labels.contains("Locked"));assertFalse(labels.contains("For reference only"));assertNoActions(view);
    }
    @Test public void acceptedTodayHasNoClockPunchButtons() { LinearLayout view=render(true,true);assertNoActions(view);assertFalse(texts(view).contains("Start work")); }
    @Test public void unacceptedDayKeepsEditAndReportActions() { String labels=texts(render(false,false));assertTrue(labels.contains("Edit time entries"));assertTrue(labels.contains("Report an issue"));assertFalse(labels.contains("Locked")); }
}
