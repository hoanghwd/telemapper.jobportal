package com.huynhdous.employeefield.profile;

import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.media.Avatars;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.TabModule;
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

/** "Profile": the employee's name and photo (take a new one or pick from the gallery) and changing their own password. */
public final class ProfileTab extends TabModule {
    // Request codes this tab uses with the camera, the gallery and the permission prompt (unique across the app).
    private static final int REQUEST_CAMERA_PERMISSION = 40;
    private static final int REQUEST_TAKE_PHOTO = 50;
    private static final int REQUEST_PICK_PHOTO = 51;

    private ImageView avatarView;
    private android.net.Uri pendingCameraUri;
    private boolean uploading;

    @Override
    public String title() {
        return "Profile";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_profile;
    }

    @Override
    public int[] requestCodes() {
        return new int[]{REQUEST_CAMERA_PERMISSION, REQUEST_TAKE_PHOTO, REQUEST_PICK_PHOTO};
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();

        LinearLayout profileCard = new LinearLayout(context());
        profileCard.setOrientation(LinearLayout.VERTICAL);
        profileCard.setGravity(Gravity.CENTER_HORIZONTAL);
        profileCard.setPadding((int) (20 * density), (int) (20 * density), (int) (20 * density), (int) (20 * density));
        profileCard.setBackground(Theme.cardBackground(context()));
        content.addView(profileCard);

        avatarView = Theme.circularAvatar(context(), 88);
        int avatarSize = (int) (88 * density);
        profileCard.addView(avatarView, new LinearLayout.LayoutParams(avatarSize, avatarSize));
        Avatars.load(context(), host().token(), avatarView);

        LinearLayout changePhotoButton = Theme.iconTextButton(context(), R.drawable.ic_camera, "Change photo", Theme.PRIMARY);
        LinearLayout.LayoutParams changePhotoParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        changePhotoParams.topMargin = (int) (8 * density);
        profileCard.addView(changePhotoButton, changePhotoParams);
        changePhotoButton.setOnClickListener(v -> showChangePhotoOptions());

        TextView nameText = new TextView(context());
        nameText.setText(host().employeeName());
        nameText.setTextSize(18);
        nameText.setTypeface(nameText.getTypeface(), android.graphics.Typeface.BOLD);
        nameText.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = (int) (10 * density);
        profileCard.addView(nameText, nameParams);

        TextView usernameText = new TextView(context());
        usernameText.setText("Signed in as " + host().username());
        usernameText.setTextSize(13);
        usernameText.setTextColor(Theme.NEUTRAL);
        profileCard.addView(usernameText);

        buildPasswordCard(content, density);
    }

    private void buildPasswordCard(LinearLayout content, float density) {
        LinearLayout passwordCard = new LinearLayout(context());
        passwordCard.setOrientation(LinearLayout.VERTICAL);
        passwordCard.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
        passwordCard.setBackground(Theme.cardBackground(context()));
        LinearLayout.LayoutParams passwordCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        passwordCardParams.topMargin = (int) (10 * density);
        content.addView(passwordCard, passwordCardParams);

        TextView passwordTitle = new TextView(context());
        passwordTitle.setText("Change Password");
        passwordTitle.setTextSize(16);
        passwordTitle.setTypeface(passwordTitle.getTypeface(), android.graphics.Typeface.BOLD);
        passwordTitle.setTextColor(Theme.TEXT_PRIMARY);
        passwordCard.addView(passwordTitle);

        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = (int) (10 * density);

        EditText currentPasswordField = passwordInput("Current password");
        passwordCard.addView(currentPasswordField, fieldParams);
        EditText newPasswordField = passwordInput("New password (12+ characters)");
        passwordCard.addView(newPasswordField, new LinearLayout.LayoutParams(fieldParams));
        EditText confirmPasswordField = passwordInput("Confirm new password");
        passwordCard.addView(confirmPasswordField, new LinearLayout.LayoutParams(fieldParams));

        TextView passwordStatus = new TextView(context());
        passwordStatus.setTextSize(13);
        passwordStatus.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
        passwordCard.addView(passwordStatus);

        Button changePasswordButton = Theme.filledButton(context(), "Update password", Theme.PRIMARY);
        LinearLayout.LayoutParams changeBtnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        changeBtnParams.topMargin = (int) (4 * density);
        passwordCard.addView(changePasswordButton, changeBtnParams);
        changePasswordButton.setOnClickListener(v -> {
            passwordStatus.setText("");
            try {
                host().request("change-own-password", new JSONObject().put("token", host().token())
                        .put("current_password", currentPasswordField.getText().toString())
                        .put("password", newPasswordField.getText().toString())
                        .put("confirmation", confirmPasswordField.getText().toString()), changePasswordButton, r -> {
                    passwordStatus.setTextColor(Theme.SUCCESS);
                    passwordStatus.setText("✓ Password updated.");
                    currentPasswordField.setText("");
                    newPasswordField.setText("");
                    confirmPasswordField.setText("");
                });
            } catch (Exception ignored) {
            }
        });
    }

    private EditText passwordInput(String hint) {
        EditText field = new EditText(context());
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setSingleLine(true);
        Theme.styleInput(field);
        return field;
    }

    @Override
    public void onShown() {
        // Nothing to load: the photo and name are filled in when the screen is built.
    }

    @Override
    public void onDetach() {
        avatarView = null;
        pendingCameraUri = null;
    }

    // ---------------------------------------------------------------- profile photo

    private void showChangePhotoOptions() {
        new Popup.Builder(context())
                .setTitle("Change photo")
                .setItems(new String[]{"Take photo", "Choose from gallery"}, (dialog, which) -> {
                    if (which == 0) startCameraCapture();
                    else startGalleryPick();
                }).show();
    }

    private void startCameraCapture() {
        if (context().checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host().requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            File photoFile = File.createTempFile("avatar_", ".jpg", context().getCacheDir());
            pendingCameraUri = androidx.core.content.FileProvider.getUriForFile(context(), context().getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            host().startActivityForResult(this, intent, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            new Popup.Builder(context()).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try choosing from gallery instead.").setPositiveButton("OK", null).show();
        }
    }

    private void startGalleryPick() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        host().startActivityForResult(this, intent, REQUEST_PICK_PHOTO);
    }

    @Override
    public void onPermissionResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && context().checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCameraCapture();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK) return;
        if (requestCode == REQUEST_TAKE_PHOTO && pendingCameraUri != null) {
            uploadAvatarFromUri(pendingCameraUri);
        } else if (requestCode == REQUEST_PICK_PHOTO && data != null && data.getData() != null) {
            uploadAvatarFromUri(data.getData());
        }
    }

    private void uploadAvatarFromUri(android.net.Uri uri) {
        if (uploading) return;
        uploading = true;
        final String token = host().token();
        new Thread(() -> {
            String error = null;
            HttpsURLConnection conn = null;
            try {
                byte[] imageBytes = Api.readAllBytes(context().getContentResolver().openInputStream(uri));
                if (imageBytes.length > 2 * 1024 * 1024) throw new IOException("TOO_LARGE");
                String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + "avatar/upload").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = conn.getOutputStream()) {
                    Api.writeMultipartField(out, boundary, "token", token);
                    Api.writeMultipartFile(out, boundary, "photo", "avatar.jpg", "image/jpeg", imageBytes);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                JSONObject resp = new JSONObject(new String(Api.readAllBytes(stream), StandardCharsets.UTF_8));
                if (code < 200 || code >= 300 || !resp.optBoolean("success")) error = resp.optString("message", "Unable to upload photo.");
            } catch (Exception e) {
                error = "TOO_LARGE".equals(e.getMessage()) ? "That photo is too large. Choose a smaller image." : "Unable to upload photo. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            String problem = error;
            context().runOnUiThread(() -> {
                uploading = false;
                if (problem != null) {
                    new Popup.Builder(context()).setTitle("Upload photo").setMessage(problem).setPositiveButton("OK", null).show();
                } else {
                    if (avatarView != null) Avatars.load(context(), host().token(), avatarView);
                    host().avatarChanged();
                }
            });
        }).start();
    }
}
