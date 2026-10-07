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

/** The card at the top of the home screen: menu button, profile photo, "Welcome, name", sign-out button and who is signed in. */
final class GreetingCard {
    final LinearLayout view;
    private final Activity activity;
    private final Session session;
    private final ImageView avatar;

    GreetingCard(Activity activity, Session session, Runnable openMenu, Runnable signOut) {
        this.activity = activity;
        this.session = session;
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
        menuButtonParams.rightMargin = (int) (10 * density);
        headerRow.addView(menuButton, menuButtonParams);
        menuButton.setOnClickListener(v -> openMenu.run());

        avatar = Theme.circularAvatar(activity, 44);
        int avatarSize = (int) (44 * density);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.rightMargin = (int) (12 * density);
        headerRow.addView(avatar, avatarParams);
        refreshAvatar();

        TextView nameText = new TextView(activity);
        nameText.setText("Welcome, " + session.employeeName);
        nameText.setTextSize(20);
        nameText.setTextColor(Theme.TEXT_PRIMARY);
        headerRow.addView(nameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton signOutButton = Theme.iconButton(activity, R.drawable.ic_logout, Theme.NEUTRAL, "Sign out");
        headerRow.addView(signOutButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        signOutButton.setOnClickListener(v -> signOut.run());
        view.addView(headerRow);

        TextView signedInAs = new TextView(activity);
        signedInAs.setText("Signed in as " + session.username + ".");
        signedInAs.setTextSize(13);
        signedInAs.setTextColor(Theme.TEXT_SECONDARY);
        signedInAs.setPadding(0, (int) (4 * density), 0, 0);
        view.addView(signedInAs);
    }

    /** The profile photo was changed: load the new one. */
    void refreshAvatar() {
        Avatars.load(activity, session.token, avatar);
    }
}
