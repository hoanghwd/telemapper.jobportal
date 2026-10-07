package com.huynhdous.employeefield.auth;

import android.app.Activity;
import android.content.SharedPreferences;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.location.TrackingService;

/**
 * Shown once per employee on this phone, right after the first sign-in: says plainly that the work location is shared with the office,
 * and the employee must agree to go on (or sign out). The interval comes from the app settings.
 */
public final class TrackingNotice {
    private TrackingNotice() {
    }

    public static void confirm(Activity activity, long employeeId, Runnable proceed, Runnable signOut) {
        SharedPreferences prefs = activity.getSharedPreferences("employee_field", Activity.MODE_PRIVATE);
        final String key = "tracking_notice_confirmed_" + employeeId;
        if (prefs.getBoolean(key, false)) {
            proceed.run();
            return;
        }
        new Popup.Builder(activity)
                .setTitle(activity.getString(R.string.tracking_title))
                .setMessage(activity.getString(R.string.tracking_message, TrackingService.POLL_INTERVAL_MS / 1000))
                .setCancelable(false)
                .setPositiveButton("I understand", (d, w) -> {
                    prefs.edit().putBoolean(key, true).apply();
                    proceed.run();
                })
                .setNegativeButton("Sign out", (d, w) -> signOut.run())
                .show();
    }
}
