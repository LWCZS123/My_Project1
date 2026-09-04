package com.example.my_project1.ui.viewmodel.saving;

import java.util.Date;

/**
 * 存钱卡片 UI 模型
 * 仅用于 RecyclerView 展示，不持久化到数据库
 */
public class SavingCardUiModel {
    private int stepIndex;      // 步骤索引（从0开始）
    private double amount;      // 本期应存金额
    private Date dueDate;       // 预计日期
    private boolean completed;  // 是否已完成
    private long recordId;      // 关联的 SavingRecord 本地 ID
    private boolean isTransfer; // 是否记录了转账

    public SavingCardUiModel(int stepIndex, double amount, Date dueDate, boolean completed, long recordId, boolean isTransfer) {
        this.stepIndex = stepIndex;
        this.amount = amount;
        this.dueDate = dueDate;
        this.completed = completed;
        this.recordId = recordId;
        this.isTransfer = isTransfer;
    }

    public int getStepIndex() { return stepIndex; }
    public double getAmount() { return amount; }
    public Date getDueDate() { return dueDate; }
    public boolean isCompleted() { return completed; }
    public long getRecordId() { return recordId; }
    public boolean isTransfer() { return isTransfer; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavingCardUiModel that = (SavingCardUiModel) o;
        return stepIndex == that.stepIndex &&
                Double.compare(that.amount, amount) == 0 &&
                completed == that.completed &&
                recordId == that.recordId &&
                isTransfer == that.isTransfer &&
                java.util.Objects.equals(dueDate, that.dueDate);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(stepIndex, amount, dueDate, completed, recordId, isTransfer);
    }
}
