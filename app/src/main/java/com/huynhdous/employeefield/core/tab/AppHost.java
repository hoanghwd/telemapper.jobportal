package com.huynhdous.employeefield.core.tab;

import android.app.Activity;
import android.widget.Button;

import org.json.JSONObject;

/**
 * What a feature tab may ask of the app around it: who is signed in, how to call the server, how to jump to another screen.
 * The activity implements this; a tab never reaches into the activity itself, so each tab can be read (and changed) on its own.
 */
public interface AppHost {
    /** The screen the tab lives in: use it as the Context for views and dialogs, and to run code on the UI thread. */
    Activity activity();

    /** The current sign-in token. Ask each time -- it changes when the employee signs in again. */
    String token();

    long employeeId();

    /** Reserve a mutation until its worker completes; shared with sign-out and recreated activities. */
    com.huynhdous.employeefield.core.session.SessionWork.Lease beginUpload();

    default boolean isCurrent(com.huynhdous.employeefield.core.session.SessionWork.Lease work) {
        return !activity().isFinishing() && !activity().isDestroyed() && work.matches(token(), employeeId());
    }

    /** "D2D" or "S2S" (door-to-door or in-store), as set by the office; may be null. */
    String programCode();

    /** Call the server (one call at a time) and run {@code result} on the UI thread; an error shows a pop-up. */
    void request(String action, JSONObject body, Button button, Result result);

    /**
     * The full form. {@code independentRead} lets a read run alongside another call without blocking it (and be dropped if the employee has
     * signed out meanwhile); {@code onProblem} may take over a specific server refusal (e.g. 409) instead of the generic pop-up.
     */
    void request(String action, JSONObject body, Button button, Result result, boolean independentRead, ProblemHandler onProblem);

    /** The weekly-schedule gate shared by every screen (see {@link ScheduleGate}). */
    ScheduleGate gate();

    /** The signed-in employee's display name and sign-in name. */
    String employeeName();

    String username();

    /** Switch to another screen; use the constants in {@link Tabs}. */
    void selectTab(int slot);

    /** Start another app's screen (camera, gallery ...); its answer comes back to {@code owner.onActivityResult}. */
    void startActivityForResult(TabModule owner, android.content.Intent intent, int requestCode);

    /** Ask for permissions; the answer comes back to {@code owner.onPermissionResult}. */
    void requestPermissions(TabModule owner, String[] permissions, int requestCode);

    /** The employee tapped "I'm at the door" on a map position: start (confirm) a door there. */
    void startDoorAtPoint(long pointId, double lat, double lon);

    /** Make sure the work-location tracking is running; asks for the location and notification permissions first if they are missing. */
    void enableTracking();

    /** The employee's profile photo changed: refresh the copy shown in the greeting at the top. */
    void avatarChanged();

    interface Result {
        void accept(JSONObject value) throws Exception;
    }

    /** Return true if you handled the refusal yourself (no generic pop-up is shown then). */
    interface ProblemHandler {
        boolean handle(int status, String problem);
    }
}
