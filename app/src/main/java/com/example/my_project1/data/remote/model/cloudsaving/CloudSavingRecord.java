package com.example.my_project1.data.remote.model.cloudsaving;

import com.example.my_project1.data.model.saving.SavingRecord;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobDate;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端 SavingRecord 实体（Bmob）
 */
public class CloudSavingRecord extends BmobObject {

    private BmobPointer user;      // 用户指针
    private BmobPointer plan;      // 计划指针
    private String planObjectId;   // 计划云端ID
    private Double amount;         // 存入金额
    private String note;           // 备注
    private BmobDate recordDate;   // 记录日期
    private String linkedBillObjectId; // 关联账单ID
    private Integer stepIndex;     // 步骤索引
    private String clientKey;      // 客户端幂等键

    public CloudSavingRecord() {}

    // Getters and Setters
    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }
    public BmobPointer getPlan() { return plan; }
    public void setPlan(BmobPointer plan) { this.plan = plan; }
    public String getPlanObjectId() { return planObjectId; }
    public void setPlanObjectId(String planObjectId) { this.planObjectId = planObjectId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public BmobDate getRecordDate() { return recordDate; }
    public void setRecordDate(BmobDate recordDate) { this.recordDate = recordDate; }
    public String getLinkedBillObjectId() { return linkedBillObjectId; }
    public void setLinkedBillObjectId(String linkedBillObjectId) { this.linkedBillObjectId = linkedBillObjectId; }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }

    /**
     * 本地实体转换为云端实体
     */
    public static CloudSavingRecord fromLocal(SavingRecord local) {
        if (local == null) return null;
        CloudSavingRecord cloud = new CloudSavingRecord();
        if (local.getObjectId() != null) cloud.setObjectId(local.getObjectId());
        cloud.setAmount(local.getAmount());
        cloud.setNote(local.getNote());
        cloud.setStepIndex(local.getStepIndex());
        cloud.setLinkedBillObjectId(local.getLinkedBillObjectId());
        if (local.getRecordDate() != null) cloud.setRecordDate(new BmobDate(local.getRecordDate()));
        if (local.getUserId() != null) cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        // Note: planObjectId should be set by the caller before sync
        return cloud;
    }

    /**
     * 云端实体转换为本地实体
     */
    public SavingRecord toLocalEntity() {
        SavingRecord local = new SavingRecord();
        local.setObjectId(getObjectId());
        if (user != null) local.setUserId(user.getObjectId());
        local.setAmount(amount != null ? amount : 0d);
        local.setNote(note);
        local.setStepIndex(stepIndex != null ? stepIndex : -1);
        local.setLinkedBillObjectId(linkedBillObjectId);
        if (recordDate != null) local.setRecordDate(DateConvertUtil.safeConvertToDate(recordDate.getDate()));
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));
        return local;
    }
}
