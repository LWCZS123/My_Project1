package com.example.my_project1.ui.activity;

import android.os.Bundle;
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
import com.example.my_project1.ui.adapter.budget.MonthAdapter;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.fragment.AddBudgetFragment;
import com.example.my_project1.ui.fragment.AddCategoryBudgetFragment;
import com.example.my_project1.ui.fragment.BudgetDateSelectorFragment;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BudgetConfig;
import com.example.my_project1.utils.BudgetPeriodHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BudgetActivity extends AppCompatActivity {

    private ActivityBudgetBinding  binding;
    private BudgetViewModel        vm;
    private CategoryViewModel      categoryVm;
    private CategoryBudgetAdapter  adapter;
    private MonthAdapter           monthAdapter;
    private boolean                isInitialRender = true;

    private boolean isAmountVisible = true;
    private long lastSyncTimeMs = 0;
    private static final long SYNC_DEBOUNCE_MS = 30_000L;

    private final Map<String, String> categoryNameCache = new HashMap<>();
    private final Map<String, String> categoryIconCache = new HashMap<>();

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

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        vm         = new ViewModelProvider(this).get(BudgetViewModel.class);
        categoryVm = new ViewModelProvider(this).get(CategoryViewModel.class);

        initRecyclerView();
        initMonthList();
        initButtons();
        observeCategoryData();
        observeViewModel();

        triggerCloudSync();
    }

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
                        }).show();
            }

            @Override
            public void onItemClick(CategoryBudgetAdapter.CategoryBudgetItem item) {
                String catCloudId = item.budget.getTargetId();
                CategoryBudgetDetailActivity.start(BudgetActivity.this, item.budget, 
                        categoryNameCache.get(catCloudId), categoryIconCache.get(catCloudId), catCloudId);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }

            @Override
            public void onRequestCategoryInfo(String targetId, int adapterPosition) {
                // ... same as before ...
            }
        });
    }

    private void initMonthList() {
        monthAdapter = new MonthAdapter(index -> {
            clearBudgetDisplay();
            String type = vm.getBudgetType();
            if (Budget.TYPE_YEAR.equals(type)) {
                int curYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                int selectedYear = (curYear - 2) + (index - 1);
                vm.setYear(selectedYear);
            } else if (Budget.TYPE_WEEK.equals(type)) {
                // 周逻辑：index 是 1-5，对应 -2 到 +2
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.WEEK_OF_YEAR, index - 3);
                vm.setYear(cal.get(java.util.Calendar.YEAR));
                vm.setMonth(cal.get(java.util.Calendar.MONTH) + 1);
                // 强制更新日期显示以反映周变化
                updateTopDateText(); 
            } else {
                vm.setMonth(index);
                vm.switchToMonth();
            }
        });
        binding.rvMonths.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvMonths.setAdapter(monthAdapter);
        // 初始化时根据当前类型刷新列表
        refreshHorizontalList(vm.getBudgetType());
    }

    private void clearBudgetDisplay() {
        binding.tvBudgetAmount.setText("¥0.00");
        binding.progressBudget.setProgress(0);
        binding.tvUsed.setText("已用 ¥0.00");
        binding.tvTotalAmountHint.setText("¥0.00");
        binding.tvDateRange.setText("加载中...");
        binding.tvIncomeActual.setText("0.00");
        binding.tvExpenseActual.setText("0.00");
        binding.pbIncomeProgress.setProgress(0);
        binding.pbExpenseProgress.setProgress(0);
    }

    private void initButtons() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivAddBudget.setOnClickListener(v -> AddBudgetFragment.newInstance(null).show(getSupportFragmentManager(), AddBudgetFragment.TAG));
        
        binding.llTitleDate.setOnClickListener(v -> {
            BudgetDateSelectorFragment selector = BudgetDateSelectorFragment.newInstance(
                    vm.getBudgetType(), vm.getCurrentYear(), vm.getCurrentMonth(),
                    (type, year, month) -> {
                        vm.setYear(year);
                        vm.setMonth(month);
                    }
            );
            selector.show(getSupportFragmentManager(), "DateSelector");
        });

        binding.ivEditMainBudget.setOnClickListener(v -> {
            Budget current = vm.getTotalBudget().getValue();
            if (current == null) return;
            
            com.example.my_project1.data.model.account.Account tempAccount = new com.example.my_project1.data.model.account.Account();
            tempAccount.setBalance(current.getAmount());
            
            com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment fragment = 
                    com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment.newInstance(tempAccount);
            fragment.setOnBalanceAdjustedListener((newAmount, recordAsTransaction) -> {
                vm.saveTotalBudget(newAmount, vm.getBudgetType());
                Toast.makeText(this, "预算已更新", Toast.LENGTH_SHORT).show();
            });
            fragment.show(getSupportFragmentManager(), "EditMainBudget");
        });
        
        binding.tvDateSelectorTop.setOnClickListener(v -> vm.switchToYear());

        // Top Period Tabs
        binding.tabWeek.setOnClickListener(v -> {
            if (vm.getBudgetType().equals(Budget.TYPE_WEEK)) return; // 避免重复点击
            isInitialRender = true;
            vm.switchToWeek();
        });
        binding.tabMonth.setOnClickListener(v -> {
            if (vm.getBudgetType().equals(Budget.TYPE_MONTH)) return;
            isInitialRender = true;
            binding.tvBudgetAmount.setText("¥--");
            vm.switchToMonth();
        });
        binding.tabYear.setOnClickListener(v -> {
            if (vm.getBudgetType().equals(Budget.TYPE_YEAR)) return;
            isInitialRender = true;
            binding.tvBudgetAmount.setText("¥--");
            vm.switchToYear();
        });

        binding.btnAddCategoryBudget.setOnClickListener(v -> {
            if (vm.getTotalBudget().getValue() == null) {
                Toast.makeText(this, "请先设置总支出预算", Toast.LENGTH_SHORT).show();
                return;
            }
            AddCategoryBudgetFragment.newInstance(null)
                    .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
        });
    }

    private void refreshAmountDisplay() {
        Budget b = vm.getTotalBudget().getValue();
        if (b == null) return;
        if (isAmountVisible) {
            binding.tvBudgetAmount.setText(String.format(Locale.getDefault(), "¥%.2f", b.getAmount()));
        } else {
            binding.tvBudgetAmount.setText("¥****");
        }
    }

    private void observeCategoryData() {
        categoryVm.getExpenseCategories(vm.getUserId()).observe(this, categories -> {
            if (categories == null) return;
            for (CategoryWithSubCategories cws : categories) {
                if (cws.category != null) {
                    categoryNameCache.put(cws.category.getCloudId(), cws.category.getName());
                    categoryIconCache.put(cws.category.getCloudId(), cws.category.getIconUri());
                }
                if (cws.subCategories != null) {
                    for (SubCategory sub : cws.subCategories) {
                        categoryNameCache.put(sub.getCloudId(), sub.getName());
                        categoryIconCache.put(sub.getCloudId(), sub.getIconUri());
                    }
                }
            }
            rebuildCategoryBudgetItems();
        });
    }

    private void observeViewModel() {
        vm.getSelectedMonth().observe(this, month -> {
            updateTopDateText();
            monthAdapter.setSelectedMonth(month);
            // 只要日期变了就重新加载统计数据，不依赖总预算是否存在
            vm.loadStats(); 
        });
        vm.getSelectedYear().observe(this, year -> {
            updateTopDateText();
            vm.loadStats();
        });

        vm.getCurrentBudgetType().observe(this, type -> {
            refreshTopTabStyle(type);
            updateTopDateText(); // 更新顶部日期显示和水平列表
            vm.loadStats();
        });

        vm.getTotalBudget().observe(this, budget -> {
            if (budget != null) {
                refreshMainBudgetCard(budget);
            } else {
                // 如果当前选中的年/月还没设预算，也要清空显示，防止显示上个月/年的旧数据
                binding.tvBudgetAmount.setText("¥0.00");
                binding.progressBudget.setProgress(0);
                binding.tvUsed.setText("已用 ¥0.00");
                binding.tvTotalAmountHint.setText("¥0.00");
                binding.tvDateRange.setText("未设置预算周期");
            }
        });

        vm.getCategoryBudgets().observe(this, budgets -> {
            if (budgets != null) buildAndSubmitItems(budgets);
            else adapter.submitList(new ArrayList<>());
        });

        vm.getMonthlyStats().observe(this, stats -> {
            if (vm.getBudgetType().equals(Budget.TYPE_MONTH)) {
                updateDetailStats(stats);
                isInitialRender = false; // 数据已就绪
            }
        });

        vm.getYearlyStats().observe(this, stats -> {
            updateYearlyOverview(stats);
            if (vm.getBudgetType().equals(Budget.TYPE_YEAR)) {
                updateDetailStats(stats);
                isInitialRender = false;
            }
        });

        vm.getSelectedMonth().observe(this, month -> monthAdapter.setSelectedMonth(month));
        vm.getSelectedYear().observe(this, year -> binding.tvDateSelectorTop.setText(year + " ▼"));
    }

    public void updateCategoryCache(String cloudId, String name, String iconUri) {
        if (cloudId != null) {
            categoryNameCache.put(cloudId, name);
            categoryIconCache.put(cloudId, iconUri);
        }
    }

    private void updateTopDateText() {
        String type = vm.getBudgetType();
        int year = vm.getCurrentYear();
        int month = vm.getCurrentMonth();
        String dateStr;
        if (Budget.TYPE_YEAR.equals(type)) {
            dateStr = year + "年";
        } else if (Budget.TYPE_WEEK.equals(type)) {
            // 根据当前选中的年、月、周（基于 selectedMonth 的第一天或当前日期）计算
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.YEAR, year);
            cal.set(java.util.Calendar.MONTH, month - 1);
            long[] range = com.example.my_project1.utils.BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, BudgetConfig.getStartDay(this), cal);
            java.util.Calendar s = java.util.Calendar.getInstance(); s.setTimeInMillis(range[0]);
            java.util.Calendar e = java.util.Calendar.getInstance(); e.setTimeInMillis(range[1]);
            dateStr = String.format(Locale.getDefault(), "%d年%d月%d日至%d日",
                    s.get(java.util.Calendar.YEAR), s.get(java.util.Calendar.MONTH) + 1, s.get(java.util.Calendar.DAY_OF_MONTH),
                    e.get(java.util.Calendar.DAY_OF_MONTH));
        } else {
            dateStr = year + "年" + month + "月";
        }
        binding.tvDateDisplay.setText(dateStr);
        
        refreshHorizontalList(type);
    }

    private void refreshHorizontalList(String type) {
        if (monthAdapter == null) return;
        List<String> items = new ArrayList<>();
        if (Budget.TYPE_YEAR.equals(type)) {
            int curYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            for (int i = curYear - 2; i <= curYear + 2; i++) items.add(i + "年");
            monthAdapter.setItems(items, vm.getCurrentYear() - (curYear - 2));
        } else if (Budget.TYPE_WEEK.equals(type)) {
            // 简单展示最近几周
            for (int i = -2; i <= 2; i++) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.WEEK_OF_YEAR, i);
                items.add("第" + cal.get(java.util.Calendar.WEEK_OF_YEAR) + "周");
            }
            monthAdapter.setItems(items, 2); // 选中中间（当前周）
        } else {
            for (int i = 1; i <= 12; i++) items.add(i + "月");
            monthAdapter.setItems(items, vm.getCurrentMonth() - 1);
        }
    }

    private void refreshMainBudgetCard(Budget budget) {
        refreshAmountDisplay();
        int startDay = BudgetConfig.getStartDay(this);
        binding.tvDateRange.setText(BudgetPeriodHelper.getPeriodDateRange(budget.getPeriod(), startDay));
        binding.tvTotalAmountHint.setText(String.format(Locale.getDefault(), "¥%.2f", budget.getAmount()));
        binding.tvRemainingLabel.setText(budget.isYearType() ? "年预算剩余" : "月预算剩余");
    }

    private void updateYearlyOverview(BudgetViewModel.BudgetStats stats) {
        binding.pbYearlyIncome.setProgress((int) (stats.totalIncome / 200000.0 * 100)); // Dummy max
        binding.tvYearlyIncomeText.setText(String.format(Locale.getDefault(), "%.0f / %.0f", stats.totalIncome, 200000.0));
        binding.pbYearlyExpense.setProgress((int) (stats.totalExpense / stats.expenseBudget * 100));
        binding.tvYearlyExpenseText.setText(String.format(Locale.getDefault(), "%.0f / %.0f", stats.totalExpense, stats.expenseBudget));
    }

    private void refreshTopTabStyle(String type) {
        boolean isWeek = Budget.TYPE_WEEK.equals(type);
        boolean isMonth = Budget.TYPE_MONTH.equals(type);
        boolean isYear = Budget.TYPE_YEAR.equals(type);

        applyTopTab(binding.tabWeek, isWeek);
        applyTopTab(binding.tabMonth, isMonth);
        applyTopTab(binding.tabYear, isYear);
    }

    private void applyTopTab(android.widget.TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected_white);
            tab.setTextColor(0xFF333333);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF999999);
        }
    }

    private void updateDetailStats(BudgetViewModel.BudgetStats stats) {
        animateTextUpdate(binding.tvIncomeActual, String.format(Locale.getDefault(), "%.2f", stats.totalIncome));
        animateTextUpdate(binding.tvExpenseActual, String.format(Locale.getDefault(), "%.2f", stats.totalExpense));
        animateTextUpdate(binding.tvExpenseBudget, String.format(Locale.getDefault(), "预算 %.2f", stats.expenseBudget));
        
        double remaining = stats.expenseBudget - stats.totalExpense;
        animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.max(0, remaining)));
        
        int progress = stats.expenseBudget > 0 ? (int) (stats.totalExpense / stats.expenseBudget * 100) : 0;
        binding.progressBudget.setProgress(progress);
        animateTextUpdate(binding.tvProgressPercent, progress + "%");
        animateTextUpdate(binding.tvUsed, String.format(Locale.getDefault(), "已用 ¥%.2f", stats.totalExpense));

        // 动态样式：垂直进度背景条 (绿色收入 / 红色支出)
        binding.pbIncomeProgress.setProgress(10000); 
        binding.pbExpenseProgress.setProgress(Math.min(progress * 100, 10000));
        binding.pbIncomeProgress.setProgressDrawable(getDrawable(R.drawable.bg_vertical_progress_income));

        if (stats.totalExpense > stats.expenseBudget && stats.expenseBudget > 0) {
            binding.pbExpenseProgress.setProgressDrawable(getDrawable(R.drawable.bg_vertical_progress_expense));
            binding.tvExpenseActual.setTextColor(0xFFEB5757);
        } else {
            binding.pbExpenseProgress.setProgressDrawable(getDrawable(R.drawable.bg_vertical_progress_expense));
            binding.tvExpenseActual.setTextColor(0xFF333333);
        }
    }

    private void animateTextUpdate(android.widget.TextView view, String newText) {
        if (view.getText().toString().equals(newText)) return;
        if (isInitialRender) {
            view.setText(newText);
            return;
        }
        view.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            view.setText(newText);
            view.animate().alpha(1f).setDuration(150).start();
        }).start();
    }

    private void rebuildCategoryBudgetItems() {
        List<Budget> budgets = vm.getCategoryBudgets().getValue();
        if (budgets != null) buildAndSubmitItems(budgets);
    }

    private void buildAndSubmitItems(List<Budget> budgets) {
        AppExecutors.get().diskIO().execute(() -> {
            List<CategoryBudgetAdapter.CategoryBudgetItem> items = new ArrayList<>();
            double totalAllocated = 0;
            for (Budget b : budgets) {
                double spent = vm.getSpentByCategoryInRange(b.getTargetId(), b.getStartTime(), b.getEndTime());
                Category cat = new Category();
                cat.setCloudId(b.getTargetId());
                cat.setName(categoryNameCache.get(b.getTargetId()));
                cat.setIconUri(categoryIconCache.get(b.getTargetId()));
                items.add(new CategoryBudgetAdapter.CategoryBudgetItem(b, cat, spent));
                totalAllocated += b.getAmount();
            }
            final double finalAllocated = totalAllocated;
            runOnUiThread(() -> {
                adapter.submitList(items);
                binding.tvClassifiedBudget.setText(String.format(Locale.getDefault(), "已分类预算 ¥%.2f", finalAllocated));
            });
        });
    }

    private void triggerCloudSync() {
        long now = System.currentTimeMillis();
        if (now - lastSyncTimeMs < SYNC_DEBOUNCE_MS) return;
        lastSyncTimeMs = now;
        vm.syncFromCloud(success -> vm.loadStats());
    }

    @Override
    protected void onResume() {
        super.onResume();
        triggerCloudSync();
    }
}
