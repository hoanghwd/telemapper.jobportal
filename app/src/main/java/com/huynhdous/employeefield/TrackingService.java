package com.huynhdous.employeefield;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;

import org.json.*;

import java.util.UUID;
import java.util.concurrent.*;

public final class TrackingService extends Service implements LocationListener {
    public static volatile String activeToken = "", status = "Tracking is off.";
    public static volatile long activeEmployee = 0;
    private static final String CHANNEL = "employee_gps", STOP = "com.huynhdous.employeefield.STOP_TRACKING";
    private static final int NOTIFICATION = 30;
    // Same knob as the server's EMPLOYEE_POLLING_PER_MIN env var, so both sides can be tuned together.
    static final long POLL_INTERVAL_MS = 60_000L / Math.max(1, BuildConfig.EMPLOYEE_POLLING_PER_MIN);
    private static final long FIX_DEDUPE_NANOS = Math.max(1_000_000_000L, (POLL_INTERVAL_MS - 1000) * 1_000_000L);
    private static final long FIX_MAX_AGE_NANOS = POLL_INTERVAL_MS * 2 * 1_000_000L;
    // Reminder-only meal-period nudge (no blocking) — mirrors the server's compliance check
    // (WeeklyTimesheet::mealPeriodReport / MEAL_PERIOD_WINDOW_START_HOURS,END_HOURS env vars).
    // Kept as constants here rather than fetched from the server: this is a soft reminder, not an
    // enforcement mechanism, so a mismatch after an env change is low-stakes — update both places
    // together if the office ever changes these.
    private static final String MEAL_CHANNEL = "meal_period_reminder";
    private static final int MEAL_NOTIFICATION = 31;
    private static final long MEAL_CHECK_INTERVAL_MS = 15 * 60_000L;
    private static final double MEAL_WINDOW_START_HOURS = 3.0, MEAL_WINDOW_END_HOURS = 5.0;
    private LocationManager manager;
    private LocationQueue queue;
    private DispositionQueue dispositionQueue;
    private ScheduledExecutorService worker;
    private PowerManager.WakeLock wakeLock;
    private String token, session;
    private long employee, expiresMs;
    private volatile long lastFixNanos = 0;
    private volatile boolean stopping = false;
    // Reset whenever the checked work_date rolls over, so each day's reminders fire at most once each.
    private String mealCheckedForDate = null;
    private boolean mealGentleReminded = false, mealUrgentReminded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        queue = new LocationQueue(this);
        dispositionQueue = new DispositionQueue(this);
        worker = Executors.newSingleThreadScheduledExecutor();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Employee location tracking", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(MEAL_CHANNEL, "Meal break reminders", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, LoginActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, TrackingService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_brand).setContentTitle("Work location tracking · " + (POLL_INTERVAL_MS / 1000) + " seconds").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).addAction(new Notification.Action.Builder(null, "Stop and sign out", stop).build()).build();
    }

    private void update(String text) {
        if (stopping) return;
        int pendingDoors = dispositionQueue.pendingCountForEmployee(employee);
        if (pendingDoors > 0) text += " · " + pendingDoors + " door" + (pendingDoors == 1 ? "" : "s") + " pending upload";
        status = text;
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(text));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (STOP.equals(intent.getAction())) {
            String old = activeToken;
            long who = employee > 0 ? employee : activeEmployee;
            Context appContext = getApplicationContext();
            activeToken = "";
            status = "Tracking stopped. Sign in again to resume.";
            stopping = true;
            stopSelf();
            new Thread(() -> {
                // A finished door still waiting in the queue can only upload with this login, and signing out deletes it -- so send
                // what is waiting first (own queue handle: this service closes its own one as it stops).
                if (who > 0 && !old.isEmpty()) {
                    DispositionQueue waiting = new DispositionQueue(appContext);
                    try {
                        waiting.drain(old, who);
                    } catch (Exception ignored) {
                    } finally {
                        waiting.close();
                    }
                }
                try {
                    EmployeeApi.post("logout", new JSONObject().put("token", old));
                } catch (Exception ignored) {
                }
            }).start();
            return START_NOT_STICKY;
        }
        if (token != null) return START_NOT_STICKY;
        token = intent.getStringExtra("token");
        employee = intent.getLongExtra("employee_id", 0);
        expiresMs = intent.getLongExtra("expires_ms", 0);
        if (token == null || token.isEmpty() || employee <= 0 || expiresMs <= System.currentTimeMillis()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION, notification("Waiting for a fresh location…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION, notification("Waiting for a fresh location…"));
            boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!fine && !coarse) throw new SecurityException();
            session = UUID.randomUUID().toString();
            activeToken = token;
            activeEmployee = employee;
            status = "Waiting for a fresh location…";
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EmployeeField:location");
            wakeLock.acquire(Math.min(12L * 3600000, expiresMs - System.currentTimeMillis()));
            // Android providers deliver fresh fixes; minTime is a target, not a guarantee.
            if (fine && manager.getAllProviders().contains(LocationManager.GPS_PROVIDER))
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, POLL_INTERVAL_MS, 0f, this, Looper.getMainLooper());
            if (manager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER))
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, POLL_INTERVAL_MS, 0f, this, Looper.getMainLooper());
            worker.scheduleWithFixedDelay(this::upload, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            worker.scheduleWithFixedDelay(this::checkMealCompliance, MEAL_CHECK_INTERVAL_MS, MEAL_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            status = "Tracking could not start. Check location permissions and reopen the app.";
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private String osVersion() {
        return "Android " + Build.VERSION.RELEASE;
    }

    private String deviceModel() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.toLowerCase(java.util.Locale.US).startsWith(manufacturer.toLowerCase(java.util.Locale.US)))
            return model.isEmpty() ? "Unknown device" : model;
        return (manufacturer + " " + model).trim();
    }

    private Integer batteryPercent() {
        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return level >= 0 && level <= 100 ? level : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String locationPermission() {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return fine ? "fine" : (coarse ? "coarse" : "denied");
    }

    @Override
    public void onLocationChanged(Location location) {
        if (stopping || System.currentTimeMillis() >= expiresMs) return;
        long now = SystemClock.elapsedRealtimeNanos();
        long fix = location.getElapsedRealtimeNanos();
        if (!location.hasAccuracy() || fix <= 0 || fix > now || now - fix > FIX_MAX_AGE_NANOS || fix <= lastFixNanos || (lastFixNanos > 0 && fix - lastFixNanos < FIX_DEDUPE_NANOS))
            return;
        lastFixNanos = fix;
        Location copy = new Location(location);
        String os = osVersion();
        String model = deviceModel();
        Integer battery = batteryPercent();
        String permission = locationPermission();
        worker.execute(() -> {
            if (stopping) return;
            try {
                JSONObject point = new JSONObject().put("sample_id", UUID.randomUUID().toString()).put("session_id", session).put("captured_ms", copy.getTime()).put("latitude", copy.getLatitude()).put("longitude", copy.getLongitude()).put("accuracy_m", copy.getAccuracy()).put("is_mock", copy.isFromMockProvider()).put("os_version", os).put("device_model", model).put("battery_percent", battery == null ? JSONObject.NULL : battery).put("location_permission", permission);
                queue.add(employee, point);
                update("Location captured · " + queue.count(employee) + " waiting to upload");
                upload();
            } catch (Exception e) {
                update("Unable to save location. Check device storage.");
            }
        });
    }

    private void upload() {
        if (stopping) return;
        if (System.currentTimeMillis() >= expiresMs) {
            update("Session expired. Sign in again to track.");
            new Handler(Looper.getMainLooper()).post(this::stopSelf);
            return;
        }
        // Piggybacks on the same periodic cycle as the GPS point batch upload above/below, rather
        // than running its own timer — this service is already the one thing guaranteed to be alive
        // and polling network reachability while the rep is clocked in.
        dispositionQueue.drain(token, employee);
        try {
            JSONArray points = queue.batch(employee);
            if (points.length() == 0) {
                if (lastFixNanos == 0 || SystemClock.elapsedRealtimeNanos() - lastFixNanos > 90_000_000_000L)
                    update("No fresh GPS fix. Check Location is enabled.");
                return;
            }
            JSONObject response = EmployeeApi.post("locations", new JSONObject().put("token", token).put("points", points));
            queue.acknowledge(employee, response.getJSONArray("accepted"));
            update("Positions saved · " + queue.count(employee) + " waiting to upload");
        } catch (EmployeeApi.ApiError e) {
            if (e.code == 401 || e.code == 403) {
                update("Session ended. Sign in again to track.");
                new Handler(Looper.getMainLooper()).post(this::stopSelf);
            } else if (e.code == 422)
                update("Upload rejected. Check automatic date/time on this phone. Positions remain saved.");
            else update("Upload delayed. Positions remain saved on this phone.");
        } catch (Exception e) {
            update("Offline · positions saved on this phone for upload.");
        }
    }

    /** Reminder-only meal-break nudge: if the rep has clocked in today, hasn't started a lunch yet,
     * and is inside or past the required window, post a local notification. Never blocks anything —
     * this only helps them remember, matching the "reminder, not enforcement" decision for this
     * feature. Runs independently of the GPS upload cycle since 15 minutes is frequent enough for a
     * reminder and needlessly wasteful at the GPS polling cadence. */
    private void checkMealCompliance() {
        if (stopping || System.currentTimeMillis() >= expiresMs) return;
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(mealCheckedForDate)) {
            mealCheckedForDate = today;
            mealGentleReminded = false;
            mealUrgentReminded = false;
        }
        if (mealUrgentReminded) return; // Nothing further to remind about once the strongest nudge has fired today.
        try {
            JSONObject response = EmployeeApi.post("timeclock/day", new JSONObject().put("token", token).put("work_date", today));
            JSONArray events = response.getJSONArray("events");
            Long clockInMs = null;
            boolean clockedOut = false, mealStarted = false;
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                String type = e.getString("event_type");
                if ("start_work".equals(type) && clockInMs == null)
                    clockInMs = java.time.Instant.parse(e.getString("event_utc").replace(' ', 'T') + "Z").toEpochMilli();
                else if ("end_work".equals(type)) clockedOut = true;
                else if ("start_lunch".equals(type)) mealStarted = true;
            }
            if (clockInMs == null || clockedOut || mealStarted) return;
            double hoursSinceClockIn = (System.currentTimeMillis() - clockInMs) / 3_600_000.0;
            if (hoursSinceClockIn >= MEAL_WINDOW_END_HOURS) {
                mealUrgentReminded = true;
                postMealReminder("Take your meal break now", "You clocked in over " + Math.round(MEAL_WINDOW_END_HOURS) + " hours ago and haven't taken a meal break yet.");
            } else if (hoursSinceClockIn >= MEAL_WINDOW_START_HOURS && !mealGentleReminded) {
                mealGentleReminded = true;
                postMealReminder("Meal break reminder", "You're eligible for your meal break — tap to log it when you take it.");
            }
        } catch (Exception ignored) {
            // Best-effort reminder; a failed check just tries again at the next 15-minute interval.
        }
    }

    private void postMealReminder(String title, String text) {
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, LoginActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, MEAL_CHANNEL).setSmallIcon(R.drawable.ic_brand).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).build();
        getSystemService(NotificationManager.class).notify(MEAL_NOTIFICATION, n);
    }

    @Override
    public void onProviderDisabled(String provider) {
        update("Location provider disabled. Enable Location to keep tracking.");
    }

    @Override
    public void onProviderEnabled(String provider) {
        update("Waiting for a fresh location…");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        activeToken = "";
        activeEmployee = 0;
        try {
            manager.removeUpdates(this);
        } catch (Exception ignored) {
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        worker.execute(() -> {
            queue.close();
            dispositionQueue.close();
        });
        worker.shutdown(); // Finish the in-flight upload before closing storage; queued captures observe stopping.
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
