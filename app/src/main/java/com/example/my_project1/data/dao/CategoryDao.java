package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;

import java.util.List;


@Dao
public interface CategoryDao {

    @Query("SELECT * FROM categories WHERE is_system_preset = 1 AND name = :name LIMIT 1")
    Category getSystemPresetByName(String name);

    @Query("SELECT * FROM categories WHERE owner_id = :userId AND is_system_preset = 1")
    List<Category> getSystemPresets(String userId);

    @Query("SELECT * FROM categories WHERE cloud_id = :cloudId LIMIT 1")
    Category getByCloudId(String cloudId);

    // 插入单条分类（REPLACE 策略，用于云同步更新场景）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Category category);

    @Query("UPDATE categories SET cloud_id = :cloudId, sync_state = :syncState WHERE id = :id")
    void updateCloudIdById(long id, String cloudId, int syncState);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    Category getCategoryById(long id);

    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    @Query("DELETE FROM categories WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE sub_categories SET sync_state = :syncState WHERE parent_category_id = :categoryId")
    void markSubCategoriesToDelete(long categoryId, int syncState);

    @Query("SELECT COUNT(*) FROM categories WHERE is_system_preset = 1 AND owner_id = :userId")
    int countSystemCategoriesByUser(String userId);

    /**
     * ★ 批量插入分类（OnConflictStrategy.IGNORE）
     *
     * 配合 Category 实体的 UNIQUE(owner_id, type, name) 约束：
     * - 冲突时静默跳过，不抛出异常，不更新已有数据
     * - 成功插入的行返回其 rowId，跳过的行返回 -1
     *
     * 用于多选图标批量保存场景，替代旧版循环 insert()。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long[] insertCategories(List<Category> categories);

    @Transaction
    @Query("SELECT * FROM categories WHERE owner_id = :userId AND type = :type ORDER BY `order` ASC")
    LiveData<List<CategoryWithSubCategories>> getCategoriesWithSubs(String userId, String type);

    @Query("SELECT * FROM categories WHERE sync_state != 0")
    List<Category> getPendingSyncCategories();

    @Query("SELECT * FROM categories WHERE cloud_id = :cloudId LIMIT 1")
    Category findByCloudId(String cloudId);

    @Query("SELECT * FROM categories WHERE sync_state != :syncedState")
    List<Category> getUnsyncedCategories(int syncedState);

    @Query("SELECT * FROM categories WHERE cloud_id = :cloudId LIMIT 1")
    Category getCategoryByCloudId(String cloudId);

    @Query("SELECT * FROM categories WHERE name = :name AND owner_id = :userId LIMIT 1")
    Category getCategoryByNameAndUser(String name, String userId);
}