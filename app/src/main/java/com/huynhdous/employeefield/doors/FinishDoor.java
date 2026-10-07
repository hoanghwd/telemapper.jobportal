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
        final com.huynhdous.employeefield.core.session.SessionWork.Lease work = host.beginUpload();
        if (work == null) {
            tab.say("Please wait for your current request to finish, then try again.");
            return;
        }
        final int dispositionId = tab.activeDispositionId;
        final File capturedPhoto = pendingDoorsPhotoFile;
        tab.say("Saving your door…");
        new Thread(() -> {
            DispositionQueue storage = new DispositionQueue(activity.getApplicationContext());
            File durablePhoto = null;
            String error = null;
            try {
                File durableDir = new File(activity.getFilesDir(), "door_finishes");
                if (!durableDir.isDirectory() && !durableDir.mkdirs()) throw new IOException("Unable to create photo directory");
                durablePhoto = File.createTempFile("door_" + dispositionId + "_", ".jpg", durableDir);
                byte[] bytes = com.huynhdous.employeefield.core.media.Images.photoBytesForUpload(capturedPhoto);
                try (OutputStream out = new FileOutputStream(durablePhoto)) { out.write(bytes); }
                storage.add(new DispositionQueue.Finish(work.employeeId, dispositionId, status, note, lat, lon,
                        callbackDate, durablePhoto.getAbsolutePath(), photoDistanceReason, null));
                capturedPhoto.delete();
            } catch (Exception e) {
                if (durablePhoto != null) durablePhoto.delete();
                error = "TOO_LARGE".equals(e.getMessage()) ? "That photo is too large. Please retake it."
                        : "Unable to save your door. Your captured photo is kept; please try again.";
            } finally {
                storage.close();
                work.close();
            }
            final String problem = error;
            activity.runOnUiThread(() -> {
                if (!host.isCurrent(work)) {
                    // The screen was recreated (a rotation) while this was saving: the screen that replaced it shows the result.
                    host.leaveOutcome(work, new com.huynhdous.employeefield.core.session.SessionWork.Outcome("door", problem == null, true, problem));
                    return;
                }
                if (tab.messageText == null) return;
                showSaved(problem, dispositionId);
            });
            if (problem == null) {
                int remaining;
                DispositionQueue uploads = new DispositionQueue(activity.getApplicationContext());
                try {
                    uploads.drain(work.token, work.employeeId);
                    remaining = uploads.pendingCountForEmployee(work.employeeId);
                } catch (Exception e) {
                    remaining = -1;   // could not tell: leave the screen as it is
                } finally {
                    uploads.close();
                }
                final int left = remaining;
                if (left >= 0) activity.runOnUiThread(() -> showUploadProgress(work, left));
            }
        }).start();
    }

    /** The door was saved on the phone (or could not be): reset the screen to "no door open" and refresh the map. */
    private void showSaved(String problem, int dispositionId) {
        if (problem != null) {
            new Popup.Builder(activity).setTitle("Finish door").setMessage(problem).setPositiveButton("OK", null).show();
            return;
        }
        if (java.util.Objects.equals(tab.activeDispositionId, dispositionId)) tab.renderIdle();
        tab.say("Door saved — uploading now…");
        tab.loadDoorsMap();
    }

    /** The background upload of the waiting doors is over: say how many are still waiting, and refresh Today's Doors when all are in. */
    private void showUploadProgress(com.huynhdous.employeefield.core.session.SessionWork.Lease work, int left) {
        if (!host.isCurrent(work) || tab.messageText == null) return;
        if (left == 0) tab.loadDoorsMap();   // the report now shows the finished outcome
        if (tab.activeDispositionId != null) return;   // a new door is already open: leave its line alone
        tab.say(left == 0 ? DoorsTab.IDLE_MESSAGE
                : left + " door" + (left == 1 ? "" : "s") + " saved — will upload when you have a signal.");
    }

    /** Shows what a door save reported after the screen had been recreated. */
    void takeOutcomes() {
        for (com.huynhdous.employeefield.core.session.SessionWork.Outcome o : com.huynhdous.employeefield.core.session.SessionWork.takeOutcomes(host.token(), "door")) {
            if (o.success) {
                tab.renderIdle();   // the restored screen still showed the door as open
                tab.say("Door saved — uploading now…");
                tab.loadDoorsMap();
            } else {
                new Popup.Builder(activity).setTitle("Finish door").setMessage(o.message).setPositiveButton("OK", null).show();
            }
        }
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
