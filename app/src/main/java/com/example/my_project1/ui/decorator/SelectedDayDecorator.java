package com.example.my_project1.ui.decorator;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

/**
 * 选中日期装饰器 - 使用 XML drawable（简化版）
 */
public class SelectedDayDecorator implements DayViewDecorator {

    private CalendarDay selectedDate;
    private final Drawable drawable;
    private final int textColor;

    /**
     * 构造函数
     * @param context 上下文
     * @param selectedDate 选中的日期
     * @param drawableResId drawable 资源 ID（如 R.drawable.bg_calendar_selected_small）
     * @param textColor 文字颜色
     */
    public SelectedDayDecorator(Context context, CalendarDay selectedDate, int drawableResId, int textColor) {
        this.selectedDate = selectedDate;
        this.drawable = ContextCompat.getDrawable(context, drawableResId);
        this.textColor = textColor;
    }

    public void setSelectedDate(CalendarDay date) {
        this.selectedDate = date;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return selectedDate != null && selectedDate.equals(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        // 设置背景
        view.setBackgroundDrawable(drawable);

        // 设置文字颜色
        view.addSpan(new ForegroundColorSpan(textColor));
    }
}