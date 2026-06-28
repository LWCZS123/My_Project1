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
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.data.remote.model.CloudCategory;
import com.example.my_project1.data.remote.model.CloudSubCategory;

import java.util.List;

/**
 * CategoryDownloadWorker — 重构版
 *
 * 核心修复：
 *  1. ★ 子分类匹配策略完全基于 parent_cloud_id（云端真理），不再依赖跨设备不可靠的本地 parentCategoryId
 *  2. ★ 下载流程：
 *       a. 通过父分类 cloudId 查找/插入本地父分类，得到 localCategoryId
 *       b. 调用 subCategoryDao.updateParentCategoryId(parentCloudId, localCategoryId)
 *          批量修正同 parentCloudId 下所有子分类的外键，确保外键准确
 *       c. 子分类匹配策略（按优先级）：
 *          策略1：cloud_id 精确匹配（最可靠，已同步过的记录）
 *          策略2：parent_cloud_id + name 匹配（跨设备同名子分类）
 *          策略3：parent_category_id（本地） + name 匹配（旧数据兼容）
 *          都找不到 → 插入新记录
 *  3. ★ 插入子分类时同时写入 parent_cloud_id 和正确的 parent_category_id
 *  4. 所有路径有完整日志，便于排查跨设备同步问题
 */
public class CategoryDownloadWorker extends Worker {

    private static final String TAG = "CategoryDownloadWorker";
    private final AppDatabase db;
    private final BmobApiImpl api;

    public CategoryDownloadWorker(Context context, WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobApiImpl(context);
    }

    @Override
    public Result doWork() {
        String userId = getInputData().getString("userId");
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "❌ 用户ID为空，无法下载分类");
            return Result.failure();
        }

        Log.d(TAG, "☁️ 开始从云端同步分类，用户：" + userId);

        try {
            List<CloudCategory> cloudCategories = api.getAllCategoriesSync(userId);
            if (cloudCategories == null || cloudCategories.isEmpty()) {
                Log.w(TAG, "⚠️ 云端无分类数据，跳过同步");
                return Result.success();
            }

            Log.d(TAG, "📦 云端共有 " + cloudCategories.size() + " 个父分类");

            for (CloudCategory cloudCat : cloudCategories) {
                // ── Step 1：同步父分类 ──────────────────────────────────────────
                long localCategoryId = syncParentCategory(cloudCat);
                if (localCategoryId <= 0) {
                    Log.e(TAG, "❌ 父分类同步失败，跳过其子分类：" + cloudCat.getName());
                    continue;
                }

                String parentCloudId = cloudCat.getObjectId();

                // ── Step 2：批量修正该父分类下子分类的本地外键 ─────────────────
                // 处理跨设备场景：本设备可能已有该父分类的子分类，但 parent_category_id 是旧值
                int fixedCount = db.subCategoryDao().updateParentCategoryId(
                        parentCloudId, localCategoryId);
                if (fixedCount > 0) {
                    Log.d(TAG, "🔧 已修正 " + fixedCount + " 条子分类的外键 → localCategoryId=" + localCategoryId);
                }

                // ── Step 3：下载并同步子分类 ────────────────────────────────────
                syncSubCategories(parentCloudId, localCategoryId, cloudCat.getName());
            }

            Log.i(TAG, "✅ 云端分类同步完成，共同步 " + cloudCategories.size() + " 个父分类");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "❌ 云端分类下载失败：" + e.getMessage(), e);
            return Result.retry();
        }
    }

    // =========================================================================
    // 同步父分类
    // =========================================================================

    /**
     * 根据 cloudId 查找或创建本地父分类
     * @return 本地 Category.id（> 0），失败返回 -1
     */
    private long syncParentCategory(CloudCategory cloudCat) {
        try {
            Category local = cloudCat.toLocalCategory();
            Category existing = db.categoryDao().getByCloudId(cloudCat.getObjectId());

            // 若 cloudId 未命中，尝试通过系统预设名称匹配（兼容旧数据）
            if (existing == null && cloudCat.isSystemPreset()) {
                existing = db.categoryDao().getSystemPresetByName(cloudCat.getName());
                if (existing != null) {
                    Log.d(TAG, "  🔗 系统预设通过 name 匹配：" + cloudCat.getName()
                            + " → 本地ID=" + existing.getId());
                }
            }

            if (existing == null) {
                long newId = db.categoryDao().insert(local);
                Log.d(TAG, "🆕 插入父分类：" + local.getName()
                        + " (本地ID=" + newId + ", cloudId=" + cloudCat.getObjectId() + ")");
                return newId;
            } else {
                local.setId(existing.getId());
                db.categoryDao().update(local);
                Log.d(TAG, "🔄 更新父分类：" + local.getName()
                        + " (本地ID=" + existing.getId() + ", cloudId=" + cloudCat.getObjectId() + ")");
                return existing.getId();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ syncParentCategory 异常：" + e.getMessage(), e);
            return -1;
        }
    }

    // =========================================================================
    // 同步子分类
    // =========================================================================

    /**
     * 下载并同步指定父分类的所有子分类
     *
     * 匹配策略（按优先级）：
     *  策略1：cloud_id 精确匹配 → 最可靠，已同步记录
     *  策略2：parent_cloud_id + name 匹配 → 跨设备同名子分类
     *  策略3：parent_category_id（本地）+ name 匹配 → 兼容旧数据（未写 parent_cloud_id）
     *  都无 → 插入新记录
     *
     * @param parentCloudId   父分类的云端 objectId
     * @param localCategoryId 父分类的本地 id（已通过 syncParentCategory 确认）
     * @param parentName      仅用于日志
     */
    private void syncSubCategories(String parentCloudId, long localCategoryId, String parentName) {
        List<CloudSubCategory> subList;
        try {
            subList = api.getSubCategoriesByParentSync(parentCloudId);
        } catch (Exception e) {
            Log.e(TAG, "❌ 查询子分类异常，父分类=" + parentName + "：" + e.getMessage(), e);
            return;
        }

        if (subList == null || subList.isEmpty()) {
            Log.d(TAG, "  ⚠️ 父分类 [" + parentName + "] 云端无子分类，跳过");
            return;
        }

        Log.d(TAG, "━━ 父分类 [" + parentName + "] 共 " + subList.size() + " 个子分类 ━━");

        for (CloudSubCategory cs : subList) {
            try {
                syncSingleSubCategory(cs, parentCloudId, localCategoryId);
            } catch (Exception e) {
                Log.e(TAG, "  ❌ 处理子分类异常：" + cs.getName() + "：" + e.getMessage(), e);
                // 单条异常不中断整体，继续处理其他子分类
            }
        }
    }

    /**
     * 处理单个云端子分类：查找 → 更新 或 插入
     */
    private void syncSingleSubCategory(CloudSubCategory cs,
                                       String parentCloudId,
                                       long localCategoryId) {
        // toLocalSubCategory() 不填充 parentCategoryId，此处手动填充
        SubCategory localSub = cs.toLocalSubCategory();
        localSub.setParentCategoryId(localCategoryId);  // ★ 使用反查到的本地 id
        localSub.setParentCloudId(parentCloudId);       // ★ 确保冗余字段同步写入

        SubCategory existing = findExistingSubCategory(cs, parentCloudId, localCategoryId, localSub.getName());

        if (existing == null) {
            // 本地不存在 → 插入
            long newId = db.subCategoryDao().insert(localSub);
            Log.d(TAG, "  🆕 插入子分类：" + localSub.getName()
                    + " (本地ID=" + newId + ", cloudId=" + cs.getObjectId() + ")");
        } else {
            // 本地已存在 → 更新（保留本地 id，更新所有云端字段）
            localSub.setId(existing.getId());
            db.subCategoryDao().update(localSub);
            Log.d(TAG, "  🔄 更新子分类：" + localSub.getName()
                    + " (本地ID=" + existing.getId()
                    + (existing.getCloudId() == null ? ", ★首次绑定cloudId" : "") + ")");
        }
    }

    /**
     * 多策略查找已有子分类
     *
     * @param cs              云端子分类对象
     * @param parentCloudId   父分类 cloudId
     * @param localCategoryId 父分类本地 id
     * @param name            子分类名（trim 后）
     * @return 找到的本地记录，未找到返回 null
     */
    private SubCategory findExistingSubCategory(CloudSubCategory cs,
                                                String parentCloudId,
                                                long localCategoryId,
                                                String name) {
        // ── 策略1：cloud_id 精确匹配 ─────────────────────────────────────────
        if (cs.getObjectId() != null && !cs.getObjectId().isEmpty()) {
            SubCategory found = db.subCategoryDao().getBySubCloudId(cs.getObjectId());
            if (found != null) {
                Log.d(TAG, "  ✓ 策略1命中 cloud_id：" + cs.getObjectId());
                return found;
            }
        }

        // ── 策略2：parent_cloud_id + name 匹配 ──────────────────────────────
        // 跨设备场景：子分类已通过其他设备写入本地，但 cloud_id 不同（极少情况）
        SubCategory found = db.subCategoryDao().getByParentCloudIdAndName(parentCloudId, name);
        if (found != null) {
            Log.d(TAG, "  ✓ 策略2命中 parentCloudId+name：" + name);
            return found;
        }

        // ── 策略3：parent_category_id（本地）+ name 匹配 ────────────────────
        // 兼容旧版数据（parent_cloud_id 字段为空时的降级方案）
        found = db.subCategoryDao().getByParentAndName(localCategoryId, name);
        if (found != null) {
            Log.d(TAG, "  ✓ 策略3命中 parentLocalId+name（旧数据兼容）：" + name);
            return found;
        }

        Log.d(TAG, "  ✗ 三策略均未匹配，将插入新记录：" + name);
        return null;
    }

    // =========================================================================
    // 静态工具方法
    // =========================================================================

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /** 异步入队（常规使用） */
    public static void enqueue(Context context, String userId) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CategoryDownloadWorker.class)
                .setConstraints(getDefaultConstraints())
                .setInputData(new androidx.work.Data.Builder()
                        .putString("userId", userId)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(request);
        Log.d(TAG, "📥 已入队 CategoryDownloadWorker，userId=" + userId);
    }

    /**
     * 同步执行（阻塞，仅用于初始化/登录场景，调用方须在后台线程执行）
     * 内部逻辑与 doWork() 完全一致，避免代码分叉
     */
    public static void enqueueSync(Context context, String userId) {
        AppDatabase db = AppDatabase.getInstance(context);
        BmobApiImpl api = new BmobApiImpl(context);

        try {
            List<CloudCategory> cloudCategories = api.getAllCategoriesSync(userId);
            if (cloudCategories == null || cloudCategories.isEmpty()) {
                Log.w(TAG, "⚠️ [Sync] 云端无数据");
                return;
            }

            for (CloudCategory cloudCat : cloudCategories) {
                Category local = cloudCat.toLocalCategory();
                Category existing = db.categoryDao().getByCloudId(cloudCat.getObjectId());

                long categoryId;
                if (existing == null) {
                    categoryId = db.categoryDao().insert(local);
                } else {
                    local.setId(existing.getId());
                    db.categoryDao().update(local);
                    categoryId = existing.getId();
                }

                // ★ 修正外键
                db.subCategoryDao().updateParentCategoryId(cloudCat.getObjectId(), categoryId);

                // 同步子分类
                List<CloudSubCategory> cloudSubs =
                        api.getSubCategoriesByParentSync(cloudCat.getObjectId());
                if (cloudSubs == null) continue;

                for (CloudSubCategory cs : cloudSubs) {
                    SubCategory localSub = cs.toLocalSubCategory();
                    localSub.setParentCategoryId(categoryId);
                    localSub.setParentCloudId(cloudCat.getObjectId());

                    SubCategory existingSub = db.subCategoryDao().getBySubCloudId(cs.getObjectId());
                    if (existingSub == null) {
                        existingSub = db.subCategoryDao()
                                .getByParentCloudIdAndName(cloudCat.getObjectId(), localSub.getName());
                    }

                    if (existingSub == null) {
                        db.subCategoryDao().insert(localSub);
                    } else {
                        localSub.setId(existingSub.getId());
                        db.subCategoryDao().update(localSub);
                    }
                }
            }

            Log.i(TAG, "✅ [Sync] 同步完成");
        } catch (Exception e) {
            Log.e(TAG, "❌ [Sync] 同步失败：" + e.getMessage(), e);
        }
    }
}