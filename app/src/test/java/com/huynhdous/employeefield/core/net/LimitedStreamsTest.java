package com.huynhdous.employeefield.core.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class LimitedStreamsTest {
    private static class TrackedInput extends ByteArrayInputStream {
        boolean closed;
        TrackedInput(int size) { super(new byte[size]); }
        @Override public void close() { closed = true; }
    }

    @Test public void acceptsExactLimitAndClosesInput() throws Exception {
        TrackedInput input = new TrackedInput(4096);
        assertEquals(4096, LimitedStreams.read(input, 4096).length);
        assertTrue(input.closed);
    }

    @Test public void rejectsLargeInputBeforeReadingTheWholeFile() throws Exception {
        TrackedInput input = new TrackedInput(100000);
        try {
            LimitedStreams.read(input, 8192);
            fail("Expected the size limit to reject the input");
        } catch (IOException e) { assertEquals("TOO_LARGE", e.getMessage()); }
        assertEquals(100000 - 8193, input.available());
        assertTrue(input.closed);
    }

    @Test public void acceptsEmptyStreamAtZeroLimit() throws Exception {
        assertEquals(0, LimitedStreams.read(new ByteArrayInputStream(new byte[0]), 0).length);
    }
}
