package com.huynhdous.employeefield;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class EmployeeApi {
    static final class ApiError extends IOException {
        final int code;

        ApiError(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    static final class FilePart {
        final String field, filename, mimeType;
        final byte[] bytes;

        FilePart(String field, String filename, String mimeType, byte[] bytes) {
            this.field = field;
            this.filename = filename;
            this.mimeType = mimeType;
            this.bytes = bytes;
        }
    }

    /** Same wire format as the multipart uploads in LoginActivity (avatar/arrival photos) — kept
     * here too so background queue drains (TrackingService has no Activity to borrow the helper
     * from) can post a photo without duplicating request plumbing. */
    static JSONObject postMultipart(String action, java.util.Map<String, String> fields, java.util.List<FilePart> files) throws Exception {
        String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
        HttpsURLConnection c = (HttpsURLConnection) new URL("https://jobportal.huynhdous.com/api/employee/" + action).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream out = c.getOutputStream()) {
                for (java.util.Map.Entry<String, String> e : fields.entrySet()) {
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
                for (FilePart file : files) {
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + file.field + "\"; filename=\"" + file.filename + "\"\r\nContent-Type: " + file.mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(file.bytes);
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            if (stream == null) throw new IOException("No response");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = stream) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                    if (bytes.size() + n > 131072) throw new IOException("Response too large");
                    bytes.write(b, 0, n);
                }
            }
            JSONObject r = new JSONObject(bytes.toString("UTF-8"));
            if (status < 200 || status >= 300 || !r.optBoolean("success"))
                throw new ApiError(status, r.optString("message", "Upload failed"));
            return r;
        } finally {
            c.disconnect();
        }
    }

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
        return postMultipart("telemapper/disposition/finish", fields, java.util.Collections.singletonList(new FilePart("photo", "door.jpg", "image/jpeg", photoBytes)));
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
            JSONObject r = post("telemapper/disposition/status", new JSONObject().put("token", token).put("disposition_id", dispositionId));
            return new FinishStatus(r.optBoolean("found", false), r.optBoolean("ended", false), r.isNull("submission_id") ? null : r.optString("submission_id", null));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static JSONObject post(String action, JSONObject body) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL("https://jobportal.huynhdous.com/api/employee/" + action).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            if (stream == null) throw new IOException("No response");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = stream) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                    if (bytes.size() + n > 131072) throw new IOException("Response too large");
                    bytes.write(b, 0, n);
                }
            }
            JSONObject r = new JSONObject(bytes.toString("UTF-8"));
            if (status < 200 || status >= 300 || !r.optBoolean("success"))
                throw new ApiError(status, r.optString("message", "Upload failed"));
            return r;
        } finally {
            c.disconnect();
        }
    }
}
