package com.huynhdous.employeefield.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.huynhdous.employeefield.auth.AuthFlow;
import com.huynhdous.employeefield.auth.SignOut;
import com.huynhdous.employeefield.core.net.Requester;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.ScheduleGate;
import com.huynhdous.employeefield.core.tab.TabManager;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.tab.Tabs;
import com.huynhdous.employeefield.home.HomeScreen;
import com.huynhdous.employeefield.location.TrackingStarter;

import org.json.JSONObject;

/**
 * Wires the app together and does nothing else: who is signed in ({@link Session}), the way in ({@link AuthFlow}), the home screen with its
 * tabs ({@link HomeScreen}, {@link TabManager}), server calls ({@link Requester}), tracking ({@link TrackingStarter}) and sign-out
 * ({@link SignOut}). It is also the {@link AppHost} every tab talks to, so each answer here is a one-line hand-over to the part that does it.
 */
public final class App implements AppHost, Requester.Listener {
    private static final String STATE_TAB = "current_tab";

    private final Activity activity;
    private final Session session = new Session();
    private final ScheduleGate gate = new ScheduleGate();
    private final Requester requester;
    private final TabManager tabs;
    private final TrackingStarter tracking;
    private final SignOut signOut;
    private final AuthFlow auth;
    private HomeScreen home;
    private int firstTab = Tabs.SCHEDULE;

    public App(Activity activity, Bundle savedState) {
        this.activity = activity;
        requester = new Requester(activity, session, this);
        tabs = new TabManager(activity, this, gate);
        tracking = new TrackingStarter(activity, session);
        signOut = new SignOut(activity, session, requester, tracking, this::showSignIn);
        auth = new AuthFlow(activity, session, requester, signOut, this::showHome);
        gate.setListener(this::applyGate);
        if (savedState != null) {
            firstTab = savedState.getInt(STATE_TAB, Tabs.SCHEDULE);
            tabs.setRestoredState(savedState);   // each tab reads its own part when it is mounted
        }
    }

    /** Open the app: carry on a sign-in that is still running, or ask for one. */
    public void start() {
        String running = TrackingStarter.runningToken();
        if (running.isEmpty()) {
            showSignIn();
            return;
        }
        session.token = running;
        try {
            auth.verify();
        } catch (Exception e) {
            showSignIn();
        }
    }

    private void showHome() {
        home = new HomeScreen(activity, session, gate, tabs, this, signOut::ifIdle);
        home.show(firstTab);
        firstTab = Tabs.SCHEDULE;
    }

    private void showSignIn() {
        tabs.detachAll();
        home = null;
        session.clear();
        auth.showSignIn();
    }

    private void applyGate() {
        if (home != null) home.applyGate();
        else tabs.dimGated();
    }

    // ---- what Android tells the app (forwarded by main) ----
    public void resume() {
        tabs.resume();
    }

    public void pause() {
        tabs.pause();
    }

    public void saveState(Bundle out) {
        out.putInt(STATE_TAB, tabs.current());
        tabs.saveState(out);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        tabs.onActivityResult(requestCode, resultCode, data);
    }

    public void onPermissionResult(int requestCode, String[] permissions, int[] grants) {
        if (!tracking.onPermissionResult(requestCode)) tabs.onPermissionResult(requestCode, permissions, grants);
    }

    /** Back closes the menu first; returns true when it did. */
    public boolean onBack() {
        return home != null && home.onBack();
    }

    // ---- Requester.Listener ----
    @Override
    public void sessionExpired() {
        tracking.stop("Session expired. Sign in again.", false);
        showSignIn();
    }

    @Override
    public void unusableAnswer() {
        showSignIn();
    }

    // ---- AppHost: what the tabs may ask ----
    @Override
    public Activity activity() {
        return activity;
    }

    @Override
    public String token() {
        return session.token;
    }

    @Override
    public long employeeId() {
        return session.employeeId;
    }

    @Override
    public String programCode() {
        return session.programCode;
    }

    @Override
    public String employeeName() {
        return session.employeeName;
    }

    @Override
    public String username() {
        return session.username;
    }

    @Override
    public void request(String action, JSONObject body, Button button, Result result) {
        requester.request(action, body, button, result, false, null);
    }

    @Override
    public void request(String action, JSONObject body, Button button, Result result, boolean independentRead, ProblemHandler onProblem) {
        requester.request(action, body, button, result, independentRead, onProblem);
    }

    @Override
    public ScheduleGate gate() {
        return gate;
    }

    @Override
    public void selectTab(int slot) {
        tabs.select(slot);
    }

    @Override
    public void startActivityForResult(TabModule owner, Intent intent, int requestCode) {
        tabs.startActivityForResult(owner, intent, requestCode);
    }

    @Override
    public void requestPermissions(TabModule owner, String[] permissions, int requestCode) {
        tabs.requestPermissions(owner, permissions, requestCode);
    }

    @Override
    public void startDoorAtPoint(long pointId, double lat, double lon) {
        if (home != null) home.doors().startAtPoint(pointId, lat, lon);
    }

    @Override
    public void enableTracking() {
        tracking.enable();
    }

    @Override
    public void avatarChanged() {
        if (home != null) home.avatarChanged();
    }
}
