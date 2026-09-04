package com.example.my_project1.data.remote.model.cloudsaving;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;
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
 * BmobSavingApiImpl - 存钱模块 Bmob 云端 API 实现
 */
public class BmobSavingApiImpl implements BmobSavingApi {

    private static final String TAG = "BmobSavingApiImpl";
    private static final int QUERY_LIMIT = 500;
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final AppDatabase db;

    public BmobSavingApiImpl(Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // --- 计划同步 ---

    public boolean uploadPlanSync(SavingPlan local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) return false;

            CloudSavingPlan cloud = CloudSavingPlan.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));
            cloud.setACL(ownerAcl(userId));
            
            String clientKey = buildClientKey("plan", local.getId(), local.getCreatedAt());
            cloud.setClientKey(clientKey);

            String cloudId = local.getObjectId();
            if (isEmpty(cloudId)) {
                cloudId = findPlanIdByClientKey(clientKey);
                if (isEmpty(cloudId)) {
                    cloudId = cloud.saveSync();
                } else {
                    cloud.updateSync(cloudId);
                }
                local.setObjectId(cloudId);
            } else {
                cloud.updateSync(cloudId);
            }

            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(new Date());
            db.savingPlanDao().updatePlan(local);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "同步上传计划异常: " + e.getMessage());
            return false;
        }
    }

    // --- 记录同步 ---

    public boolean uploadRecordSync(SavingRecord local, String planObjectId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null || isEmpty(planObjectId)) return false;

            CloudSavingRecord cloud = CloudSavingRecord.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));
            cloud.setACL(ownerAcl(userId));
            cloud.setPlan(BmobPointerUtil.savingPlan(planObjectId));
            cloud.setPlanObjectId(planObjectId);
            
            String clientKey = buildClientKey("record", local.getId(), local.getCreatedAt());
            cloud.setClientKey(clientKey);

            String cloudId = local.getObjectId();
            if (isEmpty(cloudId)) {
                cloudId = findRecordIdByClientKey(clientKey);
                if (isEmpty(cloudId)) {
                    cloudId = cloud.saveSync();
                } else {
                    cloud.updateSync(cloudId);
                }
                local.setObjectId(cloudId);
            } else {
                cloud.updateSync(cloudId);
            }

            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(new Date());
            db.savingPlanDao().updateRecord(local);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "同步上传记录异常: " + e.getMessage());
            return false;
        }
    }

    // --- 删除逻辑 ---

    public boolean deletePlanSync(String objectId) {
        return deleteObjectSync(new CloudSavingPlan(objectId));
    }

    public boolean deleteRecordSync(String objectId) {
        CloudSavingRecord cloud = new CloudSavingRecord();
        cloud.setObjectId(objectId);
        return deleteObjectSync(cloud);
    }

    // --- 拉取逻辑 ---

    public List<CloudSavingPlan> fetchPlansSync() throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudSavingPlan> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.setLimit(QUERY_LIMIT);
        try {
            return query.findObjectsSync(CloudSavingPlan.class);
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return new ArrayList<>();
            throw e;
        }
    }

    public List<CloudSavingRecord> fetchRecordsSync() throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudSavingRecord> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.setLimit(QUERY_LIMIT);
        try {
            return query.findObjectsSync(CloudSavingRecord.class);
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return new ArrayList<>();
            throw e;
        }
    }

    // --- 辅助 ---

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

    private String findPlanIdByClientKey(String clientKey) throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudSavingPlan> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.addWhereEqualTo("clientKey", clientKey);
        query.setLimit(1);
        try {
            List<CloudSavingPlan> list = query.findObjectsSync(CloudSavingPlan.class);
            return (list != null && !list.isEmpty()) ? list.get(0).getObjectId() : null;
        } catch (BmobException e) {
            if (e.getErrorCode() == 101) return null;
            throw e;
        }
    }

    private String findRecordIdByClientKey(String clientKey) throws BmobException {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        BmobQuery<CloudSavingRecord> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.addWhereEqualTo("clientKey", clientKey);
        query.setLimit(1);
        try {
            List<CloudSavingRecord> list = query.findObjectsSync(CloudSavingRecord.class);
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
            if (!latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (error[0] != null) {
            if (error[0].getErrorCode() == 101) return true;
            return false;
        }
        return true;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
