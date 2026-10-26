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

/**
 * 账单与标签多对多关联表数据库访问接口
 */
@Dao
public interface BillTagCrossRefDao {

    /**
     * 插入单条账单标签关联记录
     *
     * @param ref 账单标签关联实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRef(BillTagCrossRef ref);

    /**
     * 批量插入账单标签关联记录
     *
     * @param refs 账单标签关联实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRefs(List<BillTagCrossRef> refs);

    /**
     * 更新账单标签关联记录
     *
     * @param ref 账单标签关联实体
     */
    @Update
    void updateCrossRef(BillTagCrossRef ref);

    /**
     * 根据账单ID删除该账单的所有标签关联记录
     *
     * @param billId 账单ID
     */
    @Query("DELETE FROM bill_tag_cross_ref WHERE bill_id = :billId")
    void deleteByBillId(long billId);

    /**
     * 根据标签ID删除该标签的所有账单关联记录
     *
     * @param tagId 标签ID
     */
    @Query("DELETE FROM bill_tag_cross_ref WHERE tag_id = :tagId")
    void deleteByTagId(long tagId);

    /**
     * 同步获取账单ID对应的未删除关联列表
     *
     * @param billId 账单ID
     * @return 关联关系列表
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE bill_id = :billId AND is_deleted = 0")
    List<BillTagCrossRef> getCrossRefsByBillIdSync(long billId);

    /**
     * 实时监听账单ID对应的未删除关联列表
     *
     * @param billId 账单ID
     * @return 关联关系列表 LiveData
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE bill_id = :billId AND is_deleted = 0")
    LiveData<List<BillTagCrossRef>> getCrossRefsByBillIdLiveData(long billId);

    /**
     * 同步获取特定账单关联的所有有效标签实体
     *
     * @param billId 账单ID
     * @return 标签实体列表
     */
    @Query("SELECT t.* FROM tags t INNER JOIN bill_tag_cross_ref c ON t.id = c.tag_id WHERE c.bill_id = :billId AND c.is_deleted = 0 AND t.is_deleted = 0")
    List<TagEntity> getTagsForBillSync(long billId);

    /**
     * 实时监听特定账单关联的所有有效标签实体
     *
     * @param billId 账单ID
     * @return 标签实体列表 LiveData
     */
    @Query("SELECT t.* FROM tags t INNER JOIN bill_tag_cross_ref c ON t.id = c.tag_id WHERE c.bill_id = :billId AND c.is_deleted = 0 AND t.is_deleted = 0")
    LiveData<List<TagEntity>> getTagsForBillLiveData(long billId);

    /**
     * 同步获取关联了某标签的所有账单ID列表
     *
     * @param tagId 标签ID
     * @return 账单ID列表
     */
    @Query("SELECT bill_id FROM bill_tag_cross_ref WHERE tag_id = :tagId AND is_deleted = 0")
    List<Long> getBillIdsForTagSync(long tagId);

    /**
     * 根据账单ID与标签ID查询唯一的关联记录
     *
     * @param billId 账单ID
     * @param tagId  标签ID
     * @return 账单标签关联实体
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE bill_id = :billId AND tag_id = :tagId LIMIT 1")
    BillTagCrossRef getCrossRefSync(long billId, long tagId);

    /**
     * 获取待同步到云端的账单标签关联记录
     *
     * @param userId 用户ID
     * @return 待同步关联记录列表
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE user_id = :userId AND sync_state != 'SYNCED'")
    List<BillTagCrossRef> getPendingSyncCrossRefs(String userId);

    /**
     * 获取标记为待删除的账单标签关联记录
     *
     * @param userId 用户ID
     * @return 待删除关联记录列表
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE user_id = :userId AND (is_deleted = 1 OR sync_state = 'TO_DELETE')")
    List<BillTagCrossRef> getToDeleteCrossRefs(String userId);

    /**
     * 同步获取指定用户所有未删除的账单标签关联记录
     *
     * @param userId 用户ID
     * @return 关联记录列表
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE user_id = :userId AND is_deleted = 0")
    List<BillTagCrossRef> getAllActiveCrossRefsSync(String userId);

    /**
     * 物理删除指定的某条账单标签关联记录
     *
     * @param billId 账单ID
     * @param tagId  标签ID
     */
    @Query("DELETE FROM bill_tag_cross_ref WHERE bill_id = :billId AND tag_id = :tagId")
    void deleteCrossRefHard(long billId, long tagId);

    /**
     * 物理删除包含某标签ID的所有关联记录
     *
     * @param tagId 标签ID
     */
    @Query("DELETE FROM bill_tag_cross_ref WHERE tag_id = :tagId")
    void deleteByTagIdHard(long tagId);

    /**
     * 物理删除包含某账单ID的所有关联记录
     *
     * @param billId 账单ID
     */
    @Query("DELETE FROM bill_tag_cross_ref WHERE bill_id = :billId")
    void deleteByBillIdHard(long billId);

    /**
     * 获取未同步的账单标签关联记录列表
     *
     * @param userId 用户ID
     * @return 未同步关联关系列表
     */
    @Query("SELECT * FROM bill_tag_cross_ref WHERE user_id = :userId AND sync_state != 0")
    List<BillTagCrossRef> getUnsyncedCrossRefs(String userId);
}
