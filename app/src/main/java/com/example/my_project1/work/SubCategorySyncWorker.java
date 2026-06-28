package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.utils.AppExecutors;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * SubCategorySyncWorker — 重构版
 *
 * 核心修复：
 *  1. ★ 上传前强制检查 parent_cloud_id
 *       若父分类尚未同步（parent_cloud_id 为空），返回 Result.retry()
 *       让 WorkManager 稍后重试，而非上传一条"孤儿"子分类到云端
 *
 *  2. ★ parent_cloud_id 自动补全逻辑
 *       如果 parent_cloud_id 为空但能从本地父分类查到 cloudId，
 *       自动补全后再上传，避免无谓的 retry
 *
 *  3. 删除时：有 cloudId → 先删云端再删本地；无 cloudId → 直接删本地
 *
 *  4. 所有阻塞调用使用 CountDownLatch + AtomicBoolean
 */
public class SubCategorySyncWorker extends Worker {

    private static final String TAG = "SubCategorySyncWorker";
    private final AppDatabase db;
    private final BmobApiImpl api;

    public SubCategorySyncWorker(Context context, WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobApiImpl();
    }

    @Override
    public Result doWork() {
        List<SubCategory> pendingList = db.subCategoryDao().getPendingSyncSubCategories();
        if (pendingList.isEmpty()) {
            Log.d(TAG, "✅ 没有需要同步的子分类");
            return Result.success();
        }

        Log.d(TAG, "📤 开始同步子分类，数量：" + pendingList.size());

        boolean needRetry = false;

        for (SubCategory sub : pendingList) {
            try {
                int state = sub.getSyncState();
                boolean success;

                if (state == SyncState.TO_CREATE.getValue()) {
                    // ★ 上传前检查：确保 parent_cloud_id 已就绪
                    Result guardResult = guardParentCloudId(sub);
                    if (guardResult != null) {
                        // guardResult == retry：父类尚未同步，跳过本条，继续处理其他子分类
                        needRetry = true;
                        Log.w(TAG, "⏳ 父分类未同步，跳过子分类：" + sub.getName());
                        continue;
                    }
                    success = uploadSubCategorySync(sub);

                } else if (state == SyncState.TO_UPDATE.getValue()) {
                    if (sub.getCloudId() == null || sub.getCloudId().isEmpty()) {
                        // 补上传：先确认 parent_cloud_id
                        Result guardResult = guardParentCloudId(sub);
                        if (guardResult != null) {
                            needRetry = true;
                            Log.w(TAG, "⏳ 父分类未同步（TO_UPDATE 补上传），跳过：" + sub.getName());
                            continue;
                        }
                        Log.w(TAG, "⚠️ TO_UPDATE 但 cloudId 为空，转补上传：" + sub.getName());
                        success = uploadSubCategorySync(sub);
                    } else {
                        success = updateSubCategorySync(sub);
                    }

                } else if (state == SyncState.TO_DELETE.getValue()) {
                    success = deleteSubCategorySync(sub);

                } else {
                    Log.w(TAG, "⚠️ 未知同步状态：" + state + "，跳过");
                    continue;
                }

                if (!success) {
                    Log.w(TAG, "⚠️ 子分类同步失败，触发重试：" + sub.getName());
                    return Result.retry();
                }

                // 同步成功 → 标记为 SYNCED
                sub.setSyncState(SyncState.SYNCED.getValue());
                AppExecutors.get().diskIO().execute(() -> db.subCategoryDao().update(sub));

            } catch (Exception e) {
                Log.e(TAG, "❌ 同步子分类异常：" + e.getMessage(), e);
                return Result.retry();
            }
        }

        // 如果有因父类未同步而跳过的子分类，返回 retry
        return needRetry ? Result.retry() : Result.success();
    }

    // =========================================================================
    // 守卫：确保 parent_cloud_id 已就绪
    // =========================================================================

    /**
     * 检查子分类的 parent_cloud_id 是否已就绪
     *
     * 策略：
     *  1. 若 parentCloudId 已有值 → 直接放行（返回 null）
     *  2. 若为空，尝试从本地父分类查找 cloudId 并自动补全
     *  3. 若父分类也没有 cloudId → 返回 Result.retry()（父类还没上传完）
     *
     * @return null 表示可以继续上传；非 null 表示需要等待（调用方应 continue/retry）
     */
    private Result guardParentCloudId(SubCategory sub) {
        if (sub.getParentCloudId() != null && !sub.getParentCloudId().isEmpty()) {
            return null; // 已就绪
        }

        // 尝试从本地父分类补全
        if (sub.getParentCategoryId() > 0) {
            Category parent = db.categoryDao().getCategoryById(sub.getParentCategoryId());
            if (parent != null && parent.getCloudId() != null && !parent.getCloudId().isEmpty()) {
                // 自动补全：更新内存对象和数据库
                sub.setParentCloudId(parent.getCloudId());
                AppExecutors.get().diskIO().execute(() -> db.subCategoryDao().update(sub));
                Log.d(TAG, "🔗 自动补全 parentCloudId=" + parent.getCloudId()
                        + " → 子分类：" + sub.getName());
                return null; // 补全成功，可以继续
            }
        }

        // 父分类 cloudId 仍为空 → 需要等待 CategorySyncWorker 先执行
        Log.w(TAG, "⏳ parentCloudId 为空且父分类未同步，需等待重试：" + sub.getName());
        return Result.retry();
    }

    // =========================================================================
    // 上传新子分类（TO_CREATE）
    // =========================================================================

    private boolean uploadSubCategorySync(SubCategory sub) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.uploadSubCategory(sub, new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null && objectId != null) {
                    Log.d(TAG, "✅ 上传子分类成功：" + sub.getName() + " | cloudId=" + objectId);

                    sub.setCloudId(objectId);
                    sub.setSyncState(SyncState.SYNCED.getValue());

                    AppExecutors.get().diskIO().execute(() -> {
                        db.subCategoryDao().updateSubCloudIdById(
                                sub.getId(), objectId, SyncState.SYNCED.getValue());
                        Log.d(TAG, "✅ 本地 cloudId 写入成功：" + objectId);
                    });

                    success.set(true);
                } else {
                    Log.e(TAG, "❌ 上传子分类失败：" + (e != null ? e.getMessage() : "objectId 为空"));
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    // =========================================================================
    // 更新已有子分类（TO_UPDATE）
    // =========================================================================

    private boolean updateSubCategorySync(SubCategory sub) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.updateSubCategory(sub, new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新子分类成功：" + sub.getName());
                    success.set(true);
                } else {
                    Log.e(TAG, "❌ 更新子分类失败：" + e.getMessage());
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    // =========================================================================
    // 删除子分类（TO_DELETE）
    // =========================================================================

    private boolean deleteSubCategorySync(SubCategory sub) {
        if (sub.getCloudId() == null || sub.getCloudId().isEmpty()) {
            Log.w(TAG, "⚠️ cloudId 为空，直接删除本地：" + sub.getName());
            AppExecutors.get().diskIO().execute(() ->
                    db.subCategoryDao().deleteSubById(sub.getId()));
            return true;
        }

        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.deleteSubCategory(sub.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 云端子分类删除成功：" + sub.getName());
                    AppExecutors.get().diskIO().execute(() -> {
                        db.subCategoryDao().deleteSubById(sub.getId());
                        Log.d(TAG, "🧹 本地子分类删除完成");
                    });
                    success.set(true);
                } else {
                    Log.e(TAG, "❌ 云端子分类删除失败：" + e.getMessage());
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "⚠️ CountDownLatch 被中断");
        }
    }

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /** 外部入队入口（CategorySyncWorker 在父分类全部同步后调用） */
    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SubCategorySyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context).enqueue(request);
        Log.d(TAG, "📥 已入队 SubCategorySyncWorker");
    }
}