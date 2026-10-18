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
    boolean tapCard(View root,String title){if(root instanceof TextView&&title.contentEquals(((TextView)root).getText())){((View)root.getParent()).performClick();return true;}if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(tapCard(((ViewGroup)root).getChildAt(i),title))return true;return false;}
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
        assertEquals(View.VISIBLE,card.view.getVisibility());assertTrue(texts(card.view).contains("Today's: $13.38"));
        assertFalse(texts(card.view).contains("3 deliveries completed today (1 without an amount)"));
        card.view.performClick();List<String> shown=texts(ShadowDialog.getLatestDialog().getWindow().getDecorView());
        assertTrue(shown.contains("Today's earnings"));assertTrue(shown.contains("3 deliveries completed today (1 without an amount)"));
        assertTrue(shown.contains("+$2.50 when your current delivery is completed"));assertTrue(shown.contains("Marked paid $5.00 · Not paid yet $8.38"));
    }

    @Test public void aQuietDayShowsZeroAndNothingToTap() throws Exception {
        TodayEarningsCard card=card();reply(0,new JSONObject().put("dispatch_enabled",true).put("earned","0.00").put("completed_count",0));
        assertTrue(texts(card.view).contains("Today's: $0.00"));card.view.performClick();
        assertTrue(texts(ShadowDialog.getLatestDialog().getWindow().getDecorView()).contains("0 deliveries completed today"));
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
        TodayEarningsCard card=card();reply(0,day());card.view.performClick();
        View root=ShadowDialog.getLatestDialog().getWindow().getDecorView();tapCard(root,"Today's earnings");
        List<String> inside=texts(ShadowDialog.getLatestDialog().getWindow().getDecorView());assertTrue(inside.contains("Today's deliveries"));
        String list=inside.toString();assertTrue(list.contains("TP7")&&list.contains("no amount set"));assertTrue(list.contains("$5.00 · paid")&&list.contains("$8.38 · not paid yet"));
    }

    @Test public void moneyShowsDollarsForUsdAndTheCodeOtherwise() {
        assertEquals("$8.38",TodayEarningsCard.money("8.38","USD"));
        assertEquals("CAD 8.38",TodayEarningsCard.money("8.38","CAD"));
        assertEquals("$8.38",TodayEarningsCard.money("8.38",""));
    }
    @Test public void todayAndWeekRenderAsTwoSeparateCardsAndOpenTheirOwnLists() throws Exception {
        TodayEarningsCard card=card();JSONObject response=day();
        response.put("week",new JSONObject().put("start_date","2026-10-12").put("end_date","2026-10-18").put("earned","30.00").put("paid","10.00").put("owed","20.00").put("completed_count",4)
            .put("deliveries",new JSONArray().put(new JSONObject().put("order","WEEK-ONLY").put("completed_utc","2026-10-12 18:00:00").put("pay","30.00"))));
        reply(0,response);assertEquals(android.widget.LinearLayout.HORIZONTAL,card.view.getOrientation());assertEquals(2,card.view.getChildCount());
        assertTrue(texts(card.view.getChildAt(0)).contains("Weekly: $30.00"));assertTrue(texts(card.view.getChildAt(1)).contains("Today's: $13.38"));
        card.view.getChildAt(0).performClick();View details=ShadowDialog.getLatestDialog().getWindow().getDecorView();List<String> shown=texts(details);
        assertTrue(shown.contains("Today's earnings")&&shown.contains("This week's earnings"));assertTrue(shown.contains("Oct 12 – Oct 18"));assertTrue(shown.contains("Marked paid $10.00 · Not paid yet $20.00"));
        tapCard(details,"This week's earnings");String weekly=texts(ShadowDialog.getLatestDialog().getWindow().getDecorView()).toString();assertTrue(weekly.contains("WEEK-ONLY"));assertFalse(weekly.contains("TP5"));
    }

    @Test public void quietWeekShowsZeroButHasNoDeliveryPopup() throws Exception {
        TodayEarningsCard card=card();reply(0,day().put("week",new JSONObject().put("earned","0.00").put("completed_count",0)));
        assertTrue(texts(card.view.getChildAt(0)).contains("Weekly: $0.00"));card.view.getChildAt(0).performClick();
        assertTrue(texts(ShadowDialog.getLatestDialog().getWindow().getDecorView()).contains("0 deliveries completed this week"));
        assertEquals("Dec 28 – Jan 3",TodayEarningsCard.weekRange("2026-12-28","2027-01-03"));
    }

}
