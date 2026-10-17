package com.huynhdous.employeefield.inbox;

import android.app.Activity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.trip.TripMap;
import org.json.JSONObject;

/** Compact destination preview using the app's bundled Leaflet and existing tile settings. */
final class OfferMap {
    private OfferMap() { }
    static String buildHtml(String source) {
        try {
            JSONObject point=new JSONObject(source);
            double latitude=point.getDouble("latitude"),longitude=point.getDouble("longitude");
            if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180) return null;
            String center="["+latitude+","+longitude+"]";
            return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                +"<link rel=\"stylesheet\" href=\"leaflet.css\"><style>html,body,#map{height:100%;width:100%;margin:0}html,body{overflow:hidden}#map{touch-action:none}</style></head>"
                +"<body><div id=\"map\" aria-label=\"Drop-off map\"></div><script src=\"leaflet.js\"></script><script>"
                +Config.mapSettingsScript()+"var center="+center+";var map=L.map('map',{scrollWheelZoom:false}).setView(center,14);"
                +"L.tileLayer(APP_CONFIG.tileUrl,{attribution:APP_CONFIG.attribution,maxZoom:19}).addTo(map);"
                +"L.circleMarker(center,{radius:9,color:'#fff',weight:3,fillColor:'#d71920',fillOpacity:1}).addTo(map);"
                +"</script></body></html>";
        } catch(Exception invalid) { return null; }
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    static WebView create(Activity activity,String source) {
        String html=buildHtml(source);if(html==null) return null;
        WebView map=new WebView(activity);map.setContentDescription("Drop-off map");
        map.getSettings().setJavaScriptEnabled(true);
        map.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){return true;}
        });
        TripMap.protectGestures(map);
        map.loadDataWithBaseURL("file:///android_asset/leaflet/",html,"text/html","UTF-8",null);
        return map;
    }
}
