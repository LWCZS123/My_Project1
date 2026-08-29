package com.example.my_project1.data.repository.wish;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 愿望模块的数据唯一入口。
 *
 * 遵循 UDF 原则：Repository 仅作为“写”和“同步”的入口。
 * 查询操作由 ViewModel 直接通过 DAO 观察 Room 实现。
 */
public class WishRepository {

    private static final String TAG = "WishRepository";
    private static volatile WishRepository instance;

    private final Context context;
    private final AppDatabase database;
    private final WishDao wishDao;
    private final AppExecutors executors;

    private WishRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        this.wishDao = database.wishDao();
        this.executors = AppExecutors.get();
    }

    public static WishRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (WishRepository.class) {
                if (instance == null) {
                    instance = new WishRepository(context);
                }
            }
        }
        return instance;
    }

    /**
     * 暴露 DAO 供 ViewModel 直接进行观察
     */
    public WishDao getWishDao() {
        return wishDao;
    }

    // ==================== 愿望 CRUD API ====================

    public void insertWish(@NonNull Wish wish, @Nullable ApiResponse.Callback<Long> callback) {
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

    public void updateWish(@NonNull Wish wish, @Nullable ApiResponse.Callback<Long> callback) {
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

    public void deleteWish(long wishId, @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            Wish wish = wishDao.getWishByIdSync(wishId);
            if (wish == null) return ApiResponse.error("愿望不存在");
            Date now = new Date();
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

    // ==================== 记录 CRUD API ====================

    public void insertRecord(@NonNull WishRecord record, @Nullable ApiResponse.Callback<Long> callback) {
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
            database.runInTransaction(() -> {
                id[0] = wishDao.insertRecord(record);
                record.setId(id[0]);
                refreshProgressInternal(wish, now);
            });
            enqueueSync();
            return ApiResponse.success(id[0], "记录已添加");
        });
    }

    public void updateRecord(@NonNull WishRecord record, @Nullable ApiResponse.Callback<Long> callback) {
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
                refreshProgressInternal(wish, record.getUpdatedAt());
            });
            enqueueSync();
            return ApiResponse.success(record.getId(), "记录已更新");
        });
    }

    public void deleteRecord(long recordId, @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            WishRecord record = wishDao.getRecordByIdSync(recordId);
            if (record == null) return ApiResponse.error("记录不存在");
            Wish wish = wishDao.getWishByIdSync(record.getWishId());
            Date now = new Date();
            database.runInTransaction(() -> {
                record.setSyncState(SyncState.TO_DELETE);
                record.setUpdatedAt(now);
                wishDao.updateRecord(record);
                if (wish != null) refreshProgressInternal(wish, now);
            });
            enqueueSync();
            return ApiResponse.success(recordId, "记录已删除");
        });
    }

    // ==================== 云端同步 API ====================

    public void syncNow() {
        enqueueSync();
    }

    /**
     * 从云端拉取并同步愿望及记录数据
     */
    public void syncFromCloud(String userId, @Nullable ApiResponse.Callback<Boolean> callback) {
        if (userId == null || userId.isEmpty()) {
            if (callback != null) callback.onComplete(ApiResponse.error("用户未登录"));
            return;
        }

        executors.networkIO().execute(() -> {
            try {
                BmobWishApiImpl bmobApi = new BmobWishApiImpl(context);
                List<CloudWish> cloudWishes = bmobApi.fetchWishesSync();
                List<CloudWishRecord> cloudRecords = bmobApi.fetchRecordsSync();

                executors.diskIO().execute(() -> {
                    try {
                        database.runInTransaction(() -> {
                            // 使用 Map 缓存本地愿望 objectId -> localId 映射，优化后续记录合并
                            Map<String, Long> wishIdMap = new HashMap<>();

                            if (cloudWishes != null) {
                                for (CloudWish cloud : cloudWishes) {
                                    long localId = mergeWishInternal(cloud, userId);
                                    if (cloud.getObjectId() != null) {
                                        wishIdMap.put(cloud.getObjectId(), localId);
                                    }
                                }
                            }

                            if (cloudRecords != null) {
                                for (CloudWishRecord cloud : cloudRecords) {
                                    mergeRecordInternal(cloud, userId, wishIdMap);
                                }
                            }
                        });

                        enqueueSync();
                        if (callback != null) {
                            executors.mainThread().execute(() -> 
                                callback.onComplete(ApiResponse.success(true, "云端同步完成")));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Merge failed", e);
                        postError(callback, e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Fetch failed", e);
                postError(callback, e);
            }
        });
    }

    // ==================== 内部辅助方法 ====================

    private long mergeWishInternal(CloudWish cloud, String userId) {
        Wish incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        Wish local = wishDao.getWishByObjectId(incoming.getObjectId());
        if (local == null) {
            return wishDao.insertWish(incoming);
        } else {
            if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
                incoming.setId(local.getId());
                wishDao.updateWish(incoming);
            }
            return local.getId();
        }
    }

    private void mergeRecordInternal(CloudWishRecord cloud, String userId, Map<String, Long> wishIdMap) {
        WishRecord incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        String wishObjectId = incoming.getWishObjectId();
        Long wishLocalId = wishIdMap.get(wishObjectId);
        if (wishLocalId == null) {
            Wish parent = wishDao.getWishByObjectId(wishObjectId);
            if (parent == null) return;
            wishLocalId = parent.getId();
        }

        incoming.setWishId(wishLocalId);
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

    private boolean isCloudNewer(Date cloudDate, Date localDate) {
        if (cloudDate == null) return false;
        return localDate == null || cloudDate.after(localDate);
    }

    private void refreshProgressInternal(Wish wish, Date now) {
        double total = Math.max(0d, wishDao.getSavedAmount(wish.getId()));
        int status = wish.getStatus();
        if (status != Wish.STATUS_ABANDONED) {
            status = total >= wish.getTargetAmount() ? Wish.STATUS_COMPLETED : Wish.STATUS_ACTIVE;
        }
        wishDao.updateWishProgress(
                wish.getId(), total, status, nextWriteState(wish.getSyncState()), now);
    }

    private SyncState nextWriteState(SyncState current) {
        return current == SyncState.TO_CREATE ? SyncState.TO_CREATE : SyncState.TO_UPDATE;
    }

    private void enqueueSync() {
        try {
            WishSyncWorker.enqueue(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue sync", e);
        }
    }

    private <T> void execute(@Nullable ApiResponse.Callback<T> callback, Task<T> task) {
        executors.diskIO().execute(() -> {
            ApiResponse<T> response;
            try {
                response = task.run();
            } catch (Exception exception) {
                Log.e(TAG, "Task execution failed", exception);
                response = ApiResponse.error(exception);
            }
            if (callback != null) {
                ApiResponse<T> result = response;
                executors.mainThread().execute(() -> callback.onComplete(result));
            }
        });
    }

    private void postError(ApiResponse.Callback<Boolean> callback, Exception e) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e)));
        }
    }

    private interface Task<T> {
        ApiResponse<T> run() throws Exception;
    }
}
