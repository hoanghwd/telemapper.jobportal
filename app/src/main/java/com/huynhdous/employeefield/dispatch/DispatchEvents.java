package com.huynhdous.employeefield.dispatch;

import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tells the screens that something changed in dispatch the moment it happens, so they do not wait for their next timer. Work offers are noticed by a
 * background service (the one that plays the offer sound), which has no screen of its own: it calls {@link #offerArrived()}, and a screen that cares
 * (the Jobs map) listens with {@link #listen(Runnable)}.
 */
public final class DispatchEvents {
    private static final List<Runnable> OFFER_LISTENERS = new CopyOnWriteArrayList<>();

    private DispatchEvents() {
    }

    /** Runs {@code listener} on the main thread each time a new work offer arrives; call the returned Runnable to stop listening. */
    public static Runnable listen(Runnable listener) {
        OFFER_LISTENERS.add(listener);
        return () -> OFFER_LISTENERS.remove(listener);
    }

    /** A new work offer arrived (its notification is sounding now). Safe to call from any thread. */
    public static void offerArrived() {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (Runnable listener : OFFER_LISTENERS) listener.run();
        });
    }
}
