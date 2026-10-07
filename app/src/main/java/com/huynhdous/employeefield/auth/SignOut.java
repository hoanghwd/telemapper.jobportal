package com.huynhdous.employeefield.auth;

import android.app.Activity;
import android.widget.Toast;

import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.net.Requester;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.doors.DispositionQueue;
import com.huynhdous.employeefield.location.TrackingStarter;

import org.json.JSONObject;

/**
 * Signing out. It throws the login away on the server, and a finished door still waiting in the phone's queue can only be uploaded with a
 * valid login -- so anything waiting is sent first; if it can't go through right now (no connection), the rep is told and chooses, instead
 * of the door being quietly stranded (it would show "in progress" on the office's screen for hours).
 */
public final class SignOut {
    private final Activity activity;
    private final Session session;
    private final Requester requester;
    private final TrackingStarter tracking;
    private final Runnable showSignIn;
    private final DispositionQueue queue;

    public SignOut(Activity activity, Session session, Requester requester, TrackingStarter tracking, Runnable showSignIn) {
        this.activity = activity;
        this.session = session;
        this.requester = requester;
        this.tracking = tracking;
        this.showSignIn = showSignIn;
        this.queue = new DispositionQueue(activity);
    }

    /** The employee asked to sign out (ignored while a server call is running). */
    public void ifIdle() {
        if (!requester.isBusy()) begin();
    }

    public void begin() {
        final String old = session.token;
        final long employeeId = session.employeeId;
        if (queue.pendingCountForEmployee(employeeId) == 0) {
            finish(old);
            return;
        }
        requester.setBusy(true);
        Toast.makeText(activity, "Sending your finished doors before signing out…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            queue.drain(old, employeeId);
            activity.runOnUiThread(() -> {
                requester.setBusy(false);
                int left = queue.pendingCountForEmployee(employeeId);
                if (left == 0) {
                    finish(old);
                    return;
                }
                new Popup.Builder(activity)
                        .setCustomTitle(Theme.dialogTitle(activity, left + (left == 1 ? " door hasn't" : " doors haven't") + " uploaded yet", Theme.WARNING))
                        .setMessage("There's no connection right now. Your finished " + (left == 1 ? "door is" : "doors are") + " saved on this phone, but "
                                + "the office won't see " + (left == 1 ? "it" : "them") + " until you sign in again and the phone is online.\n\nStay signed in and try again once you have a signal.")
                        .setPositiveButton("Stay signed in", null)
                        .setNegativeButton("Sign out anyway", (d, w) -> finish(old))
                        .show();
            });
        }).start();
    }

    private void finish(String oldToken) {
        tracking.stop("Tracking is off.", true);
        showSignIn.run();
        new Thread(() -> {
            try {
                Api.post("logout", new JSONObject().put("token", oldToken));
            } catch (Exception ignored) {
            }
        }).start();
    }
}
