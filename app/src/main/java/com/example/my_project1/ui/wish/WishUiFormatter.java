package com.example.my_project1.ui.wish;

import com.example.my_project1.data.model.wish.Wish;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class WishUiFormatter {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENT = new DecimalFormat("0.#");
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA);
    private static final SimpleDateFormat DATE_TIME = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA);

    private WishUiFormatter() {
    }

    public static String money(double amount) {
        return "¥ " + MONEY.format(Math.max(0d, amount));
    }

    public static double percentValue(Wish wish) {
        if (wish == null || wish.getTargetAmount() <= 0d) return 0d;
        return Math.max(0d, wish.getCurrentAmount() * 100d / wish.getTargetAmount());
    }

    public static int progress(Wish wish) {
        // 进度控件最大值为 100，但文本仍可展示超额完成的真实百分比。
        return (int) Math.min(100d, Math.round(percentValue(wish)));
    }

    public static String percent(Wish wish) {
        return PERCENT.format(percentValue(wish)) + "%";
    }

    public static String date(Date value) {
        return value == null ? "--" : DATE.format(value);
    }

    public static String dateTime(Date value) {
        return value == null ? "--" : DATE_TIME.format(value);
    }

    public static long elapsedDays(Date startDate) {
        if (startDate == null) return 0;
        Calendar start = Calendar.getInstance();
        start.setTime(startDate);
        clearTime(start);
        Calendar today = Calendar.getInstance();
        clearTime(today);
        return Math.max(0L, (today.getTimeInMillis() - start.getTimeInMillis()) / 86_400_000L);
    }

    public static String status(Wish wish) {
        if (wish == null) return "";
        if (wish.getStatus() == Wish.STATUS_COMPLETED) return "已完成";
        if (wish.getStatus() == Wish.STATUS_ABANDONED) return "已暂停";
        return "进行中";
    }

    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
