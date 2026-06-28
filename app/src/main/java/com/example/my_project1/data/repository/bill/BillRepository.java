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
import java.util.List;
import java.util.Map;

/**
 * BillRepository - 修复版 (添加账户余额更新)
 * -------------------------------------------------------
 * ✅ 核心修复:
 * 1. 插入账单时更新账户余额
 * 2. 更新账单时调整账户余额差值
 * 3. 删除账单时恢复账户余额
 * 4. 支持收入和支出的正确计算
 */
public class BillRepository {

    private static final String TAG = "BillRepository";
    private static final String OSS_PUBLIC_BASE_URL = "https://xd-user-image.oss-cn-hangzhou.aliyuncs.com/";

    private final BillDao billDao;
    private final AccountDao accountDao; //  新增: 用于更新账户
    private final AppExecutors executors;
    private final BmobBillApiImpl bmobApi;
    private final Context context;

    // ==================== 构造函数 ====================

    public BillRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.context = context.getApplicationContext();
        this.billDao = db.billDao();
        this.accountDao = db.accountDao(); // 🔴 初始化 AccountDao
        this.executors = AppExecutors.get();
        this.bmobApi = new BmobBillApiImpl(context.getApplicationContext());
    }

    // ==================== 插入操作 ====================

    /**
     * 🔴 修复: 插入单条账单 + 更新账户余额
     */
    public void insertBill(Bill bill, ApiResponse.Callback<Long> callback) {
        executors.diskIO().execute(() -> {
            try {
                Date now = new Date();
                bill.setCreatedAt(now);
                bill.setUpdatedAt(now);
                bill.setSyncState(SyncState.TO_CREATE);

                // 🔑 关键: 在插入时处理图片URL
                processImageUrls(bill);

                long id = billDao.insert(bill);

                if (id > 0) {
                    Log.d(TAG, "✅ 插入账单成功: ID=" + id);

                    // 更新账户余额
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

                // 批量更新账户余额
                for (Bill bill : bills) {
                    updateAccountBalanceForNewBill(bill);
                }

                Log.d(TAG, "✅ 批量插入成功: " + count + " 条");
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
     * 🔴 修复: 更新账单 + 调整账户余额
     */
    public void updateBill(Bill bill, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 获取原始账单数据
                Bill oldBill = billDao.getBillByObjectIdSync(bill.getObjectId());

                bill.setUpdatedAt(new Date());
                bill.setSyncState(SyncState.TO_UPDATE);
                processImageUrls(bill);

                int rows = billDao.update(bill);

                if (rows > 0 && oldBill != null) {
                    //调整账户余额(先恢复旧值,再添加新值)
                    updateAccountBalanceForBillUpdate(oldBill, bill);
                }

                Log.d(TAG, "✅ 更新账单: " + rows + " 行");
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(rows, "更新成功"))
                );
            } catch (Exception e) {
                Log.e(TAG, "更新账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * ⭐ 同步获取账单（用于编辑模式）
     * 注意：必须在后台线程调用
     */
    public Bill getBillByObjectIdSync(String objectId) {
        return billDao.getBillByObjectIdSync(objectId);
    }

    /**
     * 按账户查询账单
     */
    public LiveData<List<Bill>> getBillsByAccount(String userId, String accountId) {
        return billDao.getBillsByAccount(userId, accountId);
    }

    // ==================== 删除操作 ====================

    /**
     * 🔴 修复: 删除账单(软删除) + 恢复账户余额
     */
    public void deleteBill(Bill bill, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 🔴 在删除前恢复账户余额
                restoreAccountBalanceForDeletedBill(bill);

                // 1. 标记为待删除状态
                bill.setSyncState(SyncState.TO_DELETE);
                bill.setUpdatedAt(new Date());

                int rows = billDao.update(bill);

                Log.d(TAG, "✅ 标记删除成功: " + rows + " 行, objectId=" + bill.getObjectId());

                executors.mainThread().execute(() -> {
                    callback.onComplete(ApiResponse.success(rows, "删除成功"));

                    //触发后台同步，将删除同步到云端
                    try {
                        BillSyncWorker.enqueue(context);
                        Log.d(TAG, "✅ 已触发删除同步任务");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ 触发同步失败: " + e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ 删除账单异常", e);
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
                // 先获取所有要删除的账单,恢复它们的账户余额
                List<Bill> billsToDelete = billDao.getBillsByBook(userId, bookId).getValue();
                if (billsToDelete != null) {
                    for (Bill bill : billsToDelete) {
                        restoreAccountBalanceForDeletedBill(bill);
                    }
                }

                int count = billDao.deleteBillsByBook(userId, bookId);
                Log.d(TAG, "✅ 删除账本账单: " + count + " 条");

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
    public void deleteBillsByAccount(String userId, String accountId, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                int count = billDao.deleteBillsByAccountId(userId, accountId);
                Log.d(TAG, "✅ 删除账户账单: " + count + " 条");

                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(count, "删除成功"))
                );
            } catch (Exception e) {
                Log.e(TAG, "删除账户账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    // ==================== 🔴 新增: 账户余额更新逻辑 ====================

    /**
     * 🔴 新增账单时更新账户余额
     *
     * @param bill 新增的账单
     */
    private void updateAccountBalanceForNewBill(Bill bill) {
        if (bill.getAccountId() == null || bill.getAccountId().isEmpty()) {
            Log.d(TAG, "⚠️ 账单未关联账户,跳过余额更新");
            return;
        }

        try {
            Account account = accountDao.getAccountByCloudId(bill.getAccountId());
            if (account == null) {
                Log.w(TAG, "⚠️ 未找到账户: " + bill.getAccountId());
                return;
            }

            double oldBalance = account.getBalance();
            double amount = bill.getAmount();
            int billType = bill.getType(); // 0-支出, 1-收入

            // 计算新余额
            double newBalance;
            if (billType == 1) {
                // 收入: 增加余额
                newBalance = oldBalance + amount;
                Log.d(TAG, "💰 收入: " + amount + ", 余额: " + oldBalance + " → " + newBalance);
            } else {
                // 支出: 减少余额
                newBalance = oldBalance - amount;
                Log.d(TAG, "💸 支出: " + amount + ", 余额: " + oldBalance + " → " + newBalance);
            }

            // 更新账户
            account.setBalance(newBalance);
            account.setUpdatedAt(new Date());
            account.setSyncState(SyncState.TO_UPDATE);
            accountDao.update(account);

            try {
                AccountSyncWorker.enqueue(context);
                Log.d(TAG, "✅ 已触发账户同步任务");
            } catch (Exception e) {
                Log.e(TAG, "❌ 触发账户同步失败: " + e.getMessage(), e);
            }

            Log.d(TAG, "✅ 账户余额已更新: " + account.getName() + " = " + newBalance);        } catch (Exception e) {
            Log.e(TAG, "❌ 更新账户余额失败", e);
        }
    }

    /**
     * 🔴 更新账单时调整账户余额
     *
     * @param oldBill 原始账单
     * @param newBill 新账单
     */
    private void updateAccountBalanceForBillUpdate(Bill oldBill, Bill newBill) {
        try {
            String oldAccountId = oldBill.getAccountId();
            String newAccountId = newBill.getAccountId();

            // 情况1: 账户未改变
            if (oldAccountId != null && oldAccountId.equals(newAccountId)) {
                Account account = accountDao.getAccountByCloudId(newAccountId);
                if (account == null) {
                    Log.w(TAG, "⚠️ 未找到账户: " + newAccountId);
                    return;
                }

                double oldAmount = oldBill.getAmount();
                double newAmount = newBill.getAmount();
                int oldType = oldBill.getType();
                int newType = newBill.getType();

                double balance = account.getBalance();

                // 先恢复旧账单的影响
                if (oldType == 1) {
                    balance -= oldAmount; // 恢复收入
                } else {
                    balance += oldAmount; // 恢复支出
                }

                // 再应用新账单的影响
                if (newType == 1) {
                    balance += newAmount; // 新收入
                } else {
                    balance -= newAmount; // 新支出
                }

                account.setBalance(balance);
                account.setUpdatedAt(new Date());
                account.setSyncState(SyncState.TO_UPDATE);
                accountDao.update(account);

                try {
                    AccountSyncWorker.enqueue(context);
                    Log.d(TAG, "✅ 已触发账户同步任务");
                } catch (Exception e) {
                    Log.e(TAG, "❌ 触发账户同步失败: " + e.getMessage(), e);
                }

                Log.d(TAG, "✅ 账户余额已调整: " + account.getName() + " = " + balance);
            }
            // 情况2: 账户改变了(从oldAccount转到newAccount)
            else {
                // 恢复旧账户余额
                if (oldAccountId != null && !oldAccountId.isEmpty()) {
                    Account oldAccount = accountDao.getAccountByCloudId(oldAccountId);
                    if (oldAccount != null) {
                        double oldBalance = oldAccount.getBalance();
                        if (oldBill.getType() == 1) {
                            oldBalance -= oldBill.getAmount(); // 恢复收入
                        } else {
                            oldBalance += oldBill.getAmount(); // 恢复支出
                        }
                        oldAccount.setBalance(oldBalance);
                        oldAccount.setUpdatedAt(new Date());
                        oldAccount.setSyncState(SyncState.TO_UPDATE);
                        accountDao.update(oldAccount);
                        try {
                            AccountSyncWorker.enqueue(context);
                            Log.d(TAG, "✅ 已触发账户同步任务");
                        } catch (Exception e) {
                            Log.e(TAG, "❌ 触发账户同步失败: " + e.getMessage(), e);
                        }
                        Log.d(TAG, "✅ 旧账户余额已恢复: " + oldAccount.getName());
                    }
                }

                // 更新新账户余额
                if (newAccountId != null && !newAccountId.isEmpty()) {
                    Account newAccount = accountDao.getAccountByCloudId(newAccountId);
                    if (newAccount != null) {
                        double newBalance = newAccount.getBalance();
                        if (newBill.getType() == 1) {
                            newBalance += newBill.getAmount(); // 新收入
                        } else {
                            newBalance -= newBill.getAmount(); // 新支出
                        }
                        newAccount.setBalance(newBalance);
                        newAccount.setUpdatedAt(new Date());
                        newAccount.setSyncState(SyncState.TO_UPDATE);
                        accountDao.update(newAccount);
                        try {
                            AccountSyncWorker.enqueue(context);
                            Log.d(TAG, "✅ 已触发账户同步任务");
                        } catch (Exception e) {
                            Log.e(TAG, "❌ 触发账户同步失败: " + e.getMessage(), e);
                        }
                        Log.d(TAG, "✅ 新账户余额已更新: " + newAccount.getName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 调整账户余额失败", e);
        }
    }

    /**
     * 🔴 删除账单时恢复账户余额
     *
     * @param bill 被删除的账单
     */
    private void restoreAccountBalanceForDeletedBill(Bill bill) {
        if (bill.getAccountId() == null || bill.getAccountId().isEmpty()) {
            Log.d(TAG, "⚠️ 账单未关联账户,跳过余额恢复");
            return;
        }

        try {
            Account account = accountDao.getAccountByCloudId(bill.getAccountId());
            if (account == null) {
                Log.w(TAG, "⚠️ 未找到账户: " + bill.getAccountId());
                return;
            }

            double balance = account.getBalance();
            double amount = bill.getAmount();
            int billType = bill.getType();

            // 恢复余额(与添加时相反)
            if (billType == 1) {
                // 删除收入: 减少余额
                balance -= amount;
                Log.d(TAG, "🔄 删除收入: " + amount + ", 余额恢复到: " + balance);
            } else {
                // 删除支出: 增加余额
                balance += amount;
                Log.d(TAG, "🔄 删除支出: " + amount + ", 余额恢复到: " + balance);
            }

            account.setBalance(balance);
            account.setUpdatedAt(new Date());
            account.setSyncState(SyncState.TO_UPDATE);
            accountDao.update(account);

            try {
                AccountSyncWorker.enqueue(context);
                Log.d(TAG, "✅ 已触发账户同步任务");
            } catch (Exception e) {
                Log.e(TAG, "❌ 触发账户同步失败: " + e.getMessage(), e);
            }

            Log.d(TAG, "✅ 账户余额已恢复: " + account.getName() + " = " + balance);
        } catch (Exception e) {
            Log.e(TAG, "❌ 恢复账户余额失败", e);
        }
    }

    // ==================== 迁移操作 ====================

    /**
     * 迁移账单到新账户
     *
     * @param fromAccountId 原账户ID
     * @param toAccountId 目标账户ID
     * @param callback 回调
     */
    public void migrateBillsToAccount(String fromAccountId, String toAccountId,
                                      ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 1. 查询原账户下的所有账单
                List<Bill> bills = billDao.getBillsByAccountSync(fromAccountId);

                if (bills == null || bills.isEmpty()) {
                    Log.d(TAG, "⚠️ 没有需要迁移的账单");
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(0, "没有需要迁移的账单"))
                    );
                    return;
                }

                Log.d(TAG, "📦 开始迁移账单: " + bills.size() + " 条");

                // 2. 获取目标账户（用于更新余额）
                Account targetAccount = accountDao.getAccountByCloudId(toAccountId);
                if (targetAccount == null) {
                    Log.e(TAG, "❌ 目标账户不存在: " + toAccountId);
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("目标账户不存在"))
                    );
                    return;
                }

                // 3. 逐条迁移账单并更新余额
                int successCount = 0;
                Date now = new Date();

                for (Bill bill : bills) {
                    // 🔴 关键：先恢复原账户余额，再更新新账户余额
                    // 因为账单迁移相当于从原账户删除，然后添加到新账户

                    // 3.1 恢复原账户余额
                    if (bill.getAccountId() != null && !bill.getAccountId().isEmpty()) {
                        Account oldAccount = accountDao.getAccountByCloudId(bill.getAccountId());
                        if (oldAccount != null) {
                            double oldBalance = oldAccount.getBalance();
                            int billType = bill.getType();
                            double amount = bill.getAmount();

                            // 恢复余额（与删除账单逻辑相同）
                            if (billType == 1) {
                                oldBalance -= amount; // 删除收入
                            } else {
                                oldBalance += amount; // 删除支出
                            }

                            oldAccount.setBalance(oldBalance);
                            oldAccount.setUpdatedAt(now);
                            oldAccount.setSyncState(SyncState.TO_UPDATE);
                            accountDao.update(oldAccount);

                            Log.d(TAG, "🔄 恢复原账户余额: " + oldAccount.getName() + " = " + oldBalance);
                        }
                    }

                    // 3.2 更新账单账户ID
                    bill.setAccountId(toAccountId);
                    bill.setUpdatedAt(now);
                    bill.setSyncState(SyncState.TO_UPDATE);

                    int updated = billDao.update(bill);
                    if (updated > 0) {
                        successCount++;

                        // 3.3 更新新账户余额
                        double newBalance = targetAccount.getBalance();
                        int billType = bill.getType();
                        double amount = bill.getAmount();

                        // 添加到新账户（与新增账单逻辑相同）
                        if (billType == 1) {
                            newBalance += amount; // 新收入
                        } else {
                            newBalance -= amount; // 新支出
                        }

                        targetAccount.setBalance(newBalance);
                        Log.d(TAG, "💰 更新新账户余额: " + targetAccount.getName() + " = " + newBalance);
                    }
                }

                // 4. 保存目标账户的最终余额
                targetAccount.setUpdatedAt(now);
                targetAccount.setSyncState(SyncState.TO_UPDATE);
                accountDao.update(targetAccount);

                Log.d(TAG, "✅ 账单迁移完成: " + successCount + "/" + bills.size());

                // 5. 触发同步
                try {
                    BillSyncWorker.enqueue(context);
                    AccountSyncWorker.enqueue(context);
                    Log.d(TAG, "✅ 已触发同步任务");
                } catch (Exception e) {
                    Log.e(TAG, "❌ 触发同步失败: " + e.getMessage(), e);
                }

                // 6. 回调成功
                int finalSuccessCount = successCount;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalSuccessCount,
                                "成功迁移 " + finalSuccessCount + " 条账单"))
                );

            } catch (Exception e) {
                Log.e(TAG, "❌ 迁移账单失败", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 将账单设置为无账户
     *
     * @param accountId 账户ID
     * @param callback 回调
     */
    public void setBillsToNoAccount(String accountId, ApiResponse.Callback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                // 1. 查询账户下的所有账单
                List<Bill> bills = billDao.getBillsByAccountSync(accountId);

                if (bills == null || bills.isEmpty()) {
                    Log.d(TAG, "⚠️ 没有需要处理的账单");
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(0, "没有需要处理的账单"))
                    );
                    return;
                }

                Log.d(TAG, "📦 开始设置账单为无账户: " + bills.size() + " 条");

                // 2. 获取账户（用于恢复余额）
                Account account = accountDao.getAccountByCloudId(accountId);

                // 3. 逐条设置账单并恢复余额
                int successCount = 0;
                Date now = new Date();

                for (Bill bill : bills) {
                    // 🔴 关键：先恢复账户余额，然后设置账单为无账户
                    // 这相当于从账户中删除账单

                    // 3.1 恢复账户余额（如果账户存在）
                    if (account != null) {
                        double balance = account.getBalance();
                        int billType = bill.getType();
                        double amount = bill.getAmount();

                        // 恢复余额（与删除账单逻辑相同）
                        if (billType == 1) {
                            balance -= amount; // 删除收入
                            Log.d(TAG, "🔄 恢复收入: " + amount + ", 余额: " + balance);
                        } else {
                            balance += amount; // 删除支出
                            Log.d(TAG, "🔄 恢复支出: " + amount + ", 余额: " + balance);
                        }

                        account.setBalance(balance);
                    }

                    // 3.2 设置账单为无账户
                    bill.setAccountId(null);
                    bill.setUpdatedAt(now);
                    bill.setSyncState(SyncState.TO_UPDATE);

                    int updated = billDao.update(bill);
                    if (updated > 0) {
                        successCount++;
                    }
                }

                // 4. 保存账户的最终余额（如果账户存在）
                if (account != null) {
                    account.setUpdatedAt(now);
                    account.setSyncState(SyncState.TO_UPDATE);
                    accountDao.update(account);

                    Log.d(TAG, "✅ 账户余额已恢复: " + account.getName() + " = " + account.getBalance());
                }

                Log.d(TAG, "✅ 账单设置完成: " + successCount + "/" + bills.size());

                // 5. 触发同步
                try {
                    BillSyncWorker.enqueue(context);
                    if (account != null) {
                        AccountSyncWorker.enqueue(context);
                    }
                    Log.d(TAG, "✅ 已触发同步任务");
                } catch (Exception e) {
                    Log.e(TAG, "❌ 触发同步失败: " + e.getMessage(), e);
                }

                // 6. 回调成功
                int finalSuccessCount = successCount;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalSuccessCount,
                                "成功设置 " + finalSuccessCount + " 条账单为无账户"))
                );

            } catch (Exception e) {
                Log.e(TAG, "❌ 设置账单失败", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }




    // ==================== 查询操作 ====================

    /**
     * 查询指定用户的所有账单
     */
    public LiveData<List<Bill>> getAllBillsByUser(String userId) {
        return billDao.getAllBillsByUser(userId);
    }

    /**
     * 按时间范围查询账单
     */
    public LiveData<List<Bill>> getBillsInTimeRange(String userId, Date start, Date end) {
        return billDao.getBillsInTimeRange(userId, start, end);
    }

    /**
     * 按账本查询账单
     */
    public LiveData<List<Bill>> getBillsByBook(String userId, String bookId) {
        return billDao.getBillsByBook(userId, bookId);
    }

    /**
     * 按分类查询账单
     */
    public LiveData<List<Bill>> getBillsByCategory(String userId, String categoryId) {
        return billDao.getBillsByCategory(userId, categoryId);
    }

    /**
     * 搜索账单
     */
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
     * 从云端同步账单
     */
    public void syncFromCloud(String userId, ApiResponse.Callback<SyncResult> callback) {
        Log.d(TAG, "========== 强制云端同步开始 ==========");

        executors.networkIO().execute(() -> {
            // 1. 获取云端最新数据（关键：NETWORK_ONLY 绕过 Bmob 本地磁盘缓存）
            cn.bmob.v3.BmobQuery<CloudBill> query = new cn.bmob.v3.BmobQuery<>();
            // 匹配当前用户
            query.addWhereEqualTo("user", cn.bmob.v3.BmobUser.getCurrentUser());
            query.setCachePolicy(cn.bmob.v3.BmobQuery.CachePolicy.NETWORK_ONLY);
            query.setLimit(500); // 确保拉取量足够

            query.findObjects(new cn.bmob.v3.listener.FindListener<CloudBill>() {
                @Override
                public void done(List<CloudBill> cloudBills, cn.bmob.v3.exception.BmobException e) {
                    if (e != null) {
                        Log.e(TAG, "❌ 拉取云端失败: " + e.getMessage());
                        executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e.getMessage())));
                        return;
                    }

                    executors.diskIO().execute(() -> {
                        try {
                            // 2. 获取本地数据库中所有该用户的账单
                            List<Bill> localBills = billDao.getAllBillsByUserSync(userId);
                            Map<String, Bill> localMap = new HashMap<>();
                            for (Bill b : localBills) {
                                if (b.getObjectId() != null) localMap.put(b.getObjectId(), b);
                            }

                            int newCount = 0;
                            int updateCount = 0;
                            List<Bill> toUpsert = new ArrayList<>();

                            // 3. 遍历云端数据，利用你提供的 CloudBill.toLocalEntity() 转换
                            for (CloudBill cloud : cloudBills) {
                                Bill cloudEntity = cloud.toLocalEntity(); // 🔴 使用你的转换逻辑
                                Bill local = localMap.get(cloud.getObjectId());

                                if (local == null) {
                                    // 本地完全没有 -> 插入
                                    cloudEntity.setSyncState(SyncState.SYNCED);
                                    toUpsert.add(cloudEntity);
                                    newCount++;
                                } else {
                                    // 本地有 -> 对比 updatedAt 判断是否需要更新
                                    // 注意：BmobObject 的 getUpdatedAt() 返回字符串，需转为 Date 对比
                                    Date cloudTime = DateConvertUtil.safeConvertToDate(cloud.getUpdatedAt());
                                    Date localTime = local.getUpdatedAt();

                                    if (localTime == null || (cloudTime != null && cloudTime.after(localTime))) {
                                        // 云端比本地新 -> 覆盖更新本地
                                        cloudEntity.setId(local.getId()); // 保持本地自增ID一致
                                        cloudEntity.setSyncState(SyncState.SYNCED);
                                        toUpsert.add(cloudEntity);
                                        updateCount++;
                                    }
                                }
                            }

                            // 4. 执行本地数据库写入
                            if (!toUpsert.isEmpty()) {
                                // 使用 REPLACE 策略的 insert 或 update
                                billDao.insertBills(toUpsert);
                            }

                            Log.i(TAG, "✅ 同步结果: 新增 " + newCount + ", 更新 " + updateCount);

                            SyncResult result = new SyncResult(newCount, updateCount, 0);
                            executors.mainThread().execute(() -> {
                                callback.onComplete(ApiResponse.success(result, "同步成功"));
                            });

                        } catch (Exception ex) {
                            Log.e(TAG, "❌ 同步处理异常", ex);
                            executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(ex)));
                        }
                    });
                }
            });
        });
    }

    /**
     * 同步结果
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

    /**
     * 处理同步数据
     */
    private SyncResult syncBillsFromCloud(String userId, List<CloudBill> cloudBills) {
        List<Bill> localBills = billDao.getAllBillsSync();
        Log.i(TAG, "========== 开始同步数据处理 ==========");
        Log.i(TAG, "📱 本地账单总数: " + localBills.size() + " 条");
        Log.i(TAG, "☁️ 云端账单总数: " + cloudBills.size() + " 条");

        // 构建云端账单映射
        Map<String, CloudBill> cloudBillMap = new HashMap<>();
        for (CloudBill cloud : cloudBills) {
            if (cloud.getObjectId() != null) {
                cloudBillMap.put(cloud.getObjectId(), cloud);
            }
        }
        Log.i(TAG, "☁️ 云端有效ObjectId数量: " + cloudBillMap.size());

        // 构建本地账单映射
        Map<String, Bill> localBillMap = new HashMap<>();
        int localWithoutObjectId = 0;
        int localToCreate = 0;
        int localToUpdate = 0;
        int localToDelete = 0;
        int localSynced = 0;

        for (Bill local : localBills) {
            // 统计同步状态
            switch (local.getSyncState()) {
                case TO_CREATE:
                    localToCreate++;
                    break;
                case TO_UPDATE:
                    localToUpdate++;
                    break;
                case TO_DELETE:
                    localToDelete++;
                    break;
                case SYNCED:
                    localSynced++;
                    break;
            }

            if (local.getObjectId() != null && !local.getObjectId().isEmpty()) {
                localBillMap.put(local.getObjectId(), local);
            } else {
                localWithoutObjectId++;
            }
        }
        int newCount = 0;
        int updateCount = 0;
        int deleteCount = 0;
        int skipCount = 0;
        int protectedByNewerLocal = 0;

        // ========== 第1步: 处理云端账单(新增或更新) ==========
        Log.i(TAG, "========== 第1步: 处理云端数据 ==========");
        for (CloudBill cloud : cloudBills) {
            String objectId = cloud.getObjectId();
            if (objectId == null) {
                Log.w(TAG, "⚠️ 云端账单 ObjectId 为空,跳过");
                continue;
            }

            Bill local = localBillMap.get(objectId);

            if (local == null) {
                // 云端有,本地没有 → 新增
                Bill newBill = cloud.toLocalEntity();
                newBill.setSyncState(SyncState.SYNCED);
                processImageUrls(newBill);
                billDao.insert(newBill);
                newCount++;
                if (newCount <= 5) {
                    Log.d(TAG, "➕ 新增账单: objectId=" + objectId);
                }
            } else {
                // 云端和本地都有 → 检查是否需要更新

                // 🔑 保护1: 跳过待处理的本地修改
                if (local.getSyncState() == SyncState.TO_CREATE ||
                        local.getSyncState() == SyncState.TO_UPDATE ||
                        local.getSyncState() == SyncState.TO_DELETE) {
                    skipCount++;
                    if (skipCount <= 5) {
                        Log.d(TAG, "⚠️ 跳过更新: objectId=" + objectId +
                                " (本地状态=" + local.getSyncState() + ")");
                    }
                    continue;
                }

                //比较更新时间,只有云端更新时间更新时才更新本地
                Date cloudUpdatedAt = DateConvertUtil.safeConvertToDate(cloud.getUpdatedAt());
                Date localUpdatedAt = local.getUpdatedAt();

                if (cloudUpdatedAt == null) {
                    // 云端没有更新时间,跳过
                    skipCount++;
                    Log.d(TAG, "⚠️ 跳过更新: objectId=" + objectId + " (云端无更新时间)");
                    continue;
                }

                if (localUpdatedAt == null) {
                    // 本地没有更新时间,使用云端数据
                    updateLocalBillFromCloud(local, cloud);
                    local.setSyncState(SyncState.SYNCED);
                    processImageUrls(local);
                    billDao.update(local);
                    updateCount++;
                    Log.d(TAG, "🔄 更新账单: objectId=" + objectId + " (本地无更新时间)");
                    continue;
                }

                // 🔑 核心修复: 只有云端时间更新时才更新
                if (cloudUpdatedAt.after(localUpdatedAt)) {
                    updateLocalBillFromCloud(local, cloud);
                    local.setSyncState(SyncState.SYNCED);
                    processImageUrls(local);
                    billDao.update(local);
                    updateCount++;

                    long timeDiff = cloudUpdatedAt.getTime() - localUpdatedAt.getTime();
                    Log.d(TAG, String.format("🔄 更新账单: objectId=%s (云端更新 %dms 前)",
                            objectId, timeDiff));
                } else if (localUpdatedAt.after(cloudUpdatedAt)) {
                    // 本地更新时间更新,保留本地数据
                    protectedByNewerLocal++;
                    Log.w(TAG, String.format("✅ 保留本地数据: objectId=%s (本地比云端新 %dms)",
                            objectId, localUpdatedAt.getTime() - cloudUpdatedAt.getTime()));
                } else {
                    // 时间相同,不需要更新
                    skipCount++;
                }
            }
        }

        if (newCount > 5) {
            Log.i(TAG, "... 共新增 " + newCount + " 条 (仅显示前5条)");
        }
        if (skipCount > 5) {
            Log.i(TAG, "... 共跳过 " + skipCount + " 条 (仅显示前5条)");
        }
        if (protectedByNewerLocal > 0) {
            Log.i(TAG, "✅ 保护本地新数据: " + protectedByNewerLocal + " 条");
        }

        // ========== 第2步: 检查需要删除的本地账单 ==========
        Log.i(TAG, "========== 第2步: 检查本地数据(删除检查) ==========");
        int checkCount = 0;
        int protectedByRule1 = 0;
        int protectedByRule2 = 0;
        int protectedByRule3 = 0;
        int protectedByInCloud = 0;

        for (Bill local : localBills) {
            String objectId = local.getObjectId();
            checkCount++;

            // 保护规则1: objectId 为空
            if (objectId == null || objectId.isEmpty()) {
                protectedByRule1++;
                if (local.getSyncState() == SyncState.TO_CREATE && protectedByRule1 <= 3) {
                    Log.w(TAG, "✅ 保护规则1: localId=" + local.getId() +
                            " (无ObjectId, 状态=" + local.getSyncState() + ")");
                }
                continue;
            }

            // 保护规则2: 待删除状态
            if (local.getSyncState() == SyncState.TO_DELETE) {
                protectedByRule2++;
                continue;
            }

            // 保护规则3: 待创建或待更新
            if (local.getSyncState() == SyncState.TO_CREATE ||
                    local.getSyncState() == SyncState.TO_UPDATE) {
                protectedByRule3++;
                if (protectedByRule3 <= 3) {
                    Log.w(TAG, "✅ 保护规则3: objectId=" + objectId +
                            ", localId=" + local.getId() +
                            " (状态=" + local.getSyncState() + ")");
                }
                continue;
            }

            // 检查云端是否存在
            if (cloudBillMap.containsKey(objectId)) {
                protectedByInCloud++;
                continue;
            }

            // 删除规则: 已同步 && 云端不存在
            if (local.getSyncState() == SyncState.SYNCED) {
                billDao.delete(local);
                deleteCount++;
                Log.w(TAG, "🗑️ 删除账单: objectId=" + objectId +
                        ", localId=" + local.getId() +
                        " (已同步但云端不存在)");
            }
        }

        return new SyncResult(newCount, updateCount, deleteCount);
    }

    /**
     * 从云端数据更新本地账单
     */
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

    // ==================== 图片URL处理 ====================

    /**
     * 处理单个账单的图片URL
     * 将OSS objectKey转换为公共访问URL
     *
     *
     */
    private void processImageUrls(Bill bill) {
        if (bill.getImageUrls() == null || bill.getImageUrls().isEmpty()) {
            return;
        }

        List<String> processedUrls = new ArrayList<>();
        for (String url : bill.getImageUrls()) {
            if (url != null && !url.isEmpty()) {
                // 如果是objectKey,转换为公共URL
                if (!url.startsWith("http")) {
                    String fullUrl = OSS_PUBLIC_BASE_URL + url;
                    processedUrls.add(fullUrl);
                    Log.d(TAG, "🔗 转换URL: " + url + " -> " + fullUrl);
                } else {
                    processedUrls.add(url);
                }
            }
        }
        bill.setImageUrls(processedUrls);
    }
}