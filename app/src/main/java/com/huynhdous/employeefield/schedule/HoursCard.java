package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.Tabs;
import com.huynhdous.employeefield.core.ui.DayProgressRing;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * The card at the top of the Schedule screen: two rings, "Today" (hours worked so far against today's scheduled hours) and "This Week"
 * (worked against the week's scheduled hours), when today's clock-in was, and a Details link to the Time Sheet. Hidden until today's punches arrive.
 */
final class HoursCard {
    /** A circular progress gauge: a ring with its value text centered inside it, plus a caption below. */
    private static final class Gauge {
        final LinearLayout column;
        final DayProgressRing ring;
        final TextView centerText;
        final TextView subText;

        Gauge(LinearLayout column, DayProgressRing ring, TextView centerText, TextView subText) {
            this.column = column;
            this.ring = ring;
            this.centerText = centerText;
            this.subText = subText;
        }
    }

    private final AppHost host;
    private final LinearLayout card;
    private final Gauge dayGauge;
    private final Gauge weekGauge;
    private final TextView clockInText;
    private int todayScheduledMinutes = Config.WORK_DAILY_TARGET_MINUTES;
    private int weekScheduledMinutes = -1;
    private int weekWorkedMinutes = -1;

    HoursCard(AppHost host) {
        this.host = host;
        Activity context = host.activity();
        float density = context.getResources().getDisplayMetrics().density;

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
        card.setBackground(Theme.cardBackground(context));
        card.setVisibility(View.GONE);

        LinearLayout gaugeRow = new LinearLayout(context);
        gaugeRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(gaugeRow);

        dayGauge = buildGauge("Today", "of 8h", density);
        weekGauge = buildGauge("This Week", "of 40h", density);
        gaugeRow.addView(dayGauge.column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        gaugeRow.addView(weekGauge.column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        clockInText = new TextView(context);
        clockInText.setTextSize(13);
        clockInText.setTextColor(Theme.TEXT_SECONDARY);
        clockInText.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams clockInParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clockInParams.topMargin = (int) (12 * density);
        card.addView(clockInText, clockInParams);

        TextView details = new TextView(context);
        details.setText("Details");
        details.setTextSize(13);
        details.setTypeface(details.getTypeface(), android.graphics.Typeface.BOLD);
        details.setTextColor(Theme.PRIMARY);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = (int) (8 * density);
        card.addView(details, detailsParams);
        details.setOnClickListener(v -> host.selectTab(Tabs.TIMESHEET));
    }

    /** The card itself, to place in the screen. */
    View view() {
        return card;
    }

    private Gauge buildGauge(String caption, String subLabel, float density) {
        Activity context = host.activity();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        int size = (int) (92 * density);
        FrameLayout frame = new FrameLayout(context);
        column.addView(frame, new LinearLayout.LayoutParams(size, size));

        DayProgressRing ring = new DayProgressRing(context);
        frame.addView(ring, new FrameLayout.LayoutParams(size, size));

        LinearLayout centerColumn = new LinearLayout(context);
        centerColumn.setOrientation(LinearLayout.VERTICAL);
        centerColumn.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(size, size);
        centerParams.gravity = Gravity.CENTER;
        frame.addView(centerColumn, centerParams);

        TextView centerText = new TextView(context);
        centerText.setTextSize(16);
        centerText.setTypeface(centerText.getTypeface(), android.graphics.Typeface.BOLD);
        centerText.setTextColor(Theme.TEXT_PRIMARY);
        centerText.setGravity(Gravity.CENTER);
        centerColumn.addView(centerText);

        TextView subText = new TextView(context);
        subText.setText(subLabel);
        subText.setTextSize(10);
        subText.setTextColor(Theme.TEXT_SECONDARY);
        subText.setGravity(Gravity.CENTER);
        centerColumn.addView(subText);

        TextView label = new TextView(context);
        label.setText(caption);
        label.setTextSize(12);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (8 * density);
        column.addView(label, labelParams);

        return new Gauge(column, ring, centerText, subText);
    }

    /** What the schedule says this week and today, in minutes (the rings' targets). */
    void setScheduled(int todayMinutes, int weekMinutes) {
        todayScheduledMinutes = todayMinutes > 0 ? todayMinutes : Config.WORK_DAILY_TARGET_MINUTES;
        weekScheduledMinutes = weekMinutes > 0 ? weekMinutes : -1;
        updateWeekGauge();
    }

    /** Today's ring and clock-in line, from today's punches. */
    void renderDayTotal(JSONArray events, java.time.ZoneId zone) throws Exception {
        int workMinutes = 0;
        String state = null;
        java.time.Instant segStart = null;
        String firstClockIn = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            java.time.Instant t = java.time.Instant.parse(e.getString("event_utc").replace(' ', 'T') + "Z");
            long elapsed = segStart != null ? java.time.Duration.between(segStart, t).toMinutes() : 0;
            String type = e.getString("event_type");
            switch (type) {
                case "start_work":
                    if (firstClockIn == null)
                        firstClockIn = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(zone).format(t);
                    state = "work";
                    segStart = t;
                    break;
                case "start_lunch":
                    if ("work".equals(state)) workMinutes += elapsed;
                    state = "lunch";
                    segStart = t;
                    break;
                case "end_lunch":
                    state = "work";
                    segStart = t;
                    break;
                case "start_break":
                    if ("work".equals(state)) workMinutes += elapsed;
                    state = "break";
                    segStart = t;
                    break;
                case "end_break":
                    state = "work";
                    segStart = t;
                    break;
                case "end_work":
                    if ("work".equals(state)) workMinutes += elapsed;
                    state = null;
                    segStart = null;
                    break;
            }
        }
        boolean stillWorking = "work".equals(state) && segStart != null;
        if (stillWorking) workMinutes += java.time.Duration.between(segStart, java.time.Instant.now()).toMinutes();

        card.setVisibility(View.VISIBLE);
        dayGauge.centerText.setText(String.format(java.util.Locale.US, "%dh %02dm", workMinutes / 60, workMinutes % 60));
        dayGauge.subText.setText(formatHoursShort(todayScheduledMinutes));
        dayGauge.ring.setProgress(workMinutes / (float) todayScheduledMinutes, stillWorking ? Theme.PRIMARY : Theme.SUCCESS);
        clockInText.setText(firstClockIn == null ? "Not clocked in yet" : "Clocked in at " + firstClockIn);
    }

    /** "of 8h" or "of 6h 30m" — the scheduled-hours caption shown inside a gauge. */
    private static String formatHoursShort(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? ("of " + h + "h") : String.format(java.util.Locale.US, "of %dh %02dm", h, m);
    }

    /** Refreshes the week gauge once both its inputs (actual worked minutes, scheduled minutes) are known. */
    private void updateWeekGauge() {
        if (weekWorkedMinutes < 0) return;
        int target = weekScheduledMinutes > 0 ? weekScheduledMinutes : Config.WORK_WEEKLY_TARGET_MINUTES;
        weekGauge.centerText.setText(String.format(java.util.Locale.US, "%dh %02dm", weekWorkedMinutes / 60, weekWorkedMinutes % 60));
        weekGauge.subText.setText(formatHoursShort(target));
        weekGauge.ring.setProgress(weekWorkedMinutes / (float) target, weekWorkedMinutes >= target ? Theme.SUCCESS : Theme.PRIMARY);
    }

    /** Fetch the week's worked total for the "This Week" ring. */
    void loadWeekTotal() {
        // Independent of the shared one-call-at-a-time gate: this runs alongside the schedule fetch
        // (also triggered from the schedule load) and must not cause that one to be silently dropped.
        final String token = host.token();
        new Thread(() -> {
            JSONObject response = null;
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + "timesheet/week").openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(new JSONObject().put("token", token).toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream != null) {
                    JSONObject data = new JSONObject(new String(Api.readAllBytes(stream), StandardCharsets.UTF_8));
                    if (code >= 200 && code < 300 && data.optBoolean("success")) response = data;
                }
            } catch (Exception ignored) {
                // Offline or transient failure — leave the week gauge showing its last value.
            } finally {
                if (conn != null) conn.disconnect();
            }
            final JSONObject data = response;
            host.activity().runOnUiThread(() -> {
                if (data == null) return;
                try {
                    weekWorkedMinutes = data.getInt("total_minutes");
                    updateWeekGauge();
                } catch (Exception ignored) {
                }
            });
        }).start();
    }
}
