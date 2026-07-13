package com.example.my_project1.ui.adapter.calendar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemCalendarInfoBinding;
import com.example.my_project1.utils.HolidayUtil;
import com.haibin.calendarview.Calendar;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CalendarInfoAdapter extends RecyclerView.Adapter<CalendarInfoAdapter.ViewHolder> {

    private Calendar mCalendar;

    public void updateDate(Calendar calendar) {
        this.mCalendar = calendar;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarInfoBinding binding = ItemCalendarInfoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (mCalendar != null) {
            holder.bind(mCalendar);
        }
    }

    @Override
    public int getItemCount() {
        return mCalendar == null ? 0 : 1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCalendarInfoBinding binding;

        ViewHolder(ItemCalendarInfoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Calendar calendar) {
            int year = calendar.getYear();
            int month = calendar.getMonth();
            int day = calendar.getDay();

            // 1. 农历详情
            Solar solar = Solar.fromYmd(year, month, day);
            Lunar lunar = solar.getLunar();
            
            binding.tvLunarDate.setText(lunar.getDayInChinese());
            String lunarDetail = String.format("%s%s年 %s月 %s日", 
                    lunar.getYearInGanZhi(), lunar.getYearShengXiao(), 
                    lunar.getMonthInGanZhi(), lunar.getDayInGanZhi());
            binding.tvLunarYear.setText(lunarDetail);

            // 2. 智能节假日倒计时优化 - 严格基于选中位置联动
            java.util.Calendar selectedCal = java.util.Calendar.getInstance();
            selectedCal.set(year, month - 1, day);
            selectedCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            selectedCal.set(java.util.Calendar.MINUTE, 0);
            selectedCal.set(java.util.Calendar.SECOND, 0);
            selectedCal.set(java.util.Calendar.MILLISECOND, 0);

            // 查找【选中日期】之后的下一个节日
            String[] nextHoliday = HolidayUtil.getNextHoliday(year, month, day);
            
            if (nextHoliday != null) {
                binding.cardSolar.setVisibility(View.VISIBLE);
                String hDateStr = nextHoliday[0];
                String hName = nextHoliday[1];
                
                binding.tvSolarName.setText(hName);
                
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date hDate = sdf.parse(hDateStr);
                    
                    java.util.Calendar holidayCal = java.util.Calendar.getInstance();
                    holidayCal.setTime(hDate);
                    holidayCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    holidayCal.set(java.util.Calendar.MINUTE, 0);
                    holidayCal.set(java.util.Calendar.SECOND, 0);
                    holidayCal.set(java.util.Calendar.MILLISECOND, 0);
                    
                    // 计算 [目标节日] 与 [选中日期] 的精准天数差
                    long diffMs = holidayCal.getTimeInMillis() - selectedCal.getTimeInMillis();
                    long diffDays = diffMs / (24 * 60 * 60 * 1000);
                    
                    binding.tvSolarDayVal.setText(String.valueOf(Math.max(0, diffDays)));
                    
                    if (diffDays == 0) {
                        binding.tvSolarDate.setText("就在今天");
                        binding.tvSolarName.setText(hName + " · 享受假期");
                    } else if (diffDays == 1) {
                        binding.tvSolarDate.setText("明天 (" + new SimpleDateFormat("M月d日", Locale.getDefault()).format(hDate) + ")");
                    } else {
                        SimpleDateFormat displayFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
                        binding.tvSolarDate.setText(displayFmt.format(hDate));
                    }
                } catch (Exception e) {
                    binding.tvSolarDayVal.setText("-");
                }
            } else {
                binding.cardSolar.setVisibility(View.GONE);
            }
        }
    }
}
