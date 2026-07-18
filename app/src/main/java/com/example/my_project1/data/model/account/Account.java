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
    private String accountType; // 账户类型 (现金、储蓄卡、信用卡等)
    private String category; // 账户大类 (资金账户、信用账户、充值账户)
    private String remark; // 备注
    private String cardNumber; // 卡号
    private String iconUrl; // 图标URL
    private String currency = "CNY"; // 币种
    private int billingDay; // 账单日
    private int repaymentDay; // 还款日
    @ColumnInfo(name = "includeBillInCurrentPeriod")
    private boolean includeBill; // 出账日账单计入当期
    private boolean includeInTotal = true; // 是否计入总资产
    private boolean canBeSelected = true; // 记账时是否可选
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

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getBillingDay() { return billingDay; }
    public void setBillingDay(int billingDay) { this.billingDay = billingDay; }

    public int getRepaymentDay() { return repaymentDay; }
    public void setRepaymentDay(int repaymentDay) { this.repaymentDay = repaymentDay; }

    public boolean isIncludeBill() { return includeBill; }
    public void setIncludeBill(boolean includeBill) { this.includeBill = includeBill; }

    public boolean isIncludeInTotal() { return includeInTotal; }
    public void setIncludeInTotal(boolean includeInTotal) { this.includeInTotal = includeInTotal; }

    public boolean isCanBeSelected() { return canBeSelected; }
    public void setCanBeSelected(boolean canBeSelected) { this.canBeSelected = canBeSelected; }

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
