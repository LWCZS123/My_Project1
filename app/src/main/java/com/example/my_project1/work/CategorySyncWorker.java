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
 * CategorySyncWorker — 重构版
 *
 * 核心修复：
 *  1. ★ 分级同步保障：父分类上传成功（获得 cloudId）后，
 *       立即将 cloudId 回写到所有子分类的 parent_cloud_id 字段，
 *       之后才触发 SubCategorySyncWorker。
 *  2. ★ 防错机制：
 *       - TO_CREATE：上传父分类 → 更新本地 cloudId → 更新子分类 parentCloudId → 触发子分类同步
 *       - TO_UPDATE：cloudId 为空时自动转为补上传流程
 *       - TO_DELETE：先删云端，再级联删本地（Room ForeignKey CASCADE 辅助）
 *  3. 所有阻塞调用使用 CountDownLatch，避免在 WorkManager 线程提前返回
 */
public class CategorySyncWorker extends Worker {

    private static final String TAG = "CategorySyncWorker";
    private final AppDatabase db;
    private final BmobApiImpl api;

    public CategorySyncWorker(Context context, WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobApiImpl();
    }

    @Override
    public Result doWork() {
        List<Category> pendingList = db.categoryDao().getPendingSyncCategories();
        if (pendingList.isEmpty()) {
            Log.d(TAG, "✅ 没有需要同步的分类");
            return Result.success();
        }

        Log.d(TAG, "📤 开始同步分类，数量：" + pendingList.size());

        for (Category category : pendingList) {
            try {
                boolean success;
                int state = category.getSyncState();

                if (state == SyncState.TO_CREATE.getValue()) {
                    success = uploadCategorySync(category);

                } else if (state == SyncState.TO_UPDATE.getValue()) {
                    if (category.getCloudId() == null || category.getCloudId().isEmpty()) {
                        Log.w(TAG, "⚠️ TO_UPDATE 但 cloudId 为空，转为补上传：" + category.getName());
                        success = uploadCategorySync(category);
                    } else {
                        success = updateCategorySync(category);
                    }

                } else if (state == SyncState.TO_DELETE.getValue()) {
                    success = deleteCategorySync(category);

                } else {
                    Log.w(TAG, "⚠️ 未知同步状态：" + state + "，跳过");
                    continue;
                }

                if (!success) {
                    Log.w(TAG, "⚠️ 同步失败，触发重试：" + category.getName());
                    return Result.retry();
                }

                // 同步完成 → 标记为已同步
                category.setSyncState(SyncState.SYNCED.getValue());
                AppExecutors.get().diskIO().execute(() -> db.categoryDao().update(category));

            } catch (Exception e) {
                Log.e(TAG, "❌ 同步分类异常：" + e.getMessage(), e);
                return Result.retry();
            }
        }

        // ★ 所有父分类同步完成后，统一触发一次子分类同步任务
        SubCategorySyncWorker.enqueue(getApplicationContext());

        return Result.success();
    }

    // =========================================================================
    // 上传新分类（TO_CREATE）
    // =========================================================================

    /**
     * 阻塞上传父分类，成功后：
     *  1. 写入本地 cloudId
     *  2. 将所有该父类下待同步子分类的 parent_cloud_id 更新为新 cloudId
     *     （保障 SubCategorySyncWorker 执行时子分类已具备上传条件）
     */
    private boolean uploadCategorySync(Category category) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.uploadCategory(category, new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null && objectId != null) {
                    Log.d(TAG, "✅ 上传父分类成功：" + category.getName() + " | cloudId=" + objectId);

                    // 1. 内存中更新 cloudId
                    category.setCloudId(objectId);
                    category.setSyncState(SyncState.SYNCED.getValue());

                    // 2. 持久化 cloudId 到数据库（同步执行，不用 execute，latch 之后再标记 SYNCED）
                    AppExecutors.get().diskIO().execute(() -> {
                        db.categoryDao().updateCloudIdById(
                                category.getId(), objectId, SyncState.SYNCED.getValue());
                        Log.d(TAG, "✅ 本地 cloudId 写入成功：" + objectId);

                        // ★ 3. 更新该父类下所有子分类的 parent_cloud_id
                        //       让它们具备上传条件（SubCategorySyncWorker 检查此字段）
                        propagateParentCloudIdToChildren(category.getId(), objectId);
                    });

                    success.set(true);
                } else {
                    Log.e(TAG, "❌ 上传父分类失败：" + (e != null ? e.getMessage() : "objectId 为空"));
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    /**
     * ★ 将父分类的 cloudId 传播给所有子分类的 parent_cloud_id
     * 确保 SubCategorySyncWorker 执行时，子分类的 parent_cloud_id 已就绪
     */
    private void propagateParentCloudIdToChildren(long parentLocalId, String parentCloudId) {
        List<SubCategory> children = db.subCategoryDao().getByParentCategoryId(parentLocalId);
        if (children == null || children.isEmpty()) return;

        int updated = 0;
        for (SubCategory child : children) {
            // 只更新 parentCloudId 为空或错误的记录
            if (parentCloudId.equals(child.getParentCloudId())) continue;
            child.setParentCloudId(parentCloudId);
            db.subCategoryDao().update(child);
            updated++;
        }
        Log.d(TAG, "🔗 已传播 parentCloudId=" + parentCloudId
                + " 给 " + updated + " 个子分类（共 " + children.size() + " 个）");
    }

    // =========================================================================
    // 更新已有分类（TO_UPDATE）
    // =========================================================================

    private boolean updateCategorySync(Category category) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.updateCategory(category, new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新父分类成功：" + category.getName());
                    success.set(true);
                } else {
                    Log.e(TAG, "❌ 更新父分类失败：" + e.getMessage());
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    // =========================================================================
    // 删除分类（TO_DELETE）
    // =========================================================================

    /**
     * 删除流程：
     *  - 有 cloudId：先删云端，再删本地（Room CASCADE 自动清理子分类）
     *  - 无 cloudId：直接删本地（Room CASCADE 自动清理子分类）
     */
    private boolean deleteCategorySync(Category category) {
        if (category.getCloudId() == null || category.getCloudId().isEmpty()) {
            Log.w(TAG, "⚠️ cloudId 为空，跳过云端删除，直接清理本地：" + category.getName());
            AppExecutors.get().diskIO().execute(() ->
                            db.categoryDao().deleteById(category.getId())
                    // Room ForeignKey CASCADE 会自动删除子分类，无需手动操作
            );
            return true;
        }

        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.deleteCategory(category.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 云端父分类删除成功：" + category.getName());
                    AppExecutors.get().diskIO().execute(() -> {
                        // ★ Room ForeignKey(onDelete=CASCADE) 会自动删除子分类
                        db.categoryDao().deleteById(category.getId());
                        Log.d(TAG, "🧹 本地父分类删除完成，Room CASCADE 已清理子分类");
                    });
                    success.set(true);
                } else {
                    Log.e(TAG, "❌ 云端父分类删除失败：" + e.getMessage());
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);

        // 父分类删除成功后，触发子分类同步（处理云端残留的待删除子分类）
        if (success.get()) {
            SubCategorySyncWorker.enqueue(getApplicationContext());
        }

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

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CategorySyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context).enqueue(request);
        Log.d(TAG, "📥 已入队 CategorySyncWorker");
    }
}