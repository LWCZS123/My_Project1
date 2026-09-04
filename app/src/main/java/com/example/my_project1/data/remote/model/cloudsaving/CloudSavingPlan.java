package com.example.my_project1.data.remote.model.cloudsaving;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobDate;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端 SavingPlan 实体（Bmob）
 */
public class CloudSavingPlan extends BmobObject {

    private BmobPointer user;      // 用户指针
    private String name;           // 计划名称
    private String iconUrl;        // 图标URL
    private String iconColor;      // 图标背景颜色
    private Integer type;          // 计划类型
    private Double targetAmount;   // 目标金额
    private Double currentAmount;  // 当前金额
    private BmobDate startDate;    // 开始日期
    private BmobDate endDate;      // 结束日期
    private Double firstPeriodAmount; // 首期金额
    private Double incrementAmount;   // 递增金额
    private Double periodAmount;      // 定额计划金额
    private String periodType;        // 定额周期类型
    private Integer duration;         // 持续时长
    private Double initialAmount;     // 初始金额
    private Boolean enableTransfer;   // 是否开启转账记录
    private String remark;            // 备注
    private Integer status;           // 状态
    /** 是否已归档 - 修复同步缺失的关键字段 */
    private Boolean isArchived;
    private String clientKey;         // 客户端幂等键

    public CloudSavingPlan() {}

    public CloudSavingPlan(String objectId) {
        setObjectId(objectId);
    }

    // Getters and Setters
    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getIconColor() { return iconColor; }
    public void setIconColor(String iconColor) { this.iconColor = iconColor; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public Double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(Double targetAmount) { this.targetAmount = targetAmount; }
    public Double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(Double currentAmount) { this.currentAmount = currentAmount; }
    public BmobDate getStartDate() { return startDate; }
    public void setStartDate(BmobDate startDate) { this.startDate = startDate; }
    public BmobDate getEndDate() { return endDate; }
    public void setEndDate(BmobDate endDate) { this.endDate = endDate; }
    public Double getFirstPeriodAmount() { return firstPeriodAmount; }
    public void setFirstPeriodAmount(Double firstPeriodAmount) { this.firstPeriodAmount = firstPeriodAmount; }
    public Double getIncrementAmount() { return incrementAmount; }
    public void setIncrementAmount(Double incrementAmount) { this.incrementAmount = incrementAmount; }
    public Double getPeriodAmount() { return periodAmount; }
    public void setPeriodAmount(Double periodAmount) { this.periodAmount = periodAmount; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Double getInitialAmount() { return initialAmount; }
    public void setInitialAmount(Double initialAmount) { this.initialAmount = initialAmount; }
    public Boolean getEnableTransfer() { return enableTransfer; }
    public void setEnableTransfer(Boolean enableTransfer) { this.enableTransfer = enableTransfer; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean isArchived) { this.isArchived = isArchived; }
    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }

    /**
     * 本地实体转换为云端实体
     */
    public static CloudSavingPlan fromLocal(SavingPlan local) {
        if (local == null) return null;
        CloudSavingPlan cloud = new CloudSavingPlan();
        if (local.getObjectId() != null) cloud.setObjectId(local.getObjectId());
        cloud.setName(local.getName());
        cloud.setIconUrl(local.getIconUrl());
        cloud.setIconColor(local.getIconColor());
        cloud.setType(local.getType());
        cloud.setTargetAmount(local.getTargetAmount());
        cloud.setCurrentAmount(local.getCurrentAmount());
        if (local.getStartDate() != null) cloud.setStartDate(new BmobDate(local.getStartDate()));
        if (local.getEndDate() != null) cloud.setEndDate(new BmobDate(local.getEndDate()));
        cloud.setFirstPeriodAmount(local.getFirstPeriodAmount());
        cloud.setIncrementAmount(local.getIncrementAmount());
        cloud.setPeriodAmount(local.getPeriodAmount());
        cloud.setPeriodType(local.getPeriodType());
        cloud.setDuration(local.getDuration());
        cloud.setInitialAmount(local.getInitialAmount());
        cloud.setEnableTransfer(local.isEnableTransfer());
        cloud.setRemark(local.getRemark());
        cloud.setStatus(local.getStatus());
        cloud.setIsArchived(local.isArchived());
        if (local.getUserId() != null) cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        return cloud;
    }

    /**
     * 云端实体转换为本地实体
     */
    public SavingPlan toLocalEntity() {
        SavingPlan local = new SavingPlan();
        local.setObjectId(getObjectId());
        if (user != null) local.setUserId(user.getObjectId());
        local.setName(name);
        local.setIconUrl(iconUrl);
        local.setIconColor(iconColor);
        local.setType(type != null ? type : 0);
        local.setTargetAmount(targetAmount != null ? targetAmount : 0d);
        local.setCurrentAmount(currentAmount != null ? currentAmount : 0d);
        if (startDate != null) local.setStartDate(DateConvertUtil.safeConvertToDate(startDate.getDate()));
        if (endDate != null) local.setEndDate(DateConvertUtil.safeConvertToDate(endDate.getDate()));
        local.setFirstPeriodAmount(firstPeriodAmount != null ? firstPeriodAmount : 0d);
        local.setIncrementAmount(incrementAmount != null ? incrementAmount : 0d);
        local.setPeriodAmount(periodAmount != null ? periodAmount : 0d);
        local.setPeriodType(periodType);
        local.setDuration(duration != null ? duration : 0);
        local.setInitialAmount(initialAmount != null ? initialAmount : 0d);
        local.setEnableTransfer(enableTransfer != null ? enableTransfer : true);
        local.setRemark(remark);
        local.setStatus(status != null ? status : 0);
        local.setArchived(isArchived != null ? isArchived : false);
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));
        return local;
    }
}
