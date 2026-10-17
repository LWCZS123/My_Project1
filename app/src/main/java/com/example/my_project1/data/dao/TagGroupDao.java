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

@Dao
public interface TagGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(TagGroupEntity group);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroups(List<TagGroupEntity> groups);

    @Update
    void updateGroup(TagGroupEntity group);

    @Query("SELECT * FROM tag_groups WHERE id = :groupId LIMIT 1")
    TagGroupEntity getGroupById(long groupId);

    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND name = :name AND is_deleted = 0 LIMIT 1")
    TagGroupEntity getGroupByName(String userId, String name);

    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_default = 1 AND is_deleted = 0 LIMIT 1")
    TagGroupEntity getDefaultGroup(String userId);

    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_deleted = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<TagGroupEntity>> getActiveGroupsLiveData(String userId);

    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_deleted = 0 ORDER BY sort_order ASC, id ASC")
    List<TagGroupEntity> getActiveGroupsSync(String userId);

    @Transaction
    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND is_deleted = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<TagGroupWithTags>> getGroupsWithTagsLiveData(String userId);

    @Query("SELECT * FROM tag_groups WHERE user_id = :userId AND sync_state != 0")
    List<TagGroupEntity> getUnsyncedGroups(String userId);

    @Query("UPDATE tag_groups SET is_folded = :isFolded WHERE id = :groupId")
    void updateFoldState(long groupId, boolean isFolded);

    @Query("DELETE FROM tag_groups WHERE id = :groupId")
    void deleteGroupHard(long groupId);
}
