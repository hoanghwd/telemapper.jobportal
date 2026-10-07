package com.huynhdous.employeefield.trip;

import android.app.Activity;
import android.widget.Toast;

import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.tab.AppHost;

/**
 * The route map page used by both the Trip screen and the D2D screen's embedded map: the same trail, the same toolbar and the same door
 * outcome markers (trip-map.js draws the "dispositions" list when present), and the one bridge that lets a tap on the map start a door.
 */
public final class TripMap {
    private TripMap() {
    }

    /**
     * The map's toolbar: icon-only buttons so they all fit on one line. Each has a text label for screen readers (aria-label / title).
     * Where am I = map pin, whole route = four corners, assigned area = outline of an area, preview = play, refresh = circular arrow. The preview button stays hidden unless
     * the screen offers it (see {@link PreviewBridge}).
     */
    private static final String TOOLBAR_BUTTONS =
            "<button id=\"where\" aria-label=\"Where am I\" title=\"Where am I\"><svg viewBox=\"0 0 24 24\"><path d=\"M12 21s-6-5.2-6-10a6 6 0 0 1 12 0c0 4.8-6 10-6 10z\"/><circle cx=\"12\" cy=\"11\" r=\"2.2\"/></svg></button>"
            + "<button id=\"fit\" aria-label=\"Show the full route\" title=\"Show the full route\"><svg viewBox=\"0 0 24 24\"><path d=\"M4 9V4h5\"/><path d=\"M20 9V4h-5\"/><path d=\"M4 15v5h5\"/><path d=\"M20 15v5h-5\"/></svg></button>"
            + "<button id=\"area\" aria-label=\"Show the assigned area\" title=\"Show the assigned area\"><svg viewBox=\"0 0 24 24\"><path d=\"M12 3l8 6-3 11H7L4 9z\" stroke-dasharray=\"3 2\"/></svg></button>"
            + "<button id=\"preview\" aria-label=\"Preview the route\" title=\"Preview the route\" style=\"display:none\"><svg viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M10 8l6 4-6 4z\"/></svg></button>"
            + "<button id=\"refresh\" aria-label=\"Refresh\" title=\"Refresh\"><svg viewBox=\"0 0 24 24\"><path d=\"M20 12a8 8 0 1 1-2.3-5.7\"/><path d=\"M20 4v5h-5\"/></svg></button>";

    /** The HTML for the map page. The map script itself is bundled (assets/leaflet/trip-map.js); the tile server comes from the app settings. */
    public static String buildHtml(org.json.JSONArray points, org.json.JSONArray scheduled, org.json.JSONArray dispositions) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\"><style>html,body{height:100%;margin:0;font:14px sans-serif}body{display:flex;flex-direction:column}#map{flex:1;min-height:200px}.toolbar{padding:8px;display:flex;gap:8px}.toolbar button{display:flex;align-items:center;justify-content:center;width:48px;height:44px;padding:0}.toolbar button:disabled{opacity:.35}button{padding:8px;border:1px solid #2563eb;border-radius:5px;background:white;color:#2563eb}.toolbar svg{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}.start-icon span{display:block;background:#2563eb;color:white;border:3px solid white;border-radius:50%;width:32px;height:32px;text-align:center;line-height:32px;box-shadow:0 1px 5px #555}.leaflet-overlay-pane canvas,.leaflet-overlay-pane svg{max-width:none!important;max-height:none!important}#status{padding:4px 8px;font-size:12px}</style></head><body>"
                + "<div class=\"toolbar\">" + TOOLBAR_BUTTONS + "</div><div id=\"status\"></div><div id=\"map\"></div><script src=\"leaflet.js\"></script><script>" + Config.mapSettingsScript()
                + "var points=" + points.toString().replace("<", "\\u003c") + ";var scheduled=" + scheduled.toString().replace("<", "\\u003c") + ";var dispositions=" + dispositions.toString().replace("<", "\\u003c") + ";"
                + "</script><script src=\"trip-map.js\"></script></body></html>";
    }

    /**
     * The "Where am I" button for any screen that shows this map: a fresh, accurate position (the same reading check-in uses, not a stale
     * cached one), then the map jumps there and drops a green dot. Needs the location permission; says so if it is missing.
     */
    public static void showWhereAmI(Activity activity, android.webkit.WebView map) {
        if (map == null) return;
        if (!Geo.hasLocationPermission(activity)) {
            Toast.makeText(activity, "Grant location permission to use this.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, "Finding your location…", Toast.LENGTH_SHORT).show();
        Geo.fetchBestLocation(activity,
                () -> Toast.makeText(activity, "Unable to get your location. Move outdoors or near a window and try again.", Toast.LENGTH_LONG).show(),
                (lat, lon) -> map.evaluateJavascript("if(window.locateMe)window.locateMe(" + lat + "," + lon + ");", null));
    }

    /**
     * Exposed to the map page (trip-map.js) so tapping the trail dot closest to a house starts a door tied to that exact, already-recorded
     * position -- no separate device GPS read, no typed house number required. Runs off the WebView's JS thread, so it hops back to the UI
     * thread before touching any views or dialogs. Register it as "AndroidBridge". The page is only ever our own bundled HTML/JS loaded
     * with loadDataWithBaseURL, never remote content, so exposing this is safe.
     */
    public static class Bridge {
        private final Activity activity;
        private final AppHost host;
        private final android.webkit.WebView map;
        private final Runnable onRefresh;

        public Bridge(AppHost host, android.webkit.WebView map, Runnable onRefresh) {
            this.host = host;
            this.activity = host.activity();
            this.map = map;
            this.onRefresh = onRefresh;
        }

        /** The map's "Refresh" button: the screen reloads its data (and the map with it). */
        @android.webkit.JavascriptInterface
        public void refresh() {
            activity.runOnUiThread(onRefresh);
        }

        /** The map's "Where am I" button: a fresh position is read here (GPS lives on this side) and the page drops a dot on it. */
        @android.webkit.JavascriptInterface
        public void whereAmI() {
            activity.runOnUiThread(() -> showWhereAmI(activity, map));
        }

        @android.webkit.JavascriptInterface
        public void startDoor(long pointId, double lat, double lon) {
            activity.runOnUiThread(() -> host.startDoorAtPoint(pointId, lat, lon));
        }
    }
    /**
     * The bridge for a screen that also offers "Preview the route" in the map's toolbar. The page shows that button only when the bridge it
     * finds has {@code previewRoute} -- My Trip uses the plain {@link Bridge}, so it has no such button.
     */
    public static final class PreviewBridge extends Bridge {
        private final Activity screen;
        private final Runnable onPreview;

        public PreviewBridge(AppHost host, android.webkit.WebView map, Runnable onRefresh, Runnable onPreview) {
            super(host, map, onRefresh);
            this.screen = host.activity();
            this.onPreview = onPreview;
        }

        @android.webkit.JavascriptInterface
        public void previewRoute() {
            screen.runOnUiThread(onPreview);
        }
    }
}
