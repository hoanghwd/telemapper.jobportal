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
}
