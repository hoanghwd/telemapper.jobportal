package com.huynhdous.employeefield.doors;

import com.huynhdous.employeefield.core.net.Api;

import org.json.JSONObject;

import java.io.*;

/**
 * Server calls that belong to the D2D (door) feature: uploading one queued door finish and asking whether a door was finished.
 * Generic calls live in {@link Api}. This file moves into the D2D package when that feature is split out.
 */
final class EmployeeApi {
    /** Uploads one queued door-finish submission. Reads the photo bytes from disk itself (rather than
     * taking them as a parameter) so a caller can just hand over the queued row. submission_id
     * uniquely identifies THIS exact attempt (fresh per DispositionQueue.add(), even for the same
     * door) -- the server records which one actually saved, so a later reconciliation can tell "this
     * attempt saved" apart from "the door is just closed by something else". */
    static JSONObject submitDispositionFinish(String token, DispositionQueue.Finish f) throws Exception {
        byte[] photoBytes = readFile(new File(f.photoPath));
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("token", token);
        fields.put("disposition_id", String.valueOf(f.dispositionId));
        fields.put("submission_id", f.submissionId);
        fields.put("status", f.status);
        fields.put("note", f.note);
        if (f.latitude != null) fields.put("latitude", String.valueOf(f.latitude));
        if (f.longitude != null) fields.put("longitude", String.valueOf(f.longitude));
        if (f.callbackDate != null) fields.put("callback_date", f.callbackDate);
        if (f.photoDistanceReason != null) fields.put("photo_distance_reason", f.photoDistanceReason);
        if (f.duplicateOverrideReason != null) fields.put("duplicate_override_reason", f.duplicateOverrideReason);
        // The moment the rep closed the door, so a finish that sat in the queue still lands at its real time.
        if (f.finishedMs > 0) fields.put("finished_utc", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(f.finishedMs)));
        return Api.postMultipart("telemapper/disposition/finish", fields, java.util.Collections.singletonList(new Api.FilePart("photo", "door.jpg", "image/jpeg", photoBytes)));
    }

    /** found=false covers both "doesn't exist" and "isn't this employee's", indistinguishably, by
     * design -- the server never reveals which. submissionId is the server's own finish_submission_id
     * for the row (null if never finished, or finished by a build too old to send one), for comparing
     * against the attempt that's asking. */
    static final class FinishStatus {
        final boolean found, ended;
        final String submissionId;

        FinishStatus(boolean found, boolean ended, String submissionId) {
            this.found = found;
            this.ended = ended;
            this.submissionId = submissionId;
        }
    }

    /** A finish can genuinely succeed on the server even though this device never saw the response
     * (dropped connection, timeout) -- a retry then gets rejected as "not found" since the door's no
     * longer open, which is indistinguishable from a real rejection without asking. Returns null when
     * the check itself fails (network/server error) -- deliberately distinct from a confirmed
     * negative, so a caller can tell "couldn't find out right now" apart from "confirmed not theirs/
     * not saved" and avoid treating a merely temporary problem as a permanent one. */
    static FinishStatus checkFinishStatus(String token, int dispositionId) {
        try {
            JSONObject r = Api.post("telemapper/disposition/status", new JSONObject().put("token", token).put("disposition_id", dispositionId));
            return new FinishStatus(r.optBoolean("found", false), r.optBoolean("ended", false), r.isNull("submission_id") ? null : r.optString("submission_id", null));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readFile(File file) throws IOException {
        return Api.readAllBytes(new FileInputStream(file));
    }
}
