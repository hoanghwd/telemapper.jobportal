package com.huynhdous.employeefield.core.media;

import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.net.Api;

import java.io.File;
import java.io.IOException;

/**
 * Camera photos are 10-50 megapixels (5-15 MB). Sending that raw made check-in slow even on 5G, and the server then has to decode and
 * shrink every one of them anyway (it keeps 1280 px). So shrink on the phone first: the long edge is capped and re-saved as a JPEG,
 * which is a few hundred KB. The camera's rotation flag is applied to the pixels here because the re-saved file no longer carries it.
 */
public final class Images {
    private Images() {
    }

    public static final int UPLOAD_MAX_EDGE = Config.PHOTO_MAX_EDGE_PX;
    public static final int UPLOAD_JPEG_QUALITY = Config.PHOTO_JPEG_QUALITY;

    /** The bytes to upload for a captured photo: shrunk, upright JPEG -- or the original if it can't be shrunk. */
    public static byte[] photoBytesForUpload(File file) throws IOException {
        try {
            return compressedJpeg(file);
        } catch (IOException | OutOfMemoryError e) {
            // Could not shrink it (unreadable or too big for memory): send the original and let the server's own limits decide.
            return Api.readAllBytes(new java.io.FileInputStream(file));
        }
    }

    public static byte[] avatarBytes(android.content.Context context, android.net.Uri uri) throws IOException {
        File temporary = File.createTempFile("avatar_upload_", ".img", context.getCacheDir());
        try {
            try (java.io.OutputStream out = new java.io.FileOutputStream(temporary)) {
                com.huynhdous.employeefield.core.net.LimitedStreams.copy(
                        context.getContentResolver().openInputStream(uri), out, 32 * 1024 * 1024);
            }
            return compressedJpeg(temporary);
        } catch (OutOfMemoryError e) {
            throw new IOException("TOO_LARGE", e);
        } finally { temporary.delete(); }
    }

    public static byte[] compressedJpeg(File file) throws IOException {
        String path = file.getAbsolutePath();
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("BAD_IMAGE");
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= UPLOAD_MAX_EDGE) sample *= 2;
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        android.graphics.Bitmap decoded = android.graphics.BitmapFactory.decodeFile(path, opts);
        if (decoded == null) throw new IOException("BAD_IMAGE");
        int orientation = android.media.ExifInterface.ORIENTATION_NORMAL;
        try {
            orientation = new android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
        }
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        switch (orientation) {
            case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1f, 1f); break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180f); break;
            case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.postRotate(180f); matrix.postScale(-1f, 1f); break;
            case android.media.ExifInterface.ORIENTATION_TRANSPOSE: matrix.postRotate(270f); matrix.postScale(-1f, 1f); break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90f); break;
            case android.media.ExifInterface.ORIENTATION_TRANSVERSE: matrix.postRotate(90f); matrix.postScale(-1f, 1f); break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270f); break;
            default: break;
        }
        float scale = (float) UPLOAD_MAX_EDGE / Math.max(decoded.getWidth(), decoded.getHeight());
        if (scale < 1f) matrix.postScale(scale, scale);
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        out.compress(android.graphics.Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, bytes);
        if (out != decoded) out.recycle();
        decoded.recycle();
        return bytes.toByteArray();
    }
}
