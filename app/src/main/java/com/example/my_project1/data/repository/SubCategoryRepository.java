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
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.data.remote.model.CloudSubCategory;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.SubCategorySyncWorker;

import java.util.List;
import java.util.function.Consumer;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;

/**
 * SubCategoryRepository — 修复版
 *
 * 修复要点：
 *  1. ★ insert() / insertAll() 写入时自动查询并填充 parentCloudId
 *     这是子分类无法同步的根本原因：子分类记录没有 parentCloudId，
 *     SubCategorySyncWorker 的 guardParentCloudId() 检测失败，永远 retry。
 *
 *  2. ★ deleteSubCategoryById 改为标记删除（TO_DELETE），由 Worker 处理云端删除，
 *     与 CategoryRepository.deleteCategoryById 保持一致的语义。
 *
 *  3. 其余方法保持不变。
 */
public class SubCategoryRepository {

    private static final String TAG = "SubCategoryRepo";

    private final SubCategoryDao subCategoryDao;
    private final CategoryDao    categoryDao;      // ★ 新增：用于查询 parentCloudId
    private final WorkManager    workManager;

    public SubCategoryRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        subCategoryDao = db.subCategoryDao();
        categoryDao    = db.categoryDao();
        workManager    = WorkManager.getInstance(context);
    }

    /** 内部测试用构造器 */
    public SubCategoryRepository(SubCategoryDao subCategoryDao,
                                 CategoryDao categoryDao,
                                 WorkManager workManager) {
        this.subCategoryDao = subCategoryDao;
        this.categoryDao    = categoryDao;
        this.workManager    = workManager;
    }

    // =========================================================================
    // 写操作
    // =========================================================================

    /**
     * 插入单个子分类并触发同步。
     *
     * ★ 修复：写入前自动填充 parentCloudId（若未设置）
     *   来源：通过 parentCategoryId 查询本地父分类的 cloudId
     */
    public void insert(SubCategory subCategory) {
        AppExecutors.get().diskIO().execute(() -> {
            fillParentCloudIdIfMissing(subCategory);
            subCategory.setSyncState(SyncState.TO_CREATE.getValue());
            subCategoryDao.insert(subCategory);
            enqueueSync();
        });
    }

    /**
     * 批量插入子分类并触发同步。
     *
     * ★ 修复：批量写入前为每条记录填充 parentCloudId
     *   若同一批次内子分类属于同一父类（常见场景），
     *   只查询一次父类 cloudId，减少 DB 查询次数。
     */
    public void insertAll(List<SubCategory> subCategories) {
        if (subCategories == null || subCategories.isEmpty()) return;
        AppExecutors.get().diskIO().execute(() -> {
            // 按 parentCategoryId 分组填充，避免重复查询
            String cachedParentCloudId = null;
            long   cachedParentId      = -1L;

            for (SubCategory sub : subCategories) {
                sub.setSyncState(SyncState.TO_CREATE.getValue());

                // ★ parentCloudId 未设置时才补全
                if (sub.getParentCloudId() == null || sub.getParentCloudId().isEmpty()) {
                    long pid = sub.getParentCategoryId();
                    if (pid != cachedParentId) {
                        // 换了父分类，重新查询
                        Category parent = categoryDao.getCategoryById(pid);
                        cachedParentCloudId = (parent != null) ? parent.getCloudId() : null;
                        cachedParentId = pid;
                    }
                    if (cachedParentCloudId != null && !cachedParentCloudId.isEmpty()) {
                        sub.setParentCloudId(cachedParentCloudId);
                        Log.d(TAG, "insertAll - 补全 parentCloudId=" + cachedParentCloudId
                                + " → " + sub.getName());
                    } else {
                        Log.w(TAG, "insertAll - 父分类尚未同步，parentCloudId 暂时为空: "
                                + sub.getName() + "（Worker 稍后补全）");
                    }
                }
            }

            subCategoryDao.insertAll(subCategories);
            Log.d(TAG, "insertAll: 批量写入 " + subCategories.size() + " 条");
            enqueueSync();
        });
    }

    /**
     * 更新子分类并触发同步。
     * ★ 更新时也检查 parentCloudId，避免旧数据缺失此字段
     */
    public void update(SubCategory subCategory) {
        AppExecutors.get().diskIO().execute(() -> {
            fillParentCloudIdIfMissing(subCategory);
            subCategory.setSyncState(SyncState.TO_UPDATE.getValue());
            subCategoryDao.update(subCategory);
            enqueueSync();
        });
    }

    /**
     * 根据 ID 标记删除子分类（保留本地记录，由 Worker 完成云端删除）。
     */
    public void deleteSubCategoryById(long subCategoryId) {
        AppExecutors.get().diskIO().execute(() -> {
            SubCategory sub = subCategoryDao.getSubCategoryById(subCategoryId);
            if (sub != null) {
                sub.setSyncState(SyncState.TO_DELETE.getValue());
                subCategoryDao.update(sub);
                enqueueSync();
            } else {
                Log.w(TAG, "⚠️ 未找到要删除的子分类 ID=" + subCategoryId);
            }
        });
    }

    // =========================================================================
    // 读操作
    // =========================================================================

    public SubCategory getSubCategoryById(long id) {
        return subCategoryDao.getSubCategoryById(id);
    }

    public LiveData<List<SubCategory>> getSubCategoriesByParent(long parentId) {
        return subCategoryDao.getSubCategoriesByParent(parentId);
    }

    public List<SubCategory> getPendingSyncSubCategories() {
        return subCategoryDao.getPendingSyncSubCategories();
    }

    public List<SubCategory> getUnsyncedSubCategories() {
        return subCategoryDao.getUnsyncedSubCategories(SyncState.SYNCED.getValue());
    }

    public SubCategory getSubCategoryByParentAndName(long parentCategoryId, String name) {
        return subCategoryDao.getByParentAndName(parentCategoryId, name);
    }

    // =========================================================================
    // 云端拉取同步
    // =========================================================================

    /**
     * 从云端拉取子分类并更新本地数据库（带去重逻辑）。
     * ★ 修复：拉取到的子分类在写入前填充 parentCloudId
     */
    public void syncSubCategoriesFromCloud(Consumer<Boolean> callback) {
        AppExecutors.get().networkIO().execute(() -> {
            BmobApiImpl bmobApi = new BmobApiImpl();
            bmobApi.fetchSubCategories(new FindListener<CloudSubCategory>() {
                @Override
                public void done(List<CloudSubCategory> cloudList, BmobException e) {
                    if (e == null && cloudList != null) {
                        AppExecutors.get().diskIO().execute(() -> {
                            for (CloudSubCategory cloud : cloudList) {
                                SubCategory local = subCategoryDao.getSubCategoryByCloudId(
                                        cloud.getObjectId());

                                if (local == null) {
                                    SubCategory newSub = cloud.toLocalSubCategory();
                                    // toLocalSubCategory 已填充 parentCloudId，
                                    // 这里再根据 parentCloudId 反查本地 parentCategoryId
                                    resolveLocalParentId(newSub);
                                    subCategoryDao.insert(newSub);
                                    Log.d(TAG, "🆕 云端拉取 - 插入新子分类：" + newSub.getName());
                                } else {
                                    if (!equalsSubCategory(local, cloud)) {
                                        local.setName(cloud.getName());
                                        local.setIconUri(cloud.getIconUri());
                                        local.setOrder(cloud.getOrder());
                                        local.setOwnerId(cloud.getOwnerId() != null
                                                ? cloud.getOwnerId().getObjectId() : null);
                                        // ★ 更新时也同步 parentCloudId
                                        String pcid = cloud.getEffectiveParentCloudId();
                                        if (pcid != null) local.setParentCloudId(pcid);
                                        local.setUpdatedAt(System.currentTimeMillis());
                                        local.setSyncState(SyncState.SYNCED.getValue());
                                        subCategoryDao.update(local);
                                        Log.d(TAG, "🔄 云端拉取 - 更新子分类：" + local.getName());
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

    /**
     * ★ 核心修复方法：若子分类的 parentCloudId 未设置，
     * 通过 parentCategoryId 查询父分类的 cloudId 并填充。
     */
    private void fillParentCloudIdIfMissing(SubCategory sub) {
        if (sub.getParentCloudId() != null && !sub.getParentCloudId().isEmpty()) return;
        if (sub.getParentCategoryId() <= 0) return;

        Category parent = categoryDao.getCategoryById(sub.getParentCategoryId());
        if (parent != null && parent.getCloudId() != null && !parent.getCloudId().isEmpty()) {
            sub.setParentCloudId(parent.getCloudId());
            Log.d(TAG, "fillParentCloudId: " + sub.getName()
                    + " → parentCloudId=" + parent.getCloudId());
        } else {
            Log.w(TAG, "fillParentCloudId: 父分类 id=" + sub.getParentCategoryId()
                    + " 尚未同步，parentCloudId 暂留空，Worker 稍后补全");
        }
    }

    /**
     * 根据 parentCloudId 反查本地 Category.id 并设置 parentCategoryId。
     * 用于云端下载场景：toLocalSubCategory() 不填充本地 id，此处补全。
     */
    private void resolveLocalParentId(SubCategory sub) {
        if (sub.getParentCloudId() == null || sub.getParentCloudId().isEmpty()) return;
        if (sub.getParentCategoryId() > 0) return; // 已有本地 id，无需反查

        Category parent = categoryDao.getCategoryByCloudId(sub.getParentCloudId());
        if (parent != null) {
            sub.setParentCategoryId(parent.getId());
            Log.d(TAG, "resolveLocalParentId: " + sub.getName()
                    + " → parentCategoryId=" + parent.getId());
        } else {
            Log.w(TAG, "resolveLocalParentId: 本地未找到 cloudId=" + sub.getParentCloudId()
                    + " 对应的父分类，parentCategoryId 暂为 0");
        }
    }

    private boolean equalsSubCategory(SubCategory local, CloudSubCategory cloud) {
        return safeEquals(local.getName(), cloud.getName())
                && safeEquals(local.getIconUri(), cloud.getIconUri())
                && local.getOrder() == cloud.getOrder();
    }

    private boolean safeEquals(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private void enqueueSync() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SubCategorySyncWorker.class)
                .build();
        workManager.enqueue(request);
    }
}