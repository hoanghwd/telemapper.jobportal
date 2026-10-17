package com.huynhdous.employeefield.dispatch;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.session.Session;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ActiveJobControlTest {
    final List<Object[]> requests=new ArrayList<>();int changes;
    AppHost host;
    ActiveJobControl control() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        Session session=new Session();session.token="fixture";session.employeeId=7;
        host=(AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)->{
            if(m.getName().equals("activity"))return activity;
            if(m.getName().equals("request"))requests.add(args);
            return null;
        });
        return new ActiveJobControl(host,session,()->changes++);
    }
    JSONObject job(String stage)throws Exception{return new JSONObject().put("id","1").put("title","Repair sink").put("body","Fixture address").put("status",stage).put("status_label",stage);}
    void reply(int i,JSONObject response)throws Exception {((AppHost.Result)requests.get(i)[3]).accept(response);}
    JSONObject status(String stage)throws Exception {return new JSONObject().put("dispatch_enabled",true).put("active_job",job(stage));}
    /** The icon button whose accessibility label is the given text (the job actions are round icon buttons with captions). */
    View button(ActiveJobControl control,String text){return find(control.view,text);}
    private View find(View parent,String text){
        if(text.contentEquals(parent.getContentDescription()==null?"":parent.getContentDescription()))return parent;
        if(parent instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)parent).getChildCount();i++){View hit=find(((android.view.ViewGroup)parent).getChildAt(i),text);if(hit!=null)return hit;}
        return null;
    }
    @Test public void restoresAcceptedJobAndSendsOnlyNextStage()throws Exception {
        ActiveJobControl control=control();reply(0,status("accepted"));assertEquals(View.VISIBLE,control.view.getVisibility());
        button(control,"On the way").performClick();
        assertEquals("dispatch/progress",requests.get(1)[0]);JSONObject sent=(JSONObject)requests.get(1)[1];
        assertEquals("on_the_way",sent.getString("stage"));assertEquals("1",sent.getString("offer_id"));assertEquals("fixture",sent.getString("token"));
        reply(1,new JSONObject().put("job",job("on_the_way")));assertNotNull(button(control,"Arrived"));assertEquals(1,changes);
    }
    @Test public void completionRequiresConfirmationAndRefreshCanClearJob()throws Exception {
        ActiveJobControl control=control();reply(0,status("started"));button(control,"Complete").performClick();assertEquals(1,requests.size());
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("completed",((JSONObject)requests.get(1)[1]).getString("stage"));
        reply(1,new JSONObject().put("job",job("completed")));assertEquals(1,changes);
        control.refresh();reply(2,new JSONObject().put("dispatch_enabled",true).put("active_job",JSONObject.NULL));assertEquals(View.GONE,control.view.getVisibility());
    }
    @Test public void staleReadCannotReplaceFreshStage()throws Exception {
        ActiveJobControl control=control();control.refresh();reply(1,status("arrived"));reply(0,status("accepted"));assertNotNull(button(control,"Start"));
    }
    @Test public void conflictReloadsServerJob()throws Exception {
        ActiveJobControl control=control();reply(0,status("accepted"));button(control,"On the way").performClick();
        ((AppHost.ProblemHandler)requests.get(1)[5]).handle(409,"Cancelled");assertEquals("dispatch/status",requests.get(2)[0]);
        reply(2,new JSONObject().put("dispatch_enabled",true));assertEquals(View.GONE,control.view.getVisibility());
    }
    // ---- several orders (an extra order he accepted) and the suggested route
    JSONObject order(String id,String title,String stage)throws Exception{return new JSONObject().put("id",id).put("title",title).put("body","Body of "+title).put("status",stage).put("status_label",stage);}
    JSONObject stop(String type,String offerId,String title,String address,double miles)throws Exception{return new JSONObject().put("type",type).put("offer_id",offerId).put("title",title).put("address",address).put("miles",miles);}
    JSONObject threeOrders()throws Exception{
        org.json.JSONArray jobs=new org.json.JSONArray().put(order("1","Order A","accepted")).put(order("2","Order B","accepted")).put(order("3","Order C","on_the_way"));
        org.json.JSONArray stops=new org.json.JSONArray().put(stop("pickup","2","Order B","Pickup B",6.9)).put(stop("pickup","1","Order A","Pickup A",0.03)).put(stop("pickup","3","Order C","Pickup C",0.01))
            .put(stop("dropoff","2","Order B","Drop-off B",3.45)).put(stop("dropoff","3","Order C","Drop-off C",2.76)).put(stop("dropoff","1","Order A","Drop-off A",1.38));
        return new JSONObject().put("dispatch_enabled",true).put("active_job",jobs.getJSONObject(0)).put("active_jobs",jobs).put("route",new JSONObject().put("miles",14.5).put("minutes",42).put("stops",stops));
    }
    void walk(View v,java.util.function.Consumer<View> each){each.accept(v);if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)walk(((android.view.ViewGroup)v).getChildAt(i),each);}
    List<String> texts(ActiveJobControl control){List<String> out=new ArrayList<>();walk(control.view,v->{if(v instanceof android.widget.TextView&&!(v instanceof android.widget.Button))out.add(((android.widget.TextView)v).getText().toString());});return out;}
    List<View> withLabel(ActiveJobControl control,String label){List<View> out=new ArrayList<>();walk(control.view,v->{if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))out.add(v);});return out;}
    @Test public void severalOrdersEachGetTheirOwnControlsAndTheRouteIsListedInOrder()throws Exception {
        ActiveJobControl control=control();reply(0,threeOrders());
        List<String> shown=texts(control);
        assertTrue(shown.contains("Your delivery requests (3)"));assertTrue(shown.contains("Suggested route"));
        assertTrue(shown.contains("Order A")&&shown.contains("Order B")&&shown.contains("Order C"));
        assertEquals(2,withLabel(control,"On the way").size());assertEquals(1,withLabel(control,"Arrived").size());
        assertEquals(3,withLabel(control,"Cancel job").size());assertEquals(1,withLabel(control,"Refresh").size());
        int first=shown.indexOf("Pick up · Order B"),last=shown.indexOf("Deliver · Order A");
        assertTrue(first>=0&&last>first);
        assertTrue(shown.indexOf("Deliver · Order B")<shown.indexOf("Deliver · Order C")&&shown.indexOf("Deliver · Order C")<last);
        assertTrue(shown.contains("About 14.5 mi · 42 min · 6 stops (straight lines, an estimate)"));   // a server answer with no source is a straight-line plan
        assertEquals("by road, no live traffic", ActiveJobControl.routeNote("road"));
        assertEquals("straight lines, an estimate", ActiveJobControl.routeNote("straight"));
        assertEquals("straight lines, an estimate", ActiveJobControl.routeNote(""));
        for(int i=1;i<=6;i++)assertTrue(shown.contains(String.valueOf(i)));
        assertTrue(shown.contains("Drop-off B · 3.45 mi"));
    }
    @Test public void oneOrderStillLooksAsBeforeEvenWhenTheServerSendsTheList()throws Exception {
        ActiveJobControl control=control();
        reply(0,new JSONObject().put("dispatch_enabled",true).put("active_jobs",new org.json.JSONArray().put(order("1","Order A","accepted"))).put("route",JSONObject.NULL));
        List<String> shown=texts(control);
        assertTrue(shown.contains("Current delivery request"));assertFalse(shown.contains("Suggested route"));
        assertEquals(1,withLabel(control,"Refresh").size());assertEquals(1,withLabel(control,"On the way").size());
    }
    @Test public void deliveryDepartureSkipsArrivalAndSendsPickedUpStage()throws Exception {
        ActiveJobControl control=control();reply(0,new JSONObject().put("dispatch_enabled",true).put("active_job",job("on_the_way").put("delivery",true)));
        assertNull(button(control,"At pickup"));assertNull(button(control,"Picked up"));
        button(control,"On Delivery").performClick();
        assertEquals("started",((JSONObject)requests.get(1)[1]).getString("stage"));
        reply(1,new JSONObject().put("job",job("started").put("delivery",true)));
        assertNotNull(button(control,"Mark delivered"));assertNull(button(control,"On Delivery"));
        assertEquals("arrived",ActiveJobControl.nextStage("on_the_way",false));
        assertEquals("started",ActiveJobControl.nextStage("arrived",true));
    }

    @Test public void longActionCaptionFitsNarrowCardsAndLargerText()throws Exception {
        for(int widthDp:new int[]{240,280,320}) {
            ActiveJobControl control=control();
            reply(requests.size()-1,new JSONObject().put("dispatch_enabled",true).put("active_job",job("on_the_way").put("delivery",true)));
            View icon=button(control,"On Delivery");
            android.widget.LinearLayout column=(android.widget.LinearLayout)icon.getParent();
            android.widget.TextView caption=(android.widget.TextView)column.getChildAt(1);
            caption.setTextSize(18); // larger accessibility text must retain every character
            int width=Math.round(widthDp*host.activity().getResources().getDisplayMetrics().density);
            control.view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            control.view.layout(0,0,width,control.view.getMeasuredHeight());
            assertEquals("On Delivery",caption.getText().toString());
            android.text.Layout layout=caption.getLayout();assertNotNull(layout);
            assertEquals(caption.length(),layout.getLineEnd(layout.getLineCount()-1));
            assertTrue("All lines fit in caption height",caption.getHeight()>=layout.getHeight()+caption.getCompoundPaddingTop()+caption.getCompoundPaddingBottom());
            assertTrue("Caption fits its column",caption.getRight()<=column.getWidth());
            assertTrue("Caption is not cut by column",caption.getBottom()<=column.getHeight());
            android.view.ViewGroup row=(android.view.ViewGroup)column.getParent();
            assertTrue("Actions stay within card",row.getWidth()<=width);
            assertNotNull(button(control,"Cancel job"));assertNotNull(button(control,"Refresh"));
        }
    }

    @Test public void aDeliveryNamesItsStagesForTheTrip()throws Exception {
        String[][] steps={{"accepted","To pickup"},{"on_the_way","On Delivery"},{"arrived","On Delivery"},{"started","Mark delivered"}};
        for(String[] step:steps) {
            ActiveJobControl control=control();
            reply(requests.size()-1,new JSONObject().put("dispatch_enabled",true).put("active_job",job(step[0]).put("delivery",true)));
            assertNotNull(step[1],button(control,step[1]));
        }
        ActiveJobControl service=control();reply(requests.size()-1,status("accepted"));
        assertNotNull("a service job keeps the plain wording",button(service,"On the way"));
        assertNull(button(service,"To pickup"));
    }
    @Test public void deliveredOnADeliveryOffersAPhotoOrNone()throws Exception {
        ActiveJobControl control=control();DeliveryPhoto photo=new DeliveryPhoto();photo.attach(host);control.setDeliveryPhoto(photo);
        reply(0,new JSONObject().put("dispatch_enabled",true).put("active_job",job("started").put("delivery",true)));
        assertNull(button(control,"Delivered"));
        button(control,"Mark delivered").performClick();
        com.huynhdous.employeefield.core.ui.Popup popup=(com.huynhdous.employeefield.core.ui.Popup)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertEquals("Take photo",popup.getButton(com.huynhdous.employeefield.core.ui.Popup.BUTTON_POSITIVE).getText().toString());
        assertEquals("No photo",popup.getButton(com.huynhdous.employeefield.core.ui.Popup.BUTTON_NEUTRAL).getText().toString());
        assertEquals(1,requests.size());   // nothing is sent until the driver chooses
        popup.getButton(com.huynhdous.employeefield.core.ui.Popup.BUTTON_NEUTRAL).performClick();
        assertEquals("dispatch/progress",requests.get(1)[0]);
        JSONObject sent=(JSONObject)requests.get(1)[1];assertEquals("completed",sent.getString("stage"));assertEquals("1",sent.getString("offer_id"));
    }
    @Test public void aServiceJobNeverAsksForADeliveryPhoto()throws Exception {
        ActiveJobControl control=control();DeliveryPhoto photo=new DeliveryPhoto();photo.attach(host);control.setDeliveryPhoto(photo);
        reply(0,status("started"));
        button(control,"Complete").performClick();
        assertNull("the plain confirmation, not the photo question",org.robolectric.shadows.ShadowDialog.getLatestDialog() instanceof com.huynhdous.employeefield.core.ui.Popup?org.robolectric.shadows.ShadowDialog.getLatestDialog():null);
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
    }
    @Test public void tappingTheOrderShowsItsDetailsInAPopup()throws Exception {
        ActiveJobControl control=control();
        JSONObject delivery=job("accepted").put("title","TP101").put("body","Pick up: B\nDrop off: A").put("details","Pick up: B\nDrop off: A\nCustomer: Sam");
        reply(0,new JSONObject().put("dispatch_enabled",true).put("active_job",delivery));
        assertTrue(texts(control).contains("Tap the order for details"));
        walk(control.view,v->{if(v instanceof android.widget.TextView&&"TP101".contentEquals(((android.widget.TextView)v).getText()))v.performClick();});
        android.app.Dialog popup=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(popup);
        java.util.List<String> inside=new ArrayList<>();
        walk(popup.getWindow().getDecorView(),v->{if(v instanceof android.widget.TextView)inside.add(((android.widget.TextView)v).getText().toString());});
        assertTrue(inside.contains("Pick up: B\nDrop off: A\nCustomer: Sam"));
    }
    @Test public void advancingOneOrderSendsOnlyThatOrderAndUpdatesOnlyIt()throws Exception {
        ActiveJobControl control=control();reply(0,threeOrders());
        withLabel(control,"On the way").get(1).performClick();   // the second order that can still go on the way: Order B
        assertEquals("dispatch/progress",requests.get(1)[0]);JSONObject sent=(JSONObject)requests.get(1)[1];
        assertEquals("2",sent.getString("offer_id"));assertEquals("on_the_way",sent.getString("stage"));
        reply(1,new JSONObject().put("job",order("2","Order B","on_the_way")));
        assertEquals(1,changes);assertEquals(2,requests.size());   // a stage change needs no re-read; the stops do not change
        assertEquals(1,withLabel(control,"On the way").size());assertEquals(2,withLabel(control,"Arrived").size());
        assertTrue(texts(control).contains("Suggested route"));
    }
    @Test public void cancellingOneOfSeveralKeepsTheOthersAndResyncs()throws Exception {
        ActiveJobControl control=control();reply(0,threeOrders());
        withLabel(control,"Cancel job").get(0).performClick();
        ((com.huynhdous.employeefield.core.ui.Popup)org.robolectric.shadows.ShadowDialog.getLatestDialog()).getButton(com.huynhdous.employeefield.core.ui.Popup.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("dispatch/release",requests.get(1)[0]);assertEquals("1",((JSONObject)requests.get(1)[1]).getString("offer_id"));
        reply(1,new JSONObject().put("offer_id",1).put("status","declined"));
        List<String> shown=texts(control);
        assertTrue(shown.contains("Your delivery requests (2)"));assertFalse(shown.contains("Order A"));assertEquals("dispatch/status",requests.get(2)[0]);
    }
    @Test public void completingOneOfSeveralRemovesItAndResyncs()throws Exception {
        ActiveJobControl control=control();
        reply(0,new JSONObject().put("dispatch_enabled",true).put("active_jobs",new org.json.JSONArray().put(order("1","Order A","started")).put(order("2","Order B","accepted"))));
        withLabel(control,"Complete").get(0).performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        reply(1,new JSONObject().put("job",order("1","Order A","completed")));
        assertEquals("dispatch/status",requests.get(2)[0]);
        List<String> shown=texts(control);
        assertTrue(shown.contains("Current delivery request"));assertTrue(shown.contains("Order B"));assertFalse(shown.contains("Order A"));
    }
}
