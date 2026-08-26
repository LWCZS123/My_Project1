package com.example.my_project1.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.example.my_project1.R;

/**
 * 圆形圆头进度环。
 *
 * 控件始终按正方形测量，避免父布局宽高不一致时被拉伸成椭圆。
 * 进度画笔使用 ROUND 端点，与愿望详情设计中的圆角弧线保持一致。
 */
public class RoundedRingProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float progress;
    private float max = 100f;
    private float strokeWidth;
    private ValueAnimator animator;

    public RoundedRingProgressView(Context context) {
        this(context, null);
    }

    public RoundedRingProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundedRingProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float defaultStroke = 12f * getResources().getDisplayMetrics().density;
        TypedArray values = context.obtainStyledAttributes(
                attrs, R.styleable.RoundedRingProgressView, defStyleAttr, 0);
        progress = values.getFloat(R.styleable.RoundedRingProgressView_ringProgress, 0f);
        max = values.getFloat(R.styleable.RoundedRingProgressView_ringMax, 100f);
        strokeWidth = values.getDimension(
                R.styleable.RoundedRingProgressView_ringStrokeWidth, defaultStroke);
        int progressColor = values.getColor(
                R.styleable.RoundedRingProgressView_ringProgressColor, Color.rgb(49, 92, 245));
        int trackColor = values.getColor(
                R.styleable.RoundedRingProgressView_ringTrackColor, Color.rgb(236, 233, 238));
        values.recycle();

        configurePaint(trackPaint, trackColor);
        configurePaint(progressPaint, progressColor);
    }

    private void configurePaint(Paint paint, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = (int) (180f * getResources().getDisplayMetrics().density);
        int measuredWidth = resolveSize(defaultSize, widthMeasureSpec);
        int measuredHeight = resolveSize(defaultSize, heightMeasureSpec);
        int size = Math.min(measuredWidth, measuredHeight);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = strokeWidth / 2f + 1f;
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float center = getWidth() / 2f;
        float radius = (getWidth() - strokeWidth) / 2f - 1f;
        canvas.drawCircle(center, center, radius, trackPaint);
        if (progress > 0f && max > 0f) {
            float sweepAngle = Math.min(progress, max) / max * 360f;
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint);
        }
    }

    public void setProgress(float progress) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        this.progress = Math.max(0f, Math.min(progress, max));
        invalidate();
    }

    /**
     * 带动画设置进度
     *
     * @param targetProgress 目标进度
     * @param duration 动画时长(ms)
     */
    public void setProgressWithAnimation(float targetProgress, long duration) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        float start = this.progress;
        float end = Math.max(0f, Math.min(targetProgress, max));

        animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            this.progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
