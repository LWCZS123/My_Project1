package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.contrarywind.adapter.WheelAdapter;
import com.contrarywind.view.WheelView;
import com.example.my_project1.R;
import com.example.my_project1.databinding.DialogYearMonthPickerBinding;
import com.example.my_project1.databinding.FragmentDateTimePickerV2Binding;
import com.example.my_project1.utils.HolidayUtil;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.CalendarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CustomDateTimePickerFragment - 日期时间选择器 (动态高度最终修复版)
 * 核心优化：
 * 1. 深度整合 CalendarLayout：利用库原生的高度计算机制实现 5行/6行 自动切换。
 * 2. 彻底禁用折叠：设置 showMode="only_month_view" 并代码加锁，杜绝向上滑动折叠。
 * 3. 解决遮挡问题：代码明确设置子项高度为 56dp，给最后一周预留充足纵向空间。
 */
public class CustomDateTimePickerFragment extends DialogFragment implements 
        CalendarView.OnCalendarSelectListener, 
        CalendarView.OnMonthChangeListener {

    private FragmentDateTimePickerV2Binding binding;
    private java.util.Calendar selectedCalendar;
    private OnDateTimeSelectedListener listener;
    private boolean isTimePickerVisible = false;

    public interface OnDateTimeSelectedListener {
        void onDateTimeSelected(java.util.Calendar calendar);
    }

    public static void show(androidx.fragment.app.FragmentManager fragmentManager,
                            java.util.Calendar initialDate,
                            OnDateTimeSelectedListener listener) {
        CustomDateTimePickerFragment fragment = new CustomDateTimePickerFragment();
        fragment.selectedCalendar = (java.util.Calendar) (initialDate != null ? initialDate.clone() : java.util.Calendar.getInstance());
        fragment.listener = listener;
        fragment.show(fragmentManager, "CustomDateTimePicker");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (selectedCalendar == null) {
            selectedCalendar = java.util.Calendar.getInstance();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDateTimePickerV2Binding.inflate(inflater, container, false);
        setupCalendarView();
        setupTimePicker();
        setupButtons();
        updateHeader(selectedCalendar.get(java.util.Calendar.YEAR), selectedCalendar.get(java.util.Calendar.MONTH) + 1);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(lp);
            }
        }
    }

    private void setupCalendarView() {
        // 关键：明确设置子项高度，确保库内部计算 6 行月份时不发生溢出或遮挡
        binding.calendarView.setCalendarItemHeight(dipToPx(56));

        // 初始选中日期
        binding.calendarView.scrollToCalendar(
                selectedCalendar.get(java.util.Calendar.YEAR),
                selectedCalendar.get(java.util.Calendar.MONTH) + 1,
                selectedCalendar.get(java.util.Calendar.DAY_OF_MONTH)
        );

        binding.calendarView.setOnCalendarSelectListener(this);
        binding.calendarView.setOnMonthChangeListener(this);
        
        // 根据初始月份调整高度
        adjustCalendarHeight(
                selectedCalendar.get(java.util.Calendar.YEAR),
                selectedCalendar.get(java.util.Calendar.MONTH) + 1
        );
        
        loadHolidays(selectedCalendar.get(java.util.Calendar.YEAR), selectedCalendar.get(java.util.Calendar.MONTH) + 1);
    }

    private void loadHolidays(int year, int month) {
        Map<String, Calendar> map = new HashMap<>();
        java.util.Calendar temp = java.util.Calendar.getInstance();
        temp.set(year, month - 1, 1);
        temp.add(java.util.Calendar.MONTH, -2);
        
        for (int i = 0; i < 150; i++) {
            int y = temp.get(java.util.Calendar.YEAR);
            int m = temp.get(java.util.Calendar.MONTH) + 1;
            int d = temp.get(java.util.Calendar.DAY_OF_MONTH);
            String tag = HolidayUtil.getDayTag(y, m, d);
            if (tag != null && (tag.equals("休") || tag.equals("班"))) {
                Calendar calendar = new Calendar();
                calendar.setYear(y);
                calendar.setMonth(m);
                calendar.setDay(d);
                calendar.setScheme(tag);
                map.put(calendar.toString(), calendar);
            }
            temp.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        binding.calendarView.setSchemeDate(map);
    }

    private void setupTimePicker() {
        int tealColor = ContextCompat.getColor(requireContext(), R.color.calendar_selection);
        binding.wheelHour.setCyclic(true);
        binding.wheelMinute.setCyclic(true);
        binding.wheelHour.setLineSpacingMultiplier(2.2f);
        binding.wheelMinute.setLineSpacingMultiplier(2.2f);
        binding.wheelHour.setTextSize(18f);
        binding.wheelMinute.setTextSize(18f);
        binding.wheelHour.setDividerType(WheelView.DividerType.FILL);
        binding.wheelMinute.setDividerType(WheelView.DividerType.FILL);
        binding.wheelHour.setTextColorCenter(tealColor);
        binding.wheelMinute.setTextColorCenter(tealColor);

        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(String.format(Locale.getDefault(), "%02d", i));
        binding.wheelHour.setAdapter(new StringWheelAdapter(hours));

        List<String> minutes = new ArrayList<>();
        for (int i = 0; i < 60; i++) minutes.add(String.format(Locale.getDefault(), "%02d", i));
        binding.wheelMinute.setAdapter(new StringWheelAdapter(minutes));

        binding.wheelHour.setCurrentItem(selectedCalendar.get(java.util.Calendar.HOUR_OF_DAY));
        binding.wheelMinute.setCurrentItem(selectedCalendar.get(java.util.Calendar.MINUTE));

        binding.llSetTimeHeader.setOnClickListener(v -> {
            isTimePickerVisible = !isTimePickerVisible;
            TransitionManager.beginDelayedTransition((ViewGroup) binding.getRoot());
            binding.llTimePickerContainer.setVisibility(isTimePickerVisible ? View.VISIBLE : View.GONE);
            binding.ivTimeArrow.setRotation(isTimePickerVisible ? 90 : 0);
            binding.getRoot().requestLayout();
        });
    }

    private void setupButtons() {
        binding.llHeader.setOnClickListener(v -> showYearMonthPickerDialog());
        binding.tvCancel.setOnClickListener(v -> dismiss());
        binding.tvConfirm.setOnClickListener(v -> {
            if (isTimePickerVisible) {
                selectedCalendar.set(java.util.Calendar.HOUR_OF_DAY, binding.wheelHour.getCurrentItem());
                selectedCalendar.set(java.util.Calendar.MINUTE, binding.wheelMinute.getCurrentItem());
            }
            if (listener != null) {
                listener.onDateTimeSelected(selectedCalendar);
            }
            dismiss();
        });
    }

    private void updateHeader(int year, int month) {
        binding.tvMonthBig.setText(month + "月");
        binding.tvYearSmall.setText(String.valueOf(year));
    }

    private void showYearMonthPickerDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.CustomDialog);
        DialogYearMonthPickerBinding dialogBinding = DialogYearMonthPickerBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        dialogBinding.wheelYear.setCyclic(false);
        dialogBinding.wheelMonth.setCyclic(true);
        dialogBinding.wheelYear.setLineSpacingMultiplier(2.2f);
        dialogBinding.wheelMonth.setLineSpacingMultiplier(2.2f);
        dialogBinding.wheelYear.setTextSize(18f);
        dialogBinding.wheelMonth.setTextSize(18f);

        int currentYear = binding.calendarView.getCurYear();
        int currentMonth = binding.calendarView.getCurMonth();

        List<String> years = new ArrayList<>();
        int startYear = currentYear - 100;
        for (int i = 0; i <= 200; i++) years.add(String.valueOf(startYear + i));
        dialogBinding.wheelYear.setAdapter(new StringWheelAdapter(years));
        dialogBinding.wheelYear.setCurrentItem(currentYear - startYear);

        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) months.add(String.valueOf(i));
        dialogBinding.wheelMonth.setAdapter(new StringWheelAdapter(months));
        dialogBinding.wheelMonth.setCurrentItem(currentMonth - 1);

        dialogBinding.tvDialogCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.tvDialogConfirm.setOnClickListener(v -> {
            int selectedYear = startYear + dialogBinding.wheelYear.getCurrentItem();
            int selectedMonth = dialogBinding.wheelMonth.getCurrentItem() + 1;
            binding.calendarView.scrollToCalendar(selectedYear, selectedMonth, 1);
            dialog.dismiss();
        });

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            window.setAttributes(lp);
        }
        dialog.show();
    }

    private int dipToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // --- CalendarView Listeners ---

    @Override
    public void onCalendarOutOfRange(Calendar calendar) {}

    @Override
    public void onCalendarSelect(Calendar calendar, boolean isClick) {
        selectedCalendar.set(calendar.getYear(), calendar.getMonth() - 1, calendar.getDay());
    }

    @Override
    public void onMonthChange(int year, int month) {
        updateHeader(year, month);
        loadHolidays(year, month);
        
        // 关键修复：动态调整日历高度并触发根布局重新计算，联动 Dialog 窗口高度
        if (binding != null) {
            adjustCalendarHeight(year, month);
            TransitionManager.beginDelayedTransition((ViewGroup) binding.getRoot());
            binding.getRoot().post(() -> {
                binding.getRoot().requestLayout();
                if (getDialog() != null && getDialog().getWindow() != null) {
                    getDialog().getWindow().getDecorView().requestLayout();
                }
            });
        }
    }

    private int getMonthRows(int year, int month) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(year, month - 1, 1);
        int dayOfWeek = c.get(java.util.Calendar.DAY_OF_WEEK) - 1; // 0代表周日
        int days = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        return (dayOfWeek + days + 6) / 7;
    }

    private void adjustCalendarHeight(int year, int month) {
        if (binding == null) return;
        int rows = getMonthRows(year, month);
        // 高度 = 星期栏(32dp) + 行数 * 项高(56dp) + 内边距(上下各10dp=20dp)
        int totalHeight = dipToPx(32 + rows * 56 + 20);
        ViewGroup.LayoutParams lp = binding.calendarView.getLayoutParams();
        if (lp.height != totalHeight) {
            lp.height = totalHeight;
            binding.calendarView.setLayoutParams(lp);
        }
    }

    private static class StringWheelAdapter implements WheelAdapter<String> {
        private final List<String> items;
        StringWheelAdapter(List<String> items) { this.items = items; }
        @Override public int getItemsCount() { return items.size(); }
        @Override public String getItem(int index) { return items.get(index); }
        @Override public int indexOf(String o) { return items.indexOf(o); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
