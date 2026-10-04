package com.example.my_project1.ui.viewmodel.billvm;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.data.repository.bill.BillRepository;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 首页顶部概览数据计算器
 * -------------------------------------------------------
 * 职责：
 * 1. 负责计算首页顶部概览卡片所需的总资产、总负债、今日变化、月度收支、年度/全量收支以及周结余。
 * 2. 优化：不再将整月的账单实体加载到内存中进行遍历，而是通过 SQLite 的聚合函数直接查询统计结果。
 * 3. 各种汇总计算均在后台计算线程执行，计算完成后返回 Immutable 的 HeaderUiModel。
 */
public class HeaderCalculator {

    private String lastFingerprint = "";

    /**
     * 计算首页 Header 数据
     *
     * @param userId     当前用户ID
     * @param repository 账单 Repository
     * @param accounts   当前所有账户列表
     * @return 顶部概览 UI 模型
     */
    public HeaderUiModel calculateHeader(String userId, BillRepository repository, List<Account> accounts) {
        if (userId == null) {
            return new HeaderUiModel("¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00");
        }

        // 1. 从账户列表中计算总资产与总负债
        double assets = 0.0;
        double liabilities = 0.0;
        if (accounts != null) {
            for (Account acc : accounts) {
                // 排除标记为已删除的账户
                if (acc.getSyncState() == SyncState.TO_DELETE) {
                    continue;
                }
                if (acc.isCredit()) {
                    liabilities += acc.getBalance();
                } else {
                    assets += acc.getBalance();
                }
            }
        }

        // 2. 通过 SQLite 聚合查询全量收支统计
        SearchSummary totalSummary = repository.getUserBillSummarySync(userId);
        double totalExpense = totalSummary != null ? totalSummary.getExpenseTotal() : 0.0;
        double totalIncome = totalSummary != null ? totalSummary.getIncomeTotal() : 0.0;

        // 3. 通过 SQLite 聚合查询本月收支统计
        Date[] monthRange = getCurrentMonthRange();
        Date monthEndExclusive = new Date(monthRange[1].getTime() + 1L);
        SearchSummary monthSummary = repository.getBillSummaryInRangeSync(userId, monthRange[0], monthEndExclusive);
        double monthlyExpense = monthSummary != null ? monthSummary.getExpenseTotal() : 0.0;
        double monthlyIncome = monthSummary != null ? monthSummary.getIncomeTotal() : 0.0;

        // 4. 通过 SQLite 聚合查询本周收支统计
        Date[] weekRange = getCurrentWeekRange();
        Date weekEndExclusive = new Date(weekRange[1].getTime() + 1L);
        SearchSummary weeklySummary = repository.getBillSummaryInRangeSync(userId, weekRange[0], weekEndExclusive);
        double weeklyExpense = weeklySummary != null ? weeklySummary.getExpenseTotal() : 0.0;
        double weeklyIncome = weeklySummary != null ? weeklySummary.getIncomeTotal() : 0.0;

        // 5. 通过 SQLite 聚合查询今日收支统计
        Date[] todayRange = getTodayRange();
        Date todayEndExclusive = new Date(todayRange[1].getTime() + 1L);
        SearchSummary todaySummary = repository.getBillSummaryInRangeSync(userId, todayRange[0], todayEndExclusive);
        double todayIncome = todaySummary != null ? todaySummary.getIncomeTotal() : 0.0;
        double todayExpense = todaySummary != null ? todaySummary.getExpenseTotal() : 0.0;
        double todayChange = todayIncome - todayExpense;

        // 6. 格式化金额字符串
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String changePrefix = todayChange >= 0 ? "+ ¥" : "- ¥";

        return new HeaderUiModel(
                "¥" + df.format(assets - liabilities),
                changePrefix + df.format(Math.abs(todayChange)),
                "¥" + df.format(assets),
                "¥" + df.format(liabilities),
                "¥" + df.format(monthlyIncome),
                "¥" + df.format(totalIncome),
                "¥" + df.format(monthlyExpense),
                "¥" + df.format(totalExpense),
                "¥" + df.format(weeklyIncome - weeklyExpense)
        );
    }

    /**
     * 判断 Header 关联的数据是否未发生改变（防止重复触发 UI 刷新）
     *
     * @param accounts  账户列表
     * @param totalSum  全量汇总
     * @param monthSum  当月汇总
     * @return true 表示数据无变化，无需重新渲染 Header
     */
    public boolean isHeaderDataUnchanged(List<Account> accounts, SearchSummary totalSum, SearchSummary monthSum) {
        StringBuilder sb = new StringBuilder();

        if (totalSum != null) {
            sb.append(totalSum.getIncomeTotal()).append("_")
              .append(totalSum.getExpenseTotal()).append("_")
              .append(totalSum.getBillCount()).append("_");
        }
        if (monthSum != null) {
            sb.append(monthSum.getIncomeTotal()).append("_")
              .append(monthSum.getExpenseTotal()).append("_");
        }
        if (accounts != null) {
            sb.append(accounts.size()).append("_");
            for (Account acc : accounts) {
                sb.append(acc.getObjectId()).append("_")
                  .append(acc.getBalance()).append("_")
                  .append(acc.getSyncState()).append(",");
            }
        }

        String newFingerprint = sb.toString();
        if (newFingerprint.equals(lastFingerprint)) {
            return true;
        }
        lastFingerprint = newFingerprint;
        return false;
    }

    /**
     * 获取当月起始与结束时间点范围
     */
    public Date[] getCurrentMonthRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();

        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);      c.set(Calendar.MILLISECOND, 999);
        Date end = c.getTime();
        return new Date[]{start, end};
    }

    /**
     * 获取本周起始与结束时间点范围（每周一为开始）
     */
    public Date[] getCurrentWeekRange() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();

        c.add(Calendar.DAY_OF_WEEK, 6);
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);      c.set(Calendar.MILLISECOND, 999);
        Date end = c.getTime();
        return new Date[]{start, end};
    }

    /**
     * 获取今日起始与结束时间点范围
     */
    public Date[] getTodayRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();

        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);      c.set(Calendar.MILLISECOND, 999);
        Date end = c.getTime();
        return new Date[]{start, end};
    }
}
