package com.example.my_project1.data.remote.model.cloudaccount;

import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobPointer;

public class Account extends BmobObject {

    public Account() {
        this.setTableName("Account"); // 对应云端表名
    }

    private BmobPointer user;
    private BmobPointer group;
    private String name;
    private Double balance;
    private Boolean isCredit;
    private Double creditLimit;
    private String remark;
    private String cardNumber;
    private String iconUrl;

    // Getter & Setter
    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

    public BmobPointer getGroup() { return group; }
    public void setGroup(BmobPointer group) { this.group = group; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    public Boolean getIsCredit() { return isCredit; }
    public void setIsCredit(Boolean isCredit) { this.isCredit = isCredit; }

    public Double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(Double creditLimit) { this.creditLimit = creditLimit; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    //获取本地账户组id
    public String getGroupId() {
        return group != null ? group.getObjectId() : null;
    }

    //获取本地用户id
    public String getUserId() {
        return user != null ? user.getObjectId() : null;
    }

    /** 云端 → 本地 */
    public com.example.my_project1.data.model.account.Account toLocalEntity() {
        com.example.my_project1.data.model.account.Account local = new com.example.my_project1.data.model.account.Account();
        local.setObjectId(getObjectId());
        local.setUserId(user == null ? null : user.getObjectId());
        local.setGroupId(group == null ? null : group.getObjectId());
        local.setName(name);
        local.setBalance(balance != null ? balance : 0);
        local.setCredit(isCredit != null && isCredit);
        local.setCreditLimit(creditLimit != null ? creditLimit : 0);
        local.setRemark(remark);
        local.setCardNumber(cardNumber);
        local.setIconUrl(iconUrl);
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));
        return local;
    }

    /** 本地 → 云端 */
    public static Account fromLocal(com.example.my_project1.data.model.account.Account local) {
        Account cloud = new Account();

        if (local.getObjectId() != null)
            cloud.setObjectId(local.getObjectId());

        cloud.setName(local.getName());
        cloud.setIsCredit(local.isCredit());
        cloud.setBalance(local.getBalance() != 0 ? local.getBalance() : 0);
        cloud.setCreditLimit(local.getCreditLimit() != 0 ? local.getCreditLimit() : 0);
        cloud.setRemark(local.getRemark());
        cloud.setCardNumber(local.getCardNumber());
        cloud.setIconUrl(local.getIconUrl());

        if (local.getUserId() != null)
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));

        if (local.getGroupId() != null)
            cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));

        return cloud;
    }
}
