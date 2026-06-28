package com.example.my_project1.ui.adapter.calendar;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class CalendarPagerAdapter extends RecyclerView.Adapter<CalendarPagerAdapter.CalendarPageViewHolder> {

    private static final int TOTAL_PAGES = 1200; // 支持前后各50年
    private static final int CENTER_POSITION = 600;

    private final Calendar baseCalendar;
    private Calendar selectedCalendar;
    private OnDayClickListener dayClickListener;

    public interface OnDayClickListener {
        void onDayClick(int year, int month, int day);
        void onMonthChanged(int year, int month);
    }

    public CalendarPagerAdapter(Calendar selectedCalendar) {
        this.baseCalendar = Calendar.getInstance();
        this.selectedCalendar = (Calendar) selectedCalendar.clone();
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.dayClickListener = listener;
    }

    public void updateSelectedDate(Calendar calendar) {
        this.selectedCalendar = (Calendar) calendar.clone();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CalendarPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView recyclerView = new RecyclerView(parent.getContext());
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new GridLayoutManager(parent.getContext(), 7));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setHasFixedSize(true);

        return new CalendarPageViewHolder(recyclerView);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarPageViewHolder holder, int position) {
        int monthOffset = position - CENTER_POSITION;

        Calendar displayCalendar = (Calendar) baseCalendar.clone();
        displayCalendar.add(Calendar.MONTH, monthOffset);

        holder.bind(displayCalendar, selectedCalendar, dayClickListener);
    }

    @Override
    public int getItemCount() {
        return TOTAL_PAGES;
    }

    public int getCenterPosition() {
        return CENTER_POSITION;
    }

    static class CalendarPageViewHolder extends RecyclerView.ViewHolder {
        private final RecyclerView recyclerView;
        private final CalendarAdapter adapter;

        CalendarPageViewHolder(RecyclerView itemView) {
            super(itemView);
            this.recyclerView = itemView;
            this.adapter = new CalendarAdapter();
            recyclerView.setAdapter(adapter);
        }

        void bind(Calendar displayCalendar, Calendar selectedCalendar, OnDayClickListener dayClickListener) {
            List<CalendarAdapter.CalendarDay> days = generateCalendarDays(displayCalendar);

            int selectedDay = -1;
            if (displayCalendar.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                    displayCalendar.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)) {
                selectedDay = selectedCalendar.get(Calendar.DAY_OF_MONTH);
            }

            adapter.setDays(days, selectedDay);

            if (dayClickListener != null) {
                adapter.setOnDayClickListener(dayClickListener::onDayClick);
            }
        }

        private List<CalendarAdapter.CalendarDay> generateCalendarDays(Calendar displayCalendar) {
            List<CalendarAdapter.CalendarDay> days = new ArrayList<>();

            int year = displayCalendar.get(Calendar.YEAR);
            int month = displayCalendar.get(Calendar.MONTH);

            Calendar firstDay = Calendar.getInstance();
            firstDay.set(year, month, 1);
            int firstDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK); // 1=周日, 7=周六

            int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

            // 上个月
            Calendar prevMonth = (Calendar) firstDay.clone();
            prevMonth.add(Calendar.MONTH, -1);
            int daysInPrevMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            int prevMonthValue = prevMonth.get(Calendar.MONTH);
            int prevYear = prevMonth.get(Calendar.YEAR);

            // 上个月尾部空白格，凑整周
            for (int i = firstDayOfWeek - 1; i > 0; i--) {
                int day = daysInPrevMonth - i + 1;
                days.add(new CalendarAdapter.CalendarDay(prevYear, prevMonthValue, day, false));
            }

            // 本月日期
            for (int day = 1; day <= daysInMonth; day++) {
                days.add(new CalendarAdapter.CalendarDay(year, month, day, true));
            }


            int totalDays = days.size();
            int targetDays;

            if (totalDays <= 35) {
                targetDays = 35; // 5行
            } else {
                targetDays = 42; // 6行
            }

            int remainingDays = targetDays - totalDays;

            // 下个月开头空白格，填充到固定天数
            Calendar nextMonth = (Calendar) firstDay.clone();
            nextMonth.add(Calendar.MONTH, 1);
            int nextMonthValue = nextMonth.get(Calendar.MONTH);
            int nextYear = nextMonth.get(Calendar.YEAR);

            for (int day = 1; day <= remainingDays; day++) {
                days.add(new CalendarAdapter.CalendarDay(nextYear, nextMonthValue, day, false));
            }

            return days;
        }
    }
}