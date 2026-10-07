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

    @Test public void outcomesAreKeptPerSessionAndKindAndTakenOnce() {
        String token = "outcome-test-a";
        SessionWork.postOutcome(token, new SessionWork.Outcome("checkin", true, false, null));
        SessionWork.postOutcome(token, new SessionWork.Outcome("photo", false, true, "No signal"));
        assertTrue(SessionWork.takeOutcomes("someone-else", "checkin").isEmpty());
        java.util.List<SessionWork.Outcome> photos = SessionWork.takeOutcomes(token, "photo");
        assertEquals(1, photos.size());
        assertEquals("No signal", photos.get(0).message);
        assertTrue(photos.get(0).retryable);
        assertTrue(SessionWork.takeOutcomes(token, "photo").isEmpty());
        assertEquals(1, SessionWork.takeOutcomes(token, "checkin").size());
    }

    @Test public void signOutDropsWaitingOutcomesAndRefusesNewOnes() {
        String token = "outcome-test-b";
        SessionWork.postOutcome(token, new SessionWork.Outcome("door", true, true, null));
        SessionWork.end(token);
        assertTrue(SessionWork.takeOutcomes(token, "door").isEmpty());
        SessionWork.postOutcome(token, new SessionWork.Outcome("door", true, true, null));
        assertTrue(SessionWork.takeOutcomes(token, "door").isEmpty());
    }

    @Test public void liveScreenIsWokenWhenAnOutcomeArrives() {
        final int[] woken = {0};
        Runnable listener = () -> woken[0]++;
        SessionWork.setOutcomeListener(listener);
        try {
            SessionWork.postOutcome("outcome-test-c", new SessionWork.Outcome("avatar", true, true, null));
            assertEquals(1, woken[0]);
        } finally {
            SessionWork.removeOutcomeListener(listener);
            SessionWork.takeOutcomes("outcome-test-c", "avatar");
        }
        SessionWork.postOutcome("outcome-test-c", new SessionWork.Outcome("avatar", true, true, null));
        assertEquals(1, woken[0]);   // no listener any more
        SessionWork.takeOutcomes("outcome-test-c", "avatar");
    }
}
