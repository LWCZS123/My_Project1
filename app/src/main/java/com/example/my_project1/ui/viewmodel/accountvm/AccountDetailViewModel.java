package com.example.my_project1.ui.viewmodel.accountvm;

import android.app.Application;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.PagingLiveData;

import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.BillWithBalance;
import com.example.my_project1.data.model.bill.DailyStat;
import com.example.my_project1.data.model.bill.MonthlyStat;
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.utils.AppExecutors;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cn.bmob.v3.BmobUser;

public class AccountDetailViewModel extends AndroidViewModel {

    private final BillDao billDao;
    private final AccountDao accountDao;
    private final String userId;

    private final MutableLiveData<String> accountId = new MutableLiveData<>();
    private final MutableLiveData<Long> localAccountId = new MutableLiveData<>(-1L);
    private final MutableLiveData<DateRange> dateRange = new MutableLiveData<>(new DateRange(null, null));
    private final Set<String> collapsedMonths = new HashSet<>();

    private final MutableLiveData<Long> refreshTrigger = new MutableLiveData<>(System.currentTimeMillis());
    public final LiveData<Account> account;
    public final LiveData<SearchSummary> stats;
    public final LiveData<List<BillDao.CategorySummary>> expenseSummary;
    public final LiveData<List<BillDao.CategorySummary>> incomeSummary;
    public final LiveData<List<MonthlyStat>> monthlyStats;
    public final LiveData<List<DailyStat>> dailyStats;
    
    public final LiveData<PagingData<AccountBillUiModel>> billPagingData;

    private static final int PAGE_SIZE = 50;

    private static class AccountTarget {
        final String cloudId;
        final long localId;

        AccountTarget(String cloudId, long localId) {
            this.cloudId = cloudId;
            this.localId = localId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AccountTarget that = (AccountTarget) o;
            return localId == that.localId && Objects.equals(cloudId, that.cloudId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cloudId, localId);
        }
    }

    private final ThreadLocal<SimpleDateFormat> monthKeyFmt = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("yyyy-MM", Locale.getDefault()); }
    };
    private final ThreadLocal<SimpleDateFormat> dayKeyFmt = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()); }
    };
    private final ThreadLocal<SimpleDateFormat> titleFmt = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("yyyy年MM月", Locale.getDefault()); }
    };
    private final ThreadLocal<SimpleDateFormat> rangeFmt = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("MM月dd日", Locale.getDefault()); }
    };
    private final ThreadLocal<SimpleDateFormat> dayTitleFmt = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault()); }
    };
    private final ThreadLocal<DecimalFormat> moneyFmt = new ThreadLocal<DecimalFormat>() {
        @Override protected DecimalFormat initialValue() { return new DecimalFormat("#,##0.00"); }
    };

    public AccountDetailViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        billDao = db.billDao();
        accountDao = db.accountDao();
        userId = BmobUser.getCurrentUser() != null ? BmobUser.getCurrentUser().getObjectId() : null;

        LiveData<AccountTarget> targetSource = Transformations.switchMap(refreshTrigger, t ->
            Transformations.switchMap(accountId, id ->
                Transformations.map(localAccountId, localId -> new AccountTarget(id, localId))
            )
        );

        // 1. Account Info
        account = Transformations.switchMap(targetSource, target -> {
            if (target == null) return new MutableLiveData<>(null);
            return accountDao.getAccountLive(target.cloudId, target.localId);
        });

        // 2. Statistics
        stats = Transformations.switchMap(targetSource, target ->
            Transformations.switchMap(dateRange, range -> 
                billDao.getAccountStatsLive(userId, target.cloudId, target.localId, range.start, range.end))
        );

        // 3. Category Summaries
        expenseSummary = Transformations.switchMap(targetSource, target ->
            Transformations.switchMap(dateRange, range -> 
                billDao.getAccountCategorySummaryLive(userId, target.cloudId, target.localId, 0, range.start, range.end))
        );

        incomeSummary = Transformations.switchMap(targetSource, target ->
            Transformations.switchMap(dateRange, range -> 
                billDao.getAccountCategorySummaryLive(userId, target.cloudId, target.localId, 1, range.start, range.end))
        );

        // 4. Monthly & Daily Stats
        monthlyStats = Transformations.switchMap(targetSource, target ->
            billDao.getAccountMonthlyStatsLive(userId, target.cloudId, target.localId));
        
        dailyStats = Transformations.switchMap(targetSource, target ->
            billDao.getAccountDailyStatsLive(userId, target.cloudId, target.localId));

        // 5. Paging Data Flow
        LiveData<PagingData<BillWithBalance>> rawPaging = Transformations.switchMap(targetSource, target ->
            Transformations.switchMap(dateRange, range -> {
                Pager<Integer, BillWithBalance> pager = new Pager<>(
                    new PagingConfig(PAGE_SIZE, PAGE_SIZE, false),
                    () -> new AccountBillsPagingSource(billDao, accountDao, userId, target.cloudId, target.localId, range.start, range.end)
                );
                LiveData<PagingData<BillWithBalance>> paged = PagingLiveData.getLiveData(pager);
                return PagingLiveData.cachedIn(paged, androidx.lifecycle.ViewModelKt.getViewModelScope(this));
            })
        );

        // Combined data flow to ensure separators have access to latest stats
        LiveData<CombinedStats> combinedStatsSource = Transformations.switchMap(monthlyStats, mList ->
            Transformations.map(dailyStats, dList -> new CombinedStats(mList, dList))
        );

        billPagingData = Transformations.switchMap(combinedStatsSource, stats -> {
            Map<String, MonthlyStat> mStatMap = new HashMap<>();
            if (stats.monthly != null) {
                for (MonthlyStat s : stats.monthly) mStatMap.put(s.month, s);
            }
            Map<String, DailyStat> dStatMap = new HashMap<>();
            if (stats.daily != null) {
                for (DailyStat s : stats.daily) dStatMap.put(s.day, s);
            }
            
            return Transformations.map(rawPaging, pagingData -> {
                    Account currentAcc = account.getValue();
                    PagingData<AccountBillUiModel> mapped = PagingDataTransforms.map(pagingData, AppExecutors.get().computation(), 
                            bwb -> mapToUiModel(bwb.bill, currentAcc, bwb.balanceAfter));
                    
                    // Pass 1: Insert Month Headers
                    PagingData<AccountBillUiModel> monthPaged = PagingDataTransforms.insertSeparators(mapped, AppExecutors.get().computation(), 
                        (before, after) -> {
                            if (after == null) return null;
                            Date afterDate = after.originalBill.getBillTime();
                            if (afterDate == null) return null;
                            String afterMonth = monthKeyFmt.get().format(afterDate);
                            
                            if (before == null) {
                                return createMonthHeader(afterDate, mStatMap.get(afterMonth));
                            }
                            
                            Date beforeDate = before.originalBill.getBillTime();
                            if (beforeDate == null) return null;
                            String beforeMonth = monthKeyFmt.get().format(beforeDate);
                            if (!beforeMonth.equals(afterMonth)) {
                                return createMonthHeader(afterDate, mStatMap.get(afterMonth));
                            }
                            return null;
                        }
                    );

                    // Pass 2: Insert Day Headers (ensuring the first day of each month also has a header)
                    PagingData<AccountBillUiModel> withSeparators = PagingDataTransforms.insertSeparators(monthPaged, AppExecutors.get().computation(), 
                        (before, after) -> {
                            if (after == null || after.type != AccountBillUiModel.TYPE_BILL_ITEM) return null;
                            Date afterDate = after.originalBill.getBillTime();
                            if (afterDate == null) return null;
                            String afterDay = dayKeyFmt.get().format(afterDate);

                            if (before == null || before.type == AccountBillUiModel.TYPE_MONTH_HEADER) {
                                return createDayHeader(afterDate, dStatMap.get(afterDay));
                            }
                            
                            if (before.type == AccountBillUiModel.TYPE_BILL_ITEM) {
                                Date beforeDate = before.originalBill.getBillTime();
                                if (beforeDate == null) return null;
                                String beforeDay = dayKeyFmt.get().format(beforeDate);
                                if (!beforeDay.equals(afterDay)) {
                                    return createDayHeader(afterDate, dStatMap.get(afterDay));
                                }
                            }
                            return null;
                        }
                    );

                    return withSeparators;
                });
        });
    }

    private AccountBillUiModel createMonthHeader(Date date, MonthlyStat stat) {
        String monthKey = monthKeyFmt.get().format(date);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String startStr = rangeFmt.get().format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        String endStr = rangeFmt.get().format(cal.getTime());

        String inflow = "流入: ¥0.00";
        String outflow = "流出: ¥0.00";
        String billAmount = "账单金额：未出账";
        if (stat != null) {
            double totalIn = stat.incomeTotal + stat.transferInTotal;
            double totalOut = stat.expenseTotal + stat.transferOutTotal;
            inflow = "流入: ¥" + moneyFmt.get().format(totalIn);
            outflow = "流出: ¥" + moneyFmt.get().format(totalOut);
            billAmount = "账单金额: ¥" + moneyFmt.get().format(stat.expenseTotal - stat.incomeTotal);
        }

        return new AccountBillUiModel(
                AccountBillUiModel.TYPE_MONTH_HEADER,
                titleFmt.get().format(date),
                startStr + " - " + endStr,
                billAmount,
                inflow, 
                outflow,
                false,
                monthKey
        );
    }

    private AccountBillUiModel createDayHeader(Date date, DailyStat stat) {
        String monthKey = monthKeyFmt.get().format(date);
        String summary = "支出: ¥0.00 收入: ¥0.00";
        if (stat != null) {
            summary = "支出: ¥" + moneyFmt.get().format(stat.expenseTotal) + " 收入: ¥" + moneyFmt.get().format(stat.incomeTotal);
        }
        return new AccountBillUiModel(
                AccountBillUiModel.TYPE_DAY_HEADER,
                dayTitleFmt.get().format(date),
                summary,
                null, null, null, false, monthKey
        );
    }

    public void refresh() {
        refreshTrigger.setValue(System.currentTimeMillis());
    }

    public Set<String> getCollapsedMonths() {
        return new HashSet<>(collapsedMonths);
    }

    public void setMonthCollapsed(String monthKey, boolean collapsed) {
        if (collapsed) collapsedMonths.add(monthKey);
        else collapsedMonths.remove(monthKey);
    }

    public void setAccount(String id, long localId) {
        accountId.setValue(id);
        localAccountId.setValue(localId);
    }

    public void setDateRange(Date start, Date end) {
        dateRange.setValue(new DateRange(start, end));
    }

    private AccountBillUiModel mapToUiModel(Bill bill, Account acc, double balanceAfter) {
        if (bill == null) return null;
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String categoryName = bill.getCategoryName() != null ? bill.getCategoryName() : "";
        boolean isReimburse = bill.isReimbursable() || bill.isReimbursed() || categoryName.contains("报销");
        boolean isAccountCreation = categoryName.contains("账户创建") || categoryName.contains("创建账户") || categoryName.contains("余额调整");
        boolean isTransfer = bill.getType() == 2 || bill.getType() == 3 || categoryName.contains("转账") || categoryName.contains("还款");

        String prefix;
        int color;
        if (isReimburse || isAccountCreation) {
            prefix = "";
            color = Color.parseColor("#FF9800");
        } else if (isTransfer) {
            boolean isTargetAccount = false;
            if (acc != null) {
                if (acc.getObjectId() != null && !acc.getObjectId().isEmpty() && acc.getObjectId().equals(bill.getToAccountId())) {
                    isTargetAccount = true;
                } else if (acc.getId() > 0 && acc.getId() == bill.getToLocalAccountId()) {
                    isTargetAccount = true;
                }
            }
            prefix = isTargetAccount ? "+" : "-";
            color = Color.parseColor("#FF9800");
        } else if (bill.getType() == 1) {
            prefix = "+";
            color = Color.parseColor("#00C48C");
        } else {
            prefix = "-";
            color = Color.parseColor("#FF6B6B");
        }
        
        String timeStr = bill.getBillTime() != null ? timeFmt.format(bill.getBillTime()) : "--:--";
        StringBuilder timeNote = new StringBuilder(timeStr);
        if (bill.getRemark() != null && !bill.getRemark().isEmpty()) {
            timeNote.append(" · ").append(bill.getRemark());
        }
        if (bill.isReimbursable() || categoryName.contains("报销")) {
            timeNote.append(bill.isReimbursed() ? " · 已报销" : " · 未报销");
        }

        // 💡 动态余额逻辑：信用账户显示“欠款”，其他显示“余额”
        String label = (acc != null && acc.isCredit()) ? "欠款: " : "余额: ";
        String balanceStr = label + "¥" + moneyFmt.get().format(balanceAfter);

        double discount = bill.getDiscount();
        boolean hasDiscount = discount > 0;
        String originalAmountText = "";
        String discountText = "";
        if (hasDiscount) {
            double originalAmount = bill.getAmount() + discount;
            originalAmountText = "¥" + moneyFmt.get().format(originalAmount);
            discountText = String.format(Locale.getDefault(), "优惠¥%s", moneyFmt.get().format(discount));
        }

        List<com.example.my_project1.data.model.tag.TagEntity> tags = com.example.my_project1.data.database.AppDatabase.getInstance(getApplication())
                .billTagCrossRefDao().getTagsForBillSync(bill.getId());

        AccountBillUiModel m = new AccountBillUiModel(
                bill.getId(),
                bill.getObjectId(),
                bill.getCategoryName(),
                bill.getCategoryIconUrl(),
                timeNote.toString(),
                prefix + "¥" + moneyFmt.get().format(bill.getAmount()),
                color,
                balanceStr,
                bill,
                originalAmountText,
                discountText,
                hasDiscount
        );
        if (tags != null) {
            m.tags = tags;
        }
        if (bill.getBillTime() != null) {
            m.key = monthKeyFmt.get().format(bill.getBillTime());
        }
        return m;
    }

    public static class DateRange {
        public final Date start;
        public final Date end;
        public DateRange(Date start, Date end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class CombinedStats {
        final List<MonthlyStat> monthly;
        final List<DailyStat> daily;
        CombinedStats(List<MonthlyStat> m, List<DailyStat> d) {
            this.monthly = m;
            this.daily = d;
        }
    }
}
