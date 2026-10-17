package com.huynhdous.employeefield.dispatch;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.media.Images;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.session.SessionWork;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * The photo a driver takes when he hands an order over ("Delivered"). It is a screen with no menu entry that only exists to own the camera
 * answers: the camera app can make Android close this one on a low-memory phone, so the order, the picture's file and the steps survive
 * through {@link #saveState}. After the photo is uploaded the order is marked delivered here, so nothing has to outlive a restart.
 * A driver can always finish without a photo; the office then sees the order has none.
 */
public class DeliveryPhoto extends TabModule {
    static final int REQUEST_CAMERA_PERMISSION = 71;
    static final int REQUEST_TAKE_PHOTO = 72;
    private static final String STATE_PATH = "delivery_photo_path";
    private static final String STATE_OFFER = "delivery_photo_offer";

    private File pendingFile;
    private String offerId = "";
    private boolean uploading;

    @Override public String title() { return "Delivery photo"; }
    @Override public int iconRes() { return R.drawable.ic_camera; }
    @Override public boolean isGated() { return false; }
    @Override public void buildContent(LinearLayout content) { }
    @Override public void onShown() { }
    @Override public int[] requestCodes() { return new int[]{REQUEST_CAMERA_PERMISSION, REQUEST_TAKE_PHOTO}; }

    @Override public void saveState(Bundle out) {
        if (pendingFile == null || offerId.isEmpty()) return;
        out.putString(STATE_PATH, pendingFile.getAbsolutePath());
        out.putString(STATE_OFFER, offerId);
    }

    @Override public void restoreState(Bundle state) {
        String path = state.getString(STATE_PATH), offer = state.getString(STATE_OFFER);
        if (path == null || offer == null) return;
        File file = new File(path);
        if (file.exists()) { pendingFile = file; offerId = offer; }
    }

    @Override public void onDetach() { pendingFile = null; }

    private Activity activity() { return host().activity(); }

    /** The driver chose "Take photo" for this order: ask for the camera if needed, then open it. */
    public void deliver(String offerId) {
        this.offerId = offerId;
        pendingFile = null;
        if (activity().checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) openCamera();
        else host().requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    @Override public void onPermissionResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_CAMERA_PERMISSION || offerId.isEmpty()) return;
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) { openCamera(); return; }
        new Popup.Builder(activity()).setTitle("Camera needed")
                .setMessage("Allow the camera to take the delivery photo, or finish the delivery without one.")
                .setPositiveButton("Try again", (d, w) -> deliver(offerId))
                .setNeutralButton("No photo", (d, w) -> complete())
                .setNegativeButton("Cancel", (d, w) -> clear()).show();
    }

    /** Where the camera app writes the picture (the app's file provider); a test replaces it. */
    protected android.net.Uri outputUri(File file) {
        return androidx.core.content.FileProvider.getUriForFile(activity(), activity().getPackageName() + ".fileprovider", file);
    }

    private void openCamera() {
        try {
            File file = File.createTempFile("delivery_", ".jpg", activity().getCacheDir());
            pendingFile = file;
            android.net.Uri uri = outputUri(file);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            host().startActivityForResult(this, intent, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            new Popup.Builder(activity()).setTitle("Camera unavailable").setMessage("Unable to open the camera. You can finish the delivery without a photo.")
                    .setPositiveButton("Try again", (d, w) -> openCamera())
                    .setNeutralButton("No photo", (d, w) -> complete())
                    .setNegativeButton("Cancel", (d, w) -> clear()).show();
        }
    }

    /** The camera came back. The file itself is checked: some camera apps do not return RESULT_OK reliably. */
    @Override public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != REQUEST_TAKE_PHOTO || offerId.isEmpty()) return;
        if (pendingFile != null && pendingFile.length() > 0) { review(); return; }
        new Popup.Builder(activity()).setTitle("Photo not saved")
                .setMessage("The photo did not save. Try again, or finish the delivery without one.")
                .setPositiveButton("Try again", (d, w) -> openCamera())
                .setNeutralButton("No photo", (d, w) -> complete())
                .setNegativeButton("Cancel", (d, w) -> clear()).show();
    }

    private void review() {
        float density = activity().getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * density);
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(Theme.photoPreview(activity(), pendingFile, 180));
        new Popup.Builder(activity()).setTitle("Delivery photo").setView(panel).setCancelable(false)
                .setPositiveButton("Use this photo", (d, w) -> upload())
                .setNeutralButton("Retake", (d, w) -> openCamera())
                .setNegativeButton("Cancel", (d, w) -> clear()).show();
    }

    private void upload() {
        if (pendingFile == null || uploading) return;
        final SessionWork.Lease work = host().beginUpload();
        if (work == null) {
            new Popup.Builder(activity()).setTitle("Please wait").setMessage("A request is still finishing. Please try again shortly.")
                    .setPositiveButton("Try again", (d, w) -> upload()).show();
            return;
        }
        uploading = true;
        final File file = pendingFile;
        final String offer = offerId;
        new Thread(() -> {
            String error = null;
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("token", work.token);
                fields.put("offer_id", offer);
                List<Api.FilePart> files = new ArrayList<>();
                files.add(new Api.FilePart("photo", "photo.jpg", "image/jpeg", Images.photoBytesForUpload(file)));
                Api.postMultipart("dispatch/proof", fields, files);
            } catch (Api.ApiError e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "Unable to send the photo. Check your connection and try again.";
            } finally {
                work.close();
            }
            final String problem = error;
            activity().runOnUiThread(() -> {
                uploading = false;
                if (problem == null) { complete(); return; }
                new Popup.Builder(activity()).setTitle("Photo not sent").setMessage(problem)
                        .setPositiveButton("Try again", (d, w) -> upload())
                        .setNeutralButton("No photo", (d, w) -> complete())
                        .setNegativeButton("Cancel", (d, w) -> clear()).show();
            });
        }).start();
    }

    private void clear() {
        pendingFile = null;
        offerId = "";
    }

    /** The photo is sent (or the driver chose none): mark the order delivered and bring the screen up to date. */
    public void complete() {
        final String offer = offerId;
        if (offer.isEmpty()) return;
        try {
            host().request("dispatch/progress", new JSONObject().put("token", host().token()).put("offer_id", offer).put("stage", "completed"), null, response -> {
                clear();
                host().refreshDispatch();
                new Popup.Builder(activity()).setMessage("Delivery completed. You can receive another offer when Ready.").setPositiveButton("OK", null).show();
            }, false, (code, message) -> {
                if (code == 409 || code == 403) { clear(); host().refreshDispatch(); }
                return false;
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
