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

import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.remote.model.cloudwish.BmobWishApiImpl;
import com.example.my_project1.data.remote.model.cloudwish.CloudWish;
import com.example.my_project1.data.remote.model.cloudwish.CloudWishRecord;

import java.util.Date;
import java.util.List;

/**
 * WishSyncWorker - 愿望与记录双向同步任务
 * -------------------------------------------------------
 * 逻辑顺序：
 * 1. 同步本地删除：先删记录，后删愿望。
 * 2. 同步本地变更：先传愿望（获取 objectId），后传记录。
 * 3. 合并云端变更：拉取云端最新数据并更新本地。
 */
public class WishSyncWorker extends Worker {

    public static final String WORK_NAME = "wish_sync_unique";
    private static final String TAG = "WishSyncWorker";
    private static final int MAX_RETRIES = 3;

    private final AppDatabase database;
    private final WishDao dao;
    private final BmobWishApiImpl api;

    public WishSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        database = AppDatabase.getInstance(context);
        dao = database.wishDao();
        api = new BmobWishApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        String userId = api.getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "用户未登录，跳过同步");
            return Result.success();
        }

        try {
            Log.i(TAG, "开始愿望模块同步任务");

            // 1. 同步删除
            syncRecordDeletes(userId);
            syncWishDeletes(userId);

            // 2. 同步写入（创建或更新）
            syncWishWrites(userId);
            syncRecordWrites(userId);

            // 3. 拉取云端并合并
            mergeCloud(userId);

            Log.i(TAG, "愿望模块同步任务完成");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "愿望同步异常: " + e.getMessage(), e);
            // 根据重试次数决定是否重试
            return getRunAttemptCount() < MAX_RETRIES ? Result.retry() : Result.failure();
        }
    }

    /**
     * 同步本地已标记删除的记录
     */
    private void syncRecordDeletes(String userId) {
        List<WishRecord> toDelete = dao.getToDeleteRecords(userId);
        if (toDelete == null || toDelete.isEmpty()) return;

        for (WishRecord record : toDelete) {
            boolean success = true;
            if (record.getObjectId() != null) {
                success = api.deleteRecordSync(record.getObjectId());
            }
            if (success) {
                dao.deleteRecordById(record.getId());
                Log.d(TAG, "物理删除本地记录成功: ID=" + record.getId());
            }
        }
    }

    /**
     * 同步本地已标记删除的愿望
     */
    private void syncWishDeletes(String userId) {
        List<Wish> toDelete = dao.getToDeleteWishes(userId);
        if (toDelete == null || toDelete.isEmpty()) return;

        for (Wish wish : toDelete) {
            boolean success = true;
            if (wish.getObjectId() != null) {
                // 先清理云端记录
                api.deleteRecordsForWishSync(wish.getObjectId());
                // 再删除愿望本身
                success = api.deleteWishSync(wish.getObjectId());
            }
            if (success) {
                dao.deleteWishById(wish.getId());
                Log.d(TAG, "物理删除本地愿望成功: ID=" + wish.getId());
            }
        }
    }

    /**
     * 同步本地待上传或修改的愿望
     */
    private void syncWishWrites(String userId) {
        List<Wish> pending = dao.getPendingSyncWishes(userId);
        if (pending == null || pending.isEmpty()) return;

        for (Wish wish : pending) {
            // api.uploadWishSync 内部会执行数据库更新
            boolean ok = api.uploadWishSync(wish);
            if (!ok) {
                Log.w(TAG, "同步愿望失败: " + wish.getWishName());
            }
        }
    }

    /**
     * 同步本地待上传或修改的记录
     */
    private void syncRecordWrites(String userId) {
        List<WishRecord> pending = dao.getPendingSyncRecords(userId);
        if (pending == null || pending.isEmpty()) return;

        for (WishRecord record : pending) {
            // 确保有关联的愿望 objectId
            if (record.getWishObjectId() == null) {
                Wish wish = dao.getWishByIdSync(record.getWishId());
                if (wish != null && wish.getObjectId() != null) {
                    record.setWishObjectId(wish.getObjectId());
                } else {
                    Log.w(TAG, "记录跳过上传，因为无法找到父愿望的 objectId: RecordID=" + record.getId());
                    continue;
                }
            }
            api.uploadRecordSync(record);
        }
    }

    /**
     * 拉取云端数据并合并到本地
     */
    private void mergeCloud(String userId) throws Exception {
        // 合并愿望
        List<CloudWish> cloudWishes = api.fetchWishesSync();
        if (cloudWishes != null) {
            database.runInTransaction(() -> {
                for (CloudWish cloud : cloudWishes) {
                    mergeWish(cloud, userId);
                }
            });
        }

        // 合并记录
        List<CloudWishRecord> cloudRecords = api.fetchRecordsSync();
        if (cloudRecords != null) {
            database.runInTransaction(() -> {
                for (CloudWishRecord cloud : cloudRecords) {
                    mergeRecord(cloud, userId);
                }
            });
        }
    }

    private void mergeWish(CloudWish cloud, String userId) {
        Wish incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        Wish local = dao.getWishByObjectId(incoming.getObjectId());
        if (local == null) {
            // 本地不存在，直接插入
            dao.insertWish(incoming);
            Log.d(TAG, "合并云端愿望 - 新增本地: " + incoming.getWishName());
        } else {
            // 冲突处理：只有本地数据是 SYNCED 状态且云端数据更晚时才覆盖
            if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
                incoming.setId(local.getId());
                dao.updateWish(incoming);
                Log.d(TAG, "合并云端愿望 - 更新本地: " + incoming.getWishName());
            }
        }
    }

    private void mergeRecord(CloudWishRecord cloud, String userId) {
        WishRecord incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        // 查找父愿望
        Wish parent = dao.getWishByObjectId(incoming.getWishObjectId());
        if (parent == null) {
            Log.w(TAG, "丢弃云端记录，因为本地找不到父愿望: RecordObjectId=" + incoming.getObjectId());
            return;
        }
        incoming.setWishId(parent.getId());

        WishRecord local = dao.getRecordByObjectId(incoming.getObjectId());
        if (local == null) {
            dao.insertRecord(incoming);
            Log.d(TAG, "合并云端记录 - 新增本地: " + incoming.getObjectId());
        } else {
            if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
                incoming.setId(local.getId());
                // 保留关联账单 ID
                incoming.setLinkedBillId(local.getLinkedBillId());
                dao.updateRecord(incoming);
                Log.d(TAG, "合并云端记录 - 更新本地: " + incoming.getObjectId());
            }
        }
    }

    private boolean isCloudNewer(Date cloudDate, Date localDate) {
        if (cloudDate == null) return false;
        return localDate == null || cloudDate.after(localDate);
    }

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WishSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
}
