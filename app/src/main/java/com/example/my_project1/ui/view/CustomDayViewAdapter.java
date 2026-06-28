package com.example.my_project1.ui.view;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义日期视图适配器
 * 用于在日期下方显示金额或农历
 */
public class CustomDayViewAdapter implements com.prolificinteractive.materialcalendarview.DayViewDecorator {

    private final Context context;
    private final Map<CalendarDay, DayData> dayDataMap = new HashMap<>();
    private final int displayType; // 0=全部, 1=仅支出, 2=仅收入

    public static class DayData {
        public String lunarText;
        public double totalExpense;
        public double totalIncome;
        public boolean hasExpense;
        public boolean hasIncome;
        public boolean isLunarFestival;
        public boolean isSolarTerm;

        public DayData(String lunar, double expense, double income,
                       boolean isLunarFestival, boolean isSolarTerm) {
            this.lunarText = lunar;
            this.totalExpense = expense;
            this.totalIncome = income;
            this.hasExpense = expense > 0;
            this.hasIncome = income > 0;
            this.isLunarFestival = isLunarFestival;
            this.isSolarTerm = isSolarTerm;
        }
    }

    public CustomDayViewAdapter(Context context, Map<CalendarDay, DayData> dayData, int displayType) {
        this.context = context;
        this.dayDataMap.putAll(dayData);
        this.displayType = displayType;
    }

    public void updateDayData(Map<CalendarDay, DayData> dayData) {
        this.dayDataMap.clear();
        this.dayDataMap.putAll(dayData);
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return dayDataMap.containsKey(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        // MaterialCalendarView 的 DayViewFacade 不支持添加自定义 View
        // 需要使用其他方式实现，如覆盖 onBindViewHolder
    }

    /**
     * 获取底部文本（金额或农历）
     */
    public String getBottomText(CalendarDay day) {
        DayData data = dayDataMap.get(day);
        if (data == null) return "";

        // 判断是否有账单需要显示
        boolean hasBillToShow = shouldShowBill(data);

        if (hasBillToShow) {
            // 显示金额
            return getAmountText(data);
        } else {
            // 显示农历
            return data.lunarText != null ? data.lunarText : "";
        }
    }

    /**
     * 获取底部文本颜色
     */
    public int getBottomTextColor(CalendarDay day) {
        DayData data = dayDataMap.get(day);
        if (data == null) return Color.parseColor("#999999");

        boolean hasBillToShow = shouldShowBill(data);

        if (hasBillToShow) {
            // 金额颜色
            return getAmountColor(data);
        } else {
            // 农历颜色
            if (data.isLunarFestival || data.isSolarTerm) {
                return ContextCompat.getColor(context, R.color.red);
            } else {
                return Color.parseColor("#999999");
            }
        }
    }

    /**
     * 判断是否应该显示账单
     */
    private boolean shouldShowBill(DayData data) {
        if (displayType == 0) {
            return data.hasExpense || data.hasIncome;
        } else if (displayType == 1) {
            return data.hasExpense;
        } else {
            return data.hasIncome;
        }
    }

    /**
     * 获取金额文本
     */
    private String getAmountText(DayData data) {
        double amount = 0;

        if (displayType == 0) {
            // 全部模式：优先显示支出
            if (data.hasExpense) {
                amount = data.totalExpense;
            } else if (data.hasIncome) {
                amount = data.totalIncome;
            }
        } else if (displayType == 1) {
            // 仅支出
            amount = data.totalExpense;
        } else {
            // 仅收入
            amount = data.totalIncome;
        }

        if (amount > 0) {
            DecimalFormat df = new DecimalFormat("#");
            return df.format(amount);
        }
        return "";
    }

    /**
     * 获取金额颜色
     */
    private int getAmountColor(DayData data) {
        if (displayType == 0) {
            // 全部模式：优先显示支出
            if (data.hasExpense) {
                return ContextCompat.getColor(context, R.color.red);
            } else if (data.hasIncome) {
                return Color.parseColor("#4CAF50");
            }
        } else if (displayType == 1) {
            // 仅支出
            return ContextCompat.getColor(context, R.color.red);
        } else {
            // 仅收入
            return Color.parseColor("#4CAF50");
        }
        return Color.parseColor("#999999");
    }
}