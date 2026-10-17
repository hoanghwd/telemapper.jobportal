package com.huynhdous.employeefield.jobs;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** The Jobs screen reads the list when it opens (and on the refresh button) and never by itself; it shows an order offered to the driver. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class JobsTabTest {
    final List<Object[]> requests=new ArrayList<>();
    Activity activity;
    LinearLayout content;
    JobsTab tab;

    void open() {
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        AppHost host=(AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)->{
            switch(m.getName()) {
                case "activity": return activity;
                case "token": return "fixture";
                case "request": requests.add(args);return null;
                default: return null;
            }
        });
        tab=new JobsTab();tab.attach(host);
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        activity.setContentView(content);
        tab.buildContent(content);
    }
    void reply(int i,JSONObject response)throws Exception{((AppHost.Result)requests.get(i)[3]).accept(response);}
    JSONObject job(long id,boolean offered)throws Exception {
        return new JSONObject().put("request_id",id).put("title","Bakery delivery sweethouse-TP"+id).put("pickup_address","Pickup "+id).put("dropoff_address","Drop-off "+id)
            .put("pickup_lat",33.7).put("pickup_lon",-117.9).put("dropoff_lat",33.8).put("dropoff_lon",-117.8).put("miles_to_pickup",1.0).put("minutes_to_pickup",3)
            .put("trip_miles",5.0).put("trip_minutes",9).put("extra_miles",0).put("extra_minutes",0).put("pay","8.38").put("offered",offered).put("offer_id",offered?500+id:JSONObject.NULL).put("can_take",!offered);
    }
    JSONObject answer(JSONObject... jobs)throws Exception {
        JSONArray list=new JSONArray();for(JSONObject j:jobs)list.put(j);
        return new JSONObject().put("dispatch_enabled",true).put("self_select",true).put("can_take",true).put("holding",0).put("max_orders",3).put("position",new JSONArray().put(33.7).put(-117.96))
            .put("orders",new JSONArray()).put("jobs",list).put("message","");
    }
    List<String> texts() {List<String> out=new ArrayList<>();walk(content,out);return out;}
    private void walk(View v,List<String> out){
        if(v instanceof TextView)out.add(((TextView)v).getText().toString());
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),out);
    }

    @Test public void showingTheScreenReadsTheListOnceAndNothingReloadsItByItself()throws Exception {
        open();tab.onShown();
        assertEquals(1,requests.size());assertEquals("dispatch/available",requests.get(0)[0]);
        reply(0,answer(job(1,false)));
        ShadowLooper.idleMainLooper(10,TimeUnit.MINUTES);
        assertEquals("no timer: the list stays as it was until he refreshes",1,requests.size());
    }
    @Test public void detachingIsSafeAfterwards()throws Exception {
        open();tab.onShown();reply(0,answer());
        tab.onDetach();
        ShadowLooper.idleMainLooper(10,TimeUnit.MINUTES);
        assertEquals(1,requests.size());
    }
    @Test public void anOrderOfferedToTheDriverIsOnTheScreenAndCanBeAcceptedFromIt()throws Exception {
        open();tab.onShown();
        reply(0,answer(job(7,true),job(8,false)));
        List<String> shown=texts();
        assertFalse("no 'offered to you' wording",shown.toString().toLowerCase().contains("offered"));
        assertEquals("both the waiting job and the offered one have an Accept button",2,java.util.Collections.frequency(shown,"Accept"));
        assertEquals("2 jobs you can take. Tap the pin, or Accept below.",shown.get(0));
    }
    @Test public void whenOnlyAnOfferedOrderIsThereItIsStillAJobHeCanAccept()throws Exception {
        open();tab.onShown();
        reply(0,answer(job(7,true)));
        String status=texts().get(0);
        assertEquals("1 job you can take. Tap the pin, or Accept below.",status);
        assertEquals("the offered order is accepted from here",1,java.util.Collections.frequency(texts(),"Accept"));
    }
}
