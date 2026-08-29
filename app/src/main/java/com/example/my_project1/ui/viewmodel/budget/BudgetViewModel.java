package com.example.my_project1.ui.viewmodel.budget;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import androidx.lifecycle.MediatorLiveData;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.data.repository.budget.BudgetRepository;
import com.example.my_project1.utils.BudgetConfig;
import com.example.my_project1.utils.BudgetPeriodHelper;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * BudgetViewModel（重构版）
 *
 * 核心改动：
 *
 * 1. addCategoryBudget() — 不再区分"新增"和"更新"两条路径，统一调用
 *    repo.addOrUpdateCategoryBudget()。Repository 层负责唯一性判断，
 *    ViewModel 层只需构建正确的 Budget 对象传入即可。
 *    这修复了：
 *      a. 同一分类多次点击"添加"导致金额叠加的 Bug。
 *      b. 同一分类允许选择不同 period 并存的问题（现在会覆盖旧记录）。
 *
 * 2. updateCategoryBudget() — 直接调用 repo.update()，逻辑不变，
 *    但现在也通过 addOrUpdateCategoryBudget() 路由，保证一致性。
 *
 * 3. 周期约束校验保留：分类预算周期不能超过总预算周期。
 */
public class BudgetViewModel extends AndroidViewModel {

    private static final String TAG = "BudgetViewModel";

    private final BudgetRepository repo;
    private final String           userId;

    private final MutableLiveData<String> currentBudgetType =
            new MutableLiveData<>(Budget.TYPE_MONTH);

    private final MutableLiveData<String> currentTransType =
            new MutableLiveData<>(Budget.TYPE_EXPENSE);

    private final MutableLiveData<Integer> selectedYear  = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedMonth = new MutableLiveData<>();

    // 新增：精确的周期起始时间，解决周预算跨年/跨月不一致问题
    private final MutableLiveData<Long> selectedStartTime = new MutableLiveData<>();
    private final MutableLiveData<Long> selectedEndTime   = new MutableLiveData<>();

    /** Atomic period state used by all database queries and asynchronous loaders. */
    private final MutableLiveData<PeriodSelection> currentPeriod = new MutableLiveData<>();
    private final AtomicInteger statsGeneration = new AtomicInteger();

    private final LiveData<Budget>       totalBudgetLive;
    private final LiveData<List<Budget>> categoryBudgetsLive;

    private final MutableLiveData<Double>         remainingAllocationLive = new MutableLiveData<>();
    private final MutableLiveData<double[]>       dailyAccumulatedLive    = new MutableLiveData<>();
    private final MutableLiveData<List<PieSlice>> pieSlicesLive           = new MutableLiveData<>();
    private final MutableLiveData<String>         errorLive               = new MutableLiveData<>();

    public static class BudgetStats {
        public String transactionType;
        public double totalIncome;
        public double totalExpense;
        public double incomeBudget;
        public double expenseBudget;
        public double dailyAvgIncome;
        public double dailyAvgExpense;
        public double remainingDay;
    }

    private final MutableLiveData<BudgetStats> monthlyStatsLive = new MutableLiveData<>();
    private final MutableLiveData<BudgetStats> yearlyStatsLive  = new MutableLiveData<>();

    public LiveData<BudgetStats> getMonthlyStats() { return monthlyStatsLive; }
    public LiveData<BudgetStats> getYearlyStats()  { return yearlyStatsLive; }

    public void loadStats() {
        PeriodSelection selection = currentPeriod.getValue();
        String transactionType = getTransactionType();
        if (selection == null) return;
        loadStats(selection, transactionType);
    }

    private void loadStats(PeriodSelection selection, String transactionType) {
        final int requestId = statsGeneration.incrementAndGet();
        AppExecutors.get().diskIO().execute(() -> {
            String type = selection.type;
            String tt = transactionType;
            long startTime = selection.startTime;
            long endTime = selection.endTime;

            // 1. 加载当前周期的统计数据 (周/月/年)
            boolean isYear = Budget.TYPE_YEAR.equals(type);
            boolean isWeek = Budget.TYPE_WEEK.equals(type);

            double inc = repo.getTotalIncomeInPeriod(userId, startTime, endTime);
            double exp = repo.getTotalSpentInPeriod(userId, startTime, endTime);

            Budget bud;
            if (isYear) bud = repo.getYearBudgetSync(userId, tt, selection.year);
            else if (isWeek) bud = repo.getWeekBudgetSyncByStart(userId, tt, startTime);
            else bud = repo.getMonthBudgetSync(userId, tt, selection.year, selection.month);

            BudgetStats currentStats = new BudgetStats();
            currentStats.transactionType = tt;
            currentStats.totalIncome = inc;
            currentStats.totalExpense = exp;
            double budgetAmount = (bud != null) ? bud.getAmount() : 0.0;
            currentStats.incomeBudget = Budget.TYPE_INCOME.equals(tt) ? budgetAmount : 0.0;
            currentStats.expenseBudget = Budget.TYPE_EXPENSE.equals(tt) ? budgetAmount : 0.0;

            int days = Math.max(1, BudgetPeriodHelper.getCalendarDayCount(startTime, endTime));

            currentStats.dailyAvgIncome = inc / days;
            currentStats.dailyAvgExpense = exp / days;

            // 2. 如果当前不是“年”模式，额外加载一整年的统计数据用于“年度概览”卡片
            BudgetStats yearlyStats = null;
            if (!isYear) {
                int year = selection.year;
                Calendar yCal = Calendar.getInstance();
                yCal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
                yCal.set(Calendar.MILLISECOND, 0);
                long yStart = yCal.getTimeInMillis();
                yCal.add(Calendar.YEAR, 1);
                long yEnd = yCal.getTimeInMillis() - 1L;

                double yInc = repo.getTotalIncomeInPeriod(userId, yStart, yEnd);
                double yExp = repo.getTotalSpentInPeriod(userId, yStart, yEnd);
                Budget yBud = repo.getYearBudgetSync(userId, tt, year);

                yearlyStats = new BudgetStats();
                yearlyStats.transactionType = tt;
                yearlyStats.totalIncome = yInc;
                yearlyStats.totalExpense = yExp;
                double yearlyBudgetAmount = (yBud != null) ? yBud.getAmount() : 0.0;
                yearlyStats.incomeBudget = Budget.TYPE_INCOME.equals(tt)
                        ? yearlyBudgetAmount : 0.0;
                yearlyStats.expenseBudget = Budget.TYPE_EXPENSE.equals(tt)
                        ? yearlyBudgetAmount : 0.0;
                
                int yDays = Math.max(1, BudgetPeriodHelper.getCalendarDayCount(yStart, yEnd));
                yearlyStats.dailyAvgIncome = yInc / yDays;
                yearlyStats.dailyAvgExpense = yExp / yDays;
            }

            final BudgetStats finalYearly = yearlyStats;
            AppExecutors.get().mainThread().execute(() -> {
                if (requestId != statsGeneration.get()) return;
                if (isYear) {
                    yearlyStatsLive.setValue(currentStats);
                } else {
                    monthlyStatsLive.setValue(currentStats);
                    if (finalYearly != null) {
                        yearlyStatsLive.setValue(finalYearly);
                    }
                }
            });
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  饼图数据模型
    // ────────────────────────────────────────────────────────────────────

    public static class PieSlice {
        public final String label;
        public final float  value;
        public final int    color;
        public PieSlice(String label, float value, int color) {
            this.label = label; this.value = value; this.color = color;
        }
    }

    public static final class PeriodSelection {
        public final String type;
        public final int year;
        public final int month;
        public final long startTime;
        public final long endTime;

        private PeriodSelection(String type, long startTime, long endTime) {
            this.type = type;
            this.startTime = startTime;
            this.endTime = endTime;
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(startTime);
            this.year = calendar.get(Calendar.YEAR);
            this.month = Budget.TYPE_YEAR.equals(type)
                    ? 0 : calendar.get(Calendar.MONTH) + 1;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  构造
    // ────────────────────────────────────────────────────────────────────

    public BudgetViewModel(@NonNull Application app) {
        super(app);
        repo = new BudgetRepository(app);

        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        userId = (user != null) ? user.getObjectId() : "";

        // 初始化为当前月
        Calendar cal = Calendar.getInstance();
        initRangeForType(Budget.TYPE_MONTH, cal);

        // Queries subscribe to one atomic period value, so they never see mixed old/new state.
        MediatorLiveData<CombinedParams> triggers = new MediatorLiveData<>();
        triggers.addSource(currentPeriod, p -> updateTriggers(triggers));
        triggers.addSource(currentTransType, tt -> updateTriggers(triggers));

        totalBudgetLive = Transformations.switchMap(triggers, p -> {
            if (p == null) return new MutableLiveData<>(null);
            if (Budget.TYPE_YEAR.equals(p.type)) {
                return repo.getYearBudgetLive(userId, p.transType, p.year);
            } else if (Budget.TYPE_WEEK.equals(p.type)) {
                return repo.getWeekBudgetLiveByStart(userId, p.transType, p.startTime);
            } else {
                return repo.getMonthBudgetLive(userId, p.transType, p.year, p.month);
            }
        });

        categoryBudgetsLive = Transformations.switchMap(triggers, p -> {
            if (p == null) return new MutableLiveData<>(null);
            if (Budget.TYPE_WEEK.equals(p.type)) {
                return repo.getCategoryBudgetsLiveByStart(userId, p.transType, p.type, p.startTime);
            }
            int m = Budget.TYPE_YEAR.equals(p.type) ? 0 : p.month;
            return repo.getCategoryBudgetsLive(userId, p.transType, p.type, p.year, m);
        });

    }

    private void updateTriggers(MediatorLiveData<CombinedParams> triggers) {
        PeriodSelection period = currentPeriod.getValue();
        String tt = currentTransType.getValue();
        if (period != null && tt != null) {
            triggers.setValue(new CombinedParams(
                    period.type, tt, period.year, period.month,
                    period.startTime, period.endTime));
            loadStats(period, tt);
        }
    }

    private void initRangeForType(String type, Calendar base) {
        int period;
        switch (type) {
            case Budget.TYPE_YEAR:  period = Budget.PERIOD_YEAR; break;
            case Budget.TYPE_WEEK:  period = Budget.PERIOD_WEEK; break;
            default:                period = Budget.PERIOD_MONTH; break;
        }
        int startDay = Budget.TYPE_MONTH.equals(type)
                ? BudgetConfig.getStartDay(getApplication()) : 1;
        long[] range = BudgetPeriodHelper.getPeriodRange(period, startDay, base);
        publishPeriod(type, range[0], range[1]);
    }

    private void publishPeriod(String type, long start, long end) {
        PeriodSelection next = new PeriodSelection(type, start, end);
        selectedYear.setValue(next.year);
        selectedMonth.setValue(next.month);
        selectedStartTime.setValue(next.startTime);
        selectedEndTime.setValue(next.endTime);
        currentBudgetType.setValue(next.type);
        currentPeriod.setValue(next);
    }

    public void setPeriod(long start, long end) {
        publishPeriod(getBudgetType(), start, end);
    }

    public void selectPeriod(String type, long start, long end) {
        publishPeriod(type, start, end);
    }

    public void autoAllocateRemaining(List<CategoryWithSubCategories> allCategories) {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return;
        
        AppExecutors.get().diskIO().execute(() -> {
            PeriodSelection selection = currentPeriod.getValue();
            if (selection == null) return;
            String bType = selection.type;
            String tt = getTransactionType();
            int year = selection.year;
            int month = selection.month;
            double remaining = Budget.TYPE_WEEK.equals(bType)
                    ? repo.getRemainingAllocationByStart(total.getAmount(), userId, tt,
                            bType, selection.startTime)
                    : repo.getRemainingAllocation(total.getAmount(), userId, tt,
                            bType, year, month);
            if (remaining <= 0) return;
            
            List<Budget> existing = Budget.TYPE_WEEK.equals(bType)
                    ? repo.getCategoryBudgetsSyncByStart(userId, tt, bType, selection.startTime)
                    : repo.getCategoryBudgetsSync(userId, tt, bType, year, month);
            List<String> existingIds = new ArrayList<>();
            if (existing != null) {
                for (Budget b : existing) existingIds.add(b.getTargetId());
            }
            
            List<CategorySelectorItem> unallocated = new ArrayList<>();
            for (CategoryWithSubCategories cws : allCategories) {
                if (cws.subCategories == null || cws.subCategories.isEmpty()) {
                    if (cws.category != null && !cws.category.isExcludeBudget() && !existingIds.contains(cws.category.getCloudId())) {
                        unallocated.add(new CategorySelectorItem(cws.category.getCloudId(), cws.category.getName(), cws.category.getIconUri()));
                    }
                } else {
                    for (SubCategory sub : cws.subCategories) {
                        if (!sub.isExcludeBudget() && !existingIds.contains(sub.getCloudId())) {
                            unallocated.add(new CategorySelectorItem(sub.getCloudId(), sub.getName(), sub.getIconUri()));
                        }
                    }
                }
            }
            
            if (unallocated.isEmpty()) return;
            double avg = remaining / unallocated.size();
            for (CategorySelectorItem item : unallocated) {
                addCategoryBudget(item.id, avg, total.getPeriod(), item.name, item.icon);
            }
        });
    }

    private static class CombinedParams {
        public String type;
        public String transType;
        public int year;
        public int month;
        public long startTime;
        public long endTime;

        public CombinedParams(String type, String transType, int year, int month,
                              long startTime, long endTime) {
            this.type = type;
            this.transType = transType;
            this.year = year;
            this.month = month;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static class CategorySelectorItem {
        String id, name, icon;
        CategorySelectorItem(String i, String n, String ic) { id=i; name=n; icon=ic; }
    }

    public LiveData<Integer> getSelectedYear() { return selectedYear; }
    public LiveData<Integer> getSelectedMonth() { return selectedMonth; }
    
    public LiveData<Long> getSelectedStartTime() { return selectedStartTime; }
    public LiveData<Long> getSelectedEndTime()   { return selectedEndTime; }
    public LiveData<PeriodSelection> getPeriodSelection() { return currentPeriod; }

    public int getCurrentYear()  { return selectedYear.getValue() != null ? selectedYear.getValue() : Calendar.getInstance().get(Calendar.YEAR); }
    public int getCurrentMonth() { return selectedMonth.getValue() != null ? selectedMonth.getValue() : Calendar.getInstance().get(Calendar.MONTH) + 1; }
    public String getUserId()       { return userId; }

    // ────────────────────────────────────────────────────────────────────
    //  公开 LiveData
    // ────────────────────────────────────────────────────────────────────

    public LiveData<Budget>         getTotalBudget()         { return totalBudgetLive; }
    public LiveData<List<Budget>>   getCategoryBudgets()     { return categoryBudgetsLive; }
    public LiveData<String>         getCurrentBudgetType()   { return currentBudgetType; }
    public LiveData<String>         getError()               { return errorLive; }
    public LiveData<Double>         getRemainingAllocation() { return remainingAllocationLive; }
    public LiveData<double[]>       getDailyAccumulated()    { return dailyAccumulatedLive; }
    public LiveData<List<PieSlice>> getPieSlices()           { return pieSlicesLive; }

    public String getTransactionType() {
        String t = currentTransType.getValue();
        return t != null ? t : Budget.TYPE_EXPENSE;
    }

    public LiveData<String> getCurrentTransactionType() {
        return currentTransType;
    }

    public void setTransactionType(String type) {
        if (!type.equals(currentTransType.getValue())) {
            currentTransType.setValue(type);
        }
    }

    public String getBudgetType() {
        String t = currentBudgetType.getValue();
        return t != null ? t : Budget.TYPE_MONTH;
    }

    // ────────────────────────────────────────────────────────────────────
    //  Tab 切换
    // ────────────────────────────────────────────────────────────────────

    public void switchToWeek() {
        if (!Budget.TYPE_WEEK.equals(currentBudgetType.getValue())) {
            initRangeForType(Budget.TYPE_WEEK, Calendar.getInstance());
        }
    }

    public void switchToMonth() {
        if (!Budget.TYPE_MONTH.equals(currentBudgetType.getValue())) {
            initRangeForType(Budget.TYPE_MONTH, Calendar.getInstance());
        }
    }

    public void switchToYear() {
        if (!Budget.TYPE_YEAR.equals(currentBudgetType.getValue())) {
            initRangeForType(Budget.TYPE_YEAR, Calendar.getInstance());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  总预算保存
    // ────────────────────────────────────────────────────────────────────

    public void saveTotalBudget(double amount, String budgetType) {
        PeriodSelection selection = currentPeriod.getValue();
        if (selection != null && budgetType.equals(selection.type)) {
            saveTotalBudget(amount, budgetType, selection.startTime, selection.endTime);
        } else {
            saveTotalBudget(amount, budgetType, getCurrentYear(), getCurrentMonth());
        }
    }

    public void saveTotalBudget(double amount, String budgetType, int year, int month) {
        if (Budget.TYPE_WEEK.equals(budgetType)) {
            throw new IllegalArgumentException("Week budgets require an exact start/end range");
        }
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        if (Budget.TYPE_YEAR.equals(budgetType)) {
            calendar.set(Calendar.MONTH, Calendar.JANUARY);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            calendar.set(Calendar.MONTH, Math.max(0, month - 1));
            int desiredStartDay = BudgetConfig.getStartDay(getApplication());
            calendar.set(Calendar.DAY_OF_MONTH,
                    Math.min(desiredStartDay,
                            calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        int startDay = Budget.TYPE_MONTH.equals(budgetType)
                ? BudgetConfig.getStartDay(getApplication()) : 1;
        long[] range = BudgetPeriodHelper.getPeriodRange(
                BudgetPeriodHelper.periodForType(budgetType), startDay, calendar);
        saveTotalBudget(amount, budgetType, range[0], range[1]);
    }

    public void saveTotalBudget(double amount, String budgetType, long startTime, long endTime) {
        PeriodSelection target = new PeriodSelection(budgetType, startTime, endTime);
        Budget candidate = buildTotalBudget(amount,
                BudgetPeriodHelper.periodForType(budgetType), budgetType,
                target.year, target.month, target.startTime, target.endTime);
        candidate.setTransactionType(getTransactionType());
        repo.saveTotalBudget(candidate, saved -> {
            refreshRemainingAllocation(amount, target);
            loadStats();
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  分类预算新增（重构核心）
    // ────────────────────────────────────────────────────────────────────

    /**
     * 新增或覆盖更新分类预算。
     *
     * 唯一性规则（新版）：
     *   周预算按 startTime，月/年预算按 budgetType + year + month 定位。
     *   - 若已存在记录（无论 period 是否相同）→ 覆盖更新。
     *   - 若不存在 → 新增。
     *
     * 这与旧版不同：旧版允许同一分类选择不同 period 共存，导致金额重复叠加。
     * 新版强制"一分类一记录"，period 变更视为修改现有记录而非新增。
     *
     * 周期约束：分类预算 period 不能超过总预算 period，否则拦截并推送错误。
     *
     * @param categoryCloudId  分类的 Bmob objectId
     * @param amount           预算金额
     * @param period           预算周期（Budget.PERIOD_DAY / WEEK / MONTH / YEAR）
     * @param categoryName     分类名称快照（存入 Budget 供离线展示）
     * @param categoryIconUrl  分类图标 URL 快照（存入 Budget 供离线展示，可为 null）
     * @return false 表示周期约束校验不通过，操作已被拦截
     */
    public boolean addCategoryBudget(String categoryCloudId, double amount,
                                     int period, String categoryName,
                                     String categoryIconUrl) {
        Budget total = totalBudgetLive.getValue();
        if (total != null && period > total.getPeriod()) {
            errorLive.setValue("分类预算周期不能超过总预算周期（"
                    + Budget.getPeriodLabel(total.getPeriod()) + "）");
            return false;
        }

        PeriodSelection selection = currentPeriod.getValue();
        if (selection == null) return false;
        String bType = selection.type;

        Budget b = new Budget();
        b.setTargetType(Budget.TARGET_CATEGORY);
        b.setTargetId(categoryCloudId);
        b.setAmount(amount);
        b.setPeriod(period);
        b.setBudgetType(bType);
        b.setTransactionType(getTransactionType());
        b.setYear(selection.year);
        b.setMonth(selection.month);
        b.setOwnerId(userId);
        b.setCategoryName(categoryName);        // ← 新增
        b.setCategoryIconUrl(categoryIconUrl);  // ← 新增
        b.setStartTime(selection.startTime);
        b.setEndTime(selection.endTime);
        b.setUpdatedAt(System.currentTimeMillis());

        // 统一走 addOrUpdateCategoryBudget，内部做唯一性判断
        repo.addOrUpdateCategoryBudget(b, action -> {
            if (total != null) {
                refreshRemainingAllocation(total.getAmount(), selection);
            }
            loadStats();
        });
        return true;
    }

    public void addOrUpdateCategoryBudget(Budget b, java.util.function.Consumer<String> callback) {
        repo.addOrUpdateCategoryBudget(b, action -> {
            loadStats();
            if (callback != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    callback.accept(action);
                }
            }
        });
    }

    /**
     * 更新分类预算金额和周期（编辑现有记录）。
     *
     * 同样通过 addOrUpdateCategoryBudget() 路由，保证唯一性约束一致。
     *
     * @return false 表示周期约束不通过
     */
    public boolean updateCategoryBudget(Budget budget, double amount, int period) {
        Budget total = totalBudgetLive.getValue();
        if (total != null && period > total.getPeriod()) {
            errorLive.setValue("分类预算周期不能超过总预算周期（"
                    + Budget.getPeriodLabel(total.getPeriod()) + "）");
            return false;
        }

        budget.setAmount(amount);
        budget.setPeriod(period);

        // 通过统一入口更新，确保不因 update() 绕过唯一性逻辑
        repo.addOrUpdateCategoryBudget(budget, action -> {
            if (total != null) {
                PeriodSelection selection = currentPeriod.getValue();
                if (selection != null) {
                    refreshRemainingAllocation(total.getAmount(), selection);
                }
            }
        });
        return true;
    }

    // ────────────────────────────────────────────────────────────────────
    //  分类预算删除
    // ────────────────────────────────────────────────────────────────────

    public void deleteCategoryBudget(int budgetId) {
        Budget total   = totalBudgetLive.getValue();
        PeriodSelection selection = currentPeriod.getValue();

        repo.markDeleteById(budgetId, () -> {
            if (total != null && selection != null) {
                refreshRemainingAllocation(total.getAmount(), selection);
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  剩余可分配预算
    // ────────────────────────────────────────────────────────────────────

    public void refreshRemainingAllocation(double totalAmount, String budgetType, int month) {
        PeriodSelection selection = currentPeriod.getValue();
        if (selection != null && budgetType.equals(selection.type)) {
            refreshRemainingAllocation(totalAmount, selection);
            return;
        }
        AppExecutors.get().diskIO().execute(() -> {
            String tt = getTransactionType();
            double remaining = repo.getRemainingAllocation(
                    totalAmount, userId, tt, budgetType, getCurrentYear(), month);
            AppExecutors.get().mainThread().execute(() ->
                    remainingAllocationLive.setValue(remaining));
        });
    }

    public void checkDuplicate(String budgetType, long startTime, Consumer<String> callback) {
        PeriodSelection target = new PeriodSelection(budgetType, startTime, startTime);
        AppExecutors.get().diskIO().execute(() -> {
            String tt = getTransactionType();
            Budget existing;
            if (Budget.TYPE_YEAR.equals(budgetType)) {
                existing = repo.getYearBudgetSync(userId, tt, target.year);
            } else if (Budget.TYPE_WEEK.equals(budgetType)) {
                existing = repo.getWeekBudgetSyncByStart(userId, tt, startTime);
            } else {
                existing = repo.getMonthBudgetSync(userId, tt, target.year, target.month);
            }
            String message = existing == null ? null
                    : (Budget.TYPE_YEAR.equals(budgetType)
                    ? target.year + "年预算已添加，如需修改请点击编辑"
                    : Budget.TYPE_WEEK.equals(budgetType)
                    ? "该周预算已添加，如需修改请点击编辑"
                    : target.year + "年" + target.month + "月预算已添加，如需修改请点击编辑");
            AppExecutors.get().mainThread().execute(() -> callback.accept(message));
        });
    }

    private void refreshRemainingAllocation(double totalAmount, PeriodSelection selection) {
        AppExecutors.get().diskIO().execute(() -> {
            String tt = getTransactionType();
            double remaining = Budget.TYPE_WEEK.equals(selection.type)
                    ? repo.getRemainingAllocationByStart(totalAmount, userId, tt,
                            selection.type, selection.startTime)
                    : repo.getRemainingAllocation(totalAmount, userId, tt,
                            selection.type, selection.year, selection.month);
            AppExecutors.get().mainThread().execute(() -> {
                PeriodSelection current = currentPeriod.getValue();
                if (current != null && current.startTime == selection.startTime
                        && current.type.equals(selection.type)) {
                    remainingAllocationLive.setValue(remaining);
                }
            });
        });
    }

    public void refreshRemainingAllocationFromCurrentTotal() {
        Budget total = totalBudgetLive.getValue();
        if (total == null) { remainingAllocationLive.setValue(null); return; }
        PeriodSelection selection = currentPeriod.getValue();
        if (selection != null) refreshRemainingAllocation(total.getAmount(), selection);
    }

    // ────────────────────────────────────────────────────────────────────
    //  图表数据
    // ────────────────────────────────────────────────────────────────────

    public void loadDailyChartData() {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return;

        final long periodStart = total.getStartTime();
        final long periodEnd   = total.getEndTime();
        
        // 计算天数
        final int days = BudgetPeriodHelper.getCalendarDayCount(periodStart, periodEnd);

        AppExecutors.get().diskIO().execute(() -> {
            double[] data = repo.getDailyAccumulatedSpent(userId, periodStart, periodEnd, days);
            AppExecutors.get().mainThread().execute(() -> dailyAccumulatedLive.setValue(data));
        });
    }

    public void loadPieChartData(java.util.Map<String, String> categoryNames) {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return;

        String  bType       = getBudgetType();
        int     year        = total.getYear();
        int     month       = total.getMonth();
        double  totalAmount = total.getAmount();

        int[] colors = {
                0xFF5B8DEF, 0xFF4CAF50, 0xFFFF9800, 0xFFE91E63,
                0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF607D8B
        };

        AppExecutors.get().diskIO().execute(() -> {
            String tt = getTransactionType();
            List<Budget> cats = Budget.TYPE_WEEK.equals(bType)
                    ? repo.getCategoryBudgetsSyncByStart(
                            userId, tt, bType, total.getStartTime())
                    : repo.getCategoryBudgetsSync(userId, tt, bType, year, month);
            List<PieSlice> slices = new ArrayList<>();
            double allocated = 0;

            if (cats != null) {
                for (int i = 0; i < cats.size(); i++) {
                    Budget cat = cats.get(i);
                    if (cat.getAmount() <= 0) continue;
                    String name = (categoryNames != null
                            && categoryNames.containsKey(cat.getTargetId()))
                            ? categoryNames.get(cat.getTargetId())
                            : "分类" + (i + 1);
                    slices.add(new PieSlice(name, (float) cat.getAmount(),
                            colors[i % colors.length]));
                    allocated += cat.getAmount();
                }
            }

            double unallocated = totalAmount - allocated;
            if (unallocated > 0.01)
                slices.add(new PieSlice("未分配", (float) unallocated, 0xFFE0E0E0));

            if (slices.isEmpty()) return;
            AppExecutors.get().mainThread().execute(() -> pieSlicesLive.setValue(slices));
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  云端同步
    // ────────────────────────────────────────────────────────────────────

    public void syncFromCloud(Consumer<Boolean> callback) {
        repo.syncBudgetsFromCloud(callback);
    }

    // ────────────────────────────────────────────────────────────────────
    //  支出统计
    // ────────────────────────────────────────────────────────────────────

    public void getPreviousPeriodBudget(String type, long currentStart, Consumer<Budget> callback) {
        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(currentStart);
        if (Budget.TYPE_YEAR.equals(type)) previous.add(Calendar.YEAR, -1);
        else if (Budget.TYPE_WEEK.equals(type)) previous.add(Calendar.DAY_OF_MONTH, -7);
        else previous.add(Calendar.MONTH, -1);
        int startDay = Budget.TYPE_MONTH.equals(type)
                ? BudgetConfig.getStartDay(getApplication()) : 1;
        long[] range = BudgetPeriodHelper.getPeriodRange(
                BudgetPeriodHelper.periodForType(type), startDay, previous);
        PeriodSelection target = new PeriodSelection(type, range[0], range[1]);
        AppExecutors.get().diskIO().execute(() -> {
            String tt = getTransactionType();
            Budget budget;
            if (Budget.TYPE_YEAR.equals(type)) {
                budget = repo.getYearBudgetSync(userId, tt, target.year);
            } else if (Budget.TYPE_WEEK.equals(type)) {
                budget = repo.getWeekBudgetSyncByStart(userId, tt, target.startTime);
            } else {
                budget = repo.getMonthBudgetSync(userId, tt, target.year, target.month);
            }
            AppExecutors.get().mainThread().execute(() -> callback.accept(budget));
        });
    }

    public double getSpentByCategorySync(String catCloudId) {
        Budget total = totalBudgetLive.getValue();
        if (total == null || catCloudId == null) return 0;
        return repo.getSpentAmountByCategory(userId, getTransactionType(), catCloudId,
                total.getStartTime(), total.getEndTime());
    }

    public double getSpentByCategoryInRange(String catCloudId, long startMs, long endMs) {
        if (catCloudId == null) return 0;
        return repo.getSpentAmountByCategory(
                userId, getTransactionType(), catCloudId, startMs, endMs);
    }

    public Map<String, Double> getBudgetAmountsByCategory(
            String transactionType, long startMs, long endMs) {
        return repo.getBudgetAmountsByCategory(
                userId, transactionType, startMs, endMs);
    }

    public double getTotalSpentSync() {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return 0;
        return repo.getTotalSpentInPeriod(userId, total.getStartTime(), total.getEndTime());
    }

    // ────────────────────────────────────────────────────────────────────
    //  Fragment 辅助
    // ────────────────────────────────────────────────────────────────────

    public Budget getCategoryBudgetSync(String catCloudId,
                                        String budgetType, int year, int month) {
        String tt = getTransactionType();
        return repo.getCategoryBudget(userId, tt, catCloudId, budgetType, year, month);
    }

    public List<Budget> getCategoryBudgetsSyncForCurrentType() {
        PeriodSelection selection = currentPeriod.getValue();
        if (selection == null) return new ArrayList<>();
        String  bType  = selection.type;
        String  tt     = getTransactionType();
        if (Budget.TYPE_WEEK.equals(bType)) {
            return repo.getCategoryBudgetsSyncByStart(
                    userId, tt, bType, selection.startTime);
        }
        return repo.getCategoryBudgetsSync(
                userId, tt, bType, selection.year, selection.month);
    }

    // ────────────────────────────────────────────────────────────────────
    //  内部工具
    // ────────────────────────────────────────────────────────────────────

    private Budget buildTotalBudget(double amount, int period, String budgetType,
                                    int year, int month, long startTime, long endTime) {
        Budget b = new Budget();
        b.setTargetType(Budget.TARGET_TOTAL);
        b.setTargetId(null);
        b.setAmount(amount);
        b.setPeriod(period);
        b.setBudgetType(budgetType);
        b.setYear(year);
        b.setMonth(month);
        b.setStartTime(startTime);
        b.setEndTime(endTime);
        b.setOwnerId(userId);
        b.setUpdatedAt(System.currentTimeMillis());
        return b;
    }
}
