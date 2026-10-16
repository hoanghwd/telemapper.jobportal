package com.huynhdous.employeefield.core.session;

/** Who is signed in right now. A plain holder: the sign-in flow fills it, everything else reads it. */
public final class Session {
    /** The sign-in token ("" = nobody signed in). */
    public String token = "";
    public long employeeId;
    /** When the sign-in stops being valid (epoch milliseconds). */
    public long expiresMs;
    /** "D2D" or "S2S" as set by the office; may be null. */
    public String programCode;
    /** Whether this worker keeps a time sheet. False for work paid per job (a delivery service): the Time Sheet and the hours are then not shown. */
    public boolean timeTracking = true;
    public String employeeName = "";
    public String username = "";

    /** Forget the signed-in person. */
    public void clear() {
        token = "";
        employeeId = 0;
        expiresMs = 0;
        programCode = null;
        timeTracking = true;
        employeeName = "";
        username = "";
    }
}
