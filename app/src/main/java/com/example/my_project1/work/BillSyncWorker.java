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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;
import io.reactivex.annotations.NonNull;

/**
 * BillSyncWorker - 账单同步Worker (完整修复版)
 * -------------------------------------------------------
 * 🔧 修复内容:
 * 1. 移除syncBills()中的重复数据库更新
 * 2. uploadBillSync()已经更新了数据库,无需重复操作
 * 3. 统一删除同步逻辑，移除重复的syncDeletedBills方法
 */
public class BillSyncWorker extends Worker {

    private static final String TAG = "BillSyncWorker";
    private static final int MAX_RETRIES = 3;
    private static final long LATCH_TIMEOUT_SECONDS = 30;

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

            // 🔴 关键:先处理删除,再处理创建和更新
            boolean okDelete = syncDeleteBills();
            if (!okDelete) {
                Log.w(TAG, "账单删除同步未完全成功,将重试");
                return Result.retry();
            }

            // 同步创建和更新
            boolean okSync = syncBills();
            if (!okSync) {
                Log.w(TAG, "账单同步未完全成功,将重试");
                return Result.retry();
            }

            Log.i(TAG, "========== 账单同步完成 ==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork 异常,准备重试:" + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ======================== 🔴 删除账单同步 ========================

    /**
     * 同步删除账单（完整版）
     * - 云端删除成功 → 物理删除本地数据
     * - 云端删除失败 → 保留 TO_DELETE 状态,下次重试
     * - 云端对象不存在(404/101) → 视为成功,物理删除本地
     */
    private boolean syncDeleteBills() {
        List<Bill> deletedBills = db.billDao().getToDeleteBills();

        if (deletedBills == null || deletedBills.isEmpty()) {
            Log.d(TAG, "syncDeleteBills - 无待删除账单");
            return true;
        }

        int deleteCount = deletedBills.size();
        int successCount = 0;
        int failCount = 0;

        Log.i(TAG, "🔄 开始同步删除 " + deleteCount + " 条账单");

        for (Bill bill : deletedBills) {
            String objectId = bill.getObjectId();
            Log.d(TAG, "🗑️ 处理待删除账单: " + bill.getAmount() + " (ID: " + objectId + ")");

            if (objectId == null || objectId.isEmpty()) {
                // 本地账单没有云端ID,直接物理删除
                try {
                    db.billDao().delete(bill);
                    successCount++;
                    Log.i(TAG, "   ✅ 无云端ID,直接物理删除本地数据");
                } catch (Exception e) {
                    failCount++;
                    Log.e(TAG, "   ❌ 本地删除失败: " + e.getMessage(), e);
                }
                continue;
            }

            // 方案1: 使用异步删除（推荐，更可靠）
            boolean cloudDeleteSuccess = deleteCloudBillAsync(objectId);

            // 方案2: 使用同步删除（可选，更简单但需要Bmob SDK支持）
            // boolean cloudDeleteSuccess = deleteCloudBillSync(objectId);

            // 🔑 关键:根据云端删除结果决定本地操作
            if (cloudDeleteSuccess) {
                // 云端删除成功,物理删除本地数据
                try {
                    db.billDao().delete(bill);
                    Log.i(TAG, "   ✅ 本地账单物理删除成功");
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "   ❌ 本地物理删除失败: " + e.getMessage(), e);
                    failCount++;
                }
            } else {
                // 云端删除失败,保留 TO_DELETE 状态,下次重试
                Log.w(TAG, "   ⚠️ 云端删除失败,保留待删除标记");
                failCount++;
            }
        }

        Log.i(TAG, String.format("syncDeleteBills 完成 - 总计:%d, 成功:%d, 失败:%d",
                deleteCount, successCount, failCount));

        // 🔑 只要有失败的,就返回 false 让 Worker 重试
        return failCount == 0;
    }

    /**
     * 删除云端账单（异步方式，使用CountDownLatch等待）
     */
    private boolean deleteCloudBillAsync(String objectId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                final boolean[] ok = {false};
                final int[] errorCode = {0};
                final CountDownLatch latch = new CountDownLatch(1);

                int finalAttempt = attempt;
                api.deleteBill(objectId, new UpdateListener() {
                    @Override
                    public void done(BmobException e) {
                        if (e == null) {
                            Log.d(TAG, "   ✅ 云端删除成功(第" + finalAttempt + "次)");
                            ok[0] = true;
                        } else {
                            errorCode[0] = e.getErrorCode();
                            Log.e(TAG, "   ❌ 云端删除失败(第" + finalAttempt + "次): "
                                    + e.getMessage() + " (错误码: " + errorCode[0] + ")");
                        }
                        latch.countDown();
                    }
                });

                boolean awaited = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!awaited) {
                    Log.w(TAG, "   ⚠️ 等待云端删除超时(第" + attempt + "次)");
                    continue;
                }

                if (ok[0]) {
                    return true;
                }

                // 🔑 关键:如果云端对象不存在(错误码 101),视为删除成功
                if (errorCode[0] == 101) {
                    Log.i(TAG, "   ✅ 云端对象已不存在(404),视为删除成功");
                    return true;
                }

            } catch (Exception e) {
                Log.e(TAG, "   ❌ 删除异常(第" + attempt + "次): " + e.getMessage(), e);
            }
        }

        return false;
    }

    /**
     * 删除云端账单（同步方式，需要BmobBillApiImpl支持）
     */
    private boolean deleteCloudBillSync(String objectId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                boolean success = api.deleteBillSync(objectId);
                if (success) {
                    Log.d(TAG, "   ✅ 云端删除成功(第" + attempt + "次)");
                    return true;
                }
                Log.e(TAG, "   ❌ 云端删除失败(第" + attempt + "次)");
            } catch (Exception e) {
                Log.e(TAG, "   ❌ 删除异常(第" + attempt + "次): " + e.getMessage(), e);
            }
        }
        return false;
    }

    // ======================== 账单同步(创建/更新) ========================

    /**
     * 同步账单(创建和更新)
     *
     * 🔧 修复说明:
     * - api.uploadBillSync() 内部已经更新了数据库
     * - 包括设置 syncState=SYNCED 和 updatedAt=当前时间
     * - 所以这里不需要再次更新数据库!
     */
    private boolean syncBills() {
        List<Bill> bills = db.billDao().getPendingSyncBills();

        if (bills == null || bills.isEmpty()) {
            Log.d(TAG, "syncBills - 无待同步账单");
            return true;
        }

        Log.i(TAG, "syncBills - 待同步账单数量: " + bills.size());

        int successCount = 0;
        int failCount = 0;

        for (Bill bill : bills) {
            // 🔑 跳过待删除的(已在 syncDeleteBills 中处理)
            if (bill.getSyncState() == SyncState.TO_DELETE) {
                Log.d(TAG, "跳过待删除账单: " + bill.getAmount());
                continue;
            }

            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    SyncState state = bill.getSyncState();
                    if (state == SyncState.TO_CREATE || state == SyncState.TO_UPDATE) {
                        ok = api.uploadBillSync(bill);
                    } else {
                        ok = true; // 已同步状态,跳过
                    }

                    if (ok) break;
                    Log.w(TAG, "syncBills - 第 " + attempt + " 次失败: " + bill.getAmount());
                } catch (Throwable t) {
                    Log.e(TAG, "syncBills - 异常 attempt=" + attempt, t);
                }
            }

            if (ok) {
                successCount++;
                // ========================================
                // 🔑 关键修复: 移除重复的数据库更新
                // uploadBillSync() 已经更新过数据库了!
                // ========================================

            } else {
                failCount++;
                Log.e(TAG, "❌ 账单同步最终失败: " + bill.getAmount());
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