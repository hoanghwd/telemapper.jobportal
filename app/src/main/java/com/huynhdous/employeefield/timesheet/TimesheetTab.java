package com.huynhdous.employeefield.timesheet;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.core.ui.TimeCardUi;

import org.json.JSONArray;
import org.json.JSONObject;

/** "Time Sheet": this week's worked hours -- the total, whether the week is accepted, and each day's hours (with in-progress, missing clock-out and conflict flags). */
public final class TimesheetTab extends TabModule {
    private LinearLayout daysContainer;
    private TextView totalText;
    private LinearLayout badgeRow;
    private TextView message;

    @Override
    public String title() {
        return "Time Sheet";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_timesheet;
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();

        LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshParams.bottomMargin = (int) (8 * density);
        content.addView(refresh, refreshParams);
        refresh.setOnClickListener(v -> load());

        LinearLayout summaryCard = new LinearLayout(context());
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
        android.graphics.drawable.GradientDrawable hero=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff153572,0xff255be0});
        hero.setCornerRadius(TimeCardUi.dp(context(),18));summaryCard.setBackground(hero);
        summaryCard.addView(TimeCardUi.text(context(),"WEEKLY HOURS",11,0xffcbdcff,true));
        content.addView(summaryCard);

        message = new TextView(context());
        message.setTextSize(13);
        message.setTextColor(0xffd9e4ff);
        summaryCard.addView(message);

        totalText = new TextView(context());
        totalText.setTextSize(36);
        totalText.setTypeface(totalText.getTypeface(), android.graphics.Typeface.BOLD);
        totalText.setTextColor(0xffffffff);
        summaryCard.addView(totalText);

        badgeRow = new LinearLayout(context());
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams badgeRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeRowParams.topMargin = (int) (8 * density);
        summaryCard.addView(badgeRow, badgeRowParams);

        daysContainer = new LinearLayout(context());
        daysContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams daysParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        daysParams.topMargin = (int) (8 * density);
        content.addView(daysContainer, daysParams);
    }

    @Override
    public void onShown() {
        load();
    }

    @Override
    public void onDetach() {
        daysContainer = null;
        totalText = null;
        badgeRow = null;
        message = null;
    }

    private void load() {
        if (message == null) return;
        message.setText("Loading…");
        try {
            host().request("timesheet/week", new JSONObject().put("token", host().token()), null, this::render);
        } catch (Exception e) {
            message.setText("Unable to load timesheet.");
        }
    }

    private void render(JSONObject data) throws Exception {
        if (message == null) return;
        float density = density();
        String weekStart = data.getString("week_start"), weekEnd = data.getString("week_end");
        java.time.LocalDate start = java.time.LocalDate.parse(weekStart), end = java.time.LocalDate.parse(weekEnd);
        java.time.format.DateTimeFormatter shortDate = java.time.format.DateTimeFormatter.ofPattern("MMM d");
        message.setText("Week of " + shortDate.format(start) + " – " + shortDate.format(end));

        int totalMinutes = data.getInt("total_minutes");
        int approvedMinutes = data.optInt("approved_minutes", 0);
        int pendingMinutes = data.optInt("pending_minutes", totalMinutes - approvedMinutes);
        totalText.setText(String.format(java.util.Locale.US, "%dh %02dm", totalMinutes / 60, totalMinutes % 60));

        badgeRow.removeAllViews();
        String submissionStatus = data.isNull("submission_status") ? null : data.optString("submission_status", null);
        // Paul only needs to know one thing: is this week done, or still being worked on. Everything else
        // (approved/submitted/accepted/reopened/needs_correction) is office-internal plumbing.
        boolean accepted = "accepted".equals(submissionStatus) && pendingMinutes <= 0;
        addBadge(badgeRow, accepted ? "Accepted" : "Pending", accepted ? Theme.SUCCESS : Theme.PRIMARY, density);

        daysContainer.removeAllViews();
        JSONArray days = data.getJSONArray("days");
        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d");
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            java.time.LocalDate date = java.time.LocalDate.parse(day.getString("date"));
            boolean incomplete = day.getBoolean("incomplete");
            boolean inProgress = day.getBoolean("in_progress");
            boolean hasDispute = day.getBoolean("has_dispute");
            Integer netMinutes = day.isNull("net_minutes") ? null : day.getInt("net_minutes");

            LinearLayout dayCard = new LinearLayout(context());
            dayCard.setOrientation(LinearLayout.VERTICAL);
            dayCard.setPadding((int) (18 * density), (int) (16 * density), (int) (18 * density), (int) (16 * density));
            dayCard.setBackground(Theme.cardBackground(context()));
            LinearLayout.LayoutParams dayCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dayCardParams.topMargin = (int) (8 * density);
            daysContainer.addView(dayCard, dayCardParams);

            LinearLayout dayRow = new LinearLayout(context());
            dayRow.setOrientation(LinearLayout.HORIZONTAL);
            dayRow.setGravity(Gravity.CENTER_VERTICAL);
            dayCard.addView(dayRow);
            TextView calendar=TimeCardUi.text(context(),String.valueOf(date.getDayOfMonth()),17,Theme.PRIMARY,true);
            calendar.setGravity(Gravity.CENTER);calendar.setBackground(TimeCardUi.background(context(),0xffedf2ff,12));
            LinearLayout.LayoutParams calendarParams=new LinearLayout.LayoutParams(TimeCardUi.dp(context(),42),TimeCardUi.dp(context(),42));
            calendarParams.rightMargin=TimeCardUi.dp(context(),12);dayRow.addView(calendar,calendarParams);

            TextView dayLabel = new TextView(context());
            dayLabel.setText(dayFormat.format(date));
            dayLabel.setTextSize(14);
            dayLabel.setTypeface(null,android.graphics.Typeface.BOLD);
            dayLabel.setTextColor(Theme.TEXT_PRIMARY);
            dayRow.addView(dayLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView hoursLabel = new TextView(context());
            if (netMinutes != null)
                hoursLabel.setText(String.format(java.util.Locale.US, "%dh %02dm", netMinutes / 60, netMinutes % 60));
            else if (inProgress) hoursLabel.setText("—");
            else if (incomplete) hoursLabel.setText("—");
            else hoursLabel.setText("—");
            hoursLabel.setTextSize(19);
            hoursLabel.setTypeface(hoursLabel.getTypeface(), android.graphics.Typeface.BOLD);
            hoursLabel.setTextColor(incomplete ? Theme.ERROR : inProgress ? Theme.PRIMARY : Theme.TEXT_PRIMARY);
            dayRow.addView(hoursLabel);

            if(inProgress || incomplete) {
                TextView stateBadge=Theme.statusBadge(context(),incomplete?"Missing clock-out":"In progress",incomplete?Theme.ERROR:Theme.PRIMARY);
                LinearLayout.LayoutParams stateParams=new LinearLayout.LayoutParams(-2,-2);stateParams.topMargin=TimeCardUi.dp(context(),10);dayCard.addView(stateBadge,stateParams);
            }
            if (hasDispute) {
                TextView conflictBadge = Theme.statusBadge(context(), "⚠ Conflict", Theme.ERROR);
                LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                conflictParams.topMargin = (int) (6 * density);
                dayCard.addView(conflictBadge, conflictParams);
                String note = day.optString("dispute_note", "");
                if (!note.isEmpty()) {
                    TextView noteView = new TextView(context());
                    noteView.setText(note);
                    noteView.setTextSize(12);
                    noteView.setTextColor(Theme.TEXT_SECONDARY);
                    LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    noteParams.topMargin = (int) (4 * density);
                    dayCard.addView(noteView, noteParams);
                }
            }
        }
    }

    private void addBadge(LinearLayout row, String text, int color, float density) {
        TextView badge = Theme.statusBadge(context(), text, color);
        badge.setTextColor(0xffffffff);badge.setBackground(TimeCardUi.background(context(),0x33ffffff,20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = (int) (6 * density);
        row.addView(badge, params);
    }
}
