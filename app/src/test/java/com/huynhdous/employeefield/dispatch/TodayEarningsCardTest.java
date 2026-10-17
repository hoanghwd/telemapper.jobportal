package com.huynhdous.employeefield.dispatch;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

/** The home screen's "Today's earnings" card: what the server says, shown plainly, and nothing when dispatch is off. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TodayEarningsCardTest {
    final List<Object[]> requests=new ArrayList<>();

    TodayEarningsCard card() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        Session session=new Session();session.token="fixture";session.employeeId=7;
        AppHost host=(AppHost)Proxy.newProxyInstance(AppHost.class.getClassLoader(),new Class[]{AppHost.class},(p,m,args)->{
            if(m.getName().equals("activity"))return activity;
            if(m.getName().equals("request"))requests.add(args);
            return null;
        });
        return new TodayEarningsCard(host,session);
    }
    void reply(int i,JSONObject response)throws Exception{((AppHost.Result)requests.get(i)[3]).accept(response);}
    List<String> texts(View root) {List<String> out=new ArrayList<>();walk(root,out);return out;}
    private void walk(View v,List<String> out){
        if(v instanceof TextView)out.add(((TextView)v).getText().toString());
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),out);
    }
    JSONObject day() throws Exception {
        return new JSONObject().put("dispatch_enabled",true).put("currency","USD").put("completed_count",3).put("unpriced_count",1).put("earned","13.38")
            .put("paid","5.00").put("owed","8.38").put("in_progress","2.50").put("in_progress_count",1)
            .put("deliveries",new JSONArray()
                .put(new JSONObject().put("order","TP7").put("completed_utc","2026-10-16 22:54:41").put("pay",JSONObject.NULL).put("paid",false))
                .put(new JSONObject().put("order","TP5").put("completed_utc","2026-10-16 20:00:00").put("pay","5.00").put("paid",true))
                .put(new JSONObject().put("order","TP1").put("completed_utc","2026-10-16 18:00:00").put("pay","8.38").put("paid",false)));
    }

    @Test public void asksTheServerForTodayWithTheSignInToken() throws Exception {
        TodayEarningsCard card=card();
        assertEquals(1,requests.size());
        assertEquals("dispatch/earnings",requests.get(0)[0]);
        assertEquals("fixture",((JSONObject)requests.get(0)[1]).getString("token"));
        assertEquals(View.GONE,card.view.getVisibility());   // nothing is shown before the answer
    }
    @Test public void showsWhatWasEarnedAndWhatIsStillBeingDelivered() throws Exception {
        TodayEarningsCard card=card();reply(0,day());
        assertEquals(View.VISIBLE,card.view.getVisibility());
        List<String> shown=texts(card.view);
        assertTrue(shown.contains("Today's earnings"));
        assertTrue(shown.contains("$13.38"));
        assertTrue(shown.contains("3 deliveries completed today (1 without an amount)"));
        assertTrue(shown.contains("+$2.50 when your current delivery is completed"));
        assertTrue(shown.contains("Marked paid $5.00 · Not paid yet $8.38"));
        assertTrue(shown.contains("Tap for today's deliveries"));
    }
    @Test public void aQuietDayShowsZeroAndNothingToTap() throws Exception {
        TodayEarningsCard card=card();
        reply(0,new JSONObject().put("dispatch_enabled",true).put("currency","USD").put("completed_count",0).put("unpriced_count",0).put("earned","0.00").put("paid","0.00").put("owed","0.00")
            .put("in_progress","0.00").put("in_progress_count",0).put("deliveries",new JSONArray()));
        List<String> shown=texts(card.view);
        assertTrue(shown.contains("$0.00"));assertTrue(shown.contains("0 deliveries completed today"));
        assertFalse(shown.contains("Tap for today's deliveries"));
        card.view.performClick();
        assertNull(ShadowDialog.getLatestDialog());
    }
    @Test public void oneDeliveryIsSingular() {
        assertEquals("1 delivery completed today",TodayEarningsCard.countText(1,0));
        assertEquals("2 deliveries completed today (2 without an amount)",TodayEarningsCard.countText(2,2));
    }
    @Test public void hiddenWhenDispatchIsOffForTheOffice() throws Exception {
        TodayEarningsCard card=card();reply(0,day());
        assertEquals(View.VISIBLE,card.view.getVisibility());
        card.refresh();
        reply(1,new JSONObject().put("dispatch_enabled",false));
        assertEquals(View.GONE,card.view.getVisibility());
    }
    @Test public void aLateAnswerToAnOlderRequestIsIgnored() throws Exception {
        TodayEarningsCard card=card();
        card.refresh();                       // a newer request is out
        reply(0,day());                       // the answer to the first one arrives late
        assertEquals(View.GONE,card.view.getVisibility());
        reply(1,day());
        assertEquals(View.VISIBLE,card.view.getVisibility());
    }
    @Test public void tappingListsEachDeliveryWithItsAmountAndPaidState() throws Exception {
        TodayEarningsCard card=card();reply(0,day());
        card.view.performClick();
        Dialog dialog=ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        List<String> inside=texts(dialog.getWindow().getDecorView());
        assertTrue(inside.contains("Today's deliveries"));
        String list=null;for(String s:inside)if(s.contains("TP5"))list=s;
        assertNotNull(list);
        assertTrue(list.contains("TP7")&&list.contains("no amount set"));
        assertTrue(list.contains("$5.00 · paid"));
        assertTrue(list.contains("$8.38 · not paid yet"));
        assertTrue(list.indexOf("TP7")<list.indexOf("TP5")&&list.indexOf("TP5")<list.indexOf("TP1"));
    }
    @Test public void moneyShowsDollarsForUsdAndTheCodeOtherwise() {
        assertEquals("$8.38",TodayEarningsCard.money("8.38","USD"));
        assertEquals("CAD 8.38",TodayEarningsCard.money("8.38","CAD"));
        assertEquals("$8.38",TodayEarningsCard.money("8.38",""));
    }
    @Test public void todayAndWeekRenderAsTwoSeparateCardsAndOpenTheirOwnLists() throws Exception {
        TodayEarningsCard card=card();JSONObject response=day();
        JSONObject week=new JSONObject().put("start_date","2026-10-12").put("end_date","2026-10-18").put("currency","USD")
            .put("earned","30.00").put("paid","10.00").put("owed","20.00").put("completed_count",4).put("unpriced_count",0)
            .put("deliveries",new JSONArray().put(new JSONObject().put("order","WEEK-ONLY").put("completed_utc","2026-10-12 18:00:00").put("pay","30.00").put("paid",false)));
        response.put("week",week);reply(0,response);
        assertEquals(2,card.view.getChildCount());assertNull(card.view.getBackground());
        View today=card.view.getChildAt(0),weekly=card.view.getChildAt(1);
        assertNotNull(today.getBackground());assertNotNull(weekly.getBackground());
        assertTrue(texts(today).contains("$13.38"));assertFalse(texts(today).contains("$30.00"));
        List<String> summary=texts(weekly);
        assertTrue(summary.contains("This week's earnings"));assertTrue(summary.contains("Oct 12 – Oct 18"));
        assertTrue(summary.contains("$30.00"));assertTrue(summary.contains("4 deliveries completed this week"));
        assertTrue(summary.contains("Marked paid $10.00 · Not paid yet $20.00"));
        weekly.performClick();List<String> popup=texts(ShadowDialog.getLatestDialog().getWindow().getDecorView());
        assertTrue(popup.contains("This week's deliveries"));
        assertTrue(popup.toString().contains("WEEK-ONLY"));assertFalse(popup.toString().contains("TP5"));
        today.performClick();popup=texts(ShadowDialog.getLatestDialog().getWindow().getDecorView());
        assertTrue(popup.contains("Today's deliveries"));assertTrue(popup.toString().contains("TP5"));assertFalse(popup.toString().contains("WEEK-ONLY"));
    }
    @Test public void quietWeekShowsZeroButHasNoDeliveryPopup() throws Exception {
        TodayEarningsCard card=card();reply(0,day().put("week",new JSONObject().put("earned","0.00").put("completed_count",0).put("deliveries",new JSONArray())));
        View weekly=card.view.getChildAt(1);assertTrue(texts(weekly).contains("$0.00"));
        assertTrue(texts(weekly).contains("0 deliveries completed this week"));
        weekly.performClick();assertNull(ShadowDialog.getLatestDialog());
        assertEquals("Dec 28 – Jan 3",TodayEarningsCard.weekRange("2026-12-28","2027-01-03"));
    }

}
