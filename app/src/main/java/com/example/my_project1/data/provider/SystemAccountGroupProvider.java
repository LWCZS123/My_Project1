package com.example.my_project1.data.provider;

import com.example.my_project1.data.model.account.AccountGroup;

import java.util.ArrayList;
import java.util.List;

public class SystemAccountGroupProvider {

    public static List<AccountGroup> getDefaultGroups(String userId) {
        List<AccountGroup> list = new ArrayList<>();
        list.add(new AccountGroup(null,"储蓄账户",userId,null,0));
        list.add(new AccountGroup(null, "信贷账户",userId,null,0));
        list.add(new AccountGroup(null, "充值账户",userId,null,0));
        return list;
    }
}
