package com.huynhdous.employeefield.auth;

import android.app.Activity;
import android.widget.Button;

import com.huynhdous.employeefield.core.tab.AppHost;

import org.json.JSONObject;

/** What the sign-in screens may ask of the app around them (the activity implements this). */
public interface AuthHost {
    Activity activity();

    /** A server call is in flight: ignore taps until it ends. */
    boolean isBusy();

    /** The token from the sign-in answer (needed to change the temporary password). */
    String token();

    void request(String action, JSONObject body, Button button, AppHost.Result result);

    /** The server accepted the sign-in; the employee must first replace a temporary password when {@code mustChangePassword}. */
    void signedIn(String token, boolean mustChangePassword) throws Exception;

    /** The new password was saved; {@code token} is the session to continue with. */
    void passwordChanged(String token) throws Exception;

    void signOut();
}
