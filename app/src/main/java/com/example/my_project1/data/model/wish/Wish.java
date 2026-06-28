package com.example.my_project1.data.model.wish;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

/**
 * Wish - 愿望/存钱计划实体（优化版）
 * -------------------------------------------------------
 * 优化内容：
 *  1. 修复状态计算逻辑：getRealStatus() 动态计算实时状态
 *  2. 自动状态回滚：修改金额时自动处理状态变化
 *  3. 安全的日期处理：防止 null 指针异常
 *  4. 增强防御编程：所有计算都有 null 检查
 */
@Entity(tableName = "wishes")
public class Wish {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "object_id")
    private String objectId;          // Bmob 云端唯一 ID

    @ColumnInfo(name = "user_id")
    private String userId;            // 所属用户

    @ColumnInfo(name = "wish_name")
    private String wishName;          // 愿望名称

    @ColumnInfo(name = "icon_url")
    private String iconUrl;           // 愿望图标 URL

    @ColumnInfo(name = "target_amount")
    private double targetAmount;      // 目标金额

    @ColumnInfo(name = "current_amount")
    private double currentAmount;     // 当前已存金额

    @ColumnInfo(name = "start_date")
    private Date startDate;           // 开始日期

    private String remark;            // 备注

    /**
     * 状态: 0=进行中, 1=已完成, 2=已放弃
     * ⚠️ 注意：这只是缓存值，实际状态由 getRealStatus() 动态计算
     */
    private int status;

    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    // ================== 构造 ==================

    public Wish() {}

    @Ignore
    public Wish(String objectId, String userId, String wishName, String iconUrl,
                double targetAmount, double currentAmount, Date startDate,
                String remark, int status) {
        this.objectId = objectId;
        this.userId = userId;
        this.wishName = wishName;
        this.iconUrl = iconUrl;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.startDate = startDate;
        this.remark = remark;
        this.status = status;
    }

    // ================== Getter / Setter ==================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWishName() { return wishName; }
    public void setWishName(String wishName) { this.wishName = wishName; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public double getTargetAmount() { return targetAmount; }

    /**
     * ✅ 修改目标金额时，自动处理状态回滚
     * 如果新金额小于已存金额，且当前状态是"已完成"，则回滚为"进行中"
     */
    public void setTargetAmount(double targetAmount) {
        this.targetAmount = Math.max(targetAmount, 0); // 防止负数

        // 自动回滚状态：如果金额降低导致不再完成，自动改为进行中
        if (this.status == 1 && this.currentAmount < this.targetAmount) {
            this.status = 0;
        }
    }

    public double getCurrentAmount() { return currentAmount; }

    /**
     * ✅ 修改当前金额时，自动检查是否完成
     */
    public void setCurrentAmount(double currentAmount) {
        this.currentAmount = Math.max(currentAmount, 0); // 防止负数

        // 自动更新状态：如果已完成，标记为完成
        if (this.status != 2 && this.currentAmount >= this.targetAmount && this.targetAmount > 0) {
            this.status = 1;
        }
    }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    /**
     * ⚠️ 注意：直接使用 getStatus() 获取的是缓存值
     * 应该使用 getRealStatus() 获取实时计算状态
     */
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public SyncState getSyncState() { return syncState; }
    public void setSyncState(SyncState syncState) { this.syncState = syncState; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    // ================== 便捷计算 ==================

    /**
     * ✅ 计算存钱进度百分比 (0~100)
     * 安全的防 null 计算
     */
    public int getProgressPercent() {
        if (targetAmount <= 0) return 0;
        int percent = (int) ((currentAmount / targetAmount) * 100);
        return Math.min(Math.max(percent, 0), 100); // 确保在 0-100 之间
    }

    /**
     * ✅ 剩余金额（防负数）
     */
    public double getRemainingAmount() {
        return Math.max(targetAmount - currentAmount, 0);
    }

    /**
     * ✅ 是否已完成（基于当前金额的实时判断）
     * 不依赖 status 字段的缓存值
     */
    public boolean isCompleted() {
        return currentAmount >= targetAmount && targetAmount > 0;
    }

    /**
     * ✅ 获取实时状态（核心方法，每次都重新计算）
     *
     * 状态优先级：
     * 1. 如果标记为"已放弃" (status==2)，则保持放弃状态
     * 2. 如果当前金额 >= 目标金额，则为"已完成" (status=1)
     * 3. 否则为"进行中" (status=0)
     *
     * @return 实时计算的状态：0=进行中，1=已完成，2=已放弃
     */
    public int getRealStatus() {
        // 优先保持"已放弃"状态
        if (status == 2) {
            return 2;
        }

        // 检查是否完成
        if (isCompleted()) {
            return 1;
        }

        // 默认进行中
        return 0;
    }

    /**
     * ✅ 获取状态文本描述
     */
    public String getStatusText() {
        switch (getRealStatus()) {
            case 0:
                return "🚀 进行中";
            case 1:
                return "🎉 已完成";
            case 2:
                return "❄️ 已放弃";
            default:
                return "未知";
        }
    }

    /**
     * ✅ 计算距离开始日期已经过的天数
     * 安全处理 null 日期
     */
    public long getDaysElapsed() {
        if (startDate == null) {
            return 0;
        }
        try {
            long now = System.currentTimeMillis();
            long startMs = startDate.getTime();
            long diffMs = now - startMs;
            long days = diffMs / (1000 * 60 * 60 * 24);
            return Math.max(days, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "Wish{" +
                "id=" + id +
                ", objectId='" + objectId + '\'' +
                ", userId='" + userId + '\'' +
                ", wishName='" + wishName + '\'' +
                ", targetAmount=" + targetAmount +
                ", currentAmount=" + currentAmount +
                ", status=" + status +
                ", realStatus=" + getRealStatus() +
                ", isCompleted=" + isCompleted() +
                ", syncState=" + syncState +
                '}';
    }
}