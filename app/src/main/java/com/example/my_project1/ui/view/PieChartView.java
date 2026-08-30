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
import java.util.Collections;
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

    private static class LabelPos {
        PieEntry entry;
        float percent;
        float p1x, p1y;
        float p2x, p2y;
        boolean isRight;
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
        leaderRadial  = 15 * d; // 第一段斜线长度
        labelMargin   = 10 * d; // 第二段横线长度
        labelTextSize = 9 * d;  // 缩小标签字号到 9dp
        centerTextSize = 24 * d; // 不再使用

        slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slicePaint.setStyle(Paint.Style.FILL); // 改回纯填充，手动处理圆角

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

        // 增大内边距，为外推的扇形和标签留出空间
        float avail = Math.min(cx, cy) - expandPx - 40 * d; 
        float radius = Math.max(avail, 70 * d); 

        float startAngle = -90 + rotationOffset;
        float sweepTotal = 360 * animProgress;

        // 设置画笔，增大描边宽度实现更圆润的视觉效果
        slicePaint.setAntiAlias(true);
        slicePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        slicePaint.setStrokeJoin(Paint.Join.ROUND);
        slicePaint.setStrokeWidth(8 * d); // 增大圆角半径 (描边的一半为圆角)

        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        // 绘制所有扇形
        for (int i = 0; i < entries.size(); i++) {
            PieEntry e = entries.get(i);
            float sweep = (e.value / total) * sweepTotal;
            // 夹角间隙
            float actualSw = Math.max(sweep - 1.5f, 0.5f);
            float actualSt = startAngle + (sweep - actualSw) / 2f;

            // 动画/选中位移
            float moveOffset = 0f;
            if (i == selectedIndex) {
                moveOffset = expandPx * highlightAnim;
            } else if (i == previousSelected) {
                moveOffset = expandPx * (1f - highlightAnim);
            }

            // 物理分离距离：配合圆角增大分离位移，防止中心挤压
            float baseSep = 10 * d;
            float midRad = (float) Math.toRadians(actualSt + actualSw / 2f);
            float dx = (baseSep + moveOffset) * (float) Math.cos(midRad);
            float dy = (baseSep + moveOffset) * (float) Math.sin(midRad);

            Path path = new Path();
            path.moveTo(cx + dx, cy + dy);
            
            // 核心修复：通过对 RectF 的同心平移，确保所有扇区仍属于同一个“虚拟圆”
            RectF sliceOval = new RectF(oval);
            sliceOval.offset(dx, dy);
            path.arcTo(sliceOval, actualSt, actualSw, false);
            
            path.lineTo(cx + dx, cy + dy);
            path.close();

            slicePaint.setColor(e.color);
            canvas.drawPath(path, slicePaint);

            startAngle += sweep;
        }

        // 绘制标签（直线模式）
        if (animProgress > 0.9f) drawLabels(canvas, cx, cy, radius);
    }

    private void drawLabels(Canvas canvas, float cx, float cy, float radius) {
        float startAngle = -90 + rotationOffset;
        float d = getResources().getDisplayMetrics().density;
        
        List<LabelPos> leftLabels = new ArrayList<>();
        List<LabelPos> rightLabels = new ArrayList<>();

        // 1. 第一阶段：预计算所有标签的基础位置，并按左右分组
        for (int i = 0; i < entries.size(); i++) {
            PieEntry e = entries.get(i);
            float sweep = (e.value / total) * 360;
            float percent = e.value / total * 100;
            
            // 降低阈值，显示更多小比例标签
            if (percent >= 0.1f) { 
                float mid = startAngle + sweep / 2;
                float rad = (float) Math.toRadians(mid);
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);
                boolean isRight = cos >= 0;

                LabelPos pos = new LabelPos();
                pos.entry = e;
                pos.percent = percent;
                
                // 起点在圆周边缘
                pos.p1x = cx + radius * cos;
                pos.p1y = cy + radius * sin;
                
                // 终点在引线末端（基础位置）
                float radialLen = leaderRadial + 10 * d; // 增加引线长度
                pos.p2x = cx + (radius + radialLen) * cos;
                pos.p2y = cy + (radius + radialLen) * sin;
                pos.isRight = isRight;

                if (isRight) rightLabels.add(pos);
                else leftLabels.add(pos);
            }
            startAngle += sweep;
        }

        // 2. 第二阶段：排序并强制执行垂直间距，防止重叠和交叉
        float minGap = labelTextSize * 1.5f;
        
        // 按 Y 坐标排序，确保调整后顺序不变（防止引导线交叉的核心）
        Collections.sort(leftLabels, (a, b) -> Float.compare(a.p2y, b.p2y));
        Collections.sort(rightLabels, (a, b) -> Float.compare(a.p2y, b.p2y));

        adjustYPositions(leftLabels, minGap);
        adjustYPositions(rightLabels, minGap);

        // 3. 第三阶段：绘制
        drawLabelPosList(canvas, leftLabels, d);
        drawLabelPosList(canvas, rightLabels, d);
    }

    private void adjustYPositions(List<LabelPos> list, float minGap) {
        if (list.size() < 2) return;
        
        // 正向调整：从上往下推
        for (int i = 1; i < list.size(); i++) {
            LabelPos prev = list.get(i - 1);
            LabelPos curr = list.get(i);
            if (curr.p2y - prev.p2y < minGap) {
                curr.p2y = prev.p2y + minGap;
            }
        }
        
        // 反向调整：防止最后几个被推得太靠下，向上拉回
        for (int i = list.size() - 2; i >= 0; i--) {
            LabelPos next = list.get(i + 1);
            LabelPos curr = list.get(i);
            if (next.p2y - curr.p2y < minGap) {
                curr.p2y = next.p2y - minGap;
            }
        }
    }

    private void drawLabelPosList(Canvas canvas, List<LabelPos> list, float d) {
        for (LabelPos pos : list) {
            linePaint.setColor(pos.entry.color);
            linePaint.setAlpha(160);
            
            // 绘制直线引导线
            canvas.drawLine(pos.p1x, pos.p1y, pos.p2x, pos.p2y, linePaint);

            // 绘制文字
            labelPaint.setTextAlign(pos.isRight ? Paint.Align.LEFT : Paint.Align.RIGHT);
            String labelStr = pos.entry.label + " " + String.format("%.2f%%", pos.percent);
            
            float textX = pos.p2x + (pos.isRight ? 4 * d : -4 * d);
            canvas.drawText(labelStr, textX, pos.p2y + labelTextSize / 3, labelPaint);
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
        float avail = Math.min(cx, cy) - expandPx - 5 * d;
        float radius = Math.max(avail, 70 * d);
        float distSq = (tx - cx) * (tx - cx) + (ty - cy) * (ty - cy);
        
        // 如果是实心尖角，允许点击距离中心很近的地方（如 5dp）
        float minClickDistSq = (5 * d) * (5 * d);
        if (distSq < minClickDistSq || distSq > (radius + expandPx + 20 * d) * (radius + expandPx + 20 * d))
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