package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.tag.TagEntity;

import java.util.List;

@Dao
public interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTag(TagEntity tag);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTags(List<TagEntity> tags);

    @Update
    void updateTag(TagEntity tag);

    @Query("SELECT * FROM tags WHERE id = :tagId LIMIT 1")
    TagEntity getTagById(long tagId);

    @Query("SELECT * FROM tags WHERE user_id = :userId AND name = :name AND is_deleted = 0 LIMIT 1")
    TagEntity getTagByName(String userId, String name);

    @Query("SELECT * FROM tags WHERE user_id = :userId AND is_deleted = 0 AND is_archived = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<TagEntity>> getAllActiveTagsLiveData(String userId);

    @Query("SELECT * FROM tags WHERE user_id = :userId AND is_deleted = 0 AND is_archived = 0 ORDER BY sort_order ASC, id ASC")
    List<TagEntity> getAllActiveTagsSync(String userId);

    @Query("SELECT * FROM tags WHERE user_id = :userId AND group_id = :groupId AND is_deleted = 0 AND is_archived = 0 ORDER BY sort_order ASC, id ASC")
    List<TagEntity> getTagsByGroupIdSync(String userId, long groupId);

    @Query("SELECT * FROM tags WHERE user_id = :userId AND is_archived = 1 AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<TagEntity>> getArchivedTagsLiveData(String userId);

    @Query("SELECT * FROM tags WHERE user_id = :userId AND sync_state != 0")
    List<TagEntity> getUnsyncedTags(String userId);

    @Query("UPDATE tags SET group_id = :targetGroupId WHERE group_id = :sourceGroupId AND user_id = :userId")
    void moveTagsToGroup(String userId, long sourceGroupId, long targetGroupId);

    @Query("DELETE FROM tags WHERE id = :tagId")
    void deleteTagHard(long tagId);
}
