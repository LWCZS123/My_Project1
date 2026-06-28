package com.example.my_project1.ui.viewmodel.budget;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
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
 * 分类预算详情页的数据层，提供：
 *   1. 当前分类预算信息（总额 / 已用 / 剩余）
 *   2. 该分类在当前预算周期内的账单列表
 *   3. 趋势折线图数据（周 / 月 / 年视图）
 *
 * 时间范围说明：
 *   账单列表和已用金额使用通过 BudgetPeriodHelper 实时计算的时间范围，
 *   而非直接读取 Budget 对象中存储的 startTime/endTime。
 *   原因：天/周预算在 BudgetResetWorker 尚未触发时，存储的时间窗口可能
 *   仍是上一个周期的旧值，直接使用会导致账单查询为空。
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
     * 初始化，传入分类预算对象并加载数据。
     * 在 Activity.onCreate() 中调用一次。
     *
     * 时间范围计算规则：
     *   - PERIOD_MONTH / PERIOD_YEAR：使用 Budget 中存储的 startTime/endTime，
     *     月/年周期的时间窗口在预算创建时已由 BudgetPeriodHelper 写入，不会过期。
     *   - PERIOD_DAY / PERIOD_WEEK：始终以当前时间重新计算，
     *     避免存储值落后于当前周期导致账单查询为空。
     */
    public void init(Budget budget, String catCloudId) {
        this.currentBudget     = budget;
        this.currentCatCloudId = catCloudId;
        budgetAmountLive.setValue(budget.getAmount());

        int period = budget.getPeriod();
        if (period == Budget.PERIOD_DAY || period == Budget.PERIOD_WEEK) {
            // 天/周预算：实时重算当前周期范围
            long[] range = BudgetPeriodHelper.getPeriodRange(period);
            effectiveStartTime = range[0];
            effectiveEndTime   = range[1];
        } else {
            // 月/年预算：直接使用存储的稳定值
            effectiveStartTime = budget.getStartTime();
            effectiveEndTime   = budget.getEndTime();
        }

        loadBills();
        loadTrendData(trendTypeLive.getValue());
    }

    // ════════════════════════════════════════════════════════
    //  账单加载
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
                Log.e(TAG, "加载账单失败：" + e.getMessage());
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  趋势图数据
    // ════════════════════════════════════════════════════════

    /**
     * 切换趋势图视图类型并重新加载数据。
     * @param trendType TREND_WEEK / TREND_MONTH / TREND_YEAR
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
            Calendar cal = Calendar.getInstance();
            long   periodStart;
            long   periodEnd;
            int    points;
            String[] labels;

            switch (type) {
                case TREND_WEEK: {
                    // 本周一 00:00 到本周日 23:59:59
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    periodStart = cal.getTimeInMillis();
                    points      = 7;
                    periodEnd   = periodStart + 7L * 86_400_000L - 1;
                    labels = new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
                    break;
                }
                case TREND_YEAR: {
                    // 本年 1/1 到 12/31
                    int year = cal.get(Calendar.YEAR);
                    cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    periodStart = cal.getTimeInMillis();
                    boolean leap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
                    points    = leap ? 366 : 365;
                    periodEnd = periodStart + (long) points * 86_400_000L - 1;
                    // 年视图按月聚合，展示 12 个点
                    labels = new String[]{"1月","2月","3月","4月","5月","6月",
                            "7月","8月","9月","10月","11月","12月"};
                    break;
                }
                default: { // TREND_MONTH
                    int year  = cal.get(Calendar.YEAR);
                    int month = cal.get(Calendar.MONTH);
                    cal.set(year, month, 1, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    periodStart = cal.getTimeInMillis();
                    int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    points    = daysInMonth;
                    periodEnd = periodStart + (long) points * 86_400_000L - 1;
                    labels = buildDayLabels(daysInMonth,
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
                    break;
                }
            }

            double[] result;
            if (TREND_YEAR.equals(type)) {
                // 年视图按月聚合，输出 12 个点
                result = getMonthlySpent(periodStart, periodEnd);
            } else {
                // 周/月视图按天统计，每天独立（非累计）
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
     * 按天统计各天的独立支出，用于周/月视图折线图。
     * 返回长度为 days 的数组，index i 对应第 i+1 天的支出。
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
            Log.e(TAG, "统计每日支出失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 按月聚合全年支出，用于年视图折线图（12 个点）。
     * 返回长度为 12 的数组，index i 对应第 i+1 月的支出。
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
            Log.e(TAG, "统计月度支出失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 生成月视图的 X 轴日期标签。
     * 每 5 天显示一个标签（格式：月.日），其余为空字符串，避免标签拥挤。
     */
    private String[] buildDayLabels(int days, int year, int month) {
        String[] labels = new String[days];
        for (int i = 0; i < days; i++) {
            labels[i] = ((i + 1) % 5 == 1) ? (month + "." + (i + 1)) : "";
        }
        return labels;
    }

    /**
     * 外部刷新入口，账单更新后可调用此方法重新加载全部数据。
     */
    public void refresh() {
        loadBills();
        loadTrendData(trendTypeLive.getValue());
    }
}