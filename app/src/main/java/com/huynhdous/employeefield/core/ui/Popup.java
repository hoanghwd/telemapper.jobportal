package com.huynhdous.employeefield.core.ui;

import com.huynhdous.employeefield.R;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The app's one pop-up look: a white rounded card floating in the middle of the screen with a bold title, the message or
 * custom content, and full-width pill buttons. It is a drop-in for AlertDialog -- same Builder methods
 * (setTitle / setCustomTitle / setMessage / setView / setItems / set*Button / setCancelable / show) and the same
 * getButton(BUTTON_*) -- so a screen only changes the class name, not how it works. As with AlertDialog, a button
 * dismisses the pop-up after its listener runs, unless the caller replaces the button's click listener.
 */
public final class Popup extends Dialog {
    private final Button[] buttons = new Button[3]; // index 0 positive, 1 neutral, 2 negative

    private Popup(Context context) {
        super(context);
    }

    /** BUTTON_POSITIVE / BUTTON_NEUTRAL / BUTTON_NEGATIVE, as in AlertDialog. */
    public Button getButton(int which) {
        return which == BUTTON_POSITIVE ? buttons[0] : which == BUTTON_NEUTRAL ? buttons[1] : which == BUTTON_NEGATIVE ? buttons[2] : null;
    }

    /** Fills the main (positive) button with this colour -- e.g. red for a warning -- keeping its label readable. */
    public void setPositiveColor(int color) {
        Button positive = buttons[0];
        if (positive == null) return;
        float density = getContext().getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(14 * density);
        positive.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        positive.setTextColor(luminance > 0.6 ? 0xff2a1200 : Color.WHITE);
    }

    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private View customTitle;
        private CharSequence message;
        private View view;
        private CharSequence[] items;
        private OnClickListener itemsListener;
        private final CharSequence[] labels = new CharSequence[3];
        private final OnClickListener[] listeners = new OnClickListener[3];
        private boolean cancelable = true;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) { this.title = title; return this; }
        public Builder setTitle(int resId) { return setTitle(context.getText(resId)); }
        public Builder setCustomTitle(View customTitle) { this.customTitle = customTitle; return this; }
        public Builder setMessage(CharSequence message) { this.message = message; return this; }
        public Builder setMessage(int resId) { return setMessage(context.getText(resId)); }
        public Builder setView(View view) { this.view = view; return this; }
        public Builder setCancelable(boolean cancelable) { this.cancelable = cancelable; return this; }

        public Builder setItems(CharSequence[] items, OnClickListener listener) {
            this.items = items;
            this.itemsListener = listener;
            return this;
        }

        public Builder setPositiveButton(CharSequence label, OnClickListener listener) { labels[0] = label; listeners[0] = listener; return this; }
        public Builder setPositiveButton(int resId, OnClickListener listener) { return setPositiveButton(context.getText(resId), listener); }
        public Builder setNeutralButton(CharSequence label, OnClickListener listener) { labels[1] = label; listeners[1] = listener; return this; }
        public Builder setNegativeButton(CharSequence label, OnClickListener listener) { labels[2] = label; listeners[2] = listener; return this; }

        public Popup show() {
            Popup popup = create();
            popup.show();
            return popup;
        }

        public Popup create() {
            final float density = context.getResources().getDisplayMetrics().density;
            final Popup popup = new Popup(context);
            popup.requestWindowFeature(Window.FEATURE_NO_TITLE);
            popup.setCancelable(cancelable);
            popup.setCanceledOnTouchOutside(cancelable);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Theme.SURFACE);
            cardBg.setCornerRadius(24 * density);
            card.setBackground(cardBg);

            // Title: the caller's own view (accent-dot titles) or a plain bold one. Both keep their own side padding.
            boolean hasTitle = customTitle != null || (title != null && title.length() > 0);
            if (customTitle != null) {
                card.addView(customTitle);
            } else if (title != null && title.length() > 0) {
                TextView titleView = new TextView(context);
                titleView.setText(title);
                titleView.setTextSize(20);
                titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
                titleView.setTextColor(Theme.TEXT_PRIMARY);
                titleView.setPadding((int) (22 * density), (int) (22 * density), (int) (22 * density), (int) (4 * density));
                card.addView(titleView);
            }

            // Body: message, custom content or a list of choices -- scrolls instead of running off a small screen.
            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            if (message != null && message.length() > 0) {
                TextView messageView = new TextView(context);
                messageView.setText(message);
                messageView.setTextSize(hasTitle ? 15 : 16);
                messageView.setLineSpacing(0, 1.1f);
                messageView.setTextColor(hasTitle ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY);
                messageView.setPadding((int) (22 * density), (int) ((hasTitle ? 6 : 24) * density), (int) (22 * density), (int) (6 * density));
                body.addView(messageView);
            }
            if (view != null) {
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                body.addView(view);
            }
            if (items != null) {
                for (int i = 0; i < items.length; i++) body.addView(itemRow(popup, items[i], i, density));
            }
            if (body.getChildCount() > 0) {
                MaxHeightScrollView scroll = new MaxHeightScrollView(context, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6f));
                scroll.setVerticalScrollBarEnabled(false);
                scroll.addView(body);
                card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            // Buttons: two side by side (Cancel left, main action right); three or one stacked, main action first.
            int count = 0;
            for (CharSequence label : labels) if (label != null) count++;
            if (count > 0) {
                LinearLayout row = new LinearLayout(context);
                // Two short labels sit side by side; a longer label (e.g. "Continue Anyway") gets the whole width, so it never has to wrap.
                boolean sideBySide = count == 2 && labels[0] != null && labels[2] != null && labels[0].length() <= 12 && labels[2].length() <= 12;
                row.setOrientation(sideBySide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
                row.setPadding((int) (22 * density), (int) (14 * density), (int) (22 * density), (int) (18 * density));
                int[] order = sideBySide ? new int[]{2, 0} : new int[]{0, 1, 2};
                boolean first = true;
                for (int idx : order) {
                    if (labels[idx] == null) continue;
                    Button b = pill(popup, labels[idx], idx, listeners[idx], density);
                    popup.buttons[idx] = b;
                    LinearLayout.LayoutParams lp = sideBySide
                            ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                            : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (!first) {
                        if (sideBySide) lp.leftMargin = (int) (10 * density);
                        else lp.topMargin = (int) (8 * density);
                    }
                    row.addView(b, lp);
                    first = false;
                }
                card.addView(row);
            } else if (body.getChildCount() > 0) {
                card.addView(new View(context), new LinearLayout.LayoutParams(1, (int) (14 * density)));
            }

            FrameLayout frame = new FrameLayout(context);
            int margin = (int) (24 * density);
            frame.setPadding(margin, 0, margin, 0);
            frame.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            popup.setContentView(frame);
            Window window = popup.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
                window.setDimAmount(0.45f);
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            return popup;
        }

        private Button pill(Popup popup, CharSequence label, int idx, OnClickListener listener, float density) {
            Button b = new Button(context);
            b.setText(label);
            b.setAllCaps(false);
            b.setTextSize(15);
            b.setTypeface(b.getTypeface(), Typeface.BOLD);
            b.setStateListAnimator(null);
            b.setMinHeight((int) (50 * density));
            b.setMinimumHeight((int) (50 * density));
            b.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * density);
            if (idx == 0) {
                bg.setColor(Theme.PRIMARY);
                b.setTextColor(Color.WHITE);
            } else if (idx == 1) {
                bg.setColor(Color.argb(24, 0, 0x3f, 0xb1));
                b.setTextColor(Theme.PRIMARY);
            } else {
                bg.setColor(Color.argb(24, 0x43, 0x46, 0x54));
                b.setTextColor(Theme.TEXT_SECONDARY);
            }
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(idx == 0 ? 0x33FFFFFF : 0x22003FB1), bg, null));
            final int which = idx == 0 ? BUTTON_POSITIVE : idx == 1 ? BUTTON_NEUTRAL : BUTTON_NEGATIVE;
            b.setOnClickListener(v -> {
                if (listener != null) listener.onClick(popup, which);
                popup.dismiss();
            });
            return b;
        }

        private View itemRow(Popup popup, CharSequence text, int index, float density) {
            TextView row = new TextView(context);
            row.setText(text);
            row.setTextSize(16);
            row.setTextColor(Theme.TEXT_PRIMARY);
            row.setTypeface(row.getTypeface(), Typeface.BOLD);
            row.setClickable(true);
            row.setFocusable(true);
            row.setPadding((int) (22 * density), (int) (14 * density), (int) (22 * density), (int) (14 * density));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1A000000), null, new ColorDrawable(Color.WHITE)));
            row.setOnClickListener(v -> {
                popup.dismiss();
                if (itemsListener != null) itemsListener.onClick(popup, index);
            });
            return row;
        }
    }

    /** A ScrollView that never grows past a given height, so a long message or form scrolls inside the card. */
    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeightPx;

        MaxHeightScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST));
        }
    }
}
