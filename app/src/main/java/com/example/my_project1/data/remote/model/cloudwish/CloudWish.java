package com.example.my_project1.data.remote.model.cloudwish;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobDate;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端 Wish 实体（Bmob）
 */
public class CloudWish extends BmobObject {

    // ==================== 字段 ====================

    private BmobPointer user;      // 用户指针
    private String wishName;       // 愿望名称
    private String iconUrl;        // 图标
    private Double targetAmount;  // 目标金额
    private Double currentAmount; // 当前金额
    private BmobDate startDate;   // 开始时间
    private String remark;        // 备注
    private Integer status;       // 状态


    public CloudWish() {}

    public CloudWish(String objectId) {
        setObjectId(objectId);
    }

    // ==================== Getter / Setter ====================

    public BmobPointer getUser() {
        return user;
    }

    public void setUser(BmobPointer user) {
        this.user = user;
    }

    public String getWishName() {
        return wishName;
    }

    public void setWishName(String wishName) {
        this.wishName = wishName;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public Double getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(Double targetAmount) {
        this.targetAmount = targetAmount;
    }

    public Double getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(Double currentAmount) {
        this.currentAmount = currentAmount;
    }

    public BmobDate getStartDate() {
        return startDate;
    }

    public void setStartDate(BmobDate startDate) {
        this.startDate = startDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    // ==================== 本地 → 云端 ====================

    /**
     * 本地 Wish 转换为云端 CloudWish
     */
    public static CloudWish fromLocal(Wish local) {
        if (local == null) return null;

        CloudWish cloud = new CloudWish();

        // objectId（用于更新）
        if (local.getObjectId() != null) {
            cloud.setObjectId(local.getObjectId());
        }

        cloud.setWishName(local.getWishName());
        cloud.setIconUrl(local.getIconUrl());
        cloud.setTargetAmount(local.getTargetAmount());
        cloud.setCurrentAmount(local.getCurrentAmount());
        cloud.setRemark(local.getRemark());
        cloud.setStatus(local.getStatus());

        // 日期转换：本地 Date -> BmobDate
        if (local.getStartDate() != null) {
            cloud.setStartDate(new BmobDate(local.getStartDate()));
        }

        // 用户指针
        if (local.getUserId() != null) {
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        }

        return cloud;
    }

    // ==================== 云端 → 本地 ====================

    /**
     * 云端 CloudWish 转换为本地 Wish（已修复日期转换逻辑）
     */
    public Wish toLocalEntity() {
        Wish local = new Wish();

        local.setObjectId(getObjectId());

        // userId 处理
        if (user != null) {
            local.setUserId(user.getObjectId());
        }

        local.setWishName(wishName);
        local.setIconUrl(iconUrl);

        // 金额防空
        local.setTargetAmount(targetAmount != null ? targetAmount : 0d);
        local.setCurrentAmount(currentAmount != null ? currentAmount : 0d);

        local.setRemark(remark);
        local.setStatus(status != null ? status : 0);

        // 🔥 关键修复 1：转换 startDate
        // startDate 是 BmobDate 对象，需要调用 getDate() 获取字符串后再转换
        if (startDate != null) {
            local.setStartDate(DateConvertUtil.safeConvertToDate(startDate.getDate()));
        }

        // 🔥 关键修复 2：转换系统时间字段
        // getCreatedAt() 和 getUpdatedAt() 返回的是 String，可以直接转换
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));

        return local;
    }
}