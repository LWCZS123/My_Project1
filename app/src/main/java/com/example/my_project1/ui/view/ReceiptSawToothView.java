package com.example.my_project1.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * ReceiptSawToothView
 * -----------------------------------------------------------
 * 自定义锯齿边缘 View，用于在购物小票卡片底部绘制锯齿撕边效果。
 */
public class ReceiptSawToothView extends View {

    private Paint paint;
    private Path path;

    // 锯齿单个宽度与高度（单位 px，逻辑自适应）
    private float toothWidth = 24f; // 锯齿宽度
    private float toothHeight = 16f; // 锯齿高度
    private int paperColor = Color.WHITE; // 小票纸张颜色

    public ReceiptSawToothView(Context context) {
        super(context);
        init();
    }

    public ReceiptSawToothView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReceiptSawToothView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(paperColor);

        path = new Path();

        // 转换 dp 到 px 保证不同机型展示效果一致
        float density = getResources().getDisplayMetrics().density;
        toothWidth = 10f * density;
        toothHeight = 8f * density;
    }

    public void setPaperColor(int color) {
        this.paperColor = color;
        if (paint != null) {
            paint.setColor(paperColor);
            invalidate();
        }
    }

    @Override
    protected void onDraw(@androidx.annotation.NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        path.reset();
        // 从左上角开始
        path.moveTo(0, 0);

        // 计算锯齿数量
        int count = (int) Math.ceil(width / toothWidth);
        if (count == 0) count = 1;
        float actualToothWidth = (float) width / count;

        // 沿底部绘制锯齿三角形
        for (int i = 0; i < count; i++) {
            float x1 = (i + 0.5f) * actualToothWidth;
            float y1 = toothHeight;
            float x2 = (i + 1f) * actualToothWidth;
            float y2 = 0;

            path.lineTo(x1, y1);
            path.lineTo(x2, y2);
        }

        path.lineTo(width, 0);
        path.close();

        canvas.drawPath(path, paint);
    }
}
