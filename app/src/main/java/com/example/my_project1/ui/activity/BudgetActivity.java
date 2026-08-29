package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;
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
import com.example.my_project1.ui.fragment.CategoryBudgetMenuFragment;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BudgetConfig;
import com.example.my_project1.utils.BudgetPeriodHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BudgetActivity extends AppCompatActivity {

    private ActivityBudgetBinding binding;
    private BudgetViewModel vm;
    private CategoryViewModel categoryVm;
    private CategoryBudgetAdapter adapter;
    private MonthAdapter monthAdapter;

    private long lastSyncTimeMs = 0;
    private static final long SYNC_DEBOUNCE_MS = 30_000L;

    private final Map<String, String> categoryNameCache = new ConcurrentHashMap<>();
    private final Map<String, String> categoryIconCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> categoryExcludeCache = new ConcurrentHashMap<>();
    private final AtomicInteger categoryLoadGeneration = new AtomicInteger();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBudgetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(binding.clTopBar, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, top, 0, 0);
            return insets;
        });

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        // 设置底部导航栏为白色以匹配顶部栏风格
        getWindow().setNavigationBarColor(android.graphics.Color.WHITE);
        insetsController.setAppearanceLightNavigationBars(true);

        vm = new ViewModelProvider(this).get(BudgetViewModel.class);
        categoryVm = new ViewModelProvider(this).get(CategoryViewModel.class);

        initRecyclerView();
        initMonthList();
        initButtons();
        observeCategoryData();
        observeViewModel();
    }

    private void initRecyclerView() {
        adapter = new CategoryBudgetAdapter();
        binding.rvCategoryBudgets.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategoryBudgets.setAdapter(adapter);

        adapter.setOnItemClickListener(new CategoryBudgetAdapter.OnItemClickListener() {
            @Override
            public void onEdit(CategoryBudgetAdapter.CategoryBudgetItem item) {
                // Ensure category metadata is set for the edit dialog
                if (item.category != null) {
                    item.budget.setCategoryName(item.category.getName());
                    item.budget.setCategoryIconUrl(item.category.getIconUri());
                }
                showCategoryMenu(item.budget);
            }

            @Override
            public void onDelete(int budgetId) {
                // Delete action is handled by the menu triggered in onEdit
            }

            @Override
            public void onItemClick(CategoryBudgetAdapter.CategoryBudgetItem item) {
                String catCloudId = item.budget.getTargetId();
                Budget b = item.budget;
                
                CategoryBudgetDetailActivity.start(BudgetActivity.this, b,
                        categoryNameCache.get(catCloudId), categoryIconCache.get(catCloudId), catCloudId);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }

            @Override
            public void onRequestCategoryInfo(String targetId, int adapterPosition) {
                // Fetch category info on demand and callback updateCategoryInfo
                String name = categoryNameCache.get(targetId);
                String icon = categoryIconCache.get(targetId);
                if (name != null && adapter != null) {
                    adapter.updateCategoryInfo(adapterPosition, name, icon);
                }
            }
        });
    }

    /**
     * 显示分类预算操作菜单
     */
    private void showCategoryMenu(Budget budget) {
        CategoryBudgetMenuFragment menu = CategoryBudgetMenuFragment.newInstance(budget);
        menu.setOnMenuActionListener(new CategoryBudgetMenuFragment.OnMenuActionListener() {
            @Override
            public void onEdit(Budget b) {
                AddCategoryBudgetFragment.newInstance(b, true)
                        .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
            }

            @Override
            public void onDelete(Budget b) {
                new ConfirmDialog(BudgetActivity.this)
                        .setTitle("删除预算")
                        .setMessage("确定要删除该分类预算吗？")
                        .setConfirmListener(() -> {
                            vm.deleteCategoryBudget(b.getId());
                            Toast.makeText(BudgetActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                        }).show();
            }
        });
        menu.show(getSupportFragmentManager(), CategoryBudgetMenuFragment.TAG);
    }

    private void initMonthList() {
        monthAdapter = new MonthAdapter(item -> {
            vm.setPeriod(item.startTime, item.endTime);
        });
        binding.rvMonths.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvMonths.setAdapter(monthAdapter);
        refreshHorizontalList(vm.getBudgetType());
    }

    private void initButtons() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivAddBudget.setOnClickListener(v ->
                AddBudgetFragment.newInstance(null).show(getSupportFragmentManager(), AddBudgetFragment.TAG));

        // 切换收入/支出预算
        binding.tabStatsBottom.setText("支出");
        binding.tabCompositionBottom.setText("收入");
        
        binding.tabStatsBottom.setOnClickListener(v -> {
            vm.setTransactionType(Budget.TYPE_EXPENSE);
            refreshBottomTabStyle(true);
        });
        
        binding.tabCompositionBottom.setOnClickListener(v -> {
            vm.setTransactionType(Budget.TYPE_INCOME);
            refreshBottomTabStyle(false);
        });

        binding.tvDateDisplay.setOnClickListener(v -> {
            BudgetDateSelectorFragment selector = BudgetDateSelectorFragment.newInstance(
                    vm.getBudgetType(),
                    vm.getSelectedStartTime().getValue() != null
                            ? vm.getSelectedStartTime().getValue() : System.currentTimeMillis(),
                    vm.getSelectedEndTime().getValue() != null
                            ? vm.getSelectedEndTime().getValue() : System.currentTimeMillis(),
                    vm::selectPeriod
            );
            selector.show(getSupportFragmentManager(), "DateSelector");
        });

        binding.ivPrevPeriod.setOnClickListener(v -> movePeriod(-1));
        binding.ivNextPeriod.setOnClickListener(v -> movePeriod(1));

        binding.ivEditMainBudget.setOnClickListener(v -> {
            Budget current = vm.getTotalBudget().getValue();
            double initialAmount = (current != null) ? current.getAmount() : 0.0;

            com.example.my_project1.data.model.account.Account tempAccount =
                    new com.example.my_project1.data.model.account.Account();
            tempAccount.setBalance(initialAmount);

            com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment fragment =
                    com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment.newInstance(tempAccount, "设置总预算");
            fragment.setOnBalanceAdjustedListener((newAmount, recordAsTransaction) -> {
                vm.saveTotalBudget(newAmount, vm.getBudgetType());
                Toast.makeText(this, "预算已更新", Toast.LENGTH_SHORT).show();
            });
            fragment.show(getSupportFragmentManager(), "EditMainBudget");
        });

        // ✅ Top Period Tabs：点击时切换类型，不再强制清空以防闪烁
        binding.tabWeek.setOnClickListener(v -> {
            if (Budget.TYPE_WEEK.equals(vm.getBudgetType())) return;
            vm.switchToWeek();
        });
        binding.tabMonth.setOnClickListener(v -> {
            if (Budget.TYPE_MONTH.equals(vm.getBudgetType())) return;
            vm.switchToMonth();
        });
        binding.tabYear.setOnClickListener(v -> {
            if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) return;
            vm.switchToYear();
        });

        binding.btnAddCategoryBudget.setOnClickListener(v -> {
            if (vm.getTotalBudget().getValue() == null) {
                Toast.makeText(this,
                        Budget.TYPE_INCOME.equals(vm.getTransactionType())
                                ? "请先设置总收入目标" : "请先设置总支出预算",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            AddCategoryBudgetFragment.newInstance(null)
                    .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
        });
    }

    // ✅ 已移除 refreshAmountDisplay()，tvBudgetAmount 不再由此方法写入

    private void observeCategoryData() {
        categoryVm.getExpenseCategories(vm.getUserId()).observe(this, this::cacheCategories);
        categoryVm.getIncomeCategories(vm.getUserId()).observe(this, this::cacheCategories);
    }

    private void cacheCategories(List<CategoryWithSubCategories> categories) {
        if (categories == null) return;
        for (CategoryWithSubCategories cws : categories) {
                if (cws.category != null) {
                    cacheCategory(cws.category.getCloudId(), cws.category.getName(),
                            cws.category.getIconUri(), cws.category.isExcludeBudget());
                }
                if (cws.subCategories != null) {
                    for (SubCategory sub : cws.subCategories) {
                        cacheCategory(sub.getCloudId(), sub.getName(),
                                sub.getIconUri(), sub.isExcludeBudget());
                    }
                }
        }
        rebuildCategoryBudgetItems();
    }

    private void observeViewModel() {
        vm.getPeriodSelection().observe(this, period -> {
            if (period == null) return;
            refreshTopTabStyle(period.type);
            updateTopDateText();
            if (monthAdapter != null) {
                refreshHorizontalList(period.type);
            }
        });

        vm.getCurrentTransactionType().observe(this, transType -> {
            categoryLoadGeneration.incrementAndGet();
            boolean isExpense = Budget.TYPE_EXPENSE.equals(transType);
            refreshBottomTabStyle(isExpense);
            
            // 更新 UI 标签
            animateTextUpdate(binding.tvRemainingLabel, remainingLabel(!isExpense));
            
        });

        // getTotalBudget only updates date range and hint text, doesn't write to tvBudgetAmount
        vm.getTotalBudget().observe(this, budget -> {
            if (budget != null) {
                refreshMainBudgetCard(budget);
            } else {
                animateTextUpdate(binding.tvTotalAmountHint, "¥0.00");
                
                animateTextUpdate(binding.tvRemainingLabel,
                        remainingLabel(Budget.TYPE_INCOME.equals(vm.getTransactionType())));
            }
        });

        vm.getCategoryBudgets().observe(this, budgets -> {
            if (budgets != null) buildAndSubmitItems(budgets);
        });

        // Stats callback is the unique data source for tvBudgetAmount
        vm.getMonthlyStats().observe(this, stats -> {
            String type = vm.getBudgetType();
            if (Budget.TYPE_MONTH.equals(type) || Budget.TYPE_WEEK.equals(type)) {
                updateDetailStats(stats);
            }
        });

        // 年度统计始终观察并更新概览卡片，不论当前处于什么周期
        vm.getYearlyStats().observe(this, stats -> {
            updateYearlyOverview(stats);
            if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) {
                updateDetailStats(stats);
            }
        });

        // ✅ 已删除重复的 getSelectedMonth / getSelectedYear observe
    }

    public void updateCategoryCache(String cloudId, String name, String iconUri) {
        Boolean excluded = cloudId != null ? categoryExcludeCache.get(cloudId) : null;
        cacheCategory(cloudId, name, iconUri, excluded != null && excluded);
    }

    private void cacheCategory(String cloudId, String name, String iconUri, boolean excluded) {
        if (cloudId == null) return;
        if (name != null) categoryNameCache.put(cloudId, name);
        if (iconUri != null) categoryIconCache.put(cloudId, iconUri);
        categoryExcludeCache.put(cloudId, excluded);
    }

    private void movePeriod(int delta) {
        String type = vm.getBudgetType();
        Long start = vm.getSelectedStartTime().getValue();
        if (start == null) return;
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        
        if (Budget.TYPE_YEAR.equals(type)) {
            cal.add(Calendar.YEAR, delta);
        } else if (Budget.TYPE_WEEK.equals(type)) {
            cal.add(Calendar.DAY_OF_MONTH, delta * 7);
        } else {
            cal.add(Calendar.MONTH, delta);
        }
        
        int period = Budget.PERIOD_MONTH;
        if (Budget.TYPE_YEAR.equals(type)) period = Budget.PERIOD_YEAR;
        else if (Budget.TYPE_WEEK.equals(type)) period = Budget.PERIOD_WEEK;
        
        long[] range = BudgetPeriodHelper.getPeriodRange(period, BudgetConfig.getStartDay(this), cal);
        vm.setPeriod(range[0], range[1]);
    }

    private void updateTopDateText() {
        String type = vm.getBudgetType();
        Long startTime = vm.getSelectedStartTime().getValue();
        Long endTime = vm.getSelectedEndTime().getValue();
        
        if (startTime == null || endTime == null) return;
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startTime);
        
        // 1. Update date range display in card
        int period = Budget.PERIOD_MONTH;
        if (Budget.TYPE_YEAR.equals(type)) period = Budget.PERIOD_YEAR;
        else if (Budget.TYPE_WEEK.equals(type)) period = Budget.PERIOD_WEEK;
        
        String rangeText;
        if (Budget.TYPE_WEEK.equals(type)) {
            rangeText = BudgetPeriodHelper.formatWeekRange(startTime, endTime);
        } else {
            rangeText = BudgetPeriodHelper.getPeriodDateRange(
                    period, BudgetConfig.getStartDay(this), cal);
        }
        animateTextUpdate(binding.tvDateRange, rangeText);

        // 2. Update top bar date display
        String dateStr;
        if (Budget.TYPE_YEAR.equals(type)) {
            dateStr = cal.get(Calendar.YEAR) + "年";
        } else if (Budget.TYPE_WEEK.equals(type)) {
            dateStr = BudgetPeriodHelper.formatWeekRange(startTime, endTime);
        } else {
            dateStr = cal.get(Calendar.YEAR) + "年" + (cal.get(Calendar.MONTH) + 1) + "月";
        }
        binding.tvDateDisplay.setText(dateStr);
    }

    private void refreshHorizontalList(String type) {
        if (monthAdapter == null || isFinishing()) return;
        List<MonthAdapter.PeriodItem> items = new ArrayList<>();
        Long currentStart = vm.getSelectedStartTime().getValue();
        if (currentStart == null) return;

        // 核心逻辑：确保生成的 Item 类型与当前 UI 模式一致
        if (Budget.TYPE_YEAR.equals(type)) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(currentStart);
            int curYear = cal.get(Calendar.YEAR);
            for (int i = curYear - 2; i <= curYear + 2; i++) {
                cal.set(Calendar.YEAR, i);
                long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_YEAR, 1, cal);
                MonthAdapter.PeriodItem item = new MonthAdapter.PeriodItem(i + "年", range[0], range[1]);
                item.selected = (range[0] == currentStart);
                items.add(item);
            }
        } else if (Budget.TYPE_WEEK.equals(type)) {
            Calendar baseCal = Calendar.getInstance();
            baseCal.setTimeInMillis(currentStart);
            for (int i = -4; i <= 4; i++) {
                Calendar cal = (Calendar) baseCal.clone();
                cal.add(Calendar.DAY_OF_MONTH, i * 7);
                long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, 1, cal);
                
                String label = (cal.get(Calendar.MONTH) + 1) + "月" + cal.get(Calendar.DAY_OF_MONTH) + "日";
                MonthAdapter.PeriodItem item = new MonthAdapter.PeriodItem(label, range[0], range[1]);
                item.selected = (i == 0);
                items.add(item);
            }
        } else {
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(currentStart);
            int startDay = BudgetConfig.getStartDay(this);
            int year = selected.get(Calendar.YEAR);
            for (int i = 1; i <= 12; i++) {
                Calendar cal = Calendar.getInstance();
                cal.clear();
                cal.set(year, i - 1, 1);
                cal.set(Calendar.DAY_OF_MONTH,
                        Math.min(startDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
                long[] range = BudgetPeriodHelper.getPeriodRange(
                        Budget.PERIOD_MONTH, startDay, cal);
                MonthAdapter.PeriodItem item = new MonthAdapter.PeriodItem(i + "月", range[0], range[1]);
                item.selected = (range[0] == currentStart);
                items.add(item);
            }
        }
        
        monthAdapter.setItems(items);
        
        // 优化滚动逻辑：仅在必要时滚动，防止 UI 抖动
        binding.rvMonths.post(() -> {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).selected) {
                    LinearLayoutManager manager =
                            (LinearLayoutManager) binding.rvMonths.getLayoutManager();
                    if (manager != null && (i < manager.findFirstVisibleItemPosition()
                            || i > manager.findLastVisibleItemPosition())) {
                        manager.scrollToPositionWithOffset(i, 0);
                    }
                    break;
                }
            }
        });
    }

    // ✅ 不再调用 refreshAmountDisplay，仅更新日期范围和标签
    private void refreshMainBudgetCard(Budget budget) {
        animateTextUpdate(binding.tvTotalAmountHint, String.format(Locale.getDefault(), "¥%.2f", budget.getAmount()));
        animateTextUpdate(binding.tvRemainingLabel,
                remainingLabel(Budget.TYPE_INCOME.equals(budget.getTransactionType())));
    }

    private void updateYearlyOverview(BudgetViewModel.BudgetStats stats) {
        if (stats == null || !vm.getTransactionType().equals(stats.transactionType)) return;
        boolean isIncome = Budget.TYPE_INCOME.equals(stats.transactionType);
        double incomeTarget = isIncome ? stats.incomeBudget : 0.0;
        double expenseTarget = isIncome ? 0.0 : stats.expenseBudget;
        int incomeProgress = incomeTarget > 0
                ? (int) Math.min(100, stats.totalIncome / incomeTarget * 100) : 0;
        int expenseProgress = expenseTarget > 0
                ? (int) Math.min(100, stats.totalExpense / expenseTarget * 100) : 0;
        updateProgressValue(binding.pbYearlyIncome, incomeProgress);
        updateProgressValue(binding.pbYearlyExpense, expenseProgress);
        animateTextUpdate(binding.tvYearlyIncomeText, isIncome
                ? String.format(Locale.getDefault(), "%.0f / %.0f", stats.totalIncome, incomeTarget)
                : String.format(Locale.getDefault(), "%.0f / --", stats.totalIncome));
        animateTextUpdate(binding.tvYearlyExpenseText, isIncome
                ? String.format(Locale.getDefault(), "%.0f / --", stats.totalExpense)
                : String.format(Locale.getDefault(), "%.0f / %.0f", stats.totalExpense, expenseTarget));
    }

    private void refreshTopTabStyle(String type) {
        applyTopTab(binding.tabWeek, Budget.TYPE_WEEK.equals(type));
        applyTopTab(binding.tabMonth, Budget.TYPE_MONTH.equals(type));
        applyTopTab(binding.tabYear, Budget.TYPE_YEAR.equals(type));
    }

    private void applyTopTab(android.widget.TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected_white);
            tab.setTextColor(0xFF222222);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF999999);
        }
    }

    private void refreshBottomTabStyle(boolean isExpense) {
        if (isExpense) {
            binding.tabStatsBottom.setBackgroundResource(R.drawable.bg_tab_selected_white);
            binding.tabStatsBottom.setTextColor(0xFF222222);
            binding.tabCompositionBottom.setBackground(null);
            binding.tabCompositionBottom.setTextColor(0xFF999999);
        } else {
            binding.tabCompositionBottom.setBackgroundResource(R.drawable.bg_tab_selected_white);
            binding.tabCompositionBottom.setTextColor(0xFF222222);
            binding.tabStatsBottom.setBackground(null);
            binding.tabStatsBottom.setTextColor(0xFF999999);
        }
    }

    // Detail stats update with over-budget handling
    private void updateDetailStats(BudgetViewModel.BudgetStats stats) {
        if (stats == null || !vm.getTransactionType().equals(stats.transactionType)) return;
        boolean isIncome = Budget.TYPE_INCOME.equals(stats.transactionType);
        double activeBudget = isIncome ? stats.incomeBudget : stats.expenseBudget;
        double activeAmount = isIncome ? stats.totalIncome : stats.totalExpense;

        animateTextUpdate(binding.tvIncomeActual, String.format(Locale.getDefault(), "%.2f", stats.totalIncome));
        animateTextUpdate(binding.tvExpenseActual, String.format(Locale.getDefault(), "%.2f", stats.totalExpense));
        binding.tvIncomeActual.setTextColor(0xFF333333);
        binding.tvExpenseActual.setTextColor(0xFF333333);
        animateTextUpdate(binding.tvIncomeBudget, isIncome
                ? String.format(Locale.getDefault(), "目标 %.2f", activeBudget) : "目标 --");
        animateTextUpdate(binding.tvExpenseBudget, isIncome
                ? "预算 --" : String.format(Locale.getDefault(), "预算 %.2f", activeBudget));

        if (activeBudget <= 0) {
            animateTextUpdate(binding.tvBudgetAmount, "¥0.00");
            updateProgressValue(binding.progressBudget, 0);
            animateTextUpdate(binding.tvProgressPercent, "0%");
            animateTextUpdate(binding.tvUsed, String.format(Locale.getDefault(),
                    isIncome ? "已入账 ¥%.2f" : "已用 ¥%.2f", activeAmount));
            binding.tvBudgetAmount.setTextColor(0xFF333333);
            
            String label = remainingLabel(isIncome);
            animateTextUpdate(binding.tvRemainingLabel, label);
            
            binding.tvRemainingLabel.setTextColor(0xFF5B8DEF);
            updateProgressDrawable(binding.progressBudget, R.drawable.bg_progress_thin);
            
            updateProgressValue(binding.pbIncomeProgress, 0);
            updateProgressValue(binding.pbExpenseProgress, 0);
            return;
        }

        double remaining = activeBudget - activeAmount;
        int progress = (int) (activeAmount / activeBudget * 100);

        if (remaining < 0) {
            binding.tvBudgetAmount.setTextColor(isIncome ? 0xFF333333 : 0xFFEB5757); // 收入超额不是坏事
            animateTextUpdate(binding.tvRemainingLabel, isIncome ? "已超出收入目标" : "预算已超支");
            binding.tvRemainingLabel.setTextColor(isIncome ? 0xFF5B8DEF : 0xFFEB5757);
            animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.abs(remaining)));
            updateProgressDrawable(binding.progressBudget, isIncome ? R.drawable.bg_progress_income : R.drawable.progress_budget_red);
        } else {
            binding.tvBudgetAmount.setTextColor(0xFF333333);
            String label = remainingLabel(isIncome);
            animateTextUpdate(binding.tvRemainingLabel, label);
            binding.tvRemainingLabel.setTextColor(0xFF5B8DEF); // Blue
            animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.max(0, remaining)));
            updateProgressDrawable(binding.progressBudget, isIncome ? R.drawable.bg_progress_income : R.drawable.bg_progress_thin);
        }

        updateProgressValue(binding.progressBudget, Math.min(progress, 100));
        animateTextUpdate(binding.tvProgressPercent, progress + "%");
        animateTextUpdate(binding.tvUsed, String.format(Locale.getDefault(),
                isIncome ? "已入账 ¥%.2f" : "已用 ¥%.2f", activeAmount));

        updateProgressValue(binding.pbIncomeProgress,
                isIncome ? Math.min(progress * 100, 10000) : 0);
        updateProgressValue(binding.pbExpenseProgress,
                isIncome ? 0 : Math.min(progress * 100, 10000));
        updateProgressDrawable(binding.pbIncomeProgress, R.drawable.bg_vertical_progress_income);

        if (!isIncome && stats.totalExpense > activeBudget) {
            updateProgressDrawable(binding.pbExpenseProgress, R.drawable.bg_vertical_progress_expense);
            binding.tvExpenseActual.setTextColor(0xFFEB5757);
        } else {
            updateProgressDrawable(binding.pbExpenseProgress, R.drawable.bg_vertical_progress_expense);
            binding.tvExpenseActual.setTextColor(0xFF333333);
        }
    }

    private String remainingLabel(boolean isIncome) {
        if (isIncome) return "收入目标剩余";
        if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) return "年预算剩余";
        if (Budget.TYPE_WEEK.equals(vm.getBudgetType())) return "周预算剩余";
        return "月预算剩余";
    }

    private void updateProgressValue(android.widget.ProgressBar bar, int value) {
        if (bar.getProgress() == value) return;
        bar.setProgress(value);
    }

    private void updateProgressDrawable(android.widget.ProgressBar bar, int resId) {
        Object tag = bar.getTag(R.id.bar_drawable_tag);
        if (tag instanceof Integer && (Integer) tag == resId) return;
        bar.setProgressDrawable(getDrawable(resId));
        bar.setTag(R.id.bar_drawable_tag, resId);
    }

    // Unified data update, removing animations to fix flickering
    private void animateTextUpdate(android.widget.TextView view, String newText) {
        if (view.getText().toString().equals(newText)) return;
        view.setText(newText);
    }

    private void rebuildCategoryBudgetItems() {
        List<Budget> budgets = vm.getCategoryBudgets().getValue();
        if (budgets != null) buildAndSubmitItems(budgets);
    }

    private void buildAndSubmitItems(List<Budget> budgets) {
        if (adapter == null) return;
        final int requestId = categoryLoadGeneration.incrementAndGet();
        BudgetViewModel.PeriodSelection selection = vm.getPeriodSelection().getValue();
        if (selection == null) return;
        String transactionType = vm.getTransactionType();
        AppExecutors.get().diskIO().execute(() -> {
            List<CategoryBudgetAdapter.CategoryBudgetItem> items = new ArrayList<>();
            double totalAllocated = 0;
            
            // 使用 ViewModel 中统一的时间范围，确保统计口径一致
            long startTime = selection.startTime;
            long endTime = selection.endTime;
            Map<String, Double> spending = vm.getBudgetAmountsByCategory(
                    transactionType, startTime, endTime);

            for (Budget b : budgets) {
                Boolean isExcluded = categoryExcludeCache.get(b.getTargetId());
                if (isExcluded != null && isExcluded) continue;

                double spent = spending.containsKey(b.getTargetId())
                        ? spending.get(b.getTargetId()) : 0.0;

                Category cat = new Category();
                cat.setCloudId(b.getTargetId());
                String categoryName = categoryNameCache.get(b.getTargetId());
                String categoryIcon = categoryIconCache.get(b.getTargetId());
                cat.setName(categoryName != null && !categoryName.isEmpty()
                        ? categoryName : b.getCategoryName());
                cat.setIconUri(categoryIcon != null
                        ? categoryIcon : b.getCategoryIconUrl());
                items.add(new CategoryBudgetAdapter.CategoryBudgetItem(b, cat, spent));
                totalAllocated += b.getAmount();
            }
            final double finalAllocated = totalAllocated;
            runOnUiThread(() -> {
                BudgetViewModel.PeriodSelection current = vm.getPeriodSelection().getValue();
                if (!isFinishing() && requestId == categoryLoadGeneration.get()
                        && current != null && current.startTime == selection.startTime
                        && current.type.equals(selection.type)
                        && transactionType.equals(vm.getTransactionType())) {
                    adapter.submitList(items, this::hideLoading);
                    animateTextUpdate(binding.tvClassifiedBudget, String.format(Locale.getDefault(), "已分类预算 ¥%.2f", finalAllocated));
                }
            });
        });
    }

    private void triggerCloudSync() {
        long now = System.currentTimeMillis();
        if (now - lastSyncTimeMs < SYNC_DEBOUNCE_MS) return;
        lastSyncTimeMs = now;
        vm.syncFromCloud(success -> vm.loadStats());
    }

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
    protected void onResume() {
        super.onResume();
        triggerCloudSync();
    }
}
