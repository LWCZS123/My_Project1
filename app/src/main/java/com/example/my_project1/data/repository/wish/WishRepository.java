package com.example.my_project1.data.repository.wish;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.remote.model.cloudwish.CloudWishRecord;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.work.BillSyncWorker;
import com.example.my_project1.work.WishSyncWorker;

import java.util.Date;
import java.util.List;

/**
 * WishRepository（优化版）
 */
public class WishRepository {

    private static final String TAG = "WishRepository";

    private final WishDao wishDao;
    private final BillDao billDao;
    private final AppExecutors executors;
    private final Context context;

    public WishRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.context   = context.getApplicationContext();
        this.wishDao   = db.wishDao();
        this.billDao   = db.billDao();
        this.executors = AppExecutors.get();
    }

    // ======================== Wish 增删改 ========================

    public void insertWish(Wish wish, ApiResponse.Callback<Long> callback) {
        executors.diskIO().execute(() -> {
            try {
                Date now = new Date();
                wish.setCreatedAt(now);
                wish.setUpdatedAt(now);
                wish.setSyncState(SyncState.TO_CREATE);

                long id = wishDao.insertWish(wish);

                if (id > 0) {
                    Log.d(TAG, "新增愿望成功: ID=" + id + " name=" + wish.getWishName());
                    WishSyncWorker.enqueue(context);
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(id, "添加成功")));
                } else {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("添加失败")));
                }
            } catch (Exception e) {
                Log.e(TAG, "新增愿望异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    public void updateWish(Wish wish, ApiResponse.Callback<String> callback) {
        executors.diskIO().execute(() -> {
            try {
                wish.setUpdatedAt(new Date());
                wish.setSyncState(SyncState.TO_UPDATE);
                int rows = wishDao.updateWish(wish);

                if (rows > 0) {
                    Log.d(TAG, "更新愿望成功: ID=" + wish.getId());
                    WishSyncWorker.enqueue(context);
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(null, "更新成功")));
                } else {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("更新失败")));
                }
            } catch (Exception e) {
                Log.e(TAG, "更新愿望异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    public void deleteWish(Wish wish, ApiResponse.Callback<String> callback) {
        executors.diskIO().execute(() -> {
            try {
                Date now = new Date();

                wish.setSyncState(SyncState.TO_DELETE);
                wish.setUpdatedAt(now);
                wishDao.updateWish(wish);

                List<WishRecord> records = wishDao.getRecordsByWishIdSync(wish.getId());
                for (WishRecord record : records) {
                    record.setSyncState(SyncState.TO_DELETE);
                    wishDao.updateRecord(record);
                }

                List<Bill> linkedBills = billDao.getBillsBySourceWishId(wish.getId());
                for (Bill bill : linkedBills) {
                    bill.setSyncState(SyncState.TO_DELETE);
                    bill.setUpdatedAt(now);
                    billDao.update(bill);
                }

                Log.d(TAG, "软删除愿望成功: ID=" + wish.getId());

                WishSyncWorker.enqueue(context);
                BillSyncWorker.enqueue(context);

                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(null, "删除成功")));

            } catch (Exception e) {
                Log.e(TAG, "删除愿望异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    // ======================== WishRecord 存钱记录 ========================

    public void addSavingRecord(long wishId, double amount, String note,
                                Date recordDate, long billId,
                                ApiResponse.Callback<String> callback) {
        executors.diskIO().execute(() -> {
            try {
                Wish wish = wishDao.getWishByIdSync(wishId);
                if (wish == null) {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("愿望不存在")));
                    return;
                }

                Date now = new Date();
                Date date = (recordDate != null) ? recordDate : now;

                WishRecord record = new WishRecord(
                        wishId,
                        wish.getObjectId(),
                        wish.getUserId(),
                        amount,
                        note,
                        date
                );
                record.setCreatedAt(now);
                record.setSyncState(SyncState.TO_CREATE);
                record.setLinkedBillId(billId);
                long recordId = wishDao.insertRecord(record);

                if (recordId <= 0) {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("记录保存失败")));
                    return;
                }

                double newAmount = wish.getCurrentAmount() + amount;
                wish.setCurrentAmount(newAmount);
                wish.setUpdatedAt(now);
                wish.setSyncState(SyncState.TO_UPDATE);

                if (newAmount >= wish.getTargetAmount() && wish.getStatus() == 0) {
                    wish.setStatus(1);
                    Log.d(TAG, "愿望已完成: " + wish.getWishName());
                }
                wishDao.updateWish(wish);

                Log.d(TAG, "存钱记录添加成功: wishId=" + wishId);

                WishSyncWorker.enqueue(context);

                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(null, "存入成功")));

            } catch (Exception e) {
                Log.e(TAG, "添加存钱记录异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    public LiveData<Wish> getWishById(long id) {
        return wishDao.getById(id);
    }

    public void deleteSavingRecord(WishRecord record, ApiResponse.Callback<String> callback) {
        executors.diskIO().execute(() -> {
            try {
                Date now = new Date();

                Wish wish = wishDao.getWishByIdSync(record.getWishId());
                if (wish != null) {
                    double newAmount = Math.max(0, wish.getCurrentAmount() - record.getAmount());
                    wish.setCurrentAmount(newAmount);
                    wish.setUpdatedAt(now);
                    wish.setSyncState(SyncState.TO_UPDATE);
                    if (wish.getStatus() == 1 && newAmount < wish.getTargetAmount()) {
                        wish.setStatus(0);
                    }
                    wishDao.updateWish(wish);
                }

                if (record.getLinkedBillId() > 0) {
                    Bill linkedBill = billDao.getBillByIdSync(record.getLinkedBillId());
                    if (linkedBill != null) {
                        linkedBill.setSyncState(SyncState.TO_DELETE);
                        linkedBill.setUpdatedAt(now);
                        billDao.update(linkedBill);
                        BillSyncWorker.enqueue(context);
                    }
                }

                record.setSyncState(SyncState.TO_DELETE);
                wishDao.updateRecord(record);

                Log.d(TAG, "删除存钱记录成功: recordId=" + record.getId());

                WishSyncWorker.enqueue(context);

                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(null, "删除成功")));

            } catch (Exception e) {
                Log.e(TAG, "删除存钱记录异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    // ======================== 云端同步：全量拉取 ========================

    public void syncWishesFromCloud(String userId, ApiResponse.Callback<Void> callback) {
        cn.bmob.v3.BmobQuery<com.example.my_project1.data.remote.model.cloudwish.CloudWish> query =
                new cn.bmob.v3.BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));

        query.findObjects(new cn.bmob.v3.listener.FindListener<
                com.example.my_project1.data.remote.model.cloudwish.CloudWish>() {
            @Override
            public void done(
                    List<com.example.my_project1.data.remote.model.cloudwish.CloudWish> list,
                    cn.bmob.v3.exception.BmobException e) {

                if (e != null) {
                    Log.e(TAG, "从云端获取愿望失败: " + e.getMessage());
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("同步失败: " + e.getMessage())));
                    return;
                }

                executors.diskIO().execute(() -> {
                    try {
                        if (list != null && !list.isEmpty()) {
                            for (com.example.my_project1.data.remote.model.cloudwish.CloudWish cloud : list) {
                                Wish wish = cloud.toLocalEntity();
                                wish.setSyncState(SyncState.SYNCED);

                                Wish local = wishDao.getWishByObjectIdSync(cloud.getObjectId());

                                if (local == null) {
                                    wishDao.insertWish(wish);
                                } else {
                                    if (local.getSyncState() == SyncState.SYNCED) {
                                        wish.setId(local.getId());
                                        wishDao.updateWish(wish);
                                    }
                                }
                            }
                        }
                        executors.mainThread().execute(() ->
                                callback.onComplete(ApiResponse.success(null, "同步完成")));
                    } catch (Exception ex) {
                        Log.e(TAG, "写入本地数据库异常", ex);
                        executors.mainThread().execute(() ->
                                callback.onComplete(ApiResponse.error(ex)));
                    }
                });
            }
        });
    }

    public void syncRecordsFromCloud(String userId, ApiResponse.Callback<Void> callback) {

        cn.bmob.v3.BmobQuery<CloudWishRecord> query = new cn.bmob.v3.BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.include("wish");

        query.findObjects(new cn.bmob.v3.listener.FindListener<CloudWishRecord>() {

            @Override
            public void done(List<CloudWishRecord> list, cn.bmob.v3.exception.BmobException e) {

                if (e != null) {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error(e)));
                    return;
                }

                executors.diskIO().execute(() -> {
                    try {
                        if (list != null) {
                            for (CloudWishRecord cloud : list) {
                                WishRecord record = cloud.toLocalEntity();
                                record.setSyncState(SyncState.SYNCED);

                                // 🔥 关键修复：从解析好的 record 中提取关联的愿望ID，或者作为保底直接从云端 Pointer 中取
                                Wish parentWish = null;
                                String wishObjectId = record.getWishObjectId();
                                if (wishObjectId == null && cloud.getWish() != null) {
                                    wishObjectId = cloud.getWish().getObjectId();
                                }

                                if (wishObjectId != null) {
                                    parentWish = wishDao.getWishByObjectIdSync(wishObjectId);
                                }

                                // 如果本地还没有同步下这个父级愿望，先跳过
                                if (parentWish == null) {
                                    Log.w(TAG, "Wish未准备好，暂存record: " + cloud.getObjectId());
                                    continue;
                                }

                                record.setWishId(parentWish.getId());

                                // 查本地 record，判断是新增还是更新
                                WishRecord local = wishDao.getRecordByObjectIdSync(cloud.getObjectId());

                                if (local == null) {
                                    wishDao.insertRecord(record);
                                    Log.d(TAG, "新增日期: " + record.getRecordDate());
//                                    Log.d(TAG, "新增日期: " + record.getRecordDate());
                                } else {
                                    if (local.getSyncState() == SyncState.SYNCED) {
                                        record.setId(local.getId());
                                        wishDao.updateRecord(record);
                                    }
                                }
                            }
                        }

                        executors.mainThread().execute(() ->
                                callback.onComplete(ApiResponse.success(null, "记录同步完成")));

                    } catch (Exception ex) {
                        Log.e(TAG, "记录同步异常", ex);
                        executors.mainThread().execute(() ->
                                callback.onComplete(ApiResponse.error(ex)));
                    }
                });
            }
        });
    }

    // ======================== 查询 ========================

    public LiveData<List<Wish>> getAllWishesByUser(String userId) {
        return wishDao.getAllWishesByUser(userId);
    }

    public LiveData<List<Wish>> getWishesByStatus(String userId, int status) {
        return wishDao.getWishesByStatus(userId, status);
    }

    public LiveData<List<WishRecord>> getRecordsByWishId(long wishId) {
        return wishDao.getRecordsByWishId(wishId);
    }

    public List<Wish> getAllWishesByUserSync(String userId) {
        return wishDao.getAllWishesByUserSync(userId);
    }

    public List<Wish> getPendingSyncWishes() {
        return wishDao.getPendingSyncWishes();
    }

    public List<Wish> getToDeleteWishes() {
        return wishDao.getToDeleteWishes();
    }

    public List<WishRecord> getPendingSyncRecords() {
        return wishDao.getPendingSyncRecords();
    }

    public List<WishRecord> getToDeleteRecords() {
        return wishDao.getToDeleteRecords();
    }

    public Wish getWishByIdSync(long id) {
        return wishDao.getWishByIdSync(id);
    }
}