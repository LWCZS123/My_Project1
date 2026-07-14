package com.example.my_project1.ui.view.calendar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.MonthView;

/**
 * 专为日期选择器定制的月份视图 - 极简高精版
 * 1. 采用分布式坐标计算，彻底解决数字与农历重合
 * 2. 正圆选中态背景
 * 3. 移除账单等无关逻辑
 */
public class PickerMonthView extends MonthView {

    private final Paint mTagPaint = new Paint();

    public PickerMonthView(Context context) {
        super(context);
        
        mTagPaint.setAntiAlias(true);
        mTagPaint.setTextSize(dipToPx(getContext(), 7));
        mTagPaint.setTextAlign(Paint.Align.CENTER);

        // 统一农历大小
        float lunarSize = dipToPx(getContext(), 9);
        mCurMonthLunarTextPaint.setTextSize(lunarSize);
        mOtherMonthLunarTextPaint.setTextSize(lunarSize);
        mSelectedLunarTextPaint.setTextSize(lunarSize);

        mCurMonthLunarTextPaint.setColor(0xFF999999);
        mOtherMonthLunarTextPaint.setColor(0xFFD0D0D0);
    }

    @Override
    protected boolean onDrawSelected(Canvas canvas, Calendar calendar, int x, int y, boolean hasScheme) {
        mSelectedPaint.setStyle(Paint.Style.FILL);
        mSelectedPaint.setAntiAlias(true);
        
        float cx = x + mItemWidth / 2f;
        float cy = y + mItemHeight / 2f;
        
        // 精致正圆，半径取单元格高度的 40%
        float radius = mItemHeight * 0.4f;
        canvas.drawCircle(cx, cy, radius, mSelectedPaint);
        return true;
    }

    @Override
    protected void onDrawScheme(Canvas canvas, Calendar calendar, int x, int y) {
        if (!calendar.isCurrentMonth()) return;
        if (calendar.getScheme() != null) {
            String tag = calendar.getScheme();
            if ("休".equals(tag)) mTagPaint.setColor(0xFF3F8BFF);
            else if ("班".equals(tag)) mTagPaint.setColor(0xFFFF5252);
            else return;
            canvas.drawText(tag, x + mItemWidth - dipToPx(getContext(), 8), y + dipToPx(getContext(), 10), mTagPaint);
        }
    }

    @Override
    protected void onDrawText(Canvas canvas, Calendar calendar, int x, int y, boolean hasScheme, boolean isSelected) {
        float cx = x + mItemWidth / 2f;
        
        // 关键：数字位于单元格 30% 处，农历位于 70% 处，拉开间距
        float dayCenterY = y + mItemHeight * 0.32f;
        float lunarCenterY = y + mItemHeight * 0.72f;

        Paint dayPaint = isSelected ? mSelectTextPaint : (calendar.isCurrentMonth() ? mCurMonthTextPaint : mOtherMonthTextPaint);
        Paint lunarPaint = isSelected ? mSelectedLunarTextPaint : (calendar.isCurrentMonth() ? mCurMonthLunarTextPaint : mOtherMonthLunarTextPaint);

        canvas.drawText(String.valueOf(calendar.getDay()), cx, getBaseline(dayPaint, dayCenterY), dayPaint);
        canvas.drawText(calendar.getLunar(), cx, getBaseline(lunarPaint, lunarCenterY), lunarPaint);
    }

    private float getBaseline(Paint paint, float centerY) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return centerY - (metrics.ascent + metrics.descent) / 2f;
    }

    private static int dipToPx(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
