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
    private String accountType; // 账户类型
    private String category; // 账户大类
    private String remark;
    private String cardNumber;
    private String iconUrl;
    private String currency;
    private Integer billingDay;
    private Integer repaymentDay;
    private Boolean includeBill; // 修复 Bmob 字段名长度限制问题 (fieldName length can't larger than 20)
    private Boolean includeInTotal;
    private Boolean canBeSelected;

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

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getBillingDay() { return billingDay; }
    public void setBillingDay(Integer billingDay) { this.billingDay = billingDay; }

    public Integer getRepaymentDay() { return repaymentDay; }
    public void setRepaymentDay(Integer repaymentDay) { this.repaymentDay = repaymentDay; }

    public Boolean getIncludeBill() { return includeBill; }
    public void setIncludeBill(Boolean includeBill) { this.includeBill = includeBill; }

    public Boolean getIncludeInTotal() { return includeInTotal; }
    public void setIncludeInTotal(Boolean includeInTotal) { this.includeInTotal = includeInTotal; }

    public Boolean getCanBeSelected() { return canBeSelected; }
    public void setCanBeSelected(Boolean canBeSelected) { this.canBeSelected = canBeSelected; }

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
        local.setAccountType(accountType);
        local.setCategory(category);
        local.setRemark(remark);
        local.setCardNumber(cardNumber);
        local.setIconUrl(iconUrl);
        local.setCurrency(currency != null ? currency : "CNY");
        local.setBillingDay(billingDay != null ? billingDay : 0);
        local.setRepaymentDay(repaymentDay != null ? repaymentDay : 0);
        local.setIncludeBill(includeBill != null ? includeBill : false);
        local.setIncludeInTotal(includeInTotal != null ? includeInTotal : true);
        local.setCanBeSelected(canBeSelected != null ? canBeSelected : true);
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
        cloud.setAccountType(local.getAccountType());
        cloud.setCategory(local.getCategory());
        cloud.setRemark(local.getRemark());
        cloud.setCardNumber(local.getCardNumber());
        cloud.setIconUrl(local.getIconUrl());
        cloud.setCurrency(local.getCurrency());
        cloud.setBillingDay(local.getBillingDay());
        cloud.setRepaymentDay(local.getRepaymentDay());
        cloud.setIncludeBill(local.isIncludeBill());
        cloud.setIncludeInTotal(local.isIncludeInTotal());
        cloud.setCanBeSelected(local.isCanBeSelected());

        if (local.getUserId() != null)
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));

        if (local.getGroupId() != null)
            cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));

        return cloud;
    }
}
