package com.example.my_project1.data.remote.model.cloudbudget;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.budget.Budget;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * BmobBudgetApiImpl — 预算云端 CRUD 实现
 *
 * 参照 BmobBillApiImpl 的设计风格，封装所有与 Bmob 云端交互的逻辑：
 *   - 同步方法（Sync 后缀）：使用 CountDownLatch 将异步 Bmob 回调包装为阻塞调用，
 *     仅供 Worker / diskIO 线程调用，禁止在主线程调用。
 *   - 异步方法：直接传入 Bmob 原生 Listener，供 Repository 在 networkIO 中使用。
 *
 * 防重复策略：
 *   - uploadBudgetSync() 上传前检查本地 cloudId；若已存在则走更新流程，不重复创建。
 *   - 所有写操作完成后不在此处更新本地 DB，由调用方（BudgetSyncWorker）统一回写，
 *     避免多处更新导致状态混乱。
 */
public class BmobBudgetApiImpl {

    private static final String TAG              = "BmobBudgetApiImpl";
    private static final long   TIMEOUT_SECONDS  = 30L;
    private static final int    ERR_NOT_FOUND    = 101; // Bmob 云端对象不存在

    private final Context    context;
    private final AppDatabase db;

    public BmobBudgetApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db      = AppDatabase.getInstance(this.context);
    }

    // ── 工具 ──────────────────────────────────────────────────────────────

    /** 获取当前登录用户 ObjectId，未登录返回 null */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // =====================================================================
    // 🟢 上传（创建 / 更新）
    // =====================================================================

    /**
     * 同步上传单条预算（阻塞）。
     *
     * 逻辑：
     *   1. 若 local.cloudId 为空 → 执行 Bmob save（创建），成功后将 cloudId 写回 budget 对象。
     *   2. 若 local.cloudId 非空 → 执行 Bmob update（更新）。
     *   3. 上传成功后不更新本地 DB，由调用方 BudgetSyncWorker.doWork() 统一处理。
     *
     * @param local 本地 Budget 对象
     * @return 是否上传成功；成功时 local.cloudId 已被赋值（若为新建）
     */
    public boolean uploadBudgetSync(Budget local) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "❌ uploadBudgetSync — 用户未登录");
            return false;
        }

        // ── 防重复：cloudId 已存在则走更新流程 ────────────────────────────
        if (local.getCloudId() != null && !local.getCloudId().isEmpty()) {
            return updateBudgetSync(local, userId);
        }

        // ── 新建 ─────────────────────────────────────────────────────────
        Log.d(TAG, "🚀 uploadBudgetSync CREATE amount=" + local.getAmount()
                + " targetId=" + local.getTargetId());

        final boolean[]      ok    = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        CloudBudget cloud = CloudBudget.fromLocalBudget(local, userId);
        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null && objectId != null) {
                    Log.d(TAG, "✅ 云端创建成功 cloudId=" + objectId);
                    local.setCloudId(objectId);    // 回写到对象，调用方可读取
                    ok[0] = true;
                } else {
                    Log.e(TAG, "❌ 云端创建失败: " + (e != null ? e.getMessage() : "null objectId"));
                }
                latch.countDown();
            }
        });

        await(latch);
        return ok[0];
    }

    /**
     * 同步更新已有云端预算（阻塞）。
     */
    public boolean updateBudgetSync(Budget local, String userId) {
        if (local.getCloudId() == null || local.getCloudId().isEmpty()) {
            Log.e(TAG, "❌ updateBudgetSync — cloudId 为空，无法更新");
            return false;
        }
        Log.d(TAG, "🚀 updateBudgetSync cloudId=" + local.getCloudId()
                + " amount=" + local.getAmount());

        final boolean[]      ok    = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        CloudBudget cloud = CloudBudget.fromLocalBudget(local, userId);
        cloud.update(local.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 云端更新成功 cloudId=" + local.getCloudId());
                    ok[0] = true;
                } else {
                    Log.e(TAG, "❌ 云端更新失败: " + e.getMessage());
                }
                latch.countDown();
            }
        });

        await(latch);
        return ok[0];
    }

    // =====================================================================
    // 🔴 删除
    // =====================================================================

    /**
     * 同步删除云端预算（阻塞）。
     *
     * 云端对象不存在（错误码 101）时视为删除成功，调用方可安心清理本地记录。
     *
     * @param cloudId 云端 objectId
     * @return 是否删除成功（含"对象不存在"的幂等成功）
     */
    public boolean deleteBudgetSync(String cloudId) {
        if (cloudId == null || cloudId.isEmpty()) {
            Log.e(TAG, "❌ deleteBudgetSync — cloudId 为空");
            return false;
        }
        Log.d(TAG, "🗑️ deleteBudgetSync cloudId=" + cloudId);

        final boolean[]      ok         = {false};
        final int[]          errorCode  = {0};
        final CountDownLatch latch      = new CountDownLatch(1);

        CloudBudget cloud = new CloudBudget();
        cloud.setObjectId(cloudId);
        cloud.delete(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 云端删除成功 cloudId=" + cloudId);
                    ok[0] = true;
                } else {
                    errorCode[0] = e.getErrorCode();
                    Log.e(TAG, "❌ 云端删除失败 cloudId=" + cloudId
                            + " code=" + errorCode[0] + " msg=" + e.getMessage());
                }
                latch.countDown();
            }
        });

        boolean completed = await(latch);
        if (!completed) {
            Log.w(TAG, "⚠️ deleteBudgetSync 超时 cloudId=" + cloudId);
            return false;
        }

        // 幂等：对象不存在视为成功
        if (!ok[0] && errorCode[0] == ERR_NOT_FOUND) {
            Log.d(TAG, "✅ 云端对象已不存在，视为删除成功 cloudId=" + cloudId);
            return true;
        }
        return ok[0];
    }

    /**
     * 异步删除云端预算，结果通过 listener 回调。
     */
    public void deleteBudget(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(902, "cloudId 为空"));
            return;
        }
        CloudBudget cloud = new CloudBudget();
        cloud.setObjectId(cloudId);
        cloud.delete(listener);
    }

    // =====================================================================
    // 🟡 查询
    // =====================================================================

    /**
     * 异步拉取当前用户的全部预算，结果通过 listener 回调。
     */
    public void fetchAllBudgets(FindListener<CloudBudget> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(903, "用户未登录"));
            return;
        }

        BmobUser user = new BmobUser();
        user.setObjectId(userId);

        BmobQuery<CloudBudget> query = new BmobQuery<>();
        query.addWhereEqualTo("ownerId", user);
        query.setLimit(500);
        query.findObjects(listener);
    }

    /**
     * 同步拉取当前用户的全部预算（阻塞），返回云端列表；失败抛异常。
     */
    public List<CloudBudget> fetchAllBudgetsSync() throws Exception {
        String userId = getCurrentUserId();
        if (userId == null) throw new IllegalStateException("用户未登录");

        BmobUser user = new BmobUser();
        user.setObjectId(userId);

        BmobQuery<CloudBudget> query = new BmobQuery<>();
        query.addWhereEqualTo("ownerId", user);
        query.setLimit(500);
        return query.findObjectsSync(CloudBudget.class);
    }

    // =====================================================================
    // 🔧 内部工具
    // =====================================================================

    /**
     * 等待 latch 计数归零，超时返回 false。
     */
    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}