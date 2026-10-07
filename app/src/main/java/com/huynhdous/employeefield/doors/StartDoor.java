package com.huynhdous.employeefield.doors;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.config.Config;
import com.huynhdous.employeefield.core.location.Geo;
import com.huynhdous.employeefield.core.net.Api;
import com.huynhdous.employeefield.core.tab.AppHost;
import com.huynhdous.employeefield.core.tab.Tabs;
import com.huynhdous.employeefield.core.ui.Popup;
import com.huynhdous.employeefield.core.ui.Theme;
import com.huynhdous.employeefield.schedule.DayClock;
import com.huynhdous.employeefield.trip.TripMap;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * Starting a door: "I'm at the door" on a map dot, or "Start a New Door Here" (a fresh GPS fix turned into a dot). Checks the house was
 * not already sold / do-not-call, confirms the address (and an optional business name) with the rep, then starts the visit on the server.
 */
final class StartDoor {
    private final DoorsTab tab;
    private final AppHost host;
    private final Activity activity;

    /** The house the rep typed when GPS couldn't tell neighbours apart; pre-fills the start dialog so the
     * server resolves the same house at start as it did at check time. Cleared at the start of each attempt. */
    private String pendingConfirmedAddress;
    // Optional business name typed in the "Confirm the house" dialog: carried through the "Fix address" round trip and sent with the start request.
    private String pendingConfirmedBusiness;
    private String doorBusinessName = "";

    StartDoor(DoorsTab tab) {
        this.tab = tab;
        this.host = tab.app();
        this.activity = host.activity();
    }

    /** The "Start a New Door Here" fallback for when no trail dot happens to be near the house --
     * trail dots are just periodic GPS samples, not one-per-house, so relying on tapping one alone
     * leaves some addresses impossible to start. This captures a fresh position on the spot instead. */
    void beginStartNewDoorHere() {
        if (tab.activeDispositionId != null) {
            new Popup.Builder(activity).setTitle("Door in progress").setMessage("Finish your current door before starting a new one.").setPositiveButton("OK", null).show();
            return;
        }
        if (!Geo.hasLocationPermission(activity)) {
            host.requestPermissions(tab, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, DoorsTab.REQUEST_START_HERE_LOCATION_PERMISSION);
            return;
        }
        tab.say("Getting your location…");
        Geo.fetchBestFix(activity,
                () -> tab.say("Unable to get your location. Move outdoors or near a window and try again."),
                fix -> createLocationPointThenConfirm(fix.getLatitude(), fix.getLongitude(), fix.getAccuracy()));
    }

    /** Turns a freshly-read GPS fix into a real dot on the map (a real employee_location_points
     * row), then continues into the exact same "I'm at the door" flow a tapped trail dot would —
     * one mechanism for starting a door, whether the dot already existed or was just forced into
     * existence here. */
    private void createLocationPointThenConfirm(double lat, double lon, float accuracy) {
        tab.messageText.setText("Creating a position here…");
        new Thread(() -> {
            Long newPointId = null;
            String error = null;
            HttpsURLConnection conn = null;
            try {
                JSONObject body = new JSONObject().put("token", host.token()).put("latitude", lat).put("longitude", lon).put("accuracy_m", accuracy);
                conn = (HttpsURLConnection) new URL(Config.API_BASE_URL + "telemapper/disposition/create-point").openConnection();
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
                JSONObject resp = new JSONObject(new String(Api.readAllBytes(stream), StandardCharsets.UTF_8));
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
            activity.runOnUiThread(() -> {
                tab.renderIdle();
                if (problem != null) {
                    new Popup.Builder(activity).setTitle("Unable to start").setMessage(problem).setPositiveButton("OK", null).show();
                    return;
                }
                tab.loadDoorsMap();
                confirmStartDoorAtPoint(pointId, lat, lon);
            });
        }).start();
    }

    void confirmStartDoorAtPoint(Long pointId, double lat, double lon) {
        if (tab.activeDispositionId != null) {
            new Popup.Builder(activity).setTitle("Door in progress").setMessage("Finish your current door before starting a new one.").setPositiveButton("OK", null).show();
            return;
        }
        pendingConfirmedAddress = null;
        pendingConfirmedBusiness = null;
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
        checkDuplicateThenShowStartDialog(pointId, lat, lon, null);
    }

    /** $houseNumber is set only after the rep was asked to type the house they're standing at (the server
     * answers "ambiguous" when GPS can't tell nearby houses apart and one of them is already closed out);
     * the server then checks that house instead of guessing from coordinates. */
    private void checkDuplicateThenShowStartDialog(Long pointId, double lat, double lon, String houseNumber) {
        final String currentToken = host.token();
        new Thread(() -> {
            JSONObject result = null;
            boolean checkFailed = false;
            try {
                JSONObject body = new JSONObject().put("token", currentToken).put("latitude", lat).put("longitude", lon);
                if (pointId != null) body.put("location_point_id", (long) pointId);
                if (houseNumber != null) body.put("house_number", houseNumber);
                result = Api.post("telemapper/disposition/check-location", body);
            } catch (Exception e) {
                checkFailed = true;
            }
            JSONObject finalResult = result;
            boolean finalFailed = checkFailed;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (finalFailed) {
                    showCouldNotVerifyDialog(pointId, lat, lon);
                } else if (finalResult.optBoolean("ambiguous", false)) {
                    showEnterHouseNumberDialog(pointId, lat, lon, null);
                } else if (finalResult.optBoolean("house_not_found", false)) {
                    showEnterHouseNumberDialog(pointId, lat, lon, "No house numbered " + houseNumber + " was found near you. Check the number and try again.");
                } else {
                    String confirmed = finalResult.optString("house_address", "").trim();
                    pendingConfirmedAddress = confirmed.isEmpty() ? null : confirmed;
                    if (finalResult.optBoolean("duplicate", false)) showDuplicateDoorWarning(finalResult, pointId, lat, lon);
                    else showStartDoorDialog(pointId, lat, lon);
                }
            });
        }).start();
    }

    /** GPS error on a phone is often bigger than the gap between neighbouring houses, so when the server can't
     * tell which house the rep is at (and one of the candidates was already sold / do-not-call) it must not guess
     * -- the rep types the house number they're physically standing at, and that house is what gets checked. */
    private void showEnterHouseNumberDialog(Long pointId, double lat, double lon, String problem) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * density);
        container.setPadding(pad, (int) (8 * density), pad, (int) (4 * density));

        TextView message = new TextView(activity);
        message.setText((problem != null ? problem + "\n\n" : "") + "Your phone's GPS can't tell which of the nearby houses you're at. Enter the house number you are standing at to confirm.");
        message.setTextColor(Theme.TEXT_SECONDARY);
        message.setTextSize(14);
        container.addView(message);

        EditText numberField = new EditText(activity);
        numberField.setHint("House number (e.g. 9652)");
        Theme.styleInput(numberField);
        container.addView(numberField);

        Popup.Builder builder = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Which house are you at?", Theme.WARNING))
                .setView(container)
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", null);
        // A number that matched nothing nearby may be a house that simply isn't on the lead list.
        if (problem != null) builder.setNeutralButton("Not on the list", (d, w) -> showStartDoorDialog(pointId, lat, lon));
        Popup dialog = builder.show();
        Theme.styleDialog(dialog, Theme.WARNING);
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = numberField.getText().toString().trim();
            if (typed.isEmpty()) {
                numberField.setError("Enter the house number");
                return;
            }
            dialog.dismiss();
            checkDuplicateThenShowStartDialog(pointId, lat, lon, typed);
        });
    }

    private void showCouldNotVerifyDialog(Long pointId, double lat, double lon) {
        new Popup.Builder(activity)
                .setTitle("Could not verify household status")
                .setMessage("Unable to check whether this house was already sold or marked do-not-call. Check your connection and retry, or continue without checking -- it'll still be verified when this door is finished and uploaded.")
                .setPositiveButton("Retry", (d, w) -> checkDuplicateThenShowStartDialog(pointId, lat, lon))
                .setNeutralButton("Continue Without Checking", (d, w) -> showStartDoorDialog(pointId, lat, lon))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDuplicateDoorWarning(JSONObject lead, Long pointId, double lat, double lon) {
        String address = lead.optString("address", "").trim();
        String statusLabel = DoorStatus.label(lead.optString("status", ""));
        String when = "";
        try {
            java.time.Instant instant = java.time.Instant.parse(lead.getString("status_updated_utc").replace(' ', 'T') + "Z");
            when = " on " + java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(java.time.ZoneId.systemDefault()).format(instant);
        } catch (Exception ignored) {
        }
        // Do Not Call is a hard stop, not a warning: the server rejects starting this door anyway
        // (D2dDisposition::startForEmployeeToken()), so offering "Continue Anyway" would only lead to an error.
        if ("do_not_call".equals(lead.optString("status", ""))) {
            Popup blocked = new Popup.Builder(activity)
                    .setCustomTitle(Theme.dialogTitle(activity, "Do Not Call", Theme.ERROR))
                    .setMessage((address.isEmpty() ? "This house" : address) + " was marked Do Not Call" + when + ".\n\nDo not knock this door. If the earlier outcome was a mistake, ask your manager to correct it.")
                    .setPositiveButton("OK", null)
                    .show();
            Theme.styleDialog(blocked, Theme.ERROR);
            return;
        }
        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Already " + statusLabel, Theme.WARNING))
                .setMessage((address.isEmpty() ? "This house" : address) + " was already marked " + statusLabel + when + ". Knocking again may annoy the homeowner.\n\nOnly continue if this is genuinely a different unit, or the earlier outcome was wrong.")
                .setPositiveButton("Continue Anyway", (d, w) -> showStartDoorDialog(pointId, lat, lon))
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.WARNING);
    }

    private void showStartDoorDialog(Long pointId, double lat, double lon) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * density);
        container.setPadding(pad, 0, pad, (int) (4 * density));

        // The address comes from a GPS lookup, which can name the neighbouring house -- the rep, standing at the
        // door, is the only one who can say whether it's right, so starting is an explicit confirmation of it.
        TextView confirmPrompt = new TextView(activity);
        confirmPrompt.setText("Is this the house you're standing at? Check the number on the door and correct it if it's wrong, then confirm.");
        confirmPrompt.setTextColor(Theme.TEXT_SECONDARY);
        confirmPrompt.setTextSize(14);
        confirmPrompt.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
        container.addView(confirmPrompt);

        EditText addressField = new EditText(activity);
        addressField.setHint("Looking up address…");
        Theme.styleInput(addressField);
        container.addView(addressField);

        final String confirmedAddress = pendingConfirmedAddress;
        pendingConfirmedAddress = null;
        if (confirmedAddress != null) addressField.setText(confirmedAddress);

        // Only for a business: a home is simply left blank.
        EditText businessField = new EditText(activity);
        businessField.setHint("Business name (optional)");
        businessField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        businessField.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(150)});
        Theme.styleInput(businessField);
        container.addView(businessField);
        final String confirmedBusiness = pendingConfirmedBusiness;
        pendingConfirmedBusiness = null;
        if (confirmedBusiness != null) businessField.setText(confirmedBusiness);

        Popup dialog = new Popup.Builder(activity)
                .setCustomTitle(Theme.dialogTitle(activity, "Confirm the house", Theme.PRIMARY))
                .setView(container)
                .setPositiveButton("Confirm & Start", null)
                .setNeutralButton("Look Up", null)
                .setNegativeButton("Cancel", null)
                .show();
        Theme.styleDialog(dialog, Theme.PRIMARY);
        dialog.getButton(Popup.BUTTON_NEUTRAL).setOnClickListener(v -> {
            addressField.setText("");
            addressField.setHint("Looking up address…");
            lookupAddress(lat, lon, addressField);
        });
        dialog.getButton(Popup.BUTTON_POSITIVE).setOnClickListener(v -> {
            String address = addressField.getText().toString().trim();
            if (address.isEmpty()) {
                addressField.setError("Enter the house address");
                return;
            }
            doorBusinessName = businessField.getText().toString().trim();
            // The reverse case of the lunch guard: starting a sale while still clocked in on lunch
            // would leave the timesheet and the day's activity contradicting each other.
            confirmNotOnLunchThenStartDoor(pointId, lat, lon, address, dialog);
        });
        if (confirmedAddress == null) lookupAddress(lat, lon, addressField);
    }

    private void confirmNotOnLunchThenStartDoor(Long pointId, double lat, double lon, String address, Popup sourceDialog) {
        String today = java.time.LocalDate.now().toString();
        try {
            host.request("timeclock/day", new JSONObject().put("token", host.token()).put("work_date", today), null, r -> {
                String state = DayClock.finalDayState(r.getJSONArray("events"));
                if ("lunch".equals(state) || "break".equals(state)) {
                    new Popup.Builder(activity)
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
            }, true, null);
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
                String url = Config.reverseGeocodeUrl(lat, lon);
                conn = (HttpsURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "EmployeeFieldApp/1.0 (huynhdous.com)");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() == 200) {
                    JSONObject resp = new JSONObject(new String(Api.readAllBytes(conn.getInputStream()), StandardCharsets.UTF_8));
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
            activity.runOnUiThread(() -> {
                if (result != null && target.getText().toString().isEmpty()) {
                    target.setText(result);
                } else {
                    target.setHint("House number or address");
                }
            });
        }).start();
    }

    private void submitStartDoor(Long pointId, double lat, double lon, String address) {
        submitStartDoor(pointId, lat, lon, address, false);
    }

    /** $unlistedHouse is true only after the server said this address isn't on the lead list near the rep and the
     * rep explicitly confirmed it's a new house -- the server never quietly attributes the visit to the nearest
     * listed house instead (that's how a neighbour gets marked sold). */
    private void submitStartDoor(Long pointId, double lat, double lon, String address, boolean unlistedHouse) {
        try {
            JSONObject body = new JSONObject().put("token", host.token());
            if (pointId != null) {
                body.put("location_point_id", (long) pointId);
            } else {
                body.put("latitude", lat).put("longitude", lon);
            }
            if (!address.isEmpty()) body.put("address", address);
            if (!doorBusinessName.isEmpty()) body.put("business_name", doorBusinessName);
            if (unlistedHouse) body.put("unlisted_house", true);
            host.request("telemapper/disposition/start", body, null, r -> {
                tab.activeDispositionId = r.getInt("disposition_id");
                tab.activeDoorStartedMs = java.time.Instant.parse(r.getString("created_utc").replace(' ', 'T') + "Z").toEpochMilli();
                tab.activeDoorLat = r.getDouble("latitude");
                tab.activeDoorLon = r.getDouble("longitude");
                String shownAddress = address.isEmpty() ? "Door at recorded position" : address;
                tab.renderActive(doorBusinessName.isEmpty() ? shownAddress : shownAddress + "\n" + doorBusinessName);
                host.selectTab(Tabs.DOORS);
                tab.loadDoorsMap();
            }, false, (status, problem) -> {
                if (status != 409) return false;
                new Popup.Builder(activity)
                        .setCustomTitle(Theme.dialogTitle(activity, "House not on the list", Theme.WARNING))
                        .setMessage(problem)
                        .setPositiveButton("It's a new house", (d, w) -> submitStartDoor(pointId, lat, lon, address, true))
                        .setNegativeButton("Fix address", (d, w) -> {
                            pendingConfirmedAddress = address;
                            pendingConfirmedBusiness = doorBusinessName;
                            showStartDoorDialog(pointId, lat, lon);
                        })
                        .show();
                return true;
            });
        } catch (Exception ignored) {
        }
    }

}
