package com.example.my_project1.ui.decorator;

import android.content.Context;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义日期装饰器 - 显示金额或农历
 */
public class CustomDayDecorator implements DayViewDecorator {

    private final Context context;
    private final Map<CalendarDay, DayInfo> dayInfoMap = new HashMap<>();
    private final int displayType; // 0=全部, 1=仅支出, 2=仅收入

    public static class DayInfo {
        public String lunarText;
        public double totalExpense;
        public double totalIncome;
        public boolean hasExpense;
        public boolean hasIncome;
        public boolean isLunarFestival;
        public boolean isSolarTerm;

        public DayInfo(String lunar, double expense, double income,
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

    public CustomDayDecorator(Context context, Map<CalendarDay, DayInfo> dayInfo, int displayType) {
        this.context = context;
        this.dayInfoMap.putAll(dayInfo);
        this.displayType = displayType;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return dayInfoMap.containsKey(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        // MaterialCalendarView 不支持在 DayViewFacade 中自定义底部文本
        // 需要使用自定义 DayView 来实现
        // 这里我们先保留接口，具体实现在 CustomDayView 中
    }
}