package com.example.my_project1.data.model.saving;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

/**
 * 存钱记录实体类
 * 关联具体的存钱计划，记录单次存入的详情
 */
@Entity(
        tableName = "saving_records",
        foreignKeys = @ForeignKey(
                entity = SavingPlan.class,
                parentColumns = "id",
                childColumns = "plan_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("plan_id")
)
public class SavingRecord {

    /** 本地主键ID */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** Bmob云端对象ID */
    @ColumnInfo(name = "object_id")
    private String objectId;

    /** 关联的存钱计划本地ID */
    @ColumnInfo(name = "plan_id")
    private long planId;

    /** 用户ID */
    @ColumnInfo(name = "user_id")
    private String userId;

    /** 存入金额 */
    private double amount;

    /** 备注信息 */
    private String note;

    /** 记录日期 */
    @ColumnInfo(name = "record_date")
    private Date recordDate;

    /** 关联账单ID */
    @ColumnInfo(name = "linked_bill_id", defaultValue = "-1")
    private long linkedBillId = -1;

    /** 关联账单云端 ID */
    @ColumnInfo(name = "linked_bill_object_id")
    private String linkedBillObjectId;

    /** 关联的步骤索引（用于动态卡片匹配） */
    @ColumnInfo(name = "step_index", defaultValue = "-1")
    private int stepIndex = -1;

    /** 同步状态 */
    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    /** 创建时间 */
    @ColumnInfo(name = "created_at")
    private Date createdAt;

    /** 更新时间 */
    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    public SavingRecord() {}

    @Ignore
    public SavingRecord(long planId, String userId, double amount, Date recordDate) {
        this.planId = planId;
        this.userId = userId;
        this.amount = amount;
        this.recordDate = recordDate;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public long getPlanId() { return planId; }
    public void setPlanId(long planId) { this.planId = planId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Date getRecordDate() { return recordDate; }
    public void setRecordDate(Date recordDate) { this.recordDate = recordDate; }
    public long getLinkedBillId() { return linkedBillId; }
    public void setLinkedBillId(long linkedBillId) { this.linkedBillId = linkedBillId; }
    public String getLinkedBillObjectId() { return linkedBillObjectId; }
    public void setLinkedBillObjectId(String linkedBillObjectId) { this.linkedBillObjectId = linkedBillObjectId; }
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    public SyncState getSyncState() { return syncState; }
    public void setSyncState(SyncState syncState) { this.syncState = syncState; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
