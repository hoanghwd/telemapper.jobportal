package com.huynhdous.employeefield.core.ui;

import com.huynhdous.employeefield.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A small canvas-drawn ring showing worked minutes against a daily target — no drawable/XML needed. */
public final class DayProgressRing extends View {
    private float progress;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DayProgressRing(Context ctx) {
        super(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        float strokeWidth = 3.5f * density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(Theme.OUTLINE);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Theme.SUCCESS);
    }

    public void setProgress(float fraction, int color) {
        this.progress = Math.max(0f, Math.min(1f, fraction));
        progressPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = trackPaint.getStrokeWidth() / 2f;
        RectF rect = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(rect, 0, 360, false, trackPaint);
        if (progress > 0f) canvas.drawArc(rect, -90, 360 * progress, false, progressPaint);
    }
}
