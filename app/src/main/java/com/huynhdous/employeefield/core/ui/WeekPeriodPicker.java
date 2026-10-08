package com.huynhdous.employeefield.core.ui;

import android.widget.LinearLayout;

/** Shared calendar-week choices for Schedule and Time Sheet. */
public final class WeekPeriodPicker {
    private WeekPeriodPicker() {}
    public static void show(android.app.Activity context, int previousWeeks, java.util.function.IntConsumer selected) {
        String[] choices = {"This week", "Last week", "Last 2 weeks", "Last 3 weeks", "Last N weeks…"};
        new android.app.AlertDialog.Builder(context).setTitle("Choose period")
                .setSingleChoiceItems(choices, previousWeeks <= 3 ? previousWeeks : 4, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 4) chooseCustomWeeks(context, previousWeeks, selected); else selected.accept(which);
                }).setNegativeButton("Cancel", null).show();
    }

    private static void chooseCustomWeeks(android.app.Activity context, int previousWeeks, java.util.function.IntConsumer selected) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Number of previous weeks");
        if (previousWeeks > 0) input.setText(String.valueOf(previousWeeks));
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setPadding(TimeCardUi.dp(context, 24), 0, TimeCardUi.dp(context, 24), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("Last N weeks").setMessage("Previous complete weeks, excluding this week.")
                .setView(wrapper).setPositiveButton("Apply", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int count;
            try { count = Integer.parseInt(input.getText().toString().trim()); } catch (NumberFormatException e) { count = 0; }
            if (count < 1 || count > com.huynhdous.employeefield.core.config.Config.TIMESHEET_MAX_WEEKS) {
                input.setError("Enter 1 to " + com.huynhdous.employeefield.core.config.Config.TIMESHEET_MAX_WEEKS + " weeks");
                return;
            }
            dialog.dismiss(); selected.accept(count);
        }));
        dialog.show();
    }

}
