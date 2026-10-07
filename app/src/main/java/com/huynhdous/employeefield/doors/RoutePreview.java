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

/**
 * Read-only preview of today's already-built walk order for the territory the office scheduled the rep into -- shows the whole day on a
 * full-screen map before he leaves the house. Never builds anything on the phone; if the office hasn't set it up yet the server says so.
 */
final class RoutePreview {
    private final AppHost host;
    private final Activity activity;

    RoutePreview(DoorsTab tab) {
        this.host = tab.app();
        this.activity = host.activity();
    }

    /** Read-only preview of today's already-built walk order for whichever territory the office
     * scheduled this rep into — lets him see the whole day before he ever leaves the house. Never
     * generates or builds anything on the phone's behalf; if the office hasn't set it up yet, the
     * server just says so and this shows that message instead of a map. */
    void previewRoute() {
        previewRoute(null);
    }

    /** territoryId picks a specific territory when the rep has more than one scheduled today; left
     * null, the server auto-picks if there's only one, or hands back a `territories` list to choose
     * from instead of silently always showing whichever one happened to start earliest. */
    private void previewRoute(Integer territoryId) {
        try {
            JSONObject body = new JSONObject().put("token", host.token());
            if (territoryId != null) body.put("territory_id", (int) territoryId);
            host.request("telemapper/territory/my-route", body, null, r -> {
                org.json.JSONArray territories = r.optJSONArray("territories");
                if (territories != null && territories.length() > 0) {
                    showTerritoryPickerDialog(territories);
                    return;
                }
                org.json.JSONArray stops = r.getJSONArray("route");
                if (stops.length() == 0) {
                    String msg = r.isNull("message") ? "No route available yet." : r.getString("message");
                    new Popup.Builder(activity).setTitle("Preview Route").setMessage(msg).setPositiveButton("OK", null).show();
                    return;
                }
                String territoryName = r.isNull("territory_name") ? "Your territory" : r.getString("territory_name");
                org.json.JSONArray unrouted = r.optJSONArray("unrouted");
                if (unrouted == null) unrouted = new org.json.JSONArray();
                showRoutePreviewDialog(territoryName, stops, unrouted);
            });
        } catch (Exception ignored) {
        }
    }

    private void showTerritoryPickerDialog(org.json.JSONArray territories) throws Exception {
        String[] names = new String[territories.length()];
        int[] ids = new int[territories.length()];
        for (int i = 0; i < territories.length(); i++) {
            JSONObject t = territories.getJSONObject(i);
            names[i] = t.getString("territory_name");
            ids[i] = t.getInt("territory_id");
        }
        new Popup.Builder(activity)
                .setTitle("Which territory?")
                .setItems(names, (d, which) -> previewRoute(ids[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRoutePreviewDialog(String territoryName, org.json.JSONArray stops, org.json.JSONArray unrouted) throws Exception {
        float density = activity.getResources().getDisplayMetrics().density;
        int n = stops.length();

        // Same straight-line + average-walking-speed estimate the office's own route map shows.
        double totalMeters = 0;
        java.util.LinkedHashSet<String> streetOrder = new java.util.LinkedHashSet<>();
        String lastStreet = null;
        for (int i = 0; i < n; i++) {
            JSONObject s = stops.getJSONObject(i);
            String street = s.getString("street");
            if (!street.equals(lastStreet)) streetOrder.add(street);
            lastStreet = street;
            if (i > 0) {
                JSONObject prev = stops.getJSONObject(i - 1);
                double latAvg = Math.toRadians((s.getDouble("latitude") + prev.getDouble("latitude")) / 2);
                double dx = (s.getDouble("longitude") - prev.getDouble("longitude")) * Math.cos(latAvg) * 111320;
                double dy = (s.getDouble("latitude") - prev.getDouble("latitude")) * 111320;
                totalMeters += Math.sqrt(dx * dx + dy * dy);
            }
        }
        double miles = totalMeters / 1609.34;
        long walkMinutes = Math.round(miles / 3 * 60);
        long doorMinutes = (long) n * 2;
        double hours = Math.round((walkMinutes + doorMinutes) / 60.0 * 10) / 10.0;

        // A plain Dialog instead of Popup.Builder: AlertDialog wraps a custom view in its own
        // internal scroll container that measures it as wrap_content regardless of the view's own
        // requested layout params, which is exactly what was squashing the WebView to near-zero
        // height. A plain Dialog with setContentView() has no such wrapper.
        android.app.Dialog dialog = new android.app.Dialog(activity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(android.graphics.Color.WHITE);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerRow.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (8 * density));
        container.addView(headerRow);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText(territoryName);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titleCol.addView(title);
        TextView stats = new TextView(activity);
        stats.setText(n + " stops · " + streetOrder.size() + " streets · " + String.format(java.util.Locale.US, "%.2f", miles) + " mi · ~" + hours + " hr");
        stats.setTextSize(13);
        stats.setTextColor(Theme.TEXT_SECONDARY);
        titleCol.addView(stats);
        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeText = new TextView(activity);
        closeText.setText("Close");
        closeText.setTextColor(Theme.PRIMARY);
        closeText.setTypeface(closeText.getTypeface(), android.graphics.Typeface.BOLD);
        closeText.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        closeText.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(closeText);

        android.webkit.WebView webView = new android.webkit.WebView(activity);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new android.webkit.WebViewClient());
        webView.addJavascriptInterface(new RoutePreviewBridge(), "AndroidBridge");
        webView.loadDataWithBaseURL("file:///android_asset/leaflet/", buildRoutePreviewHtml(stops, unrouted), "text/html", "UTF-8", null);

        // Floating "My Location" button over the map -- jumps straight to where the rep actually is
        // instead of making him pan/zoom around looking for himself before he can even start.
        android.widget.FrameLayout mapStack = new android.widget.FrameLayout(activity);
        mapStack.addView(webView, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        Button locateButton = Theme.filledButton(activity, "📍 My Location", Theme.PRIMARY);
        android.widget.FrameLayout.LayoutParams locateParams = new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        locateParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        locateParams.setMargins(0, 0, (int) (16 * density), (int) (16 * density));
        mapStack.addView(locateButton, locateParams);
        container.addView(mapStack, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        locateButton.setElevation(6 * density);
        locateButton.setOnClickListener(v -> TripMap.showWhereAmI(activity, webView));

        dialog.setContentView(container);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // The default Dialog theme insets its window with margins and a rounded card background
            // -- strip both so the content actually reaches the edges instead of leaving a gap.
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
    }

    private String buildRoutePreviewHtml(org.json.JSONArray stops, org.json.JSONArray unrouted) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\"><style>html,body{height:100%;margin:0;font:14px sans-serif}body{display:flex;flex-direction:column}#map{flex:1;min-height:200px}.toolbar{padding:8px;display:flex;gap:8px;align-items:center}#play{padding:9px 16px;border:none;border-radius:20px;background:#ff8c42;color:#2a1200;font-weight:600}#caption{padding:0 8px;font-size:12px;color:#555;flex:1}#status{padding:4px 8px;font-size:12px;color:#666}</style></head><body>"
                + "<div class=\"toolbar\"><button id=\"play\">▶ Play</button><span id=\"caption\">Tap Play to preview the walk</span></div><div id=\"status\"></div><div id=\"map\"></div><script src=\"leaflet.js\"></script><script>" + Config.mapSettingsScript()
                + "var stops=" + stops.toString().replace("<", "\\u003c") + ";var unrouted=" + unrouted.toString().replace("<", "\\u003c") + ";"
                + "</script><script src=\"route-preview.js\"></script></body></html>";
    }

    /** Exposed to route-preview.js — opens a stop's real photo/street-level view in the device's own
     * Google Maps app (falls back to a browser tab), instead of trying to embed a photo viewer. */
    private final class RoutePreviewBridge {
        @android.webkit.JavascriptInterface
        public void openStreetView(double lat, double lon) {
            activity.runOnUiThread(() -> {
                try {
                    android.net.Uri gmmUri = android.net.Uri.parse("google.streetview:cbll=" + lat + "," + lon);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmUri);
                    intent.setPackage("com.google.android.apps.maps");
                    if (intent.resolveActivity(activity.getPackageManager()) != null) {
                        activity.startActivity(intent);
                    } else {
                        android.net.Uri webUri = android.net.Uri.parse(Config.streetViewWebUrl(lat, lon));
                        activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, webUri));
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "Unable to open Street View.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

}
