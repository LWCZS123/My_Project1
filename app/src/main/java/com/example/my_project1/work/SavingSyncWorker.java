package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.dao.SavingPlanDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;
import com.example.my_project1.data.remote.model.cloudsaving.BmobSavingApiImpl;
import com.example.my_project1.data.remote.model.cloudsaving.CloudSavingPlan;
import com.example.my_project1.data.remote.model.cloudsaving.CloudSavingRecord;

import java.util.Date;
import java.util.List;

/**
 * SavingSyncWorker - 存钱计划双向同步任务
 * -------------------------------------------------------
 * 逻辑顺序：
 * 1. 同步本地删除：先删记录，后删计划
 * 2. 同步本地变更：先传计划，后传记录
 * 3. 合并云端变更：拉取云端数据更新本地
 */
public class SavingSyncWorker extends Worker {

    public static final String WORK_NAME = "saving_sync_unique";
    private static final String TAG = "SavingSyncWorker";
    private static final int MAX_RETRIES = 3;

    private final AppDatabase database;
    private final SavingPlanDao dao;
    private final BmobSavingApiImpl api;

    public SavingSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        database = AppDatabase.getInstance(context);
        dao = database.savingPlanDao();
        api = new BmobSavingApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 获取当前用户ID，未登录则跳过同步
        String userId = api.getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.success();
        }

        try {
            Log.i(TAG, "开始存钱计划同步");

            // 1. 同步本地删除：确保先同步删除操作，避免数据冲突
            syncRecordDeletes(userId);
            syncPlanDeletes(userId);

            // 2. 同步本地写入：上传新增或修改的数据到云端
            syncPlanWrites(userId);
            syncRecordWrites(userId);

            // 3. 拉取云端合并：获取云端最新数据并更新本地数据库
            mergeCloud(userId);

            Log.i(TAG, "存钱计划同步完成");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "同步异常", e);
            // 重试逻辑：在达到最大重试次数前返回 retry
            return getRunAttemptCount() < MAX_RETRIES ? Result.retry() : Result.failure();
        }
    }

    private void syncRecordDeletes(String userId) {
        List<SavingRecord> toDelete = dao.getToDeleteRecords(userId);
        if (toDelete == null) return;
        for (SavingRecord record : toDelete) {
            if (record.getObjectId() != null) {
                if (api.deleteRecordSync(record.getObjectId())) {
                    dao.deleteRecordById(record.getId());
                }
            } else {
                dao.deleteRecordById(record.getId());
            }
        }
    }

    private void syncPlanDeletes(String userId) {
        List<SavingPlan> toDelete = dao.getToDeletePlans(userId);
        if (toDelete == null) return;
        for (SavingPlan plan : toDelete) {
            if (plan.getObjectId() != null) {
                if (api.deletePlanSync(plan.getObjectId())) {
                    dao.deletePlanById(plan.getId());
                }
            } else {
                dao.deletePlanById(plan.getId());
            }
        }
    }

    private void syncPlanWrites(String userId) {
        List<SavingPlan> pending = dao.getPendingSyncPlans(userId);
        if (pending == null) return;
        for (SavingPlan plan : pending) {
            api.uploadPlanSync(plan);
        }
    }

    private void syncRecordWrites(String userId) {
        List<SavingRecord> pending = dao.getPendingSyncRecords(userId);
        if (pending == null) return;
        for (SavingRecord record : pending) {
            SavingPlan plan = dao.getPlanByIdSync(record.getPlanId());
            if (plan != null && plan.getObjectId() != null) {
                api.uploadRecordSync(record, plan.getObjectId());
            }
        }
    }

    private void mergeCloud(String userId) throws Exception {
        List<CloudSavingPlan> cloudPlans = api.fetchPlansSync();
        if (cloudPlans != null) {
            database.runInTransaction(() -> {
                for (CloudSavingPlan cloud : cloudPlans) {
                    mergePlan(cloud, userId);
                }
            });
        }

        List<CloudSavingRecord> cloudRecords = api.fetchRecordsSync();
        if (cloudRecords != null) {
            database.runInTransaction(() -> {
                for (CloudSavingRecord cloud : cloudRecords) {
                    mergeRecord(cloud, userId);
                }
            });
        }
    }

    private void mergePlan(CloudSavingPlan cloud, String userId) {
        SavingPlan incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);
        SavingPlan local = dao.getPlanByObjectId(incoming.getObjectId());
        if (local == null) {
            dao.insertPlan(incoming);
        } else if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
            incoming.setId(local.getId());
            dao.updatePlan(incoming);
        }
    }

    private void mergeRecord(CloudSavingRecord cloud, String userId) {
        SavingRecord incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);
        
        SavingPlan parent = dao.getPlanByObjectId(cloud.getPlanObjectId());
        if (parent == null) return;
        incoming.setPlanId(parent.getId());

        SavingRecord local = dao.getRecordByObjectId(incoming.getObjectId());
        if (local == null) {
            // 尝试恢复关联账单的本地 ID
            if (incoming.getLinkedBillObjectId() != null) {
                com.example.my_project1.data.model.bill.Bill bill = database.billDao().getBillByObjectIdSync(incoming.getLinkedBillObjectId());
                if (bill != null) {
                    incoming.setLinkedBillId(bill.getId());
                }
            }
            dao.insertRecord(incoming);
        } else if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
            incoming.setId(local.getId());
            // 如果云端有关联账单且本地还没有，尝试恢复
            if (incoming.getLinkedBillObjectId() != null && local.getLinkedBillId() <= 0) {
                com.example.my_project1.data.model.bill.Bill bill = database.billDao().getBillByObjectIdSync(incoming.getLinkedBillObjectId());
                if (bill != null) {
                    incoming.setLinkedBillId(bill.getId());
                }
            } else {
                incoming.setLinkedBillId(local.getLinkedBillId());
            }
            dao.updateRecord(incoming);
        }
    }

    private boolean isCloudNewer(Date cloud, Date local) {
        return cloud != null && (local == null || cloud.after(local));
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SavingSyncWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
}
