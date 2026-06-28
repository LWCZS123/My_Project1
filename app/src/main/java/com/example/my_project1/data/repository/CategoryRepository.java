package com.example.my_project1.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.SubCategoryDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.data.remote.model.CloudCategory;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.CategorySyncWorker;

import java.util.List;
import java.util.function.Consumer;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;

/**
 * CategoryRepository — 修复版
 *
 * 修复要点：
 *  1. ★ insertAll() 批量写入后，触发一次 CategorySyncWorker。
 *     CategorySyncWorker 在父分类上传成功后会自动调用
 *     propagateParentCloudIdToChildren()，将 cloudId 传递给所有子分类，
 *     使子分类获得上传条件。
 *
 *  2. ★ deleteCategoryById 不再手动调用 markSubCategoriesToDelete，
 *     Room ForeignKey(onDelete=CASCADE) 会自动清理子分类（已在上一轮重构中设置）。
 *     保留手动标记逻辑作为兼容，防止旧数据库版本没有外键约束时失效。
 *
 *  3. 新增 getCategoryByCloudId() 供 SubCategoryRepository 反查使用。
 */
public class CategoryRepository {

    private static final String TAG = "CategoryRepo";

    private final CategoryDao    categoryDao;
    private final SubCategoryDao subCategoryDao;
    private final WorkManager    workManager;

    public CategoryRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        categoryDao    = db.categoryDao();
        subCategoryDao = db.subCategoryDao();
        workManager    = WorkManager.getInstance(context);
    }

    public CategoryRepository(CategoryDao categoryDao,
                              SubCategoryDao subCategoryDao,
                              WorkManager workManager) {
        this.categoryDao    = categoryDao;
        this.subCategoryDao = subCategoryDao;
        this.workManager    = workManager;
    }

    // =========================================================================
    // 写操作
    // =========================================================================

    /** 插入单条分类，触发一次同步 */
    public void insert(Category category) {
        AppExecutors.get().diskIO().execute(() -> {
            category.setSyncState(SyncState.TO_CREATE.getValue());
            categoryDao.insert(category);
            enqueueSync();
        });
    }

    /**
     * 批量插入分类（核心方法）。
     *
     * ★ 重要：批量写入后触发 CategorySyncWorker。
     *   Worker 在每个父分类上传成功后会调用 propagateParentCloudIdToChildren()，
     *   自动将 cloudId 传递给子分类，使子分类具备上传条件。
     *   这是解决"父分类先于子分类同步"问题的关键链路。
     */
    public void insertAll(List<Category> categories) {
        if (categories == null || categories.isEmpty()) return;
        AppExecutors.get().diskIO().execute(() -> {
            for (Category cat : categories) {
                cat.setSyncState(SyncState.TO_CREATE.getValue());
            }
            long[] ids = categoryDao.insertCategories(categories);

            // 将自增 id 回填到对象（供调用方使用）
            for (int i = 0; i < ids.length && i < categories.size(); i++) {
                if (ids[i] > 0) categories.get(i).setId(ids[i]);
            }

            Log.d(TAG, "insertAll: 批量写入 " + categories.size() + " 条");
            // ★ 触发一次 Worker，处理所有待同步父分类（包括本批次新增的）
            enqueueSync();
        });
    }

    /** 更新分类 */
    public void update(Category category) {
        AppExecutors.get().diskIO().execute(() -> {
            category.setSyncState(SyncState.TO_UPDATE.getValue());
            categoryDao.update(category);
            enqueueSync();
        });
    }

    /** 删除分类（标记删除，由 Worker 同步云端） */
    public void delete(Category category) {
        AppExecutors.get().diskIO().execute(() -> {
            category.setSyncState(SyncState.TO_DELETE.getValue());
            categoryDao.update(category);
            // 同时标记子分类为待删除（兼容无 ForeignKey CASCADE 的旧数据库版本）
            subCategoryDao.markSubCategoriesToDelete(
                    category.getId(), SyncState.TO_DELETE.getValue());
            enqueueSync();
        });
    }

    /** 根据 ID 标记删除分类 */
    public void deleteCategoryById(long categoryId) {
        AppExecutors.get().diskIO().execute(() -> {
            Category category = categoryDao.getCategoryById(categoryId);
            if (category != null) {
                category.setSyncState(SyncState.TO_DELETE.getValue());
                categoryDao.update(category);
                // 兼容旧数据库版本（无 ForeignKey CASCADE）
                subCategoryDao.markSubCategoriesToDelete(
                        categoryId, SyncState.TO_DELETE.getValue());
                enqueueSync();
            } else {
                Log.w(TAG, "⚠️ 未找到要删除的分类 ID=" + categoryId);
            }
        });
    }

    // =========================================================================
    // 读操作
    // =========================================================================

    public List<Category> getUnsyncedCategories() {
        return categoryDao.getUnsyncedCategories(SyncState.SYNCED.getValue());
    }

    public LiveData<List<CategoryWithSubCategories>> getCategoriesWithSubs(
            String userId, String type) {
        return categoryDao.getCategoriesWithSubs(userId, type);
    }

    public Category getCategoryById(long id) {
        return categoryDao.getCategoryById(id);
    }

    public List<Category> getPendingSyncCategories() {
        return categoryDao.getPendingSyncCategories();
    }

    public boolean hasSystemPresetCategories(String userId) {
        return categoryDao.countSystemCategoriesByUser(userId) > 0;
    }

    public Category getCategoryByNameAndUser(String name, String userId) {
        return categoryDao.getCategoryByNameAndUser(name, userId);
    }

    /** ★ 新增：通过 cloudId 查询本地分类，供子分类 Repository 反查父分类 id 使用 */
    public Category getCategoryByCloudId(String cloudId) {
        return categoryDao.getCategoryByCloudId(cloudId);
    }

    // =========================================================================
    // 云端同步
    // =========================================================================

    /**
     * 从云端拉取分类并更新到本地数据库（带去重逻辑）。
     */
    public void syncCategoriesFromCloud(Consumer<Boolean> callback) {
        AppExecutors.get().networkIO().execute(() -> {
            BmobApiImpl bmobApi = new BmobApiImpl();
            bmobApi.fetchCategories(new FindListener<CloudCategory>() {
                @Override
                public void done(List<CloudCategory> cloudList, BmobException e) {
                    if (e == null && cloudList != null) {
                        AppExecutors.get().diskIO().execute(() -> {
                            for (CloudCategory cloud : cloudList) {
                                Category local = categoryDao.getCategoryByCloudId(
                                        cloud.getObjectId());

                                if (local == null) {
                                    Category newC = cloud.toLocalCategory();
                                    long newId = categoryDao.insert(newC);
                                    // ★ 拉取后修正子分类外键（多设备场景）
                                    if (newId > 0) {
                                        subCategoryDao.updateParentCategoryId(
                                                cloud.getObjectId(), newId);
                                    }
                                    Log.d(TAG, "🆕 云端拉取 - 插入新分类：" + newC.getName());
                                } else {
                                    if (!equalsCategory(local, cloud)) {
                                        local.setName(cloud.getName());
                                        local.setType(cloud.getType());
                                        local.setColor(cloud.getColor());
                                        local.setIconUri(cloud.getIconUri());
                                        local.setOrder(cloud.getOrder());
                                        local.setUpdatedAt(System.currentTimeMillis());
                                        local.setSyncState(SyncState.SYNCED.getValue());
                                        categoryDao.update(local);
                                        // ★ 更新时也修正子分类外键
                                        subCategoryDao.updateParentCategoryId(
                                                cloud.getObjectId(), local.getId());
                                        Log.d(TAG, "🔄 云端拉取 - 更新分类：" + local.getName());
                                    }
                                }
                            }
                            AppExecutors.get().mainThread().execute(() -> callback.accept(true));
                        });
                    } else {
                        Log.e(TAG, "❌ 云端拉取失败：" + (e != null ? e.getMessage() : "未知错误"));
                        AppExecutors.get().mainThread().execute(() -> callback.accept(false));
                    }
                }
            });
        });
    }

    // =========================================================================
    // 私有工具
    // =========================================================================

    private boolean equalsCategory(Category local, CloudCategory cloud) {
        return safeEquals(local.getName(), cloud.getName())
                && safeEquals(local.getType(), cloud.getType())
                && safeEquals(local.getColor(), cloud.getColor())
                && safeEquals(local.getIconUri(), cloud.getIconUri())
                && local.getOrder() == cloud.getOrder()
                && safeEquals(local.getOwnerId(), cloud.getOwnerId());
    }

    private boolean safeEquals(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private void enqueueSync() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CategorySyncWorker.class)
                .build();
        workManager.enqueue(request);
    }
}