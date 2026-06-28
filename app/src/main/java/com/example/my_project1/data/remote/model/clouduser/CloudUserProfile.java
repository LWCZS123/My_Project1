package com.example.my_project1.data.remote.model.clouduser;

import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.utils.DateConvertUtil;

import java.util.Date;

import cn.bmob.v3.BmobUser;

/**
 * CloudUserProfile - 云端用户信息模型
 * -------------------------------------------------------
 * 对应 Bmob User 表的扩展字段
 */
public class CloudUserProfile extends BmobUser {

    private static final String TAG = "CloudUserProfile";

    private String avatarUrl;           // 用户头像URL
    private Integer gender;             // 性别 0=未设置 1=男 2=女
    private Date birthday;              // 出生年月
    private String school;              // 学校
    private String signature;           // 个性签名
    private String backgroundUrl;       // 用户背景图URL
    private Integer billDays;           // 记录账单的天数
    private Integer billCount;          // 记录账单的总数

    // ==================== Getter & Setter ====================

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Date getBirthday() {
        return birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
    }

    public Integer getBillDays() {
        return billDays;
    }

    public void setBillDays(Integer billDays) {
        this.billDays = billDays;
    }

    public Integer getBillCount() {
        return billCount;
    }

    public void setBillCount(Integer billCount) {
        this.billCount = billCount;
    }

    // ==================== 转换方法 ====================

    /**
     * 云端 → 本地
     */
    public UserProfile toLocalEntity() {
        UserProfile local = new UserProfile();

        local.setUserId(getObjectId());
        local.setUsername(getUsername());
        local.setEmail(getEmail());
        local.setAvatarUrl(avatarUrl);
        local.setGender(gender != null ? gender : 0);
        local.setBirthday(birthday);
        local.setSchool(school);
        local.setSignature(signature);
        local.setBackgroundUrl(backgroundUrl);
        local.setBillDays(billDays != null ? billDays : 0);
        local.setBillCount(billCount != null ? billCount : 0);
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));

        return local;
    }

    /**
     * 本地 → 云端
     */
    public static CloudUserProfile fromLocal(UserProfile local) {
        CloudUserProfile cloud = new CloudUserProfile();

        if (local.getUserId() != null) {
            cloud.setObjectId(local.getUserId());
        }
        cloud.setUsername(local.getUsername());
        cloud.setEmail(local.getEmail());
        cloud.setAvatarUrl(local.getAvatarUrl());
        cloud.setGender(local.getGender());
        cloud.setBirthday(local.getBirthday());
        cloud.setSchool(local.getSchool());
        cloud.setSignature(local.getSignature());
        cloud.setBackgroundUrl(local.getBackgroundUrl());
        cloud.setBillDays(local.getBillDays());
        cloud.setBillCount(local.getBillCount());

        return cloud;
    }

    @Override
    public String toString() {
        return "CloudUserProfile{" +
                "objectId='" + getObjectId() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", gender=" + gender +
                ", school='" + school + '\'' +
                ", billDays=" + billDays +
                ", billCount=" + billCount +
                '}';
    }
}