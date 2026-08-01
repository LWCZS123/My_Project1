package com.example.my_project1.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.my_project1.utils.HolidayUtil;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.CalendarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarFragment extends Fragment implements
        CalendarView.OnCalendarSelectListener,
        CalendarView.OnMonthChangeListener {

    private FragmentCalendarBinding binding;
    private BillViewModel billViewModel;
    private BillListAdapter billAdapter;
    private CalendarInfoAdapter infoAdapter;
    private Calendar mCurrentSelectedDate;

    // 缓存已生成的日历 Scheme Map，避免重复生成
    private Map<String, Calendar> mFullSchemeMap = new HashMap<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

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
    }

    private void setupCalendar() {
        binding.calendarView.setOnCalendarSelectListener(this);
        binding.calendarView.setOnMonthChangeListener(this);
        mCurrentSelectedDate = binding.calendarView.getSelectedCalendar();
        updateDateTitle(mCurrentSelectedDate);
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
    }

    private void setupListeners() {
        binding.btnToday.setOnClickListener(v -> binding.calendarView.scrollToCurrent());
        binding.ivAddBill.setOnClickListener(v -> {
            if (!isAdded()) return;
            startActivity(new Intent(requireContext(), AddBillActivity.class));
        });
    }

    private void observeData() {
        // 核心优化：观察 ViewModel 预计算好的统计 Map
        billViewModel.dailyStatsMap.observe(getViewLifecycleOwner(), statsMap -> {
            if (statsMap == null) return;
            updateCalendarSchemes(statsMap);
        });

        // 观察当前选中日期对应的账单列表
        billViewModel.groupedBillsMap.observe(getViewLifecycleOwner(), groupedMap -> {
            refreshBillList();
        });
    }

    private void updateCalendarSchemes(Map<String, DailyStat> statsMap) {
        mExecutor.execute(() -> {
            Map<String, Calendar> schemeMap = new HashMap<>();
            
            // 1. 将账单统计转换为日历 Scheme
            for (Map.Entry<String, DailyStat> entry : statsMap.entrySet()) {
                String dateKey = entry.getKey(); // yyyy-MM-dd
                DailyStat stat = entry.getValue();
                
                Calendar calendar = parseDateKey(dateKey);
                if (calendar == null) continue;

                // 获取并缓存节假日信息
                stat.dayTag = HolidayUtil.getDayTag(calendar.getYear(), calendar.getMonth(), calendar.getDay());
                stat.isHoliday = "休".equals(stat.dayTag);

                Calendar.Scheme scheme = new Calendar.Scheme();
                scheme.setObj(stat);
                scheme.setScheme("s");
                calendar.addScheme(scheme);
                schemeMap.put(calendar.toString(), calendar);
            }

            // 2. 补全可见范围内的节假日（即使没账单也要显示“休/班”）
            addVisibleHolidays(schemeMap);

            mMainHandler.post(() -> {
                if (binding == null) return;
                mFullSchemeMap = schemeMap;
                binding.calendarView.setSchemeDate(schemeMap);
            });
        });
    }

    private void addVisibleHolidays(Map<String, Calendar> schemeMap) {
        // 为了极致性能，我们仅补全当前选中月份中没有账单但有节假日的日期
        Calendar cur = binding.calendarView.getSelectedCalendar();
        if (cur == null) return;
        
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(cur.getYear(), cur.getMonth() - 1, 1);
        int maxDays = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        
        for (int d = 1; d <= maxDays; d++) {
            int year = cur.getYear();
            int month = cur.getMonth();
            String tag = HolidayUtil.getDayTag(year, month, d);
            if (tag != null) {
                Calendar calObj = new Calendar();
                calObj.setYear(year); calObj.setMonth(month); calObj.setDay(d);
                String libKey = calObj.toString();
                if (!schemeMap.containsKey(libKey)) {
                    DailyStat stat = new DailyStat(0, 0, 0);
                    stat.dayTag = tag;
                    stat.isHoliday = "休".equals(tag);
                    Calendar.Scheme s = new Calendar.Scheme();
                    s.setObj(stat);
                    s.setScheme("s");
                    calObj.addScheme(s);
                    schemeMap.put(libKey, calObj);
                }
            }
        }
    }

    private Calendar parseDateKey(String key) {
        try {
            String[] parts = key.split("-");
            Calendar c = new Calendar();
            c.setYear(Integer.parseInt(parts[0]));
            c.setMonth(Integer.parseInt(parts[1]));
            c.setDay(Integer.parseInt(parts[2]));
            return c;
        } catch (Exception e) { return null; }
    }

    private void refreshBillList() {
        if (mCurrentSelectedDate == null || binding == null) return;
        
        String dateKey = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                mCurrentSelectedDate.getYear(), mCurrentSelectedDate.getMonth(), mCurrentSelectedDate.getDay());
        
        Map<String, List<Bill>> groupedMap = billViewModel.groupedBillsMap.getValue();
        List<Bill> bills = (groupedMap != null) ? groupedMap.get(dateKey) : null;
        
        if (bills == null) bills = new ArrayList<>();
        
        List<BillUiModel> uiModels = billViewModel.mapBillsToUiModels(bills);
        List<BillListAdapter.ListItem> items = new ArrayList<>();
        if (!uiModels.isEmpty()) {
            items.add(new BillListAdapter.ListItem(uiModels));
        }
        
        billAdapter.submitList(items);
        infoAdapter.updateDate(mCurrentSelectedDate);
    }

    private void updateDateTitle(Calendar calendar) {
        if (calendar == null || binding == null) return;
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", calendar.getYear(), calendar.getMonth()));
        
        // 计算相对时间（今天/x天前）
        mExecutor.execute(() -> {
            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0); today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0); today.set(java.util.Calendar.MILLISECOND, 0);

            java.util.Calendar target = java.util.Calendar.getInstance();
            target.set(calendar.getYear(), calendar.getMonth() - 1, calendar.getDay());
            target.set(java.util.Calendar.HOUR_OF_DAY, 0); target.set(java.util.Calendar.MINUTE, 0);
            target.set(java.util.Calendar.SECOND, 0); target.set(java.util.Calendar.MILLISECOND, 0);

            long diff = (target.getTimeInMillis() - today.getTimeInMillis()) / (1000 * 60 * 60 * 24);
            String text = diff == 0 ? "今天" : (diff > 0 ? diff + "天后" : Math.abs(diff) + "天前");
            
            mMainHandler.post(() -> {
                if (binding != null) binding.tvRelativeTime.setText(text);
            });
        });
    }

    @Override
    public void onCalendarSelect(Calendar calendar, boolean isClick) {
        if (calendar == null) return;
        if (!isClick && mCurrentSelectedDate != null) return; // 翻页不更新列表
        
        mCurrentSelectedDate = calendar;
        updateDateTitle(calendar);
        refreshBillList();
    }

    @Override
    public void onMonthChange(int year, int month) {
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", year, month));
        // 月份改变时，补全该月的节假日信息
        Map<String, DailyStat> stats = billViewModel.dailyStatsMap.getValue();
        if (stats != null) {
            updateCalendarSchemes(stats);
        }
    }

    @Override
    public void onCalendarOutOfRange(Calendar calendar) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
