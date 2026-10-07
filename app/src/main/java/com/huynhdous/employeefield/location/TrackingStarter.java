package com.huynhdous.employeefield.location;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.ui.Popup;

/**
 * Starts and stops the background work-location tracking ({@link TrackingService}) for the signed-in employee, asking for the location and
 * notification permissions first when they are missing.
 */
public final class TrackingStarter {
    private static final int REQUEST_PERMISSIONS = 30;

    private final Activity activity;
    private final Session session;

    public TrackingStarter(Activity activity, Session session) {
        this.activity = activity;
        this.session = session;
    }

    private boolean hasNotificationPermission() {
        return android.os.Build.VERSION.SDK_INT < 33
                || activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Start tracking if it is not already running for this sign-in. */
    public void enable() {
        if (session.token.isEmpty()) return;
        if (session.token.equals(TrackingService.activeToken)) return;
        if (Geo.hasLocationPermission(activity) && hasNotificationPermission()) {
            start();
            return;
        }
        new Popup.Builder(activity).setTitle("Work location reporting")
                .setMessage("Allow location and notifications to record your work route every " + (TrackingService.POLL_INTERVAL_MS / 1000) + " seconds, including while this app is minimized. Recruiters can view your daily route. A tracking notification stays visible; sign out or use its Stop action to end tracking.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSIONS);
                    else
                        activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
                }).setNegativeButton("Not now", (dialog, which) -> {
                    TrackingService.status = "Tracking is off. Location permission is required.";
                }).show();
    }

    /** The answer to the permission question asked by {@link #enable}. Returns false when the answer is not ours. */
    public boolean onPermissionResult(int requestCode) {
        if (requestCode != REQUEST_PERMISSIONS) return false;
        if (Geo.hasLocationPermission(activity) && hasNotificationPermission()) {
            start();
        } else {
            TrackingService.status = "Tracking is off. Enable Location and Notifications in app settings.";
            new Popup.Builder(activity).setMessage(TrackingService.status)
                    .setPositiveButton("App settings", (d, w) -> activity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + activity.getPackageName()))))
                    .setNegativeButton("Cancel", null).show();
        }
        return true;
    }

    private void start() {
        try {
            Intent intent = new Intent(activity, TrackingService.class).putExtra("token", session.token).putExtra("employee_id", session.employeeId).putExtra("expires_ms", session.expiresMs);
            activity.startForegroundService(intent);
        } catch (Exception e) {
            TrackingService.status = "Tracking could not start. Reopen the app and check permissions.";
        }
    }

    /** Stop tracking and show {@code statusText} in the log (no screen shows it any more); {@code forgetSignIn} also clears the token the service keeps. */
    public void stop(String statusText, boolean forgetSignIn) {
        activity.stopService(new Intent(activity, TrackingService.class));
        if (forgetSignIn) TrackingService.activeToken = "";
        TrackingService.status = statusText;
    }

    /** The token a still-running tracking service holds (so reopening the app can continue that sign-in), or "". */
    public static String runningToken() {
        return TrackingService.activeToken;
    }
}
