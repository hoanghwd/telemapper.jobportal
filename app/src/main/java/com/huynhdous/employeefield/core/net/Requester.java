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
 * Screen reads can run together; mutations are serialized with uploads across activity recreation.
 * Answers return on the UI thread only for the current session. A refused sign-in (401/403) ends it.
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
    private final ConnectionFactory connections;

    public interface ConnectionFactory {
        HttpsURLConnection open(String action) throws IOException;
    }
    private boolean busy;
    private static final java.util.Set<String> READS = new java.util.HashSet<>(java.util.Arrays.asList(
            "schedule", "trip", "timesheet/week", "timeclock/day", "telemapper/arrival/assignments",
            "telemapper/disposition/active", "telemapper/followup/my-leads", "telemapper/program/my-program",
            "telemapper/retail-event/my-events", "telemapper/territory/my-route"));

    public Requester(Activity activity, Session session, Listener listener) {
        this(activity, session, listener, action -> (HttpsURLConnection) new URL(Config.API_BASE_URL + action).openConnection());
    }

    public Requester(Activity activity, Session session, Listener listener, ConnectionFactory connections) {
        this.activity = activity;
        this.session = session;
        this.listener = listener;
        this.connections = connections;
    }

    public boolean isBusy() {
        return busy || com.huynhdous.employeefield.core.session.SessionWork.isBusy(session.token);
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
        final boolean parallelRead = independentRead || READS.contains(action);
        if (!parallelRead && isBusy()) {
            new Popup.Builder(activity).setTitle("Please wait")
                    .setMessage("A request is still finishing. Please try again shortly.").setPositiveButton("OK", null).show();
            return;
        }
        final String requestToken = session.token;
        final com.huynhdous.employeefield.core.session.SessionWork.Lease work;
        if (!parallelRead && !requestToken.isEmpty() && session.employeeId > 0) {
            work = com.huynhdous.employeefield.core.session.SessionWork.begin(requestToken, session.employeeId);
            if (work == null) return;
        } else {
            work = null;
        }
        if (!parallelRead) busy = true;
        if (button != null) setButtonBusy(button, true);
        new Thread(() -> {
            JSONObject response = null;
            String error = null;
            int code = 0;
            HttpsURLConnection conn = null;
            try {
                conn = connections.open(action);
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
                try {
                    if (conn != null) conn.disconnect();
                } finally {
                    if (work != null) work.close();
                }
            }
            final JSONObject data = response;
            final String problem = error;
            final int status = code;
            activity.runOnUiThread(() -> {
                if (!parallelRead) busy = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!java.util.Objects.equals(requestToken, session.token)
                        || com.huynhdous.employeefield.core.session.SessionWork.hasEnded(requestToken)) return;
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
                    if ("login".equals(action) || "me".equals(action) || "change-password".equals(action)) {
                        listener.unusableAnswer();
                    } else {
                        new Popup.Builder(activity).setTitle("Unable to load")
                                .setMessage("The server response could not be used. Please refresh and try again.")
                                .setPositiveButton("OK", null).show();
                    }
                }
            });
        }).start();
    }
}
