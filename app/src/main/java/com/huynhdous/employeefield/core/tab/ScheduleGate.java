package com.huynhdous.employeefield.core.tab;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The weekly-schedule gate. When the employee has shifts this week but has not confirmed the schedule, every action in the app is turned
 * off (dimmed and untouchable) and a banner says to confirm first. The Schedule screen owns the facts (it reads the "schedule" reply and
 * calls {@link #set}); the app shell shows the banner and dims the screens when told the state changed; every screen's column is built with
 * {@link #newColumn} so it swallows touches while locked.
 */
public final class ScheduleGate {
    public interface Listener {
        void onGateChanged();
    }

    private boolean locked;
    private String week;
    private Listener listener;
    private final List<View> dimmedColumns = new ArrayList<>();

    public boolean isLocked() {
        return locked;
    }

    /** Monday of the week the gate is about (yyyy-MM-dd), or null. */
    public String week() {
        return week;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Record the state without telling anyone yet (a screen being drawn does this first, then calls {@link #notifyChanged} when done). */
    public void setState(boolean locked, String week) {
        this.locked = locked;
        this.week = week;
    }

    /** Record the state and update the app (banner, dimming) straight away. */
    public void set(boolean locked, String week) {
        setState(locked, week);
        notifyChanged();
    }

    public void notifyChanged() {
        if (listener != null) listener.onGateChanged();
    }

    /** A column that swallows every touch meant for its children while the gate is locked (scrolling still works). */
    public LinearLayout newColumn(Context context) {
        return new Column(context);
    }

    /** Same, and also dimmed by the app shell while locked (for pieces inside a screen that must go grey but are not a whole screen). */
    public LinearLayout newDimmedColumn(Context context) {
        LinearLayout column = new Column(context);
        dimmedColumns.add(column);
        return column;
    }

    public void clearDimmedColumns() {
        dimmedColumns.clear();
    }

    public List<View> dimmedColumns() {
        return dimmedColumns;
    }

    private final class Column extends LinearLayout {
        Column(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return locked || super.onInterceptTouchEvent(ev);
        }
    }
}
