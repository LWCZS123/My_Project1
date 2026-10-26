package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;

import java.util.List;

/**
 * 标签分组数据库访问接口
 */
@Dao
public interface TagGroupDao {

    /**
     * 插入单个标签分组
     *
     * @param group 标签分组实体
     * @return 插入成功后的自增ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(TagGroupEntity group);

    /**
     * 批量插入标签分组
     *
     * @param groups 标签分组实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroups(List<TagGroupEntity> groups);

    /**
     * 更新标签分组信息
     *
     * @param group 标签分组实体
     */
    @Update
    void updateGroup(TagGroupEntity group);

    /**
     * 根据主键ID获取标签分组
     *
     * @param groupId 标签分组ID
     * @return 标签分组实体
     */
    @Query("SELECT * FROM tag_groups WHERE id = :groupId LIMIT 1")
    TagGroupEntity getGroupById(long groupId);

    /**
     * 根据用户ID和分组名称获取未删除的标签分组
     *
     * @param userId 用户ID
     * @param name   分组名称
     * @return 标签分组实体
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND name = :name AND is_deleted = 0 LIMIT 1")
    TagGroupEntity getGroupByName(String userId, String name);

    /**
     * 获取指定用户的默认标签分组
     *
     * @param userId 用户ID
     * @return 默认标签分组实体
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_default = 1 AND is_deleted = 0 LIMIT 1")
    TagGroupEntity getDefaultGroup(String userId);

    /**
     * 实时监听指定用户未删除的所有标签分组
     *
     * @param userId 用户ID
     * @return 标签分组列表 LiveData
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_deleted = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<TagGroupEntity>> getActiveGroupsLiveData(String userId);

    /**
     * 同步获取指定用户未删除的所有标签分组
     *
     * @param userId 用户ID
     * @return 标签分组列表
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_deleted = 0 ORDER BY sort_order ASC, id ASC")
    List<TagGroupEntity> getActiveGroupsSync(String userId);

    /**
     * 实时监听指定用户的标签分组及其包含的所有标签关系
     *
     * @param userId 用户ID
     * @return 带有标签列表的标签分组关系列表 LiveData
     */
    @Transaction
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_deleted = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<TagGroupWithTags>> getGroupsWithTagsLiveData(String userId);

    /**
     * 根据 Bmob 云端 objectId 查找标签分组
     *
     * @param objectId 云端唯一标识
     * @return 标签分组实体
     */
    @Query("SELECT * FROM tag_groups WHERE object_id = :objectId LIMIT 1")
    TagGroupEntity getGroupByObjectIdSync(String objectId);

    /**
     * 获取需要与云端同步（新增或更新）的标签分组列表
     *
     * @param userId 用户ID
     * @return 待同步标签分组列表
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND sync_state != 'SYNCED'")
    List<TagGroupEntity> getPendingSyncGroups(String userId);

    /**
     * 获取标记为待删除的标签分组列表
     *
     * @param userId 用户ID
     * @return 待删除标签分组列表
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND (is_deleted = 1 OR sync_state = 'TO_DELETE')")
    List<TagGroupEntity> getToDeleteGroups(String userId);

    /**
     * 获取未同步的标签分组列表
     *
     * @param userId 用户ID
     * @return 未同步标签分组列表
     */
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND sync_state != 0")
    List<TagGroupEntity> getUnsyncedGroups(String userId);

    /**
     * 更新标签分组的折叠展开状态
     *
     * @param groupId  标签分组ID
     * @param isFolded 是否折叠
     */
    @Query("UPDATE tag_groups SET is_folded = :isFolded WHERE id = :groupId")
    void updateFoldState(long groupId, boolean isFolded);

    /**
     * 物理删除指定的标签分组记录
     *
     * @param groupId 标签分组ID
     */
    @Query("DELETE FROM tag_groups WHERE id = :groupId")
    void deleteGroupHard(long groupId);
}
