package com.example.my_project1.data.model.account;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.io.Serializable;
import java.util.Date;

import io.reactivex.annotations.NonNull;

@Entity(tableName = "accounts")
public class Account  implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "object_id")
    private String objectId; // 对应 Bmob 的 objectId

    @ColumnInfo(name = "user_id")
    private String userId; // 用户ID

    @ColumnInfo(name = "group_id")
    private String groupId; // 所属分组ID

    private String name; // 账户名称
    private double balance; // 余额
    private boolean isCredit; // 是否信贷账户
    private double creditLimit; // 信用额度
    private String remark; // 备注
    private String cardNumber; // 卡号
    private String iconUrl; // 图标URL
    private Date createdAt;
    private Date updatedAt;

    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    public Account () {}

    @Ignore
    public Account( String objectId, String userId, String groupId,
                   String name, double balance, boolean isCredit, double creditLimit,
                   String remark, String cardNumber, String iconUrl){

        this.objectId = objectId;
        this.userId = userId;
        this.groupId = groupId;
        this.name = name;
        this.balance = balance;
        this.isCredit = isCredit;
        this.creditLimit = creditLimit;
        this.remark = remark;
        this.cardNumber = cardNumber;
        this.iconUrl = iconUrl;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    // getter & setter
    @NonNull


    public String getObjectId() { return objectId; }
    public void setObjectId(@NonNull String objectId) { this.objectId = objectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public boolean isCredit() { return isCredit; }
    public void setCredit(boolean credit) { isCredit = credit; }

    public double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(double creditLimit) { this.creditLimit = creditLimit; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public SyncState getSyncState() {
        return syncState;
    }

    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }
}
