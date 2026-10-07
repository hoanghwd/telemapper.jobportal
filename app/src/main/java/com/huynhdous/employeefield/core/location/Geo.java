package com.huynhdous.employeefield.core.location;

import android.content.Context;
import android.content.pm.PackageManager;

import com.huynhdous.employeefield.core.config.Config;

/** Location helpers every feature needs: the straight-line distance between two points and whether the app may read the location. */
public final class Geo {
    private Geo() {
    }

    /** Great-circle distance in metres. */
    public static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * A fresh GPS reading, for moments where the position must be where the person is right now (check-in/out, "Where am I") -- not a
     * cached fix from however long ago, so this never trusts getLastKnownLocation(). The passive provider just replays whatever fix some
     * OTHER app last requested, anywhere, any time, so it is never used. And even among the real providers the FIRST to answer isn't
     * necessarily the most accurate: network/fused fixes can return in well under a second with 200-500 m of error, which was rejecting a
     * genuinely on-site check-in as "too far". So keep listening (up to the timeout in the app settings) for the best accuracy seen, and
     * settle early only once a fix is actually good (30 m). Runs the callbacks on the UI thread.
     */
    public static void fetchBestLocation(Context context, Runnable onFailure, java.util.function.BiConsumer<Double, Double> onLocation) {
        fetchBestFix(context, onFailure, fix -> onLocation.accept(fix.getLatitude(), fix.getLongitude()));
    }

    /** Same fresh reading as {@link #fetchBestLocation}, but hands over the whole fix (so the caller can also use its accuracy). */
    public static void fetchBestFix(Context context, Runnable onFailure, java.util.function.Consumer<android.location.Location> onFix) {
        if (!hasLocationPermission(context)) {
            onFailure.run();
            return;
        }
        android.location.LocationManager manager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            java.util.List<String> providers = new java.util.ArrayList<>(manager.getProviders(true));
            providers.remove(android.location.LocationManager.PASSIVE_PROVIDER);
            if (providers.isEmpty()) {
                onFailure.run();
                return;
            }
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] resolved = {false};
            final android.location.Location[] best = {null};
            final float GOOD_ACCURACY_METERS = 30f;
            android.location.LocationListener listener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (resolved[0]) return;
                    if (best[0] == null || location.getAccuracy() < best[0].getAccuracy()) best[0] = location;
                    if (location.getAccuracy() <= GOOD_ACCURACY_METERS) {
                        resolved[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        manager.removeUpdates(this);
                        onFix.accept(location);
                    }
                }

                @Override
                public void onProviderDisabled(String provider) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                }
            };
            // Ask every remaining provider (GPS is often slow or unavailable indoors; network location can fill in).
            for (String provider : providers) manager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (resolved[0]) return;
                resolved[0] = true;
                manager.removeUpdates(listener);
                if (best[0] != null) {
                    onFix.accept(best[0]);
                } else {
                    onFailure.run();
                }
            }, Config.CHECKIN_LOCATION_TIMEOUT_MS);
        } catch (SecurityException e) {
            onFailure.run();
        }
    }

    /**
     * A quick position for when "roughly where I am" is enough: the newest fix any provider already has, else one single GPS update.
     * Calls back with (null, null) when there is no permission, no fix, or Location is switched off. Runs on the UI thread.
     */
    public static void fetchQuickLocation(Context context, java.util.function.BiConsumer<Double, Double> onLocation) {
        if (!hasLocationPermission(context)) {
            onLocation.accept(null, null);
            return;
        }
        android.location.LocationManager manager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            android.location.Location best = null;
            for (String provider : manager.getProviders(true)) {
                android.location.Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null) {
                onLocation.accept(best.getLatitude(), best.getLongitude());
                return;
            }
            manager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    onLocation.accept(location.getLatitude(), location.getLongitude());
                }

                @Override
                public void onProviderDisabled(String provider) {
                    onLocation.accept(null, null);
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                }
            }, android.os.Looper.getMainLooper());
        } catch (SecurityException e) {
            onLocation.accept(null, null);
        }
    }

    /** True when the app may read the phone's location (precise or approximate). */
    public static boolean hasLocationPermission(Context context) {
        boolean precise = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean approx = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return precise || approx;
    }
}
