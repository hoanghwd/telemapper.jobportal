package com.huynhdous.employeefield.core.tab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import com.huynhdous.employeefield.core.ui.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Keeps the feature tabs: mounts each one into the home screen with its row for the menu, shows one at a time, passes the app's lifecycle
 * to them, and sends camera / permission answers back to the tab that asked -- also when Android closed the app while the camera was open
 * (the tab's saved state is handed back as it is mounted again, and an answer that arrives before that is held until then).
 */
public final class TabManager {
    private static final class Mounted {
        final TabModule module;
        final LinearLayout content;
        final LinearLayout label;

        Mounted(TabModule module, LinearLayout content, LinearLayout label) {
            this.module = module;
            this.content = content;
            this.label = label;
        }
    }

    /** An activity or permission answer that arrived before the tab that asked was mounted again. */
    private static final class EarlyResult {
        final int code, resultCode;
        final Intent data;
        final String[] permissions;
        final int[] grants;

        EarlyResult(int code, int resultCode, Intent data, String[] permissions, int[] grants) {
            this.code = code;
            this.resultCode = resultCode;
            this.data = data;
            this.permissions = permissions;
            this.grants = grants;
        }
    }

    private final Activity activity;
    private final AppHost host;
    private final ScheduleGate gate;
    private final Map<Integer, Mounted> mounted = new LinkedHashMap<>();
    /** Which tab asked for each camera/gallery/permission answer (request codes are unique across the app). */
    private final Map<Integer, TabModule> resultOwners = new HashMap<>();
    private final List<EarlyResult> earlyResults = new ArrayList<>();
    /** What the tabs saved before Android closed the app (null on a normal start); handed to each tab as it is mounted. */
    private Bundle restoredState;
    private boolean homeBuilt;
    private boolean resumed;
    private int current;
    private IntConsumer onSelected = index -> {
    };
    private Runnable afterMenuPick = () -> {
    };

    public TabManager(Activity activity, AppHost host, ScheduleGate gate) {
        this.activity = activity;
        this.host = host;
        this.gate = gate;
    }

    public void setRestoredState(Bundle state) {
        restoredState = state;
    }

    /** Called after a tab is shown (the title bar and the schedule banner follow it). */
    public void setOnSelected(IntConsumer listener) {
        onSelected = listener;
    }

    /** Called after the employee picks a row in the menu (the menu closes). */
    public void setAfterMenuPick(Runnable action) {
        afterMenuPick = action;
    }

    /** Builds a tab's screen inside the home panel and its row for the menu; the tab owns everything inside. */
    public void mount(int slot, TabModule module, LinearLayout panel, int pad) {
        module.attach(host);
        LinearLayout content = module.isGated() ? gate.newColumn(activity) : new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, pad / 2, 0, 0);
        content.setVisibility(View.GONE);
        panel.addView(content);
        module.buildContent(content);
        LinearLayout label = Theme.drawerMenuItem(activity, module.iconRes(), module.title());
        label.setOnClickListener(v -> {
            select(slot);
            afterMenuPick.run();
        });
        mounted.put(slot, new Mounted(module, content, label));
        for (int code : module.requestCodes()) resultOwners.put(code, module);
        if (restoredState != null) module.restoreState(restoredState);
        deliverEarlyResults(module);
        if (resumed) module.onResume();
    }

    /** All tabs are mounted: from now on camera answers go straight to their tab, and the saved state has been used up. */
    public void finishMounting() {
        homeBuilt = true;
        restoredState = null;
        earlyResults.clear();
    }

    /** The menu rows for the tabs in {@code slots} (in that order) that this kind of employee gets. */
    public List<View> labels(String programCode, int... slots) {
        List<View> rows = new ArrayList<>();
        for (int slot : slots) {
            Mounted m = mounted.get(slot);
            if (m != null && m.module.isAvailableFor(programCode)) rows.add(m.label);
        }
        return rows;
    }

    public void select(int index) {
        current = index;
        for (Map.Entry<Integer, Mounted> e : mounted.entrySet()) {
            e.getValue().content.setVisibility(e.getKey() == index ? View.VISIBLE : View.GONE);
            Theme.styleTabItem(e.getValue().label, e.getKey() == index);
        }
        dimGated();
        onSelected.accept(index);
        Mounted shown = mounted.get(index);
        if (shown != null) shown.module.onShown();
    }

    public int current() {
        return current;
    }

    public String titleOf(int index) {
        Mounted m = mounted.get(index);
        return m != null ? m.module.title() : "";
    }

    /** Dims the screens the weekly-schedule gate has turned off (and the time-clock buttons inside the Schedule screen). */
    public void dimGated() {
        float alpha = gate.isLocked() ? 0.4f : 1f;
        for (Mounted m : mounted.values()) if (m.module.isGated()) m.content.setAlpha(alpha);
        for (View v : gate.dimmedColumns()) v.setAlpha(alpha);
    }

    // ---- the app's lifecycle, passed on ----
    public void resume() {
        resumed = true;
        for (Mounted m : mounted.values()) m.module.onResume();
    }

    public void pause() {
        resumed = false;
        for (Mounted m : mounted.values()) m.module.onPause();
    }

    public void saveState(Bundle out) {
        for (Mounted m : mounted.values()) m.module.saveState(out);
    }

    /** The home screen is going away (sign-out): every tab lets go of its views and state. */
    public void detachAll() {
        for (Mounted m : mounted.values()) m.module.onDetach();
        mounted.clear();
        resultOwners.clear();
        earlyResults.clear();
        homeBuilt = false;
    }

    // ---- camera / permission answers ----
    public void startActivityForResult(TabModule owner, Intent intent, int requestCode) {
        resultOwners.put(requestCode, owner);
        activity.startActivityForResult(intent, requestCode);
    }

    public void requestPermissions(TabModule owner, String[] permissions, int requestCode) {
        resultOwners.put(requestCode, owner);
        activity.requestPermissions(permissions, requestCode);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        TabModule owner = resultOwners.get(requestCode);
        if (owner != null) owner.onActivityResult(requestCode, resultCode, data);
        else if (!homeBuilt) earlyResults.add(new EarlyResult(requestCode, resultCode, data, null, null));
    }

    public void onPermissionResult(int requestCode, String[] permissions, int[] grants) {
        TabModule owner = resultOwners.get(requestCode);
        if (owner != null) owner.onPermissionResult(requestCode, permissions, grants);
        else if (!homeBuilt) earlyResults.add(new EarlyResult(requestCode, 0, null, permissions, grants));
    }

    private void deliverEarlyResults(TabModule module) {
        for (Iterator<EarlyResult> it = earlyResults.iterator(); it.hasNext(); ) {
            EarlyResult r = it.next();
            if (resultOwners.get(r.code) != module) continue;
            it.remove();
            if (r.permissions != null) module.onPermissionResult(r.code, r.permissions, r.grants);
            else module.onActivityResult(r.code, r.resultCode, r.data);
        }
    }
}
