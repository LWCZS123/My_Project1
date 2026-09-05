package com.example.my_project1.ui.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IPieDataSet;
import com.github.mikephil.charting.renderer.PieChartRenderer;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * 自定义 PieChart 渲染器，修复视觉 Bug 并实现完美圆角
 */
public class RoundedPieChartRenderer extends PieChartRenderer {

    public RoundedPieChartRenderer(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
    }

    @Override
    protected void drawDataSet(Canvas c, IPieDataSet dataSet) {
        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();
        float rotationAngle = mChart.getRotationAngle();

        float[] drawAngles = mChart.getDrawAngles();

        RectF circleBox = mChart.getCircleBox();
        float radius = mChart.getRadius();
        float holeRadiusPercent = mChart.getHoleRadius() / 100f;
        
        // 计算圆环宽度和中心圆环半径
        float donutWidth = radius * (1f - holeRadiusPercent);
        float middleRadius = radius - (donutWidth / 2f);

        // 计算 Cap.ROUND 带来的额外角度（圆帽半径对应的角度）
        // 圆帽半径 r = donutWidth / 2
        float capAngle = (float) Math.toDegrees((donutWidth / 2f) / middleRadius);

        // 保存画笔状态
        Paint.Style oldStyle = mRenderPaint.getStyle();
        float oldStrokeWidth = mRenderPaint.getStrokeWidth();
        Paint.Cap oldCap = mRenderPaint.getStrokeCap();

        // 使用 STROKE + Cap.ROUND 是最稳定的圆角方案，不会产生多余的边框线
        mRenderPaint.setStyle(Paint.Style.STROKE);
        mRenderPaint.setStrokeWidth(donutWidth);
        mRenderPaint.setStrokeCap(Paint.Cap.ROUND);
        mRenderPaint.setAntiAlias(true);

        RectF arcBox = new RectF(
                circleBox.centerX() - middleRadius,
                circleBox.centerY() - middleRadius,
                circleBox.centerX() + middleRadius,
                circleBox.centerY() + middleRadius
        );

        float angle = rotationAngle;
        for (int i = 0; i < drawAngles.length; i++) {
            float sliceAngle = drawAngles[i];
            float sweepAngle = sliceAngle * phaseY;

            if (sweepAngle > 0.1f) {
                mRenderPaint.setColor(dataSet.getColor(i));
                
                // 计算间隙角度 (SliceSpace)
                float spacingAngle = (float) Math.toDegrees(dataSet.getSliceSpace() / middleRadius);
                
                // 核心计算：从总角度中减去两个圆帽的角度和间隙角度
                float actualSweep = sweepAngle - (2 * capAngle) - spacingAngle;
                
                // 如果扇区太小，保证至少绘制一个小点
                if (actualSweep < 0) {
                    actualSweep = 0.1f;
                }
                
                // 起始角度偏移：圆帽角度 + 一半的间隙
                float actualStart = angle + capAngle + (spacingAngle / 2f);

                c.drawArc(arcBox, actualStart, actualSweep, false, mRenderPaint);
            }

            angle += sliceAngle * phaseX;
        }

        // 还原画笔状态
        mRenderPaint.setStyle(oldStyle);
        mRenderPaint.setStrokeWidth(oldStrokeWidth);
        mRenderPaint.setStrokeCap(oldCap);
    }

    @Override
    public void drawHighlighted(Canvas c, Highlight[] indices) {
        // 覆盖此方法，防止点击时触发默认的直角高亮样式
    }
}
