package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.ActivityBudgetBinding;
import com.example.my_project1.ui.adapter.budget.CategoryBudgetAdapter;
import com.example.my_project1.ui.chart.BudgetChartManager;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.fragment.AddBudgetFragment;
import com.example.my_project1.ui.fragment.AddCategoryBudgetFragment;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BudgetPeriodHelper;
import com.example.my_project1.work.BudgetResetScheduler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BudgetActivity extends AppCompatActivity {

    private ActivityBudgetBinding  binding;
    private BudgetViewModel        vm;
    private CategoryViewModel      categoryVm;
    private CategoryBudgetAdapter  adapter;
    private BudgetChartManager     chartManager;

    // 当前图表类型：true = 折线图，false = 饼图
    private boolean showingLineChart = true;

    /**
     * 上次从云端同步的时间戳，用于防抖：
     * 切回页面时若距上次同步不足 30 秒则跳过，避免频繁网络请求。
     */
    private long lastSyncTimeMs = 0;
    private static final long SYNC_DEBOUNCE_MS = 30_000L; // 30 秒

    /**
     * 分类 cloudId 到名称/图标的缓存。
     * 由 observeCategoryData() 实时填充，保证 item 构建时数据可用。
     */
    private final Map<String, String> categoryNameCache = new HashMap<>();
    private final Map<String, String> categoryIconCache = new HashMap<>();

    /**
     * 供 AddCategoryBudgetFragment 在保存后补充新分类的缓存，
     * 避免列表刷新时因缓存缺失导致图标/名称为空。
     */
    public void updateCategoryCache(String catId, String name, String iconUri) {
        if (catId == null) return;
        if (name    != null) categoryNameCache.put(catId, name);
        if (iconUri != null) categoryIconCache.put(catId, iconUri);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityBudgetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(0, top, 0, 0);

            return insets;
        });

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);



        vm         = new ViewModelProvider(this).get(BudgetViewModel.class);
        categoryVm = new ViewModelProvider(this).get(CategoryViewModel.class);
        chartManager = new BudgetChartManager(this);

        // 注册天/周预算自动重置任务，首次触发时间对齐到下一个凌晨 0 点
        BudgetResetScheduler.schedule(this);

        initRecyclerView();
        initCharts();
        initTabs();
        initChartTypeTabs();
        initButtons();
        observeCategoryData();
        observeViewModel();

        // 从云端拉取最新预算数据并合并到本地（首次进入）
        // 云端同步完成后，Room LiveData 会自动触发 UI 刷新，无需额外处理
        triggerCloudSync();
    }

    /**
     * 切回预算页面时触发云端同步，使来自其他设备的新增/修改数据能及时拉取。
     * 加入 30 秒防抖：避免快速前后台切换导致频繁网络请求。
     */
    @Override
    protected void onResume() {
        super.onResume();
        triggerCloudSync();
    }

    /**
     * 防抖同步：距上次同步不足 SYNC_DEBOUNCE_MS 则跳过。
     */
    private void triggerCloudSync() {
        long now = System.currentTimeMillis();
        if (now - lastSyncTimeMs < SYNC_DEBOUNCE_MS) return;
        lastSyncTimeMs = now;
        vm.syncFromCloud(success -> {
            // Room LiveData 变更会自动刷新 UI；
            // 若同步带来新数据，额外刷新一次图表数据保证最新
            if (Boolean.TRUE.equals(success)) {
                vm.loadDailyChartData();
                if (!showingLineChart) vm.loadPieChartData(categoryNameCache);
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  RecyclerView
    // ════════════════════════════════════════════════════════

    private void initRecyclerView() {
        adapter = new CategoryBudgetAdapter();
        binding.rvCategoryBudgets.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategoryBudgets.setAdapter(adapter);

        adapter.setOnItemClickListener(new CategoryBudgetAdapter.OnItemClickListener() {

            @Override
            public void onEdit(CategoryBudgetAdapter.CategoryBudgetItem item) {
                AddCategoryBudgetFragment.newInstance(item.budget)
                        .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
            }

            @Override
            public void onDelete(int budgetId) {
                new ConfirmDialog(BudgetActivity.this)
                        .setTitle("删除预算")
                        .setMessage("确定要删除该分类预算吗？")
                        .setConfirmListener(() -> {
                            vm.deleteCategoryBudget(budgetId);
                            Toast.makeText(BudgetActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                        })
                        .setCancelListener(() -> {})
                        .show();
            }

            /**
             * item 点击 -> 跳转分类预算详情页，携带分类名称和图标。
             */
            @Override
            public void onItemClick(CategoryBudgetAdapter.CategoryBudgetItem item) {
                String catCloudId = item.budget.getTargetId();
                String catName    = categoryNameCache.get(catCloudId);
                String catIcon    = categoryIconCache.get(catCloudId);

                // 缓存未命中时从 item 本身兜底读取
                if (catName == null && item.category != null) catName = item.category.getName();
                if (catIcon == null && item.category != null) catIcon = item.category.getIconUri();

                CategoryBudgetDetailActivity.start(
                        BudgetActivity.this, item.budget, catName, catIcon, catCloudId);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

            }

            /**
             * Adapter 通知宿主补充某条 item 的分类信息。
             * 仅当缓存中存在完整数据时才更新，避免触发无限刷新循环。
             */
            @Override
            public void onRequestCategoryInfo(String targetId, int adapterPosition) {
                if (targetId == null || targetId.isEmpty()) return;
                String name = categoryNameCache.get(targetId);
                String icon = categoryIconCache.get(targetId);
                if (name == null || name.isEmpty()) return;

                List<CategoryBudgetAdapter.CategoryBudgetItem> current =
                        new ArrayList<>(getCurrentList());
                if (adapterPosition < 0 || adapterPosition >= current.size()) return;

                CategoryBudgetAdapter.CategoryBudgetItem old = current.get(adapterPosition);
                if (old.budget.getTargetId() == null
                        || !old.budget.getTargetId().equals(targetId)) return;

                Category cat = new Category();
                cat.setName(name);
                cat.setIconUri(icon);

                CategoryBudgetAdapter.CategoryBudgetItem updated =
                        new CategoryBudgetAdapter.CategoryBudgetItem(old.budget, cat, old.spentAmount);
                current.set(adapterPosition, updated);
                adapter.submitList(current);
            }
        });
    }

    private List<CategoryBudgetAdapter.CategoryBudgetItem> getCurrentList() {
        List<CategoryBudgetAdapter.CategoryBudgetItem> list = adapter.getCurrentList();
        return list != null ? list : new ArrayList<>();
    }

    // ════════════════════════════════════════════════════════
    //  图表初始化
    // ════════════════════════════════════════════════════════

    private void initCharts() {
        chartManager.initLineChart(binding.lineChart, daysInCurrentPeriod());
        chartManager.initPieChart(binding.pieChart);
    }

    private int daysInCurrentPeriod() {
        boolean isYear = Budget.TYPE_YEAR.equals(vm.getBudgetType());
        if (isYear) {
            int year = vm.getCurrentYear();
            return ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) ? 366 : 365;
        } else {
            Calendar cal = Calendar.getInstance();
            cal.set(vm.getCurrentYear(), vm.getCurrentMonth() - 1, 1);
            return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
    }

    // ════════════════════════════════════════════════════════
    //  Tab 初始化
    // ════════════════════════════════════════════════════════

    private void initTabs() {
        binding.tabPieExpense.setOnClickListener(v -> {
            vm.switchToMonth();
            updatePeriodTabStyle(true);
        });
        binding.tabPieIncome.setOnClickListener(v -> {
            vm.switchToYear();
            updatePeriodTabStyle(false);
        });
        updatePeriodTabStyle(true);
    }

    private void initChartTypeTabs() {
        binding.tabChartLine.setOnClickListener(v -> switchChartType(true));
        binding.tabChartPie.setOnClickListener(v  -> switchChartType(false));
    }

    private void switchChartType(boolean toLine) {
        showingLineChart = toLine;
        binding.llLineChartContainer.setVisibility(toLine ? View.VISIBLE : View.GONE);
        binding.llPieChartContainer.setVisibility(toLine  ? View.GONE   : View.VISIBLE);
        applyChartTab(binding.tabChartLine, toLine);
        applyChartTab(binding.tabChartPie, !toLine);
        if (!toLine) vm.loadPieChartData(categoryNameCache);
    }

    private void applyChartTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            tab.setTextColor(0xFFFFFFFF);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF888888);
        }
    }

    private void updatePeriodTabStyle(boolean isMonth) {
        applyPeriodTab(binding.tabPieExpense, isMonth);
        applyPeriodTab(binding.tabPieIncome, !isMonth);
    }

    private void applyPeriodTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            tab.setTextColor(0xFFFFFFFF);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF888888);
        }
    }

    // ════════════════════════════════════════════════════════
    //  按钮
    // ════════════════════════════════════════════════════════

    private void initButtons() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.ivAddBudget.setOnClickListener(v ->
                AddBudgetFragment.newInstance(null)
                        .show(getSupportFragmentManager(), AddBudgetFragment.TAG));

        binding.ivEditBudget.setOnClickListener(v -> {
            Budget current = vm.getTotalBudget().getValue();
            AddBudgetFragment.newInstance(current)
                    .show(getSupportFragmentManager(), AddBudgetFragment.TAG);
        });

        binding.btnAddCategoryBudget.setOnClickListener(v -> {
            if (vm.getTotalBudget().getValue() == null) {
                Toast.makeText(this, "请先设置总预算", Toast.LENGTH_SHORT).show();
                return;
            }
            AddCategoryBudgetFragment.newInstance(null)
                    .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
        });
    }

    // ════════════════════════════════════════════════════════
    //  分类数据监听
    // ════════════════════════════════════════════════════════

    /**
     * 监听所有支出分类数据，实时填充 categoryNameCache / categoryIconCache。
     * 缓存更新后触发分类预算列表重建，确保 item 中分类信息完整显示。
     */
    private void observeCategoryData() {
        categoryVm.getExpenseCategories(vm.getUserId()).observe(this, categories -> {
            if (categories == null) return;

            for (CategoryWithSubCategories cws : categories) {
                if (cws.category != null) {
                    String id   = cws.category.getCloudId();
                    String name = cws.category.getName();
                    String icon = cws.category.getIconUri();
                    if (id != null && name != null) {
                        categoryNameCache.put(id, name);
                        if (icon != null) categoryIconCache.put(id, icon);
                    }
                }
                if (cws.subCategories != null) {
                    for (SubCategory sub : cws.subCategories) {
                        String id   = sub.getCloudId();
                        String name = sub.getName();
                        String icon = sub.getIconUri();
                        if (id != null && name != null) {
                            categoryNameCache.put(id, name);
                            if (icon != null) categoryIconCache.put(id, icon);
                        }
                    }
                }
            }

            // 缓存更新后重建分类预算列表，解决图标/名称缺失问题
            rebuildCategoryBudgetItems();
        });
    }

    // ════════════════════════════════════════════════════════
    //  ViewModel 观察
    // ════════════════════════════════════════════════════════

    private void observeViewModel() {
        vm.getTotalBudget().observe(this, budget -> {
            if (budget != null) {
                refreshTotalBudgetCard(budget);
                binding.ivEditBudget.setVisibility(View.VISIBLE);
                binding.tvRemainingHint.setVisibility(View.VISIBLE);
                vm.refreshRemainingAllocationFromCurrentTotal();
                vm.loadDailyChartData();
                if (!showingLineChart) vm.loadPieChartData(categoryNameCache);
            } else {
                binding.tvBudgetAmount.setText("¥—");
                binding.tvUsed.setText("已用：—");
                binding.tvOver.setText("未设置总预算");
                binding.ivEditBudget.setVisibility(View.GONE);
                binding.tvRemainingHint.setVisibility(View.GONE);
            }
        });

        vm.getCategoryBudgets().observe(this, budgets -> {
            if (budgets == null) return;
            buildAndSubmitItems(budgets);
            vm.refreshRemainingAllocationFromCurrentTotal();
            if (!showingLineChart) vm.loadPieChartData(categoryNameCache);
        });

        vm.getRemainingAllocation().observe(this, remaining -> {
            if (remaining == null) {
                binding.tvRemainingHint.setVisibility(View.GONE);
                return;
            }
            binding.tvRemainingHint.setVisibility(View.VISIBLE);
            if (remaining >= 0) {
                binding.tvRemainingHint.setText(
                        String.format(Locale.getDefault(), "剩余可分配：¥%.2f", remaining));
                binding.tvRemainingHint.setTextColor(0xFF5B8DEF);
            } else {
                binding.tvRemainingHint.setText(
                        String.format(Locale.getDefault(), "已超分配：¥%.2f", Math.abs(remaining)));
                binding.tvRemainingHint.setTextColor(0xFFFF5252);
            }
        });

        vm.getDailyAccumulated().observe(this, dailyData -> {
            if (dailyData == null) return;
            Budget total = vm.getTotalBudget().getValue();
            if (total == null) return;
            chartManager.updateLineChart(binding.lineChart, dailyData, total.getAmount(),
                    vm.getCurrentYear(), vm.getCurrentMonth());
        });

        vm.getPieSlices().observe(this, slices -> {
            if (slices == null || slices.isEmpty()) return;
            Budget total = vm.getTotalBudget().getValue();
            if (total == null) return;
            chartManager.updatePieChart(binding.pieChart, slices, total.getAmount());
        });

        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  分类预算列表构建
    // ════════════════════════════════════════════════════════

    /**
     * 在分类缓存更新后，使用最新 LiveData 数据重建 item 列表。
     */
    private void rebuildCategoryBudgetItems() {
        List<Budget> budgets = vm.getCategoryBudgets().getValue();
        if (budgets == null || budgets.isEmpty()) return;
        buildAndSubmitItems(budgets);
    }

    /**
     * 构建分类预算 item 列表并提交给 Adapter。
     *
     * 修复：已用金额的查询时间范围改为按每条分类预算自身的 period 动态计算，
     * 而非统一使用总预算的时间范围。
     * 这样才能保证列表中的进度条数值与详情页账单列表的统计口径一致。
     *
     * 在 diskIO 线程中查询，构建完成后切回主线程提交 Adapter。
     * 使用缓存快照避免并发修改问题。
     */
    private void buildAndSubmitItems(List<Budget> budgets) {
        final Map<String, String> nameSnap = new HashMap<>(categoryNameCache);
        final Map<String, String> iconSnap = new HashMap<>(categoryIconCache);

        AppExecutors.get().diskIO().execute(() -> {
            List<CategoryBudgetAdapter.CategoryBudgetItem> items = new ArrayList<>();

            for (Budget b : budgets) {
                // 按该分类预算自身的 period 计算有效时间范围
                long startTime;
                long endTime;
                int period = b.getPeriod();

                if (period == Budget.PERIOD_DAY || period == Budget.PERIOD_WEEK) {
                    // 天/周预算：实时计算当前周期范围，避免使用可能已过期的存储值
                    long[] range = BudgetPeriodHelper.getPeriodRange(period);
                    startTime = range[0];
                    endTime   = range[1];
                } else {
                    // 月/年预算：直接使用存储的稳定时间窗口
                    startTime = b.getStartTime();
                    endTime   = b.getEndTime();
                }

                // 用正确的时间范围查询该分类的实际支出
                double spent = vm.getSpentByCategoryInRange(b.getTargetId(), startTime, endTime);

                String catId = b.getTargetId();
                String name  = nameSnap.containsKey(catId) ? nameSnap.get(catId) : "";
                String icon  = iconSnap.get(catId);

                Category cat = new Category();
                cat.setCloudId(catId);
                cat.setName(name != null ? name : "");
                cat.setIconUri(icon);
                items.add(new CategoryBudgetAdapter.CategoryBudgetItem(b, cat, spent));
            }
            runOnUiThread(() -> adapter.submitList(items));
        });
    }

    // ════════════════════════════════════════════════════════
    //  总预算卡片刷新
    // ════════════════════════════════════════════════════════

    private void refreshTotalBudgetCard(Budget budget) {
        double budgetAmt = budget.getAmount();
        binding.tvBudgetAmount.setText(
                String.format(Locale.getDefault(), "¥%.2f", budgetAmt));

        AppExecutors.get().diskIO().execute(() -> {
            double spent     = vm.getTotalSpentSync();
            double remaining = budgetAmt - spent;
            boolean over     = remaining < 0;
            int progress = budgetAmt > 0 ? (int) Math.min(spent / budgetAmt * 100, 100) : 0;

            runOnUiThread(() -> {
                binding.tvUsed.setText(
                        String.format(Locale.getDefault(), "已用：¥%.2f", spent));
                if (!over) {
                    binding.tvOver.setText(
                            String.format(Locale.getDefault(), "剩余：¥%.2f", remaining));
                    binding.tvOver.setTextColor(0xFF4CAF50);
                } else {
                    binding.tvOver.setText(
                            String.format(Locale.getDefault(), "超支：¥%.2f", Math.abs(remaining)));
                    binding.tvOver.setTextColor(0xFFFF5252);
                }

                if (progress < 100) {
                    binding.progressBudget.setProgressDrawable(
                            getResources().getDrawable(R.drawable.bg_progress_budget_item));
                } else {
                    binding.progressBudget.setProgressDrawable(
                            getResources().getDrawable(R.drawable.progress_budget_red));
                }
                binding.progressBudget.setProgress(progress);

                // 进度指示器位置跟随进度条
                binding.progressBudget.post(() -> {
                    int   barWidth  = binding.progressBudget.getWidth();
                    int   indWidth  = binding.ivProgressIndicator.getWidth();
                    float ratio     = progress / 100f;
                    float rawOffset = ratio * barWidth - indWidth / 2f;
                    float clamped   = Math.max(0, Math.min(rawOffset, barWidth - indWidth));
                    binding.ivProgressIndicator.setTranslationX(clamped);
                });
            });
        });

        boolean isYear = budget.isYearType();
        binding.tvMonth.setText(isYear ? "本年" : "本月");
        int year  = vm.getCurrentYear();
        int month = vm.getCurrentMonth();
        if (isYear) {
            binding.tvDateRange.setText("（" + year + "年）");
        } else {
            int days = new java.util.GregorianCalendar(year, month - 1, 1)
                    .getActualMaximum(Calendar.DAY_OF_MONTH);
            binding.tvDateRange.setText(
                    String.format(Locale.getDefault(), "（%d.1-%d.%d）", month, month, days));
        }
        binding.tvMonthBudget.setText(isYear ? "年预算" : "月预算");
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}