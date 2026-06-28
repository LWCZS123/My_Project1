package com.example.my_project1.utils;
import android.content.Context;
import android.util.DisplayMetrics;

public class DisplayUtils {

    private DisplayUtils() {
        // 私有构造，避免实例化
        throw new UnsupportedOperationException("Cannot instantiate utility class.");
    }

    /**
     * dp 转 px
     * @param context 上下文
     * @param dpValue dp 值
     * @return px 值
     */
    public static int dp2px(Context context, float dpValue) {
        if (context == null) return (int) dpValue;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(dpValue * metrics.density);
    }

    /**
     * px 转 dp
     * @param context 上下文
     * @param pxValue px 值
     * @return dp 值
     */
    public static int px2dp(Context context, float pxValue) {
        if (context == null) return (int) pxValue;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(pxValue / metrics.density);
    }

    /**
     * sp 转 px（适用于字体大小）
     */
    public static int sp2px(Context context, float spValue) {
        if (context == null) return (int) spValue;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(spValue * metrics.scaledDensity);
    }

    /**
     * px 转 sp（适用于字体大小）
     */
    public static int px2sp(Context context, float pxValue) {
        if (context == null) return (int) pxValue;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(pxValue / metrics.scaledDensity);
    }
}
