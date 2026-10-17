package com.example.my_project1.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorWheelView extends View {

    public interface OnColorChangeListener {
        void onColorChanged(float hue, float saturation);
    }

    private Paint wheelPaint;
    private Paint selectorOuterPaint;
    private Paint selectorInnerPaint;

    private float centerX;
    private float centerY;
    private float radius;

    private float currentHue = 0f;
    private float currentSat = 1.0f;

    private float selectorX;
    private float selectorY;

    private OnColorChangeListener listener;

    public ColorWheelView(Context context) {
        super(context);
        init();
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        selectorOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectorOuterPaint.setStyle(Paint.Style.STROKE);
        selectorOuterPaint.setStrokeWidth(dpToPx(3));
        selectorOuterPaint.setColor(Color.WHITE);

        selectorInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectorInnerPaint.setStyle(Paint.Style.STROKE);
        selectorInnerPaint.setStrokeWidth(dpToPx(1.5f));
        selectorInnerPaint.setColor(Color.parseColor("#333333"));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    public void setOnColorChangeListener(OnColorChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        float padding = dpToPx(12);
        radius = Math.min(w, h) / 2f - padding;

        if (radius <= 0) return;

        int[] colors = new int[]{
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        };
        Shader sweepShader = new SweepGradient(centerX, centerY, colors, null);
        Shader radialShader = new RadialGradient(centerX, centerY, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
        ComposeShader composeShader = new ComposeShader(sweepShader, radialShader, PorterDuff.Mode.SRC_OVER);

        wheelPaint.setShader(composeShader);

        updateSelectorFromHueSat();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0) return;

        canvas.drawCircle(centerX, centerY, radius, wheelPaint);

        // Draw selector ring on current position
        canvas.drawCircle(selectorX, selectorY, dpToPx(8), selectorOuterPaint);
        canvas.drawCircle(selectorX, selectorY, dpToPx(9.5f), selectorInnerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - centerX;
                float dy = event.getY() - centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist > radius) {
                    dx = dx * (radius / dist);
                    dy = dy * (radius / dist);
                    dist = radius;
                }

                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360f;

                currentHue = angle;
                currentSat = dist / radius;

                selectorX = centerX + dx;
                selectorY = centerY + dy;

                invalidate();

                if (listener != null) {
                    listener.onColorChanged(currentHue, currentSat);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    public void setColor(float hue, float sat) {
        this.currentHue = hue;
        this.currentSat = Math.max(0f, Math.min(1.0f, sat));
        updateSelectorFromHueSat();
        invalidate();
    }

    private void updateSelectorFromHueSat() {
        if (radius <= 0) return;
        double rad = Math.toRadians(currentHue);
        float dist = currentSat * radius;
        selectorX = (float) (centerX + dist * Math.cos(rad));
        selectorY = (float) (centerY + dist * Math.sin(rad));
    }

    public float getCurrentHue() {
        return currentHue;
    }

    public float getCurrentSat() {
        return currentSat;
    }
}
