package com.example.my_project1.data.repository.wish;

import android.content.Context;

import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.remote.model.cloudwish.BmobWishApiImpl;
import com.example.my_project1.data.remote.model.cloudwish.CloudWish;
import com.example.my_project1.data.remote.model.cloudwish.CloudWishRecord;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.WishSyncWorker;

import java.util.Date;
import java.util.List;

/**
 * 愿望模块的数据唯一入口。
 *
 * 遵循 UDF 原则：Repository 仅作为“写”和“同步”的入口。
 * 查询操作由 ViewModel 直接通过 DAO 观察 Room 实现。
 */
public class WishRepository {

    private final Context context;
    private final AppDatabase database;
    private final WishDao wishDao;
    private final AppExecutors executors;

    public WishRepository(Context context) {
        this.context = context.getApplicationContext();
        database = AppDatabase.getInstance(this.context);
        wishDao = database.wishDao();
        executors = AppExecutors.get();
    }

    /**
     * 暴露 DAO 供 ViewModel 直接进行观察
     */
    public WishDao getWishDao() {
        return wishDao;
    }

    public void insertWish(Wish wish, ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            Date now = new Date();
            wish.setId(0);
            wish.setCurrentAmount(Math.max(0d, wish.getCurrentAmount()));
            wish.setSyncState(SyncState.TO_CREATE);
            wish.setCreatedAt(now);
            wish.setUpdatedAt(now);
            long id = wishDao.insertWish(wish);
            wish.setId(id);
            enqueueSync();
            return ApiResponse.success(id, "愿望已创建");
        });
    }

    public void updateWish(Wish wish, ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            Wish stored = wishDao.getWishByIdSync(wish.getId());
            if (stored == null || stored.getSyncState() == SyncState.TO_DELETE) {
                return ApiResponse.error("愿望不存在或已删除");
            }
            wish.setObjectId(stored.getObjectId());
            wish.setUserId(stored.getUserId());
            wish.setCurrentAmount(stored.getCurrentAmount());
            wish.setCreatedAt(stored.getCreatedAt());
            wish.setUpdatedAt(new Date());
            if (wish.getStatus() != Wish.STATUS_ABANDONED) {
                wish.setStatus(wish.getCurrentAmount() >= wish.getTargetAmount()
                        ? Wish.STATUS_COMPLETED : Wish.STATUS_ACTIVE);
            }
            wish.setSyncState(nextWriteState(stored.getSyncState()));
            wishDao.updateWish(wish);
            enqueueSync();
            return ApiResponse.success(wish.getId(), "愿望已更新");
        });
    }

    public void deleteWish(long wishId, ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            Wish wish = wishDao.getWishByIdSync(wishId);
            if (wish == null) return ApiResponse.error("愿望不存在");
            Date now = new Date();
            // 愿望与其记录必须一起进入待删除状态，避免只删父对象后留下云端孤儿记录。
            database.runInTransaction(() -> {
                wishDao.markRecordsDeleted(wishId, now);
                wish.setSyncState(SyncState.TO_DELETE);
                wish.setUpdatedAt(now);
                wishDao.updateWish(wish);
            });
            enqueueSync();
            return ApiResponse.success(wishId, "愿望已删除");
        });
    }

    public void insertRecord(WishRecord record, ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            Wish wish = wishDao.getWishByIdSync(record.getWishId());
            if (wish == null || wish.getSyncState() == SyncState.TO_DELETE) {
                return ApiResponse.error("愿望不存在或已删除");
            }
            Date now = new Date();
            record.setId(0);
            record.setWishObjectId(wish.getObjectId());
            record.setUserId(wish.getUserId());
            record.setSyncState(SyncState.TO_CREATE);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            final long[] id = new long[1];
            // 写记录和重算累计金额属于同一个业务动作，必须在同一事务内提交。
            database.runInTransaction(() -> {
                id[0] = wishDao.insertRecord(record);
                record.setId(id[0]);
                refreshProgress(wish, now);
            });
            enqueueSync();
            return ApiResponse.success(id[0], "记录已添加");
        });
    }

    public void updateRecord(WishRecord record, ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            WishRecord stored = wishDao.getRecordByIdSync(record.getId());
            if (stored == null || stored.getSyncState() == SyncState.TO_DELETE) {
                return ApiResponse.error("记录不存在或已删除");
            }
            Wish wish = wishDao.getWishByIdSync(stored.getWishId());
            if (wish == null) return ApiResponse.error("关联愿望不存在");
            record.setWishId(stored.getWishId());
            record.setWishObjectId(stored.getWishObjectId());
            record.setUserId(stored.getUserId());
            record.setObjectId(stored.getObjectId());
            record.setLinkedBillId(stored.getLinkedBillId());
            record.setCreatedAt(stored.getCreatedAt());
            record.setUpdatedAt(new Date());
            record.setSyncState(nextWriteState(stored.getSyncState()));
            database.runInTransaction(() -> {
                wishDao.updateRecord(record);
                refreshProgress(wish, record.getUpdatedAt());
            });
            enqueueSync();
            return ApiResponse.success(record.getId(), "记录已更新");
        });
    }

    public void deleteRecord(long recordId, ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            WishRecord record = wishDao.getRecordByIdSync(recordId);
            if (record == null) return ApiResponse.error("记录不存在");
            Wish wish = wishDao.getWishByIdSync(record.getWishId());
            Date now = new Date();
            database.runInTransaction(() -> {
                record.setSyncState(SyncState.TO_DELETE);
                record.setUpdatedAt(now);
                wishDao.updateRecord(record);
                if (wish != null) refreshProgress(wish, now);
            });
            enqueueSync();
            return ApiResponse.success(recordId, "记录已删除");
        });
    }

    public void syncNow() {
        enqueueSync();
    }

    /**
     * 从云端拉取并同步愿望及记录数据
     * 逻辑参考账单模块，先执行一次全量拉取并合并，然后触发后台同步任务处理本地待上传变更
     */
    public void syncFromCloud(String userId, ApiResponse.Callback<Boolean> callback) {
        if (userId == null || userId.isEmpty()) {
            if (callback != null) callback.onComplete(ApiResponse.error("用户未登录"));
            return;
        }

        executors.networkIO().execute(() -> {
            try {
                // 1. 获取 Bmob API 实例
                BmobWishApiImpl bmobApi = new BmobWishApiImpl(context);
                
                // 2. 拉取云端愿望
                List<CloudWish> cloudWishes = bmobApi.fetchWishesSync();
                // 3. 拉取云端记录
                List<CloudWishRecord> cloudRecords = bmobApi.fetchRecordsSync();

                // 4. 在磁盘 IO 线程执行本地数据库合并
                executors.diskIO().execute(() -> {
                    try {
                        database.runInTransaction(() -> {
                            // 处理愿望合并
                            if (cloudWishes != null) {
                                for (CloudWish cloud : cloudWishes) {
                                    mergeWishInternal(cloud, userId);
                                }
                            }
                            // 处理记录合并
                            if (cloudRecords != null) {
                                for (CloudWishRecord cloud : cloudRecords) {
                                    mergeRecordInternal(cloud, userId);
                                }
                            }
                        });

                        // 5. 合并完成后触发一次增量同步任务，处理本地尚未上传的修改
                        enqueueSync();

                        // 6. 回调成功
                        if (callback != null) {
                            executors.mainThread().execute(() -> 
                                callback.onComplete(ApiResponse.success(true, "云端同步完成")));
                        }
                    } catch (Exception e) {
                        handleError(callback, e);
                    }
                });
            } catch (Exception e) {
                handleError(callback, e);
            }
        });
    }

    /**
     * 合并单个云端愿望到本地（内部辅助方法）
     */
    private void mergeWishInternal(CloudWish cloud, String userId) {
        Wish incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        Wish local = wishDao.getWishByObjectId(incoming.getObjectId());
        if (local == null) {
            wishDao.insertWish(incoming);
        } else {
            // 时间对比逻辑由 Repository 层保证幂等和冲突控制
            if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming, local)) {
                incoming.setId(local.getId());
                wishDao.updateWish(incoming);
            }
        }
    }

    /**
     * 合并单个云端记录到本地（内部辅助方法）
     */
    private void mergeRecordInternal(CloudWishRecord cloud, String userId) {
        WishRecord incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        Wish parent = wishDao.getWishByObjectId(incoming.getWishObjectId());
        if (parent == null) return;
        
        incoming.setWishId(parent.getId());
        WishRecord local = wishDao.getRecordByObjectId(incoming.getObjectId());
        
        if (local == null) {
            wishDao.insertRecord(incoming);
        } else {
            if (local.getSyncState() == SyncState.SYNCED && 
                    isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
                incoming.setId(local.getId());
                incoming.setLinkedBillId(local.getLinkedBillId());
                wishDao.updateRecord(incoming);
            }
        }
    }

    private boolean isCloudNewer(Wish incoming, Wish local) {
        return isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt());
    }

    private boolean isCloudNewer(Date cloudDate, Date localDate) {
        if (cloudDate == null) return false;
        return localDate == null || cloudDate.after(localDate);
    }

    private void handleError(ApiResponse.Callback<Boolean> callback, Exception e) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e)));
        }
    }

    private void refreshProgress(Wish wish, Date now) {
        // 累计金额始终从记录表重新求和，编辑和删除记录时不会产生增量计算误差。
        double total = Math.max(0d, wishDao.getSavedAmount(wish.getId()));
        int status = wish.getStatus();
        if (status != Wish.STATUS_ABANDONED) {
            status = total >= wish.getTargetAmount() ? Wish.STATUS_COMPLETED : Wish.STATUS_ACTIVE;
        }
        wishDao.updateWishProgress(
                wish.getId(), total, status, nextWriteState(wish.getSyncState()), now);
    }

    private SyncState nextWriteState(SyncState current) {
        // 尚未创建到云端的数据继续保持 TO_CREATE，不能提前变成 UPDATE。
        return current == SyncState.TO_CREATE ? SyncState.TO_CREATE : SyncState.TO_UPDATE;
    }

    private void enqueueSync() {
        WishSyncWorker.enqueue(context);
    }

    private <T> void execute(ApiResponse.Callback<T> callback, Task<T> task) {
        executors.diskIO().execute(() -> {
            ApiResponse<T> response;
            try {
                response = task.run();
            } catch (Exception exception) {
                response = ApiResponse.error(exception);
            }
            if (callback != null) {
                ApiResponse<T> result = response;
                executors.mainThread().execute(() -> callback.onComplete(result));
            }
        });
    }

    private interface Task<T> {
        ApiResponse<T> run() throws Exception;
    }
}
