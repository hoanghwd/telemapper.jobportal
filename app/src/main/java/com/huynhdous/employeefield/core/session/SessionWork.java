package com.huynhdous.employeefield.core.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Process-wide mutation ownership, including across activity recreation. Never persists tokens. */
public final class SessionWork {
    private static final Map<String, Lease> active = new HashMap<>();
    private static final Set<String> ended = new HashSet<>();

    private SessionWork() { }

    public static synchronized Lease begin(String token, long employeeId) {
        if (token == null || token.isEmpty() || employeeId <= 0 || ended.contains(token) || active.containsKey(token)) return null;
        Lease lease = new Lease(token, employeeId);
        active.put(token, lease);
        return lease;
    }

    public static synchronized boolean isBusy(String token) {
        return active.containsKey(token);
    }

    public static synchronized boolean hasEnded(String token) {
        return ended.contains(token);
    }

    public static synchronized void end(String token) {
        if (token == null || token.isEmpty()) return;
        ended.add(token);
        outcomes.remove(token);
    }

    /** Background-only: Stop ends GPS immediately, but revocation waits for an existing upload. */
    public static synchronized void awaitIdle(String token) throws InterruptedException {
        while (active.containsKey(token)) SessionWork.class.wait();
    }

    /** What an upload reported after the screen that started it was gone (a rotation): kept for the screen that replaced it. */
    public static final class Outcome {
        public final String kind;
        public final boolean success;
        /** The upload failed but trying again may work (no connection, server trouble). */
        public final boolean retryable;
        public final String message;
        /** Optional draft identity: a late result must not change a different draft. */
        public final String reference;

        public Outcome(String kind, boolean success, boolean retryable, String message) {
            this(kind, success, retryable, message, null);
        }

        public Outcome(String kind, boolean success, boolean retryable, String message, String reference) {
            this.reference = reference;
            this.kind = kind;
            this.success = success;
            this.retryable = retryable;
            this.message = message;
        }
    }

    private static final Map<String, java.util.List<Outcome>> outcomes = new HashMap<>();
    private static Runnable outcomeListener;

    /** Leaves an outcome for the current screen to pick up, and wakes that screen if there is one. */
    public static void postOutcome(String token, Outcome outcome) {
        Runnable wake;
        synchronized (SessionWork.class) {
            if (token == null || token.isEmpty() || ended.contains(token)) return;
            outcomes.computeIfAbsent(token, t -> new java.util.ArrayList<>()).add(outcome);
            wake = outcomeListener;
        }
        if (wake != null) wake.run();
    }

    /** Takes (and removes) the waiting outcomes of one kind for this sign-in. */
    public static synchronized java.util.List<Outcome> takeOutcomes(String token, String kind) {
        java.util.List<Outcome> mine = new java.util.ArrayList<>();
        java.util.List<Outcome> all = outcomes.get(token);
        if (all == null) return mine;
        for (java.util.Iterator<Outcome> it = all.iterator(); it.hasNext(); ) {
            Outcome o = it.next();
            if (o.kind.equals(kind)) {
                mine.add(o);
                it.remove();
            }
        }
        if (all.isEmpty()) outcomes.remove(token);
        return mine;
    }

    /** The live screen asks to be woken (on any thread) when an outcome arrives. */
    public static synchronized void setOutcomeListener(Runnable listener) {
        outcomeListener = listener;
    }

    public static synchronized void removeOutcomeListener(Runnable listener) {
        if (outcomeListener == listener) outcomeListener = null;
    }

    public static final class Lease implements AutoCloseable {
        public final String token;
        public final long employeeId;

        private Lease(String token, long employeeId) {
            this.token = token;
            this.employeeId = employeeId;
        }

        public boolean matches(String currentToken, long currentEmployeeId) {
            synchronized (SessionWork.class) {
                return token.equals(currentToken) && employeeId == currentEmployeeId && !ended.contains(token);
            }
        }

        @Override
        public void close() {
            synchronized (SessionWork.class) {
                if (active.get(token) == this) active.remove(token);
                SessionWork.class.notifyAll();
            }
        }
    }
}
