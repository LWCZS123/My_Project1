package com.example.my_project1.data.remote.model.cloudbudget;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.budget.Budget;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobUser;

/**
 * 云端 Budget 对象（对应 Bmob 表 "Budget"）
 *
 * 字段说明：
 *  targetType  : 1 分类预算 / 2 总预算
 *  targetId    : 分类 Bmob objectId（String）；总预算为 null
 *  amount      : 预算金额
 *  period      : 0日 / 1周 / 2月 / 3年
 *  budgetType  : "MONTH" / "YEAR"（区分月/年预算独立记录）
 *  year        : 所属年份
 *  month       : 所属月份（年预算为 0）
 *  startTime   : 周期开始时间戳
 *  endTime     : 周期结束时间戳
 *  ownerId     : 所属用户（BmobUser Pointer）
 *  localId     : 本地 Room 主键（用于双向同步定位）
 *
 * ── 不含 categoryName / categoryIconUrl 的原因 ────────────────────
 *   云端通过 targetId（分类 objectId）关联分类，名称和图标从分类表读取，
 *   无需在预算表冗余存储。categoryName / categoryIconUrl 是本地 Budget
 *   表的展示快照，仅用于本地 UI，不参与云端同步。
 *   toLocalBudget() 转换时这两个字段保持为 null / ""，由调用方
 *   （BudgetRepository.mergeCloudBudget / ViewModel）在需要时补填。
 */
public class CloudBudget extends BmobObject {

    private int      localId;
    private BmobUser ownerId;
    private int      targetType;

    /** 分类的 Bmob objectId；总预算为 null */
    private String   targetId;

    private double   amount;
    private int      period;

    /** "MONTH" / "YEAR" */
    private String   budgetType;

    private int      year;

    /** 月份 1-12；年预算为 0 */
    private int      month;

    private long     startTime;
    private long     endTime;

    // ── Getters / Setters ──────────────────────────────────

    public int getLocalId()                    { return localId; }
    public void setLocalId(int localId)        { this.localId = localId; }

    public BmobUser getOwnerId()               { return ownerId; }
    public void setOwnerId(BmobUser ownerId)   { this.ownerId = ownerId; }

    public int getTargetType()                 { return targetType; }
    public void setTargetType(int t)           { this.targetType = t; }

    public String getTargetId()                { return targetId; }
    public void setTargetId(String id)         { this.targetId = id; }

    public double getAmount()                  { return amount; }
    public void setAmount(double amount)       { this.amount = amount; }

    public int getPeriod()                     { return period; }
    public void setPeriod(int period)          { this.period = period; }

    public String getBudgetType()              { return budgetType; }
    public void setBudgetType(String t)        { this.budgetType = t; }

    public int getYear()                       { return year; }
    public void setYear(int year)              { this.year = year; }

    public int getMonth()                      { return month; }
    public void setMonth(int month)            { this.month = month; }

    public long getStartTime()                 { return startTime; }
    public void setStartTime(long t)           { this.startTime = t; }

    public long getEndTime()                   { return endTime; }
    public void setEndTime(long t)             { this.endTime = t; }

    // ── 云端 → 本地 ────────────────────────────────────────

    /**
     * 将云端对象转换为本地 Budget 实体。
     *
     * 注意：categoryName 和 categoryIconUrl 不从云端获取（云端不存储这两个字段）。
     * 这两个字段会在 BudgetRepository.mergeCloudBudget() 中保留本地已有的值，
     * 或在 AddCategoryBudgetFragment 保存时由用户传入的分类信息填充。
     */
    public Budget toLocalBudget() {
        Budget b = new Budget();
        b.setCloudId(getObjectId());
        b.setOwnerId(ownerId != null ? ownerId.getObjectId() : null);
        b.setTargetType(targetType);
        b.setTargetId(targetId);
        b.setAmount(amount);
        b.setPeriod(period);
        b.setBudgetType(budgetType);
        b.setYear(year);
        b.setMonth(month);
        b.setStartTime(startTime);
        b.setEndTime(endTime);
        b.setSyncState(SyncState.SYNCED.getValue());
        b.setUpdatedAt(System.currentTimeMillis());
        // categoryName / categoryIconUrl 不从云端获取，保持空值
        // 由调用方根据本地分类信息补填（若已有本地记录则保留原值）
        b.setCategoryName("");
        b.setCategoryIconUrl(null);
        return b;
    }

    // ── 本地 → 云端 ────────────────────────────────────────

    /**
     * 将本地 Budget 实体转换为云端对象。
     *
     * 注意：categoryName 和 categoryIconUrl 不上传（云端不需要这两个字段）。
     * 云端通过 targetId 关联分类，分类名称/图标从分类表读取。
     */
    public static CloudBudget fromLocalBudget(Budget local, String userId) {
        CloudBudget cloud = new CloudBudget();
        cloud.setLocalId(local.getId());
        cloud.setTargetType(local.getTargetType());
        cloud.setTargetId(local.getTargetId());
        cloud.setAmount(local.getAmount());
        cloud.setPeriod(local.getPeriod());
        cloud.setBudgetType(local.getBudgetType());
        cloud.setYear(local.getYear());
        cloud.setMonth(local.getMonth());
        cloud.setStartTime(local.getStartTime());
        cloud.setEndTime(local.getEndTime());
        // categoryName / categoryIconUrl 不上传

        if (userId != null) {
            BmobUser user = new BmobUser();
            user.setObjectId(userId);
            cloud.setOwnerId(user);
        }
        return cloud;
    }
}