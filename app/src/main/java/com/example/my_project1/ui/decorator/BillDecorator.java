package com.example.my_project1.ui.decorator;

import android.graphics.drawable.GradientDrawable;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 账单装饰器 - 根据账单类型显示不同颜色背景和金额
 */
public class BillDecorator implements DayViewDecorator {

    private final Map<CalendarDay, BillInfo> billInfoMap = new HashMap<>();
    private final int displayType; // 0=全部, 1=仅支出, 2=仅收入
    private final int bgColor;
    private final int textColor;

    public static class BillInfo {
        public double totalExpense;
        public double totalIncome;
        public boolean hasExpense;
        public boolean hasIncome;

        public BillInfo(double expense, double income) {
            this.totalExpense = expense;
            this.totalIncome = income;
            this.hasExpense = expense > 0;
            this.hasIncome = income > 0;
        }
    }

    public BillDecorator(Collection<CalendarDay> dates, Map<CalendarDay, BillInfo> billInfo,
                         int displayType, int bgColor, int textColor) {
        this.billInfoMap.putAll(billInfo);
        this.displayType = displayType;
        this.bgColor = bgColor;
        this.textColor = textColor;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        BillInfo info = billInfoMap.get(day);
        if (info == null) return false;

        if (displayType == 0) {
            return info.hasExpense || info.hasIncome;
        } else if (displayType == 1) {
            return info.hasExpense;
        } else {
            return info.hasIncome;
        }
    }

    @Override
    public void decorate(DayViewFacade view) {
        // 设置背景
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(6);
        drawable.setColor(bgColor);
        view.setBackgroundDrawable(drawable);
    }
}