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

/**
 * Doors the server permanently rejected (see {@link DispositionQueue#drain}): kept on the phone with their photo, listed under Today's
 * Doors as "Needs attention", and fixable here (edit the outcome, retake the photo, try again) or discardable. Also covers the old
 * records that have no confirmed owner ("Unidentified"), which are only opened after the server confirms they belong to this employee.
 */
final class FailedFinishes {
    static final int REQUEST_RETRY_CAMERA_PERMISSION = 68;
    static final int REQUEST_TAKE_RETRY_PHOTO = 69;

    private final DoorsTab tab;
    private final AppHost host;
    private final Activity activity;

    // Retaking a photo for a failed/needs-attention queue item -- deliberately separate from the
    // normal finish-a-door flow (activeDispositionId): that flow is tied to "a door currently in
    // progress," which a queued-but-rejected finish no longer is.
    private DispositionQueue.Finish editingFailedFinish;
    private String editingFailedFinishAddress;
    // Captured right before launching the camera so a retake doesn't discard whatever the rep had
    // already typed/selected in the edit dialog -- the dialog itself doesn't survive the round trip
    // through the camera app, but these plain instance fields do.
    private String editingDraftStatus, editingDraftNote, editingDraftCallbackDate, editingDraftDuplicateReason;
    private android.net.Uri pendingRetryPhotoUri;
    private File pendingRetryPhotoFile;

    FailedFinishes(DoorsTab tab) {
        this.tab = tab;
        this.host = tab.app();
        this.activity = host.activity();
    }

    void onRetryPhotoTaken() {
        if (pendingRetryPhotoFile != null && pendingRetryPhotoFile.length() > 0 && editingFailedFinish != null) {
            showEditFailedDialog(editingFailedFinish, editingFailedFinishAddress);
        } else {
            pendingRetryPhotoFile = null;
            new Popup.Builder(activity)
                    .setTitle("Photo not saved")
                    .setMessage("The photo didn't save — this can happen with some camera apps. Try again.")
                    .setPositiveButton("Retake", (d, w) -> beginRetryPhotoCapture())
                    .setNegativeButton("Cancel", (d, w) -> {
                        if (editingFailedFinish != null) showEditFailedDialog(editingFailedFinish, editingFailedFinishAddress);
                    })
                    .show();
        }
    }

    void onRetryCameraPermission() {
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startRetryPhotoCapture();
        else if (editingFailedFinish != null) showEditFailedDialog(editingFailedFinish, editingFailedFinishAddress);
    }

    /** Same outcome picker as correcting a finished door (status/note/callback), but for a submission the
     * server never actually accepted -- editing it re-queues the correction locally (via add()) and
     * drains again, rather than calling the server's "correct an already-saved door" endpoint, since
     * there's nothing saved server-side yet to correct. Also offers retaking the photo, for when the
     * photo itself was the problem (corrupt file, unreadable image, etc). */
    private void showEditFailedDialog(DispositionQueue.Finish f, String address) {
        editingFailedFinish = f;
        editingFailedFinishAddress = address;
        String initialStatus = editingDraftStatus != null ? editingDraftStatus : f.status;
        String initialNote = editingDraftNote != null ? editingDraftNote : f.note;
        String initialCallbackDateStr = editingDraftCallbackDate != null ? editingDraftCallbackDate : f.callbackDate;
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);

        container.addView(OutcomeForm.sectionLabel(activity, "PHOTO", Theme.TEXT_SECONDARY));
        LinearLayout photoRow = new LinearLayout(activity);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        photoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams photoRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        photoRowParams.topMargin = (int) (4 * density);
        container.addView(photoRow, photoRowParams);

        File currentPhotoFile = pendingRetryPhotoFile != null ? pendingRetryPhotoFile : new File(f.photoPath);
        ImageView thumb = Theme.photoPreview(activity, currentPhotoFile, 72);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        thumbParams.rightMargin = (int) (12 * density);
        photoRow.addView(thumb, thumbParams);
        Button retakeButton = Theme.filledButton(activity, "Retake Photo", Theme.PRIMARY);
        photoRow.addView(retakeButton);

        LinearLayout outcomeColumn = new LinearLayout(activity);
        outcomeColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams outcomeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        outcomeParams.topMargin = (int) (16 * density);
        container.addView(outcomeColumn, outcomeParams);
        OutcomeForm form = new OutcomeForm(activity, outcomeColumn, initialStatus, initialNote, initialCallbackDateStr);

        // The server rejected this as a repeat sale/close at an address that already has one on
        // record -- it requires an explicit justification before accepting another one, so offer the
        // field right here instead of making the rep hit the same rejection again blind.
        boolean wasDuplicateRejection = f.failureReason != null && f.failureReason.contains("was already marked");
        String initialDuplicateReason = editingDraftDuplicateReason != null ? editingDraftDuplicateReason : f.duplicateOverrideReason;
        EditText duplicateReasonField = null;
        if (wasDuplicateRejection) {
            LinearLayout.LayoutParams duplicateLabelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            duplicateLabelParams.topMargin = (int) (12 * density);
            container.addView(OutcomeForm.sectionLabel(activity, "WHY RECORD ANOTHER SALE HERE", Theme.WARNING), duplicateLabelParams);

            duplicateReasonField = new EditText(activity);
            duplicateReasonField.setHint("e.g. different unit, prior outcome was wrong");
            duplicateReasonField.setText(initialDuplicateReason);
            Theme.styleInput(duplicateReasonField);
            LinearLayout.LayoutParams duplicateReasonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            duplicateReasonParams.topMargin = (int) (4 * density);
            container.addView(duplicateReasonField, duplicateReasonParams);
        }
        final EditText finalDuplicateReasonField = duplicateReasonField;

        retakeButton.setOnClickListener(v -> {
            // Preserve whatever's currently typed/selected across the trip through the camera app.
            editingDraftStatus = form.status();
            editingDraftNote = form.noteRaw();
            editingDraftCallbackDate = form.dateText();
            if (finalDuplicateReasonField != null) editingDraftDuplicateReason = finalDuplicateReasonField.getText().toString();
            beginRetryPhotoCapture();
        });

        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Edit " + address, Theme.PRIMARY))
                .setView(container)
                .setPositiveButton("Save & Retry", null)
                .setNegativeButton("Cancel", (d, w) -> clearEditingFailedState())
                .show();
        Theme.styleDialog(dialog, Theme.PRIMARY);
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!form.validate()) return;
            String status = form.status();
            String duplicateReason = finalDuplicateReasonField != null ? finalDuplicateReasonField.getText().toString().trim() : null;
            if (finalDuplicateReasonField != null && DoorStatus.CLOSING.contains(status) && duplicateReason.isEmpty()) {
                finalDuplicateReasonField.setError("Explain why you're recording another sale here");
                return;
            }
            submitEditedFailedFinish(f, status, form.note(), form.callbackDate(), duplicateReason != null && !duplicateReason.isEmpty() ? duplicateReason : null);
            clearEditingFailedState();
            dialog.dismiss();
        });
    }

    void addNeedsAttentionRow(float density, DispositionQueue.Finish f, String address, boolean unassigned) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
        row.setBackground(Theme.cardBackground(activity));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (6 * density);
        tab.doorsReportContainer.addView(row, rowParams);

        TextView addressText = new TextView(activity);
        addressText.setText(address);
        addressText.setTextSize(14);
        addressText.setTextColor(Theme.TEXT_PRIMARY);
        row.addView(addressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = Theme.statusBadge(activity, unassigned ? "Unidentified" : "Needs attention", Theme.WARNING);
        row.addView(badge);

        row.setContentDescription(address + (unassigned ? ". Unidentified door, may not be yours. Tap to review." : ". Needs attention. Tap to review."));
        row.setOnClickListener(v -> {
            if (unassigned) verifyThenShowUnassignedDialog(f, address);
            else showNeedsAttentionDialog(f, address);
        });
    }

    /** An unassigned (employee_id 0) failed record might belong to a completely different employee
     * who's never even touched this device -- never reveal its photo/note, or offer to edit/discard
     * it, without the server confirming it's genuinely this signed-in employee's first. Otherwise
     * whoever happens to be signed in when it surfaces could casually view, or permanently destroy,
     * another rep's work. Fails closed: if the check itself can't complete, access is denied the same
     * as a confirmed "not yours" -- never assume access just because the network is uncooperative.
     * Also re-checks the captured host.token()/host.employeeId() still match the live session once the response
     * arrives -- a slow check could otherwise resolve after the signed-in employee changed, acting on
     * (or showing) a record that no longer has anything to do with who's actually using the device now. */
    private void verifyThenShowUnassignedDialog(DispositionQueue.Finish f, String address) {
        Toast.makeText(activity, "Checking…", Toast.LENGTH_SHORT).show();
        final String currentToken = host.token();
        final long currentEmployeeId = host.employeeId();
        new Thread(() -> {
            EmployeeApi.FinishStatus check = EmployeeApi.checkFinishStatus(currentToken, f.dispositionId);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                // The signed-in session moved on while this was checking (signed out, or a different
                // employee signed in) -- this result no longer applies to anyone currently using the
                // device; acting on it now would claim or show someone else's door under a stale session.
                if (!currentToken.equals(host.token()) || currentEmployeeId != host.employeeId()) return;
                if (check != null && check.found) {
                    // Confirmed genuinely theirs -- claim it locally so it's an ordinary record for
                    // this employee from now on, not something every signed-in user can see. Reload
                    // it afterward: this method's own f still has host.employeeId() 0, and editing through
                    // that stale object would later re-save employee_id 0 and undo the claim.
                    DispositionQueue.Finish claimed = tab.queue.claimUnassignedAndReload(currentEmployeeId, f.dispositionId, f.queuedMs);
                    if (claimed != null) showNeedsAttentionDialog(claimed, address);
                    tab.loadDoors();
                } else {
                    new Popup.Builder(activity)
                            .setTitle("Not yours")
                            .setMessage(check == null
                                    ? "Couldn't verify this record right now. Try again when you have a signal."
                                    : "This record isn't linked to your account and can't be opened from this device.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    /** A door the server permanently rejected (see DispositionQueue.drain()) -- kept locally with its
     * photo rather than silently discarded, so the rep decides what happens to it: fix whatever was
     * wrong and try again, or give up on it for good. Never blindly resends the exact same bytes --
     * if the photo or an answer was genuinely the problem, retrying unmodified would just fail the
     * same way again. Only ever called once a record is confirmed to belong to this employee -- a
     * previously-unassigned record is claimed (see verifyThenShowUnassignedDialog()) before it ever
     * reaches here, so this never exposes another employee's photo/note. */
    private void showNeedsAttentionDialog(DispositionQueue.Finish f, String address) {
        String reason = f.failureReason != null && !f.failureReason.isEmpty() ? f.failureReason : "Unable to save this door.";
        new Popup.Builder(activity)
                .setTitle("Needs attention")
                .setMessage(address + "\n\n" + reason)
                .setPositiveButton("Edit & Retry", (d, w) -> showEditFailedDialog(f, address))
                .setNeutralButton("Discard", (d, w) -> new Popup.Builder(activity)
                        .setTitle("Discard this door?")
                        .setMessage("This permanently deletes the saved photo and outcome for " + address + ". This can't be undone.")
                        .setPositiveButton("Discard", (d2, w2) -> {
                            tab.queue.discardFailed(f.dispositionId, f.queuedMs, f.photoPath);
                            tab.loadDoors();
                        })
                        .setNegativeButton("Cancel", null)
                        .show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearEditingFailedState() {
        editingDraftDuplicateReason = null;
        editingFailedFinish = null;
        editingFailedFinishAddress = null;
        editingDraftStatus = null;
        editingDraftNote = null;
        editingDraftCallbackDate = null;
        pendingRetryPhotoFile = null;
        pendingRetryPhotoUri = null;
    }

    private void beginRetryPhotoCapture() {
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(tab, new String[]{android.Manifest.permission.CAMERA}, REQUEST_RETRY_CAMERA_PERMISSION);
            return;
        }
        startRetryPhotoCapture();
    }

    private void startRetryPhotoCapture() {
        try {
            File photoFile = File.createTempFile("door_retry_", ".jpg", activity.getCacheDir());
            pendingRetryPhotoFile = photoFile;
            pendingRetryPhotoUri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingRetryPhotoUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            host.startActivityForResult(tab, intent, REQUEST_TAKE_RETRY_PHOTO);
        } catch (Exception e) {
            new Popup.Builder(activity).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    /** Re-queues a failed submission with its (possibly edited) answers and (possibly retaken) photo,
     * then drains immediately -- same local-queue path as finishing a door the first time, not the
     * server's "correct an already-saved door" endpoint, since the server never actually saved this
     * one. A too-large retaken photo falls back to keeping the original rather than losing the edit. */
    private void submitEditedFailedFinish(DispositionQueue.Finish original, String status, String note, String callbackDate, String duplicateOverrideReason) {
        String photoPath = original.photoPath;
        if (pendingRetryPhotoFile != null && pendingRetryPhotoFile.length() > 0) {
            try {
                byte[] bytes = Api.readAllBytes(new FileInputStream(pendingRetryPhotoFile));
                if (bytes.length > 8 * 1024 * 1024) {
                    new Popup.Builder(activity).setTitle("Edit & Retry").setMessage("That photo is too large. The original photo was kept.").setPositiveButton("OK", null).show();
                } else {
                    File durableDir = new File(activity.getFilesDir(), "door_finishes");
                    if (!durableDir.exists()) durableDir.mkdirs();
                    File durablePhoto = new File(durableDir, "door_" + original.dispositionId + "_" + System.currentTimeMillis() + ".jpg");
                    try (OutputStream out = new FileOutputStream(durablePhoto)) {
                        out.write(bytes);
                    }
                    photoPath = durablePhoto.getAbsolutePath();
                }
            } catch (IOException e) {
                new Popup.Builder(activity).setTitle("Edit & Retry").setMessage("Unable to save the new photo. The original photo was kept.").setPositiveButton("OK", null).show();
            } finally {
                pendingRetryPhotoFile.delete();
            }
        }
        tab.queue.add(new DispositionQueue.Finish(original.employeeId, original.dispositionId, status, note, original.latitude, original.longitude, callbackDate, photoPath, original.photoDistanceReason, duplicateOverrideReason));
        if (tab.messageText != null) tab.messageText.setText("Retrying…");
        final String currentToken = host.token();
        final long currentEmployeeId = host.employeeId();
        new Thread(() -> {
            tab.queue.drain(currentToken, currentEmployeeId);
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) tab.loadDoors();
            });
        }).start();
    }

}
