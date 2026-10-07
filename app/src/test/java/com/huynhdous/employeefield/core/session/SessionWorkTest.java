package com.huynhdous.employeefield.core.session;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionWorkTest {
    @Test public void uploadBlocksAnotherMutationAcrossOwnersUntilClosed() {
        SessionWork.Lease first = SessionWork.begin("work-test-one", 1);
        assertNotNull(first);
        try {
            assertTrue(SessionWork.isBusy(first.token));
            assertNull(SessionWork.begin(first.token, 1));
            assertFalse(first.matches(first.token, 2));
        } finally { first.close(); }
        assertFalse(SessionWork.isBusy(first.token));
        SessionWork.Lease next = SessionWork.begin(first.token, 1);
        assertNotNull(next);
        first.close(); // A stale close must not release a newer operation.
        assertTrue(SessionWork.isBusy(first.token));
        next.close();
    }

    @Test public void signOutInvalidatesCallbackAndRejectsFurtherUploads() {
        SessionWork.Lease lease = SessionWork.begin("work-test-ended", 2);
        try {
            assertTrue(lease.matches(lease.token, 2));
            SessionWork.end(lease.token);
            assertFalse(lease.matches(lease.token, 2));
            assertNull(SessionWork.begin(lease.token, 2));
        } finally { lease.close(); }
    }

    @Test public void employeesHaveIndependentOperations() {
        try (SessionWork.Lease a = SessionWork.begin("work-test-a", 1);
             SessionWork.Lease b = SessionWork.begin("work-test-b", 2)) {
            assertNotNull(a);
            assertNotNull(b);
            assertFalse(a.matches(b.token, 2));
        }
    }
}
