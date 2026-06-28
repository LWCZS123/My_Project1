package com.example.my_project1.data.remote.model.cloudbill;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.utils.AppExecutors;
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
 * BmobBillApiImpl - Bmob 账单 API 实现（修复版）
 * -------------------------------------------------------
 * 🔧 修复内容：
 * 1. deleteBillSync() 不再依赖 CloudBill.deleteSync()
 * 2. 使用 CountDownLatch 包装异步删除为同步方法
 * 3. 添加超时控制和错误处理
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

    // 无参构造器（用于单元测试或静态调用）
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
    // 🟢 账单上传（创建/更新）
    // ----------------------------------------------------------------------

    /**
     * 上传账单（异步）
     * 如果 objectId 存在则更新，否则创建
     */
    public void uploadBill(Bill local, SaveListener<String> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(900, "用户未登录"));
            return;
        }

        CloudBill cloud = CloudBill.fromLocal(local);
        cloud.setUser(BmobPointerUtil.user(userId));

        // 关联账户
        if (local.getAccountId() != null) {
            cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
        }

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 上传账单成功: " + local.getAmount() + " -> " + objectId);
                    local.setObjectId(objectId);
                    local.setSyncState(SyncState.SYNCED);
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "❌ 上传账单失败: " + e.getMessage());
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

        // 关联账户
        if (local.getAccountId() != null) {
            cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
        }

        cloud.update(local.getObjectId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新账单成功: " + local.getAmount());
                    local.setSyncState(SyncState.SYNCED);
                    listener.done(null);
                } else {
                    Log.e(TAG, "❌ 更新账单失败: " + e.getMessage());
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
                    Log.d(TAG, "✅ 删除账单成功: " + objectId);
                    listener.done(null);
                } else {
                    Log.e(TAG, "❌ 删除账单失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    /**
     * 🔧 修复：同步删除账单（阻塞）
     * 使用 CountDownLatch 包装异步删除为同步方法
     *
     * @param objectId 云端账单ID
     * @return 是否删除成功
     */
    public boolean deleteBillSync(String objectId) {
        if (objectId == null || objectId.isEmpty()) {
            Log.e(TAG, "❌ deleteBillSync - objectId为空");
            return false;
        }

        Log.d(TAG, "🔄 同步删除账单: objectId=" + objectId);

        try {
            // 使用 CountDownLatch 等待异步操作完成
            final BmobException[] exceptionHolder = new BmobException[1];
            final CountDownLatch latch = new CountDownLatch(1);

            CloudBill cloud = new CloudBill();
            cloud.setObjectId(objectId);

            // 调用异步删除
            cloud.delete(new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    exceptionHolder[0] = e;
                    latch.countDown();
                }
            });

            // 等待删除完成
            boolean completed = latch.await(DELETE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                Log.e(TAG, "❌ 同步删除账单超时: objectId=" + objectId);
                return false;
            }

            // 检查是否有异常
            if (exceptionHolder[0] != null) {
                BmobException e = exceptionHolder[0];

                // 🔑 关键：如果云端对象不存在（错误码101），也视为删除成功
                if (e.getErrorCode() == 101) {
                    Log.d(TAG, "✅ 云端对象已不存在，视为删除成功: objectId=" + objectId);
                    return true;
                }

                Log.e(TAG, "❌ 同步删除账单失败: objectId=" + objectId
                        + ", error=" + e.getMessage()
                        + ", code=" + e.getErrorCode(), e);
                return false;
            }

            Log.d(TAG, "✅ 同步删除账单成功: " + objectId);
            return true;

        } catch (InterruptedException e) {
            Log.e(TAG, "❌ 同步删除账单被中断: objectId=" + objectId, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步删除账单异常: objectId=" + objectId, e);
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // 🟡 账单查询
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
        query.include("book,account"); // 展开关联对象
        query.order("-billTime"); // 按账单时间倒序
        query.setLimit(1000); // 设置查询上限
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
    // 🧩 同步接口（同步方法，仅在后台任务使用）
    // ----------------------------------------------------------------------

    /**
     * 同步上传单个账单（阻塞）
     * 如果 objectId 存在则更新，否则创建
     */
    public boolean uploadBillSync(Bill local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                Log.e(TAG, "❌ uploadBillSync - 用户未登录");
                return false;
            }

            CloudBill cloud = CloudBill.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));
            Log.d(TAG, "🚀 uploadBillSync() 调用"
                    + " amount=" + local.getAmount()
                    + " state=" + local.getSyncState()
                    + " objectId=" + local.getObjectId()
            );

            // 关联账户
            if (local.getAccountId() != null) {
                cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
            }

            String cloudId;
            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                // 创建新账单
                cloudId = cloud.saveSync();
                local.setObjectId(cloudId);
                Log.d(TAG, "✅ 同步创建账单成功: " + local.getAmount() + " -> " + cloudId);
            } else {
                // 更新现有账单
                cloud.updateSync(local.getObjectId());
                cloudId = local.getObjectId();
                Log.d(TAG, "✅ 同步更新账单成功: " + local.getAmount() + " -> " + cloudId);
            }

            // 更新本地状态和时间戳(加1秒余量)
            Date now = new Date();
            now.setTime(now.getTime() + 1000);  // 加1秒

            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(now);

            Log.d(TAG, "🔧 更新本地时间戳: " + now + " (已加1秒余量)");

            if (db != null) {
                AppExecutors.get().diskIO().execute(() -> {
                    db.billDao().update(local);
                    Log.d(TAG, "✅ 本地数据库已更新: ID=" + local.getId());
                });
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步上传账单失败: " + e.getMessage(), e);
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

    // ----------------------------------------------------------------------
    // 🔧 辅助工具方法
    // ----------------------------------------------------------------------

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

        // TODO: 实现批量上传逻辑
        // Bmob SDK 支持批量操作，可以使用 BmobBatch
        Log.w(TAG, "批量上传功能待实现");
    }
}