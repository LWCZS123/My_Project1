package com.example.my_project1.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
 * 柱状图自定义 View
 * - 支出（红色）、收入（绿色）、结余（蓝色）三组柱子
 * - 圆角柱子
 * - 点击弹出 Tooltip 显示具体数值
 * - 支持水平滑动（包裹在 HorizontalScrollView 内）
 * - 入场动画
 */
public class BarChartView extends View {

    // ===== 颜色 =====
    private static final int COLOR_EXPENSE_TOP    = Color.parseColor("#FF5252");
    private static final int COLOR_EXPENSE_BOTTOM = Color.parseColor("#FFCDD2");
    private static final int COLOR_INCOME_TOP     = Color.parseColor("#4CAF50");
    private static final int COLOR_INCOME_BOTTOM  = Color.parseColor("#A5D6A7");
    private static final int COLOR_BALANCE_TOP    = Color.parseColor("#2F5FFF");
    private static final int COLOR_BALANCE_BOTTOM = Color.parseColor("#90CAF9");
    private static final int COLOR_TOOLTIP_BG     = Color.parseColor("#CC1A1A2E");
    private static final int COLOR_AXIS           = Color.parseColor("#E0E0E0");
    private static final int COLOR_LABEL          = Color.parseColor("#888888");

    // ===== 尺寸 =====
    private float barWidth;
    private float groupGap;   // 组间距
    private float barGap;     // 组内柱间距
    private float paddingLeft;
    private float paddingBottom;
    private float paddingTop;
    private float cornerRadius;

    // ===== 数据 =====
    public static class BarEntry {
        public String label;   // X 轴标签，如 "3/1"
        public float expense;
        public float income;
        public float balance;
        public BarEntry(String label, float expense, float income, float balance) {
            this.label = label; this.expense = expense;
            this.income = income; this.balance = balance;
        }
    }

    private List<BarEntry> entries = new ArrayList<>();
    private float maxValue = 1f;

    // ===== 动画 =====
    private float animProgress = 1f; // 0→1

    // ===== Tooltip =====
    private int selectedIndex = -1;
    private Paint tooltipPaint;
    private Paint tooltipTextPaint;

    // ===== Paints =====
    private Paint barPaint;
    private Paint axisPaint;
    private Paint labelPaint;
    private Paint gridPaint;

    private GestureDetector gestureDetector;

    public BarChartView(Context context) { this(context, null); }
    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;
        barWidth     = 18 * density;
        groupGap     = 28 * density;
        barGap       = 4  * density;
        paddingLeft  = 36 * density;
        paddingBottom= 40 * density;
        paddingTop   = 24 * density;
        cornerRadius = 6  * density;

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(COLOR_AXIS);
        axisPaint.setStrokeWidth(1 * density);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(COLOR_AXIS);
        gridPaint.setStrokeWidth(0.5f * density);
        gridPaint.setStyle(Paint.Style.STROKE);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(COLOR_LABEL);
        labelPaint.setTextSize(10 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipPaint.setColor(COLOR_TOOLTIP_BG);

        tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(11 * density);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }
        });
    }

    /** 设置数据并触发入场动画 */
    public void setData(List<BarEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
        this.maxValue = 1f;
        for (BarEntry e : this.entries) {
            maxValue = Math.max(maxValue, Math.max(e.expense, Math.max(e.income, Math.abs(e.balance))));
        }
        selectedIndex = -1;
        startAnimation();
        requestLayout();
        invalidate();
    }

    private void startAnimation() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        float groupWidth = barWidth * 3 + barGap * 2;
        float totalWidth = paddingLeft + entries.size() * (groupWidth + groupGap) + groupGap;
        int minWidth = (int) Math.max(parentWidth, totalWidth);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST || height == 0) {
            height = (int) (220 * getResources().getDisplayMetrics().density);
        }
        setMeasuredDimension(minWidth, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (entries.isEmpty()) return;
        float width  = getWidth();
        float height = getHeight();
        float chartH = height - paddingBottom - paddingTop;
        float chartW = width  - paddingLeft;

        // Y 轴刻度（5格）
        int ySteps = 5;
        float yStepValue = maxValue / ySteps;
        Paint yLabelPaint = new Paint(labelPaint);
        yLabelPaint.setTextAlign(Paint.Align.RIGHT);

        for (int i = 0; i <= ySteps; i++) {
            float y = paddingTop + chartH - (chartH * i / ySteps);
            // 网格线
            canvas.drawLine(paddingLeft, y, width, y, gridPaint);
            // Y 轴标签
            String yLabel = formatAmount(yStepValue * i);
            canvas.drawText(yLabel, paddingLeft - 6, y + 4, yLabelPaint);
        }

        // X 轴线
        canvas.drawLine(paddingLeft, paddingTop + chartH, width, paddingTop + chartH, axisPaint);

        float groupWidth = barWidth * 3 + barGap * 2;

        for (int i = 0; i < entries.size(); i++) {
            BarEntry entry = entries.get(i);
            float groupLeft = paddingLeft + groupGap / 2 + i * (groupWidth + groupGap);
            float centerX   = groupLeft + groupWidth / 2f;

            // 绘制三根柱子
            drawBar(canvas, groupLeft, paddingTop, chartH, entry.expense, maxValue,
                    COLOR_EXPENSE_TOP, COLOR_EXPENSE_BOTTOM, animProgress);
            drawBar(canvas, groupLeft + barWidth + barGap, paddingTop, chartH, entry.income, maxValue,
                    COLOR_INCOME_TOP, COLOR_INCOME_BOTTOM, animProgress);
            drawBar(canvas, groupLeft + (barWidth + barGap) * 2, paddingTop, chartH, Math.abs(entry.balance), maxValue,
                    COLOR_BALANCE_TOP, COLOR_BALANCE_BOTTOM, animProgress);

            // X 轴标签
            canvas.drawText(entry.label, centerX, height - 8, labelPaint);

            // 选中高亮背景
            if (selectedIndex == i) {
                Paint hlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                hlPaint.setColor(Color.parseColor("#1A2F5FFF"));
                RectF hlRect = new RectF(groupLeft - 6, paddingTop, groupLeft + groupWidth + 6, paddingTop + chartH);
                canvas.drawRoundRect(hlRect, 8, 8, hlPaint);
            }
        }

        // 绘制 Tooltip
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            drawTooltip(canvas, selectedIndex, chartH);
        }
    }

    private void drawBar(Canvas canvas, float left, float topPad, float chartH,
                         float value, float maxVal, int colorTop, int colorBottom, float progress) {
        float barH = chartH * (value / maxVal) * progress;
        float top  = topPad + chartH - barH;
        float right = left + barWidth;
        float bottom = topPad + chartH;

        barPaint.setShader(new LinearGradient(left, top, left, bottom,
                colorTop, colorBottom, Shader.TileMode.CLAMP));
        RectF rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint);
        barPaint.setShader(null);
    }

    private void drawTooltip(Canvas canvas, int index, float chartH) {
        BarEntry e = entries.get(index);
        float density = getResources().getDisplayMetrics().density;
        float groupWidth = barWidth * 3 + barGap * 2;
        float groupLeft  = paddingLeft + groupGap / 2 + index * (groupWidth + groupGap);
        float centerX    = groupLeft + groupWidth / 2f;

        String[] lines = {
                "支出：¥" + String.format("%.2f", e.expense),
                "收入：¥" + String.format("%.2f", e.income),
                "结余：¥" + String.format("%.2f", e.balance)
        };

        float lineH    = 18 * density;
        float padding  = 10 * density;
        float tipW     = 120 * density;
        float tipH     = lines.length * lineH + padding * 2;

        float tipLeft = centerX - tipW / 2;
        float tipTop  = paddingTop + 4 * density;

        // 防止超出左边界
        if (tipLeft < paddingLeft) tipLeft = paddingLeft;
        // 防止超出右边界
        if (tipLeft + tipW > getWidth()) tipLeft = getWidth() - tipW;

        RectF tipRect = new RectF(tipLeft, tipTop, tipLeft + tipW, tipTop + tipH);
        canvas.drawRoundRect(tipRect, 8 * density, 8 * density, tooltipPaint);

        tooltipTextPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], tipLeft + padding, tipTop + padding + lineH * (i + 0.8f), tooltipTextPaint);
        }
    }

    private void handleTap(float x, float y) {
        float groupWidth = barWidth * 3 + barGap * 2;
        for (int i = 0; i < entries.size(); i++) {
            float groupLeft = paddingLeft + groupGap / 2 + i * (groupWidth + groupGap);
            if (x >= groupLeft - 10 && x <= groupLeft + groupWidth + 10) {
                selectedIndex = (selectedIndex == i) ? -1 : i;
                invalidate();
                return;
            }
        }
        selectedIndex = -1;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private String formatAmount(float v) {
        if (v >= 10000) return String.format("%.1f万", v / 10000);
        if (v >= 1000)  return String.format("%.0f", v);
        return String.format("%.0f", v);
    }
}