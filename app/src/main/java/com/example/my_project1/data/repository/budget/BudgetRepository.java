package com.example.my_project1.data.repository.budget;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.BudgetDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.data.remote.model.cloudbudget.BmobBudgetApiImpl;
import com.example.my_project1.data.remote.model.cloudbudget.CloudBudget;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.BudgetSyncWorker;

import java.util.List;
import java.util.function.Consumer;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;

/**
 * BudgetRepository（重构版）
 *
 * 核心修复：
 *
 * 1. 【修复重复金额叠加 Bug】
 *    addOrUpdateCategoryBudget() 在插入前调用 getExistingCategoryBudget() 唯一性检查：
 *    同一分类在同一 budgetType + year + month 下若已存在记录则执行更新而非插入，
 *    彻底杜绝重复记录导致的金额叠加问题。
 *
 * 2. 【一分类一周期约束】
 *    同一分类在同一预算周期内只允许存在一条记录。修改 period 是对现有记录的更新，
 *    而非新增不同 period 的记录。
 *
 * 3. 【防重复同步】
 *    enqueueSync() 委托给 BudgetSyncWorker.enqueue()，内部使用 enqueueUniqueWork(KEEP)。
 *
 * 4. 【云端拉取合并】
 *    syncBudgetsFromCloud() 完整实现合并规则，不覆盖本地待同步数据。
 */
public class BudgetRepository {

    private static final String TAG = "BudgetRepository";

    private final BudgetDao         dao;
    private final AppDatabase       db;
    private final BmobBudgetApiImpl api;
    private final Context           context;

    public BudgetRepository(Context context) {
        this.context = context.getApplicationContext();
        db   = AppDatabase.getInstance(this.context);
        dao  = db.budgetDao();
        api  = new BmobBudgetApiImpl(this.context);
    }

    // ── 总预算 LiveData ────────────────────────────────────

    public LiveData<Budget> getMonthBudgetLive(String userId, int year, int month) {
        return dao.getMonthBudgetLive(userId, year, month);
    }

    public LiveData<Budget> getYearBudgetLive(String userId, int year) {
        return dao.getYearBudgetLive(userId, year);
    }

    // ── 总预算同步读取 ─────────────────────────────────────

    public Budget getMonthBudgetSync(String userId, int year, int month) {
        return dao.getMonthBudgetSync(userId, year, month);
    }

    public Budget getYearBudgetSync(String userId, int year) {
        return dao.getYearBudgetSync(userId, year);
    }

    // ── 分类预算 ───────────────────────────────────────────

    public LiveData<List<Budget>> getCategoryBudgetsLive(
            String userId, String budgetType, int year, int month) {
        return dao.getCategoryBudgetsLive(userId, budgetType, year, month);
    }

    public Budget getCategoryBudget(String userId, String catId,
                                    String budgetType, int year, int month) {
        return dao.getCategoryBudget(userId, catId, budgetType, year, month);
    }

    public Budget getCategoryBudgetByPeriod(String userId, String catId,
                                            String budgetType, int period,
                                            int year, int month) {
        return dao.getCategoryBudgetWithPeriod(userId, catId, budgetType, period, year, month);
    }

    public List<Budget> getCategoryBudgetsSync(String userId,
                                               String budgetType, int year, int month) {
        return dao.getCategoryBudgetsSyncByType(userId, budgetType, year, month);
    }

    // ── 剩余可分配预算 ─────────────────────────────────────

    public double getRemainingAllocation(double totalAmount, String userId,
                                         String budgetType, int year, int month) {
        double allocated = dao.getTotalAllocatedAmount(userId, budgetType, year, month);
        return totalAmount - allocated;
    }

    // ── 总预算写操作 ───────────────────────────────────────

    public void insert(Budget budget, Consumer<Long> onInserted) {
        AppExecutors.get().diskIO().execute(() -> {
            budget.setSyncState(SyncState.TO_CREATE.getValue());
            budget.setUpdatedAt(System.currentTimeMillis());
            long id = dao.insert(budget);
            enqueueSync();
            if (onInserted != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                AppExecutors.get().mainThread().execute(() -> onInserted.accept(id));
            }
        });
    }

    public void update(Budget budget) {
        AppExecutors.get().diskIO().execute(() -> {
            budget.setSyncState(SyncState.TO_UPDATE.getValue());
            budget.setUpdatedAt(System.currentTimeMillis());
            dao.update(budget);
            enqueueSync();
        });
    }

    // ── 分类预算写操作（核心修复）─────────────────────────

    /**
     * 新增或更新分类预算（唯一性约束版本）。
     *
     * 规则：同一分类在同一 budgetType + year + month 下只允许存在一条记录。
     *   - 若已存在（不论 period）→ 执行更新（amount + period + 时间范围一并更新）。
     *   - 若不存在              → 执行插入。
     *
     * 所有添加分类预算的入口均应调用此方法，禁止直接调用 insert()。
     *
     * @param budget  分类预算对象（targetType=TARGET_CATEGORY，targetId 必须非空）
     * @param onDone  完成回调（主线程），参数："insert" 或 "update"
     */
    public void addOrUpdateCategoryBudget(Budget budget, Consumer<String> onDone) {
        AppExecutors.get().diskIO().execute(() -> {
            String userId     = budget.getOwnerId();
            String catId      = budget.getTargetId();
            String budgetType = budget.getBudgetType();
            int    year       = budget.getYear();
            int    month      = budget.getMonth();

            Budget existing = dao.getExistingCategoryBudget(
                    userId, catId, budgetType, year, month);

            String action;
            if (existing != null) {
                // 已存在 → 更新，保留 cloudId 和 ownerId
                existing.setAmount(budget.getAmount());
                existing.setPeriod(budget.getPeriod());
                existing.setStartTime(budget.getStartTime());
                existing.setEndTime(budget.getEndTime());
                // 同步更新分类展示信息快照（分类名称/图标可能已变更）
                if (budget.getCategoryName() != null)
                    existing.setCategoryName(budget.getCategoryName());
                if (budget.getCategoryIconUrl() != null)
                    existing.setCategoryIconUrl(budget.getCategoryIconUrl());
                existing.setSyncState(SyncState.TO_UPDATE.getValue());
                existing.setUpdatedAt(System.currentTimeMillis());
                dao.update(existing);
                Log.d(TAG, "addOrUpdateCategoryBudget UPDATE id=" + existing.getId()
                        + " catId=" + catId + " amount=" + budget.getAmount());
                action = "update";
            } else {
                // 不存在 → 插入
                budget.setSyncState(SyncState.TO_CREATE.getValue());
                budget.setUpdatedAt(System.currentTimeMillis());
                long id = dao.insert(budget);
                Log.d(TAG, "addOrUpdateCategoryBudget INSERT id=" + id
                        + " catId=" + catId + " amount=" + budget.getAmount());
                action = "insert";
            }

            enqueueSync();

            if (onDone != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                final String a = action;
                AppExecutors.get().mainThread().execute(() -> onDone.accept(a));
            }
        });
    }

    // ── 删除 ──────────────────────────────────────────────

    /**
     * 按主键软删除：有 cloudId → 标记 TO_DELETE；无 cloudId → 直接删本地。
     */
    public void markDeleteById(int budgetId) {
        AppExecutors.get().diskIO().execute(() -> {
            Budget b = dao.getById(budgetId);
            if (b == null) return;
            if (b.getCloudId() != null && !b.getCloudId().isEmpty()) {
                b.setSyncState(SyncState.TO_DELETE.getValue());
                dao.update(b);
            } else {
                dao.deleteById(budgetId);
            }
            enqueueSync();
        });
    }

    public void markDelete(Budget budget) {
        AppExecutors.get().diskIO().execute(() -> {
            budget.setSyncState(SyncState.TO_DELETE.getValue());
            dao.update(budget);
            enqueueSync();
        });
    }

    // ── 同步相关 ───────────────────────────────────────────

    public List<Budget> getPendingSyncBudgets() { return dao.getPendingSyncBudgets(); }

    public Budget getByCloudId(String cloudId)  { return dao.getByCloudId(cloudId); }

    public void updateCloudId(int id, String cloudId, int syncState) {
        dao.updateCloudId(id, cloudId, syncState);
    }

    /**
     * 从 Bmob 云端拉取全部预算并与本地合并。
     *
     * 合并策略（与 BillRepository.syncFromCloud 对齐）：
     *   1. 云端有、本地无                          → 插入，状态 SYNCED。
     *   2. 云端有、本地有，且关键字段不同            → 更新本地。
     *   3. 本地 TO_CREATE / TO_UPDATE / TO_DELETE  → 跳过，优先保留本地变更。
     */
    public void syncBudgetsFromCloud(Consumer<Boolean> callback) {
        AppExecutors.get().networkIO().execute(() ->
                api.fetchAllBudgets(new FindListener<CloudBudget>() {
                    @Override
                    public void done(List<CloudBudget> list, BmobException e) {
                        if (e != null || list == null) {
                            Log.e(TAG, "拉取预算失败：" + (e != null ? e.getMessage() : "null"));
                            postCallback(callback, false);
                            return;
                        }
                        AppExecutors.get().diskIO().execute(() -> {
                            String userId = api.getCurrentUserId();
                            if (userId == null) { postCallback(callback, false); return; }
                            for (CloudBudget cloud : list) mergeCloudBudget(cloud, userId);
                            postCallback(callback, true);
                        });
                    }
                })
        );
    }

    private void mergeCloudBudget(CloudBudget cloud, String userId) {
        Budget local = dao.getByCloudId(cloud.getObjectId());

        if (local == null) {
            Budget b = cloud.toLocalBudget();
            b.setOwnerId(userId);
            b.setSyncState(SyncState.SYNCED.getValue());
            dao.insert(b);
            Log.d(TAG, "云端预算同步到本地 cloudId=" + cloud.getObjectId());
            return;
        }

        int state = local.getSyncState();
        if (state == SyncState.TO_DELETE.getValue()
                || state == SyncState.TO_CREATE.getValue()
                || state == SyncState.TO_UPDATE.getValue()) {
            return; // 本地有待处理变更，跳过
        }

        boolean needUpdate =
                local.getAmount()     != cloud.getAmount()
                        || local.getPeriod()     != cloud.getPeriod()
                        || local.getStartTime()  != cloud.getStartTime()
                        || local.getEndTime()    != cloud.getEndTime()
                        || local.getTargetType() != cloud.getTargetType()
                        || !safeEquals(local.getTargetId(),   cloud.getTargetId())
                        || !safeEquals(local.getBudgetType(), cloud.getBudgetType())
                        || local.getYear()       != cloud.getYear()
                        || local.getMonth()      != cloud.getMonth();
        // 注意：categoryName / categoryIconUrl 是本地展示快照，CloudBudget 不含这两个字段，
        // 合并时保留本地已有的值，不做覆盖。

        if (needUpdate) {
            local.setAmount(cloud.getAmount());
            local.setPeriod(cloud.getPeriod());
            local.setStartTime(cloud.getStartTime());
            local.setEndTime(cloud.getEndTime());
            local.setTargetType(cloud.getTargetType());
            local.setTargetId(cloud.getTargetId());
            local.setBudgetType(cloud.getBudgetType());
            local.setYear(cloud.getYear());
            local.setMonth(cloud.getMonth());
            // categoryName / categoryIconUrl 保留本地原值，不从云端覆盖
            local.setSyncState(SyncState.SYNCED.getValue());
            local.setUpdatedAt(System.currentTimeMillis());
            dao.update(local);
            Log.d(TAG, "云端预算更新本地 cloudId=" + cloud.getObjectId());
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ── 支出统计 ───────────────────────────────────────────

    public List<Budget> getBudgetsInRange(String userId, long from, long to) {
        return dao.getBudgetsInRange(userId, from, to);
    }

    public double getSpentAmountByCategory(String userId, String catCloudId,
                                           long startMs, long endMs) {
        if (userId == null || catCloudId == null) return 0;
        try {
            List<Bill> bills = db.billDao()
                    .getBillsByCategoryInRange(userId, catCloudId, startMs, endMs);
            if (bills == null) return 0;
            double sum = 0;
            for (Bill b : bills) { if (!b.isExcludeBudget()) sum += b.getAmount(); }
            return sum;
        } catch (Exception e) {
            Log.e(TAG, "统计分类支出失败：" + e.getMessage());
            return 0;
        }
    }

    public double getTotalSpentInPeriod(String userId, long startMs, long endMs) {
        if (userId == null) return 0;
        try {
            List<Bill> bills = db.billDao().getExpenseBillsInRange(userId, startMs, endMs);
            if (bills == null) return 0;
            double sum = 0;
            for (Bill b : bills) { if (!b.isExcludeBudget()) sum += b.getAmount(); }
            return sum;
        } catch (Exception e) {
            Log.e(TAG, "统计总支出失败：" + e.getMessage());
            return 0;
        }
    }

    public double[] getDailyAccumulatedSpent(String userId, long periodStartMs,
                                             long periodEndMs, int days) {
        double[] result = new double[days];
        if (userId == null || days <= 0) return result;
        try {
            List<Bill> bills = db.billDao()
                    .getExpenseBillsInRange(userId, periodStartMs, periodEndMs);
            if (bills == null) return result;
            double[] daily = new double[days];
            long msPerDay = 86_400_000L;
            for (Bill b : bills) {
                if (b.isExcludeBudget()) continue;
                long bt = b.getBillTime() != null ? b.getBillTime().getTime() : 0;
                int idx = (int) ((bt - periodStartMs) / msPerDay);
                if (idx >= 0 && idx < days) daily[idx] += b.getAmount();
            }
            double acc = 0;
            for (int i = 0; i < days; i++) { acc += daily[i]; result[i] = acc; }
        } catch (Exception e) {
            Log.e(TAG, "统计每日累计支出失败：" + e.getMessage());
        }
        return result;
    }

    // ── 内部工具 ───────────────────────────────────────────

    private void enqueueSync() {
        BudgetSyncWorker.enqueue(context);
    }

    private void postCallback(Consumer<Boolean> cb, boolean val) {
        if (cb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AppExecutors.get().mainThread().execute(() -> cb.accept(val));
        }
    }
}