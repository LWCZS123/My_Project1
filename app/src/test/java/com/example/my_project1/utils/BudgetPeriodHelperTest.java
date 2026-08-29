package com.example.my_project1.utils;

import static org.junit.Assert.assertEquals;

import com.example.my_project1.data.model.budget.Budget;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class BudgetPeriodHelperTest {

    private TimeZone originalTimeZone;

    @Before
    public void rememberTimeZone() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void weekRangeUsesSundayAndCrossesYearWithoutWeekNumberConversion() {
        Calendar base = calendar(2026, Calendar.JANUARY, 1);
        long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, 1, base);

        assertDate(range[0], 2025, Calendar.DECEMBER, 28, 0, 0, 0, 0);
        assertDate(range[1], 2026, Calendar.JANUARY, 3, 23, 59, 59, 999);
        assertEquals(7, BudgetPeriodHelper.getCalendarDayCount(range[0], range[1]));
    }

    @Test
    public void monthStartDay31IsClampedWithoutRollingIntoNextMonth() {
        Calendar base = calendar(2026, Calendar.FEBRUARY, 28);
        long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_MONTH, 31, base);

        assertDate(range[0], 2026, Calendar.FEBRUARY, 28, 0, 0, 0, 0);
        assertDate(range[1], 2026, Calendar.MARCH, 30, 23, 59, 59, 999);
    }

    @Test
    public void leapYearContains366CalendarDays() {
        long[] range = BudgetPeriodHelper.getPeriodRange(
                Budget.PERIOD_YEAR, 1, calendar(2024, Calendar.JULY, 10));

        assertEquals(366, BudgetPeriodHelper.getCalendarDayCount(range[0], range[1]));
        assertDate(range[1], 2024, Calendar.DECEMBER, 31, 23, 59, 59, 999);
    }

    @Test
    public void dayCountUsesCalendarDaysAcrossDstChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        long[] range = BudgetPeriodHelper.getPeriodRange(
                Budget.PERIOD_MONTH, 1, calendar(2026, Calendar.MARCH, 15));

        assertEquals(31, BudgetPeriodHelper.getCalendarDayCount(range[0], range[1]));
    }

    private static Calendar calendar(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar;
    }

    private static void assertDate(long millis, int year, int month, int day,
                                   int hour, int minute, int second, int millisecond) {
        Calendar actual = Calendar.getInstance();
        actual.setTimeInMillis(millis);
        assertEquals(year, actual.get(Calendar.YEAR));
        assertEquals(month, actual.get(Calendar.MONTH));
        assertEquals(day, actual.get(Calendar.DAY_OF_MONTH));
        assertEquals(hour, actual.get(Calendar.HOUR_OF_DAY));
        assertEquals(minute, actual.get(Calendar.MINUTE));
        assertEquals(second, actual.get(Calendar.SECOND));
        assertEquals(millisecond, actual.get(Calendar.MILLISECOND));
    }
}
