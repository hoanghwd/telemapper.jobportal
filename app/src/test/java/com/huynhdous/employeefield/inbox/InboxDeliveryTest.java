package com.huynhdous.employeefield.inbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class InboxDeliveryTest {
    @Test public void notificationListPreviewsDeliveryAndStillOpensFullOffer() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_83",0).edit().clear().commit();
        InboxActions ui=new InboxActions(activity,83);ui.setWorkOffersEnabled(true);ui.attach(new LinearLayout(activity));
        ui.setAnswer((id,decision,done)->{});ui.setPendingOffer("offer:301");
        String body="Pick up: Bakery address\nDrop off: Customer address\nTotal distance: 1.5 mi\nETA: about 6 min\nOffer Amount: $4.46";
        ui.receiveWorkOffer("offer:301","TP202610170085",body);
        android.app.Notification ringing=shadowOf(activity.getSystemService(NotificationManager.class)).getAllNotifications().get(0);
        String expected="Drop-off: Customer address\nDistance: 1.5 mi\nOffer: $4.46";
        assertEquals(expected,ringing.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString());
        assertNotNull(ringing.headsUpContentView);assertNotNull(ringing.bigContentView);
        android.view.View banner=ringing.headsUpContentView.apply(activity,new LinearLayout(activity));
        assertEquals("TP202610170085",((android.widget.TextView)banner.findViewById(com.huynhdous.employeefield.R.id.notification_offer_title)).getText().toString());
        assertEquals(expected,((android.widget.TextView)banner.findViewById(com.huynhdous.employeefield.R.id.notification_offer_summary)).getText().toString());
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);show.invoke(ui,false);
        AlertDialog list=ShadowAlertDialog.getLatestAlertDialog();
        CharSequence label=(CharSequence)list.getListView().getAdapter().getItem(0);
        assertEquals("● TP202610170085\nDrop-off: Customer address\nDistance: 1.5 mi\nOffer: $4.46",label.toString());
        android.widget.TextView row=(android.widget.TextView)list.getListView().getAdapter().getView(0,null,list.getListView());
        assertEquals(label.toString(),row.getText().toString());assertTrue(row.getMaxLines()>=4);
        assertEquals(14f,row.getTextSize()/activity.getResources().getDisplayMetrics().scaledDensity,0.01f);
        list.getListView().performItemClick(null,0,0);
        AlertDialog detail=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(body,shadowOf(detail).getMessage().toString());
        assertEquals("Accept",detail.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        ui.close();
    }

    @Test public void ineligibleEmployeeCannotReceiveLocalOrServerOffers() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_900",0).edit().clear().commit();
        InboxActions ui=new InboxActions(activity,900);
        LinearLayout header=new LinearLayout(activity);ui.attach(header);
        ui.receiveWorkOffer("offer:900","Private work","Other company");
        ui.receiveWorkOffer("test-900","Test work offer","Local test");
        assertFalse(activity.getSharedPreferences("work_inbox_900",0).contains("items"));
        ui.setWorkOffersEnabled(true);
        ui.receiveWorkOffer("offer:900","Work","Own company");
        ui.setWorkOffersEnabled(false);
        assertEquals("Notifications, 0 unread",((android.widget.FrameLayout)header.getChildAt(1)).getChildAt(0).getContentDescription());
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);
        show.setAccessible(true);show.invoke(ui,false);
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).getVisibility()==android.view.View.VISIBLE);
        ui.close();
    }

    @Test public void backgroundDeliveryUpdatesVisibleBadgesAndIsDeduplicated() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_77",0).edit().clear().commit();
        InboxActions ui=new InboxActions(activity,77);ui.setWorkOffersEnabled(true);
        LinearLayout header=new LinearLayout(activity);ui.attach(header);
        InboxActions background=new InboxActions(activity.getApplicationContext(),77);
        background.setPendingOffer("offer:101");
        background.receiveWorkOffer("offer:101","Repair faucet","Fixture address");
        background.receiveWorkOffer("offer:101","Repair faucet","Duplicate delivery");
        shadowOf(android.os.Looper.getMainLooper()).idle();
        android.widget.FrameLayout button=(android.widget.FrameLayout)header.getChildAt(0);
        assertEquals("Messages, 0 unread",button.getChildAt(0).getContentDescription());
        assertEquals("Notifications, 1 unread",((android.widget.FrameLayout)header.getChildAt(1)).getChildAt(0).getContentDescription());
        assertEquals(1,shadowOf(activity.getSystemService(NotificationManager.class)).getAllNotifications().size());
        ui.close();
    }
    @Test public void openingRealOfferAllowsAcceptanceAndSavesReadState() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_78",0).edit().clear().commit();
        InboxActions ui=new InboxActions(activity,78);ui.setWorkOffersEnabled(true);ui.attach(new LinearLayout(activity));
        final String[] result=new String[2];ui.setAnswer((id,decision,done)->{result[0]=id;result[1]=decision;done.run();});
        ui.setPendingOffer("offer:102"); // the poll says this offer is the open one
        ui.receiveWorkOffer("offer:102","Work offer","Fixture address");
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);show.invoke(ui,false);
        AlertDialog list=ShadowAlertDialog.getLatestAlertDialog();
        list.getListView().performItemClick(null,0,0);
        AlertDialog detail=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Accept",detail.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        detail.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("102",result[0]);assertEquals("accepted",result[1]);
        assertTrue(activity.getSharedPreferences("work_inbox_78",0).contains("answer_offer:102"));
        ui.close();
    }
    @Test public void anOfferThatIsNoLongerOpenCannotBeAnswered() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_81",0).edit().clear().commit();
        InboxActions ui=new InboxActions(activity,81);ui.setWorkOffersEnabled(true);ui.attach(new LinearLayout(activity));
        ui.setAnswer((id,decision,done)->{throw new AssertionError("A closed offer must not be answered");});
        ui.setPendingOffer("offer:104");
        ui.receiveWorkOffer("offer:104","Work offer","Fixture address");
        ui.setPendingOffer("");
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);show.invoke(ui,false);
        AlertDialog list=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(0,list.getListView()==null?0:list.getListView().getCount());
        assertEquals("No open offers right now.",shadowOf(list).getMessage().toString());
        ui.close();
    }
    @Test public void onlyTheOpenOfferIsListedAndCounted() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_82",0).edit().clear().commit();
        InboxActions ui=new InboxActions(activity,82);ui.setWorkOffersEnabled(true);
        LinearLayout header=new LinearLayout(activity);ui.attach(header);
        for(String id:new String[]{"offer:201","offer:202","offer:203"}){ui.setPendingOffer(id);ui.receiveWorkOffer(id,"Order "+id,"Body");}
        ui.setPendingOffer("offer:203"); // the poll says only 203 is still open
        assertEquals("Notifications, 1 unread",((android.widget.FrameLayout)header.getChildAt(1)).getChildAt(0).getContentDescription());
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);show.invoke(ui,false);
        AlertDialog list=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(1,list.getListView().getCount());
        assertTrue(list.getListView().getAdapter().getItem(0).toString().contains("offer:203"));
        ui.setPendingOffer("");
        assertEquals("Notifications, 0 unread",((android.widget.FrameLayout)header.getChildAt(1)).getChildAt(0).getContentDescription());
        ui.close();
    }
    @Test public void employeeStorageDoesNotLeakOffersBetweenAccounts() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_79",0).edit().clear().commit();
        activity.getSharedPreferences("work_inbox_80",0).edit().clear().commit();
        new InboxActions(activity.getApplicationContext(),79).receiveWorkOffer("offer:103","Work","Private details");
        LinearLayout header=new LinearLayout(activity);InboxActions other=new InboxActions(activity,80);other.attach(header);
        android.widget.FrameLayout button=(android.widget.FrameLayout)header.getChildAt(0);
        assertEquals("Messages, 0 unread",button.getChildAt(0).getContentDescription());
        other.close();
    }
    @Test public void businessHeadingMatchesRingingAndRestoredBellPopup() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_184",0).edit().clear().commit();
        InboxActions inbox=new InboxActions(activity,184);inbox.setWorkOffersEnabled(true);inbox.setPendingOffer("offer:600");
        String body="Pick up: Bakery\nDrop off: Customer\nTotal distance: 12 mi\nOffer Amount: $7.57";
        inbox.receiveWorkOffer("offer:600","TP600",body,"","Sweethouse Bakery");
        android.app.Notification notification=shadowOf(activity.getSystemService(NotificationManager.class)).getAllNotifications().get(0);
        assertEquals("Sweethouse Bakery - Order Request",notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString());
        String expected="TP600\nDrop-off: Customer\nDistance: 12 mi\nOffer: $7.57";
        assertEquals(expected,notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString());
        android.view.View banner=notification.headsUpContentView.apply(activity,new LinearLayout(activity));
        assertEquals("Sweethouse Bakery - Order Request",((android.widget.TextView)banner.findViewById(com.huynhdous.employeefield.R.id.notification_offer_title)).getText().toString());
        assertEquals(expected,((android.widget.TextView)banner.findViewById(com.huynhdous.employeefield.R.id.notification_offer_summary)).getText().toString());
        inbox.close();inbox=new InboxActions(activity,184);
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);show.invoke(inbox,false);
        assertEquals("Sweethouse Bakery - Order Request",shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());
        inbox.receiveWorkOffer("offer:600","TP600",body,"","Other Bakery");
        show.invoke(inbox,false);assertEquals("Other Bakery - Order Request",shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());
        assertEquals(1,shadowOf(activity.getSystemService(NotificationManager.class)).getAllNotifications().size());inbox.close();
    }

}
