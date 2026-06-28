package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.model.wish.WishWithRecords;

import java.util.List;

/**
 * WishDao - 愿望数据库访问层
 * -------------------------------------------------------
 * 设计规范（与 BillDao 保持一致）:
 *  - ✅ 所有查询都添加 userId 过滤，实现用户数据隔离
 *  - ✅ 排除软删除数据（sync_state != 'TO_DELETE'）
 *  - ✅ 按时间倒序排序
 *  - ✅ LiveData 用于 UI 实时监听，同步方法用于后台任务
 */
@Dao
public interface WishDao {

    // ==================== Wish 基本增删改 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertWish(Wish wish);

    @Update
    int updateWish(Wish wish);

    @Delete
    int deleteWish(Wish wish);

    // ==================== Wish LiveData 查询 ====================

    /**
     * 🔥 获取指定用户的所有愿望（实时监听）- 排除软删除
     */
    @Query("SELECT * FROM wishes WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY created_at DESC")
    LiveData<List<Wish>> getAllWishesByUser(String userId);

    /**
     * 🔥 按状态查询愿望（0=进行中, 1=已完成, 2=已放弃）
     */
    @Query("SELECT * FROM wishes WHERE user_id = :userId AND status = :status AND sync_state != 'TO_DELETE' ORDER BY created_at DESC")
    LiveData<List<Wish>> getWishesByStatus(String userId, int status);

    // ==================== Wish 同步查询 ====================

    // 新增：同步查询单个愿望
    @Query("SELECT * FROM wishes WHERE id = :id")
    Wish getByIdSync(long id);

    // 新增：异步查询单个愿望
    @Query("SELECT * FROM wishes WHERE id = :id")
    LiveData<Wish> getById(long id);


    // 新增：级联查询愿望和其记录
    @Transaction
    @Query("SELECT * FROM wishes WHERE id = :id")
    LiveData<WishWithRecords> getWishWithRecords(long id);



    @Query("SELECT * FROM wishes WHERE sync_state != 'SYNCED'")
    List<Wish> getPendingSyncWishes();

    @Query("SELECT * FROM wishes WHERE sync_state = 'TO_DELETE'")
    List<Wish> getToDeleteWishes();

    @Query("SELECT * FROM wishes WHERE object_id = :objectId LIMIT 1")
    Wish getWishByObjectId(String objectId);

    @Query("SELECT * FROM wishes WHERE object_id = :objectId LIMIT 1")
    Wish getWishByObjectIdSync(String objectId);

    /**
     * 同步查询单个愿望（用于详情页编辑）
     */
    @Query("SELECT * FROM wishes WHERE id = :id AND sync_state != 'TO_DELETE' LIMIT 1")
    Wish getWishByIdSync(long id);

    /**
     * 获取用户所有愿望（同步，用于列表Overview图表统计）
     */
    @Query("SELECT * FROM wishes WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY created_at DESC")
    List<Wish> getAllWishesByUserSync(String userId);

    // ==================== WishRecord 增删改 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRecord(WishRecord record);

    @Delete
    int deleteRecord(WishRecord record);

    @Update
    int updateRecord(WishRecord record);

    // ==================== WishRecord LiveData 查询 ====================

    /**
     * 🔥 获取某个愿望的所有存钱记录（实时监听，按日期倒序）
     */
    @Query("SELECT * FROM wish_records WHERE wish_id = :wishId AND sync_state != 'TO_DELETE' ORDER BY record_date DESC")
    LiveData<List<WishRecord>> getRecordsByWishId(long wishId);

    // ==================== WishRecord 同步查询 ====================

    @Query("SELECT * FROM wish_records WHERE wish_id = :wishId AND sync_state != 'TO_DELETE' ORDER BY record_date ASC")
    List<WishRecord> getRecordsByWishIdSync(long wishId);

    @Query("SELECT * FROM wish_records WHERE sync_state != 'SYNCED'")
    List<WishRecord> getPendingSyncRecords();

    @Query("SELECT * FROM wish_records WHERE sync_state = 'TO_DELETE'")
    List<WishRecord> getToDeleteRecords();


    @Query("SELECT * FROM wish_records WHERE object_id = :objectId LIMIT 1")
    WishRecord getRecordByObjectIdSync(String objectId);

    // ==================== 批量删除 ====================

    @Query("DELETE FROM wish_records WHERE wish_id = :wishId")
    int deleteRecordsByWishId(long wishId);

    @Query("DELETE FROM wishes WHERE user_id = :userId")
    int deleteAllWishesByUser(String userId);
}