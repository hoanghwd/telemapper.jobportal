package com.huynhdous.employeefield.checkin;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.media.Images;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.TabModule;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * "Check In" (S2S reps): today's worksite assignments. "I'm Arrived" checks the phone is at the site (GPS), takes a selfie and one or more
 * store photos, and checks in; "I'm done" checks out (also GPS-checked). A finished check-in's photos can be fixed afterwards (see
 * {@link PhotoFix}). The photo steps send the employee to the camera app, which can make Android close this app on a low-memory phone, so
 * the half-finished check-in is saved and restored ({@link #saveState} / {@link #restoreState}).
 */
public final class CheckInTab extends TabModule {
    private static final int REQUEST_CAMERA_PERMISSION = 65;
    private static final int REQUEST_TAKE_SELFIE_PHOTO = 66;
    private static final int REQUEST_TAKE_STORE_PHOTO = 67;
    private static final String STATE_LAT = "arrival_latitude", STATE_LON = "arrival_longitude";
    private static final String STATE_SELFIE_PATH = "pending_selfie_path";
    private static final String STATE_STORE_PATH = "pending_store_path";
    private static final String STATE_ARRIVAL_ASSIGNMENT = "active_arrival_assignment";
    private static final String STATE_ACCEPTED_STORE_PATHS = "accepted_store_paths";
    private static final String STATE_UNSENT = "check_in_unsent";

    private LinearLayout container;
    private TextView message;
    private PhotoFix photoFix;

    // The check-in being made (nothing is sent until the last photo is approved).
    private JSONObject activeAssignment;
    private Double activeLat;
    private Double activeLon;
    private JSONObject activeCheckOutAssignment;
    private android.net.Uri pendingSelfieUri;
    private File pendingSelfieFile;
    private File pendingStorePhotoFile;
    /** Store photos already approved this check-in (not counting the one currently being reviewed). Capped at the app setting. */
    private final java.util.List<File> acceptedStorePhotos = new java.util.ArrayList<>();
    private boolean submitting;
    /** The last attempt to send the check-in failed for lack of a connection: the photos are kept and can be sent again. */
    private boolean unsent;
    private LinearLayout unsentCardView;
    private CheckInDraftStore draftStore;
    private String draftId;

    @Override
    public String title() {
        return "Check In";
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_tab_checkin;
    }

    /** Worksite check-in has no meaning for D2D reps, who don't have a fixed site. */
    @Override
    public boolean isAvailableFor(String programCode) {
        return "S2S".equals(programCode);
    }

    @Override
    public int[] requestCodes() {
        return new int[]{REQUEST_CAMERA_PERMISSION, REQUEST_TAKE_SELFIE_PHOTO, REQUEST_TAKE_STORE_PHOTO, PhotoFix.REQUEST_TAKE_CORRECTION_PHOTO};
    }

    @Override
    public void buildContent(LinearLayout content) {
        float density = density();
        draftStore = new CheckInDraftStore(context(), host().employeeId());
        photoFix = new PhotoFix(host(), this, this::setMessage, this::load);

        LinearLayout headerRow = new LinearLayout(context());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(headerRow);
        TextView heading = new TextView(context());
        heading.setText("Today's Worksite");
        heading.setTextSize(18);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        heading.setTextColor(Theme.TEXT_PRIMARY);
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout refresh = Theme.iconTextButton(context(), R.drawable.ic_refresh, "Refresh", Theme.PRIMARY);
        headerRow.addView(refresh);
        refresh.setOnClickListener(v -> load());

        message = new TextView(context());
        message.setTextSize(13);
        message.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = (int) (4 * density);
        content.addView(message, messageParams);

        container = new LinearLayout(context());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = (int) (12 * density);
        content.addView(container, containerParams);
        Bundle draft = draftStore.load();
        if (draft != null) restoreState(draft);
        showUnsentCardIfAny();
    }

    @Override
    public void onShown() {
        load();
    }

    @Override
    public void onDetach() {
        container = null;
        message = null;
        photoFix = null;
        resetFlow(); // Rotation and sign-out preserve the employee-owned draft and upload inputs.
        activeCheckOutAssignment = null;
    }

    private void setMessage(String text) {
        if (message != null) message.setText(text);
    }

    /** Release references without deleting camera/upload inputs during recreation. */
    private void resetFlow() {
        unsent = false;
        activeAssignment = null;
        activeLat = null;
        activeLon = null;
        pendingSelfieUri = null;
        pendingSelfieFile = null;
        pendingStorePhotoFile = null;
        acceptedStorePhotos.clear();
    }

    /** Discard or complete a check-in only after its upload has released the files. */
    private void clearFlow() {
        if (submitting || com.huynhdous.employeefield.core.session.SessionWork.isBusy(host().token())) {
            setMessage("Please wait for your upload to finish.");
            return;
        }
        CheckInDraftStore storage = storage();
        storage.clear(draftId);
        storage.deletePhoto(pendingSelfieFile);
        storage.deletePhoto(pendingStorePhotoFile);
        for (File photo : acceptedStorePhotos) storage.deletePhoto(photo);
        draftId = null;
        resetFlow();
        showUnsentCardIfAny();
    }

    private CheckInDraftStore storage() {
        if (draftStore == null) draftStore = new CheckInDraftStore(context(), host().employeeId());
        return draftStore;
    }

    /** Record the whole ready-to-send draft before starting any network operation. */
    private void persistReadyDraft() throws IOException {
        CheckInDraftStore storage = storage();
        File oldSelfie = pendingSelfieFile;
        File selfie = storage.keep(oldSelfie);
        java.util.List<File> photos = new java.util.ArrayList<>();
        try {
            for (File photo : acceptedStorePhotos) photos.add(storage.keep(photo));
            if (draftId == null) draftId = java.util.UUID.randomUUID().toString();
            storage.save(draftId, activeAssignment, selfie, photos, activeLat, activeLon);
        } catch (IOException e) {
            // Failed migration must keep the original capture and remove only its new copies.
            if (!selfie.equals(oldSelfie)) storage.deletePhoto(selfie);
            for (int i = 0; i < photos.size(); i++) {
                if (!photos.get(i).equals(acceptedStorePhotos.get(i))) storage.deletePhoto(photos.get(i));
            }
            throw e;
        }
        pendingSelfieFile = selfie;
        pendingSelfieUri = fileUri(selfie);
        if (!selfie.equals(oldSelfie)) storage.deletePhoto(oldSelfie);
        for (int i = 0; i < photos.size(); i++) {
            if (!photos.get(i).equals(acceptedStorePhotos.get(i))) storage.deletePhoto(acceptedStorePhotos.get(i));
        }
        acceptedStorePhotos.clear();
        acceptedStorePhotos.addAll(photos);
    }

    // ---------------------------------------------------------------- saved state (the camera app can close this one)

    @Override
    public void saveState(Bundle out) {
        if (draftId != null) out.putString("check_in_draft_id", draftId);
        if (pendingSelfieFile != null) out.putString(STATE_SELFIE_PATH, pendingSelfieFile.getAbsolutePath());
        if (pendingStorePhotoFile != null) out.putString(STATE_STORE_PATH, pendingStorePhotoFile.getAbsolutePath());
        if (activeAssignment != null) out.putString(STATE_ARRIVAL_ASSIGNMENT, activeAssignment.toString());
        if (!acceptedStorePhotos.isEmpty()) {
            String[] paths = new String[acceptedStorePhotos.size()];
            for (int i = 0; i < paths.length; i++) paths[i] = acceptedStorePhotos.get(i).getAbsolutePath();
            out.putStringArray(STATE_ACCEPTED_STORE_PATHS, paths);
        }
        if (activeLat != null && activeLon != null) {
            out.putDouble(STATE_LAT, activeLat);
            out.putDouble(STATE_LON, activeLon);
        }
        if (unsent) out.putBoolean(STATE_UNSENT, true);
        if (photoFix != null) photoFix.saveState(out);
    }

    @Override
    public void restoreState(Bundle state) {
        Bundle cameraState = state;
        if (draftStore != null) {
            Bundle saved = draftStore.load();
            if (saved != null) { state = new Bundle(state); state.putAll(saved); }
        }
        resetFlow();
        draftId = state.getString("check_in_draft_id");
        String selfiePath = state.getString(STATE_SELFIE_PATH);
        if (selfiePath != null) {
            File f = new File(selfiePath);
            if (f.exists() && f.length() > 0) {
                pendingSelfieFile = f;
                pendingSelfieUri = fileUri(f);
            }
        }
        String storePath = state.getString(STATE_STORE_PATH);
        if (storePath != null) {
            File f = new File(storePath);
            if (f.exists() && f.length() > 0) pendingStorePhotoFile = f;
        }
        String assignmentJson = state.getString(STATE_ARRIVAL_ASSIGNMENT);
        if (assignmentJson != null) {
            try {
                activeAssignment = new JSONObject(assignmentJson);
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
        if (state.containsKey(STATE_LAT) && state.containsKey(STATE_LON)) {
            activeLat = state.getDouble(STATE_LAT);
            activeLon = state.getDouble(STATE_LON);
        }
        unsent = state.getBoolean(STATE_UNSENT, false) && activeAssignment != null && !acceptedStorePhotos.isEmpty();
        if (draftId != null && (pendingSelfieFile == null || acceptedStorePhotos.isEmpty())) resetFlow();
        if (photoFix != null) photoFix.restoreState(cameraState);
    }

    private android.net.Uri fileUri(File f) {
        return androidx.core.content.FileProvider.getUriForFile(context(), context().getPackageName() + ".fileprovider", f);
    }

    // ---------------------------------------------------------------- camera / permission answers

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        // Some camera apps (notably on certain OEMs) don't reliably return RESULT_OK from ACTION_IMAGE_CAPTURE even when the photo was written
        // successfully to our EXTRA_OUTPUT file -- so the file itself is checked instead of trusting resultCode.
        if (requestCode == REQUEST_TAKE_SELFIE_PHOTO) {
            if (pendingSelfieFile != null && pendingSelfieFile.length() > 0) {
                setMessage("");
                showSelfieReviewDialog();
            } else {
                showPhotoFailedDialog("selfie", this::beginSelfie);
            }
        } else if (requestCode == REQUEST_TAKE_STORE_PHOTO) {
            if (pendingStorePhotoFile != null && pendingStorePhotoFile.length() > 0) {
                setMessage("");
                showStorePhotoReviewDialog();
            } else {
                showPhotoFailedDialog("store", this::beginStorePhoto);
            }
        } else if (requestCode == PhotoFix.REQUEST_TAKE_CORRECTION_PHOTO && photoFix != null) {
            photoFix.onCaptured();
        }
    }

    @Override
    public void onPermissionResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_CAMERA_PERMISSION) return;
        if (context().checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) locateThenCheckProximity();
        else setMessage("Camera permission is required to check in.");
    }

    private void showPhotoFailedDialog(String which, Runnable retry) {
        setMessage("");
        new Popup.Builder(context())
                .setTitle("Photo not saved")
                .setMessage("The " + ("selfie".equals(which) ? "selfie" : "store photo") + " didn't save — this can happen with some camera apps. Try again.")
                .setPositiveButton("Retake", (d, w) -> retry.run())
                .setNegativeButton("Cancel", (d, w) -> clearFlow())
                .show();
    }

    // ---------------------------------------------------------------- today's assignments

    private void load() {
        if (container == null) return;
        setMessage("Loading…");
        try {
            host().request("telemapper/arrival/assignments", new JSONObject().put("token", host().token()), null, r -> render(r.getJSONArray("assignments")));
        } catch (Exception ignored) {
        }
    }

    /** Downloads a check-in photo (selfie or one of the store photos) through the token-authenticated photo endpoint and drops it into the
     * given thumbnail once it arrives -- independent of the shared one-call-at-a-time gate so a slow photo never blocks the rest of the screen. */
    private void loadThumbnail(long arrivalId, String which, int position, ImageView target) {
        final String token = host().token();
        new Thread(() -> {
            android.graphics.Bitmap bitmap = null;
            HttpsURLConnection conn = null;
            try {
                String url = Config.API_BASE_URL + "telemapper/arrival/photo?token=" + java.net.URLEncoder.encode(token, "UTF-8")
                        + "&arrival_id=" + arrivalId + "&which=" + which + "&position=" + position;
                conn = (HttpsURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() == 200) {
                    // Shown at about 112 dp, so decode a quarter-size copy instead of the whole 1280 px picture.
                    byte[] data;
                    try (InputStream in = conn.getInputStream()) {
                        data = Api.readAllBytes(in);
                    }
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            android.graphics.Bitmap result = bitmap;
            context().runOnUiThread(() -> {
                if (result != null) target.setImageBitmap(result);
            });
        }).start();
    }

    /** One check-in photo with its name underneath (Selfie / Store front / Store photo 2); tapping it offers retake or delete. */
    private LinearLayout photoTile(long arrivalId, String which, int position, int storeCount, int size, float density) {
        LinearLayout tile = new LinearLayout(context());
        tile.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.WRAP_CONTENT);
        tileParams.rightMargin = (int) (6 * density);
        tile.setLayoutParams(tileParams);
        ImageView thumb = new ImageView(context());
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tile.addView(thumb, new LinearLayout.LayoutParams(size, size));
        TextView caption = new TextView(context());
        caption.setText(PhotoFix.label(which, position));
        caption.setTextSize(12);
        caption.setTypeface(caption.getTypeface(), android.graphics.Typeface.BOLD);
        caption.setTextColor(Theme.TEXT_SECONDARY);
        caption.setPadding(0, (int) (2 * density), 0, 0);
        tile.addView(caption);
        loadThumbnail(arrivalId, which, position, thumb);
        tile.setOnClickListener(v -> photoFix.showActions(arrivalId, which, position, storeCount));
        return tile;
    }

    private void render(JSONArray assignments) throws Exception {
        if (container == null) return;
        container.removeAllViews();
        unsentCardView = null;
        showUnsentCardIfAny();
        if (assignments.length() == 0) {
            message.setText("No worksite assigned for today.");
            return;
        }
        message.setText(assignments.length() + " assignment" + (assignments.length() == 1 ? "" : "s") + " today");
        float density = density();
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject assignment = assignments.getJSONObject(i);

            LinearLayout card = new LinearLayout(context());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            card.setBackground(Theme.cardBackground(context()));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) (8 * density);
            container.addView(card, cardParams);

            TextView siteText = new TextView(context());
            siteText.setText(assignment.getString("site_name"));
            siteText.setTextSize(15);
            siteText.setTypeface(siteText.getTypeface(), android.graphics.Typeface.BOLD);
            siteText.setTextColor(Theme.TEXT_PRIMARY);
            card.addView(siteText);

            String address = assignment.optString("site_address", "");
            if (!address.isEmpty()) {
                TextView addressText = new TextView(context());
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
                TextView checkedInBadge = Theme.statusBadge(context(), badgeText, checkedOut ? Theme.TEXT_SECONDARY : Theme.SUCCESS);
                LinearLayout.LayoutParams checkedInParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                checkedInParams.topMargin = (int) (10 * density);
                card.addView(checkedInBadge, checkedInParams);

                if (!assignment.isNull("arrival_id")) {
                    long arrivalId = assignment.optLong("arrival_id");
                    android.widget.HorizontalScrollView thumbScroll = new android.widget.HorizontalScrollView(context());
                    thumbScroll.setHorizontalScrollBarEnabled(false);
                    LinearLayout.LayoutParams thumbScrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    thumbScrollParams.topMargin = (int) (8 * density);
                    card.addView(thumbScroll, thumbScrollParams);
                    LinearLayout thumbRow = new LinearLayout(context());
                    thumbRow.setOrientation(LinearLayout.HORIZONTAL);
                    thumbScroll.addView(thumbRow);

                    int thumbSize = (int) (112 * density);
                    int storeCount = assignment.optInt("store_photo_count", 0);
                    thumbRow.addView(photoTile(arrivalId, "selfie", 1, storeCount, thumbSize, density));
                    for (int p = 1; p <= storeCount; p++) {
                        thumbRow.addView(photoTile(arrivalId, "store", p, storeCount, thumbSize, density));
                    }

                    // A forgotten or blurry photo can be fixed any time the same day, even after "I'm done".
                    TextView fixHint = new TextView(context());
                    fixHint.setText("Tap a photo to retake or delete it.");
                    fixHint.setTextSize(12);
                    fixHint.setTextColor(Theme.TEXT_SECONDARY);
                    fixHint.setPadding(0, (int) (4 * density), 0, 0);
                    card.addView(fixHint);
                    if (storeCount < Config.CHECKIN_MAX_STORE_PHOTOS) {
                        TextView addPhoto = new TextView(context());
                        addPhoto.setText("+ Add a store photo");
                        addPhoto.setTextSize(14);
                        addPhoto.setTypeface(addPhoto.getTypeface(), android.graphics.Typeface.BOLD);
                        addPhoto.setTextColor(Theme.PRIMARY);
                        addPhoto.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
                        card.addView(addPhoto);
                        addPhoto.setOnClickListener(v -> photoFix.begin(arrivalId, "store", 0));
                    }
                }

                if (!checkedOut) {
                    // Same light "pill" look as the screen-title bar (Check In) instead of a solid
                    // filled button -- a quieter, secondary-feeling action to close out the visit.
                    Button checkOutButton = new Button(context());
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
                    Button arriveAgainButton = Theme.filledButton(context(), "I'm Arrived — Check In", Theme.PRIMARY);
                    LinearLayout.LayoutParams arriveAgainParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    arriveAgainParams.topMargin = (int) (10 * density);
                    card.addView(arriveAgainButton, arriveAgainParams);
                    arriveAgainButton.setOnClickListener(v -> beginArrival(assignment));
                }
            } else {
                Button arriveButton = Theme.filledButton(context(), "I'm Arrived — Check In", Theme.PRIMARY);
                LinearLayout.LayoutParams arriveParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                arriveParams.topMargin = (int) (10 * density);
                card.addView(arriveButton, arriveParams);
                arriveButton.setOnClickListener(v -> beginArrival(assignment));
            }
        }
    }

    // ---------------------------------------------------------------- checking out

    /** Closes out a worksite visit -- a rep with more than one site in a day needs a real way to say
     * "finished here, heading to the next one" instead of the day just showing whichever check-in
     * happened to be last. No photo needed: check-in already proved he was there. */
    private void confirmCheckOut(JSONObject assignment) {
        String siteName = assignment.optString("site_name", "this site");
        new Popup.Builder(context())
                .setTitle("Check out?")
                .setMessage("Mark yourself done at " + siteName + " and on your way?")
                .setPositiveButton("Yes, check out", (d, w) -> beginCheckOut(assignment))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Check-out needs the same GPS proof check-in has -- otherwise "I'm done" could be tapped from
     * anywhere, including a site he never actually left. Captures a fresh fix first, then does a local proximity check against the
     * assignment's own worksite location before submitting, mirroring beginArrival()/checkArrivalProximityAndContinue(). */
    private void beginCheckOut(JSONObject assignment) {
        activeCheckOutAssignment = assignment;
        setMessage("Checking your location…");
        Geo.fetchBestLocation(context(), this::showCheckOutLocationTimeoutDialog, this::checkCheckOutProximityAndSubmit);
    }

    private void showCheckOutLocationTimeoutDialog() {
        setMessage("");
        new Popup.Builder(context())
                .setTitle("Unable to get your location")
                .setMessage("Move outdoors or near a window and try again.")
                .setPositiveButton("Try again", (d, w) -> beginCheckOut(activeCheckOutAssignment))
                .setNegativeButton("Cancel", (d, w) -> activeCheckOutAssignment = null)
                .show();
    }

    private void checkCheckOutProximityAndSubmit(Double lat, Double lon) {
        JSONObject assignment = activeCheckOutAssignment;
        if (assignment == null) return;
        activeCheckOutAssignment = null;
        try {
            if (lat != null && lon != null) {
                double siteLat = assignment.getDouble("latitude");
                double siteLon = assignment.getDouble("longitude");
                int proximity = assignment.optInt("proximity_meters", 150);
                double distance = Geo.metersBetween(siteLat, siteLon, lat, lon);
                if (distance > proximity) {
                    setMessage("");
                    Popup dialog = new Popup.Builder(context())
                            .setCustomTitle(Theme.dialogTitle(context(), "Too far from the worksite", Theme.WARNING))
                            .setMessage("You're about " + Math.round(distance) + "m from " + assignment.getString("site_name") + ". Move closer and try again.")
                            .setPositiveButton("Try again", (d, w) -> beginCheckOut(assignment))
                            .setNegativeButton("Cancel", (d, w) -> setMessage(""))
                            .show();
                    Theme.styleDialog(dialog, Theme.WARNING);
                    return;
                }
            }
            setMessage("");
            submitCheckOut(assignment, lat, lon);
        } catch (Exception e) {
            // A bad/missing field in the assignment JSON shouldn't block checkout entirely --
            // the server re-validates proximity anyway, so just submit with whatever location we have.
            setMessage("");
            submitCheckOut(assignment, lat, lon);
        }
    }

    private void submitCheckOut(JSONObject assignment, Double lat, Double lon) {
        try {
            JSONObject body = new JSONObject().put("token", host().token())
                    .put("assignment_id", assignment.getInt("assignment_id"))
                    .put("source", assignment.getString("source"));
            if (lat != null && lon != null) body.put("latitude", lat).put("longitude", lon);
            host().request("telemapper/arrival/check-out", body, null, r -> load());
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- checking in

    private void beginArrival(JSONObject assignment) {
        if (submitting || com.huynhdous.employeefield.core.session.SessionWork.isBusy(host().token())) {
            setMessage("Please wait for your upload to finish.");
            return;
        }
        if (activeAssignment != null) {
            new Popup.Builder(context()).setTitle("You have a saved check-in")
                    .setMessage("Finish or discard your check-in at " + activeAssignment.optString("site_name", "your worksite")
                            + " before starting another one. Your photos are kept.")
                    .setPositiveButton("Continue saved check-in", (d, w) -> continueDraft())
                    .setNeutralButton("Discard saved check-in", (d, w) -> confirmDiscardDraft())
                    .setNegativeButton("Keep it for later", null).show();
            return;
        }
        activeAssignment = assignment;
        if (context().checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host().requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        // Capture GPS first, before either photo — this confirms he's actually within range
        // right when he taps "I'm Arrived" (not a couple camera round-trips later), and avoids
        // making him take two photos only to be told afterward that he's too far away.
        locateThenCheckProximity();
    }

    private void continueDraft() {
        if (activeAssignment == null) return;
        if (unsent) { retryAfterCheckingStatus(); return; }
        if (context().checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host().requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else if (pendingStorePhotoFile != null && pendingStorePhotoFile.length() > 0) {
            showStorePhotoReviewDialog();
        } else if (pendingSelfieFile != null && pendingSelfieFile.length() > 0) {
            showSelfieReviewDialog();
        } else {
            locateThenCheckProximity();
        }
    }

    private void locateThenCheckProximity() {
        setMessage("Checking your location…");
        Geo.fetchBestLocation(context(), this::showLocationTimeoutDialog, this::checkProximityAndContinue);
    }

    private void showLocationTimeoutDialog() {
        setMessage("");
        new Popup.Builder(context())
                .setTitle("Unable to get your location")
                .setMessage("Move outdoors or near a window and try again.")
                .setPositiveButton("Try again", (d, w) -> locateThenCheckProximity())
                .setNegativeButton("Cancel", (d, w) -> clearFlow())
                .show();
    }

    private void checkProximityAndContinue(Double lat, Double lon) {
        if (activeAssignment == null) {
            setMessage("");
            return;
        }
        try {
            if (lat != null && lon != null) {
                double siteLat = activeAssignment.getDouble("latitude");
                double siteLon = activeAssignment.getDouble("longitude");
                int proximity = activeAssignment.optInt("proximity_meters", 150);
                double distance = Geo.metersBetween(siteLat, siteLon, lat, lon);
                if (distance > proximity) {
                    setMessage("");
                    Popup dialog = new Popup.Builder(context())
                            .setCustomTitle(Theme.dialogTitle(context(), "Too far from the worksite", Theme.WARNING))
                            .setMessage("You're about " + Math.round(distance) + "m from " + activeAssignment.getString("site_name") + ". Move closer and try again.")
                            .setPositiveButton("Try again", (d, which) -> locateThenCheckProximity())
                            .setNegativeButton("Cancel", (d, which) -> {
                                clearFlow();
                                setMessage("");
                            })
                            .show();
                    Theme.styleDialog(dialog, Theme.WARNING);
                    return;
                }
            }
            activeLat = lat;
            activeLon = lon;
            setMessage("✓ Location confirmed.");
            beginSelfie();
        } catch (Exception e) {
            // Previously silently swallowed here, which made every failure in this block
            // (bad JSON field, a dialog that couldn't be shown, anything) look identical to
            // the app doing nothing at all. Surface it instead so a real cause is visible.
            setMessage("");
            final Double retryLat = lat, retryLon = lon;
            new Popup.Builder(context())
                    .setTitle("Check-in error")
                    .setMessage("Something went wrong finishing your check-in (" + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : "") + "). Tap Retry to try again.")
                    .setPositiveButton("Retry", (d, w) -> checkProximityAndContinue(retryLat, retryLon))
                    .setNegativeButton("Cancel", (d, w) -> clearFlow())
                    .show();
        }
    }

    private void beginSelfie() {
        try {
            File photoFile = storage().newPhoto("selfie_");
            storage().deletePhoto(pendingSelfieFile);
            pendingSelfieFile = photoFile;
            pendingSelfieUri = fileUri(photoFile);
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingSelfieUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            new Popup.Builder(context()).setTitle("Selfie").setMessage("Take a quick selfie to confirm it's you — flip to the front camera if needed.")
                    .setPositiveButton("Open camera", (d, w) -> host().startActivityForResult(this, intent, REQUEST_TAKE_SELFIE_PHOTO)).setNegativeButton("Cancel", (d, w) -> clearFlow()).show();
        } catch (Exception e) {
            new Popup.Builder(context()).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    private void beginStorePhoto() {
        try {
            File photoFile = storage().newPhoto("store_");
            storage().deletePhoto(pendingStorePhotoFile);
            pendingStorePhotoFile = photoFile;
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, fileUri(photoFile));
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            new Popup.Builder(context()).setTitle("Store photo").setMessage("Now take a photo showing you're at the store front.")
                    .setPositiveButton("Open camera", (d, w) -> host().startActivityForResult(this, intent, REQUEST_TAKE_STORE_PHOTO)).setNegativeButton("Cancel", (d, w) -> clearFlow()).show();
        } catch (Exception e) {
            new Popup.Builder(context()).setTitle("Camera unavailable").setMessage("Unable to open the camera. Try again.").setPositiveButton("OK", null).show();
        }
    }

    private void showSelfieReviewDialog() {
        if (pendingSelfieFile == null) {
            showPhotoFailedDialog("selfie", this::beginSelfie);
            return;
        }
        float density = density();
        LinearLayout panel = new LinearLayout(context());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * density);
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(Theme.photoPreview(context(), pendingSelfieFile, 160));
        new Popup.Builder(context())
                .setTitle("Selfie captured")
                .setView(panel)
                .setCancelable(false)
                .setPositiveButton("Looks good", (d, w) -> beginStorePhoto())
                .setNegativeButton("Retake", (d, w) -> beginSelfie())
                .show();
    }

    private void showStorePhotoReviewDialog() {
        if (pendingStorePhotoFile == null) {
            showPhotoFailedDialog("store", this::beginStorePhoto);
            return;
        }
        float density = density();
        LinearLayout panel = new LinearLayout(context());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * density);
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(Theme.photoPreview(context(), pendingStorePhotoFile, 160));
        int totalSoFar = acceptedStorePhotos.size() + 1;
        TextView countText = new TextView(context());
        countText.setText(totalSoFar + " of up to " + Config.CHECKIN_MAX_STORE_PHOTOS + " store photos");
        countText.setTextSize(13);
        countText.setTextColor(Theme.TEXT_SECONDARY);
        countText.setPadding(0, (int) (8 * density), 0, 0);
        panel.addView(countText);
        Popup.Builder builder = new Popup.Builder(context())
                .setTitle("Store photo captured")
                .setView(panel)
                .setCancelable(false)
                .setPositiveButton("Done — Submit", (d, w) -> {
                    acceptedStorePhotos.add(pendingStorePhotoFile);
                    pendingStorePhotoFile = null;
                    submit();
                })
                .setNegativeButton("Retake", (d, w) -> beginStorePhoto());
        if (totalSoFar < Config.CHECKIN_MAX_STORE_PHOTOS) {
            builder.setNeutralButton("Add another", (d, w) -> {
                acceptedStorePhotos.add(pendingStorePhotoFile);
                pendingStorePhotoFile = null;
                beginStorePhoto();
            });
        }
        builder.show();
    }

    private void retryAfterCheckingStatus() {
        if (activeAssignment == null) return;
        final JSONObject pending = activeAssignment;
        try {
            host().request("telemapper/arrival/assignments", new JSONObject().put("token", host().token()), null, r -> {
                if (activeAssignment != pending) return; // The draft was discarded while the status read was in flight.
                org.json.JSONArray assignments = r.getJSONArray("assignments");
                for (int i = 0; i < assignments.length(); i++) {
                    JSONObject a = assignments.getJSONObject(i);
                    if (a.getInt("assignment_id") == pending.getInt("assignment_id")
                            && a.optString("source", "worksite").equals(pending.optString("source", "worksite"))) {
                        if (a.optBoolean("checked_in") && !a.optBoolean("checked_out")) {
                            clearFlow();
                            setMessage("");
                            new Popup.Builder(context()).setTitle("Checked in").setMessage("Your check-in did go through the first time.").setPositiveButton("OK", null).show();
                            render(assignments);
                            return;
                        }
                        submit();
                        return;
                    }
                }
                setMessage("The assignment has changed. Please refresh before checking in again.");
            });
        } catch (Exception e) {
            setMessage("Unable to check the status. Your photos are kept; use Send now to try again.");
        }
    }

    private void submit() {
        if (activeAssignment == null || pendingSelfieUri == null || acceptedStorePhotos.isEmpty()) return;
        if (submitting) return;
        if (activeLat == null || activeLon == null) {
            setMessage("Checking your location…");
            Geo.fetchBestLocation(context(), this::showLocationTimeoutDialog, (lat, lon) -> {
                if (activeAssignment == null || context().isDestroyed()) return;
                try {
                    if (Geo.metersBetween(activeAssignment.getDouble("latitude"), activeAssignment.getDouble("longitude"), lat, lon)
                            > activeAssignment.optInt("proximity_meters", 150)) {
                        setMessage("Move closer to the worksite and try again.");
                        return;
                    }
                    activeLat = lat;
                    activeLon = lon;
                    submit();
                } catch (Exception e) { setMessage("Unable to verify the worksite location. Please refresh."); }
            });
            return;
        }
        try {
            persistReadyDraft();
        } catch (IOException e) {
            new Popup.Builder(context()).setTitle("Unable to save check-in")
                    .setMessage("Check device storage and try again. Your captured photos are kept.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        final com.huynhdous.employeefield.core.session.SessionWork.Lease work = host().beginUpload();
        if (work == null) {
            unsent = true;
            showUnsentCardIfAny();
            new Popup.Builder(context()).setTitle("Please wait").setMessage("A request is still finishing. Please try again shortly.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        submitting = true;
        unsent = false;
        showUnsentCardIfAny();
        final boolean[] retryable = {false};
        final JSONObject assignment = activeAssignment;
        final android.net.Uri selfieUri = pendingSelfieUri;
        final File selfieFile = pendingSelfieFile;
        final java.util.List<File> storeFiles = new java.util.ArrayList<>(acceptedStorePhotos);
        final Double lat = activeLat;
        final Double lon = activeLon;
        final String token = work.token;
        setMessage("Checking in…");
        new Thread(() -> {
            String error = null;
            HttpsURLConnection conn = null;
            try {
                // Shrunk on the phone first (see Images.photoBytesForUpload): a few hundred KB each instead of the camera's 5-15 MB.
                byte[] selfieBytes = selfieFile != null && selfieFile.exists()
                        ? Images.photoBytesForUpload(selfieFile)
                        : Api.readAllBytes(context().getContentResolver().openInputStream(selfieUri));
                if (selfieBytes.length > 8 * 1024 * 1024) throw new IOException("TOO_LARGE");
                java.util.List<byte[]> storeBytesList = new java.util.ArrayList<>();
                for (File f : storeFiles) {
                    byte[] bytes = Images.photoBytesForUpload(f);
                    if (bytes.length > 8 * 1024 * 1024) throw new IOException("TOO_LARGE");
                    storeBytesList.add(bytes);
                }
                String boundary = "----EmployeeFieldBoundary" + System.currentTimeMillis();
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + "telemapper/arrival/submit").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = conn.getOutputStream()) {
                    Api.writeMultipartField(out, boundary, "token", token);
                    Api.writeMultipartField(out, boundary, "assignment_id", String.valueOf(assignment.getInt("assignment_id")));
                    Api.writeMultipartField(out, boundary, "source", assignment.optString("source", "worksite"));
                    if (lat != null && lon != null) {
                        Api.writeMultipartField(out, boundary, "latitude", String.valueOf(lat));
                        Api.writeMultipartField(out, boundary, "longitude", String.valueOf(lon));
                    }
                    Api.writeMultipartFile(out, boundary, "selfie", "selfie.jpg", "image/jpeg", selfieBytes);
                    for (int i = 0; i < storeBytesList.size(); i++) {
                        Api.writeMultipartFile(out, boundary, "store_photo[]", "store_" + (i + 1) + ".jpg", "image/jpeg", storeBytesList.get(i));
                    }
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) throw new IOException();
                JSONObject resp = new JSONObject(new String(Api.readAllBytes(stream), StandardCharsets.UTF_8));
                if (code < 200 || code >= 300 || !resp.optBoolean("success")) {
                    error = resp.optString("message", "Unable to check in.");
                    retryable[0] = code >= 500;   // a refusal (too far, already checked in ...) will not change by retrying; a server fault might
                }
            } catch (Exception e) {
                retryable[0] = !"TOO_LARGE".equals(e.getMessage());   // a connection problem can be retried; a photo that is too large cannot
                error = "TOO_LARGE".equals(e.getMessage()) ? "That photo is too large." : "Unable to check in. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
                work.close();
            }
            String problem = error;
            context().runOnUiThread(() -> finishSubmission(work, problem, retryable[0]));
        }).start();
    }
    /** The upload ended with {@code problem} (null = it worked). Treated as retryable, i.e. the photos are kept. */
    void finishSubmission(com.huynhdous.employeefield.core.session.SessionWork.Lease work, String problem) {
        finishSubmission(work, problem, true);
    }

    void finishSubmission(com.huynhdous.employeefield.core.session.SessionWork.Lease work, String problem, boolean retryable) {
        submitting = false;
        if (draftStore != null && (problem == null || !retryable)) draftStore.clear(draftId);
        if (!host().isCurrent(work)) {
            // The screen was recreated (a rotation) while this was uploading: the screen that replaced it shows the result.
            host().leaveOutcome(work, new com.huynhdous.employeefield.core.session.SessionWork.Outcome("checkin", problem == null, retryable, problem, draftId));
            return;
        }
        showResult(problem == null, retryable, problem);
    }

    /** What a check-in upload ended with: done; refused or hopeless (start over); or only a connection problem (the photos are kept). */
    private void showResult(boolean success, boolean retryable, String problem) {
        setMessage("");
        if (success) {
            clearFlow();
            new Popup.Builder(context()).setTitle("Checked in").setMessage("You're checked in. Have a great shift!").setPositiveButton("OK", null).show();
            load();
        } else if (!retryable || activeAssignment == null || acceptedStorePhotos.isEmpty()) {
            clearFlow();   // the server said no (or a photo is too large, or the photos are gone): starting over is the only way
            showUnsentCardIfAny();
            new Popup.Builder(context()).setTitle("Check in").setMessage(problem).setPositiveButton("OK", null).show();
        } else {
            unsent = true;   // a connection problem: the photos and the check-in details stay, and nothing has to be retaken
            showUnsentDialog(problem);
        }
    }

    @Override
    public void onUploadOutcome() {
        if (container == null) return;
        String token = host().token();
        for (com.huynhdous.employeefield.core.session.SessionWork.Outcome o : com.huynhdous.employeefield.core.session.SessionWork.takeOutcomes(token, "checkin")) {
            if (o.reference != null && !o.reference.equals(draftId)) continue;
            showResult(o.success, o.retryable, o.message);
        }
        if (photoFix != null) photoFix.takeOutcomes();
    }

    // ---------------------------------------------------------------- a check-in that could not be sent

    private void showUnsentDialog(String problem) {
        showUnsentCardIfAny();
        new Popup.Builder(context())
                .setTitle("Check-in not sent")
                .setMessage(problem + "\n\nYour photos are saved on this phone, so nothing has to be retaken. Try again now, or later from the card at the top of this screen.")
                .setPositiveButton("Try again", (d, w) -> retryAfterCheckingStatus())
                .setNegativeButton("Later", null)
                .show();
    }

    private void confirmDiscardDraft() {
        new Popup.Builder(context()).setTitle("Discard this check-in?")
                .setMessage("The photos will be deleted from this phone and you will have to check in again from the start.")
                .setPositiveButton("Discard", (d, w) -> clearFlow())
                .setNegativeButton("Keep it", null).show();
    }

    /** Puts (or removes) the "check-in waiting to be sent" card at the top of the screen, so the retry is never out of reach. */
    private void showUnsentCardIfAny() {
        if (container == null) return;
        if (unsentCardView != null) {
            container.removeView(unsentCardView);
            unsentCardView = null;
        }
        if (!unsent || activeAssignment == null) return;
        boolean busy = submitting || com.huynhdous.employeefield.core.session.SessionWork.isBusy(host().token());
        float density = density();
        LinearLayout card = new LinearLayout(context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        card.setBackground(Theme.cardBackground(context()));
        TextView title = new TextView(context());
        title.setText("Check-in waiting to be sent");
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(Theme.WARNING);
        card.addView(title);
        TextView text = new TextView(context());
        text.setText(activeAssignment.optString("site_name", "Your worksite") + " — your photos are saved on this phone. Send it when you have a signal.");
        text.setTextSize(13);
        text.setTextColor(Theme.TEXT_SECONDARY);
        text.setPadding(0, (int) (4 * density), 0, (int) (8 * density));
        card.addView(text);
        LinearLayout buttons = new LinearLayout(context());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button send = Theme.filledButton(context(), "Send now", Theme.PRIMARY);
        buttons.addView(send);
        send.setEnabled(!busy);
        send.setOnClickListener(v -> retryAfterCheckingStatus());
        TextView discard = new TextView(context());
        discard.setText("Discard");
        discard.setTextSize(14);
        discard.setTypeface(discard.getTypeface(), android.graphics.Typeface.BOLD);
        discard.setTextColor(Theme.TEXT_SECONDARY);
        discard.setMinimumHeight((int) (48 * density));
        discard.setGravity(Gravity.CENTER_VERTICAL);
        discard.setPadding((int) (18 * density), 0, (int) (18 * density), 0);
        discard.setClickable(true);
        discard.setEnabled(!busy);
        buttons.addView(discard);
        discard.setOnClickListener(v -> confirmDiscardDraft());
        card.addView(buttons);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * density);
        container.addView(card, 0, params);
        unsentCardView = card;
    }
}
