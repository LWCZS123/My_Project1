package com.example.my_project1.data.model.saving;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

/**
 * 存钱计划实体类
 * 用于存储用户的存钱目标、进度及配置信息
 */
@Entity(tableName = "saving_plans")
public class SavingPlan {

    /** 定额存钱类型 */
    public static final int TYPE_FIXED = 0;
    /** 灵活存钱类型 */
    public static final int TYPE_FLEXIBLE = 1;
    /** 52周存钱类型 */
    public static final int TYPE_52WEEK = 2;
    /** 365天存钱类型 */
    public static final int TYPE_365DAY = 3;

    /** 进行中状态 */
    public static final int STATUS_ACTIVE = 0;
    /** 已完成状态 */
    public static final int STATUS_COMPLETED = 1;

    /** 本地主键ID */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** Bmob云端对象ID */
    @ColumnInfo(name = "object_id")
    private String objectId;

    /** 用户ID */
    @ColumnInfo(name = "user_id")
    private String userId;

    /** 计划名称 */
    private String name;

    /** 图标地址 */
    @ColumnInfo(name = "icon_url")
    private String iconUrl;

    /** 计划类型 */
    private int type;

    /** 目标金额 */
    @ColumnInfo(name = "target_amount")
    private double targetAmount;

    /** 当前已存金额 */
    @ColumnInfo(name = "current_amount")
    private double currentAmount;

    /** 初始存入金额 */
    @ColumnInfo(name = "initial_amount", defaultValue = "0")
    private double initialAmount;

    /** 开始日期 */
    @ColumnInfo(name = "start_date")
    private Date startDate;

    /** 结束日期 */
    @ColumnInfo(name = "end_date")
    private Date endDate;

    /** 首期存入金额（适用于52周等） */
    @ColumnInfo(name = "first_period_amount")
    private double firstPeriodAmount;

    /** 递增金额（适用于52周等） */
    @ColumnInfo(name = "increment_amount")
    private double incrementAmount;

    /** 周期存入金额（适用于定额） */
    @ColumnInfo(name = "period_amount")
    private double periodAmount;
    
    /** 周期类型（每周/每月等） */
    @ColumnInfo(name = "period_type")
    private String periodType;

    /** 持续时长（周数/天数等） */
    private int duration;

    /** 图标背景颜色 */
    @ColumnInfo(name = "icon_color")
    private String iconColor;

    /** 备注信息 */
    private String remark;

    /** 计划状态 */
    private int status = STATUS_ACTIVE;

    /** 是否已归档 */
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    private boolean isArchived = false;

    /** 是否打卡后记录转账 */
    @ColumnInfo(name = "enable_transfer", defaultValue = "1")
    private boolean enableTransfer = true;

    /** 同步状态 */
    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    /** 创建时间 */
    @ColumnInfo(name = "created_at")
    private Date createdAt;

    /** 更新时间 */
    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    public SavingPlan() {}

    @Ignore
    public SavingPlan(String userId, String name, int type, double targetAmount, Date startDate) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.targetAmount = targetAmount;
        this.startDate = startDate;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }
    public double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(double currentAmount) { this.currentAmount = currentAmount; }
    public double getInitialAmount() { return initialAmount; }
    public void setInitialAmount(double initialAmount) { this.initialAmount = initialAmount; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
    public double getFirstPeriodAmount() { return firstPeriodAmount; }
    public void setFirstPeriodAmount(double firstPeriodAmount) { this.firstPeriodAmount = firstPeriodAmount; }
    public double getIncrementAmount() { return incrementAmount; }
    public void setIncrementAmount(double incrementAmount) { this.incrementAmount = incrementAmount; }
    public double getPeriodAmount() { return periodAmount; }
    public void setPeriodAmount(double periodAmount) { this.periodAmount = periodAmount; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public String getIconColor() { return iconColor; }
    public void setIconColor(String iconColor) { this.iconColor = iconColor; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }
    public boolean isEnableTransfer() { return enableTransfer; }
    public void setEnableTransfer(boolean enableTransfer) { this.enableTransfer = enableTransfer; }
    public SyncState getSyncState() { return syncState; }
    public void setSyncState(SyncState syncState) { this.syncState = syncState; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavingPlan that = (SavingPlan) o;
        return id == that.id &&
                Double.compare(that.targetAmount, targetAmount) == 0 &&
                Double.compare(that.currentAmount, currentAmount) == 0 &&
                Double.compare(that.initialAmount, initialAmount) == 0 &&
                type == that.type &&
                status == that.status &&
                isArchived == that.isArchived &&
                enableTransfer == that.enableTransfer &&
                java.util.Objects.equals(name, that.name) &&
                java.util.Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name, type, targetAmount, currentAmount, initialAmount, status, isArchived, enableTransfer, updatedAt);
    }
}
