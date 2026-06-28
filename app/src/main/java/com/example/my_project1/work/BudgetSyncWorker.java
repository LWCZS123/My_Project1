package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.data.remote.model.cloudbudget.BmobBudgetApiImpl;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * BudgetSyncWorker（重构版）
 *
 * 将本地待同步的预算记录（TO_CREATE / TO_UPDATE / TO_DELETE）上传到 Bmob 云端。
 *
 * 同步策略：
 *   - TO_CREATE：检查 cloudId；若已有 cloudId（极端并发情况下另一个 Worker 抢先写入）
 *                则转为 update，否则调用 BmobBudgetApiImpl.uploadBudgetSync() 新建。
 *   - TO_UPDATE：有 cloudId 则调用 update；无 cloudId 则补上传（视为 TO_CREATE）。
 *   - TO_DELETE：有 cloudId 则调用 deleteBudgetSync() 删除云端，无 cloudId 则直接删本地。
 *
 * 防重复：
 *   - 使用 enqueueUniqueWork("budget_sync_unique", KEEP) 保证同一时刻队列中只有一个实例。
 *   - upload 前通过 DB 二次校验 cloudId，防止极端并发导致同一条记录被重复 save。
 *
 * 线程：doWork() 运行在 WorkManager 的 diskIO 线程，所有 DB 操作可直接调用同步方法。
 */
public class BudgetSyncWorker extends Worker {

    private static final String TAG       = "BudgetSyncWorker";
    /** 唯一任务名，与 BudgetRepository.enqueueSync() 保持一致 */
    public  static final String WORK_NAME = "budget_sync_unique";

    private final AppDatabase       db;
    private final BmobBudgetApiImpl api;

    public BudgetSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db  = AppDatabase.getInstance(context);
        api = new BmobBudgetApiImpl(context);
    }

    // =====================================================================
    //  主入口
    // =====================================================================

    @NonNull
    @Override
    public Result doWork() {
        String userId = api.getCurrentUserId();
        if (userId == null) {
            Log.w(TAG, "用户未登录，跳过预算同步");
            return Result.success();
        }

        List<Budget> pending = db.budgetDao().getPendingSyncBudgets();
        if (pending == null || pending.isEmpty()) {
            Log.d(TAG, "没有待同步的预算");
            return Result.success();
        }

        Log.d(TAG, "开始同步预算，共 " + pending.size() + " 条");

        boolean allOk = true;

        // 第一轮：优先处理 TO_DELETE（与 BillSyncWorker 保持一致）
        for (Budget b : pending) {
            if (b.getSyncState() != SyncState.TO_DELETE.getValue()) continue;
            if (!handleDelete(b)) allOk = false;
        }

        // 第二轮：处理 TO_CREATE / TO_UPDATE
        for (Budget b : pending) {
            int state = b.getSyncState();
            if (state == SyncState.TO_DELETE.getValue()) continue;

            boolean ok;
            if (state == SyncState.TO_CREATE.getValue()) {
                ok = handleCreate(b, userId);
            } else if (state == SyncState.TO_UPDATE.getValue()) {
                ok = handleUpdate(b, userId);
            } else {
                Log.w(TAG, "未知 syncState=" + state + "，跳过 id=" + b.getId());
                ok = true;
            }

            if (!ok) {
                allOk = false;
            } else {
                // 同步成功，标记 SYNCED（handleCreate 成功后会通过 updateCloudId 一起写，此处补全其他字段）
                if (state == SyncState.TO_UPDATE.getValue()) {
                    b.setSyncState(SyncState.SYNCED.getValue());
                    db.budgetDao().update(b);
                }
            }
        }

        if (!allOk) {
            Log.w(TAG, "部分预算同步失败，Worker 将重试");
            return Result.retry();
        }

        Log.d(TAG, "预算同步全部完成");
        return Result.success();
    }

    // =====================================================================
    //  各状态处理
    // =====================================================================

    /**
     * 处理 TO_CREATE。
     * 防重复二次校验：从 DB 重新读取最新记录，若已有 cloudId 则转 update。
     */
    private boolean handleCreate(Budget budget, String userId) {
        // 二次校验：防止并发 Worker 实例重复上传
        Budget latest = db.budgetDao().getById(budget.getId());
        if (latest == null) {
            Log.d(TAG, "记录已被删除，跳过 id=" + budget.getId());
            return true;
        }
        if (latest.getCloudId() != null && !latest.getCloudId().isEmpty()) {
            Log.d(TAG, "cloudId 已存在，转为 update cloudId=" + latest.getCloudId());
            budget.setCloudId(latest.getCloudId());
            return handleUpdate(budget, userId);
        }

        boolean ok = api.uploadBudgetSync(budget);
        if (ok) {
            // uploadBudgetSync 成功后 budget.cloudId 已被赋值，通过 updateCloudId 原子写回
            db.budgetDao().updateCloudId(
                    budget.getId(), budget.getCloudId(), SyncState.SYNCED.getValue());
            Log.d(TAG, "✅ 预算创建并上传成功 cloudId=" + budget.getCloudId());
        } else {
            Log.e(TAG, "❌ 预算上传失败 id=" + budget.getId());
        }
        return ok;
    }

    /**
     * 处理 TO_UPDATE。无 cloudId 时降级为 handleCreate。
     */
    private boolean handleUpdate(Budget budget, String userId) {
        if (budget.getCloudId() == null || budget.getCloudId().isEmpty()) {
            Log.w(TAG, "TO_UPDATE 但 cloudId 为空，降级为 create id=" + budget.getId());
            return handleCreate(budget, userId);
        }
        boolean ok = api.updateBudgetSync(budget, userId);
        if (ok) {
            Log.d(TAG, "✅ 预算更新成功 cloudId=" + budget.getCloudId());
        } else {
            Log.e(TAG, "❌ 预算更新失败 cloudId=" + budget.getCloudId());
        }
        return ok;
    }

    /**
     * 处理 TO_DELETE。
     * 有 cloudId → 先删云端再删本地；无 cloudId → 直接删本地。
     */
    private boolean handleDelete(Budget budget) {
        if (budget.getCloudId() == null || budget.getCloudId().isEmpty()) {
            db.budgetDao().deleteById(budget.getId());
            Log.d(TAG, "✅ 无 cloudId，直接本地删除 id=" + budget.getId());
            return true;
        }

        boolean ok = api.deleteBudgetSync(budget.getCloudId());
        if (ok) {
            db.budgetDao().deleteById(budget.getId());
            Log.d(TAG, "✅ 云端和本地预算均已删除 cloudId=" + budget.getCloudId());
        } else {
            Log.e(TAG, "❌ 云端预算删除失败，保留待删除标记 cloudId=" + budget.getCloudId());
        }
        return ok;
    }

    // =====================================================================
    //  静态入队
    // =====================================================================

    /**
     * 触发一次预算同步任务。
     * 使用唯一任务名 + KEEP 策略，队列中已有同名任务时不重复入队。
     */
    public static void enqueue(Context context) {
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        new OneTimeWorkRequest.Builder(BudgetSyncWorker.class).build());
    }
}