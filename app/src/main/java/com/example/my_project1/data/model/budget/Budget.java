package com.example.my_project1.data.model.budget;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Budget 预算实体
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  独立存储设计                                                 │
 * │  月预算与年预算通过 budgetType + year + month 唯一标识，       │
 * │  互不覆盖，例如：                                             │
 * │    2026年预算   → budgetType=YEAR,  year=2026, month=0      │
 * │    2026年3月预算 → budgetType=MONTH, year=2026, month=3     │
 * └─────────────────────────────────────────────────────────────┘
 *
 * targetType      : 1 = 分类预算  2 = 总预算
 * period          : 0=日 1=周 2=月 3=年
 * budgetType      : "MONTH" | "YEAR"
 * year            : 所属年份（2026）
 * month           : 所属月份 1-12；年预算时为 0
 * categoryName    : 分类名称快照（仅分类预算有值），本地展示用，不上传云端
 * categoryIconUrl : 分类图标 URL 快照，本地展示用，不上传云端
 *
 * ── 为什么把 categoryName / categoryIconUrl 存到 Budget 表 ────────
 *   分类预算列表展示分类名称和图标时，若每次都 JOIN 分类表，
 *   需要额外 @Relation 或多表 Query，增加复杂度且难以维护。
 *   将名称/图标作为"快照"存入 Budget，展示时直接读取，简单高效。
 *   分类信息变更时（如用户改了分类名称），在保存/更新预算时一并刷新即可。
 *   这两个字段仅用于本地展示，不参与云端同步（CloudBudget 不含此字段）。
 */
@Entity(tableName = "budgets")
public class Budget {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "target_type")
    private int targetType;

    /**
     * 分类 Bmob objectId（String）。
     * 分类预算时存云端 objectId；总预算时为 null 或空串。
     */
    @ColumnInfo(name = "target_id")
    private String targetId;

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "period")
    private int period;

    /** "MONTH" 或 "YEAR"，区分月/年预算独立记录 */
    @ColumnInfo(name = "budget_type")
    private String budgetType;

    @ColumnInfo(name = "year")
    private int year;

    /** 月份 1-12；年预算填 0 */
    @ColumnInfo(name = "month")
    private int month;

    @ColumnInfo(name = "start_time")
    private long startTime;

    @ColumnInfo(name = "end_time")
    private long endTime;

    @ColumnInfo(name = "owner_id")
    private String ownerId;

    @ColumnInfo(name = "cloud_id")
    private String cloudId;

    @ColumnInfo(name = "sync_state")
    private int syncState;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    /**
     * 分类名称快照（仅 targetType=TARGET_CATEGORY 时有值）。
     * 本地展示用，不上传云端；分类名称变更时随预算更新一并刷新。
     * defaultValue="" 避免旧数据库迁移时出现 null。
     */
    @ColumnInfo(name = "category_name", defaultValue = "")
    private String categoryName;

    /**
     * 分类图标 URL 快照（仅分类预算时有值，可为 null）。
     * 本地展示用，不上传云端。
     */
    @ColumnInfo(name = "category_icon_url")
    private String categoryIconUrl;

    // ── 常量 ──────────────────────────────────────────────

    public static final int TARGET_CATEGORY = 1;
    public static final int TARGET_TOTAL    = 2;

    public static final int PERIOD_DAY   = 0;
    public static final int PERIOD_WEEK  = 1;
    public static final int PERIOD_MONTH = 2;
    public static final int PERIOD_YEAR  = 3;

    public static final String TYPE_MONTH = "MONTH";
    public static final String TYPE_YEAR  = "YEAR";

    // ── Getters / Setters ──────────────────────────────────

    public int getId()                         { return id; }
    public void setId(int id)                  { this.id = id; }

    public int getTargetType()                 { return targetType; }
    public void setTargetType(int t)           { this.targetType = t; }

    public String getTargetId()                { return targetId; }
    public void setTargetId(String id)         { this.targetId = id; }

    public double getAmount()                  { return amount; }
    public void setAmount(double v)            { this.amount = v; }

    public int getPeriod()                     { return period; }
    public void setPeriod(int p)               { this.period = p; }

    public String getBudgetType()              { return budgetType; }
    public void setBudgetType(String t)        { this.budgetType = t; }

    public int getYear()                       { return year; }
    public void setYear(int y)                 { this.year = y; }

    public int getMonth()                      { return month; }
    public void setMonth(int m)                { this.month = m; }

    public long getStartTime()                 { return startTime; }
    public void setStartTime(long t)           { this.startTime = t; }

    public long getEndTime()                   { return endTime; }
    public void setEndTime(long t)             { this.endTime = t; }

    public String getOwnerId()                 { return ownerId; }
    public void setOwnerId(String id)          { this.ownerId = id; }

    public String getCloudId()                 { return cloudId; }
    public void setCloudId(String id)          { this.cloudId = id; }

    public int getSyncState()                  { return syncState; }
    public void setSyncState(int s)            { this.syncState = s; }

    public long getUpdatedAt()                 { return updatedAt; }
    public void setUpdatedAt(long t)           { this.updatedAt = t; }

    /** 分类名称，不为 null（返回空串兜底） */
    public String getCategoryName()            { return categoryName != null ? categoryName : ""; }
    public void setCategoryName(String name)   { this.categoryName = name; }

    /** 分类图标 URL，可为 null */
    public String getCategoryIconUrl()         { return categoryIconUrl; }
    public void setCategoryIconUrl(String url) { this.categoryIconUrl = url; }

    // ── 工具方法 ───────────────────────────────────────────

    public static String getPeriodLabel(int period) {
        switch (period) {
            case PERIOD_DAY:   return "日";
            case PERIOD_WEEK:  return "周";
            case PERIOD_MONTH: return "月";
            case PERIOD_YEAR:  return "年";
            default:           return "月";
        }
    }

    public boolean isMonthType()      { return TYPE_MONTH.equals(budgetType); }
    public boolean isYearType()       { return TYPE_YEAR.equals(budgetType); }
    public boolean isCategoryBudget() { return targetType == TARGET_CATEGORY; }
    public boolean isTotalBudget()    { return targetType == TARGET_TOTAL; }

    @Override
    public String toString() {
        return "Budget{id=" + id
                + ", budgetType=" + budgetType
                + ", targetType=" + targetType
                + ", category='" + categoryName + "'"
                + ", amount=" + amount
                + ", period=" + getPeriodLabel(period)
                + ", year=" + year + ", month=" + month
                + ", cloudId=" + cloudId
                + ", syncState=" + syncState + "}";
    }
}