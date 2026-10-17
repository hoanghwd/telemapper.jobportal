package com.huynhdous.employeefield.inbox;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class OfferMapTest {
    @Test public void onlyValidCoordinatesCreateCenteredMap() {
        String html=OfferMap.buildHtml("{\"latitude\":33.8,\"longitude\":-117.8,\"address\":\"</script>\"}");
        assertTrue(html.contains("var center=[33.8,-117.8]"));
        assertTrue(html.contains(".setView(center,14)"));
        assertTrue(html.contains("L.circleMarker(center"));
        assertFalse(html.contains("address"));
        assertNull(OfferMap.buildHtml(""));assertNull(OfferMap.buildHtml("null"));
        assertNull(OfferMap.buildHtml("{\"latitude\":91,\"longitude\":1}"));
        assertNull(OfferMap.buildHtml("{\"latitude\":33.8}"));
    }
    private WebView findMap(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            WebView found=findMap(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    @Test public void cachedOfferCanGainAMapWithoutRingingAgainAndStillBeAccepted() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("work_inbox_180",0).edit().clear().commit();
        InboxActions inbox=new InboxActions(activity,180);inbox.setWorkOffersEnabled(true);
        inbox.setPendingOffer("offer:500");inbox.receiveWorkOffer("offer:500","TP500","Drop off: Customer");
        inbox.receiveWorkOffer("offer:500","TP500","Drop off: Customer","{\"latitude\":33.8,\"longitude\":-117.8}");
        assertEquals(1,shadowOf(activity.getSystemService(NotificationManager.class)).getAllNotifications().size());
        JSONArrayCheck(activity);
        inbox.close();inbox=new InboxActions(activity,180);
        final String[] decision={""};inbox.setAnswer((id,answer,done)->decision[0]=id+":"+answer);
        java.lang.reflect.Method show=InboxActions.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);show.invoke(inbox,false);
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,0,0);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        WebView map=findMap(dialog.getWindow().getDecorView());assertNotNull(map);
        assertEquals(Math.round(180*activity.getResources().getDisplayMetrics().density),map.getLayoutParams().height);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("500:accepted",decision[0]);inbox.close();
    }
    private void JSONArrayCheck(Activity activity) throws Exception {
        JSONObject saved=new org.json.JSONArray(activity.getSharedPreferences("work_inbox_180",0).getString("items","[]")).getJSONObject(0);
        assertTrue(saved.getString("map").contains("33.8"));
    }
}
