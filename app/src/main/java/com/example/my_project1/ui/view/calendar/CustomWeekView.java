package com.example.my_project1.ui.view.calendar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.my_project1.data.model.calendar.DailyStat;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.WeekView;

import java.util.List;

public class CustomWeekView extends WeekView {

    private static final String[] DAY_TEXT = createDayText();

    private final Paint mIncomePaint = new Paint();
    private final Paint mExpensePaint = new Paint();
    private final Paint mHeatMapPaint = new Paint();
    private final Paint mHolidayPaint = new Paint();
    private final Paint mTagPaint = new Paint();
    private final RectF mDrawRect = new RectF();
    private final int mPadding;
    private final float mRoundRadius;
    private final float mTagMarginRight;
    private final float mTagMarginTop;
    private final float mAmountBaselineOffset;
    private final float mDualAmountOffset;

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
        mTagPaint.setTextSize(dipToPx(getContext(), 10));
        mTagPaint.setFakeBoldText(true);

        mPadding = dipToPx(getContext(), 3);
        mRoundRadius = dipToPx(getContext(), 8);
        mTagMarginRight = dipToPx(getContext(), 10);
        mTagMarginTop = dipToPx(getContext(), 11);
        mAmountBaselineOffset = dipToPx(getContext(), 6);
        mDualAmountOffset = dipToPx(getContext(), 11);
        float amountTextSize = dipToPx(getContext(), 9);
        mIncomePaint.setTextSize(amountTextSize);
        mExpensePaint.setTextSize(amountTextSize);

        // 加深农历字体
        mCurMonthLunarTextPaint.setColor(0xFF666666);
        mSchemeLunarTextPaint.setColor(0xFF666666);
        mOtherMonthLunarTextPaint.setColor(0xFFBBBBBB);
    }

    @Override
    protected boolean onDrawSelected(Canvas canvas, Calendar calendar, int x, boolean hasScheme) {
        mSelectedPaint.setStyle(Paint.Style.FILL);
        mDrawRect.set(x + mPadding, mPadding, x + mItemWidth - mPadding, mItemHeight - mPadding);
        canvas.drawRoundRect(mDrawRect, mRoundRadius, mRoundRadius, mSelectedPaint);
        return true;
    }

    @Override
    protected void onDrawScheme(Canvas canvas, Calendar calendar, int x) {
        DailyStat stat = getDailyStat(calendar);
        if (stat == null) return;

        mDrawRect.set(x + mPadding, mPadding, x + mItemWidth - mPadding, mItemHeight - mPadding);

        if (stat.isHoliday) {
            canvas.drawRoundRect(mDrawRect, mRoundRadius, mRoundRadius, mHolidayPaint);
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
            canvas.drawRoundRect(mDrawRect, mRoundRadius, mRoundRadius, mHeatMapPaint);
        }

        if (stat.dayTag != null) {
            if ("休".equals(stat.dayTag)) {
                mTagPaint.setColor(0xFF3F8BFF);
            } else {
                mTagPaint.setColor(0xFFFF5252);
            }
            canvas.drawText(stat.dayTag, x + mItemWidth - mTagMarginRight, mTagMarginTop, mTagPaint);
        }

        float cx = x + mItemWidth / 2f;
        float baseLine = mItemHeight - mAmountBaselineOffset;

        if (stat.income > 0 && stat.expense > 0) {
            canvas.drawText(amountText(stat.signedIncomeText, "+", stat.income), cx, baseLine - mDualAmountOffset, mIncomePaint);
            canvas.drawText(amountText(stat.signedExpenseText, "-", stat.expense), cx, baseLine, mExpensePaint);
        } else if (stat.income > 0) {
            canvas.drawText(amountText(stat.signedIncomeText, "+", stat.income), cx, baseLine, mIncomePaint);
        } else if (stat.expense > 0) {
            canvas.drawText(amountText(stat.signedExpenseText, "-", stat.expense), cx, baseLine, mExpensePaint);
        }
    }

    private String amountText(String cached, String prefix, double amt) {
        if (cached != null) return cached;
        if (amt >= 10000) return prefix + (int) (amt / 1000) + "k";
        return prefix + (int) amt;
    }

    @Override
    protected void onDrawText(Canvas canvas, Calendar calendar, int x, boolean hasScheme, boolean isSelected) {
        float cx = x + mItemWidth / 2f;
        float top = -mItemHeight / 6f;

        DailyStat stat = getDailyStat(calendar);
        boolean reallyHasBills = stat != null && (stat.income > 0 || stat.expense > 0 || stat.count > 0);

        if (isSelected) {
            canvas.drawText(DAY_TEXT[calendar.getDay()], cx, mTextBaseLine + top, mSelectTextPaint);
            if (!reallyHasBills) {
                canvas.drawText(calendar.getLunar(), cx, mTextBaseLine + mItemHeight / 10f, mSelectedLunarTextPaint);
            }
        } else {
            canvas.drawText(DAY_TEXT[calendar.getDay()], cx, mTextBaseLine + top,
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

    private static String[] createDayText() {
        String[] result = new String[32];
        for (int day = 1; day < result.length; day++) result[day] = String.valueOf(day);
        return result;
    }
}
