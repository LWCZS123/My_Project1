package com.example.my_project1.data.model.user;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

/**
 * UserProfile - 用户信息实体 (优化版)
 * -------------------------------------------------------
 * ✅ 添加 syncState 字段支持离线编辑
 * ✅ 与 Bill 实体保持一致的同步机制
 */
@Entity(tableName = "user_profiles")
public class UserProfile {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "user_id")
    private String userId;                  // 用户ID（关联Bmob User的objectId）

    @ColumnInfo(name = "avatar_url")
    private String avatarUrl;               // 用户头像URL

    @ColumnInfo(name = "username")
    private String username;                // 用户名

    @ColumnInfo(name = "email")
    private String email;                   // 邮箱

    @ColumnInfo(name = "gender")
    private Integer gender;                 // 性别 0=未设置 1=男 2=女

    @ColumnInfo(name = "birthday")
    private Date birthday;                  // 出生年月

    @ColumnInfo(name = "school")
    private String school;                  // 学校

    @ColumnInfo(name = "signature")
    private String signature;               // 个性签名

    @ColumnInfo(name = "background_url")
    private String backgroundUrl;           // 用户背景图URL

    @ColumnInfo(name = "bill_days")
    private Integer billDays;               // 记录账单的天数

    @ColumnInfo(name = "bill_count")
    private Integer billCount;              // 记录账单的总数

    //同步状态字段
    @ColumnInfo(name = "sync_state")
    private SyncState syncState;            // 同步状态

    @ColumnInfo(name = "created_at")
    private Date createdAt;                 // 创建时间

    @ColumnInfo(name = "updated_at")
    private Date updatedAt;                 // 更新时间

    // ==================== 构造函数 ====================

    @Ignore
    public UserProfile() {
        this.billDays = 0;
        this.billCount = 0;
        this.gender = 0;
        this.syncState = SyncState.SYNCED;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public UserProfile(String userId) {
        this();
        this.userId = userId;
    }

    // ==================== Getter & Setter ====================

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
        return billDays != null ? billDays : 0;
    }

    public void setBillDays(Integer billDays) {
        this.billDays = billDays;
    }

    public Integer getBillCount() {
        return billCount != null ? billCount : 0;
    }

    public void setBillCount(Integer billCount) {
        this.billCount = billCount;
    }

    public SyncState getSyncState() {
        return syncState != null ? syncState : SyncState.SYNCED;
    }

    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取性别显示文本
     */
    public String getGenderText() {
        if (gender == null || gender == 0) {
            return "未设置";
        } else if (gender == 1) {
            return "男";
        } else if (gender == 2) {
            return "女";
        }
        return "未设置";
    }

    /**
     * 获取年龄
     */
    public Integer getAge() {
        if (birthday == null) {
            return null;
        }

        Date now = new Date();
        long diff = now.getTime() - birthday.getTime();
        long ageInDays = diff / (1000L * 60 * 60 * 24);
        return (int) (ageInDays / 365);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", gender=" + gender +
                ", school='" + school + '\'' +
                ", billDays=" + billDays +
                ", billCount=" + billCount +
                ", syncState=" + syncState +
                '}';
    }
}