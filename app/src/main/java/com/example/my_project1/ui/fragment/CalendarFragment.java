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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.calendar.DailyStat;
import com.example.my_project1.databinding.FragmentCalendarBinding;
import com.example.my_project1.ui.activity.AddBillActivity;
import com.example.my_project1.ui.activity.BillDetailActivity;
import com.example.my_project1.ui.adapter.bill.BillListAdapter;
import com.example.my_project1.ui.adapter.calendar.CalendarInfoAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.HolidayUtil;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.CalendarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CalendarFragment extends Fragment implements
        CalendarView.OnCalendarSelectListener,
        CalendarView.OnMonthChangeListener {

    private FragmentCalendarBinding binding;
    private BillViewModel billViewModel;
    private BillListAdapter billAdapter;
    private CalendarInfoAdapter infoAdapter;
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
    private final AtomicInteger mBillRenderGeneration = new AtomicInteger();
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
        setupRecyclerView();
        setupListeners();
        observeData();
        selectToday(false);
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

    private void setupRecyclerView() {
        billAdapter = new BillListAdapter(requireContext());
        billAdapter.setOnBillClickListener(bill -> {
            if (bill == null || !isAdded()) return;
            Intent intent = new Intent(requireContext(), BillDetailActivity.class);
            intent.putExtra("bill_id", bill.objectId);
            intent.putExtra("bill_local_id", bill.localId);
            startActivity(intent);
        });

        infoAdapter = new CalendarInfoAdapter();
        ConcatAdapter concatAdapter = new ConcatAdapter(infoAdapter, billAdapter);

        binding.rvBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBills.setAdapter(concatAdapter);
        binding.rvBills.setHasFixedSize(true);
        binding.rvBills.setItemViewCacheSize(12);
        binding.rvBills.setItemAnimator(null);
    }

    private void setupListeners() {
        binding.btnToday.setOnClickListener(v -> binding.calendarView.scrollToCurrent(true));
        binding.ivAddBill.setOnClickListener(v -> {
            if (!isAdded()) return;
            startActivity(new Intent(requireContext(), AddBillActivity.class));
        });
    }

    private void observeData() {
        // 核心优化：观察 ViewModel 预计算好的统计 Map
        billViewModel.dailyStatsMap.observe(getViewLifecycleOwner(), statsMap -> {
            mLatestStatsMap = statsMap == null ? new HashMap<>() : statsMap;
            mStatsVersion++;
            synchronized (mMonthSchemeCache) {
                mMonthSchemeCache.clear();
            }
            updateCalendarSchemes();
            // 在日历数据加载并更新 Scheme 后初次隐藏 Loading（或者等待下方账单列表渲染）
        });

        // 🚀 核心优化：按需加载选中日期的账单
        billViewModel.selectedDateBills.observe(getViewLifecycleOwner(), this::renderBillList);
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

    private void renderBillList(List<Bill> bills) {
        if (mCurrentSelectedDate == null || binding == null) return;
        final int generation = mBillRenderGeneration.incrementAndGet();
        final List<Bill> snapshot = bills == null ? new ArrayList<>() : new ArrayList<>(bills);
        AppExecutors.get().computation().execute(() -> {
            List<BillUiModel> uiModels = billViewModel.mapBillsToUiModels(snapshot);
            List<BillListAdapter.ListItem> items = new ArrayList<>();
            if (uiModels != null) {
                for (BillUiModel model : uiModels) {
                    items.add(new BillListAdapter.ListItem(model));
                }
            }
            AppExecutors.get().mainThread().execute(() -> {
                if (binding == null || generation != mBillRenderGeneration.get()) return;
                billAdapter.submitList(items, this::hideLoading);
            });
        });
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
     * 只有当选中的不是今天时（移动到其他月份或点击其他日期），才显示该按钮。
     */
    private void updateTodayButtonVisibility(Calendar calendar) {
        if (calendar == null || binding == null) return;
        
        java.util.Calendar today = java.util.Calendar.getInstance();
        boolean isToday = calendar.getYear() == today.get(java.util.Calendar.YEAR)
                && calendar.getMonth() == (today.get(java.util.Calendar.MONTH) + 1)
                && calendar.getDay() == today.get(java.util.Calendar.DAY_OF_MONTH);
        
        if (isToday) {
            // 如果已经是今天，平滑隐藏按钮
            if (binding.btnToday.getVisibility() == View.VISIBLE) {
                binding.btnToday.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> binding.btnToday.setVisibility(View.GONE))
                        .start();
            }
        } else {
            // 如果不是今天，显示按钮
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
        infoAdapter.updateDate(calendar);
        updateDateTitle(calendar);
        updateTodayButtonVisibility(calendar);
        
        // 🚀 按需通知 ViewModel 切换日期，触发 selectedDateBills 观察者
        billViewModel.setSelectedDate(calendar.getYear(), calendar.getMonth(), calendar.getDay());
    }

    @Override
    public void onMonthChange(int year, int month) {
        mVisibleYear = year;
        mVisibleMonth = month;
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", year, month));
        updateCalendarSchemes();
    }

    private void selectToday(boolean scrollCalendar) {
        if (binding == null || billViewModel == null) return;
        if (scrollCalendar) binding.calendarView.scrollToCurrent(false);
        java.util.Calendar today = java.util.Calendar.getInstance();
        Calendar selected = new Calendar();
        selected.setYear(today.get(java.util.Calendar.YEAR));
        selected.setMonth(today.get(java.util.Calendar.MONTH) + 1);
        selected.setDay(today.get(java.util.Calendar.DAY_OF_MONTH));
        mCurrentSelectedDate = selected;
        infoAdapter.updateDate(selected);
        mVisibleYear = selected.getYear();
        mVisibleMonth = selected.getMonth();
        updateDateTitle(selected);
        updateTodayButtonVisibility(selected);
        billViewModel.setSelectedDate(selected.getYear(), selected.getMonth(), selected.getDay());
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
        mBillRenderGeneration.incrementAndGet();
        binding = null;
    }
}
