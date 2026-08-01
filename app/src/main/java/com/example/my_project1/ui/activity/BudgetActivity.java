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
        monthAdapter = new MonthAdapter(index -> {
            String type = vm.getBudgetType();
            if (Budget.TYPE_YEAR.equals(type)) {
                int curYear = Calendar.getInstance().get(Calendar.YEAR);
                int selectedYear = (curYear - 2) + (index - 1);
                vm.setYear(selectedYear);
            } else if (Budget.TYPE_WEEK.equals(type)) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.YEAR, vm.getCurrentYear());
                cal.set(Calendar.WEEK_OF_YEAR, vm.getCurrentMonth());
                cal.add(Calendar.WEEK_OF_YEAR, index - 4);
                vm.setYear(cal.get(Calendar.YEAR));
                vm.setMonth(cal.get(Calendar.WEEK_OF_YEAR));
            } else {
                vm.setMonth(index);
            }
        });
        binding.rvMonths.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvMonths.setAdapter(monthAdapter);
        refreshHorizontalList(vm.getBudgetType());
    }

    private void initButtons() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivAddBudget.setOnClickListener(v ->
                AddBudgetFragment.newInstance(null).show(getSupportFragmentManager(), AddBudgetFragment.TAG));

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
            if (current == null) return;

            com.example.my_project1.data.model.account.Account tempAccount =
                    new com.example.my_project1.data.model.account.Account();
            tempAccount.setBalance(current.getAmount());

            com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment fragment =
                    com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment.newInstance(tempAccount);
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
        });

        vm.getSelectedYear().observe(this, year -> {
            updateTopDateText();
        });

        vm.getCurrentBudgetType().observe(this, type -> {
            refreshTopTabStyle(type);
            updateTopDateText();
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
        int year = vm.getCurrentYear();
        int month = vm.getCurrentMonth();
        
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        
        if (Budget.TYPE_YEAR.equals(type)) {
            vm.setYear(year + delta);
        } else if (Budget.TYPE_WEEK.equals(type)) {
            cal.set(Calendar.WEEK_OF_YEAR, month);
            cal.add(Calendar.WEEK_OF_YEAR, delta);
            vm.setYear(cal.get(Calendar.YEAR));
            vm.setMonth(cal.get(Calendar.WEEK_OF_YEAR));
        } else {
            cal.set(Calendar.MONTH, month - 1);
            cal.add(Calendar.MONTH, delta);
            vm.setYear(cal.get(Calendar.YEAR));
            vm.setMonth(cal.get(Calendar.MONTH) + 1);
        }
    }

    private void updateTopDateText() {
        String type = vm.getBudgetType();
        int year = vm.getCurrentYear();
        int month = vm.getCurrentMonth();
        int startDay = BudgetConfig.getStartDay(this);
        
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.SUNDAY);
        cal.set(Calendar.YEAR, year);
        
        int period;
        if (Budget.TYPE_YEAR.equals(type)) {
            period = Budget.PERIOD_YEAR;
            cal.set(Calendar.MONTH, 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else if (Budget.TYPE_WEEK.equals(type)) {
            period = Budget.PERIOD_WEEK;
            cal.set(Calendar.WEEK_OF_YEAR, month);
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        } else {
            period = Budget.PERIOD_MONTH;
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }
        
        // 1. Update date range display in card immediately
        String rangeText = BudgetPeriodHelper.getPeriodDateRange(period, startDay, cal);
        animateTextUpdate(binding.tvDateRange, rangeText);

        // 2. Update top bar date display
        String dateStr;
        if (Budget.TYPE_YEAR.equals(type)) {
            dateStr = year + "年";
        } else if (Budget.TYPE_WEEK.equals(type)) {
            long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, startDay, cal);
            Calendar s = Calendar.getInstance();
            s.setTimeInMillis(range[0]);
            Calendar e = Calendar.getInstance();
            e.setTimeInMillis(range[1]);
            
            if (s.get(Calendar.MONTH) == e.get(Calendar.MONTH)) {
                dateStr = String.format(Locale.getDefault(), "%d年%02d月%02d日 - %02d日 (第%d周)",
                        s.get(Calendar.YEAR), s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.DAY_OF_MONTH), month);
            } else {
                dateStr = String.format(Locale.getDefault(), "%d.%02d.%02d - %d.%02d.%02d (第%d周)",
                        s.get(Calendar.YEAR), s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.YEAR), e.get(Calendar.MONTH) + 1, e.get(Calendar.DAY_OF_MONTH), month);
            }
        } else {
            dateStr = year + "年" + month + "月";
        }
        binding.tvDateDisplay.setText(dateStr);
        
        // 3. Refresh horizontal list below
        refreshHorizontalList(type);
    }

    private void refreshHorizontalList(String type) {
        if (monthAdapter == null) return;
        List<String> items = new ArrayList<>();
        if (Budget.TYPE_YEAR.equals(type)) {
            int curYear = Calendar.getInstance().get(Calendar.YEAR);
            for (int i = curYear - 2; i <= curYear + 2; i++) items.add(i + "年");
            monthAdapter.setItems(items, vm.getCurrentYear() - (curYear - 2));
        } else if (Budget.TYPE_WEEK.equals(type)) {
            // Show 4 weeks before and after, 9 weeks total
            int currentYear = vm.getCurrentYear();
            int currentMonth = vm.getCurrentMonth(); // month stores weekNum here
            for (int i = -4; i <= 4; i++) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.YEAR, currentYear);
                cal.set(Calendar.WEEK_OF_YEAR, currentMonth);
                cal.add(Calendar.WEEK_OF_YEAR, i);
                items.add("第" + cal.get(Calendar.WEEK_OF_YEAR) + "周");
            }
            monthAdapter.setItems(items, 4);
        } else {
            for (int i = 1; i <= 12; i++) items.add(i + "月");
            monthAdapter.setItems(items, vm.getCurrentMonth() - 1);
        }
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
            tab.setTextColor(0xFF333333);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF999999);
        }
    }

    // Detail stats update with over-budget handling
    private void updateDetailStats(BudgetViewModel.BudgetStats stats) {
        animateTextUpdate(binding.tvIncomeActual, String.format(Locale.getDefault(), "%.2f", stats.totalIncome));
        animateTextUpdate(binding.tvExpenseActual, String.format(Locale.getDefault(), "%.2f", stats.totalExpense));
        animateTextUpdate(binding.tvExpenseBudget, String.format(Locale.getDefault(), "预算 %.2f", stats.expenseBudget));

        if (stats.expenseBudget <= 0) {
            animateTextUpdate(binding.tvBudgetAmount, "¥--");
            updateProgressValue(binding.progressBudget, 0);
            animateTextUpdate(binding.tvProgressPercent, "0%");
            animateTextUpdate(binding.tvUsed, String.format(Locale.getDefault(), "已用 ¥%.2f", stats.totalExpense));
            binding.tvBudgetAmount.setTextColor(0xFF333333);
            binding.tvRemainingLabel.setTextColor(0xFF5B8DEF);
            updateProgressDrawable(binding.progressBudget, R.drawable.bg_progress_thin);
            return;
        }

        double remaining = stats.expenseBudget - stats.totalExpense;
        int progress = stats.expenseBudget > 0 ? (int) (stats.totalExpense / stats.expenseBudget * 100) : 0;

        if (remaining < 0 && stats.expenseBudget > 0) {
            binding.tvBudgetAmount.setTextColor(0xFFEB5757); // Red
            animateTextUpdate(binding.tvRemainingLabel, "预算已超支");
            binding.tvRemainingLabel.setTextColor(0xFFEB5757);
            animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.abs(remaining)));
            updateProgressDrawable(binding.progressBudget, R.drawable.progress_budget_red);
        } else {
            binding.tvBudgetAmount.setTextColor(0xFF333333);
            String label = "月预算剩余";
            if (Budget.TYPE_YEAR.equals(vm.getBudgetType())) label = "年预算剩余";
            else if (Budget.TYPE_WEEK.equals(vm.getBudgetType())) label = "周预算剩余";
            animateTextUpdate(binding.tvRemainingLabel, label);
            binding.tvRemainingLabel.setTextColor(0xFF5B8DEF); // Blue
            animateTextUpdate(binding.tvBudgetAmount, String.format(Locale.getDefault(), "¥%.2f", Math.max(0, remaining)));
            updateProgressDrawable(binding.progressBudget, R.drawable.bg_progress_thin);
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
        AppExecutors.get().diskIO().execute(() -> {
            List<CategoryBudgetAdapter.CategoryBudgetItem> items = new ArrayList<>();
            double totalAllocated = 0;
            
            int startDay = BudgetConfig.getStartDay(this);
            
            for (Budget b : budgets) {
                // Base statistics on year/month rather than stored startTime/endTime
                Calendar bCal = Calendar.getInstance();
                bCal.setFirstDayOfWeek(Calendar.SUNDAY);
                bCal.set(Calendar.YEAR, b.getYear());
                
                if (Budget.TYPE_WEEK.equals(b.getBudgetType())) {
                    bCal.set(Calendar.WEEK_OF_YEAR, b.getMonth());
                    bCal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                } else if (b.getMonth() > 0) {
                    bCal.set(Calendar.MONTH, b.getMonth() - 1);
                    bCal.set(Calendar.DAY_OF_MONTH, 1);
                } else {
                    bCal.set(Calendar.MONTH, 0);
                    bCal.set(Calendar.DAY_OF_MONTH, 1);
                }
                
                long[] range = BudgetPeriodHelper.getPeriodRange(b.getPeriod(), startDay, bCal);
                b.setStartTime(range[0]);
                b.setEndTime(range[1]);
                double spent = vm.getSpentByCategoryInRange(b.getTargetId(), range[0], range[1]);

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
                animateTextUpdate(binding.tvClassifiedBudget, String.format(Locale.getDefault(), "已分类预算 ¥%.2f", finalAllocated));
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