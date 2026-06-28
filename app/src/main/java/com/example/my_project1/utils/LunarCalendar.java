package com.example.my_project1.utils;

/**
 * 中国农历工具类
 * 支持1900-2100年的农历转换、节气、传统节日
 */
public class LunarCalendar {

    // 农历年份信息表 (1900-2100)
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
            0x0d520
    };

    // 24节气信息
    private static final String[] SOLAR_TERMS = {
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
            "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
            "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
            "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    };

    // 节气基准数据
    private static final int[] SOLAR_TERM_BASE = {
            4, 19, 3, 18, 4, 19, 4, 19, 4, 20, 4, 20,
            6, 22, 6, 22, 6, 22, 7, 22, 6, 21, 6, 21
    };

    // 农历月份名称
    private static final String[] LUNAR_MONTHS = {
            "正月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "冬月", "腊月"
    };

    // 农历日期名称
    private static final String[] LUNAR_DAYS = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    /**
     * 获取农历年的总天数
     */
    private static int getLunarYearDays(int year) {
        int sum = 348;
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            sum += ((LUNAR_INFO[year - 1900] & i) != 0 ? 1 : 0);
        }
        return sum + getLeapDays(year);
    }

    /**
     * 获取农历年闰月的天数
     */
    private static int getLeapDays(int year) {
        if (getLeapMonth(year) != 0) {
            return ((LUNAR_INFO[year - 1900] & 0x10000) != 0 ? 30 : 29);
        }
        return 0;
    }

    /**
     * 获取农历年的闰月月份，没有闰月返回0
     */
    private static int getLeapMonth(int year) {
        return (int) (LUNAR_INFO[year - 1900] & 0xf);
    }

    /**
     * 获取农历月的天数
     */
    private static int getMonthDays(int year, int month) {
        return ((LUNAR_INFO[year - 1900] & (0x10000 >> month)) != 0 ? 30 : 29);
    }

    /**
     * 公历转农历
     */
    public static LunarDate solarToLunar(int year, int month, int day) {
        if (year < 1900 || year > 2100) {
            return new LunarDate(year, month, day, false, "");
        }

        // 计算距离1900年1月31日的天数
        int offset = 0;
        for (int i = 1900; i < year; i++) {
            offset += isLeapYear(i) ? 366 : 365;
        }

        for (int i = 1; i < month; i++) {
            offset += getDaysInMonth(year, i);
        }
        offset += day - 1;

        // 1900年1月31日是农历1900年正月初一
        offset -= 30;

        // 计算农历年份
        int lunarYear = 1900;
        int daysInYear;
        while (offset > 0) {
            daysInYear = getLunarYearDays(lunarYear);
            if (offset < daysInYear) break;
            offset -= daysInYear;
            lunarYear++;
        }

        // 计算农历月份
        int leapMonth = getLeapMonth(lunarYear);
        boolean isLeap = false;
        int lunarMonth = 1;
        int daysInMonth;

        for (lunarMonth = 1; lunarMonth <= 12; lunarMonth++) {
            if (leapMonth > 0 && lunarMonth == leapMonth + 1 && !isLeap) {
                lunarMonth--;
                isLeap = true;
                daysInMonth = getLeapDays(lunarYear);
            } else {
                daysInMonth = getMonthDays(lunarYear, lunarMonth);
            }

            if (offset < daysInMonth) break;
            offset -= daysInMonth;

            if (isLeap && lunarMonth == leapMonth + 1) {
                isLeap = false;
            }
        }

        int lunarDay = offset + 1;
        return new LunarDate(lunarYear, lunarMonth, lunarDay, isLeap, getLunarDayString(lunarDay));
    }

    /**
     * 判断是否是闰年
     */
    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    /**
     * 获取公历月份的天数
     */
    private static int getDaysInMonth(int year, int month) {
        int[] days = {31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return days[month - 1];
    }

    /**
     * 获取农历日期字符串
     */
    public static String getLunarDayString(int day) {
        if (day >= 1 && day <= 30) {
            return LUNAR_DAYS[day - 1];
        }
        return "";
    }

    /**
     * 获取节气
     */
    public static String getSolarTerm(int year, int month, int day) {
        if (month < 1 || month > 12) return "";

        int index = (month - 1) * 2;
        int d1 = getSolarTermDay(year, index);
        int d2 = getSolarTermDay(year, index + 1);

        if (day == d1) {
            return SOLAR_TERMS[index];
        } else if (day == d2) {
            return SOLAR_TERMS[index + 1];
        }
        return "";
    }

    /**
     * 计算节气日期
     */
    private static int getSolarTermDay(int year, int index) {
        int base = SOLAR_TERM_BASE[index];
        int y = year % 100;

        if (year >= 2000) {
            y = y + 100;
        }

        int d = (int)(y * 0.2422 + base) - (int)((y - 1) / 4.0);

        // 特殊年份修正
        if (index == 0 && year == 1982) d = 4;
        if (index == 3 && year == 2008) d = 19;

        return d;
    }

    /**
     * 获取传统节日
     */
    public static String getFestival(int lunarMonth, int lunarDay, boolean isLeap) {
        if (isLeap) return "";

        if (lunarMonth == 1 && lunarDay == 1) return "春节";
        if (lunarMonth == 1 && lunarDay == 15) return "元宵";
        if (lunarMonth == 2 && lunarDay == 2) return "龙抬头";
        if (lunarMonth == 5 && lunarDay == 5) return "端午";
        if (lunarMonth == 7 && lunarDay == 7) return "七夕";
        if (lunarMonth == 7 && lunarDay == 15) return "中元";
        if (lunarMonth == 8 && lunarDay == 15) return "中秋";
        if (lunarMonth == 9 && lunarDay == 9) return "重阳";
        if (lunarMonth == 10 && lunarDay == 1) return "寒衣";
        if (lunarMonth == 10 && lunarDay == 15) return "下元";
        if (lunarMonth == 12 && lunarDay == 8) return "腊八";
        if (lunarMonth == 12 && lunarDay == 23) return "小年";

        return "";
    }

    /**
     * 获取公历节日
     */
    public static String getSolarFestival(int month, int day) {
        if (month == 1 && day == 1) return "元旦";
        if (month == 2 && day == 14) return "情人节";
        if (month == 3 && day == 8) return "妇女节";
        if (month == 3 && day == 12) return "植树节";
        if (month == 4 && day == 1) return "愚人节";
        if (month == 5 && day == 1) return "劳动节";
        if (month == 5 && day == 4) return "青年节";
        if (month == 6 && day == 1) return "儿童节";
        if (month == 7 && day == 1) return "建党节";
        if (month == 8 && day == 1) return "建军节";
        if (month == 9 && day == 10) return "教师节";
        if (month == 10 && day == 1) return "国庆节";
        if (month == 12 && day == 25) return "圣诞节";

        return "";
    }

    /**
     * 获取完整的显示文本（优先级：公历节日 > 农历节日 > 节气 > 农历日期）
     */
    public static String getDisplayText(int year, int month, int day) {
        // 公历节日优先
        String solarFestival = getSolarFestival(month, day);
        if (!solarFestival.isEmpty()) {
            return solarFestival;
        }

        // 节气次之
        String solarTerm = getSolarTerm(year, month, day);
        if (!solarTerm.isEmpty()) {
            return solarTerm;
        }

        // 农历节日
        LunarDate lunar = solarToLunar(year, month, day);
        String festival = getFestival(lunar.month, lunar.day, lunar.isLeap);
        if (!festival.isEmpty()) {
            return festival;
        }

        // 普通农历日期
        return lunar.dayString;
    }

    /**
     * 农历日期数据类
     */
    public static class LunarDate {
        public int year;
        public int month;
        public int day;
        public boolean isLeap;
        public String dayString;

        public LunarDate(int year, int month, int day, boolean isLeap, String dayString) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.isLeap = isLeap;
            this.dayString = dayString;
        }
    }
}