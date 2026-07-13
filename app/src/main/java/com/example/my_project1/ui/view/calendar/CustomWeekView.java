package com.example.my_project1.ui.view.calendar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.my_project1.data.model.calendar.DailyStat;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.WeekView;

import java.util.List;
import java.util.Locale;

public class CustomWeekView extends WeekView {

    private final Paint mIncomePaint = new Paint();
    private final Paint mExpensePaint = new Paint();
    private final Paint mHeatMapPaint = new Paint();
    private final Paint mHolidayPaint = new Paint();
    private final Paint mTagPaint = new Paint();
    private final int mPadding;

    public CustomWeekView(Context context) {
        super(context);
        mIncomePaint.setAntiAlias(true);
        mIncomePaint.setTextAlign(Paint.Align.CENTER);
        mIncomePaint.setColor(0xFF4CAF50);
        mIncomePaint.setFakeBoldText(true);

        mExpensePaint.setAntiAlias(true);
        mExpensePaint.setTextAlign(Paint.Align.CENTER);
        mExpensePaint.setColor(0xFFFF5252);
        mExpensePaint.setFakeBoldText(true);

        mHeatMapPaint.setAntiAlias(true);
        mHeatMapPaint.setStyle(Paint.Style.FILL);

        mHolidayPaint.setAntiAlias(true);
        mHolidayPaint.setStyle(Paint.Style.FILL);
        mHolidayPaint.setColor(0xFFE3F2FD);

        mTagPaint.setAntiAlias(true);
        mTagPaint.setTextSize(dipToPx(getContext(), 8));

        mPadding = dipToPx(getContext(), 3);

        // 加深农历字体
        mCurMonthLunarTextPaint.setColor(0xFF666666);
        mSchemeLunarTextPaint.setColor(0xFF666666);
        mOtherMonthLunarTextPaint.setColor(0xFFBBBBBB);
    }

    @Override
    protected boolean onDrawSelected(Canvas canvas, Calendar calendar, int x, boolean hasScheme) {
        mSelectedPaint.setStyle(Paint.Style.FILL);
        RectF rectF = new RectF(x + mPadding, mPadding, x + mItemWidth - mPadding, mItemHeight - mPadding);
        canvas.drawRoundRect(rectF, dipToPx(getContext(), 8), dipToPx(getContext(), 8), mSelectedPaint);
        return true;
    }

    @Override
    protected void onDrawScheme(Canvas canvas, Calendar calendar, int x) {
        DailyStat stat = getDailyStat(calendar);
        if (stat == null) return;

        RectF rectF = new RectF(x + mPadding, mPadding, x + mItemWidth - mPadding, mItemHeight - mPadding);

        if (stat.isHoliday) {
            canvas.drawRoundRect(rectF, dipToPx(getContext(), 8), dipToPx(getContext(), 8), mHolidayPaint);
        }

        double net = stat.income - stat.expense;
        if (Math.abs(net) > 0 || stat.count > 0) {
            if (net >= 0) {
                mHeatMapPaint.setColor(0xFF81C784);
                int alpha = (int) Math.min(60 + (stat.income / 500.0) * 120, 200);
                mHeatMapPaint.setAlpha(alpha);
            } else {
                mHeatMapPaint.setColor(0xFFE57373);
                int alpha = (int) Math.min(60 + (stat.expense / 500.0) * 120, 200);
                mHeatMapPaint.setAlpha(alpha);
            }
            canvas.drawRoundRect(rectF, dipToPx(getContext(), 8), dipToPx(getContext(), 8), mHeatMapPaint);
        }

        if (stat.dayTag != null) {
            if ("休".equals(stat.dayTag)) {
                mTagPaint.setColor(0xFF3F8BFF);
            } else {
                mTagPaint.setColor(0xFFFF5252);
            }
            canvas.drawText(stat.dayTag, x + mItemWidth - dipToPx(getContext(), 10), dipToPx(getContext(), 10), mTagPaint);
        }

        float cx = x + mItemWidth / 2f;
        float baseLine = mItemHeight - dipToPx(getContext(), 6);

        mIncomePaint.setTextSize(dipToPx(getContext(), 9));
        mExpensePaint.setTextSize(dipToPx(getContext(), 9));

        if (stat.income > 0 && stat.expense > 0) {
            canvas.drawText("+" + formatAmt(stat.income), cx, baseLine - dipToPx(getContext(), 11), mIncomePaint);
            canvas.drawText("-" + formatAmt(stat.expense), cx, baseLine, mExpensePaint);
        } else if (stat.income > 0) {
            canvas.drawText("+" + formatAmt(stat.income), cx, baseLine, mIncomePaint);
        } else if (stat.expense > 0) {
            canvas.drawText("-" + formatAmt(stat.expense), cx, baseLine, mExpensePaint);
        }
    }

    private String formatAmt(double amt) {
        if (amt >= 10000) return String.format(Locale.getDefault(), "%.1fk", amt / 1000.0);
        return String.format(Locale.getDefault(), "%.0f", amt);
    }

    @Override
    protected void onDrawText(Canvas canvas, Calendar calendar, int x, boolean hasScheme, boolean isSelected) {
        float cx = x + mItemWidth / 2f;
        float top = -mItemHeight / 6f;

        DailyStat stat = getDailyStat(calendar);
        boolean reallyHasBills = stat != null && (stat.income > 0 || stat.expense > 0 || stat.count > 0);

        if (isSelected) {
            canvas.drawText(String.valueOf(calendar.getDay()), cx, mTextBaseLine + top, mSelectTextPaint);
            if (!reallyHasBills) {
                canvas.drawText(calendar.getLunar(), cx, mTextBaseLine + mItemHeight / 10f, mSelectedLunarTextPaint);
            }
        } else {
            canvas.drawText(String.valueOf(calendar.getDay()), cx, mTextBaseLine + top,
                    calendar.isCurrentMonth() ? mCurMonthTextPaint : mOtherMonthTextPaint);
            if (!reallyHasBills) {
                canvas.drawText(calendar.getLunar(), cx, mTextBaseLine + mItemHeight / 10f,
                        calendar.isCurrentMonth() ? mCurMonthLunarTextPaint : mOtherMonthLunarTextPaint);
            }
        }
    }

    private DailyStat getDailyStat(Calendar calendar) {
        List<Calendar.Scheme> schemes = calendar.getSchemes();
        if (schemes == null || schemes.isEmpty()) return null;
        for (Calendar.Scheme s : schemes) {
            if (s.getObj() instanceof DailyStat) return (DailyStat) s.getObj();
        }
        return null;
    }

    private static int dipToPx(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
