package com.huynhdous.employeefield;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.HideReturnsTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import org.json.JSONObject;

import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Employee authentication and visible foreground work-location tracking.
 */
public final class LoginActivity extends Activity {
    private EditText email, password;
    private String token = "";
    private boolean busy;
    private long employeeId, sessionExpires;
    private String programCode;
    private TextView trackingStatus;
    private LinearLayout scheduleContainer;
    private TextView confirmStatus;
    private Button confirmButton;
    private Button enableTrackingButton;
    private String scheduleWeekStart;
    private LinearLayout scheduleTabContent;
    private LinearLayout dayTotalCard;
    private Gauge dayGauge;
    private Gauge weekGauge;
    private TextView dayTotalClockInText;
    private int todayScheduledMinutes = DAILY_TARGET_MINUTES;
    private int weekScheduledMinutes = -1;
    private int weekWorkedMinutes = -1;

    /** A circular progress gauge: a ring with its value text centered inside it, plus a caption below. */
    private static final class Gauge {
        final LinearLayout column;
        final DayProgressRing ring;
        final TextView centerText;
        final TextView subText;

        Gauge(LinearLayout column, DayProgressRing ring, TextView centerText, TextView subText) {
            this.column = column;
            this.ring = ring;
            this.centerText = centerText;
            this.subText = subText;
        }
    }
    private TextView screenTitleText;
    private LinearLayout locationTabContent;
    private LinearLayout tripTabContent;
    private LinearLayout tabScheduleLabel;
    private LinearLayout tabLocationLabel;
    private LinearLayout tabTripLabel;
    private LinearLayout tabProfileLabel;
    private LinearLayout profileTabContent;
    private LinearLayout tabTimesheetLabel;
    private LinearLayout timesheetTabContent;
    private LinearLayout tabDoorsLabel;
    private LinearLayout doorsTabContent;
    private LinearLayout tabEventsLabel;
    private LinearLayout eventsTabContent;
    private LinearLayout myEventsContainer;
    private LinearLayout tabProgramsLabel;
    private LinearLayout programsTabContent;
    private LinearLayout myProgramsContainer;
    private LinearLayout tabArrivalLabel;
    private LinearLayout arrivalTabContent;
    private LinearLayout arrivalAssignmentsContainer;
    private TextView arrivalMessageText;
    private LinearLayout navDrawer;
    private View navScrim;
    private int drawerWidthPx;
    private TextView doorsMessageText;
    private TextView doorsProgramText;
    private Button doorsPreviewRouteButton;
    private TextView doorsTimerText;
    private TextView doorsAddressText;
    private Button doorsFinishButton;
    private Button doorsStartHereButton;
    private Integer activeDispositionId;
    private long activeDoorStartedMs;
    private Double activeDoorLat;
    private Double activeDoorLon;
    private android.net.Uri pendingDoorsPhotoUri;
    private File pendingDoorsPhotoFile;
    private DispositionQueue dispositionQueue;
    private android.webkit.WebView doorsMapView;
    private TextView doorsMapStatus;
    private LinearLayout doorsReportContainer;
    private LinearLayout myLeadsContainer;
    private static final int REQUEST_DOORS_CAMERA_PERMISSION = 61;
    private static final int REQUEST_DOORS_START_HERE_LOCATION_PERMISSION = 63;
    private static final int REQUEST_TAKE_DOORS_PHOTO = 62;
    private static final double DOORS_PHOTO_PROXIMITY_METERS = 100;
    // Retaking a photo for a failed/needs-attention queue item -- deliberately separate from the
    // normal finish-a-door flow above (REQUEST_TAKE_DOORS_PHOTO/activeDispositionId): that flow is
    // tied to "a door currently in progress," which a queued-but-rejected finish no longer is.
    private DispositionQueue.Finish editingFailedFinish;
    private String editingFailedFinishAddress;
    // Captured right before launching the camera so a retake doesn't discard whatever the rep had
    // already typed/selected in the edit dialog -- the dialog itself doesn't survive the round trip
    // through the camera app, but these plain instance fields do (same Activity instance).
    private String editingDraftStatus, editingDraftNote, editingDraftCallbackDate, editingDraftDuplicateReason;
    private static final java.util.Set<String> CLOSING_STATUSES = new java.util.HashSet<>(java.util.Arrays.asList("sold", "do_not_call", "already_serviced"));
    private android.net.Uri pendingRetryPhotoUri;
    private File pendingRetryPhotoFile;
    private static final int REQUEST_RETRY_CAMERA_PERMISSION = 68;
    private static final int REQUEST_TAKE_RETRY_PHOTO = 69;
    private org.json.JSONObject activeArrivalAssignment;
    private Double activeArrivalLat;
    private Double activeArrivalLon;
    private org.json.JSONObject activeCheckOutAssignment;
    private android.net.Uri pendingSelfieUri;
    private android.net.Uri pendingStorePhotoUri;
    private File pendingSelfieFile;
    private File pendingStorePhotoFile;
    /** Store photos already reviewed and accepted this check-in (not counting whatever is currently
     * pending review in pendingStorePhotoFile). Capped at MAX_STORE_PHOTOS total. */
    private final java.util.List<File> acceptedStorePhotos = new java.util.ArrayList<>();
    private static final int MAX_STORE_PHOTOS = 10;
    private static final int REQUEST_ARRIVAL_CAMERA_PERMISSION = 65;
    private static final int REQUEST_TAKE_SELFIE_PHOTO = 66;
    private static final int REQUEST_TAKE_STORE_PHOTO = 67;
    // The stock camera app on low-RAM devices (e.g. the Stratus_C7) can make Android destroy this
    // Activity in the background while it's open. onActivityResult is still redelivered after
    // recreation, but every instance field above resets to null/0 first — without saving and
    // restoring them here, the arrival flow silently loses its place and the app appears to jump
    // back to the Schedule tab with no photos taken and no error shown.
    private static final String STATE_TAB = "current_tab";
    private static final String STATE_SELFIE_PATH = "pending_selfie_path";
    private static final String STATE_STORE_PATH = "pending_store_path";
    private static final String STATE_ARRIVAL_ASSIGNMENT = "active_arrival_assignment";
    private static final String STATE_ACCEPTED_STORE_PATHS = "accepted_store_paths";
    // Same low-RAM camera-launch issue as the arrival photos above, but for the D2D door-finish
    // photo: without saving/restoring these, a killed-and-recreated Activity comes back with
    // activeDispositionId null and pendingDoorsPhotoFile null, so onActivityResult wrongly reports
    // "photo not saved" (the photo is fine, the app just forgot about it) and showFinishDoorDialog
    // silently no-ops instead of showing the outcome picker.
    private static final String STATE_DOORS_PHOTO_PATH = "pending_doors_photo_path";
    private static final String STATE_ACTIVE_DISPOSITION_ID = "active_disposition_id";
    private static final String STATE_ACTIVE_DOOR_STARTED_MS = "active_door_started_ms";
    private static final String STATE_ACTIVE_DOOR_LAT = "active_door_lat";
    private static final String STATE_ACTIVE_DOOR_LON = "active_door_lon";
    private int currentTabIndex = 0;
    private int restoredTabIndex = 0;
    private LinearLayout timesheetDaysContainer;
    private TextView timesheetTotalText;
    private LinearLayout timesheetBadgeRow;
    private TextView timesheetMessage;
    private ImageView greetingAvatarView;
    private ImageView profileAvatarView;
    private android.net.Uri pendingCameraUri;
    private static final int REQUEST_CAMERA_PERMISSION = 40;
    private static final int REQUEST_TAKE_PHOTO = 50;
    private static final int REQUEST_PICK_PHOTO = 51;
    private android.webkit.WebView locationMapView;
    private android.webkit.WebView tripMapView;
    private TextView locationMapStatus;
    private TextView tripStatus;
    private Button tripDateButton;
    private java.time.LocalDate tripDate;
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final int DAILY_TARGET_MINUTES = 480;
    private static final int WEEKLY_TARGET_MINUTES = 2400;
    private final android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshStatus = new Runnable() {
        public void run() {
            if (trackingStatus != null) trackingStatus.setText(TrackingService.status);
            if (enableTrackingButton != null)
                enableTrackingButton.setVisibility(hasLocationPermission() ? View.GONE : View.VISIBLE);
            statusHandler.postDelayed(this, 2000);
        }
    };
    private final Runnable doorsTick = new Runnable() {
        public void run() {
            if (doorsTimerText != null && activeDispositionId != null && activeDoorStartedMs > 0) {
                long elapsed = (System.currentTimeMillis() - activeDoorStartedMs) / 1000;
                doorsTimerText.setText(formatDoorsElapsed(elapsed));
            }
            statusHandler.postDelayed(this, 1000);
        }
    };
    private static final String API = "https://jobportal.huynhdous.com/api/employee/";

    interface Result {
        void accept(JSONObject value) throws Exception;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        dispositionQueue = new DispositionQueue(this);
        if (state != null) {
            restoredTabIndex = state.getInt(STATE_TAB, 0);
            String selfiePath = state.getString(STATE_SELFIE_PATH);
            if (selfiePath != null) {
                File f = new File(selfiePath);
                if (f.exists() && f.length() > 0) {
                    pendingSelfieFile = f;
                    pendingSelfieUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                }
            }
            String storePath = state.getString(STATE_STORE_PATH);
            if (storePath != null) {
                File f = new File(storePath);
                if (f.exists() && f.length() > 0) {
                    pendingStorePhotoFile = f;
                    pendingStorePhotoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                }
            }
            String assignmentJson = state.getString(STATE_ARRIVAL_ASSIGNMENT);
            if (assignmentJson != null) {
                try {
                    activeArrivalAssignment = new JSONObject(assignmentJson);
                } catch (Exception ignored) {
                }
            }
            String[] acceptedPaths = state.getStringArray(STATE_ACCEPTED_STORE_PATHS);
            if (acceptedPaths != null) {
                for (String p : acceptedPaths) {
                    File f = new File(p);
                    if (f.exists() && f.length() > 0) acceptedStorePhotos.add(f);
                }
            }
            String doorsPhotoPath = state.getString(STATE_DOORS_PHOTO_PATH);
            if (doorsPhotoPath != null) {
                File f = new File(doorsPhotoPath);
                if (f.exists() && f.length() > 0) {
                    pendingDoorsPhotoFile = f;
                    pendingDoorsPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                }
            }
            if (state.containsKey(STATE_ACTIVE_DISPOSITION_ID)) {
                activeDispositionId = state.getInt(STATE_ACTIVE_DISPOSITION_ID);
                activeDoorStartedMs = state.getLong(STATE_ACTIVE_DOOR_STARTED_MS);
                if (state.containsKey(STATE_ACTIVE_DOOR_LAT)) activeDoorLat = state.getDouble(STATE_ACTIVE_DOOR_LAT);
                if (state.containsKey(STATE_ACTIVE_DOOR_LON)) activeDoorLon = state.getDouble(STATE_ACTIVE_DOOR_LON);
            }
        }
        if (!TrackingService.activeToken.isEmpty()) {
            token = TrackingService.activeToken;
            try {
                verifyAccess();
            } catch (Exception e) {
                showLogin();
            }
        } else showLogin();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTabIndex);
        if (pendingSelfieFile != null) outState.putString(STATE_SELFIE_PATH, pendingSelfieFile.getAbsolutePath());
        if (pendingStorePhotoFile != null) outState.putString(STATE_STORE_PATH, pendingStorePhotoFile.getAbsolutePath());
        if (activeArrivalAssignment != null) outState.putString(STATE_ARRIVAL_ASSIGNMENT, activeArrivalAssignment.toString());
        if (!acceptedStorePhotos.isEmpty()) {
            String[] paths = new String[acceptedStorePhotos.size()];
            for (int i = 0; i < paths.length; i++) paths[i] = acceptedStorePhotos.get(i).getAbsolutePath();
            outState.putStringArray(STATE_ACCEPTED_STORE_PATHS, paths);
        }
        if (pendingDoorsPhotoFile != null) outState.putString(STATE_DOORS_PHOTO_PATH, pendingDoorsPhotoFile.getAbsolutePath());
        if (activeDispositionId != null) {
            outState.putInt(STATE_ACTIVE_DISPOSITION_ID, activeDispositionId);
            outState.putLong(STATE_ACTIVE_DOOR_STARTED_MS, activeDoorStartedMs);
            if (activeDoorLat != null) outState.putDouble(STATE_ACTIVE_DOOR_LAT, activeDoorLat);
            if (activeDoorLon != null) outState.putDouble(STATE_ACTIVE_DOOR_LON, activeDoorLon);
        }
    }

    private void insets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void showLogin() {
        trackingStatus = null;
        scheduleContainer = null;
        confirmStatus = null;
        confirmButton = null;
        enableTrackingButton = null;
        scheduleTabContent = null;
        dayTotalCard = null;
        dayGauge = null;
        weekGauge = null;
        dayTotalClockInText = null;
        screenTitleText = null;
        locationTabContent = null;
        tripTabContent = null;
        tabScheduleLabel = null;
        tabLocationLabel = null;
        tabTripLabel = null;
        tabProfileLabel = null;
        profileTabContent = null;
        tabTimesheetLabel = null;
        timesheetTabContent = null;
        tabDoorsLabel = null;
        doorsTabContent = null;
        tabEventsLabel = null;
        eventsTabContent = null;
        myEventsContainer = null;
        tabProgramsLabel = null;
        programsTabContent = null;
        myProgramsContainer = null;
        tabArrivalLabel = null;
        arrivalTabContent = null;
        arrivalAssignmentsContainer = null;
        arrivalMessageText = null;
        activeArrivalAssignment = null;
        activeArrivalLat = null;
        activeArrivalLon = null;
        pendingSelfieUri = null;
        pendingStorePhotoUri = null;
        pendingSelfieFile = null;
        pendingStorePhotoFile = null;
        acceptedStorePhotos.clear();
        navDrawer = null;
        navScrim = null;
        doorsMessageText = null;
        doorsProgramText = null;
        doorsPreviewRouteButton = null;
        doorsTimerText = null;
        doorsAddressText = null;
        doorsFinishButton = null;
        doorsStartHereButton = null;
        activeDoorLat = null;
        activeDoorLon = null;
        pendingDoorsPhotoUri = null;
        pendingDoorsPhotoFile = null;
        doorsMapView = null;
        doorsMapStatus = null;
        doorsReportContainer = null;
        myLeadsContainer = null;
        timesheetDaysContainer = null;
        timesheetTotalText = null;
        timesheetBadgeRow = null;
        timesheetMessage = null;
        greetingAvatarView = null;
        profileAvatarView = null;
        locationMapView = null;
        tripMapView = null;
        locationMapStatus = null;
        tripStatus = null;
        tripDateButton = null;
        tripDate = null;
        token = "";
        setContentView(R.layout.activity_login);
        insets(findViewById(R.id.root));
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        ((CheckBox) findViewById(R.id.show_password)).setOnCheckedChangeListener((button, checked) -> {
            int cursor = password.getSelectionEnd();
            password.setTransformationMethod(checked ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            password.setSelection(Math.max(0, Math.min(cursor, password.length())));
        });
        findViewById(R.id.sign_in).setTag(findViewById(R.id.sign_in_progress));
        findViewById(R.id.sign_in).setOnClickListener(v -> login());
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });
        findViewById(R.id.help).setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(R.string.help).setMessage(R.string.help_message).setPositiveButton(R.string.ok, null).show());
    }

    private void login() {
        if (busy) return;
        email.setError(null);
        password.setError(null);
        if (email.getText().toString().trim().isEmpty()) {
            email.setError(getString(R.string.email_error));
            return;
        }
        if (password.length() == 0) {
            password.setError(getString(R.string.password_error));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("username", email.getText().toString().trim());
            body.put("password", password.getText().toString());
        } catch (Exception e) {
            return;
        }
        password.getText().clear();
        request("login", body, (Button) findViewById(R.id.sign_in), r -> {
            token = r.getString("token");
            if (r.getBoolean("must_change_password")) showChange();
            else verifyAccess();
        });
    }

    private LinearLayout screen(String title, String description) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad * 2, pad, pad);
        scroll.setBackgroundColor(Theme.BACKGROUND);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        setContentView(scroll);
        insets(scroll);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(26);
        heading.setTextColor(Theme.TEXT_PRIMARY);
        panel.addView(heading);
        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(16);
        desc.setPadding(0, pad, 0, pad);
        panel.addView(desc);
        return panel;
    }

    private static final String[] DAY_CODES = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};

    private String dayCode(int dayIndex) {
        return DAY_CODES[dayIndex];
    }

    private void addRow(String avatarText, int avatarBg, int avatarTextColor, String title, String subtitle, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        row.setBackground(Theme.cardBackground(this));

        TextView avatar = new TextView(this);
        avatar.setText(avatarText);
        avatar.setTextColor(avatarTextColor);
        avatar.setTextSize(12);
        avatar.setTypeface(avatar.getTypeface(), android.graphics.Typeface.BOLD);
        avatar.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(avatarBg);
        avatar.setBackground(circle);
        int size = (int) (36 * density);
        row.addView(avatar, new LinearLayout.LayoutParams(size, size));

        LinearLayout textStack = new LinearLayout(this);
        textStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stackParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stackParams.leftMargin = (int) (12 * density);
        row.addView(textStack, stackParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(Theme.TEXT_PRIMARY);
        textStack.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(13);
            subtitleView.setTextColor(Theme.TEXT_SECONDARY);
            textStack.addView(subtitleView);
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = (int) (8 * density);
        scheduleContainer.addView(row, cardParams);
    }

    private Button styledButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xffffffff);
        button.setTextSize(15);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        float density = getResources().getDisplayMetrics().density;
        button.setPadding((int) (18 * density), (int) (14 * density), (int) (18 * density), (int) (14 * density));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(8 * density);
        button.setBackground(background);
        return button;
    }

    private EditText passwordField(LinearLayout panel, String title) {
        TextView label = new TextView(this);
        label.setText(title);
        panel.addView(label);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // Apply last: setSingleLine can replace the password transformation.
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setSaveEnabled(false);
        field.setHint(title);
        Theme.styleInput(field);
        panel.addView(field);
        return field;
    }

    private void showChange() {
        LinearLayout panel = screen("Change your password", "You must replace your temporary password before accessing your employee account. Use 12–72 characters.");
        EditText next = passwordField(panel, "New password"), confirm = passwordField(panel, "Confirm new password");
        Button save = new Button(this);
        save.setText("Save password and sign in");
        panel.addView(save);
        save.setOnClickListener(v -> {
            if (busy) return;
            String value = next.getText().toString();
            if (value.getBytes(StandardCharsets.UTF_8).length < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
                next.setError("Use 12–72 characters (up to 72 UTF-8 bytes).");
                return;
            }
            if (!value.equals(confirm.getText().toString())) {
                confirm.setError("Passwords do not match.");
                return;
            }
            JSONObject body = new JSONObject();
            try {
                body.put("token", token);
                body.put("password", value);
                body.put("confirmation", confirm.getText().toString());
            } catch (Exception e) {
                return;
            }
            next.getText().clear();
            confirm.getText().clear();
            request("change-password", body, save, r -> {
                token = r.getString("token");
                verifyAccess();
            });
        });
        Button cancel = new Button(this);
        cancel.setText("Back to sign in");
        panel.addView(cancel);
        cancel.setOnClickListener(v -> {
            if (!busy) logout();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusHandler.post(refreshStatus);
        statusHandler.post(doorsTick);
        if (scheduleContainer != null) loadSchedule();
    }

    @Override
    protected void onPause() {
        statusHandler.removeCallbacks(refreshStatus);
        statusHandler.removeCallbacks(doorsTick);
        super.onPause();
    }

    private void verifyAccess() throws Exception {
        request("me", new JSONObject().put("token", token), null, r -> {
            employeeId = r.getLong("employee_id");
            sessionExpires = java.time.Instant.parse(r.getString("expires_utc").replace(' ', 'T') + "Z").toEpochMilli();
            programCode = r.isNull("program_code") ? null : r.optString("program_code", null);
            renderHome(r.getString("employee_name"), r.getString("username"));
        });
    }

    private void renderHome(String employeeName, String username) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);
        LinearLayout panel = screen("", "Signed in as " + username + ".");
        panel.removeViewAt(0);
        TextView descView = (TextView) panel.getChildAt(0);
        String descText = descView.getText().toString();
        panel.removeViewAt(0);

        LinearLayout greetingCard = new LinearLayout(this);
        greetingCard.setOrientation(LinearLayout.VERTICAL);
        greetingCard.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
        greetingCard.setBackground(Theme.cardBackground(this));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageButton menuButton = Theme.iconButton(this, R.drawable.ic_menu_hamburger, Theme.NEUTRAL, "Menu");
        int menuButtonSize = (int) (40 * density);
        LinearLayout.LayoutParams menuButtonParams = new LinearLayout.LayoutParams(menuButtonSize, menuButtonSize);
        menuButtonParams.rightMargin = (int) (10 * density);
        headerRow.addView(menuButton, menuButtonParams);
        greetingAvatarView = Theme.circularAvatar(this, 44);
        int avatarSize = (int) (44 * density);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.rightMargin = (int) (12 * density);
        headerRow.addView(greetingAvatarView, avatarParams);
        loadMyAvatar(greetingAvatarView);
        TextView nameText = new TextView(this);
        nameText.setText("Welcome, " + employeeName);
        nameText.setTextSize(24);
        nameText.setTextColor(Theme.TEXT_PRIMARY);
        headerRow.addView(nameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton signOut = Theme.iconButton(this, R.drawable.ic_logout, Theme.NEUTRAL, "Sign out");
        int signOutSize = (int) (40 * density);
        headerRow.addView(signOut, new LinearLayout.LayoutParams(signOutSize, signOutSize));
        signOut.setOnClickListener(v -> {
            if (!busy) logout();
        });
        greetingCard.addView(headerRow);

        TextView descLabel = new TextView(this);
        descLabel.setText(descText);
        descLabel.setTextSize(13);
        descLabel.setTextColor(Theme.TEXT_SECONDARY);
        descLabel.setPadding(0, (int) (4 * density), 0, 0);
        greetingCard.addView(descLabel);

        LinearLayout.LayoutParams greetingParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        greetingParams.bottomMargin = (int) (8 * density);
        panel.addView(greetingCard, 0, greetingParams);

        // A persistent "what screen am I on" bar — stays visible above whichever tab content is
        // showing, since it's never toggled by selectTab() the way the tab contents themselves are.
        screenTitleText = new TextView(this);
        screenTitleText.setTextSize(15);
        screenTitleText.setTypeface(screenTitleText.getTypeface(), android.graphics.Typeface.BOLD);
        screenTitleText.setTextColor(Theme.PRIMARY);
        screenTitleText.setLetterSpacing(0.02f);
        int screenTitlePadH = (int) (14 * density), screenTitlePadV = (int) (10 * density);
        screenTitleText.setPadding(screenTitlePadH, screenTitlePadV, screenTitlePadH, screenTitlePadV);
        android.graphics.drawable.GradientDrawable screenTitleBg = new android.graphics.drawable.GradientDrawable();
        screenTitleBg.setColor(android.graphics.Color.argb(28, android.graphics.Color.red(Theme.PRIMARY), android.graphics.Color.green(Theme.PRIMARY), android.graphics.Color.blue(Theme.PRIMARY)));
        screenTitleBg.setCornerRadius(8 * density);
        screenTitleText.setBackground(screenTitleBg);
        LinearLayout.LayoutParams screenTitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        screenTitleParams.topMargin = (int) (10 * density);
        screenTitleParams.bottomMargin = (int) (4 * density);
        panel.addView(screenTitleText, 1, screenTitleParams);

        // The old horizontal tab bar is gone — these are now rows inside the slide-out nav drawer
        // (built by installDrawer() at the end of this method), not children of `panel`.
        tabScheduleLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_schedule, "Schedule");
        tabLocationLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_location, "Location");
        tabTripLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_trip, "Trip");
        tabTimesheetLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_timesheet, "Time Sheet");
        tabProfileLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_profile, "Profile");
        tabEventsLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_events, "Events");
        tabProgramsLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_events, "Programs");
        tabDoorsLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_d2d, "D2D");
        tabArrivalLabel = Theme.drawerMenuItem(this, R.drawable.ic_tab_checkin, "Check In");

        scheduleTabContent = new LinearLayout(this);
        scheduleTabContent.setOrientation(LinearLayout.VERTICAL);
        scheduleTabContent.setPadding(0, pad / 2, 0, 0);
        panel.addView(scheduleTabContent);

        LinearLayout scheduleRefresh = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        LinearLayout.LayoutParams scheduleRefreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scheduleRefreshParams.bottomMargin = (int) (8 * density);
        scheduleRefresh.setOnClickListener(v -> loadSchedule());
        scheduleTabContent.addView(scheduleRefresh, scheduleRefreshParams);

        dayTotalCard = new LinearLayout(this);
        dayTotalCard.setOrientation(LinearLayout.VERTICAL);
        dayTotalCard.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
        dayTotalCard.setBackground(Theme.cardBackground(this));
        dayTotalCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams dayTotalCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dayTotalCardParams.bottomMargin = (int) (12 * density);
        scheduleTabContent.addView(dayTotalCard, dayTotalCardParams);

        LinearLayout gaugeRow = new LinearLayout(this);
        gaugeRow.setOrientation(LinearLayout.HORIZONTAL);
        dayTotalCard.addView(gaugeRow);

        dayGauge = buildGauge("Today", "of 8h", density);
        weekGauge = buildGauge("This Week", "of 40h", density);
        gaugeRow.addView(dayGauge.column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        gaugeRow.addView(weekGauge.column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        dayTotalClockInText = new TextView(this);
        dayTotalClockInText.setTextSize(13);
        dayTotalClockInText.setTextColor(Theme.TEXT_SECONDARY);
        dayTotalClockInText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dayTotalClockInParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dayTotalClockInParams.topMargin = (int) (12 * density);
        dayTotalCard.addView(dayTotalClockInText, dayTotalClockInParams);

        TextView dayTotalDetails = new TextView(this);
        dayTotalDetails.setText("Details");
        dayTotalDetails.setTextSize(13);
        dayTotalDetails.setTypeface(dayTotalDetails.getTypeface(), android.graphics.Typeface.BOLD);
        dayTotalDetails.setTextColor(Theme.PRIMARY);
        dayTotalDetails.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dayTotalDetailsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dayTotalDetailsParams.topMargin = (int) (8 * density);
        dayTotalCard.addView(dayTotalDetails, dayTotalDetailsParams);
        dayTotalDetails.setOnClickListener(v -> selectTab(3));

        scheduleContainer = new LinearLayout(this);
        scheduleContainer.setOrientation(LinearLayout.VERTICAL);
        scheduleTabContent.addView(scheduleContainer);
        TextView loading = new TextView(this);
        loading.setText("Loading…");
        loading.setTextSize(15);
        scheduleContainer.addView(loading);

        confirmStatus = new TextView(this);
        Theme.applyBadgeStyle(confirmStatus, Theme.SUCCESS);
        confirmStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams confirmStatusParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmStatusParams.bottomMargin = (int) (8 * density);
        scheduleTabContent.addView(confirmStatus, confirmStatusParams);

        confirmButton = styledButton("Confirm this week's schedule", Theme.PRIMARY);
        confirmButton.setVisibility(View.GONE);
        scheduleTabContent.addView(confirmButton);
        confirmButton.setOnClickListener(v -> confirmSchedule());

        locationTabContent = new LinearLayout(this);
        locationTabContent.setOrientation(LinearLayout.VERTICAL);
        locationTabContent.setPadding(0, pad / 2, 0, 0);
        locationTabContent.setVisibility(View.GONE);
        panel.addView(locationTabContent);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));
        statusCard.setBackground(Theme.cardBackground(this));
        locationTabContent.addView(statusCard);

        trackingStatus = new TextView(this);
        trackingStatus.setText(TrackingService.status);
        trackingStatus.setTextSize(14);
        trackingStatus.setTextColor(Theme.TEXT_PRIMARY);
        statusCard.addView(trackingStatus);
        enableTrackingButton = new Button(this);
        enableTrackingButton.setText("Enable / resume location tracking");
        enableTrackingButton.setVisibility(hasLocationPermission() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams enableParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        enableParams.topMargin = (int) (8 * density);
        statusCard.addView(enableTrackingButton, enableParams);
        enableTrackingButton.setOnClickListener(v -> enableTracking());

        LinearLayout mapCard = new LinearLayout(this);
        mapCard.setOrientation(LinearLayout.VERTICAL);
        mapCard.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));
        mapCard.setBackground(Theme.cardBackground(this));
        LinearLayout.LayoutParams mapCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mapCardParams.topMargin = (int) (8 * density);
        locationTabContent.addView(mapCard, mapCardParams);

        locationMapStatus = new TextView(this);
        locationMapStatus.setTextSize(13);
        locationMapStatus.setTextColor(Theme.TEXT_SECONDARY);
        locationMapStatus.setPadding(0, 0, 0, (int) (8 * density));
        mapCard.addView(locationMapStatus);

        locationMapView = new android.webkit.WebView(this);
        locationMapView.getSettings().setJavaScriptEnabled(true);
        locationMapView.setWebViewClient(new android.webkit.WebViewClient());
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (320 * density));
        mapCard.addView(locationMapView, mapParams);

        LinearLayout refreshLocation = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshParams.topMargin = (int) (8 * density);
        locationTabContent.addView(refreshLocation, refreshParams);
        refreshLocation.setOnClickListener(v -> loadMyLocation());

        tripTabContent = new LinearLayout(this);
        tripTabContent.setOrientation(LinearLayout.VERTICAL);
        tripTabContent.setPadding(0, pad / 2, 0, 0);
        tripTabContent.setVisibility(View.GONE);
        panel.addView(tripTabContent);

        tripDate = java.time.LocalDate.now();
        tripDateButton = styledButton(tripDateLabel(), Theme.PRIMARY);
        tripTabContent.addView(tripDateButton);
        tripDateButton.setOnClickListener(v -> {
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                tripDate = java.time.LocalDate.of(year, month + 1, day);
                tripDateButton.setText(tripDateLabel());
                loadTrip();
            }, tripDate.getYear(), tripDate.getMonthValue() - 1, tripDate.getDayOfMonth()).show();
        });

        LinearLayout tripCard = new LinearLayout(this);
        tripCard.setOrientation(LinearLayout.VERTICAL);
        tripCard.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));
        tripCard.setBackground(Theme.cardBackground(this));
        LinearLayout.LayoutParams tripCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tripCardParams.topMargin = (int) (8 * density);
        tripTabContent.addView(tripCard, tripCardParams);

        tripStatus = new TextView(this);
        tripStatus.setTextSize(13);
        tripStatus.setTextColor(Theme.TEXT_SECONDARY);
        tripStatus.setPadding(0, 0, 0, (int) (8 * density));
        tripCard.addView(tripStatus);

        tripMapView = new android.webkit.WebView(this);
        tripMapView.getSettings().setJavaScriptEnabled(true);
        tripMapView.setWebViewClient(new android.webkit.WebViewClient());
        // Only ever loaded with our own bundled HTML/JS via loadDataWithBaseURL, never remote/untrusted
        // content, so exposing a JS interface here is safe.
        tripMapView.addJavascriptInterface(new TripMapBridge(), "AndroidBridge");
        LinearLayout.LayoutParams tripMapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (320 * density));
        tripCard.addView(tripMapView, tripMapParams);

        LinearLayout tripRefresh = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        LinearLayout.LayoutParams tripRefreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tripRefreshParams.topMargin = (int) (8 * density);
        tripTabContent.addView(tripRefresh, tripRefreshParams);
        tripRefresh.setOnClickListener(v -> loadTrip());

        timesheetTabContent = new LinearLayout(this);
        timesheetTabContent.setOrientation(LinearLayout.VERTICAL);
        timesheetTabContent.setPadding(0, pad / 2, 0, 0);
        timesheetTabContent.setVisibility(View.GONE);
        panel.addView(timesheetTabContent);

        LinearLayout timesheetRefresh = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        LinearLayout.LayoutParams timesheetRefreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timesheetRefreshParams.bottomMargin = (int) (8 * density);
        timesheetTabContent.addView(timesheetRefresh, timesheetRefreshParams);
        timesheetRefresh.setOnClickListener(v -> loadTimesheet());

        LinearLayout timesheetSummaryCard = new LinearLayout(this);
        timesheetSummaryCard.setOrientation(LinearLayout.VERTICAL);
        timesheetSummaryCard.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
        timesheetSummaryCard.setBackground(Theme.cardBackground(this));
        timesheetTabContent.addView(timesheetSummaryCard);

        timesheetMessage = new TextView(this);
        timesheetMessage.setTextSize(13);
        timesheetMessage.setTextColor(Theme.TEXT_SECONDARY);
        timesheetSummaryCard.addView(timesheetMessage);

        timesheetTotalText = new TextView(this);
        timesheetTotalText.setTextSize(28);
        timesheetTotalText.setTypeface(timesheetTotalText.getTypeface(), android.graphics.Typeface.BOLD);
        timesheetTotalText.setTextColor(Theme.PRIMARY);
        timesheetSummaryCard.addView(timesheetTotalText);

        timesheetBadgeRow = new LinearLayout(this);
        timesheetBadgeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams timesheetBadgeRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timesheetBadgeRowParams.topMargin = (int) (8 * density);
        timesheetSummaryCard.addView(timesheetBadgeRow, timesheetBadgeRowParams);

        timesheetDaysContainer = new LinearLayout(this);
        timesheetDaysContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams timesheetDaysParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timesheetDaysParams.topMargin = (int) (8 * density);
        timesheetTabContent.addView(timesheetDaysContainer, timesheetDaysParams);

        profileTabContent = new LinearLayout(this);
        profileTabContent.setOrientation(LinearLayout.VERTICAL);
        profileTabContent.setPadding(0, pad / 2, 0, 0);
        profileTabContent.setVisibility(View.GONE);
        panel.addView(profileTabContent);

        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.VERTICAL);
        profileCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        profileCard.setPadding((int) (20 * density), (int) (20 * density), (int) (20 * density), (int) (20 * density));
        profileCard.setBackground(Theme.cardBackground(this));
        profileTabContent.addView(profileCard);

        profileAvatarView = Theme.circularAvatar(this, 88);
        int profileAvatarSize = (int) (88 * density);
        profileCard.addView(profileAvatarView, new LinearLayout.LayoutParams(profileAvatarSize, profileAvatarSize));
        loadMyAvatar(profileAvatarView);

        LinearLayout changePhotoButton = Theme.iconTextButton(this, R.drawable.ic_camera, "Change photo", Theme.PRIMARY);
        LinearLayout.LayoutParams changePhotoParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        changePhotoParams.topMargin = (int) (8 * density);
        profileCard.addView(changePhotoButton, changePhotoParams);
        changePhotoButton.setOnClickListener(v -> showChangePhotoOptions());

        TextView profileNameText = new TextView(this);
        profileNameText.setText(employeeName);
        profileNameText.setTextSize(18);
        profileNameText.setTypeface(profileNameText.getTypeface(), android.graphics.Typeface.BOLD);
        profileNameText.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams profileNameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        profileNameParams.topMargin = (int) (10 * density);
        profileCard.addView(profileNameText, profileNameParams);

        TextView profileUsernameText = new TextView(this);
        profileUsernameText.setText("Signed in as " + username);
        profileUsernameText.setTextSize(13);
        profileUsernameText.setTextColor(Theme.NEUTRAL);
        profileCard.addView(profileUsernameText);

        LinearLayout passwordCard = new LinearLayout(this);
        passwordCard.setOrientation(LinearLayout.VERTICAL);
        passwordCard.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
        passwordCard.setBackground(Theme.cardBackground(this));
        LinearLayout.LayoutParams passwordCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        passwordCardParams.topMargin = (int) (10 * density);
        profileTabContent.addView(passwordCard, passwordCardParams);

        TextView passwordTitle = new TextView(this);
        passwordTitle.setText("Change Password");
        passwordTitle.setTextSize(16);
        passwordTitle.setTypeface(passwordTitle.getTypeface(), android.graphics.Typeface.BOLD);
        passwordTitle.setTextColor(Theme.TEXT_PRIMARY);
        passwordCard.addView(passwordTitle);

        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = (int) (10 * density);

        EditText currentPasswordField = new EditText(this);
        currentPasswordField.setHint("Current password");
        currentPasswordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        currentPasswordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        currentPasswordField.setSingleLine(true);
        Theme.styleInput(currentPasswordField);
        passwordCard.addView(currentPasswordField, fieldParams);

        EditText newPasswordField = new EditText(this);
        newPasswordField.setHint("New password (12+ characters)");
        newPasswordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPasswordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        newPasswordField.setSingleLine(true);
        Theme.styleInput(newPasswordField);
        passwordCard.addView(newPasswordField, new LinearLayout.LayoutParams(fieldParams));

        EditText confirmPasswordField = new EditText(this);
        confirmPasswordField.setHint("Confirm new password");
        confirmPasswordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPasswordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        confirmPasswordField.setSingleLine(true);
        Theme.styleInput(confirmPasswordField);
        passwordCard.addView(confirmPasswordField, new LinearLayout.LayoutParams(fieldParams));

        TextView passwordStatus = new TextView(this);
        passwordStatus.setTextSize(13);
        passwordStatus.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
        passwordCard.addView(passwordStatus);

        Button changePasswordButton = styledButton("Update password", Theme.PRIMARY);
        LinearLayout.LayoutParams changeBtnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        changeBtnParams.topMargin = (int) (4 * density);
        passwordCard.addView(changePasswordButton, changeBtnParams);
        changePasswordButton.setOnClickListener(v -> {
            passwordStatus.setText("");
            try {
                request("change-own-password", new JSONObject().put("token", token)
                        .put("current_password", currentPasswordField.getText().toString())
                        .put("password", newPasswordField.getText().toString())
                        .put("confirmation", confirmPasswordField.getText().toString()), changePasswordButton, r -> {
                    passwordStatus.setTextColor(Theme.SUCCESS);
                    passwordStatus.setText("✓ Password updated.");
                    currentPasswordField.setText("");
                    newPasswordField.setText("");
                    confirmPasswordField.setText("");
                });
            } catch (Exception ignored) {
            }
        });

        doorsTabContent = new LinearLayout(this);
        doorsTabContent.setOrientation(LinearLayout.VERTICAL);
        doorsTabContent.setPadding(0, pad / 2, 0, 0);
        doorsTabContent.setVisibility(View.GONE);
        panel.addView(doorsTabContent);

        LinearLayout doorsCard = new LinearLayout(this);
        doorsCard.setOrientation(LinearLayout.VERTICAL);
        doorsCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsCard.setPadding((int) (20 * density), (int) (20 * density), (int) (20 * density), (int) (20 * density));
        doorsCard.setBackground(Theme.cardBackground(this));
        doorsTabContent.addView(doorsCard);

        // Shows what the rep is currently assigned to sell (Employee Management ▸ Program Assignment
        // on the web) — visible whether or not a door is currently open, so it's never a mystery
        // what they're supposed to be pitching before they even start knocking.
        doorsProgramText = new TextView(this);
        doorsProgramText.setTextSize(13);
        doorsProgramText.setTypeface(doorsProgramText.getTypeface(), android.graphics.Typeface.BOLD);
        doorsProgramText.setTextColor(Theme.PRIMARY);
        doorsProgramText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsProgramText.setVisibility(View.GONE);
        doorsCard.addView(doorsProgramText);

        // Lets a rep see the whole day's walk order before he ever leaves -- same route the office
        // already built, just read-only here. Only shown once a territory + route actually exist.
        doorsPreviewRouteButton = styledButton("▶ Preview Route", Theme.PRIMARY);
        LinearLayout.LayoutParams doorsPreviewRouteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsPreviewRouteParams.topMargin = (int) (8 * density);
        doorsCard.addView(doorsPreviewRouteButton, doorsPreviewRouteParams);
        doorsPreviewRouteButton.setOnClickListener(v -> previewRoute());

        doorsMessageText = new TextView(this);
        doorsMessageText.setText("Tap your position on the map below, closest to the house, to start a door.");
        doorsMessageText.setTextSize(14);
        doorsMessageText.setTextColor(Theme.TEXT_SECONDARY);
        doorsMessageText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams doorsMessageParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsMessageParams.topMargin = (int) (4 * density);
        doorsCard.addView(doorsMessageText, doorsMessageParams);

        doorsAddressText = new TextView(this);
        doorsAddressText.setTextSize(16);
        doorsAddressText.setTypeface(doorsAddressText.getTypeface(), android.graphics.Typeface.BOLD);
        doorsAddressText.setTextColor(Theme.TEXT_PRIMARY);
        doorsAddressText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsAddressText.setVisibility(View.GONE);
        LinearLayout.LayoutParams doorsAddressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsAddressParams.topMargin = (int) (12 * density);
        doorsCard.addView(doorsAddressText, doorsAddressParams);

        doorsTimerText = new TextView(this);
        doorsTimerText.setTextSize(36);
        doorsTimerText.setTypeface(doorsTimerText.getTypeface(), android.graphics.Typeface.BOLD);
        doorsTimerText.setTextColor(Theme.PRIMARY);
        doorsTimerText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        doorsTimerText.setVisibility(View.GONE);
        LinearLayout.LayoutParams doorsTimerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsTimerParams.topMargin = (int) (6 * density);
        doorsCard.addView(doorsTimerText, doorsTimerParams);

        doorsFinishButton = styledButton("Finish", Theme.SUCCESS);
        LinearLayout.LayoutParams doorsFinishParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsFinishParams.topMargin = (int) (16 * density);
        doorsCard.addView(doorsFinishButton, doorsFinishParams);
        doorsFinishButton.setVisibility(View.GONE);
        doorsFinishButton.setOnClickListener(v -> beginFinishDoor());

        // Not every house lines up with an existing trail dot (those are just periodic GPS samples,
        // not one-per-house) — this is the fallback: capture a fresh position right now instead of
        // needing a dot to tap.
        doorsStartHereButton = styledButton("Start a New Door Here", Theme.PRIMARY);
        LinearLayout.LayoutParams doorsStartHereParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsStartHereParams.topMargin = (int) (10 * density);
        doorsCard.addView(doorsStartHereButton, doorsStartHereParams);
        doorsStartHereButton.setOnClickListener(v -> beginStartNewDoorHere());

        // Same trip map as the Trip tab (same trail, same toolbar) embedded right here — starting a
        // door means tapping a position on THIS map, no switching tabs.
        LinearLayout doorsMapCard = new LinearLayout(this);
        doorsMapCard.setOrientation(LinearLayout.VERTICAL);
        doorsMapCard.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));
        doorsMapCard.setBackground(Theme.cardBackground(this));
        LinearLayout.LayoutParams doorsMapCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsMapCardParams.topMargin = (int) (16 * density);
        doorsTabContent.addView(doorsMapCard, doorsMapCardParams);

        doorsMapStatus = new TextView(this);
        doorsMapStatus.setTextSize(12);
        doorsMapStatus.setTextColor(Theme.TEXT_SECONDARY);
        doorsMapStatus.setPadding(0, 0, 0, (int) (6 * density));
        doorsMapCard.addView(doorsMapStatus);

        doorsMapView = new android.webkit.WebView(this);
        doorsMapView.getSettings().setJavaScriptEnabled(true);
        doorsMapView.setWebViewClient(new android.webkit.WebViewClient());
        protectMapGestures(doorsMapView);
        doorsMapView.addJavascriptInterface(new TripMapBridge(), "AndroidBridge");
        LinearLayout.LayoutParams doorsMapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (320 * density));
        doorsMapCard.addView(doorsMapView, doorsMapParams);

        TextView doorsReportTitle = new TextView(this);
        doorsReportTitle.setText("Today's Doors");
        doorsReportTitle.setTextSize(15);
        doorsReportTitle.setTypeface(doorsReportTitle.getTypeface(), android.graphics.Typeface.BOLD);
        doorsReportTitle.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams doorsReportTitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsReportTitleParams.topMargin = (int) (16 * density);
        doorsTabContent.addView(doorsReportTitle, doorsReportTitleParams);

        doorsReportContainer = new LinearLayout(this);
        doorsReportContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams doorsReportContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorsReportContainerParams.topMargin = (int) (6 * density);
        doorsTabContent.addView(doorsReportContainer, doorsReportContainerParams);

        TextView myLeadsTitle = new TextView(this);
        myLeadsTitle.setText("My Leads");
        myLeadsTitle.setTextSize(15);
        myLeadsTitle.setTypeface(myLeadsTitle.getTypeface(), android.graphics.Typeface.BOLD);
        myLeadsTitle.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams myLeadsTitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        myLeadsTitleParams.topMargin = (int) (18 * density);
        doorsTabContent.addView(myLeadsTitle, myLeadsTitleParams);

        myLeadsContainer = new LinearLayout(this);
        myLeadsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams myLeadsContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        myLeadsContainerParams.topMargin = (int) (8 * density);
        doorsTabContent.addView(myLeadsContainer, myLeadsContainerParams);

        eventsTabContent = new LinearLayout(this);
        eventsTabContent.setOrientation(LinearLayout.VERTICAL);
        eventsTabContent.setPadding(0, pad / 2, 0, 0);
        eventsTabContent.setVisibility(View.GONE);
        panel.addView(eventsTabContent);

        LinearLayout eventsHeaderRow = new LinearLayout(this);
        eventsHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        eventsHeaderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        eventsTabContent.addView(eventsHeaderRow);
        TextView eventsTitle = new TextView(this);
        eventsTitle.setText("My Events");
        eventsTitle.setTextSize(18);
        eventsTitle.setTypeface(eventsTitle.getTypeface(), android.graphics.Typeface.BOLD);
        eventsTitle.setTextColor(Theme.TEXT_PRIMARY);
        eventsHeaderRow.addView(eventsTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout eventsRefresh = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        eventsHeaderRow.addView(eventsRefresh);
        eventsRefresh.setOnClickListener(v -> loadMyEvents());

        myEventsContainer = new LinearLayout(this);
        myEventsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams myEventsContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        myEventsContainerParams.topMargin = (int) (12 * density);
        eventsTabContent.addView(myEventsContainer, myEventsContainerParams);

        // D2D's equivalent of "My Events" — S2S reps see what event they're staffed to; D2D reps
        // see what program they're currently out selling, same "assigned by a manager" shape.
        programsTabContent = new LinearLayout(this);
        programsTabContent.setOrientation(LinearLayout.VERTICAL);
        programsTabContent.setPadding(0, pad / 2, 0, 0);
        programsTabContent.setVisibility(View.GONE);
        panel.addView(programsTabContent);

        LinearLayout programsHeaderRow = new LinearLayout(this);
        programsHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        programsHeaderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        programsTabContent.addView(programsHeaderRow);
        TextView programsTitle = new TextView(this);
        programsTitle.setText("My Programs");
        programsTitle.setTextSize(18);
        programsTitle.setTypeface(programsTitle.getTypeface(), android.graphics.Typeface.BOLD);
        programsTitle.setTextColor(Theme.TEXT_PRIMARY);
        programsHeaderRow.addView(programsTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout programsRefresh = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        programsHeaderRow.addView(programsRefresh);
        programsRefresh.setOnClickListener(v -> loadMyPrograms());

        myProgramsContainer = new LinearLayout(this);
        myProgramsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams myProgramsContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        myProgramsContainerParams.topMargin = (int) (12 * density);
        programsTabContent.addView(myProgramsContainer, myProgramsContainerParams);

        arrivalTabContent = new LinearLayout(this);
        arrivalTabContent.setOrientation(LinearLayout.VERTICAL);
        arrivalTabContent.setPadding(0, pad / 2, 0, 0);
        arrivalTabContent.setVisibility(View.GONE);
        panel.addView(arrivalTabContent);

        LinearLayout arrivalHeaderRow = new LinearLayout(this);
        arrivalHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        arrivalHeaderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        arrivalTabContent.addView(arrivalHeaderRow);
        TextView arrivalTitle = new TextView(this);
        arrivalTitle.setText("Today's Worksite");
        arrivalTitle.setTextSize(18);
        arrivalTitle.setTypeface(arrivalTitle.getTypeface(), android.graphics.Typeface.BOLD);
        arrivalTitle.setTextColor(Theme.TEXT_PRIMARY);
        arrivalHeaderRow.addView(arrivalTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout arrivalRefresh = Theme.iconTextButton(this, R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        arrivalHeaderRow.addView(arrivalRefresh);
        arrivalRefresh.setOnClickListener(v -> loadArrivalAssignments());

        arrivalMessageText = new TextView(this);
        arrivalMessageText.setTextSize(13);
        arrivalMessageText.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams arrivalMessageParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        arrivalMessageParams.topMargin = (int) (4 * density);
        arrivalTabContent.addView(arrivalMessageText, arrivalMessageParams);

        arrivalAssignmentsContainer = new LinearLayout(this);
        arrivalAssignmentsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams arrivalContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        arrivalContainerParams.topMargin = (int) (12 * density);
        arrivalTabContent.addView(arrivalAssignmentsContainer, arrivalContainerParams);

        tabScheduleLabel.setOnClickListener(v -> { selectTab(0); closeDrawer(); });
        tabLocationLabel.setOnClickListener(v -> { selectTab(1); closeDrawer(); });
        tabTripLabel.setOnClickListener(v -> { selectTab(2); closeDrawer(); });
        tabTimesheetLabel.setOnClickListener(v -> { selectTab(3); closeDrawer(); });
        tabProfileLabel.setOnClickListener(v -> { selectTab(4); closeDrawer(); });
        tabDoorsLabel.setOnClickListener(v -> { selectTab(5); closeDrawer(); });
        tabEventsLabel.setOnClickListener(v -> { selectTab(6); closeDrawer(); });
        tabProgramsLabel.setOnClickListener(v -> { selectTab(8); closeDrawer(); });
        tabArrivalLabel.setOnClickListener(v -> { selectTab(7); closeDrawer(); });
        selectTab(restoredTabIndex);

        enableTracking();
        installDrawer(menuButton);
    }

    /** Wraps the screen()-built content in a FrameLayout so a slide-out nav drawer (and its scrim) can
     * sit on top of it, then moves the drawer rows built above into the drawer instead of a tab bar. */
    private void installDrawer(ImageButton menuButton) {
        float density = getResources().getDisplayMetrics().density;
        drawerWidthPx = (int) (280 * density);

        ViewGroup decorContent = findViewById(android.R.id.content);
        View content = decorContent.getChildAt(0);
        decorContent.removeView(content);

        FrameLayout root = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        navScrim = new View(this);
        navScrim.setBackgroundColor(0x99000000);
        navScrim.setVisibility(View.GONE);
        navScrim.setOnClickListener(v -> closeDrawer());
        root.addView(navScrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        navDrawer = new LinearLayout(this);
        navDrawer.setOrientation(LinearLayout.VERTICAL);
        navDrawer.setBackgroundColor(Theme.SURFACE);
        navDrawer.setElevation(16 * density);
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(drawerWidthPx, FrameLayout.LayoutParams.MATCH_PARENT);
        drawerParams.gravity = android.view.Gravity.START;
        root.addView(navDrawer, drawerParams);
        navDrawer.setTranslationX(-drawerWidthPx);
        insets(navDrawer);

        TextView drawerTitle = new TextView(this);
        drawerTitle.setText("Menu");
        drawerTitle.setTextSize(20);
        drawerTitle.setTextColor(Theme.TEXT_PRIMARY);
        drawerTitle.setTypeface(drawerTitle.getTypeface(), android.graphics.Typeface.BOLD);
        drawerTitle.setPadding((int) (18 * density), (int) (24 * density), (int) (18 * density), (int) (16 * density));
        navDrawer.addView(drawerTitle);

        navDrawer.addView(tabScheduleLabel);
        navDrawer.addView(tabLocationLabel);
        navDrawer.addView(tabTripLabel);
        navDrawer.addView(tabTimesheetLabel);
        navDrawer.addView(tabProfileLabel);
        // S2S-only: retail events are staffed by S2S reps; D2D has its own "Programs" tab instead,
        // since D2D and S2S never share a program — see SalesProgram (Office module) on the web.
        if ("S2S".equals(programCode)) navDrawer.addView(tabEventsLabel);
        // D2D-only: door-to-door disposition logging has no meaning for other programs (e.g. S2S).
        if ("D2D".equals(programCode)) navDrawer.addView(tabDoorsLabel);
        if ("D2D".equals(programCode)) navDrawer.addView(tabProgramsLabel);
        // S2S-only: worksite check-in has no meaning for D2D reps, who don't have a fixed site.
        if ("S2S".equals(programCode)) navDrawer.addView(tabArrivalLabel);

        setContentView(root);
        menuButton.setOnClickListener(v -> openDrawer());
    }

    private void openDrawer() {
        if (navDrawer == null) return;
        navScrim.setVisibility(View.VISIBLE);
        navDrawer.animate().translationX(0).setDuration(200).start();
    }

    private void closeDrawer() {
        if (navDrawer == null || navDrawer.getTranslationX() == -drawerWidthPx) return;
        navDrawer.animate().translationX(-drawerWidthPx).setDuration(200).withEndAction(() -> navScrim.setVisibility(View.GONE)).start();
    }

    @Override
    public void onBackPressed() {
        if (navDrawer != null && navDrawer.getTranslationX() != -drawerWidthPx) {
            closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    private String tripDateLabel() {
        return "Trip date: " + java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d").format(tripDate);
    }

    private static final String[] SCREEN_TITLES = {"Schedule", "Location", "Trip", "Time Sheet", "Profile", "D2D", "Events", "Check In", "Programs"};

    private void selectTab(int index) {
        currentTabIndex = index;
        if (screenTitleText != null) screenTitleText.setText(index >= 0 && index < SCREEN_TITLES.length ? SCREEN_TITLES[index] : "");
        scheduleTabContent.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        locationTabContent.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tripTabContent.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        timesheetTabContent.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        profileTabContent.setVisibility(index == 4 ? View.VISIBLE : View.GONE);
        doorsTabContent.setVisibility(index == 5 ? View.VISIBLE : View.GONE);
        eventsTabContent.setVisibility(index == 6 ? View.VISIBLE : View.GONE);
        arrivalTabContent.setVisibility(index == 7 ? View.VISIBLE : View.GONE);
        programsTabContent.setVisibility(index == 8 ? View.VISIBLE : View.GONE);
        Theme.styleTabItem(tabScheduleLabel, index == 0);
        Theme.styleTabItem(tabLocationLabel, index == 1);
        Theme.styleTabItem(tabTripLabel, index == 2);
        Theme.styleTabItem(tabTimesheetLabel, index == 3);
        Theme.styleTabItem(tabProfileLabel, index == 4);
        Theme.styleTabItem(tabDoorsLabel, index == 5);
        Theme.styleTabItem(tabEventsLabel, index == 6);
        Theme.styleTabItem(tabArrivalLabel, index == 7);
        Theme.styleTabItem(tabProgramsLabel, index == 8);
        if (index == 0) loadSchedule();
        else if (index == 1) loadMyLocation();
        else if (index == 2) loadTrip();
        else if (index == 3) loadTimesheet();
        else if (index == 5) loadDoors();
        else if (index == 6) loadMyEvents();
        else if (index == 7) loadArrivalAssignments();
        else if (index == 8) loadMyPrograms();
    }

    private void loadMyAvatar(ImageView target) {
        // Independent of the shared request()/busy gate: this should not block, and should not be
        // silently dropped if another network call happens to be in flight at the same time.
        new Thread(() -> {
            android.graphics.Bitmap bitmap = null;
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) new URL(API + "avatar?token=" + java.net.URLEncoder.encode(token, "UTF-8")).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        bitmap = android.graphics.BitmapFactory.decodeStream(in);
                    }
                }
            } catch (Exception ignored) {
                // No photo uploaded yet, or offline — keep the placeholder.
            } finally {
                if (conn != null) conn.disconnect();
            }
            android.graphics.Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (result != null) target.setImageBitmap(result);
            });
        }).start();
    }

    private void showChangePhotoOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Change photo")
                .setItems(new String[]{"Take photo", "Choose from gallery"}, (dialog, which) -> {
                    if (which == 0) startCameraCapture();
                    else startGalleryPick();
                }).show();
    }

    private void startCameraCapture() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            File photoFile = File.createTempFile("avatar_", ".jpg", getCacheDir());
            pendingCameraUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try choosing from gallery instead.").setPositiveButton("OK", null).show();
        }
    }

    private void startGalleryPick() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Some camera apps (notably on certain OEMs) don't reliably return RESULT_OK from
        // ACTION_IMAGE_CAPTURE even when the photo was written successfully to our EXTRA_OUTPUT file —
        // the arrival flow below checks the file itself instead of trusting resultCode.
        if (requestCode == REQUEST_TAKE_SELFIE_PHOTO) {
            if (pendingSelfieFile != null && pendingSelfieFile.length() > 0) {
                if (arrivalMessageText != null) arrivalMessageText.setText("");
                showSelfieReviewDialog();
            } else {
                showArrivalPhotoFailedDialog("selfie", this::beginArrivalSelfie);
            }
            return;
        }
        if (requestCode == REQUEST_TAKE_STORE_PHOTO) {
            if (pendingStorePhotoFile != null && pendingStorePhotoFile.length() > 0) {
                if (arrivalMessageText != null) arrivalMessageText.setText("");
                showStorePhotoReviewDialog();
            } else {
                showArrivalPhotoFailedDialog("store", this::beginArrivalStorePhoto);
            }
            return;
        }
        if (requestCode == REQUEST_TAKE_DOORS_PHOTO) {
            // Same unreliable-resultCode issue as the arrival selfie/store photos (certain OEM camera
            // apps don't reliably return RESULT_OK even when the file wrote fine) — check the file
            // itself instead of trusting resultCode.
            if (pendingDoorsPhotoFile != null && pendingDoorsPhotoFile.length() > 0) {
                locatePhotoThenContinue();
            } else {
                showDoorsPhotoFailedDialog();
            }
            return;
        }
        if (requestCode == REQUEST_TAKE_RETRY_PHOTO) {
            if (pendingRetryPhotoFile != null && pendingRetryPhotoFile.length() > 0 && editingFailedFinish != null) {
                showEditFailedDialog(editingFailedFinish, editingFailedFinishAddress);
            } else {
                pendingRetryPhotoFile = null;
                new AlertDialog.Builder(this)
                        .setTitle("Photo not saved")
                        .setMessage("The photo didn't save — this can happen with some camera apps. Try again.")
                        .setPositiveButton("Retake", (d, w) -> beginRetryPhotoCapture())
                        .setNegativeButton("Cancel", (d, w) -> {
                            if (editingFailedFinish != null) showEditFailedDialog(editingFailedFinish, editingFailedFinishAddress);
                        })
                        .show();
            }
            return;
        }
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQUEST_TAKE_PHOTO && pendingCameraUri != null) {
            uploadAvatarFromUri(pendingCameraUri);
        } else if (requestCode == REQUEST_PICK_PHOTO && data != null && data.getData() != null) {
            uploadAvatarFromUri(data.getData());
        }
    }

    private void showArrivalPhotoFailedDialog(String which, Runnable retry) {
        if (arrivalMessageText != null) arrivalMessageText.setText("");
        new AlertDialog.Builder(this)
                .setTitle("Photo not saved")
                .setMessage("The " + ("selfie".equals(which) ? "selfie" : "store photo") + " didn't save — this can happen with some camera apps. Try again.")
                .setPositiveButton("Retake", (d, w) -> retry.run())
                .setNegativeButton("Cancel", (d, w) -> {
                    activeArrivalAssignment = null;
                    pendingSelfieUri = null;
                    pendingStorePhotoUri = null;
                    pendingSelfieFile = null;
                    pendingStorePhotoFile = null;
                    acceptedStorePhotos.clear();
                })
                .show();
    }

    private void showDoorsPhotoFailedDialog() {
        if (doorsMessageText != null) doorsMessageText.setText("Timer running — tap Finish when the door closes.");
        new AlertDialog.Builder(this)
                .setTitle("Photo not saved")
                .setMessage("The photo didn't save — this can happen with some camera apps. Try again.")
                .setPositiveButton("Retake", (d, w) -> startDoorsPhotoCapture())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A small square preview of a just-captured photo, downsampled so a handful of these on screen
     * at once doesn't risk running out of memory on a low-RAM device. */
    private ImageView reviewThumbnail(File file, int sizeDp) {
        ImageView iv = new ImageView(this);
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        iv.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = 4;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap != null) iv.setImageBitmap(bitmap);
        } catch (Exception ignored) {
        }
        return iv;
    }

    private void showSelfieReviewDialog() {
        if (pendingSelfieFile == null) {
            showArrivalPhotoFailedDialog("selfie", this::beginArrivalSelfie);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * density);
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(reviewThumbnail(pendingSelfieFile, 160));
        new AlertDialog.Builder(this)
                .setTitle("Selfie captured")
                .setView(panel)
                .setCancelable(false)
                .setPositiveButton("Looks good", (d, w) -> beginArrivalStorePhoto())
                .setNegativeButton("Retake", (d, w) -> beginArrivalSelfie())
                .show();
    }

    private void showStorePhotoReviewDialog() {
        if (pendingStorePhotoFile == null) {
            showArrivalPhotoFailedDialog("store", this::beginArrivalStorePhoto);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * density);
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(reviewThumbnail(pendingStorePhotoFile, 160));
        int totalSoFar = acceptedStorePhotos.size() + 1;
        TextView countText = new TextView(this);
        countText.setText(totalSoFar + " of up to " + MAX_STORE_PHOTOS + " store photos");
        countText.setTextSize(13);
        countText.setTextColor(Theme.TEXT_SECONDARY);
        countText.setPadding(0, (int) (8 * density), 0, 0);
        panel.addView(countText);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Store photo captured")
                .setView(panel)
                .setCancelable(false)
                .setPositiveButton("Done — Submit", (d, w) -> {
                    acceptedStorePhotos.add(pendingStorePhotoFile);
                    pendingStorePhotoFile = null;
                    submitArrival();
                })
                .setNegativeButton("Retake", (d, w) -> beginArrivalStorePhoto());
        if (totalSoFar < MAX_STORE_PHOTOS) {
            builder.setNeutralButton("Add another", (d, w) -> {
                acceptedStorePhotos.add(pendingStorePhotoFile);
                pendingStorePhotoFile = null;
                beginArrivalStorePhoto();
            });
        }
        builder.show();
    }

    private void uploadAvatarFromUri(android.net.Uri uri) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            String error = null;
            HttpsURLConnection conn = null;
            try {
                byte[] imageBytes = readAllBytes(getContentResolver().openInputStream(uri));
                if (imageBytes.length > 2 * 1024 * 1024) throw new IOException("TOO_LARGE");
                String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
                conn = (HttpsURLConnection) new URL(API + "avatar/upload").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = conn.getOutputStream()) {
                    writeMultipartField(out, boundary, "token", token);
                    writeMultipartFile(out, boundary, "photo", "avatar.jpg", "image/jpeg", imageBytes);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                JSONObject resp = new JSONObject(new String(readAllBytes(stream), StandardCharsets.UTF_8));
                if (code < 200 || code >= 300 || !resp.optBoolean("success")) error = resp.optString("message", "Unable to upload photo.");
            } catch (Exception e) {
                error = "TOO_LARGE".equals(e.getMessage()) ? "That photo is too large. Choose a smaller image." : "Unable to upload photo. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            String problem = error;
            runOnUiThread(() -> {
                busy = false;
                if (problem != null) {
                    new AlertDialog.Builder(this).setTitle("Upload photo").setMessage(problem).setPositiveButton("OK", null).show();
                } else {
                    if (profileAvatarView != null) loadMyAvatar(profileAvatarView);
                    if (greetingAvatarView != null) loadMyAvatar(greetingAvatarView);
                }
            });
        }).start();
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
        in.close();
        return bytes.toByteArray();
    }

    private static void writeMultipartField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMultipartFile(OutputStream out, String boundary, String field, String filename, String mime, byte[] bytes) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void loadMyLocation() {
        if (locationMapView == null) return;
        if (!hasLocationPermission()) {
            locationMapStatus.setText("Grant location permission to see your current position.");
            return;
        }
        locationMapStatus.setText("Locating…");
        android.location.LocationManager manager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            android.location.Location best = null;
            for (String provider : manager.getProviders(true)) {
                android.location.Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null) {
                showLocationOnMap(best.getLatitude(), best.getLongitude(), best.getAccuracy());
            } else {
                manager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        showLocationOnMap(location.getLatitude(), location.getLongitude(), location.getAccuracy());
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        locationMapStatus.setText("Location provider disabled. Enable Location to see your position.");
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }
                }, android.os.Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            locationMapStatus.setText("Grant location permission to see your current position.");
        }
    }

    private void showLocationOnMap(double lat, double lng, float accuracy) {
        String html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\">"
                + "<style>html,body,#map{height:100%;margin:0;padding:0;}</style></head><body>"
                + "<div id=\"map\"></div><script src=\"leaflet.js\"></script><script>"
                + "var map=L.map('map',{preferCanvas:true}).setView([" + lat + "," + lng + "],16);"
                + "L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);"
                + "L.circleMarker([" + lat + "," + lng + "],{radius:9,color:'#fff',fillColor:'#2563eb',fillOpacity:1,weight:3}).addTo(map).bindPopup('You are here').openPopup();"
                + "</script></body></html>";
        locationMapView.loadDataWithBaseURL("file:///android_asset/leaflet/", html, "text/html", "UTF-8", null);
        locationMapStatus.setText("Accurate to about " + Math.round(accuracy) + " m · " + java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(java.time.LocalTime.now()));
    }

    private void loadTrip() {
        if (tripMapView == null) return;
        tripStatus.setText("Loading…");
        try {
            request("trip", new JSONObject().put("token", token).put("date", tripDate.toString()), null, this::renderTrip);
        } catch (Exception e) {
            tripStatus.setText("Unable to load trip.");
        }
    }

    /** Shared by the Trip tab and the D2D tab's embedded map — same trail, same toolbar, same door
     * outcome markers (trip-map.js renders the "dispositions" array when present). */
    private String buildTripMapHtml(org.json.JSONArray points, org.json.JSONArray scheduled, org.json.JSONArray dispositions) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\"><style>html,body{height:100%;margin:0;font:14px sans-serif}body{display:flex;flex-direction:column}#map{flex:1;min-height:200px}.toolbar{padding:8px;display:flex;gap:8px}button{padding:8px;border:1px solid #2563eb;border-radius:5px;background:white;color:#2563eb}.start-icon span{display:block;background:#2563eb;color:white;border:3px solid white;border-radius:50%;width:32px;height:32px;text-align:center;line-height:32px;box-shadow:0 1px 5px #555}.leaflet-overlay-pane canvas,.leaflet-overlay-pane svg{max-width:none!important;max-height:none!important}#status{padding:4px 8px;font-size:12px}</style></head><body>"
                + "<div class=\"toolbar\"><button id=\"start\">▶ Start</button><button id=\"fit\">Show full route</button><button id=\"area\">Assigned area</button></div><div id=\"status\"></div><div id=\"map\"></div><script src=\"leaflet.js\"></script><script>"
                + "var points=" + points.toString().replace("<", "\\u003c") + ";var scheduled=" + scheduled.toString().replace("<", "\\u003c") + ";var dispositions=" + dispositions.toString().replace("<", "\\u003c") + ";"
                + "</script><script src=\"trip-map.js\"></script></body></html>";
    }

    /** Read-only preview of today's already-built walk order for whichever territory the office
     * scheduled this rep into — lets him see the whole day before he ever leaves the house. Never
     * generates or builds anything on the phone's behalf; if the office hasn't set it up yet, the
     * server just says so and this shows that message instead of a map. */
    private void previewRoute() {
        previewRoute(null);
    }

    /** territoryId picks a specific territory when the rep has more than one scheduled today; left
     * null, the server auto-picks if there's only one, or hands back a `territories` list to choose
     * from instead of silently always showing whichever one happened to start earliest. */
    private void previewRoute(Integer territoryId) {
        try {
            JSONObject body = new JSONObject().put("token", token);
            if (territoryId != null) body.put("territory_id", (int) territoryId);
            request("telemapper/territory/my-route", body, null, r -> {
                org.json.JSONArray territories = r.optJSONArray("territories");
                if (territories != null && territories.length() > 0) {
                    showTerritoryPickerDialog(territories);
                    return;
                }
                org.json.JSONArray stops = r.getJSONArray("route");
                if (stops.length() == 0) {
                    String msg = r.isNull("message") ? "No route available yet." : r.getString("message");
                    new AlertDialog.Builder(this).setTitle("Preview Route").setMessage(msg).setPositiveButton("OK", null).show();
                    return;
                }
                String territoryName = r.isNull("territory_name") ? "Your territory" : r.getString("territory_name");
                org.json.JSONArray unrouted = r.optJSONArray("unrouted");
                if (unrouted == null) unrouted = new org.json.JSONArray();
                showRoutePreviewDialog(territoryName, stops, unrouted);
            });
        } catch (Exception ignored) {
        }
    }

    private void showTerritoryPickerDialog(org.json.JSONArray territories) throws Exception {
        String[] names = new String[territories.length()];
        int[] ids = new int[territories.length()];
        for (int i = 0; i < territories.length(); i++) {
            JSONObject t = territories.getJSONObject(i);
            names[i] = t.getString("territory_name");
            ids[i] = t.getInt("territory_id");
        }
        new AlertDialog.Builder(this)
                .setTitle("Which territory?")
                .setItems(names, (d, which) -> previewRoute(ids[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRoutePreviewDialog(String territoryName, org.json.JSONArray stops, org.json.JSONArray unrouted) throws Exception {
        float density = getResources().getDisplayMetrics().density;
        int n = stops.length();

        // Same straight-line + average-walking-speed estimate the office's own route map shows.
        double totalMeters = 0;
        java.util.LinkedHashSet<String> streetOrder = new java.util.LinkedHashSet<>();
        String lastStreet = null;
        for (int i = 0; i < n; i++) {
            JSONObject s = stops.getJSONObject(i);
            String street = s.getString("street");
            if (!street.equals(lastStreet)) streetOrder.add(street);
            lastStreet = street;
            if (i > 0) {
                JSONObject prev = stops.getJSONObject(i - 1);
                double latAvg = Math.toRadians((s.getDouble("latitude") + prev.getDouble("latitude")) / 2);
                double dx = (s.getDouble("longitude") - prev.getDouble("longitude")) * Math.cos(latAvg) * 111320;
                double dy = (s.getDouble("latitude") - prev.getDouble("latitude")) * 111320;
                totalMeters += Math.sqrt(dx * dx + dy * dy);
            }
        }
        double miles = totalMeters / 1609.34;
        long walkMinutes = Math.round(miles / 3 * 60);
        long doorMinutes = (long) n * 2;
        double hours = Math.round((walkMinutes + doorMinutes) / 60.0 * 10) / 10.0;

        // A plain Dialog instead of AlertDialog.Builder: AlertDialog wraps a custom view in its own
        // internal scroll container that measures it as wrap_content regardless of the view's own
        // requested layout params, which is exactly what was squashing the WebView to near-zero
        // height. A plain Dialog with setContentView() has no such wrapper.
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(android.graphics.Color.WHITE);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerRow.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (8 * density));
        container.addView(headerRow);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(territoryName);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titleCol.addView(title);
        TextView stats = new TextView(this);
        stats.setText(n + " stops · " + streetOrder.size() + " streets · " + String.format(java.util.Locale.US, "%.2f", miles) + " mi · ~" + hours + " hr");
        stats.setTextSize(13);
        stats.setTextColor(Theme.TEXT_SECONDARY);
        titleCol.addView(stats);
        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeText = new TextView(this);
        closeText.setText("Close");
        closeText.setTextColor(Theme.PRIMARY);
        closeText.setTypeface(closeText.getTypeface(), android.graphics.Typeface.BOLD);
        closeText.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        closeText.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(closeText);

        android.webkit.WebView webView = new android.webkit.WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new android.webkit.WebViewClient());
        webView.addJavascriptInterface(new RoutePreviewBridge(), "AndroidBridge");
        webView.loadDataWithBaseURL("file:///android_asset/leaflet/", buildRoutePreviewHtml(stops, unrouted), "text/html", "UTF-8", null);

        // Floating "My Location" button over the map -- jumps straight to where the rep actually is
        // instead of making him pan/zoom around looking for himself before he can even start.
        android.widget.FrameLayout mapStack = new android.widget.FrameLayout(this);
        mapStack.addView(webView, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        Button locateButton = styledButton("📍 My Location", Theme.PRIMARY);
        android.widget.FrameLayout.LayoutParams locateParams = new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        locateParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        locateParams.setMargins(0, 0, (int) (16 * density), (int) (16 * density));
        mapStack.addView(locateButton, locateParams);
        container.addView(mapStack, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        locateButton.setElevation(6 * density);
        locateButton.setOnClickListener(v -> locateMeOnRoutePreview(webView));

        dialog.setContentView(container);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // The default Dialog theme insets its window with margins and a rounded card background
            // -- strip both so the content actually reaches the edges instead of leaving a gap.
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
    }

    /** Jumps the route-preview map straight to the rep's current position instead of making him
     * pan/zoom around the whole territory looking for himself first. */
    private void locateMeOnRoutePreview(android.webkit.WebView webView) {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Grant location permission to use this.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.location.LocationManager manager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            android.location.Location best = null;
            for (String provider : manager.getProviders(true)) {
                android.location.Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null) {
                goToMyLocationOnRoutePreview(webView, best.getLatitude(), best.getLongitude());
            } else {
                manager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        goToMyLocationOnRoutePreview(webView, location.getLatitude(), location.getLongitude());
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        Toast.makeText(LoginActivity.this, "Enable Location to use this.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }
                }, android.os.Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Grant location permission to use this.", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToMyLocationOnRoutePreview(android.webkit.WebView webView, double lat, double lon) {
        webView.evaluateJavascript("if(window.locateMe)window.locateMe(" + lat + "," + lon + ");", null);
    }

    private String buildRoutePreviewHtml(org.json.JSONArray stops, org.json.JSONArray unrouted) {
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\"><style>html,body{height:100%;margin:0;font:14px sans-serif}body{display:flex;flex-direction:column}#map{flex:1;min-height:200px}.toolbar{padding:8px;display:flex;gap:8px;align-items:center}#play{padding:9px 16px;border:none;border-radius:20px;background:#ff8c42;color:#2a1200;font-weight:600}#caption{padding:0 8px;font-size:12px;color:#555;flex:1}#status{padding:4px 8px;font-size:12px;color:#666}</style></head><body>"
                + "<div class=\"toolbar\"><button id=\"play\">▶ Play</button><span id=\"caption\">Tap Play to preview the walk</span></div><div id=\"status\"></div><div id=\"map\"></div><script src=\"leaflet.js\"></script><script>"
                + "var stops=" + stops.toString().replace("<", "\\u003c") + ";var unrouted=" + unrouted.toString().replace("<", "\\u003c") + ";"
                + "</script><script src=\"route-preview.js\"></script></body></html>";
    }

    /** Exposed to route-preview.js — opens a stop's real photo/street-level view in the device's own
     * Google Maps app (falls back to a browser tab), instead of trying to embed a photo viewer. */
    private final class RoutePreviewBridge {
        @android.webkit.JavascriptInterface
        public void openStreetView(double lat, double lon) {
            runOnUiThread(() -> {
                try {
                    android.net.Uri gmmUri = android.net.Uri.parse("google.streetview:cbll=" + lat + "," + lon);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmUri);
                    intent.setPackage("com.google.android.apps.maps");
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        android.net.Uri webUri = android.net.Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=" + lat + "," + lon);
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, webUri));
                    }
                } catch (Exception e) {
                    Toast.makeText(LoginActivity.this, "Unable to open Street View.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void renderTrip(JSONObject data) throws Exception {
        org.json.JSONArray points = data.getJSONArray("points");
        org.json.JSONArray scheduled = data.optJSONArray("scheduled");
        if (scheduled == null) scheduled = new org.json.JSONArray();
        org.json.JSONArray dispositions = data.optJSONArray("dispositions");
        if (dispositions == null) dispositions = new org.json.JSONArray();
        tripMapView.loadDataWithBaseURL("file:///android_asset/leaflet/", buildTripMapHtml(points, scheduled, dispositions), "text/html", "UTF-8", null);
        tripStatus.setText(points.length() + " position" + (points.length() == 1 ? "" : "s") + " recorded on " + tripDateLabel().replace("Trip date: ", ""));
    }

    private void loadDoors() {
        loadDoors(false, null);
    }

    private void loadDoors(boolean finishAfterLoad, Integer expectedDoorId) {
        if (doorsMessageText == null) return;
        loadMyLeads();
        loadDoorsMap();
        if (dispositionQueue.pendingCountForEmployee(employeeId) > 0) {
            final String currentToken = token;
            final long currentEmployeeId = employeeId;
            new Thread(() -> dispositionQueue.drain(currentToken, currentEmployeeId)).start();
        }
        try {
            request("telemapper/disposition/active", new JSONObject().put("token", token), null, r -> {
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
                    renderDoorsIdle();
                    if (finishAfterLoad) new AlertDialog.Builder(this).setMessage("This door is already finished. Refreshing your doors.").setPositiveButton("OK", null).show();
                } else {
                    JSONObject active = r.getJSONObject("active");
                    activeDispositionId = active.getInt("disposition_id");
                    activeDoorStartedMs = java.time.Instant.parse(active.getString("created_utc").replace(' ', 'T') + "Z").toEpochMilli();
                    activeDoorLat = active.isNull("latitude") ? null : active.getDouble("latitude");
                    activeDoorLon = active.isNull("longitude") ? null : active.getDouble("longitude");
                    renderDoorsActive(active.getString("address"));
                    if (finishAfterLoad && java.util.Objects.equals(activeDispositionId, expectedDoorId)) beginFinishDoor();
                    else if (finishAfterLoad) new AlertDialog.Builder(this).setMessage("The active door has changed. Please select the current door again.").setPositiveButton("OK", null).show();
                }
            }, true);
        } catch (Exception ignored) {
        }
    }

    /** Independent of the shared request()/busy gate — loadDoors() already fires off loadMyLeads()
     * and the active-door check back to back, and request() silently drops any call that arrives
     * while another is still in flight, so this needs its own connection rather than competing for
     * that single slot. Always today's trip, regardless of whatever date the Trip tab itself has
     * picked, since a door can only ever be started "now". */
    private void loadDoorsMap() {
        if (doorsMapView == null) return;
        new Thread(() -> {
            JSONObject response = null;
            HttpsURLConnection conn = null;
            try {
                JSONObject body = new JSONObject().put("token", token).put("date", java.time.LocalDate.now().toString());
                conn = (HttpsURLConnection) new URL(API + "trip").openConnection();
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
                    JSONObject candidate = new JSONObject(new String(readAllBytes(conn.getInputStream()), StandardCharsets.UTF_8));
                    if (candidate.optBoolean("success")) response = candidate;
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            JSONObject result = response;
            runOnUiThread(() -> {
                if (result == null) return;
                try {
                    org.json.JSONArray points = result.getJSONArray("points");
                    org.json.JSONArray scheduled = result.optJSONArray("scheduled");
                    if (scheduled == null) scheduled = new org.json.JSONArray();
                    org.json.JSONArray dispositions = result.optJSONArray("dispositions");
                    if (dispositions == null) dispositions = new org.json.JSONArray();
                    doorsMapView.loadDataWithBaseURL("file:///android_asset/leaflet/", buildTripMapHtml(points, scheduled, dispositions), "text/html", "UTF-8", null);
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
        float density = getResources().getDisplayMetrics().density;

        java.util.List<DispositionQueue.Finish> failed = dispositionQueue.failedForEmployee(employeeId);
        for (DispositionQueue.Finish f : failed) {
            addNeedsAttentionRow(density, f, addressForDisposition(dispositions, f.dispositionId), false);
        }
        java.util.List<DispositionQueue.Finish> unassigned = dispositionQueue.failedUnassigned();
        for (DispositionQueue.Finish f : unassigned) {
            addNeedsAttentionRow(density, f, addressForDisposition(dispositions, f.dispositionId), true);
        }

        if (dispositions.length() == 0) {
            if (failed.isEmpty() && unassigned.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("No doors logged yet today.");
                empty.setTextSize(13);
                empty.setTextColor(Theme.TEXT_SECONDARY);
                doorsReportContainer.addView(empty);
            }
            return;
        }
        for (int i = dispositions.length() - 1; i >= 0; i--) {
            JSONObject d = dispositions.getJSONObject(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
            row.setBackground(Theme.cardBackground(this));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = (int) (6 * density);
            doorsReportContainer.addView(row, rowParams);

            TextView addressText = new TextView(this);
            addressText.setText(d.optString("address", "Unnamed door"));
            addressText.setTextSize(14);
            addressText.setTextColor(Theme.TEXT_PRIMARY);
            row.addView(addressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            boolean inProgress = d.optBoolean("in_progress", false);
            String status = d.optString("status", "");
            TextView badge = inProgress
                    ? Theme.statusBadge(this, "In progress", Theme.PRIMARY)
                    : Theme.statusBadge(this, doorsStatusLabel(status), doorsStatusColorInt(status));
            row.addView(badge);
            if (inProgress) {
                row.setContentDescription(d.optString("address", "Door") + ". In progress. Tap to finish.");
                row.setOnClickListener(v -> new AlertDialog.Builder(this)
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
                row.setContentDescription(address + ". " + doorsStatusLabel(status) + ". Tap to correct.");
                row.setOnClickListener(v -> showEditOutcomeDialog(dispositionId, currentStatus, currentNote, address));
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

    private void addNeedsAttentionRow(float density, DispositionQueue.Finish f, String address, boolean unassigned) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
        row.setBackground(Theme.cardBackground(this));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (6 * density);
        doorsReportContainer.addView(row, rowParams);

        TextView addressText = new TextView(this);
        addressText.setText(address);
        addressText.setTextSize(14);
        addressText.setTextColor(Theme.TEXT_PRIMARY);
        row.addView(addressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = Theme.statusBadge(this, unassigned ? "Unidentified" : "Needs attention", Theme.WARNING);
        row.addView(badge);

        row.setContentDescription(address + (unassigned ? ". Unidentified door, may not be yours. Tap to review." : ". Needs attention. Tap to review."));
        row.setOnClickListener(v -> {
            if (unassigned) verifyThenShowUnassignedDialog(f, address);
            else showNeedsAttentionDialog(f, address);
        });
    }

    /** An unassigned (employee_id 0) failed record might belong to a completely different employee
     * who's never even touched this device -- never reveal its photo/note, or offer to edit/discard
     * it, without the server confirming it's genuinely this signed-in employee's first. Otherwise
     * whoever happens to be signed in when it surfaces could casually view, or permanently destroy,
     * another rep's work. Fails closed: if the check itself can't complete, access is denied the same
     * as a confirmed "not yours" -- never assume access just because the network is uncooperative.
     * Also re-checks the captured token/employeeId still match the live session once the response
     * arrives -- a slow check could otherwise resolve after the signed-in employee changed, acting on
     * (or showing) a record that no longer has anything to do with who's actually using the device now. */
    private void verifyThenShowUnassignedDialog(DispositionQueue.Finish f, String address) {
        Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show();
        final String currentToken = token;
        final long currentEmployeeId = employeeId;
        new Thread(() -> {
            EmployeeApi.FinishStatus check = EmployeeApi.checkFinishStatus(currentToken, f.dispositionId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                // The signed-in session moved on while this was checking (signed out, or a different
                // employee signed in) -- this result no longer applies to anyone currently using the
                // device; acting on it now would claim or show someone else's door under a stale session.
                if (!currentToken.equals(token) || currentEmployeeId != employeeId) return;
                if (check != null && check.found) {
                    // Confirmed genuinely theirs -- claim it locally so it's an ordinary record for
                    // this employee from now on, not something every signed-in user can see. Reload
                    // it afterward: this method's own f still has employeeId 0, and editing through
                    // that stale object would later re-save employee_id 0 and undo the claim.
                    DispositionQueue.Finish claimed = dispositionQueue.claimUnassignedAndReload(currentEmployeeId, f.dispositionId, f.queuedMs);
                    if (claimed != null) showNeedsAttentionDialog(claimed, address);
                    loadDoors();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Not yours")
                            .setMessage(check == null
                                    ? "Couldn't verify this record right now. Try again when you have a signal."
                                    : "This record isn't linked to your account and can't be opened from this device.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    /** A door the server permanently rejected (see DispositionQueue.drain()) -- kept locally with its
     * photo rather than silently discarded, so the rep decides what happens to it: fix whatever was
     * wrong and try again, or give up on it for good. Never blindly resends the exact same bytes --
     * if the photo or an answer was genuinely the problem, retrying unmodified would just fail the
     * same way again. Only ever called once a record is confirmed to belong to this employee -- a
     * previously-unassigned record is claimed (see verifyThenShowUnassignedDialog()) before it ever
     * reaches here, so this never exposes another employee's photo/note. */
    private void showNeedsAttentionDialog(DispositionQueue.Finish f, String address) {
        String reason = f.failureReason != null && !f.failureReason.isEmpty() ? f.failureReason : "Unable to save this door.";
        new AlertDialog.Builder(this)
                .setTitle("Needs attention")
                .setMessage(address + "\n\n" + reason)
                .setPositiveButton("Edit & Retry", (d, w) -> showEditFailedDialog(f, address))
                .setNeutralButton("Discard", (d, w) -> new AlertDialog.Builder(this)
                        .setTitle("Discard this door?")
                        .setMessage("This permanently deletes the saved photo and outcome for " + address + ". This can't be undone.")
                        .setPositiveButton("Discard", (d2, w2) -> {
                            dispositionQueue.discardFailed(f.dispositionId, f.queuedMs, f.photoPath);
                            loadDoors();
                        })
                        .setNegativeButton("Cancel", null)
                        .show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Same outcome picker as showEditOutcomeDialog (status/note/callback), but for a submission the
     * server never actually accepted -- editing it re-queues the correction locally (via add()) and
     * drains again, rather than calling the server's "correct an already-saved door" endpoint, since
     * there's nothing saved server-side yet to correct. Also offers retaking the photo, for when the
     * photo itself was the problem (corrupt file, unreadable image, etc). */
    private void showEditFailedDialog(DispositionQueue.Finish f, String address) {
        editingFailedFinish = f;
        editingFailedFinishAddress = address;
        String initialStatus = editingDraftStatus != null ? editingDraftStatus : f.status;
        String initialNote = editingDraftNote != null ? editingDraftNote : f.note;
        String initialCallbackDateStr = editingDraftCallbackDate != null ? editingDraftCallbackDate : f.callbackDate;
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);

        TextView photoLabel = new TextView(this);
        photoLabel.setText("PHOTO");
        photoLabel.setTextSize(12);
        photoLabel.setTypeface(photoLabel.getTypeface(), android.graphics.Typeface.BOLD);
        photoLabel.setTextColor(Theme.TEXT_SECONDARY);
        photoLabel.setLetterSpacing(0.06f);
        container.addView(photoLabel);

        LinearLayout photoRow = new LinearLayout(this);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        photoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams photoRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        photoRowParams.topMargin = (int) (4 * density);
        container.addView(photoRow, photoRowParams);

        File currentPhotoFile = pendingRetryPhotoFile != null ? pendingRetryPhotoFile : new File(f.photoPath);
        ImageView thumb = reviewThumbnail(currentPhotoFile, 72);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        thumbParams.rightMargin = (int) (12 * density);
        photoRow.addView(thumb, thumbParams);

        Button retakeButton = styledButton("Retake Photo", Theme.PRIMARY);
        photoRow.addView(retakeButton);

        TextView outcomeLabel = new TextView(this);
        outcomeLabel.setText("OUTCOME");
        outcomeLabel.setTextSize(12);
        outcomeLabel.setTypeface(outcomeLabel.getTypeface(), android.graphics.Typeface.BOLD);
        outcomeLabel.setTextColor(Theme.TEXT_SECONDARY);
        outcomeLabel.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams outcomeLabelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        outcomeLabelParams.topMargin = (int) (16 * density);
        container.addView(outcomeLabel, outcomeLabelParams);

        String[] statusCodes = {"sold", "not_home", "already_serviced", "not_interested", "not_owner", "callback", "do_not_call", "other"};
        String[] statusLabels = {"Sold", "Not Home", "Already Had Service", "Not Interested", "Not the Owner", "Come Back Another Time", "Do Not Call", "Other"};
        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.content.res.ColorStateList radioTint = android.content.res.ColorStateList.valueOf(Theme.PRIMARY);
        for (int i = 0; i < statusCodes.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(statusLabels[i]);
            rb.setTextColor(Theme.TEXT_PRIMARY);
            rb.setButtonTintList(radioTint);
            rb.setTag(statusCodes[i]);
            LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rbParams.topMargin = (int) (4 * density);
            radioGroup.addView(rb, rbParams);
            if (statusCodes[i].equals(initialStatus)) rb.setChecked(true);
        }
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        radioParams.topMargin = (int) (8 * density);
        container.addView(radioGroup, radioParams);

        LinearLayout callbackDateRow = new LinearLayout(this);
        callbackDateRow.setOrientation(LinearLayout.VERTICAL);
        callbackDateRow.setVisibility("callback".equals(initialStatus) ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams callbackDateRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackDateRowParams.topMargin = (int) (12 * density);
        container.addView(callbackDateRow, callbackDateRowParams);

        TextView callbackLabel = new TextView(this);
        callbackLabel.setText("CALL BACK ON");
        callbackLabel.setTextSize(12);
        callbackLabel.setTypeface(callbackLabel.getTypeface(), android.graphics.Typeface.BOLD);
        callbackLabel.setTextColor(Theme.TEXT_SECONDARY);
        callbackLabel.setLetterSpacing(0.06f);
        callbackDateRow.addView(callbackLabel);

        java.time.LocalDate parsedCallbackDate;
        try {
            parsedCallbackDate = initialCallbackDateStr != null ? java.time.LocalDate.parse(initialCallbackDateStr) : java.time.LocalDate.now().plusDays(1);
        } catch (Exception e) {
            parsedCallbackDate = java.time.LocalDate.now().plusDays(1);
        }
        final java.time.LocalDate[] callbackDateHolder = {parsedCallbackDate};
        Button callbackDateButton = styledButton(callbackDateHolder[0].toString(), Theme.PRIMARY);
        LinearLayout.LayoutParams callbackButtonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackButtonParams.topMargin = (int) (4 * density);
        callbackDateRow.addView(callbackDateButton, callbackButtonParams);
        callbackDateButton.setOnClickListener(v -> new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            callbackDateHolder[0] = java.time.LocalDate.of(year, month + 1, day);
            callbackDateButton.setText(callbackDateHolder[0].toString());
        }, callbackDateHolder[0].getYear(), callbackDateHolder[0].getMonthValue() - 1, callbackDateHolder[0].getDayOfMonth()).show());

        EditText noteField = new EditText(this);
        noteField.setHint("callback".equals(initialStatus) || "other".equals(initialStatus) ? "Reason" : "Note (optional)");
        noteField.setText(initialNote);
        Theme.styleInput(noteField);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = (int) (16 * density);
        noteParams.bottomMargin = (int) (4 * density);
        container.addView(noteField, noteParams);

        // The server rejected this as a repeat sale/close at an address that already has one on
        // record -- it requires an explicit justification before accepting another one, so offer the
        // field right here instead of making the rep hit the same rejection again blind.
        boolean wasDuplicateRejection = f.failureReason != null && f.failureReason.contains("was already marked");
        String initialDuplicateReason = editingDraftDuplicateReason != null ? editingDraftDuplicateReason : f.duplicateOverrideReason;
        EditText duplicateReasonField = null;
        if (wasDuplicateRejection) {
            TextView duplicateLabel = new TextView(this);
            duplicateLabel.setText("WHY RECORD ANOTHER SALE HERE");
            duplicateLabel.setTextSize(12);
            duplicateLabel.setTypeface(duplicateLabel.getTypeface(), android.graphics.Typeface.BOLD);
            duplicateLabel.setTextColor(Theme.WARNING);
            duplicateLabel.setLetterSpacing(0.06f);
            LinearLayout.LayoutParams duplicateLabelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            duplicateLabelParams.topMargin = (int) (12 * density);
            container.addView(duplicateLabel, duplicateLabelParams);

            duplicateReasonField = new EditText(this);
            duplicateReasonField.setHint("e.g. different unit, prior outcome was wrong");
            duplicateReasonField.setText(initialDuplicateReason);
            Theme.styleInput(duplicateReasonField);
            LinearLayout.LayoutParams duplicateReasonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            duplicateReasonParams.topMargin = (int) (4 * density);
            container.addView(duplicateReasonField, duplicateReasonParams);
        }
        final EditText finalDuplicateReasonField = duplicateReasonField;

        retakeButton.setOnClickListener(v -> {
            // Preserve whatever's currently typed/selected across the trip through the camera app.
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId != -1) {
                android.widget.RadioButton checked = radioGroup.findViewById(checkedId);
                if (checked != null && checked.getTag() != null) editingDraftStatus = (String) checked.getTag();
            }
            editingDraftNote = noteField.getText().toString();
            editingDraftCallbackDate = callbackDateHolder[0].toString();
            if (finalDuplicateReasonField != null) editingDraftDuplicateReason = finalDuplicateReasonField.getText().toString();
            beginRetryPhotoCapture();
        });

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            android.widget.RadioButton checked = group.findViewById(checkedId);
            String tag = checked != null ? (String) checked.getTag() : null;
            callbackDateRow.setVisibility("callback".equals(tag) ? View.VISIBLE : View.GONE);
            noteField.setHint("other".equals(tag) ? "Reason (required)" : "Note (optional)");
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "Edit " + address, Theme.PRIMARY))
                .setView(container)
                .setPositiveButton("Save & Retry", null)
                .setNegativeButton("Cancel", (d, w) -> clearEditingFailedState())
                .show();
        Theme.styleDialog(dialog, Theme.PRIMARY);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            String status = "sold";
            if (checkedId != -1) {
                android.widget.RadioButton checked = radioGroup.findViewById(checkedId);
                if (checked != null && checked.getTag() != null) status = (String) checked.getTag();
            }
            String note = noteField.getText().toString().trim();
            if ("other".equals(status) && note.isEmpty()) {
                noteField.setError("Enter a reason");
                return;
            }
            String duplicateReason = finalDuplicateReasonField != null ? finalDuplicateReasonField.getText().toString().trim() : null;
            if (finalDuplicateReasonField != null && CLOSING_STATUSES.contains(status) && duplicateReason.isEmpty()) {
                finalDuplicateReasonField.setError("Explain why you're recording another sale here");
                return;
            }
            String callbackDate = "callback".equals(status) ? callbackDateHolder[0].toString() : null;
            submitEditedFailedFinish(f, status, note, callbackDate, duplicateReason != null && !duplicateReason.isEmpty() ? duplicateReason : null);
            clearEditingFailedState();
            dialog.dismiss();
        });
    }

    private void clearEditingFailedState() {
        editingDraftDuplicateReason = null;
        editingFailedFinish = null;
        editingFailedFinishAddress = null;
        editingDraftStatus = null;
        editingDraftNote = null;
        editingDraftCallbackDate = null;
        pendingRetryPhotoFile = null;
        pendingRetryPhotoUri = null;
    }

    private void beginRetryPhotoCapture() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_RETRY_CAMERA_PERMISSION);
            return;
        }
        startRetryPhotoCapture();
    }

    private void startRetryPhotoCapture() {
        try {
            File photoFile = File.createTempFile("door_retry_", ".jpg", getCacheDir());
            pendingRetryPhotoFile = photoFile;
            pendingRetryPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingRetryPhotoUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_TAKE_RETRY_PHOTO);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    /** Re-queues a failed submission with its (possibly edited) answers and (possibly retaken) photo,
     * then drains immediately -- same local-queue path as finishing a door the first time, not the
     * server's "correct an already-saved door" endpoint, since the server never actually saved this
     * one. A too-large retaken photo falls back to keeping the original rather than losing the edit. */
    private void submitEditedFailedFinish(DispositionQueue.Finish original, String status, String note, String callbackDate, String duplicateOverrideReason) {
        String photoPath = original.photoPath;
        if (pendingRetryPhotoFile != null && pendingRetryPhotoFile.length() > 0) {
            try {
                byte[] bytes = readAllBytes(new FileInputStream(pendingRetryPhotoFile));
                if (bytes.length > 8 * 1024 * 1024) {
                    new AlertDialog.Builder(this).setTitle("Edit & Retry").setMessage("That photo is too large. The original photo was kept.").setPositiveButton("OK", null).show();
                } else {
                    File durableDir = new File(getFilesDir(), "door_finishes");
                    if (!durableDir.exists()) durableDir.mkdirs();
                    File durablePhoto = new File(durableDir, "door_" + original.dispositionId + "_" + System.currentTimeMillis() + ".jpg");
                    try (OutputStream out = new FileOutputStream(durablePhoto)) {
                        out.write(bytes);
                    }
                    photoPath = durablePhoto.getAbsolutePath();
                }
            } catch (IOException e) {
                new AlertDialog.Builder(this).setTitle("Edit & Retry").setMessage("Unable to save the new photo. The original photo was kept.").setPositiveButton("OK", null).show();
            } finally {
                pendingRetryPhotoFile.delete();
            }
        }
        dispositionQueue.add(new DispositionQueue.Finish(original.employeeId, original.dispositionId, status, note, original.latitude, original.longitude, callbackDate, photoPath, original.photoDistanceReason, duplicateOverrideReason));
        if (doorsMessageText != null) doorsMessageText.setText("Retrying…");
        final String currentToken = token;
        final long currentEmployeeId = employeeId;
        new Thread(() -> {
            dispositionQueue.drain(currentToken, currentEmployeeId);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) loadDoors();
            });
        }).start();
    }

    /** Same outcome picker as finishing a door, pre-filled with what's on record — no new photo
     * required, since the visit already has one; only the outcome/note (and any callback) change. */
    private void showEditOutcomeDialog(int dispositionId, String currentStatus, String currentNote, String address) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);

        TextView label = new TextView(this);
        label.setText("OUTCOME");
        label.setTextSize(12);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextColor(Theme.TEXT_SECONDARY);
        label.setLetterSpacing(0.06f);
        container.addView(label);

        String[] statusCodes = {"sold", "not_home", "already_serviced", "not_interested", "not_owner", "callback", "do_not_call", "other"};
        String[] statusLabels = {"Sold", "Not Home", "Already Had Service", "Not Interested", "Not the Owner", "Come Back Another Time", "Do Not Call", "Other"};
        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.content.res.ColorStateList radioTint = android.content.res.ColorStateList.valueOf(Theme.PRIMARY);
        for (int i = 0; i < statusCodes.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(statusLabels[i]);
            rb.setTextColor(Theme.TEXT_PRIMARY);
            rb.setButtonTintList(radioTint);
            rb.setTag(statusCodes[i]);
            LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rbParams.topMargin = (int) (4 * density);
            radioGroup.addView(rb, rbParams);
            if (statusCodes[i].equals(currentStatus)) rb.setChecked(true);
        }
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        radioParams.topMargin = (int) (8 * density);
        container.addView(radioGroup, radioParams);

        LinearLayout callbackDateRow = new LinearLayout(this);
        callbackDateRow.setOrientation(LinearLayout.VERTICAL);
        callbackDateRow.setVisibility("callback".equals(currentStatus) ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams callbackDateRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackDateRowParams.topMargin = (int) (12 * density);
        container.addView(callbackDateRow, callbackDateRowParams);

        TextView callbackLabel = new TextView(this);
        callbackLabel.setText("CALL BACK ON");
        callbackLabel.setTextSize(12);
        callbackLabel.setTypeface(callbackLabel.getTypeface(), android.graphics.Typeface.BOLD);
        callbackLabel.setTextColor(Theme.TEXT_SECONDARY);
        callbackLabel.setLetterSpacing(0.06f);
        callbackDateRow.addView(callbackLabel);

        final java.time.LocalDate[] callbackDateHolder = {java.time.LocalDate.now().plusDays(1)};
        Button callbackDateButton = styledButton(callbackDateHolder[0].toString(), Theme.PRIMARY);
        LinearLayout.LayoutParams callbackButtonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackButtonParams.topMargin = (int) (4 * density);
        callbackDateRow.addView(callbackDateButton, callbackButtonParams);
        callbackDateButton.setOnClickListener(v -> new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            callbackDateHolder[0] = java.time.LocalDate.of(year, month + 1, day);
            callbackDateButton.setText(callbackDateHolder[0].toString());
        }, callbackDateHolder[0].getYear(), callbackDateHolder[0].getMonthValue() - 1, callbackDateHolder[0].getDayOfMonth()).show());

        EditText noteField = new EditText(this);
        noteField.setHint("callback".equals(currentStatus) || "other".equals(currentStatus) ? "Reason" : "Note (optional)");
        noteField.setText(currentNote);
        Theme.styleInput(noteField);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = (int) (16 * density);
        noteParams.bottomMargin = (int) (4 * density);
        container.addView(noteField, noteParams);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            android.widget.RadioButton checked = group.findViewById(checkedId);
            String tag = checked != null ? (String) checked.getTag() : null;
            callbackDateRow.setVisibility("callback".equals(tag) ? View.VISIBLE : View.GONE);
            noteField.setHint("other".equals(tag) ? "Reason (required)" : "Note (optional)");
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "Correct " + address, Theme.PRIMARY))
                .setView(container)
                .setPositiveButton("Save correction", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.PRIMARY);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            String status = "sold";
            if (checkedId != -1) {
                android.widget.RadioButton checked = radioGroup.findViewById(checkedId);
                if (checked != null && checked.getTag() != null) status = (String) checked.getTag();
            }
            String note = noteField.getText().toString().trim();
            if ("other".equals(status) && note.isEmpty()) {
                noteField.setError("Enter a reason");
                return;
            }
            String callbackDate = "callback".equals(status) ? callbackDateHolder[0].toString() : null;
            submitEditDoor(dispositionId, status, note, callbackDate);
            dialog.dismiss();
        });
    }

    private void submitEditDoor(int dispositionId, String status, String note, String callbackDate) {
        submitEditDoor(dispositionId, status, note, callbackDate, null);
    }

    /** Not routed through the shared request() helper -- that always shows a plain dead-end "OK"
     * dialog on failure, with no way to act on it. A correction that lands on a closing outcome
     * (sold/do-not-call/already-serviced) at an address with one already on record gets rejected the
     * same way a fresh finish would (see D2dDisposition::enforceDuplicateSalePolicy()); this reacts to
     * that specific rejection by prompting for the same justification and retrying, instead of leaving
     * the rep stuck. */
    private void submitEditDoor(int dispositionId, String status, String note, String callbackDate, String duplicateOverrideReason) {
        final String currentToken = token;
        new Thread(() -> {
            JSONObject result = null;
            EmployeeApi.ApiError error = null;
            try {
                JSONObject body = new JSONObject().put("token", currentToken).put("disposition_id", dispositionId).put("status", status).put("note", note);
                if (callbackDate != null) body.put("callback_date", callbackDate);
                if (duplicateOverrideReason != null) body.put("duplicate_override_reason", duplicateOverrideReason);
                result = EmployeeApi.post("telemapper/disposition/edit", body);
            } catch (EmployeeApi.ApiError e) {
                error = e;
            } catch (Exception ignored) {
            }
            JSONObject finalResult = result;
            EmployeeApi.ApiError finalError = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalResult != null) {
                    loadDoors();
                    loadMyLeads();
                } else if (finalError != null && finalError.getMessage() != null && finalError.getMessage().contains("was already marked")) {
                    promptDuplicateReasonThenEditDoor(dispositionId, status, note, callbackDate, finalError.getMessage());
                } else if (finalError != null) {
                    new AlertDialog.Builder(this).setTitle("Unable to save").setMessage(finalError.getMessage()).setPositiveButton("OK", null).show();
                }
            });
        }).start();
    }

    private void promptDuplicateReasonThenEditDoor(int dispositionId, String status, String note, String callbackDate, String serverMessage) {
        float density = getResources().getDisplayMetrics().density;
        EditText reasonField = new EditText(this);
        reasonField.setHint("e.g. different unit, prior outcome was wrong");
        Theme.styleInput(reasonField);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(reasonField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "Explain this sale", Theme.WARNING))
                .setMessage(serverMessage)
                .setView(container)
                .setPositiveButton("Save & Retry", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.WARNING);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonField.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonField.setError("Required");
                return;
            }
            submitEditDoor(dispositionId, status, note, callbackDate, reason);
            dialog.dismiss();
        });
    }

    /** Keep map drags and pinch gestures inside the WebView, not its parent ScrollView. */
    private void protectMapGestures(android.webkit.WebView map) {
        map.setOnTouchListener((view, event) -> {
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

    private String doorsStatusLabel(String status) {
        switch (status) {
            case "sold": return "Sold";
            case "already_serviced": return "Already Had Service";
            case "not_interested": return "Not Interested";
            case "not_owner": return "Not the Owner";
            case "callback": return "Come Back Another Time";
            case "do_not_call": return "Do Not Call";
            case "not_home": return "Not Home";
            default: return "Other";
        }
    }

    private int doorsStatusColorInt(String status) {
        switch (status) {
            case "sold": return Theme.SUCCESS;
            case "already_serviced": return 0xff3b82f6;
            case "not_interested": return 0xffef4444;
            case "not_owner": return 0xfff97316;
            case "callback": return 0xffa855f7;
            case "do_not_call": return 0xff111827;
            default: return Theme.NEUTRAL;
        }
    }

    private void loadMyLeads() {
        if (myLeadsContainer == null) return;
        try {
            request("telemapper/followup/my-leads", new JSONObject().put("token", token), null, r -> renderMyLeads(r.getJSONArray("leads")));
        } catch (Exception ignored) {
        }
    }

    private void renderMyLeads(org.json.JSONArray leads) throws Exception {
        myLeadsContainer.removeAllViews();
        if (leads.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No pending callbacks.");
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            myLeadsContainer.addView(empty);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d");
        for (int i = 0; i < leads.length(); i++) {
            JSONObject lead = leads.getJSONObject(i);
            int followupId = lead.getInt("followup_id");
            boolean overdue = lead.optBoolean("is_overdue", false);
            java.time.LocalDate callbackDate = java.time.LocalDate.parse(lead.getString("callback_date"));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            card.setBackground(Theme.cardBackground(this));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) (8 * density);
            myLeadsContainer.addView(card, cardParams);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(topRow);

            TextView addressText = new TextView(this);
            addressText.setText(lead.getString("address"));
            addressText.setTextSize(15);
            addressText.setTypeface(addressText.getTypeface(), android.graphics.Typeface.BOLD);
            addressText.setTextColor(Theme.TEXT_PRIMARY);
            topRow.addView(addressText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView dateText = new TextView(this);
            dateText.setText(dayFormat.format(callbackDate));
            dateText.setTextSize(13);
            dateText.setTypeface(dateText.getTypeface(), android.graphics.Typeface.BOLD);
            dateText.setTextColor(overdue ? Theme.ERROR : Theme.PRIMARY);
            topRow.addView(dateText);

            if (overdue) {
                TextView overdueBadge = Theme.statusBadge(this, "Overdue", Theme.ERROR);
                LinearLayout.LayoutParams overdueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                overdueParams.topMargin = (int) (4 * density);
                card.addView(overdueBadge, overdueParams);
            }

            String note = lead.optString("note", "");
            if (!note.isEmpty()) {
                TextView noteText = new TextView(this);
                noteText.setText(note);
                noteText.setTextSize(13);
                noteText.setTextColor(Theme.TEXT_SECONDARY);
                LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                noteParams.topMargin = (int) (4 * density);
                card.addView(noteText, noteParams);
            }

            Button doneButton = styledButton("Mark done", Theme.SUCCESS);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            doneParams.topMargin = (int) (8 * density);
            card.addView(doneButton, doneParams);
            doneButton.setOnClickListener(v -> completeLead(followupId));
        }
    }

    private void loadMyEvents() {
        if (myEventsContainer == null) return;
        try {
            request("telemapper/retail-event/my-events", new JSONObject().put("token", token), null, r -> renderMyEvents(r.getJSONArray("events")));
        } catch (Exception ignored) {
        }
    }

    private void renderMyEvents(org.json.JSONArray events) throws Exception {
        myEventsContainer.removeAllViews();
        if (events.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No events assigned right now.");
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            myEventsContainer.addView(empty);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d");
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            java.time.LocalDate startDate = java.time.LocalDate.parse(event.getString("start_date"));
            java.time.LocalDate endDate = java.time.LocalDate.parse(event.getString("end_date"));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            card.setBackground(Theme.cardBackground(this));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) (8 * density);
            myEventsContainer.addView(card, cardParams);

            TextView nameText = new TextView(this);
            nameText.setText(event.getString("event_name"));
            nameText.setTextSize(15);
            nameText.setTypeface(nameText.getTypeface(), android.graphics.Typeface.BOLD);
            nameText.setTextColor(Theme.TEXT_PRIMARY);
            card.addView(nameText);

            TextView dateText = new TextView(this);
            dateText.setText(dayFormat.format(startDate) + " – " + dayFormat.format(endDate));
            dateText.setTextSize(13);
            dateText.setTextColor(Theme.PRIMARY);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dateParams.topMargin = (int) (2 * density);
            card.addView(dateText, dateParams);

            String locationName = event.optString("location_name", "");
            String locationAddress = event.optString("location_address", "");
            if (!locationName.isEmpty() || !locationAddress.isEmpty()) {
                TextView locationText = new TextView(this);
                locationText.setText(locationName.isEmpty() ? locationAddress : locationName + (locationAddress.isEmpty() ? "" : " — " + locationAddress));
                locationText.setTextSize(13);
                locationText.setTextColor(Theme.TEXT_SECONDARY);
                LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                locationParams.topMargin = (int) (6 * density);
                card.addView(locationText, locationParams);
            }

            String promo = event.optString("promo_description", "");
            if (!promo.isEmpty()) {
                TextView promoText = new TextView(this);
                promoText.setText(promo);
                promoText.setTextSize(13);
                promoText.setTextColor(Theme.TEXT_PRIMARY);
                LinearLayout.LayoutParams promoParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                promoParams.topMargin = (int) (8 * density);
                card.addView(promoText, promoParams);
            }
        }
    }

    private void loadMyPrograms() {
        if (myProgramsContainer == null) return;
        try {
            request("telemapper/program/my-program", new JSONObject().put("token", token), null, r -> renderMyProgram(r.isNull("program") ? null : r.getJSONObject("program")));
        } catch (Exception ignored) {
        }
    }

    /** D2D's "what am I selling right now" card — set by a manager under Employee Management ▸
     * Program Assignment on the web, and only shown here while the program itself is still active
     * and unexpired (an expired program isn't "current" just because it's the latest assignment). */
    private void renderMyProgram(JSONObject program) throws Exception {
        myProgramsContainer.removeAllViews();
        if (program == null) {
            TextView empty = new TextView(this);
            empty.setText("No program assigned right now.");
            empty.setTextSize(13);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            myProgramsContainer.addView(empty);
            return;
        }
        float density = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
        card.setBackground(Theme.cardBackground(this));
        myProgramsContainer.addView(card);

        TextView nameText = new TextView(this);
        nameText.setText(program.getString("name"));
        nameText.setTextSize(15);
        nameText.setTypeface(nameText.getTypeface(), android.graphics.Typeface.BOLD);
        nameText.setTextColor(Theme.TEXT_PRIMARY);
        card.addView(nameText);

        String valueLine = program.optString("program_value", "");
        if (!program.isNull("duration_months") && program.optInt("duration_months", 0) > 0) {
            valueLine += " · " + program.optInt("duration_months") + " months";
        }
        TextView valueText = new TextView(this);
        valueText.setText(valueLine);
        valueText.setTextSize(13);
        valueText.setTextColor(Theme.PRIMARY);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = (int) (2 * density);
        card.addView(valueText, valueParams);

        if (!program.isNull("discount_type") && !program.isNull("discount_value")) {
            double discountValue = program.optDouble("discount_value", 0);
            String discountText = "dollar".equals(program.optString("discount_type", ""))
                    ? "$" + (discountValue == Math.floor(discountValue) ? String.valueOf((int) discountValue) : String.valueOf(discountValue)) + " off"
                    : (discountValue == Math.floor(discountValue) ? String.valueOf((int) discountValue) : String.valueOf(discountValue)) + "% off";
            TextView discountLabel = new TextView(this);
            discountLabel.setText(discountText);
            discountLabel.setTextSize(13);
            discountLabel.setTextColor(Theme.SUCCESS);
            LinearLayout.LayoutParams discountParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            discountParams.topMargin = (int) (4 * density);
            card.addView(discountLabel, discountParams);
        }

        String terms = program.optString("offer_description", "");
        if (!terms.isEmpty()) {
            TextView termsText = new TextView(this);
            termsText.setText(terms);
            termsText.setTextSize(13);
            termsText.setTextColor(Theme.TEXT_PRIMARY);
            LinearLayout.LayoutParams termsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            termsParams.topMargin = (int) (8 * density);
            card.addView(termsText, termsParams);
        }

        TextView expiresText = new TextView(this);
        if (program.isNull("expires_date")) {
            expiresText.setText("No expiration");
        } else {
            java.time.LocalDate expires = java.time.LocalDate.parse(program.getString("expires_date"));
            expiresText.setText("Expires " + java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(expires));
        }
        expiresText.setTextSize(12);
        expiresText.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams expiresParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        expiresParams.topMargin = (int) (8 * density);
        card.addView(expiresText, expiresParams);
    }

    private void loadArrivalAssignments() {
        if (arrivalAssignmentsContainer == null) return;
        arrivalMessageText.setText("Loading…");
        try {
            request("telemapper/arrival/assignments", new JSONObject().put("token", token), null, r -> renderArrivalAssignments(r.getJSONArray("assignments")));
        } catch (Exception ignored) {
        }
    }

    /** Downloads a check-in photo (selfie or one of the store photos) through the token-authenticated
     * apiPhoto endpoint and drops it into the given thumbnail once it arrives — same pattern as
     * loadMyAvatar(), independent of the shared request()/busy gate so a slow photo never blocks
     * the rest of the screen. */
    private void loadArrivalThumbnail(long arrivalId, String which, int position, ImageView target) {
        new Thread(() -> {
            android.graphics.Bitmap bitmap = null;
            HttpsURLConnection conn = null;
            try {
                String url = API + "telemapper/arrival/photo?token=" + java.net.URLEncoder.encode(token, "UTF-8")
                        + "&arrival_id=" + arrivalId + "&which=" + which + "&position=" + position;
                conn = (HttpsURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        bitmap = android.graphics.BitmapFactory.decodeStream(in);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            android.graphics.Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (result != null) target.setImageBitmap(result);
            });
        }).start();
    }

    private void renderArrivalAssignments(org.json.JSONArray assignments) throws Exception {
        arrivalAssignmentsContainer.removeAllViews();
        if (assignments.length() == 0) {
            arrivalMessageText.setText("No worksite assigned for today.");
            return;
        }
        arrivalMessageText.setText(assignments.length() + " assignment" + (assignments.length() == 1 ? "" : "s") + " today");
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject assignment = assignments.getJSONObject(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            card.setBackground(Theme.cardBackground(this));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) (8 * density);
            arrivalAssignmentsContainer.addView(card, cardParams);

            TextView siteText = new TextView(this);
            siteText.setText(assignment.getString("site_name"));
            siteText.setTextSize(15);
            siteText.setTypeface(siteText.getTypeface(), android.graphics.Typeface.BOLD);
            siteText.setTextColor(Theme.TEXT_PRIMARY);
            card.addView(siteText);

            String address = assignment.optString("site_address", "");
            if (!address.isEmpty()) {
                TextView addressText = new TextView(this);
                addressText.setText(address);
                addressText.setTextSize(13);
                addressText.setTextColor(Theme.TEXT_SECONDARY);
                LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                addressParams.topMargin = (int) (2 * density);
                card.addView(addressText, addressParams);
            }

            if (assignment.optBoolean("checked_in", false)) {
                boolean checkedOut = assignment.optBoolean("checked_out", false);
                String badgeText = "✓ Checked in";
                if (checkedOut) {
                    String checkedOutUtc = assignment.isNull("checked_out_utc") ? null : assignment.optString("checked_out_utc", null);
                    String time = null;
                    if (checkedOutUtc != null && !checkedOutUtc.isEmpty()) {
                        try {
                            java.time.Instant instant = java.time.Instant.parse(checkedOutUtc.replace(' ', 'T') + "Z");
                            time = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(java.time.ZoneId.systemDefault()).format(instant);
                        } catch (Exception ignored) {
                        }
                    }
                    // A rep can leave and come back to the same site the same day -- this visit is
                    // done, but that must not block a fresh check-in if he's back.
                    badgeText = "Visited earlier today · checked out" + (time != null ? " at " + time : "");
                }
                TextView checkedInBadge = Theme.statusBadge(this, badgeText, checkedOut ? Theme.TEXT_SECONDARY : Theme.SUCCESS);
                LinearLayout.LayoutParams checkedInParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                checkedInParams.topMargin = (int) (10 * density);
                card.addView(checkedInBadge, checkedInParams);

                if (!assignment.isNull("arrival_id")) {
                    long arrivalId = assignment.optLong("arrival_id");
                    android.widget.HorizontalScrollView thumbScroll = new android.widget.HorizontalScrollView(this);
                    thumbScroll.setHorizontalScrollBarEnabled(false);
                    LinearLayout.LayoutParams thumbScrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    thumbScrollParams.topMargin = (int) (8 * density);
                    card.addView(thumbScroll, thumbScrollParams);
                    LinearLayout thumbRow = new LinearLayout(this);
                    thumbRow.setOrientation(LinearLayout.HORIZONTAL);
                    thumbScroll.addView(thumbRow);

                    int thumbSize = (int) (112 * density);
                    ImageView selfieThumb = new ImageView(this);
                    selfieThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams selfieThumbParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
                    selfieThumbParams.rightMargin = (int) (6 * density);
                    thumbRow.addView(selfieThumb, selfieThumbParams);
                    loadArrivalThumbnail(arrivalId, "selfie", 1, selfieThumb);

                    int storeCount = assignment.optInt("store_photo_count", 0);
                    for (int p = 1; p <= storeCount; p++) {
                        ImageView storeThumb = new ImageView(this);
                        storeThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        LinearLayout.LayoutParams storeThumbParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
                        storeThumbParams.rightMargin = (int) (6 * density);
                        thumbRow.addView(storeThumb, storeThumbParams);
                        loadArrivalThumbnail(arrivalId, "store", p, storeThumb);
                    }
                }

                if (!checkedOut) {
                    // Same light "pill" look as the screen-title bar (Check In) instead of a solid
                    // filled button -- a quieter, secondary-feeling action to close out the visit.
                    Button checkOutButton = new Button(this);
                    checkOutButton.setText("I'm done");
                    checkOutButton.setAllCaps(false);
                    checkOutButton.setTextSize(15);
                    checkOutButton.setTypeface(checkOutButton.getTypeface(), android.graphics.Typeface.BOLD);
                    checkOutButton.setTextColor(Theme.PRIMARY);
                    int checkOutPadH = (int) (14 * density), checkOutPadV = (int) (10 * density);
                    checkOutButton.setPadding(checkOutPadH, checkOutPadV, checkOutPadH, checkOutPadV);
                    android.graphics.drawable.GradientDrawable checkOutBg = new android.graphics.drawable.GradientDrawable();
                    checkOutBg.setColor(android.graphics.Color.argb(28, android.graphics.Color.red(Theme.PRIMARY), android.graphics.Color.green(Theme.PRIMARY), android.graphics.Color.blue(Theme.PRIMARY)));
                    checkOutBg.setCornerRadius(8 * density);
                    checkOutButton.setBackground(checkOutBg);
                    LinearLayout.LayoutParams checkOutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    checkOutParams.topMargin = (int) (10 * density);
                    card.addView(checkOutButton, checkOutParams);
                    checkOutButton.setOnClickListener(v -> confirmCheckOut(assignment));
                } else {
                    // Already checked out of this visit, but he can still be back at the same site
                    // later the same day -- offer a fresh check-in instead of leaving the card stuck
                    // in a "done for today" state.
                    Button arriveAgainButton = styledButton("I'm Arrived — Check In", Theme.PRIMARY);
                    LinearLayout.LayoutParams arriveAgainParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    arriveAgainParams.topMargin = (int) (10 * density);
                    card.addView(arriveAgainButton, arriveAgainParams);
                    arriveAgainButton.setOnClickListener(v -> beginArrival(assignment));
                }
            } else {
                Button arriveButton = styledButton("I'm Arrived — Check In", Theme.PRIMARY);
                LinearLayout.LayoutParams arriveParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                arriveParams.topMargin = (int) (10 * density);
                card.addView(arriveButton, arriveParams);
                arriveButton.setOnClickListener(v -> beginArrival(assignment));
            }
        }
    }

    /** Closes out a worksite visit -- a rep with more than one site in a day needs a real way to say
     * "finished here, heading to the next one" instead of the day just showing whichever check-in
     * happened to be last. No photo needed: check-in already proved he was there. */
    private void confirmCheckOut(org.json.JSONObject assignment) {
        String siteName = assignment.optString("site_name", "this site");
        new AlertDialog.Builder(this)
                .setTitle("Check out?")
                .setMessage("Mark yourself done at " + siteName + " and on your way?")
                .setPositiveButton("Yes, check out", (d, w) -> beginCheckOut(assignment))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Check-out needs the same GPS proof check-in has -- otherwise "I'm done" could be tapped from
     * anywhere, including a site he never actually left. Captures a fresh fix first (same provider
     * logic as arrival), then does a local proximity check against the assignment's own worksite
     * location before submitting, mirroring beginArrival()/checkArrivalProximityAndContinue(). */
    private void beginCheckOut(org.json.JSONObject assignment) {
        activeCheckOutAssignment = assignment;
        if (arrivalMessageText != null) arrivalMessageText.setText("Checking your location…");
        fetchBestLocation(this::showCheckOutLocationTimeoutDialog, this::checkCheckOutProximityAndSubmit);
    }

    private void showCheckOutLocationTimeoutDialog() {
        if (arrivalMessageText != null) arrivalMessageText.setText("");
        new AlertDialog.Builder(this)
                .setTitle("Unable to get your location")
                .setMessage("Move outdoors or near a window and try again.")
                .setPositiveButton("Try again", (d, w) -> beginCheckOut(activeCheckOutAssignment))
                .setNegativeButton("Cancel", (d, w) -> activeCheckOutAssignment = null)
                .show();
    }

    private void checkCheckOutProximityAndSubmit(Double lat, Double lon) {
        org.json.JSONObject assignment = activeCheckOutAssignment;
        if (assignment == null) return;
        activeCheckOutAssignment = null;
        try {
            if (lat != null && lon != null) {
                double siteLat = assignment.getDouble("latitude");
                double siteLon = assignment.getDouble("longitude");
                int proximity = assignment.optInt("proximity_meters", 150);
                double distance = metersBetween(siteLat, siteLon, lat, lon);
                if (distance > proximity) {
                    if (arrivalMessageText != null) arrivalMessageText.setText("");
                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setCustomTitle(Theme.dialogTitle(this, "Too far from the worksite", Theme.WARNING))
                            .setMessage("You're about " + Math.round(distance) + "m from " + assignment.getString("site_name") + ". Move closer and try again.")
                            .setPositiveButton("Try again", (d, w) -> beginCheckOut(assignment))
                            .setNegativeButton("Cancel", (d, w) -> {
                                if (arrivalMessageText != null) arrivalMessageText.setText("");
                            })
                            .show();
                    Theme.styleDialog(dialog, Theme.WARNING);
                    return;
                }
            }
            if (arrivalMessageText != null) arrivalMessageText.setText("");
            submitCheckOut(assignment, lat, lon);
        } catch (Exception e) {
            // A bad/missing field in the assignment JSON shouldn't block checkout entirely --
            // the server re-validates proximity anyway, so just submit with whatever location we have.
            if (arrivalMessageText != null) arrivalMessageText.setText("");
            submitCheckOut(assignment, lat, lon);
        }
    }

    private void submitCheckOut(org.json.JSONObject assignment, Double lat, Double lon) {
        try {
            JSONObject body = new JSONObject().put("token", token)
                    .put("assignment_id", assignment.getInt("assignment_id"))
                    .put("source", assignment.getString("source"));
            if (lat != null && lon != null) body.put("latitude", lat).put("longitude", lon);
            request("telemapper/arrival/check-out", body, null, r -> loadArrivalAssignments());
        } catch (Exception ignored) {
        }
    }

    private void beginArrival(org.json.JSONObject assignment) {
        // Always called before any photo has been taken (GPS is checked first, then photos), so
        // there's nothing accepted yet to preserve — safe to reset unconditionally.
        acceptedStorePhotos.clear();
        activeArrivalAssignment = assignment;
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_ARRIVAL_CAMERA_PERMISSION);
            return;
        }
        // Capture GPS first, before either photo — this confirms he's actually within range
        // right when he taps "I'm Arrived" (not a couple camera round-trips later), and avoids
        // making him take two photos only to be told afterward that he's too far away.
        locateArrivalThenCheckProximity();
    }

    private void beginArrivalSelfie() {
        try {
            File photoFile = File.createTempFile("selfie_", ".jpg", getCacheDir());
            pendingSelfieFile = photoFile;
            pendingSelfieUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingSelfieUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            new AlertDialog.Builder(this).setTitle("Selfie").setMessage("Take a quick selfie to confirm it's you — flip to the front camera if needed.").setPositiveButton("Open camera", (d, w) -> startActivityForResult(intent, REQUEST_TAKE_SELFIE_PHOTO)).setNegativeButton("Cancel", null).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    private void beginArrivalStorePhoto() {
        try {
            File photoFile = File.createTempFile("store_", ".jpg", getCacheDir());
            pendingStorePhotoFile = photoFile;
            pendingStorePhotoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingStorePhotoUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            new AlertDialog.Builder(this).setTitle("Store photo").setMessage("Now take a photo showing you're at the store front.").setPositiveButton("Open camera", (d, w) -> startActivityForResult(intent, REQUEST_TAKE_STORE_PHOTO)).setNegativeButton("Cancel", null).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    private static final long ARRIVAL_LOCATION_TIMEOUT_MS = 20000;

    private void locateArrivalThenCheckProximity() {
        if (arrivalMessageText != null) arrivalMessageText.setText("Checking your location…");
        fetchBestLocation(this::showArrivalLocationTimeoutDialog, this::checkArrivalProximityAndContinue);
    }

    /** Shared GPS fix logic for both S2S check-in and check-out -- a proximity check must reflect
     * where he is right now, not a cached fix from however long ago, so this always forces a fresh
     * reading rather than trusting getLastKnownLocation(). The passive provider just replays whatever
     * fix some OTHER app last requested, anywhere, any time -- it must never be used here. And even
     * among the real providers, the FIRST one to answer isn't necessarily the most accurate:
     * network/fused fixes can return in well under a second with 200-500m+ of error, which was
     * rejecting a genuinely on-site check-in/out as "too far". So keep listening (up to the timeout)
     * for the best accuracy seen, and only settle early once a fix is actually good. */
    private void fetchBestLocation(Runnable onFailure, java.util.function.BiConsumer<Double, Double> onLocation) {
        if (!hasLocationPermission()) {
            onFailure.run();
            return;
        }
        android.location.LocationManager manager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            java.util.List<String> providers = new java.util.ArrayList<>(manager.getProviders(true));
            providers.remove(android.location.LocationManager.PASSIVE_PROVIDER);
            if (providers.isEmpty()) {
                onFailure.run();
                return;
            }
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] resolved = {false};
            final android.location.Location[] best = {null};
            final float GOOD_ACCURACY_METERS = 30f;
            android.location.LocationListener listener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (resolved[0]) return;
                    if (best[0] == null || location.getAccuracy() < best[0].getAccuracy()) best[0] = location;
                    if (location.getAccuracy() <= GOOD_ACCURACY_METERS) {
                        resolved[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        manager.removeUpdates(this);
                        onLocation.accept(location.getLatitude(), location.getLongitude());
                    }
                }

                @Override
                public void onProviderDisabled(String provider) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }
            };
            // Ask every remaining provider (GPS is often slow or unavailable indoors; network location can fill in).
            for (String provider : providers) manager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (resolved[0]) return;
                resolved[0] = true;
                manager.removeUpdates(listener);
                if (best[0] != null) {
                    onLocation.accept(best[0].getLatitude(), best[0].getLongitude());
                } else {
                    onFailure.run();
                }
            }, ARRIVAL_LOCATION_TIMEOUT_MS);
        } catch (SecurityException e) {
            onFailure.run();
        }
    }

    private void showArrivalLocationTimeoutDialog() {
        if (arrivalMessageText != null) arrivalMessageText.setText("");
        new AlertDialog.Builder(this)
                .setTitle("Unable to get your location")
                .setMessage("Move outdoors or near a window and try again.")
                .setPositiveButton("Try again", (d, w) -> locateArrivalThenCheckProximity())
                .setNegativeButton("Cancel", (d, w) -> {
                    activeArrivalAssignment = null;
                    pendingSelfieUri = null;
                    pendingStorePhotoUri = null;
                    pendingSelfieFile = null;
                    pendingStorePhotoFile = null;
                    acceptedStorePhotos.clear();
                })
                .show();
    }

    private void checkArrivalProximityAndContinue(Double lat, Double lon) {
        if (activeArrivalAssignment == null) {
            if (arrivalMessageText != null) arrivalMessageText.setText("");
            return;
        }
        try {
            if (lat != null && lon != null) {
                double siteLat = activeArrivalAssignment.getDouble("latitude");
                double siteLon = activeArrivalAssignment.getDouble("longitude");
                int proximity = activeArrivalAssignment.optInt("proximity_meters", 150);
                double distance = metersBetween(siteLat, siteLon, lat, lon);
                if (distance > proximity) {
                    if (arrivalMessageText != null) arrivalMessageText.setText("");
                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setCustomTitle(Theme.dialogTitle(this, "Too far from the worksite", Theme.WARNING))
                            .setMessage("You're about " + Math.round(distance) + "m from " + activeArrivalAssignment.getString("site_name") + ". Move closer and try again.")
                            .setPositiveButton("Try again", (d, which) -> beginArrival(activeArrivalAssignment))
                            .setNegativeButton("Cancel", (d, which) -> {
                                activeArrivalAssignment = null;
                                pendingSelfieUri = null;
                                pendingStorePhotoUri = null;
                                pendingSelfieFile = null;
                                pendingStorePhotoFile = null;
                                acceptedStorePhotos.clear();
                                if (arrivalMessageText != null) arrivalMessageText.setText("");
                            })
                            .show();
                    Theme.styleDialog(dialog, Theme.WARNING);
                    return;
                }
            }
            activeArrivalLat = lat;
            activeArrivalLon = lon;
            if (arrivalMessageText != null) arrivalMessageText.setText("✓ Location confirmed.");
            beginArrivalSelfie();
        } catch (Exception e) {
            // Previously silently swallowed here, which made every failure in this block
            // (bad JSON field, a dialog that couldn't be shown, anything) look identical to
            // the app doing nothing at all. Surface it instead so a real cause is visible.
            if (arrivalMessageText != null) arrivalMessageText.setText("");
            final Double retryLat = lat, retryLon = lon;
            new AlertDialog.Builder(this)
                    .setTitle("Check-in error")
                    .setMessage("Something went wrong finishing your check-in (" + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : "") + "). Tap Retry to try again.")
                    .setPositiveButton("Retry", (d, w) -> checkArrivalProximityAndContinue(retryLat, retryLon))
                    .setNegativeButton("Cancel", (d, w) -> {
                        activeArrivalAssignment = null;
                        pendingSelfieUri = null;
                        pendingStorePhotoUri = null;
                        pendingSelfieFile = null;
                        pendingStorePhotoFile = null;
                        acceptedStorePhotos.clear();
                    })
                    .show();
        }
    }

    private void submitArrival() {
        if (activeArrivalAssignment == null || pendingSelfieUri == null || acceptedStorePhotos.isEmpty()) return;
        if (busy) return;
        busy = true;
        final org.json.JSONObject assignment = activeArrivalAssignment;
        final android.net.Uri selfieUri = pendingSelfieUri;
        final java.util.List<File> storeFiles = new java.util.ArrayList<>(acceptedStorePhotos);
        final Double lat = activeArrivalLat;
        final Double lon = activeArrivalLon;
        if (arrivalMessageText != null) arrivalMessageText.setText("Checking in…");
        new Thread(() -> {
            String error = null;
            HttpsURLConnection conn = null;
            try {
                byte[] selfieBytes = readAllBytes(getContentResolver().openInputStream(selfieUri));
                if (selfieBytes.length > 8 * 1024 * 1024) throw new IOException("TOO_LARGE");
                java.util.List<byte[]> storeBytesList = new java.util.ArrayList<>();
                for (File f : storeFiles) {
                    byte[] bytes = readAllBytes(new java.io.FileInputStream(f));
                    if (bytes.length > 8 * 1024 * 1024) throw new IOException("TOO_LARGE");
                    storeBytesList.add(bytes);
                }
                String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
                conn = (HttpsURLConnection) new URL(API + "telemapper/arrival/submit").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = conn.getOutputStream()) {
                    writeMultipartField(out, boundary, "token", token);
                    writeMultipartField(out, boundary, "assignment_id", String.valueOf(assignment.getInt("assignment_id")));
                    writeMultipartField(out, boundary, "source", assignment.optString("source", "worksite"));
                    if (lat != null && lon != null) {
                        writeMultipartField(out, boundary, "latitude", String.valueOf(lat));
                        writeMultipartField(out, boundary, "longitude", String.valueOf(lon));
                    }
                    writeMultipartFile(out, boundary, "selfie", "selfie.jpg", "image/jpeg", selfieBytes);
                    for (int i = 0; i < storeBytesList.size(); i++) {
                        writeMultipartFile(out, boundary, "store_photo[]", "store_" + (i + 1) + ".jpg", "image/jpeg", storeBytesList.get(i));
                    }
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                JSONObject resp = new JSONObject(new String(readAllBytes(stream), StandardCharsets.UTF_8));
                if (code < 200 || code >= 300 || !resp.optBoolean("success")) error = resp.optString("message", "Unable to check in.");
            } catch (Exception e) {
                error = "TOO_LARGE".equals(e.getMessage()) ? "That photo is too large." : "Unable to check in. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            String problem = error;
            runOnUiThread(() -> {
                busy = false;
                activeArrivalAssignment = null;
                pendingSelfieUri = null;
                pendingStorePhotoUri = null;
                pendingSelfieFile = null;
                pendingStorePhotoFile = null;
                acceptedStorePhotos.clear();
                activeArrivalLat = null;
                activeArrivalLon = null;
                if (arrivalMessageText != null) arrivalMessageText.setText("");
                if (problem != null) {
                    new AlertDialog.Builder(this).setTitle("Check in").setMessage(problem).setPositiveButton("OK", null).show();
                } else {
                    new AlertDialog.Builder(this).setTitle("Checked in").setMessage("You're checked in. Have a great shift!").setPositiveButton("OK", null).show();
                    loadArrivalAssignments();
                }
            });
        }).start();
    }

    private void completeLead(int followupId) {
        try {
            request("telemapper/followup/complete", new JSONObject().put("token", token).put("followup_id", followupId), null, r -> loadMyLeads());
        } catch (Exception ignored) {
        }
    }

    private void renderDoorsIdle() {
        activeDispositionId = null;
        activeDoorStartedMs = 0;
        activeDoorLat = null;
        activeDoorLon = null;
        pendingDoorsPhotoUri = null;
        pendingDoorsPhotoFile = null;
        doorsMessageText.setText("Tap your position on the map below, closest to the house, to start a door — or use \"Start a New Door Here\" if none is close enough.");
        doorsAddressText.setVisibility(View.GONE);
        doorsTimerText.setVisibility(View.GONE);
        doorsFinishButton.setVisibility(View.GONE);
        doorsStartHereButton.setVisibility(View.VISIBLE);
    }

    private void renderDoorsActive(String address) {
        doorsMessageText.setText("Timer running — tap Finish when the door closes.");
        doorsAddressText.setText(address);
        doorsAddressText.setVisibility(View.VISIBLE);
        doorsTimerText.setVisibility(View.VISIBLE);
        doorsTimerText.setText(formatDoorsElapsed((System.currentTimeMillis() - activeDoorStartedMs) / 1000));
        doorsFinishButton.setVisibility(View.VISIBLE);
        doorsStartHereButton.setVisibility(View.GONE);
    }

    private String formatDoorsElapsed(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60, s = seconds % 60;
        return m + "m " + String.format(java.util.Locale.US, "%02d", s) + "s";
    }

    /** Exposed to the Trip tab's Leaflet map (trip-map.js) so tapping the trail dot closest to a
     * house starts a door tied to that exact, already-recorded position — no separate device GPS
     * read, no typed house number required. Runs off the WebView's JS thread, so hop back to the
     * UI thread before touching any views or dialogs. */
    private final class TripMapBridge {
        @android.webkit.JavascriptInterface
        public void startDoor(long pointId, double lat, double lon) {
            runOnUiThread(() -> confirmStartDoorAtPoint(pointId, lat, lon));
        }
    }

    /** The "Start a New Door Here" fallback for when no trail dot happens to be near the house —
     * trail dots are just periodic GPS samples, not one-per-house, so relying on tapping one alone
     * leaves some addresses impossible to start. This captures a fresh position on the spot instead,
     * same accuracy-preferring approach as the S2S check-in's own location fetch. */
    private void beginStartNewDoorHere() {
        if (activeDispositionId != null) {
            new AlertDialog.Builder(this).setTitle("Door in progress").setMessage("Finish your current door before starting a new one.").setPositiveButton("OK", null).show();
            return;
        }
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_DOORS_START_HERE_LOCATION_PERMISSION);
            return;
        }
        doorsMessageText.setText("Getting your location…");
        android.location.LocationManager manager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            java.util.List<String> providers = new java.util.ArrayList<>(manager.getProviders(true));
            providers.remove(android.location.LocationManager.PASSIVE_PROVIDER);
            if (providers.isEmpty()) {
                doorsMessageText.setText("No location provider available. Enable Location and try again.");
                return;
            }
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] resolved = {false};
            final android.location.Location[] best = {null};
            final float GOOD_ACCURACY_METERS = 30f;
            android.location.LocationListener listener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (resolved[0]) return;
                    if (best[0] == null || location.getAccuracy() < best[0].getAccuracy()) best[0] = location;
                    if (location.getAccuracy() <= GOOD_ACCURACY_METERS) {
                        resolved[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        manager.removeUpdates(this);
                        createLocationPointThenConfirm(location.getLatitude(), location.getLongitude(), location.getAccuracy());
                    }
                }

                @Override
                public void onProviderDisabled(String provider) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }
            };
            for (String provider : providers) manager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (resolved[0]) return;
                resolved[0] = true;
                manager.removeUpdates(listener);
                if (best[0] != null) {
                    createLocationPointThenConfirm(best[0].getLatitude(), best[0].getLongitude(), best[0].getAccuracy());
                } else {
                    doorsMessageText.setText("Unable to get your location. Move outdoors or near a window and try again.");
                }
            }, 20000);
        } catch (SecurityException e) {
            doorsMessageText.setText("Location permission is required to start.");
        }
    }

    /** Turns a freshly-read GPS fix into a real dot on the map (a real employee_location_points
     * row), then continues into the exact same "I'm at the door" flow a tapped trail dot would —
     * one mechanism for starting a door, whether the dot already existed or was just forced into
     * existence here. */
    private void createLocationPointThenConfirm(double lat, double lon, float accuracy) {
        doorsMessageText.setText("Creating a position here…");
        new Thread(() -> {
            Long newPointId = null;
            String error = null;
            HttpsURLConnection conn = null;
            try {
                JSONObject body = new JSONObject().put("token", token).put("latitude", lat).put("longitude", lon).put("accuracy_m", accuracy);
                conn = (HttpsURLConnection) new URL(API + "telemapper/disposition/create-point").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                JSONObject resp = new JSONObject(new String(readAllBytes(stream), StandardCharsets.UTF_8));
                if (code < 200 || code >= 300 || !resp.optBoolean("success")) {
                    error = resp.optString("message", "Unable to create a position here.");
                } else {
                    newPointId = resp.getLong("point_id");
                }
            } catch (Exception e) {
                error = "Unable to create a position here. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            Long pointId = newPointId;
            String problem = error;
            runOnUiThread(() -> {
                renderDoorsIdle();
                if (problem != null) {
                    new AlertDialog.Builder(this).setTitle("Unable to start").setMessage(problem).setPositiveButton("OK", null).show();
                    return;
                }
                loadDoorsMap();
                confirmStartDoorAtPoint(pointId, lat, lon);
            });
        }).start();
    }

    private void confirmStartDoorAtPoint(Long pointId, double lat, double lon) {
        if (activeDispositionId != null) {
            new AlertDialog.Builder(this).setTitle("Door in progress").setMessage("Finish your current door before starting a new one.").setPositiveButton("OK", null).show();
            return;
        }
        checkDuplicateThenShowStartDialog(pointId, lat, lon);
    }

    /** Checked right before opening "I'm at the door" -- the same house getting knocked (and sold,
     * or re-asked after a do-not-call) over and over on different days, just because nothing on the
     * phone flagged it was already closed out, is a real annoyance for the homeowner. Non-blocking:
     * the rep has to explicitly get past a clear warning rather than being stopped outright, since a
     * wrong GPS match or a genuinely different unit at the same spot is still possible. If the check
     * itself can't be completed (no signal, server error), that's surfaced explicitly -- see
     * showCouldNotVerifyDialog() -- rather than silently treated the same as "confirmed clear",
     * since those mean very different things. The real backstop regardless is server-side: a closing
     * outcome (sold/do-not-call/already-serviced) is enforced again when the door is actually
     * finished (see D2dDisposition::finishForEmployeeToken()), so a duplicate can't slip through just
     * because this particular check never ran. */
    private void checkDuplicateThenShowStartDialog(Long pointId, double lat, double lon) {
        final String currentToken = token;
        new Thread(() -> {
            JSONObject result = null;
            boolean checkFailed = false;
            try {
                result = EmployeeApi.post("telemapper/disposition/check-location", new JSONObject().put("token", currentToken).put("latitude", lat).put("longitude", lon));
            } catch (Exception e) {
                checkFailed = true;
            }
            JSONObject finalResult = result;
            boolean finalFailed = checkFailed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalFailed) showCouldNotVerifyDialog(pointId, lat, lon);
                else if (finalResult.optBoolean("duplicate", false)) showDuplicateDoorWarning(finalResult, pointId, lat, lon);
                else showStartDoorDialog(pointId, lat, lon);
            });
        }).start();
    }

    private void showCouldNotVerifyDialog(Long pointId, double lat, double lon) {
        new AlertDialog.Builder(this)
                .setTitle("Could not verify household status")
                .setMessage("Unable to check whether this house was already sold or marked do-not-call. Check your connection and retry, or continue without checking -- it'll still be verified when this door is finished and uploaded.")
                .setPositiveButton("Retry", (d, w) -> checkDuplicateThenShowStartDialog(pointId, lat, lon))
                .setNeutralButton("Continue Without Checking", (d, w) -> showStartDoorDialog(pointId, lat, lon))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDuplicateDoorWarning(JSONObject lead, Long pointId, double lat, double lon) {
        String address = lead.optString("address", "").trim();
        String statusLabel = doorsStatusLabel(lead.optString("status", ""));
        String when = "";
        try {
            java.time.Instant instant = java.time.Instant.parse(lead.getString("status_updated_utc").replace(' ', 'T') + "Z");
            when = " on " + java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(java.time.ZoneId.systemDefault()).format(instant);
        } catch (Exception ignored) {
        }
        // Do Not Call is a hard stop, not a warning: the server rejects starting this door anyway
        // (D2dDisposition::startForEmployeeToken()), so offering "Continue Anyway" would only lead to an error.
        if ("do_not_call".equals(lead.optString("status", ""))) {
            AlertDialog blocked = new AlertDialog.Builder(this)
                    .setCustomTitle(Theme.dialogTitle(this, "Do Not Call", Theme.ERROR))
                    .setMessage((address.isEmpty() ? "This house" : address) + " was marked Do Not Call" + when + ".\n\nDo not knock this door. If the earlier outcome was a mistake, ask your manager to correct it.")
                    .setPositiveButton("OK", null)
                    .show();
            Theme.styleDialog(blocked, Theme.ERROR);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "Already " + statusLabel, Theme.WARNING))
                .setMessage((address.isEmpty() ? "This house" : address) + " was already marked " + statusLabel + when + ". Knocking again may annoy the homeowner.\n\nOnly continue if this is genuinely a different unit, or the earlier outcome was wrong.")
                .setPositiveButton("Continue Anyway", (d, w) -> showStartDoorDialog(pointId, lat, lon))
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.WARNING);
    }

    private void showStartDoorDialog(Long pointId, double lat, double lon) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * density);
        container.setPadding(pad, 0, pad, (int) (4 * density));

        EditText addressField = new EditText(this);
        addressField.setHint("Looking up address…");
        Theme.styleInput(addressField);
        container.addView(addressField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "I'm at the door", Theme.PRIMARY))
                .setView(container)
                .setPositiveButton("Start", null)
                .setNeutralButton("Look Up", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.PRIMARY);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            addressField.setText("");
            addressField.setHint("Looking up address…");
            lookupAddress(lat, lon, addressField);
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String address = addressField.getText().toString().trim();
            if (address.isEmpty()) {
                addressField.setError("Enter the house address");
                return;
            }
            // The reverse case of the lunch guard: starting a sale while still clocked in on lunch
            // would leave the timesheet and the day's activity contradicting each other.
            confirmNotOnLunchThenStartDoor(pointId, lat, lon, address, dialog);
        });
        lookupAddress(lat, lon, addressField);
    }

    private void confirmNotOnLunchThenStartDoor(Long pointId, double lat, double lon, String address, AlertDialog sourceDialog) {
        String today = java.time.LocalDate.now().toString();
        try {
            request("timeclock/day", new JSONObject().put("token", token).put("work_date", today), null, r -> {
                String state = finalDayState(r.getJSONArray("events"));
                if ("lunch".equals(state) || "break".equals(state)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Still on " + state)
                            .setMessage("You're still clocked in on " + state + ". Start this door anyway?")
                            .setPositiveButton("Yes, start it", (d, w) -> {
                                submitStartDoor(pointId, lat, lon, address);
                                sourceDialog.dismiss();
                            })
                            .setNegativeButton("Go back", null)
                            .show();
                } else {
                    submitStartDoor(pointId, lat, lon, address);
                    sourceDialog.dismiss();
                }
            }, true);
        } catch (Exception e) {
            // If the check itself fails, don't block the rep from starting the door over it.
            submitStartDoor(pointId, lat, lon, address);
            sourceDialog.dismiss();
        }
    }

    /** Reverse-geocodes the tapped position via Nominatim (OpenStreetMap's own lookup, matching the
     * tiles already used for the map) so the manager sees exactly which house — the rep can still
     * correct it, but doesn't have to type it from memory. Only fills the field if he hasn't already
     * started typing something himself. */
    private void lookupAddress(double lat, double lon, EditText target) {
        new Thread(() -> {
            String address = null;
            HttpsURLConnection conn = null;
            try {
                String url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lon + "&zoom=18&addressdetails=1";
                conn = (HttpsURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "EmployeeFieldApp/1.0 (huynhdous.com)");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() == 200) {
                    JSONObject resp = new JSONObject(new String(readAllBytes(conn.getInputStream()), StandardCharsets.UTF_8));
                    JSONObject addr = resp.optJSONObject("address");
                    if (addr != null) {
                        String houseNumber = addr.optString("house_number", "");
                        String road = addr.optString("road", "");
                        String combined = (houseNumber + " " + road).trim();
                        if (!combined.isEmpty()) address = combined;
                    }
                    if (address == null) address = resp.isNull("display_name") ? null : resp.getString("display_name");
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            String result = address;
            runOnUiThread(() -> {
                if (result != null && target.getText().toString().isEmpty()) {
                    target.setText(result);
                } else {
                    target.setHint("House number or address");
                }
            });
        }).start();
    }

    private void submitStartDoor(Long pointId, double lat, double lon, String address) {
        try {
            JSONObject body = new JSONObject().put("token", token);
            if (pointId != null) {
                body.put("location_point_id", (long) pointId);
            } else {
                body.put("latitude", lat).put("longitude", lon);
            }
            if (!address.isEmpty()) body.put("address", address);
            request("telemapper/disposition/start", body, null, r -> {
                activeDispositionId = r.getInt("disposition_id");
                activeDoorStartedMs = java.time.Instant.parse(r.getString("created_utc").replace(' ', 'T') + "Z").toEpochMilli();
                activeDoorLat = r.getDouble("latitude");
                activeDoorLon = r.getDouble("longitude");
                renderDoorsActive(address.isEmpty() ? "Door at recorded position" : address);
                selectTab(5);
                loadDoorsMap();
            });
        } catch (Exception ignored) {
        }
    }

    private static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void beginFinishDoor() {
        if (activeDispositionId == null) return;
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_DOORS_CAMERA_PERMISSION);
            return;
        }
        startDoorsPhotoCapture();
    }

    private void startDoorsPhotoCapture() {
        try {
            File photoFile = File.createTempFile("door_", ".jpg", getCacheDir());
            pendingDoorsPhotoFile = photoFile;
            pendingDoorsPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingDoorsPhotoUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_TAKE_DOORS_PHOTO);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    private void locatePhotoThenContinue() {
        if (doorsMessageText != null) doorsMessageText.setText("Checking your location…");
        if (!hasLocationPermission()) {
            checkPhotoProximityAndContinue(null, null);
            return;
        }
        android.location.LocationManager manager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            android.location.Location best = null;
            for (String provider : manager.getProviders(true)) {
                android.location.Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null) {
                checkPhotoProximityAndContinue(best.getLatitude(), best.getLongitude());
            } else {
                manager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        checkPhotoProximityAndContinue(location.getLatitude(), location.getLongitude());
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        checkPhotoProximityAndContinue(null, null);
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }
                }, android.os.Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            checkPhotoProximityAndContinue(null, null);
        }
    }

    private void checkPhotoProximityAndContinue(Double lat, Double lon) {
        if (lat != null && lon != null && activeDoorLat != null && activeDoorLon != null) {
            double distance = metersBetween(activeDoorLat, activeDoorLon, lat, lon);
            if (distance > DOORS_PHOTO_PROXIMITY_METERS) {
                // Retake is the normal path (still standing at the door), but a rep who only
                // realizes later — after leaving, at the end of the day — needs a way to still
                // close this out instead of it being stuck open forever. The server already
                // tolerates this: a far-away photo is recorded and flagged for manager review,
                // never rejected, so "Finish anyway" just uses the path that already exists.
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setCustomTitle(Theme.dialogTitle(this, "That looks too far from the house", Theme.WARNING))
                        .setMessage("This photo was taken about " + Math.round(distance) + "m from where you started this door. If you're still there, retake it closer. If you've already left, you can still finish it — this will be flagged for your manager to review.")
                        .setPositiveButton("Retake photo", (d, which) -> beginFinishDoor())
                        .setNeutralButton("Finish anyway", (d, which) -> promptFarAwayReason(lat, lon))
                        .setNegativeButton("Cancel", (d, which) -> {
                            if (doorsMessageText != null) doorsMessageText.setText("Timer running — tap Finish when the door closes.");
                        })
                        .show();
                Theme.styleDialog(dialog, Theme.WARNING);
                return;
            }
        }
        showFinishDoorDialog(lat, lon, null);
    }

    /** Required before finishing a door away from its location — the server won't accept it without
     * one, and asking up front (instead of failing after the outcome picker) saves a rep from
     * re-entering the whole outcome if they left it blank. */
    private void promptFarAwayReason(Double lat, Double lon) {
        float density = getResources().getDisplayMetrics().density;
        EditText reasonField = new EditText(this);
        reasonField.setHint("e.g. Already left for the day");
        Theme.styleInput(reasonField);
        int pad = (int) (20 * density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(reasonField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "Why are you finishing this away from the door?", Theme.WARNING))
                .setView(container)
                .setPositiveButton("Continue", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.WARNING);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonField.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonField.setError("Enter a reason");
                return;
            }
            showFinishDoorDialog(lat, lon, reason);
            dialog.dismiss();
        });
    }

    private void showFinishDoorDialog(Double lat, Double lon, String photoDistanceReason) {
        if (activeDispositionId == null) return;
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        container.setPadding(pad, pad / 2, pad, 0);

        TextView label = new TextView(this);
        label.setText("OUTCOME");
        label.setTextSize(12);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextColor(Theme.TEXT_SECONDARY);
        label.setLetterSpacing(0.06f);
        container.addView(label);

        String[] statusCodes = {"sold", "not_home", "already_serviced", "not_interested", "not_owner", "callback", "do_not_call", "other"};
        String[] statusLabels = {"Sold", "Not Home", "Already Had Service", "Not Interested", "Not the Owner", "Come Back Another Time", "Do Not Call", "Other"};
        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.content.res.ColorStateList radioTint = android.content.res.ColorStateList.valueOf(Theme.PRIMARY);
        for (int i = 0; i < statusCodes.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(statusLabels[i]);
            rb.setTextColor(Theme.TEXT_PRIMARY);
            rb.setButtonTintList(radioTint);
            rb.setTag(statusCodes[i]);
            LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rbParams.topMargin = (int) (4 * density);
            radioGroup.addView(rb, rbParams);
            if (i == 0) rb.setChecked(true);
        }
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        radioParams.topMargin = (int) (8 * density);
        container.addView(radioGroup, radioParams);

        LinearLayout callbackDateRow = new LinearLayout(this);
        callbackDateRow.setOrientation(LinearLayout.VERTICAL);
        callbackDateRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams callbackDateRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackDateRowParams.topMargin = (int) (12 * density);
        container.addView(callbackDateRow, callbackDateRowParams);

        TextView callbackLabel = new TextView(this);
        callbackLabel.setText("CALL BACK ON");
        callbackLabel.setTextSize(12);
        callbackLabel.setTypeface(callbackLabel.getTypeface(), android.graphics.Typeface.BOLD);
        callbackLabel.setTextColor(Theme.TEXT_SECONDARY);
        callbackLabel.setLetterSpacing(0.06f);
        callbackDateRow.addView(callbackLabel);

        final java.time.LocalDate[] callbackDateHolder = {java.time.LocalDate.now().plusDays(1)};
        Button callbackDateButton = styledButton(callbackDateHolder[0].toString(), Theme.PRIMARY);
        LinearLayout.LayoutParams callbackButtonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        callbackButtonParams.topMargin = (int) (4 * density);
        callbackDateRow.addView(callbackDateButton, callbackButtonParams);
        callbackDateButton.setOnClickListener(v -> new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            callbackDateHolder[0] = java.time.LocalDate.of(year, month + 1, day);
            callbackDateButton.setText(callbackDateHolder[0].toString());
        }, callbackDateHolder[0].getYear(), callbackDateHolder[0].getMonthValue() - 1, callbackDateHolder[0].getDayOfMonth()).show());

        EditText noteField = new EditText(this);
        noteField.setHint("Note (optional)");
        Theme.styleInput(noteField);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = (int) (16 * density);
        noteParams.bottomMargin = (int) (4 * density);
        container.addView(noteField, noteParams);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            android.widget.RadioButton checked = group.findViewById(checkedId);
            String tag = checked != null ? (String) checked.getTag() : null;
            callbackDateRow.setVisibility("callback".equals(tag) ? View.VISIBLE : View.GONE);
            noteField.setHint("other".equals(tag) ? "Reason (required)" : "Note (optional)");
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(Theme.dialogTitle(this, "Finish this door", Theme.SUCCESS))
                .setView(container)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.SUCCESS);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            String status = "sold";
            if (checkedId != -1) {
                android.widget.RadioButton checked = radioGroup.findViewById(checkedId);
                if (checked != null && checked.getTag() != null) status = (String) checked.getTag();
            }
            String note = noteField.getText().toString().trim();
            if ("other".equals(status) && note.isEmpty()) {
                noteField.setError("Enter a reason");
                return;
            }
            String callbackDate = "callback".equals(status) ? callbackDateHolder[0].toString() : null;
            submitFinishDoor(status, note, lat, lon, callbackDate, photoDistanceReason);
            dialog.dismiss();
        });
    }

    /** Queues the finish locally first (a durable copy of the photo + the outcome fields), then
     * tries to upload right away — same offline-first shape as LocationQueue/TrackingService, so a
     * dead zone at the door doesn't cost the rep their photo or make them babysit a spinner. The
     * queue is drained again on the next periodic tracking cycle if this immediate attempt fails. */
    private void submitFinishDoor(String status, String note, Double lat, Double lon, String callbackDate, String photoDistanceReason) {
        if (activeDispositionId == null || pendingDoorsPhotoFile == null) return;
        final int dispositionId = activeDispositionId;
        final File capturedPhoto = pendingDoorsPhotoFile;
        File durableDir = new File(getFilesDir(), "door_finishes");
        if (!durableDir.exists()) durableDir.mkdirs();
        File durablePhoto = new File(durableDir, "door_" + dispositionId + "_" + System.currentTimeMillis() + ".jpg");
        try {
            byte[] bytes = readAllBytes(new FileInputStream(capturedPhoto));
            // Checked here, before queueing, not just left to the server: a too-large photo would
            // otherwise queue successfully and then fail every future drain attempt forever, blocking
            // every other queued door behind it since drain() stops at the first failure it hits.
            if (bytes.length > 8 * 1024 * 1024) {
                new AlertDialog.Builder(this).setTitle("Finish door").setMessage("That photo is too large. Please retake it.").setPositiveButton("OK", null).show();
                return;
            }
            try (OutputStream out = new FileOutputStream(durablePhoto)) {
                out.write(bytes);
            }
        } catch (IOException e) {
            new AlertDialog.Builder(this).setTitle("Finish door").setMessage("Unable to save the photo. Try again.").setPositiveButton("OK", null).show();
            return;
        }
        capturedPhoto.delete();
        dispositionQueue.add(new DispositionQueue.Finish(employeeId, dispositionId, status, note, lat, lon, callbackDate, durablePhoto.getAbsolutePath(), photoDistanceReason, null));
        renderDoorsIdle();
        loadDoorsMap();
        doorsMessageText.setText("Door saved — uploading now…");

        final String currentToken = token;
        final long currentEmployeeId = employeeId;
        new Thread(() -> {
            dispositionQueue.drain(currentToken, currentEmployeeId);
            int remaining = dispositionQueue.pendingCountForEmployee(currentEmployeeId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || doorsMessageText == null || activeDispositionId != null) return;
                doorsMessageText.setText(remaining == 0
                        ? "Tap your position on the map below, closest to the house, to start a door — or use \"Start a New Door Here\" if none is close enough."
                        : remaining + " door" + (remaining == 1 ? "" : "s") + " saved — will upload when you have a signal.");
            });
        }).start();
    }

    private void loadTimesheet() {
        if (timesheetMessage == null) return;
        timesheetMessage.setText("Loading…");
        try {
            request("timesheet/week", new JSONObject().put("token", token), null, this::renderTimesheet);
        } catch (Exception e) {
            timesheetMessage.setText("Unable to load timesheet.");
        }
    }

    private void renderTimesheet(JSONObject data) throws Exception {
        float density = getResources().getDisplayMetrics().density;
        String weekStart = data.getString("week_start"), weekEnd = data.getString("week_end");
        java.time.LocalDate start = java.time.LocalDate.parse(weekStart), end = java.time.LocalDate.parse(weekEnd);
        java.time.format.DateTimeFormatter shortDate = java.time.format.DateTimeFormatter.ofPattern("MMM d");
        timesheetMessage.setText("Week of " + shortDate.format(start) + " – " + shortDate.format(end));

        int totalMinutes = data.getInt("total_minutes");
        int approvedMinutes = data.optInt("approved_minutes", 0);
        int pendingMinutes = data.optInt("pending_minutes", totalMinutes - approvedMinutes);
        timesheetTotalText.setText(String.format(java.util.Locale.US, "%dh %02dm", totalMinutes / 60, totalMinutes % 60));

        timesheetBadgeRow.removeAllViews();
        String submissionStatus = data.isNull("submission_status") ? null : data.optString("submission_status", null);
        // Paul only needs to know one thing: is this week done, or still being worked on. Everything else
        // (approved/submitted/accepted/reopened/needs_correction) is office-internal plumbing.
        boolean accepted = "accepted".equals(submissionStatus) && pendingMinutes <= 0;
        addTimesheetBadge(timesheetBadgeRow, accepted ? "Accepted" : "Pending", accepted ? Theme.SUCCESS : Theme.PRIMARY, density);

        timesheetDaysContainer.removeAllViews();
        org.json.JSONArray days = data.getJSONArray("days");
        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d");
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            java.time.LocalDate date = java.time.LocalDate.parse(day.getString("date"));
            boolean incomplete = day.getBoolean("incomplete");
            boolean inProgress = day.getBoolean("in_progress");
            boolean hasDispute = day.getBoolean("has_dispute");
            Integer netMinutes = day.isNull("net_minutes") ? null : day.getInt("net_minutes");

            LinearLayout dayCard = new LinearLayout(this);
            dayCard.setOrientation(LinearLayout.VERTICAL);
            dayCard.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            dayCard.setBackground(Theme.cardBackground(this));
            LinearLayout.LayoutParams dayCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dayCardParams.topMargin = (int) (8 * density);
            timesheetDaysContainer.addView(dayCard, dayCardParams);

            LinearLayout dayRow = new LinearLayout(this);
            dayRow.setOrientation(LinearLayout.HORIZONTAL);
            dayRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            dayCard.addView(dayRow);

            TextView dayLabel = new TextView(this);
            dayLabel.setText(dayFormat.format(date));
            dayLabel.setTextSize(14);
            dayLabel.setTextColor(Theme.TEXT_PRIMARY);
            dayRow.addView(dayLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView hoursLabel = new TextView(this);
            if (netMinutes != null)
                hoursLabel.setText(String.format(java.util.Locale.US, "%dh %02dm", netMinutes / 60, netMinutes % 60));
            else if (inProgress) hoursLabel.setText("In progress");
            else if (incomplete) hoursLabel.setText("Missing clock-out");
            else hoursLabel.setText("—");
            hoursLabel.setTextSize(14);
            hoursLabel.setTypeface(hoursLabel.getTypeface(), android.graphics.Typeface.BOLD);
            hoursLabel.setTextColor(incomplete ? Theme.ERROR : inProgress ? Theme.PRIMARY : Theme.TEXT_PRIMARY);
            dayRow.addView(hoursLabel);

            if (hasDispute) {
                TextView conflictBadge = Theme.statusBadge(this, "⚠ Conflict", Theme.ERROR);
                LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                conflictParams.topMargin = (int) (6 * density);
                dayCard.addView(conflictBadge, conflictParams);
                String note = day.optString("dispute_note", "");
                if (!note.isEmpty()) {
                    TextView noteView = new TextView(this);
                    noteView.setText(note);
                    noteView.setTextSize(12);
                    noteView.setTextColor(Theme.TEXT_SECONDARY);
                    LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    noteParams.topMargin = (int) (4 * density);
                    dayCard.addView(noteView, noteParams);
                }
            }
        }
    }

    private void addTimesheetBadge(LinearLayout row, String text, int color, float density) {
        TextView badge = Theme.statusBadge(this, text, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = (int) (6 * density);
        row.addView(badge, params);
    }

    private void loadSchedule() {
        loadWeekTotal();
        try {
            request("schedule", new JSONObject().put("token", token), null, this::renderSchedule);
        } catch (Exception e) {
            scheduleContainer.removeAllViews();
            TextView error = new TextView(this);
            error.setText("Unable to load schedule.");
            scheduleContainer.addView(error);
        }
    }

    private void renderSchedule(JSONObject data) throws Exception {
        scheduleWeekStart = data.getString("week_start");
        java.time.LocalDate monday = java.time.LocalDate.parse(scheduleWeekStart);
        java.time.LocalDate weekEndDate = java.time.LocalDate.parse(data.getString("week_end"));
        int scheduleDayCount = (int) (java.time.temporal.ChronoUnit.DAYS.between(monday, weekEndDate) + 1);
        JSONObject employeeObj = data.getJSONObject("employee");
        java.time.ZoneId zone;
        try {
            zone = java.time.ZoneId.of(employeeObj.optString("timezone", ""));
        } catch (Exception e) {
            zone = java.time.ZoneId.systemDefault();
        }
        java.time.LocalDate today = java.time.LocalDate.now(zone);

        org.json.JSONArray assignments = data.getJSONArray("assignments");
        java.util.Map<String, java.util.List<JSONObject>> byDate = new java.util.LinkedHashMap<>();
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject a = assignments.getJSONObject(i);
            byDate.computeIfAbsent(a.getString("work_date"), k -> new java.util.ArrayList<>()).add(a);
        }
        float density = getResources().getDisplayMetrics().density;

        scheduleContainer.removeAllViews();
        java.util.List<Object[]> timeClockTasks = new java.util.ArrayList<>();
        int weekMinutesScheduled = 0;
        int todayMinutesScheduled = 0;
        for (int i = 0; i < scheduleDayCount; i++) {
            java.time.LocalDate date = monday.plusDays(i);
            String dateKey = date.toString();
            boolean isToday = date.equals(today);

            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            headerRow.setPadding(0, (int) (18 * density), 0, (int) (6 * density));
            scheduleContainer.addView(headerRow);

            TextView header = new TextView(this);
            header.setText((DAY_NAMES[i] + " " + date.getMonthValue() + "/" + date.getDayOfMonth()).toUpperCase());
            header.setTextSize(12);
            header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
            header.setTextColor(isToday ? Theme.PRIMARY : Theme.NEUTRAL);
            headerRow.addView(header);

            if (isToday) {
                TextView todayBadge = Theme.statusBadge(this, "TODAY", Theme.PRIMARY);
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                badgeParams.leftMargin = (int) (8 * density);
                headerRow.addView(todayBadge, badgeParams);
            }

            java.util.List<JSONObject> rows = byDate.get(dateKey);
            if (rows == null || rows.isEmpty()) {
                addRow(dayCode(i), 0xfff3f4f6, 0xff9ca3af, "No assignments", null, density);
                continue;
            }
            for (JSONObject a : rows) {
                String startTime = a.getString("start_time"), endTime = a.getString("end_time");
                addRow(dayCode(i), isToday ? Theme.PRIMARY : 0xffe5e7eb, isToday ? 0xffffffff : Theme.NEUTRAL,
                        a.optString("location_name", "Unknown location"),
                        startTime.substring(0, 5) + " – " + endTime.substring(0, 5), density);
                int shiftMinutes = shiftMinutes(a);
                weekMinutesScheduled += shiftMinutes;
                if (isToday) todayMinutesScheduled += shiftMinutes;
            }
            if (date.isAfter(today)) continue;
            LinearLayout timeClockContainer = new LinearLayout(this);
            timeClockContainer.setOrientation(LinearLayout.VERTICAL);
            scheduleContainer.addView(timeClockContainer);
            boolean withinWindow = false;
            java.time.LocalTime nextStart = null;
            if (isToday) {
                java.time.LocalTime nowTime = java.time.LocalTime.now(zone);
                for (JSONObject a : rows) {
                    java.time.LocalTime s = java.time.LocalTime.parse(a.getString("start_time").substring(0, 5));
                    java.time.LocalTime e = java.time.LocalTime.parse(a.getString("end_time").substring(0, 5));
                    if (!nowTime.isBefore(s) && nowTime.isBefore(e)) {
                        withinWindow = true;
                        break;
                    }
                    if (nowTime.isBefore(s) && (nextStart == null || s.isBefore(nextStart))) nextStart = s;
                }
            }
            timeClockTasks.add(new Object[]{dateKey, timeClockContainer, withinWindow, nextStart, zone, isToday});
        }
        todayScheduledMinutes = todayMinutesScheduled > 0 ? todayMinutesScheduled : DAILY_TARGET_MINUTES;
        weekScheduledMinutes = weekMinutesScheduled > 0 ? weekMinutesScheduled : -1;
        updateWeekGauge();
        loadTimeClockStatesSequentially(timeClockTasks, 0);

        String confirmedUtc = data.isNull("confirmed_utc") ? null : data.optString("confirmed_utc", null);
        if (confirmedUtc != null && !confirmedUtc.isEmpty()) {
            java.time.Instant instant = java.time.Instant.parse(confirmedUtc.replace(' ', 'T') + "Z");
            String local = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(instant);
            confirmStatus.setText("✓ Schedule confirmed " + local);
            confirmStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
        } else {
            confirmStatus.setVisibility(View.GONE);
            confirmButton.setVisibility(View.VISIBLE);
        }
    }

    private void confirmSchedule() {
        try {
            request("schedule/confirm", new JSONObject().put("token", token).put("week_start", scheduleWeekStart), confirmButton, r -> {
                confirmButton.setVisibility(View.GONE);
                confirmStatus.setText("✓ Schedule confirmed just now.");
                confirmStatus.setVisibility(View.VISIBLE);
            });
        } catch (Exception ignored) {
        }
    }

    private void loadTimeClockState(String dateKey, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        try {
            request("timeclock/day", new JSONObject().put("token", token).put("work_date", dateKey), null,
                    r -> renderTimeClockButtons(dateKey, container, r.getJSONArray("events"), r.optJSONObject("dispute"), withinWindow, nextStart, zone, isToday));
        } catch (Exception ignored) {
        }
    }

    private void loadTimeClockStatesSequentially(java.util.List<Object[]> tasks, int index) {
        if (index >= tasks.size()) return;
        Object[] task = tasks.get(index);
        String dateKey = (String) task[0];
        LinearLayout container = (LinearLayout) task[1];
        boolean withinWindow = (Boolean) task[2];
        java.time.LocalTime nextStart = (java.time.LocalTime) task[3];
        java.time.ZoneId zone = (java.time.ZoneId) task[4];
        boolean isToday = (Boolean) task[5];
        try {
            request("timeclock/day", new JSONObject().put("token", token).put("work_date", dateKey), null,
                    r -> {
                        renderTimeClockButtons(dateKey, container, r.getJSONArray("events"), r.optJSONObject("dispute"), withinWindow, nextStart, zone, isToday);
                        loadTimeClockStatesSequentially(tasks, index + 1);
                    });
        } catch (Exception ignored) {
            loadTimeClockStatesSequentially(tasks, index + 1);
        }
    }

    private String finalDayState(org.json.JSONArray events) throws Exception {
        String state = null;
        boolean everStarted = false;
        for (int i = 0; i < events.length(); i++) {
            switch (events.getJSONObject(i).getString("event_type")) {
                case "start_work": state = "work"; everStarted = true; break;
                case "start_lunch": state = "lunch"; break;
                case "end_lunch": state = "work"; break;
                case "start_break": state = "break"; break;
                case "end_break": state = "work"; break;
                case "end_work": state = "ended"; break;
            }
        }
        if (state == null) return everStarted ? "ended" : "none";
        return state;
    }

    private Gauge buildGauge(String caption, String subLabel, float density) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        int size = (int) (92 * density);
        FrameLayout frame = new FrameLayout(this);
        column.addView(frame, new LinearLayout.LayoutParams(size, size));

        DayProgressRing ring = new DayProgressRing(this);
        frame.addView(ring, new FrameLayout.LayoutParams(size, size));

        LinearLayout centerColumn = new LinearLayout(this);
        centerColumn.setOrientation(LinearLayout.VERTICAL);
        centerColumn.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(size, size);
        centerParams.gravity = android.view.Gravity.CENTER;
        frame.addView(centerColumn, centerParams);

        TextView centerText = new TextView(this);
        centerText.setTextSize(16);
        centerText.setTypeface(centerText.getTypeface(), android.graphics.Typeface.BOLD);
        centerText.setTextColor(Theme.TEXT_PRIMARY);
        centerText.setGravity(android.view.Gravity.CENTER);
        centerColumn.addView(centerText);

        TextView subText = new TextView(this);
        subText.setText(subLabel);
        subText.setTextSize(10);
        subText.setTextColor(Theme.TEXT_SECONDARY);
        subText.setGravity(android.view.Gravity.CENTER);
        centerColumn.addView(subText);

        TextView label = new TextView(this);
        label.setText(caption);
        label.setTextSize(12);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (8 * density);
        column.addView(label, labelParams);

        return new Gauge(column, ring, centerText, subText);
    }

    private void renderDayTotal(org.json.JSONArray events, java.time.ZoneId zone) throws Exception {
        if (dayTotalCard == null) return;

        int workMinutes = 0;
        String state = null;
        java.time.Instant segStart = null;
        String firstClockIn = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            java.time.Instant t = java.time.Instant.parse(e.getString("event_utc").replace(' ', 'T') + "Z");
            long elapsed = segStart != null ? java.time.Duration.between(segStart, t).toMinutes() : 0;
            String type = e.getString("event_type");
            switch (type) {
                case "start_work":
                    if (firstClockIn == null)
                        firstClockIn = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(zone).format(t);
                    state = "work";
                    segStart = t;
                    break;
                case "start_lunch":
                    if ("work".equals(state)) workMinutes += elapsed;
                    state = "lunch";
                    segStart = t;
                    break;
                case "end_lunch":
                    state = "work";
                    segStart = t;
                    break;
                case "start_break":
                    if ("work".equals(state)) workMinutes += elapsed;
                    state = "break";
                    segStart = t;
                    break;
                case "end_break":
                    state = "work";
                    segStart = t;
                    break;
                case "end_work":
                    if ("work".equals(state)) workMinutes += elapsed;
                    state = null;
                    segStart = null;
                    break;
            }
        }
        boolean stillWorking = "work".equals(state) && segStart != null;
        if (stillWorking) workMinutes += java.time.Duration.between(segStart, java.time.Instant.now()).toMinutes();

        dayTotalCard.setVisibility(View.VISIBLE);
        dayGauge.centerText.setText(String.format(java.util.Locale.US, "%dh %02dm", workMinutes / 60, workMinutes % 60));
        dayGauge.subText.setText(formatHoursShort(todayScheduledMinutes));
        dayGauge.ring.setProgress(workMinutes / (float) todayScheduledMinutes, stillWorking ? Theme.PRIMARY : Theme.SUCCESS);
        dayTotalClockInText.setText(firstClockIn == null ? "Not clocked in yet" : "Clocked in at " + firstClockIn);
    }

    /** "of 8h" or "of 6h 30m" — the scheduled-hours caption shown inside a gauge. */
    private String formatHoursShort(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? ("of " + h + "h") : String.format(java.util.Locale.US, "of %dh %02dm", h, m);
    }

    private int shiftMinutes(JSONObject a) throws Exception {
        java.time.LocalTime s = java.time.LocalTime.parse(a.getString("start_time").substring(0, 5));
        java.time.LocalTime e = java.time.LocalTime.parse(a.getString("end_time").substring(0, 5));
        int minutes = (int) java.time.Duration.between(s, e).toMinutes();
        return minutes >= 0 ? minutes : minutes + 24 * 60;
    }

    /** Refreshes the week gauge once both its inputs (actual worked minutes, scheduled minutes) are known. */
    private void updateWeekGauge() {
        if (weekGauge == null || weekWorkedMinutes < 0) return;
        int target = weekScheduledMinutes > 0 ? weekScheduledMinutes : WEEKLY_TARGET_MINUTES;
        weekGauge.centerText.setText(String.format(java.util.Locale.US, "%dh %02dm", weekWorkedMinutes / 60, weekWorkedMinutes % 60));
        weekGauge.subText.setText(formatHoursShort(target));
        weekGauge.ring.setProgress(weekWorkedMinutes / (float) target, weekWorkedMinutes >= target ? Theme.SUCCESS : Theme.PRIMARY);
    }

    private void loadWeekTotal() {
        // Independent of the shared request()/busy gate: this runs alongside the schedule fetch
        // (also triggered from loadSchedule()) and must not cause that one to be silently dropped.
        new Thread(() -> {
            JSONObject response = null;
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) new URL(API + "timesheet/week").openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(new JSONObject().put("token", token).toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream != null) {
                    JSONObject data = new JSONObject(new String(readAllBytes(stream), StandardCharsets.UTF_8));
                    if (code >= 200 && code < 300 && data.optBoolean("success")) response = data;
                }
            } catch (Exception ignored) {
                // Offline or transient failure — leave the week gauge showing its last value.
            } finally {
                if (conn != null) conn.disconnect();
            }
            final JSONObject data = response;
            runOnUiThread(() -> {
                if (data == null) return;
                try {
                    weekWorkedMinutes = data.getInt("total_minutes");
                    updateWeekGauge();
                } catch (Exception ignored) {
                }
            });
        }).start();
    }

    private void renderTimeClockButtons(String dateKey, LinearLayout container, org.json.JSONArray events, JSONObject dispute, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) throws Exception {
        if (isToday) renderDayTotal(events, zone);

        String finalState = finalDayState(events);
        boolean workStarted = finalState.equals("work");
        boolean workEnded = finalState.equals("ended");
        boolean onLunch = finalState.equals("lunch");
        boolean onBreak = finalState.equals("break");

        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.rightMargin = (int) (8 * density);

        renderTimeline(container, events, zone, density);

        boolean hadLunch = false;
        for (int i = 0; i < events.length(); i++) {
            if (events.getJSONObject(i).getString("event_type").equals("end_lunch")) { hadLunch = true; break; }
        }

        if (workEnded) {
            TextView doneText = Theme.statusBadge(this, "✓ Work day complete", Theme.SUCCESS);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            doneParams.topMargin = (int) (4 * density);
            doneParams.bottomMargin = (int) (8 * density);
            container.addView(doneText, doneParams);
        } else if (isToday) {
            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = (int) (4 * density);
            rowParams.bottomMargin = (int) (8 * density);
            if (onLunch) {
                addTimeClockButton(buttonRow, container, btnParams, "End lunch", 0xffea580c, dateKey, "end_lunch", withinWindow, nextStart, zone, isToday);
            } else if (onBreak) {
                addTimeClockButton(buttonRow, container, btnParams, "End paid break", 0xffea580c, dateKey, "end_break", withinWindow, nextStart, zone, isToday);
            } else if (workStarted) {
                if (!hadLunch) addTimeClockButton(buttonRow, container, btnParams, "Start lunch", Theme.PRIMARY, dateKey, "start_lunch", withinWindow, nextStart, zone, isToday);
                addTimeClockButton(buttonRow, container, btnParams, "Start break", Theme.PRIMARY, dateKey, "start_break", withinWindow, nextStart, zone, isToday);
                addTimeClockButton(buttonRow, container, btnParams, "End work", 0xffb91c1c, dateKey, "end_work", withinWindow, nextStart, zone, isToday);
            } else if (withinWindow) {
                addTimeClockButton(buttonRow, container, btnParams, "Start work", 0xff16a34a, dateKey, "start_work", withinWindow, nextStart, zone, isToday);
            } else {
                java.time.format.DateTimeFormatter clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
                Button disabledButton = smallTimeClockButton(nextStart != null ? "Starts at " + clock.format(nextStart) : "Working window has ended", Theme.NEUTRAL);
                disabledButton.setEnabled(false);
                buttonRow.addView(disabledButton, btnParams);
            }
            container.addView(buttonRow, rowParams);
        } else {
            TextView notClockedIn = new TextView(this);
            notClockedIn.setText(workStarted || onLunch || onBreak ? "In progress — no clock-out recorded" : "No punches recorded");
            notClockedIn.setTextColor(Theme.NEUTRAL);
            notClockedIn.setTextSize(13);
            notClockedIn.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            container.addView(notClockedIn);
        }

        if (dispute != null) {
            TextView conflictBadge = Theme.statusBadge(this, "⚠ CONFLICT FLAGGED", Theme.ERROR);
            LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            conflictParams.topMargin = (int) (10 * density);
            container.addView(conflictBadge, conflictParams);

            TextView flagged = new TextView(this);
            flagged.setText(dispute.optString("note", ""));
            flagged.setTextColor(Theme.TEXT_SECONDARY);
            flagged.setTextSize(13);
            flagged.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            container.addView(flagged);
        } else {
            TextView flagLink = new TextView(this);
            flagLink.setText("Flag an issue with this day");
            flagLink.setTextColor(Theme.NEUTRAL);
            flagLink.setTextSize(13);
            flagLink.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
            flagLink.setOnClickListener(v -> showFlagDialog(dateKey, container, withinWindow, nextStart, zone, isToday));
            container.addView(flagLink);
        }

        TextView editLink = new TextView(this);
        editLink.setText(isToday ? "Edit today's times" : "Edit this day's times");
        editLink.setTextColor(Theme.PRIMARY);
        editLink.setTextSize(13);
        editLink.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        editLink.setOnClickListener(v -> {
            try {
                showEditTimesDialog(dateKey, container, withinWindow, nextStart, zone, events, isToday);
            } catch (Exception ignored) {
            }
        });
        container.addView(editLink);
    }

    private void renderTimeline(LinearLayout container, org.json.JSONArray events, java.time.ZoneId zone, float density) throws Exception {
        java.time.format.DateTimeFormatter clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(zone);
        java.util.List<Object[]> segments = new java.util.ArrayList<>();
        String state = null;
        java.time.Instant segStart = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.getJSONObject(i);
            String type = ev.getString("event_type");
            java.time.Instant t = java.time.Instant.parse(ev.getString("event_utc").replace(' ', 'T') + "Z");
            switch (type) {
                case "start_work":
                    state = "work"; segStart = t;
                    break;
                case "start_lunch":
                    if (state != null && segStart != null) segments.add(new Object[]{state, segStart, t});
                    state = "lunch"; segStart = t;
                    break;
                case "end_lunch":
                    if (segStart != null) segments.add(new Object[]{"lunch", segStart, t});
                    state = "work"; segStart = t;
                    break;
                case "start_break":
                    if (state != null && segStart != null) segments.add(new Object[]{state, segStart, t});
                    state = "break"; segStart = t;
                    break;
                case "end_break":
                    if (segStart != null) segments.add(new Object[]{"break", segStart, t});
                    state = "work"; segStart = t;
                    break;
                case "end_work":
                    if (segStart != null) segments.add(new Object[]{"work", segStart, t});
                    state = null; segStart = null;
                    break;
            }
        }
        if (state != null && segStart != null) segments.add(new Object[]{state, segStart, null});
        if (segments.isEmpty()) return;

        long totalWorkMinutes = 0;
        for (Object[] seg : segments) {
            if (seg[0].equals("work")) {
                java.time.Instant start = (java.time.Instant) seg[1];
                java.time.Instant end = seg[2] != null ? (java.time.Instant) seg[2] : java.time.Instant.now();
                totalWorkMinutes += java.time.Duration.between(start, end).toMinutes();
            }
        }
        TextView totalView = new TextView(this);
        totalView.setText(String.format(java.util.Locale.US, "Worked hrs: %d:%02d", totalWorkMinutes / 60, totalWorkMinutes % 60));
        totalView.setTextSize(14);
        totalView.setTypeface(null, android.graphics.Typeface.BOLD);
        totalView.setTextColor(Theme.PRIMARY);
        totalView.setPadding(0, (int) (6 * density), 0, (int) (4 * density));
        container.addView(totalView);

        for (Object[] seg : segments) {
            String label = (String) seg[0];
            java.time.Instant start = (java.time.Instant) seg[1];
            java.time.Instant end = (java.time.Instant) seg[2];
            String rowLabel = label.equals("work") ? "In" : label.equals("lunch") ? "Lunch" : "Break";
            int badgeColor = label.equals("work") ? Theme.SUCCESS : Theme.WARNING;
            StringBuilder text = new StringBuilder(clock.format(start)).append(" to ").append(end != null ? clock.format(end) : "—");
            if (end != null) {
                long minutes = java.time.Duration.between(start, end).toMinutes();
                text.append("  ·  ").append(label.equals("work") ? (minutes / 60) + ":" + String.format(java.util.Locale.US, "%02d", minutes % 60) : minutes + " min");
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (3 * density), 0, (int) (3 * density));

            TextView badge = Theme.statusBadge(this, rowLabel, badgeColor);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.rightMargin = (int) (8 * density);
            row.addView(badge, badgeParams);

            TextView detailView = new TextView(this);
            detailView.setText(text.toString());
            detailView.setTextSize(13);
            detailView.setTextColor(Theme.TEXT_SECONDARY);
            row.addView(detailView);

            container.addView(row);
        }
    }

    private static final String[] TIME_FIELD_TYPES = {"start_work", "end_work", "start_lunch", "end_lunch", "start_break", "end_break"};
    private static final String[] TIME_FIELD_LABELS = {"Clock In", "Clock Out", "Lunch Start", "Lunch End", "Break Start", "Break End"};

    private void showEditTimesDialog(String dateKey, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, org.json.JSONArray events, boolean isToday) throws Exception {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        java.time.LocalTime[] originals = new java.time.LocalTime[6];
        int[] originalEventIds = new int[6];
        for (int i = 0; i < 6; i++) {
            String utc = null;
            int eventId = 0;
            for (int j = 0; j < events.length(); j++) {
                JSONObject ev = events.getJSONObject(j);
                if (ev.getString("event_type").equals(TIME_FIELD_TYPES[i])) {
                    utc = ev.getString("event_utc");
                    eventId = ev.optInt("event_id", 0);
                }
            }
            originalEventIds[i] = eventId;
            originals[i] = utc != null ? java.time.LocalTime.from(java.time.Instant.parse(utc.replace(' ', 'T') + "Z").atZone(zone)) : null;
        }

        java.time.LocalTime[][] holders = new java.time.LocalTime[6][];
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, pad);
        scroll.addView(form);
        for (int i = 0; i < 6; i++) {
            holders[i] = new java.time.LocalTime[]{originals[i]};
            addTimeField(form, TIME_FIELD_LABELS[i], holders[i], density);
        }
        EditText reason = new EditText(this);
        reason.setHint("Reason for the change");
        reason.setMinLines(2);
        Theme.styleInput(reason);
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        reasonParams.topMargin = (int) (12 * density);
        form.addView(reason, reasonParams);

        new AlertDialog.Builder(this)
                .setTitle(isToday ? "Edit today's times" : "Edit this day's times")
                .setView(scroll)
                .setPositiveButton("Save", (d, w) -> {
                    String note = reason.getText().toString().trim();
                    java.util.List<Integer> changed = new java.util.ArrayList<>();
                    for (int i = 0; i < 6; i++) {
                        java.time.LocalTime cur = holders[i][0];
                        if (cur != null && !cur.equals(originals[i])) changed.add(i);
                    }
                    if (changed.isEmpty()) return;
                    if (note.isEmpty()) {
                        new AlertDialog.Builder(this).setTitle("Reason required").setMessage("Enter a reason for the change.").setPositiveButton("OK", null).show();
                        return;
                    }
                    submitTimeEditQueue(dateKey, container, withinWindow, nextStart, zone, holders, originalEventIds, changed, 0, note, isToday);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addTimeField(LinearLayout form, String label, java.time.LocalTime[] holder, float density) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(Theme.NEUTRAL);
        labelView.setPadding(0, (int) (10 * density), 0, (int) (2 * density));
        form.addView(labelView);
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.field_background);
        button.setTextColor(Theme.TEXT_PRIMARY);
        button.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        updateTimeFieldButton(button, holder[0]);
        button.setOnClickListener(v -> {
            java.time.LocalTime current = holder[0] != null ? holder[0] : java.time.LocalTime.of(9, 0);
            new android.app.TimePickerDialog(this, (view, hour, minute) -> {
                holder[0] = java.time.LocalTime.of(hour, minute);
                updateTimeFieldButton(button, holder[0]);
            }, current.getHour(), current.getMinute(), false).show();
        });
        form.addView(button);
    }

    private void updateTimeFieldButton(Button button, java.time.LocalTime time) {
        button.setText(time != null ? java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(time) : "Not set");
    }

    private void submitTimeEditQueue(String dateKey, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, java.time.LocalTime[][] holders, int[] originalEventIds, java.util.List<Integer> changed, int index, String note, boolean isToday) {
        if (index >= changed.size()) {
            loadTimeClockState(dateKey, container, withinWindow, nextStart, zone, isToday);
            return;
        }
        int fieldIndex = changed.get(index);
        java.time.LocalTime time = holders[fieldIndex][0];
        try {
            JSONObject body = new JSONObject()
                    .put("token", token)
                    .put("work_date", dateKey)
                    .put("event_type", TIME_FIELD_TYPES[fieldIndex])
                    .put("corrected_time", String.format(java.util.Locale.US, "%02d:%02d", time.getHour(), time.getMinute()))
                    .put("reason", note);
            if (originalEventIds[fieldIndex] != 0) body.put("original_event_id", originalEventIds[fieldIndex]);
            request("timeclock/correct", body, null,
                    r -> submitTimeEditQueue(dateKey, container, withinWindow, nextStart, zone, holders, originalEventIds, changed, index + 1, note, isToday));
        } catch (Exception ignored) {
        }
    }

    private void addTimeClockButton(LinearLayout row, LinearLayout outerContainer, LinearLayout.LayoutParams params, String label, int color, String dateKey, String eventType, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        Button button = smallTimeClockButton(label, color);
        row.addView(button, params);
        button.setOnClickListener(v -> punchTimeClock(dateKey, eventType, button, outerContainer, withinWindow, nextStart, zone, isToday));
    }

    private Button smallTimeClockButton(String label, int color) {
        Button button = styledButton(label, color);
        button.setTextSize(12);
        float density = getResources().getDisplayMetrics().density;
        button.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        return button;
    }

    private void punchTimeClock(String dateKey, String eventType, Button button, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        // Going to lunch or on break with a door still open means that visit (and its photo/outcome)
        // never gets closed out until he's back -- ask him to confirm instead of letting it slip silently.
        if ("start_lunch".equals(eventType) || "start_break".equals(eventType)) {
            confirmNoActiveDoorThenPunch(dateKey, eventType, button, container, withinWindow, nextStart, zone, isToday);
            return;
        }
        doPunchTimeClock(dateKey, eventType, button, container, withinWindow, nextStart, zone, isToday);
    }

    private void confirmNoActiveDoorThenPunch(String dateKey, String eventType, Button button, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        String label = "start_break".equals(eventType) ? "break" : "lunch";
        try {
            request("telemapper/disposition/active", new JSONObject().put("token", token), null, r -> {
                if (!r.isNull("active")) {
                    String address = r.getJSONObject("active").optString("address", "a door");
                    new AlertDialog.Builder(this)
                            .setTitle("Finish that door first?")
                            .setMessage("You have an ongoing visit at " + address + ". Did you finish that door yet?")
                            .setPositiveButton("Yes, go to " + label, (d, w) -> doPunchTimeClock(dateKey, eventType, button, container, withinWindow, nextStart, zone, isToday))
                            .setNegativeButton("Go back", null)
                            .show();
                } else {
                    confirmNoOpenS2sSiteThenPunch(dateKey, eventType, button, container, withinWindow, nextStart, zone, isToday, label);
                }
            }, true);
        } catch (Exception ignored) {
        }
    }

    /** Same guard as the D2D door check above, but for S2S: going to lunch/break while still
     * checked into a worksite (no checkout yet) leaves that visit open with no record of him
     * actually stepping away, same blind spot a door left open has. */
    private void confirmNoOpenS2sSiteThenPunch(String dateKey, String eventType, Button button, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday, String label) {
        try {
            request("telemapper/arrival/assignments", new JSONObject().put("token", token), null, r -> {
                String openSite = null;
                org.json.JSONArray assignments = r.optJSONArray("assignments");
                if (assignments != null) {
                    for (int i = 0; i < assignments.length(); i++) {
                        JSONObject a = assignments.getJSONObject(i);
                        if (a.optBoolean("checked_in", false) && !a.optBoolean("checked_out", false)) {
                            openSite = a.optString("site_name", "a worksite");
                            break;
                        }
                    }
                }
                if (openSite != null) {
                    String site = openSite;
                    new AlertDialog.Builder(this)
                            .setTitle("Check out first?")
                            .setMessage("You're still checked in at " + site + ". Did you check out before going on " + label + "?")
                            .setPositiveButton("Yes, go to " + label, (d, w) -> doPunchTimeClock(dateKey, eventType, button, container, withinWindow, nextStart, zone, isToday))
                            .setNegativeButton("Go back", null)
                            .show();
                } else {
                    doPunchTimeClock(dateKey, eventType, button, container, withinWindow, nextStart, zone, isToday);
                }
            }, true);
        } catch (Exception ignored) {
        }
    }

    private void doPunchTimeClock(String dateKey, String eventType, Button button, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        try {
            request("timeclock/punch", new JSONObject().put("token", token).put("event_type", eventType).put("work_date", dateKey), button,
                    r -> loadTimeClockState(dateKey, container, withinWindow, nextStart, zone, isToday));
        } catch (Exception ignored) {
        }
    }

    private void showFlagDialog(String dateKey, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        EditText input = new EditText(this);
        input.setHint("What's wrong with this day's record?");
        input.setMinLines(2);
        Theme.styleInput(input);
        new AlertDialog.Builder(this)
                .setTitle("Flag a conflict")
                .setView(input)
                .setPositiveButton("Submit", (d, w) -> {
                    String note = input.getText().toString().trim();
                    if (!note.isEmpty()) flagDispute(dateKey, note, container, withinWindow, nextStart, zone, isToday);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void flagDispute(String dateKey, String note, LinearLayout container, boolean withinWindow, java.time.LocalTime nextStart, java.time.ZoneId zone, boolean isToday) {
        try {
            request("timeclock/dispute", new JSONObject().put("token", token).put("work_date", dateKey).put("note", note), null,
                    r -> loadTimeClockState(dateKey, container, withinWindow, nextStart, zone, isToday));
        } catch (Exception ignored) {
        }
    }

    private boolean hasLocationPermission() {
        boolean precise = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean approx = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return precise || approx;
    }

    private void enableTracking() {
        if (token.isEmpty()) return;
        if (token.equals(TrackingService.activeToken)) return;
        boolean notifications = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (hasLocationPermission() && notifications) {
            startTracking();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Work location reporting")
                .setMessage("Allow location and notifications to record your work route every " + (TrackingService.POLL_INTERVAL_MS / 1000) + " seconds, including while this app is minimized. Recruiters can view your daily route. A tracking notification stays visible; sign out or use its Stop action to end tracking.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.POST_NOTIFICATIONS}, 30);
                    else
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, 30);
                }).setNegativeButton("Not now", (dialog, which) -> {
                    TrackingService.status = "Tracking is off. Location permission is required.";
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_CAMERA_PERMISSION) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startCameraCapture();
            return;
        }
        if (code == REQUEST_DOORS_CAMERA_PERMISSION) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startDoorsPhotoCapture();
            else if (doorsMessageText != null) doorsMessageText.setText("Camera permission is required to finish a door.");
            return;
        }
        if (code == REQUEST_ARRIVAL_CAMERA_PERMISSION) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) beginArrivalSelfie();
            else if (arrivalMessageText != null) arrivalMessageText.setText("Camera permission is required to check in.");
            return;
        }
        if (code == REQUEST_RETRY_CAMERA_PERMISSION) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startRetryPhotoCapture();
            else if (editingFailedFinish != null) showEditFailedDialog(editingFailedFinish, editingFailedFinishAddress);
            return;
        }
        if (code == REQUEST_DOORS_START_HERE_LOCATION_PERMISSION) {
            if (hasLocationPermission()) beginStartNewDoorHere();
            else if (doorsMessageText != null) doorsMessageText.setText("Location permission is required to start a door.");
            return;
        }
        if (code != 30) return;
        boolean location = hasLocationPermission();
        boolean notifications = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (enableTrackingButton != null) enableTrackingButton.setVisibility(location ? View.GONE : View.VISIBLE);
        if (location && notifications) startTracking();
        else {
            TrackingService.status = "Tracking is off. Enable Location and Notifications in app settings.";
            new AlertDialog.Builder(this).setMessage(TrackingService.status).setPositiveButton("App settings", (d, w) -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName())))).setNegativeButton("Cancel", null).show();
        }
    }

    private void startTracking() {
        try {
            android.content.Intent intent = new android.content.Intent(this, TrackingService.class).putExtra("token", token).putExtra("employee_id", employeeId).putExtra("expires_ms", sessionExpires);
            startForegroundService(intent);
        } catch (Exception e) {
            TrackingService.status = "Tracking could not start. Reopen the app and check permissions.";
        }
    }

    private void logout() {
        final String old = token;
        stopService(new android.content.Intent(this, TrackingService.class));
        TrackingService.activeToken = "";
        TrackingService.status = "Tracking is off.";
        showLogin();
        new Thread(() -> {
            try {
                EmployeeApi.post("logout", new JSONObject().put("token", old));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void setButtonBusy(Button button, boolean busyState) {
        button.setEnabled(!busyState);
        Object tag = button.getTag();
        if (tag instanceof View) ((View) tag).setVisibility(busyState ? View.VISIBLE : View.GONE);
    }

    private void request(String action, JSONObject body, Button button, Result result) {
        request(action, body, button, result, false);
    }

    private void request(String action, JSONObject body, Button button, Result result, boolean independentRead) {
        if (!independentRead && busy) return;
        if (!independentRead) busy = true;
        final String requestToken = token;
        if (button != null) setButtonBusy(button, true);
        new Thread(() -> {
            JSONObject response = null;
            String error = null;
            int code = 0;
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) new URL(API + action).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = stream) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (bytes.size() + n > 8_388_608) throw new IOException("RESPONSE_TOO_LARGE");
                        bytes.write(buffer, 0, n);
                    }
                }
                response = new JSONObject(bytes.toString("UTF-8"));
                if (code < 200 || code >= 300 || !response.optBoolean("success"))
                    error = response.optString("message", "Unable to complete request.");
            } catch (Exception e) {
                error = "RESPONSE_TOO_LARGE".equals(e.getMessage())
                        ? "That response was too large to load. Try a narrower date range."
                        : "Unable to connect. Check your internet connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            final JSONObject data = response;
            final String problem = error;
            final int status = code;
            runOnUiThread(() -> {
                if (!independentRead) busy = false;
                if (isFinishing() || isDestroyed()) return;
                if (independentRead && !java.util.Objects.equals(requestToken, token)) return;
                if (button != null) setButtonBusy(button, false);
                if (problem != null) {
                    if (status == 401 || status == 403) {
                        stopService(new android.content.Intent(this, TrackingService.class));
                        TrackingService.status = "Session expired. Sign in again.";
                        showLogin();
                    }
                    new AlertDialog.Builder(this).setTitle("Employee sign-in").setMessage(problem).setPositiveButton("OK", null).show();
                    return;
                }
                try {
                    result.accept(data);
                } catch (Exception e) {
                    showLogin();
                }
            });
        }).start();
    }
}
