package com.example.my_project1.ui.view.calendar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.my_project1.data.model.calendar.DailyStat;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.MonthView;

import java.util.List;
import java.util.Locale;

public class CustomMonthView extends MonthView {

    private final Paint mIncomePaint = new Paint();
    private final Paint mExpensePaint = new Paint();
    private final Paint mHeatMapPaint = new Paint();
    private final Paint mHolidayPaint = new Paint();
    private final Paint mTagPaint = new Paint();
    
    // 缓存尺寸，避免 onDraw 中重复计算
    private final int mPadding;
    private final float mTagTextSize;
    private final float mTagMarginRight;
    private final float mTagMarginTop;
    private final float mCapsuleTextSize;
    private final float mCapsuleOffset1;
    private final float mCapsuleOffset2;
    private final float mRoundRadius;

    public CustomMonthView(Context context) {
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
        
        // 预计算尺寸
        mPadding = dipToPx(getContext(), 3);
        mTagTextSize = dipToPx(getContext(), 8);
        mTagMarginRight = dipToPx(getContext(), 10);
        mTagMarginTop = dipToPx(getContext(), 10);
        mCapsuleTextSize = dipToPx(getContext(), 9);
        mCapsuleOffset1 = dipToPx(getContext(), 11);
        mCapsuleOffset2 = dipToPx(getContext(), 6);
        mRoundRadius = dipToPx(getContext(), 8);

        mTagPaint.setTextSize(mTagTextSize);
        mIncomePaint.setTextSize(mCapsuleTextSize);
        mExpensePaint.setTextSize(mCapsuleTextSize);

        mCurMonthLunarTextPaint.setColor(0xFF666666);
        mSchemeLunarTextPaint.setColor(0xFF666666);
        mOtherMonthLunarTextPaint.setColor(0xFFBBBBBB);
    }

    @Override
    protected boolean onDrawSelected(Canvas canvas, Calendar calendar, int x, int y, boolean hasScheme) {
        mSelectedPaint.setStyle(Paint.Style.FILL);
        RectF rectF = new RectF(x + mPadding, y + mPadding, x + mItemWidth - mPadding, y + mItemHeight - mPadding);
        canvas.drawRoundRect(rectF, mRoundRadius, mRoundRadius, mSelectedPaint);
        return true;
    }

    @Override
    protected void onDrawScheme(Canvas canvas, Calendar calendar, int x, int y) {
        // 非本月日期不绘制 Scheme，减少计算量
        if (!calendar.isCurrentMonth()) return;

        DailyStat stat = getDailyStat(calendar);
        if (stat == null) return;

        RectF rectF = new RectF(x + mPadding, y + mPadding, x + mItemWidth - mPadding, y + mItemHeight - mPadding);

        // 1. 节假日背景
        if (stat.isHoliday) {
            canvas.drawRoundRect(rectF, mRoundRadius, mRoundRadius, mHolidayPaint);
        }

        // 2. 热力图背景 (仅在有金额或账单时绘制)
        if (stat.income > 0 || stat.expense > 0 || stat.count > 0) {
            double net = stat.income - stat.expense;
            if (net >= 0) {
                mHeatMapPaint.setColor(0xFF81C784);
                int alpha = (int) Math.min(60 + (stat.income / 500.0) * 120, 200);
                mHeatMapPaint.setAlpha(alpha);
            } else {
                mHeatMapPaint.setColor(0xFFE57373);
                int alpha = (int) Math.min(60 + (stat.expense / 500.0) * 120, 200);
                mHeatMapPaint.setAlpha(alpha);
            }
            canvas.drawRoundRect(rectF, mRoundRadius, mRoundRadius, mHeatMapPaint);
        }

        // 3. 绘制 "休" 或 "班" 标签
        if (stat.dayTag != null) {
            mTagPaint.setColor("休".equals(stat.dayTag) ? 0xFF3F8BFF : 0xFFFF5252);
            canvas.drawText(stat.dayTag, x + mItemWidth - mTagMarginRight, y + mTagMarginTop, mTagPaint);
        }

        // 4. 绘制金额摘要 (缩减版，避免过多文字)
        float cx = x + mItemWidth / 2f;
        float baseLine = y + mItemHeight - mCapsuleOffset2;

        if (stat.income > 0 && stat.expense > 0) {
            canvas.drawText(formatAmt(stat.income), cx, baseLine - mCapsuleOffset1, mIncomePaint);
            canvas.drawText(formatAmt(stat.expense), cx, baseLine, mExpensePaint);
        } else if (stat.income > 0) {
            canvas.drawText(formatAmt(stat.income), cx, baseLine, mIncomePaint);
        } else if (stat.expense > 0) {
            canvas.drawText(formatAmt(stat.expense), cx, baseLine, mExpensePaint);
        }
    }

    // 优化：使用简单的逻辑代替复杂的 String.format
    private String formatAmt(double amt) {
        if (amt >= 10000) return (int)(amt / 1000) + "k";
        return String.valueOf((int)amt);
    }

    @Override
    protected void onDrawText(Canvas canvas, Calendar calendar, int x, int y, boolean hasScheme, boolean isSelected) {
        float cx = x + mItemWidth / 2f;
        float top = y - mItemHeight / 6f;

        DailyStat stat = getDailyStat(calendar);
        boolean hasBills = stat != null && (stat.income > 0 || stat.expense > 0 || stat.count > 0);

        if (isSelected) {
            canvas.drawText(String.valueOf(calendar.getDay()), cx, mTextBaseLine + top, mSelectTextPaint);
            if (!hasBills) {
                canvas.drawText(calendar.getLunar(), cx, mTextBaseLine + y + mItemHeight / 10f, mSelectedLunarTextPaint);
            }
        } else {
            canvas.drawText(String.valueOf(calendar.getDay()), cx, mTextBaseLine + top,
                    calendar.isCurrentMonth() ? mCurMonthTextPaint : mOtherMonthTextPaint);
            if (!hasBills) {
                canvas.drawText(calendar.getLunar(), cx, mTextBaseLine + y + mItemHeight / 10f,
                        calendar.isCurrentMonth() ? mCurMonthLunarTextPaint : mOtherMonthLunarTextPaint);
            }
        }
    }

    private DailyStat getDailyStat(Calendar calendar) {
        List<Calendar.Scheme> schemes = calendar.getSchemes();
        if (schemes == null || schemes.isEmpty()) return null;
        Object obj = schemes.get(0).getObj();
        return obj instanceof DailyStat ? (DailyStat) obj : null;
    }

    private static int dipToPx(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
