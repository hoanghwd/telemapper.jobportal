package com.huynhdous.employeefield.core.media;

import android.app.Activity;
import android.widget.ImageView;

import com.huynhdous.employeefield.core.config.Config;

import java.io.InputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/** Loads the signed-in employee's profile photo into a round avatar (the greeting at the top and the Profile screen both use it). */
public final class Avatars {
    private Avatars() {
    }

    /**
     * Independent of the shared one-call-at-a-time request gate: it must not block, and must not be silently dropped when another call is in
     * flight. With no photo yet (or offline) the placeholder stays.
     */
    public static void load(Activity activity, String token, ImageView target) {
        new Thread(() -> {
            android.graphics.Bitmap bitmap = null;
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + "avatar?token=" + java.net.URLEncoder.encode(token, "UTF-8")).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        bitmap = android.graphics.BitmapFactory.decodeStream(in);
                    }
                }
            } catch (Exception ignored) {
                // No photo uploaded yet, or offline -- keep the placeholder.
            } finally {
                if (conn != null) conn.disconnect();
            }
            android.graphics.Bitmap result = bitmap;
            activity.runOnUiThread(() -> {
                if (result != null) target.setImageBitmap(result);
            });
        }).start();
    }
}
