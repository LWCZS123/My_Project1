package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.user.UserProfile;

import java.util.List;

/**
 * UserProfileDao - 用户信息数据库访问层 (优化版)
 * -------------------------------------------------------
 * ✅ 添加同步相关查询方法
 * ✅ 与 BillDao 保持一致的代码风格
 * ✅ 添加 deleteAll 方法用于退出登录清空数据
 */
@Dao
public interface UserProfileDao {

    // ==================== 基本操作 ====================

    /**
     * 插入或替换用户信息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(UserProfile profile);

    /**
     * 更新用户信息
     */
    @Update
    int update(UserProfile profile);

    /**
     * 删除用户信息
     */
    @Query("DELETE FROM user_profiles WHERE user_id = :userId")
    int delete(String userId);

    /**
     * 删除所有用户信息
     * 🔑 用于退出登录时清空本地数据
     */
    @Query("DELETE FROM user_profiles")
    int deleteAll();

    // ==================== 查询操作 ====================

    /**
     * 根据用户ID查询用户信息（LiveData）
     */
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId LIMIT 1")
    LiveData<UserProfile> getUserProfileLiveData(String userId);

    /**
     * 根据用户ID同步查询用户信息
     */
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId LIMIT 1")
    UserProfile getUserProfileSync(String userId);

    /**
     * 查询所有用户信息（同步）
     */
    @Query("SELECT * FROM user_profiles")
    List<UserProfile> getAllUserProfilesSync();

    // ==================== 🔑 同步相关查询 ====================

    /**
     * 获取待同步的用户信息
     * 包括：TO_CREATE, TO_UPDATE, SYNC_FAILED
     */
    @Query("SELECT * FROM user_profiles " +
            "WHERE sync_state IN (:toCreate, :toUpdate, :syncFailed)")
    List<UserProfile> getPendingSyncProfiles(
            int toCreate,
            int toUpdate,
            int syncFailed
    );

    /**
     * 获取待同步的用户信息（简化版）
     */
    @Query("SELECT * FROM user_profiles " +
            "WHERE sync_state != 'SYNCED'")  // 0 = SYNCED
    List<UserProfile> getPendingSyncProfilesSimple();

    /**
     * 获取待删除的用户信息
     */
    @Query("SELECT * FROM user_profiles WHERE sync_state = 3")  // 3 = TO_DELETE
    List<UserProfile> getToDeleteProfiles();

    // ==================== 更新操作 ====================

    /**
     * 更新头像URL
     */
    @Query("UPDATE user_profiles " +
            "SET avatar_url = :avatarUrl, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE user_id = :userId")
    int updateAvatar(String userId, String avatarUrl, long updatedAt, SyncState syncState);

    /**
     * 更新背景图URL
     */
    @Query("UPDATE user_profiles " +
            "SET background_url = :backgroundUrl, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE user_id = :userId")
    int updateBackground(String userId, String backgroundUrl, long updatedAt, SyncState syncState);

    /**
     * 更新账单统计数据
     */
    @Query("UPDATE user_profiles " +
            "SET bill_days = :days, bill_count = :count, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE user_id = :userId")
    int updateBillStats(String userId, int days, int count, long updatedAt, SyncState syncState);

    /**
     * 增加账单计数
     */
    @Query("UPDATE user_profiles " +
            "SET bill_count = bill_count + 1, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE user_id = :userId")
    int incrementBillCount(String userId, long updatedAt, SyncState syncState);

    /**
     * 更新同步状态
     */
    @Query("UPDATE user_profiles SET sync_state = :syncState WHERE user_id = :userId")
    int updateSyncState(String userId, SyncState syncState);

    /**
     * 批量更新同步状态为已同步
     */
    @Query("UPDATE user_profiles SET sync_state = 0 WHERE user_id IN (:userIds)")
    int markAsSynced(List<String> userIds);
}