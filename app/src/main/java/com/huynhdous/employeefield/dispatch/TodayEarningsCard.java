package com.huynhdous.employeefield.dispatch;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "Today's earnings" on the home screen: what the driver earned from the deliveries he completed today (the office's day), how many, what he is
 * still delivering and how much of it Coast Runner has marked paid. Tapping it lists today's deliveries. The server does the sums; this only shows
 * them. Hidden when dispatch is not turned on for his office.
 */
public final class TodayEarningsCard {
    public final LinearLayout view;
    private final AppHost host;
    private final Session session;
    private long readVersion;
    private JSONObject data;

    public TodayEarningsCard(AppHost host, Session session) {
        this.host = host;
        this.session = session;
        view = new LinearLayout(host.activity());
        view.setOrientation(LinearLayout.VERTICAL);

        view.setVisibility(View.GONE);
        view.setOnClickListener(v -> { if (data != null) showList(false); });
        refresh();
    }

    private int dp(int value) { return Math.round(value * host.activity().getResources().getDisplayMetrics().density); }

    public void refresh() {
        final long version = ++readVersion;
        try {
            host.request("dispatch/earnings", new JSONObject().put("token", session.token), null, response -> {
                if (version != readVersion) return;
                data = response;
                render();
            }, true, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** "$8.38" for the server's "8.38" in US dollars; other currencies show their code. */
    static String money(String amount, String currency) {
        return ("USD".equals(currency) || currency == null || currency.isEmpty() ? "$" : currency + " ") + amount;
    }

    private TextView line(String text, int size, boolean bold, int color, int top) {
        TextView t = new TextView(host.activity());
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, top, 0, 0);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    static String countText(int completed, int unpriced) {
        String text = completed == 1 ? "1 delivery completed today" : completed + " deliveries completed today";
        return unpriced > 0 ? text + " (" + unpriced + " without an amount)" : text;
    }

    private void render() {
        view.removeAllViews();
        if (data == null || !data.optBoolean("dispatch_enabled")) { view.setVisibility(View.GONE); return; }
        view.setVisibility(View.VISIBLE);
        addCard(data,false);
        JSONObject week=data.optJSONObject("week");
        if(week!=null)addCard(week,true);
    }

    private void addCard(JSONObject period,boolean weekly) {
        LinearLayout card=new LinearLayout(host.activity());card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(Theme.cardBackground(host.activity()));
        card.setOnClickListener(v->showList(weekly));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        if(weekly)params.topMargin=dp(10);view.addView(card,params);
        String currency=period.optString("currency","USD");
        card.addView(line(weekly?"This week's earnings":"Today's earnings",13,false,Theme.TEXT_SECONDARY,0));
        if(weekly) {
            String range=weekRange(period.optString("start_date"),period.optString("end_date"));
            if(!range.isEmpty())card.addView(line(range,12,false,Theme.TEXT_SECONDARY,dp(2)));
        }
        card.addView(line(money(period.optString("earned","0.00"),currency),28,true,Theme.TEXT_PRIMARY,dp(2)));
        String count=countText(period.optInt("completed_count"),period.optInt("unpriced_count"));
        if(weekly)count=count.replace("today","this week");
        card.addView(line(count,14,false,Theme.TEXT_PRIMARY,dp(2)));
        if(!weekly && period.optInt("in_progress_count")>0)
            card.addView(line("+"+money(period.optString("in_progress","0.00"),currency)+" when your current delivery is completed",13,false,Theme.TEXT_SECONDARY,dp(4)));
        if(period.optInt("completed_count")>0 && !"0.00".equals(period.optString("earned")))
            card.addView(line("Marked paid "+money(period.optString("paid","0.00"),currency)+" · Not paid yet "+money(period.optString("owed","0.00"),currency),13,false,Theme.TEXT_SECONDARY,dp(4)));
        if(period.optInt("completed_count")>0)card.addView(line(weekly?"Tap for this week's deliveries":"Tap for today's deliveries",12,false,Theme.TEXT_SECONDARY,dp(6)));
    }

    static String weekRange(String start,String end) {
        try {
            DateTimeFormatter format=DateTimeFormatter.ofPattern("MMM d",Locale.US);
            return java.time.LocalDate.parse(start).format(format)+" – "+java.time.LocalDate.parse(end).format(format);
        } catch(Exception invalid){return "";}
    }

    /** One line per delivery: order number, time (this phone's clock), amount and whether it is paid. */
    static String listText(JSONArray deliveries, String currency) { return listText(deliveries,currency,false); }
    static String listText(JSONArray deliveries, String currency, boolean weekly) {
        StringBuilder out = new StringBuilder();
        DateTimeFormatter time = DateTimeFormatter.ofPattern(weekly ? "EEE, MMM d · h:mm a" : "h:mm a", Locale.US).withZone(ZoneId.systemDefault());
        for (int i = 0; i < deliveries.length(); i++) {
            JSONObject d = deliveries.optJSONObject(i);
            if (d == null) continue;
            String when = "";
            try { when = time.format(Instant.parse(d.optString("completed_utc").replace(' ', 'T') + "Z")); } catch (Exception ignored) { }
            String amount = d.isNull("pay") ? "no amount set" : money(d.optString("pay"), currency) + (d.optBoolean("paid") ? " · paid" : " · not paid yet");
            if (out.length() > 0) out.append("\n\n");
            out.append(d.optString("order")).append(when.isEmpty() ? "" : " · " + when).append("\n").append(amount);
        }
        return out.toString();
    }

    private void showList(boolean weekly) {
        JSONObject period=weekly?data.optJSONObject("week"):data;
        if(period==null)return;
        JSONArray deliveries = period.optJSONArray("deliveries");
        if (deliveries == null || deliveries.length() == 0) return;
        new Popup.Builder(host.activity()).setTitle(weekly?"This week's deliveries":"Today's deliveries")
                .setMessage(listText(deliveries, period.optString("currency", "USD"),weekly))
                .setPositiveButton("Close", null)
                .setNeutralButton("Refresh", (d, w) -> refresh()).show();
    }
}
