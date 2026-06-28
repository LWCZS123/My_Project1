package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.SubCategory;

import java.util.List;

/**
 * SubCategoryDao — 重构版
 *
 * 重构要点：
 *  1. 新增 getByParentCloudId()：通过 parent_cloud_id 查询子分类
 *     用于 CategoryDownloadWorker 下载时反查已有记录
 *  2. 新增 updateParentCategoryId()：批量修正本地外键
 *     下载场景下，若本地 parent_category_id 与反查结果不符时使用
 *  3. getByParentAndName 使用 COLLATE NOCASE 忽略大小写
 *  4. 移除了与 localId 相关的已废弃查询
 */
@Dao
public interface SubCategoryDao {

    // -------------------------------------------------------------------------
    // 写操作
    // -------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SubCategory subCategory);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SubCategory> subCategories);

    /** 批量插入并返回各行 rowId（-1 表示因 IGNORE 跳过） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long[] insertSubCategories(List<SubCategory> subCategories);

    @Update
    int update(SubCategory subCategory);

    @Delete
    void delete(SubCategory subCategory);

    @Delete
    void deleteSubCategories(List<SubCategory> subCategories);

    @Query("DELETE FROM sub_categories WHERE id = :id")
    void deleteSubById(long id);

    // -------------------------------------------------------------------------
    // 字段更新
    // -------------------------------------------------------------------------

    @Query("UPDATE sub_categories SET cloud_id = :cloudId, sync_state = :syncState WHERE id = :id")
    void updateSubCloudIdById(long id, String cloudId, int syncState);

    @Query("UPDATE sub_categories SET sync_state = :syncState WHERE parent_category_id = :parentId")
    void markSubCategoriesToDelete(long parentId, int syncState);

    /**
     * ★ 修正本地外键：将所有 parent_cloud_id = :parentCloudId 的记录的
     * parent_category_id 更新为正确的本地 categoryId
     * 用于多设备下载时，子分类外键与父分类本地 id 不匹配的修复场景
     */
    @Query("UPDATE sub_categories SET parent_category_id = :localCategoryId " +
            "WHERE parent_cloud_id = :parentCloudId AND parent_category_id != :localCategoryId")
    int updateParentCategoryId(String parentCloudId, long localCategoryId);

    // -------------------------------------------------------------------------
    // 读操作 — 单条查询
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM sub_categories WHERE id = :id LIMIT 1")
    SubCategory getById(long id);

    @Query("SELECT * FROM sub_categories WHERE id = :id LIMIT 1")
    SubCategory getSubCategoryById(long id);

    @Query("SELECT * FROM sub_categories WHERE cloud_id = :cloudId LIMIT 1")
    SubCategory getBySubCloudId(String cloudId);

    @Query("SELECT * FROM sub_categories WHERE cloud_id = :cloudId LIMIT 1")
    SubCategory getSubCategoryByCloudId(String cloudId);

    /**
     * ★ 通过 parent_cloud_id 查询子分类列表
     * 下载场景：先通过此字段确认是否已存在，再决定 insert 还是 update
     */
    @Query("SELECT * FROM sub_categories WHERE parent_cloud_id = :parentCloudId")
    List<SubCategory> getByParentCloudId(String parentCloudId);

    /**
     * 通过 parent_cloud_id + name 精确匹配（忽略大小写与首尾空格）
     * 下载时的第二匹配策略
     */
    @Query("SELECT * FROM sub_categories " +
            "WHERE parent_cloud_id = :parentCloudId AND TRIM(name) = TRIM(:name) COLLATE NOCASE LIMIT 1")
    SubCategory getByParentCloudIdAndName(String parentCloudId, String name);

    @Query("SELECT * FROM sub_categories WHERE parent_category_id = :parentId AND name = :name LIMIT 1")
    SubCategory getByParentAndName(long parentId, String name);

    // -------------------------------------------------------------------------
    // 读操作 — 列表查询
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM sub_categories WHERE parent_category_id = :parentId")
    List<SubCategory> getByParentCategoryId(long parentId);

    @Query("SELECT * FROM sub_categories WHERE parent_category_id = :parentId ORDER BY `order` ASC")
    LiveData<List<SubCategory>> getSubCategoriesByParent(long parentId);

    /** 所有待同步（syncState != SYNCED）的子分类 */
    @Query("SELECT * FROM sub_categories WHERE sync_state != 0")
    List<SubCategory> getPendingSyncSubCategories();

    @Query("SELECT * FROM sub_categories WHERE sync_state != :syncedState")
    List<SubCategory> getUnsyncedSubCategories(int syncedState);

    @Query("SELECT * FROM sub_categories WHERE is_system_preset = 1 AND cloud_id IS NULL")
    List<SubCategory> getSystemPresetWithoutCloudId();
}