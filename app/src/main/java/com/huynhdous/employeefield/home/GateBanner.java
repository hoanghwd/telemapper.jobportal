package com.huynhdous.employeefield.home;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.core.tab.ScheduleGate;
import com.huynhdous.employeefield.core.ui.Theme;

/** "Confirm this week's schedule first": shown on every screen while the weekly-schedule gate is locked, hidden otherwise. */
final class GateBanner {
    final LinearLayout view;
    private final TextView text;
    private final Button button;

    GateBanner(Activity activity, Runnable openSchedule) {
        float density = activity.getResources().getDisplayMetrics().density;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xfffff7ed);
        background.setStroke((int) (1 * density), Theme.WARNING);
        background.setCornerRadius(10 * density);
        view.setBackground(background);

        TextView title = new TextView(activity);
        title.setText("🔒 Confirm this week's schedule first");
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(0xff9a3412);
        view.addView(title);

        text = new TextView(activity);
        text.setTextSize(13);
        text.setTextColor(0xff7c2d12);
        text.setPadding(0, (int) (4 * density), 0, 0);
        view.addView(text);

        button = Theme.filledButton(activity, "Open Schedule to confirm", Theme.WARNING);
        button.setOnClickListener(v -> openSchedule.run());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = (int) (10 * density);
        view.addView(button, buttonParams);
        view.setVisibility(View.GONE);
    }

    /** Show or hide the banner from the gate's state. On the Schedule screen the confirm button is right there, so the shortcut is hidden. */
    void update(ScheduleGate gate, boolean onScheduleScreen) {
        view.setVisibility(gate.isLocked() ? View.VISIBLE : View.GONE);
        if (!gate.isLocked()) return;
        String week = "";
        try {
            if (gate.week() != null) {
                java.time.LocalDate monday = java.time.LocalDate.parse(gate.week());
                week = " for the week of " + java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d").format(monday);
            }
        } catch (Exception ignored) {
        }
        text.setText("Everything is turned off until you confirm your schedule" + week + ". Open the Schedule, read it, then tap “Confirm this week's schedule”.");
        button.setVisibility(onScheduleScreen ? View.GONE : View.VISIBLE);
    }
}
