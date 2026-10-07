package com.huynhdous.employeefield.core.net;

import android.app.Activity;
import android.view.View;
import android.widget.Button;

import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Popup;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * The app's server calls from a screen: one call at a time (a second tap is ignored while one is running), a busy spinner on the button
 * that started it, the answer delivered on the UI thread, and a pop-up for any error. A refused sign-in (401/403) ends the session.
 */
public final class Requester {
    /** What happens to the app when the server says the sign-in is over, or an answer cannot be used. */
    public interface Listener {
        void sessionExpired();

        void unusableAnswer();
    }

    private final Activity activity;
    private final Session session;
    private final Listener listener;
    private boolean busy;

    public Requester(Activity activity, Session session, Listener listener) {
        this.activity = activity;
        this.session = session;
        this.listener = listener;
    }

    public boolean isBusy() {
        return busy;
    }

    /** Lets other long jobs (sending queued doors before sign-out) block new calls the same way. */
    public void setBusy(boolean value) {
        busy = value;
    }

    private void setButtonBusy(Button button, boolean busyState) {
        button.setEnabled(!busyState);
        Object tag = button.getTag();
        if (tag instanceof View) ((View) tag).setVisibility(busyState ? View.VISIBLE : View.GONE);
    }

    public void request(String action, JSONObject body, Button button, AppHost.Result result, boolean independentRead, AppHost.ProblemHandler onProblem) {
        if (!independentRead && busy) return;
        if (!independentRead) busy = true;
        final String requestToken = session.token;
        if (button != null) setButtonBusy(button, true);
        new Thread(() -> {
            JSONObject response = null;
            String error = null;
            int code = 0;
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + action).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = stream) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (bytes.size() + n > 8_388_608) throw new IOException("RESPONSE_TOO_LARGE");
                        bytes.write(buffer, 0, n);
                    }
                }
                response = new JSONObject(bytes.toString("UTF-8"));
                if (code < 200 || code >= 300 || !response.optBoolean("success"))
                    error = response.optString("message", "Unable to complete request.");
            } catch (Exception e) {
                error = "RESPONSE_TOO_LARGE".equals(e.getMessage())
                        ? "That response was too large to load. Try a narrower date range."
                        : "Unable to connect. Check your internet connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            final JSONObject data = response;
            final String problem = error;
            final int status = code;
            activity.runOnUiThread(() -> {
                if (!independentRead) busy = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (independentRead && !java.util.Objects.equals(requestToken, session.token)) return;
                if (button != null) setButtonBusy(button, false);
                if (problem != null) {
                    if (status == 401 || status == 403) listener.sessionExpired();
                    if (onProblem != null && onProblem.handle(status, problem)) return;
                    new Popup.Builder(activity).setTitle("Employee sign-in").setMessage(problem).setPositiveButton("OK", null).show();
                    return;
                }
                try {
                    result.accept(data);
                } catch (Exception e) {
                    listener.unusableAnswer();
                }
            });
        }).start();
    }
}
