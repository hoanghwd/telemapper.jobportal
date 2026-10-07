package com.huynhdous.employeefield.auth;

import android.app.Activity;
import android.widget.Button;

import com.huynhdous.employeefield.core.net.Requester;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;

import org.json.JSONObject;

/**
 * The way in: sign-in form -> (replace a temporary password) -> ask the server who this is -> the one-time tracking notice -> {@code onReady}
 * (the app builds the home screen). Also used to carry on a sign-in that is still valid when the app is reopened.
 */
public final class AuthFlow implements AuthHost {
    private final Activity activity;
    private final Session session;
    private final Requester requester;
    private final SignOut signOut;
    private final Runnable onReady;

    public AuthFlow(Activity activity, Session session, Requester requester, SignOut signOut, Runnable onReady) {
        this.activity = activity;
        this.session = session;
        this.requester = requester;
        this.signOut = signOut;
        this.onReady = onReady;
    }

    public void showSignIn() {
        SignInScreen.show(this);
    }

    /** Find out who the current token belongs to, then go on to the home screen. */
    public void verify() throws Exception {
        requester.request("me", new JSONObject().put("token", session.token), null, r -> {
            session.employeeId = r.getLong("employee_id");
            session.expiresMs = java.time.Instant.parse(r.getString("expires_utc").replace(' ', 'T') + "Z").toEpochMilli();
            session.programCode = r.isNull("program_code") ? null : r.optString("program_code", null);
            session.employeeName = r.getString("employee_name");
            session.username = r.getString("username");
            TrackingNotice.confirm(activity, session.employeeId, onReady, signOut::begin);
        }, false, null);
    }

    // ---- AuthHost: what the sign-in screens may ask ----
    @Override
    public Activity activity() {
        return activity;
    }

    @Override
    public boolean isBusy() {
        return requester.isBusy();
    }

    @Override
    public String token() {
        return session.token;
    }

    @Override
    public void request(String action, JSONObject body, Button button, AppHost.Result result) {
        requester.request(action, body, button, result, false, null);
    }

    @Override
    public void signedIn(String token, boolean mustChangePassword) throws Exception {
        session.token = token;
        if (mustChangePassword) ChangePasswordScreen.show(this);
        else verify();
    }

    @Override
    public void passwordChanged(String token) throws Exception {
        session.token = token;
        verify();
    }

    @Override
    public void signOut() {
        signOut.begin();
    }
}
