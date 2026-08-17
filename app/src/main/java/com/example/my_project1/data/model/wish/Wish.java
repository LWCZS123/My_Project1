package com.example.my_project1.data.model.wish;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

/**
 * Wish - 愿望 / 存钱计划实体
 *
 * 负责：
 * 1. 保存数据
 * 2. 提供 Getter / Setter
 *
 * 不负责：
 * - 状态计算
 * - 金额计算
 * - 日期计算
 * - 数据校验
 * - 同步逻辑
 * - UI 文本
 */
@Entity(tableName = "wishes")
public class Wish {

    /** 进行中 */
    public static final int STATUS_ACTIVE = 0;

    /** 已完成 */
    public static final int STATUS_COMPLETED = 1;

    /** 已放弃 */
    public static final int STATUS_ABANDONED = 2;


    /** 本地数据库 ID */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 云端对象 ID */
    @ColumnInfo(name = "object_id")
    private String objectId;

    /** 用户 ID */
    @ColumnInfo(name = "user_id")
    private String userId;

    /** 愿望名称 */
    @ColumnInfo(name = "wish_name")
    private String wishName;

    /** 愿望图标 URL */
    @ColumnInfo(name = "icon_url")
    private String iconUrl;

    /** 目标金额 */
    @ColumnInfo(name = "target_amount")
    private double targetAmount;

    /** 当前已存金额 */
    @ColumnInfo(name = "current_amount")
    private double currentAmount;

    /** 开始日期 */
    @ColumnInfo(name = "start_date")
    private Date startDate;

    /** 愿望备注 */
    private String remark;

    /** 愿望状态 */
    private int status = STATUS_ACTIVE;

    /** 数据同步状态 */
    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    /** 创建时间 */
    @ColumnInfo(name = "created_at")
    private Date createdAt;

    /** 最后更新时间 */
    @ColumnInfo(name = "updated_at")
    private Date updatedAt;


    // =========================
    // Room 构造
    // =========================

    /** Room 使用的无参构造方法 */
    public Wish() {
    }


    // =========================
    // 业务构造
    // =========================

    /**
     * 创建 Wish 的业务构造方法
     */
    @Ignore
    public Wish(
            String objectId,
            String userId,
            String wishName,
            String iconUrl,
            double targetAmount,
            double currentAmount,
            Date startDate,
            String remark,
            int status
    ) {
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


    /** 获取本地数据库 ID */
    public long getId() {
        return id;
    }

    /** 设置本地数据库 ID */
    public void setId(long id) {
        this.id = id;
    }


    /** 获取云端对象 ID */
    public String getObjectId() {
        return objectId;
    }

    /** 设置云端对象 ID */
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }


    /** 获取用户 ID */
    public String getUserId() {
        return userId;
    }

    /** 设置用户 ID */
    public void setUserId(String userId) {
        this.userId = userId;
    }


    /** 获取愿望名称 */
    public String getWishName() {
        return wishName;
    }

    /** 设置愿望名称 */
    public void setWishName(String wishName) {
        this.wishName = wishName;
    }


    /** 获取愿望图标 URL */
    public String getIconUrl() {
        return iconUrl;
    }

    /** 设置愿望图标 URL */
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }


    /** 获取目标金额 */
    public double getTargetAmount() {
        return targetAmount;
    }

    /** 设置目标金额 */
    public void setTargetAmount(double targetAmount) {
        this.targetAmount = targetAmount;
    }


    /** 获取当前已存金额 */
    public double getCurrentAmount() {
        return currentAmount;
    }

    /** 设置当前已存金额 */
    public void setCurrentAmount(double currentAmount) {
        this.currentAmount = currentAmount;
    }


    /** 获取开始日期 */
    public Date getStartDate() {
        return startDate;
    }

    /** 设置开始日期 */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }


    /** 获取愿望备注 */
    public String getRemark() {
        return remark;
    }

    /** 设置愿望备注 */
    public void setRemark(String remark) {
        this.remark = remark;
    }


    /** 获取愿望状态 */
    public int getStatus() {
        return status;
    }

    /** 设置愿望状态 */
    public void setStatus(int status) {
        this.status = status;
    }


    /** 获取数据同步状态 */
    public SyncState getSyncState() {
        return syncState;
    }

    /** 设置数据同步状态 */
    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }


    /** 获取创建时间 */
    public Date getCreatedAt() {
        return createdAt;
    }

    /** 设置创建时间 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }


    /** 获取最后更新时间 */
    public Date getUpdatedAt() {
        return updatedAt;
    }

    /** 设置最后更新时间 */
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }


    /** 输出 Wish 对象信息 */
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
                ", syncState=" + syncState +
                '}';
    }
}