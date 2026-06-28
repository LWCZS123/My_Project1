package com.example.my_project1.ui.decorator;

import android.graphics.drawable.GradientDrawable;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

/**
 * 今天日期装饰器 - 红色圆形边框
 */
public class TodayDecorator implements DayViewDecorator {

    private final CalendarDay today;
    private final int borderColor;

    public TodayDecorator(CalendarDay today, int borderColor) {
        this.today = today;
        this.borderColor = borderColor;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return today.equals(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(2, borderColor);
        view.setBackgroundDrawable(drawable);
    }
}