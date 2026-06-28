package com.example.my_project1.data.remote.model.clouduser;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.user.UserProfile;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

/**
 * BmobUserApiImpl - Bmob用户信息API实现 (优化版)
 * -------------------------------------------------------
 * ✅ 添加同步上传方法 uploadUserProfileSync
 * ✅ 与 BmobBillApiImpl 保持一致的代码风格
 * ✅ 支持离线编辑，有网络时自动同步
 */
public class BmobUserApiImpl {

    private static final String TAG = "BmobUserApiImpl";
    private static final int TIMEOUT_SECONDS = 30;

    private final Context context;
    private final AppDatabase db;

    public BmobUserApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
    }

    // ==================== 同步上传方法（核心） ====================

    /**
     * 🔑 同步上传用户信息到云端（与 BmobBillApiImpl.uploadBillSync 一致）
     *
     * @param profile 本地用户信息
     * @return true=成功, false=失败
     */
    public boolean uploadUserProfileSync(UserProfile profile) {
        final boolean[] success = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            // 转换为云端对象
            CloudUserProfile cloudProfile = CloudUserProfile.fromLocal(profile);

            // 根据同步状态决定操作
            SyncState state = profile.getSyncState();

            if (state == SyncState.TO_CREATE) {
                // 创建新用户信息（通常不会有这种情况，因为注册时就创建了）
                Log.d(TAG, "⚠️ 用户信息不支持TO_CREATE状态");
                return false;

            } else if (state == SyncState.TO_UPDATE) {
                // 更新已有用户信息
                cloudProfile.update(new UpdateListener() {
                    @Override
                    public void done(BmobException e) {
                        if (e == null) {
                            Log.d(TAG, "✅ 云端更新成功: " + profile.getUsername());

                            // 🔑 关键：更新本地数据库状态
                            try {
                                profile.setSyncState(SyncState.SYNCED);
                                profile.setUpdatedAt(new Date());
                                db.userProfileDao().update(profile);
                                Log.d(TAG, "   ✅ 本地状态已更新为SYNCED");
                                success[0] = true;
                            } catch (Exception ex) {
                                Log.e(TAG, "   ❌ 更新本地状态失败: " + ex.getMessage());
                            }
                        } else {
                            Log.e(TAG, "❌ 云端更新失败: " + e.getMessage() +
                                    " (错误码: " + e.getErrorCode() + ")");
                        }
                        latch.countDown();
                    }
                });

            } else {
                Log.d(TAG, "⚠️ 无需同步，状态=" + state);
                return true;
            }

            // 等待完成
            boolean awaited = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!awaited) {
                Log.e(TAG, "⚠️ 上传用户信息超时");
                return false;
            }

            return success[0];

        } catch (Exception e) {
            Log.e(TAG, "❌ 上传用户信息异常: " + e.getMessage(), e);
            return false;
        }
    }

    // ==================== 原有方法保持不变 ====================

    /**
     * 更新用户信息（异步方法）
     */
    public void updateUserProfile(CloudUserProfile profile, UpdateListener listener) {
        profile.update(listener);
    }

    /**
     * 获取当前登录用户信息
     */
    public CloudUserProfile getCurrentUser() {
        BmobUser currentUser = BmobUser.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "❌ 未登录");
            return null;
        }

        final CloudUserProfile[] result = {null};
        final CountDownLatch latch = new CountDownLatch(1);

        try {

            new cn.bmob.v3.BmobQuery<CloudUserProfile>()
                    .getObject(currentUser.getObjectId(),
                            new cn.bmob.v3.listener.QueryListener<CloudUserProfile>() {

                                @Override
                                public void done(CloudUserProfile user, BmobException e) {

                                    if (e == null) {
                                        Log.d(TAG, "✅ 从云端获取用户成功");
                                        result[0] = user;
                                    } else {
                                        Log.e(TAG, "❌ 获取云端用户失败: " + e.getMessage());
                                    }

                                    latch.countDown();
                                }
                            });

            boolean awaited = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!awaited) {
                Log.e(TAG, "⚠️ 获取用户信息超时");
                return null;
            }

            return result[0];

        } catch (Exception e) {
            Log.e(TAG, "❌ 获取用户异常", e);
            return null;
        }
    }

    /**
     * 更新当前用户的单个字段（同步方法）
     *
     * @deprecated 建议使用 uploadUserProfileSync 统一处理
     */
    @Deprecated
    public boolean updateUserField(String key, Object value) {
        CloudUserProfile currentUser = getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "❌ 未登录，无法更新用户信息");
            return false;
        }

        final boolean[] success = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        CloudUserProfile updateUser = new CloudUserProfile();
        updateUser.setObjectId(currentUser.getObjectId());
        updateUser.setValue(key, value);

        updateUser.update(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 字段更新成功: " + key);
                    success[0] = true;
                } else {
                    Log.e(TAG, "❌ 字段更新失败: " + e.getMessage());
                }
                latch.countDown();
            }
        });

        try {
            boolean awaited = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!awaited) {
                Log.e(TAG, "⚠️ 字段更新超时: " + key);
                return false;
            }
            return success[0];
        } catch (InterruptedException e) {
            Log.e(TAG, "❌ 字段更新异常: " + e.getMessage());
            return false;
        }
    }
}