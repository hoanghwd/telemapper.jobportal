package com.huynhdous.employeefield;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Queued "finish door" submissions awaiting upload — same offline-first shape as LocationQueue,
 * but for the disposition-finish photo+outcome instead of a GPS point. The photo file itself lives
 * in the durable finish-queue directory (not the camera capture's cache-dir temp file, which the OS
 * can reclaim at any time), so a submission survives even if it sits queued for hours. */
final class DispositionQueue extends SQLiteOpenHelper {
    // LoginActivity (a user-triggered drain on the Doors tab) and TrackingService (a periodic
    // background drain) each hold their own DispositionQueue instance but run in the same process --
    // without this, both can read the same pending rows and submit the same disposition twice at
    // once. A JVM-wide lock, not an instance lock, is required since they're different objects.
    private static final Object DRAIN_LOCK = new Object();

    static final class Finish {
        final long employeeId;
        final int dispositionId;
        final String status, note, callbackDate, photoPath, photoDistanceReason;
        final Double latitude, longitude;
        /** 0 for a Finish being passed to add() (not queued yet); the row's own queued_ms once read
         * back from the database -- see the class-level note on why remove()/markFailed() key off
         * this instead of disposition_id alone. */
        final long queuedMs;
        /** Only meaningful for a row read back via failedForEmployee(). */
        final String failureReason;
        /** Assigned fresh by add() for every submission attempt, even a replacement of the same
         * dispositionId -- lets a 404 reconciliation (see drain()) confirm THIS attempt is what the
         * server actually saved, not just that the door happens to be closed by some other attempt. */
        final String submissionId;
        /** The rep's explanation for recording another closing outcome (sold/do-not-call/already-
         * serviced) at an address that already has one on file -- required by the server for those
         * statuses when a duplicate is on record (see D2dDisposition::finishForEmployeeToken()).
         * Null for an ordinary finish; only ever set when retrying a submission the server rejected
         * for exactly this reason (see LoginActivity.showEditFailedDialog()). */
        final String duplicateOverrideReason;
        /** When the rep actually closed the door (phone clock). Sent with the upload so the server records that moment, not the
         * moment a queued finish finally got through -- a door finished at 6:24 PM but uploaded at 9:50 PM is still a 6:24 PM door.
         * Stays the same when a correction replaces the queued row. 0 only for a Finish not queued yet. */
        final long finishedMs;

        Finish(long employeeId, int dispositionId, String status, String note, Double latitude, Double longitude, String callbackDate, String photoPath, String photoDistanceReason, String duplicateOverrideReason) {
            this(employeeId, dispositionId, status, note, latitude, longitude, callbackDate, photoPath, photoDistanceReason, 0, null, null, duplicateOverrideReason, 0);
        }

        private Finish(long employeeId, int dispositionId, String status, String note, Double latitude, Double longitude, String callbackDate, String photoPath, String photoDistanceReason, long queuedMs, String failureReason, String submissionId, String duplicateOverrideReason, long finishedMs) {
            this.finishedMs = finishedMs;
            this.employeeId = employeeId;
            this.dispositionId = dispositionId;
            this.status = status;
            this.note = note;
            this.latitude = latitude;
            this.longitude = longitude;
            this.callbackDate = callbackDate;
            this.photoPath = photoPath;
            this.photoDistanceReason = photoDistanceReason;
            this.queuedMs = queuedMs;
            this.failureReason = failureReason;
            this.submissionId = submissionId;
            this.duplicateOverrideReason = duplicateOverrideReason;
        }
    }

    DispositionQueue(Context context) {
        super(context, "employee_disposition_finishes.db", null, 6);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE finishes (disposition_id INTEGER PRIMARY KEY,employee_id INTEGER NOT NULL,status TEXT NOT NULL,note TEXT NOT NULL,latitude REAL,longitude REAL,callback_date TEXT,photo_path TEXT NOT NULL,photo_distance_reason TEXT,failed INTEGER NOT NULL DEFAULT 0,failure_reason TEXT,submission_id TEXT NOT NULL,duplicate_override_reason TEXT,queued_ms INTEGER NOT NULL,finished_ms INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Existing queued (not-yet-uploaded) finishes are rare and short-lived — but still worth
        // preserving rather than the old throw-and-lose-everything behavior, so an app update
        // doesn't cost a rep an already-captured door sitting in the queue during a dead zone.
        if (oldVersion < 2) db.execSQL("ALTER TABLE finishes ADD COLUMN photo_distance_reason TEXT");
        if (oldVersion < 3) {
            // employee_id 0 marks a row queued before this column existed -- there's no way to
            // recover which employee it really was from this device alone, so it's deliberately
            // NOT assigned to whoever happens to sign in next. See pendingForEmployee()/drain(): it's
            // opportunistically attempted under whichever employee is currently signed in, and the
            // server's own ownership check (not a local guess) is what actually resolves it.
            db.execSQL("ALTER TABLE finishes ADD COLUMN employee_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE finishes ADD COLUMN failed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE finishes ADD COLUMN failure_reason TEXT");
        }
        if (oldVersion < 4) {
            // Empty string for any row queued before this column existed -- it will simply never
            // match a real submission_id the server reports back, so it just falls through to normal
            // (non-reconciled) handling on a 404, same as before this column existed.
            db.execSQL("ALTER TABLE finishes ADD COLUMN submission_id TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 5) db.execSQL("ALTER TABLE finishes ADD COLUMN duplicate_override_reason TEXT");
        // 0 for a row queued before this column existed: readRow() then falls back to that row's own queued_ms.
        if (oldVersion < 6) db.execSQL("ALTER TABLE finishes ADD COLUMN finished_ms INTEGER NOT NULL DEFAULT 0");
    }

    void add(Finish f) {
        // A rep can re-finish a door that's already queued (e.g. correcting a mistake before it's
        // even uploaded) -- CONFLICT_REPLACE below overwrites that row. If an older photo is being
        // superseded, it's now orphaned on disk and nothing will ever upload (or delete) it, since
        // drain() only ever sees the row that's actually in the table -- clean it up here instead.
        String oldPhotoPath = null;
        // A correction keeps the original finish moment; only a brand-new finish is stamped "now".
        long finishedMs = System.currentTimeMillis();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT photo_path, finished_ms, queued_ms FROM finishes WHERE disposition_id=?", new String[]{String.valueOf(f.dispositionId)})) {
            if (c.moveToFirst()) {
                oldPhotoPath = c.getString(0);
                long was = c.getLong(1) > 0 ? c.getLong(1) : c.getLong(2);
                if (was > 0) finishedMs = was;
            }
        }

        ContentValues row = new ContentValues();
        row.put("disposition_id", f.dispositionId);
        row.put("employee_id", f.employeeId);
        row.put("status", f.status);
        row.put("note", f.note);
        if (f.latitude != null) row.put("latitude", f.latitude);
        if (f.longitude != null) row.put("longitude", f.longitude);
        row.put("callback_date", f.callbackDate);
        row.put("photo_path", f.photoPath);
        row.put("photo_distance_reason", f.photoDistanceReason);
        row.put("failed", 0);
        row.put("failure_reason", (String) null);
        // Fresh every time, even replacing an existing row for the same disposition -- this is what
        // lets drain() later tell "this exact attempt saved" apart from "the door is just closed by
        // something else" (see its class comment).
        row.put("submission_id", java.util.UUID.randomUUID().toString());
        row.put("duplicate_override_reason", f.duplicateOverrideReason);
        row.put("queued_ms", System.currentTimeMillis());
        row.put("finished_ms", finishedMs);
        // CONFLICT_REPLACE on disposition_id also doubles as "retry": re-queuing the same door
        // (e.g. after a rep corrects something) clears any earlier failed/failure_reason state.
        getWritableDatabase().insertWithOnConflict("finishes", null, row, SQLiteDatabase.CONFLICT_REPLACE);

        if (oldPhotoPath != null && !oldPhotoPath.equals(f.photoPath)) new java.io.File(oldPhotoPath).delete();
    }

    private Finish readRow(Cursor c) {
        return new Finish(
                c.getLong(0), c.getInt(1), c.getString(2), c.getString(3),
                c.isNull(4) ? null : c.getDouble(4), c.isNull(5) ? null : c.getDouble(5),
                c.isNull(6) ? null : c.getString(6), c.getString(7),
                c.isNull(8) ? null : c.getString(8), c.getLong(9), c.isNull(10) ? null : c.getString(10), c.getString(11),
                c.isNull(12) ? null : c.getString(12),
                c.getLong(13) > 0 ? c.getLong(13) : c.getLong(9)
        );
    }

    private static final String ROW_COLUMNS = "employee_id,disposition_id,status,note,latitude,longitude,callback_date,photo_path,photo_distance_reason,queued_ms,failure_reason,submission_id,duplicate_override_reason,finished_ms";

    /** This employee's still-retryable submissions, plus any pre-migration row with no known owner
     * (employee_id=0 -- see onUpgrade()) so it gets an honest, server-verified shot at resolving
     * under whoever is currently signed in, without ever being guessed at or reassigned locally. */
    List<Finish> pendingForEmployee(long employeeId) {
        List<Finish> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + ROW_COLUMNS + " FROM finishes WHERE (employee_id=? OR employee_id=0) AND failed=0 ORDER BY queued_ms",
                new String[]{String.valueOf(employeeId)})) {
            while (c.moveToNext()) out.add(readRow(c));
        }
        return out;
    }

    /** This employee's permanently-rejected submissions, kept (not deleted) for a "needs attention"
     * screen to show -- the door, the server's reason, and a way to retry or discard. */
    List<Finish> failedForEmployee(long employeeId) {
        List<Finish> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + ROW_COLUMNS + " FROM finishes WHERE employee_id=? AND failed=1 ORDER BY queued_ms",
                new String[]{String.valueOf(employeeId)})) {
            while (c.moveToNext()) out.add(readRow(c));
        }
        return out;
    }

    /** Permanently-rejected submissions with no confirmed owner (employee_id 0 -- a pre-migration
     * row that got a genuinely permanent rejection, e.g. 422/missing photo, unrelated to whose it
     * is). failedForEmployee() can never show these (they don't match any real employee_id), so
     * without this they'd be invisible forever -- surfaced instead to whoever is currently signed
     * in, since there's no way to know who they really belong to, but leaving them permanently
     * hidden is worse than letting a human judge them (retry under their own account, or discard). */
    List<Finish> failedUnassigned() {
        List<Finish> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + ROW_COLUMNS + " FROM finishes WHERE employee_id=0 AND failed=1 ORDER BY queued_ms", null)) {
            while (c.moveToNext()) out.add(readRow(c));
        }
        return out;
    }

    /** Scoped to the exact submission attempt (disposition_id AND the queued_ms it was read with),
     * not disposition_id alone -- a rep can re-finish (and so replace) a queued door while an older
     * upload of that same disposition_id is still in flight; without this, the older upload finishing
     * (or failing) afterward would remove/mark the NEWER replacement instead of the stale attempt it
     * actually belongs to. If the row's been replaced in the meantime, queued_ms no longer matches
     * and this simply affects zero rows, leaving the replacement untouched. */
    private void remove(int dispositionId, long queuedMs) {
        getWritableDatabase().delete("finishes", "disposition_id=? AND queued_ms=?", new String[]{String.valueOf(dispositionId), String.valueOf(queuedMs)});
    }

    /** Marks a submission as permanently rejected instead of deleting it -- the rep's work (the visit,
     * the photo) isn't discarded just because this one attempt failed; it's kept, with the server's
     * reason, for failedForEmployee() to show. Excluded from pendingForEmployee() so it stops being
     * retried (and stops blocking the queue), without disappearing. Same queued_ms scoping as
     * remove() -- see its note. */
    private void markFailed(int dispositionId, long queuedMs, String reason) {
        ContentValues row = new ContentValues();
        row.put("failed", 1);
        row.put("failure_reason", reason);
        getWritableDatabase().update("finishes", row, "disposition_id=? AND queued_ms=?", new String[]{String.valueOf(dispositionId), String.valueOf(queuedMs)});
    }

    /** Puts a failed submission back in the retry pool as-is (same photo, same answers) -- for when
     * whatever the office needed to fix on their end is now fixed, or the rejection turns out to have
     * been a mistake. Scoped by queued_ms so this can't resurrect a submission that's since been
     * superseded by a fresh add(). */
    void retryFailed(int dispositionId, long queuedMs) {
        ContentValues row = new ContentValues();
        row.put("failed", 0);
        row.put("failure_reason", (String) null);
        getWritableDatabase().update("finishes", row, "disposition_id=? AND queued_ms=?", new String[]{String.valueOf(dispositionId), String.valueOf(queuedMs)});
    }

    /** The rep (or office) has decided a failed submission can't be recovered -- deletes the record
     * and its photo for good, rather than leaving it stuck in "needs attention" forever. Scoped by
     * queued_ms for the same reason as retryFailed(). */
    void discardFailed(int dispositionId, long queuedMs, String photoPath) {
        remove(dispositionId, queuedMs);
        new java.io.File(photoPath).delete();
    }

    /** Promotes a pre-migration unassigned record (employee_id 0) to a confirmed owner once the
     * server has verified it's genuinely theirs (see LoginActivity's verifyThenShowUnassignedDialog)
     * -- from then on it's an ordinary record for that employee, not something every signed-in user
     * on this device can see and act on. Scoped by queued_ms like remove()/markFailed(), and requires
     * the row to still be employee_id=0 so this can't reassign an already-claimed record. */
    void claimUnassigned(long employeeId, int dispositionId, long queuedMs) {
        ContentValues row = new ContentValues();
        row.put("employee_id", employeeId);
        getWritableDatabase().update("finishes", row, "disposition_id=? AND queued_ms=? AND employee_id=0", new String[]{String.valueOf(dispositionId), String.valueOf(queuedMs)});
    }

    /** Same as claimUnassigned(), but hands back the row's fresh state afterward -- a caller that
     * already holds the pre-claim Finish object (employeeId still 0) must not keep using it: editing
     * through it would later call add() with that stale employeeId 0 via CONFLICT_REPLACE, silently
     * undoing the claim it just made. Returns null if the row no longer matches (e.g. replaced or
     * already claimed by something else in the meantime), so the caller knows not to proceed with it. */
    Finish claimUnassignedAndReload(long employeeId, int dispositionId, long queuedMs) {
        claimUnassigned(employeeId, dispositionId, queuedMs);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + ROW_COLUMNS + " FROM finishes WHERE disposition_id=? AND queued_ms=? AND employee_id=?",
                new String[]{String.valueOf(dispositionId), String.valueOf(queuedMs), String.valueOf(employeeId)})) {
            return c.moveToFirst() ? readRow(c) : null;
        }
    }

    /** Still-retryable submissions waiting on a connection -- used for "N doors pending upload"
     * messaging. Excludes permanently-failed rows, which need a human, not a retry. */
    int pendingCountForEmployee(long employeeId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM finishes WHERE (employee_id=? OR employee_id=0) AND failed=0", new String[]{String.valueOf(employeeId)})) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    /** Permanently-rejected submissions waiting on this employee to notice -- e.g. a "N doors need
     * attention" badge, distinct from ordinary pending-upload count. */
    int failedCountForEmployee(long employeeId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM finishes WHERE employee_id=? AND failed=1", new String[]{String.valueOf(employeeId)})) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    /** What drain() does with a failed submission -- pulled out as its own pure decision (no Android
     * framework, no network, no SQLite) so it can be covered by a plain JVM unit test without any
     * device, emulator, or Robolectric shadow. */
    enum DrainOutcome {
        /** Permanent to this one record regardless of who it belongs to (e.g. corrupt data) --
         * mark it failed (keep it, don't delete) and move on. */
        SKIP_MARK_FAILED,
        /** A pre-migration record with no confirmed owner (employeeId 0) was rejected as "not
         * found" -- that only means it isn't the CURRENT employee's, not that it belongs to no one.
         * Leave it exactly as-is so a different employee signing into this device later still gets
         * an honest, server-verified shot at it, instead of being locked out by this guess. */
        SKIP_LEAVE_PENDING,
        /** Likely affects every queued item the same way -- stop the whole drain here. */
        STOP
    }

    static DrainOutcome decideDrainOutcome(Exception e, boolean employeeIdKnown) {
        if (e instanceof java.io.FileNotFoundException) {
            // The photo this finish depended on is gone -- nothing left to retry, regardless of
            // whose record this is.
            return DrainOutcome.SKIP_MARK_FAILED;
        }
        if (e instanceof EmployeeApi.ApiError) {
            int code = ((EmployeeApi.ApiError) e).code;
            if (code == 404 && !employeeIdKnown) return DrainOutcome.SKIP_LEAVE_PENDING;
            // 422/404: the server has permanently rejected this exact submission (bad data, or the
            // door was already closed/removed another way) -- retrying the same bytes will never
            // succeed. Anything else (401/403 auth broken, 429 rate-limited, 5xx server error)
            // likely affects every queued item the same way, same as a dead zone.
            return (code == 422 || code == 404) ? DrainOutcome.SKIP_MARK_FAILED : DrainOutcome.STOP;
        }
        // Plain network failure (dead zone, timeout, no response) -- the rest will fail too.
        return DrainOutcome.STOP;
    }

    /** Uploads this employee's queued finishes, oldest first, using decideDrainOutcome() to tell a
     * failure permanent to one record apart from one likely to affect the whole queue -- the next
     * scheduled drain (or the next time this is called) picks back up from wherever a STOP left off.
     * A record permanently rejected is marked failed (see markFailed()) rather than deleted, so a
     * rep's actual work is never silently discarded -- UNLESS its true owner is still unverified
     * (a pre-migration employee_id=0 row rejected under the wrong guess): that one is left pending
     * exactly as it was, so a different employee signing into this device later still gets an honest,
     * server-verified shot at it, instead of being locked out by someone else's failed guess.
     * A 404 is reconciled with the server first (see EmployeeApi.checkFinishStatus()) in case it
     * actually means "this exact attempt already succeeded, you just never saw the response" rather
     * than a real rejection -- matched by submission_id, not just "is the door closed", so a newer
     * correction queued behind an older (now-successful) stale attempt is never mistaken for that
     * older attempt's own success and discarded unsaved. If the reconciliation check itself can't be
     * completed (network/server error), the record is left pending rather than guessed at either way
     * -- a temporary problem checking must never turn into a permanent "needs attention". Synchronized
     * process-wide so this can never run concurrently with another drain() call (from either this or a
     * different DispositionQueue instance) and double-submit the same record. Safe to call from any
     * thread; never touches UI. */
    void drain(String token, long employeeId) {
        synchronized (DRAIN_LOCK) {
            for (Finish f : pendingForEmployee(employeeId)) {
                try {
                    EmployeeApi.submitDispositionFinish(token, f);
                    remove(f.dispositionId, f.queuedMs);
                    new java.io.File(f.photoPath).delete();
                } catch (Exception e) {
                    if (e instanceof EmployeeApi.ApiError && ((EmployeeApi.ApiError) e).code == 404) {
                        EmployeeApi.FinishStatus check = EmployeeApi.checkFinishStatus(token, f.dispositionId);
                        if (check == null) continue; // couldn't verify right now -- leave pending, try again next drain
                        if (check.found && check.ended) {
                            if (f.submissionId.equals(check.submissionId)) {
                                remove(f.dispositionId, f.queuedMs);
                                new java.io.File(f.photoPath).delete();
                            } else {
                                // The door IS closed, but by a different submission than this one (an
                                // older stale attempt, or a different device) -- this attempt's own
                                // answers never actually saved. A real conflict, not a success.
                                markFailed(f.dispositionId, f.queuedMs, "This door was already finished by a different attempt (possibly another device) before this one could save. Review and retry if these answers should still be recorded.");
                            }
                            continue;
                        }
                        // check.found==false (not theirs / doesn't exist), or found&&!ended -- not a
                        // reconciled success; fall through to the normal handling below, which already
                        // knows how to treat an unverified-owner 404 (see decideDrainOutcome()).
                    }
                    DrainOutcome outcome = decideDrainOutcome(e, f.employeeId != 0);
                    if (outcome == DrainOutcome.STOP) break;
                    if (outcome == DrainOutcome.SKIP_MARK_FAILED) markFailed(f.dispositionId, f.queuedMs, e.getMessage());
                    // SKIP_LEAVE_PENDING: nothing to do -- it stays exactly as it was.
                }
            }
        }
    }
}
