package com.example.my_project1.ui.view;

import android.content.Context;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

/**
 * 自定义 DayView 工厂
 */
public class CustomDayViewFactory implements com.prolificinteractive.materialcalendarview.DayViewDecorator {

    private final Context context;

    public CustomDayViewFactory(Context context) {
        this.context = context;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return true; // 装饰所有日期
    }

    @Override
    public void decorate(DayViewFacade view) {
        // 在这里可以设置自定义的 DayView
    }
}