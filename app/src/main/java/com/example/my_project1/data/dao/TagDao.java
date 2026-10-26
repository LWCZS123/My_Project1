package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.tag.TagEntity;

import java.util.List;

/**
 * 标签实体数据库访问接口
 */
@Dao
public interface TagDao {

    /**
     * 插入单个标签记录
     *
     * @param tag 标签实体
     * @return 插入成功的自增ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTag(TagEntity tag);

    /**
     * 批量插入标签记录
     *
     * @param tags 标签实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTags(List<TagEntity> tags);

    /**
     * 更新标签记录
     *
     * @param tag 标签实体
     */
    @Update
    void updateTag(TagEntity tag);

    /**
     * 根据本地主键ID查询标签
     *
     * @param tagId 标签ID
     * @return 标签实体
     */
    @Query("SELECT * FROM tags WHERE id = :tagId LIMIT 1")
    TagEntity getTagById(long tagId);

    /**
     * 根据用户ID和标签名称查询未删除的标签
     *
     * @param userId 用户ID
     * @param name   标签名称
     * @return 标签实体
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND name = :name AND is_deleted = 0 LIMIT 1")
    TagEntity getTagByName(String userId, String name);

    /**
     * 实时监听指定用户所有处于激活状态（未删除、未归档）的标签
     *
     * @param userId 用户ID
     * @return 标签列表 LiveData
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND is_deleted = 0 AND is_archived = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<TagEntity>> getAllActiveTagsLiveData(String userId);

    /**
     * 同步获取指定用户所有处于激活状态的标签
     *
     * @param userId 用户ID
     * @return 标签列表
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND is_deleted = 0 AND is_archived = 0 ORDER BY sort_order ASC, id ASC")
    List<TagEntity> getAllActiveTagsSync(String userId);

    /**
     * 同步获取特定分组内所有激活状态的标签
     *
     * @param userId  用户ID
     * @param groupId 分组ID
     * @return 标签列表
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND group_id = :groupId AND is_deleted = 0 AND is_archived = 0 ORDER BY sort_order ASC, id ASC")
    List<TagEntity> getTagsByGroupIdSync(String userId, long groupId);

    /**
     * 实时监听指定用户已被归档的标签列表
     *
     * @param userId 用户ID
     * @return 归档标签列表 LiveData
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND is_archived = 1 AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<TagEntity>> getArchivedTagsLiveData(String userId);

    /**
     * 根据 Bmob 云端的 objectId 查询本地标签记录
     *
     * @param objectId 云端唯一标识
     * @return 标签实体
     */
    @Query("SELECT * FROM tags WHERE object_id = :objectId LIMIT 1")
    TagEntity getTagByObjectIdSync(String objectId);

    /**
     * 获取待同步（新增或修改）到云端的标签列表
     *
     * @param userId 用户ID
     * @return 待同步标签列表
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND sync_state != 'SYNCED'")
    List<TagEntity> getPendingSyncTags(String userId);

    /**
     * 获取标记为待删除状态的标签列表
     *
     * @param userId 用户ID
     * @return 待删除标签列表
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND (is_deleted = 1 OR sync_state = 'TO_DELETE')")
    List<TagEntity> getToDeleteTags(String userId);

    /**
     * 获取未同步的标签列表
     *
     * @param userId 用户ID
     * @return 未同步标签列表
     */
    @Query("SELECT * FROM tags WHERE user_id = :userId AND sync_state != 0")
    List<TagEntity> getUnsyncedTags(String userId);

    /**
     * 将源分组下的所有标签批量移动到目标分组
     *
     * @param userId        用户ID
     * @param sourceGroupId 原分组ID
     * @param targetGroupId 目标分组ID
     */
    @Query("UPDATE tags SET group_id = :targetGroupId WHERE group_id = :sourceGroupId AND user_id = :userId")
    void moveTagsToGroup(String userId, long sourceGroupId, long targetGroupId);

    /**
     * 物理删除指定的本地标签记录
     *
     * @param tagId 标签ID
     */
    @Query("DELETE FROM tags WHERE id = :tagId")
    void deleteTagHard(long tagId);
}
