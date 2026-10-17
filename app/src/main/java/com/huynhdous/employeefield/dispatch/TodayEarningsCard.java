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
        int pad = dp(16);
        view.setPadding(pad, pad, pad, pad);
        view.setBackground(Theme.cardBackground(host.activity()));
        view.setVisibility(View.GONE);
        view.setOnClickListener(v -> { if (data != null) showList(); });
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
        String currency = data.optString("currency", "USD");
        view.addView(line("Today's earnings", 13, false, Theme.TEXT_SECONDARY, 0));
        view.addView(line(money(data.optString("earned", "0.00"), currency), 28, true, Theme.TEXT_PRIMARY, dp(2)));
        view.addView(line(countText(data.optInt("completed_count"), data.optInt("unpriced_count")), 14, false, Theme.TEXT_PRIMARY, dp(2)));
        if (data.optInt("in_progress_count") > 0)
            view.addView(line("+" + money(data.optString("in_progress", "0.00"), currency) + " when your current delivery is completed", 13, false, Theme.TEXT_SECONDARY, dp(4)));
        if (data.optInt("completed_count") > 0 && !"0.00".equals(data.optString("earned")))
            view.addView(line("Marked paid " + money(data.optString("paid", "0.00"), currency) + " · Not paid yet " + money(data.optString("owed", "0.00"), currency), 13, false, Theme.TEXT_SECONDARY, dp(4)));
        if (data.optInt("completed_count") > 0) view.addView(line("Tap for today's deliveries", 12, false, Theme.TEXT_SECONDARY, dp(6)));
    }

    /** One line per delivery: order number, time (this phone's clock), amount and whether it is paid. */
    static String listText(JSONArray deliveries, String currency) {
        StringBuilder out = new StringBuilder();
        DateTimeFormatter time = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneId.systemDefault());
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

    private void showList() {
        JSONArray deliveries = data.optJSONArray("deliveries");
        if (deliveries == null || deliveries.length() == 0) return;
        new Popup.Builder(host.activity()).setTitle("Today's deliveries")
                .setMessage(listText(deliveries, data.optString("currency", "USD")))
                .setPositiveButton("Close", null)
                .setNeutralButton("Refresh", (d, w) -> refresh()).show();
    }
}
