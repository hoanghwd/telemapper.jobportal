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
 * Finishing a door: photo of the door, a check the photo was taken near the house, the outcome, then a durable local copy that uploads in
 * the background (see {@link DispositionQueue}). The pending photo survives Android closing the app while the camera is open.
 */
final class FinishDoor {
    static final int REQUEST_CAMERA_PERMISSION = 61;
    static final int REQUEST_TAKE_PHOTO = 62;
    private static final String STATE_PHOTO_PATH = "pending_doors_photo_path";

    private final DoorsTab tab;
    private final AppHost host;
    private final Activity activity;

    private android.net.Uri pendingDoorsPhotoUri;
    private File pendingDoorsPhotoFile;

    FinishDoor(DoorsTab tab) {
        this.tab = tab;
        this.host = tab.app();
        this.activity = host.activity();
    }

    void saveState(Bundle out) {
        if (pendingDoorsPhotoFile != null) out.putString(STATE_PHOTO_PATH, pendingDoorsPhotoFile.getAbsolutePath());
    }

    void restoreState(Bundle state) {
        String path = state.getString(STATE_PHOTO_PATH);
        if (path == null) return;
        File f = new File(path);
        if (f.exists() && f.length() > 0) {
            pendingDoorsPhotoFile = f;
            pendingDoorsPhotoUri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", f);
        }
    }

    /** The door is closed out (or none is open): forget the photo that was waiting. */
    void clearPhoto() {
        pendingDoorsPhotoUri = null;
        pendingDoorsPhotoFile = null;
    }

    /** The camera came back. Same unreliable-resultCode issue as the check-in photos (certain OEM camera apps don't reliably return
     * RESULT_OK even when the file wrote fine) -- the file itself is checked instead of trusting resultCode. */
    void onPhotoTaken() {
        if (pendingDoorsPhotoFile != null && pendingDoorsPhotoFile.length() > 0) {
            locatePhotoThenContinue();
        } else {
            showDoorsPhotoFailedDialog();
        }
    }

    void onCameraPermission() {
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startDoorsPhotoCapture();
        else tab.say("Camera permission is required to finish a door.");
    }

    private void locatePhotoThenContinue() {
        tab.say("Checking your location…");
        Geo.fetchQuickLocation(activity, this::checkPhotoProximityAndContinue);
    }

    private void showFinishDoorDialog(Double lat, Double lon, String photoDistanceReason) {
        if (tab.activeDispositionId == null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);
        OutcomeForm form = new OutcomeForm(activity, container, null, null, null);

        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Finish this door", Theme.SUCCESS))
                .setView(container)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.SUCCESS);
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!form.validate()) return;
            submitFinishDoor(form.status(), form.note(), lat, lon, form.callbackDate(), photoDistanceReason);
            dialog.dismiss();
        });
    }

    void beginFinishDoor() {
        if (tab.activeDispositionId == null) return;
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(tab, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        startDoorsPhotoCapture();
    }

    private void startDoorsPhotoCapture() {
        try {
            File photoFile = File.createTempFile("door_", ".jpg", activity.getCacheDir());
            pendingDoorsPhotoFile = photoFile;
            pendingDoorsPhotoUri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingDoorsPhotoUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            host.startActivityForResult(tab, intent, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            new Popup.Builder(activity).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    private void checkPhotoProximityAndContinue(Double lat, Double lon) {
        if (lat != null && lon != null && tab.activeDoorLat != null && tab.activeDoorLon != null) {
            double distance = Geo.metersBetween(tab.activeDoorLat, tab.activeDoorLon, lat, lon);
            if (distance > Config.DOOR_PHOTO_PROXIMITY_METERS) {
                // Retake is the normal path (still standing at the door), but a rep who only
                // realizes later — after leaving, at the end of the day — needs a way to still
                // close this out instead of it being stuck open forever. The server already
                // tolerates this: a far-away photo is recorded and flagged for manager review,
                // never rejected, so "Finish anyway" just uses the path that already exists.
                Popup dialog = new Popup.Builder(activity)
                        .setCustomTitle(Theme.dialogTitle(activity, "That looks too far from the house", Theme.WARNING))
                        .setMessage("This photo was taken about " + Math.round(distance) + "m from where you started this door. If you're still there, retake it closer. If you've already left, you can still finish it — this will be flagged for your manager to review.")
                        .setPositiveButton("Retake photo", (d, which) -> beginFinishDoor())
                        .setNeutralButton("Finish anyway", (d, which) -> promptFarAwayReason(lat, lon))
                        .setNegativeButton("Cancel", (d, which) -> {
                            if (tab.messageText != null) tab.messageText.setText("Timer running — tap Finish when the door closes.");
                        })
                        .show();
                Theme.styleDialog(dialog, Theme.WARNING);
                return;
            }
        }
        showFinishDoorDialog(lat, lon, null);
    }

    /** Required before finishing a door away from its location — the server won't accept it without
     * one, and asking up front (instead of failing after the outcome picker) saves a rep from
     * re-entering the whole outcome if they left it blank. */
    private void promptFarAwayReason(Double lat, Double lon) {
        float density = activity.getResources().getDisplayMetrics().density;
        EditText reasonField = new EditText(activity);
        reasonField.setHint("e.g. Already left for the day");
        Theme.styleInput(reasonField);
        int pad = (int) (20 * density);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(reasonField);

        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Why are you finishing this away from the door?", Theme.WARNING))
                .setView(container)
                .setPositiveButton("Continue", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.WARNING);
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonField.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonField.setError("Enter a reason");
                return;
            }
            showFinishDoorDialog(lat, lon, reason);
            dialog.dismiss();
        });
    }

    /** Queues the finish locally first (a durable copy of the photo + the outcome fields), then
     * tries to upload right away — same offline-first shape as LocationQueue/TrackingService, so a
     * dead zone at the door doesn't cost the rep their photo or make them babysit a spinner. The
     * queue is drained again on the next periodic tracking cycle if this immediate attempt fails. */
    private void submitFinishDoor(String status, String note, Double lat, Double lon, String callbackDate, String photoDistanceReason) {
        if (tab.activeDispositionId == null || pendingDoorsPhotoFile == null) return;
        final int dispositionId = tab.activeDispositionId;
        final File capturedPhoto = pendingDoorsPhotoFile;
        File durableDir = new File(activity.getFilesDir(), "door_finishes");
        if (!durableDir.exists()) durableDir.mkdirs();
        File durablePhoto = new File(durableDir, "door_" + dispositionId + "_" + System.currentTimeMillis() + ".jpg");
        try {
            byte[] bytes = Api.readAllBytes(new FileInputStream(capturedPhoto));
            // Checked here, before queueing, not just left to the server: a too-large photo would
            // otherwise queue successfully and then fail every future drain attempt forever, blocking
            // every other queued door behind it since drain() stops at the first failure it hits.
            if (bytes.length > 8 * 1024 * 1024) {
                new Popup.Builder(activity).setTitle("Finish door").setMessage("That photo is too large. Please retake it.").setPositiveButton("OK", null).show();
                return;
            }
            try (OutputStream out = new FileOutputStream(durablePhoto)) {
                out.write(bytes);
            }
        } catch (IOException e) {
            new Popup.Builder(activity).setTitle("Finish door").setMessage("Unable to save the photo. Try again.").setPositiveButton("OK", null).show();
            return;
        }
        capturedPhoto.delete();
        tab.queue.add(new DispositionQueue.Finish(host.employeeId(), dispositionId, status, note, lat, lon, callbackDate, durablePhoto.getAbsolutePath(), photoDistanceReason, null));
        tab.renderIdle();
        tab.loadDoorsMap();
        tab.messageText.setText("Door saved — uploading now…");

        final String currentToken = host.token();
        final long currentEmployeeId = host.employeeId();
        new Thread(() -> {
            tab.queue.drain(currentToken, currentEmployeeId);
            int remaining = tab.queue.pendingCountForEmployee(currentEmployeeId);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || tab.messageText == null || tab.activeDispositionId != null) return;
                tab.messageText.setText(remaining == 0
                        ? "Tap your position on the map below, closest to the house, to start a door — or use \"Start a New Door Here\" if none is close enough."
                        : remaining + " door" + (remaining == 1 ? "" : "s") + " saved — will upload when you have a signal.");
            });
        }).start();
    }

    private void showDoorsPhotoFailedDialog() {
        if (tab.messageText != null) tab.messageText.setText("Timer running — tap Finish when the door closes.");
        new Popup.Builder(activity)
                .setTitle("Photo not saved")
                .setMessage("The photo didn't save — this can happen with some camera apps. Try again.")
                .setPositiveButton("Retake", (d, w) -> startDoorsPhotoCapture())
                .setNegativeButton("Cancel", null)
                .show();
    }

}
