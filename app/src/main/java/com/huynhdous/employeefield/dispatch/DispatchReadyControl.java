package com.huynhdous.employeefield.dispatch;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.location.TrackingService;
import org.json.JSONObject;

/** Ready is explicit, and only appears when the server confirms a customer-service model. */
public final class DispatchReadyControl {
    public final Button view;
    private final AppHost host;
    private final Session session;
    private String busyLabel = "";
    public DispatchReadyControl(AppHost host, Session session) {
        this.host = host; this.session = session;
        view = Theme.filledButton(host.activity(), "Ready to accept new orders", Theme.PRIMARY);
        view.setVisibility(View.GONE);
        view.setOnClickListener(v -> {
            if (!session.token.equals(TrackingService.activeToken)) {
                host.enableTracking();
                new AlertDialog.Builder(host.activity()).setMessage("Enable work location tracking, then tap Ready again.").setPositiveButton("OK", null).show();
                return;
            }
            DispatchPolling.ready(session.token, !DispatchPolling.isReady(session.token));
            refreshLabel();
        });
        check();
    }
    public void check() {
        try { host.request("dispatch/status", new JSONObject().put("token", session.token), null, response -> {
            view.setVisibility(response.optBoolean("dispatch_enabled") ? View.VISIBLE : View.GONE);
            if (!response.optBoolean("dispatch_enabled")) DispatchPolling.ready(session.token, false);
            JSONObject job=response.optJSONObject("active_job");
            busyLabel=job==null?"":"Busy — "+job.optString("status_label",job.optString("status"));
            view.setEnabled(job==null);
            refreshLabel();
        }, true, null); } catch (Exception e) { view.setVisibility(View.GONE); }
    }
    private void refreshLabel() {
        view.setText(!busyLabel.isEmpty() ? busyLabel : DispatchPolling.isReady(session.token) ? "Ready to accept new orders — tap to go unavailable" : "Ready to accept new orders");
    }
}
