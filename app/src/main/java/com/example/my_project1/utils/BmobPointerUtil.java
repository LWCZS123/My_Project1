package com.example.my_project1.utils;

import com.example.my_project1.data.remote.model.cloudaccount.Account;
import com.example.my_project1.data.remote.model.cloudaccount.AccountGroup;
import com.example.my_project1.data.remote.model.cloudsaving.CloudSavingPlan;
import com.example.my_project1.data.remote.model.clouduser.CloudUser;
import com.example.my_project1.data.remote.model.cloudwish.CloudWish;

import cn.bmob.v3.datatype.BmobPointer;

public class BmobPointerUtil {

    private static boolean isNumeric(String str) {
        return str != null && str.trim().matches("\\d+");
    }

    /** 校验是否为合法的 Bmob 云端 10 位/16 位 objectId 格式 */
    private static boolean isValidBmobObjectId(String id) {
        if (id == null) return false;
        String trimmed = id.trim();
        if (trimmed.isEmpty() || isNumeric(trimmed)) return false;
        if ("default_book".equalsIgnoreCase(trimmed)) return false;
        // Bmob objectId 通常为至少 8 位的英数字符串
        return trimmed.length() >= 8;
    }

    /** 用户关联 */
    public static BmobPointer user(String userId) {
        if (!isValidBmobObjectId(userId)) return null;
        return new BmobPointer(new CloudUser(userId.trim()));
    }

    /** 账户组关联 */
    public static BmobPointer group(String groupId) {
        if (!isValidBmobObjectId(groupId)) return null;
        AccountGroup group = new AccountGroup();
        group.setObjectId(groupId.trim());
        return new BmobPointer(group);
    }

    /** 账户关联 */
    public static BmobPointer account(String accountId) {
        if (!isValidBmobObjectId(accountId)) return null;
        Account account = new Account();
        account.setObjectId(accountId.trim());
        return new BmobPointer(account);
    }

    /** 愿望关联 */
    public static BmobPointer wish(String wishId) {
        if (!isValidBmobObjectId(wishId)) return null;
        return new BmobPointer(new CloudWish(wishId.trim()));
    }

    /** 存钱计划关联 */
    public static BmobPointer savingPlan(String planId) {
        if (!isValidBmobObjectId(planId)) return null;
        return new BmobPointer(new CloudSavingPlan(planId.trim()));
    }

    /** 账本关联 */
    public static BmobPointer book(String bookId) {
        if (!isValidBmobObjectId(bookId)) return null;
        cn.bmob.v3.BmobObject book = new cn.bmob.v3.BmobObject("Book") {};
        book.setObjectId(bookId.trim());
        return new BmobPointer(book);
    }
}
