package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bigkoo.pickerview.builder.TimePickerBuilder;
import com.bigkoo.pickerview.view.TimePickerView;
import com.example.my_project1.R;
import com.example.my_project1.data.model.calendar.CalendarDay;
import com.example.my_project1.databinding.FragmentDateTimePickerBinding;
import com.example.my_project1.ui.adapter.calendar.CalendarDataEngine;
import com.example.my_project1.ui.adapter.calendar.MonthPagerAdapter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * DateTimePickerFragment - 弹出抖动修复版
 *
 * 抖动根因：
 *   BottomSheetDialog 弹出动画开始时 ViewPager2 高度为 0（wrap_content 但内容未渲染），
 *   动画过程中高度从 0 跳变为真实值，导致整个 BottomSheet 闪跳。
 *
 * 修复策略（三步缺一不可）：
 *   1. onCreateDialog：STATE_EXPANDED + skipCollapsed，消除"先展开到 peekHeight 再撑满"的二段动画。
 *   2. setupViewPager：绑定 Adapter 前用 getMetaSync 同步算出初始高度并立即写入 LayoutParams，
 *      确保第一次 layout pass 时 ViewPager2 就有正确尺寸，动画过程中高度不再跳变。
 *   3. onPageSelected：翻页时同步更新高度（getMetaSync 是纯计算，无 IO，主线程安全），
 *      不等待 dataEngine 异步回调，彻底消除翻页时的延迟跳变。
 */
public class DateTimePickerFragment extends BottomSheetDialogFragment {

    private static final int MONTHS_COUNT    = 120;
    private static final int CENTER_POSITION = 60;

    private FragmentDateTimePickerBinding binding;
    private MonthPagerAdapter  monthPagerAdapter;
    private CalendarDataEngine dataEngine;

    private Calendar selectedCalendar;
    private Calendar todayDate;
    private int currentMonthIndex = CENTER_POSITION;

    private OnDateTimeSelectedListener listener;

    // 静态共享，避免每次构建 Fragment 都 new
    private static final SimpleDateFormat KEY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat YM_FORMAT =
            new SimpleDateFormat("yyyy年MM月", Locale.CHINESE);
    private static final SimpleDateFormat HM_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public interface OnDateTimeSelectedListener {
        void onDateTimeSelected(long timestamp, String formattedDateTime);
    }

    public void setOnDateTimeSelectedListener(OnDateTimeSelectedListener listener) {
        this.listener = listener;
    }

    // ════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        todayDate        = Calendar.getInstance();
        selectedCalendar = (Calendar) todayDate.clone();
        dataEngine       = new CalendarDataEngine(todayDate, CENTER_POSITION);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDateTimePickerBinding.inflate(inflater, container, false);
        setupViewPager();
        setupButtons();
        updateTimeDisplay();
        return binding.getRoot();
    }

    /**
     * 【修复 1】：onCreateDialog 强制 EXPANDED，禁用折叠态。
     *
     * BottomSheet 默认先滑到 peekHeight（半屏），再被用户/代码撑到全高。
     * 这个"二段"过程中 ViewPager2 高度还没确定，造成明显抖动。
     * STATE_EXPANDED + skipCollapsed 让弹出动画只有一段，且目标高度确定。
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet =
                    dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior =
                        BottomSheetBehavior.from(bottomSheet);

                // 关键：禁止折叠中间态，弹出动画直接到 EXPANDED
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                // 禁止用户拖拽折叠，避免意外关闭
                behavior.setDraggable(true);

                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
            }
        });
        return dialog;
    }

    // ════════════════════════════════════════════════════════════
    // ViewPager 初始化
    // ════════════════════════════════════════════════════════════

    private void setupViewPager() {
        monthPagerAdapter = new MonthPagerAdapter();

        // 【修复 2】：在 setAdapter 之前同步计算初始高度并写入 LayoutParams。
        //   getMetaSync 是纯内存计算（年月 → 天数 → 行数），无 IO，可在主线程调用。
        //   这样 ViewPager2 第一次 layout pass 时就拥有正确高度，
        //   弹出动画全程高度稳定，不会从 0 或错误值跳变。
        CalendarDataEngine.MonthMeta initialMeta = dataEngine.getMetaSync(CENTER_POSITION);
        applyCalendarHeight(initialMeta);   // ← 必须在 setAdapter 之前调用！

        // 异步数据回调：仅用于刷新内容，高度已在 onPageSelected 同步搞定
        dataEngine.setCallback((pageIndex, days, meta) -> {
            if (binding == null) return;
            monthPagerAdapter.deliverData(pageIndex, days, meta);
            // 高度修正：异步数据到达时若 rowCount 与同步估算不同则微调
            if (pageIndex == currentMonthIndex) {
                applyCalendarHeight(meta);
            }
        });

        monthPagerAdapter.init(MONTHS_COUNT, dataEngine);
        monthPagerAdapter.setOnDayClickListener(this::onDaySelected);
        binding.vpCalendar.setAdapter(monthPagerAdapter);
        binding.vpCalendar.setOffscreenPageLimit(1);

        binding.vpCalendar.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentMonthIndex = position;
                updateYearMonthDisplay(position);

                // 【修复 3】：翻页时立即同步更新高度，不等异步回调。
                //   getMetaSync 纯计算（< 1ms），主线程调用完全安全。
                //   如果等 dataEngine 异步回调再改高度，用户会看到
                //   "先显示旧高度 → 数据到了再跳一次"的抖动。
                CalendarDataEngine.MonthMeta meta = dataEngine.getMetaSync(position);
                applyCalendarHeight(meta);

                dataEngine.requestMonth(position);
            }
        });

        // 跳转到今天，无动画（弹出时不想看到翻页过程）
        binding.vpCalendar.setCurrentItem(CENTER_POSITION, false);
        updateYearMonthDisplay(CENTER_POSITION);
    }

    /**
     * 根据行数精确设置 ViewPager2 高度。
     * 公式与 MonthPagerAdapter.applyHeight() 完全一致，单一真相来源。
     * 纯 LayoutParams 写入，不触发 measure。
     */
    private void applyCalendarHeight(CalendarDataEngine.MonthMeta meta) {
        if (binding == null || meta == null) return;
        float density  = getResources().getDisplayMetrics().density;
        int   heightPx = (int) (MonthPagerAdapter.ROW_HEIGHT_DP * meta.rowCount * density);
        ViewGroup.LayoutParams params = binding.vpCalendar.getLayoutParams();
        if (params.height != heightPx) {
            params.height = heightPx;
            binding.vpCalendar.setLayoutParams(params);
        }
    }

    // ════════════════════════════════════════════════════════════
    // 日期选择
    // ════════════════════════════════════════════════════════════

    private void onDaySelected(CalendarDay day) {
        selectedCalendar.set(day.getYear(), day.getMonth() - 1, day.getDay());
        String newKey = KEY_FORMAT.format(selectedCalendar.getTime());
        dataEngine.updateSelectedKey(newKey, currentMonthIndex);
    }

    // ════════════════════════════════════════════════════════════
    // 按钮 & 时间
    // ════════════════════════════════════════════════════════════

    private void setupButtons() {
        binding.ivPrevMonth.setOnClickListener(v -> {
            if (currentMonthIndex > 0)
                binding.vpCalendar.setCurrentItem(currentMonthIndex - 1, true);
        });
        binding.ivNextMonth.setOnClickListener(v -> {
            if (currentMonthIndex < MONTHS_COUNT - 1)
                binding.vpCalendar.setCurrentItem(currentMonthIndex + 1, true);
        });
        binding.ivToday.setOnClickListener(v -> {
            todayDate        = Calendar.getInstance();
            selectedCalendar = (Calendar) todayDate.clone();
            String key       = KEY_FORMAT.format(todayDate.getTime());
            dataEngine.refreshTodayKey(todayDate, key, key, CENTER_POSITION);
            binding.vpCalendar.setCurrentItem(CENTER_POSITION, true);
            updateYearMonthDisplay(CENTER_POSITION);
        });
        binding.tvTime.setOnClickListener(v -> showCustomTimePicker());
        binding.btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                long   timestamp  = selectedCalendar.getTimeInMillis();
                String formatted  = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(selectedCalendar.getTime());
                listener.onDateTimeSelected(timestamp, formatted);
            }
            dismiss();
        });
    }

    private void updateYearMonthDisplay(int pageIndex) {
        if (binding == null) return;
        Calendar cal = (Calendar) todayDate.clone();
        cal.add(Calendar.MONTH, pageIndex - CENTER_POSITION);
        binding.tvYearMonth.setText(YM_FORMAT.format(cal.getTime()));
    }

    private void updateTimeDisplay() {
        if (binding == null) return;
        binding.tvTime.setText(HM_FORMAT.format(selectedCalendar.getTime()));
    }

    private void showCustomTimePicker() {
        TimePickerView pvTime = new TimePickerBuilder(getContext(), (date, v) -> {
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            selectedCalendar.set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY));
            selectedCalendar.set(Calendar.MINUTE, c.get(Calendar.MINUTE));
            updateTimeDisplay();
        })
                .setType(new boolean[]{false, false, false, true, true, false})
                .isDialog(true)
                .build();

        Dialog innerDialog = pvTime.getDialog();
        if (innerDialog != null) {
            Window window = innerDialog.getWindow();
            if (window != null) {
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
        pvTime.show();
    }

    // ════════════════════════════════════════════════════════════
    // Cleanup
    // ════════════════════════════════════════════════════════════

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dataEngine != null) { dataEngine.release(); dataEngine = null; }
        binding = null;
    }
}