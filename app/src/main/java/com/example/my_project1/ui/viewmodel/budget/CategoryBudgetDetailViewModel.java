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
 *   Bills and spent amount use real-time calculated range from BudgetPeriodHelper.
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

        int period = budget.getPeriod();
        int year = budget.getYear();
        int month = budget.getMonth(); // Stores weekNum for week budget
        
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.SUNDAY);
        cal.set(Calendar.YEAR, year);
        
        if (period == Budget.PERIOD_WEEK) {
            cal.set(Calendar.WEEK_OF_YEAR, month);
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        } else if (period == Budget.PERIOD_MONTH) {
            cal.set(Calendar.MONTH, month > 0 ? month - 1 : 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else if (period == Budget.PERIOD_YEAR) {
            cal.set(Calendar.MONTH, 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }

        // Calculate effective range consistently with main screen
        int startDay = com.example.my_project1.utils.BudgetConfig.getStartDay(getApplication());
        long[] range = BudgetPeriodHelper.getPeriodRange(period, startDay, cal);
        effectiveStartTime = range[0];
        effectiveEndTime   = range[1];

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
                        userId, currentCatCloudId, start, end);
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

        AppExecutors.get().diskIO().execute(() -> {
            // Base date based on budget year/month to align trend with budget period
            Calendar cal = Calendar.getInstance();
            cal.setFirstDayOfWeek(Calendar.SUNDAY);
            cal.set(Calendar.YEAR, currentBudget.getYear());
            
            if (currentBudget.isMonthType()) {
                cal.set(Calendar.MONTH, currentBudget.getMonth() - 1);
            } else if (Budget.TYPE_WEEK.equals(currentBudget.getBudgetType())) {
                cal.set(Calendar.WEEK_OF_YEAR, currentBudget.getMonth());
            }

            long   periodStart;
            long   periodEnd;
            int    points;
            String[] labels;

            switch (type) {
                case TREND_WEEK: {
                    // Sunday to Saturday of the budget week
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    periodStart = cal.getTimeInMillis();
                    points      = 7;
                    cal.add(Calendar.DAY_OF_YEAR, 7);
                    periodEnd   = cal.getTimeInMillis() - 1;
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
                    // 1st to last day of the budget month
                    int year  = cal.get(Calendar.YEAR);
                    int month = cal.get(Calendar.MONTH);
                    cal.set(year, month, 1, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    periodStart = cal.getTimeInMillis();
                    int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    points    = daysInMonth;
                    
                    labels = buildDayLabels(daysInMonth, year, month + 1);

                    cal.add(Calendar.MONTH, 1);
                    periodEnd = cal.getTimeInMillis() - 1;
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
                    userId, currentCatCloudId, periodStart, periodEnd);
            if (bills == null) return result;

            long msPerDay = 86_400_000L;
            for (Bill b : bills) {
                if (b.isExcludeBudget()) continue;
                long billTime = b.getBillTime() != null ? b.getBillTime().getTime() : 0;
                int idx = (int) ((billTime - periodStart) / msPerDay);
                if (idx >= 0 && idx < days) {
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
                    userId, currentCatCloudId, periodStart, periodEnd);
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
    private String[] buildDayLabels(int days, int year, int month) {
        String[] labels = new String[days];
        for (int i = 0; i < days; i++) {
            labels[i] = ((i + 1) % 5 == 1) ? (month + "." + (i + 1)) : "";
        }
        return labels;
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
