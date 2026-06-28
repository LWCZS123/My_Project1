package com.example.my_project1.data.remote.model.cloudaccount;

import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobPointer;

public class AccountGroup extends BmobObject {

    public AccountGroup() {
        this.setTableName("AccountGroup"); // 对应 Bmob 表名
    }

    private BmobPointer user;
    private String name;
    private String iconUrl;
    private int accountCount;

    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public int getAccountCount() { return accountCount; }
    public void setAccountCount(int accountCount) { this.accountCount = accountCount; }

    //获取本地用户id
    public String getUserId() {
        return user != null ? user.getObjectId() : null;
    }
    /** 云端 → 本地 */
    public com.example.my_project1.data.model.account.AccountGroup toLocalEntity() {
        com.example.my_project1.data.model.account.AccountGroup local = new com.example.my_project1.data.model.account.AccountGroup();
        local.setObjectId(getObjectId());
        local.setUserId(user == null ? null : user.getObjectId());
        local.setName(name);
        local.setIconUrl(iconUrl);
        local.setAccountCount(accountCount);
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));
        return local;
    }

    /** 本地 → 云端 */
    public static AccountGroup fromLocal(com.example.my_project1.data.model.account.AccountGroup local) {
        AccountGroup cloud = new AccountGroup();

        if (local.getObjectId() != null)
            cloud.setObjectId(local.getObjectId());

        cloud.setName(local.getName());
        cloud.setIconUrl(local.getIconUrl());
        cloud.setAccountCount(local.getAccountCount());

        if (local.getUserId() != null)
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        return cloud;
    }
}
