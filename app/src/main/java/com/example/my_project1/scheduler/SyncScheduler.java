package com.example.my_project1.scheduler;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.my_project1.work.CategorySyncWorker;
import com.example.my_project1.work.SubCategorySyncWorker;

import java.util.concurrent.TimeUnit;

/**
 * SyncScheduler
 * --------------------------------------------------------
 * 用于注册周期性同步任务（例如每 1 小时自动同步一次）
 * 与网络恢复触发机制互补。
 */
public class SyncScheduler {

    private static final String TAG = "SyncScheduler";

    public static void schedulePeriodicSync(Context context) {
        WorkManager wm = WorkManager.getInstance(context);

        // 分类同步任务（每小时执行一次）
        PeriodicWorkRequest categoryPeriodicSync =
                new PeriodicWorkRequest.Builder(
                        CategorySyncWorker.class,
                        1, TimeUnit.HOURS)
                        .setConstraints(CategorySyncWorker.getDefaultConstraints())
                        .build();

        // 子分类同步任务（每小时执行一次）
        PeriodicWorkRequest subCategoryPeriodicSync =
                new PeriodicWorkRequest.Builder(
                        SubCategorySyncWorker.class,
                        1, TimeUnit.HOURS)
                        .setConstraints(SubCategorySyncWorker.getDefaultConstraints())
                        .build();

        // 避免重复注册，用唯一名称确保只存在一个周期任务
        wm.enqueueUniquePeriodicWork(
                "CategoryPeriodicSync",
                ExistingPeriodicWorkPolicy.KEEP,
                categoryPeriodicSync);

        wm.enqueueUniquePeriodicWork(
                "SubCategoryPeriodicSync",
                ExistingPeriodicWorkPolicy.KEEP,
                subCategoryPeriodicSync);

        Log.d(TAG, "🕒 周期性同步任务已注册（每 1 小时执行一次）");
    }
}
