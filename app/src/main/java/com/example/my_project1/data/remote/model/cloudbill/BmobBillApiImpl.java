package com.example.my_project1.data.remote.model.cloudbill;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * BmobBillApiImpl - Bmob 账单 API 实现
 * -------------------------------------------------------
 * 提供账单云端增删改查与同步 API
 */
public class BmobBillApiImpl {

    private static final String TAG = "BmobBillApiImpl";
    private static final long DELETE_TIMEOUT_SECONDS = 30;

    private final Context context;
    private final AppDatabase db;

    public BmobBillApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    public BmobBillApiImpl() {
        this.context = null;
        this.db = null;
    }

    /** 获取当前登录用户 ID */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // ----------------------------------------------------------------------
    // 账单上传（创建/更新）
    // ----------------------------------------------------------------------

    /**
     * 上传账单（异步）
     */
    public void uploadBill(Bill local, SaveListener<String> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(900, "用户未登录"));
            return;
        }

        CloudBill cloud = CloudBill.fromLocal(local);
        cloud.setUser(BmobPointerUtil.user(userId));

        if (local.getAccountId() != null) {
            cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
        }

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    Log.d(TAG, "上传账单成功: " + local.getAmount() + " -> " + objectId);
                    local.setObjectId(objectId);
                    local.setSyncState(SyncState.SYNCED);
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "上传账单失败: " + e.getMessage());
                    listener.done(null, e);
                }
            }
        });
    }

    /**
     * 更新账单（异步）
     */
    public void updateBill(Bill local, UpdateListener listener) {
        if (local.getObjectId() == null) {
            listener.done(new BmobException(901, "objectId为空，无法更新账单"));
            return;
        }

        CloudBill cloud = CloudBill.fromLocal(local);

        if (local.getAccountId() != null) {
            cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
        }

        cloud.update(local.getObjectId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "更新账单成功: " + local.getAmount());
                    local.setSyncState(SyncState.SYNCED);
                    listener.done(null);
                } else {
                    Log.e(TAG, "更新账单失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    /**
     * 删除账单（异步）
     */
    public void deleteBill(String objectId, UpdateListener listener) {
        if (objectId == null) {
            listener.done(new BmobException(902, "objectId为空，无法删除账单"));
            return;
        }

        CloudBill cloud = new CloudBill();
        cloud.setObjectId(objectId);
        cloud.delete(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "删除账单成功: " + objectId);
                    listener.done(null);
                } else {
                    Log.e(TAG, "删除账单失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    /**
     * 同步删除账单（阻塞）
     */
    public boolean deleteBillSync(String objectId) {
        if (objectId == null || objectId.isEmpty()) {
            Log.e(TAG, "deleteBillSync - objectId为空");
            return false;
        }

        Log.d(TAG, "同步删除账单: objectId=" + objectId);

        try {
            final BmobException[] exceptionHolder = new BmobException[1];
            final CountDownLatch latch = new CountDownLatch(1);

            CloudBill cloud = new CloudBill();
            cloud.setObjectId(objectId);

            cloud.delete(new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    exceptionHolder[0] = e;
                    latch.countDown();
                }
            });

            boolean completed = latch.await(DELETE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                Log.e(TAG, "同步删除账单超时: objectId=" + objectId);
                return false;
            }

            if (exceptionHolder[0] != null) {
                BmobException e = exceptionHolder[0];

                if (e.getErrorCode() == 101) {
                    Log.d(TAG, "云端对象已不存在，视为删除成功: objectId=" + objectId);
                    return true;
                }

                Log.e(TAG, "同步删除账单失败: objectId=" + objectId
                        + ", error=" + e.getMessage()
                        + ", code=" + e.getErrorCode(), e);
                return false;
            }

            Log.d(TAG, "同步删除账单成功: " + objectId);
            return true;

        } catch (InterruptedException e) {
            Log.e(TAG, "同步删除账单被中断: objectId=" + objectId, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "同步删除账单异常: objectId=" + objectId, e);
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // 账单查询
    // ----------------------------------------------------------------------

    /**
     * 拉取当前用户的所有账单（异步）
     */
    public void fetchBills(FindListener<CloudBill> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(903, "用户未登录"));
            return;
        }

        BmobQuery<CloudBill> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.include("book,account");
        query.order("-billTime");
        query.setLimit(1000);
        query.findObjects(listener);
    }

    /**
     * 按账本ID拉取账单（异步）
     */
    public void fetchBillsByBook(String bookId, FindListener<CloudBill> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(904, "用户未登录"));
            return;
        }

        BmobQuery<CloudBill> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("-billTime");
        query.setLimit(1000);
        query.findObjects(listener);
    }

    /**
     * 按账户ID拉取账单（异步）
     */
    public void fetchBillsByAccount(String accountId, FindListener<CloudBill> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(905, "用户未登录"));
            return;
        }

        BmobQuery<CloudBill> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.addWhereEqualTo("account", BmobPointerUtil.account(accountId));
        query.order("-billTime");
        query.setLimit(1000);
        query.findObjects(listener);
    }

    /**
     * 按分类ID拉取账单（异步）
     */
    public void fetchBillsByCategory(String categoryId, FindListener<CloudBill> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(906, "用户未登录"));
            return;
        }

        BmobQuery<CloudBill> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.addWhereEqualTo("categoryId", categoryId);
        query.order("-billTime");
        query.setLimit(1000);
        query.findObjects(listener);
    }

    // ----------------------------------------------------------------------
    // 同步接口（同步方法，仅在后台任务使用）
    // ----------------------------------------------------------------------

    /**
     * 同步上传单个账单（阻塞）
     */
    public boolean uploadBillSync(Bill local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                Log.e(TAG, "uploadBillSync - 用户未登录");
                return false;
            }

            CloudBill cloud = CloudBill.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));
            Log.d(TAG, "uploadBillSync() 调用 amount=" + local.getAmount()
                    + " state=" + local.getSyncState()
                    + " objectId=" + local.getObjectId());

            if (local.getAccountId() != null) {
                cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
            }

            String cloudId;
            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                cloudId = cloud.saveSync();
                local.setObjectId(cloudId);
                Log.d(TAG, "同步创建账单成功: " + local.getAmount() + " -> " + cloudId);
            } else {
                cloud.updateSync(local.getObjectId());
                cloudId = local.getObjectId();
                Log.d(TAG, "同步更新账单成功: " + local.getAmount() + " -> " + cloudId);
            }

            Date now = new Date();
            now.setTime(now.getTime() + 1000);

            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(now);

            Log.d(TAG, "更新本地时间戳: " + now);

            if (db != null) {
                try {
                    db.billDao().update(local);

                    com.example.my_project1.data.model.wish.WishRecord wishRecord = db.wishDao().getRecordByBillId(local.getId());
                    if (wishRecord != null) {
                        wishRecord.setLinkedBillObjectId(local.getObjectId());
                        db.wishDao().updateRecord(wishRecord);
                    }

                    com.example.my_project1.data.model.saving.SavingRecord savingRecord = db.savingPlanDao().getRecordByBillId(local.getId());
                    if (savingRecord != null) {
                        savingRecord.setLinkedBillObjectId(local.getObjectId());
                        db.savingPlanDao().updateRecord(savingRecord);
                    }

                    Log.d(TAG, "本地数据库已同步更新: ID=" + local.getId());
                } catch (Exception e) {
                    Log.e(TAG, "本地数据库更新异常: " + e.getMessage(), e);
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "同步上传账单失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 同步获取当前用户的所有账单（阻塞）
     */
    public List<CloudBill> getAllBillsSync(String userId) throws Exception {
        if (userId == null) return null;

        BmobQuery<CloudBill> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.include("book,account");
        query.order("-billTime");
        query.setLimit(1000);

        return query.findObjectsSync(CloudBill.class);
    }

    /**
     * 同步获取某账本的所有账单（阻塞）
     */
    public List<CloudBill> getBillsByBookSync(String userId, String bookId) throws Exception {
        if (userId == null || bookId == null) return null;

        BmobQuery<CloudBill> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("-billTime");
        query.setLimit(1000);

        return query.findObjectsSync(CloudBill.class);
    }

    /**
     * 批量上传账单（异步）
     */
    public void uploadBills(List<Bill> bills, SaveListener<List<String>> listener) {
        if (bills == null || bills.isEmpty()) {
            listener.done(null, new BmobException(907, "账单列表为空"));
            return;
        }

        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(908, "用户未登录"));
            return;
        }

        Log.w(TAG, "批量上传功能待实现");
    }
}
