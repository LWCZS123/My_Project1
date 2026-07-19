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
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;

import java.util.List;

/**
 * AccountDao
 * ----------------------------------------
 * 统一管理「账户（Account）」与「账户组（AccountGroup）」的数据库访问接口
 *
 * 优化：
 *  - 插入返回主键 String
 *  - 更新、删除返回 int 表示影响行数
 *  - 支持按名称查询，避免账户组重名
 *  - 支持按 cloudId 查询，方便云同步
 */
@Dao
public interface AccountDao {

    // ----------------------------------------------------------------------
    // 🟢 账户组相关操作
    // ----------------------------------------------------------------------

    /** 插入账户组（冲突时替换，返回 objectId） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(AccountGroup group);

    /** 批量插入账户组 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertGroups(List<AccountGroup> groups);

    /** 更新账户组（返回受影响行数） */
    @Update
    int updateGroup(AccountGroup group);

    /** 删除账户组（返回受影响行数） */
    @Delete
    int deleteGroup(AccountGroup group);

    /** 查询所有账户组（LiveData） */
    @Query("SELECT * FROM account_groups ORDER BY createdAt ASC")
    LiveData<List<AccountGroup>> getAllGroupsLive();

    /** 同步查询所有账户组（Worker/同步逻辑使用） */
    @Query("SELECT * FROM account_groups ORDER BY createdAt ASC")
    List<AccountGroup> getAllGroupsSync();

    /**查询所有需要同步的账户组*/
    @Query("SELECT * FROM account_groups WHERE sync_state != 'SYNCED'")
    List<AccountGroup> getPendingSyncGroups();


    /** 查询未同步的账户组 */
    @Query("SELECT * FROM account_groups WHERE sync_state != :state")
    List<AccountGroup> getUnsyncedGroups(SyncState state);

    /** 根据用户ID查询账户组 */
    @Query("SELECT * FROM account_groups WHERE user_id = :userId AND sync_state != 'TO_DELETE'ORDER BY createdAt ASC")
    LiveData<List<AccountGroup>> getGroupsByUser(String userId);

    /** 根据用户ID和名称查询账户组（重名校验） */
    @Query("SELECT * FROM account_groups WHERE user_id = :userId AND name = :name LIMIT 1")
    AccountGroup getGroupByName(String userId, String name);

    /** 根据 cloudId 查询账户组（云同步） */
    @Query("SELECT * FROM account_groups WHERE object_id = :cloudId LIMIT 1")
    AccountGroup getGroupByCloudId(String cloudId);

    // ----------------------------------------------------------------------
    // 🟡 账户相关操作
    // ----------------------------------------------------------------------

    /** 插入账户（返回 objectId） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAccount(Account account);

    /** 批量插入账户 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAccounts(List<Account> accounts);

    /** 更新账户（返回受影响行数） */
    @Update
    int update(Account account);

    //更新账户组下面的账户数量
    @Query("UPDATE account_groups SET accountCount = :count, sync_state = 'TO_UPDATE' WHERE object_id = :groupId")
    void updateAccountCount(String groupId, int count);

    /** 删除账户（返回受影响行数） */
    @Delete
    int delete(Account account);

    /** 查询所有账户（LiveData） */
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    LiveData<List<Account>> getAllAccountsLive();

    /**
     * 根据 objectId 查询账户
     * 当账户余额更新时，LiveData会自动通知观察者
     */
    @Query("SELECT * FROM accounts WHERE object_id = :id LIMIT 1")
    LiveData<Account> getAccountByIdLive(String id);

    /** 同步查询所有账户（排除标记删除的） */
    @Query("SELECT * FROM accounts WHERE sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    List<Account> getAllAccountsSyncExcludeDeleted();

    /** 同步查询所有账户 */
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    List<Account> getAllAccountsSync();

    /** 查询某个账户组下的账户（LiveData） */
    @Query("SELECT * FROM accounts WHERE group_id = :groupId AND sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    LiveData<List<Account>> getAccountsByGroup(String groupId);

    /** 查询某个账户组下的账户（同步版） */
    @Query("SELECT * FROM accounts WHERE group_id = :groupId ORDER BY createdAt ASC")
    List<Account> getAccountsByGroupIdSync(String groupId);

    /**查询所有需要同步的账户*/
    @Query("SELECT * FROM accounts WHERE sync_state != 'SYNCED'")
    List<Account> getPendingSyncAccounts();


    /** 查询未同步账户 */
    @Query("SELECT * FROM accounts WHERE sync_state != :state")
    List<Account> getUnsyncedAccounts(SyncState state);

    /** 根据 cloudId 查询账户 */
    @Query("SELECT * FROM accounts WHERE object_id = :cloudId LIMIT 1")
    Account getAccountByCloudId(String cloudId);

    /** 根据 objectId 查询账户（Worker使用） */
    @Query("SELECT * FROM accounts WHERE object_id = :id LIMIT 1")
    Account getAccountById(String id);

    /** 根据本地 ID 查询账户 */
    @Query("SELECT * FROM accounts WHERE id = :localId LIMIT 1")
    Account getAccountByLocalId(long localId);

    /** 根据本地 ID 查询账户 (LiveData) */
    @Query("SELECT * FROM accounts WHERE id = :localId LIMIT 1")
    LiveData<Account> getAccountByLocalIdLive(long localId);

    // ----------------------------------------------------------------------
    // 🧩 联合操作
    // ----------------------------------------------------------------------

    // 查询所有需要同步的账户组


    /** 批量标记删除某个账户组下的所有账户 (性能优化) */
    @Query("UPDATE accounts SET sync_state = 'TO_DELETE', updatedAt = :now WHERE group_id = :groupId")
    int markAccountsAsDeletedByGroup(String groupId, long now);

    /** 删除某个账户组下的所有账户（返回受影响行数） */
    @Query("DELETE FROM accounts WHERE group_id = :groupId")
    int deleteAccountsByGroup(String groupId);

    /** 删除账户组及其账户（事务操作） */
    @Transaction
    default void deleteGroupWithAccounts(AccountGroup group) {
        deleteAccountsByGroup(group.getObjectId());
        deleteGroup(group);
    }

    /** 插入完整账户组及账户（事务操作） */
    @Transaction
    default void insertGroupWithAccounts(AccountGroup group, List<Account> accounts) {
        insertGroup(group);
        if (accounts != null && !accounts.isEmpty()) {
            insertAccounts(accounts);
        }
    }

    /** 根据用户ID查询所有账户 */
    @Query("SELECT * FROM accounts WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    LiveData<List<Account>> getAccountsByUser(String userId);

    @Query("SELECT * FROM account_groups WHERE object_id = :groupId LIMIT 1")
    AccountGroup getById(String groupId);
}
