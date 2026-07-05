package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.contrarywind.adapter.WheelAdapter;
import com.contrarywind.view.WheelView;
import com.example.my_project1.R;
import com.example.my_project1.databinding.DialogYearMonthPickerBinding;
import com.example.my_project1.databinding.FragmentDateTimePickerV2Binding;
import com.example.my_project1.ui.adapter.calendar.CalendarDataEngine;
import com.example.my_project1.ui.adapter.calendar.MonthPagerAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * CustomDateTimePickerFragment - 高级日期时间选择器
 * 风格参考支付宝/记账类App
 * 支持农历显示、月份快捷切换、集成时间选择
 */
public class CustomDateTimePickerFragment extends DialogFragment {

    private static final int MONTHS_COUNT = 2400; // 200年范围
    private static final int CENTER_POSITION = 1200;

    private FragmentDateTimePickerV2Binding binding;
    private MonthPagerAdapter monthPagerAdapter;
    private CalendarDataEngine dataEngine;

    private Calendar selectedCalendar;
    private Calendar todayDate;
    private int currentMonthIndex = CENTER_POSITION;

    private OnDateTimeSelectedListener listener;
    private boolean isTimePickerVisible = true;

    public interface OnDateTimeSelectedListener {
        void onDateTimeSelected(Calendar calendar);
    }

    /**
     * 显示日期时间选择弹窗，支持预设初始日期和选择结果回调
     *
     * @param fragmentManager Fragment 管理器，用于弹出 DialogFragment
     * @param initialDate     初始选中的日期，为 null 时默认为当前日期
     * @param listener        日期时间选择完成后的回调，用户点击确认时触发
     */
    public static void show(androidx.fragment.app.FragmentManager fragmentManager,
                            Calendar initialDate,
                            OnDateTimeSelectedListener listener) {
        CustomDateTimePickerFragment fragment = new CustomDateTimePickerFragment();
        fragment.selectedCalendar = (Calendar) (initialDate != null ? initialDate.clone() : Calendar.getInstance());
        fragment.listener = listener;
        fragment.show(fragmentManager, "CustomDateTimePicker");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        todayDate = Calendar.getInstance();
        if (selectedCalendar == null) {
            selectedCalendar = (Calendar) todayDate.clone();
        }
        
        // 计算初始位置
        int yearDiff = selectedCalendar.get(Calendar.YEAR) - todayDate.get(Calendar.YEAR);
        int monthDiff = selectedCalendar.get(Calendar.MONTH) - todayDate.get(Calendar.MONTH);
        currentMonthIndex = CENTER_POSITION + (yearDiff * 12) + monthDiff;
        
        dataEngine = new CalendarDataEngine(todayDate, CENTER_POSITION);
        String key = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCalendar.getTime());
        dataEngine.updateSelectedKey(key, currentMonthIndex);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDateTimePickerV2Binding.inflate(inflater, container, false);
        setupViewPager();
        setupTimePicker();
        setupButtons();
        updateHeader();
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                // 背景透明以便显示布局的圆角
                window.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams lp = window.getAttributes();
                // 宽度设置为屏幕宽度的 90%
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(lp);
            }
        }
    }

    private void setupViewPager() {
        monthPagerAdapter = new MonthPagerAdapter();
        monthPagerAdapter.setDisplayType(-1); // 特殊标识，使用 Teal 选中色
        
        CalendarDataEngine.MonthMeta initialMeta = dataEngine.getMetaSync(currentMonthIndex);
        applyCalendarHeight(initialMeta);

        dataEngine.setCallback((pageIndex, days, meta) -> {
            if (binding == null) return;
            monthPagerAdapter.deliverData(pageIndex, days, meta);
            if (pageIndex == currentMonthIndex) {
                applyCalendarHeight(meta);
            }
        });

        monthPagerAdapter.init(MONTHS_COUNT, dataEngine);
        monthPagerAdapter.setOnDayClickListener(day -> {
            selectedCalendar.set(day.getYear(), day.getMonth() - 1, day.getDay());
            String newKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCalendar.getTime());
            dataEngine.updateSelectedKey(newKey, currentMonthIndex);
        });

        binding.vpCalendar.setAdapter(monthPagerAdapter);
        binding.vpCalendar.setOffscreenPageLimit(1);

        binding.vpCalendar.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentMonthIndex = position;
                updateHeader();
                CalendarDataEngine.MonthMeta meta = dataEngine.getMetaSync(position);
                applyCalendarHeight(meta);
                dataEngine.requestMonth(position);
            }
        });

        binding.vpCalendar.setCurrentItem(currentMonthIndex, false);
    }

    private void applyCalendarHeight(CalendarDataEngine.MonthMeta meta) {
        if (binding == null || meta == null) return;
        float density = getResources().getDisplayMetrics().density;
        int heightPx = (int) (MonthPagerAdapter.ROW_HEIGHT_DP * meta.rowCount * density);
        ViewGroup.LayoutParams params = binding.vpCalendar.getLayoutParams();
        if (params.height != heightPx) {
            params.height = heightPx;
            binding.vpCalendar.setLayoutParams(params);
        }
    }

    private void setupTimePicker() {
        // 优化滚轮参数，提高流畅度和选择准确度
        binding.wheelHour.setCyclic(true);
        binding.wheelMinute.setCyclic(true);
        
        binding.wheelHour.setLineSpacingMultiplier(2.2f); // 增加行间距，更容易点选
        binding.wheelMinute.setLineSpacingMultiplier(2.2f);
        
        binding.wheelHour.setTextSize(18f); // 调整文字大小
        binding.wheelMinute.setTextSize(18f);
        
        // 设置分割线样式
        binding.wheelHour.setDividerType(WheelView.DividerType.FILL);
        binding.wheelMinute.setDividerType(WheelView.DividerType.FILL);

        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(String.format(Locale.getDefault(), "%02d", i));
        binding.wheelHour.setAdapter(new StringWheelAdapter(hours));

        List<String> minutes = new ArrayList<>();
        for (int i = 0; i < 60; i++) minutes.add(String.format(Locale.getDefault(), "%02d", i));
        binding.wheelMinute.setAdapter(new StringWheelAdapter(minutes));

        binding.wheelHour.setCurrentItem(selectedCalendar.get(Calendar.HOUR_OF_DAY));
        binding.wheelMinute.setCurrentItem(selectedCalendar.get(Calendar.MINUTE));

        // 默认显示时间选择器
        binding.llTimePickerContainer.setVisibility(View.VISIBLE);
        binding.ivTimeArrow.setRotation(90);

        binding.llSetTimeHeader.setOnClickListener(v -> {
            isTimePickerVisible = !isTimePickerVisible;
            binding.llTimePickerContainer.setVisibility(isTimePickerVisible ? View.VISIBLE : View.GONE);
            binding.ivTimeArrow.setRotation(isTimePickerVisible ? 90 : 0);
        });
    }

    private void setupButtons() {
        binding.llHeader.setOnClickListener(v -> showYearMonthPickerDialog());
        
        binding.tvCancel.setOnClickListener(v -> dismiss());
        
        binding.tvConfirm.setOnClickListener(v -> {
            if (isTimePickerVisible) {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, binding.wheelHour.getCurrentItem());
                selectedCalendar.set(Calendar.MINUTE, binding.wheelMinute.getCurrentItem());
            }
            if (listener != null) {
                listener.onDateTimeSelected(selectedCalendar);
            }
            dismiss();
        });
    }

    private void updateHeader() {
        Calendar cal = (Calendar) todayDate.clone();
        cal.add(Calendar.MONTH, currentMonthIndex - CENTER_POSITION);
        
        binding.tvMonthBig.setText((cal.get(Calendar.MONTH) + 1) + "月");
        binding.tvYearSmall.setText(String.valueOf(cal.get(Calendar.YEAR)));
    }

    private void showYearMonthPickerDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.CustomDialog);
        DialogYearMonthPickerBinding dialogBinding = DialogYearMonthPickerBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        // 优化年月选择弹窗的滚轮，使其更流畅且易于选准
        dialogBinding.wheelYear.setCyclic(false);
        dialogBinding.wheelMonth.setCyclic(true);
        dialogBinding.wheelYear.setLineSpacingMultiplier(2.2f);
        dialogBinding.wheelMonth.setLineSpacingMultiplier(2.2f);
        dialogBinding.wheelYear.setTextSize(18f);
        dialogBinding.wheelMonth.setTextSize(18f);
        dialogBinding.wheelYear.setDividerType(WheelView.DividerType.FILL);
        dialogBinding.wheelMonth.setDividerType(WheelView.DividerType.FILL);

        Calendar displayCal = (Calendar) todayDate.clone();
        displayCal.add(Calendar.MONTH, currentMonthIndex - CENTER_POSITION);
        int currentYear = displayCal.get(Calendar.YEAR);
        int currentMonth = displayCal.get(Calendar.MONTH) + 1;

        // 年份范围：今天 +/- 50年
        List<String> years = new ArrayList<>();
        int startYear = todayDate.get(Calendar.YEAR) - 100;
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
            
            int targetMonthIndex = CENTER_POSITION + (selectedYear - todayDate.get(Calendar.YEAR)) * 12 + (selectedMonth - 1 - todayDate.get(Calendar.MONTH));
            binding.vpCalendar.setCurrentItem(targetMonthIndex, false);
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

    private static class StringWheelAdapter implements WheelAdapter<String> {
        private List<String> items;
        StringWheelAdapter(List<String> items) { this.items = items; }
        @Override public int getItemsCount() { return items.size(); }
        @Override public String getItem(int index) { return items.get(index); }
        @Override public int indexOf(String o) { return items.indexOf(o); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dataEngine != null) dataEngine.release();
        binding = null;
    }
}