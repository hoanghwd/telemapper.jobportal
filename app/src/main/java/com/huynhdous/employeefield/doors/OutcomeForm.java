package com.huynhdous.employeefield.doors;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.huynhdous.employeefield.core.ui.Theme;

import java.time.LocalDate;

/**
 * The outcome picker shared by every place a door's result is entered (finishing a door, correcting a finished one, fixing a door that
 * failed to upload): the outcome list, the "call back on" date that only shows for a callback, and the note. {@link #addTo} appends them to
 * a dialog column; the caller adds anything of its own before or after.
 */
final class OutcomeForm {
    private final RadioGroup group;
    private final LinearLayout callbackRow;
    private final Button dateButton;
    private final EditText noteField;
    private final LocalDate[] date = new LocalDate[1];

    /** @param initialStatus checked outcome (null = Sold); @param initialNote text for the note; @param initialCallbackDate ISO date or null (= tomorrow) */
    OutcomeForm(Activity activity, LinearLayout container, String initialStatus, String initialNote, String initialCallbackDate) {
        float density = activity.getResources().getDisplayMetrics().density;
        String status = initialStatus != null ? initialStatus : "sold";

        container.addView(sectionLabel(activity, "OUTCOME", Theme.TEXT_SECONDARY));

        group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.VERTICAL);
        android.content.res.ColorStateList radioTint = android.content.res.ColorStateList.valueOf(Theme.PRIMARY);
        for (int i = 0; i < DoorStatus.CODES.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setText(DoorStatus.LABELS[i]);
            rb.setTextColor(Theme.TEXT_PRIMARY);
            rb.setButtonTintList(radioTint);
            rb.setTag(DoorStatus.CODES[i]);
            LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rbParams.topMargin = (int) (4 * density);
            group.addView(rb, rbParams);
            if (DoorStatus.CODES[i].equals(status)) rb.setChecked(true);
        }
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        radioParams.topMargin = (int) (8 * density);
        container.addView(group, radioParams);

        callbackRow = new LinearLayout(activity);
        callbackRow.setOrientation(LinearLayout.VERTICAL);
        callbackRow.setVisibility("callback".equals(status) ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams callbackRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackRowParams.topMargin = (int) (12 * density);
        container.addView(callbackRow, callbackRowParams);
        callbackRow.addView(sectionLabel(activity, "CALL BACK ON", Theme.TEXT_SECONDARY));

        LocalDate start;
        try {
            start = initialCallbackDate != null ? LocalDate.parse(initialCallbackDate) : LocalDate.now().plusDays(1);
        } catch (Exception e) {
            start = LocalDate.now().plusDays(1);
        }
        date[0] = start;
        dateButton = Theme.filledButton(activity, start.toString(), Theme.PRIMARY);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = (int) (4 * density);
        callbackRow.addView(dateButton, dateParams);
        dateButton.setOnClickListener(v -> new android.app.DatePickerDialog(activity, (view, year, month, day) -> {
            date[0] = LocalDate.of(year, month + 1, day);
            dateButton.setText(date[0].toString());
        }, date[0].getYear(), date[0].getMonthValue() - 1, date[0].getDayOfMonth()).show());

        noteField = new EditText(activity);
        noteField.setHint("callback".equals(status) || "other".equals(status) ? "Reason" : "Note (optional)");
        if (initialNote != null) noteField.setText(initialNote);
        Theme.styleInput(noteField);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = (int) (16 * density);
        noteParams.bottomMargin = (int) (4 * density);
        container.addView(noteField, noteParams);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            RadioButton checked = g.findViewById(checkedId);
            String tag = checked != null ? (String) checked.getTag() : null;
            callbackRow.setVisibility("callback".equals(tag) ? View.VISIBLE : View.GONE);
            noteField.setHint("other".equals(tag) ? "Reason (required)" : "Note (optional)");
        });
    }

    /** A small bold caption above a group of fields ("OUTCOME", "PHOTO" ...). */
    static TextView sectionLabel(Activity activity, String text, int color) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextColor(color);
        label.setLetterSpacing(0.06f);
        return label;
    }

    /** The chosen outcome code (Sold when nothing is ticked). */
    String status() {
        int checkedId = group.getCheckedRadioButtonId();
        if (checkedId != -1) {
            RadioButton checked = group.findViewById(checkedId);
            if (checked != null && checked.getTag() != null) return (String) checked.getTag();
        }
        return "sold";
    }

    /** The note as typed (untrimmed) -- for keeping a draft while the camera is open. */
    String noteRaw() {
        return noteField.getText().toString();
    }

    String note() {
        return noteRaw().trim();
    }

    /** The call-back date as text, whether or not "callback" is chosen. */
    String dateText() {
        return date[0].toString();
    }

    /** The call-back date, or null unless the outcome is a callback. */
    String callbackDate() {
        return "callback".equals(status()) ? dateText() : null;
    }

    /** "Other" needs a reason. Marks the note field and returns false when it is missing. */
    boolean validate() {
        if ("other".equals(status()) && note().isEmpty()) {
            noteField.setError("Enter a reason");
            return false;
        }
        return true;
    }
}
