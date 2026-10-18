package com.huynhdous.employeefield.dispatch;

import android.content.Context;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.session.SessionWork;
import com.huynhdous.employeefield.inbox.InboxActions;
import com.huynhdous.employeefield.location.TrackingService;
import org.json.JSONObject;

/** Runs on the existing location service's single worker; no second background service. */
public final class DispatchPolling {
    private static volatile String readyToken = "";
    public static volatile String status = "Unavailable for service work";
    interface Poster { JSONObject post(String action, JSONObject request) throws Exception; }
    private final Poster api;
    private final java.util.function.LongSupplier clock;
    private final Context context;
    private final String token;
    private final long employee;
    private long next;
    private boolean lastReady, pendingUnavailable;
    private int interval = 15, retry = 15;
    private JSONObject point;

    public DispatchPolling(Context context, String token, long employee) {
        this(context, token, employee, Api::post, System::currentTimeMillis);
    }
    DispatchPolling(Context context, String token, long employee, Poster api, java.util.function.LongSupplier clock) {
        this.api = api; this.clock = clock;
        this.context = context; this.token = token; this.employee = employee;
    }
    public static void ready(String token, boolean ready) {
        readyToken = ready ? token : "";
        status = ready ? "Ready — checking for work offers" : "Unavailable for service work";
    }
    public static boolean isReady(String token) { return !token.isEmpty() && token.equals(readyToken); }
    public void location(JSONObject point) { this.point = point; }
    public void tick() {
        if (!token.equals(TrackingService.activeToken) || SessionWork.hasEnded(token)) return;
        boolean ready = isReady(token);
        if (!ready && !lastReady && !pendingUnavailable) return;
        if (ready == lastReady && clock.getAsLong() < next) return;
        try {
            JSONObject request = new JSONObject().put("token", token).put("availability", ready ? "ready" : "unavailable");
            if (ready && point != null && point.optLong("captured_ms") >= clock.getAsLong() - 175000)
                request.put("point", point);
            JSONObject response = api.post("dispatch/poll", request);
            if (!token.equals(TrackingService.activeToken) || SessionWork.hasEnded(token)) return;
            lastReady = ready; pendingUnavailable = false; retry = 15;
            interval = Math.max(5, Math.min(300, response.optInt("poll_interval_seconds", 15)));
            status = "busy".equals(response.optString("availability")) ? "Order request accepted — busy" : ready ? "Ready — waiting for work" : "Unavailable for service work";
            InboxActions inbox = new InboxActions(context, employee);
            inbox.setWorkOffersEnabled(response.optBoolean("job_offers_enabled", false));
            JSONObject offer = response.optJSONObject("pending_offer");
            inbox.setPendingOffer(offer != null ? "offer:" + offer.getString("id") : "");
            if (ready && isReady(token) && offer != null)
                inbox.receiveWorkOffer("offer:" + offer.getString("id"), offer.getString("title"), offer.getString("body"), offer.optJSONObject("map")==null ? "" : offer.getJSONObject("map").toString(), offer.optString("business_name", ""));
            next = clock.getAsLong() + interval * 1000L;
        } catch (Api.ApiError e) {
            if (e.code == 401 || e.code == 403) { ready(token, false); lastReady = false; pendingUnavailable = false; status = e.getMessage(); }
            else delay();
        } catch (Exception e) { delay(); }
    }
    private void delay() {
        status = "Connection interrupted — retrying work offers";
        next = clock.getAsLong() + retry * 1000L;
        retry = Math.min(60, retry * 2);
        // Mark the desired state as attempted so a failed Ready transition also backs off.
        lastReady = isReady(token);
        pendingUnavailable = !lastReady;
    }
    public void stop() {
        if (isReady(token)) ready(token, false);
        if (lastReady || pendingUnavailable) try { api.post("dispatch/poll", new JSONObject().put("token", token).put("availability", "unavailable")); }
        catch (Exception ignored) { }
        lastReady = false;
    }
}
