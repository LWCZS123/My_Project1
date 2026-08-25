package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.remote.model.cloudwish.BmobWishApiImpl;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

/**
 * WishSyncWorker - 愿望同步 Worker
 * -------------------------------------------------------
 * 负责将本地愿望数据同步到 Bmob 云端
 */
public class WishSyncWorker extends Worker {

    private static final String TAG = "WishSyncWorker";
    private static final int MAX_RETRIES = 3;
    private static final long LATCH_TIMEOUT_SECONDS = 30;

    private final AppDatabase db;
    private final BmobWishApiImpl api;

    public WishSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobWishApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "========== 开始愿望同步 ==========");

            // 1. 先处理删除
            boolean okDelete = syncDeleteWishes();
            if (!okDelete) {
                Log.w(TAG, "愿望删除同步未完全成功, 将重试");
                return Result.retry();
            }

            // 2. 处理创建和更新
            boolean okSync = syncWishes();
            if (!okSync) {
                Log.w(TAG, "愿望同步未完全成功, 将重试");
                return Result.retry();
            }

            Log.i(TAG, "========== 愿望同步完成 ==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork 异常, 准备重试: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    /**
     * 同步删除愿望
     */
    private boolean syncDeleteWishes() {
        List<Wish> deletedWishes = db.wishDao().getToDeleteWishes();

        if (deletedWishes == null || deletedWishes.isEmpty()) {
            Log.d(TAG, "syncDeleteWishes - 无待删除愿望");
            return true;
        }

        int successCount = 0;
        int failCount = 0;

        Log.i(TAG, "开始同步删除 " + deletedWishes.size() + " 条愿望");

        for (Wish wish : deletedWishes) {
            String objectId = wish.getObjectId();

            if (objectId == null || objectId.isEmpty()) {
                // 本地数据没有云端ID，直接物理删除
                try {
                    db.wishDao().deleteWish(wish);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    Log.e(TAG, "本地删除失败: " + e.getMessage());
                }
                continue;
            }

            // 同步删除云端数据
            boolean cloudDeleteSuccess = deleteCloudWishAsync(objectId);

            if (cloudDeleteSuccess) {
                // 云端删除成功, 物理删除本地数据
                try {
                    db.wishDao().deleteWish(wish);
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "本地物理删除失败: " + e.getMessage());
                    failCount++;
                }
            } else {
                failCount++;
            }
        }

        Log.i(TAG, String.format("syncDeleteWishes 完成 - 成功:%d, 失败:%d", successCount, failCount));
        return failCount == 0;
    }

    /**
     * 异步方式删除云端愿望，使用 CountDownLatch 等待
     */
    private boolean deleteCloudWishAsync(String objectId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                final boolean[] ok = {false};
                final int[] errorCode = {0};
                final CountDownLatch latch = new CountDownLatch(1);

                api.deleteWish(objectId, new UpdateListener() {
                    @Override
                    public void done(BmobException e) {
                        if (e == null) {
                            ok[0] = true;
                        } else {
                            errorCode[0] = e.getErrorCode();
                        }
                        latch.countDown();
                    }
                });

                boolean awaited = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!awaited) continue;
                if (ok[0]) return true;
                if (errorCode[0] == 101) return true; // 云端不存在视为成功

            } catch (Exception e) {
                Log.e(TAG, "删除异常: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * 同步愿望（创建和更新）
     */
    private boolean syncWishes() {
        List<Wish> wishes = db.wishDao().getPendingSyncWishes();

        if (wishes == null || wishes.isEmpty()) {
            Log.d(TAG, "syncWishes - 无待同步愿望");
            return true;
        }

        int successCount = 0;
        int failCount = 0;

        for (Wish wish : wishes) {
            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    ok = api.uploadWishSync(wish);
                    if (ok) break;
                } catch (Throwable t) {
                    Log.e(TAG, "syncWishes 异常 attempt=" + attempt, t);
                }
            }

            if (ok) {
                successCount++;
            } else {
                failCount++;
                Log.i(TAG, "愿望同步最终失败: " + wish.getWishName());
            }
        }

        Log.i(TAG, String.format("syncWishes 完成 - 成功:%d, 失败:%d", successCount, failCount));
        return failCount == 0;
    }

    // ======================== WorkManager 入口 ========================

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WishSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork("WishSync", ExistingWorkPolicy.KEEP, request);
    }
}
