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
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BudgetPeriodHelper;

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

    private final MutableLiveData<Integer> selectedYear  = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedMonth = new MutableLiveData<>();

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
            int year = getCurrentYear();
            int month = getCurrentMonth();
            
            // 根据当前选中的类型计算统计范围
            int period = Budget.PERIOD_MONTH;
            if (Budget.TYPE_WEEK.equals(type)) period = Budget.PERIOD_WEEK;
            else if (Budget.TYPE_YEAR.equals(type)) period = Budget.PERIOD_YEAR;

            Calendar base = Calendar.getInstance();
            base.set(Calendar.YEAR, year);
            base.set(Calendar.MONTH, month - 1);
            base.set(Calendar.DAY_OF_MONTH, 1);
            
            long[] range = BudgetPeriodHelper.getPeriodRange(period, 1, base);
            double inc = repo.getTotalIncomeInPeriod(userId, range[0], range[1]);
            double exp = repo.getTotalSpentInPeriod(userId, range[0], range[1]);
            
            Budget bud;
            if (Budget.TYPE_YEAR.equals(type)) bud = repo.getYearBudgetSync(userId, year);
            else if (Budget.TYPE_WEEK.equals(type)) bud = repo.getWeekBudgetSync(userId, year, month);
            else bud = repo.getMonthBudgetSync(userId, year, month);

            BudgetStats stats = new BudgetStats();
            stats.totalIncome = inc;
            stats.totalExpense = exp;
            stats.expenseBudget = (bud != null) ? bud.getAmount() : 0;
            
            Calendar cal = (Calendar) base.clone();
            int days = (period == Budget.PERIOD_YEAR) ? (cal.getActualMaximum(Calendar.DAY_OF_YEAR)) : 
                      (period == Budget.PERIOD_WEEK ? 7 : cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            stats.dailyAvgIncome = inc / days;
            stats.dailyAvgExpense = exp / days;
            
            if (Budget.TYPE_YEAR.equals(type)) {
                AppExecutors.get().mainThread().execute(() -> yearlyStatsLive.setValue(stats));
            } else {
                AppExecutors.get().mainThread().execute(() -> monthlyStatsLive.setValue(stats));
            }
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

        Calendar cal = Calendar.getInstance();
        selectedYear.setValue(cal.get(Calendar.YEAR));
        selectedMonth.setValue(cal.get(Calendar.MONTH) + 1);

        // 优化触发逻辑：监听所有参数，并在参数变化时确保触发实时查询
        MediatorLiveData<CombinedParams> triggers = new MediatorLiveData<>();
        triggers.addSource(currentBudgetType, t -> triggers.setValue(new CombinedParams(t, getCurrentYear(), getCurrentMonth())));
        triggers.addSource(selectedYear, y -> triggers.setValue(new CombinedParams(getBudgetType(), y, getCurrentMonth())));
        triggers.addSource(selectedMonth, m -> triggers.setValue(new CombinedParams(getBudgetType(), getCurrentYear(), m)));

        totalBudgetLive = Transformations.switchMap(triggers, p -> {
            // 当触发器改变时，Repository 会返回新的 LiveData。
            // 但如果数据库中有缓存，它会立刻发射旧数据。
            // 我们在 Repo 外层不做过多干预，但在 Activity 中加强空值处理。
            if (Budget.TYPE_YEAR.equals(p.type)) {
                return repo.getYearBudgetLive(userId, p.year);
            } else if (Budget.TYPE_WEEK.equals(p.type)) {
                return repo.getWeekBudgetLive(userId, p.year, p.month);
            } else {
                return repo.getMonthBudgetLive(userId, p.year, p.month);
            }
        });

        categoryBudgetsLive = Transformations.switchMap(triggers, p -> {
            if (p == null) return new MutableLiveData<>(null);
            int m = Budget.TYPE_YEAR.equals(p.type) ? 0 : p.month;
            return repo.getCategoryBudgetsLive(userId, p.type, p.year, m);
        });

        // 自动触发统计加载，解决 Semantic Conflict 与重复调用
        Transformations.switchMap(triggers, p -> {
            loadStats();
            return new MutableLiveData<>(null);
        }).observeForever(o -> {});
    }

    public void setYear(int year) { 
        if (selectedYear.getValue() != null && selectedYear.getValue() == year) return;
        selectedYear.setValue(year); 
    }

    public void setMonth(int month) { 
        if (selectedMonth.getValue() != null && selectedMonth.getValue() == month) return;
        selectedMonth.setValue(month);
    }

    public void autoAllocateRemaining(List<CategoryWithSubCategories> allCategories) {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return;
        
        AppExecutors.get().diskIO().execute(() -> {
            String bType = getBudgetType();
            int year = getCurrentYear();
            int month = getCurrentMonth();
            double remaining = repo.getRemainingAllocation(total.getAmount(), userId, bType, year, month);
            if (remaining <= 0) return;
            
            List<Budget> existing = repo.getCategoryBudgetsSync(userId, bType, year, month);
            List<String> existingIds = new ArrayList<>();
            if (existing != null) {
                for (Budget b : existing) existingIds.add(b.getTargetId());
            }
            
            List<CategorySelectorItem> unallocated = new ArrayList<>();
            for (CategoryWithSubCategories cws : allCategories) {
                if (cws.subCategories == null || cws.subCategories.isEmpty()) {
                    if (cws.category != null && !existingIds.contains(cws.category.getCloudId())) {
                        unallocated.add(new CategorySelectorItem(cws.category.getCloudId(), cws.category.getName(), cws.category.getIconUri()));
                    }
                } else {
                    for (SubCategory sub : cws.subCategories) {
                        if (!existingIds.contains(sub.getCloudId())) {
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
        String type; int year; int month;
        CombinedParams(String t, int y, int m) { type=t; year=y; month=m; }
    }

    private static class CategorySelectorItem {
        String id, name, icon;
        CategorySelectorItem(String i, String n, String ic) { id=i; name=n; icon=ic; }
    }

    public LiveData<Integer> getSelectedYear() { return selectedYear; }
    public LiveData<Integer> getSelectedMonth() { return selectedMonth; }

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

    public String getBudgetType() {
        String t = currentBudgetType.getValue();
        return t != null ? t : Budget.TYPE_MONTH;
    }

    // ────────────────────────────────────────────────────────────────────
    //  Tab 切换
    // ────────────────────────────────────────────────────────────────────

    public void switchToWeek() {
        if (!Budget.TYPE_WEEK.equals(currentBudgetType.getValue()))
            currentBudgetType.setValue(Budget.TYPE_WEEK);
    }

    public void switchToMonth() {
        if (!Budget.TYPE_MONTH.equals(currentBudgetType.getValue()))
            currentBudgetType.setValue(Budget.TYPE_MONTH);
    }

    public void switchToYear() {
        if (!Budget.TYPE_YEAR.equals(currentBudgetType.getValue()))
            currentBudgetType.setValue(Budget.TYPE_YEAR);
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
            if (isYear) period = Budget.PERIOD_YEAR;
            else if (isWeek) period = Budget.PERIOD_WEEK;
            else period = Budget.PERIOD_MONTH;

            Budget existing;
            if (isYear) existing = repo.getYearBudgetSync(userId, year);
            else if (isWeek) existing = repo.getWeekBudgetSync(userId, year, finalMonth);
            else existing = repo.getMonthBudgetSync(userId, year, finalMonth);

            if (existing != null) {
                existing.setAmount(amount);
                existing.setPeriod(period);
                long[] range = BudgetPeriodHelper.getPeriodRange(period);
                existing.setStartTime(range[0]);
                existing.setEndTime(range[1]);
                repo.update(existing);
            } else {
                Budget b = buildTotalBudget(amount, period, budgetType, year, finalMonth);
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
            
            Budget existing;
            if (isYear) existing = repo.getYearBudgetSync(userId, year);
            else if (isWeek) existing = repo.getWeekBudgetSync(userId, year, month);
            else existing = repo.getMonthBudgetSync(userId, year, month);

            String msg = null;
            if (existing != null) {
                if (isYear) msg = year + "年预算已添加，如需修改请点击编辑";
                else if (isWeek) msg = year + "年" + month + "月某周预算已添加，如需修改请点击编辑";
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
        int     month  = isYear ? 0 : getCurrentMonth();

        Budget b = new Budget();
        b.setTargetType(Budget.TARGET_CATEGORY);
        b.setTargetId(categoryCloudId);
        b.setAmount(amount);
        b.setPeriod(period);
        b.setBudgetType(bType);
        b.setYear(getCurrentYear());
        b.setMonth(month);
        b.setOwnerId(userId);
        b.setCategoryName(categoryName);        // ← 新增
        b.setCategoryIconUrl(categoryIconUrl);  // ← 新增
        long[] range = BudgetPeriodHelper.getPeriodRange(period);
        b.setStartTime(range[0]);
        b.setEndTime(range[1]);
        b.setUpdatedAt(System.currentTimeMillis());

        // 统一走 addOrUpdateCategoryBudget，内部做唯一性判断
        repo.addOrUpdateCategoryBudget(b, action -> {
            if (total != null) {
                refreshRemainingAllocation(total.getAmount(), bType, month);
            }
        });
        return true;
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
        long[] range = BudgetPeriodHelper.getPeriodRange(period);
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
            double remaining = repo.getRemainingAllocation(
                    totalAmount, userId, budgetType, getCurrentYear(), month);
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

        String  bType  = getBudgetType();
        boolean isYear = Budget.TYPE_YEAR.equals(bType);

        Calendar cal = Calendar.getInstance();
        long periodStart;
        long periodEnd;
        int  days;

        if (isYear) {
            cal.set(getCurrentYear(), Calendar.JANUARY, 1, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            periodStart = cal.getTimeInMillis();
            boolean leap = (getCurrentYear() % 4 == 0 && getCurrentYear() % 100 != 0)
                    || (getCurrentYear() % 400 == 0);
            days      = leap ? 366 : 365;
            periodEnd = periodStart + (long) days * 86_400_000L - 1;
        } else {
            cal.set(getCurrentYear(), getCurrentMonth() - 1, 1, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            periodStart = cal.getTimeInMillis();
            days      = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            periodEnd = periodStart + (long) days * 86_400_000L - 1;
        }

        final long start = periodStart, end = periodEnd;
        final int  d     = days;
        AppExecutors.get().diskIO().execute(() -> {
            double[] data = repo.getDailyAccumulatedSpent(userId, start, end, d);
            AppExecutors.get().mainThread().execute(() -> dailyAccumulatedLive.setValue(data));
        });
    }

    public void loadPieChartData(java.util.Map<String, String> categoryNames) {
        Budget total = totalBudgetLive.getValue();
        if (total == null) return;

        String  bType       = getBudgetType();
        boolean isYear      = Budget.TYPE_YEAR.equals(bType);
        int     month       = isYear ? 0 : getCurrentMonth();
        double  totalAmount = total.getAmount();

        int[] colors = {
                0xFF5B8DEF, 0xFF4CAF50, 0xFFFF9800, 0xFFE91E63,
                0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF607D8B
        };

        AppExecutors.get().diskIO().execute(() -> {
            List<Budget> cats = repo.getCategoryBudgetsSync(userId, bType, getCurrentYear(), month);
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
            if (Budget.TYPE_YEAR.equals(type)) {
                b = repo.getYearBudgetSync(userId, prevYear);
            } else if (Budget.TYPE_WEEK.equals(type)) {
                b = repo.getWeekBudgetSync(userId, prevYear, prevMonth);
            } else {
                b = repo.getMonthBudgetSync(userId, prevYear, prevMonth);
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
        return repo.getCategoryBudget(userId, catCloudId, budgetType, year, month);
    }

    public List<Budget> getCategoryBudgetsSyncForCurrentType() {
        String  bType  = getBudgetType();
        boolean isYear = Budget.TYPE_YEAR.equals(bType);
        int     month  = isYear ? 0 : getCurrentMonth();
        return repo.getCategoryBudgetsSync(userId, bType, getCurrentYear(), month);
    }

    // ────────────────────────────────────────────────────────────────────
    //  内部工具
    // ────────────────────────────────────────────────────────────────────

    private Budget buildTotalBudget(double amount, int period, String budgetType,
                                    int year, int month) {
        long[] range = BudgetPeriodHelper.getPeriodRange(period);
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