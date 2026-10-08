package com.huynhdous.employeefield.core.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared presentation for schedule activity and weekly time cards. */
public final class TimeCardUi {
    private TimeCardUi() {}
    public static int dp(Context c, int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    public static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return v;
    }
    public static GradientDrawable background(Context c,int color,int radius) {
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(c,radius));return bg;
    }
    public static LinearLayout card(Context c) {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);
        int p=dp(c,18);v.setPadding(p,p,p,p);v.setBackground(Theme.cardBackground(c));return v;
    }
    public static void divider(LinearLayout parent) {
        Context c=parent.getContext();View line=new View(c);line.setBackgroundColor(Theme.OUTLINE);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(c,1));p.topMargin=dp(c,14);p.bottomMargin=dp(c,12);parent.addView(line,p);
    }
    public static void segment(LinearLayout parent,String kind,String times,String duration,int color) {
        Context c=parent.getContext();LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(c,8),0,dp(c,8));
        TextView dot=text(c,"•",24,color,true);dot.setGravity(Gravity.CENTER);
        row.addView(dot,new LinearLayout.LayoutParams(dp(c,20),-2));
        LinearLayout copy=new LinearLayout(c);copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(c,8);cp.rightMargin=dp(c,8);row.addView(copy,cp);
        copy.addView(text(c,kind,13,Theme.TEXT_PRIMARY,true));copy.addView(text(c,times,12,Theme.NEUTRAL,false));
        TextView amount=text(c,duration,12,color,true);amount.setGravity(Gravity.CENTER);
        amount.setPadding(dp(c,8),dp(c,6),dp(c,8),dp(c,6));amount.setBackground(background(c,kind.equals("Work")?0xffedf5ff:0xfffff5e6,8));
        row.addView(amount,new LinearLayout.LayoutParams(-2,-2));parent.addView(row);
    }
    /** A compact lock with text, rather than relying on a font's emoji rendering. */
    public static android.widget.TextView lockedBadge(android.content.Context context, String label) {
        android.widget.TextView badge = Theme.statusBadge(context, label, Theme.SUCCESS);
        android.graphics.drawable.Drawable lock = context.getDrawable(com.huynhdous.employeefield.R.drawable.ic_lock);
        if (lock != null) {
            lock.setTint(Theme.SUCCESS);
            lock.setBounds(0, 0, dp(context, 16), dp(context, 16));
            badge.setCompoundDrawables(lock, null, null, null);
            badge.setCompoundDrawablePadding(dp(context, 6));
        }
        return badge;
    }
}
