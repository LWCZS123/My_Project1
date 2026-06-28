package com.example.my_project1.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 工具类：安全地将各种对象类型（如 String / Date / BmobDate）转换为 java.util.Date。
 * 用于云端与本地同步时的时间字段解析。
 */
public class DateConvertUtil {

    private static final String[] DATE_PATTERNS = new String[]{
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd"
    };

    /**
     * 将任意对象安全转换为 java.util.Date。
     * 支持：
     *  - Date 对象
     *  - 字符串（多种常见格式）
     *  - 含 getDate() 或 getTime() 方法的对象（如 BmobDate）
     */
    public static Date safeConvertToDate(Object value) {
        if (value == null) return null;

        // ✅ 已经是 Date 类型
        if (value instanceof Date) {
            return (Date) value;
        }

        // ✅ String 类型（常见于 Bmob 返回 JSON）
        if (value instanceof String) {
            return parseDateString((String) value);
        }

        // ✅ 尝试反射调用 getDate() 或 getTime()（兼容 BmobDate）
        try {
            java.lang.reflect.Method getDateMethod = value.getClass().getMethod("getDate");
            Object res = getDateMethod.invoke(value);
            if (res instanceof Date) return (Date) res;
        } catch (Exception ignored) { }

        try {
            java.lang.reflect.Method getTimeMethod = value.getClass().getMethod("getTime");
            Object res = getTimeMethod.invoke(value);
            if (res instanceof Date) return (Date) res;
            if (res instanceof Long) return new Date((Long) res);
        } catch (Exception ignored) { }

        // ❌ 全部失败，返回 null（可改为 new Date() 表示当前时间）
        return null;
    }

    /** 尝试解析字符串为 Date */
    private static Date parseDateString(String s) {
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                sdf.setLenient(true);
                return sdf.parse(s);
            } catch (ParseException ignored) { }
        }
        return null;
    }
}
