package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.tag.BillTagCrossRef;
import com.example.my_project1.data.model.tag.TagEntity;

import java.util.List;

@Dao
public interface BillTagCrossRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRef(BillTagCrossRef ref);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRefs(List<BillTagCrossRef> refs);

    @Update
    void updateCrossRef(BillTagCrossRef ref);

    @Query("DELETE FROM bill_tag_cross_ref WHERE bill_id = :billId")
    void deleteByBillId(long billId);

    @Query("DELETE FROM bill_tag_cross_ref WHERE tag_id = :tagId")
    void deleteByTagId(long tagId);

    @Query("SELECT * FROM bill_tag_cross_ref WHERE bill_id = :billId AND is_deleted = 0")
    List<BillTagCrossRef> getCrossRefsByBillIdSync(long billId);

    @Query("SELECT * FROM bill_tag_cross_ref WHERE bill_id = :billId AND is_deleted = 0")
    LiveData<List<BillTagCrossRef>> getCrossRefsByBillIdLiveData(long billId);

    @Query("SELECT t.* FROM tags t INNER JOIN bill_tag_cross_ref c ON t.id = c.tag_id WHERE c.bill_id = :billId AND c.is_deleted = 0 AND t.is_deleted = 0")
    List<TagEntity> getTagsForBillSync(long billId);

    @Query("SELECT t.* FROM tags t INNER JOIN bill_tag_cross_ref c ON t.id = c.tag_id WHERE c.bill_id = :billId AND c.is_deleted = 0 AND t.is_deleted = 0")
    LiveData<List<TagEntity>> getTagsForBillLiveData(long billId);

    @Query("SELECT bill_id FROM bill_tag_cross_ref WHERE tag_id = :tagId AND is_deleted = 0")
    List<Long> getBillIdsForTagSync(long tagId);

    @Query("SELECT * FROM bill_tag_cross_ref WHERE user_id = :userId AND sync_state != 0")
    List<BillTagCrossRef> getUnsyncedCrossRefs(String userId);
}
