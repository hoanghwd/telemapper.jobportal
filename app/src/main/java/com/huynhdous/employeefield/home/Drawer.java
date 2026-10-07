package com.huynhdous.employeefield.home;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.huynhdous.employeefield.core.ui.Insets;
import com.huynhdous.employeefield.core.ui.Theme;

import java.util.List;

/** The slide-out menu that sits on top of the home screen, with a dark scrim behind it. */
final class Drawer {
    private final Activity activity;
    private LinearLayout panel;
    private View scrim;
    private int widthPx;

    Drawer(Activity activity) {
        this.activity = activity;
    }

    /** Wraps what is already on screen (the home panel) so the menu can slide over it, and fills the menu with {@code rows}. */
    void install(List<View> rows) {
        float density = activity.getResources().getDisplayMetrics().density;
        widthPx = (int) (280 * density);

        ViewGroup decorContent = activity.findViewById(android.R.id.content);
        View content = decorContent.getChildAt(0);
        decorContent.removeView(content);

        FrameLayout root = new FrameLayout(activity);
        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scrim = new View(activity);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Theme.SURFACE);
        panel.setElevation(16 * density);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(widthPx, FrameLayout.LayoutParams.MATCH_PARENT);
        panelParams.gravity = Gravity.START;
        root.addView(panel, panelParams);
        panel.setTranslationX(-widthPx);
        Insets.apply(panel);

        TextView title = new TextView(activity);
        title.setText("Menu");
        title.setTextSize(20);
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding((int) (18 * density), (int) (24 * density), (int) (18 * density), (int) (16 * density));
        panel.addView(title);
        for (View row : rows) panel.addView(row);

        activity.setContentView(root);
    }

    void open() {
        if (panel == null) return;
        scrim.setVisibility(View.VISIBLE);
        panel.animate().translationX(0).setDuration(200).start();
    }

    void close() {
        if (panel == null || panel.getTranslationX() == -widthPx) return;
        panel.animate().translationX(-widthPx).setDuration(200).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
    }

    boolean isOpen() {
        return panel != null && panel.getTranslationX() != -widthPx;
    }
}
