package com.huynhdous.employeefield.jobs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import com.huynhdous.employeefield.trip.TripMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MapGesturesTest {
    private static final class Parent extends LinearLayout {
        boolean blocked;
        Parent(Context context) { super(context); }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            blocked = disallow;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static void send(View.OnTouchListener listener, WebView map, int action) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, 30, 30, 0);
        try { assertFalse(listener.onTouch(map, event)); }
        finally { event.recycle(); }
    }

    @Test public void draggingAndPinchingStayInsideMapUntilRelease() {
        Context context = RuntimeEnvironment.getApplication();
        Parent parent = new Parent(context);
        WebView map = new WebView(context);
        parent.addView(map);
        TripMap.protectGestures(map);
        View.OnTouchListener listener = shadowOf(map).getOnTouchListener();
        send(listener, map, MotionEvent.ACTION_DOWN);
        assertTrue(parent.blocked);
        send(listener, map, MotionEvent.ACTION_MOVE);
        assertTrue(parent.blocked);
        send(listener, map, MotionEvent.ACTION_POINTER_DOWN);
        assertTrue(parent.blocked);
        send(listener, map, MotionEvent.ACTION_POINTER_UP);
        assertTrue(parent.blocked);
        send(listener, map, MotionEvent.ACTION_UP);
        assertFalse(parent.blocked);
        map.destroy();
    }

    @Test public void cancelledGestureReleasesParentScrolling() {
        Context context = RuntimeEnvironment.getApplication();
        Parent parent = new Parent(context);
        WebView map = new WebView(context);
        parent.addView(map);
        TripMap.protectGestures(map);
        View.OnTouchListener listener = shadowOf(map).getOnTouchListener();
        send(listener, map, MotionEvent.ACTION_DOWN);
        assertTrue(parent.blocked);
        send(listener, map, MotionEvent.ACTION_CANCEL);
        assertFalse(parent.blocked);
        map.destroy();
    }
}