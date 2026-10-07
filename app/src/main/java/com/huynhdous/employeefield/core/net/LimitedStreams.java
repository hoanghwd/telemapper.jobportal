package com.huynhdous.employeefield.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Enforces limits while reading, before a file or response can exhaust memory. */
public final class LimitedStreams {
    private LimitedStreams() { }

    public static byte[] read(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output, maxBytes);
        return output.toByteArray();
    }

    /** Owns/closes input; the caller owns output. Reads at most maxBytes + 1 bytes. */
    public static void copy(InputStream input, OutputStream output, int maxBytes) throws IOException {
        if (input == null) throw new IOException("No input");
        try (InputStream in = input) {
            if (maxBytes < 0) throw new IllegalArgumentException("Negative byte limit");
            byte[] buffer = new byte[4096];
            int total = 0;
            while (true) {
                int count = in.read(buffer, 0, (int) Math.min(buffer.length, (long) maxBytes - total + 1));
                if (count == -1) return;
                if (count == 0) continue;
                if ((long) total + count > maxBytes) throw new IOException("TOO_LARGE");
                output.write(buffer, 0, count);
                total += count;
            }
        }
    }
}
