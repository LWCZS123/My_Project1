package com.example.my_project1.data.model.saving;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * 存钱步骤/卡片实体类
 * 用于展示分阶段的存钱任务（如52周、365天）
 */
@Entity(
        tableName = "saving_steps",
        foreignKeys = @ForeignKey(
                entity = SavingPlan.class,
                parentColumns = "id",
                childColumns = "plan_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("plan_id")
)
public class SavingStep {

    /** 本地主键ID */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 关联的存钱计划本地ID */
    @ColumnInfo(name = "plan_id")
    private long planId;

    /** 该步骤建议存入的金额 */
    private double amount;

    /** 该步骤的预定日期 */
    @ColumnInfo(name = "due_date")
    private Date dueDate;

    /** 是否已完成 */
    @ColumnInfo(name = "is_completed")
    private boolean isCompleted = false;

    /** 关联的存入记录本地ID */
    @ColumnInfo(name = "record_id")
    private long recordId = -1;

    public SavingStep() {}

    @Ignore
    public SavingStep(long planId, double amount, Date dueDate) {
        this.planId = planId;
        this.amount = amount;
        this.dueDate = dueDate;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getPlanId() { return planId; }
    public void setPlanId(long planId) { this.planId = planId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    public long getRecordId() { return recordId; }
    public void setRecordId(long recordId) { this.recordId = recordId; }
}
