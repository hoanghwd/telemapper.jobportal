package com.huynhdous.employeefield.jobs;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.trip.TripMap;

import org.json.JSONObject;

/**
 * The Jobs screen's map page: the same map, toolbar and look as My Trip, drawn by assets/leaflet/jobs-map.js from the server's list of jobs
 * the driver may take. Tapping a red pin offers "Take this job", which reaches the screen through {@link Bridge#takeJob}.
 */
final class JobsMap {
    private JobsMap() {
    }

    /** The HTML for the map page; {@code available} is the answer of "dispatch/available". */
    static String buildHtml(JSONObject available) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\"><style>" + TripMap.PAGE_CSS
                + "html,body{overflow:hidden;overscroll-behavior:none}#map{touch-action:none;overscroll-behavior:contain}.stop-icon span{display:block;color:#fff;border:3px solid #fff;border-radius:50%;width:30px;height:30px;text-align:center;line-height:24px;font-weight:700;box-shadow:0 1px 5px #555}.pin-icon svg{display:block;filter:drop-shadow(0 1px 3px #555)}"
                + "</style></head><body><div class=\"toolbar\">" + TripMap.TOOLBAR_BUTTONS + "</div><div id=\"status\"></div><div id=\"map\"></div>"
                + "<script src=\"leaflet.js\"></script><script>" + Config.mapSettingsScript()
                + "var jobsData=" + available.toString().replace("<", "\\u003c") + ";</script><script src=\"jobs-map.js\"></script></body></html>";
    }

    /** What the map page may ask of the screen: the toolbar's Refresh and Where am I (as on My Trip), and a tap on "Take this job". Register it as "AndroidBridge". */
    static final class Bridge extends TripMap.Bridge {
        private final Activity screen;
        private final java.util.function.LongConsumer onTake;

        Bridge(AppHost host, WebView map, Runnable onRefresh, java.util.function.LongConsumer onTake) {
            super(host, map, onRefresh);
            this.screen = host.activity();
            this.onTake = onTake;
        }

        @JavascriptInterface
        public void takeJob(long requestId) {
            screen.runOnUiThread(() -> onTake.accept(requestId));
        }
    }
}
