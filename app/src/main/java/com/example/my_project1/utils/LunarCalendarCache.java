package com.example.my_project1.utils;

import android.util.LruCache;

/**
 * 农历计算缓存工具类
 *
 * 优化策略：
 * 1. LruCache缓存已计算的农历结果
 * 2. 减少重复的农历转换计算
 * 3. 内存占用控制在合理范围
 */
public class LunarCalendarCache {

    private static final int CACHE_SIZE = 500; // 缓存500天的数据

    // 农历文本缓存
    private static final LruCache<String, String> displayTextCache = new LruCache<>(CACHE_SIZE);

    // 阳历节日缓存
    private static final LruCache<String, String> solarFestivalCache = new LruCache<>(CACHE_SIZE);

    // 节气缓存
    private static final LruCache<String, String> solarTermCache = new LruCache<>(CACHE_SIZE);

    /**
     * 生成缓存key
     */
    private static String makeKey(int year, int month, int day) {
        return year + "-" + month + "-" + day;
    }

    /**
     * 获取农历显示文本（带缓存）
     */
    public static String getDisplayText(int year, int month, int day) {
        String key = makeKey(year, month, day);
        String cached = displayTextCache.get(key);

        if (cached != null) {
            return cached;
        }

        // 未缓存，计算并存储
        String result = LunarCalendar.getDisplayText(year, month, day);
        displayTextCache.put(key, result);
        return result;
    }

    /**
     * 获取阳历节日（带缓存）
     */
    public static String getSolarFestival(int month, int day) {
        String key = month + "-" + day;
        String cached = solarFestivalCache.get(key);

        if (cached != null) {
            return cached;
        }

        String result = LunarCalendar.getSolarFestival(month, day);
        solarFestivalCache.put(key, result);
        return result;
    }

    /**
     * 获取节气（带缓存）
     */
    public static String getSolarTerm(int year, int month, int day) {
        String key = makeKey(year, month, day);
        String cached = solarTermCache.get(key);

        if (cached != null) {
            return cached;
        }

        String result = LunarCalendar.getSolarTerm(year, month, day);
        solarTermCache.put(key, result);
        return result;
    }

    /**
     * 清除缓存（在内存紧张时调用）
     */
    public static void clearCache() {
        displayTextCache.evictAll();
        solarFestivalCache.evictAll();
        solarTermCache.evictAll();
    }

    /**
     * 预加载一个月的农历数据（在后台线程调用）
     */
    public static void preloadMonth(int year, int month) {
        int daysInMonth = getDaysInMonth(year, month);

        for (int day = 1; day <= daysInMonth; day++) {
            getDisplayText(year, month, day);
            getSolarFestival(month, day);
            getSolarTerm(year, month, day);
        }
    }

    /**
     * 获取月份天数
     */
    private static int getDaysInMonth(int year, int month) {
        switch (month) {
            case 1: case 3: case 5: case 7: case 8: case 10: case 12:
                return 31;
            case 4: case 6: case 9: case 11:
                return 30;
            case 2:
                // 闰年判断
                return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0) ? 29 : 28;
            default:
                return 30;
        }
    }
}