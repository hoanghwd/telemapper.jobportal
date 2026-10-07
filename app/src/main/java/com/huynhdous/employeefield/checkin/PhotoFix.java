package com.huynhdous.employeefield.checkin;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.media.Images;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.ActionSheet;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

import java.util.function.Consumer;

/**
 * Fixing a check-in's photos afterwards (the same day, even after "I'm done"): tap a photo to retake it or delete it, or add another store
 * photo. A fresh picture is shown for approval, then replaces / adds on the server (the server keeps at least one store photo and never
 * lets the selfie be deleted). Its pending picture survives the camera app killing this one (low-RAM phones) via {@link #saveState}.
 */
final class PhotoFix {
    static final int REQUEST_TAKE_CORRECTION_PHOTO = 70;
    private static final String STATE_PATH = "pending_correction_path";
    private static final String STATE_ARRIVAL = "correction_arrival_id";
    private static final String STATE_WHICH = "correction_which";
    private static final String STATE_POSITION = "correction_position";

    private final AppHost host;
    private final TabModule owner;
    private final Consumer<String> message;   // the screen's status line
    private final Runnable changed;           // reload the screen after a fix

    private File pendingFile;
    private long arrivalId;
    private String which;
    private int position;
    private boolean uploading;

    PhotoFix(AppHost host, TabModule owner, Consumer<String> message, Runnable changed) {
        this.host = host;
        this.owner = owner;
        this.message = message;
        this.changed = changed;
    }

    private Activity context() {
        return host.activity();
    }

    /** What each check-in photo is called, so the rep (and the manager on the web) can tell them apart. */
    static String label(String which, int position) {
        if ("selfie".equals(which)) return "Selfie";
        return position <= 1 ? "Store front" : "Store photo " + position;
    }

    void saveState(Bundle out) {
        if (pendingFile == null) return;
        out.putString(STATE_PATH, pendingFile.getAbsolutePath());
        out.putLong(STATE_ARRIVAL, arrivalId);
        out.putString(STATE_WHICH, which);
        out.putInt(STATE_POSITION, position);
    }

    void restoreState(Bundle state) {
        String path = state.getString(STATE_PATH);
        if (path == null) return;
        File f = new File(path);
        if (f.exists() && f.length() > 0) {
            pendingFile = f;
            arrivalId = state.getLong(STATE_ARRIVAL);
            which = state.getString(STATE_WHICH);
            position = state.getInt(STATE_POSITION);
        }
    }

    void clear() {
        pendingFile = null;
    }

    /** Tapped a check-in photo: replace it with a new picture, or (a store photo, when another remains) delete it. */
    void showActions(long arrivalId, String which, int position, int storeCount) {
        boolean canDelete = "store".equals(which) && storeCount > 1;
        java.util.List<ActionSheet.Action> actions = new java.util.ArrayList<>();
        actions.add(new ActionSheet.Action("Retake photo", R.drawable.ic_camera, false, () -> begin(arrivalId, which, position)));
        if (canDelete) actions.add(new ActionSheet.Action("Delete photo", R.drawable.ic_delete, true, () -> confirmDelete(arrivalId, position)));
        ActionSheet.show(context(), label(which, position), "selfie".equals(which) ? "Replace the selfie with a new one." : "Retake it" + (canDelete ? " or remove it from your check-in." : "."), actions);
    }

    private void confirmDelete(long arrivalId, int position) {
        new Popup.Builder(context())
                .setTitle("Delete " + label("store", position) + "?")
                .setMessage("This photo will be removed from your check-in.")
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        host.request("telemapper/arrival/photo-delete", new JSONObject().put("token", host.token()).put("arrival_id", arrivalId).put("position", position), null, r -> changed.run());
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** position 0 = add one more store photo; otherwise replace that photo (the selfie ignores the position). */
    void begin(long arrivalId, String which, int position) {
        if (context().checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            message.accept("Camera permission is required to take a photo.");
            return;
        }
        try {
            File photoFile = File.createTempFile("fix_", ".jpg", context().getCacheDir());
            pendingFile = photoFile;
            this.arrivalId = arrivalId;
            this.which = which;
            this.position = position;
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context(), context().getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String what = "selfie".equals(which) ? "selfie" : (position == 0 ? "extra store photo" : label(which, position).toLowerCase(java.util.Locale.US));
            new Popup.Builder(context())
                    .setTitle("selfie".equals(which) ? "Selfie" : "Store photo")
                    .setMessage("Take the new " + what + ".")
                    .setPositiveButton("Open camera", (d, w) -> host.startActivityForResult(owner, intent, REQUEST_TAKE_CORRECTION_PHOTO))
                    .setNegativeButton("Cancel", (d, w) -> pendingFile = null)
                    .show();
        } catch (Exception e) {
            new Popup.Builder(context()).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    /** The camera came back (the answer to {@link #begin}). The file itself is checked: some camera apps don't return RESULT_OK reliably. */
    void onCaptured() {
        if (pendingFile != null && pendingFile.length() > 0) {
            showReview();
        } else {
            pendingFile = null;
            new Popup.Builder(context())
                    .setTitle("Photo not saved")
                    .setMessage("The photo didn't save — this can happen with some camera apps. Try again.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showReview() {
        if (pendingFile == null) return;
        float density = context().getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(context());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * density);
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(Theme.photoPreview(context(), pendingFile, 160));
        final String which = this.which;
        final int position = this.position;
        new Popup.Builder(context())
                .setTitle(position == 0 ? "New store photo" : label(which, position) + " — new photo")
                .setView(panel)
                .setCancelable(false)
                .setPositiveButton("Use this photo", (d, w) -> upload())
                .setNeutralButton("Retake", (d, w) -> begin(arrivalId, which, position))
                .setNegativeButton("Cancel", (d, w) -> pendingFile = null)
                .show();
    }

    private void upload() {
        if (pendingFile == null || uploading) return;
        final com.huynhdous.employeefield.core.session.SessionWork.Lease work = host.beginUpload();
        if (work == null) {
            new Popup.Builder(context()).setTitle("Please wait").setMessage("A request is still finishing. Please try again shortly.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        uploading = true;
        final File file = pendingFile;
        final long arrivalId = this.arrivalId;
        final String which = this.which;
        final int position = this.position;
        final String token = work.token;
        message.accept("Saving photo…");
        new Thread(() -> {
            String error = null;
            HttpsURLConnection conn = null;
            try {
                byte[] bytes = Images.photoBytesForUpload(file);
                String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + (position == 0 ? "telemapper/arrival/photo-add" : "telemapper/arrival/photo-replace")).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = conn.getOutputStream()) {
                    Api.writeMultipartField(out, boundary, "token", token);
                    Api.writeMultipartField(out, boundary, "arrival_id", String.valueOf(arrivalId));
                    if (position != 0) {
                        Api.writeMultipartField(out, boundary, "which", which);
                        Api.writeMultipartField(out, boundary, "position", String.valueOf(position));
                    }
                    Api.writeMultipartFile(out, boundary, "photo", "photo.jpg", "image/jpeg", bytes);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                JSONObject resp = new JSONObject(new String(Api.readAllBytes(stream), StandardCharsets.UTF_8));
                if (code < 200 || code >= 300 || !resp.optBoolean("success")) error = resp.optString("message", "Unable to save the photo.");
            } catch (Exception e) {
                error = "Unable to save the photo. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
                work.close();
            }
            String problem = error;
            context().runOnUiThread(() -> {
                uploading = false;
                if (!host.isCurrent(work)) return;
                message.accept("");
                if (problem != null) {
                    // Keep the picture so a dropped connection doesn't mean taking it again.
                    new Popup.Builder(context())
                            .setTitle("Photo not saved")
                            .setMessage(problem)
                            .setPositiveButton("Try again", (d, w) -> upload())
                            .setNegativeButton("Cancel", (d, w) -> pendingFile = null)
                            .show();
                } else {
                    pendingFile = null;
                    changed.run();
                }
            });
        }).start();
    }
}
