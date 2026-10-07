package com.huynhdous.employeefield.trip;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.location.TrackingService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "Trip": the day's recorded GPS trail on a map (blue trail, green assigned territory, door outcomes), for any date. A tap on a trail dot
 * can start a door there. The map stretches to fill the screen so the Refresh / Where am I buttons sit just above the bottom, and
 * "Where am I" drops a green dot on the employee's fresh position. A line at the top says whether location tracking is on (with a button to
 * turn it on when the permission is missing).
 */
public final class TripTab extends TabModule {
    private LinearLayout content;
    private android.webkit.WebView mapView;
    private TextView status;
    private Button dateButton;
    private java.time.LocalDate date;
    private View buttonRow;
    private android.view.ViewTreeObserver.OnGlobalLayoutListener fitListener;
    private TextView trackingStatus;
    private Button enableTrackingButton;

    // The tracking line follows the tracking service every two seconds while the app is in front.
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshTracking = new Runnable() {
        public void run() {
            if (trackingStatus != null) trackingStatus.setText(TrackingService.status);
            if (enableTrackingButton != null) enableTrackingButton.setVisibility(Geo.hasLocationPermission(context()) ? View.GONE : View.VISIBLE);
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public String title() {
        return "My Trip";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_trip;
    }

    private String dateLabel() {
        return "Trip date: " + java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d").format(date);
    }

    @Override
    public void buildContent(LinearLayout content) {
        this.content = content;
        float density = density();

        // Whether the phone is reporting the work route, and a way to turn it on again if the permission was refused.
        trackingStatus = new TextView(context());
        trackingStatus.setText(TrackingService.status);
        trackingStatus.setTextSize(13);
        trackingStatus.setTextColor(Theme.TEXT_PRIMARY);
        trackingStatus.setPadding(0, 0, 0, (int) (6 * density));
        content.addView(trackingStatus);
        enableTrackingButton = Theme.filledButton(context(), "Enable location tracking", Theme.WARNING);
        enableTrackingButton.setVisibility(Geo.hasLocationPermission(context()) ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams enableParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        enableParams.bottomMargin = (int) (8 * density);
        content.addView(enableTrackingButton, enableParams);
        enableTrackingButton.setOnClickListener(v -> host().enableTracking());

        date = java.time.LocalDate.now();
        dateButton = Theme.filledButton(context(), dateLabel(), Theme.PRIMARY);
        content.addView(dateButton);
        dateButton.setOnClickListener(v -> {
            new android.app.DatePickerDialog(context(), (view, year, month, day) -> {
                date = java.time.LocalDate.of(year, month + 1, day);
                dateButton.setText(dateLabel());
                load();
            }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
        });

        LinearLayout card = new LinearLayout(context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));
        card.setBackground(Theme.cardBackground(context()));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = (int) (8 * density);
        content.addView(card, cardParams);

        status = new TextView(context());
        status.setTextSize(13);
        status.setTextColor(Theme.TEXT_SECONDARY);
        status.setPadding(0, 0, 0, (int) (8 * density));
        card.addView(status);

        mapView = new android.webkit.WebView(context());
        mapView.getSettings().setJavaScriptEnabled(true);
        mapView.setWebViewClient(new android.webkit.WebViewClient());
        // Only ever loaded with our own bundled HTML/JS via loadDataWithBaseURL, never remote/untrusted content, so exposing a JS interface is safe.
        mapView.addJavascriptInterface(new TripMap.Bridge(host()), "AndroidBridge");
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (320 * density));
        card.addView(mapView, mapParams);

        LinearLayout row = new LinearLayout(context());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (8 * density);
        content.addView(row, rowParams);
        LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        row.addView(refresh);
        refresh.setOnClickListener(v -> load());
        LinearLayout whereAmI = Theme.iconTextButton(context(), R.drawable.ic_tab_location, "Where am I", Theme.PRIMARY);
        LinearLayout.LayoutParams whereParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        whereParams.leftMargin = (int) (8 * density);
        row.addView(whereAmI, whereParams);
        whereAmI.setOnClickListener(v -> showWhereAmI());
        buttonRow = row;

        // The map takes whatever height is left, so Refresh sits just above the bottom of the screen (re-fitted whenever the layout changes).
        fitListener = this::fitMapToScreen;
        content.getViewTreeObserver().addOnGlobalLayoutListener(fitListener);
    }

    @Override
    public void onResume() {
        handler.post(refreshTracking);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshTracking);
    }

    @Override
    public void onShown() {
        load();
    }

    @Override
    public void onDetach() {
        handler.removeCallbacks(refreshTracking);
        trackingStatus = null;
        enableTrackingButton = null;
        if (content != null && fitListener != null && content.getViewTreeObserver().isAlive()) {
            content.getViewTreeObserver().removeOnGlobalLayoutListener(fitListener);
        }
        fitListener = null;
        content = null;
        if (mapView != null) {
            mapView.stopLoading();
            mapView.removeJavascriptInterface("AndroidBridge");
            if (mapView.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) mapView.getParent()).removeView(mapView);
            mapView.destroy();
        }
        mapView = null;
        status = null;
        dateButton = null;
        buttonRow = null;
        date = null;
    }

    private void load() {
        if (mapView == null) return;
        status.setText("Loading…");
        try {
            host().request("trip", new JSONObject().put("token", host().token()).put("date", date.toString()), null, this::render);
        } catch (Exception e) {
            status.setText("Unable to load trip.");
        }
    }

    private void render(JSONObject data) throws Exception {
        if (mapView == null) return;
        JSONArray points = data.getJSONArray("points");
        JSONArray scheduled = data.optJSONArray("scheduled");
        if (scheduled == null) scheduled = new JSONArray();
        JSONArray dispositions = data.optJSONArray("dispositions");
        if (dispositions == null) dispositions = new JSONArray();
        mapView.loadDataWithBaseURL("file:///android_asset/leaflet/", TripMap.buildHtml(points, scheduled, dispositions), "text/html", "UTF-8", null);
        status.setText(points.length() + " position" + (points.length() == 1 ? "" : "s") + " recorded on " + dateLabel().replace("Trip date: ", ""));
    }

    /** "Where am I": the map jumps to a fresh position and marks it (shared with the D2D screen's map). */
    private void showWhereAmI() {
        TripMap.showWhereAmI(context(), mapView);
    }

    /** Stretches the map so the buttons below it end just above the bottom of the screen: the room left in the scroll area, minus what
     * sits above the map and the card padding / button row / panel padding below it. */
    private void fitMapToScreen() {
        if (mapView == null || buttonRow == null || content == null || content.getVisibility() != View.VISIBLE) return;
        ScrollView scroll = null;
        for (android.view.ViewParent p = mapView.getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollView) { scroll = (ScrollView) p; break; }
        }
        if (scroll == null || scroll.getChildCount() == 0 || scroll.getHeight() == 0) return;
        View scrollContent = scroll.getChildAt(0);
        int top = 0;
        View v = mapView;
        while (v != scrollContent) {
            top += v.getTop();
            if (!(v.getParent() instanceof View)) return;
            v = (View) v.getParent();
        }
        float density = density();
        ViewGroup.MarginLayoutParams rowParams = (ViewGroup.MarginLayoutParams) buttonRow.getLayoutParams();
        int rowHeight = buttonRow.getHeight() > 0 ? buttonRow.getHeight() : (int) (48 * density);
        LinearLayout card = (LinearLayout) mapView.getParent();
        int below = card.getPaddingBottom() + rowParams.topMargin + rowHeight + scrollContent.getPaddingBottom();
        int available = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom() - top - below;
        int target = Math.max((int) (240 * density), Math.min(available, (int) (900 * density)));
        ViewGroup.LayoutParams params = mapView.getLayoutParams();
        if (Math.abs(params.height - target) > (int) density) {
            params.height = target;
            mapView.setLayoutParams(params);
        }
    }
}
