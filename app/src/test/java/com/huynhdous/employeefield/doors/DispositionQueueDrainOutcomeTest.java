package com.huynhdous.employeefield.doors;

import com.huynhdous.employeefield.core.net.Api;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

/** Covers the pieces of the door-completion queue that are pure logic with no Android framework,
 * network, or SQLite dependency: given a failed upload, should drain() permanently mark that one
 * record failed, leave it pending for someone else to try, or stop the whole queue? A permanently
 * invalid record (or a missing photo) must never block every door finished after it, and a
 * pre-migration record with no confirmed owner must never be locked out by one employee's wrong
 * guess -- see DispositionQueue.decideDrainOutcome(). */
public class DispositionQueueDrainOutcomeTest {

    @Test
    public void missingPhotoFile_isMarkedFailed() {
        assertEquals(DispositionQueue.DrainOutcome.SKIP_MARK_FAILED,
                DispositionQueue.decideDrainOutcome(new FileNotFoundException("gone"), true));
    }

    @Test
    public void missingPhotoFile_isMarkedFailedEvenForUnverifiedOwner() {
        // A corrupt/missing photo is permanent regardless of whose record this is.
        assertEquals(DispositionQueue.DrainOutcome.SKIP_MARK_FAILED,
                DispositionQueue.decideDrainOutcome(new FileNotFoundException("gone"), false));
    }

    @Test
    public void serverRejectsSubmission422_isMarkedFailed() {
        assertEquals(DispositionQueue.DrainOutcome.SKIP_MARK_FAILED,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(422, "Invalid photo"), true));
    }

    @Test
    public void serverRejectsSubmission422_isMarkedFailedEvenForUnverifiedOwner() {
        // Bad data (e.g. a corrupt photo) will never succeed under any employee -- an unconfirmed
        // owner doesn't make it any more retryable.
        assertEquals(DispositionQueue.DrainOutcome.SKIP_MARK_FAILED,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(422, "Invalid photo"), false));
    }

    @Test
    public void dispositionAlreadyClosed404_isMarkedFailedForKnownOwner() {
        assertEquals(DispositionQueue.DrainOutcome.SKIP_MARK_FAILED,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(404, "Active disposition not found"), true));
    }

    @Test
    public void dispositionAlreadyClosed404_isLeftPendingForUnverifiedOwner() {
        // A pre-migration record (employee_id unknown) rejected as "not found" only means it isn't
        // THIS employee's -- not that it belongs to no one. It must stay available for a different
        // employee signing into this device later, not get locked out by this one wrong guess.
        assertEquals(DispositionQueue.DrainOutcome.SKIP_LEAVE_PENDING,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(404, "Active disposition not found"), false));
    }

    @Test
    public void authExpired401_stopsTheDrain() {
        assertEquals(DispositionQueue.DrainOutcome.STOP,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(401, "Session expired"), true));
    }

    @Test
    public void forbidden403_stopsTheDrain() {
        assertEquals(DispositionQueue.DrainOutcome.STOP,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(403, "Forbidden"), true));
    }

    @Test
    public void rateLimited429_stopsTheDrain() {
        assertEquals(DispositionQueue.DrainOutcome.STOP,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(429, "Too many requests"), true));
    }

    @Test
    public void serverError500_stopsTheDrain() {
        assertEquals(DispositionQueue.DrainOutcome.STOP,
                DispositionQueue.decideDrainOutcome(new Api.ApiError(500, "Unable to save disposition."), true));
    }

    @Test
    public void plainNetworkFailure_stopsTheDrain() {
        assertEquals(DispositionQueue.DrainOutcome.STOP,
                DispositionQueue.decideDrainOutcome(new IOException("No response"), true));
    }

    @Test
    public void unexpectedException_stopsTheDrain() {
        assertEquals(DispositionQueue.DrainOutcome.STOP,
                DispositionQueue.decideDrainOutcome(new RuntimeException("unexpected"), true));
    }
}
