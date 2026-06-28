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

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.remote.model.cloudbill.BmobBillApiImpl;
import com.example.my_project1.data.remote.model.cloudwish.BmobWishApiImpl;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;
import io.reactivex.annotations.NonNull;

/**
 * WishSyncWorker（优化版）
 * -------------------------------------------------------
 * 优化内容：
 *  1. ✅ 强化级联删除：删除愿望时同步删除所有关联记录和账单
 *  2. ✅ 规范化同步顺序：删除 -> 创建/更新，防止数据冲突
 *  3. ✅ 增强错误处理：对云端对象不存在（101）的情况特殊处理
 *  4. ✅ 精细化日志：记录每一步操作的成功/失败状态
 *  5. ✅ 防超时机制：使用 CountDownLatch + 重试
 */
public class WishSyncWorker extends Worker {

    private static final String TAG = "WishSyncWorker";
    private static final String WORK_NAME = "WishSync";
    private static final int    MAX_RETRIES            = 3;
    private static final long   LATCH_TIMEOUT_SECONDS  = 30;

    private final WishDao wishDao;
    private final BillDao billDao;
    private final BmobWishApiImpl wishApi;
    private final BmobBillApiImpl billApi;

    public WishSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        AppDatabase db = AppDatabase.getInstance(context);
        wishDao = db.wishDao();
        billDao = db.billDao();
        wishApi = new BmobWishApiImpl(context);
        billApi = new BmobBillApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "========== 开始愿望同步 ==========");

            // ✅ Step 1：先处理删除（顺序严格）
            if (!syncDeleteRecords()) {
                Log.w(TAG, "⚠️  存钱记录删除同步未完全成功，将重试");
                return Result.retry();
            }

            if (!syncDeleteBills()) {
                Log.w(TAG, "⚠️  账单删除同步未完全成功，将重试");
                return Result.retry();
            }

            if (!syncDeleteWishes()) {
                Log.w(TAG, "⚠️  愿望删除同步未完全成功，将重试");
                return Result.retry();
            }

            // ✅ Step 2：处理创建/更新
            if (!syncWishes()) {
                Log.w(TAG, "⚠️  愿望上行同步未完全成功，将重试");
                return Result.retry();
            }

            if (!syncRecords()) {
                Log.w(TAG, "⚠️  存钱记录上行同步未完全成功，将重试");
                return Result.retry();
            }

            Log.i(TAG, "========== 愿望同步完成（成功）==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "❌ doWork 异常，准备重试: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ======================== 删除同步（优先级最高）========================

    /**
     * ✅ 同步删除愿望（包括级联删除记录和账单）
     */
    private boolean syncDeleteWishes() {
        List<Wish> toDelete = wishDao.getToDeleteWishes();
        if (toDelete == null || toDelete.isEmpty()) {
            Log.d(TAG, "✓ 无待删除的愿望");
            return true;
        }

        Log.i(TAG, "待删除愿望数量: " + toDelete.size());
        int fail = 0;

        for (Wish wish : toDelete) {
            try {
                // ✅ Step 1: 级联删除该愿望的所有存钱记录和账单
                cascadeDeleteRecordsAndBills(wish.getId());

                // ✅ Step 2: 删除愿望本体
                boolean ok;
                if (wish.getObjectId() == null || wish.getObjectId().isEmpty()) {
                    // 从未上传云端，直接物理删除本地
                    wishDao.deleteWish(wish);
                    Log.d(TAG, "   ✓ 愿望直接删除（无云端ID）: " + wish.getWishName());
                    ok = true;
                } else {
                    // 先删云端，再删本地
                    ok = deleteCloudWish(wish.getObjectId());
                    if (ok) {
                        wishDao.deleteWish(wish);
                        Log.d(TAG, "   ✓ 愿望云端+本地删除: " + wish.getWishName());
                    } else {
                        Log.e(TAG, "   ✗ 愿望删除失败: " + wish.getWishName());
                    }
                }

                if (!ok) fail++;

            } catch (Exception e) {
                Log.e(TAG, "   ✗ 删除愿望异常: " + wish.getWishName(), e);
                fail++;
            }
        }

        return fail == 0;
    }

    /**
     * ✅ 级联删除愿望的所有存钱记录（本地）
     * 注：云端记录的删除已在 syncDeleteRecords() 中处理
     */
    private void cascadeDeleteRecordsAndBills(long wishId) {
        try {
            List<WishRecord> records = wishDao.getRecordsByWishIdSync(wishId);
            if (records == null || records.isEmpty()) {
                Log.d(TAG, "      无关联存钱记录: wishId=" + wishId);
                return;
            }

            Log.d(TAG, "      级联删除记录数: " + records.size());
            for (WishRecord record : records) {
                // 删除关联账单
                if (record.getLinkedBillId() > 0) {
                    try {
                        Bill bill = billDao.getByIdSync(record.getLinkedBillId());
                        if (bill != null) {
                            // 删除云端账单
                            if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
                                billApi.deleteBillSync(bill.getObjectId());
                            }
                            // 删除本地账单
                            billDao.delete(bill);
                            Log.d(TAG, "         ✓ 关联账单已删除: billId=" + bill.getId());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "         ✗ 删除账单异常: " + e.getMessage(), e);
                    }
                }

                // 删除存钱记录本体
                wishDao.deleteRecord(record);
            }

        } catch (Exception e) {
            Log.e(TAG, "      ✗ 级联删除异常", e);
        }
    }

    /**
     * ✅ 同步删除存钱记录
     */
    private boolean syncDeleteRecords() {
        List<WishRecord> toDelete = wishDao.getToDeleteRecords();
        if (toDelete == null || toDelete.isEmpty()) {
            Log.d(TAG, "✓ 无待删除的存钱记录");
            return true;
        }

        Log.i(TAG, "待删除存钱记录数量: " + toDelete.size());
        int fail = 0;

        for (WishRecord record : toDelete) {
            try {
                boolean ok;
                if (record.getObjectId() == null || record.getObjectId().isEmpty()) {
                    // 从未上传，直接删除
                    wishDao.deleteRecord(record);
                    ok = true;
                } else {
                    // 先删云端，再删本地
                    ok = deleteCloudRecord(record.getObjectId());
                    if (ok) {
                        wishDao.deleteRecord(record);
                        Log.d(TAG, "   ✓ 存钱记录删除: recordId=" + record.getId());
                    }
                }

                if (!ok) fail++;

            } catch (Exception e) {
                Log.e(TAG, "   ✗ 删除记录异常", e);
                fail++;
            }
        }

        return fail == 0;
    }

    /**
     * ✅ 同步删除账单
     */
    private boolean syncDeleteBills() {
        List<Bill> toDelete = billDao.getToDeleteBills();
        if (toDelete == null || toDelete.isEmpty()) {
            Log.d(TAG, "✓ 无待删除的账单");
            return true;
        }

        Log.i(TAG, "待删除账单数量: " + toDelete.size());
        int fail = 0;

        for (Bill bill : toDelete) {
            try {
                boolean ok;
                if (bill.getObjectId() == null || bill.getObjectId().isEmpty()) {
                    billDao.delete(bill);
                    ok = true;
                } else {
                    ok = billApi.deleteBillSync(bill.getObjectId());
                    if (ok) {
                        billDao.delete(bill);
                        Log.d(TAG, "   ✓ 账单删除: billId=" + bill.getId());
                    }
                }

                if (!ok) fail++;

            } catch (Exception e) {
                Log.e(TAG, "   ✗ 删除账单异常", e);
                fail++;
            }
        }

        return fail == 0;
    }

    // ======================== 云端删除操作（异步转同步）========================

    /**
     * ✅ 异步删除云端愿望，CountDownLatch 转同步
     */
    private boolean deleteCloudWish(String objectId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                final boolean[] ok = {false};
                final int[] errorCode = {0};
                final CountDownLatch latch = new CountDownLatch(1);

                wishApi.deleteWish(objectId, new UpdateListener() {
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

                // 等待异步操作完成
                if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Log.w(TAG, "      ⏱️  云端愿望删除超时 (第" + attempt + "次)，继续重试");
                    continue;
                }

                // ✅ 关键：101 = 对象不存在，视为已删除（成功）
                if (ok[0] || errorCode[0] == 101) {
                    return true;
                }

            } catch (InterruptedException e) {
                Log.e(TAG, "      ✗ 删除愿望被中断 (第" + attempt + "次)", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "      ✗ 删除愿望异常 (第" + attempt + "次): " + e.getMessage(), e);
            }
        }

        Log.e(TAG, "      ✗ 云端愿望删除最终失败");
        return false;
    }

    /**
     * ✅ 异步删除云端存钱记录，CountDownLatch 转同步
     */
    private boolean deleteCloudRecord(String objectId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                final boolean[] ok = {false};
                final int[] errorCode = {0};
                final CountDownLatch latch = new CountDownLatch(1);

                wishApi.deleteRecord(objectId, new UpdateListener() {
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

                if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) continue;

                if (ok[0] || errorCode[0] == 101) return true;

            } catch (Exception e) {
                Log.e(TAG, "      ✗ 删除记录异常 (第" + attempt + "次): " + e.getMessage(), e);
            }
        }

        return false;
    }

    // ======================== 创建/更新同步 ========================

    /**
     * ✅ 同步创建/更新愿望
     */
    private boolean syncWishes() {
        List<Wish> pending = wishDao.getPendingSyncWishes();
        if (pending == null || pending.isEmpty()) {
            Log.d(TAG, "✓ 无待同步的愿望");
            return true;
        }

        Log.i(TAG, "待同步愿望数量: " + pending.size());
        int fail = 0;

        for (Wish wish : pending) {
            // 跳过待删除（已在上一步处理）
            if (wish.getSyncState() == SyncState.TO_DELETE) continue;

            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    ok = wishApi.uploadWishSync(wish);
                    if (ok) {
                        Log.d(TAG, "   ✓ 愿望同步成功 (第" + attempt + "次): " + wish.getWishName());
                        break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "   ✗ 愿望同步异常 (第" + attempt + "次): " + e.getMessage());
                }
            }

            if (!ok) {
                fail++;
                Log.e(TAG, "   ✗ 愿望同步最终失败: " + wish.getWishName());
            }
        }

        return fail == 0;
    }

    /**
     * ✅ 同步创建/更新存钱记录
     */
    private boolean syncRecords() {
        List<WishRecord> pending = wishDao.getPendingSyncRecords();
        if (pending == null || pending.isEmpty()) {
            Log.d(TAG, "✓ 无待同步的存钱记录");
            return true;
        }

        Log.i(TAG, "待同步存钱记录数量: " + pending.size());
        int fail = 0;

        for (WishRecord record : pending) {
            // 跳过待删除
            if (record.getSyncState() == SyncState.TO_DELETE) continue;

            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    ok = wishApi.uploadRecordSync(record);
                    if (ok) {
                        Log.d(TAG, "   ✓ 存钱记录同步成功 (第" + attempt + "次): recordId=" + record.getId());
                        break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "   ✗ 存钱记录同步异常 (第" + attempt + "次): " + e.getMessage());
                }
            }

            if (!ok) {
                fail++;
                Log.e(TAG, "   ✗ 存钱记录同步最终失败: recordId=" + record.getId());
            }
        }

        return fail == 0;
    }

    // ======================== WorkManager 入口 ========================

    /**
     * ✅ 入队同步任务
     * 使用 KEEP 策略防止重复入队
     */
    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WishSyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request);

        Log.d(TAG, "同步任务已入队");
    }
}