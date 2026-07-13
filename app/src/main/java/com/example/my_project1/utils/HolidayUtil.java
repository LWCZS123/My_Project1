package com.example.my_project1.utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 节假日工具类 - Assets 优化版
 * 从 assets 目录读取 JSON 数据，支持动态加载和多年份覆盖
 */
public class HolidayUtil {

    private static final String TAG = "HolidayUtil";
    private static final Map<String, String> HOLIDAY_DATA = new HashMap<>();
    private static boolean isInitialized = false;

    /**
     * 初始化节假日数据
     * 建议在 Application.onCreate() 中调用
     */
    public static void init(Context context) {
        if (isInitialized) return;
        try {
            String[] files = {"2024.json", "2025.json", "2026.json", "2027.json"};
            Gson gson = new Gson();
            for (String fileName : files) {
                try (InputStream is = context.getAssets().open(fileName);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    
                    HolidayJson data = gson.fromJson(reader, HolidayJson.class);
                    if (data != null && data.days != null) {
                        for (HolidayDay day : data.days) {
                            add(day.date, day.name, day.isOffDay);
                        }
                    }
                    Log.d(TAG, "Loaded holiday data from: " + fileName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load " + fileName, e);
                }
            }
            isInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "HolidayUtil initialization failed", e);
        }
    }

    private static void add(String date, String name, boolean isOff) {
        HOLIDAY_DATA.put(date, name + "|" + (isOff ? "休" : "班"));
    }

    /**
     * 获取日期的标签：休、班 或 null
     */
    public static String getDayTag(int year, int month, int day) {
        String key = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
        String data = HOLIDAY_DATA.get(key);
        if (data != null) {
            return data.split("\\|")[1];
        }
        
        // 兜底使用库数据
        try {
            com.nlf.calendar.Holiday h = com.nlf.calendar.util.HolidayUtil.getHoliday(year, month, day);
            if (h != null) {
                return h.isWork() ? "班" : "休";
            }
        } catch (Exception ignored) {}
        
        return null;
    }

    public static boolean isHoliday(int year, int month, int day) {
        return "休".equals(getDayTag(year, month, day));
    }

    public static String[] getNextHoliday(int year, int month, int day) {
        String currentKey = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
        List<HolidayInfo> list = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : HOLIDAY_DATA.entrySet()) {
            String[] parts = entry.getValue().split("\\|");
            if ("休".equals(parts[1])) {
                list.add(new HolidayInfo(entry.getKey(), parts[0]));
            }
        }

        if (year >= 2027) {
            addMajorFestivals(list, year);
            addMajorFestivals(list, year + 1);
        }

        if (list.isEmpty()) return null;

        Collections.sort(list, (o1, o2) -> o1.dateKey.compareTo(o2.dateKey));

        for (HolidayInfo h : list) {
            if (h.dateKey.compareTo(currentKey) >= 0) {
                return new String[]{h.dateKey, h.name};
            }
        }
        return null;
    }

    private static void addMajorFestivals(List<HolidayInfo> list, int year) {
        list.add(new HolidayInfo(year + "-01-01", "元旦"));
        list.add(new HolidayInfo(year + "-05-01", "劳动节"));
        list.add(new HolidayInfo(year + "-10-01", "国庆节"));
        addLunarFestival(list, year, 1, 1, "春节");
        addLunarFestival(list, year, 5, 5, "端午节");
        addLunarFestival(list, year, 8, 15, "中秋节");
    }

    private static void addLunarFestival(List<HolidayInfo> list, int year, int lMonth, int lDay, String name) {
        try {
            Lunar lunar = Lunar.fromYmd(year, lMonth, lDay);
            Solar solar = lunar.getSolar();
            list.add(new HolidayInfo(solar.toYmd(), name));
        } catch (Exception ignored) {}
    }

    private static class HolidayInfo {
        String dateKey;
        String name;
        HolidayInfo(String dateKey, String name) {
            this.dateKey = dateKey;
            this.name = name;
        }
    }

    // JSON 解析类
    private static class HolidayJson {
        int year;
        List<HolidayDay> days;
    }

    private static class HolidayDay {
        String name;
        String date;
        boolean isOffDay;
    }
}
