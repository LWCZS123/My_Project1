package com.example.my_project1.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.calendar.CalendarDay;
import com.example.my_project1.databinding.FragmentCalendarBinding;
import com.example.my_project1.ui.activity.BillDetailActivity;
import com.example.my_project1.ui.adapter.bill.BillGroupedAdapter;
import com.example.my_project1.ui.adapter.calendar.CalendarDataEngine;
import com.example.my_project1.ui.adapter.calendar.MonthPagerAdapter;
import com.example.my_project1.ui.popup.FilterPopupMenu;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarFragment extends Fragment {

    private static final String TAG      = "CalendarFragment";
    private static final String PERF_TAG = "CalendarPerf";

    private static final int MONTHS_COUNT    = 120;
    private static final int CENTER_POSITION = 60;

    // ─── 格式化工具：静态共享，杜绝在 bind / groupAsync 里反复 new ───────────
    private static final SimpleDateFormat KEY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd",          Locale.getDefault());
    private static final SimpleDateFormat TITLE_FORMAT =
            new SimpleDateFormat("yyyy年MM月dd日 EEEE",  Locale.getDefault());
    private static final SimpleDateFormat YM_FORMAT =
            new SimpleDateFormat("yyyy年MM月",           Locale.CHINESE);
    // SimpleDateFormat 非线程安全，后台线程 groupAsync 中需要自己 new 一个，
    // 但 UI 线程中的三个实例只需要各自一份，不必每帧创建。

    private FragmentCalendarBinding binding;
    private BillViewModel           billViewModel;
    private MonthPagerAdapter       monthPagerAdapter;
    private BillGroupedAdapter      billAdapter;
    private FilterPopupMenu         filterPopupMenu;

    private Calendar selectedDate;
    private Calendar todayDate;
    private Calendar baseDate;
    private int  currentMonthIndex = CENTER_POSITION;
    private int  displayType       = 0;
    private boolean isInitialized  = false;
    private boolean dataReady      = false;

    // volatile：后台写、主线程读，无需额外锁
    private volatile Map<String, List<Bill>> billsByDate  = new HashMap<>();
    private volatile Map<String, Double>     incomeByDate  = new HashMap<>();
    private volatile Map<String, Double>     expenseByDate = new HashMap<>();

    private ExecutorService    groupingExecutor;
    private CalendarDataEngine dataEngine;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════

    public static CalendarFragment newInstance() { return new CalendarFragment(); }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        todayDate    = Calendar.getInstance();
        selectedDate = (Calendar) todayDate.clone();
        baseDate     = (Calendar) todayDate.clone();

        // 低优先级后台线程，避免抢占主线程资源
        groupingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BillGroupThread");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        dataEngine = new CalendarDataEngine(
                requireContext().getApplicationContext(), todayDate, CENTER_POSITION);
        dataEngine.setBillDataProvider(new CalendarDataEngine.BillDataProvider() {
            @Override public List<Bill> getBillsForDate(String k) { return billsByDate.get(k); }
            @Override public double getIncomeForDate(String k)    { return incomeByDate.getOrDefault(k, 0.0); }
            @Override public double getExpenseForDate(String k)   { return expenseByDate.getOrDefault(k, 0.0); }
        });
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(requireView(), (v, insets) -> {

            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());

            v.setPadding(0, bars.top, 0, 0);

            return insets;
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding      = FragmentCalendarBinding.inflate(inflater, container, false);
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);



        // ── 1. 先只做轻量初始化，不绑定 Adapter ──────────────────────────────
        setupBillList();
        setupListeners();
        updateTitle();
        observeData();

        // ── 2. 日历 Adapter 延迟到下一帧（跳过转场动画帧）绑定 ──────────────
        //    postDelayed 100ms：等待 Fragment 转场动画结束再开始 inflate 120 个
        //    月份 holder，避免同帧内出现 jank。
        //    如果转场动画时长 < 100ms，可适当减小到 50ms。
        binding.getRoot().postDelayed(this::setupCalendarPager, 100);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
        if (dataEngine != null)       { dataEngine.release();       dataEngine = null; }
        if (groupingExecutor != null) { groupingExecutor.shutdownNow(); groupingExecutor = null; }
        binding = null;
    }

    // ════════════════════════════════════════════════════════════
    // 日历 Pager 初始化（延迟执行）
    // ════════════════════════════════════════════════════════════

    private void setupCalendarPager() {
        if (binding == null) return; // View 已销毁（极端情况保护）

        // ── offscreenPageLimit：移除强制预加载，使用默认值 1 ─────────────────
        //    ViewPager2 默认只保留当前页，滑动时懒加载相邻页，
        //    首屏只需 inflate 1 个月份（35~42 个 item），压力大幅降低。
        //    注意：不要再调用 setOffscreenPageLimit(2)！

        monthPagerAdapter = new MonthPagerAdapter();
        monthPagerAdapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);

        dataEngine.setCallback((page, days, meta) -> {
            if (binding == null) return;
            monthPagerAdapter.deliverData(page, days, meta);

            // ── 3. 高度更新：基于行数固定公式，无需强制 measure ─────────────
            if (page == currentMonthIndex) {
                applyCalendarHeight(meta.rowCount);
            }
        });

        monthPagerAdapter.init(MONTHS_COUNT, dataEngine);
        monthPagerAdapter.setOnDayClickListener(this::onDayClick);
        binding.vpCalendar.setAdapter(monthPagerAdapter);

        // 仅保留默认 offscreenPageLimit（不调用 setOffscreenPageLimit 即为默认 1）
        binding.vpCalendar.setUserInputEnabled(true);
        binding.rvBills.setNestedScrollingEnabled(true);

        // 跳转到今天所在页（无动画，避免首帧闪动）
        binding.vpCalendar.post(() -> {
            binding.vpCalendar.setCurrentItem(CENTER_POSITION, false);
        });
        currentMonthIndex = CENTER_POSITION;
        updateYM();

        binding.vpCalendar.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                // 跳过第一次 setCurrentItem 触发的回调
                if (!isInitialized) { isInitialized = true; return; }

                currentMonthIndex = pos;
                updateYM();
                dataEngine.requestMonth(pos);

                // ── 4. 不再 measure itemView！
                //    由 dataEngine.setCallback 在数据就绪时调用 applyCalendarHeight()。
                //    翻页时数据通常已缓存，回调几乎即时触发，高度切换流畅无跳变。
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    // 高度计算：行数 × ROW_HEIGHT_DP（固定公式，零测量开销）
    // ════════════════════════════════════════════════════════════

    /**
     * 根据当月行数（5 或 6）精确设置 ViewPager2 高度。
     * 公式与 MonthPagerAdapter.applyHeight() 完全一致，保证两侧数值相同。
     * 在主线程调用，无需 post。
     */
    private void applyCalendarHeight(int rowCount) {
        if (binding == null) return;
        float density = getResources().getDisplayMetrics().density;
        int heightPx  = (int) (MonthPagerAdapter.ROW_HEIGHT_DP * rowCount * density);
        ViewGroup.LayoutParams lp = binding.vpCalendar.getLayoutParams();
        if (lp.height != heightPx) {
            lp.height = heightPx;
            binding.vpCalendar.setLayoutParams(lp);
            Log.d(PERF_TAG, "applyCalendarHeight: rows=" + rowCount + " px=" + heightPx);
        }
    }

    // ════════════════════════════════════════════════════════════
    // 点击日期
    // ════════════════════════════════════════════════════════════

    private void onDayClick(CalendarDay day) {
        selectedDate.set(day.getYear(), day.getMonth() - 1, day.getDay());
        String key = KEY_FORMAT.format(selectedDate.getTime());
        dataEngine.updateSelectedKey(key, currentMonthIndex);
        updateTitle();
        loadDayBills();
    }

    // ════════════════════════════════════════════════════════════
    // 账单列表
    // ════════════════════════════════════════════════════════════

    private void setupBillList() {
        billAdapter = new BillGroupedAdapter(requireContext());
        billAdapter.setOnBillClickListener(bill -> {
            if (!isAdded() || getContext() == null || getActivity() == null) return;
            Intent intent = new Intent(requireContext(), BillDetailActivity.class);
            if (bill.getObjectId() != null && !bill.getObjectId().isEmpty())
                intent.putExtra("bill_id", bill.getObjectId());
            intent.putExtra("bill_local_id", bill.getId());
            startActivity(intent);
            if (getActivity() != null)
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        binding.rvBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBills.setAdapter(billAdapter);
        binding.rvBills.setItemAnimator(null);
        binding.rvBills.setHasFixedSize(true);
        binding.rvBills.setVisibility(View.GONE);
        binding.llEmpty.setVisibility(View.VISIBLE);
    }

    private void loadDayBills() {
        if (binding == null || !dataReady) return;
        String key  = KEY_FORMAT.format(selectedDate.getTime());
        List<Bill> all    = billsByDate.get(key);
        List<Bill> filter = new ArrayList<>();
        if (all != null) {
            for (Bill b : all) {
                if      (displayType == 0)                  filter.add(b);
                else if (displayType == 1 && b.getType() == 0) filter.add(b);
                else if (displayType == 2 && b.getType() == 1) filter.add(b);
            }
        }
        if (!filter.isEmpty()) {
            binding.rvBills.setVisibility(View.VISIBLE);
            binding.llEmpty.setVisibility(View.GONE);
            billAdapter.setBills(filter);
        } else {
            binding.rvBills.setVisibility(View.GONE);
            binding.llEmpty.setVisibility(View.VISIBLE);
        }
    }

    // ════════════════════════════════════════════════════════════
    // 监听数据（后台分组 → 主线程刷新）
    // ════════════════════════════════════════════════════════════

    private void observeData() {
        dataReady = true;
        billViewModel.getAllBills().observe(getViewLifecycleOwner(), bills -> {
            if (bills == null) return;
            long t0 = SystemClock.elapsedRealtime();
            groupAsync(bills, () -> {
                Log.d(PERF_TAG, "observeData_done: " + (SystemClock.elapsedRealtime() - t0) + "ms");
                // ── 5. 延迟 50ms 刷新，避免数据到达与首屏渲染撞帧 ─────────
                mainHandler.postDelayed(() -> {
                    if (binding == null) return;
                    dataEngine.invalidateVisibleRange(currentMonthIndex);
                    loadDayBills();
                }, 50);
            });
        });
    }

    /**
     * 后台线程分组账单。
     *
     * 优化点：
     * ① groupAsync 内部自己 new SimpleDateFormat（SDF 非线程安全），
     *   不依赖外部静态实例，安全且无竞争。
     * ② 用 computeIfAbsent 替代 getOrDefault + put，减少一次 get 查找。
     * ③ done 回调在主线程执行（mainHandler.post），外部无需再套一层 post。
     */
    private void groupAsync(List<Bill> list, Runnable done) {
        final List<Bill> snap = new ArrayList<>(list); // 快照，避免主线程并发修改
        if (groupingExecutor == null || groupingExecutor.isShutdown()) return;

        groupingExecutor.execute(() -> {
            long t0 = SystemClock.elapsedRealtime();

            // 后台线程专属 SDF 实例
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            Map<String, List<Bill>> mapB = new HashMap<>();
            Map<String, Double>     mapI = new HashMap<>();
            Map<String, Double>     mapE = new HashMap<>();

            for (Bill b : snap) {
                if (b.getBillTime() == null) continue;
                String k = sdf.format(b.getBillTime());
                mapB.computeIfAbsent(k, x -> new ArrayList<>()).add(b);
                if (b.getType() == 0) mapE.merge(k, b.getAmount(), Double::sum);
                else                  mapI.merge(k, b.getAmount(), Double::sum);
            }

            Log.d(PERF_TAG, "group_bg: keys=" + mapB.size()
                    + " took=" + (SystemClock.elapsedRealtime() - t0) + "ms");

            mainHandler.post(() -> {
                billsByDate  = mapB;
                incomeByDate = mapI;
                expenseByDate = mapE;
                if (done != null) done.run();
            });
        });
    }

    // ════════════════════════════════════════════════════════════
    // 其他 UI 方法
    // ════════════════════════════════════════════════════════════

    private void setupListeners() {
        binding.ivBackToToday.setOnClickListener(v -> backToToday());
        binding.ivPrevMonth.setOnClickListener(v -> {
            if (currentMonthIndex > 0)
                binding.vpCalendar.setCurrentItem(currentMonthIndex - 1, true);
        });
        binding.ivNextMonth.setOnClickListener(v -> {
            if (currentMonthIndex < MONTHS_COUNT - 1)
                binding.vpCalendar.setCurrentItem(currentMonthIndex + 1, true);
        });
        binding.ivRefresh.setOnClickListener(v -> billViewModel.forceSyncFromCloud());
        binding.ivMenu.setOnClickListener(this::showFilterMenu);
    }

    private void backToToday() {
        todayDate    = Calendar.getInstance();
        selectedDate = (Calendar) todayDate.clone();
        String key   = KEY_FORMAT.format(todayDate.getTime());
        dataEngine.refreshTodayKey(todayDate, key, key, CENTER_POSITION);
        binding.vpCalendar.setCurrentItem(CENTER_POSITION, true);
        updateTitle();
        loadDayBills();
    }

    private void showFilterMenu(View anchor) {
        if (filterPopupMenu == null) {
            filterPopupMenu = new FilterPopupMenu(requireContext(), type -> {
                switch (type) {
                    case ALL:     setDisplayType(0); break;
                    case EXPENSE: setDisplayType(1); break;
                    case INCOME:  setDisplayType(2); break;
                }
            });
        }
        filterPopupMenu.show(anchor);
    }

    private void setDisplayType(int t) {
        if (displayType == t) return;
        displayType = t;
        monthPagerAdapter.setDisplayType(t);
        mainHandler.post(() -> dataEngine.invalidateVisibleRange(currentMonthIndex));
        loadDayBills();
    }

    private void updateTitle() {
        if (binding != null)
            binding.tvSelectedDate.setText(TITLE_FORMAT.format(selectedDate.getTime()));
    }

    private void updateYM() {
        if (binding != null) {
            Calendar c = (Calendar) baseDate.clone();
            c.add(Calendar.MONTH, currentMonthIndex - CENTER_POSITION);
            binding.tvYearMonth.setText(YM_FORMAT.format(c.getTime()));
        }
    }
}