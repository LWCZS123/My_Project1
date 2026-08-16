package com.example.my_project1.utils;

import com.example.my_project1.data.model.budget.Budget;

import java.util.Calendar;
import java.util.Locale;

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
        return getPeriodRange(period, 1);
    }

    public static long[] getPeriodRange(int period, int startDay) {
        return getPeriodRange(period, startDay, Calendar.getInstance());
    }

    public static long[] getPeriodRange(int period, int startDay, Calendar baseDate) {
        Calendar start = (Calendar) baseDate.clone();
        Calendar end   = (Calendar) baseDate.clone();
        resetToStartOfDay(start);
        resetToEndOfDay(end);

        switch (period) {
            case Budget.PERIOD_DAY:
                break;

            case Budget.PERIOD_WEEK:
                // 周预算固定从周日开始（符合截图展示）
                start.setFirstDayOfWeek(Calendar.SUNDAY);
                int dow = start.get(Calendar.DAY_OF_WEEK);
                int offset = Calendar.SUNDAY - dow;
                start.add(Calendar.DAY_OF_MONTH, offset);

                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.DAY_OF_MONTH, 6);
                resetToEndOfDay(end);
                break;

            case Budget.PERIOD_MONTH:
                if (startDay == 1) {
                    start.set(Calendar.DAY_OF_MONTH, 1);
                    end.set(Calendar.DAY_OF_MONTH,
                            start.getActualMaximum(Calendar.DAY_OF_MONTH));
                } else {
                    int today = start.get(Calendar.DAY_OF_MONTH);
                    if (today >= startDay) {
                        start.set(Calendar.DAY_OF_MONTH, startDay);
                        end.setTimeInMillis(start.getTimeInMillis());
                        end.add(Calendar.MONTH, 1);
                        end.add(Calendar.DAY_OF_MONTH, -1);
                    } else {
                        start.add(Calendar.MONTH, -1);
                        start.set(Calendar.DAY_OF_MONTH, startDay);
                        end.setTimeInMillis(start.getTimeInMillis());
                        end.add(Calendar.MONTH, 1);
                        end.add(Calendar.DAY_OF_MONTH, -1);
                    }
                }
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
    public static String getPeriodDateRange(int period, int startDay) {
        return getPeriodDateRange(period, startDay, Calendar.getInstance());
    }

    public static String getPeriodDateRange(int period, int startDay, Calendar baseDate) {
        long[] range = getPeriodRange(period, startDay, baseDate);
        Calendar s = Calendar.getInstance();
        Calendar e = Calendar.getInstance();
        s.setTimeInMillis(range[0]);
        e.setTimeInMillis(range[1]);

        switch (period) {
            case Budget.PERIOD_DAY:
                return String.format("%d.%d",
                        s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH));
            case Budget.PERIOD_WEEK:
            case Budget.PERIOD_MONTH:
                if (s.get(Calendar.YEAR) != e.get(Calendar.YEAR)) {
                    return String.format("%d.%d.%d-%d.%d.%d",
                            s.get(Calendar.YEAR), s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH),
                            e.get(Calendar.YEAR), e.get(Calendar.MONTH) + 1, e.get(Calendar.DAY_OF_MONTH));
                }
                return String.format("%d.%d-%d.%d",
                        s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.MONTH) + 1, e.get(Calendar.DAY_OF_MONTH));
            case Budget.PERIOD_YEAR:
                return s.get(Calendar.YEAR) + "年";
            default:
                return "";
        }
    }

    /**
     * 格式化周范围显示，例如 "2026年8月10日 - 16日" 或 "8月31日 - 9月6日"
     */
    public static String formatWeekRange(long startMs, long endMs) {
        Calendar s = Calendar.getInstance();
        Calendar e = Calendar.getInstance();
        s.setTimeInMillis(startMs);
        e.setTimeInMillis(endMs);

        int sYear = s.get(Calendar.YEAR);
        int eYear = e.get(Calendar.YEAR);
        int sMonth = s.get(Calendar.MONTH) + 1;
        int eMonth = e.get(Calendar.MONTH) + 1;
        int sDay = s.get(Calendar.DAY_OF_MONTH);
        int eDay = e.get(Calendar.DAY_OF_MONTH);

        if (sYear != eYear) {
            return String.format(Locale.getDefault(), "%d年%d月%d日 - %d年%d月%d日",
                    sYear, sMonth, sDay, eYear, eMonth, eDay);
        } else if (sMonth != eMonth) {
            return String.format(Locale.getDefault(), "%d年%d月%d日 - %d月%d日",
                    sYear, sMonth, sDay, eMonth, eDay);
        } else {
            return String.format(Locale.getDefault(), "%d年%d月%d日 - %d日",
                    sYear, sMonth, sDay, eDay);
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