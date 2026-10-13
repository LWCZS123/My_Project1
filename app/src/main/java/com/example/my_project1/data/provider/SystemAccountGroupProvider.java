package com.example.my_project1.data.provider;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.AccountGroup;

import java.util.ArrayList;
import java.util.List;

public class SystemAccountGroupProvider {

    public static List<AccountGroup> getDefaultGroups(String userId) {
        List<AccountGroup> list = new ArrayList<>();
        AccountGroup g1 = new AccountGroup(null, "资金账户", userId, null, 0);
        g1.setSyncState(SyncState.TO_CREATE);
        AccountGroup g2 = new AccountGroup(null, "信用账户", userId, null, 0);
        g2.setSyncState(SyncState.TO_CREATE);
        AccountGroup g3 = new AccountGroup(null, "充值账户", userId, null, 0);
        g3.setSyncState(SyncState.TO_CREATE);

        list.add(g1);
        list.add(g2);
        list.add(g3);
        return list;
    }
}
