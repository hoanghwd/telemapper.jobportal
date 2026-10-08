package com.huynhdous.employeefield.core.config;

import com.huynhdous.employeefield.BuildConfig;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Every address and tunable number the app uses, in one place. The values are NOT typed here: they come from
 * {@code app/app-config.properties} (see that file for what each one means), which the build turns into {@link BuildConfig}.
 * Code asks for them through this class so nothing else hard-codes a URL or a limit.
 */
public final class Config {
    private Config() {
    }

    // Server
    /** The employee API, ending in a slash. */
    public static final String API_BASE_URL = BuildConfig.API_BASE_URL;

    // Maps and address lookup
    public static final String MAP_TILE_URL = BuildConfig.MAP_TILE_URL;
    public static final String MAP_ATTRIBUTION = BuildConfig.MAP_ATTRIBUTION;
    private static final String GEOCODE_REVERSE_URL = BuildConfig.GEOCODE_REVERSE_URL;
    private static final String STREETVIEW_WEB_URL = BuildConfig.STREETVIEW_WEB_URL;

    // Location tracking
    public static final int TRACKING_POSITIONS_PER_MINUTE = BuildConfig.TRACKING_POSITIONS_PER_MINUTE;

    // Photos
    public static final int PHOTO_MAX_EDGE_PX = BuildConfig.PHOTO_MAX_EDGE_PX;
    public static final int PHOTO_JPEG_QUALITY = BuildConfig.PHOTO_JPEG_QUALITY;

    // Doors and check-in
    public static final double DOOR_PHOTO_PROXIMITY_METERS = BuildConfig.DOOR_PHOTO_PROXIMITY_METERS;
    public static final int CHECKIN_MAX_STORE_PHOTOS = BuildConfig.CHECKIN_MAX_STORE_PHOTOS;
    public static final long CHECKIN_LOCATION_TIMEOUT_MS = BuildConfig.CHECKIN_LOCATION_TIMEOUT_SECONDS * 1000L;

    public static final int TIMESHEET_MAX_WEEKS = BuildConfig.TIMESHEET_MAX_WEEKS;

    // Work hours
    public static final int WORK_DAILY_TARGET_MINUTES = BuildConfig.WORK_DAILY_TARGET_MINUTES;
    public static final int WORK_WEEKLY_TARGET_MINUTES = BuildConfig.WORK_WEEKLY_TARGET_MINUTES;

    // Meal-break reminder
    public static final long MEAL_CHECK_INTERVAL_MS = BuildConfig.MEAL_CHECK_INTERVAL_MINUTES * 60_000L;
    public static final double MEAL_WINDOW_START_HOURS = BuildConfig.MEAL_WINDOW_START_HOURS;
    public static final double MEAL_WINDOW_END_HOURS = BuildConfig.MEAL_WINDOW_END_HOURS;

    /** The street-address lookup for a GPS position. */
    public static String reverseGeocodeUrl(double lat, double lon) {
        return GEOCODE_REVERSE_URL + "?format=json&lat=" + lat + "&lon=" + lon + "&zoom=18&addressdetails=1";
    }

    /** Where to open Street View in a browser for a position. */
    public static String streetViewWebUrl(double lat, double lon) {
        return String.format(Locale.US, STREETVIEW_WEB_URL, Double.toString(lat), Double.toString(lon));
    }

    /**
     * The map settings for the map pages (Leaflet) loaded inside the app. Put the result in a {@code <script>} before the map script:
     * the page reads {@code APP_CONFIG.tileUrl} and {@code APP_CONFIG.attribution}, so the tile server is set here and nowhere else.
     */
    public static String mapSettingsScript() {
        try {
            JSONObject settings = new JSONObject().put("tileUrl", MAP_TILE_URL).put("attribution", MAP_ATTRIBUTION);
            return "var APP_CONFIG=" + settings.toString().replace("<", "\\u003c") + ";";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
