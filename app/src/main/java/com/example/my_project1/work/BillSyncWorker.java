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
 * BillSyncWorker - 账单同步Worker (优化修复版)
 * -------------------------------------------------------
 * 优化重点:
 * 1. 在 doWork 开始时增加当前用户登录检查，未登录直接返回 success，避免无意义重试与多用户混淆
 * 2. 隔离用户数据，使用 getPendingSyncBillsByUser 与 getToDeleteBillsByUser
 * 3. 简化删除逻辑，复用 BmobBillApiImpl.deleteBillSync(objectId)
 * 4. BmobBillApiImpl.uploadBillSync 已同步更新 SQLite，确保 Worker 线程事务写盘
 */
public class BillSyncWorker extends Worker {

    private static final String TAG = "BillSyncWorker";
    private static final int MAX_RETRIES = 3;

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
            Log.i(TAG, "========== 开始账单同步 ==========");

            String currentUserId = api.getCurrentUserId();
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "用户未登录，取消本次账单同步任务");
                return Result.success();
            }

            // 关键:先处理删除，再处理创建和更新
            boolean okDelete = syncDeleteBills(currentUserId);
            if (!okDelete) {
                Log.w(TAG, "账单删除同步未完全成功，将重试");
                return Result.retry();
            }

            // 同步创建和更新
            boolean okSync = syncBills(currentUserId);
            if (!okSync) {
                Log.w(TAG, "账单同步未完全成功，将重试");
                return Result.retry();
            }

            Log.i(TAG, "========== 账单同步完成 ==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork 异常，准备重试: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ======================== 删除账单同步 ========================

    /**
     * 同步删除账单
     * - 云端删除成功 → 物理删除本地数据
     * - 云端删除失败 → 保留 TO_DELETE 状态，下次重试
     * - 云端对象不存在(404/101) → 视为成功，物理删除本地
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
            Log.d(TAG, "处理待删除账单: " + bill.getAmount() + " (ID: " + objectId + ")");

            if (objectId == null || objectId.isEmpty()) {
                // 本地账单没有云端ID，直接物理删除
                try {
                    db.billDao().delete(bill);
                    successCount++;
                    Log.i(TAG, "   无云端ID，直接物理删除本地数据");
                } catch (Exception e) {
                    failCount++;
                    Log.e(TAG, "   本地删除失败: " + e.getMessage(), e);
                }
                continue;
            }

            // 直接调用 API 提供的同步删除（内置重试、超时与 101 错误码兼容）
            boolean cloudDeleteSuccess = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                cloudDeleteSuccess = api.deleteBillSync(objectId);
                if (cloudDeleteSuccess) break;
            }

            // 根据云端删除结果决定本地操作
            if (cloudDeleteSuccess) {
                try {
                    db.billDao().delete(bill);
                    Log.i(TAG, "   本地账单物理删除成功");
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "   本地物理删除失败: " + e.getMessage(), e);
                    failCount++;
                }
            } else {
                Log.w(TAG, "   云端删除失败，保留待删除标记");
                failCount++;
            }
        }

        Log.i(TAG, String.format("syncDeleteBills 完成 - 总计:%d, 成功:%d, 失败:%d",
                deleteCount, successCount, failCount));

        return failCount == 0;
    }

    // ======================== 账单同步(创建/更新) ========================

    /**
     * 同步账单(创建和更新)
     */
    private boolean syncBills(String userId) {
        List<Bill> bills = db.billDao().getPendingSyncBillsByUser(userId);

        if (bills == null || bills.isEmpty()) {
            Log.d(TAG, "syncBills - 无待同步账单");
            return true;
        }

        Log.i(TAG, "syncBills - 待同步账单数量: " + bills.size() + " (userId=" + userId + ")");

        int successCount = 0;
        int failCount = 0;

        for (Bill bill : bills) {
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
                    Log.w(TAG, "syncBills - 第 " + attempt + " 次失败: " + bill.getAmount());
                } catch (Throwable t) {
                    Log.e(TAG, "syncBills - 异常 attempt=" + attempt, t);
                }
            }

            if (ok) {
                successCount++;
            } else {
                failCount++;
                Log.e(TAG, "账单同步最终失败: " + bill.getAmount());
            }
        }

        Log.i(TAG, String.format("syncBills 完成 - 成功:%d, 失败:%d", successCount, failCount));

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
                .enqueueUniqueWork("BillSync", ExistingWorkPolicy.KEEP, request);
    }
}