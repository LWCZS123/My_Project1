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
import java.util.function.Consumer;

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

    private final LiveData<Budget>       totalBudgetLive;
    private final LiveData<List<Budget>> categoryBudgetsLive;

    private final MutableLiveData<Double>         remainingAllocationLive = new MutableLiveData<>();
    private final MutableLiveData<double[]>       dailyAccumulatedLive    = new MutableLiveData<>();
    private final MutableLiveData<List<PieSlice>> pieSlicesLive           = new MutableLiveData<>();
    private final MutableLiveData<String>         errorLive               = new MutableLiveData<>();

    public static class BudgetStats {
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
        AppExecutors.get().diskIO().execute(() -> {
            String type = getBudgetType();
            String tt = getTransactionType();
            Long startTime = selectedStartTime.getValue();
            Long endTime = selectedEndTime.getValue();

            if (startTime == null || endTime == null) return;

            // 1. 加载当前周期的统计数据 (周/月/年)
            boolean isYear = Budget.TYPE_YEAR.equals(type);
            boolean isWeek = Budget.TYPE_WEEK.equals(type);

            double inc = repo.getTotalIncomeInPeriod(userId, startTime, endTime);
            double exp = repo.getTotalSpentInPeriod(userId, startTime, endTime);

            Budget bud;
            if (isYear) bud = repo.getYearBudgetSync(userId, tt, getCurrentYear());
            else if (isWeek) bud = repo.getWeekBudgetSyncByStart(userId, tt, startTime);
            else bud = repo.getMonthBudgetSync(userId, tt, getCurrentYear(), getCurrentMonth());

            BudgetStats currentStats = new BudgetStats();
            currentStats.totalIncome = inc;
            currentStats.totalExpense = exp;
            currentStats.expenseBudget = (bud != null) ? bud.getAmount() : 0.0;

            if (Budget.TYPE_INCOME.equals(tt)) {
                currentStats.totalExpense = inc;
                currentStats.expenseBudget = (bud != null) ? bud.getAmount() : 0.0;
                currentStats.totalIncome = exp;
            }

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(startTime);
            int days;
            if (isYear) days = cal.getActualMaximum(Calendar.DAY_OF_YEAR);
            else if (isWeek) days = 7;
            else days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            currentStats.dailyAvgIncome = inc / days;
            currentStats.dailyAvgExpense = exp / days;

            // 2. 如果当前不是“年”模式，额外加载一整年的统计数据用于“年度概览”卡片
            BudgetStats yearlyStats = null;
            if (!isYear) {
                int year = getCurrentYear();
                Calendar yCal = Calendar.getInstance();
                yCal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
                long yStart = yCal.getTimeInMillis();
                yCal.set(year, Calendar.DECEMBER, 31, 23, 59, 59);
                long yEnd = yCal.getTimeInMillis();

                double yInc = repo.getTotalIncomeInPeriod(userId, yStart, yEnd);
                double yExp = repo.getTotalSpentInPeriod(userId, yStart, yEnd);
                Budget yBud = repo.getYearBudgetSync(userId, tt, year);

                yearlyStats = new BudgetStats();
                yearlyStats.totalIncome = yInc;
                yearlyStats.totalExpense = yExp;
                yearlyStats.expenseBudget = (yBud != null) ? yBud.getAmount() : 0.0;

                if (Budget.TYPE_INCOME.equals(tt)) {
                    yearlyStats.totalExpense = yInc;
                    yearlyStats.expenseBudget = (yBud != null) ? yBud.getAmount() : 0.0;
                    yearlyStats.totalIncome = yExp;
                }
                
                int yDays = yCal.getActualMaximum(Calendar.DAY_OF_YEAR);
                yearlyStats.dailyAvgIncome = yInc / yDays;
                yearlyStats.dailyAvgExpense = yExp / yDays;
            }

            final BudgetStats finalYearly = yearlyStats;
            AppExecutors.get().mainThread().execute(() -> {
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

        // 核心触发器：监听 类型、收支类型 和 起始时间
        MediatorLiveData<CombinedParams> triggers = new MediatorLiveData<>();
        triggers.addSource(currentBudgetType, t -> updateTriggers(triggers));
        triggers.addSource(currentTransType, tt -> updateTriggers(triggers));
        triggers.addSource(selectedStartTime, s -> updateTriggers(triggers));

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

        // 自动触发统计加载
        Transformations.switchMap(triggers, p -> {
            loadStats();
            return new MutableLiveData<>(null);
        }).observeForever(o -> {});
    }

    private void updateTriggers(MediatorLiveData<CombinedParams> triggers) {
        String type = currentBudgetType.getValue();
        String tt = currentTransType.getValue();
        Long start = selectedStartTime.getValue();
        if (type != null && start != null && tt != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(start);
            int y = cal.get(Calendar.YEAR);
            int m = cal.get(Calendar.MONTH) + 1;
            triggers.setValue(new CombinedParams(type, tt, y, m, start));
        }
    }

    private void initRangeForType(String type, Calendar base) {
        int period;
        switch (type) {
            case Budget.TYPE_YEAR:  period = Budget.PERIOD_YEAR; break;
            case Budget.TYPE_WEEK:  period = Budget.PERIOD_WEEK; break;
            default:                period = Budget.PERIOD_MONTH; break;
        }
        long[] range = BudgetPeriodHelper.getPeriodRange(period, 1, base);
        
        // 重要：先设置类型，再设置时间，确保 Observer 获取到正确的组合状态
        currentBudgetType.setValue(type);
        selectedStartTime.setValue(range[0]);
        selectedEndTime.setValue(range[1]);
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(range[0]);
        selectedYear.setValue(cal.get(Calendar.YEAR));
        selectedMonth.setValue(cal.get(Calendar.MONTH) + 1);
    }

    public void setYear(int year) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedStartTime.getValue() != null ? selectedStartTime.getValue() : System.currentTimeMillis());
        cal.set(Calendar.YEAR, year);
        initRangeForType(currentBudgetType.getValue(), cal);
    }

    public void setMonth(int month) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedStartTime.getValue() != null ? selectedStartTime.getValue() : System.currentTimeMillis());
        if (Budget.TYPE_WEEK.equals(currentBudgetType.getValue())) {
            cal.set(Calendar.WEEK_OF_YEAR, month);
        } else {
            cal.set(Calendar.MONTH, month - 1);
        }
        initRangeForType(currentBudgetType.getValue(), cal);
    }

    public void setPeriod(long start, long end) {
        selectedStartTime.setValue(start);
        selectedEndTime.setValue(end);
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        selectedYear.setValue(cal.get(Calendar.YEAR));
        selectedMonth.setValue(cal.get(Calendar.MONTH) + 1);
    }

    public void autoAllocateRemaining(List<CategoryWithSubCategories> allCategories) {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return;
        
        AppExecutors.get().diskIO().execute(() -> {
            String bType = getBudgetType();
            String tt = getTransactionType();
            int year = getCurrentYear();
            int month = getCurrentMonth();
            double remaining = repo.getRemainingAllocation(total.getAmount(), userId, tt, bType, year, month);
            if (remaining <= 0) return;
            
            List<Budget> existing = repo.getCategoryBudgetsSync(userId, tt, bType, year, month);
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

        public CombinedParams(String type, String transType, int year, int month, long startTime) {
            this.type = type;
            this.transType = transType;
            this.year = year;
            this.month = month;
            this.startTime = startTime;
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
        saveTotalBudget(amount, budgetType, getCurrentYear(), getCurrentMonth());
    }

    public void saveTotalBudget(double amount, String budgetType, int year, int month) {
        AppExecutors.get().diskIO().execute(() -> {
            boolean isYear = Budget.TYPE_YEAR.equals(budgetType);
            boolean isWeek = Budget.TYPE_WEEK.equals(budgetType);
            int     finalMonth = isYear ? 0 : month;
            
            int period;
            if (Budget.TYPE_YEAR.equals(budgetType)) period = Budget.PERIOD_YEAR;
            else if (Budget.TYPE_WEEK.equals(budgetType)) period = Budget.PERIOD_WEEK;
            else period = Budget.PERIOD_MONTH;

            Calendar cal = Calendar.getInstance();
            cal.setFirstDayOfWeek(Calendar.SUNDAY);
            cal.set(Calendar.YEAR, year);
            if (Budget.TYPE_WEEK.equals(budgetType)) {
                cal.set(Calendar.WEEK_OF_YEAR, finalMonth);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
            } else if (Budget.TYPE_YEAR.equals(budgetType)) {
                cal.set(Calendar.MONTH, 0);
                cal.set(Calendar.DAY_OF_MONTH, 1);
            } else {
                cal.set(Calendar.MONTH, finalMonth - 1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
            }

            int startDay = BudgetConfig.getStartDay(getApplication());
            long[] range = BudgetPeriodHelper.getPeriodRange(period, startDay, cal);
            
            String tt = getTransactionType();
            Budget existing;
            if (Budget.TYPE_YEAR.equals(budgetType)) existing = repo.getYearBudgetSync(userId, tt, year);
            else if (Budget.TYPE_WEEK.equals(budgetType)) existing = repo.getWeekBudgetSyncByStart(userId, tt, range[0]);
            else existing = repo.getMonthBudgetSync(userId, tt, year, finalMonth);

            if (existing != null) {
                existing.setAmount(amount);
                existing.setStartTime(range[0]);
                existing.setEndTime(range[1]);
                existing.setTransactionType(getTransactionType()); // ← 新增
                repo.update(existing);
            } else {
                Budget b = buildTotalBudget(amount, period, budgetType, year, finalMonth, cal);
                b.setTransactionType(getTransactionType()); // ← 新增
                repo.insert(b, null);
            }

            refreshRemainingAllocation(amount, budgetType, finalMonth);
            loadStats();
        });
    }

    public void checkDuplicate(String budgetType, Consumer<String> callback) {
        checkDuplicate(budgetType, getCurrentYear(), getCurrentMonth(), callback);
    }

    public void checkDuplicate(String budgetType, int year, int month, Consumer<String> callback) {
        AppExecutors.get().diskIO().execute(() -> {
            boolean isYear = Budget.TYPE_YEAR.equals(budgetType);
            boolean isWeek = Budget.TYPE_WEEK.equals(budgetType);
            
            String tt = getTransactionType();
            Budget existing;
            if (isYear) existing = repo.getYearBudgetSync(userId, tt, year);
            else if (isWeek) {
                Calendar cal2 = Calendar.getInstance();
                cal2.setFirstDayOfWeek(Calendar.SUNDAY);
                cal2.set(Calendar.YEAR, year);
                cal2.set(Calendar.WEEK_OF_YEAR, month);
                cal2.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, 1, cal2);
                existing = repo.getWeekBudgetSyncByStart(userId, tt, range[0]);
            }
            else existing = repo.getMonthBudgetSync(userId, tt, year, month);

            String msg = null;
            if (existing != null) {
                if (isYear) msg = year + "年预算已添加，如需修改请点击编辑";
                else if (isWeek) msg = "该周预算已添加，如需修改请点击编辑";
                else msg = year + "年" + month + "月预算已添加，如需修改请点击编辑";
            }
            final String result = msg;
            AppExecutors.get().mainThread().execute(() -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    callback.accept(result);
                }
            });
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  分类预算新增（重构核心）
    // ────────────────────────────────────────────────────────────────────

    /**
     * 新增或覆盖更新分类预算。
     *
     * 唯一性规则（新版）：
     *   同一分类在同一 budgetType + year + month 下只允许存在一条记录。
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

        String  bType  = getBudgetType();
        boolean isYear = Budget.TYPE_YEAR.equals(bType);
        boolean isWeek = Budget.TYPE_WEEK.equals(bType);
        int     year   = getCurrentYear();
        int     month  = isYear ? 0 : getCurrentMonth();

        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.SUNDAY);
        cal.set(Calendar.YEAR, year);
        if (isWeek) {
            cal.set(Calendar.WEEK_OF_YEAR, month);
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        } else if (isYear) {
            cal.set(Calendar.MONTH, 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }

        int startDay = BudgetConfig.getStartDay(getApplication());

        Budget b = new Budget();
        b.setTargetType(Budget.TARGET_CATEGORY);
        b.setTargetId(categoryCloudId);
        b.setAmount(amount);
        b.setPeriod(period);
        b.setBudgetType(bType);
        b.setYear(year);
        b.setMonth(month);
        b.setOwnerId(userId);
        b.setCategoryName(categoryName);        // ← 新增
        b.setCategoryIconUrl(categoryIconUrl);  // ← 新增
        long[] range = BudgetPeriodHelper.getPeriodRange(period, startDay, cal);
        b.setStartTime(range[0]);
        b.setEndTime(range[1]);
        b.setUpdatedAt(System.currentTimeMillis());

        // 统一走 addOrUpdateCategoryBudget，内部做唯一性判断
        repo.addOrUpdateCategoryBudget(b, action -> {
            if (total != null) {
                refreshRemainingAllocation(total.getAmount(), bType, month);
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

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, budget.getYear());
        if (Budget.TYPE_WEEK.equals(budget.getBudgetType())) {
            cal.set(Calendar.WEEK_OF_YEAR, budget.getMonth());
        } else if (budget.getMonth() > 0) {
            cal.set(Calendar.MONTH, budget.getMonth() - 1);
        } else {
            cal.set(Calendar.MONTH, 0);
        }
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int startDay = BudgetConfig.getStartDay(getApplication());

        budget.setAmount(amount);
        budget.setPeriod(period);
        long[] range = BudgetPeriodHelper.getPeriodRange(period, startDay, cal);
        budget.setStartTime(range[0]);
        budget.setEndTime(range[1]);

        // 通过统一入口更新，确保不因 update() 绕过唯一性逻辑
        repo.addOrUpdateCategoryBudget(budget, action -> {
            if (total != null) {
                String bType   = getBudgetType();
                boolean isYear = Budget.TYPE_YEAR.equals(bType);
                int m          = isYear ? 0 : getCurrentMonth();
                refreshRemainingAllocation(total.getAmount(), bType, m);
            }
        });
        return true;
    }

    // ────────────────────────────────────────────────────────────────────
    //  分类预算删除
    // ────────────────────────────────────────────────────────────────────

    public void deleteCategoryBudget(int budgetId) {
        Budget total   = totalBudgetLive.getValue();
        String bType   = getBudgetType();
        boolean isYear = Budget.TYPE_YEAR.equals(bType);
        int month      = isYear ? 0 : getCurrentMonth();

        repo.markDeleteById(budgetId);

        if (total != null) {
            final double totalAmt = total.getAmount();
            AppExecutors.get().diskIO().execute(() ->
                    refreshRemainingAllocation(totalAmt, bType, month));
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  剩余可分配预算
    // ────────────────────────────────────────────────────────────────────

    public void refreshRemainingAllocation(double totalAmount, String budgetType, int month) {
        AppExecutors.get().diskIO().execute(() -> {
            String tt = getTransactionType();
            double remaining = repo.getRemainingAllocation(
                    totalAmount, userId, tt, budgetType, getCurrentYear(), month);
            AppExecutors.get().mainThread().execute(() ->
                    remainingAllocationLive.setValue(remaining));
        });
    }

    public void refreshRemainingAllocationFromCurrentTotal() {
        Budget total = totalBudgetLive.getValue();
        if (total == null) { remainingAllocationLive.setValue(null); return; }
        String  bType  = getBudgetType();
        boolean isYear = Budget.TYPE_YEAR.equals(bType);
        int     month  = isYear ? 0 : getCurrentMonth();
        refreshRemainingAllocation(total.getAmount(), bType, month);
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
        long diff = periodEnd - periodStart;
        final int days = (int) (diff / 86_400_000L) + 1;

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
            List<Budget> cats = repo.getCategoryBudgetsSync(userId, tt, bType, year, month);
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

    public void getPreviousPeriodBudget(String type, int year, int month, Consumer<Budget> callback) {
        AppExecutors.get().diskIO().execute(() -> {
            int prevYear = year;
            int prevMonth = month;
            
            if (Budget.TYPE_YEAR.equals(type)) {
                prevYear = year - 1;
            } else {
                prevMonth = month - 1;
                if (prevMonth < 1) {
                    prevMonth = 12;
                    prevYear = year - 1;
                }
            }
            
            Budget b;
            String tt = getTransactionType();
            if (Budget.TYPE_YEAR.equals(type)) {
                b = repo.getYearBudgetSync(userId, tt, prevYear);
            } else if (Budget.TYPE_WEEK.equals(type)) {
                b = repo.getWeekBudgetSync(userId, tt, prevYear, prevMonth);
            } else {
                b = repo.getMonthBudgetSync(userId, tt, prevYear, prevMonth);
            }
            
            final Budget result = b;
            AppExecutors.get().mainThread().execute(() -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    callback.accept(result);
                }
            });
        });
    }

    public double getSpentByCategorySync(String catCloudId) {
        Budget total = totalBudgetLive.getValue();
        if (total == null || catCloudId == null) return 0;
        return repo.getSpentAmountByCategory(userId, catCloudId,
                total.getStartTime(), total.getEndTime());
    }

    public double getSpentByCategoryInRange(String catCloudId, long startMs, long endMs) {
        if (catCloudId == null) return 0;
        return repo.getSpentAmountByCategory(userId, catCloudId, startMs, endMs);
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
        String  bType  = getBudgetType();
        String  tt     = getTransactionType();
        boolean isYear = Budget.TYPE_YEAR.equals(bType);
        int     month  = isYear ? 0 : getCurrentMonth();
        return repo.getCategoryBudgetsSync(userId, tt, bType, getCurrentYear(), month);
    }

    // ────────────────────────────────────────────────────────────────────
    //  内部工具
    // ────────────────────────────────────────────────────────────────────

    private Budget buildTotalBudget(double amount, int period, String budgetType,
                                    int year, int month, Calendar cal) {
        int startDay = BudgetConfig.getStartDay(getApplication());
        long[] range = BudgetPeriodHelper.getPeriodRange(period, startDay, cal);
        Budget b = new Budget();
        b.setTargetType(Budget.TARGET_TOTAL);
        b.setTargetId(null);
        b.setAmount(amount);
        b.setPeriod(period);
        b.setBudgetType(budgetType);
        b.setYear(year);
        b.setMonth(month);
        b.setStartTime(range[0]);
        b.setEndTime(range[1]);
        b.setOwnerId(userId);
        b.setUpdatedAt(System.currentTimeMillis());
        return b;
    }
}