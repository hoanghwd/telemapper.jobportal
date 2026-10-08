package com.huynhdous.employeefield.checkin;

import android.content.Context;
import android.os.Bundle;
import android.util.AtomicFile;
import com.huynhdous.employeefield.core.net.LimitedStreams;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Private, employee-scoped storage for a check-in ready to send. No tokens are persisted. */
final class CheckInDraftStore {
    private final File directory;
    private final File cache;
    private final AtomicFile record;

    CheckInDraftStore(Context context, long employeeId) {
        if (employeeId <= 0) throw new IllegalArgumentException("Employee required");
        directory = new File(context.getFilesDir(), "checkin_drafts/" + employeeId);
        cache = context.getCacheDir();
        record = new AtomicFile(new File(directory, "draft.json"));
    }

    File newPhoto(String prefix) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Unable to save check-in photos");
        return File.createTempFile(prefix, ".jpg", directory);
    }

    /** Migrates captures from older app builds out of reclaimable cache storage. */
    File keep(File photo) throws IOException {
        if (owns(photo)) return photo;
        if (!isLegacyPhoto(photo)) throw new IOException("Photo does not belong to this check-in");
        File saved = newPhoto(photo.getName().startsWith("selfie_") ? "selfie_" : "store_");
        try (FileOutputStream out = new FileOutputStream(saved)) {
            LimitedStreams.copy(new java.io.FileInputStream(photo), out, 64 * 1024 * 1024);
        } catch (IOException e) { saved.delete(); throw e; }
        return saved; // Caller deletes the old capture only after the replacement is recorded.
    }

    synchronized void save(String id, JSONObject assignment, File selfie, List<File> photos, Double lat, Double lon) throws IOException {
        try {
            JSONArray paths = new JSONArray();
            if (!owns(selfie) || !selfie.isFile() || selfie.length() == 0) throw new IOException("Selfie is missing");
            for (File photo : photos) {
                if (!owns(photo) || !photo.isFile() || photo.length() == 0) throw new IOException("Store photo is missing");
                paths.put(photo.getAbsolutePath());
            }
            if (paths.length() == 0) throw new IOException("Store photo is missing");
            JSONObject data = new JSONObject().put("id", id).put("assignment", assignment)
                    .put("selfie", selfie.getAbsolutePath()).put("photos", paths);
            if (lat != null && lon != null) data.put("latitude", lat).put("longitude", lon);
            FileOutputStream out = null;
            try {
                out = record.startWrite();
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));
                record.finishWrite(out);
            } catch (IOException e) { if (out != null) record.failWrite(out); throw e; }
        } catch (org.json.JSONException e) { throw new IOException("Unable to save check-in details", e); }
    }

    private JSONObject read() throws IOException, org.json.JSONException {
        return new JSONObject(new String(LimitedStreams.read(record.openRead(), 65536), StandardCharsets.UTF_8));
    }

    synchronized Bundle load() {
        try {
            JSONObject data = read();
            File selfie = new File(data.getString("selfie"));
            if (!owns(selfie) || !selfie.isFile() || selfie.length() == 0) return null;
            JSONArray photos = data.getJSONArray("photos");
            if (photos.length() == 0) return null;
            String[] paths = new String[photos.length()];
            for (int i = 0; i < paths.length; i++) {
                File photo = new File(photos.getString(i));
                if (!owns(photo) || !photo.isFile() || photo.length() == 0) return null;
                paths[i] = photo.getAbsolutePath();
            }
            Bundle state = new Bundle();
            state.putString("check_in_draft_id", data.getString("id"));
            state.putString("active_arrival_assignment", data.getJSONObject("assignment").toString());
            state.putString("pending_selfie_path", selfie.getAbsolutePath());
            state.putStringArray("accepted_store_paths", paths);
            state.putBoolean("check_in_unsent", true); // An interrupted upload must check server status before retrying.
            if (data.has("latitude") && data.has("longitude")) {
                state.putDouble("arrival_latitude", data.getDouble("latitude"));
                state.putDouble("arrival_longitude", data.getDouble("longitude"));
            }
            return state;
        } catch (IOException | org.json.JSONException e) { return null; }
    }

    /** An old upload must never remove a newer draft. Deletes only files owned by this employee. */
    synchronized void clear(String expectedId) {
        if (expectedId == null) return;
        try {
            JSONObject data = read();
            if (!expectedId.equals(data.optString("id"))) return;
            deletePhoto(new File(data.getString("selfie")));
            JSONArray photos = data.getJSONArray("photos");
            for (int i = 0; i < photos.length(); i++) deletePhoto(new File(photos.getString(i)));
            record.delete();
        } catch (IOException | org.json.JSONException ignored) { }
    }

    void deletePhoto(File photo) {
        if (owns(photo) || isLegacyPhoto(photo)) photo.delete();
    }

    private boolean owns(File photo) {
        if (photo == null || !(photo.getName().startsWith("selfie_") || photo.getName().startsWith("store_"))) return false;
        try { return directory.getCanonicalFile().equals(photo.getCanonicalFile().getParentFile()); }
        catch (IOException e) { return false; }
    }

    private boolean isLegacyPhoto(File photo) {
        if (photo == null || !(photo.getName().startsWith("selfie_") || photo.getName().startsWith("store_"))) return false;
        try { return cache.getCanonicalFile().equals(photo.getCanonicalFile().getParentFile()); }
        catch (IOException e) { return false; }
    }
}
