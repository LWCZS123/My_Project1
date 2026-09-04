package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.model.wish.WishWithRecords;

import java.util.Date;
import java.util.List;

@Dao
public interface WishDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertWish(Wish wish);

    @Update
    int updateWish(Wish wish);

    @Delete
    int deleteWish(Wish wish);

    @Query("DELETE FROM wishes WHERE id = :id")
    int deleteWishById(long id);

    @Query("SELECT * FROM wishes WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY created_at DESC")
    LiveData<List<Wish>> getAllWishesByUser(String userId);

    @Query("SELECT * FROM wishes WHERE user_id = :userId ORDER BY created_at DESC")
    List<Wish> getAllWishesByUserSync(String userId);

    @Query("SELECT * FROM wishes WHERE id = :id AND sync_state != 'TO_DELETE' LIMIT 1")
    LiveData<Wish> getWishById(long id);

    @Query("SELECT * FROM wishes WHERE id = :id LIMIT 1")
    Wish getWishByIdSync(long id);

    @Transaction
    @Query("SELECT * FROM wishes WHERE id = :id AND sync_state != 'TO_DELETE' LIMIT 1")
    LiveData<WishWithRecords> getWishWithRecords(long id);

    /** Worker 只消费尚未与云端一致的数据，避免每次同步全表上传。 */
    @Query("SELECT * FROM wishes WHERE user_id = :userId AND sync_state IN ('TO_CREATE', 'TO_UPDATE', 'SYNC_FAILED')")
    List<Wish> getPendingSyncWishes(String userId);

    @Query("SELECT * FROM wishes WHERE user_id = :userId AND sync_state = 'TO_DELETE'")
    List<Wish> getToDeleteWishes(String userId);

    @Query("SELECT * FROM wishes WHERE object_id = :objectId LIMIT 1")
    Wish getWishByObjectId(String objectId);

    @Query("UPDATE wishes SET object_id = :objectId, sync_state = 'SYNCED' WHERE id = :id")
    int markWishSynced(long id, String objectId);

    @Query("UPDATE wishes SET current_amount = :amount, status = :status, sync_state = :syncState, updated_at = :updatedAt WHERE id = :wishId")
    int updateWishProgress(long wishId, double amount, int status, SyncState syncState, Date updatedAt);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRecord(WishRecord record);

    @Update
    int updateRecord(WishRecord record);

    @Delete
    int deleteRecord(WishRecord record);

    @Query("DELETE FROM wish_records WHERE id = :id")
    int deleteRecordById(long id);

    @Query("SELECT * FROM wish_records WHERE wish_id = :wishId AND sync_state != 'TO_DELETE' ORDER BY record_date DESC, created_at DESC")
    LiveData<List<WishRecord>> getRecordsByWish(long wishId);

    @Query("SELECT * FROM wish_records WHERE id = :id AND sync_state != 'TO_DELETE' LIMIT 1")
    LiveData<WishRecord> getRecordById(long id);

    @Query("SELECT * FROM wish_records WHERE id = :id LIMIT 1")
    WishRecord getRecordByIdSync(long id);

    @Query("SELECT * FROM wish_records WHERE object_id = :objectId LIMIT 1")
    WishRecord getRecordByObjectId(String objectId);

    @Query("SELECT * FROM wish_records WHERE user_id = :userId ORDER BY created_at DESC")
    List<WishRecord> getAllRecordsByUserSync(String userId);

    @Query("SELECT * FROM wish_records WHERE user_id = :userId AND sync_state IN ('TO_CREATE', 'TO_UPDATE', 'SYNC_FAILED')")
    List<WishRecord> getPendingSyncRecords(String userId);

    @Query("SELECT * FROM wish_records WHERE user_id = :userId AND sync_state = 'TO_DELETE'")
    List<WishRecord> getToDeleteRecords(String userId);

    @Query("UPDATE wish_records SET object_id = :objectId, wish_object_id = :wishObjectId, sync_state = 'SYNCED' WHERE id = :id")
    int markRecordSynced(long id, String objectId, String wishObjectId);

    @Query("UPDATE wish_records SET sync_state = 'TO_DELETE', updated_at = :updatedAt WHERE wish_id = :wishId AND sync_state != 'TO_DELETE'")
    int markRecordsDeleted(long wishId, Date updatedAt);

    /** 进度以有效记录求和为准，不信任 UI 传入的累计金额。 */
    @Query("SELECT * FROM wish_records WHERE linked_bill_id = :billId LIMIT 1")
    WishRecord getRecordByBillId(long billId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM wish_records WHERE wish_id = :wishId AND sync_state != 'TO_DELETE'")
    double getSavedAmount(long wishId);
}
