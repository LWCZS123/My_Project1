package com.example.my_project1.utils;

import com.example.my_project1.data.model.budget.Budget;

import java.util.Calendar;

/**
 * BudgetPeriodHelper
 * ─────────────────────────────────────────────────────────────
 * 根据 period 计算当前周期的开始 / 结束时间戳（毫秒）。
 *
 *  0 = 日  →  今日 00:00:00 ~ 23:59:59
 *  1 = 周  →  本周一 00:00:00 ~ 本周日 23:59:59
 *  2 = 月  →  本月 1 日 00:00:00 ~ 最后一天 23:59:59
 *  3 = 年  →  本年 1月1日 00:00:00 ~ 12月31日 23:59:59
 */
public class BudgetPeriodHelper {

    /** 返回 [startTime, endTime] 毫秒时间戳数组 */
    public static long[] getPeriodRange(int period) {
        Calendar start = Calendar.getInstance();
        Calendar end   = Calendar.getInstance();
        resetToStartOfDay(start);
        resetToEndOfDay(end);

        switch (period) {
            case Budget.PERIOD_DAY:
                // 已经是今天开始/结束
                break;

            case Budget.PERIOD_WEEK:
                // 本周一
                int dow = start.get(Calendar.DAY_OF_WEEK);
                int offset = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
                start.add(Calendar.DAY_OF_MONTH, offset);
                end.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                if (end.before(start)) end.add(Calendar.WEEK_OF_YEAR, 1);
                resetToEndOfDay(end);
                break;

            case Budget.PERIOD_MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                end.set(Calendar.DAY_OF_MONTH,
                        end.getActualMaximum(Calendar.DAY_OF_MONTH));
                resetToEndOfDay(end);
                break;

            case Budget.PERIOD_YEAR:
                start.set(Calendar.DAY_OF_YEAR, 1);
                end.set(Calendar.MONTH, Calendar.DECEMBER);
                end.set(Calendar.DAY_OF_MONTH, 31);
                resetToEndOfDay(end);
                break;
        }

        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    /** 返回周期的显示文字，如 "3.1-3.31" */
    public static String getPeriodDateRange(int period) {
        long[] range = getPeriodRange(period);
        Calendar s = Calendar.getInstance();
        Calendar e = Calendar.getInstance();
        s.setTimeInMillis(range[0]);
        e.setTimeInMillis(range[1]);

        switch (period) {
            case Budget.PERIOD_DAY:
                return String.format("%d.%d",
                        s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH));
            case Budget.PERIOD_WEEK:
                return String.format("%d.%d-%d.%d",
                        s.get(Calendar.MONTH)+1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.MONTH)+1, e.get(Calendar.DAY_OF_MONTH));
            case Budget.PERIOD_MONTH:
                return String.format("%d.%d-%d.%d",
                        s.get(Calendar.MONTH)+1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.MONTH)+1, e.get(Calendar.DAY_OF_MONTH));
            case Budget.PERIOD_YEAR:
                return s.get(Calendar.YEAR) + "年";
            default:
                return "";
        }
    }

    private static void resetToStartOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static void resetToEndOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
    }
}