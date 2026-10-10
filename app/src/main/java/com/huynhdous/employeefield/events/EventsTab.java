package com.huynhdous.employeefield.events;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * S2S reps' Events screen, in two sections taken from the Weekly Schedule: "Today's Events" (the events they have to go to today, with
 * the hours) and "Weekly Events" (every event they are scheduled at this week, with the days and hours).
 */
public final class EventsTab extends TabModule {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

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
        container = new LinearLayout(context());
        container.setOrientation(LinearLayout.VERTICAL);
        content.addView(container, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        showMessage("Loading events…");
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
            host().request("telemapper/retail-event/my-events", new JSONObject().put("token", host().token()), null, this::render);
        } catch (Exception ignored) {
        }
    }

    private void showMessage(String text) {
        container.removeAllViews();
        TextView message = new TextView(context());
        message.setText(text);
        message.setTextSize(13);
        message.setTextColor(Theme.TEXT_SECONDARY);
        container.addView(message);
    }

    private void render(JSONObject response) throws Exception {
        if (container == null) return;
        container.removeAllViews();
        LocalDate today = LocalDate.parse(response.optString("today_date", LocalDate.now().toString()));
        addSection("Today's Events", DAY.format(today), response.optJSONArray("today"), "Nothing to go to today.", true, true);
        String weekRange = "";
        if (response.has("week_start") && response.has("week_end")) {
            weekRange = DAY.format(LocalDate.parse(response.getString("week_start"))) + " – " + DAY.format(LocalDate.parse(response.getString("week_end")));
        }
        addSection("Weekly Events", weekRange, response.optJSONArray("events"), "No events in your schedule this week.", false, false);
    }

    /** One titled section: the heading (with Refresh on the first one), what it covers, then one card per event or a short empty note. */
    private void addSection(String heading, String subtitle, JSONArray events, String emptyText, boolean withRefresh, boolean todayOnly) throws Exception {
        float density = density();
        LinearLayout headerRow = new LinearLayout(context());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (container.getChildCount() > 0) headerParams.topMargin = (int) (22 * density);
        container.addView(headerRow, headerParams);

        LinearLayout titles = new LinearLayout(context());
        titles.setOrientation(LinearLayout.VERTICAL);
        headerRow.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = new TextView(context());
        title.setText(heading);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(Theme.TEXT_PRIMARY);
        titles.addView(title);
        if (!subtitle.isEmpty()) {
            TextView sub = new TextView(context());
            sub.setText(subtitle);
            sub.setTextSize(13);
            sub.setTextColor(Theme.TEXT_SECONDARY);
            titles.addView(sub);
        }
        if (withRefresh) {
            LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
            headerRow.addView(refresh);
            refresh.setOnClickListener(v -> load());
        }

        if (events == null || events.length() == 0) {
            TextView empty = new TextView(context());
            empty.setText(emptyText);
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            emptyParams.topMargin = (int) (10 * density);
            container.addView(empty, emptyParams);
            return;
        }
        for (int i = 0; i < events.length(); i++) addCard(events.getJSONObject(i), todayOnly);
    }

    private void addCard(JSONObject event, boolean todayOnly) throws Exception {
        float density = density();
        LocalDate startDate = LocalDate.parse(event.getString("start_date"));
        LocalDate endDate = LocalDate.parse(event.getString("end_date"));

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

        // When the rep has to be there: their own shifts for this event (today's hours, or each day of the week).
        JSONArray shifts = event.optJSONArray("shifts");
        for (int i = 0; shifts != null && i < shifts.length(); i++) {
            JSONObject shift = shifts.getJSONObject(i);
            String hours = LocalTime.parse(shift.getString("start")).format(TIME) + " – " + LocalTime.parse(shift.getString("end")).format(TIME);
            TextView shiftText = new TextView(context());
            shiftText.setText(todayOnly ? hours : DAY.format(LocalDate.parse(shift.getString("date"))) + " · " + hours);
            shiftText.setTextSize(14);
            shiftText.setTypeface(shiftText.getTypeface(), android.graphics.Typeface.BOLD);
            shiftText.setTextColor(Theme.PRIMARY);
            LinearLayout.LayoutParams shiftParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            shiftParams.topMargin = (int) (3 * density);
            card.addView(shiftText, shiftParams);
        }

        TextView dateText = new TextView(context());
        dateText.setText("Event runs " + DAY.format(startDate) + " – " + DAY.format(endDate));
        dateText.setTextSize(12);
        dateText.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = (int) (4 * density);
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
