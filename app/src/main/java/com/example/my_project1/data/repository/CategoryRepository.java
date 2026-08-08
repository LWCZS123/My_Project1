package com.example.my_project1.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.SubCategoryDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.data.remote.model.CloudCategory;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.CategorySyncWorker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
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
    private final BillDao        billDao;
    private final WorkManager    workManager;
    private final Context        context;

    // ────────────────────────────────────────────────────────────────────
    //  ⚡ 商用级快照缓存 (Memory + Disk)
    // ────────────────────────────────────────────────────────────────────
    private static final String SP_NAME = "category_snapshot";
    private static final String KEY_EXPENSE = "snapshot_expense";
    private static final String KEY_INCOME = "snapshot_income";
    private static final Gson gson = new Gson();

    private static List<CategoryWithSubCategories> sCachedExpense = null;
    private static List<CategoryWithSubCategories> sCachedIncome = null;

    public CategoryRepository(Context context) {
        this.context   = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(this.context);
        categoryDao    = db.categoryDao();
        subCategoryDao = db.subCategoryDao();
        billDao        = db.billDao();
        workManager    = WorkManager.getInstance(this.context);
        
        loadSnapshotsFromDisk();
    }

    public CategoryRepository(CategoryDao categoryDao,
                              SubCategoryDao subCategoryDao,
                              BillDao billDao,
                              WorkManager workManager,
                              Context context) {
        this.categoryDao    = categoryDao;
        this.subCategoryDao = subCategoryDao;
        this.billDao        = billDao;
        this.workManager    = workManager;
        this.context        = context.getApplicationContext();
        loadSnapshotsFromDisk();
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

    /** 批量更新分类 */
    public void updateAll(List<Category> categories) {
        if (categories == null || categories.isEmpty()) return;
        AppExecutors.get().diskIO().execute(() -> {
            for (Category cat : categories) {
                cat.setSyncState(SyncState.TO_UPDATE.getValue());
            }
            categoryDao.updateCategories(categories);
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
                // 自动处理该分类下的账单
                int count = billDao.countBillsByCategory(category.getOwnerId(), category.getCloudId());
                if (count > 0) {
                    Log.w(TAG, "警告：删除含有账单的分类 (" + category.getName() + "), 账单数: " + count);
                }
                
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

    public void checkBillsCount(String userId, String categoryCloudId, Consumer<Integer> callback) {
        AppExecutors.get().diskIO().execute(() -> {
            int count = billDao.countBillsByCategory(userId, categoryCloudId);
            AppExecutors.get().mainThread().execute(() -> callback.accept(count));
        });
    }

    /** 归档一级分类 */
    public void archiveCategory(long categoryId, boolean archiveChildren) {
        AppExecutors.get().diskIO().execute(() -> {
            Category cat = categoryDao.getCategoryById(categoryId);
            if (cat != null) {
                cat.setArchiveStatus(1);
                cat.setArchiveTime(System.currentTimeMillis());
                cat.setSyncState(SyncState.TO_UPDATE.getValue());
                categoryDao.update(cat);

                if (archiveChildren) {
                    List<SubCategory> subs = subCategoryDao.getByParentCategoryId(categoryId);
                    for (SubCategory sub : subs) {
                        sub.setArchiveStatus(1);
                        sub.setArchiveTime(System.currentTimeMillis());
                        sub.setSyncState(SyncState.TO_UPDATE.getValue());
                        subCategoryDao.update(sub);
                    }
                }
                enqueueSync();
            }
        });
    }

    /** 归档二级分类 */
    public void archiveSubCategory(long subId) {
        AppExecutors.get().diskIO().execute(() -> {
            SubCategory sub = subCategoryDao.getById(subId);
            if (sub != null) {
                sub.setArchiveStatus(1);
                sub.setArchiveTime(System.currentTimeMillis());
                sub.setSyncState(SyncState.TO_UPDATE.getValue());
                subCategoryDao.update(sub);
                enqueueSync();
            }
        });
    }

    /** 恢复分类 */
    public void restoreCategory(long id, boolean isSub) {
        AppExecutors.get().diskIO().execute(() -> {
            if (isSub) {
                SubCategory sub = subCategoryDao.getById(id);
                if (sub != null) {
                    sub.setArchiveStatus(0);
                    sub.setArchiveTime(null);
                    sub.setSyncState(SyncState.TO_UPDATE.getValue());
                    subCategoryDao.update(sub);
                }
            } else {
                Category cat = categoryDao.getCategoryById(id);
                if (cat != null) {
                    cat.setArchiveStatus(0);
                    cat.setArchiveTime(null);
                    cat.setSyncState(SyncState.TO_UPDATE.getValue());
                    categoryDao.update(cat);
                }
            }
            enqueueSync();
        });
    }

    /** 迁移账单数据 */
    public void migrateBills(String userId, String sourceId, Category target, Consumer<Integer> resultCallback) {
        AppExecutors.get().diskIO().execute(() -> {
            int count = billDao.countBillsByCategory(userId, sourceId);
            if (count > 0) {
                billDao.migrateBills(userId, sourceId, target.getCloudId(), target.getName(),
                        target.getIconUri(), target.getIconBackgroundColor(), System.currentTimeMillis());
                // 账单迁移由 BillSyncWorker 同步
                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(com.example.my_project1.work.BillSyncWorker.class).build();
                workManager.enqueue(request);
            }
            AppExecutors.get().mainThread().execute(() -> resultCallback.accept(count));
        });
    }

    /** 调整二级分类归属 */
    public void changeSubCategoryParent(long subId, Category newParent) {
        AppExecutors.get().diskIO().execute(() -> {
            SubCategory sub = subCategoryDao.getById(subId);
            if (sub != null) {
                sub.setParentCategoryId(newParent.getId());
                sub.setParentCloudId(newParent.getCloudId());
                sub.setSyncState(SyncState.TO_UPDATE.getValue());
                subCategoryDao.update(sub);
                enqueueSync();
            }
        });
    }

    /** 将二级分类晋升为一级分类 */
    public void promoteSubCategory(long subId, String categoryType, Consumer<Boolean> callback) {
        AppExecutors.get().diskIO().execute(() -> {
            SubCategory sub = subCategoryDao.getById(subId);
            if (sub == null || sub.getCloudId() == null) {
                AppExecutors.get().mainThread().execute(() -> callback.accept(false));
                return;
            }

            // 1. 同步上传一级分类以获取 cloudId
            Category newCat = new Category();
            newCat.setOwnerId(sub.getOwnerId());
            newCat.setType(categoryType);
            newCat.setName(sub.getName());
            newCat.setIconUri(sub.getIconUri());
            newCat.setIconBackgroundColor(sub.getIconBackgroundColor());
            newCat.setColor(sub.getColor());
            newCat.setExcludeBudget(sub.isExcludeBudget());
            newCat.setArchiveStatus(sub.getArchiveStatus());
            newCat.setArchiveTime(sub.getArchiveTime());
            newCat.setSyncState(SyncState.SYNCED.getValue());
            newCat.setUpdatedAt(System.currentTimeMillis());

            try {
                CloudCategory cloud = CloudCategory.fromLocalCategory(newCat);
                String newCloudId = cloud.saveSync();
                if (newCloudId != null) {
                    newCat.setCloudId(newCloudId);
                    long newLocalId = categoryDao.insert(newCat);
                    newCat.setId(newLocalId);

                    // 2. 迁移本地账单数据
                    billDao.migrateBills(sub.getOwnerId(), sub.getCloudId(), newCloudId,
                            newCat.getName(), newCat.getIconUri(), newCat.getIconBackgroundColor(),
                            System.currentTimeMillis());

                    // 3. 标记旧子分类为待删除（让 Worker 删除云端数据）
                    sub.markDeletedForSync();
                    subCategoryDao.update(sub);

                    enqueueSync(); // 触发子分类删除同步
                    // 4. 触发账单同步
                    OneTimeWorkRequest billSyncRequest = new OneTimeWorkRequest.Builder(com.example.my_project1.work.BillSyncWorker.class).build();
                    workManager.enqueue(billSyncRequest);

                    AppExecutors.get().mainThread().execute(() -> callback.accept(true));
                } else {
                    AppExecutors.get().mainThread().execute(() -> callback.accept(false));
                }
            } catch (Exception e) {
                Log.e(TAG, "promoteSubCategory failed", e);
                AppExecutors.get().mainThread().execute(() -> callback.accept(false));
            }
        });
    }

    // =========================================================================
    // 读操作
    // =========================================================================

    public LiveData<List<CategoryWithSubCategories>> getCategoriesWithSubs(
            String userId, String type) {
        LiveData<List<CategoryWithSubCategories>> liveData = categoryDao.getCategoriesWithSubs(userId, type);
        
        // 自动更新快照
        androidx.lifecycle.MediatorLiveData<List<CategoryWithSubCategories>> mediator = new androidx.lifecycle.MediatorLiveData<>();
        mediator.addSource(liveData, categories -> {
            if (categories != null) {
                updateCacheAndDisk(type, categories);
            }
            mediator.setValue(categories);
        });
        
        return mediator;
    }

    public List<CategoryWithSubCategories> getCategoriesSnapshot(String type) {
        if ("expense".equals(type)) return sCachedExpense;
        if ("income".equals(type)) return sCachedIncome;
        return null;
    }

    private void updateCacheAndDisk(String type, List<CategoryWithSubCategories> data) {
        if ("expense".equals(type)) sCachedExpense = data;
        else if ("income".equals(type)) sCachedIncome = data;

        AppExecutors.get().diskIO().execute(() -> {
            context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("expense".equals(type) ? KEY_EXPENSE : KEY_INCOME, gson.toJson(data))
                    .apply();
        });
    }

    private void loadSnapshotsFromDisk() {
        if (sCachedExpense != null && sCachedIncome != null) return;
        
        android.content.SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        java.lang.reflect.Type listType = new TypeToken<List<CategoryWithSubCategories>>(){}.getType();

        if (sCachedExpense == null) {
            String json = sp.getString(KEY_EXPENSE, null);
            if (json != null) sCachedExpense = gson.fromJson(json, listType);
        }
        if (sCachedIncome == null) {
            String json = sp.getString(KEY_INCOME, null);
            if (json != null) sCachedIncome = gson.fromJson(json, listType);
        }
    }

    public Category getCategoryById(long id) {
        return categoryDao.getCategoryById(id);
    }

    public Category getCategoryByNameAndUser(String name, String userId) {
        return categoryDao.getCategoryByNameAndUser(name, userId);
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
                                        local.setSortIndex(cloud.getOrder());
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
                && local.getSortIndex() == cloud.getOrder()
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