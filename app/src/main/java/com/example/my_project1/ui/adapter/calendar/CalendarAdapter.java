package com.example.my_project1.ui.adapter.calendar;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemCalendarDayBinding;
import com.example.my_project1.utils.LunarCalendar;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.DayViewHolder> {

    private List<CalendarDay> days = new ArrayList<>();
    private int selectedPosition = -1;
    private OnDayClickListener listener;

    public interface OnDayClickListener {
        void onDayClick(int year, int month, int day);
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void setDays(List<CalendarDay> days, int selectedDay) {
        this.days = days;

        // 找到选中的日期位置
        selectedPosition = -1;
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).day == selectedDay && days.get(i).isCurrentMonth) {
                selectedPosition = i;
                break;
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarDayBinding binding = ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        CalendarDay calendarDay = days.get(position);
        holder.bind(calendarDay, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    class DayViewHolder extends RecyclerView.ViewHolder {
        private final ItemCalendarDayBinding binding;

        DayViewHolder(ItemCalendarDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CalendarDay calendarDay, boolean isSelected) {
            // 非当前月份的日期 - 显示但变淡
            if (!calendarDay.isCurrentMonth) {
                binding.getRoot().setVisibility(View.VISIBLE);
                binding.tvDay.setText(String.valueOf(calendarDay.day));
                binding.tvDay.setTextColor(Color.parseColor("#CCCCCC"));
                binding.tvLunar.setText("");
                binding.tvLunar.setVisibility(View.INVISIBLE);
                binding.getRoot().setClickable(false);
                binding.getRoot().setSelected(false);
                return;
            }

            // 显示有效日期
            binding.getRoot().setVisibility(View.VISIBLE);

            // 设置公历日期
            binding.tvDay.setText(String.valueOf(calendarDay.day));

            // 设置农历/节日/节气
            String lunarText = LunarCalendar.getDisplayText(
                    calendarDay.year,
                    calendarDay.month + 1,  // Calendar.MONTH 从0开始
                    calendarDay.day);
            binding.tvLunar.setText(lunarText);
            binding.tvLunar.setVisibility(View.VISIBLE);

            binding.getRoot().setClickable(true);
            binding.getRoot().setSelected(isSelected);

            // 设置颜色
            if (isSelected) {
                binding.tvDay.setTextColor(Color.WHITE);
                binding.tvLunar.setTextColor(Color.WHITE);
            } else {
                binding.tvDay.setTextColor(Color.parseColor("#000000"));

                // 判断是否是节日或节气，设置特殊颜色
                if (isFestivalOrSolarTerm(lunarText)) {
                    binding.tvLunar.setTextColor(Color.parseColor("#FF5722"));
                } else {
                    binding.tvLunar.setTextColor(Color.parseColor("#999999"));
                }
            }

            binding.getRoot().setOnClickListener(v -> {
                int oldPosition = selectedPosition;
                selectedPosition = getAdapterPosition();

                if (oldPosition != -1) {
                    notifyItemChanged(oldPosition);
                }
                notifyItemChanged(selectedPosition);

                if (listener != null) {
                    listener.onDayClick(calendarDay.year, calendarDay.month, calendarDay.day);
                }
            });
        }

        /**
         * 判断是否是节日或节气
         */
        private boolean isFestivalOrSolarTerm(String text) {
            return !text.startsWith("初") && !text.startsWith("十") &&
                    !text.startsWith("廿") && !text.startsWith("三十");
        }
    }

    public static class CalendarDay {
        public int year;
        public int month;
        public int day;
        public boolean isCurrentMonth;

        public CalendarDay(int year, int month, int day, boolean isCurrentMonth) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.isCurrentMonth = isCurrentMonth;
        }
    }
}