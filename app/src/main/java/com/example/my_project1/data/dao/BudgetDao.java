package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.budget.Budget;

import java.util.List;

@Dao
public interface BudgetDao {

    // ── 增删改 ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Budget budget);

    @Update
    void update(Budget budget);

    @Delete
    void delete(Budget budget);

    /**
     * 按主键删除，删除后 Room 自动通知相关 LiveData 刷新 UI。
     */
    @Query("DELETE FROM budgets WHERE id = :id")
    void deleteById(int id);

    // ── 单条记录查询 ─────────────────────────────────────────

    /**
     * 按主键查询单条预算，用于删除前确认云端状态。
     */
    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    Budget getById(int id);

    // ── 总预算查询（按 budgetType + year + month 精确定位）───

    /** 同步查询：指定年月的月预算 */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 2 " +
            "AND budget_type = 'MONTH' AND year = :year AND month = :month LIMIT 1")
    Budget getMonthBudgetSync(String userId, int year, int month);

    /** 同步查询：指定年份的年预算 */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 2 " +
            "AND budget_type = 'YEAR' AND year = :year LIMIT 1")
    Budget getYearBudgetSync(String userId, int year);

    /** LiveData：监听指定年月的月预算变化 */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 2 " +
            "AND budget_type = 'MONTH' AND year = :year AND month = :month LIMIT 1")
    LiveData<Budget> getMonthBudgetLive(String userId, int year, int month);

    /** LiveData：监听指定年份的年预算变化 */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 2 " +
            "AND budget_type = 'YEAR' AND year = :year LIMIT 1")
    LiveData<Budget> getYearBudgetLive(String userId, int year);

    // ── 分类预算 ─────────────────────────────────────────────

    /**
     * LiveData：监听指定类型+年月的分类预算列表。
     * 排除 TO_DELETE 状态的记录，避免已标记删除的数据出现在列表中。
     */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND budget_type = :budgetType AND year = :year AND month = :month " +
            "AND sync_state != 3 " +
            "ORDER BY updated_at DESC")
    LiveData<List<Budget>> getCategoryBudgetsLive(
            String userId, String budgetType, int year, int month);

    /**
     * 同步查询：同一分类 + 同一 budgetType + 同一年月 下的唯一预算（不区分 period）。
     *
     * ⚠️ 业务约束：一个分类在同一 budgetType+年月 下只允许存在一条预算记录，
     * period 选择后不可与已有记录共存（如已有月预算，不能再添加日预算）。
     * 写入前用此方法检查，有记录则做更新而非新增。
     */
    /**
     * 同步查询：按分类 + budgetType + 年月精确定位（不区分 period）。
     * SQL 中不含 period 过滤条件（一个分类在同一年月下只有一条有效记录），
     * 原方法签名含 period 参数但 SQL 中未使用，导致 "Unused parameter" 警告，
     * 已移除该参数。调用方如需按 period 过滤请改用 getCategoryBudgetWithPeriod()。
     */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND target_id = :categoryId AND budget_type = :budgetType " +
            "AND year = :year AND month = :month LIMIT 1")
    Budget getCategoryBudgetByPeriod(String userId, String categoryId,
                                     String budgetType, int year, int month);

    /**
     * 同步查询：按分类 + budgetType + period + 年月精确定位（含 period 过滤）。
     * 用于需要区分周期类型的场景（如 BudgetResetWorker 处理日/周预算重置）。
     * 参数名使用 budgetPeriod 避免与 Budget.PERIOD_* 常量名冲突导致 lint 误判。
     */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND target_id = :categoryId AND budget_type = :budgetType " +
            "AND period = :budgetPeriod " +
            "AND year = :year AND month = :month LIMIT 1")
    Budget getCategoryBudgetWithPeriod(String userId, String categoryId,
                                       String budgetType, int budgetPeriod, int year, int month);

    /**
     * 同步查询：按分类 + budgetType + 年月 查找已有记录（不限 period）。
     * 用于判断新增前是否已存在同分类预算。
     */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND target_id = :categoryId AND budget_type = :budgetType " +
            "AND year = :year AND month = :month LIMIT 1")
    Budget getExistingCategoryBudget(String userId, String categoryId,
                                     String budgetType, int year, int month);

    /**
     * 同步查询（兼容旧版）：不区分 period，取最新的一条。
     * 用于云端同步及分类选择器 tag 展示场景。
     */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND target_id = :categoryId AND budget_type = :budgetType " +
            "AND year = :year AND month = :month LIMIT 1")
    Budget getCategoryBudget(String userId, String categoryId,
                             String budgetType, int year, int month);

    /** 同步查询：指定类型+年月下所有分类预算列表（排除待删除） */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND budget_type = :budgetType AND year = :year AND month = :month " +
            "AND sync_state != 3")
    List<Budget> getCategoryBudgetsSyncByType(String userId, String budgetType,
                                              int year, int month);

    /** 同步查询：当前用户所有分类预算，用于周期性重置（BudgetResetWorker） */
    @Query("SELECT * FROM budgets WHERE owner_id = :userId AND target_type = 1 " +
            "AND sync_state != 3")
    List<Budget> getCategoryBudgetsSync(String userId);

    // ── 同步 ─────────────────────────────────────────────────

    /** 查询所有待同步记录（TO_CREATE / TO_UPDATE / TO_DELETE） */
    @Query("SELECT * FROM budgets WHERE sync_state != 0")
    List<Budget> getPendingSyncBudgets();

    /** 按 cloudId 查询本地记录，用于云端同步合并 */
    @Query("SELECT * FROM budgets WHERE cloud_id = :cloudId LIMIT 1")
    Budget getByCloudId(String cloudId);

    /** 云端上传成功后，回写 cloudId 并将状态置为 SYNCED */
    @Query("UPDATE budgets SET cloud_id = :cloudId, sync_state = :syncState WHERE id = :id")
    void updateCloudId(int id, String cloudId, int syncState);

    /**
     * 更新分类预算的分类名称和图标（分类信息变更时调用）。
     */
    @Query("UPDATE budgets SET category_name = :name, category_icon_url = :iconUri WHERE id = :id")
    void updateCategoryMeta(int id, String name, String iconUri);

    // ── 时间范围查询 ─────────────────────────────────────────

    @Query("SELECT * FROM budgets WHERE owner_id = :userId " +
            "AND start_time >= :from AND end_time <= :to")
    List<Budget> getBudgetsInRange(String userId, long from, long to);

    // ── 统计 ─────────────────────────────────────────────────

    /**
     * 汇总指定类型+年月下所有分类的已分配预算金额。
     * 用于计算"剩余可分配预算 = 总预算金额 - 此值"。
     * 排除 TO_DELETE 状态，避免已标记删除的预算影响计算结果。
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM budgets " +
            "WHERE owner_id = :userId AND target_type = 1 " +
            "AND budget_type = :budgetType AND year = :year AND month = :month " +
            "AND sync_state != 3")
    double getTotalAllocatedAmount(String userId, String budgetType, int year, int month);
}