package com.example.my_project1.data.repository.saving;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.SavingPlanDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.SavingSyncWorker;

import java.util.Date;

/**
 * 存钱计划模块数据仓库
 * 作为MVVM架构中的Repository层，负责处理所有数据的写操作和同步调度
 */
public class SavingRepository {

    private static final String TAG = "SavingRepository";
    private static volatile SavingRepository instance;

    private final Context context;
    private final AppDatabase database;
    private final SavingPlanDao savingPlanDao;
    private final AppExecutors executors;

    private SavingRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        this.savingPlanDao = database.savingPlanDao();
        this.executors = AppExecutors.get();
    }

    public static SavingRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (SavingRepository.class) {
                if (instance == null) {
                    instance = new SavingRepository(context);
                }
            }
        }
        return instance;
    }

    public SavingPlanDao getSavingPlanDao() {
        return savingPlanDao;
    }

    // --- 计划 API ---

    /**
     * 插入新计划
     * 优化：不再预生成 SavingStep
     */
    public void insertPlan(@NonNull SavingPlan plan, @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            Date now = new Date();
            plan.setId(0);
            plan.setCurrentAmount(plan.getInitialAmount());
            plan.setSyncState(SyncState.TO_CREATE);
            plan.setCreatedAt(now);
            plan.setUpdatedAt(now);
            
            long id = savingPlanDao.insertPlan(plan);
            enqueueSync();
            return ApiResponse.success(id, "计划创建成功");
        });
    }

    /**
     * 更新计划信息
     */
    public void updatePlan(@NonNull SavingPlan plan, @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            SavingPlan stored = savingPlanDao.getPlanByIdSync(plan.getId());
            if (stored == null || stored.getSyncState() == SyncState.TO_DELETE) {
                return ApiResponse.error("计划不存在");
            }
            plan.setObjectId(stored.getObjectId());
            plan.setUserId(stored.getUserId());
            plan.setCurrentAmount(stored.getCurrentAmount());
            plan.setCreatedAt(stored.getCreatedAt());
            plan.setUpdatedAt(new Date());
            plan.setSyncState(nextWriteState(stored.getSyncState()));
            savingPlanDao.updatePlan(plan);
            enqueueSync();
            return ApiResponse.success(plan.getId(), "计划更新成功");
        });
    }

    /**
     * 删除计划
     */
    public void deletePlan(long planId, @Nullable ApiResponse.Callback<Long> callback) {
        deletePlan(planId, false, callback);
    }

    /**
     * 删除计划，提供是否删除关联账单的选项
     * @param planId 计划ID
     * @param deleteBills 是否同时删除关联的转账账单
     * @param callback 回调
     */
    public void deletePlan(long planId, boolean deleteBills, @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            SavingPlan plan = savingPlanDao.getPlanByIdSync(planId);
            if (plan == null) return ApiResponse.error("计划不存在");
            
            Date now = new Date();
            database.runInTransaction(() -> {
                if (deleteBills) {
                    // 批量获取关联记录
                    java.util.List<SavingRecord> records = savingPlanDao.getRecordsByPlanSync(planId);
                    java.util.List<Long> billIds = new java.util.ArrayList<>();
                    for (SavingRecord r : records) {
                        if (r.getLinkedBillId() > 0) billIds.add(r.getLinkedBillId());
                    }

                    if (!billIds.isEmpty()) {
                        // 1. 批量获取账单数据以优化性能
                        java.util.List<com.example.my_project1.data.model.bill.Bill> bills = database.billDao().getBillsByIds(billIds);
                        java.util.Map<Long, Double> accountChanges = new java.util.HashMap<>();
                        
                        // 2. 统计所有账户的余额变动（回滚逻辑）
                        for (com.example.my_project1.data.model.bill.Bill bill : bills) {
                            if (bill.getSyncState() != SyncState.TO_DELETE) {
                                // 支出账户增加（回退扣款）
                                long fromId = bill.getLocalAccountId();
                                Double fromChange = accountChanges.get(fromId);
                                accountChanges.put(fromId, (fromChange == null ? 0.0 : fromChange) + bill.getAmount());
                                
                                // 收入账户减少（回退存入）
                                long toId = bill.getToLocalAccountId();
                                Double toChange = accountChanges.get(toId);
                                accountChanges.put(toId, (toChange == null ? 0.0 : toChange) - bill.getAmount());
                            }
                        }

                        // 3. 批量更新受影响的账户余额
                        for (java.util.Map.Entry<Long, Double> entry : accountChanges.entrySet()) {
                            com.example.my_project1.data.model.account.Account acc = database.accountDao().getAccountByLocalId(entry.getKey());
                            if (acc != null) {
                                acc.setBalance(acc.getBalance() + entry.getValue());
                                acc.setSyncState(SyncState.TO_UPDATE);
                                acc.setUpdatedAt(now);
                                database.accountDao().update(acc);
                            }
                        }

                        // 4. 批量标记账单为删除状态
                        database.billDao().markBillsDeletedByIds(billIds, now.getTime());
                    }
                }

                // 标记计划及记录删除
                savingPlanDao.markRecordsDeleted(planId, now);
                plan.setSyncState(SyncState.TO_DELETE);
                plan.setUpdatedAt(now);
                savingPlanDao.updatePlan(plan);
            });

            enqueueSync();
            return ApiResponse.success(planId, "计划已删除");
        });
    }

    // --- 记录 API ---

    /**
     * 插入存钱记录
     * 优化：直接使用 stepIndex 关联动态卡片
     */
    public void insertRecord(@NonNull SavingRecord record, int stepIndex, 
                             long fromAccountId, long toAccountId,
                             @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            SavingPlan plan = savingPlanDao.getPlanByIdSync(record.getPlanId());
            if (plan == null || plan.getSyncState() == SyncState.TO_DELETE) {
                return ApiResponse.error("关联计划不存在");
            }
            Date now = new Date();
            record.setId(0);
            record.setStepIndex(stepIndex);
            record.setUserId(plan.getUserId());
            record.setSyncState(SyncState.TO_CREATE);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            
            final long[] id = new long[1];
            database.runInTransaction(() -> {
                // 1. 处理账户余额和账单 (只有在计划开启了转账记录时才执行)
                if (plan.isEnableTransfer() && fromAccountId > 0 && toAccountId > 0) {
                    processSavingTransaction(record, fromAccountId, toAccountId, plan.getName());
                }

                // 2. 插入存钱记录
                id[0] = savingPlanDao.insertRecord(record);
                
                // 3. 更新计划进度
                refreshProgressInternal(plan, now);
            });
            enqueueSync();
            return ApiResponse.success(id[0], "记账成功");
        });
    }

    private void processSavingTransaction(SavingRecord record, long fromId, long toId, String planName) {
        com.example.my_project1.data.model.account.Account fromAcc = database.accountDao().getAccountByLocalId(fromId);
        com.example.my_project1.data.model.account.Account toAcc = database.accountDao().getAccountByLocalId(toId);
        
        if (fromAcc != null && toAcc != null) {
            fromAcc.setBalance(fromAcc.getBalance() - record.getAmount());
            fromAcc.setSyncState(SyncState.TO_UPDATE);
            fromAcc.setUpdatedAt(new Date());
            database.accountDao().update(fromAcc);

            toAcc.setBalance(toAcc.getBalance() + record.getAmount());
            toAcc.setSyncState(SyncState.TO_UPDATE);
            toAcc.setUpdatedAt(new Date());
            database.accountDao().update(toAcc);

            com.example.my_project1.data.model.bill.Bill bill = new com.example.my_project1.data.model.bill.Bill();
            bill.setUserId(record.getUserId());
            bill.setAmount(record.getAmount());
            bill.setType(2); 
            bill.setLocalAccountId(fromId);
            bill.setToLocalAccountId(toId);
            bill.setAccountId(fromAcc.getObjectId());
            bill.setToAccountId(toAcc.getObjectId());
            bill.setBillTime(record.getRecordDate());
            bill.setCategoryIconUrl("android.resource://" + context.getPackageName() + "/" + R.drawable.ic_transference); // 统一图标
            bill.setCategoryName("存钱转账");
            bill.setRemark(fromAcc.getName() + "转" + record.getAmount() + "元到" + toAcc.getName() + " (计划:" + planName + ")");
            bill.setSyncState(SyncState.TO_CREATE);
            bill.setCreatedAt(new Date());
            bill.setUpdatedAt(new Date());
            
            long billId = database.billDao().insert(bill);
            record.setLinkedBillId(billId);
        }
    }

    /**
     * 删除存钱记录
     */
    public void deleteRecord(long recordId, @Nullable ApiResponse.Callback<Long> callback) {
        execute(callback, () -> {
            SavingRecord record = savingPlanDao.getRecordByIdSync(recordId);
            if (record == null) return ApiResponse.error("记录不存在");
            SavingPlan plan = savingPlanDao.getPlanByIdSync(record.getPlanId());
            Date now = new Date();
            database.runInTransaction(() -> {
                if (record.getLinkedBillId() > 0) {
                    revertSavingTransaction(record);
                }
                record.setSyncState(SyncState.TO_DELETE);
                record.setUpdatedAt(now);
                savingPlanDao.updateRecord(record);
                if (plan != null) refreshProgressInternal(plan, now);
            });
            enqueueSync();
            return ApiResponse.success(recordId, "记录已删除");
        });
    }

    private void revertSavingTransaction(SavingRecord record) {
        com.example.my_project1.data.model.bill.Bill bill = database.billDao().getByIdSync(record.getLinkedBillId());
        if (bill != null) {
            bill.setSyncState(SyncState.TO_DELETE);
            bill.setUpdatedAt(new Date());
            database.billDao().update(bill);

            com.example.my_project1.data.model.account.Account fromAcc = database.accountDao().getAccountByLocalId(bill.getLocalAccountId());
            com.example.my_project1.data.model.account.Account toAcc = database.accountDao().getAccountByLocalId(bill.getToLocalAccountId());

            if (fromAcc != null) {
                fromAcc.setBalance(fromAcc.getBalance() + record.getAmount());
                fromAcc.setSyncState(SyncState.TO_UPDATE);
                database.accountDao().update(fromAcc);
            }
            if (toAcc != null) {
                toAcc.setBalance(toAcc.getBalance() - record.getAmount());
                toAcc.setSyncState(SyncState.TO_UPDATE);
                database.accountDao().update(toAcc);
            }
        }
    }

    // --- 内部逻辑 ---

    private void refreshProgressInternal(SavingPlan plan, Date now) {
        double totalRecords = Math.max(0d, savingPlanDao.getSavedAmount(plan.getId()));
        double currentAmount = plan.getInitialAmount() + totalRecords;
        plan.setCurrentAmount(currentAmount);
        plan.setStatus(currentAmount >= plan.getTargetAmount() ? SavingPlan.STATUS_COMPLETED : SavingPlan.STATUS_ACTIVE);
        plan.setSyncState(nextWriteState(plan.getSyncState()));
        plan.setUpdatedAt(now);
        savingPlanDao.updatePlan(plan);
    }

    private SyncState nextWriteState(SyncState current) {
        return current == SyncState.TO_CREATE ? SyncState.TO_CREATE : SyncState.TO_UPDATE;
    }

    private void enqueueSync() {
        try {
            SavingSyncWorker.enqueue(context);
        } catch (Exception e) {
            Log.e(TAG, "调度同步失败", e);
        }
    }

    private <T> void execute(@Nullable ApiResponse.Callback<T> callback, Task<T> task) {
        executors.diskIO().execute(() -> {
            ApiResponse<T> response;
            try {
                response = task.run();
            } catch (Exception e) {
                Log.e(TAG, "任务执行异常", e);
                response = ApiResponse.error(e);
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
