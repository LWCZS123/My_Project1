package com.example.my_project1.data.remote.model.cloudwish;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

/**
 * BmobWishApiImpl - Bmob 愿望 API 实现
 * -------------------------------------------------------
 * 实现愿望的云端同步功能，包括上传、更新和删除
 */
public class BmobWishApiImpl {

    private static final String TAG = "BmobWishApiImpl";
    private static final long TIMEOUT_SECONDS = 30;

    private final Context context;
    private final AppDatabase db;

    public BmobWishApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    /** 获取当前登录用户 ID */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    /**
     * 同步上传单个愿望（阻塞）
     * 如果 objectId 存在则更新，否则创建
     */
    public boolean uploadWishSync(Wish local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                Log.e(TAG, "uploadWishSync - 用户未登录");
                return false;
            }

            CloudWish cloud = CloudWish.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));

            Log.d(TAG, "uploadWishSync() 调用"
                    + " name=" + local.getWishName()
                    + " state=" + local.getSyncState()
                    + " objectId=" + local.getObjectId()
            );

            String cloudId;
            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                // 创建新愿望
                cloudId = cloud.saveSync();
                local.setObjectId(cloudId);
                Log.d(TAG, "同步创建愿望成功: " + local.getWishName() + " -> " + cloudId);
            } else {
                // 更新现有愿望
                cloud.updateSync(local.getObjectId());
                cloudId = local.getObjectId();
                Log.d(TAG, "同步更新愿望成功: " + local.getWishName() + " -> " + cloudId);
            }

            // 更新本地状态和时间戳
            Date now = new Date();
            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(now);

            if (db != null) {
                AppExecutors.get().diskIO().execute(() -> {
                    db.wishDao().updateWish(local);
                    Log.d(TAG, "本地数据库愿望已更新: ID=" + local.getId());
                });
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "同步上传愿望失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 同步删除愿望（阻塞）
     */
    public boolean deleteWishSync(String objectId) {
        if (objectId == null || objectId.isEmpty()) {
            Log.e(TAG, "deleteWishSync - objectId为空");
            return false;
        }

        Log.d(TAG, "同步删除愿望: objectId=" + objectId);

        try {
            final BmobException[] exceptionHolder = new BmobException[1];
            final CountDownLatch latch = new CountDownLatch(1);

            CloudWish cloud = new CloudWish();
            cloud.setObjectId(objectId);

            cloud.delete(new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    exceptionHolder[0] = e;
                    latch.countDown();
                }
            });

            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                Log.e(TAG, "同步删除愿望超时: objectId=" + objectId);
                return false;
            }

            if (exceptionHolder[0] != null) {
                BmobException e = exceptionHolder[0];
                // 如果云端对象不存在（错误码101），视为删除成功
                if (e.getErrorCode() == 101) {
                    Log.d(TAG, "云端愿望已不存在，视为删除成功: objectId=" + objectId);
                    return true;
                }
                Log.e(TAG, "同步删除愿望失败: " + objectId + ", error=" + e.getMessage());
                return false;
            }

            Log.d(TAG, "同步删除愿望成功: " + objectId);
            return true;
        } catch (InterruptedException e) {
            Log.e(TAG, "同步删除愿望被中断", e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "同步删除愿望异常", e);
            return false;
        }
    }

    /**
     * 异步删除愿望接口（供Worker使用）
     */
    public void deleteWish(String objectId, UpdateListener listener) {
        if (objectId == null) {
            listener.done(new BmobException(902, "objectId为空，无法删除愿望"));
            return;
        }
        CloudWish cloud = new CloudWish();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }
}
