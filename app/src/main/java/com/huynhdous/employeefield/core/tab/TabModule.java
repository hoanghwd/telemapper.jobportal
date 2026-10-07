package com.huynhdous.employeefield.core.tab;

import android.app.Activity;

import android.widget.LinearLayout;

/**
 * One feature screen of the app (Programs, Events, Trip ...). A tab lives in its own package, builds its own views, loads its own data
 * and keeps its own state; the activity only mounts it in the menu and shows or hides it.
 *
 * <p>To add a screen: extend this class, then mount it in {@code HomeScreen.show()} with {@code tabs.mount(slot, new MyTab(), ...)} and give it a slot in {@code Tabs}.
 */
public abstract class TabModule {
    private AppHost host;

    /** Called by the activity once, before {@link #buildContent}. */
    public final void attach(AppHost host) {
        this.host = host;
    }

    protected final AppHost host() {
        return host;
    }

    /** The screen the tab lives in (a Context for views and dialogs). */
    protected final Activity context() {
        return host.activity();
    }

    protected final float density() {
        return host.activity().getResources().getDisplayMetrics().density;
    }

    /** Name in the menu and in the title bar above the screen. */
    public abstract String title();

    /** Icon in the menu (a drawable resource). */
    public abstract int iconRes();

    /** Whether this tab is in the menu for this kind of employee ("D2D" / "S2S"). Default: everyone. */
    public boolean isAvailableFor(String programCode) {
        return true;
    }

    /** Whether the weekly-schedule gate turns this screen off (dims it and swallows touches) while the week is unconfirmed. Default yes. */
    public boolean isGated() {
        return true;
    }

    /** Fill the (empty, padded, vertical) column that will hold this screen. Runs once each time the home screen is built. */
    public abstract void buildContent(LinearLayout content);

    /** The app came to the front / went to the background (while signed in). Start and stop anything that polls here. */
    public void onResume() {
    }

    public void onPause() {
    }

    /** The employee opened this screen: load or refresh its data. */
    public abstract void onShown();

    /**
     * The activity-result / permission request codes this tab uses (unique across the app). The shell registers them when the tab is
     * mounted, so an answer that arrives after Android recreated the activity (the camera app can close this one on a low-RAM phone)
     * still reaches the tab that asked.
     */
    public int[] requestCodes() {
        return new int[0];
    }

    /** Write what must survive Android closing the app while the camera is open (pending photo paths, the half-finished flow ...). */
    public void saveState(android.os.Bundle out) {
    }

    /** Read back what {@link #saveState} wrote; runs right after {@link #buildContent} when the activity was recreated. */
    public void restoreState(android.os.Bundle state) {
    }

    /** The answer to a {@link AppHost#startActivityForResult} this tab made (request codes must be unique across the app). */
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
    }

    /** The answer to a {@link AppHost#requestPermissions} this tab made. */
    public void onPermissionResult(int requestCode, String[] permissions, int[] results) {
    }

    /** The home screen is being torn down (sign-out or session end): let go of views and state. */
    public void onDetach() {
    }
}
