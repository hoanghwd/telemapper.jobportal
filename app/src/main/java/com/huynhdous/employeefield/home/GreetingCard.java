package com.huynhdous.employeefield.home;

import android.app.Activity;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.R;
import com.huynhdous.employeefield.core.media.Avatars;
import com.huynhdous.employeefield.core.session.Session;
import com.huynhdous.employeefield.core.ui.Theme;

/** The card at the top of the home screen: a row with the menu button, profile photo and sign-out button, then "Welcome, name" and who is signed in. */
final class GreetingCard {
    final LinearLayout view;
    private final Activity activity;
    private final Session session;
    private final ImageView avatar;
    private final com.huynhdous.employeefield.inbox.InboxActions inbox;

    GreetingCard(Activity activity, Session session, Runnable openMenu, Runnable signOut, com.huynhdous.employeefield.core.tab.AppHost host, Runnable dispatchChanged) {
        this.activity = activity;
        this.session = session;
        inbox = new com.huynhdous.employeefield.inbox.InboxActions(activity, session.employeeId);
        inbox.setAnswer((id, decision, done) -> {
            try { host.request("dispatch/respond", new org.json.JSONObject().put("token", host.token()).put("offer_id", id).put("decision", decision), null, response -> { done.run(); dispatchChanged.run(); }); }
            catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        });
        float density = activity.getResources().getDisplayMetrics().density;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
        view.setBackground(Theme.cardBackground(activity));

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton menuButton = Theme.iconButton(activity, R.drawable.ic_menu_hamburger, Theme.NEUTRAL, "Menu");
        int buttonSize = (int) (48 * density);
        LinearLayout.LayoutParams menuButtonParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        menuButtonParams.rightMargin = (int) (8 * density);
        headerRow.addView(menuButton, menuButtonParams);
        menuButton.setOnClickListener(v -> openMenu.run());

        avatar = Theme.circularAvatar(activity, 44);
        int avatarSize = (int) (44 * density);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.rightMargin = (int) (2 * density);
        headerRow.addView(avatar, avatarParams);
        refreshAvatar();

        // The space between the photo and the sign-out button; the name no longer squeezes in here.
        headerRow.addView(new android.view.View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

        inbox.attach(headerRow);
        inbox.setWorkOffersEnabled(false);
        try {
            host.request("dispatch/status", new org.json.JSONObject().put("token", session.token), null,
                    response -> inbox.setWorkOffersEnabled(response.optBoolean("dispatch_enabled", false)), true, null);
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }

        ImageButton signOutButton = Theme.iconButton(activity, R.drawable.ic_logout, Theme.NEUTRAL, "Sign out");
        LinearLayout.LayoutParams signOutParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        signOutParams.leftMargin = (int) (8 * density); // a gap so the round buttons do not touch
        headerRow.addView(signOutButton, signOutParams);
        signOutButton.setOnClickListener(v -> signOut.run());
        view.addView(headerRow);

        // "Welcome, name" sits on its own line, right above "Signed in as ...", so a long name no longer wraps beside the photo.
        TextView nameText = new TextView(activity);
        nameText.setText("Welcome, " + session.employeeName);
        nameText.setTextSize(20);
        nameText.setTextColor(Theme.TEXT_PRIMARY);
        nameText.setPadding(0, (int) (12 * density), 0, 0);
        view.addView(nameText);

        TextView signedInAs = new TextView(activity);
        signedInAs.setText("Signed in as " + session.username + ".");
        signedInAs.setTextSize(13);
        signedInAs.setTextColor(Theme.TEXT_SECONDARY);
        signedInAs.setPadding(0, (int) (4 * density), 0, 0);
        view.addView(signedInAs);
    }

    void close() { inbox.close(); }

    void receiveWorkOffer(String id, String title, String body) {
        inbox.receiveWorkOffer(id, title, body);
    }

    /** The profile photo was changed: load the new one. */
    void refreshAvatar() {
        Avatars.load(activity, session.token, avatar);
    }
}
