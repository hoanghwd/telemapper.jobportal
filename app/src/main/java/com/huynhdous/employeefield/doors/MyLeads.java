package com.huynhdous.employeefield.doors;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.Tabs;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.schedule.DayClock;
import com.huynhdous.employeefield.trip.TripMap;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/** "My Leads": the callbacks the rep promised (Come Back Another Time), with a button to mark each one done. */
final class MyLeads {
    private final DoorsTab tab;
    private final AppHost host;
    private final Activity activity;

    private final LinearLayout container;

    MyLeads(DoorsTab tab, LinearLayout parent) {
        this.tab = tab;
        this.host = tab.app();
        this.activity = host.activity();
        float density = activity.getResources().getDisplayMetrics().density;

        TextView title = new TextView(activity);
        title.setText("My Leads");
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = (int) (18 * density);
        parent.addView(title, titleParams);

        container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = (int) (8 * density);
        parent.addView(container, containerParams);
    }

    void load() {
        if (container == null) return;
        try {
            host.request("telemapper/followup/my-leads", new JSONObject().put("token", host.token()), null, r -> renderMyLeads(r.getJSONArray("leads")));
        } catch (Exception ignored) {
        }
    }

    private void renderMyLeads(org.json.JSONArray leads) throws Exception {
        container.removeAllViews();
        if (leads.length() == 0) {
            TextView empty = new TextView(activity);
            empty.setText("No pending callbacks.");
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            container.addView(empty);
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d");
        for (int i = 0; i < leads.length(); i++) {
            JSONObject lead = leads.getJSONObject(i);
            int followupId = lead.getInt("followup_id");
            boolean overdue = lead.optBoolean("is_overdue", false);
            java.time.LocalDate callbackDate = java.time.LocalDate.parse(lead.getString("callback_date"));

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            card.setBackground(Theme.cardBackground(activity));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) (8 * density);
            container.addView(card, cardParams);

            LinearLayout topRow = new LinearLayout(activity);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(topRow);

            TextView addressText = new TextView(activity);
            addressText.setText(lead.getString("address"));
            addressText.setTextSize(15);
            addressText.setTypeface(addressText.getTypeface(), android.graphics.Typeface.BOLD);
            addressText.setTextColor(Theme.TEXT_PRIMARY);
            topRow.addView(addressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView dateText = new TextView(activity);
            dateText.setText(dayFormat.format(callbackDate));
            dateText.setTextSize(13);
            dateText.setTypeface(dateText.getTypeface(), android.graphics.Typeface.BOLD);
            dateText.setTextColor(overdue ? Theme.ERROR : Theme.PRIMARY);
            topRow.addView(dateText);

            if (overdue) {
                TextView overdueBadge = Theme.statusBadge(activity, "Overdue", Theme.ERROR);
                LinearLayout.LayoutParams overdueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                overdueParams.topMargin = (int) (4 * density);
                card.addView(overdueBadge, overdueParams);
            }

            String note = lead.optString("note", "");
            if (!note.isEmpty()) {
                TextView noteText = new TextView(activity);
                noteText.setText(note);
                noteText.setTextSize(13);
                noteText.setTextColor(Theme.TEXT_SECONDARY);
                LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                noteParams.topMargin = (int) (4 * density);
                card.addView(noteText, noteParams);
            }

            Button doneButton = Theme.filledButton(activity, "Mark done", Theme.SUCCESS);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            doneParams.topMargin = (int) (8 * density);
            card.addView(doneButton, doneParams);
            doneButton.setOnClickListener(v -> completeLead(followupId));
        }
    }

    private void completeLead(int followupId) {
        try {
            host.request("telemapper/followup/complete", new JSONObject().put("token", host.token()).put("followup_id", followupId), null, r -> load());
        } catch (Exception ignored) {
        }
    }

}
