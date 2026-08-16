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
import com.example.my_project1.ui.fragment.CategoryBudgetMenuFragment;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BudgetConfig;
import com.example.my_project1.utils.BudgetPeriodHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BudgetActivity extends AppCompatActivity {

    private ActivityBudgetBinding binding;
    private BudgetViewModel vm;
    private CategoryViewModel categoryVm;
    private CategoryBudgetAdapter adapter;
    private MonthAdapter monthAdapter;

    private long lastSyncTimeMs = 0;
    private static final long SYNC_DEBOUNCE_MS = 30_000L;

    private final Map<String, String> categoryNameCache = new HashMap<>();
    private final Map<String, String> categoryIconCache = new HashMap<>();
    private final Map<String, Boolean> categoryExcludeCache = new HashMap<>();

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
                
                // Ensure the time range passed to detail page is accurately calculated
                Calendar bCal = Calendar.getInstance();
                bCal.setFirstDayOfWeek(Calendar.SUNDAY);
                bCal.set(Calendar.YEAR, b.getYear());
                if (Budget.TYPE_WEEK.equals(b.getBudgetType())) {
                    bCal.set(Calendar.WEEK_OF_YEAR, b.getMonth());
                    bCal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                } else if (b.getMonth() > 0) {
                    bCal.set(Calendar.MONTH, b.getMonth() - 1);
                    bCal.set(Calendar.DAY_OF_MONTH, 1);
                }
                long[] range = BudgetPeriodHelper.getPeriodRange(b.getPeriod(), BudgetConfig.getStartDay(BudgetActivity.this), bCal);
                b.setStartTime(range[0]);
                b.setEndTime(range[1]);

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
                    vm.getBudgetType(), vm.getCurrentYear(), vm.getCurrentMonth(),
                    (type, year, month) -> {
                        vm.setYear(year);
                        vm.setMonth(month);
                    }
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
                Toast.makeText(this, "请先设置总支出预算", Toast.LENGTH_SHORT).show();
                return;
            }
            AddCategoryBudgetFragment.newInstance(null)
                    .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
        });
    }

    // ✅ 已移除 refreshAmountDisplay()，tvBudgetAmount 不再由此方法写入

    private void observeCategoryData() {
        categoryVm.getExpenseCategories(vm.getUserId()).observe(this, categories -> {
            if (categories == null) return;
            for (CategoryWithSubCategories cws : categories) {
                if (cws.category != null) {
                    categoryNameCache.put(cws.category.getCloudId(), cws.category.getName());
                    categoryIconCache.put(cws.category.getCloudId(), cws.category.getIconUri());
                    categoryExcludeCache.put(cws.category.getCloudId(), cws.category.isExcludeBudget());
                }
                if (cws.subCategories != null) {
                    for (SubCategory sub : cws.subCategories) {
                        categoryNameCache.put(sub.getCloudId(), sub.getName());
                        categoryIconCache.put(sub.getCloudId(), sub.getIconUri());
                        categoryExcludeCache.put(sub.getCloudId(), sub.isExcludeBudget());
                    }
                }
            }
            rebuildCategoryBudgetItems();
        });
    }

    private void observeViewModel() {
        // 使用单独的变量记录当前显示的列表类型，防止异步刷新导致的数据错乱
        vm.getSelectedStartTime().observe(this, startTime -> {
            updateTopDateText();
            String type = vm.getBudgetType();
            if (monthAdapter != null) {
                refreshHorizontalList(type);
            }
        });

        vm.getCurrentBudgetType().observe(this, type -> {
            refreshTopTabStyle(type);
            updateTopDateText();
            // 切换 Tab 时强制刷新列表
            refreshHorizontalList(type);
        });

        vm.getCurrentTransactionType().observe(this, transType -> {
            boolean isExpense = Budget.TYPE_EXPENSE.equals(transType);
            refreshBottomTabStyle(isExpense);
            
            // 更新 UI 标签
            if (isExpense) {
                binding.tvRemainingLabel.setText("月预算剩余"); // 这是一个默认值，updateDetailStats 会进一步细化
            } else {
                binding.tvRemainingLabel.setText("本月收入目标剩余");
            }
            
            // 重新加载统计和预算数据
            vm.loadStats();
        });

        // getTotalBudget only updates date range and hint text, doesn't write to tvBudgetAmount
        vm.getTotalBudget().observe(this, budget -> {
            if (budget != null) {
                refreshMainBudgetCard(budget);
            } else {
                animateTextUpdate(binding.tvTotalAmountHint, "¥0.00");
                animateTextUpdate(binding.tvDateRange, "未设置预算周期");
                
                String label = "月预算剩余";
                if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) label = "年预算剩余";
                else if (Budget.TYPE_WEEK.equals(vm.getBudgetType())) label = "周预算剩余";
                animateTextUpdate(binding.tvRemainingLabel, label);
            }
        });

        vm.getCategoryBudgets().observe(this, budgets -> {
            if (budgets != null) buildAndSubmitItems(budgets);
            else adapter.submitList(new ArrayList<>());
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
        if (cloudId != null) {
            categoryNameCache.put(cloudId, name);
            categoryIconCache.put(cloudId, iconUri);
        }
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
            cal.add(Calendar.WEEK_OF_YEAR, delta);
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
        
        String rangeText = BudgetPeriodHelper.getPeriodDateRange(period, BudgetConfig.getStartDay(this), cal);
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
                cal.add(Calendar.WEEK_OF_YEAR, i);
                long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, 1, cal);
                
                String label = (cal.get(Calendar.MONTH) + 1) + "月" + cal.get(Calendar.DAY_OF_MONTH) + "日";
                MonthAdapter.PeriodItem item = new MonthAdapter.PeriodItem(label, range[0], range[1]);
                item.selected = (i == 0);
                items.add(item);
            }
        } else {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(currentStart);
            int year = cal.get(Calendar.YEAR);
            for (int i = 1; i <= 12; i++) {
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, i - 1);
                long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_MONTH, BudgetConfig.getStartDay(this), cal);
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
                    binding.rvMonths.smoothScrollToPosition(i);
                    break;
                }
            }
        });
    }

    // ✅ 不再调用 refreshAmountDisplay，仅更新日期范围和标签
    private void refreshMainBudgetCard(Budget budget) {
        animateTextUpdate(binding.tvTotalAmountHint, String.format(Locale.getDefault(), "¥%.2f", budget.getAmount()));
        
        String label = "月预算剩余";
        if (Budget.TYPE_YEAR.equals(budget.getBudgetType())) label = "年预算剩余";
        else if (Budget.TYPE_WEEK.equals(budget.getBudgetType())) label = "周预算剩余";
        animateTextUpdate(binding.tvRemainingLabel, label);
    }

    private void updateYearlyOverview(BudgetViewModel.BudgetStats stats) {
        binding.pbYearlyIncome.setProgress((int) (stats.totalIncome / 200000.0 * 100));
        animateTextUpdate(binding.tvYearlyIncomeText, String.format(Locale.getDefault(), "%.0f / %.0f", stats.totalIncome, 200000.0));
        binding.pbYearlyExpense.setProgress((int) (stats.totalExpense / stats.expenseBudget * 100));
        animateTextUpdate(binding.tvYearlyExpenseText, String.format(Locale.getDefault(), "%.0f / %.0f", stats.totalExpense, stats.expenseBudget));
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
        animateTextUpdate(binding.tvIncomeActual, String.format(Locale.getDefault(), "%.2f", stats.totalIncome));
        animateTextUpdate(binding.tvExpenseActual, String.format(Locale.getDefault(), "%.2f", stats.totalExpense));
        animateTextUpdate(binding.tvExpenseBudget, String.format(Locale.getDefault(), "预算 %.2f", stats.expenseBudget));

        if (stats.expenseBudget <= 0) {
            animateTextUpdate(binding.tvBudgetAmount, "¥0.00");
            updateProgressValue(binding.progressBudget, 0);
            animateTextUpdate(binding.tvProgressPercent, "0%");
            animateTextUpdate(binding.tvUsed, String.format(Locale.getDefault(), "已用 ¥%.2f", stats.totalExpense));
            binding.tvBudgetAmount.setTextColor(0xFF333333);
            
            String label = "月预算剩余";
            if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) label = "年预算剩余";
            else if (Budget.TYPE_WEEK.equals(vm.getBudgetType())) label = "周预算剩余";
            animateTextUpdate(binding.tvRemainingLabel, label);
            
            binding.tvRemainingLabel.setTextColor(0xFF5B8DEF);
            updateProgressDrawable(binding.progressBudget, R.drawable.bg_progress_thin);
            
            updateProgressValue(binding.pbIncomeProgress, 0);
            updateProgressValue(binding.pbExpenseProgress, 0);
            return;
        }

        double remaining = stats.expenseBudget - stats.totalExpense;
        int progress = stats.expenseBudget > 0 ? (int) (stats.totalExpense / stats.expenseBudget * 100) : 0;

        if (remaining < 0 && stats.expenseBudget > 0) {
            boolean isIncome = Budget.TYPE_INCOME.equals(vm.getTransactionType());
            binding.tvBudgetAmount.setTextColor(isIncome ? 0xFF333333 : 0xFFEB5757); // 收入超额不是坏事
            animateTextUpdate(binding.tvRemainingLabel, isIncome ? "已超出收入目标" : "预算已超支");
            binding.tvRemainingLabel.setTextColor(isIncome ? 0xFF5B8DEF : 0xFFEB5757);
            animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.abs(remaining)));
            updateProgressDrawable(binding.progressBudget, isIncome ? R.drawable.bg_progress_income : R.drawable.progress_budget_red);
        } else {
            binding.tvBudgetAmount.setTextColor(0xFF333333);
            String label;
            boolean isIncome = Budget.TYPE_INCOME.equals(vm.getTransactionType());
            if (isIncome) {
                label = "收入目标剩余";
            } else {
                label = "月预算剩余";
                if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) label = "年预算剩余";
                else if (Budget.TYPE_WEEK.equals(vm.getBudgetType())) label = "周预算剩余";
            }
            animateTextUpdate(binding.tvRemainingLabel, label);
            binding.tvRemainingLabel.setTextColor(0xFF5B8DEF); // Blue
            animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.max(0, remaining)));
            updateProgressDrawable(binding.progressBudget, isIncome ? R.drawable.bg_progress_income : R.drawable.bg_progress_thin);
        }

        updateProgressValue(binding.progressBudget, Math.min(progress, 100));
        animateTextUpdate(binding.tvProgressPercent, progress + "%");
        animateTextUpdate(binding.tvUsed, String.format(Locale.getDefault(), "已用 ¥%.2f", stats.totalExpense));

        updateProgressValue(binding.pbIncomeProgress, 10000);
        updateProgressValue(binding.pbExpenseProgress, Math.min(progress * 100, 10000));
        updateProgressDrawable(binding.pbIncomeProgress, R.drawable.bg_vertical_progress_income);

        if (stats.totalExpense > stats.expenseBudget && stats.expenseBudget > 0) {
            updateProgressDrawable(binding.pbExpenseProgress, R.drawable.bg_vertical_progress_expense);
            binding.tvExpenseActual.setTextColor(0xFFEB5757);
        } else {
            updateProgressDrawable(binding.pbExpenseProgress, R.drawable.bg_vertical_progress_expense);
            binding.tvExpenseActual.setTextColor(0xFF333333);
        }
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
        AppExecutors.get().diskIO().execute(() -> {
            List<CategoryBudgetAdapter.CategoryBudgetItem> items = new ArrayList<>();
            double totalAllocated = 0;
            
            // 使用 ViewModel 中统一的时间范围，确保统计口径一致
            Long startTime = vm.getSelectedStartTime().getValue();
            Long endTime = vm.getSelectedEndTime().getValue();
            if (startTime == null || endTime == null) return;

            for (Budget b : budgets) {
                Boolean isExcluded = categoryExcludeCache.get(b.getTargetId());
                if (isExcluded != null && isExcluded) continue;

                double spent = vm.getSpentByCategoryInRange(b.getTargetId(), startTime, endTime);

                Category cat = new Category();
                cat.setCloudId(b.getTargetId());
                cat.setName(categoryNameCache.get(b.getTargetId()));
                cat.setIconUri(categoryIconCache.get(b.getTargetId()));
                items.add(new CategoryBudgetAdapter.CategoryBudgetItem(b, cat, spent));
                totalAllocated += b.getAmount();
            }
            final double finalAllocated = totalAllocated;
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    adapter.submitList(items);
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

    @Override
    protected void onResume() {
        super.onResume();
        triggerCloudSync();
    }
}