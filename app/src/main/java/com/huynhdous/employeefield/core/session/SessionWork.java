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
        if (token != null && !token.isEmpty()) ended.add(token);
    }

    /** Background-only: Stop ends GPS immediately, but revocation waits for an existing upload. */
    public static synchronized void awaitIdle(String token) throws InterruptedException {
        while (active.containsKey(token)) SessionWork.class.wait();
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
