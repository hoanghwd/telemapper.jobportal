package com.huynhdous.employeefield.schedule;

import android.app.Activity;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.core.ui.TimeCardUi;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One day's time clock on the Schedule screen: today's Start work / Lunch / Break / End buttons, the day's punch timeline, "Edit times",
 * and "Flag an issue with this day". Past days show the same record without punch buttons. The column ({@code container}) is redrawn from the
 * server's punches every time something changes.
 */
public final class DayClock {
    private static final String[] TIME_FIELD_TYPES = {"start_work", "end_work", "start_lunch", "end_lunch", "start_break", "end_break"};
    private static final String[] TIME_FIELD_LABELS = {"Clock In", "Clock Out", "Lunch Start", "Lunch End", "Break Start", "Break End"};

    private final AppHost host;
    private final LinearLayout container;
    private final String dateKey;
    private final boolean withinWindow;
    private final java.time.LocalTime nextStart;
    private final java.time.ZoneId zone;
    private final boolean isToday;
    private final HoursCard hours;
    private boolean accountingLocked;

    DayClock(AppHost host, LinearLayout container, String dateKey, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday, HoursCard hours) {
        this.host = host;
        this.container = container;
        this.dateKey = dateKey;
        this.withinWindow = withinWindow;
        this.nextStart = nextStart;
        this.zone = zone;
        this.isToday = isToday;
        this.hours = hours;
    }

    void setAccountingLocked(boolean locked) { accountingLocked = locked; }

    private Activity context() {
        return host.activity();
    }

    /** Where the employee is in the day, from the punches so far: "none", "work", "lunch", "break" or "ended". */
    public static String finalDayState(JSONArray events) throws Exception {
        String state = null;
        boolean everStarted = false;
        for (int i = 0; i < events.length(); i++) {
            switch (events.getJSONObject(i).getString("event_type")) {
                case "start_work": state = "work"; everStarted = true; break;
                case "start_lunch": state = "lunch"; break;
                case "end_lunch": state = "work"; break;
                case "start_break": state = "break"; break;
                case "end_break": state = "work"; break;
                case "end_work": state = "ended"; break;
            }
        }
        if (state == null) return everStarted ? "ended" : "none";
        return state;
    }

    /** Fetch this day's punches and redraw. */
    void load() {
        load(null);
    }

    /** Same, and run {@code afterRendered} once it is drawn (used to load the days one after another). If the fetch itself fails the
     * app shows its error and {@code afterRendered} does not run -- as before. */
    void load(Runnable afterRendered) { load(afterRendered, () -> true); }

    void load(Runnable afterRendered, java.util.function.BooleanSupplier active) {
        try {
            host.request("timeclock/day", new JSONObject().put("token", host.token()).put("work_date", dateKey), null, r -> {
                if (!active.getAsBoolean()) return;
                if (r.has("locked")) accountingLocked = r.getBoolean("locked");
                render(r.getJSONArray("events"), r.optJSONObject("dispute"));
                if (afterRendered != null) afterRendered.run();
            }, true, (status, problem) -> {
                if (active.getAsBoolean()) {
                    container.removeAllViews();
                    container.addView(TimeCardUi.text(context(), "Unable to load time entries. Tap Refresh to retry.", 12, Theme.ERROR, false));
                    if (afterRendered != null) afterRendered.run();
                }
                return true;
            });
        } catch (Exception ignored) {
            if (afterRendered != null) afterRendered.run();
        }
    }

    private void render(JSONArray events, JSONObject dispute) throws Exception {
        if (isToday && hours != null) hours.renderDayTotal(events, zone);

        String finalState = finalDayState(events);
        boolean workStarted = finalState.equals("work");
        boolean workEnded = finalState.equals("ended");
        boolean onLunch = finalState.equals("lunch");
        boolean onBreak = finalState.equals("break");

        container.removeAllViews();
        float density = context().getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.rightMargin = (int) (8 * density);

        renderTimeline(events, density);

        boolean hadLunch = false;
        for (int i = 0; i < events.length(); i++) {
            if (events.getJSONObject(i).getString("event_type").equals("end_lunch")) { hadLunch = true; break; }
        }

        if (workEnded) {
            TextView doneText = Theme.statusBadge(context(), "✓ Work day complete", Theme.SUCCESS);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            doneParams.topMargin = (int) (4 * density);
            doneParams.bottomMargin = (int) (8 * density);
            container.addView(doneText, doneParams);
        } else if (isToday && !accountingLocked) {
            LinearLayout buttonRow = new LinearLayout(context());
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = (int) (4 * density);
            rowParams.bottomMargin = (int) (8 * density);
            if (onLunch) {
                addButton(buttonRow, btnParams, "End lunch", 0xffea580c, "end_lunch");
            } else if (onBreak) {
                addButton(buttonRow, btnParams, "End paid break", 0xffea580c, "end_break");
            } else if (workStarted) {
                if (!hadLunch) addButton(buttonRow, btnParams, "Start lunch", Theme.PRIMARY, "start_lunch");
                addButton(buttonRow, btnParams, "Start break", Theme.PRIMARY, "start_break");
                addButton(buttonRow, btnParams, "End work", 0xffb91c1c, "end_work");
            } else if (withinWindow) {
                addButton(buttonRow, btnParams, "Start work", 0xff16a34a, "start_work");
            } else {
                java.time.format.DateTimeFormatter clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
                Button disabledButton = smallButton(nextStart != null ? "Starts at " + clock.format(nextStart) : "Working window has ended", Theme.NEUTRAL);
                disabledButton.setEnabled(false);
                buttonRow.addView(disabledButton, btnParams);
            }
            container.addView(buttonRow, rowParams);
        } else {
            TextView notClockedIn = new TextView(context());
            notClockedIn.setText(workStarted || onLunch || onBreak ? "In progress — no clock-out recorded" : "No punches recorded");
            notClockedIn.setTextColor(Theme.NEUTRAL);
            notClockedIn.setTextSize(13);
            notClockedIn.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            container.addView(notClockedIn);
        }

        if (dispute != null) {
            TextView conflictBadge = Theme.statusBadge(context(), "⚠ CONFLICT FLAGGED", Theme.ERROR);
            LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            conflictParams.topMargin = (int) (10 * density);
            container.addView(conflictBadge, conflictParams);

            TextView flagged = new TextView(context());
            flagged.setText(dispute.optString("note", ""));
            flagged.setTextColor(Theme.TEXT_SECONDARY);
            flagged.setTextSize(13);
            flagged.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            container.addView(flagged);
        } else if (!accountingLocked) {
            TextView flagLink = new TextView(context());
            flagLink.setText("Report an issue");
            flagLink.setMinHeight(TimeCardUi.dp(context(),48));
            flagLink.setGravity(Gravity.CENTER_VERTICAL);
            flagLink.setTextColor(Theme.NEUTRAL);
            flagLink.setTextSize(13);
            flagLink.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
            flagLink.setOnClickListener(v -> showFlagDialog());
            container.addView(flagLink);
        }

        if (accountingLocked) return;

        TextView editLink = new TextView(context());
        editLink.setText("Edit time entries  →");
        editLink.setMinHeight(TimeCardUi.dp(context(),48));
        editLink.setGravity(Gravity.CENTER_VERTICAL);
        editLink.setTypeface(null,android.graphics.Typeface.BOLD);
        editLink.setTextColor(Theme.PRIMARY);
        editLink.setTextSize(13);
        editLink.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        editLink.setOnClickListener(v -> {
            try {
                showEditTimesDialog(events);
            } catch (Exception ignored) {
            }
        });
        container.addView(editLink);
    }

    private void renderTimeline(JSONArray events, float density) throws Exception {
        java.time.format.DateTimeFormatter clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(zone);
        java.util.List<Object[]> segments = new java.util.ArrayList<>();
        String state = null;
        java.time.Instant segStart = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.getJSONObject(i);
            String type = ev.getString("event_type");
            java.time.Instant t = java.time.Instant.parse(ev.getString("event_utc").replace(' ', 'T') + "Z");
            switch (type) {
                case "start_work":
                    state = "work"; segStart = t;
                    break;
                case "start_lunch":
                    if (state != null && segStart != null) segments.add(new Object[]{state, segStart, t});
                    state = "lunch"; segStart = t;
                    break;
                case "end_lunch":
                    if (segStart != null) segments.add(new Object[]{"lunch", segStart, t});
                    state = "work"; segStart = t;
                    break;
                case "start_break":
                    if (state != null && segStart != null) segments.add(new Object[]{state, segStart, t});
                    state = "break"; segStart = t;
                    break;
                case "end_break":
                    if (segStart != null) segments.add(new Object[]{"break", segStart, t});
                    state = "work"; segStart = t;
                    break;
                case "end_work":
                    if (segStart != null) segments.add(new Object[]{"work", segStart, t});
                    state = null; segStart = null;
                    break;
            }
        }
        if (state != null && segStart != null) segments.add(new Object[]{state, segStart, null});
        if (segments.isEmpty()) return;

        long totalWorkMinutes = 0;
        for (Object[] seg : segments) {
            if (seg[0].equals("work")) {
                java.time.Instant start = (java.time.Instant) seg[1];
                java.time.Instant end = seg[2] != null ? (java.time.Instant) seg[2] : java.time.Instant.now();
                totalWorkMinutes += java.time.Duration.between(start, end).toMinutes();
            }
        }
        TimeCardUi.divider(container);
        LinearLayout totalRow = new LinearLayout(context());
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.addView(TimeCardUi.text(context(), "Hours worked", 12, Theme.NEUTRAL, false),new LinearLayout.LayoutParams(0,-2,1));
        totalRow.addView(TimeCardUi.text(context(),String.format(java.util.Locale.US,"%dh %02dm",totalWorkMinutes/60,totalWorkMinutes%60),22,Theme.TEXT_PRIMARY,true));
        container.addView(totalRow);
        for (Object[] seg : segments) {
            String kind = (String) seg[0];
            java.time.Instant start = (java.time.Instant) seg[1], end = (java.time.Instant) seg[2];
            String label = kind.equals("work") ? "Work" : kind.equals("lunch") ? "Lunch" : "Paid break";
            String duration = "Live";
            if(end!=null) {
                long minutes = java.time.Duration.between(start,end).toMinutes();
                duration = minutes >= 60 ? String.format(java.util.Locale.US,"%dh %02dm",minutes/60,minutes%60) : minutes+" min";
            }
            TimeCardUi.segment(container,label,clock.format(start)+" – "+(end!=null?clock.format(end):"Now"),duration,kind.equals("work")?Theme.PRIMARY:0xffb86b0c);
        }
    }

    // ---------------------------------------------------------------- edit times

    private void showEditTimesDialog(JSONArray events) throws Exception {
        if (accountingLocked) return;
        float density = context().getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        java.time.LocalTime[] originals = new java.time.LocalTime[6];
        int[] originalEventIds = new int[6];
        for (int i = 0; i < 6; i++) {
            String utc = null;
            int eventId = 0;
            for (int j = 0; j < events.length(); j++) {
                JSONObject ev = events.getJSONObject(j);
                if (ev.getString("event_type").equals(TIME_FIELD_TYPES[i])) {
                    utc = ev.getString("event_utc");
                    eventId = ev.optInt("event_id", 0);
                }
            }
            originalEventIds[i] = eventId;
            originals[i] = utc != null ? java.time.LocalTime.from(java.time.Instant.parse(utc.replace(' ', 'T') + "Z").atZone(zone)) : null;
        }

        java.time.LocalTime[][] holders = new java.time.LocalTime[6][];
        ScrollView scroll = new ScrollView(context());
        LinearLayout form = new LinearLayout(context());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, pad);
        scroll.addView(form);
        for (int i = 0; i < 6; i++) {
            holders[i] = new java.time.LocalTime[]{originals[i]};
            addTimeField(form, TIME_FIELD_LABELS[i], holders[i], density);
        }
        Popup editDialog = new Popup.Builder(context())
                .setTitle(isToday ? "Edit today's times" : "Edit this day's times")
                .setView(scroll)
                .setPositiveButton("Continue", null)
                .setNegativeButton("Cancel", null)
                .show();
        editDialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            java.util.List<Integer> changed = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                java.time.LocalTime cur = holders[i][0];
                if (cur != null && !cur.equals(originals[i])) changed.add(i);
            }
            if (changed.isEmpty()) {
                editDialog.dismiss();
                return;
            }
            showTimeEditReasonDialog(editDialog, holders, originalEventIds, changed);
        });
    }

    private void showTimeEditReasonDialog(Popup editDialog, java.time.LocalTime[][] holders,
                                          int[] originalEventIds, java.util.List<Integer> changed) {
        EditText reason = new EditText(context());
        reason.setHint("Reason for the change (required)");
        reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        reason.setMinLines(2);
        Theme.styleInput(reason);
        LinearLayout form = new LinearLayout(context());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context().getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);
        form.addView(reason, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Popup reasonDialog = new Popup.Builder(context())
                .setTitle("Reason for changing times")
                .setMessage("Enter a reason before saving your time changes.")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Back", null)
                .show();
        Button save = reasonDialog.getButton(Popup.BUTTON_POSITIVE);
        save.setEnabled(false);
        boolean[] saving = {false};
        reason.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                save.setEnabled(!saving[0] && !s.toString().trim().isEmpty() && s.toString().trim().codePointCount(0, s.toString().trim().length()) <= 500);
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
        save.setOnClickListener(v -> {
            String note = reason.getText().toString().trim();
            if (note.isEmpty()) {
                reason.setError("Enter a reason for the change.");
                return;
            }
            if (saving[0]) return;
            if (note.codePointCount(0, note.length()) > 500) {
                reason.setError("Use a reason of up to 500 characters.");
                return;
            }
            saving[0] = true;
            reason.setEnabled(false);
            reasonDialog.getButton(Popup.BUTTON_NEGATIVE).setEnabled(false);
            reasonDialog.setCancelable(false);
            reasonDialog.setCanceledOnTouchOutside(false);
            Runnable release = () -> {
                saving[0] = false;
                reason.setEnabled(true);
                save.setEnabled(!reason.getText().toString().trim().isEmpty());
                reasonDialog.getButton(Popup.BUTTON_NEGATIVE).setEnabled(true);
                reasonDialog.setCancelable(true);
                reasonDialog.setCanceledOnTouchOutside(true);
            };
            try {
                JSONObject body = timeEditRequest(holders, originalEventIds, changed, note);
                host.request("timeclock/correct", body, save, r -> {
                    release.run();
                    reasonDialog.dismiss();
                    editDialog.dismiss();
                    load();
                }, false, (status, problem) -> {
                    release.run();
                    reason.setError(problem);
                    return status != 401 && status != 403;
                });
                // Requester leaves the button enabled when another mutation already owns the slot.
                if (save.isEnabled()) release.run();
            } catch (Exception e) {
                release.run();
                reason.setError("Unable to save. Your changes are still here; please try again.");
            }
        });
        reason.requestFocus();
        if (reasonDialog.getWindow() != null) reasonDialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void addTimeField(LinearLayout form, String label, java.time.LocalTime[] holder, float density) {
        TextView labelView = new TextView(context());
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(Theme.NEUTRAL);
        labelView.setPadding(0, (int) (10 * density), 0, (int) (2 * density));
        form.addView(labelView);
        Button button = new Button(context());
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.field_background);
        button.setTextColor(Theme.TEXT_PRIMARY);
        button.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        updateTimeFieldButton(button, holder[0]);
        button.setOnClickListener(v -> {
            java.time.LocalTime current = holder[0] != null ? holder[0] : java.time.LocalTime.of(9, 0);
            new android.app.TimePickerDialog(context(), (view, hour, minute) -> {
                holder[0] = java.time.LocalTime.of(hour, minute);
                updateTimeFieldButton(button, holder[0]);
            }, current.getHour(), current.getMinute(), false).show();
        });
        form.addView(button);
    }

    private void updateTimeFieldButton(Button button, java.time.LocalTime time) {
        button.setText(time != null ? java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(time) : "Not set");
    }

    private JSONObject timeEditRequest(java.time.LocalTime[][] holders, int[] originalEventIds,
                                       java.util.List<Integer> changed, String note) throws Exception {
        JSONArray corrections = new JSONArray();
        for (int fieldIndex : changed) {
            java.time.LocalTime time = holders[fieldIndex][0];
            JSONObject correction = new JSONObject()
                    .put("event_type", TIME_FIELD_TYPES[fieldIndex])
                    .put("corrected_time", String.format(java.util.Locale.US, "%02d:%02d", time.getHour(), time.getMinute()));
            if (originalEventIds[fieldIndex] != 0) correction.put("original_event_id", originalEventIds[fieldIndex]);
            corrections.put(correction);
        }
        return new JSONObject().put("token", host.token()).put("work_date", dateKey)
                .put("reason", note).put("corrections", corrections);
    }

    // ---------------------------------------------------------------- punching in and out

    private void addButton(LinearLayout row, LinearLayout.LayoutParams params, String label, int color, String eventType) {
        Button button = smallButton(label, color);
        row.addView(button, params);
        button.setOnClickListener(v -> punch(eventType, button));
    }

    private Button smallButton(String label, int color) {
        Button button = Theme.filledButton(context(), label, color);
        button.setTextSize(12);
        float density = context().getResources().getDisplayMetrics().density;
        button.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        return button;
    }

    private void punch(String eventType, Button button) {
        if (accountingLocked) return;
        // Going to lunch or on break with a door still open means that visit (and its photo/outcome)
        // never gets closed out until he's back -- ask him to confirm instead of letting it slip silently.
        if ("start_lunch".equals(eventType) || "start_break".equals(eventType)) {
            confirmNoActiveDoorThenPunch(eventType, button);
            return;
        }
        doPunch(eventType, button);
    }

    private void confirmNoActiveDoorThenPunch(String eventType, Button button) {
        String label = "start_break".equals(eventType) ? "break" : "lunch";
        try {
            host.request("telemapper/disposition/active", new JSONObject().put("token", host.token()), null, r -> {
                if (!r.isNull("active")) {
                    String address = r.getJSONObject("active").optString("address", "a door");
                    new Popup.Builder(context())
                            .setTitle("Finish that door first?")
                            .setMessage("You have an ongoing visit at " + address + ". Did you finish that door yet?")
                            .setPositiveButton("Yes, go to " + label, (d, w) -> doPunch(eventType, button))
                            .setNegativeButton("Go back", null)
                            .show();
                } else {
                    confirmNoOpenS2sSiteThenPunch(eventType, button, label);
                }
            }, true, null);
        } catch (Exception ignored) {
        }
    }

    /** Same guard as the D2D door check above, but for S2S: going to lunch/break while still
     * checked into a worksite (no checkout yet) leaves that visit open with no record of him
     * actually stepping away, same blind spot a door left open has. */
    private void confirmNoOpenS2sSiteThenPunch(String eventType, Button button, String label) {
        try {
            host.request("telemapper/arrival/assignments", new JSONObject().put("token", host.token()), null, r -> {
                String openSite = null;
                JSONArray assignments = r.optJSONArray("assignments");
                if (assignments != null) {
                    for (int i = 0; i < assignments.length(); i++) {
                        JSONObject a = assignments.getJSONObject(i);
                        if (a.optBoolean("checked_in", false) && !a.optBoolean("checked_out", false)) {
                            openSite = a.optString("site_name", "a worksite");
                            break;
                        }
                    }
                }
                if (openSite != null) {
                    String site = openSite;
                    new Popup.Builder(context())
                            .setTitle("Check out first?")
                            .setMessage("You're still checked in at " + site + ". Did you check out before going on " + label + "?")
                            .setPositiveButton("Yes, go to " + label, (d, w) -> doPunch(eventType, button))
                            .setNegativeButton("Go back", null)
                            .show();
                } else {
                    doPunch(eventType, button);
                }
            }, true, null);
        } catch (Exception ignored) {
        }
    }

    private void doPunch(String eventType, Button button) {
        if (accountingLocked) return;
        try {
            host.request("timeclock/punch", new JSONObject().put("token", host.token()).put("event_type", eventType).put("work_date", dateKey), button,
                    r -> load(), false, null);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- flagging a day

    private void showFlagDialog() {
        if (accountingLocked) return;
        EditText input = new EditText(context());
        input.setHint("What's wrong with this day's record?");
        input.setMinLines(2);
        Theme.styleInput(input);
        new Popup.Builder(context())
                .setTitle("Flag a conflict")
                .setView(input)
                .setPositiveButton("Submit", (d, w) -> {
                    String note = input.getText().toString().trim();
                    if (!note.isEmpty()) flag(note);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void flag(String note) {
        if (accountingLocked) return;
        try {
            host.request("timeclock/dispute", new JSONObject().put("token", host.token()).put("work_date", dateKey).put("note", note), null, r -> load());
        } catch (Exception ignored) {
        }
    }
}
