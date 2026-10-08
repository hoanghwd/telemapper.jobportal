package com.huynhdous.employeefield.schedule;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.core.ui.TimeCardUi;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "Schedule": this week's shifts day by day, each day's time clock (see {@link DayClock}), the hours rings (see {@link HoursCard}), and the
 * button to confirm the week. It also owns the facts behind the weekly-schedule gate: with shifts this week and no confirmation, the gate
 * locks the rest of the app until the employee opens this screen and confirms.
 */
public final class ScheduleTab extends TabModule {
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] DAY_CODES = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};

    private LinearLayout scheduleContainer;
    private TextView confirmStatus;
    private Button confirmButton;
    private HoursCard hoursCard;
    private String scheduleWeekStart;

    @Override
    public String title() {
        return "Schedule";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_schedule;
    }

    /** This screen is where the schedule is read and confirmed, so it must stay usable while everything else is locked. */
    @Override
    public boolean isGated() {
        return false;
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();

        LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshParams.bottomMargin = (int) (8 * density);
        refresh.setOnClickListener(v -> load());
        content.addView(refresh, refreshParams);

        hoursCard = new HoursCard(host());
        LinearLayout.LayoutParams hoursParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hoursParams.bottomMargin = (int) (12 * density);
        content.addView(hoursCard.view(), hoursParams);

        scheduleContainer = new LinearLayout(context());
        scheduleContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(scheduleContainer);
        TextView loading = new TextView(context());
        loading.setText("Loading…");
        loading.setTextSize(15);
        scheduleContainer.addView(loading);

        confirmStatus = new TextView(context());
        Theme.applyBadgeStyle(confirmStatus, Theme.SUCCESS);
        confirmStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams confirmStatusParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmStatusParams.bottomMargin = (int) (8 * density);
        content.addView(confirmStatus, confirmStatusParams);

        confirmButton = Theme.filledButton(context(), "Confirm this week's schedule", Theme.PRIMARY);
        confirmButton.setVisibility(View.GONE);
        content.addView(confirmButton);
        confirmButton.setOnClickListener(v -> confirm());
    }

    @Override
    public void onShown() {
        load();
    }

    /** Coming back to the app: reload so the schedule, the punches and the gate are current. */
    @Override
    public void onResume() {
        if (scheduleContainer != null) load();
    }

    @Override
    public void onDetach() {
        scheduleContainer = null;
        confirmStatus = null;
        confirmButton = null;
        hoursCard = null;
    }

    /** Checks the gate without drawing this screen (used when the app opens on another screen). */
    public void refreshGate() {
        try {
            host().request("schedule", new JSONObject().put("token", host().token()), null, r ->
                    host().gate().set(r.getJSONArray("assignments").length() > 0 && !isConfirmed(r), r.optString("week_start", null)), true, null);
        } catch (Exception ignored) {
            // offline or unreachable: keep whatever was last known rather than locking someone out of the field
        }
    }

    private static boolean isConfirmed(JSONObject data) {
        String confirmedUtc = data.isNull("confirmed_utc") ? null : data.optString("confirmed_utc", null);
        return confirmedUtc != null && !confirmedUtc.isEmpty();
    }

    private void load() {
        if (scheduleContainer == null) return;
        hoursCard.loadWeekTotal();
        try {
            host().request("schedule", new JSONObject().put("token", host().token()), null, this::render);
        } catch (Exception e) {
            scheduleContainer.removeAllViews();
            TextView error = new TextView(context());
            error.setText("Unable to load schedule.");
            scheduleContainer.addView(error);
        }
    }

    private void render(JSONObject data) throws Exception {
        if (scheduleContainer == null) return;
        scheduleWeekStart = data.getString("week_start");
        java.time.LocalDate monday = java.time.LocalDate.parse(scheduleWeekStart);
        java.time.LocalDate weekEndDate = java.time.LocalDate.parse(data.getString("week_end"));
        int scheduleDayCount = (int) (java.time.temporal.ChronoUnit.DAYS.between(monday, weekEndDate) + 1);
        JSONObject employeeObj = data.getJSONObject("employee");
        java.time.ZoneId zone;
        try {
            zone = java.time.ZoneId.of(employeeObj.optString("timezone", ""));
        } catch (Exception e) {
            zone = java.time.ZoneId.systemDefault();
        }
        java.time.LocalDate today = java.time.LocalDate.now(zone);

        JSONArray assignments = data.getJSONArray("assignments");
        // decided BEFORE the day buttons are built, so they start out turned off if the week is not confirmed yet
        host().gate().clearDimmedColumns();
        host().gate().setState(assignments.length() > 0 && !isConfirmed(data), scheduleWeekStart);
        java.util.Map<String, java.util.List<JSONObject>> byDate = new java.util.LinkedHashMap<>();
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject a = assignments.getJSONObject(i);
            byDate.computeIfAbsent(a.getString("work_date"), k -> new java.util.ArrayList<>()).add(a);
        }
        float density = density();

        scheduleContainer.removeAllViews();
        java.util.List<DayClock> dayClocks = new java.util.ArrayList<>();
        int weekMinutesScheduled = 0;
        int todayMinutesScheduled = 0;
        for (int i = 0; i < scheduleDayCount; i++) {
            java.time.LocalDate date = monday.plusDays(i);
            String dateKey = date.toString();
            boolean isToday = date.equals(today);

            LinearLayout dayCard = TimeCardUi.card(context());
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(-1,-2);
            dayParams.topMargin = TimeCardUi.dp(context(),14);
            scheduleContainer.addView(dayCard,dayParams);
            LinearLayout headerRow = new LinearLayout(context());
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setPadding(0,0,0,TimeCardUi.dp(context(),8));
            dayCard.addView(headerRow);

            TextView header = new TextView(context());
            header.setText(date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d",java.util.Locale.US)));
            header.setTextSize(15);
            header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
            header.setTextColor(isToday ? Theme.PRIMARY : Theme.TEXT_PRIMARY);
            headerRow.addView(header);

            if (isToday) {
                TextView todayBadge = Theme.statusBadge(context(), "TODAY", Theme.PRIMARY);
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                badgeParams.leftMargin = (int) (8 * density);
                headerRow.addView(todayBadge, badgeParams);
            }

            java.util.List<JSONObject> rows = byDate.get(dateKey);
            if (rows == null || rows.isEmpty()) {
                addRow(DAY_CODES[i], 0xfff3f4f6, 0xff9ca3af, "No assignments", null, density, dayCard);
                continue;
            }
            for (JSONObject a : rows) {
                String startTime = a.getString("start_time"), endTime = a.getString("end_time");
                addRow(DAY_CODES[i], isToday ? Theme.PRIMARY : 0xffe5e7eb, isToday ? 0xffffffff : Theme.NEUTRAL,
                        a.optString("location_name", "Unknown location"),
                        startTime.substring(0, 5) + " – " + endTime.substring(0, 5), density, dayCard);
                int shiftMinutes = shiftMinutes(a);
                weekMinutesScheduled += shiftMinutes;
                if (isToday) todayMinutesScheduled += shiftMinutes;
            }
            if (date.isAfter(today)) continue;
            LinearLayout timeClockContainer = host().gate().newDimmedColumn(context());
            timeClockContainer.setOrientation(LinearLayout.VERTICAL);
            dayCard.addView(timeClockContainer);
            boolean withinWindow = false;
            java.time.LocalTime nextStart = null;
            if (isToday) {
                java.time.LocalTime nowTime = java.time.LocalTime.now(zone);
                for (JSONObject a : rows) {
                    java.time.LocalTime s = java.time.LocalTime.parse(a.getString("start_time").substring(0, 5));
                    java.time.LocalTime e = java.time.LocalTime.parse(a.getString("end_time").substring(0, 5));
                    if (!nowTime.isBefore(s) && nowTime.isBefore(e)) {
                        withinWindow = true;
                        break;
                    }
                    if (nowTime.isBefore(s) && (nextStart == null || s.isBefore(nextStart))) nextStart = s;
                }
            }
            dayClocks.add(new DayClock(host(), timeClockContainer, dateKey, withinWindow, nextStart, zone, isToday, hoursCard));
        }
        hoursCard.setScheduled(todayMinutesScheduled, weekMinutesScheduled);
        loadDayClocks(dayClocks, 0);

        if (isConfirmed(data)) {
            String confirmedUtc = data.optString("confirmed_utc", "");
            java.time.Instant instant = java.time.Instant.parse(confirmedUtc.replace(' ', 'T') + "Z");
            String local = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(instant);
            confirmStatus.setText("✓ Schedule confirmed " + local);
            confirmStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
        } else {
            confirmStatus.setVisibility(View.GONE);
            confirmButton.setVisibility(View.VISIBLE);
        }
        host().gate().notifyChanged();
    }

    /** One day's punches at a time: the app allows one call at once, so each day loads after the previous one is drawn. */
    private void loadDayClocks(java.util.List<DayClock> clocks, int index) {
        if (index >= clocks.size()) return;
        clocks.get(index).load(() -> loadDayClocks(clocks, index + 1));
    }

    private void confirm() {
        try {
            host().request("schedule/confirm", new JSONObject().put("token", host().token()).put("week_start", scheduleWeekStart), confirmButton, r -> {
                confirmButton.setVisibility(View.GONE);
                confirmStatus.setText("✓ Schedule confirmed just now.");
                confirmStatus.setVisibility(View.VISIBLE);
                host().gate().set(false, scheduleWeekStart);   // everything turns back on
            });
        } catch (Exception ignored) {
        }
    }

    private static int shiftMinutes(JSONObject a) throws Exception {
        java.time.LocalTime s = java.time.LocalTime.parse(a.getString("start_time").substring(0, 5));
        java.time.LocalTime e = java.time.LocalTime.parse(a.getString("end_time").substring(0, 5));
        int minutes = (int) java.time.Duration.between(s, e).toMinutes();
        return minutes >= 0 ? minutes : minutes + 24 * 60;
    }

    /** One shift (or "No assignments") as a card: a round day code, the place and the hours. */
    private void addRow(String avatarText, int avatarBg, int avatarTextColor, String title, String subtitle, float density, LinearLayout parent) {
        LinearLayout row = new LinearLayout(context());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        TextView avatar = new TextView(context());
        avatar.setText(avatarText);
        avatar.setTextColor(Theme.PRIMARY);
        avatar.setTextSize(12);
        avatar.setTypeface(avatar.getTypeface(), android.graphics.Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0xffedf2ff);
        avatar.setBackground(circle);
        int size = (int) (36 * density);
        row.addView(avatar, new LinearLayout.LayoutParams(size, size));

        LinearLayout textStack = new LinearLayout(context());
        textStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stackParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stackParams.leftMargin = (int) (12 * density);
        row.addView(textStack, stackParams);

        TextView titleView = new TextView(context());
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(Theme.TEXT_PRIMARY);
        textStack.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(context());
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(13);
            subtitleView.setTextColor(Theme.TEXT_SECONDARY);
            textStack.addView(subtitleView);
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = (int) (8 * density);
        parent.addView(row, cardParams);
    }
}
