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
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.HolidayUtil;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.CalendarView;

import java.text.SimpleDateFormat;
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

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat mBillDateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 设置沉浸式透明状态栏
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
            intent.putExtra("bill_id", bill.getObjectId());
            intent.putExtra("bill_local_id", bill.getId());
            startActivity(intent);
        });

        infoAdapter = new CalendarInfoAdapter();
        // 移除 HeaderAdapter，因为已经在 CalendarView 内部添加了固定拉杆
        ConcatAdapter concatAdapter = new ConcatAdapter(infoAdapter, billAdapter);

        binding.rvBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBills.setAdapter(concatAdapter);
    }

    private void setupListeners() {
        binding.btnToday.setOnClickListener(v -> binding.calendarView.scrollToCurrent());
        binding.ivAddBill.setOnClickListener(v -> {
            if (!isAdded()) return;
            Intent intent = new Intent(requireContext(), AddBillActivity.class);
            startActivity(intent);
        });
        binding.ivMore.setOnClickListener(v -> {
            // Show menu if needed
        });
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadBillsForSelectedDate();
        }
    }

    private void observeData() {
        billViewModel.getAllBills().observe(getViewLifecycleOwner(), bills -> {
            if (bills == null || isHidden()) return;
            processBillsAsync(bills, mCurrentSelectedDate);
        });
    }

    private void loadBillsForDate(Calendar pivot) {
        List<Bill> allBills = billViewModel.getAllBills().getValue();
        if (allBills != null) {
            processBillsAsync(allBills, pivot);
        }
    }

    private void processBillsAsync(final List<Bill> bills, final Calendar pivot) {
        if (bills == null) return;
        final Calendar selectedSnapshot = mCurrentSelectedDate;
        
        mExecutor.execute(() -> {
            Map<String, Calendar> schemeMap = new HashMap<>();
            Map<String, Double> incomeMap = new HashMap<>();
            Map<String, Double> expenseMap = new HashMap<>();
            Map<String, Integer> countMap = new HashMap<>();

            for (Bill bill : bills) {
                if (bill.getBillTime() == null) continue;
                String key = mBillDateFmt.format(bill.getBillTime());
                
                Integer count = countMap.get(key);
                countMap.put(key, (count == null ? 0 : count) + 1);

                if (bill.getType() == 1) {
                    Double inc = incomeMap.get(key);
                    incomeMap.put(key, (inc == null ? 0.0 : inc) + bill.getAmount());
                } else {
                    Double exp = expenseMap.get(key);
                    expenseMap.put(key, (exp == null ? 0.0 : exp) + bill.getAmount());
                }
            }

            // Combine into schemes
            for (String key : countMap.keySet()) {
                Calendar calendar = new Calendar();
                try {
                    String[] parts = key.split("-");
                    calendar.setYear(Integer.parseInt(parts[0]));
                    calendar.setMonth(Integer.parseInt(parts[1]));
                    calendar.setDay(Integer.parseInt(parts[2]));
                } catch (Exception e) { continue; }

                Double incVal = incomeMap.get(key);
                double income = incVal != null ? incVal : 0.0;
                Double expVal = expenseMap.get(key);
                double expense = expVal != null ? expVal : 0.0;
                Integer countVal = countMap.get(key);
                int count = countVal != null ? countVal : 0;

                DailyStat stat = new DailyStat(income, expense, count);
                stat.dayTag = HolidayUtil.getDayTag(calendar.getYear(), calendar.getMonth(), calendar.getDay());
                stat.isHoliday = "休".equals(stat.dayTag);
                
                Calendar.Scheme scheme = new Calendar.Scheme();
                scheme.setObj(stat);
                scheme.setScheme("s");
                calendar.addScheme(scheme);
                
                // IMPORTANT: The library requires calendar.toString() as key (yyyyMMdd)
                schemeMap.put(calendar.toString(), calendar);
            }

            // Add pure holidays - 扩大范围以覆盖当前可见月份及前后各一月
            java.util.Calendar cal = java.util.Calendar.getInstance();
            if (pivot != null) {
                cal.set(pivot.getYear(), pivot.getMonth() - 1, 1);
            } else if (selectedSnapshot != null) {
                cal.set(selectedSnapshot.getYear(), selectedSnapshot.getMonth() - 1, 1);
            }
            cal.add(java.util.Calendar.MONTH, -2);
            for (int i = 0; i < 150; i++) { // 150天约5个月，足够覆盖可见区域
                int y = cal.get(java.util.Calendar.YEAR);
                int m = cal.get(java.util.Calendar.MONTH) + 1;
                int d = cal.get(java.util.Calendar.DAY_OF_MONTH);
                String tag = HolidayUtil.getDayTag(y, m, d);
                if (tag != null) {
                    Calendar c = new Calendar();
                    c.setYear(y); c.setMonth(m); c.setDay(d);
                    String libKey = c.toString();
                    if (!schemeMap.containsKey(libKey)) {
                        DailyStat stat = new DailyStat(0, 0, 0);
                        stat.dayTag = tag;
                        stat.isHoliday = "休".equals(tag);
                        Calendar.Scheme s = new Calendar.Scheme();
                        s.setObj(stat);
                        s.setScheme("s");
                        c.addScheme(s);
                        schemeMap.put(libKey, c);
                    }
                }
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
            }

            // Filter for selected date
            List<Bill> filtered = new ArrayList<>();
            if (selectedSnapshot != null) {
                String selectedKey = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                        selectedSnapshot.getYear(), selectedSnapshot.getMonth(), selectedSnapshot.getDay());
                for (Bill bill : bills) {
                    if (bill.getBillTime() != null && mBillDateFmt.format(bill.getBillTime()).equals(selectedKey)) {
                        filtered.add(bill);
                    }
                }
            }

            final List<BillListAdapter.ListItem> listItems = new ArrayList<>();
            if (!filtered.isEmpty()) {
                listItems.add(new BillListAdapter.ListItem(filtered));
            }

            mMainHandler.post(() -> {
                if (binding == null) return;
                binding.calendarView.setSchemeDate(schemeMap);
                billAdapter.submitList(listItems);
                if (selectedSnapshot != null) {
                    infoAdapter.updateDate(selectedSnapshot);
                }
            });
        });
    }

    private void loadBillsForSelectedDate() {
        loadBillsForDate(mCurrentSelectedDate);
    }

    private void updateDateTitle(Calendar calendar) {
        if (calendar == null) return;
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", calendar.getYear(), calendar.getMonth()));

        mExecutor.execute(() -> {
            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0);
            today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0);
            today.set(java.util.Calendar.MILLISECOND, 0);

            java.util.Calendar target = java.util.Calendar.getInstance();
            target.set(calendar.getYear(), calendar.getMonth() - 1, calendar.getDay());
            target.set(java.util.Calendar.HOUR_OF_DAY, 0);
            target.set(java.util.Calendar.MINUTE, 0);
            target.set(java.util.Calendar.SECOND, 0);
            target.set(java.util.Calendar.MILLISECOND, 0);

            long diff = (target.getTimeInMillis() - today.getTimeInMillis()) / (1000 * 60 * 60 * 24);
            final String relativeText;
            if (diff == 0) {
                relativeText = "今天";
            } else if (diff > 0) {
                relativeText = diff + "天后";
            } else {
                relativeText = Math.abs(diff) + "天前";
            }

            mMainHandler.post(() -> {
                if (binding != null) binding.tvRelativeTime.setText(relativeText);
            });
        });
    }

    @Override
    public void onCalendarOutOfRange(Calendar calendar) {
    }

    @Override
    public void onCalendarSelect(Calendar calendar, boolean isClick) {
        if (calendar == null) return;
        // 优化：滑动翻页时不主动选中日期，仅响应手动点击
        if (!isClick && mCurrentSelectedDate != null) {
            // 如果不是手动点击，且已经有选中日期，则忽略翻页带来的自动选中
            return;
        }
        mCurrentSelectedDate = calendar;
        updateDateTitle(calendar);

        // 关键修复：选中新日期时，立即刷新底部卡片
        if (infoAdapter != null) {
            infoAdapter.updateDate(calendar);
        }

        loadBillsForSelectedDate();
    }

    @Override
    public void onMonthChange(int year, int month) {
        binding.tvYearMonth.setText(String.format(Locale.getDefault(), "%d / %d", year, month));
        // 优化：月份切换时，重新加载数据以刷新该月的节假日背景
        Calendar pivot = new Calendar();
        pivot.setYear(year);
        pivot.setMonth(month);
        pivot.setDay(1);
        loadBillsForDate(pivot);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
