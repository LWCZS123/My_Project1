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
 */
@Dao
public interface AccountDao {

    // ----------------------------------------------------------------------
    // 🟢 账户组相关操作
    // ----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(AccountGroup group);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertGroups(List<AccountGroup> groups);

    @Update
    int updateGroup(AccountGroup group);

    @Delete
    int deleteGroup(AccountGroup group);

    @Query("SELECT * FROM account_groups ORDER BY createdAt ASC")
    LiveData<List<AccountGroup>> getAllGroupsLive();

    @Query("SELECT * FROM account_groups ORDER BY createdAt ASC")
    List<AccountGroup> getAllGroupsSync();

    @Query("SELECT * FROM account_groups WHERE sync_state != 'SYNCED'")
    List<AccountGroup> getPendingSyncGroups();

    @Query("SELECT * FROM account_groups WHERE sync_state != :state")
    List<AccountGroup> getUnsyncedGroups(SyncState state);

    @Query("SELECT * FROM account_groups WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    LiveData<List<AccountGroup>> getGroupsByUser(String userId);

    @Query("SELECT * FROM account_groups WHERE user_id = :userId AND name = :name LIMIT 1")
    AccountGroup getGroupByName(String userId, String name);

    @Query("SELECT * FROM account_groups WHERE object_id = :cloudId LIMIT 1")
    AccountGroup getGroupByCloudId(String cloudId);

    // ----------------------------------------------------------------------
    // 🟡 账户相关操作
    // ----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAccount(Account account);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAccounts(List<Account> accounts);

    @Update
    int update(Account account);

    @Query("UPDATE account_groups SET accountCount = :count, sync_state = 'TO_UPDATE' WHERE object_id = :groupId")
    void updateAccountCount(String groupId, int count);

    @Delete
    int delete(Account account);

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    LiveData<List<Account>> getAllAccountsLive();

    @Query("SELECT * FROM accounts WHERE object_id = :id LIMIT 1")
    LiveData<Account> getAccountByIdLive(String id);

    @Query("SELECT * FROM accounts WHERE sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    List<Account> getAllAccountsSyncExcludeDeleted();

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    List<Account> getAllAccountsSync();

    @Query("SELECT * FROM accounts WHERE group_id = :groupId AND sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    LiveData<List<Account>> getAccountsByGroup(String groupId);

    @Query("SELECT * FROM accounts WHERE group_id = :groupId ORDER BY createdAt ASC")
    List<Account> getAccountsByGroupIdSync(String groupId);

    @Query("SELECT * FROM accounts WHERE sync_state != 'SYNCED'")
    List<Account> getPendingSyncAccounts();

    @Query("SELECT * FROM accounts WHERE sync_state != :state")
    List<Account> getUnsyncedAccounts(SyncState state);

    @Query("SELECT * FROM accounts WHERE object_id = :cloudId LIMIT 1")
    Account getAccountByCloudId(String cloudId);

    @Query("SELECT * FROM accounts WHERE object_id = :id LIMIT 1")
    Account getAccountById(String id);

    @Query("SELECT * FROM accounts WHERE id = :localId LIMIT 1")
    Account getAccountByLocalId(long localId);

    @Query("SELECT * FROM accounts WHERE id = :localId LIMIT 1")
    LiveData<Account> getAccountByLocalIdLive(long localId);

    @Query("UPDATE accounts SET sync_state = 'TO_DELETE', updatedAt = :now WHERE group_id = :groupId")
    int markAccountsAsDeletedByGroup(String groupId, long now);

    @Query("DELETE FROM accounts WHERE group_id = :groupId")
    int deleteAccountsByGroup(String groupId);

    @Transaction
    default void deleteGroupWithAccounts(AccountGroup group) {
        deleteAccountsByGroup(group.getObjectId());
        deleteGroup(group);
    }

    @Transaction
    default void insertGroupWithAccounts(AccountGroup group, List<Account> accounts) {
        insertGroup(group);
        if (accounts != null && !accounts.isEmpty()) {
            insertAccounts(accounts);
        }
    }

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY createdAt ASC")
    LiveData<List<Account>> getAccountsByUser(String userId);

    @Query("SELECT * FROM account_groups WHERE object_id = :groupId LIMIT 1")
    AccountGroup getById(String groupId);

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND name LIKE :keyword AND sync_state != 'TO_DELETE' LIMIT 5")
    List<Account> searchAccounts(String userId, String keyword);
}
