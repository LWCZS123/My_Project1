package com.example.my_project1.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.data.model.calendar.DailyStat;
import com.example.my_project1.databinding.FragmentCalendarBinding;
import com.example.my_project1.ui.activity.AddBillActivity;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.HolidayUtil;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.CalendarView;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 日历模块主界面
 * 优化后：仅保留日历展示和打点逻辑，账单详情通过底部弹窗显示
 */
public class CalendarFragment extends Fragment implements
        CalendarView.OnCalendarSelectListener,
        CalendarView.OnMonthChangeListener {

    private FragmentCalendarBinding binding;
    private BillViewModel billViewModel;
    private Calendar mCurrentSelectedDate;
    private int mVisibleYear, mVisibleMonth;

    private Map<String, DailyStat> mLatestStatsMap = new HashMap<>();
    private final Map<String, Map<String, Calendar>> mMonthSchemeCache =
            new LinkedHashMap<String, Map<String, Calendar>>(24, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, Calendar>> eldest) {
                    return size() > 24;
                }
            };
    private final AtomicInteger mSchemeGeneration = new AtomicInteger();
    private int mStatsVersion = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 沉浸式透明状态栏
        if (getActivity() != null) {
            Window window = getActivity().getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);

        setupCalendar();
        setupListeners();
        observeData();
        selectToday(false);
        if (mCurrentSelectedDate != null) {
            updateHolidayInfo(mCurrentSelectedDate);
        }
    }

    private void setupCalendar() {
        binding.calendarView.setOnCalendarSelectListener(this);
        binding.calendarView.setOnMonthChangeListener(this);
        mCurrentSelectedDate = binding.calendarView.getSelectedCalendar();
        mVisibleYear = mCurrentSelectedDate.getYear();
        mVisibleMonth = mCurrentSelectedDate.getMonth();
        updateDateTitle(mCurrentSelectedDate);
        updateTodayButtonVisibility(mCurrentSelectedDate);
    }

    private void setupListeners() {
        binding.btnToday.setOnClickListener(v -> binding.calendarView.scrollToCurrent(true));
        binding.ivAddBill.setOnClickListener(v -> {
            if (!isAdded()) return;
            startActivity(new Intent(requireContext(), AddBillActivity.class));
        });
    }

    private void observeData() {
        // 核心优化：观察 ViewModel 预计算好的统计 Map，用于绘制日历打点/Scheme
        billViewModel.dailyStatsMap.observe(getViewLifecycleOwner(), statsMap -> {
            mLatestStatsMap = statsMap == null ? new HashMap<>() : statsMap;
            mStatsVersion++;
            synchronized (mMonthSchemeCache) {
                mMonthSchemeCache.clear();
            }
            updateCalendarSchemes();
            hideLoading();
        });
    }

    private void updateCalendarSchemes() {
        final int year = mVisibleYear;
        final int month = mVisibleMonth;
        final int generation = mSchemeGeneration.incrementAndGet();
        final int statsVersion = mStatsVersion;
        final Map<String, DailyStat> statsSnapshot = mLatestStatsMap;

        AppExecutors.get().computation().execute(() -> {
            Map<String, Calendar> schemeMap = new HashMap<>();
            java.util.Calendar cursor = java.util.Calendar.getInstance();
            cursor.set(year, month - 1, 1);
            cursor.add(java.util.Calendar.MONTH, -1);
            for (int i = 0; i < 3; i++) {
                int windowYear = cursor.get(java.util.Calendar.YEAR);
                int windowMonth = cursor.get(java.util.Calendar.MONTH) + 1;
                schemeMap.putAll(getOrBuildMonthSchemes(
                        statsSnapshot, statsVersion, windowYear, windowMonth));
                cursor.add(java.util.Calendar.MONTH, 1);
            }

            AppExecutors.get().mainThread().execute(() -> {
                if (binding == null || generation != mSchemeGeneration.get()
                        || year != mVisibleYear || month != mVisibleMonth) return;
                binding.calendarView.setSchemeDate(schemeMap);
            });
        });
    }

    private Map<String, Calendar> getOrBuildMonthSchemes(Map<String, DailyStat> statsMap,
                                                         int statsVersion, int year, int month) {
        String displayMonthKey = year + "-" + twoDigits(month);
        String cacheKey = statsVersion + "_" + displayMonthKey;
        synchronized (mMonthSchemeCache) {
            Map<String, Calendar> cached = mMonthSchemeCache.get(cacheKey);
            if (cached != null) return cached;
        }

        Map<String, Calendar> result = new HashMap<>();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(year, month - 1, 1);
        int maxDays = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);

        for (int d = 1; d <= maxDays; d++) {
            String dateKey = displayMonthKey + "-" + twoDigits(d);
            DailyStat source = statsMap.get(dateKey);
            String tag = HolidayUtil.getDayTag(year, month, d);
            if (source == null && tag == null) continue;

            DailyStat stat = source == null
                    ? new DailyStat(0, 0, 0)
                    : new DailyStat(source.income, source.expense, source.count);
            stat.dayTag = tag;
            stat.isHoliday = "休".equals(tag);
            stat.incomeText = formatCalendarAmount(stat.income);
            stat.expenseText = formatCalendarAmount(stat.expense);
            stat.signedIncomeText = "+" + stat.incomeText;
            stat.signedExpenseText = "-" + stat.expenseText;

            Calendar calendar = new Calendar();
            calendar.setYear(year);
            calendar.setMonth(month);
            calendar.setDay(d);
            Calendar.Scheme scheme = new Calendar.Scheme();
            scheme.setObj(stat);
            scheme.setScheme("s");
            calendar.addScheme(scheme);
            result.put(calendar.toString(), calendar);
        }

        synchronized (mMonthSchemeCache) {
            mMonthSchemeCache.put(cacheKey, result);
        }
        return result;
    }

    private static String formatCalendarAmount(double amount) {
        if (amount >= 10000) return (int) (amount / 1000) + "k";
        return String.valueOf((int) amount);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private void updateDateTitle(Calendar calendar) {
        if (calendar == null || binding == null) return;
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", calendar.getYear(), calendar.getMonth()));

        java.util.Calendar today = java.util.Calendar.getInstance();
        today.set(java.util.Calendar.HOUR_OF_DAY, 12);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar target = java.util.Calendar.getInstance();
        target.set(calendar.getYear(), calendar.getMonth() - 1, calendar.getDay(), 12, 0, 0);
        target.set(java.util.Calendar.MILLISECOND, 0);

        long diff = (target.getTimeInMillis() - today.getTimeInMillis()) / (24L * 60 * 60 * 1000);
        binding.tvRelativeTime.setText(
                diff == 0 ? "今天" : (diff > 0 ? diff + "天后" : Math.abs(diff) + "天前"));
    }

    /**
     * 根据当前选中的日期，控制“回到今天”按钮的显示/隐藏
     */
    private void updateTodayButtonVisibility(Calendar calendar) {
        if (calendar == null || binding == null) return;
        
        java.util.Calendar today = java.util.Calendar.getInstance();
        boolean isToday = calendar.getYear() == today.get(java.util.Calendar.YEAR)
                && calendar.getMonth() == (today.get(java.util.Calendar.MONTH) + 1)
                && calendar.getDay() == today.get(java.util.Calendar.DAY_OF_MONTH);
        
        if (isToday) {
            if (binding.btnToday.getVisibility() == View.VISIBLE) {
                binding.btnToday.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> binding.btnToday.setVisibility(View.GONE))
                        .start();
            }
        } else {
            if (binding.btnToday.getVisibility() != View.VISIBLE) {
                binding.btnToday.setVisibility(View.VISIBLE);
                binding.btnToday.setAlpha(0f);
                binding.btnToday.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start();
            }
        }
    }

    @Override
    public void onCalendarSelect(Calendar calendar, boolean isClick) {
        if (calendar == null) return;
        
        mCurrentSelectedDate = calendar;
        updateDateTitle(calendar);
        updateTodayButtonVisibility(calendar);

        if (isClick) {
            // 检查当日是否有账单，若无则不弹出底部弹窗
            String dateKey = calendar.getYear() + "-" + twoDigits(calendar.getMonth()) + "-" + twoDigits(calendar.getDay());
            DailyStat stat = mLatestStatsMap.get(dateKey);
            if (stat == null || stat.count == 0) {
                // 如果没有账单统计信息，或者账单数量为0，则不显示弹窗，仅更新节日信息
                updateHolidayInfo(calendar);
                return;
            }

            FragmentManager fragmentManager = getChildFragmentManager();
            if (fragmentManager.isStateSaved()
                    || fragmentManager.findFragmentByTag("DailyBills") != null) {
                return;
            }
            DailyBillsBottomSheetDialogFragment.newInstance(
                    calendar.getYear(),
                    calendar.getMonth(),
                    calendar.getDay()
            ).show(fragmentManager, "DailyBills");

            binding.getRoot().post(() -> {
                if (binding != null && isSameDate(mCurrentSelectedDate, calendar)) {
                    updateHolidayInfo(calendar);
                }
            });
        } else {
            updateHolidayInfo(calendar);
        }
    }

    private static boolean isSameDate(Calendar first, Calendar second) {
        return first != null && second != null
                && first.getYear() == second.getYear()
                && first.getMonth() == second.getMonth()
                && first.getDay() == second.getDay();
    }

    private void updateHolidayInfo(Calendar calendar) {
        int year = calendar.getYear();
        int month = calendar.getMonth();
        int day = calendar.getDay();

        // 1. 农历详情
        Solar solar = Solar.fromYmd(year, month, day);
        Lunar lunar = solar.getLunar();

        binding.layoutCalendarInfo.tvLunarDate.setText(lunar.getDayInChinese());
        String lunarDetail = String.format("%s%s年 %s月 %s日",
                lunar.getYearInGanZhi(), lunar.getYearShengXiao(),
                lunar.getMonthInGanZhi(), lunar.getDayInGanZhi());
        binding.layoutCalendarInfo.tvLunarYear.setText(lunarDetail);

        // 2. 节假日倒计时
        java.util.Calendar selectedCal = java.util.Calendar.getInstance();
        selectedCal.set(year, month - 1, day);
        selectedCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        selectedCal.set(java.util.Calendar.MINUTE, 0);
        selectedCal.set(java.util.Calendar.SECOND, 0);
        selectedCal.set(java.util.Calendar.MILLISECOND, 0);

        String[] nextHoliday = HolidayUtil.getNextHoliday(year, month, day);

        if (nextHoliday != null) {
            binding.layoutCalendarInfo.cardSolar.setVisibility(View.VISIBLE);
            String hDateStr = nextHoliday[0];
            String hName = nextHoliday[1];

            binding.layoutCalendarInfo.tvSolarName.setText(hName);

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date hDate = sdf.parse(hDateStr);

                java.util.Calendar holidayCal = java.util.Calendar.getInstance();
                holidayCal.setTime(hDate);
                holidayCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                holidayCal.set(java.util.Calendar.MINUTE, 0);
                holidayCal.set(java.util.Calendar.SECOND, 0);
                holidayCal.set(java.util.Calendar.MILLISECOND, 0);

                long diffMs = holidayCal.getTimeInMillis() - selectedCal.getTimeInMillis();
                long diffDays = diffMs / (24 * 60 * 60 * 1000);

                binding.layoutCalendarInfo.tvSolarDayVal.setText(String.valueOf(Math.max(0, diffDays)));

                if (diffDays == 0) {
                    binding.layoutCalendarInfo.tvSolarDate.setText("就在今天");
                    binding.layoutCalendarInfo.tvSolarName.setText(hName + " · 享受假期");
                } else if (diffDays == 1) {
                    binding.layoutCalendarInfo.tvSolarDate.setText("明天 (" + new SimpleDateFormat("M月d日", Locale.getDefault()).format(hDate) + ")");
                } else {
                    SimpleDateFormat displayFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
                    binding.layoutCalendarInfo.tvSolarDate.setText(displayFmt.format(hDate));
                }
            } catch (Exception e) {
                binding.layoutCalendarInfo.tvSolarDayVal.setText("-");
            }
        } else {
            binding.layoutCalendarInfo.cardSolar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMonthChange(int year, int month) {
        mVisibleYear = year;
        mVisibleMonth = month;
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", year, month));
        updateCalendarSchemes();
    }

    private void selectToday(boolean scrollCalendar) {
        if (binding == null) return;
        if (scrollCalendar) binding.calendarView.scrollToCurrent(false);
        java.util.Calendar today = java.util.Calendar.getInstance();
        Calendar selected = new Calendar();
        selected.setYear(today.get(java.util.Calendar.YEAR));
        selected.setMonth(today.get(java.util.Calendar.MONTH) + 1);
        selected.setDay(today.get(java.util.Calendar.DAY_OF_MONTH));
        mCurrentSelectedDate = selected;
        mVisibleYear = selected.getYear();
        mVisibleMonth = selected.getMonth();
        updateDateTitle(selected);
        updateTodayButtonVisibility(selected);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) selectToday(true);
    }

    @Override
    public void onCalendarOutOfRange(Calendar calendar) {}

    private void hideLoading() {
        if (binding == null || binding.loadingLayout.getVisibility() == View.GONE) return;

        binding.loadingLayout.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    if (binding != null) {
                        binding.loadingLayout.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSchemeGeneration.incrementAndGet();
        binding = null;
    }
}
