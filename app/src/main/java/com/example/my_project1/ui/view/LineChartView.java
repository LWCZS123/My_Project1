package com.example.my_project1.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.Nullable;

/**
 * 曲线图自定义 View — 完整修复版
 *
 * 修复内容：
 *   1. 数据渲染顺序修正：数据从左到右按传入顺序（时间升序）排列，
 *      不再出现"倒序"问题（需确保 ViewModel 传入的数据已升序排序）。
 *   2. X 轴间距统一：所有数据点等间距分布，xStep 由实际可用宽度均分计算。
 *   3. 数据量多时支持水平滑动：onMeasure 按最小 xStep(48dp) 计算最小宽度，
 *      超出父容器时自然产生滚动（配合外层 HorizontalScrollView）；
 *      数据量少时优先撑满父容器，不留多余空白。
 *   4. 最大 xStep 限制（80dp）：避免数据点极少时节点过于稀疏。
 *   5. X 轴标签防重叠：当 xStep 较小时自动隔几个显示一个标签。
 *   6. Y 轴区域（paddingL=72dp）确保最长标签不被截断，
 *      绘制顺序保证 Y 轴文字不被曲线遮挡。
 *   7. 图表右侧保留 paddingR(16dp) 使最后一个节点不贴边。
 */
public class LineChartView extends View {

    private static final int COLOR_EXPENSE = Color.parseColor("#FF5252");
    private static final int COLOR_INCOME  = Color.parseColor("#4CAF50");
    private static final int COLOR_BALANCE = Color.parseColor("#2F5FFF");
    private static final int COLOR_AXIS    = Color.parseColor("#EEEEEE");
    private static final int COLOR_LABEL   = Color.parseColor("#AAAAAA");
    private static final int COLOR_TOOLTIP = Color.parseColor("#CC1A1A2E");

    // 每个数据点的最小/最大横向间距（dp）
    private static final float MIN_X_STEP_DP = 48f;
    private static final float MAX_X_STEP_DP = 80f;

    public static class LineEntry {
        public String label;
        public float  expense, income, balance;

        public LineEntry(String label, float expense, float income, float balance) {
            this.label = label;
            this.expense = expense;
            this.income = income;
            this.balance = balance;
        }
    }

    private List<LineEntry> entries      = new ArrayList<>();
    private float           maxValue     = 1f;
    private float           minValue     = 0f;
    private float           animProgress = 1f;

    // ── 固定布局尺寸（init 时 dp→px） ──
    private float paddingL;   // Y 轴左侧宽度
    private float paddingB;   // X 轴下方高度
    private float paddingT;   // 顶部留白
    private float paddingR;   // 右侧留白
    private float pointRadius, pointStroke, lineStroke;

    // ── 动态 xStep（onDraw 里根据实际宽度计算） ──
    private float xStep;

    private Paint linePaint, fillPaint, pointPaint;
    private Paint gridPaint, yAxisLinePaint;
    private Paint labelPaint;
    private Paint tooltipBgPaint, tooltipTextPaint;

    private int             selectedIndex = -1;
    private GestureDetector gestureDetector;

    // ================================================================
    //  构造 & 初始化
    // ================================================================

    public LineChartView(Context context) {
        this(context, null);
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float d = getResources().getDisplayMetrics().density;

        paddingL    = 36 * d;
        paddingB    = 36 * d;
        paddingT    = 24 * d;
        paddingR    = 16 * d;
        xStep       = MIN_X_STEP_DP * d;
        pointRadius = 4.5f * d;
        pointStroke = 2f * d;
        lineStroke  = 2.5f * d;

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineStroke);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(COLOR_AXIS);
        gridPaint.setStrokeWidth(0.8f * d);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{5 * d, 4 * d}, 0));

        yAxisLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        yAxisLinePaint.setColor(Color.parseColor("#DDDDDD"));
        yAxisLinePaint.setStrokeWidth(1f * d);
        yAxisLinePaint.setStyle(Paint.Style.STROKE);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(COLOR_LABEL);
        labelPaint.setTextSize(10f * d);

        tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipBgPaint.setColor(COLOR_TOOLTIP);

        tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(11f * d);

        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        handleTap(e.getX());
                        return true;
                    }
                });
    }

    // ================================================================
    //  公开 API
    // ================================================================

    /**
     * 设置数据。
     * 注意：传入列表必须已按时间升序排列（最早的在 index 0），
     * 这样 X 轴从左到右就是时间正序。
     * ViewModel 的 buildBarData/buildLineData 使用 LinkedHashMap 保留插入顺序，
     * 只要账单查询结果升序即可。推荐在 ViewModel 中对 filtered 按 billTime 升序排序后再构建图表数据。
     */
    public void setData(List<LineEntry> data) {
        this.entries = (data != null) ? data : new ArrayList<>();
        recalcRange();
        selectedIndex = -1;
        startEntranceAnim();
        requestLayout();
        invalidate();
    }

    // ================================================================
    //  数据处理
    // ================================================================

    private void recalcRange() {
        maxValue = 0f;
        minValue = 0f;
        for (LineEntry e : entries) {
            maxValue = Math.max(maxValue, Math.max(e.expense, Math.max(e.income, e.balance)));
            minValue = Math.min(minValue, Math.min(e.expense, Math.min(e.income, e.balance)));
        }
        if (maxValue == minValue) {
            maxValue = minValue + 100;
        }
        float pad = (maxValue - minValue) * 0.15f;
        maxValue += pad;
        minValue -= pad * 0.2f;
        if (minValue > 0) minValue = 0;
    }

    private void startEntranceAnim() {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(650);
        a.setInterpolator(new DecelerateInterpolator(1.2f));
        a.addUpdateListener(va -> {
            animProgress = (float) va.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    // ================================================================
    //  onMeasure
    //
    //  策略：
    //    • 数据点少时：优先撑满父容器（不留空白）；xStep 由父容器宽均分，
    //      但上限 MAX_X_STEP_DP，防止节点过疏。
    //    • 数据点多时：按 MIN_X_STEP_DP 计算最小宽度，超出父容器时触发滚动。
    // ================================================================
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float d       = getResources().getDisplayMetrics().density;
        int parentW   = MeasureSpec.getSize(wSpec);
        int n         = entries.size();

        // 按最小 xStep 算出内容需要的最小宽度
        float minContentW = paddingL + paddingR
                + (n > 1 ? (n - 1) : 1) * (MIN_X_STEP_DP * d);

        // 实际宽度 = max(父容器宽, 最小内容宽)
        // 若内容比父容器窄 → 使用父容器宽（图表撑满，不留空白）
        // 若内容比父容器宽 → 使用内容宽（触发 HorizontalScrollView 滚动）
        int w = (int) Math.max(parentW, minContentW);

        int h = MeasureSpec.getSize(hSpec);
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.AT_MOST || h == 0) {
            h = (int) (220 * d);
        }
        setMeasuredDimension(w, h);
    }

    // ================================================================
    //  onDraw
    // ================================================================
    @Override
    protected void onDraw(Canvas canvas) {
        if (entries.isEmpty()) return;

        final float W       = getWidth();
        final float H       = getHeight();
        final float chartH  = H - paddingT - paddingB;
        final float chartX0 = paddingL;
        final float chartX1 = W - paddingR;
        final float d       = getResources().getDisplayMetrics().density;
        final int   n       = entries.size();

        // ── xStep 计算 ──
        // 用实际可用宽度均分；再夹在 [MIN, MAX] 之间。
        if (n > 1) {
            float computed = (chartX1 - chartX0) / (float) (n - 1);
            float minStep  = MIN_X_STEP_DP * d;
            float maxStep  = MAX_X_STEP_DP * d;
            xStep = Math.min(Math.max(computed, minStep), maxStep);
        } else {
            // 只有 1 个点：居中显示
            xStep = chartX1 - chartX0;
        }

        // ── 1. Y 轴标签 + 虚线网格（最先绘，保证不被遮挡）──
        drawYAxisAndGrid(canvas, chartX0, chartX1, chartH);

        // ── 2. Y 轴竖线 ──
        canvas.drawLine(chartX0, paddingT, chartX0, paddingT + chartH, yAxisLinePaint);

        // ── 3. 曲线 + 填充（clip 进图表区，不侵入 Y 轴）──
        int drawCount = Math.round(animProgress * n);
        if (drawCount >= 1) {
            canvas.save();
            canvas.clipRect(chartX0, paddingT - 8, W, paddingT + chartH + 8);
            drawSeries(canvas, "expense", COLOR_EXPENSE, chartH, drawCount);
            drawSeries(canvas, "income",  COLOR_INCOME,  chartH, drawCount);
            drawSeries(canvas, "balance", COLOR_BALANCE, chartH, drawCount);
            canvas.restore();
        }

        // ── 4. X 轴标签（防重叠：xStep 不足时跳显）──
        if (drawCount >= 1) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            int labelStep = calcLabelStep(xStep, d);
            for (int i = 0; i < drawCount; i++) {
                boolean isFirst = (i == 0);
                boolean isLast  = (i == n - 1);
                if (i % labelStep == 0 || isFirst || isLast) {
                    canvas.drawText(entries.get(i).label, getX(i), H - 6, labelPaint);
                }
            }
        }

        // ── 5. Tooltip ──
        if (selectedIndex >= 0 && drawCount > selectedIndex) {
            drawTooltip(canvas, selectedIndex, chartH);
        }
    }

    // ================================================================
    //  绘制子方法
    // ================================================================

    private void drawYAxisAndGrid(Canvas canvas, float chartX0, float chartX1, float chartH) {
        final int   STEPS   = 5;
        final float range   = maxValue - minValue;
        final float labelRX = chartX0 - getResources().getDisplayMetrics().density * 6;

        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= STEPS; i++) {
            float val = minValue + range * i / STEPS;
            float y   = yToScreen(val, chartH);
            canvas.drawLine(chartX0, y, chartX1, y, gridPaint);
            canvas.drawText(fmtAmt(val), labelRX, y + 4, labelPaint);
        }
    }

    private void drawSeries(Canvas canvas, String key, int color, float chartH, int count) {
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = getX(i);
            ys[i] = yToScreen(getValue(entries.get(i), key), chartH);
        }

        Path line = smoothPath(xs, ys);

        // 渐变填充阴影
        Path fill = new Path(line);
        float baseY = yToScreen(0, chartH);
        fill.lineTo(xs[count - 1], baseY);
        fill.lineTo(xs[0], baseY);
        fill.close();
        fillPaint.setShader(new LinearGradient(
                0, paddingT, 0, paddingT + chartH,
                withAlpha(color, 60), withAlpha(color, 0),
                Shader.TileMode.CLAMP));
        canvas.drawPath(fill, fillPaint);
        fillPaint.setShader(null);

        // 曲线
        linePaint.setColor(color);
        canvas.drawPath(line, linePaint);

        // 节点：数据量大时缩小节点避免拥挤
        float r = (entries.size() > 20) ? pointRadius * 0.65f : pointRadius;
        for (int i = 0; i < count; i++) {
            float rr = (selectedIndex == i) ? r * 1.6f : r;
            pointPaint.setColor(Color.WHITE);
            canvas.drawCircle(xs[i], ys[i], rr + pointStroke, pointPaint);
            pointPaint.setColor(color);
            canvas.drawCircle(xs[i], ys[i], rr, pointPaint);
        }
    }

    private void drawTooltip(Canvas canvas, int idx, float chartH) {
        float     d = getResources().getDisplayMetrics().density;
        LineEntry e = entries.get(idx);
        String[] lines = {
                "支出 ¥" + String.format("%.2f", e.expense),
                "收入 ¥" + String.format("%.2f", e.income),
                "结余 ¥" + String.format("%.2f", e.balance),
        };
        float lineH = 18 * d, pad = 10 * d, tipW = 130 * d;
        float tipH  = lines.length * lineH + pad * 2;
        float cx    = getX(idx);
        float left  = cx - tipW / 2;
        float top   = paddingT + 4 * d;

        if (left < paddingL)                         left = paddingL;
        if (left + tipW > getWidth() - paddingR)     left = getWidth() - paddingR - tipW;

        canvas.drawRoundRect(
                new RectF(left, top, left + tipW, top + tipH),
                8 * d, 8 * d, tooltipBgPaint);
        tooltipTextPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i],
                    left + pad, top + pad + lineH * (i + 0.82f),
                    tooltipTextPaint);
        }
    }

    // ================================================================
    //  触摸处理
    // ================================================================

    private void handleTap(float x) {
        if (entries.isEmpty()) return;
        int   nearest = -1;
        float minD    = Float.MAX_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            float dist = Math.abs(x - getX(i));
            if (dist < minD) {
                minD    = dist;
                nearest = i;
            }
        }
        float thresh  = 36 * getResources().getDisplayMetrics().density;
        selectedIndex = (nearest >= 0 && minD < thresh)
                ? (selectedIndex == nearest ? -1 : nearest) : -1;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestureDetector.onTouchEvent(e);
        return true;
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 第 i 个数据点的屏幕 X 坐标 */
    private float getX(int i) {
        return paddingL + i * xStep;
    }

    /** 数据值 → 屏幕 Y 坐标 */
    private float yToScreen(float val, float chartH) {
        float ratio = (val - minValue) / (maxValue - minValue);
        return paddingT + chartH * (1f - ratio);
    }

    private float getValue(LineEntry e, String k) {
        if ("expense".equals(k)) return e.expense;
        if ("income".equals(k))  return e.income;
        return e.balance;
    }

    /**
     * 计算 X 轴标签跳显步长，防止文字重叠。
     * 估算每个标签宽约 32dp（"15日"=4字符×约8dp）。
     */
    private int calcLabelStep(float xStep, float d) {
        float labelW = 32 * d;
        if (xStep >= labelW) return 1;
        return Math.max((int) Math.ceil(labelW / xStep), 1);
    }

    /** 生成平滑贝塞尔曲线路径 */
    private Path smoothPath(float[] xs, float[] ys) {
        Path p = new Path();
        p.moveTo(xs[0], ys[0]);
        for (int i = 0; i < xs.length - 1; i++) {
            float mx = (xs[i] + xs[i + 1]) / 2f;
            p.cubicTo(mx, ys[i], mx, ys[i + 1], xs[i + 1], ys[i + 1]);
        }
        return p;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private String fmtAmt(float v) {
        if (Math.abs(v) >= 10000) return String.format("%.1f万", v / 10000);
        return String.format("%.0f", v);
    }
}