package com.huynhdous.employeefield.core.ui;

import com.huynhdous.employeefield.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** A modern centred card for "what do you want to do with this?" choices -- rounded corners, one big
 * tappable row per action (icon + label), and a Cancel pill -- instead of the plain list in a stock AlertDialog. */
public final class ActionSheet {
    private ActionSheet() {
    }

    public static final class Action {
        public final String label;
        public final int iconRes;
        public final boolean destructive;
        public final Runnable run;

        public Action(String label, int iconRes, boolean destructive, Runnable run) {
            this.label = label;
            this.iconRes = iconRes;
            this.destructive = destructive;
            this.run = run;
        }
    }

    public static void show(Activity activity, String title, String subtitle, List<Action> actions) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Theme.SURFACE);
        sheetBg.setCornerRadius(24 * density);
        sheet.setBackground(sheetBg);
        int side = (int) (22 * density);
        sheet.setPadding(side, (int) (22 * density), side, (int) (18 * density));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(Theme.TEXT_PRIMARY);
        sheet.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(activity);
            sub.setText(subtitle);
            sub.setTextSize(14);
            sub.setTextColor(Theme.TEXT_SECONDARY);
            sub.setPadding(0, (int) (2 * density), 0, 0);
            sheet.addView(sub);
        }

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = (int) (12 * density);
        sheet.addView(list, listParams);
        for (Action action : actions) list.addView(row(activity, action, dialog, density));

        TextView cancel = new TextView(activity);
        cancel.setText("Cancel");
        cancel.setTextSize(15);
        cancel.setTypeface(cancel.getTypeface(), Typeface.BOLD);
        cancel.setTextColor(Theme.TEXT_SECONDARY);
        cancel.setGravity(Gravity.CENTER);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.argb(20, 0, 0x3f, 0xb1));
        cancelBg.setCornerRadius(14 * density);
        cancel.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22003FB1), cancelBg, null));
        cancel.setClickable(true);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (50 * density));
        cancelParams.topMargin = (int) (8 * density);
        sheet.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> dialog.dismiss());

        // Floats in the middle of the screen with a margin on each side, like the app's other pop-ups.
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
        int margin = (int) (24 * density);
        frame.setPadding(margin, 0, margin, 0);
        frame.addView(sheet, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(frame);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.45f);
        }
        dialog.show();
    }

    private static View row(Activity activity, Action action, Dialog dialog, float density) {
        int color = action.destructive ? Theme.ERROR : Theme.PRIMARY;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding((int) (4 * density), (int) (10 * density), (int) (4 * density), (int) (10 * density));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(12 * density);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1A000000), null, mask));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(action.iconRes);
        icon.setColorFilter(color);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = (int) (10 * density);
        icon.setPadding(pad, pad, pad, pad);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)));
        icon.setBackground(iconBg);
        row.addView(icon, new LinearLayout.LayoutParams((int) (44 * density), (int) (44 * density)));

        TextView label = new TextView(activity);
        label.setText(action.label);
        label.setTextSize(16);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setTextColor(action.destructive ? Theme.ERROR : Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = (int) (14 * density);
        row.addView(label, labelParams);

        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run.run();
        });
        return row;
    }
}
