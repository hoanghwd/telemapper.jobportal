package com.huynhdous.employeefield.core.ui;

import com.huynhdous.employeefield.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colors and small styled-view helpers borrowed from the Tracko app's "Corporate Modernism" design system. */
public final class Theme {
    private Theme() {
    }

    public static final int PRIMARY = 0xff003fb1;
    public static final int PRIMARY_LIGHT = 0xff1a56db;
    public static final int SUCCESS = 0xff006e2f;
    public static final int WARNING = 0xfff59e0b;
    public static final int ERROR = 0xffba1a1a;
    public static final int NEUTRAL = 0xff737686;
    public static final int BACKGROUND = 0xfff8f9ff;
    public static final int SURFACE = 0xffffffff;
    public static final int TEXT_PRIMARY = 0xff0b1c30;
    public static final int TEXT_SECONDARY = 0xff434654;
    public static final int OUTLINE = 0xffe1e6f5;

    /** Applies the badge look (15% tint background, solid bold text, rounded pill) to an existing TextView. */
    public static void applyBadgeStyle(TextView badge, int color) {
        badge.setTextColor(color);
        badge.setTextSize(11);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        float density = badge.getContext().getResources().getDisplayMetrics().density;
        badge.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setCornerRadius(4 * density);
        badge.setBackground(bg);
    }

    /** A small status pill: 15% tint of `color` as background, solid `color` bold text — mirrors Tracko's StatusBadge. */
    public static TextView statusBadge(Context ctx, String text, int color) {
        TextView badge = new TextView(ctx);
        badge.setText(text);
        applyBadgeStyle(badge, color);
        return badge;
    }

    /** A round, subtly-tinted icon-only button (like a Material IconButton). */
    public static ImageButton iconButton(Context ctx, int drawableRes, int tintColor, String contentDescription) {
        ImageButton btn = new ImageButton(ctx);
        btn.setImageResource(drawableRes);
        btn.setColorFilter(tintColor);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setContentDescription(contentDescription);
        float density = ctx.getResources().getDisplayMetrics().density;
        btn.setPadding((int) (9 * density), (int) (9 * density), (int) (9 * density), (int) (9 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(24, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)));
        btn.setBackground(bg);
        return btn;
    }

    /** A small square preview of a just-captured photo, downsampled so a handful of these on screen at once doesn't risk running out of memory on a low-RAM device. */
    public static android.widget.ImageView photoPreview(Context ctx, java.io.File file, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
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

    /** The app's main action button: a solid `color` fill, white bold text, rounded corners. */
    public static android.widget.Button filledButton(Context ctx, String text, int color) {
        android.widget.Button button = new android.widget.Button(ctx);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xffffffff);
        button.setTextSize(15);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        float density = ctx.getResources().getDisplayMetrics().density;
        button.setPadding((int) (18 * density), (int) (14 * density), (int) (18 * density), (int) (14 * density));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(8 * density);
        button.setBackground(background);
        return button;
    }

    /** A pill-shaped, tinted icon+text button (e.g. an icon next to the word "Refresh"). */
    public static LinearLayout iconTextButton(Context ctx, int drawableRes, String text, int color) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        float density = ctx.getResources().getDisplayMetrics().density;
        row.setPadding((int) (10 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(drawableRes);
        icon.setColorFilter(color);
        int iconSize = (int) (18 * density);
        row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(14);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = (int) (6 * density);
        row.addView(label, labelParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setCornerRadius(10 * density);
        row.setBackground(bg);
        return row;
    }

    /** An icon used as a tab item (no text). Size and tint are set by the caller / styleTabIcon. */
    /** A bottom-tab item: icon above a short text label, both tinted together by styleTabItem(). */
    public static LinearLayout tabIconWithLabel(Context ctx, int drawableRes, String label) {
        float density = ctx.getResources().getDisplayMetrics().density;
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(drawableRes);
        icon.setContentDescription(label);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int) (22 * density), (int) (22 * density));
        item.addView(icon, iconParams);

        TextView text = new TextView(ctx);
        text.setText(label);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (2 * density);
        item.addView(text, textParams);

        return item;
    }

    /** A left-nav drawer row: icon + label side by side, full width. Child order (icon, then text) matches
     * tabIconWithLabel() so styleTabItem() can tint either shape. */
    public static LinearLayout drawerMenuItem(Context ctx, int drawableRes, String label) {
        float density = ctx.getResources().getDisplayMetrics().density;
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setPadding((int) (18 * density), (int) (14 * density), (int) (18 * density), (int) (14 * density));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(drawableRes);
        icon.setContentDescription(label);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        item.addView(icon, new LinearLayout.LayoutParams((int) (22 * density), (int) (22 * density)));

        TextView text = new TextView(ctx);
        text.setText(label);
        text.setTextSize(15);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = (int) (18 * density);
        item.addView(text, textParams);

        return item;
    }

    /** Tints a tabIconWithLabel()/drawerMenuItem() item's icon + text and toggles its active pill background. */
    public static void styleTabItem(LinearLayout item, boolean active) {
        ImageView icon = (ImageView) item.getChildAt(0);
        TextView text = (TextView) item.getChildAt(1);
        int color = active ? PRIMARY : NEUTRAL;
        icon.setColorFilter(color);
        text.setTextColor(color);
        text.setTypeface(text.getTypeface(), active ? Typeface.BOLD : Typeface.NORMAL);
        if (active) {
            float density = item.getContext().getResources().getDisplayMetrics().density;
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(SURFACE);
            pill.setCornerRadius(10 * density);
            item.setBackground(pill);
        } else {
            item.setBackground(null);
        }
    }

    /** A circular avatar placeholder ImageView; content set later (e.g. from a downloaded bitmap) is clipped to a circle. */
    public static ImageView circularAvatar(Context ctx, int sizeDp) {
        ImageView avatar = new ImageView(ctx);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setShape(GradientDrawable.OVAL);
        placeholder.setColor(OUTLINE);
        avatar.setImageDrawable(placeholder);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        return avatar;
    }

    /** Applies the same rounded-border look used on the login screen's inputs (field_background.xml). */
    public static void styleInput(EditText field) {
        field.setBackgroundResource(R.drawable.field_background);
        float density = field.getContext().getResources().getDisplayMetrics().density;
        field.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        field.setTextColor(TEXT_PRIMARY);
        field.setHintTextColor(NEUTRAL);
    }

    /** A white rounded-corner card background (16dp radius, faint outline standing in for elevation). */
    public static GradientDrawable cardBackground(Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(16 * density);
        bg.setStroke(Math.max(1, (int) density), OUTLINE);
        return bg;
    }

    /** A dialog title row: a small colored accent dot beside bold text, in place of a plain default title (used with Popup.Builder.setCustomTitle). */
    public static android.view.View dialogTitle(Context ctx, String text, int accentColor) {
        float density = ctx.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (22 * density), (int) (20 * density), (int) (22 * density), (int) (6 * density));

        android.view.View dot = new android.view.View(ctx);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(accentColor);
        dot.setBackground(dotBg);
        int dotSize = (int) (10 * density);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.rightMargin = (int) (10 * density);
        row.addView(dot, dotParams);

        TextView title = new TextView(ctx);
        title.setText(text);
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(TEXT_PRIMARY);
        row.addView(title);
        return row;
    }

    /** Colours an already-shown Popup's main button (blue by default; e.g. red for a warning). The card itself is styled by Popup. */
    public static void styleDialog(Popup dialog, int positiveColor) {
        if (positiveColor != PRIMARY) dialog.setPositiveColor(positiveColor);
    }
}
