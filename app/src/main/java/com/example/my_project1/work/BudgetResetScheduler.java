package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * BudgetResetScheduler
 *
 * 在 Application.onCreate() 或用户登录后调用 schedule()，
 * 注册一个每 24 小时执行一次的 PeriodicWorkRequest。
 * 使用 ExistingPeriodicWorkPolicy.KEEP 避免重复注册。
 *
 * 初始延迟计算：距离下一个凌晨 0:00 的毫秒数，保证第一次触发即在午夜。
 */
public class BudgetResetScheduler {

    private static final String TAG       = "BudgetResetScheduler";
    private static final String WORK_NAME = "budget_daily_reset";

    public static void schedule(Context context) {
        long delayMs = millisUntilMidnight();
        Log.d(TAG, "⏰ 预算重置任务已调度，首次触发延迟：" + delayMs / 1000 + "s");

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BudgetResetWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request);
    }

    /** 计算距今天凌晨 00:00:00 的毫秒差（若当前已过午夜则指向明天凌晨）*/
    private static long millisUntilMidnight() {
        Calendar nextMidnight = Calendar.getInstance();
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        return nextMidnight.getTimeInMillis() - System.currentTimeMillis();
    }
}