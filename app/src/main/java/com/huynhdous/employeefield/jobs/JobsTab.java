package com.huynhdous.employeefield.jobs;

import android.graphics.Typeface;
import android.view.Gravity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "Jobs": a map of the delivery jobs still waiting for a driver, for a driver who is on his way somewhere near a drop-off and decides to earn
 * extra. He taps a red pin (or "Take this job" in the list under the map), confirms, and the job becomes his order at once. The office decides
 * whether this is on and how far from him a job may be (Office Settings > Logistics); the server checks every rule again when he takes one.
 */
public final class JobsTab extends TabModule {
    private static final int MAP_HEIGHT_DP = 320;

    private TextView status;
    private WebView mapView;
    private LinearLayout list;
    private JSONObject data = new JSONObject();
    private long readVersion;
    private boolean claiming;

    @Override
    public String title() {
        return "Jobs";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_jobs;
    }

    /** Jobs are for delivery and service drivers, not for the door-to-door or in-store programs. */
    @Override
    public boolean isAvailableFor(String programCode) {
        return !"D2D".equals(programCode) && !"S2S".equals(programCode);
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();
        status = new TextView(context());
        status.setTextSize(13);
        status.setTextColor(Theme.TEXT_PRIMARY);
        status.setText("Loading jobs…");
        content.addView(status);

        LinearLayout card = new LinearLayout(context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));
        card.setBackground(Theme.cardBackground(context()));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = (int) (8 * density);
        content.addView(card, cardParams);

        mapView = new WebView(context());
        mapView.getSettings().setJavaScriptEnabled(true);
        mapView.setWebViewClient(new WebViewClient());
        // Only ever loaded with our own bundled HTML/JS via loadDataWithBaseURL, never remote content, so exposing a JS interface is safe.
        mapView.addJavascriptInterface(new JobsMap.Bridge(host(), mapView, this::load, this::takeFromMap), "AndroidBridge");
        card.addView(mapView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (MAP_HEIGHT_DP * density)));

        list = new LinearLayout(context());
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onShown() {
        load();
    }

    @Override
    public void onDetach() {
        if (mapView != null) {
            mapView.stopLoading();
            mapView.removeJavascriptInterface("AndroidBridge");
            if (mapView.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) mapView.getParent()).removeView(mapView);
            mapView.destroy();
        }
        mapView = null;
        status = null;
        list = null;
    }

    private void load() {
        if (mapView == null) return;
        long version = ++readVersion;
        status.setText("Loading jobs…");
        try {
            host().request("dispatch/available", new JSONObject().put("token", host().token()), null, response -> {
                if (version != readVersion || mapView == null) return;
                render(response);
            }, true, (code, message) -> {
                if (version == readVersion && status != null) status.setText("Unable to load jobs. Open this screen again to retry.");
                return true;
            });
        } catch (Exception e) {
            status.setText("Unable to load jobs.");
        }
    }

    private void render(JSONObject response) {
        data = response;
        JSONArray jobs = response.optJSONArray("jobs");
        if (jobs == null) jobs = new JSONArray();
        status.setText(JobText.statusLine(response.optBoolean("can_take"), jobs.length(), response.optInt("holding"), response.optInt("max_orders"), response.optString("message")));
        mapView.loadDataWithBaseURL("file:///android_asset/leaflet/", JobsMap.buildHtml(response), "text/html", "UTF-8", null);
        list.removeAllViews();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null) addCard(job);
        }
    }

    private void addCard(JSONObject job) {
        float density = density();
        LinearLayout card = new LinearLayout(context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
        card.setBackground(Theme.cardBackground(context()));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = (int) (8 * density);
        list.addView(card, cardParams);

        card.addView(text(JobText.orderNumber(job.optString("title")), 15, true, Theme.TEXT_PRIMARY));
        card.addView(text("Pick up: " + job.optString("pickup_address"), 13, false, Theme.TEXT_PRIMARY));
        card.addView(text("Drop off: " + job.optString("dropoff_address"), 13, false, Theme.TEXT_PRIMARY));
        card.addView(text(JobText.tripLine(job.optDouble("miles_to_pickup"), job.optInt("minutes_to_pickup"), job.optDouble("trip_miles"), job.optInt("trip_minutes")), 12, false, Theme.TEXT_SECONDARY));
        String earn = JobText.earnLine(job.optString("pay", ""));
        if (!earn.isEmpty()) card.addView(text(earn, 14, true, Theme.SUCCESS));
        boolean holding = data.optInt("holding") > 0;
        if (holding) card.addView(text(JobText.extraLine(job.optDouble("extra_miles"), job.optInt("extra_minutes")), 12, false, Theme.TEXT_SECONDARY));

        LinearLayout row = new LinearLayout(context());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout show = Theme.iconTextButton(context(), R.drawable.ic_tab_location, "Show on map", Theme.PRIMARY);
        show.setOnClickListener(v -> {
            if (mapView != null) mapView.evaluateJavascript("if(window.focusJob)window.focusJob(" + job.optLong("request_id") + ");", null);
        });
        row.addView(show);
        if (data.optBoolean("can_take")) {
            LinearLayout take = Theme.iconTextButton(context(), R.drawable.ic_job_complete, "Take this job", Theme.SUCCESS);
            take.setOnClickListener(v -> confirm(job));
            row.addView(take);
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (6 * density);
        card.addView(row, rowParams);
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(context());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    /** A tap on "Take this job" in the map's popup: the job is looked up in what the screen last loaded. */
    private void takeFromMap(long requestId) {
        JSONArray jobs = data.optJSONArray("jobs");
        for (int i = 0; jobs != null && i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && job.optLong("request_id") == requestId) {
                confirm(job);
                return;
            }
        }
    }

    private void confirm(JSONObject job) {
        String message = JobText.confirmMessage(job.optString("pickup_address"), job.optString("dropoff_address"), job.optDouble("miles_to_pickup"), job.optInt("minutes_to_pickup"), job.optDouble("trip_miles"), job.optInt("trip_minutes"), job.optString("pay", ""),
                data.optInt("holding") > 0, job.optDouble("extra_miles"), job.optInt("extra_minutes"));
        new Popup.Builder(context()).setTitle("Take " + JobText.orderNumber(job.optString("title")) + "?").setMessage(message)
                .setPositiveButton("Take this job", (dialog, which) -> claim(job)).setNegativeButton("Not now", null).show();
    }

    private void claim(JSONObject job) {
        if (claiming) return;
        claiming = true;
        ++readVersion;   // ignore a list read that started before this
        try {
            host().request("dispatch/claim", new JSONObject().put("token", host().token()).put("request_id", job.optLong("request_id")), null, response -> {
                claiming = false;
                host().refreshDispatch();
                load();
                new Popup.Builder(context()).setTitle("It's yours").setMessage("The job is now one of your orders. Use the orders box at the top to start it.")
                        .setPositiveButton("OK", null).show();
            }, false, (code, message) -> {
                claiming = false;
                if (code != 409 && code != 403) return false;
                new Popup.Builder(context()).setTitle("Can't take this job").setMessage(message).setPositiveButton("OK", null).show();
                load();
                return true;
            });
        } catch (Exception e) {
            claiming = false;
            throw new IllegalStateException(e);
        }
    }
}
