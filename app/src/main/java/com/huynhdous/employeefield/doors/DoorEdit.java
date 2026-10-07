package com.huynhdous.employeefield.doors;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.Tabs;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.schedule.DayClock;
import com.huynhdous.employeefield.trip.TripMap;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/** Correcting a door that was already finished today (outcome / note / callback only -- the visit already has its photo). */
final class DoorEdit {
    private final DoorsTab tab;
    private final AppHost host;
    private final Activity activity;

    DoorEdit(DoorsTab tab) {
        this.tab = tab;
        this.host = tab.app();
        this.activity = host.activity();
    }

    /** Same outcome picker as finishing a door, pre-filled with what's on record -- no new photo
     * required, since the visit already has one; only the outcome/note (and any callback) change. */
    void showEditOutcomeDialog(int dispositionId, String currentStatus, String currentNote, String address) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);
        OutcomeForm form = new OutcomeForm(activity, container, currentStatus, currentNote, null);

        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Correct " + address, Theme.PRIMARY))
                .setView(container)
                .setPositiveButton("Save correction", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.PRIMARY);
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!form.validate()) return;
            submitEditDoor(dispositionId, form.status(), form.note(), form.callbackDate());
            dialog.dismiss();
        });
    }

    private void submitEditDoor(int dispositionId, String status, String note, String callbackDate) {
        submitEditDoor(dispositionId, status, note, callbackDate, null);
    }

    /** Not routed through the shared host.request() helper -- that always shows a plain dead-end "OK"
     * dialog on failure, with no way to act on it. A correction that lands on a closing outcome
     * (sold/do-not-call/already-serviced) at an address with one already on record gets rejected the
     * same way a fresh finish would (see D2dDisposition::enforceDuplicateSalePolicy()); this reacts to
     * that specific rejection by prompting for the same justification and retrying, instead of leaving
     * the rep stuck. */
    private void submitEditDoor(int dispositionId, String status, String note, String callbackDate, String duplicateOverrideReason) {
        final String currentToken = host.token();
        new Thread(() -> {
            JSONObject result = null;
            Api.ApiError error = null;
            try {
                JSONObject body = new JSONObject().put("token", currentToken).put("disposition_id", dispositionId).put("status", status).put("note", note);
                if (callbackDate != null) body.put("callback_date", callbackDate);
                if (duplicateOverrideReason != null) body.put("duplicate_override_reason", duplicateOverrideReason);
                result = Api.post("telemapper/disposition/edit", body);
            } catch (Api.ApiError e) {
                error = e;
            } catch (Exception ignored) {
            }
            JSONObject finalResult = result;
            Api.ApiError finalError = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (finalResult != null) {
                    tab.loadDoors();
                    tab.loadMyLeads();
                } else if (finalError != null && finalError.getMessage() != null && finalError.getMessage().contains("was already marked")) {
                    promptDuplicateReasonThenEditDoor(dispositionId, status, note, callbackDate, finalError.getMessage());
                } else if (finalError != null) {
                    new Popup.Builder(activity).setTitle("Unable to save").setMessage(finalError.getMessage()).setPositiveButton("OK", null).show();
                }
            });
        }).start();
    }

    private void promptDuplicateReasonThenEditDoor(int dispositionId, String status, String note, String callbackDate, String serverMessage) {
        float density = activity.getResources().getDisplayMetrics().density;
        EditText reasonField = new EditText(activity);
        reasonField.setHint("e.g. different unit, prior outcome was wrong");
        Theme.styleInput(reasonField);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(reasonField);

        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Explain this sale", Theme.WARNING))
                .setMessage(serverMessage)
                .setView(container)
                .setPositiveButton("Save & Retry", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.WARNING);
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonField.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonField.setError("Required");
                return;
            }
            submitEditDoor(dispositionId, status, note, callbackDate, reason);
            dialog.dismiss();
        });
    }

}
