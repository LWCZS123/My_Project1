package com.example.my_project1.data.remote.model.cloudwish;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobACL;
import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

/**
 * BmobWishApiImpl - 愿望模块 Bmob 云端 API 实现
 * -------------------------------------------------------
 * 功能：
 * 1. 负责愿望(Wish)与存钱记录(WishRecord)的云端增删改查
 * 2. 提供同步方法供 Worker 使用，并在操作成功后更新本地数据库状态
 * 3. 严格遵循代码规范，注释不含表情符号
 */
public class BmobWishApiImpl {

    private static final String TAG = "BmobWishApiImpl";
    private static final int QUERY_LIMIT = 500;
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final AppDatabase db;

    public BmobWishApiImpl(Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    /**
     * 获取当前登录用户 ID
     */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // ----------------------------------------------------------------------
    // 🟢 愿望上传与同步
    // ----------------------------------------------------------------------

    /**
     * 同步上传愿望（阻塞）
     * 逻辑：如果本地无 objectId 则尝试按 clientKey 查重，查不到则创建，查到或已有则更新。
     * 操作成功后会更新本地数据库的同步状态和时间戳。
     */
    public boolean uploadWishSync(Wish local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                Log.e(TAG, "uploadWishSync 失败: 用户未登录");
                return false;
            }

            CloudWish cloud = CloudWish.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));
            cloud.setACL(ownerAcl(userId));
            
            // 构建幂等键，防止重复提交
            String clientKey = buildClientKey("wish", local.getId(), local.getCreatedAt());
            cloud.setClientKey(clientKey);

            String cloudId = local.getObjectId();
            if (isEmpty(cloudId)) {
                // 检查云端是否已存在（针对本地未及时回写 objectId 的重试场景）
                cloudId = findWishIdByClientKey(clientKey);
                if (isEmpty(cloudId)) {
                    cloudId = cloud.saveSync();
                    Log.d(TAG, "同步创建愿望成功: " + local.getWishName() + " -> " + cloudId);
                } else {
                    cloud.updateSync(cloudId);
                    Log.d(TAG, "云端已存在相同 clientKey 的愿望，已同步更新: " + cloudId);
                }
                local.setObjectId(cloudId);
            } else {
                cloud.updateSync(cloudId);
                Log.d(TAG, "同步更新愿望成功: " + local.getWishName());
            }

            // 更新本地同步状态
            Date now = new Date();
            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(now);

            if (db != null) {
                db.wishDao().updateWish(local);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "同步上传愿望异常: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 同步上传记录（阻塞）
     */
    public boolean uploadRecordSync(WishRecord local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                Log.e(TAG, "uploadRecordSync 失败: 用户未登录");
                return false;
            }

            if (isEmpty(local.getWishObjectId())) {
                Log.e(TAG, "uploadRecordSync 失败: 父愿望 objectId 为空");
                return false;
            }

            CloudWishRecord cloud = CloudWishRecord.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));
            cloud.setACL(ownerAcl(userId));
            cloud.setWish(BmobPointerUtil.wish(local.getWishObjectId()));
            
            String clientKey = buildClientKey("record", local.getId(), local.getCreatedAt());
            cloud.setClientKey(clientKey);

            String cloudId = local.getObjectId();
            if (isEmpty(cloudId)) {
                cloudId = findRecordIdByClientKey(clientKey);
                if (isEmpty(cloudId)) {
                    cloudId = cloud.saveSync();
                    Log.d(TAG, "同步创建记录成功: 金额=" + local.getAmount() + " -> " + cloudId);
                } else {
                    cloud.updateSync(cloudId);
                    Log.d(TAG, "云端已存在相同 clientKey 的记录，已同步更新: " + cloudId);
                }
                local.setObjectId(cloudId);
            } else {
                cloud.updateSync(cloudId);
                Log.d(TAG, "同步更新记录成功: " + cloudId);
            }

            Date now = new Date();
            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(now);

            if (db != null) {
                db.wishDao().updateRecord(local);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "同步上传记录异常: " + e.getMessage(), e);
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // 🔴 愿望删除
    // ----------------------------------------------------------------------

    /**
     * 同步删除愿望
     */
    public boolean deleteWishSync(String objectId) {
        return deleteObjectSync(new CloudWish(objectId));
    }

    /**
     * 同步删除记录
     */
    public boolean deleteRecordSync(String objectId) {
        CloudWishRecord record = new CloudWishRecord();
        record.setObjectId(objectId);
        return deleteObjectSync(record);
    }

    /**
     * 同步删除某愿望下的所有记录
     */
    public boolean deleteRecordsForWishSync(String wishObjectId) {
        if (isEmpty(wishObjectId)) return true;
        try {
            BmobQuery<CloudWishRecord> query = new BmobQuery<>();
            query.addWhereEqualTo("wish", BmobPointerUtil.wish(wishObjectId));
            query.setLimit(QUERY_LIMIT);
            List<CloudWishRecord> records = query.findObjectsSync(CloudWishRecord.class);
            if (records == null || records.isEmpty()) return true;

            boolean allSuccess = true;
            for (CloudWishRecord record : records) {
                if (!deleteObjectSync(record)) {
                    allSuccess = false;
                }
            }
            return allSuccess;
        } catch (BmobException e) {
            // 错误码 101 表示表不存在，即没有记录需要删除，视为成功
            if (e.getErrorCode() == 101) return true;
            Log.e(TAG, "删除关联记录异常: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "删除关联记录未知异常: " + e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // 🟡 愿望拉取
    // ----------------------------------------------------------------------

    /**
     * 同步拉取愿望列表
     */
    public List<CloudWish> fetchWishesSync() throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudWish> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("-updatedAt");
        query.setLimit(QUERY_LIMIT);
        try {
            return query.findObjectsSync(CloudWish.class);
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return new ArrayList<>();
            throw e;
        }
    }

    /**
     * 同步拉取记录列表
     */
    public List<CloudWishRecord> fetchRecordsSync() throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudWishRecord> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("-updatedAt");
        query.setLimit(QUERY_LIMIT);
        try {
            return query.findObjectsSync(CloudWishRecord.class);
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return new ArrayList<>();
            throw e;
        }
    }

    // ----------------------------------------------------------------------
    // 🛠️ 辅助方法
    // ----------------------------------------------------------------------

    private BmobACL ownerAcl(String userId) {
        BmobACL acl = new BmobACL();
        acl.setReadAccess(userId, true);
        acl.setWriteAccess(userId, true);
        return acl;
    }

    private String buildClientKey(String type, long localId, Date createdAt) {
        long time = createdAt != null ? createdAt.getTime() : 0L;
        return type + ":" + localId + ":" + time;
    }

    private String findWishIdByClientKey(String clientKey) throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudWish> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.addWhereEqualTo("clientKey", clientKey);
        query.setLimit(1);
        try {
            List<CloudWish> list = query.findObjectsSync(CloudWish.class);
            return (list != null && !list.isEmpty()) ? list.get(0).getObjectId() : null;
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return null;
            throw e;
        }
    }

    private String findRecordIdByClientKey(String clientKey) throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudWishRecord> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.addWhereEqualTo("clientKey", clientKey);
        query.setLimit(1);
        try {
            List<CloudWishRecord> list = query.findObjectsSync(CloudWishRecord.class);
            return (list != null && !list.isEmpty()) ? list.get(0).getObjectId() : null;
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return null;
            throw e;
        }
    }

    private boolean deleteObjectSync(BmobObject object) {
        if (isEmpty(object.getObjectId())) return true;
        
        final CountDownLatch latch = new CountDownLatch(1);
        final BmobException[] error = new BmobException[1];
        
        object.delete(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                error[0] = e;
                latch.countDown();
            }
        });

        try {
            if (!latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.e(TAG, "删除操作超时: " + object.getObjectId());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (error[0] != null) {
            // 错误码 101 表示云端数据已不存在，视为删除成功
            if (error[0].getErrorCode() == 101) {
                Log.d(TAG, "云端数据已不存在，视为删除成功: " + object.getObjectId());
                return true;
            }
            Log.e(TAG, "删除失败: " + error[0].getMessage());
            return false;
        }

        Log.d(TAG, "删除成功: " + object.getObjectId());
        return true;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
