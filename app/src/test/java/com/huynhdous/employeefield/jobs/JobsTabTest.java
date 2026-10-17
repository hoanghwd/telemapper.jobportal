package com.huynhdous.employeefield.jobs;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.dispatch.DispatchEvents;
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

/** The Jobs screen reads itself again while it is showing, on the offer sound, and shows an order offered to the driver. */
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
            .put("trip_miles",5.0).put("trip_minutes",9).put("extra_miles",0).put("extra_minutes",0).put("pay","8.38").put("offered",offered).put("can_take",!offered);
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
    void wait15() {ShadowLooper.idleMainLooper(JobsTab.AUTO_REFRESH_MS,TimeUnit.MILLISECONDS);}

    @Test public void showingTheScreenReadsTheListAndThenKeepsReadingIt()throws Exception {
        open();tab.onShown();
        assertEquals(1,requests.size());assertEquals("dispatch/available",requests.get(0)[0]);
        reply(0,answer(job(1,false)));
        assertEquals(1,tab.mapLoads);
        wait15();
        assertEquals("it reads again by itself after 15 seconds",2,requests.size());
        wait15();
        assertEquals(3,requests.size());
    }
    @Test public void aReadWithNothingNewLeavesTheMapAloneButAChangeRedrawsIt()throws Exception {
        open();tab.onShown();reply(0,answer(job(1,false)));
        wait15();reply(1,answer(job(1,false)));
        assertEquals("same jobs: the map is not redrawn (so his pan and zoom stay)",1,tab.mapLoads);
        wait15();reply(2,answer(job(1,false),job(2,false)));
        assertEquals("a new job: redrawn",2,tab.mapLoads);
        wait15();reply(3,answer(job(1,false),job(2,true)));
        assertEquals("a job turning into 'offered to you': redrawn",3,tab.mapLoads);
    }
    @Test public void itStopsReadingWhileTheScreenIsNotShown()throws Exception {
        open();tab.onShown();reply(0,answer());
        content.setVisibility(View.GONE);
        wait15();wait15();
        assertEquals("nothing is read while another screen is showing",1,requests.size());
        content.setVisibility(View.VISIBLE);
        wait15();
        assertEquals("and it carries on once it is showing again",2,requests.size());
    }
    @Test public void theOfferSoundMakesTheMapReadAtOnce()throws Exception {
        open();tab.onShown();reply(0,answer(job(1,false)));
        DispatchEvents.offerArrived();
        ShadowLooper.idleMainLooper();
        assertEquals("a new offer arrived: read now, not at the next timer",2,requests.size());
        reply(1,answer(job(1,false)));
        assertEquals("and the map is drawn even though the jobs are the same",2,tab.mapLoads);
        content.setVisibility(View.GONE);
        DispatchEvents.offerArrived();ShadowLooper.idleMainLooper();
        assertEquals("not while the screen is hidden",2,requests.size());
    }
    @Test public void detachingStopsEverything()throws Exception {
        open();tab.onShown();reply(0,answer());
        tab.onDetach();
        wait15();DispatchEvents.offerArrived();ShadowLooper.idleMainLooper();
        assertEquals(1,requests.size());
    }
    @Test public void anOrderOfferedToTheDriverIsOnTheScreenMarkedOfferedAndCannotBeTakenFromIt()throws Exception {
        open();tab.onShown();
        reply(0,answer(job(7,true),job(8,false)));
        List<String> shown=texts();
        assertTrue(shown.contains("Offered to you"));
        assertTrue(shown.contains("Open Notifications (the bell) to accept or decline."));
        assertEquals("only the job he can take has a Take this job button",1,java.util.Collections.frequency(shown,"Take this job"));
        String status=shown.get(0);
        assertTrue(status,status.contains("1 job you can take")&&status.contains("1 job is offered to you"));
    }
    @Test public void whenOnlyAnOfferedOrderIsThereTheLineSaysSo()throws Exception {
        open();tab.onShown();
        reply(0,answer(job(7,true)));
        String status=texts().get(0);
        assertEquals("1 job is offered to you: open Notifications (the bell) to accept or decline.",status);
        assertEquals(0,java.util.Collections.frequency(texts(),"Take this job"));
    }
}
