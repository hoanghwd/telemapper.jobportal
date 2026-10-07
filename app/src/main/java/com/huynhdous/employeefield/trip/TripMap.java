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

    /** The HTML for the map page. The map script itself is bundled (assets/leaflet/trip-map.js); the tile server comes from the app settings. */
    public static String buildHtml(org.json.JSONArray points, org.json.JSONArray scheduled, org.json.JSONArray dispositions) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\"><style>html,body{height:100%;margin:0;font:14px sans-serif}body{display:flex;flex-direction:column}#map{flex:1;min-height:200px}.toolbar{padding:8px;display:flex;gap:8px}button{padding:8px;border:1px solid #2563eb;border-radius:5px;background:white;color:#2563eb}.start-icon span{display:block;background:#2563eb;color:white;border:3px solid white;border-radius:50%;width:32px;height:32px;text-align:center;line-height:32px;box-shadow:0 1px 5px #555}.leaflet-overlay-pane canvas,.leaflet-overlay-pane svg{max-width:none!important;max-height:none!important}#status{padding:4px 8px;font-size:12px}</style></head><body>"
                + "<div class=\"toolbar\"><button id=\"start\">▶ Start</button><button id=\"fit\">Show full route</button><button id=\"area\">Assigned area</button></div><div id=\"status\"></div><div id=\"map\"></div><script src=\"leaflet.js\"></script><script>" + Config.mapSettingsScript()
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
    public static final class Bridge {
        private final Activity activity;
        private final AppHost host;

        public Bridge(AppHost host) {
            this.host = host;
            this.activity = host.activity();
        }

        @android.webkit.JavascriptInterface
        public void startDoor(long pointId, double lat, double lon) {
            activity.runOnUiThread(() -> host.startDoorAtPoint(pointId, lat, lon));
        }
    }
}
