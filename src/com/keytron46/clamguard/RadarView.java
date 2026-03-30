package com.keytron46.clamguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RadarView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private boolean active;
    private float sweepAngle = 0f;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!active) {
                return;
            }
            sweepAngle += 6f;
            if (sweepAngle >= 360f) {
                sweepAngle -= 360f;
            }
            invalidate();
            postDelayed(this, 16L);
        }
    };

    public RadarView(Context context) {
        super(context);
        init();
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(3f);
        gridPaint.setColor(Color.parseColor("#3A4A53"));

        sweepPaint.setStyle(Paint.Style.FILL);
        sweepPaint.setColor(Color.parseColor("#66EF3E42"));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.parseColor("#EF3E42"));
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        removeCallbacks(ticker);
        if (active) {
            post(ticker);
        } else {
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = (size / 2f) - 8f;

        canvas.drawCircle(centerX, centerY, radius, gridPaint);
        canvas.drawCircle(centerX, centerY, radius * 0.66f, gridPaint);
        canvas.drawCircle(centerX, centerY, radius * 0.33f, gridPaint);
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridPaint);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridPaint);

        if (active) {
            arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            canvas.drawArc(arcRect, sweepAngle - 30f, 30f, true, sweepPaint);
        }

        canvas.drawCircle(centerX + radius * 0.3f, centerY - radius * 0.18f, 7f, dotPaint);
        canvas.drawCircle(centerX - radius * 0.22f, centerY + radius * 0.34f, 5f, dotPaint);
    }
}
