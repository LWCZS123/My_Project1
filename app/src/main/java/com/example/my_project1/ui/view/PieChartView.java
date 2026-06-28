package com.example.my_project1.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

public class PieChartView extends View {

    public interface OnSliceClickListener {
        void onSliceClicked(PieEntry entry, int index);
    }

    public static class PieEntry {
        public String label;
        public float  value;
        public int    color;
        public String categoryId;

        public PieEntry(String label, float value, int color, String categoryId) {
            this.label      = label;
            this.value      = value;
            this.color      = color;
            this.categoryId = categoryId;
        }
    }

    // 示例图配色
    private static final int[] PRESET_COLORS = {
            0xFFFFB726,  // A3 橙色
            0xFF2196F3,  // A5 蓝色
            0xFF4DD0E1,  // RI 绿色
            0xFF9C27B0,  // AF 紫色
            0xFFE91E63,  // SE 红色
            0xFF00BCD4   // MX 青色
    };

    public static int getPresetColor(int i) {
        return PRESET_COLORS[i % PRESET_COLORS.length];
    }

    // ── Data ──────────────────────────────────────────────────────────
    private List<PieEntry> entries  = new ArrayList<>();
    private float          total    = 1f;
    // 移除中心显示的总数值
    // private String         centerText = "11793";

    // ── Animation ─────────────────────────────────────────────────────
    private float          animProgress  = 1f;
    private ValueAnimator  entryAnimator;

    // ── Rotation ──────────────────────────────────────────────────────
    private float          rotationOffset = 0f;
    private float          lastTouchAngle = 0f;
    private float          lastRawAngle   = 0f;
    private long           lastTouchTime  = 0;
    private float          angularVelocity = 0f;
    private ValueAnimator  flingAnimator;
    private boolean        isTouchDown    = false;

    // ── Highlight ─────────────────────────────────────────────────────
    private int            selectedIndex  = -1;
    private float          highlightAnim  = 0f;
    private ValueAnimator  highlightAnimator;
    private int            previousSelected = -1;

    // ── Dimensions ────────────────────────────────────────────────────
    private float expandPx;
    private float leaderRadial;
    private float labelMargin;
    private float labelTextSize; // 缩小标签字号
    private float centerTextSize; // 不再使用
    private float innerRatio   = 0.45f; // 内圈半径比例
    private float sliceGapDeg  = 2.0f;   // 扇区间隙角度

    // ── Paints ────────────────────────────────────────────────────────
    private Paint slicePaint;
    private Paint holePaint;
    private Paint linePaint;
    private Paint labelPaint;
    // 移除中心文字画笔
    // private TextPaint centerTextPaint;
    // 移除图例画笔
    // private Paint legendPaint;

    // ── Touch threshold ───────────────────────────────────────────────
    private float touchSlopSq;

    private OnSliceClickListener clickListener;

    // ═════════════════════════════════════════════════════════════════
    public PieChartView(Context c) { this(c, null); }

    public PieChartView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;

        expandPx      = 10 * d;
        leaderRadial  = 12 * d;
        labelMargin   = 2  * d;
        labelTextSize = 8 * d; // 将标签字号从 14d 改为 12d
        centerTextSize = 24 * d; // 不再使用

        slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slicePaint.setStyle(Paint.Style.FILL);

        holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        holePaint.setColor(Color.WHITE);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.5f * d);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(labelTextSize);

        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        touchSlopSq = slop * slop;
    }

    // ── Public API ────────────────────────────────────────────────────

    public void setData(List<PieEntry> data) {
        entries = (data == null) ? new ArrayList<>() : data;
        total   = 0;
        for (PieEntry e : entries) total += e.value;
        if (total == 0) total = 1;
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).color == 0)
                entries.get(i).color = PRESET_COLORS[i % PRESET_COLORS.length];

        selectedIndex = -1;
        startEntryAnim();
    }

    // 移除设置中心文本的方法
    // public void setCenterText(String text) {
    //     this.centerText = text;
    //     invalidate();
    // }

    public void setOnSliceClickListener(OnSliceClickListener l) {
        this.clickListener = l;
    }

    public void highlightSlice(int index) {
        if (index == selectedIndex) index = -1;
        animateHighlight(index);
    }

    // ── Entry animation (draw-in) ─────────────────────────────────────

    private void startEntryAnim() {
        if (entryAnimator != null) entryAnimator.cancel();
        animProgress = 0f;
        entryAnimator = ValueAnimator.ofFloat(0f, 1f);
        entryAnimator.setDuration(600);
        entryAnimator.setInterpolator(new DecelerateInterpolator());
        entryAnimator.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        entryAnimator.start();
    }

    // ── Highlight animation ───────────────────────────────────────────

    private void animateHighlight(int newIndex) {
        if (highlightAnimator != null) highlightAnimator.cancel();
        previousSelected = selectedIndex;
        selectedIndex    = newIndex;

        highlightAnimator = ValueAnimator.ofFloat(0f, 1f);
        highlightAnimator.setDuration(200);
        highlightAnimator.setInterpolator(new DecelerateInterpolator());
        highlightAnimator.addUpdateListener(a -> {
            highlightAnim = (float) a.getAnimatedValue();
            invalidate();
        });
        highlightAnimator.start();
    }

    // ── Draw ──────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (entries.isEmpty()) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float d = getResources().getDisplayMetrics().density;

        float avail = Math.min(cx, cy) - expandPx - leaderRadial - 30 * d;
        float radius = Math.max(avail, 48 * d);
        float innerR = radius * innerRatio;

        float startAngle = -90 + rotationOffset;
        float sweepTotal = 360 * animProgress;

        // 绘制所有扇形
        for (int i = 0; i < entries.size(); i++) {
            PieEntry e = entries.get(i);
            float sweep = (e.value / total) * sweepTotal;
            float actualSw = Math.max(sweep - sliceGapDeg, 0.1f);
            float actualSt = startAngle + (sweep - actualSw) / 2f;

            float offsetPx = 0f;
            if (i == selectedIndex) {
                offsetPx = expandPx * highlightAnim;
            } else if (i == previousSelected) {
                offsetPx = expandPx * (1f - highlightAnim);
            }

            float outerR = radius + offsetPx;
            float innerRCurrent = innerR + offsetPx;

            Path path = new Path();

            float startRad = (float) Math.toRadians(actualSt);
            float endRad = (float) Math.toRadians(actualSt + actualSw);

            float outStartX = cx + outerR * (float) Math.cos(startRad);
            float outStartY = cy + outerR * (float) Math.sin(startRad);
            float outEndX = cx + outerR * (float) Math.cos(endRad);
            float outEndY = cy + outerR * (float) Math.sin(endRad);
            float inStartX = cx + innerRCurrent * (float) Math.cos(endRad);
            float inStartY = cy + innerRCurrent * (float) Math.sin(endRad);
            float inEndX = cx + innerRCurrent * (float) Math.cos(startRad);
            float inEndY = cy + innerRCurrent * (float) Math.sin(startRad);

            // 使用 arcTo 绘制扇形
            path.moveTo(outStartX, outStartY);
            path.arcTo(
                    new RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR),
                    actualSt,
                    actualSw,
                    false
            );
            path.arcTo(
                    new RectF(cx - innerRCurrent, cy - innerRCurrent, cx + innerRCurrent, cy + innerRCurrent),
                    actualSt + actualSw,
                    -actualSw,
                    false
            );
            path.close();

            slicePaint.setColor(e.color);
            canvas.drawPath(path, slicePaint);

            startAngle += sweep;
        }

        // 绘制中心空心圆
        canvas.drawCircle(cx, cy, innerR, holePaint);

        // 不再绘制中心文本
        // Paint.FontMetricsInt fm = centerTextPaint.getFontMetricsInt();
        // float baseline = cy - (fm.top + fm.bottom) / 2f;
        // canvas.drawText(centerText, cx, baseline, centerTextPaint);

        // 绘制标签
        if (animProgress > 0.9f) drawLabels(canvas, cx, cy, radius);

        // 不再绘制图例
        // drawLegend(canvas, cx, cy, radius);
    }

    private void drawLabels(Canvas canvas, float cx, float cy, float radius) {
        float startAngle = -90 + rotationOffset;
        for (int i = 0; i < entries.size(); i++) {
            PieEntry e = entries.get(i);
            float sweep = (e.value / total) * 360;
            float percent = e.value / total * 100;
            if (percent < 2) { startAngle += sweep; continue; }

            float mid = startAngle + sweep / 2;
            float rad = (float) Math.toRadians(mid);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            boolean right = cos >= 0;

            float p1x = cx + (radius + labelMargin) * cos;
            float p1y = cy + (radius + labelMargin) * sin;
            float p2x = cx + (radius + leaderRadial) * cos;
            float p2y = cy + (radius + leaderRadial) * sin;

            linePaint.setColor(e.color);
            canvas.drawLine(p1x, p1y, p2x, p2y, linePaint);

            // 关键修改：在标签中显示百分比
            labelPaint.setTextAlign(right ? Paint.Align.LEFT : Paint.Align.RIGHT);
            // 格式化为两位小数的百分比字符串
            String labelWithPercent = e.label + " " + String.format("%.2f%%", percent);
            canvas.drawText(labelWithPercent, right ? p2x + 6 : p2x - 6, p2y + labelTextSize / 3, labelPaint);

            startAngle += sweep;
        }
    }

    // ── Touch: drag-rotate + tap-to-highlight ─────────────────────────
    private float downX, downY;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (flingAnimator != null) flingAnimator.cancel();
                downX = ev.getX();
                downY = ev.getY();
                lastTouchAngle = angleDeg(ev.getX() - cx, ev.getY() - cy);
                lastRawAngle = lastTouchAngle;
                lastTouchTime = ev.getEventTime();
                angularVelocity = 0f;
                isTouchDown = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                float curAngle = angleDeg(ev.getX() - cx, ev.getY() - cy);
                float delta = deltaAngle(curAngle, lastTouchAngle);
                rotationOffset += delta;
                lastTouchAngle = curAngle;

                long dt = ev.getEventTime() - lastTouchTime;
                float va = deltaAngle(curAngle, lastRawAngle) / Math.max(dt, 1);
                angularVelocity = 0.7f * angularVelocity + 0.3f * va;

                lastRawAngle = curAngle;
                lastTouchTime = ev.getEventTime();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                boolean isTap = (dx * dx + dy * dy) < touchSlopSq;

                if (isTap) {
                    int idx = sliceAtAngle(angleDeg(ev.getX() - cx, ev.getY() - cy), cx, cy, ev.getX(), ev.getY());
                    animateHighlight(idx == selectedIndex ? -1 : idx);
                    if (clickListener != null && idx >= 0)
                        clickListener.onSliceClicked(entries.get(idx), idx);
                } else {
                    startFling(angularVelocity);
                }
                isTouchDown = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private int sliceAtAngle(float touchAngle, float cx, float cy, float tx, float ty) {
        float d = getResources().getDisplayMetrics().density;
        float avail = Math.min(cx, cy) - expandPx - leaderRadial - 30 * d;
        float radius = Math.max(avail, 48 * d);
        float distSq = (tx - cx) * (tx - cx) + (ty - cy) * (ty - cy);
        float innerR = radius * innerRatio;
        if (distSq < innerR * innerR || distSq > (radius + expandPx) * (radius + expandPx))
            return -1;

        float start = -90 + rotationOffset;
        for (int i = 0; i < entries.size(); i++) {
            float sweep = (entries.get(i).value / total) * 360;
            float end = start + sweep;
            float ta = touchAngle;
            while (ta < start) ta += 360;
            if (ta >= start && ta < end) return i;
            start = end;
        }
        return -1;
    }

    private void startFling(float velocityDegPerMs) {
        if (flingAnimator != null) flingAnimator.cancel();
        long duration = (long) Math.min(Math.max(Math.abs(velocityDegPerMs) * 800, 300), 1800);
        float[] rotation = {rotationOffset};
        float[] vel = {velocityDegPerMs};

        flingAnimator = ValueAnimator.ofFloat(0f, 1f);
        flingAnimator.setDuration(duration);
        flingAnimator.addUpdateListener(a -> {
            float dt = 16f;
            vel[0] *= 0.95f;
            rotation[0] += vel[0] * dt;
            rotationOffset = rotation[0];
            invalidate();
        });
        flingAnimator.start();
    }

    // ── Angle helpers ─────────────────────────────────────────────────
    private static float angleDeg(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    private static float deltaAngle(float a, float b) {
        float d = a - b;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }
}