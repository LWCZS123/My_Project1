package com.example.my_project1.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.my_project1.work.CategorySyncWorker;
import com.example.my_project1.work.SubCategorySyncWorker;
import com.example.my_project1.work.AccountSyncWorker;

/**
 * SyncRepository
 * ------------------------------------------------------
 * 统一管理所有数据同步本地→云端（Bmob）
 * 触发来源：
 *  ① 网络恢复 (NetworkReceiver)
 *  ② 用户手动点击
 *  ③ 定时后台同步（可扩展）
 */
public class SyncRepository {

    private static final String TAG = "SyncRepository";
    private final Context context;

    public SyncRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 🔄 全量同步 (分类 → 子分类 → 账户组/账户)
     * ⭐ 一条链完成所有数据同步！
     */
    public void syncAll() {
        Log.d(TAG, "🚀 SyncRepository → 全量同步开始排队执行...");

        WorkManager wm = WorkManager.getInstance(context);

        OneTimeWorkRequest categorySync =
                new OneTimeWorkRequest.Builder(CategorySyncWorker.class)
                        .setConstraints(CategorySyncWorker.getDefaultConstraints())
                        .build();

        OneTimeWorkRequest subCategorySync =
                new OneTimeWorkRequest.Builder(SubCategorySyncWorker.class)
                        .setConstraints(SubCategorySyncWorker.getDefaultConstraints())
                        .build();

        OneTimeWorkRequest accountSync =
                new OneTimeWorkRequest.Builder(AccountSyncWorker.class)
                        .setConstraints(AccountSyncWorker.getDefaultConstraints())
                        .build();

        /** 🔥 链式执行顺序非常关键！
         *  第一步必须是分类 → 再子分类
         *  AccountSyncWorker 内部已处理 先删→组→账户
         */
        wm.beginUniqueWork(
                "sync_all_work",
                ExistingWorkPolicy.KEEP,
                categorySync
        ).then(subCategorySync)
                .then(accountSync)  // ← 第三步执行账户同步
                .enqueue();

        Log.d(TAG,"✔ 全量同步任务已提交 → 等待WorkManager逐个执行");
    }

    /** 仅同步分类 */
    public void syncCategoriesOnly() {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "category_sync_work",
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(CategorySyncWorker.class)
                        .setConstraints(CategorySyncWorker.getDefaultConstraints())
                        .build()
        );
    }

    /** 仅同步子分类 */
    public void syncSubCategoriesOnly() {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "subcategory_sync_work",
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(SubCategorySyncWorker.class)
                        .setConstraints(SubCategorySyncWorker.getDefaultConstraints())
                        .build()
        );
    }

    /** 仅同步账户部分 (含账户组+账户+删除任务) */
    public void syncAccountsOnly() {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "account_sync_work",
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AccountSyncWorker.class)
                        .setConstraints(AccountSyncWorker.getDefaultConstraints())
                        .build()
        );
    }
}
