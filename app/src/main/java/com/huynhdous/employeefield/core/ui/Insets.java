package com.huynhdous.employeefield.core.ui;

import android.view.View;
import android.view.WindowInsets;

/** Keeps a screen clear of the status bar, the navigation bar and the on-screen keyboard. */
public final class Insets {
    private Insets() {
    }

    public static void apply(View root) {
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
}
