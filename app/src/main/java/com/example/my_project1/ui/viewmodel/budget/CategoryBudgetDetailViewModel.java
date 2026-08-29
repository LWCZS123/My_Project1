package com.example.my_project1.ui.viewmodel.budget;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.data.repository.budget.BudgetRepository;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BudgetPeriodHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * CategoryBudgetDetailViewModel
 *
 * Data layer for Category Budget Detail, providing:
 *   1. Category budget info (Total / Spent / Remaining)
 *   2. Bill list in the budget period
 *   3. Trend data (Week / Month / Year views)
 *
 * Time Range:
 *   Bills and spent amount use the canonical range persisted on the budget record.
 */
public class CategoryBudgetDetailViewModel extends AndroidViewModel {

    private static final String TAG = "CatBudgetDetailVM";

    public static final String TREND_WEEK  = "WEEK";
    public static final String TREND_MONTH = "MONTH";
    public static final String TREND_YEAR  = "YEAR";

    // 当前分类在预算时间范围内的账单列表
    public final MutableLiveData<List<Bill>>   billsLive        = new MutableLiveData<>();
    // 趋势图数据数组
    public final MutableLiveData<double[]>     trendDataLive    = new MutableLiveData<>();
    // 当前趋势图视图类型
    public final MutableLiveData<String>       trendTypeLive    = new MutableLiveData<>(TREND_MONTH);
    // 监听特定预算变更
    public LiveData<Budget>                    budgetLive;
    // 已用金额（从账单实时统计）
    public final MutableLiveData<Double>       spentLive        = new MutableLiveData<>(0.0);
    // 预算设定金额
    public final MutableLiveData<Double>       budgetAmountLive = new MutableLiveData<>(0.0);
    // X 轴日期标签
    public final MutableLiveData<String[]>     trendLabelsLive  = new MutableLiveData<>();

    private final BudgetRepository repo;
    private final BillDao          billDao;
    private final String           userId;

    private String currentCatCloudId;
    private Budget currentBudget;

    // 当前周期实际生效的时间范围，由 init() 时计算并固定，整个页面生命周期内不变
    private long effectiveStartTime;
    private long effectiveEndTime;
    private final AtomicInteger trendGeneration = new AtomicInteger();

    public CategoryBudgetDetailViewModel(@NonNull Application app) {
        super(app);
        repo    = new BudgetRepository(app);
        billDao = AppDatabase.getInstance(app).billDao();
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        userId = (user != null) ? user.getObjectId() : "";
    }

    /**
     * Initialize with budget object and load data.
     * Called once in Activity.onCreate().
     */
    public void init(Budget budget, String catCloudId) {
        this.currentBudget     = budget;
        this.currentCatCloudId = catCloudId;
        budgetAmountLive.setValue(budget.getAmount());
        budgetLive = repo.getByIdLive(budget.getId());

        // The persisted range is the canonical period key. Reconstruct only legacy rows.
        if (budget.getStartTime() > 0 && budget.getEndTime() >= budget.getStartTime()) {
            effectiveStartTime = budget.getStartTime();
            effectiveEndTime = budget.getEndTime();
        } else {
            Calendar fallback = Calendar.getInstance();
            fallback.clear();
            fallback.set(budget.getYear(),
                    Math.max(0, budget.getMonth() - 1), 1);
            long[] range = BudgetPeriodHelper.getPeriodRange(
                    budget.getPeriod(), 1, fallback);
            effectiveStartTime = range[0];
            effectiveEndTime = range[1];
        }

        loadBills();
        loadTrendData(trendTypeLive.getValue());
    }

    // ════════════════════════════════════════════════════════
    //  Bill Loading
    // ════════════════════════════════════════════════════════

    private void loadBills() {
        if (currentCatCloudId == null) return;

        final long start = effectiveStartTime;
        final long end   = effectiveEndTime;

        AppExecutors.get().diskIO().execute(() -> {
            try {
                List<Bill> bills = billDao.getBillsByCategoryInRange(
                        userId, currentCatCloudId, currentBillType(), start, end);
                if (bills == null) bills = new ArrayList<>();

                double spent = 0;
                for (Bill b : bills) {
                    if (!b.isExcludeBudget()) spent += b.getAmount();
                }
                final double     finalSpent = spent;
                final List<Bill> finalBills = bills;

                AppExecutors.get().mainThread().execute(() -> {
                    billsLive.setValue(finalBills);
                    spentLive.setValue(finalSpent);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load bills: " + e.getMessage());
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  Trend Data
    // ════════════════════════════════════════════════════════

    /**
     * Switch trend view type and reload data.
     */
    public void switchTrend(String trendType) {
        trendTypeLive.setValue(trendType);
        loadTrendData(trendType);
    }

    private void loadTrendData(String trendType) {
        if (currentCatCloudId == null) return;
        if (trendType == null) trendType = TREND_MONTH;

        final String type = trendType;
        final int requestId = trendGeneration.incrementAndGet();

        AppExecutors.get().diskIO().execute(() -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(effectiveStartTime);

            long   periodStart;
            long   periodEnd;
            int    points;
            String[] labels;

            switch (type) {
                case TREND_WEEK: {
                    long[] range = BudgetPeriodHelper.getPeriodRange(
                            Budget.PERIOD_WEEK, 1, cal);
                    periodStart = range[0];
                    periodEnd = range[1];
                    points      = 7;
                    labels = new String[]{"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                    break;
                }
                case TREND_YEAR: {
                    // Jan 1 to Dec 31 of the budget year
                    int year = cal.get(Calendar.YEAR);
                    cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    periodStart = cal.getTimeInMillis();
                    cal.add(Calendar.YEAR, 1);
                    periodEnd = cal.getTimeInMillis() - 1;
                    points    = 12;
                    labels = new String[]{"1月","2月","3月","4月","5月","6月",
                            "7月","8月","9月","10月","11月","12月"};
                    break;
                }
                default: { // TREND_MONTH
                    if (Budget.TYPE_MONTH.equals(currentBudget.getBudgetType())) {
                        periodStart = effectiveStartTime;
                        periodEnd = effectiveEndTime;
                    } else {
                        long[] range = BudgetPeriodHelper.getPeriodRange(
                                Budget.PERIOD_MONTH, 1, cal);
                        periodStart = range[0];
                        periodEnd = range[1];
                    }
                    points = BudgetPeriodHelper.getCalendarDayCount(periodStart, periodEnd);
                    labels = buildDayLabels(points, periodStart);
                    break;
                }
            }

            double[] result;
            if (TREND_YEAR.equals(type)) {
                // Annual view aggregated by month
                result = getMonthlySpent(periodStart, periodEnd);
            } else {
                // Week/Month view aggregated by day
                result = getDailySpent(periodStart, periodEnd, points);
            }

            final double[] finalData   = result;
            final String[] finalLabels = labels;

            AppExecutors.get().mainThread().execute(() -> {
                if (requestId != trendGeneration.get()) return;
                trendDataLive.setValue(finalData);
                trendLabelsLive.setValue(finalLabels);
            });
        });
    }

    /**
     * Statistical daily spent for week/month views.
     */
    private double[] getDailySpent(long periodStart, long periodEnd, int days) {
        double[] result = new double[days];
        try {
            List<Bill> bills = billDao.getBillsByCategoryInRange(
                    userId, currentCatCloudId, currentBillType(), periodStart, periodEnd);
            if (bills == null) return result;

            Map<Long, Integer> dayIndexes = new HashMap<>(days);
            Calendar cursor = Calendar.getInstance();
            cursor.setTimeInMillis(periodStart);
            for (int i = 0; i < days; i++) {
                resetToStartOfDay(cursor);
                dayIndexes.put(cursor.getTimeInMillis(), i);
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            for (Bill b : bills) {
                if (b.isExcludeBudget()) continue;
                long billTime = b.getBillTime() != null ? b.getBillTime().getTime() : 0;
                Calendar billDay = Calendar.getInstance();
                billDay.setTimeInMillis(billTime);
                resetToStartOfDay(billDay);
                Integer idx = dayIndexes.get(billDay.getTimeInMillis());
                if (idx != null) {
                    result[idx] += b.getAmount();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to aggregate daily spent: " + e.getMessage());
        }
        return result;
    }

    /**
     * Statistical monthly spent for annual view.
     */
    private double[] getMonthlySpent(long periodStart, long periodEnd) {
        double[] result = new double[12];
        try {
            List<Bill> bills = billDao.getBillsByCategoryInRange(
                    userId, currentCatCloudId, currentBillType(), periodStart, periodEnd);
            if (bills == null) return result;

            for (Bill b : bills) {
                if (b.isExcludeBudget() || b.getBillTime() == null) continue;
                Calendar c = Calendar.getInstance();
                c.setTime(b.getBillTime());
                int month = c.get(Calendar.MONTH); // 0-11
                if (month >= 0 && month < 12) {
                    result[month] += b.getAmount();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to aggregate monthly spent: " + e.getMessage());
        }
        return result;
    }

    /**
     * Build day labels for month view.
     */
    private String[] buildDayLabels(int days, long startTime) {
        String[] labels = new String[days];
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startTime);
        for (int i = 0; i < days; i++) {
            labels[i] = i % 5 == 0
                    ? (day.get(Calendar.MONTH) + 1) + "." + day.get(Calendar.DAY_OF_MONTH)
                    : "";
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return labels;
    }

    private static void resetToStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int currentBillType() {
        return currentBudget != null
                && Budget.TYPE_INCOME.equals(currentBudget.getTransactionType()) ? 1 : 0;
    }

    /**
     * Refresh all data.
     */
    public void refresh() {
        loadBills();
        loadTrendData(trendTypeLive.getValue());
    }

    /**
     * Delete current category budget.
     */
    public void deleteCategoryBudget() {
        if (currentBudget != null) {
            repo.markDeleteById(currentBudget.getId());
        }
    }
}
