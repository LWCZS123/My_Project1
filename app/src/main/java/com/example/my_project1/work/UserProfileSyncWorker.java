package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.data.remote.model.clouduser.BmobUserApiImpl;
import com.example.my_project1.data.remote.model.clouduser.CloudUserProfile;
import com.example.my_project1.utils.DateConvertUtil;

import java.util.Date;
import java.util.List;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * UserProfileSyncWorker - 用户信息同步Worker (完整版)
 * -------------------------------------------------------
 * ✅ 与 BillSyncWorker 保持完全一致的代码风格
 * ✅ 支持双向同步：本地→云端，云端→本地
 * ✅ 支持离线编辑，有网络时自动同步
 * ✅ 登录后自动拉取云端数据
 *
 * 🔧 功能:
 * 1. 上传本地待同步的用户信息到云端（TO_UPDATE）
 * 2. 从云端拉取最新用户信息到本地（登录后）
 * 3. 最多重试3次，与 BillSyncWorker 一致
 */
public class UserProfileSyncWorker extends Worker {

    private static final String TAG = "UserProfileSyncWorker";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final AppDatabase db;
    private final BmobUserApiImpl api;

    public UserProfileSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobUserApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "========== 开始用户信息同步 ==========");

            // 检查登录状态
            BmobUser currentUser = BmobUser.getCurrentUser();
            if (currentUser == null) {
                Log.w(TAG, "⚠️ 用户未登录，跳过同步");
                return Result.failure();
            }

            String userId = currentUser.getObjectId();
            Log.d(TAG, "📱 当前用户ID: " + userId);

            // 🔑 步骤1: 上传本地修改到云端（与 BillSyncWorker 一致）
            boolean uploadSuccess = syncUserProfilesToCloud();
            if (!uploadSuccess) {
                Log.w(TAG, "用户信息上传未完全成功，将重试");
                return Result.retry();
            }

            //  从云端拉取最新数据到本地
            boolean downloadSuccess = syncUserProfileFromCloud(userId);
            if (!downloadSuccess) {
                Log.w(TAG, "用户信息下载未完全成功，将重试");
                return Result.retry();
            }

            Log.i(TAG, "========== ✅ 用户信息同步完成 ==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "❌ doWork 异常: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ======================== 🔑 上传：本地 → 云端 ========================

    /**
     * 同步用户信息到云端
     * 与 BillSyncWorker.syncBills() 保持一致的逻辑
     */
    private boolean syncUserProfilesToCloud() {
        List<UserProfile> profiles = db.userProfileDao().getPendingSyncProfilesSimple();

        if (profiles == null || profiles.isEmpty()) {
            Log.d(TAG, "syncUserProfilesToCloud - 无待同步用户信息");
            return true;
        }

        Log.i(TAG, "syncUserProfilesToCloud - 待同步用户信息: " + profiles.size() + " 条");

        int successCount = 0;
        int failCount = 0;

        for (UserProfile profile : profiles) {
            // 跳过待删除的（用户信息一般不删除）
            if (profile.getSyncState() == SyncState.TO_DELETE) {
                Log.d(TAG, "跳过待删除用户信息: " + profile.getUsername());
                continue;
            }

            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    SyncState state = profile.getSyncState();
                    if (state == SyncState.TO_UPDATE || state == SyncState.SYNC_FAILED) {
                        // 🔑 关键：uploadUserProfileSync 内部会更新数据库状态
                        ok = api.uploadUserProfileSync(profile);
                    } else {
                        ok = true; // 已同步状态，跳过
                    }

                    if (ok) break;
                    Log.w(TAG, "syncUserProfilesToCloud - 第 " + attempt + " 次失败: " + profile.getUsername());

                    // 重试延迟
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "syncUserProfilesToCloud - 异常 attempt=" + attempt, t);
                }
            }

            if (ok) {
                successCount++;
                // 注意：uploadUserProfileSync 已经更新了数据库状态，这里不需要重复更新
            } else {
                failCount++;
                Log.e(TAG, "❌ 用户信息同步最终失败: " + profile.getUsername());
            }
        }

        Log.i(TAG, String.format("syncUserProfilesToCloud 完成 - 成功:%d, 失败:%d",
                successCount, failCount));

        return failCount == 0;
    }

    // ======================== 🔑 下载：云端 → 本地 ========================

    /**
     * 从云端同步用户信息到本地
     * 🔑 关键：主动拉取云端数据并保存到本地数据库
     */
    private boolean syncUserProfileFromCloud(String userId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Log.d(TAG, "🔄 开始同步用户信息 (第" + attempt + "次尝试)");

                // 1. 从云端获取用户信息
                CloudUserProfile cloudUser = api.getCurrentUser();

                if (cloudUser == null) {
                    Log.e(TAG, "   ❌ 无法获取云端用户信息 (第" + attempt + "次)");
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    return false;
                }

                // 2. 验证用户ID匹配
                if (!cloudUser.getObjectId().equals(userId)) {
                    Log.e(TAG, "   ❌ 用户ID不匹配: 期望=" + userId + ", 实际=" + cloudUser.getObjectId());
                    return false;
                }

                Log.d(TAG, "   ✅ 成功获取云端用户信息:");
                Log.d(TAG, "      - 用户名: " + cloudUser.getUsername());
                Log.d(TAG, "      - 邮箱: " + cloudUser.getEmail());
                Log.d(TAG, "      - 头像: " + cloudUser.getAvatarUrl());
                Log.d(TAG, "      - 背景: " + cloudUser.getBackgroundUrl());
                Log.d(TAG, "      - 性别: " + cloudUser.getGender());
                Log.d(TAG, "      - 学校: " + cloudUser.getSchool());
                Log.d(TAG, "      - 签名: " + cloudUser.getSignature());
                Log.d(TAG, "      - 记账天数: " + cloudUser.getBillDays());
                Log.d(TAG, "      - 记账总数: " + cloudUser.getBillCount());

                // 3. 转换为本地实体
                UserProfile localProfile = cloudUser.toLocalEntity();

                // 4. 检查本地是否已存在
                UserProfile existingProfile = db.userProfileDao().getUserProfileSync(userId);

                if (existingProfile != null) {
                    Log.d(TAG, "   📝 本地已存在用户信息，检查是否需要更新");

                    // 🔑 关键：保护本地未同步的修改
                    if (existingProfile.getSyncState() != SyncState.SYNCED) {
                        Log.w(TAG, "   ⚠️ 本地有待同步修改(状态=" + existingProfile.getSyncState() + ")，跳过云端更新");
                        return true; // 跳过更新，但不视为失败
                    }

                    // 🔑 关键：比较更新时间，只有云端更新时间更新时才更新
                    Date cloudUpdatedAt = DateConvertUtil.safeConvertToDate(cloudUser.getUpdatedAt());
                    Date localUpdatedAt = existingProfile.getUpdatedAt();

                    if (cloudUpdatedAt != null && localUpdatedAt != null) {
                        if (cloudUpdatedAt.after(localUpdatedAt)) {
                            // 云端更新时间更新，使用云端数据
                            localProfile.setId(existingProfile.getId()); // 保留本地ID
                            localProfile.setSyncState(SyncState.SYNCED);
                            localProfile.setUpdatedAt(new Date());

                            int rows = db.userProfileDao().update(localProfile);

                            if (rows > 0) {
                                Log.i(TAG, "   ✅ 本地用户信息更新成功 (云端更新)");
                                return true;
                            } else {
                                Log.e(TAG, "   ❌ 本地用户信息更新失败 (第" + attempt + "次)");
                            }
                        } else {
                            Log.d(TAG, "   ✅ 本地数据已是最新，无需更新");
                            return true;
                        }
                    } else {
                        // 时间信息不完整，强制更新
                        localProfile.setId(existingProfile.getId());
                        localProfile.setSyncState(SyncState.SYNCED);
                        localProfile.setUpdatedAt(new Date());

                        int rows = db.userProfileDao().update(localProfile);

                        if (rows > 0) {
                            Log.i(TAG, "   ✅ 本地用户信息更新成功 (强制更新)");
                            return true;
                        } else {
                            Log.e(TAG, "   ❌ 本地用户信息更新失败 (第" + attempt + "次)");
                        }
                    }

                } else {
                    Log.d(TAG, "   📝 本地不存在用户信息，执行插入");

                    // 设置创建和更新时间
                    localProfile.setCreatedAt(new Date());
                    localProfile.setUpdatedAt(new Date());
                    localProfile.setSyncState(SyncState.SYNCED);

                    // 插入数据库
                    long id = db.userProfileDao().insert(localProfile);

                    if (id > 0) {
                        Log.i(TAG, "   ✅ 本地用户信息插入成功, ID=" + id);
                        return true;
                    } else {
                        Log.e(TAG, "   ❌ 本地用户信息插入失败 (第" + attempt + "次)");
                    }
                }

                // 如果执行到这里说明数据库操作失败，准备重试
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "   ⏳ 等待重试...");
                    Thread.sleep(RETRY_DELAY_MS);
                }

            } catch (InterruptedException e) {
                Log.e(TAG, "   ❌ 同步被中断 (第" + attempt + "次): " + e.getMessage(), e);
                Thread.currentThread().interrupt();
                return false;

            } catch (Exception e) {
                Log.e(TAG, "   ❌ 同步异常 (第" + attempt + "次): " + e.getMessage(), e);

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }

        Log.e(TAG, "❌ 用户信息同步最终失败，已达最大重试次数");
        return false;
    }

    // ======================== WorkManager 配置 ========================

    /**
     * 获取默认约束条件
     */
    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /**
     * 加入同步队列（保持已有任务）
     * 🎯 适用场景：定期同步、App启动时检查
     */
    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UserProfileSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        "UserProfileSync",
                        ExistingWorkPolicy.KEEP,  // 保持已有任务
                        request
                );

        Log.d(TAG, "🚀 已加入用户信息同步任务队列 (KEEP)");
    }

    /**
     * 立即执行同步（替换已有任务）
     * 🎯 适用场景：登录成功后、用户手动刷新
     */
    public static void enqueueImmediate(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UserProfileSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        "UserProfileSync",
                        ExistingWorkPolicy.REPLACE,  // 替换已有任务
                        request
                );

        Log.d(TAG, "🚀 已加入用户信息同步任务队列 (REPLACE - 立即执行)");
    }

    /**
     * 取消所有用户信息同步任务
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context)
                .cancelUniqueWork("UserProfileSync");
        Log.d(TAG, "🛑 已取消用户信息同步任务");
    }
}