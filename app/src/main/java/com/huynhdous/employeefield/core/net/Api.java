package com.huynhdous.employeefield.core.net;

import com.huynhdous.employeefield.core.config.Config;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * The one place that talks to the employee API over HTTPS: JSON posts, multipart uploads, and the byte/multipart helpers they
 * (and screens that stream their own request) share. Feature code never builds a URL or a multipart body by hand.
 */
public final class Api {
    private Api() {
    }

    

    /** The server answered with an error status (or success=false). {@code code} is the HTTP status. */
    public static final class ApiError extends IOException {
        public final int code;

        public ApiError(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static final class FilePart {
        public final String field, filename, mimeType;
        public final byte[] bytes;

        public FilePart(String field, String filename, String mimeType, byte[] bytes) {
            this.field = field;
            this.filename = filename;
            this.mimeType = mimeType;
            this.bytes = bytes;
        }
    }

    /** Multipart upload usable from a background service too (no Activity needed), e.g. draining the queued door finishes. */
    public static JSONObject postMultipart(String action, java.util.Map<String, String> fields, java.util.List<FilePart> files) throws Exception {
        String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
        HttpsURLConnection c = (HttpsURLConnection) new URL(Config.API_BASE_URL + action).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream out = c.getOutputStream()) {
                for (java.util.Map.Entry<String, String> e : fields.entrySet()) writeMultipartField(out, boundary, e.getKey(), e.getValue());
                for (FilePart file : files) writeMultipartFile(out, boundary, file.field, file.filename, file.mimeType, file.bytes);
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            return readJson(c);
        } finally {
            c.disconnect();
        }
    }

    /** A JSON post that throws {@link ApiError} on any non-success answer. */
    public static JSONObject post(String action, JSONObject body) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(Config.API_BASE_URL + action).openConnection();
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
            return readJson(c);
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject readJson(HttpsURLConnection c) throws Exception {
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
        if (status < 200 || status >= 300 || !r.optBoolean("success")) throw new ApiError(status, r.optString("message", "Upload failed"));
        return r;
    }

    // ---- building blocks for a screen that streams its own request (it needs the status code and a bigger response) ----

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
        in.close();
        return bytes.toByteArray();
    }

    public static void writeMultipartField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void writeMultipartFile(OutputStream out, String boundary, String field, String filename, String mime, byte[] bytes) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
