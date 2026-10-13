package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.remote.model.cloudbill.BmobBillApiImpl;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * BillSyncWorker - 账单同步Worker (分批次并发控制版)
 * -------------------------------------------------------
 * 优化重点:
 * 1. 分批次上传（BATCH_SIZE = 20），控制 Bmob 云端并发频次，防止超过 2000 并发限制
 * 2. 批次与单条之间增加平滑停顿与重试退避机制，保障长时间高并发写入的稳定性
 * 3. 在 doWork 开始时增加当前用户登录检查，隔离多用户数据
 * 4. BmobBillApiImpl.uploadBillSync 已同步更新 SQLite，确保 Worker 线程事务写盘
 */
public class BillSyncWorker extends Worker {

    private static final String TAG = "BillSyncWorker";
    private static final int MAX_RETRIES = 3;

    // 分批与并发频次控制参数
    private static final int BATCH_SIZE = 20;            // 每批次处理数量
    private static final long BATCH_INTERVAL_MS = 200;    // 批次之间的平滑停顿时间(ms)
    private static final long ITEM_INTERVAL_MS = 20;      // 单条上传后的微小停顿时间(ms)

    private final AppDatabase db;
    private final BmobBillApiImpl api;

    public BillSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobBillApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "========== 开始账单分批同步 ==========");

            String currentUserId = api.getCurrentUserId();
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "用户未登录，取消本次账单同步任务");
                return Result.success();
            }

            // 处理删除、创建与更新
            boolean okDelete = syncDeleteBills(currentUserId);
            boolean okSync = syncBills(currentUserId);

            if (!okDelete || !okSync) {
                Log.w(TAG, "账单分批同步未完全成功 (删除: " + okDelete + ", 同步: " + okSync + ")，将重试");
                return Result.retry();
            }

            Log.i(TAG, "========== 账单分批同步完成 ==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork 异常，准备重试: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ======================== 删除账单同步 ========================

    /**
     * 同步删除账单
     */
    private boolean syncDeleteBills(String userId) {
        List<Bill> deletedBills = db.billDao().getToDeleteBillsByUser(userId);

        if (deletedBills == null || deletedBills.isEmpty()) {
            Log.d(TAG, "syncDeleteBills - 无待删除账单");
            return true;
        }

        int deleteCount = deletedBills.size();
        int successCount = 0;
        int failCount = 0;

        Log.i(TAG, "开始同步删除 " + deleteCount + " 条账单 (userId=" + userId + ")");

        for (Bill bill : deletedBills) {
            String objectId = bill.getObjectId();

            if (objectId == null || objectId.isEmpty()) {
                try {
                    db.billDao().delete(bill);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    Log.e(TAG, "本地删除失败: " + e.getMessage(), e);
                }
                continue;
            }

            boolean cloudDeleteSuccess = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                cloudDeleteSuccess = api.deleteBillSync(objectId);
                if (cloudDeleteSuccess) break;
                try {
                    Thread.sleep(200 * attempt);
                } catch (InterruptedException ignored) {}
            }

            if (cloudDeleteSuccess) {
                try {
                    db.billDao().delete(bill);
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "本地物理删除失败: " + e.getMessage(), e);
                    failCount++;
                }
            } else {
                Log.w(TAG, "云端删除失败，保留待删除标记");
                failCount++;
            }

            try {
                Thread.sleep(ITEM_INTERVAL_MS);
            } catch (InterruptedException ignored) {}
        }

        Log.i(TAG, String.format("syncDeleteBills 完成 - 总计:%d, 成功:%d, 失败:%d",
                deleteCount, successCount, failCount));

        return failCount == 0;
    }

    // ======================== 账单同步(分批创建与更新) ========================

    /**
     * 分批次同步账单(创建和更新)
     */
    private boolean syncBills(String userId) {
        List<Bill> bills = db.billDao().getPendingSyncBillsByUser(userId);

        if (bills == null || bills.isEmpty()) {
            Log.d(TAG, "syncBills - 无待同步账单");
            return true;
        }

        int totalBills = bills.size();
        int totalBatches = (int) Math.ceil((double) totalBills / BATCH_SIZE);

        Log.i(TAG, String.format("开始分批同步账单: 总数=%d 条, 每批=%d 条, 共%d 批 (userId=%s)",
                totalBills, BATCH_SIZE, totalBatches, userId));

        int successCount = 0;
        int failCount = 0;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int start = batchIndex * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, totalBills);
            List<Bill> batch = bills.subList(start, end);

            Log.i(TAG, String.format("正在处理第 %d/%d 批 (本批包含 %d 条账单)", batchIndex + 1, totalBatches, batch.size()));

            for (Bill bill : batch) {
                boolean ok = false;
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        SyncState state = bill.getSyncState();
                        if (state == SyncState.TO_CREATE || state == SyncState.TO_UPDATE) {
                            ok = api.uploadBillSync(bill);
                        } else {
                            ok = true;
                        }

                        if (ok) break;

                        Log.w(TAG, "syncBills - 第 " + attempt + " 次上传失败: " + bill.getAmount() + "，将稍后重试");
                        Thread.sleep(300 * attempt); // 失败避让机制
                    } catch (Throwable t) {
                        Log.e(TAG, "syncBills - 异常 attempt=" + attempt, t);
                    }
                }

                if (ok) {
                    successCount++;
                } else {
                    failCount++;
                    Log.e(TAG, "账单分批同步单条最终失败: ID=" + bill.getId() + ", amount=" + bill.getAmount());
                }

                // 单条间隔控制
                try {
                    Thread.sleep(ITEM_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            Log.i(TAG, String.format("第 %d/%d 批同步完成, 当前累积成功: %d, 失败: %d",
                    batchIndex + 1, totalBatches, successCount, failCount));

            // 批次之间的平滑停顿，规避 Bmob 并发限制
            if (batchIndex < totalBatches - 1) {
                try {
                    Thread.sleep(BATCH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Log.i(TAG, String.format("syncBills 全部分批同步结束 - 总计:%d, 成功:%d, 失败:%d",
                totalBills, successCount, failCount));

        return failCount == 0;
    }

    // ======================== WorkManager 入口 ========================

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BillSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork("BillSync", ExistingWorkPolicy.REPLACE, request);
    }
}
