package com.example.my_project1.data.repository.bill;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.remote.model.cloudbill.BmobBillApiImpl;
import com.example.my_project1.data.remote.model.cloudbill.CloudBill;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.DateConvertUtil;
import com.example.my_project1.work.AccountSyncWorker;
import com.example.my_project1.work.BillSyncWorker;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BillRepository - 账单数据仓库
 * -------------------------------------------------------
 * 核心逻辑:
 * 1. 账单 CRUD 操作与账户余额自动联动
 * 2. 优化云端同步: 优先拉取最新批次，首屏秒出，后续后台分批同步
 * 3. 统一图片 URL 转换
 */
public class BillRepository {

    private static final String TAG = "BillRepository";
    private static final String OSS_PUBLIC_BASE_URL = "https://xd-user-image.oss-cn-hangzhou.aliyuncs.com/";

    private final BillDao billDao;
    private final AccountDao accountDao;
    private final AppExecutors executors;
    private final BmobBillApiImpl bmobApi;
    private final Context context;

    // ==================== 构造函数 ====================

    public BillRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.context = context.getApplicationContext();
        this.billDao = db.billDao();
        this.accountDao = db.accountDao();
        this.executors = AppExecutors.get();
        this.bmobApi = new BmobBillApiImpl(context.getApplicationContext());
    }

    // ==================== 插入操作 ====================

    /**
     * 插入单条账单并更新账户余额
     */
    public void insertBill(Bill bill, ApiResponse.Callback<Long> callback) {
        executors.diskIO().execute(() -> {
            try {
                Date now = new Date();
                bill.setCreatedAt(now);
                bill.setUpdatedAt(now);
                bill.setSyncState(SyncState.TO_CREATE);

                processImageUrls(bill);

                long id = billDao.insert(bill);

                if (id > 0) {
                    Log.d(TAG, "插入账单成功: ID=" + id);
                    updateAccountBalanceForNewBill(bill);

                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(id, "添加成功"))
                    );
                } else {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("插入失败"))
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "插入账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 批量插入账单
     */
    public void insertBills(List<Bill> bills, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                Date now = new Date();
                for (Bill bill : bills) {
                    bill.setCreatedAt(now);
                    bill.setUpdatedAt(now);
                    bill.setSyncState(SyncState.TO_CREATE);
                    processImageUrls(bill);
                }

                List<Long> ids = billDao.insertBills(bills);
                int count = ids != null ? ids.size() : 0;

                for (Bill bill : bills) {
                    updateAccountBalanceForNewBill(bill);
                }

                Log.d(TAG, "批量插入成功: " + count + " 条");
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(count, "批量添加成功"))
                );
            } catch (Exception e) {
                Log.e(TAG, "批量插入异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    // ==================== 更新操作 ====================

    /**
     * 更新账单并调整账户余额
     */
    public void updateBill(Bill bill, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                Bill oldBill = null;
                if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
                    oldBill = billDao.getBillByObjectIdSync(bill.getObjectId());
                }
                if (oldBill == null && bill.getId() > 0) {
                    oldBill = billDao.getBillByIdSync(bill.getId());
                }
                if (oldBill == null) {
                    postUpdateResult(callback, ApiResponse.error("找不到要更新的账单"));
                    return;
                }

                bill.setUpdatedAt(new Date());
                bill.setSyncState(SyncState.TO_UPDATE);
                processImageUrls(bill);

                int rows = billDao.update(bill);

                if (rows > 0) {
                    updateAccountBalanceForBillUpdate(oldBill, bill);
                }

                Log.d(TAG, "更新账单: " + rows + " 行");
                if (rows == 0) {
                    postUpdateResult(callback, ApiResponse.error("账单未发生更新"));
                } else {
                    postUpdateResult(callback, ApiResponse.success(rows, "更新成功"));
                }
            } catch (Exception e) {
                Log.e(TAG, "更新账单异常", e);
                postUpdateResult(callback, ApiResponse.error(e));
            }
        });
    }

    private void postUpdateResult(ApiResponse.Callback<Integer> callback, ApiResponse<Integer> response) {
        executors.mainThread().execute(() -> callback.onComplete(response));
    }

    public Bill getBillByObjectIdSync(String objectId) {
        return billDao.getBillByObjectIdSync(objectId);
    }

    public Bill getBillByIdSync(long id) {
        return billDao.getBillByIdSync(id);
    }

    public LiveData<List<Bill>> getBillsByAccount(String userId, String accountId, long localAccountId) {
        return billDao.getBillsByAccount(userId, accountId, localAccountId);
    }

    // ==================== 删除操作 ====================

    /**
     * 删除账单(软删除)并恢复账户余额
     */
    public void deleteBill(Bill bill, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                restoreAccountBalanceForDeletedBill(bill);

                bill.setSyncState(SyncState.TO_DELETE);
                bill.setUpdatedAt(new Date());

                int rows = billDao.update(bill);

                Log.d(TAG, "标记删除成功: " + rows + " 行, objectId=" + bill.getObjectId());

                executors.mainThread().execute(() -> {
                    callback.onComplete(ApiResponse.success(rows, "删除成功"));

                    try {
                        BillSyncWorker.enqueue(context);
                        Log.d(TAG, "已触发删除同步任务");
                    } catch (Exception e) {
                        Log.e(TAG, "触发同步失败: " + e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "删除账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 删除账本下的所有账单
     */
    public void deleteBillsByBook(String userId, String bookId, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<Bill> billsToDelete = billDao.getBillsByBook(userId, bookId).getValue();
                if (billsToDelete != null) {
                    for (Bill bill : billsToDelete) {
                        restoreAccountBalanceForDeletedBill(bill);
                    }
                }

                int count = billDao.deleteBillsByBook(userId, bookId);
                Log.d(TAG, "删除账本账单: " + count + " 条");

                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(count, "删除成功"))
                );
            } catch (Exception e) {
                Log.e(TAG, "删除账本账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 删除账户下的所有账单
     */
    public void deleteBillsByAccount(String userId, String accountId, long localAccountId, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                int count = billDao.markBillsAsDeletedByAccountId(userId, accountId, localAccountId, now);

                Log.d(TAG, "批量标记删除账户账单: " + count + " 条");

                final int finalCount = count;
                executors.mainThread().execute(() -> {
                    callback.onComplete(ApiResponse.success(finalCount, "删除成功"));

                    try {
                        BillSyncWorker.enqueue(context);
                    } catch (Exception e) {
                        Log.e(TAG, "触发同步失败: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "批量标记删除账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    // ==================== 账户余额关联逻辑 ====================

    /**
     * 新增账单时更新账户余额
     */
    private void updateAccountBalanceForNewBill(Bill bill) {
        String accountId = bill.getAccountId();
        long localAccountId = bill.getLocalAccountId();
        int billType = bill.getType();

        if ((accountId != null && !accountId.isEmpty()) || localAccountId > 0) {
            try {
                Account account = getAccount(accountId, localAccountId);
                if (account != null) {
                    double oldBalance = account.getBalance();
                    double amount = bill.getAmount();
                    double newBalance = (billType == 1) ? (oldBalance + amount) : (oldBalance - amount);

                    account.setBalance(newBalance);
                    updateAccountInDb(account);
                    Log.d(TAG, "账户余额已更新(主账户): " + account.getName() + " = " + newBalance);
                }
            } catch (Exception e) {
                Log.e(TAG, "更新主账户余额失败", e);
            }
        }

        if (billType == 2 || billType == 3) {
            String toAccountId = bill.getToAccountId();
            long toLocalId = bill.getToLocalAccountId();

            if ((toAccountId != null && !toAccountId.isEmpty()) || toLocalId > 0) {
                try {
                    Account toAccount = getAccount(toAccountId, toLocalId);
                    if (toAccount != null) {
                        double oldBalance = toAccount.getBalance();
                        double amount = bill.getAmount();
                        double newBalance = oldBalance + amount;

                        toAccount.setBalance(newBalance);
                        updateAccountInDb(toAccount);
                        Log.d(TAG, "账户余额已更新(目标账户): " + toAccount.getName() + " = " + newBalance);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "更新目标账户余额失败", e);
                }
            }
        }
    }

    private Account getAccount(String cloudId, long localId) {
        Account account = null;
        if (cloudId != null && !cloudId.isEmpty()) {
            account = accountDao.getAccountByCloudId(cloudId);
        }
        if (account == null && localId > 0) {
            account = accountDao.getAccountByLocalId(localId);
        }
        return account;
    }

    private void updateAccountInDb(Account account) {
        account.setUpdatedAt(new Date());
        account.setSyncState(SyncState.TO_UPDATE);
        accountDao.update(account);
        try {
            AccountSyncWorker.enqueue(context);
        } catch (Exception ignored) {}
    }

    /**
     * 更新账单时调整账户余额：先冲销旧账单对账户的影响，再应用新账单的影响
     */
    private void updateAccountBalanceForBillUpdate(Bill oldBill, Bill newBill) {
        try {
            restoreAccountBalanceForDeletedBill(oldBill);
            updateAccountBalanceForNewBill(newBill);
        } catch (Exception e) {
            Log.e(TAG, "更新账单余额异常", e);
        }
    }

    /**
     * 删除账单时恢复账户余额
     */
    private void restoreAccountBalanceForDeletedBill(Bill bill) {
        String accountId = bill.getAccountId();
        long localAccountId = bill.getLocalAccountId();
        int billType = bill.getType();

        if ((accountId != null && !accountId.isEmpty()) || localAccountId > 0) {
            try {
                Account account = getAccount(accountId, localAccountId);
                if (account != null) {
                    double balance = account.getBalance();
                    double amount = bill.getAmount();
                    double newBalance = (billType == 1) ? (balance - amount) : (balance + amount);

                    account.setBalance(newBalance);
                    updateAccountInDb(account);
                    Log.d(TAG, "账户余额已恢复(主账户): " + account.getName() + " = " + newBalance);
                }
            } catch (Exception e) {
                Log.e(TAG, "恢复主账户余额失败", e);
            }
        }

        if (billType == 2 || billType == 3) {
            String toAccountId = bill.getToAccountId();
            long toLocalId = bill.getToLocalAccountId();

            if ((toAccountId != null && !toAccountId.isEmpty()) || toLocalId > 0) {
                try {
                    Account toAccount = getAccount(toAccountId, toLocalId);
                    if (toAccount != null) {
                        double balance = toAccount.getBalance();
                        double amount = bill.getAmount();
                        double newBalance = balance - amount;

                        toAccount.setBalance(newBalance);
                        updateAccountInDb(toAccount);
                        Log.d(TAG, "账户余额已恢复(目标账户): " + toAccount.getName() + " = " + newBalance);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "恢复目标账户余额失败", e);
                }
            }
        }
    }

    // ==================== 迁移与账户重置操作 ====================

    /**
     * 迁移账单到新账户
     */
    public void migrateBillsToAccount(String fromAccountId, long fromLocalId, String toAccountId,
                                      ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<Bill> bills = billDao.getBillsByAccountSync(fromAccountId, fromLocalId);

                if (bills == null || bills.isEmpty()) {
                    Log.d(TAG, "没有需要迁移的账单");
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(0, "没有需要迁移的账单"))
                    );
                    return;
                }

                Log.d(TAG, "开始迁移账单: " + bills.size() + " 条");

                int successCount = 0;
                Date now = new Date();

                for (Bill bill : bills) {
                    restoreAccountBalanceForDeletedBill(bill);

                    bill.setAccountId(toAccountId);
                    bill.setUpdatedAt(now);
                    bill.setSyncState(SyncState.TO_UPDATE);

                    int updated = billDao.update(bill);
                    if (updated > 0) {
                        successCount++;
                        updateAccountBalanceForNewBill(bill);
                    }
                }

                Log.d(TAG, "账单迁移完成: " + successCount + "/" + bills.size());

                try {
                    BillSyncWorker.enqueue(context);
                    AccountSyncWorker.enqueue(context);
                } catch (Exception e) {
                    Log.e(TAG, "触发同步失败: " + e.getMessage(), e);
                }

                int finalSuccessCount = successCount;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalSuccessCount,
                                "成功迁移 " + finalSuccessCount + " 条账单"))
                );

            } catch (Exception e) {
                Log.e(TAG, "迁移账单失败", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 将账单设置为无账户
     */
    public void setBillsToNoAccount(String accountId, long localAccountId, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<Bill> bills = billDao.getBillsByAccountSync(accountId, localAccountId);

                if (bills == null || bills.isEmpty()) {
                    Log.d(TAG, "没有需要处理的账单");
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(0, "没有需要处理的账单"))
                    );
                    return;
                }

                Log.d(TAG, "开始设置账单为无账户: " + bills.size() + " 条");

                int successCount = 0;
                Date now = new Date();

                for (Bill bill : bills) {
                    restoreAccountBalanceForDeletedBill(bill);

                    bill.setAccountId(null);
                    bill.setLocalAccountId(-1);
                    bill.setUpdatedAt(now);
                    bill.setSyncState(SyncState.TO_UPDATE);

                    int updated = billDao.update(bill);
                    if (updated > 0) {
                        successCount++;
                    }
                }

                Log.d(TAG, "账单设置完成: " + successCount + "/" + bills.size());

                try {
                    BillSyncWorker.enqueue(context);
                    AccountSyncWorker.enqueue(context);
                } catch (Exception e) {
                    Log.e(TAG, "触发同步失败: " + e.getMessage(), e);
                }

                int finalSuccessCount = successCount;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalSuccessCount,
                                "成功设置 " + finalSuccessCount + " 条账单为无账户"))
                );

            } catch (Exception e) {
                Log.e(TAG, "设置账单失败", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    // ==================== 查询操作 ====================

    public LiveData<List<Bill>> getAllBillsByUser(String userId) {
        return billDao.getAllBillsByUser(userId);
    }

    public List<Bill> getAllBillsByUserSync(String userId) {
        return billDao.getAllBillsByUserSync(userId);
    }

    public LiveData<List<Bill>> getBillsInTimeRange(String userId, Date start, Date end) {
        return billDao.getBillsInTimeRange(userId, start, end);
    }

    public LiveData<List<Bill>> getBillsInTimeRangeExclusive(String userId, Date start, Date endExclusive) {
        return billDao.getBillsInTimeRangeExclusive(userId, start, endExclusive);
    }

    public List<Bill> getBillsInTimeRangePaged(String userId, Date start, Date end, int limit, int offset) {
        return billDao.getBillsInTimeRangePaged(userId, start, end, limit, offset);
    }

    public List<Bill> getBillsInTimeRangeSync(String userId, Date start, Date end) {
        return billDao.getBillsInTimeRangeSync(userId, start, end);
    }

    public LiveData<List<Bill>> getBillsByBook(String userId, String bookId) {
        return billDao.getBillsByBook(userId, bookId);
    }

    public LiveData<List<Bill>> getBillsByCategory(String userId, String categoryId) {
        return billDao.getBillsByCategory(userId, categoryId);
    }

    public void searchBills(String userId, String keyword, ApiResponse.Callback<List<Bill>> callback) {
        executors.diskIO().execute(() -> {
            try {
                String searchPattern = "%" + keyword + "%";
                List<Bill> results = billDao.searchBills(userId, searchPattern);

                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(results, "搜索完成"))
                );
            } catch (Exception e) {
                Log.e(TAG, "搜索账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    // ==================== 云端同步 ====================

    /**
     * 从云端同步账单:
     * 倒序拉取，第一批 (最新100条) 保存后立即回调 UI 刷新首页，
     * 后续数据在后台分批同步拉取写入。
     */
    public void syncFromCloud(String userId, ApiResponse.Callback<SyncResult> callback) {
        Log.d(TAG, "开始云端账单同步, userId=" + userId);
        executors.networkIO().execute(() ->
                fetchCloudBillPage(userId, 0, new HashSet<>(), new int[]{0, 0, 0}, callback)
        );
    }

    private void fetchCloudBillPage(String userId, int skip, Set<String> cloudObjectIdSet,
                                    int[] counts, ApiResponse.Callback<SyncResult> callback) {
        cn.bmob.v3.BmobQuery<CloudBill> query = new cn.bmob.v3.BmobQuery<>();
        query.addWhereEqualTo("user", cn.bmob.v3.BmobUser.getCurrentUser());
        query.setCachePolicy(cn.bmob.v3.BmobQuery.CachePolicy.NETWORK_ONLY);
        query.order("-billTime,-createdAt");
        query.setLimit(100);
        query.setSkip(skip);

        query.findObjects(new cn.bmob.v3.listener.FindListener<CloudBill>() {
            @Override
            public void done(List<CloudBill> page, cn.bmob.v3.exception.BmobException e) {
                if (e != null) {
                    Log.e(TAG, "拉取云端账单失败: " + e.getMessage());
                    if (skip == 0 && callback != null) {
                        executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e.getMessage())));
                    }
                    return;
                }

                if (page != null && !page.isEmpty()) {
                    for (CloudBill cb : page) {
                        if (cb.getObjectId() != null) {
                            cloudObjectIdSet.add(cb.getObjectId());
                        }
                    }

                    executors.diskIO().execute(() -> {
                        int[] batchCounts = saveCloudBillBatch(userId, page);
                        counts[0] += batchCounts[0];
                        counts[1] += batchCounts[1];

                        // 如果是第一页，保存后立刻通知回调，使 UI 能够第一时间显示最新账单
                        if (skip == 0 && callback != null) {
                            executors.mainThread().execute(() ->
                                    callback.onComplete(ApiResponse.success(
                                            new SyncResult(counts[0], counts[1], 0),
                                            "首批账单同步完成"
                                    ))
                            );
                        }

                        // 继续拉取下一页或进行收尾
                        if (page.size() == 100) {
                            executors.networkIO().execute(() ->
                                    fetchCloudBillPage(userId, skip + page.size(), cloudObjectIdSet, counts, callback)
                            );
                        } else {
                            finishFullSync(userId, cloudObjectIdSet, counts, skip == 0 ? null : callback);
                        }
                    });
                } else {
                    executors.diskIO().execute(() -> {
                        if (skip == 0 && callback != null) {
                            executors.mainThread().execute(() ->
                                    callback.onComplete(ApiResponse.success(new SyncResult(0, 0, 0), "云端无账单"))
                            );
                        } else {
                            finishFullSync(userId, cloudObjectIdSet, counts, skip == 0 ? null : callback);
                        }
                    });
                }
            }
        });
    }

    private int[] saveCloudBillBatch(String userId, List<CloudBill> page) {
        int newCount = 0;
        int updateCount = 0;
        List<Bill> localBills = billDao.getAllBillsByUserSync(userId);
        Map<String, Bill> localMap = new HashMap<>();
        if (localBills != null) {
            for (Bill b : localBills) {
                if (b.getObjectId() != null && !b.getObjectId().isEmpty()) {
                    localMap.put(b.getObjectId(), b);
                }
            }
        }

        for (CloudBill cloud : page) {
            String objectId = cloud.getObjectId();
            if (objectId == null || objectId.isEmpty()) continue;

            Bill local = localMap.get(objectId);
            if (local == null) {
                Bill newBill = cloud.toLocalEntity();
                newBill.setSyncState(SyncState.SYNCED);
                processImageUrls(newBill);
                billDao.insert(newBill);
                newCount++;
            } else {
                if (local.getSyncState() != SyncState.SYNCED) {
                    continue; // 保护本地未同步修改
                }

                Date cloudTime = DateConvertUtil.safeConvertToDate(cloud.getUpdatedAt());
                Date localTime = local.getUpdatedAt();
                if (localTime == null || (cloudTime != null && cloudTime.after(localTime))) {
                    updateLocalBillFromCloud(local, cloud);
                    local.setSyncState(SyncState.SYNCED);
                    processImageUrls(local);
                    billDao.update(local);
                    updateCount++;
                }
            }
        }
        return new int[]{newCount, updateCount};
    }

    private void finishFullSync(String userId, Set<String> cloudObjectIdSet, int[] counts,
                                ApiResponse.Callback<SyncResult> callback) {
        try {
            List<Bill> localBills = billDao.getAllBillsByUserSync(userId);
            int deleteCount = 0;
            if (localBills != null) {
                for (Bill local : localBills) {
                    String objectId = local.getObjectId();
                    if (objectId != null && !objectId.isEmpty()
                            && local.getSyncState() == SyncState.SYNCED
                            && !cloudObjectIdSet.contains(objectId)) {
                        billDao.delete(local);
                        deleteCount++;
                    }
                }
            }
            counts[2] = deleteCount;
            Log.i(TAG, "云端账单全量同步完成: 新增 " + counts[0] + ", 更新 " + counts[1] + ", 删除 " + deleteCount);
            if (callback != null) {
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(
                                new SyncResult(counts[0], counts[1], counts[2]),
                                "全量同步完成"
                        ))
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "全量同步收尾异常", e);
        }
    }

    private void updateLocalBillFromCloud(Bill local, CloudBill cloud) {
        local.setUserId(cloud.getUserId());
        local.setBookId(cloud.getBookId());
        local.setAccountId(cloud.getAccountId());
        local.setCategoryId(cloud.getCategoryId());
        local.setCategoryName(cloud.getCategoryName());
        local.setCategoryIconUrl(cloud.getCategoryIconUrl());
        local.setAmount(cloud.getAmount() != null ? cloud.getAmount() : 0);
        local.setType(cloud.getType() != null ? cloud.getType() : 0);
        local.setExcludeBudget(cloud.getExcludeBudget() != null && cloud.getExcludeBudget());
        local.setRemark(cloud.getRemark());
        local.setBillTime(DateConvertUtil.safeConvertToDate(cloud.getBillTime()));
        local.setImageUrls(cloud.getImageUrls());
        local.setLocation(cloud.getLocation());
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(cloud.getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(cloud.getUpdatedAt()));
    }

    /**
     * 同步结果封装
     */
    public static class SyncResult {
        public final int newCount;
        public final int updateCount;
        public final int deleteCount;

        public SyncResult(int newCount, int updateCount, int deleteCount) {
            this.newCount = newCount;
            this.updateCount = updateCount;
            this.deleteCount = deleteCount;
        }
    }

    // ==================== 图片URL处理 ====================

    private void processImageUrls(Bill bill) {
        if (bill.getImageUrls() == null || bill.getImageUrls().isEmpty()) {
            return;
        }

        List<String> processedUrls = new ArrayList<>();
        for (String url : bill.getImageUrls()) {
            if (url != null && !url.isEmpty()) {
                if (!url.startsWith("http")) {
                    String fullUrl = OSS_PUBLIC_BASE_URL + url;
                    processedUrls.add(fullUrl);
                    Log.d(TAG, "转换URL: " + url + " -> " + fullUrl);
                } else {
                    processedUrls.add(url);
                }
            }
        }
        bill.setImageUrls(processedUrls);
    }
}
