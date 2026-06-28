package com.example.my_project1.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.core.content.ContextCompat;

import com.example.my_project1.R;

import java.text.DecimalFormat;

import io.reactivex.annotations.NonNull;

/**
 * 自定义日期视图 - 支持显示金额和农历
 */
public class CustomDayView extends androidx.appcompat.widget.AppCompatTextView {

    private String bottomText = "";
    private int bottomTextColor = Color.parseColor("#999999");
    private Paint bottomTextPaint;
    private static final float BOTTOM_TEXT_SIZE = 24f; // sp

    public CustomDayView(Context context) {
        super(context);
        init();
    }

    public CustomDayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomDayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bottomTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bottomTextPaint.setTextAlign(Paint.Align.CENTER);
        bottomTextPaint.setTextSize(BOTTOM_TEXT_SIZE * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setBottomText(String text, int color) {
        this.bottomText = text;
        this.bottomTextColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (bottomText != null && !bottomText.isEmpty()) {
            bottomTextPaint.setColor(bottomTextColor);

            float x = getWidth() / 2f;
            float y = getHeight() - 8; // 距离底部8dp

            canvas.drawText(bottomText, x, y, bottomTextPaint);
        }
    }

    /**
     * 设置金额显示
     */
    public void setAmountText(double amount, boolean isExpense) {
        if (amount > 0) {
            DecimalFormat df = new DecimalFormat("#");
            String amountText = df.format(amount);
            int color = isExpense ?
                    ContextCompat.getColor(getContext(), R.color.red) :
                    Color.parseColor("#4CAF50");
            setBottomText(amountText, color);
        } else {
            setBottomText("", Color.TRANSPARENT);
        }
    }

    /**
     * 设置农历显示
     */
    public void setLunarText(String lunar, boolean isFestival, boolean isSolarTerm) {
        if (lunar != null && !lunar.isEmpty()) {
            int color = (isFestival || isSolarTerm) ?
                    ContextCompat.getColor(getContext(), R.color.red) :
                    Color.parseColor("#999999");
            setBottomText(lunar, color);
        } else {
            setBottomText("", Color.TRANSPARENT);
        }
    }
}