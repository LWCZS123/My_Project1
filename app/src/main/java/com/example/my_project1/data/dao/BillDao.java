package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;

import java.util.Date;
import java.util.List;

/**
 * BillDao（优化版 - 添加搜索功能）
 * -------------------------------------------------------
 * 账单数据库访问层
 * 核心优化：
 *  - ✅ 所有查询都添加 userId 过滤，实现用户数据隔离
 *  - ✅ 排除已删除的账单（sync_state != 3）
 *  - ✅ 按时间倒序排序，优化查询性能
 *  - ✅ 新增搜索功能(支持分类、备注、地点模糊查询)
 */
@Dao
public interface BillDao {

    //基本增删改
    /** 插入账单(冲突替换),返回主键 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Bill bill);

    /** 批量插入账单 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertBills(List<Bill> bills);

    /** 更新账单(返回影响条数) */
    @Update
    int update(Bill bill);

    /** 删除单条账单 */
    @Delete
    int delete(Bill bill);


    /** 根据同步状态查询账单 */
    @Query("SELECT * FROM bills WHERE sync_state = :state")
    List<Bill> getBillsBySyncState(SyncState state);

    // ==================== LiveData查询 ====================

    // 在 BillDao 中添加
    @Query("SELECT * FROM bills WHERE id = :id")
    Bill getByIdSync(long id);

    /**
     * 🔥 获取指定用户的所有账单(实时监听) - 排除已删除的账单
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getAllBillsByUser(String userId);

    /**
     * 🔥 按时间范围查询账单(LiveData) - 排除已删除的账单
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND billTime >= :start AND billTime <= :end AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsInTimeRange(String userId, Date start, Date end);

    /** 🔥 按账本和用户查询账单 - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND book_id = :bookId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsByBook(String userId, String bookId);

    /** 🔥 按账户和用户查询账单 - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND account_id = :accountId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsByAccount(String userId, String accountId);

    /** 🔥 按分类ID和用户查询账单(支持一级/二级) - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND category_id = :categoryId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsByCategory(String userId, String categoryId);

    /** 🔥 获取某用户某账本在某月的账单(适合做图表) - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND book_id = :bookId AND billTime BETWEEN :start AND :end AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getMonthlyBills(String userId, String bookId, Date start, Date end);


    // BillDao.java
    @Query("SELECT * FROM bills WHERE source_wish_id = :wishId AND sync_state != 'TO_DELETE'")
    List<Bill> getBillsBySourceWishId(long wishId);

    @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
    Bill getBillByIdSync(long billId);


    @Query("SELECT * FROM bills WHERE user_id = :userId")
    List<Bill> getAllBillsByUserSync(String userId);

    // ==================== 搜索功能(新增) ====================

    /**
     * 🔍 搜索账单(模糊查询)
     * 支持搜索: 分类名称(category_name)、备注(remark)、地点(location)
     * 排除已删除的账单
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (category_name LIKE :keyword " +
            "OR remark LIKE :keyword " +
            "OR location LIKE :keyword) " +
            "ORDER BY billTime DESC")
    List<Bill> searchBills(String userId, String keyword);

    // ==================== 同步查询(非LiveData) ====================

    /** 同步查询所有账单(Worker使用) */
    @Query("SELECT * FROM bills ORDER BY billTime DESC")
    List<Bill> getAllBillsSync();

    /** 🔴 查询需要同步的账单 (TO_CREATE=1, TO_UPDATE=2, TO_DELETE=3) */
    @Query("SELECT * FROM bills WHERE sync_state != 'SYNCED'")
    List<Bill> getPendingSyncBills();


    /** 🔴 查询需要删除的账单 */
    @Query("SELECT * FROM bills WHERE sync_state = 'TO_DELETE'")
    List<Bill> getToDeleteBills();


    /** 🔴 根据 objectId 查询账单 */
    @Query("SELECT * FROM bills WHERE object_id = :objectId LIMIT 1")
    Bill getBillByObjectId(String objectId);

    // ==================== 批量删除 ====================

    /** 🔥 删除某用户某账本下全部账单 */
    @Query("DELETE FROM bills WHERE user_id = :userId AND book_id = :bookId")
    int deleteBillsByBook(String userId, String bookId);

    /** 🔥 删除某用户某账户下全部账单 */
    @Query("DELETE FROM bills WHERE user_id = :userId AND account_id = :accountId")
    int deleteBillsByAccountId(String userId, String accountId);

    /** 🔥 删除某用户某分类下账单 */
    @Query("DELETE FROM bills WHERE user_id = :userId AND category_id = :categoryId")
    int deleteBillsByCategory(String userId, String categoryId);



    // 🔴 在 BillDao 接口中新增以下方法

    /**
     * 同步查询某个账户下的所有账单（用于后台任务）
     */
    @Query("SELECT * FROM bills WHERE account_id = :accountId ORDER BY billTime DESC")
    List<Bill> getBillsByAccountSync(String accountId);

    /**
     * 批量更新账单的账户ID
     */
    @Query("UPDATE bills SET account_id = :newAccountId, sync_state = 'TO_UPDATE' WHERE account_id = :oldAccountId")
    int updateAccountIdForBills(String oldAccountId, String newAccountId);

    /**
     * 将账单设置为无账户
     */
    @Query("UPDATE bills SET account_id = NULL, sync_state = 'TO_UPDATE' WHERE account_id = :accountId")
    int setAccountIdToNull(String accountId);

    /**
     * ⭐ 根据objectId同步查询账单（用于编辑模式）
     * 注意：这是同步方法，必须在后台线程调用
     */
    @Query("SELECT * FROM bills WHERE object_id = :objectId AND sync_state != 'TO_DELETE' LIMIT 1")
    Bill getBillByObjectIdSync(String objectId);

    @Query("SELECT * FROM bills " +
            "WHERE user_id = :userId " +
            "AND category_id = :catCloudId " +
            "AND type = 0 " +
            "AND excludeBudget = 0 " +
            "AND billTime >= :startMs AND billTime <= :endMs " +
            "AND sync_state != 'TO_DELETE'")
    List<Bill> getBillsByCategoryInRange(String userId, String catCloudId,
                                         long startMs, long endMs);



     @Query("SELECT * FROM bills " +
                 "WHERE user_id = :userId " +
                 "AND type = 0 " +
                 "AND excludeBudget = 0 " +
                 "AND billTime >= :startMs AND billTime <= :endMs " +
                 "AND sync_state != 'TO_DELETE'")
     List<Bill> getExpenseBillsInRange(String userId, long startMs, long endMs);





}