package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.BillWithBalance;
import com.example.my_project1.data.model.bill.DailyStat;
import com.example.my_project1.data.model.bill.MonthlyStat;
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.data.model.budget.CategoryAmount;

import java.util.Date;
import java.util.List;

/**
 * BillDao
 * -------------------------------------------------------
 * 账单数据库访问接口
 */
@Dao
public interface BillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Bill bill);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertBills(List<Bill> bills);

    @Update
    int update(Bill bill);

    @Delete
    int delete(Bill bill);

    @Query("SELECT * FROM bills WHERE sync_state = :state")
    List<Bill> getBillsBySyncState(SyncState state);

    @Query("SELECT * FROM bills WHERE id = :id")
    Bill getByIdSync(long id);

    /**
     * 获取指定用户的所有账单(实时监听) - 排除已删除的账单
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC, id DESC")
    LiveData<List<Bill>> getAllBillsByUser(String userId);

    @Query("SELECT * FROM bills WHERE user_id = :userId AND " +
            "((:objectId IS NOT NULL AND object_id = :objectId) OR (:localId > 0 AND id = :localId)) " +
            "AND sync_state != 'TO_DELETE' LIMIT 1")
    LiveData<Bill> getBillLive(String userId, String objectId, long localId);

    @Query("SELECT strftime('%Y-%m-%d', billTime/1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS billCount, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS incomeTotal, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) AS expenseTotal " +
            "FROM bills WHERE user_id = :userId AND sync_state != 'TO_DELETE' AND billTime IS NOT NULL " +
            "GROUP BY day ORDER BY day DESC")
    LiveData<List<DailyStat>> getUserDailyStatsLive(String userId);

    /**
     * 按时间范围查询账单(LiveData) - 排除已删除的账单
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND billTime >= :start AND billTime <= :end AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsInTimeRange(String userId, Date start, Date end);

    @Query("SELECT * FROM bills WHERE user_id = :userId AND billTime >= :start AND billTime < :endExclusive AND sync_state != 'TO_DELETE' ORDER BY billTime DESC, id DESC")
    LiveData<List<Bill>> getBillsInTimeRangeExclusive(String userId, Date start, Date endExclusive);

    @Query("SELECT * FROM bills WHERE user_id = :userId AND billTime >= :start AND billTime < :endExclusive AND sync_state != 'TO_DELETE' ORDER BY billTime DESC, id DESC LIMIT :limit OFFSET :offset")
    List<Bill> getBillsInTimeRangePaged(String userId, Date start, Date endExclusive, int limit, int offset);

    /** 全量账单统计在 SQLite 聚合，避免为了 count/days 把所有账单实体加载到内存。 */
    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) as incomeTotal, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) as expenseTotal, " +
            "COUNT(*) as billCount, " +
            "COUNT(DISTINCT date(billTime/1000, 'unixepoch', 'localtime')) as billDays " +
            "FROM bills WHERE user_id = :userId AND sync_state != 'TO_DELETE'")
    LiveData<SearchSummary> getUserBillSummaryLive(String userId);

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) as incomeTotal, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) as expenseTotal, " +
            "COUNT(*) as billCount, COUNT(DISTINCT date(billTime/1000, 'unixepoch', 'localtime')) as billDays " +
            "FROM bills WHERE user_id = :userId AND sync_state != 'TO_DELETE'")
    SearchSummary getUserBillSummarySync(String userId);

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) as incomeTotal, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) as expenseTotal, " +
            "COUNT(*) as billCount, COUNT(DISTINCT date(billTime/1000, 'unixepoch', 'localtime')) as billDays " +
            "FROM bills WHERE user_id = :userId AND sync_state != 'TO_DELETE' " +
            "AND billTime >= :start AND billTime < :endExclusive")
    SearchSummary getBillSummaryInRangeSync(String userId, Date start, Date endExclusive);

    @Query("SELECT * FROM bills WHERE user_id = :userId AND billTime >= :start AND billTime <= :end AND sync_state != 'TO_DELETE' ORDER BY billTime DESC, id DESC")
    List<Bill> getBillsInTimeRangeSync(String userId, Date start, Date end);

    /** 按账本和用户查询账单 - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND book_id = :bookId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsByBook(String userId, String bookId);

    @Query("SELECT * FROM bills WHERE user_id = :userId " +
            "AND (account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId) " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (:startTime IS NULL OR billTime >= :startTime) " +
            "AND (:endTime IS NULL OR billTime <= :endTime) " +
            "ORDER BY billTime DESC, id DESC LIMIT :limit OFFSET :offset")
    List<Bill> getAccountBillsPaged(String userId, String accountId, long localAccountId, java.util.Date startTime, java.util.Date endTime, int limit, int offset);

    /**
     * 获取账户账单（带余额计算）
     */
    @Query("SELECT *, (:currentBalance - (SELECT COALESCE(SUM(CASE " +
            "WHEN b2.type = 1 THEN b2.amount " +
            "WHEN b2.type = 0 THEN -b2.amount " +
            "WHEN (b2.type = 2 OR b2.type = 3) AND (b2.account_id = :accountId OR b2.local_account_id = :localAccountId) THEN -b2.amount " +
            "WHEN (b2.type = 2 OR b2.type = 3) AND (b2.to_account_id = :accountId OR b2.to_local_account_id = :localAccountId) THEN b2.amount " +
            "ELSE 0 END), 0) FROM bills AS b2 WHERE b2.user_id = :userId " +
            "AND (b2.account_id = :accountId OR b2.local_account_id = :localAccountId OR b2.to_account_id = :accountId OR b2.to_local_account_id = :localAccountId) " +
            "AND b2.sync_state != 'TO_DELETE' " +
            "AND (b2.billTime > b.billTime OR (b2.billTime = b.billTime AND b2.id > b.id)))) AS balanceAfter " +
            "FROM bills AS b WHERE b.user_id = :userId " +
            "AND (b.account_id = :accountId OR b.local_account_id = :localAccountId " +
            "OR b.to_account_id = :accountId OR b.to_local_account_id = :localAccountId) " +
            "AND b.sync_state != 'TO_DELETE' " +
            "AND (:startTime IS NULL OR b.billTime >= :startTime) " +
            "AND (:endTime IS NULL OR b.billTime <= :endTime) " +
            "ORDER BY b.billTime DESC, b.id DESC LIMIT :limit OFFSET :offset")
    List<BillWithBalance> getAccountBillsWithBalancePaged(String userId, String accountId, long localAccountId, double currentBalance, java.util.Date startTime, java.util.Date endTime, int limit, int offset);

    @Query("SELECT " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as incomeTotal, " +
            "SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) as expenseTotal, " +
            "COUNT(*) as billCount, " +
            "COUNT(DISTINCT date(billTime/1000, 'unixepoch', 'localtime')) as billDays " +
            "FROM bills WHERE user_id = :userId " +
            "AND (account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId) " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (:startTime IS NULL OR billTime >= :startTime) " +
            "AND (:endTime IS NULL OR billTime <= :endTime)")
    androidx.lifecycle.LiveData<SearchSummary> getAccountStatsLive(String userId, String accountId, long localAccountId, java.util.Date startTime, java.util.Date endTime);

    @Query("SELECT category_id, category_name, SUM(amount) as total_amount " +
            "FROM bills WHERE user_id = :userId " +
            "AND (account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId) " +
            "AND sync_state != 'TO_DELETE' " +
            "AND type = :type " +
            "AND (:startTime IS NULL OR billTime >= :startTime) " +
            "AND (:endTime IS NULL OR billTime <= :endTime) " +
            "GROUP BY category_name " +
            "ORDER BY total_amount DESC")
    androidx.lifecycle.LiveData<List<CategorySummary>> getAccountCategorySummaryLive(String userId, String accountId, long localAccountId, int type, java.util.Date startTime, java.util.Date endTime);

    @Query("SELECT strftime('%Y-%m-%d', billTime/1000, 'unixepoch', 'localtime') as day, " +
            "COUNT(*) as billCount, " +
            "COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) as incomeTotal, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) as expenseTotal " +
            "FROM bills WHERE user_id = :userId " +
            "AND (account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId) " +
            "AND sync_state != 'TO_DELETE' " +
            "GROUP BY day")
   LiveData<List<DailyStat>> getAccountDailyStatsLive(String userId, String accountId, long localAccountId);

    @Query("SELECT strftime('%Y-%m', billTime/1000, 'unixepoch', 'localtime') as month, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as incomeTotal, " +
            "SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) as expenseTotal, " +
            "SUM(CASE WHEN (type = 2 OR type = 3) AND (to_account_id = :accountId OR to_local_account_id = :localAccountId) THEN amount ELSE 0 END) as transferInTotal, " +
            "SUM(CASE WHEN (type = 2 OR type = 3) AND (account_id = :accountId OR local_account_id = :localAccountId) THEN amount ELSE 0 END) as transferOutTotal " +
            "FROM bills WHERE user_id = :userId " +
            "AND (account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId) " +
            "AND sync_state != 'TO_DELETE' " +
            "GROUP BY month")
    androidx.lifecycle.LiveData<List<MonthlyStat>> getAccountMonthlyStatsLive(String userId, String accountId, long localAccountId);

    public static class CategorySummary {
        @ColumnInfo(name = "category_id")
        public String categoryId;
        @ColumnInfo(name = "category_name")
        public String categoryName;
        @ColumnInfo(name = "total_amount")
        public double totalAmount;
    }

    /** 按账户和用户查询账单 - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId " +
            "AND (account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId) " +
            "AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsByAccount(String userId, String accountId, long localAccountId);

    /** 按分类ID和用户查询账单(支持一级/二级) - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND category_id = :categoryId AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getBillsByCategory(String userId, String categoryId);

    /** 获取某用户某账本在某月的账单 - 排除已删除的账单 */
    @Query("SELECT * FROM bills WHERE user_id = :userId AND book_id = :bookId AND billTime BETWEEN :start AND :end AND sync_state != 'TO_DELETE' ORDER BY billTime DESC")
    LiveData<List<Bill>> getMonthlyBills(String userId, String bookId, Date start, Date end);

    @Query("SELECT * FROM bills WHERE source_wish_id = :wishId AND sync_state != 'TO_DELETE'")
    List<Bill> getBillsBySourceWishId(long wishId);

    @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
    Bill getBillByIdSync(long billId);

    @Query("SELECT * FROM bills WHERE user_id = :userId")
    List<Bill> getAllBillsByUserSync(String userId);

    // ==================== 搜索功能 ====================

    /**
     * 搜索账单(模糊查询)
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (category_name LIKE :keyword " +
            "OR remark LIKE :keyword " +
            "OR location LIKE :keyword) " +
            "ORDER BY billTime DESC")
    List<Bill> searchBills(String userId, String keyword);

    /**
     * 高级搜索：支持多种筛选条件
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (:keyword IS NULL OR category_name LIKE :keyword OR remark LIKE :keyword OR location LIKE :keyword) " +
            "AND (:billType = -1 OR type = :billType) " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND (:accountIdsCount = 0 OR account_id IN (:accountIds)) " +
            "AND (:startTime IS NULL OR billTime >= :startTime) " +
            "AND (:endTime IS NULL OR billTime <= :endTime) " +
            "AND (:minAmount IS NULL OR amount >= :minAmount) " +
            "AND (:maxAmount IS NULL OR amount <= :maxAmount) " +
            "ORDER BY billTime DESC")
    List<Bill> searchBillsAdvanced(String userId, String keyword, int billType, String categoryId,
                                   List<String> accountIds, int accountIdsCount,
                                   Date startTime, Date endTime, Double minAmount, Double maxAmount);

    /**
     * 高级搜索：支持分页
     */
    @Query("SELECT * FROM bills WHERE user_id = :userId " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (:keyword IS NULL OR category_name LIKE :keyword OR remark LIKE :keyword OR location LIKE :keyword) " +
            "AND (:billType = -1 OR type = :billType) " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND (:accountIdsCount = 0 OR account_id IN (:accountIds)) " +
            "AND (:startTime IS NULL OR billTime >= :startTime) " +
            "AND (:endTime IS NULL OR billTime <= :endTime) " +
            "AND (:minAmount IS NULL OR amount >= :minAmount) " +
            "AND (:maxAmount IS NULL OR amount <= :maxAmount) " +
            "AND (:includeBudget IS NULL OR excludeBudget != :includeBudget) " +
            "ORDER BY billTime DESC LIMIT :limit OFFSET :offset")
    List<Bill> searchBillsAdvancedPaged(String userId, String keyword, int billType, String categoryId,
                                        List<String> accountIds, int accountIdsCount,
                                        Date startTime, Date endTime, Double minAmount, Double maxAmount,
                                        Boolean includeBudget,
                                        int limit, int offset);

    /**
     * 高级搜索汇总：直接在数据库层面计算聚合结果
     */
    @Query("SELECT " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as incomeTotal, " +
            "SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) as expenseTotal, " +
            "COUNT(*) as billCount, " +
            "COUNT(DISTINCT date(billTime/1000, 'unixepoch', 'localtime')) as billDays " +
            "FROM bills WHERE user_id = :userId " +
            "AND sync_state != 'TO_DELETE' " +
            "AND (:keyword IS NULL OR category_name LIKE :keyword OR remark LIKE :keyword OR location LIKE :keyword) " +
            "AND (:billType = -1 OR type = :billType) " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND (:accountIdsCount = 0 OR account_id IN (:accountIds)) " +
            "AND (:startTime IS NULL OR billTime >= :startTime) " +
            "AND (:endTime IS NULL OR billTime <= :endTime) " +
            "AND (:minAmount IS NULL OR amount >= :minAmount) " +
            "AND (:maxAmount IS NULL OR amount <= :maxAmount) " +
            "AND (:includeBudget IS NULL OR excludeBudget != :includeBudget)")
    SearchSummary searchBillsSummaryAdvanced(String userId, String keyword, int billType, String categoryId,
                                           List<String> accountIds, int accountIdsCount,
                                           Date startTime, Date endTime, Double minAmount, Double maxAmount,
                                           Boolean includeBudget);

    @Query("SELECT DISTINCT remark FROM bills WHERE user_id = :userId AND remark LIKE :keyword AND sync_state != 'TO_DELETE' LIMIT 5")
    List<String> getRemarkSuggestions(String userId, String keyword);

    @Query("SELECT DISTINCT location FROM bills WHERE user_id = :userId AND location LIKE :keyword AND sync_state != 'TO_DELETE' LIMIT 5")
    List<String> getLocationSuggestions(String userId, String keyword);

    // ==================== 同步查询 ====================

    @Query("SELECT * FROM bills ORDER BY billTime DESC")
    List<Bill> getAllBillsSync();

    @Query("SELECT * FROM bills WHERE sync_state != 'SYNCED'")
    List<Bill> getPendingSyncBills();

    @Query("SELECT * FROM bills WHERE user_id = :userId AND sync_state IN ('TO_CREATE', 'TO_UPDATE')")
    List<Bill> getPendingSyncBillsByUser(String userId);

    @Query("SELECT * FROM bills WHERE sync_state = 'TO_DELETE'")
    List<Bill> getToDeleteBills();

    @Query("SELECT * FROM bills WHERE user_id = :userId AND sync_state = 'TO_DELETE'")
    List<Bill> getToDeleteBillsByUser(String userId);

    @Query("SELECT * FROM bills WHERE object_id = :objectId LIMIT 1")
    Bill getBillByObjectId(String objectId);

    // ==================== 批量删除与更新 ====================

    @Query("DELETE FROM bills WHERE user_id = :userId AND book_id = :bookId")
    int deleteBillsByBook(String userId, String bookId);

    @Query("UPDATE bills SET sync_state = 'TO_DELETE', updatedAt = :now " +
            "WHERE user_id = :userId AND (account_id = :accountId OR local_account_id = :localAccountId)")
    int markBillsAsDeletedByAccountId(String userId, String accountId, long localAccountId, long now);

    @Query("DELETE FROM bills WHERE user_id = :userId AND (account_id = :accountId OR local_account_id = :localAccountId)")
    int deleteBillsByAccountId(String userId, String accountId, long localAccountId);

    @Query("DELETE FROM bills WHERE user_id = :userId AND category_id = :categoryId")
    int deleteBillsByCategory(String userId, String categoryId);

    @Query("UPDATE bills SET sync_state = 'TO_DELETE', updatedAt = :now WHERE id IN (:billIds)")
    void markBillsDeletedByIds(List<Long> billIds, long now);

    @Query("DELETE FROM bills WHERE id IN (:billIds)")
    void deleteBillsByIds(List<Long> billIds);

    @Query("SELECT * FROM bills WHERE id IN (:billIds)")
    List<Bill> getBillsByIds(List<Long> billIds);

    @Query("SELECT COUNT(*) FROM bills WHERE category_id = :categoryId AND user_id = :userId AND sync_state != 'TO_DELETE'")
    int countBillsByCategory(String userId, String categoryId);

    @Query("UPDATE bills SET category_id = :targetId, category_name = :targetName, " +
            "category_icon = :targetIcon, category_icon_bg_color = :targetIconBg, " +
            "sync_state = 'TO_UPDATE', updatedAt = :now " +
            "WHERE category_id = :sourceId AND user_id = :userId")
    void migrateBills(String userId, String sourceId, String targetId, String targetName,
                      String targetIcon, String targetIconBg, long now);

    @Query("SELECT * FROM bills WHERE account_id = :accountId OR local_account_id = :localAccountId " +
            "OR to_account_id = :accountId OR to_local_account_id = :localAccountId ORDER BY billTime DESC")
    List<Bill> getBillsByAccountSync(String accountId, long localAccountId);

    @Query("UPDATE bills SET account_id = :newAccountId, sync_state = 'TO_UPDATE' WHERE account_id = :oldAccountId")
    int updateAccountIdForBills(String oldAccountId, String newAccountId);

    @Query("UPDATE bills SET account_id = NULL, sync_state = 'TO_UPDATE' WHERE account_id = :accountId")
    int setAccountIdToNull(String accountId);

    @Query("SELECT * FROM bills WHERE object_id = :objectId AND sync_state != 'TO_DELETE' LIMIT 1")
    Bill getBillByObjectIdSync(String objectId);

    @Query("SELECT * FROM bills " +
            "WHERE user_id = :userId " +
            "AND category_id = :catCloudId " +
            "AND type = :billType " +
            "AND excludeBudget = 0 " +
            "AND billTime >= :startMs AND billTime <= :endMs " +
            "AND sync_state != 'TO_DELETE'")
    List<Bill> getBillsByCategoryInRange(String userId, String catCloudId,
                                         int billType, long startMs, long endMs);

    @Query("SELECT * FROM bills " +
            "WHERE user_id = :userId " +
            "AND type = 0 " +
            "AND excludeBudget = 0 " +
            "AND billTime >= :startMs AND billTime <= :endMs " +
            "AND sync_state != 'TO_DELETE'")
    List<Bill> getExpenseBillsInRange(String userId, long startMs, long endMs);

    @Query("SELECT * FROM bills " +
            "WHERE user_id = :userId " +
            "AND type = 1 " +
            "AND excludeBudget = 0 " +
            "AND billTime >= :startMs AND billTime <= :endMs " +
            "AND sync_state != 'TO_DELETE'")
    List<Bill> getIncomeBillsInRange(String userId, long startMs, long endMs);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM bills " +
            "WHERE user_id = :userId AND type = :type AND excludeBudget = 0 " +
            "AND billTime >= :startMs AND billTime <= :endMs " +
            "AND sync_state != 'TO_DELETE'")
    double getBudgetAmountInRange(String userId, int type, long startMs, long endMs);

    @Query("SELECT category_id, COALESCE(SUM(amount), 0) AS total_amount FROM bills " +
            "WHERE user_id = :userId AND type = :billType AND excludeBudget = 0 " +
            "AND billTime >= :startMs AND billTime <= :endMs " +
            "AND sync_state != 'TO_DELETE' AND category_id IS NOT NULL " +
            "GROUP BY category_id")
    List<CategoryAmount> getBudgetAmountsByCategoryInRange(
            String userId, int billType, long startMs, long endMs);
}
