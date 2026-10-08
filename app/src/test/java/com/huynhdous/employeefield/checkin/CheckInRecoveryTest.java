package com.huynhdous.employeefield.checkin;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.huynhdous.employeefield.core.session.SessionWork;
import com.huynhdous.employeefield.core.tab.AppHost;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = CheckInRecoveryTest.FileProviderShadow.class)
public class CheckInRecoveryTest {
    // FileProvider's Android-only slash handling cannot resolve Windows-hosted Robolectric files.
    // Isolate URI construction; the real manifest/provider configuration is checked by the Android build.
    @org.robolectric.annotation.Implements(androidx.core.content.FileProvider.class)
    public static class FileProviderShadow {
        @org.robolectric.annotation.Implementation
        protected static android.net.Uri getUriForFile(android.content.Context context, String authority, File file) {
            return new android.net.Uri.Builder().scheme("content").authority(authority)
                    .appendPath("test-photos").appendPath(file.getName()).build();
        }
    }

    private static void set(CheckInTab tab, String name, Object value) throws Exception {
        Field f = CheckInTab.class.getDeclaredField(name); f.setAccessible(true); f.set(tab, value);
    }
    private static Object get(CheckInTab tab, String name) throws Exception {
        Field f = CheckInTab.class.getDeclaredField(name); f.setAccessible(true); return f.get(tab);
    }

    /** A host for one signed-in employee. {@code current} says whether the screen that started an upload is still the live one. */
    private static AppHost host(Activity activity, String token, boolean current) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "activity": return activity;
                case "token": return token;
                case "employeeId": return 5L;
                case "isCurrent": return current;
                case "leaveOutcome": {   // the same rule as AppHost.leaveOutcome: post for the next screen if the sign-in is unchanged
                    SessionWork.Lease w = (SessionWork.Lease) args[0];
                    if (w.matches(token, 5L)) SessionWork.postOutcome(w.token, (SessionWork.Outcome) args[1]);
                    return null;
                }
                default: return null;
            }
        };
        return (AppHost) Proxy.newProxyInstance(AppHost.class.getClassLoader(), new Class[]{AppHost.class}, handler);
    }

    @SuppressWarnings("unchecked")
    private static File addAcceptedPhoto(CheckInTab tab) throws Exception {
        File photo = File.createTempFile("checkin-test", ".jpg");
        Files.write(photo.toPath(), new byte[]{1, 2, 3});
        photo.deleteOnExit();
        ((List<File>) get(tab, "acceptedStorePhotos")).add(photo);
        return photo;
    }

    private static void invoke(CheckInTab tab, String method, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m = CheckInTab.class.getDeclaredMethod(method, types);
        m.setAccessible(true); m.invoke(tab, args);
    }

    @Test public void startingAnotherArrivalKeepsTheWaitingCheckInAndPhotos() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "draft-overwrite", true));
        JSONObject original = new JSONObject().put("assignment_id", 11).put("site_name", "First store");
        set(tab, "activeAssignment", original); set(tab, "unsent", true);
        File photo = addAcceptedPhoto(tab);
        invoke(tab, "beginArrival", new Class<?>[]{JSONObject.class},
                new JSONObject().put("assignment_id", 22).put("site_name", "Second store"));
        assertSame(original, get(tab, "activeAssignment"));
        assertEquals(Boolean.TRUE, get(tab, "unsent"));
        assertEquals(1, ((List<?>) get(tab, "acceptedStorePhotos")).size());
        assertTrue(photo.exists());
    }

    @Test public void discardDeletesOwnedPhotosButLeavesOtherFilesAlone() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "draft-discard", true));
        File selfie = new File(activity.getCacheDir(), "selfie_discard.jpg");
        File store = new File(activity.getCacheDir(), "store_discard.jpg");
        Files.write(selfie.toPath(), new byte[]{1}); Files.write(store.toPath(), new byte[]{2});
        File unrelated = addAcceptedPhoto(tab);
        set(tab, "pendingSelfieFile", selfie); set(tab, "pendingStorePhotoFile", store);
        invoke(tab, "clearFlow", new Class<?>[]{});
        assertFalse(selfie.exists()); assertFalse(store.exists());
        assertTrue(unrelated.exists());
    }

    @Test public void detachingForRotationDoesNotDeleteCapturedPhotos() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "draft-detach", true));
        File selfie = new File(activity.getCacheDir(), "selfie_rotation.jpg");
        Files.write(selfie.toPath(), new byte[]{1});
        set(tab, "pendingSelfieFile", selfie);
        tab.onDetach();
        assertTrue(selfie.exists());
    }

    @SuppressWarnings("unchecked")
    @Test public void readyCheckInRecoversAfterRestartWithoutSavedActivityState() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab original = new CheckInTab(); original.attach(host(activity, "durable-restart", true));
        CheckInDraftStore files = new CheckInDraftStore(activity, 5);
        File selfie = files.newPhoto("selfie_"); File store = files.newPhoto("store_");
        Files.write(selfie.toPath(), new byte[]{1}); Files.write(store.toPath(), new byte[]{2});
        set(original, "pendingSelfieFile", selfie);
        ((List<File>) get(original, "acceptedStorePhotos")).add(store);
        set(original, "activeAssignment", new JSONObject().put("assignment_id", 31).put("site_name", "Saved store"));
        set(original, "activeLat", 34.25); set(original, "activeLon", -118.5);
        invoke(original, "persistReadyDraft", new Class<?>[]{});
        original.onDetach();

        CheckInTab fresh = new CheckInTab(); fresh.attach(host(activity, "durable-restart", true));
        fresh.buildContent(new LinearLayout(activity)); // No Bundle from the old activity.
        assertEquals(Boolean.TRUE, get(fresh, "unsent"));
        assertEquals(31, ((JSONObject) get(fresh, "activeAssignment")).getInt("assignment_id"));
        assertEquals(1, ((List<?>) get(fresh, "acceptedStorePhotos")).size());
        assertEquals(34.25, (Double) get(fresh, "activeLat"), 0.0);
        invoke(fresh, "clearFlow", new Class<?>[]{});
        assertNull(files.load()); assertFalse(selfie.exists()); assertFalse(store.exists());
    }

    @Test public void draftCleanupIsEmployeeScopedAndIgnoresOldUploadIdentities() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInDraftStore mine = new CheckInDraftStore(activity, 5), other = new CheckInDraftStore(activity, 6);
        File selfie = mine.newPhoto("selfie_"); File store = mine.newPhoto("store_");
        Files.write(selfie.toPath(), new byte[]{1}); Files.write(store.toPath(), new byte[]{2});
        mine.save("new-draft", new JSONObject().put("assignment_id", 4), selfie,
                java.util.Collections.singletonList(store), 34.0, -118.0);
        assertNull(other.load());
        other.deletePhoto(selfie); assertTrue(selfie.exists());
        mine.clear("old-draft"); assertNotNull(mine.load()); assertTrue(store.exists());
        mine.clear("new-draft"); assertNull(mine.load()); assertFalse(selfie.exists()); assertFalse(store.exists());
    }

    @Test public void lateUploadOutcomeDoesNotClearAnotherDraft() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String token = "late-draft-outcome";
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, token, true));
        tab.buildContent(new LinearLayout(activity));
        JSONObject newer = new JSONObject().put("assignment_id", 99);
        set(tab, "activeAssignment", newer); set(tab, "draftId", "newer");
        SessionWork.postOutcome(token, new SessionWork.Outcome("checkin", true, false, null, "older"));
        tab.onUploadOutcome();
        assertSame(newer, get(tab, "activeAssignment"));
    }

    @Test public void discardCannotRemoveFilesStillBeingUploaded() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "draft-busy", true));
        File selfie = new File(activity.getCacheDir(), "selfie_busy.jpg");
        Files.write(selfie.toPath(), new byte[]{1}); set(tab, "pendingSelfieFile", selfie);
        try (SessionWork.Lease work = SessionWork.begin("draft-busy", 5)) {
            invoke(tab, "clearFlow", new Class<?>[]{});
            assertTrue(selfie.exists()); assertSame(selfie, get(tab, "pendingSelfieFile"));
        }
        invoke(tab, "clearFlow", new Class<?>[]{});
        assertFalse(selfie.exists());
    }

    @SuppressWarnings("unchecked")
    @Test public void completedUploadDuringRotationCannotRestoreAStaleSavedDraft() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String token = "completed-durable-draft";
        CheckInTab old = new CheckInTab(); old.attach(host(activity, token, false));
        CheckInDraftStore files = new CheckInDraftStore(activity, 5);
        File selfie = files.newPhoto("selfie_"); File store = files.newPhoto("store_");
        Files.write(selfie.toPath(), new byte[]{1}); Files.write(store.toPath(), new byte[]{2});
        set(old, "pendingSelfieFile", selfie);
        ((List<File>) get(old, "acceptedStorePhotos")).add(store);
        set(old, "activeAssignment", new JSONObject().put("assignment_id", 10));
        invoke(old, "persistReadyDraft", new Class<?>[]{});
        Bundle snapshot = new Bundle(); old.saveState(snapshot);
        old.onDetach();
        SessionWork.Lease work = SessionWork.begin(token, 5); work.close();
        old.finishSubmission(work, null);
        assertNull(files.load()); assertFalse(selfie.exists()); assertFalse(store.exists());
        CheckInTab fresh = new CheckInTab(); fresh.attach(host(activity, token, true));
        fresh.buildContent(new LinearLayout(activity)); fresh.restoreState(snapshot);
        assertNull(get(fresh, "activeAssignment"));
        assertEquals(Boolean.FALSE, get(fresh, "unsent"));
    }

    @Test public void cameraRecreationPreservesVerifiedCoordinates() throws Exception {
        CheckInTab original = new CheckInTab();
        set(original, "activeLat", 34.25); set(original, "activeLon", -118.5);
        Bundle state = new Bundle(); original.saveState(state);
        CheckInTab restored = new CheckInTab(); restored.restoreState(state);
        assertEquals(34.25, (Double)get(restored, "activeLat"), 0.0);
        assertEquals(-118.5, (Double)get(restored, "activeLon"), 0.0);
    }

    @Test public void transientUploadFailureKeepsAssignmentForRetry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "checkin-retry-test", true));
        JSONObject assignment = new JSONObject().put("assignment_id", 123);
        set(tab, "activeAssignment", assignment);
        set(tab, "activeLat", 34.25);
        addAcceptedPhoto(tab);
        SessionWork.Lease work = SessionWork.begin("checkin-retry-test", 5);
        work.close();
        tab.finishSubmission(work, "Connection timed out");
        assertSame(assignment, get(tab, "activeAssignment"));
        assertEquals(34.25, (Double)get(tab, "activeLat"), 0.0);
        assertEquals(Boolean.TRUE, get(tab, "unsent"));
        tab.finishSubmission(work, null);
        assertNull(get(tab, "activeAssignment"));
    }

    @Test public void refusedCheckInStartsOverInsteadOfOfferingAHopelessRetry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        CheckInTab tab = new CheckInTab(); tab.attach(host(activity, "checkin-refused-test", true));
        set(tab, "activeAssignment", new JSONObject().put("assignment_id", 9));
        addAcceptedPhoto(tab);
        SessionWork.Lease work = SessionWork.begin("checkin-refused-test", 5);
        work.close();
        tab.finishSubmission(work, "You are too far from the worksite.", false);
        assertNull(get(tab, "activeAssignment"));
        assertEquals(Boolean.FALSE, get(tab, "unsent"));
    }

    @Test public void waitingCheckInSurvivesRecreation() throws Exception {
        CheckInTab original = new CheckInTab();
        set(original, "activeAssignment", new JSONObject().put("assignment_id", 7).put("site_name", "Costco"));
        File photo = addAcceptedPhoto(original);
        set(original, "unsent", true);
        Bundle state = new Bundle(); original.saveState(state);

        CheckInTab restored = new CheckInTab(); restored.restoreState(state);
        assertEquals(Boolean.TRUE, get(restored, "unsent"));
        assertEquals("Costco", ((JSONObject) get(restored, "activeAssignment")).getString("site_name"));
        assertEquals(1, ((List<?>) get(restored, "acceptedStorePhotos")).size());

        // the photos are gone (cache cleared): nothing is waiting any more
        assertTrue(photo.delete());
        CheckInTab lost = new CheckInTab(); lost.restoreState(state);
        assertEquals(Boolean.FALSE, get(lost, "unsent"));
    }

    @Test public void resultOfAnUploadThatFinishedDuringARotationReachesTheNewScreen() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String token = "checkin-rotation-test";

        // The old screen is gone when the upload ends without a connection: it leaves the result behind.
        CheckInTab old = new CheckInTab(); old.attach(host(activity, token, false));
        SessionWork.Lease work = SessionWork.begin(token, 5);
        work.close();
        old.finishSubmission(work, "Unable to check in. Check your connection and try again.", true);

        // The new screen was restored with the same pending check-in and picks the result up.
        CheckInTab fresh = new CheckInTab(); fresh.attach(host(activity, token, true));
        fresh.buildContent(new LinearLayout(activity));
        set(fresh, "activeAssignment", new JSONObject().put("assignment_id", 3).put("site_name", "Walmart"));
        addAcceptedPhoto(fresh);
        fresh.onUploadOutcome();

        assertEquals(Boolean.TRUE, get(fresh, "unsent"));
        assertNotNull(get(fresh, "activeAssignment"));
        assertTrue(SessionWork.takeOutcomes(token, "checkin").isEmpty());   // taken exactly once
    }
}
