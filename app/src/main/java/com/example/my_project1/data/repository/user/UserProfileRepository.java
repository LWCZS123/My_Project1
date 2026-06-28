package com.example.my_project1.data.repository.user;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.UserProfileDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.data.remote.model.clouduser.BmobUserApiImpl;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.UserProfileSyncWorker;

import java.util.Date;

import cn.bmob.v3.BmobUser;

/**
 * UserProfileRepository - 用户信息仓库层 (完整优化版)
 * -------------------------------------------------------
 * ✅
 * ✅ 支持离线编辑：无网络时标记 TO_UPDATE，有网络时自动同步
 * ✅ 所有修改操作都触发 UserProfileSyncWorker
 * ✅ 图片上传不显示进度，直接返回成功/失败
 */
public class UserProfileRepository {

    private static final String TAG = "UserProfileRepository";
    private static final String OSS_PUBLIC_BASE_URL = "https://xd-user-image.oss-cn-hangzhou.aliyuncs.com/";

    private static volatile UserProfileRepository instance;

    private final Context context;
    private final UserProfileDao dao;
    private final BmobUserApiImpl api;
    private final AppExecutors executors;

    private UserProfileRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        this.dao = db.userProfileDao();
        this.api = new BmobUserApiImpl(context);
        this.executors = AppExecutors.get();
    }

    public static UserProfileRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (UserProfileRepository.class) {
                if (instance == null) {
                    instance = new UserProfileRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ==================== 查询操作 ====================

    /**
     * 获取用户信息 (LiveData)
     * ✅ 自动触发后台同步
     */
    public LiveData<UserProfile> getUserProfile(String userId) {
        // 触发后台同步（使用 KEEP 策略，避免重复执行）
        UserProfileSyncWorker.enqueue(context);

        // 返回本地 LiveData
        return dao.getUserProfileLiveData(userId);
    }

    /**
     * 同步获取用户信息
     */
    public UserProfile getUserProfileSync(String userId) {
        return dao.getUserProfileSync(userId);
    }

    // ==================== 更新操作 ====================

    /**
     * 更新用户信息
     * 🔑 关键：标记为 TO_UPDATE，触发同步
     */
    public void updateUserProfile(UserProfile profile, ApiResponse.Callback<Void> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 标记为待同步状态
                profile.setUpdatedAt(new Date());
                profile.setSyncState(SyncState.TO_UPDATE);

                int rows = dao.update(profile);

                if (rows > 0) {
                    Log.d(TAG, "✅ 本地用户信息更新成功");

                    // 触发后台同步
                    UserProfileSyncWorker.enqueue(context);
                    Log.d(TAG, "✅ 已触发用户信息同步任务");

                    executors.mainThread().execute(() -> {
                        if (callback != null) {
                            callback.onComplete(ApiResponse.success(null, "更新成功"));
                        }
                    });
                } else {
                    Log.e(TAG, "❌ 本地用户信息更新失败");
                    executors.mainThread().execute(() -> {
                        if (callback != null) {
                            callback.onComplete(ApiResponse.error("本地更新失败"));
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 更新用户信息异常: " + e.getMessage(), e);
                executors.mainThread().execute(() -> {
                    if (callback != null) {
                        callback.onComplete(ApiResponse.error(e));
                    }
                });
            }
        });
    }

    /**
     * 更新头像
     * 🔑 不显示上传进度，直接返回成功/失败
     */
    public void updateAvatar(String userId, String avatarObjectKey, ApiResponse.Callback<String> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 拼接成完整 URL
                String fullAvatarUrl = avatarObjectKey.startsWith("http")
                        ? avatarObjectKey
                        : OSS_PUBLIC_BASE_URL + avatarObjectKey;

                // 更新本地数据库，标记为 TO_UPDATE
                long now = System.currentTimeMillis();
                int rows = dao.updateAvatar(userId, fullAvatarUrl, now, SyncState.TO_UPDATE);

                if (rows > 0) {
                    Log.d(TAG, "✅ 本地头像更新成功: " + fullAvatarUrl);

                    // 触发后台同步
                    UserProfileSyncWorker.enqueue(context);
                    Log.d(TAG, "✅ 已触发用户信息同步任务");

                    executors.mainThread().execute(() -> {
                        if (callback != null) {
                            callback.onComplete(ApiResponse.success(fullAvatarUrl, "头像更新成功"));
                        }
                    });
                } else {
                    Log.e(TAG, "❌ 本地头像更新失败");
                    executors.mainThread().execute(() -> {
                        if (callback != null) {
                            callback.onComplete(ApiResponse.error("本地更新失败"));
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ 更新头像异常: " + e.getMessage(), e);
                executors.mainThread().execute(() -> {
                    if (callback != null) {
                        callback.onComplete(ApiResponse.error(e));
                    }
                });
            }
        });
    }

    /**
     * 更新背景图
     * 🔑 不显示上传进度，直接返回成功/失败
     */
    public void updateBackground(String userId, String backgroundObjectKey, ApiResponse.Callback<String> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 拼接成完整 URL
                String fullBackgroundUrl = backgroundObjectKey.startsWith("http")
                        ? backgroundObjectKey
                        : OSS_PUBLIC_BASE_URL + backgroundObjectKey;

                // 更新本地数据库，标记为 TO_UPDATE
                long now = System.currentTimeMillis();
                int rows = dao.updateBackground(userId, fullBackgroundUrl, now, SyncState.TO_UPDATE);

                if (rows > 0) {
                    Log.d(TAG, "✅ 本地背景图更新成功: " + fullBackgroundUrl);

                    // 触发后台同步
                    UserProfileSyncWorker.enqueue(context);
                    Log.d(TAG, "✅ 已触发用户信息同步任务");

                    executors.mainThread().execute(() -> {
                        if (callback != null) {
                            callback.onComplete(ApiResponse.success(fullBackgroundUrl, "背景图更新成功"));
                        }
                    });
                } else {
                    Log.e(TAG, "❌ 本地背景图更新失败");
                    executors.mainThread().execute(() -> {
                        if (callback != null) {
                            callback.onComplete(ApiResponse.error("本地更新失败"));
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ 更新背景图异常: " + e.getMessage(), e);
                executors.mainThread().execute(() -> {
                    if (callback != null) {
                        callback.onComplete(ApiResponse.error(e));
                    }
                });
            }
        });
    }

    /**
     * 更新账单统计
     * 🔑 关键：标记为 TO_UPDATE，触发同步
     */
    public void updateBillStats(String userId, int days, int count) {
        executors.diskIO().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                int rows = dao.updateBillStats(userId, days, count, now, SyncState.TO_UPDATE);

                if (rows > 0) {
                    // 触发后台同步
                    UserProfileSyncWorker.enqueue(context);
                    Log.d(TAG, "✅ 账单统计更新成功并触发同步: days=" + days + ", count=" + count);
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 更新账单统计失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 增加账单计数
     * 标记为 TO_UPDATE，触发同步
     */
    public void incrementBillCount(String userId) {
        executors.diskIO().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                int rows = dao.incrementBillCount(userId, now, SyncState.TO_UPDATE);

                if (rows > 0) {
                    // 触发后台同步
                    UserProfileSyncWorker.enqueue(context);

                    // 获取当前计数并记录日志
                    UserProfile profile = dao.getUserProfileSync(userId);
                    if (profile != null) {
                        Log.d(TAG, "✅ 账单计数增加成功并触发同步: " + profile.getBillCount());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 增加账单计数失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 强制刷新用户信息
     * 🎯 使用场景：用户手动下拉刷新
     */
    public void forceRefresh(ApiResponse.Callback<Void> callback) {
        Log.d(TAG, "🔄 开始强制刷新用户信息");

        // 立即触发同步（使用 REPLACE 策略）
        UserProfileSyncWorker.enqueueImmediate(context);

        executors.mainThread().execute(() -> {
            if (callback != null) {
                callback.onComplete(ApiResponse.success(null, "刷新中..."));
            }
        });
    }

    // ==================== 兼容旧版回调接口 ====================

    /**
     * 兼容旧版的 UpdateCallback
     * @deprecated 建议使用 ApiResponse.Callback<Void>
     */
    @Deprecated
    public void updateUserProfile(UserProfile profile, UpdateCallback callback) {
        updateUserProfile(profile, response -> {
            if (response.isSuccess()) {
                callback.onSuccess();
            } else if (response.isError()) {
                callback.onError(response.message);
            }
        });
    }

    /**
     * 兼容旧版的 UpdateCallback (头像)
     * @deprecated 建议使用 ApiResponse.Callback<String>
     */
    @Deprecated
    public void updateAvatar(String userId, String avatarUrl, UpdateCallback callback) {
        updateAvatar(userId, avatarUrl, response -> {
            if (response.isSuccess()) {
                callback.onSuccess();
            } else if (response.isError()) {
                callback.onError(response.message);
            }
        });
    }

    /**
     * 兼容旧版的 UpdateCallback (背景图)
     * @deprecated 建议使用 ApiResponse.Callback<String>
     */
    @Deprecated
    public void updateBackground(String userId, String backgroundUrl, UpdateCallback callback) {
        updateBackground(userId, backgroundUrl, response -> {
            if (response.isSuccess()) {
                callback.onSuccess();
            } else if (response.isError()) {
                callback.onError(response.message);
            }
        });
    }

    /**
     * 退出登录
     * 🔑 清除本地数据，注销 Bmob 登录状态
     */
    public void logout(ApiResponse.Callback<Void> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 1. 清除本地数据库所有用户数据
                dao.deleteAll();
                Log.d(TAG, "✅ 本地用户数据已清除");

                // 2. 清除 Bmob 登录状态
                executors.mainThread().execute(() -> {
                    try {
                        BmobUser.logOut();
                        Log.d(TAG, "✅ Bmob 登录状态已清除");

                        if (callback != null) {
                            callback.onComplete(ApiResponse.success(null, "退出成功"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ 清除 Bmob 登录状态失败: " + e.getMessage(), e);
                        if (callback != null) {
                            callback.onComplete(ApiResponse.error("退出失败: " + e.getMessage()));
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ 退出登录异常: " + e.getMessage(), e);
                executors.mainThread().execute(() -> {
                    if (callback != null) {
                        callback.onComplete(ApiResponse.error(e));
                    }
                });
            }
        });
    }

    /**
     * 旧版回调接口
     * @deprecated 建议使用 ApiResponse.Callback
     */
    @Deprecated
    public interface UpdateCallback {
        void onSuccess();
        void onError(String message);
    }
}