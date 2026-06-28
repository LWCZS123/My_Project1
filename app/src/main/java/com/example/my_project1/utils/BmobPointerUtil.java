package com.example.my_project1.utils;

import com.example.my_project1.data.remote.model.cloudaccount.Account;
import com.example.my_project1.data.remote.model.cloudaccount.AccountGroup;
import com.example.my_project1.data.remote.model.clouduser.CloudUser;
import com.example.my_project1.data.remote.model.cloudwish.CloudWish;

import cn.bmob.v3.datatype.BmobPointer;

public class BmobPointerUtil {

    /** 用户关联 */
    public static BmobPointer user(String userId) {
        return new BmobPointer(new CloudUser(userId));
    }

    /** 账户组关联 */
    public static BmobPointer group(String groupId) {
        AccountGroup group = new AccountGroup();
        group.setObjectId(groupId);
        return new BmobPointer(group);
    }

    /** 账户关联 */
    public static BmobPointer account(String accountId) {
        Account account = new Account();
        account.setObjectId(accountId);
        return new BmobPointer(account);
    }

    /** ✅ 愿望关联（修复点） */
    public static BmobPointer wish(String wishId) {
        return new BmobPointer(new CloudWish(wishId));
    }
}