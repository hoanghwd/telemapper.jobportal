package com.huynhdous.employeefield.events;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

/** S2S reps' "My Events": the retail events a manager has staffed them to -- name, dates, location and what to promote. */
public final class EventsTab extends TabModule {
    private LinearLayout container;

    @Override
    public String title() {
        return "Events";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_events;
    }

    /** Retail events are staffed by S2S reps; D2D reps have the Programs screen instead. */
    @Override
    public boolean isAvailableFor(String programCode) {
        return "S2S".equals(programCode);
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();
        LinearLayout headerRow = new LinearLayout(context());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(headerRow);
        TextView heading = new TextView(context());
        heading.setText("My Events");
        heading.setTextSize(18);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        heading.setTextColor(Theme.TEXT_PRIMARY);
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        headerRow.addView(refresh);
        refresh.setOnClickListener(v -> load());

        container = new LinearLayout(context());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = (int) (12 * density);
        content.addView(container, containerParams);
    }

    @Override
    public void onShown() {
        load();
    }

    @Override
    public void onDetach() {
        container = null;
    }

    private void load() {
        if (container == null) return;
        try {
            host().request("telemapper/retail-event/my-events", new JSONObject().put("token", host().token()), null, r -> render(r.getJSONArray("events")));
        } catch (Exception ignored) {
        }
    }

    private void render(JSONArray events) throws Exception {
        if (container == null) return;
        container.removeAllViews();
        if (events.length() == 0) {
            TextView empty = new TextView(context());
            empty.setText("No events assigned right now.");
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            container.addView(empty);
            return;
        }
        float density = density();
        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d");
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            java.time.LocalDate startDate = java.time.LocalDate.parse(event.getString("start_date"));
            java.time.LocalDate endDate = java.time.LocalDate.parse(event.getString("end_date"));

            LinearLayout card = new LinearLayout(context());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            card.setBackground(Theme.cardBackground(context()));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) (8 * density);
            container.addView(card, cardParams);

            TextView nameText = new TextView(context());
            nameText.setText(event.getString("event_name"));
            nameText.setTextSize(15);
            nameText.setTypeface(nameText.getTypeface(), android.graphics.Typeface.BOLD);
            nameText.setTextColor(Theme.TEXT_PRIMARY);
            card.addView(nameText);

            TextView dateText = new TextView(context());
            dateText.setText(dayFormat.format(startDate) + " – " + dayFormat.format(endDate));
            dateText.setTextSize(13);
            dateText.setTextColor(Theme.PRIMARY);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dateParams.topMargin = (int) (2 * density);
            card.addView(dateText, dateParams);

            String locationName = event.optString("location_name", "");
            String locationAddress = event.optString("location_address", "");
            if (!locationName.isEmpty() || !locationAddress.isEmpty()) {
                TextView locationText = new TextView(context());
                locationText.setText(locationName.isEmpty() ? locationAddress : locationName + (locationAddress.isEmpty() ? "" : " — " + locationAddress));
                locationText.setTextSize(13);
                locationText.setTextColor(Theme.TEXT_SECONDARY);
                LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                locationParams.topMargin = (int) (6 * density);
                card.addView(locationText, locationParams);
            }

            String promo = event.optString("promo_description", "");
            if (!promo.isEmpty()) {
                TextView promoText = new TextView(context());
                promoText.setText(promo);
                promoText.setTextSize(13);
                promoText.setTextColor(Theme.TEXT_PRIMARY);
                LinearLayout.LayoutParams promoParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                promoParams.topMargin = (int) (8 * density);
                card.addView(promoText, promoParams);
            }
        }
    }
}
