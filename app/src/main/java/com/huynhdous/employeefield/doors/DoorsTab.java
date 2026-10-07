package com.huynhdous.employeefield.doors;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.trip.TripMap;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * "D2D": door-to-door work. Shows the door in progress (address, timer, Finish), "Start a New Door Here", the day's map to tap a position on,
 * today's doors (tap one to correct it), doors that failed to upload, and the rep's callback leads. The work itself is split by step:
 * {@link StartDoor}, {@link FinishDoor}, {@link DoorEdit}, {@link FailedFinishes}, {@link RoutePreview}, {@link MyLeads}; the uploads
 * queue is {@link DispositionQueue}. This class owns what they share: the open door and the views on screen.
 */
public final class DoorsTab extends TabModule {
    static final int REQUEST_START_HERE_LOCATION_PERMISSION = 63;
    // The camera app can make Android close this app while it is open: without saving these, the recreated screen forgets which door was
    // open and the photo that was just taken ("photo not saved" although the photo is fine).
    private static final String STATE_ACTIVE_DISPOSITION_ID = "active_disposition_id";
    private static final String STATE_ACTIVE_DOOR_STARTED_MS = "active_door_started_ms";
    private static final String STATE_ACTIVE_DOOR_LAT = "active_door_lat";
    private static final String STATE_ACTIVE_DOOR_LON = "active_door_lon";

    private AppHost host;
    private Activity activity;
    DispositionQueue queue;

    // The door in progress (null = none).
    Integer activeDispositionId;
    long activeDoorStartedMs;
    Double activeDoorLat;
    Double activeDoorLon;

    TextView messageText;
    private TextView doorsProgramText;
    private Button doorsPreviewRouteButton;
    private TextView doorsTimerText;
    private TextView doorsAddressText;
    private Button doorsFinishButton;
    private Button doorsStartHereButton;
    private android.webkit.WebView doorsMapView;
    private TextView doorsMapStatus;
    LinearLayout doorsReportContainer;

    private StartDoor starter;
    private FinishDoor finisher;
    private DoorEdit edits;
    private FailedFinishes fixes;
    private RoutePreview routes;
    private MyLeads leads;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() {
            if (doorsTimerText != null && activeDispositionId != null && activeDoorStartedMs > 0) {
                long elapsed = (System.currentTimeMillis() - activeDoorStartedMs) / 1000;
                doorsTimerText.setText(formatElapsed(elapsed));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public String title() {
        return "D2D";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_d2d;
    }

    /** Door-to-door disposition logging has no meaning for other programs (e.g. S2S). */
    @Override
    public boolean isAvailableFor(String programCode) {
        return "D2D".equals(programCode);
    }

    @Override
    public int[] requestCodes() {
        return new int[]{FinishDoor.REQUEST_CAMERA_PERMISSION, FinishDoor.REQUEST_TAKE_PHOTO, REQUEST_START_HERE_LOCATION_PERMISSION,
                FailedFinishes.REQUEST_RETRY_CAMERA_PERMISSION, FailedFinishes.REQUEST_TAKE_RETRY_PHOTO};
    }

    // ---- what the helper classes in this package use ----
    AppHost app() {
        return host;
    }

    /** Show a line under the title (safe once the screen is gone). */
    void say(String text) {
        if (messageText != null) messageText.setText(text);
    }

    @Override
    public void buildContent(LinearLayout doorsTabContent) {
        host = host();
        activity = context();
        queue = new DispositionQueue(activity);
        starter = new StartDoor(this);
        finisher = new FinishDoor(this);
        edits = new DoorEdit(this);
        fixes = new FailedFinishes(this);
        routes = new RoutePreview(this);
        float density = density();

        LinearLayout doorsCard = new LinearLayout(activity);
        doorsCard.setOrientation(LinearLayout.VERTICAL);
        doorsCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsCard.setPadding((int) (20 * density), (int) (20 * density), (int) (20 * density), (int) (20 * density));
        doorsCard.setBackground(Theme.cardBackground(activity));
        doorsTabContent.addView(doorsCard);

        // Shows what the rep is currently assigned to sell (Employee Management ▸ Program Assignment
        // on the web) — visible whether or not a door is currently open, so it's never a mystery
        // what they're supposed to be pitching before they even start knocking.
        doorsProgramText = new TextView(activity);
        doorsProgramText.setTextSize(13);
        doorsProgramText.setTypeface(doorsProgramText.getTypeface(), android.graphics.Typeface.BOLD);
        doorsProgramText.setTextColor(Theme.PRIMARY);
        doorsProgramText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsProgramText.setVisibility(View.GONE);
        doorsCard.addView(doorsProgramText);

        messageText = new TextView(activity);
        messageText.setText("Tap your position on the map below, closest to the house, to start a door.");
        messageText.setTextSize(14);
        messageText.setTextColor(Theme.TEXT_SECONDARY);
        messageText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams doorsMessageParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsMessageParams.topMargin = (int) (4 * density);
        doorsCard.addView(messageText, doorsMessageParams);

        doorsAddressText = new TextView(activity);
        doorsAddressText.setTextSize(16);
        doorsAddressText.setTypeface(doorsAddressText.getTypeface(), android.graphics.Typeface.BOLD);
        doorsAddressText.setTextColor(Theme.TEXT_PRIMARY);
        doorsAddressText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsAddressText.setVisibility(View.GONE);
        LinearLayout.LayoutParams doorsAddressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsAddressParams.topMargin = (int) (12 * density);
        doorsCard.addView(doorsAddressText, doorsAddressParams);

        doorsTimerText = new TextView(activity);
        doorsTimerText.setTextSize(36);
        doorsTimerText.setTypeface(doorsTimerText.getTypeface(), android.graphics.Typeface.BOLD);
        doorsTimerText.setTextColor(Theme.PRIMARY);
        doorsTimerText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsTimerText.setVisibility(View.GONE);
        LinearLayout.LayoutParams doorsTimerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsTimerParams.topMargin = (int) (6 * density);
        doorsCard.addView(doorsTimerText, doorsTimerParams);

        doorsFinishButton = Theme.filledButton(activity, "Finish", Theme.SUCCESS);
        LinearLayout.LayoutParams doorsFinishParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsFinishParams.topMargin = (int) (16 * density);
        doorsCard.addView(doorsFinishButton, doorsFinishParams);
        doorsFinishButton.setVisibility(View.GONE);
        doorsFinishButton.setOnClickListener(v -> finisher.beginFinishDoor());

        // Not every house lines up with an existing trail dot (those are just periodic GPS samples,
        // not one-per-house) — this is the fallback: capture a fresh position right now instead of
        // needing a dot to tap.
        doorsStartHereButton = Theme.filledButton(activity, "Start a New Door Here", Theme.PRIMARY);
        LinearLayout.LayoutParams doorsStartHereParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsStartHereParams.topMargin = (int) (10 * density);
        doorsCard.addView(doorsStartHereButton, doorsStartHereParams);
        doorsStartHereButton.setOnClickListener(v -> starter.beginStartNewDoorHere());

        // Same trip map as the Trip tab (same trail, same toolbar) embedded right here — starting a
        // door means tapping a position on THIS map, no switching tabs.
        LinearLayout doorsMapCard = new LinearLayout(activity);
        doorsMapCard.setOrientation(LinearLayout.VERTICAL);
        doorsMapCard.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));
        doorsMapCard.setBackground(Theme.cardBackground(activity));
        LinearLayout.LayoutParams doorsMapCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsMapCardParams.topMargin = (int) (16 * density);
        doorsTabContent.addView(doorsMapCard, doorsMapCardParams);

        doorsMapStatus = new TextView(activity);
        doorsMapStatus.setTextSize(12);
        doorsMapStatus.setTextColor(Theme.TEXT_SECONDARY);
        doorsMapStatus.setPadding(0, 0, 0, (int) (6 * density));
        doorsMapCard.addView(doorsMapStatus);

        doorsMapView = new android.webkit.WebView(activity);
        doorsMapView.getSettings().setJavaScriptEnabled(true);
        doorsMapView.setWebViewClient(new android.webkit.WebViewClient());
        protectMapGestures(doorsMapView);
        doorsMapView.addJavascriptInterface(new TripMap.Bridge(host), "AndroidBridge");
        LinearLayout.LayoutParams doorsMapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (320 * density));
        doorsMapCard.addView(doorsMapView, doorsMapParams);

        // Same two buttons as My Trip: Refresh reloads the map, today's doors and the leads; Where am I drops a dot on the fresh position.
        LinearLayout doorsButtonRow = new LinearLayout(activity);
        doorsButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams doorsButtonRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsButtonRowParams.topMargin = (int) (8 * density);
        doorsMapCard.addView(doorsButtonRow, doorsButtonRowParams);
        LinearLayout doorsRefresh = Theme.iconTextButton(activity, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        doorsButtonRow.addView(doorsRefresh);
        doorsRefresh.setOnClickListener(v -> loadDoors());
        LinearLayout doorsWhereAmI = Theme.iconTextButton(activity, R.drawable.ic_tab_location, "Where am I", Theme.PRIMARY);
        LinearLayout.LayoutParams doorsWhereParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsWhereParams.leftMargin = (int) (8 * density);
        doorsButtonRow.addView(doorsWhereAmI, doorsWhereParams);
        doorsWhereAmI.setOnClickListener(v -> TripMap.showWhereAmI(activity, doorsMapView));

        TextView doorsReportTitle = new TextView(activity);
        doorsReportTitle.setText("Today's Doors");
        doorsReportTitle.setTextSize(15);
        doorsReportTitle.setTypeface(doorsReportTitle.getTypeface(), android.graphics.Typeface.BOLD);
        doorsReportTitle.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams doorsReportTitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsReportTitleParams.topMargin = (int) (16 * density);
        doorsTabContent.addView(doorsReportTitle, doorsReportTitleParams);

        doorsReportContainer = new LinearLayout(activity);
        doorsReportContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams doorsReportContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsReportContainerParams.topMargin = (int) (6 * density);
        doorsTabContent.addView(doorsReportContainer, doorsReportContainerParams);


        leads = new MyLeads(this, doorsTabContent);

        // Lets a rep see the whole day's walk order before he leaves -- the route the office already built, read-only. Last on the screen.
        doorsPreviewRouteButton = Theme.filledButton(activity, "▶ Preview Route", Theme.PRIMARY);
        LinearLayout.LayoutParams doorsPreviewRouteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsPreviewRouteParams.topMargin = (int) (18 * density);
        doorsPreviewRouteParams.bottomMargin = (int) (8 * density);
        doorsTabContent.addView(doorsPreviewRouteButton, doorsPreviewRouteParams);
        doorsPreviewRouteButton.setOnClickListener(v -> routes.previewRoute());
    }

    @Override
    public void onShown() {
        loadDoors();
    }

    @Override
    public void onResume() {
        handler.post(tick);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
    }

    @Override
    public void onDetach() {
        handler.removeCallbacks(tick);
        if (queue != null) queue.close();
        messageText = null;
        doorsProgramText = null;
        doorsPreviewRouteButton = null;
        doorsTimerText = null;
        doorsAddressText = null;
        doorsFinishButton = null;
        doorsStartHereButton = null;
        if (doorsMapView != null) {
            doorsMapView.stopLoading();
            doorsMapView.removeJavascriptInterface("AndroidBridge");
            if (doorsMapView.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) doorsMapView.getParent()).removeView(doorsMapView);
            doorsMapView.destroy();
        }
        doorsMapView = null;
        doorsMapStatus = null;
        doorsReportContainer = null;
        activeDoorLat = null;
        activeDoorLon = null;
        finisher.clearPhoto();
    }

    /** The employee tapped "I'm at the door" on a map position (or the Trip map): confirm the house and start a door there. */
    public void startAtPoint(Long pointId, double lat, double lon) {
        starter.confirmStartDoorAtPoint(pointId, lat, lon);
    }

    void loadMyLeads() {
        leads.load();
    }

    // ---- saved state / camera answers ----
    @Override
    public void saveState(Bundle out) {
        if (finisher != null) finisher.saveState(out);
        if (activeDispositionId != null) {
            out.putInt(STATE_ACTIVE_DISPOSITION_ID, activeDispositionId);
            out.putLong(STATE_ACTIVE_DOOR_STARTED_MS, activeDoorStartedMs);
            if (activeDoorLat != null) out.putDouble(STATE_ACTIVE_DOOR_LAT, activeDoorLat);
            if (activeDoorLon != null) out.putDouble(STATE_ACTIVE_DOOR_LON, activeDoorLon);
        }
    }

    @Override
    public void restoreState(Bundle state) {
        finisher.restoreState(state);
        if (state.containsKey(STATE_ACTIVE_DISPOSITION_ID)) {
            activeDispositionId = state.getInt(STATE_ACTIVE_DISPOSITION_ID);
            activeDoorStartedMs = state.getLong(STATE_ACTIVE_DOOR_STARTED_MS);
            if (state.containsKey(STATE_ACTIVE_DOOR_LAT)) activeDoorLat = state.getDouble(STATE_ACTIVE_DOOR_LAT);
            if (state.containsKey(STATE_ACTIVE_DOOR_LON)) activeDoorLon = state.getDouble(STATE_ACTIVE_DOOR_LON);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == FinishDoor.REQUEST_TAKE_PHOTO) finisher.onPhotoTaken();
        else if (requestCode == FailedFinishes.REQUEST_TAKE_RETRY_PHOTO) fixes.onRetryPhotoTaken();
    }

    @Override
    public void onPermissionResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == FinishDoor.REQUEST_CAMERA_PERMISSION) {
            finisher.onCameraPermission();
        } else if (requestCode == FailedFinishes.REQUEST_RETRY_CAMERA_PERMISSION) {
            fixes.onRetryCameraPermission();
        } else if (requestCode == REQUEST_START_HERE_LOCATION_PERMISSION) {
            if (com.huynhdous.employeefield.core.location.Geo.hasLocationPermission(activity)) starter.beginStartNewDoorHere();
            else say("Location permission is required to start a door.");
        }
    }

    void loadDoors() {
        loadDoors(false, null);
    }

    void loadDoors(boolean finishAfterLoad, Integer expectedDoorId) {
        if (messageText == null) return;
        leads.load();
        loadDoorsMap();
        if (queue.pendingCountForEmployee(host.employeeId()) > 0) {
            final String currentToken = host.token();
            final long currentEmployeeId = host.employeeId();
            new Thread(() -> queue.drain(currentToken, currentEmployeeId)).start();
        }
        try {
            host.request("telemapper/disposition/active", new JSONObject().put("token", host.token()), null, r -> {
                if (doorsProgramText != null) {
                    JSONObject program = r.isNull("current_program") ? null : r.getJSONObject("current_program");
                    if (program == null) {
                        doorsProgramText.setVisibility(View.GONE);
                    } else {
                        doorsProgramText.setText("Selling: " + program.getString("name") + " — " + program.getString("program_value"));
                        doorsProgramText.setVisibility(View.VISIBLE);
                    }
                }
                if (r.isNull("active")) {
                    renderIdle();
                    if (finishAfterLoad) new Popup.Builder(activity).setMessage("This door is already finished. Refreshing your doors.").setPositiveButton("OK", null).show();
                } else {
                    JSONObject active = r.getJSONObject("active");
                    activeDispositionId = active.getInt("disposition_id");
                    activeDoorStartedMs = java.time.Instant.parse(active.getString("created_utc").replace(' ', 'T') + "Z").toEpochMilli();
                    activeDoorLat = active.isNull("latitude") ? null : active.getDouble("latitude");
                    activeDoorLon = active.isNull("longitude") ? null : active.getDouble("longitude");
                    renderActive(active.getString("address"));
                    if (finishAfterLoad && java.util.Objects.equals(activeDispositionId, expectedDoorId)) finisher.beginFinishDoor();
                    else if (finishAfterLoad) new Popup.Builder(activity).setMessage("The active door has changed. Please select the current door again.").setPositiveButton("OK", null).show();
                }
            }, true, null);
        } catch (Exception ignored) {
        }
    }

    /** Independent of the shared host.request()/busy gate — loadDoors() already fires off leads.load()
     * and the active-door check back to back, and host.request() silently drops any call that arrives
     * while another is still in flight, so this needs its own connection rather than competing for
     * that single slot. Always today's trip, regardless of whatever date the Trip tab itself has
     * picked, since a door can only ever be started "now". */
    void loadDoorsMap() {
        if (doorsMapView == null) return;
        new Thread(() -> {
            JSONObject response = null;
            HttpsURLConnection conn = null;
            try {
                JSONObject body = new JSONObject().put("token", host.token()).put("date", java.time.LocalDate.now().toString());
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + "trip").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (conn.getResponseCode() == 200) {
                    JSONObject candidate = new JSONObject(new String(Api.readAllBytes(conn.getInputStream()), StandardCharsets.UTF_8));
                    if (candidate.optBoolean("success")) response = candidate;
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            JSONObject result = response;
            activity.runOnUiThread(() -> {
                if (result == null) return;
                try {
                    org.json.JSONArray points = result.getJSONArray("points");
                    org.json.JSONArray scheduled = result.optJSONArray("scheduled");
                    if (scheduled == null) scheduled = new org.json.JSONArray();
                    org.json.JSONArray dispositions = result.optJSONArray("dispositions");
                    if (dispositions == null) dispositions = new org.json.JSONArray();
                    doorsMapView.loadDataWithBaseURL("file:///android_asset/leaflet/", TripMap.buildHtml(points, scheduled, dispositions), "text/html", "UTF-8", null);
                    doorsMapStatus.setText(points.length() + " position" + (points.length() == 1 ? "" : "s") + " recorded today");
                    renderDoorsReport(dispositions);
                } catch (Exception ignored) {
                }
            });
        }).start();
    }

    /** A quick scannable "did I get anywhere today" list — address on the left, outcome badge on
     * the right, same status colors as the map markers above it. Door finishes the server
     * permanently rejected (see DispositionQueue) are shown first, since those need the rep's
     * attention and the server doesn't know about them at all yet (it still thinks that door is
     * open). Also includes any pre-migration record with no confirmed owner (see
     * DispositionQueue.failedUnassigned()) -- it may not even be this rep's, but leaving it
     * permanently invisible to everyone is worse than showing it to someone who can judge it. */
    private void renderDoorsReport(org.json.JSONArray dispositions) throws Exception {
        if (doorsReportContainer == null) return;
        doorsReportContainer.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;

        java.util.List<DispositionQueue.Finish> failed = queue.failedForEmployee(host.employeeId());
        for (DispositionQueue.Finish f : failed) {
            fixes.addNeedsAttentionRow(density, f, addressForDisposition(dispositions, f.dispositionId), false);
        }
        java.util.List<DispositionQueue.Finish> unassigned = queue.failedUnassigned();
        for (DispositionQueue.Finish f : unassigned) {
            fixes.addNeedsAttentionRow(density, f, addressForDisposition(dispositions, f.dispositionId), true);
        }

        if (dispositions.length() == 0) {
            if (failed.isEmpty() && unassigned.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText("No doors logged yet today.");
                empty.setTextSize(13);
                empty.setTextColor(Theme.TEXT_SECONDARY);
                doorsReportContainer.addView(empty);
            }
            return;
        }
        for (int i = dispositions.length() - 1; i >= 0; i--) {
            JSONObject d = dispositions.getJSONObject(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
            row.setBackground(Theme.cardBackground(activity));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = (int) (6 * density);
            doorsReportContainer.addView(row, rowParams);

            TextView addressText = new TextView(activity);
            addressText.setText(d.optString("address", "Unnamed door"));
            addressText.setTextSize(14);
            addressText.setTextColor(Theme.TEXT_PRIMARY);
            row.addView(addressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            boolean inProgress = d.optBoolean("in_progress", false);
            String status = d.optString("status", "");
            TextView badge = inProgress
                    ? Theme.statusBadge(activity, "In progress", Theme.PRIMARY)
                    : Theme.statusBadge(activity, DoorStatus.label(status), DoorStatus.color(status));
            row.addView(badge);
            if (inProgress) {
                row.setContentDescription(d.optString("address", "Door") + ". In progress. Tap to finish.");
                row.setOnClickListener(v -> new Popup.Builder(activity)
                        .setTitle("Finish this door?")
                        .setMessage(d.optString("address", "Current door") + "\nRecord the outcome and required photo to finish this visit.")
                        .setPositiveButton("Finish door", (dialog, which) -> loadDoors(true, d.optInt("disposition_id")))
                        .setNegativeButton("Cancel", null).show());
                badge.setText("Tap to finish");
            } else if (!status.isEmpty()) {
                // Mistakes happen — a finished door from today can still be corrected. Only today's
                // doors ever show here, so the server's same-day restriction never blocks this.
                final int dispositionId = d.optInt("disposition_id");
                final String currentStatus = status;
                final String currentNote = d.optString("note", "");
                final String address = d.optString("address", "This door");
                row.setContentDescription(address + ". " + DoorStatus.label(status) + ". Tap to correct.");
                row.setOnClickListener(v -> edits.showEditOutcomeDialog(dispositionId, currentStatus, currentNote, address));
            }
        }
    }

    /** Best-effort address lookup for a queued/failed submission -- the queue itself only ever
     * stores a disposition_id, so this cross-references today's server-known dispositions (which do
     * carry an address, set when the door was started) to show something human-readable. */
    private String addressForDisposition(org.json.JSONArray dispositions, int dispositionId) throws Exception {
        for (int i = 0; i < dispositions.length(); i++) {
            JSONObject d = dispositions.getJSONObject(i);
            if (d.optInt("disposition_id") == dispositionId) return d.optString("address", "This door");
        }
        return "This door";
    }

    void renderIdle() {
        activeDispositionId = null;
        activeDoorStartedMs = 0;
        activeDoorLat = null;
        activeDoorLon = null;
        finisher.clearPhoto();
        messageText.setText("Tap your position on the map below, closest to the house, to start a door — or use \"Start a New Door Here\" if none is close enough.");
        doorsAddressText.setVisibility(View.GONE);
        doorsTimerText.setVisibility(View.GONE);
        doorsFinishButton.setVisibility(View.GONE);
        doorsStartHereButton.setVisibility(View.VISIBLE);
    }

    void renderActive(String address) {
        messageText.setText("Timer running — tap Finish when the door closes.");
        doorsAddressText.setText(address);
        doorsAddressText.setVisibility(View.VISIBLE);
        doorsTimerText.setVisibility(View.VISIBLE);
        doorsTimerText.setText(formatElapsed((System.currentTimeMillis() - activeDoorStartedMs) / 1000));
        doorsFinishButton.setVisibility(View.VISIBLE);
        doorsStartHereButton.setVisibility(View.GONE);
    }

    private String formatElapsed(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60, s = seconds % 60;
        return m + "m " + String.format(java.util.Locale.US, "%02d", s) + "s";
    }

    /** Keep map drags and pinch gestures inside the WebView, not its parent ScrollView. */
    private void protectMapGestures(android.webkit.WebView map) {
        map.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) view.performClick();
            android.view.ViewParent parent = view.getParent();
            if (parent != null) {
                int action = event.getActionMasked();
                parent.requestDisallowInterceptTouchEvent(
                        action != android.view.MotionEvent.ACTION_UP
                        && action != android.view.MotionEvent.ACTION_CANCEL);
            }
            return false; // WebView/Leaflet still receives and handles the gesture.
        });
    }

}
